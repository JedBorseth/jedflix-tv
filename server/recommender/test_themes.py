import unittest
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from themes import active_themes, eligible_for_theme, profile_context, theme_definitions


THEMES = {theme.id: theme for theme in theme_definitions()}
LOCAL = ZoneInfo('America/Vancouver')


def when(month=10, day=9, hour=18):
    return datetime(2026, month, day, hour, tzinfo=LOCAL)


def ids(now, request=None, metadata=None):
    return {theme.id for theme in active_themes(request or {}, metadata or {}, now)}


def detail(*genre_ids, **extra):
    return {'runtime': 90, 'genres': [{'id': genre_id, 'name': str(genre_id)} for genre_id in genre_ids], **extra}


class CalendarTests(unittest.TestCase):
    def test_all_27_proposals_have_activation_evidence(self):
        now = when()
        metadata = {f'movie-{ident}': detail(35, 53, 9648, 878, 14, release_date='1996-01-01',
                    keywords={'keywords': [{'name': 'based on true story'}]},
                    credits={'crew': [{'id': 10, 'name': 'A Director', 'job': 'Director'}],
                             'cast': [{'id': 20, 'name': 'An Actor'}]}) for ident in (1, 2, 3)}
        metadata['tv-4'] = detail(18, status='Ended',
                last_episode_to_air={'season_number': 1, 'episode_number': 6})
        request = {'history': [{'tmdbId': ident, 'mediaType': 'movie', 'watchedMs': 2_000_000,
                                'positionMs': 2_000_000, 'lastWatchedAt': int(now.timestamp() * 1000)}
                               for ident in (1, 2, 3)] + [
                    {'tmdbId': 4, 'mediaType': 'tv', 'watchedMs': 2_000_000, 'positionMs': 2_000_000,
                     'latestWatchedMs': 2_000_000,
                     'durationMs': 2_100_000, 'season': 1, 'episode': 6,
                     'lastWatchedAt': int(now.timestamp() * 1000)}]}
        reached = set()
        for date in (now, when(day=10), when(day=11), when(day=12, hour=22),
                     when(month=12, day=24), when(month=12, day=27),
                     when(month=2, day=14), when(month=7, day=4)):
            for ident in ids(date, request, metadata):
                reached.add('more-director' if ident.startswith('more-director-') else
                            'starring-actor' if ident.startswith('starring-actor-') else ident)
        self.assertEqual(len(THEMES), 27)
        self.assertEqual(reached, set(THEMES))

    def test_friday_eligibility_uses_local_time_and_boundaries(self):
        self.assertNotIn('friday-movie-night', ids(when(hour=16)))
        self.assertIn('friday-movie-night', ids(when(hour=17)))
        instant = datetime(2026, 10, 9, 1, tzinfo=timezone.utc).astimezone(LOCAL)
        self.assertNotIn('friday-movie-night', ids(instant))  # Thursday in Vancouver.
        with self.assertRaises(ValueError):
            active_themes({}, {}, datetime(2026, 10, 9, 17))

    def test_holiday_windows_and_canadian_thanksgiving(self):
        self.assertNotIn('halloween', ids(when(month=9, day=30)))
        self.assertIn('halloween', ids(when(day=31)))
        self.assertNotIn('halloween', ids(when(month=11, day=1)))
        self.assertNotIn('christmas', ids(when(month=11, day=19)))
        self.assertIn('christmas', ids(when(month=11, day=20)))
        self.assertIn('christmas', ids(when(month=12, day=25)))
        self.assertNotIn('christmas', ids(when(month=12, day=26)))
        self.assertIn('new-year', ids(when(month=1, day=7)))
        self.assertNotIn('new-year', ids(when(month=1, day=8)))
        self.assertNotIn('thanksgiving', ids(when(day=9)))
        for day in (10, 11, 12):
            self.assertIn('thanksgiving', ids(when(day=day)))
        self.assertNotIn('thanksgiving', ids(when(day=13)))
        self.assertNotIn('thanksgiving', ids(when(month=11, day=26)))

    def test_empty_profile_does_not_invent_taste(self):
        selected = ids(when(month=5, day=5, hour=12))
        self.assertEqual(selected, {'start-new-series'})


