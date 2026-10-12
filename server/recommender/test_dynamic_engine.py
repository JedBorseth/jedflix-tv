"""Cloud output, persistence, quotas and policy tested through the production interface."""
import json
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

from engine import ContentCache, Engine, key, validate
from home_cache import HomeCache, QuotaExceeded
from model import MODEL, OpenRouter


def title(ident, kind='movie', **fields):
    return {'id': ident, 'title': f'Title {ident}', 'name': f'Title {ident}',
            'overview': 'A warm playful comedy adventure', 'poster_path': '/poster.jpg',
            'release_date': '1995-01-01', 'first_air_date': '1995-01-01',
            'genres': [{'id': 35, 'name': 'Comedy'}], 'keywords': {'keywords': []},
            'vote_average': 8, 'vote_count': 300, 'popularity': 10, 'runtime': 95,
            'type': 'Miniseries' if kind == 'tv' else '', 'status': 'Ended',
            'number_of_seasons': 1, 'number_of_episodes': 6, 'episode_run_time': [30], **fields}


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
        {'release_dates': [{'type': 1, 'release_date': '2026-10-05'}]}]})
    return values


class Snapshot:
    ready = True
    version = 1

    def __init__(self, metadata):
        self.metadata = metadata
        self.requested = {}

    def request(self, references):
        self.requested.update(references)

    def snapshot(self):
        return dict(self.metadata)

    def expand_prompt(self, prompt):
        pass


class NoNetwork:
    def __getattr__(self, name):
        raise AssertionError('Home may not perform TMDB network work')


class Curator:
    model = MODEL
    version = MODEL + ':test-v1'

    def __init__(self):
        self.calls = []
        self.fail = False
        self.invalid = False
        self.for_you = None

    def generate(self, prompt, schema, *, home=False):
        self.calls.append((prompt, home))
        if self.fail:
            raise RuntimeError('upstream failed')
        result = {ident: list(reversed(shape['items']['enum']))[:shape['maxItems']]
                  for ident, shape in schema['properties'].items()}
        if self.for_you is not None and 'for-you' in result:
            result['for-you'] = self.for_you
        if self.invalid:
            result[next(iter(result))] = ['movie-999999']
        return result


class CloudDiscoveryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.cache = ContentCache(self.directory.name + '/content.sqlite3')
        self.addCleanup(self.cache.db.close)
        self.model = Curator()
        self.catalog = Snapshot(corpus())
        self.home = HomeCache(self.cache)
        self.engine = Engine(NoNetwork(), self.cache, self.model, self.catalog, self.home)
        self.now = datetime(2026, 10, 6, 1, tzinfo=timezone.utc)
        self.request = {'timeZone': 'America/Vancouver', 'cacheToken': 'anonymous-profile-one',
                        'history': [], 'feedback': [], 'myList': []}

    def test_home_visits_progress_changes_and_daypart_switches_reuse_one_generation(self):
        morning = datetime(2026, 5, 5, 16, tzinfo=timezone.utc)
        first = self.engine.recommend(self.request, morning)
        self.assertNotIn('dynamic-after-long-day', [s['id'] for s in first['shelves']])
        evening = morning + timedelta(hours=9)
        changed = {**self.request, 'history': [{'tmdbId': 170, 'mediaType': 'movie', 'watchedMs': 3_000_000}]}
        second = self.engine.recommend(changed, evening)
        self.assertIn('dynamic-after-long-day', [s['id'] for s in second['shelves']])
        self.assertEqual(first['generatedAt'], second['generatedAt'])
        self.assertEqual(1, len(self.model.calls))
        all_keys = [key(t) for s in second['shelves'] for t in s['items']]
        self.assertNotIn('movie-170', all_keys)
        self.assertEqual(len(all_keys), len(set(all_keys)))

    def test_restart_reuses_persisted_plan_and_global_budget_across_profiles(self):
        first = self.engine.recommend(self.request, self.now)
        restarted = Engine(NoNetwork(), self.cache, self.model, self.catalog)
        self.assertEqual(first['generatedAt'], restarted.recommend(self.request, self.now)['generatedAt'])
        restarted.recommend({**self.request, 'cacheToken': 'anonymous-profile-two'}, self.now)
        with self.assertRaises(QuotaExceeded):
            restarted.recommend({**self.request, 'cacheToken': 'anonymous-profile-three'}, self.now)
        self.assertEqual(2, len(self.model.calls))

    def test_app_update_adopts_old_client_plan_without_spending_another_generation(self):
        legacy_request = {**self.request, 'cacheToken': ''}
        first = self.engine.recommend(legacy_request, self.now)
        upgraded = self.engine.recommend(self.request, self.now)
        self.assertEqual(first['generatedAt'], upgraded['generatedAt'])
        self.assertEqual(1, len(self.model.calls))
        self.assertIsNotNone(self.home.get(self.request['cacheToken'], self.now))

    def test_adopting_legacy_cache_does_not_extend_seven_day_stale_lifetime(self):
        generated = self.now - timedelta(days=6)
        plan = {'generatedAt': int(generated.timestamp() * 1000)}
        self.home.save('legacy-token', plan, generated)
        self.home.save('new-client-token', self.home.get('legacy-token', self.now), self.now)
        self.assertIsNone(self.home.get('new-client-token', self.now + timedelta(days=2)))

    def test_model_order_is_preserved_and_no_local_ranking_replaces_it(self):
        self.model.for_you = ['movie-150', 'movie-142', 'movie-169', 'movie-153', 'movie-180', 'movie-155']
        result = self.engine.recommend(self.request, datetime(2026, 5, 5, 16, tzinfo=timezone.utc))
        shelf = next(s for s in result['shelves'] if s['id'] == 'for-you')
        self.assertEqual(self.model.for_you, [key(t) for t in shelf['items']])

    def test_theme_policy_dedup_dislikes_watched_and_release_filters_apply_to_model_output(self):
        request = {**self.request, 'feedback': [{'tmdbId': 83, 'mediaType': 'movie', 'value': 'dislike'}],
                   'history': [{'tmdbId': 84, 'mediaType': 'movie', 'watchedMs': 3_600_000}]}
        result = self.engine.recommend(request, self.now)
        keys = [key(t) for shelf in result['shelves'] for t in shelf['items']]
        self.assertTrue(keys)
        self.assertEqual(len(keys), len(set(keys)))
        for ident in ('movie-83', 'movie-84', 'movie-300', 'movie-301', 'movie-302'):
            self.assertNotIn(ident, keys)
        self.assertLessEqual(len([s for s in result['shelves'] if s['id'].startswith('dynamic-')]), 5)
        bedtime = next((s for s in result['shelves'] if s['id'] == 'dynamic-done-before-bed'), None)
        if bedtime:
            self.assertTrue(all(45 <= self.catalog.metadata[key(t)]['runtime'] < 100 for t in bedtime['items']))

    def test_failed_generation_counts_and_cools_down_without_spending_second_call(self):
        self.model.fail = True
        with self.assertRaises(RuntimeError):
            self.engine.recommend(self.request, self.now)
        with self.assertRaises(QuotaExceeded):
            self.engine.recommend(self.request, self.now + timedelta(minutes=1))
        self.assertEqual(1, len(self.model.calls))
        self.model.fail = False
        self.engine.recommend(self.request, self.now + timedelta(hours=1))
        self.assertEqual(2, len(self.model.calls))

    def test_failed_refresh_retains_model_output_but_expired_calendar_shelves_disappear(self):
        before = datetime(2026, 11, 1, 6, 30, tzinfo=timezone.utc)
        result = self.engine.recommend(self.request, before)
        self.assertIn('dynamic-halloween', [s['id'] for s in result['shelves']])
        self.model.fail = True
        after = self.engine.recommend(self.request, before + timedelta(hours=1))
        self.assertNotIn('dynamic-halloween', [s['id'] for s in after['shelves']])
        self.assertEqual(result['generatedAt'], after['generatedAt'])

    def test_failed_model_change_reports_the_model_that_generated_cached_output(self):
        first = self.engine.recommend(self.request, self.now)
        self.model.model = 'other/model:free'
        self.model.version = 'other/model:free:v2'
        self.model.fail = True
        after = self.engine.recommend(self.request, self.now)
        self.assertEqual(first['model'], after['model'])
        self.assertEqual(first['modelVersion'], after['modelVersion'])

    def test_one_bad_model_selection_does_not_destroy_other_valid_shelves(self):
        self.model.invalid = True
        result = self.engine.recommend(self.request, self.now)
        plan = self.home.get(self.request['cacheToken'], self.now)
        self.assertTrue(result['shelves'])
        self.assertNotIn('movie-999999', json.dumps(plan))
        self.assertEqual(1, len(self.model.calls))

    def test_provider_selection_errors_are_filtered_without_reordering_valid_output(self):
        pools = {'results': ['movie-1', 'movie-2', 'movie-3']}
        reply = {'results': ['movie-3', 'movie-3', 'movie-999', 1, 'movie-1']}
        self.assertEqual({'results': ['movie-3', 'movie-1']}, Engine._validated(reply, pools, {'results': 8}))

    def test_empty_home_plan_is_not_cached_as_a_success(self):
        self.model.generate = lambda prompt, schema, **kwargs: {name: [] for name in schema['properties']}
        with self.assertRaises(RuntimeError):
            self.engine.recommend(self.request, self.now)
        self.assertIsNone(self.home.get(self.request['cacheToken'], self.now))

    def test_raw_signals_and_tokens_do_not_enter_model_prompts_or_cached_plan_data(self):
        request = {**self.request, 'history': [{'tmdbId': 170, 'mediaType': 'movie',
                   'watchedMs': 3_600_000, 'lastWatchedAt': int(self.now.timestamp()*1000)}]}
        self.engine.recommend(request, self.now)
        prompt = json.dumps(self.model.calls[0][0])
        stored = self.cache.db.execute('SELECT data FROM home_plans').fetchone()[0]
        for field in ('watchedMs', 'lastWatchedAt', 'cacheToken', 'anonymous-profile-one', 'apiKey', 'profileId'):
            self.assertNotIn(field, prompt)
            self.assertNotIn(field, stored)

    def test_prompt_discovery_is_always_live_and_does_not_write_a_home_or_prompt_cache(self):
        request = {**self.request, 'prompt': 'Unusual adventure movies'}
        first = self.engine.discover(request, self.now)
        second = self.engine.discover(request, self.now)
        self.assertEqual(first, second)
        self.assertEqual(2, len(self.model.calls))
        self.assertTrue(all(not home for _, home in self.model.calls))
        self.assertEqual([], list(self.cache.db.execute('SELECT * FROM home_plans')))
        self.assertNotIn('Unusual adventure movies', repr(list(self.cache.db.execute('SELECT * FROM content'))))

    def test_search_budget_leaves_home_capacity_and_survives_restart(self):
        for _ in range(40):
            self.home.reserve('discover', self.now)
        with self.assertRaises(QuotaExceeded):
            HomeCache(self.cache).reserve('discover', self.now)
        self.engine.recommend(self.request, self.now)
        self.assertEqual(1, len(self.model.calls))

    def test_atomic_global_home_limit_under_concurrent_claims(self):
        def claim(index):
            try:
                HomeCache(self.cache).reserve('home', self.now, f'profile-{index}')
                return True
            except QuotaExceeded:
                return False
        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(claim, range(8)))
        self.assertEqual(2, sum(results))

    def test_catalog_warmup_does_not_spend_calls(self):
        self.catalog.ready = False
        with self.assertRaises(BlockingIOError):
            self.engine.recommend(self.request, self.now)
        self.assertEqual([], self.model.calls)
        self.assertEqual([], list(self.cache.db.execute('SELECT * FROM model_calls')))

    def test_missing_taste_reference_is_enriched_before_spending_daily_home_call(self):
        for value in ('like', 'dislike'):
            request = {**self.request, 'feedback': [{'tmdbId': 999, 'mediaType': 'movie', 'value': value}]}
            with self.assertRaises(BlockingIOError):
                self.engine.recommend(request, self.now)
        self.assertIn('movie-999', self.catalog.requested)
        self.assertEqual([], self.model.calls)

    def test_recent_successful_playback_can_verify_home_offer_but_never_future_release(self):
        timestamp = int(self.now.timestamp()*1000)
        self.catalog.metadata['movie-400'] = title(400, release_date='2026-10-01')
        self.catalog.metadata['movie-401'] = title(401, release_date='2027-01-01')
        request = {**self.request, 'candidates': [{'id': ident, 'mediaType': 'movie'} for ident in (400,401)],
                   'history': [{'tmdbId': ident, 'mediaType': 'movie', 'watchedMs': 10_000,
                                'lastWatchedAt': timestamp} for ident in (400,401)]}
        result = self.engine.recommend(request,self.now)
        self.assertIn('movie-400', result['eligibleKeys'])
        self.assertNotIn('movie-401', result['eligibleKeys'])

    def test_prompt_and_cache_token_validation(self):
        for request in ({'cacheToken':'../profile'}, {'cacheToken':True}, {'prompt':'a'*501}, {'prompt':None}):
            with self.assertRaises(ValueError):
                validate(request)


