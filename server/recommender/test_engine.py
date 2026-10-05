import json
import math
import tempfile
import unittest
from datetime import date, datetime, timezone

from engine import (ContentCache, Engine, MAX_CANDIDATES, candidate, diverse_rank,
                    key, release_policy, signals, validate)
from service import Service

TODAY = date(2026, 10, 5)
NOW = datetime(2026, 10, 5, tzinfo=timezone.utc)


def movie(releases=(), primary='2026-10-01', offers=None):
    return {'release_date': primary, 'release_dates': {'results': [
        {'iso_3166_1': region, 'release_dates': [{'type': kind, 'release_date': released}]} for region, kind, released in releases]},
        'watch/providers': {'results': offers or {}}}


class ReleasePolicyTests(unittest.TestCase):
    def test_upcoming_rejected_even_with_provider_offer(self):
        data = movie([('CA', 3, '2026-11-05')], offers={'CA': {'buy': [{}]}})
        self.assertFalse(release_policy(data, 'movie', TODAY)[0])

    def test_fresh_theatrical_rejected_old_theatrical_allowed(self):
        self.assertFalse(release_policy(movie([('US', 3, '2026-10-01')]), 'movie', TODAY)[0])
        self.assertTrue(release_policy(movie([('US', 3, '2026-09-05')]), 'movie', TODAY)[0])

    def test_public_digital_and_physical_tv_override_holdback(self):
        for kind in (4, 5, 6):
            self.assertTrue(release_policy(movie([('US', 3, '2026-10-01'), ('GB', kind, '2026-10-03')]), 'movie', TODAY)[0])
        self.assertFalse(release_policy(movie([('US', 3, '2026-10-01'), ('US', 4, '2026-10-06')]), 'movie', TODAY)[0])

    def test_premiere_does_not_make_future_public_movie_eligible(self):
        self.assertFalse(release_policy(movie([('US', 1, '2026-08-01'), ('US', 3, '2026-11-01')]), 'movie', TODAY)[0])
        self.assertFalse(release_policy(movie([('US', 1, '2026-08-01')], primary='2026-11-01'), 'movie', TODAY)[0])
        self.assertFalse(release_policy(movie([('US', 1, '2026-08-01')], primary='2026-08-01'), 'movie', TODAY)[0])

    def test_earlier_public_region_wins_over_future_primary(self):
        eligible, first = release_policy(movie([('FR', 3, '2026-08-01'), ('CA', 3, '2026-11-01')], primary='2026-11-01'), 'movie', TODAY)
        self.assertTrue(eligible)
        self.assertEqual(first, date(2026, 8, 1))

    def test_ca_offer_or_us_fallback_allows_fresh_movie(self):
        for offers in ({'CA': {'rent': [{}]}}, {'CA': {'link': 'x'}, 'US': {'flatrate': [{}]}}):
            self.assertTrue(release_policy(movie([('CA', 3, '2026-10-01')], offers=offers), 'movie', TODAY)[0])

    def test_missing_dates_fail_closed_and_shows_have_no_theatre_delay(self):
        self.assertFalse(release_policy({}, 'movie', TODAY)[0])
        self.assertTrue(release_policy({'first_air_date': '2026-10-05'}, 'tv', TODAY)[0])
        self.assertFalse(release_policy({'first_air_date': '2026-10-06'}, 'tv', TODAY)[0])


