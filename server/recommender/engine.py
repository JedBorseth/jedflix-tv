"""Content-only recommendation engine. User signals are never written to disk."""
import hashlib
import json
import math
import os
import sqlite3
import re
import threading
import time
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from model import MODEL, REVISION

MAX_CANDIDATES = 300
MAX_SIGNALS = 200
MAX_DYNAMIC_SHELVES = 5


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
    token = request.get('cacheToken', '')
    if not isinstance(token, str) or (token and not re.fullmatch(r'[A-Za-z0-9_-]{16,100}', token)):
        raise ValueError('invalid cacheToken')
    prompt = request.get('prompt', '')
    if not isinstance(prompt, str) or len(prompt) > 500:
        raise ValueError('invalid prompt')
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

    def get(self, path, *, timeout=5, **query):
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
        with urllib.request.urlopen(req, timeout=timeout) as response:
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



class Engine:
    """One persistent daily Home plan; uncached prompt discovery uses the same curator."""
    def __init__(self, tmdb, cache, model, catalog, home_cache=None):
        from home_cache import HomeCache
        self.tmdb, self.cache, self.model, self.catalog = tmdb, cache, model, catalog
        self.home_cache = home_cache or HomeCache(cache, os.environ.get('HOME_TIME_ZONE', 'America/Vancouver'))
        self.home_lock = threading.Lock()

    @property
    def model_version(self):
        return self.model.version

    def _prepare(self, request, now):
        local = now.astimezone(ZoneInfo(request.get('timeZone', 'UTC')))
        now_ms = int(now.timestamp() * 1000)
        seeds, excluded, dislikes = signals(request, now_ms)
        references = {key(row): {'id': row['tmdbId'], 'mediaType': row['mediaType']} for row in seeds}
        references.update({key(row): {'id': row['tmdbId'], 'mediaType': row['mediaType']}
                           for row in request.get('feedback', []) if key(row) in dislikes})
        supplied = {key(row): row for row in request.get('candidates', [])}
        references.update(supplied)
        self.catalog.request(references)
        metadata = self.catalog.snapshot()
        verified = {key(row) for row in request.get('history', [])
                    if row.get('watchedMs', 0) >= 10_000 and
                    now_ms - 7 * 86_400_000 <= row.get('lastWatchedAt', 0) <= now_ms + 300_000}
        eligible, evaluated, dates, items = [], [], {}, {}
        for ident, value in metadata.items():
            kind = ident.split('-', 1)[0]
            allowed, first = release_policy(value, kind, local.date())
            if not allowed and first and first <= local.date() and ident in verified:
                allowed = True
            if ident in supplied:
                evaluated.append(ident)
                if first:
                    dates[ident] = first.isoformat()
            if allowed and not value.get('adult', False):
                eligible.append(ident)
                if ident not in excluded and value.get('poster_path'):
                    items[ident] = candidate(value, kind, first)
        ready = self.catalog.ready and all(key(seed) in metadata for seed in seeds[:20]) and (
            all(ident in metadata for ident in sorted(dislikes)[:10]))
        return dict(local=local, seeds=seeds, excluded=excluded, dislikes=dislikes,
                    metadata=metadata, items=items, ready=ready, evidence={
                        'eligibleKeys': eligible, 'evaluatedKeys': evaluated,
                        'publicReleaseDates': dates, 'completeEligibility': len(evaluated) == len(supplied)})

    @staticmethod
    def _description(ident, metadata):
        value = metadata[ident]
        words = value.get('keywords', {})
        return {'key': ident, 'title': value.get('title') or value.get('name'),
                'overview': (value.get('overview') or '')[:650],
                'genres': [g['name'] for g in value.get('genres', [])],
                'keywords': [g['name'] for g in words.get('keywords', words.get('results', []))[:12]],
                'year': str(value.get('release_date') or value.get('first_air_date') or '')[:4],
                'runtime': value.get('runtime'), 'rating': value.get('vote_average'),
                'votes': value.get('vote_count'), 'popularity': value.get('popularity'),
                'cast': [p['name'] for p in value.get('credits', {}).get('cast', [])[:4]],
                'directors': [p['name'] for p in value.get('credits', {}).get('crew', []) if p.get('job') == 'Director'][:2]}

    def _taste(self, state):
        # Only public descriptions and relative relevance leave the server.
        # No profile token, raw viewing durations, timestamps or credentials enter prompts.
        positive = [{**self._description(key(seed), state['metadata']), 'relevance': round(seed['weight'], 2)}
                    for seed in state['seeds'][:20] if key(seed) in state['metadata']]
        negative = [self._description(ident, state['metadata'])
                    for ident in sorted(state['dislikes'])[:10] if ident in state['metadata']]
        return {'positiveReferences': positive, 'negativeReferences': negative}

    def _shortlist(self, keys, metadata, seeds, limit, salt=''):
        # Acquisition only: retain broad coverage and seed-linked metadata. The model
        # makes the final theme/taste selection and ordering; no local model is involved.
        genres = set()
        for seed in seeds[:10]:
            genres.update(g['id'] for g in metadata.get(key(seed), {}).get('genres', []) if 'id' in g)
        def priority(ident):
            value = metadata[ident]
            overlap = len(genres & {g['id'] for g in value.get('genres', []) if 'id' in g})
            quality = float(value.get('vote_average') or 0) * min(1, math.log1p(value.get('vote_count') or 0) / 7)
            return (-overlap, -quality, hashlib.sha256((salt + ident).encode()).digest())
        ordered = sorted(keys, key=priority)
        if len(ordered) <= limit:
            return ordered
        # A quarter of the slate explores other genres/popularity bands.
        head = ordered[:limit * 3 // 4]
        rest = sorted(ordered[len(head):], key=lambda ident: hashlib.sha256((salt + ident).encode()).digest())
        return head + rest[:limit - len(head)]

    @staticmethod
    def _schema(pools, limits):
        return {'type': 'object', 'additionalProperties': False, 'required': list(pools),
                'properties': {ident: {'type': 'array', 'maxItems': limits.get(ident, 24),
                    'items': {'type': 'string', 'enum': keys}} for ident, keys in pools.items()}}

    @staticmethod
    def _validated(result, pools, limits):
        if not isinstance(result, dict) or set(result) != set(pools):
            raise RuntimeError('Invalid model shelf names')
        ranked = {}
        discarded = {'duplicate': 0, 'ineligible': 0, 'wrongType': 0}
        for ident, values in result.items():
            if not isinstance(values, list) or len(values) > limits.get(ident, 24):
                raise RuntimeError('Invalid model shelf size')
            allowed, seen = set(pools[ident]), set()
            selected = []
            for value in values:
                if not isinstance(value, str):
                    discarded['wrongType'] += 1
                    continue
                if value not in allowed:
                    discarded['ineligible'] += 1
                    continue
                if value in seen:
                    discarded['duplicate'] += 1
                    continue
                seen.add(value)
                selected.append(value)
            ranked[ident] = selected
        if any(discarded.values()):
            print(f'Model selection guard: {json.dumps(discarded)}', flush=True)
        return ranked

    def _home_plan(self, request, state, now):
        from themes import active_themes, eligible_for_theme, profile_context
        metadata, items, seeds = state['metadata'], state['items'], state['seeds']
        local = state['local']
        context = profile_context(request, metadata, local)
        # Cache selections for every time window of this local date, not just arrival.
        themes = {}
        for hour in (0, 3, 12, 16, 17, 22):
            for theme in active_themes(request, metadata, local.replace(hour=hour, minute=0, second=0)):
                themes[theme.id] = theme
        pools, labels, instructions, limits = {}, {}, {}, {'for-you': 40}
        for theme in sorted(themes.values(), key=lambda value: (-value.priority, value.id)):
            eligible = [ident for ident in items if eligible_for_theme(theme, metadata[ident], ident.split('-', 1)[0], context)]
            if len(eligible) >= 6:
                ident = 'dynamic-' + theme.id
                pools[ident] = self._shortlist(eligible, metadata, seeds, 42, local.date().isoformat())
                labels[ident], instructions[ident] = theme.title, theme.query
        because = sorted((seed for seed in seeds if seed.get('watched') and key(seed) in metadata),
                         key=lambda seed: seed.get('lastWatchedAt', 0), reverse=True)[:2]
        for seed in because:
            ident = 'because-' + key(seed)
            pools[ident] = self._shortlist(items, metadata, [seed], 90, local.date().isoformat())
            labels[ident] = 'Because you watched ' + (metadata[key(seed)].get('title') or metadata[key(seed)].get('name') or '')
            instructions[ident] = 'Recommend genuine thematic/style successors to this reference: ' + text(metadata[key(seed)])[:1500]
            limits[ident] = 18
        if len(items) >= 6:
            pools['for-you'] = self._shortlist(items, metadata, seeds, 240, local.date().isoformat())
            labels['for-you'] = 'For You'
            instructions['for-you'] = 'A varied mix of highly relevant discoveries. For a fresh profile, curate a broadly appealing mix without claiming personal knowledge.'
        if not pools:
            raise BlockingIOError('Catalog has too few eligible titles')
        keys = set(ident for values in pools.values() for ident in values)
        prompt = {'task': 'Generate a full day of Home shelves. Return ordered keys for each requested shelf; empty arrays are allowed. '
                          'Select up to 24 excellent matches per dynamic shelf, 18 per because shelf, 40 for For You. '
                          'These shelves can activate at different hours. Avoid excessive overlap, but do not sacrifice theme fit. '
                          'Never pad a shelf with weak matches. Comfort shelves must be low-stress and warm, not merely labeled Comedy.',
                  'taste': self._taste(state), 'shelves': {
                      ident: {'title': labels[ident], 'theme': instructions[ident], 'eligibleKeys': values}
                      for ident, values in pools.items()},
                  'candidates': [self._description(ident, metadata) for ident in sorted(keys)]}
        result = self.model.generate(prompt, self._schema(pools, limits), home=True)
        rankings = self._validated(result, pools, limits)
        if not any(rankings.values()):
            raise RuntimeError('Model returned no usable Home selections')
        return {'day': local.date().isoformat(), 'timeZone': request.get('timeZone', 'UTC'),
                'model': self.model.model,
                'modelVersion': self.model_version, 'generatedAt': int(now.timestamp() * 1000),
                'legacyClient': not bool(request.get('cacheToken')),
                'rankings': rankings, 'labels': labels}

    @staticmethod
    def _token(request):
        token = request.get('cacheToken', '')
        if token:
            return token
        # Older clients have no anonymous cache token. Their title IDs form a
        # bounded legacy cache identity; progress ticks never invalidate it.
        ids = {field: sorted({key(row) for row in request.get(field, [])}) for field in ('history', 'myList', 'feedback')}
        latest_feedback = {}
        for row in request.get('feedback', []):
            ident = key(row)
            if ident not in latest_feedback or row.get('updatedAt', 0) >= latest_feedback[ident].get('updatedAt', 0):
                latest_feedback[ident] = row
        ids['feedback'] = sorted((ident, row['value']) for ident, row in latest_feedback.items())
        return 'legacy-' + hashlib.sha256(json.dumps(ids, sort_keys=True).encode()).hexdigest()

    def recommend(self, request, now=None):
        from home_cache import QuotaExceeded
        from themes import active_themes, eligible_for_theme, profile_context
        validate(request)
        now = now or datetime.now(timezone.utc)
        state = self._prepare(request, now)
        token = self._token(request)
        with self.home_lock:
            plan = self.home_cache.get(token, now)
            if plan is None and request.get('cacheToken'):
                # An app update must not spend another generation just to adopt
                # the new anonymous profile token. Only migrate an actual old-client plan.
                legacy = self.home_cache.get(self._token({**request, 'cacheToken': ''}), now)
                if legacy and legacy.get('legacyClient'):
                    plan = legacy
                    self.home_cache.save(token, plan, now)
            fresh = plan and plan['day'] == state['local'].date().isoformat() and (
                plan.get('timeZone') == request.get('timeZone', 'UTC') and plan.get('modelVersion') == self.model_version)
            if not fresh and state['ready']:
                try:
                    self.home_cache.reserve('home', now, token)
                    replacement = self._home_plan(request, state, now)
                    self.home_cache.save(token, replacement, now)
                    plan = replacement
                except Exception:
                    # A failed refresh retains the last verified model output.
                    if plan is None:
                        raise
            if plan is None:
                if not state['ready']:
                    raise BlockingIOError('Public catalog is warming')
                raise QuotaExceeded('Home daily generation allowance reached')
        metadata, items, local = state['metadata'], state['items'], state['local']
        context = profile_context(request, metadata, local)
        used, dynamic, personal = set(), [], []
        for theme in active_themes(request, metadata, local):
            ident = 'dynamic-' + theme.id
            ranked = [value for value in plan['rankings'].get(ident, []) if value in items and value not in used and
                      eligible_for_theme(theme, metadata[value], value.split('-', 1)[0], context)]
            ranked = ranked[:16]
            if len(ranked) >= 6:
                dynamic.append({'id': ident, 'title': theme.title, 'items': [items[value] for value in ranked]})
                used.update(ranked)
                if len(dynamic) == MAX_DYNAMIC_SHELVES:
                    break
        # Reserve seed-specific shelves before For You; render For You first.
        for ident in plan['rankings']:
            if not ident.startswith('because-'):
                continue
            ranked = [value for value in plan['rankings'][ident] if value in items and value not in used][:12]
            if len(ranked) >= 4:
                personal.append({'id': ident, 'title': plan['labels'][ident], 'items': [items[value] for value in ranked]})
                used.update(ranked)
        ranked = [value for value in plan['rankings'].get('for-you', []) if value in items and value not in used][:24]
        if ranked:
            personal.insert(0, {'id': 'for-you', 'title': 'For You', 'items': [items[value] for value in ranked]})
        now_ms = int(now.timestamp() * 1000)
        next_hour = local.replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)
        valid_until = min(int(next_hour.timestamp() * 1000), now_ms + 3_600_000)
        return {'model': plan.get('model', plan['modelVersion'].rsplit(':', 1)[0]), 'modelVersion': plan['modelVersion'],
                'shelves': personal + dynamic, **state['evidence'], 'refreshedAt': now_ms,
                'generatedAt': plan['generatedAt'], 'validUntil': max(now_ms + 1000, valid_until),
                'catalogReady': state['ready']}

    def discover(self, request, now=None):
        validate(request)
        prompt = request.get('prompt', '').strip()
        if not prompt or len(prompt) > 500:
            raise ValueError('Discovery prompt is required')
        now = now or datetime.now(timezone.utc)
        self.catalog.expand_prompt(prompt)
        state = self._prepare(request, now)
        if not state['ready']:
            raise BlockingIOError('Public catalog is warming')
        metadata, items = state['metadata'], state['items']
        # Public word overlap expands the shortlist; only the LLM interprets and ranks.
        terms = set(prompt.lower().split()) - {'movies', 'shows', 'that', 'would', 'like', 'something', 'for', 'the', 'and', 'me', 'i'}
        targeted = sorted(items, key=lambda ident: (
            -sum(term in text(metadata[ident]).lower() for term in terms), ident))[:120]
        broad = self._shortlist(items, metadata, state['seeds'], 220, prompt)
        keys = list(dict.fromkeys(targeted + broad))
        if not keys:
            raise BlockingIOError('Catalog has too few eligible titles')
        pools, limits = {'results': keys}, {'results': 30}
        self.home_cache.reserve('discover', now)
        result = self.model.generate({'task': 'Interpret this prompt and select up to 30 genuinely fitting titles, ordered by theme and taste. '
                                             'If it specifies movies, omit shows; if shows, omit movies. An empty array is allowed.',
                                      'prompt': prompt, 'taste': self._taste(state),
                                      'candidates': [self._description(ident, metadata) for ident in keys]},
                                     self._schema(pools, limits))
        ranked = self._validated(result, pools, limits)['results']
        # No prompt response or raw prompt is written to disk or reused on another click.
        return {'model': self.model.model, 'prompt': prompt, 'items': [items[ident] for ident in ranked]}