class RemoteClientTests(unittest.TestCase):
    def test_missing_key_fails_instead_of_loading_a_local_or_fake_model(self):
        with patch.dict('os.environ', {}, clear=True):
            with self.assertRaises(RuntimeError):
                OpenRouter()

    def test_no_paid_model_can_be_configured_accidentally(self):
        with self.assertRaises(ValueError):
            OpenRouter(api_key='test-only', model='nvidia/nemotron-3-super-120b-a12b')

    def test_search_stops_at_reserved_home_allowance(self):
        client = OpenRouter(api_key='test-only')
        calls=[]
        def remote(path, payload=None):
            calls.append(path)
            return {'data': {'free_model_daily_requests': {'remaining':2}}}
        client._request=remote
        with self.assertRaises(QuotaExceeded):
            client.generate({}, {})
        self.assertEqual(['/key'], calls)

    def test_provider_receives_zero_price_ceiling_and_no_paid_fallback(self):
        client=OpenRouter(api_key='test-only')
        captured=[]
        def remote(path,payload=None):
            if path=='/key':
                return {'data': {'free_model_daily_requests': {'remaining':10}}}
            captured.append(payload)
            return {'choices':[{'message':{'content':'{"results":[]}'}}]}
        client._request=remote
        self.assertEqual({'results':[]},client.generate({},{}))
        self.assertEqual({'prompt':0,'completion':0},captured[0]['provider']['max_price'])
        self.assertNotIn('models',captured[0])


if __name__ == '__main__':
    unittest.main()