class SignalsTests(unittest.TestCase):
    def test_pauses_and_brief_starts_do_not_seed(self):
        request = {'history': [{'tmdbId': 1, 'mediaType': 'movie', 'watchedMs': 50_000, 'positionMs': 50_000}]}
        seeds, excluded, _ = signals(request, 100)
        self.assertEqual(seeds, [])
        self.assertEqual(excluded, set())

    def test_brief_actual_playback_with_large_seek_is_not_a_watched_seed(self):
        request = {'history': [{'tmdbId': 1, 'mediaType': 'movie', 'watchedMs': 10_000,
                               'positionMs': 5_000_000, 'durationMs': 5_100_000}]}
        seeds, excluded, _ = signals(request, 100)
        self.assertEqual(seeds, [])
        self.assertEqual(excluded, set())
        request['feedback'] = [{'tmdbId': 1, 'mediaType': 'movie', 'value': 'like'}]
        seeds, _, _ = signals(request, 100)
        self.assertEqual(seeds[0]['weight'], 2)
        self.assertFalse(seeds[0].get('watched', False))

    def test_explicit_like_seeds_taste_but_excludes_the_liked_title(self):
        request = {'feedback': [{'tmdbId': 1, 'mediaType': 'movie', 'value': 'like'}]}
        seeds, excluded, dislikes = signals(request, 100)
        self.assertEqual(seeds[0]['tmdbId'], 1)
        self.assertEqual(excluded, {'movie-1'})
        self.assertEqual(dislikes, set())

    def test_latest_feedback_and_existing_history(self):
        request = {'history': [{'tmdbId': 1, 'mediaType': 'movie', 'positionMs': 2_000_000}],
                   'feedback': [{'tmdbId': 1, 'mediaType': 'movie', 'value': 'dislike', 'updatedAt': 1},
                                {'tmdbId': 1, 'mediaType': 'movie', 'value': 'like', 'updatedAt': 2}]}
        seeds, excluded, dislikes = signals(request, 100)
        self.assertTrue(seeds[0]['watched'])
        self.assertGreater(seeds[0]['weight'], 2)
        self.assertEqual(dislikes, set())
        self.assertIn('movie-1', excluded)

    def test_dislike_overrides_watch_and_mylist(self):
        request = {'history': [{'tmdbId': 1, 'mediaType': 'movie', 'watchedMs': 3_000_000}],
                   'myList': [{'tmdbId': 1, 'mediaType': 'movie'}],
                   'feedback': [{'tmdbId': 1, 'mediaType': 'movie', 'value': 'dislike'}]}
        seeds, excluded, dislikes = signals(request, 100)
        self.assertEqual(seeds, [])
        self.assertIn('movie-1', excluded & dislikes)


