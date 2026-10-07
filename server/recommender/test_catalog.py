from copy import deepcopy
import threading
import time
import unittest
import urllib.error
from datetime import datetime, timezone
from unittest.mock import patch

from catalog import CatalogIndex


class Cache:
    def __init__(self):
        self.values = {}

    def get(self, table, ident, ttl=None):
        return deepcopy(self.values.get((table, ident)))

    def put(self, table, ident, value):
        self.values[(table, ident)] = deepcopy(value)

    def delete(self, table, ident):
        self.values.pop((table, ident), None)


class TMDB:
    def __init__(self):
        self.calls = []
        self.rows = {}

    def detail(self, kind, ident):
        self.calls.append(('detail', kind, ident))
        return self.rows.get((kind, ident), {'id': ident, 'title': f'Title {ident}', 'release_date': '2020-01-01'})

    def get(self, path, **query):
        self.calls.append((path, query))
        if path == '/search/keyword':
            return {'results': [{'id': 10, 'name': query['query'] + ' extra'}, {'id': 11, 'name': query['query']}]}
        if path.startswith('/trending'):
            return {'results': [{'id': 1, 'media_type': 'movie'}, {'id': 2, 'media_type': 'tv'}, {'id': 3, 'media_type': 'person'}]}
        return {'results': [{'id': 100}], 'total_pages': 3}


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.cache, self.tmdb = Cache(), TMDB()
        self.encoded = []

        def encode(values):
            self.encoded.append(list(values))
            return {ident: [0.6, 0.8] for ident in values}

        self.encode = encode
        self.index = CatalogIndex(self.tmdb, self.cache, encode, fingerprint='model-a', request_interval=0)

    def test_snapshot_has_no_network_or_gpu_work_and_is_independent_of_profile(self):
        self.index.request({'movie-42': {'id': 42, 'mediaType': 'movie', 'watchedMs': 999}})
        self.assertEqual(self.tmdb.calls, [])
        self.assertEqual(self.encoded, [])
        self.index.refresh_once()
        calls, jobs = list(self.tmdb.calls), list(self.encoded)
        metadata, vectors = self.index.snapshot()
        metadata.pop('movie-42')
        vectors.clear()
        self.assertIn('movie-42', self.index.snapshot()[0])
        self.assertEqual(self.tmdb.calls, calls)
        self.assertEqual(self.encoded, jobs)
        stored = repr(self.cache.values)
        self.assertNotIn('watchedMs', stored)
        self.assertNotIn('999', stored)

    def test_persistent_public_snapshot_restores_vectors_only_for_same_model(self):
        self.index.request({'movie-42': {'id': 42, 'mediaType': 'movie'}})
        self.index.refresh_once()
        same = CatalogIndex(self.tmdb, self.cache, self.encode, fingerprint='model-a', request_interval=0)
        self.assertIn('movie-42', same.snapshot()[1])
        different = CatalogIndex(self.tmdb, self.cache, self.encode, fingerprint='model-b', request_interval=0)
        self.assertIn('movie-42', different.snapshot()[0])
        self.assertEqual(different.snapshot()[1], {})
        call_count = len(self.tmdb.calls)
        different.refresh_once()
        self.assertEqual(len(self.tmdb.calls), call_count)
        self.assertIn('movie-42', different.snapshot()[1])

    def test_profile_references_take_priority_over_background_discovery(self):
        self.index.refresh_once()  # Trending puts public IDs in the queue.
        self.index.request({'tv-90': {'id': 90, 'mediaType': 'tv'}})
        self.index.refresh_once()
        details = [row for row in self.tmdb.calls if row[0] == 'detail']
        self.assertEqual(details[0], ('detail', 'tv', 90))
        self.assertIn('movie-1', self.index.snapshot()[0])
        self.assertIn('tv-2', self.index.snapshot()[0])
        self.assertNotIn('person-3', self.index.snapshot()[0])

    def test_background_queries_cover_genres_eras_short_movies_and_limited_series(self):
        templates = self.index._recipe_templates
        self.assertTrue(any(query.get('with_genres') == 27 for _, query, _ in templates))
        self.assertTrue(any(query.get('primary_release_date.gte') == '1990-01-01' for _, query, _ in templates))
        self.assertTrue(any(query.get('with_runtime.lte') == 99 for _, query, _ in templates))
        self.assertTrue(any(kind == 'tv' and query.get('with_type') == 2 for _, query, kind in templates))
        self.assertTrue(any(query.get('sort_by') == 'popularity.asc' for _, query, _ in templates))
        self.assertTrue(any(query.get('query') == 'christmas' for _, query, _ in templates))

    def test_keyword_ids_are_exact_matches_and_discovery_is_paginated(self):
        self.index._recipes.clear()
        self.index._recipes.append(('/search/keyword', {'query': 'christmas'}, 'keyword', 1))
        self.index.refresh_once()
        self.index.refresh_once()
        last = self.tmdb.calls[-1]
        self.assertEqual(last[0], '/discover/movie')
        self.assertEqual(last[1]['with_keywords'], 11)
        self.assertTrue(any(page == 2 for _, _, _, page in self.index._recipes))

    def test_stale_metadata_refreshes_even_when_catalog_already_full(self):
        self.index.request({'movie-42': {'id': 42, 'mediaType': 'movie'}})
        self.index.refresh_once()
        self.index._metadata['movie-42']['_catalog_fetched_at'] = time.time() - 25 * 3600
        self.index._recipes.clear()
        self.index.refresh_once()  # Schedule stale content, without IO.
        self.index.refresh_once()
        details = [row for row in self.tmdb.calls if row[0] == 'detail']
        self.assertEqual(len(details), 2)
        self.assertTrue(self.index._fresh(self.index.snapshot()[0]['movie-42']))

    def test_rate_limit_retry_after_is_honored_without_home_lock(self):
        waits = []

        class Event:
            def is_set(self):
                return False

            def wait(self, seconds):
                self_test.assertTrue(self_test.index._lock.acquire(blocking=False))
                self_test.index._lock.release()
                waits.append(seconds)
                return False

        self_test = self
        self.index._stop = Event()
        attempts = []

        def api():
            attempts.append(1)
            if len(attempts) == 1:
                raise urllib.error.HTTPError('https://tmdb.invalid', 429, 'limited', {'Retry-After': '7'}, None)
            return {'ok': True}

        self.assertEqual(self.index._api(api), {'ok': True})
        self.assertEqual(waits, [7])
        self.assertEqual(len(attempts), 2)

    def test_gpu_work_is_batched_and_version_changes_only_on_publish(self):
        refs = {f'movie-{ident}': {'id': ident, 'mediaType': 'movie'} for ident in range(1, 20)}
        initial = self.index.version
        self.index.request(refs)
        self.assertEqual(self.index.version, initial)
        self.index.refresh_once()
        self.assertEqual(len(self.encoded[0]), 8)
        self.assertGreater(self.index.version, initial)

    def test_full_background_queue_cannot_block_a_new_profile_reference(self):
        self.index._pending = {f'movie-{ident}': ('movie', ident, False) for ident in range(1, 401)}
        self.index.request({'tv-999': {'id': 999, 'mediaType': 'tv'}})
        self.assertEqual(len(self.index._pending), 400)
        self.index.refresh_once()
        self.assertEqual(self.tmdb.calls[0], ('detail', 'tv', 999))

    def test_calendar_change_reprioritizes_current_season_and_config_is_bounded(self):
        current = datetime(2026, 11, 23, tzinfo=timezone.utc)
        self.index._recipe_day = current.replace(month=10).date()
        with patch('catalog.datetime') as clock:
            clock.now.return_value = current
            self.index.refresh_once()
        early_keywords = [query['query'] for path, query, _ in self.index._recipe_templates[2:5]]
        self.assertEqual(set(early_keywords), {'christmas', 'winter', 'snow'})
        self.assertTrue(self.index._refresh_plan)
        oversized = CatalogIndex(self.tmdb, self.cache, self.encode, target=999_999)
        self.assertEqual(oversized.target, 10_000)

    def test_structured_metadata_refresh_overwrites_one_public_vector_record(self):
        self.index.request({'movie-42': {'id': 42, 'mediaType': 'movie'}})
        self.index.refresh_once()
        metadata = deepcopy(self.index.snapshot()[0]['movie-42'])
        metadata.update(popularity=55, vote_average=8,
                        **{'watch/providers': {'results': {'CA': {'rent': [{'provider_id': 1}]}}}})
        before = [ident for table, ident in self.cache.values if table == 'vectors']
        self.index._publish({'movie-42': metadata})
        after = [ident for table, ident in self.cache.values if table == 'vectors']
        self.assertEqual(before, after)
        self.assertEqual(len(after), 1)

    def test_crash_window_reuses_unchanged_documents_and_rebuilds_changed_documents(self):
        self.index.request({'movie-42': {'id': 42, 'mediaType': 'movie'}})
        self.index.refresh_once()
        metadata = self.cache.get('content', 'catalog:v3:metadata:movie-42')
        metadata['popularity'] = 50  # Simulate a crash between metadata and vector persistence.
        self.cache.put('content', 'catalog:v3:metadata:movie-42', metadata)
        same_text = CatalogIndex(self.tmdb, self.cache, self.encode, fingerprint='model-a')
        self.assertIn('movie-42', same_text.snapshot()[1])
        metadata['overview'] = 'A completely new semantic description.'
        self.cache.put('content', 'catalog:v3:metadata:movie-42', metadata)
        changed_text = CatalogIndex(self.tmdb, self.cache, self.encode, fingerprint='model-a', request_interval=0)
        self.assertNotIn('movie-42', changed_text.snapshot()[1])
        changed_text.refresh_once()
        self.assertIn('movie-42', changed_text.snapshot()[1])

    def test_fifo_eviction_cleans_persistent_index_and_protects_same_batch_refresh(self):
        self.index._max_entries = 2
        self.index._publish({'movie-1': {'id': 1, 'title': 'One'}, 'movie-2': {'id': 2, 'title': 'Two'}})
        self.index._publish({'movie-1': {'id': 1, 'title': 'One refreshed'}, 'movie-3': {'id': 3, 'title': 'Three'}})
        self.assertEqual(set(self.index.snapshot()[0]), {'movie-1', 'movie-3'})
        self.assertIsNone(self.cache.get('content', 'catalog:v3:metadata:movie-2'))
        indexed = [ident for table, ident in self.cache.values if table == 'vectors']
        self.assertEqual(len(indexed), 2)


if __name__ == '__main__':
    unittest.main()
