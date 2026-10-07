"""Indexed production pipeline policy tests, with no model download or network."""
import hashlib
import json
import math
import tempfile
import unittest
from datetime import datetime, timezone
from unittest.mock import patch
from zoneinfo import ZoneInfo

from catalog import CatalogIndex
from engine import ContentCache, Engine, key, validate
from service import Service
from themes import active_themes, eligible_for_theme, profile_context, theme_definitions


class CountingEncoder:
    cache_fingerprint = 'deterministic-test-v1'

    def __init__(self):
        self.documents = self.queries = 0

    @staticmethod
    def vector(value):
        values = [value / 255 for value in hashlib.sha256(value.encode()).digest()[:8]]
        length = math.sqrt(sum(value * value for value in values))
        return [value / length for value in values]

    def __call__(self, descriptions):
        self.documents += len(descriptions)
        return [self.vector(value) for value in descriptions]

    def encode_queries(self, queries, instruction):
        self.queries += len(queries)
        return [self.vector(instruction + value) for value in queries]


class NoNetworkTMDB:
    def detail(self, *_):
        raise AssertionError('indexed Home must not fetch TMDB details')

    def recommendations(self, *_):
        raise AssertionError('indexed Home must not fetch TMDB recommendations')


class SnapshotCatalog:
    ready = True
    version = 1

    def __init__(self, metadata, vectors):
        self.metadata, self.vectors = metadata, vectors
        self.requested = {}

    def request(self, references):
        self.requested.update(references)

    def snapshot(self):
        return dict(self.metadata), dict(self.vectors)


def title(ident, kind='movie', **fields):
    return {'id': ident, 'title': f'Title {ident}', 'name': f'Title {ident}',
            'overview': 'An interesting story', 'poster_path': '/poster.jpg',
            'release_date': '1995-01-01', 'first_air_date': '1995-01-01',
            'genres': [{'id': 35, 'name': 'Comedy'}], 'keywords': {'keywords': []},
            'vote_average': 8, 'vote_count': 300, 'popularity': 10, 'runtime': 95,
            'type': 'Miniseries' if kind == 'tv' else '', 'status': 'Ended',
            'number_of_seasons': 1, 'number_of_episodes': 6, 'episode_run_time': [30],
            **fields}


def corpus():
    values = {}
    for ident in range(1, 241):
        kind = 'tv' if ident > 180 else 'movie'
        fields = {}
        if ident <= 40:
            fields = {'genres': [{'id': 27, 'name': 'Horror'}], 'overview': 'Haunted ghosts on Halloween'}
        elif ident <= 80:
            fields = {'genres': [{'id': 10751, 'name': 'Family'}], 'overview': 'Friendly ghosts celebrate Halloween'}
        elif ident <= 120:
            fields = {'overview': 'A snowy winter Christmas celebration with Santa Claus'}
        values[f'{kind}-{ident}'] = title(ident, kind, **fields)
    values['movie-300'] = title(300, release_date='2027-01-01')
    values['movie-301'] = title(301, adult=True)
    values['movie-302'] = title(302, release_date='2026-10-05', release_dates={'results': [
        {'release_dates': [{'type': 1, 'release_date': '2026-10-05'}]},
    ]})
    return values


class DynamicEngineIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.cache = ContentCache(self.directory.name + '/content.sqlite3')
        self.addCleanup(self.cache.db.close)
        self.encoder = CountingEncoder()
        self.engine = Engine(NoNetworkTMDB(), self.cache, self.encoder)
        self.metadata = corpus()
        self.catalog = SnapshotCatalog(self.metadata, self.engine.vectors(self.metadata))
        self.engine.catalog = self.catalog
        self.engine.warm_themes()
        self.now = datetime(2026, 10, 6, 1, tzinfo=timezone.utc)  # Monday 18:00 Vancouver.

    def request(self):
        return {'timeZone': 'America/Vancouver',
                'candidates': [{'id': 81, 'mediaType': 'movie', 'title': 'Untrusted client caption'}],
                'feedback': [{'tmdbId': 82, 'mediaType': 'movie', 'value': 'like', 'updatedAt': 1}],
                'history': [], 'myList': []}

    def test_indexed_home_uses_broad_background_corpus_without_network_or_encoding(self):
        counts = (self.encoder.documents, self.encoder.queries)
        result = self.engine.recommend(self.request(), self.now)
        all_items = [item for shelf in result['shelves'] for item in shelf['items']]
        self.assertTrue(all_items)
        self.assertTrue(any(key(item) != 'movie-81' for item in all_items))
        self.assertTrue(all(item['title'].startswith('Title ') for item in all_items))
        self.assertEqual(counts, (self.encoder.documents, self.encoder.queries))
        self.assertEqual(result, self.engine.recommend(self.request(), self.now))
        self.assertEqual(counts, (self.encoder.documents, self.encoder.queries))
        self.assertIn('movie-82', self.catalog.requested)  # Seeds queued as public IDs.

    def test_whole_page_dedup_and_theme_bounds_keep_release_feedback_and_hard_guards(self):
        request = self.request()
        request['feedback'].append({'tmdbId': 83, 'mediaType': 'movie', 'value': 'dislike'})
        request['history'] = [{'tmdbId': 84, 'mediaType': 'movie', 'watchedMs': 3_600_000,
                              'lastWatchedAt': int(self.now.timestamp() * 1000)}]
        result = self.engine.recommend(request, self.now)
        keys = [key(item) for shelf in result['shelves'] for item in shelf['items']]
        self.assertEqual(len(keys), len(set(keys)))
        for ident in ('movie-82', 'movie-83', 'movie-84', 'movie-300', 'movie-301', 'movie-302'):
            self.assertNotIn(ident, keys)
        dynamic = [shelf for shelf in result['shelves'] if shelf['id'].startswith('dynamic-')]
        self.assertEqual(5, len(dynamic))
        local = self.now.astimezone(ZoneInfo(request['timeZone']))
        active = {theme.id: theme for theme in active_themes(request, self.metadata, local)}
        context = profile_context(request, self.metadata, local)
        for shelf in dynamic:
            self.assertGreaterEqual(len(shelf['items']), 6)
            theme = active[shelf['id'].removeprefix('dynamic-')]
            for item in shelf['items']:
                self.assertTrue(eligible_for_theme(theme, self.metadata[key(item)], item['mediaType'], context))
        self.assertNotIn('dynamic-christmas', [shelf['id'] for shelf in dynamic])

    def test_thin_theme_is_omitted_rather_than_filled_with_wrong_titles(self):
        metadata = {f'movie-{ident}': title(ident, genres=[{'id': 27, 'name': 'Horror'}])
                    for ident in range(1, 6)}
        self.engine.catalog = SnapshotCatalog(metadata, self.engine.vectors(metadata))
        result = self.engine.recommend({'timeZone': 'America/Vancouver'}, self.now)
        self.assertEqual([], result['shelves'])

    def test_calendar_change_never_reuses_halloween_or_early_christmas_shelves(self):
        october = self.engine.recommend({}, datetime(2026, 10, 31, 23, 59, tzinfo=timezone.utc))
        november = self.engine.recommend({}, datetime(2026, 11, 1, tzinfo=timezone.utc))
        december = self.engine.recommend({}, datetime(2026, 12, 20, 18, tzinfo=timezone.utc))
        self.assertIn('dynamic-halloween', [shelf['id'] for shelf in october['shelves']])
        self.assertFalse(any(shelf['id'] in ('dynamic-halloween', 'dynamic-spooky-not-scary', 'dynamic-christmas')
                             for shelf in november['shelves']))
        self.assertIn('dynamic-christmas', [shelf['id'] for shelf in december['shelves']])
        christmas = next(shelf for shelf in december['shelves'] if shelf['id'] == 'dynamic-christmas')
        self.assertTrue(all(81 <= item['id'] <= 120 for item in christmas['items']))

    def test_missing_positive_or_negative_seed_marks_ready_corpus_incomplete(self):
        for value in ('like', 'dislike'):
            request = {'feedback': [{'tmdbId': 999, 'mediaType': 'movie', 'value': value}]}
            result = self.engine.recommend(request, self.now)
            self.assertFalse(result['catalogReady'])
            self.assertIn('movie-999', self.catalog.requested)

    def test_indexed_response_and_disk_never_persist_profile_signals(self):
        result = self.engine.recommend(self.request(), self.now)
        encoded = json.dumps(result)
        for field in ('watchedMs', 'lastWatchedAt', 'profileId', 'apiKey', 'feedback'):
            self.assertNotIn(field, encoded)
        self.assertEqual([], list(self.cache.db.execute('SELECT data FROM content')))
        for row in self.cache.db.execute('SELECT data FROM vectors'):
            self.assertIsInstance(json.loads(row[0]), list)

    def test_season_episode_timezone_validation_rejects_bad_values(self):
        for zone in ('Mars/Timezone', '../etc/passwd', '', 1):
            with self.assertRaises(ValueError):
                validate({'timeZone': zone})
        for field in ('season', 'episode'):
            for value in (-1, 100001, True, 1.5, '2'):
                with self.assertRaises(ValueError):
                    validate({'history': [{'tmdbId': 1, 'mediaType': 'tv', field: value}]})
        self.assertEqual('America/Vancouver', validate({'timeZone': 'America/Vancouver'})['timeZone'])

    def test_service_cache_expires_at_device_local_boundary_with_identical_request(self):
        engine = self.engine

        class ClockEngine:
            catalog = engine.catalog
            calls = 0

            def recommend(self, request):
                self.calls += 1
                return engine.recommend(request, datetime.fromtimestamp(clock[0], timezone.utc))

        clock = [datetime(2026, 11, 1, 6, 59, tzinfo=timezone.utc).timestamp()]
        wrapped = ClockEngine()
        service = Service(wrapped)
        request = {'timeZone': 'America/Vancouver'}
        with patch('service.time.time', side_effect=lambda: clock[0]):
            before = service.recommend(request)
            self.assertIn('dynamic-halloween', [shelf['id'] for shelf in before['shelves']])
            self.assertIs(before, service.recommend(request))
            self.assertEqual(1, wrapped.calls)
            clock[0] = datetime(2026, 11, 1, 7, tzinfo=timezone.utc).timestamp()
            after = service.recommend(request)
            self.assertEqual(2, wrapped.calls)
            self.assertNotIn('dynamic-halloween', [shelf['id'] for shelf in after['shelves']])

    def test_warmed_credit_themes_never_run_gpu_inference_on_home(self):
        for value in self.metadata.values():
            value['credits'] = {'crew': [{'id': 123, 'name': 'Director', 'job': 'Director'}],
                                'cast': [{'id': 456, 'name': 'Actor'}]}
        request = {'feedback': [
            {'tmdbId': ident, 'mediaType': 'movie', 'value': 'like'} for ident in (121, 122)
        ], 'timeZone': 'America/Vancouver'}
        counts = (self.encoder.documents, self.encoder.queries)
        result = self.engine.recommend(request, datetime(2026, 4, 1, 17, tzinfo=timezone.utc))
        ids = [shelf['id'] for shelf in result['shelves']]
        self.assertIn('dynamic-more-director-123', ids)
        self.assertIn('dynamic-starring-actor-456', ids)
        self.assertEqual(counts, (self.encoder.documents, self.encoder.queries))

    def test_six_holiday_titles_reserved_before_taste_shelves_consume_them(self):
        metadata = {f'movie-{ident}': title(ident) for ident in range(1, 101)}
        for ident in range(1, 7):
            metadata[f'movie-{ident}']['genres'] = [{'id': 27, 'name': 'Horror'}]
        metadata['movie-999'] = title(999)
        halloween = next(theme for theme in theme_definitions() if theme.id == 'halloween')
        vectors = {ident: list(self.engine.theme_vectors[halloween.query]) for ident in metadata}
        self.engine.catalog = SnapshotCatalog(metadata, vectors)
        request = {'timeZone': 'America/Vancouver', 'history': [
            {'tmdbId': 999, 'mediaType': 'movie', 'watchedMs': 3_600_000,
             'lastWatchedAt': int(self.now.timestamp() * 1000)},
        ]}
        result = self.engine.recommend(request, self.now)
        ids = [shelf['id'] for shelf in result['shelves']]
        self.assertEqual('for-you', ids[0])
        self.assertIn('because-movie-999', ids)
        holiday = next(shelf for shelf in result['shelves'] if shelf['id'] == 'dynamic-halloween')
        self.assertEqual(set(range(1, 7)), {item['id'] for item in holiday['items']})
        all_keys = [key(item) for shelf in result['shelves'] for item in shelf['items']]
        self.assertEqual(len(all_keys), len(set(all_keys)))

    def test_public_discover_worker_broadens_index_without_client_or_seed_recommendations(self):
        public = {f'movie-{ident}': title(ident, genres=[{'id': 27, 'name': 'Horror'}])
                  for ident in range(1, 41)}

        class DiscoverTMDB(NoNetworkTMDB):
            def get(self, path, **query):
                if path == '/trending/all/week':
                    return {'results': [{'id': ident, 'media_type': 'movie'} for ident in range(1, 41)]}
                return {'results': []}

            def detail(self, kind, ident):
                return public[f'{kind}-{ident}']

        index = CatalogIndex(DiscoverTMDB(), self.cache, self.engine.vectors,
                             fingerprint=self.encoder.cache_fingerprint, target=40, request_interval=0)
        self.assertTrue(index.refresh_once())
        for _ in range(5):
            self.assertTrue(index.refresh_once())
        self.assertTrue(index.ready)
        self.engine.catalog = index
        index.tmdb = NoNetworkTMDB()
        count = self.encoder.documents
        result = self.engine.recommend({}, self.now)
        holiday = next(shelf for shelf in result['shelves'] if shelf['id'] == 'dynamic-halloween')
        self.assertGreaterEqual(len(holiday['items']), 6)
        self.assertEqual(count, self.encoder.documents)

    def test_next_obsession_requires_meaningfully_watched_verified_final_episode(self):
        now = datetime(2026, 4, 1, 17, tzinfo=timezone.utc)
        show = self.metadata['tv-181']
        show.update(status='Ended', last_episode_to_air={'season_number': 2, 'episode_number': 8})
        history = {'tmdbId': 181, 'mediaType': 'tv', 'watchedMs': 1_800_000,
                   'latestWatchedMs': 1_800_000,
                   'positionMs': 2_850_000, 'durationMs': 3_000_000,
                   'lastWatchedAt': int(now.timestamp() * 1000), 'season': 2, 'episode': 8}
        request = {'timeZone': 'America/Vancouver', 'history': [history]}
        result = self.engine.recommend(request, now)
        self.assertIn('dynamic-next-obsession', [shelf['id'] for shelf in result['shelves']])
        for changed in ({'episode': 7}, {'season': 1}, {'positionMs': 1_000_000},
                        {'watchedMs': 0, 'latestWatchedMs': 0}, {'latestWatchedMs': 10_000}):
            request['history'] = [{**history, **changed}]
            result = self.engine.recommend(request, now)
            self.assertNotIn('dynamic-next-obsession', [shelf['id'] for shelf in result['shelves']], changed)

    def christmas_index(self, strong_count):
        metadata = {f'movie-{ident}': title(ident, overview='A Christmas celebration with Santa Claus')
                    for ident in range(1, 13)}
        metadata['movie-999'] = title(999)
        strong = [1.0, 0.0] + [0.0] * 6
        weak = [0.0, 1.0] + [0.0] * 6
        vectors = {ident: list(strong if int(ident.split('-')[1]) <= strong_count else weak)
                   for ident in metadata}
        self.engine.catalog = SnapshotCatalog(metadata, vectors)
        christmas = next(theme for theme in theme_definitions() if theme.id == 'christmas')
        self.engine.theme_vectors[christmas.query] = list(strong)
        return datetime(2026, 12, 20, 18, tzinfo=timezone.utc)

    def test_keyword_eligible_christmas_titles_without_semantic_matches_cannot_fill_a_shelf(self):
        now = self.christmas_index(strong_count=0)
        result = self.engine.recommend({'timeZone': 'America/Vancouver'}, now)
        self.assertNotIn('dynamic-christmas', [shelf['id'] for shelf in result['shelves']])

    def test_personal_taste_cannot_promote_orthogonal_holiday_titles_over_strong_theme_matches(self):
        now = self.christmas_index(strong_count=6)
        request = {'timeZone': 'America/Vancouver',
                   'feedback': [{'tmdbId': 999, 'mediaType': 'movie', 'value': 'like'}]}
        result = self.engine.recommend(request, now)
        christmas = next(shelf for shelf in result['shelves'] if shelf['id'] == 'dynamic-christmas')
        self.assertEqual(set(range(1, 7)), {item['id'] for item in christmas['items']})
        personal = next(shelf for shelf in result['shelves'] if shelf['id'] == 'for-you')
        self.assertEqual(set(range(7, 13)), {item['id'] for item in personal['items']})

    def test_too_few_strong_holiday_matches_stays_hidden_after_semantic_filtering(self):
        now = self.christmas_index(strong_count=5)
        result = self.engine.recommend({'timeZone': 'America/Vancouver'}, now)
        self.assertNotIn('dynamic-christmas', [shelf['id'] for shelf in result['shelves']])


if __name__ == '__main__':
    unittest.main()
