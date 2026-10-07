"""Content-only recommendation engine. User signals are never written to disk."""
import hashlib
import json
import math
import os
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from model import MODEL, REVISION

MAX_CANDIDATES = 300
MAX_SIGNALS = 200
MAX_DYNAMIC_SHELVES = 5
MIN_THEME_SIMILARITY = 0.30
THEME_FIT_MARGIN = 0.12
THEME_INSTRUCTION = 'Retrieve movies or television shows matching the viewing mood, subjects and themes in the query.'


def key(item):
    return f"{item['mediaType']}-{item.get('id', item.get('tmdbId'))}"


def day(value):
    try:
        return date.fromisoformat(str(value)[:10])
    except (ValueError, TypeError):
        return None


def release_policy(metadata, media_type, today):
    """Return eligibility and earliest public release (premieres aren't public)."""
    if media_type == 'tv':
        first = day(metadata.get('first_air_date'))
        return bool(first and first <= today), first
    all_releases = [r for region in metadata.get('release_dates', {}).get('results', [])
                    for r in region.get('release_dates', [])]
    releases = [r for r in all_releases if r.get('type') in (2, 3, 4, 5, 6)]
    dated = [(r['type'], day(r.get('release_date'))) for r in releases]
    dated = [(kind, released) for kind, released in dated if released]
    if all_releases and not dated:
        # A festival premiere alone doesn't establish any public release.
        return False, None
    # Typed public dates take precedence over the often regional primary date.
    first = min((released for _, released in dated), default=day(metadata.get('release_date')))
    if first is None or first > today:
        return False, first
    home_release = any(kind in (4, 5, 6) and released <= today for kind, released in dated)
    providers = metadata.get('watch/providers', {}).get('results', {})
    offered = lambda region: any(providers.get(region, {}).get(kind) for kind in ('flatrate', 'free', 'ads', 'rent', 'buy'))
    # US only when the title has no Canadian home offer.
    available = offered('CA') or offered('US')
    return home_release or available or first <= today - timedelta(days=30), first


def validate(request):
    if not isinstance(request, dict):
        raise ValueError('request must be an object')
    zone = request.get('timeZone', 'UTC')
    if not isinstance(zone, str) or len(zone) > 80:
        raise ValueError('invalid timeZone')
    try:
        ZoneInfo(zone)
    except (ZoneInfoNotFoundError, ValueError):
        raise ValueError('invalid timeZone') from None
    limits = {'candidates': MAX_CANDIDATES, 'history': MAX_SIGNALS,
              'myList': MAX_SIGNALS, 'feedback': MAX_SIGNALS}
    for field, limit in limits.items():
        rows = request.get(field, [])
        if not isinstance(rows, list) or len(rows) > limit:
            raise ValueError(f'{field} must have at most {limit} titles')
        for row in rows:
            if not isinstance(row, dict) or row.get('mediaType') not in ('movie', 'tv'):
                raise ValueError(f'invalid {field} mediaType')
            ident = row.get('id') if field == 'candidates' else row.get('tmdbId')
            if type(ident) is not int or not 0 < ident < 2**31:
                raise ValueError(f'invalid {field} ID')
            for number in ('watchedMs', 'latestWatchedMs', 'positionMs', 'durationMs', 'lastWatchedAt', 'updatedAt'):
                value = row.get(number, 0)
                if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 10**15:
                    raise ValueError(f'invalid {field} {number}')
            for number in ('season', 'episode'):
                value = row.get(number, 0)
                if type(value) is not int or not 0 <= value <= 100_000:
                    raise ValueError(f'invalid {field} {number}')
            if field == 'feedback' and row.get('value') not in ('like', 'dislike'):
                raise ValueError('feedback value must be like or dislike')
            if field == 'candidates':
                for name in ('title', 'overview', 'posterUrl', 'backdropUrl', 'releaseDate'):
                    if row.get(name) is not None and (not isinstance(row[name], str) or len(row[name]) > 8000):
                        raise ValueError(f'invalid candidate {name}')
    return request


