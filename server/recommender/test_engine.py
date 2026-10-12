import json
import unittest
from datetime import date, datetime, timezone

from engine import release_policy, signals, validate

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
