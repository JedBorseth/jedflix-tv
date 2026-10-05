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

MODEL = 'BAAI/bge-small-en-v1.5'
REVISION = '5c38ec7c405ec4b44b94cc5a9bb96e735b38267a'
MAX_CANDIDATES = 300
MAX_SIGNALS = 200


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
            for number in ('watchedMs', 'positionMs', 'durationMs', 'lastWatchedAt', 'updatedAt'):
                value = row.get(number, 0)
                if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 10**15:
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


class TMDB:
    def __init__(self, api_key, cache, base='https://api.themoviedb.org/3'):
        self.api_key, self.cache, self.base = api_key, cache, base

    def get(self, path, **query):
        cached = self.cache.get('content', path, 24 * 3600)
        if cached is not None:
            return cached
        query.update(api_key=self.api_key, language='en-US')
        req = urllib.request.Request(self.base + path + '?' + urllib.parse.urlencode(query),
                                     headers={'Accept': 'application/json'})
        with urllib.request.urlopen(req, timeout=5) as response:
            data = json.load(response)
        self.cache.put('content', path, data)
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
    remaining, chosen = set(pool), list(selected)
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
    def __init__(self, tmdb, cache, encoder):
        self.tmdb, self.cache, self.encoder = tmdb, cache, encoder
        self.inference_lock = threading.Lock()

    def vectors(self, metadata):
        found, missing = {}, {}
        for ident, value in metadata.items():
            description = text(value)
            digest = hashlib.sha256((MODEL + REVISION + description).encode()).hexdigest()
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

    def recommend(self, request, now=None):
        validate(request)
        now = now or datetime.now(timezone.utc)
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
        for seed in because_seeds:
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
        deadline = time.monotonic() + 35
        def fetch(pair):
            ident, row = pair
            if time.monotonic() > deadline:
                return ident, None
            try:
                return ident, self.tmdb.detail(row['mediaType'], row['id'])
            except Exception:
                # An unverified candidate isn't silently asserted unavailable.
                return ident, None
        with ThreadPoolExecutor(max_workers=6) as workers:
            metadata = {ident: value for ident, value in workers.map(fetch, references.items()) if value is not None}
        evaluated, eligible, items, public_dates = [], [], {}, {}
        for ident, value in metadata.items():
            kind = references[ident]['mediaType']
            allowed, first = release_policy(value, kind, now.date())
            if not allowed and first and first <= now.date() and ident in verified_streams:
                allowed = True
            if ident in supplied:
                evaluated.append(ident)
                if first:
                    public_dates[ident] = first.isoformat()
            if allowed and not value.get('adult', False):
                eligible.append(ident)
                if ident not in excluded and value.get('poster_path'):
                    items[ident] = candidate(value, kind, first)
        if not metadata and references:
            raise RuntimeError('TMDB metadata unavailable')
        # Only eligible candidates and profile seeds need inference.
        vector_metadata = {ident: value for ident, value in metadata.items()
                           if ident in items or ident in {key(s) for s in seeds + negative_seeds}}
        vectors = self.vectors(vector_metadata)
        positive = [(key(s), s['weight']) for s in seeds if key(s) in vectors]
        # Several distinct taste anchors rather than a single vector that averages genres away.
        interests = []
        for ident, weight in positive:
            if len(interests) >= 8:
                break
            if all(cosine(vectors[ident], vectors[other]) < 0.88 for other, _ in interests):
                interests.append((ident, weight))
        scores = {}
        for ident in items:
            sims = [cosine(vectors[ident], vectors[seed]) * (0.8 + 0.2 * min(1, weight / 2)) for seed, weight in interests]
            taste = max(sims, default=0)
            quality = min(1, (items[ident].get('rating') or 0) / 10)
            negative = max((max(0, cosine(vectors[ident], vectors[other]) - 0.65)
                            for other in dislikes if other in vectors), default=0)
            scores[ident] = 0.85 * taste + 0.15 * quality - 0.5 * negative
        shelves, used, because_shelves = [], set(), []
        # Reserve a few seed-specific discoveries before For You consumes the pool.
        # The rendered shelf order still puts For You first.
        because_limit = min(12, max(0, (len(items) - 12) // max(1, len(because_seeds))))
        for seed in because_seeds:
            ident = key(seed)
            if ident not in vectors or because_limit < 4:
                continue
            available = set(items) - used
            seed_scores = {other: cosine(vectors[other], vectors[ident]) + (0.05 if other in related.get(ident, set()) else 0)
                           for other in available}
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
        shelves.extend(because_shelves)
        return {'model': MODEL, 'modelRevision': REVISION, 'shelves': shelves,
                'eligibleKeys': eligible, 'evaluatedKeys': evaluated,
                'publicReleaseDates': public_dates,
                'completeEligibility': len(evaluated) == len(supplied), 'refreshedAt': now_ms}