class ContentCache:
    def __init__(self, path):
        os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('CREATE TABLE IF NOT EXISTS content (key TEXT PRIMARY KEY, fetched REAL, data TEXT)')
        self.db.execute('CREATE TABLE IF NOT EXISTS vectors (key TEXT PRIMARY KEY, data TEXT)')
        self.lock = threading.Lock()

    def get(self, table, ident, ttl=None):
        with self.lock:
            row = self.db.execute(f'SELECT * FROM {table} WHERE key=?', (ident,)).fetchone()
        if not row or (ttl is not None and time.time() - row[1] > ttl):
            return None
        return json.loads(row[-1])

    def put(self, table, ident, value):
        encoded = json.dumps(value, separators=(',', ':'))
        with self.lock:
            if table == 'content':
                self.db.execute('INSERT OR REPLACE INTO content VALUES (?,?,?)', (ident, time.time(), encoded))
            else:
                self.db.execute('INSERT OR REPLACE INTO vectors VALUES (?,?)', (ident, encoded))
            self.db.commit()

    def delete(self, table, ident):
        with self.lock:
            self.db.execute(f'DELETE FROM {table} WHERE key=?', (ident,))
            self.db.commit()


class TMDB:
    def __init__(self, api_key, cache, base='https://api.themoviedb.org/3'):
        self.api_key, self.cache, self.base = api_key, cache, base

    def get(self, path, **query):
        # Pages, themes and filters must never share a cache entry. Credentials
        # are added only after building this public-content identity.
        public_query = {**query, 'language': 'en-US'}
        ident = path + '?' + urllib.parse.urlencode(sorted(public_query.items()))
        cached = self.cache.get('content', ident, 24 * 3600)
        if cached is not None:
            return cached
        query.update(api_key=self.api_key, language='en-US')
        req = urllib.request.Request(self.base + path + '?' + urllib.parse.urlencode(query),
                                     headers={'Accept': 'application/json'})
        with urllib.request.urlopen(req, timeout=5) as response:
            data = json.load(response)
        self.cache.put('content', ident, data)
        return data

    def detail(self, media_type, ident):
        append = 'keywords,credits,watch/providers,release_dates' if media_type == 'movie' else 'keywords,credits,watch/providers'
        return self.get(f'/{media_type}/{ident}', append_to_response=append)

    def recommendations(self, media_type, ident):
        return self.get(f'/{media_type}/{ident}/recommendations').get('results', [])[:20]


def candidate(metadata, media_type, first=None):
    poster, backdrop = metadata.get('poster_path'), metadata.get('backdrop_path')
    released = first or day(metadata.get('release_date') or metadata.get('first_air_date'))
    return {'id': metadata['id'], 'mediaType': media_type,
            'title': metadata.get('title') or metadata.get('name') or '',
            'overview': metadata.get('overview') or '',
            'posterUrl': f'https://image.tmdb.org/t/p/w342{poster}' if poster else None,
            'backdropUrl': f'https://image.tmdb.org/t/p/w780{backdrop}' if backdrop else None,
            'year': released.year if released else None, 'rating': metadata.get('vote_average'),
            'genres': [g['name'] for g in metadata.get('genres', [])],
            'releaseDate': released.isoformat() if released else None}


def text(metadata):
    keywords = metadata.get('keywords', {})
    terms = keywords.get('keywords', keywords.get('results', []))
    cast = metadata.get('credits', {}).get('cast', [])[:5]
    directors = [p['name'] for p in metadata.get('credits', {}).get('crew', []) if p.get('job') == 'Director'][:2]
    # Repetition gives plot/theme more influence than a single shared actor.
    overview = (metadata.get('overview') or '')[:3000]
    return '\n'.join([metadata.get('title') or metadata.get('name') or '', overview,
                      'Genres: ' + ', '.join(g['name'] for g in metadata.get('genres', [])),
                      'Themes: ' + ', '.join(g['name'] for g in terms[:20]),
                      'Cast: ' + ', '.join(g['name'] for g in cast), 'Directors: ' + ', '.join(directors)])


