"""A bounded, persistent public TMDB corpus warmed outside Home requests.

Only public title metadata and model-versioned vectors are persisted. Profile
signals never enter this cache. Reads take a short lock and never perform network
or GPU work; a single daemon serializes paced TMDB acquisition and vector batches.
"""
from collections import deque
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import hashlib
import json
import logging
import os
import threading
import time
import urllib.error

_LOG = logging.getLogger(__name__)
_STATE = 'catalog:v3:state'
_METADATA = 'catalog:v3:metadata:'
_TTL = 24 * 3600
_MAX_PENDING = 400
_BATCH = 8


class CatalogIndex:
    def __init__(self, tmdb, cache, vectorize, fingerprint='catalog-default', *,
                 target=None, request_interval=0.3, refresh_interval=60):
        self.tmdb, self.cache, self.vectorize = tmdb, cache, vectorize
        self.fingerprint = str(fingerprint)
        self.target = min(10_000, max(40, int(target or os.environ.get('RECOMMENDER_CATALOG_SIZE', '3000'))))
        self.request_interval, self.refresh_interval = request_interval, refresh_interval
        self._metadata, self._vectors, self._pending = {}, {}, {}
        self._recipes = deque()
        self._recipe_templates = []
        self._person_queries = set()
        self._keyword_ids = {}
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._worker = None
        self._version = 0
        self._last_request = 0
        self._last_discovery = 0
        self._recipe_day = None
        self._refresh_plan = False
        self._max_entries = self.target + _MAX_PENDING
        self._next_sweep = 0
        self._restore()
        self._build_recipes()

    @property
    def ready(self):
        with self._lock:
            return len(self._vectors) >= min(120, self.target)

    @property
    def version(self):
        with self._lock:
            return self._version

    def snapshot(self):
        with self._lock:
            return dict(self._metadata), dict(self._vectors)

    def request(self, references):
        """Queue public IDs for enrichment, preserving caller priority; no IO."""
        with self._lock:
            for ident, reference in references.items():
                kind = reference.get('mediaType')
                value = reference.get('id', reference.get('tmdbId'))
                if kind not in ('movie', 'tv') or type(value) is not int or value <= 0:
                    continue
                ident = f'{kind}-{value}'
                existing = self._metadata.get(ident)
                if existing and self._fresh(existing) and ident in self._vectors:
                    continue
                if len(self._pending) >= _MAX_PENDING and ident not in self._pending:
                    background = next((queued for queued, task in reversed(self._pending.items())
                                       if not task[2]), None)
                    if background is None:
                        break
                    self._pending.pop(background)
                self._pending[ident] = (kind, value, True)

    def start(self):
        with self._lock:
            if self._worker is not None and self._worker.is_alive():
                return
            self._stop.clear()
            self._worker = threading.Thread(target=self._run, name='tmdb-catalog', daemon=True)
            self._worker.start()

    def stop(self, timeout=10):
        self._stop.set()
        worker = self._worker
        if worker is not None:
            worker.join(timeout)

    def _restore(self):
        state = self.cache.get('content', _STATE) or {}
        for ident in state.get('ids', [])[:self._max_entries]:
            value = self.cache.get('content', _METADATA + ident)
            if not isinstance(value, dict) or not value.get('id'):
                continue
            self._metadata[ident] = value
            record = self.cache.get('vectors', self._vector_key(ident))
            if isinstance(record, dict) and record.get('digest') == self._vector_digest(value):
                self._vectors[ident] = record['vector']
        if self._metadata:
            self._version += 1

    def _vector_key(self, ident):
        # Overwrite one record per public title, rather than accumulating another
        # 1024-float copy every time popularity or providers change.
        return f'catalog:v3:vector:{self.fingerprint}:{ident}'

    @staticmethod
    def _vector_digest(metadata):
        # Share the engine's exact document recipe, so structured eligibility
        # metadata cannot invalidate an unchanged semantic embedding.
        from engine import text
        return hashlib.sha256(text(metadata).encode()).hexdigest()

    @staticmethod
    def _fresh(metadata):
        return time.time() - metadata.get('_catalog_fetched_at', 0) < _TTL

    def _save_state(self):
        with self._lock:
            ids = list(self._metadata)
        self.cache.put('content', _STATE, {'ids': ids})

    def _publish(self, values):
        if not values:
            return
        # Encoder caches underlying content digests, including model recipe/version.
        vectors = self.vectorize(values)
        for ident, value in values.items():
            self.cache.put('content', _METADATA + ident, value)
            vector = vectors.get(ident)
            if vector is not None:
                self.cache.put('vectors', self._vector_key(ident),
                               {'digest': self._vector_digest(value), 'vector': vector})
        evicted = []
        with self._lock:
            for ident, value in values.items():
                if ident not in self._metadata and len(self._metadata) >= self._max_entries:
                    # Bounded FIFO public corpus; never evict according to a profile.
                    victim = next(iter(self._metadata))
                    self._metadata.pop(victim)
                    self._vectors.pop(victim, None)
                    evicted.append(victim)
                # A public metadata refresh is a new FIFO publication. A freshly
                # enriched seed cannot be evicted later in the very same batch.
                self._metadata.pop(ident, None)
                self._metadata[ident] = value
                self._vectors.pop(ident, None)
                if ident in vectors:
                    self._vectors[ident] = vectors[ident]
            self._version += 1
        for ident in evicted:
            self.cache.delete('content', _METADATA + ident)
            self.cache.delete('vectors', self._vector_key(ident))
        self._save_state()

    def _api(self, callback):
        for attempt in range(3):
            if self._stop.is_set():
                raise InterruptedError('catalog stopped')
            delay = self.request_interval - (time.monotonic() - self._last_request)
            if delay > 0 and self._stop.wait(delay):
                raise InterruptedError('catalog stopped')
            self._last_request = time.monotonic()
            try:
                return callback()
            except urllib.error.HTTPError as error:
                if error.code != 429 or attempt == 2:
                    error.close()
                    raise
                retry = error.headers.get('Retry-After', '') if error.headers else ''
                try:
                    delay = max(0, float(retry))
                except ValueError:
                    try:
                        delay = max(0, parsedate_to_datetime(retry).timestamp() - time.time())
                    except (ValueError, TypeError, OverflowError):
                        delay = 2 ** (attempt + 1)
                error.close()
                if self._stop.wait(delay):
                    raise InterruptedError('catalog stopped')
        raise RuntimeError('unreachable')

    @staticmethod
    def _slim(value):
        fields = ('id', 'title', 'name', 'overview', 'poster_path', 'backdrop_path', 'genres',
                  'keywords', 'watch/providers', 'release_dates', 'release_date', 'first_air_date',
                  'vote_average', 'vote_count', 'popularity', 'runtime', 'episode_run_time',
                  'number_of_seasons', 'number_of_episodes', 'type', 'status', 'last_episode_to_air',
                  'adult', 'original_language')
        slim = {name: value[name] for name in fields if name in value}
        credits = value.get('credits', {})
        slim['credits'] = {
            'cast': [{name: person[name] for name in ('id', 'name') if name in person}
                     for person in credits.get('cast', [])[:20]],
            'crew': [{name: person[name] for name in ('id', 'name', 'job') if name in person}
                     for person in credits.get('crew', []) if person.get('job') == 'Director'],
        }
        slim['_catalog_fetched_at'] = time.time()
        return slim

    def _people(self, metadata):
        # Expand verified public credits from requested titles, with a fixed bound.
        crew = [p for p in metadata.get('credits', {}).get('crew', []) if p.get('job') == 'Director'][:1]
        cast = metadata.get('credits', {}).get('cast', [])[:1]
        for field, people in (('with_crew', crew), ('with_cast', cast)):
            for person in people:
                marker = (field, person.get('id'))
                if not marker[1] or marker in self._person_queries or len(self._person_queries) >= 32:
                    continue
                self._person_queries.add(marker)
                self._recipes.appendleft(('/discover/movie', {'sort_by': 'popularity.desc',
                                          'include_adult': 'false', 'with_runtime.gte': 45,
                                          field: marker[1]}, 'movie', 1))

    def _build_recipes(self):
        self._recipes.clear()
        current = datetime.now(timezone.utc)
        self._recipe_day = current.date()
        movie = dict(include_adult='false', include_video='false',
                     **{'vote_count.gte': 100, 'with_runtime.gte': 45})
        tv = dict(include_adult='false', **{'vote_count.gte': 50})
        recipes = [('/trending/all/week', {}, None),
                   ('/discover/movie', {**movie, 'sort_by': 'popularity.desc'}, 'movie'),
                   ('/discover/tv', {**tv, 'sort_by': 'popularity.desc', 'with_type': '2|4'}, 'tv'),
                   ('/discover/movie', {**movie, 'sort_by': 'vote_average.desc', 'with_runtime.lte': 99}, 'movie'),
                   ('/discover/tv', {**tv, 'sort_by': 'vote_average.desc', 'with_type': 2}, 'tv'),
                   ('/discover/movie', {**movie, 'sort_by': 'popularity.asc', 'vote_average.gte': 7}, 'movie')]
        for kind, genre_ids, defaults in (
                ('movie', (35, 27, 12, 10749, 878, 14, 9648, 53, 18, 10751, 80, 28, 36), movie),
                ('tv', (18, 35, 9648, 10765, 80, 10759), tv)):
            for genre_id in genre_ids:
                recipes.append((f'/discover/{kind}', {**defaults, 'sort_by': 'popularity.desc', 'with_genres': genre_id}, kind))
        for start, end in ((1990, 1999), (1980, 1989), (2000, 2009), (2010, 2019), (2020, datetime.now(timezone.utc).year)):
            for kind, defaults, prefix in (('movie', movie, 'primary_release_date'), ('tv', tv, 'first_air_date')):
                recipes.append((f'/discover/{kind}', {**defaults, 'sort_by': 'vote_average.desc',
                                prefix + '.gte': f'{start}-01-01', prefix + '.lte': f'{end}-12-31'}, kind))
        # Resolve keyword IDs from exact TMDB search results rather than hard-coded guesses.
        terms = ['christmas', 'halloween', 'ghost', 'witch', 'monster', 'winter', 'snow',
                 'road trip', 'summer vacation', 'family reunion', 'thanksgiving', 'cooking',
                 'based on true story', 'biography', 'new beginning', 'second chance']
        month = current.month
        seasonal = ('halloween', 'ghost', 'witch') if month == 10 else ('christmas', 'winter', 'snow') if month in (11, 12, 1, 2) else ('road trip', 'summer vacation')
        terms.sort(key=lambda term: (term not in seasonal, term))
        # Seasonal coverage comes before exhaustive genre/era pages during a cold warmup.
        keyword_recipes = [('/search/keyword', {'query': term}, 'keyword') for term in terms]
        seasonal_recipes = [recipe for recipe in keyword_recipes if recipe[1]['query'] in seasonal]
        other_keywords = [recipe for recipe in keyword_recipes if recipe[1]['query'] not in seasonal]
        self._recipe_templates = recipes[:2] + seasonal_recipes + recipes[2:6] + other_keywords + recipes[6:]
        self._recipes.extend((path, query, kind, 1) for path, query, kind in self._recipe_templates)

    def _discover(self):
        if not self._recipes:
            # Refresh the public plan once daily; pages rotate while warming.
            if time.time() - self._last_discovery < _TTL:
                self._refresh_plan = False
                return False
            self._build_recipes()
            self._refresh_plan = True
            self._last_discovery = time.time()
        path, query, kind, page = self._recipes.popleft()
        result = self._api(lambda: self.tmdb.get(path, **query, page=page))
        if kind == 'keyword':
            term = query['query']
            ids = [row['id'] for row in result.get('results', [])
                   if row.get('name', '').casefold() == term.casefold() and row.get('id')]
            if ids:
                self._keyword_ids[term] = ids[0]
                defaults = {'include_adult': 'false', 'vote_count.gte': 30,
                            'with_runtime.gte': 45,
                            'sort_by': 'popularity.desc', 'with_keywords': ids[0]}
                self._recipes.appendleft(('/discover/movie', defaults, 'movie', 1))
            return True
        with self._lock:
            for row in result.get('results', []):
                media_type = kind or row.get('media_type')
                if media_type not in ('movie', 'tv') or not row.get('id') or row.get('adult'):
                    continue
                ident = f"{media_type}-{row['id']}"
                if ident not in self._metadata and ident not in self._pending and len(self._pending) < _MAX_PENDING:
                    self._pending[ident] = (media_type, row['id'], False)
        pages = min(5, int(result.get('total_pages', 1)))
        if page < pages and len(self._metadata) < self.target:
            self._recipes.append((path, query, kind, page + 1))
        return True

    def refresh_once(self):
        """Perform one bounded worker unit; useful for operational warmup/testing."""
        if datetime.now(timezone.utc).date() != self._recipe_day:
            self._build_recipes()
            self._refresh_plan = True
            self._last_discovery = time.time()
        with self._lock:
            # Explicit IDs precede discovery IDs even if discovered earlier.
            selected = sorted(self._pending, key=lambda ident: not self._pending[ident][2])[:_BATCH]
            pending = [(ident, self._pending.pop(ident)) for ident in selected]
            missing = {ident: value for ident, value in self._metadata.items()
                       if ident not in self._vectors}
        if pending:
            values = {}
            for ident, (kind, value, requested) in pending:
                try:
                    detail = self._api(lambda: self.tmdb.detail(kind, value))
                    if detail.get('id') != value or detail.get('adult'):
                        continue
                    values[ident] = self._slim(detail)
                    if requested:
                        self._people(values[ident])
                except InterruptedError:
                    raise
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        _LOG.warning('Catalog detail %s failed: HTTP %s', ident, error.code)
                except (OSError, ValueError, TimeoutError) as error:
                    _LOG.warning('Catalog detail %s failed: %s', ident, type(error).__name__)
            self._publish(values)
            return bool(values)
        if missing:
            self._publish(dict(list(missing.items())[:_BATCH]))
            return True
        if time.time() >= self._next_sweep:
            self._next_sweep = time.time() + self.refresh_interval
            with self._lock:
                for ident, value in self._metadata.items():
                    if not self._fresh(value) and len(self._pending) < _MAX_PENDING:
                        kind, raw_id = ident.split('-', 1)
                        self._pending[ident] = (kind, int(raw_id), False)
            if self._pending:
                return True
        # Grow in the background, then refresh Discover once the public corpus ages.
        if self._refresh_plan or len(self._metadata) < self.target or time.time() - self._last_discovery >= _TTL:
            self._last_discovery = self._last_discovery or time.time()
            return self._discover()
        return False

    def _run(self):
        while not self._stop.is_set():
            try:
                worked = self.refresh_once()
            except InterruptedError:
                break
            except Exception as error:
                # Upstream exception text/tracebacks can contain authenticated URLs.
                _LOG.warning('Catalog background warmup failed: %s', type(error).__name__)
                worked = False
            if not worked:
                self._stop.wait(self.refresh_interval)