class RankingTests(unittest.TestCase):
    def test_like_only_recommends_new_neighbors_instead_of_the_seed(self):
        class TMDBFake:
            def detail(self, kind, ident):
                return {'id': ident, 'title': f'Movie {ident}', 'overview': 'Space adventure',
                        'poster_path': '/poster.jpg', 'release_date': '2020-01-01'}
            def recommendations(self, *_):
                return []
        request = {'feedback': [{'tmdbId': 1, 'mediaType': 'movie', 'value': 'like'}],
                   'candidates': [{'id': ident, 'mediaType': 'movie'} for ident in (1, 2, 3)]}
        with tempfile.TemporaryDirectory() as directory:
            cache = ContentCache(directory + '/cache.sqlite3')
            self.addCleanup(cache.db.close)
            result = Engine(TMDBFake(), cache, lambda texts: [[1, 0] for _ in texts]).recommend(request, NOW)
        self.assertEqual(result['shelves'][0]['id'], 'for-you')
        self.assertEqual({item['id'] for item in result['shelves'][0]['items']}, {2, 3})

    def test_recent_actual_stream_overrides_theatre_holdback_but_not_future(self):
        class TMDBFake:
            def detail(self, kind, ident):
                metadata = movie([('US', 3, '2026-11-01' if ident == 2 else '2026-10-01')])
                return {**metadata, 'id': ident, 'title': 'Movie', 'overview': 'Plot', 'poster_path': '/poster.jpg'}
            def recommendations(self, *_):
                return []
        timestamp = int(NOW.timestamp() * 1000)
        request = {'history': [
            {'tmdbId': 1, 'mediaType': 'movie', 'watchedMs': 10_000, 'lastWatchedAt': timestamp},
            {'tmdbId': 2, 'mediaType': 'movie', 'watchedMs': 10_000, 'lastWatchedAt': timestamp},
            {'tmdbId': 3, 'mediaType': 'movie', 'positionMs': 2_000_000, 'lastWatchedAt': timestamp},
            {'tmdbId': 4, 'mediaType': 'movie', 'watchedMs': 10_000, 'lastWatchedAt': timestamp - 8 * 86_400_000}],
            'candidates': [{'id': ident, 'mediaType': 'movie'} for ident in range(1, 5)]}
        with tempfile.TemporaryDirectory() as directory:
            cache = ContentCache(directory + '/cache.sqlite3')
            self.addCleanup(cache.db.close)
            result = Engine(TMDBFake(), cache, lambda texts: [[1, 0] for _ in texts]).recommend(request, NOW)
        self.assertEqual(result['eligibleKeys'], ['movie-1'])
        self.assertEqual(set(result['evaluatedKeys']), {'movie-1', 'movie-2', 'movie-3', 'movie-4'})

    def test_diversification_penalizes_duplicates_in_theme(self):
        vectors = {'a': [1, 0], 'b': [1, 0], 'c': [0, 1]}
        self.assertEqual(diverse_rank(vectors, {'a': 1, 'b': 0.99, 'c': 0.9}, vectors, 2), ['a', 'c'])

    def test_request_bounds_and_types(self):
        with self.assertRaises(ValueError):
            validate({'candidates': [{'id': 1, 'mediaType': 'movie'}] * (MAX_CANDIDATES + 1)})
        for ident in (-1, True, '1'):
            with self.assertRaises(ValueError):
                validate({'myList': [{'tmdbId': ident, 'mediaType': 'movie'}]})

    def test_real_pipeline_filters_dedups_caches_and_learns(self):
        class TMDBFake:
            def detail(self, kind, ident):
                if ident == 90:
                    raise RuntimeError('unavailable')
                return {'id': ident, 'title': f'Movie {ident}', 'overview': 'Space exploration' if ident % 2 else 'Family comedy',
                        'genres': [{'name': 'Science Fiction' if ident % 2 else 'Comedy'}], 'poster_path': '/poster.jpg',
                        'release_date': '2027-01-01' if ident == 91 else '2020-01-01', 'vote_average': 7}
            def recommendations(self, *_):
                return [{'id': ident} for ident in range(40, 60)]
        encoded_count = [0]
        def fake_encoder(descriptions):
            encoded_count[0] += len(descriptions)
            return [[1, 0] if 'Space exploration' in description else [0, 1] for description in descriptions]
        with tempfile.TemporaryDirectory() as directory:
            cache = ContentCache(directory + '/content.sqlite3')
            self.addCleanup(cache.db.close)
            engine = Engine(TMDBFake(), cache, fake_encoder)
            request = {'history': [{'tmdbId': 1, 'mediaType': 'movie', 'watchedMs': 3_600_000, 'positionMs': 3_600_000}],
                       'feedback': [{'tmdbId': 3, 'mediaType': 'movie', 'value': 'dislike'}],
                       'candidates': [{'id': ident, 'mediaType': 'movie', 'title': 'Untrusted text'} for ident in range(2, 92)]}
            result = engine.recommend(request, NOW)
            titles = [item for shelf in result['shelves'] for item in shelf['items']]
            keys = [key(item) for item in titles]
            self.assertEqual(len(keys), len(set(keys)))
            self.assertNotIn('movie-1', keys)
            self.assertNotIn('movie-3', keys)
            self.assertNotIn('movie-90', result['evaluatedKeys'])
            self.assertIn('movie-91', result['evaluatedKeys'])
            self.assertNotIn('movie-91', result['eligibleKeys'])
            self.assertFalse(result['completeEligibility'])
            self.assertEqual(result['shelves'][0]['id'], 'for-you')
            self.assertTrue(any(shelf['id'] == 'because-movie-1' for shelf in result['shelves']))
            self.assertTrue(all(item['title'].startswith('Movie ') for item in titles))
            count = encoded_count[0]
            self.assertEqual(engine.recommend(request, NOW), result)
            self.assertEqual(encoded_count[0], count)
            # Persistent rows only contain fetched content and vectors, never history.
            for row in cache.db.execute('SELECT data FROM vectors'):
                self.assertIsInstance(json.loads(row[0]), list)

    def test_busy_gate_and_response_cache(self):
        class Fake:
            calls = 0
            def recommend(self, request):
                self.calls += 1
                return {'shelves': []}
        engine = Fake()
        service = Service(engine)
        self.assertEqual(service.recommend({}), service.recommend({}))
        self.assertEqual(engine.calls, 1)
        service.gate.acquire()
        try:
            with self.assertRaises(BlockingIOError):
                service.recommend({'candidates': [{'id': 1, 'mediaType': 'movie'}]})
        finally:
            service.gate.release()


if __name__ == '__main__':
    unittest.main()