def cosine(a, b):
    # The embedding model returns normalized vectors.
    return sum(x * y for x, y in zip(a, b))


class Similarities:
    """Reuse a CPU float32 corpus matrix; no GPU inference during ranking."""
    def __init__(self, vectors, identities):
        self.vectors, self.identities = vectors, list(identities)
        try:
            import numpy as np
        except ImportError:
            np = None
        self.np = np
        self.matrix = np.asarray([vectors[ident] for ident in self.identities], dtype=np.float32) if np is not None and self.identities else None

    def against(self, vector):
        if self.matrix is not None:
            values = self.matrix @ self.np.asarray(vector, dtype=self.np.float32)
        else:
            values = [cosine(self.vectors[ident], vector) for ident in self.identities]
        return dict(zip(self.identities, map(float, values)))


def signals(request, now_ms):
    feedback = {}
    for row in request.get('feedback', []):
        ident = key(row)
        if ident not in feedback or row.get('updatedAt', 0) >= feedback[ident].get('updatedAt', 0):
            feedback[ident] = row
    dislikes = {ident for ident, row in feedback.items() if row['value'] == 'dislike'}
    weighted, watched = {}, set()
    for row in request.get('history', []):
        ident = key(row)
        actual = row.get('watchedMs', 0)
        position, duration = row.get('positionMs', 0), row.get('durationMs', 0)
        # Existing history predates actual-time tracking: weak resume-position evidence.
        if (0 < actual < 300_000) or (actual == 0 and position < 1_200_000):
            continue
        watched.add(ident)
        if ident in dislikes:
            continue
        completion = min(1, position / duration) if duration > 0 else 0
        strength = min(2, actual / 1_800_000) + completion if actual else 0.35 + 0.4 * completion
        age = max(0, now_ms - row.get('lastWatchedAt', now_ms)) / 86_400_000
        strength *= 0.5 + 0.5 * math.exp(-age / 90)
        weighted[ident] = {**row, 'weight': max(strength, weighted.get(ident, {}).get('weight', 0)), 'watched': True}
    for row in request.get('myList', []):
        ident = key(row)
        if ident not in dislikes:
            previous = weighted.get(ident, {})
            weighted[ident] = {**row, **previous, 'weight': previous.get('weight', 0) + 0.6}
    for ident, row in feedback.items():
        if row['value'] == 'like':
            previous = weighted.get(ident, {})
            weighted[ident] = {**row, **previous, 'weight': previous.get('weight', 0) + 2}
    ordered = sorted(weighted.values(), key=lambda row: row['weight'], reverse=True)[:40]
    excluded = watched | set(feedback) | {key(row) for row in request.get('myList', [])}
    return ordered, excluded, dislikes


def diverse_rank(pool, scores, vectors, limit, selected=()):
    # Shortlist before diversity scoring: the full public corpus can be large.
    remaining = sorted(pool, key=lambda ident: (-scores[ident], ident))[:max(160, limit * 8)]
    try:
        import numpy as np
    except ImportError:
        np = None
    if np is not None and remaining:
        matrix = np.asarray([vectors[ident] for ident in remaining], dtype=np.float32)
        base = np.asarray([scores[ident] for ident in remaining], dtype=np.float32)
        redundancy = np.zeros(len(remaining), dtype=np.float32)
        for ident in selected:
            if ident in vectors:
                redundancy = np.maximum(redundancy, matrix @ np.asarray(vectors[ident], dtype=np.float32))
        result = []
        for _ in range(min(limit, len(remaining))):
            index = int(np.argmax(base - 0.18 * redundancy))
            result.append(remaining[index])
            redundancy = np.maximum(redundancy, matrix @ matrix[index])
            base[index] = -np.inf
        return result
    remaining, chosen = set(remaining), list(selected)
    result = []
    while remaining and len(result) < limit:
        def adjusted(ident):
            redundancy = max((cosine(vectors[ident], vectors[other]) for other in chosen if other in vectors), default=0)
            return scores[ident] - 0.18 * redundancy
        best = max(sorted(remaining), key=adjusted)
        result.append(best)
        chosen.append(best)
        remaining.remove(best)
    return result