class EvidenceTests(unittest.TestCase):
    def test_runtime_and_limited_series_claims_require_facts(self):
        short = THEMES['done-before-bed']
        self.assertTrue(eligible_for_theme(short, {'runtime': 99}, 'movie'))
        self.assertTrue(eligible_for_theme(short, {'runtime': 45}, 'movie'))
        for value in ({}, {'runtime': 0}, {'runtime': 44}, {'runtime': 100}):
            self.assertFalse(eligible_for_theme(short, value, 'movie'))
        mini = dict(number_of_seasons=1, type='Miniseries', status='Ended', number_of_episodes=6)
        self.assertTrue(eligible_for_theme(THEMES['one-season-done'], mini, 'tv'))
        for change in ({'status': 'Returning Series'}, {'type': 'Scripted'}, {'number_of_seasons': 2}):
            self.assertFalse(eligible_for_theme(THEMES['one-season-done'], {**mini, **change}, 'tv'))
        self.assertFalse(eligible_for_theme(THEMES['weekend-binge'], {**mini, 'episode_run_time': [121]}, 'tv'))
        self.assertTrue(eligible_for_theme(THEMES['weekend-binge'], {**mini, 'episode_run_time': [60]}, 'tv'))

    def test_holiday_and_true_story_do_not_follow_embedding_mood_alone(self):
        snowy = detail(10751, overview='A family travels through a snowy forest in winter.')
        self.assertFalse(eligible_for_theme(THEMES['christmas'], snowy, 'movie'))
        self.assertTrue(eligible_for_theme(THEMES['cozy-winter'], snowy, 'movie'))
        christmas = {**snowy, 'keywords': {'keywords': [{'name': 'christmas'}]}}
        self.assertTrue(eligible_for_theme(THEMES['christmas'], christmas, 'movie'))
        self.assertFalse(eligible_for_theme(THEMES['true-story'], {'overview': 'Based on a true story.'}, 'movie'))
        self.assertTrue(eligible_for_theme(THEMES['true-story'], {'keywords': {'results': [{'name': 'based on true story'}]}}, 'tv'))

    def test_spooky_family_rail_excludes_horror(self):
        friendly = detail(10751, 35, overview='A friendly ghost helps a family.')
        self.assertTrue(eligible_for_theme(THEMES['spooky-not-scary'], friendly, 'movie'))
        self.assertFalse(eligible_for_theme(THEMES['spooky-not-scary'], detail(27, 10751, overview='A friendly ghost.'), 'movie'))
        self.assertFalse(eligible_for_theme(THEMES['spooky-not-scary'], detail(35, overview='A friendly ghost.'), 'movie'))

    def test_feature_shelves_exclude_shorts_and_unknown_runtime(self):
        for theme_id in ('friday-movie-night', 'halloween', 'done-before-bed', 'hidden-gems'):
            for runtime in (None, 0, 9, 44):
                movie = detail(35, 27, runtime=runtime, vote_average=8, vote_count=1000, popularity=10)
                self.assertFalse(eligible_for_theme(THEMES[theme_id], movie, 'movie'))

    def test_christmas_ghost_stories_do_not_fill_october_family_rail(self):
        carol = detail(10751, title='Mickey’s Christmas Carol', overview='Ghosts visit a miser at Christmas.')
        self.assertFalse(eligible_for_theme(THEMES['spooky-not-scary'], carol, 'movie'))
        nightmare = detail(10751, title='The Nightmare Before Christmas',
                           overview='The Halloween king encounters Christmas Town.')
        self.assertTrue(eligible_for_theme(THEMES['spooky-not-scary'], nightmare, 'movie'))

    def test_comfort_shelves_reject_stressful_adventure_but_allow_light_action_comedy(self):
        for theme_id in ('after-long-day', 'sunday-comfort'):
            for stressful in (28, 10752, 80):
                self.assertFalse(eligible_for_theme(THEMES[theme_id], detail(12, stressful), 'movie'))
                self.assertTrue(eligible_for_theme(THEMES[theme_id], detail(12, stressful, 35), 'movie'))
            self.assertFalse(eligible_for_theme(THEMES[theme_id], detail(12, 35, 53), 'movie'))

    def test_single_multigenre_title_cannot_manufacture_repeated_interest(self):
        now = when(month=5, day=5, hour=12)
        metadata = {'movie-1': detail(53, 9648, 878, 14)}
        request = {'feedback': [{'mediaType': 'movie', 'tmdbId': 1, 'value': 'like'}]}
        selected = ids(now, request, metadata)
        self.assertNotIn('twists-turns', selected)
        self.assertNotIn('worlds', selected)

    def test_show_completion_requires_actual_final_episode_and_completion(self):
        now = when()
        timestamp = int(now.timestamp() * 1000)
        show = dict(status='Ended', last_episode_to_air={'season_number': 2, 'episode_number': 8})
        row = dict(tmdbId=1, mediaType='tv', watchedMs=2_000_000, durationMs=2_100_000,
                   latestWatchedMs=2_000_000, positionMs=2_000_000, season=2, episode=8, lastWatchedAt=timestamp)
        for change in ({'episode': 7}, {'season': 1}, {'positionMs': 500_000}, {'watchedMs': 10_000},
                       {'latestWatchedMs': 0}, {'latestWatchedMs': 300_000}):
            self.assertNotIn('next-obsession', ids(now, {'history': [{**row, **change}]}, {'tv-1': show}))
        self.assertIn('next-obsession', ids(now, {'history': [row]}, {'tv-1': show}))
        self.assertNotIn('next-obsession', ids(now, {'history': [row]}, {'tv-1': {**show, 'status': 'Returning Series'}}))

    def test_person_shelves_use_verified_credits_and_repeated_positive_titles(self):
        now = when()
        metadata = {f'movie-{ident}': detail(35, credits={
            'cast': [{'id': 22, 'name': 'Actor Name'}],
            'crew': [{'id': 11, 'name': 'Director Name', 'job': 'Director'}]}) for ident in (1, 2)}
        request = {'feedback': [{'mediaType': 'movie', 'tmdbId': ident, 'value': 'like'} for ident in (1, 2)]}
        selected = {theme.id: theme for theme in active_themes(request, metadata, now)}
        director = selected['more-director-11']
        actor = selected['starring-actor-22']
        self.assertEqual(director.title, 'More From Director Name')
        self.assertTrue(eligible_for_theme(director, metadata['movie-1'], 'movie'))
        self.assertFalse(eligible_for_theme(director, metadata['movie-1'], 'tv'))
        self.assertFalse(eligible_for_theme(actor, {'overview': 'Actor Name appears.'}, 'movie'))
        request['feedback'].append({'mediaType': 'movie', 'tmdbId': 2, 'value': 'dislike', 'updatedAt': 1})
        self.assertNotIn('more-director-11', ids(now, request, metadata))

    def test_change_of_pace_requires_recent_concentration_and_changes_genre(self):
        now = when()
        request = {'history': [{'tmdbId': ident, 'mediaType': 'movie', 'watchedMs': 1_000_000,
                                'lastWatchedAt': int(now.timestamp() * 1000)} for ident in (1, 2, 3)]}
        metadata = {f'movie-{ident}': detail(35) for ident in (1, 2, 3)}
        context = profile_context(request, metadata, now)
        self.assertIn('change-pace', ids(now, request, metadata))
        self.assertFalse(eligible_for_theme(THEMES['change-pace'], detail(35), 'movie', context))
        self.assertTrue(eligible_for_theme(THEMES['change-pace'], detail(18), 'movie', context))


if __name__ == '__main__':
    unittest.main()