class Engine:
    def __init__(self, tmdb, cache, encoder, catalog=None):
        self.tmdb, self.cache, self.encoder = tmdb, cache, encoder
        self.catalog = catalog
        self.inference_lock = threading.Lock()
        self.theme_vectors = {}

    @property
    def model_version(self):
        return getattr(self.encoder, 'cache_fingerprint', MODEL + ':' + REVISION)

    def vectors(self, metadata):
        found, missing = {}, {}
        for ident, value in metadata.items():
            description = text(value)
            digest = hashlib.sha256((self.model_version + description).encode()).hexdigest()
            vector = self.cache.get('vectors', digest)
            if vector is None:
                missing[ident] = (description, digest)
            else:
                found[ident] = vector
        if missing:
            with self.inference_lock:
                encoded = self.encoder([value[0] for value in missing.values()])
            for (ident, (_, digest)), vector in zip(missing.items(), encoded):
                vector = [float(value) for value in vector]
                self.cache.put('vectors', digest, vector)
                found[ident] = vector
        return found

    def warm_themes(self):
        from themes import theme_definitions
        themes = theme_definitions()
        self.query_vectors([theme.query for theme in themes])

    def query_vectors(self, queries):
        missing = list(dict.fromkeys(query for query in queries if query not in self.theme_vectors))
        if missing:
            with self.inference_lock:
                if hasattr(self.encoder, 'encode_queries'):
                    vectors = self.encoder.encode_queries(missing, THEME_INSTRUCTION)
                else:
                    vectors = self.encoder(missing)
            for query, vector in zip(missing, vectors):
                self.theme_vectors[query] = vector
            while len(self.theme_vectors) > 512:
                self.theme_vectors.pop(next(iter(self.theme_vectors)))
        return {query: self.theme_vectors[query] for query in queries}

    def recommend(self, request, now=None):
        validate(request)
        now = now or datetime.now(timezone.utc)
        local_now = now.astimezone(ZoneInfo(request.get('timeZone', 'UTC')))
        now_ms = int(now.timestamp() * 1000)
        seeds, excluded, dislikes = signals(request, now_ms)
        # Actual successful playback is account-local evidence, never persisted as
        # global availability. Legacy resume position alone isn't stream proof.
        verified_streams = {key(row) for row in request.get('history', [])
                            if row.get('watchedMs', 0) >= 10_000
                            and now_ms - 7 * 86_400_000 <= row.get('lastWatchedAt', 0) <= now_ms + 300_000}
        supplied = {key(row): row for row in request.get('candidates', [])}
        # Cold corpus warmup must establish taste vectors before its candidate budget.
        references = {}
        for seed in seeds:
            references[key(seed)] = {'id': seed['tmdbId'], 'mediaType': seed['mediaType']}
        negative_seeds = [row for row in request.get('feedback', []) if key(row) in dislikes][:10]
        for row in negative_seeds:
            references[key(row)] = {'id': row['tmdbId'], 'mediaType': row['mediaType']}
        references.update(supplied)
        related = {}
        # Candidate generation from meaningful viewing broadens discovery beyond Home.
        because_seeds = sorted((s for s in seeds if s.get('watched')), key=lambda s: s.get('lastWatchedAt', 0), reverse=True)[:2]
        for seed in because_seeds if self.catalog is None else []:
            try:
                recs = self.tmdb.recommendations(seed['mediaType'], seed['tmdbId'])
            except Exception:
                recs = []
            related[key(seed)] = set()
            for row in recs:
                row = {**row, 'mediaType': seed['mediaType']}
                ident = key(row)
                related[key(seed)].add(ident)
                references.setdefault(ident, row)
        if self.catalog is not None:
            # Enrichment and embeddings run in the background. Home never waits
            # for hundreds of TMDB calls or a catalog encoding batch.
            self.catalog.request(references)
            metadata, vectors = self.catalog.snapshot()
        else:
            # Bounded synchronous path remains useful to exercise the full
            # policy with injected dependencies; production always has an index.
            deadline = time.monotonic() + 35
            def fetch(pair):
                ident, row = pair
                if time.monotonic() > deadline:
                    return ident, None
                try:
                    return ident, self.tmdb.detail(row['mediaType'], row['id'])
                except Exception:
                    return ident, None
            with ThreadPoolExecutor(max_workers=6) as workers:
                metadata = {ident: value for ident, value in workers.map(fetch, references.items()) if value is not None}
        evaluated, eligible, items, public_dates = [], [], {}, {}
        for ident, value in metadata.items():
            kind = ident.split('-', 1)[0]
            allowed, first = release_policy(value, kind, local_now.date())
            if not allowed and first and first <= local_now.date() and ident in verified_streams:
                allowed = True
            if ident in supplied:
                evaluated.append(ident)
                if first:
                    public_dates[ident] = first.isoformat()
            if allowed and not value.get('adult', False):
                eligible.append(ident)
                if ident not in excluded and value.get('poster_path'):
                    items[ident] = candidate(value, kind, first)
        if not metadata and references and self.catalog is None:
            raise RuntimeError('TMDB metadata unavailable')
        # Only eligible candidates and profile seeds need inference.
        vector_metadata = {ident: value for ident, value in metadata.items()
                           if ident in items or ident in {key(s) for s in seeds + negative_seeds}}
        if self.catalog is None:
            vectors = self.vectors(vector_metadata)
        items = {ident: value for ident, value in items.items() if ident in vectors}
        positive = [(key(s), s['weight']) for s in seeds if key(s) in vectors]
        # Several distinct taste anchors rather than a single vector that averages genres away.
        interests = []
        for ident, weight in positive:
            if len(interests) >= 8:
                break
            if all(cosine(vectors[ident], vectors[other]) < 0.88 for other, _ in interests):
                interests.append((ident, weight))
        similarities = Similarities(vectors, items)
        taste_scores = dict.fromkeys(items, 0.0)
        for seed, weight in interests:
            for ident, score in similarities.against(vectors[seed]).items():
                taste_scores[ident] = max(taste_scores[ident], score * (0.8 + 0.2 * min(1, weight / 2)))
        negative_scores = dict.fromkeys(items, 0.0)
        for other in dislikes:
            if other in vectors:
                for ident, score in similarities.against(vectors[other]).items():
                    negative_scores[ident] = max(negative_scores[ident], max(0, score - 0.65))
        scores = {}
        for ident in items:
            quality = min(1, (items[ident].get('rating') or 0) / 10)
            scores[ident] = 0.85 * taste_scores[ident] + 0.15 * quality - 0.5 * negative_scores[ident]
        shelves, used, because_shelves = [], set(), []
        dynamic_shelves = []
        if self.catalog is not None:
            from themes import active_themes, eligible_for_theme, profile_context, theme_definitions
            definitions = {theme.id: theme for theme in theme_definitions()}
            context = profile_context(request, metadata, local_now)
            dynamic_count = 0
            for theme in active_themes(request, metadata, local_now):
                available = {ident for ident in items if ident not in used and eligible_for_theme(
                    theme, metadata[ident], ident.split('-', 1)[0], context)}
                if len(available) < 6:
                    continue
                query_text = definitions['more-director'].query if theme.id.startswith('more-director-') else definitions['starring-actor'].query if theme.id.startswith('starring-actor-') else theme.query
                query = self.query_vectors([query_text])[query_text]
                fit = similarities.against(query)
                # Taste can reorder genuine matches, but must not turn an
                # unrelated favorite into holiday or mood-shelf filler.
                strongest = max(fit[ident] for ident in available)
                floor = max(MIN_THEME_SIMILARITY, strongest - THEME_FIT_MARGIN)
                available = {ident for ident in available if fit[ident] >= floor}
                if len(available) < 6:
                    continue
                theme_scores = {ident: 0.65 * fit[ident] + 0.25 * taste_scores[ident]
                                + 0.1 * min(1, (items[ident].get('rating') or 0) / 10)
                                - 0.5 * negative_scores[ident] for ident in available}
                # New-show successors inherit the taste of the completed Show
                # through the normal interest anchors, rather than copying its titles.
                ranked = diverse_rank(available, theme_scores, vectors, 16)
                if len(ranked) < 6:
                    continue
                dynamic_shelves.append({'id': 'dynamic-' + theme.id, 'title': theme.title,
                                'items': [items[ident] for ident in ranked]})
                used.update(ranked)
                dynamic_count += 1
                if dynamic_count == MAX_DYNAMIC_SHELVES:
                    break
        # Reserve a few seed-specific discoveries before For You consumes the pool.
        # The rendered shelf order still puts For You first.
        because_limit = min(12, max(0, (len(items) - len(used) - 12) // max(1, len(because_seeds))))
        for seed in because_seeds:
            ident = key(seed)
            if ident not in vectors or because_limit < 4:
                continue
            available = set(items) - used
            seed_similarities = similarities.against(vectors[ident])
            seed_scores = {other: seed_similarities[other] + (0.05 if other in related.get(ident, set()) else 0) for other in available}
            ranked = diverse_rank(available, seed_scores, vectors, because_limit)
            if ranked:
                seed_title = metadata[ident].get('title') or metadata[ident].get('name')
                because_shelves.append({'id': 'because-' + ident, 'title': 'Because you watched ' + seed_title,
                                        'items': [items[other] for other in ranked]})
                used.update(ranked)
        if interests:
            available = set(items) - used
            ranked = diverse_rank(available, scores, vectors, 24)
            # Every sixth title explores another theme, ranked on quality + novelty.
            exploration = {ident: 0.1 * ((items[ident].get('rating') or 0) / 10) - 0.15 * scores[ident] for ident in available}
            explorers = diverse_rank(available - set(ranked), exploration, vectors, 4, ranked)
            for offset, ident in zip((5, 11, 17, 23), explorers):
                if offset < len(ranked):
                    ranked[offset] = ident
            if ranked:
                shelves.append({'id': 'for-you', 'title': 'For You', 'items': [items[ident] for ident in ranked]})
                used.update(ranked)
        shelves.extend(because_shelves)
        shelves.extend(dynamic_shelves)
        next_hour = (local_now.replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)).astimezone(timezone.utc)
        # UTC fallback also bounds expiry across a repeated DST hour.
        valid_until = min(int(next_hour.timestamp() * 1000), now_ms + 3_600_000)
        if valid_until <= now_ms:
            valid_until = now_ms + 3_600_000
        return {'model': MODEL, 'modelRevision': REVISION, 'shelves': shelves,
                'eligibleKeys': eligible, 'evaluatedKeys': evaluated,
                'publicReleaseDates': public_dates,
                'completeEligibility': len(evaluated) == len(supplied), 'refreshedAt': now_ms,
                'validUntil': valid_until,
                'catalogReady': self.catalog is None or (self.catalog.ready and
                    all(key(seed) in vectors for seed in seeds + negative_seeds)),
                'modelVersion': self.model_version}
