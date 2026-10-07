"""Model-contract checks run without downloading weights or requiring a GPU."""
import json
import importlib.util
import os
import unittest
from unittest.mock import patch

from model import (CACHE_FINGERPRINT, DIMENSION, MODEL, REVISION, Encoder,
                   bounded_setting, fingerprint, query_prompt, last_token_pool)


class ModelContractTests(unittest.TestCase):
    def test_cache_identity_cannot_reuse_bge_vectors(self):
        identity = json.loads(CACHE_FINGERPRINT)
        self.assertEqual(identity['model'], MODEL)
        self.assertEqual(identity['revision'], REVISION)
        self.assertRegex(REVISION, r'^[a-f0-9]{40}$')
        self.assertEqual(identity['dimensions'], DIMENSION)
        self.assertEqual(identity['dtype'], 'float16')
        self.assertNotIn('BAAI', CACHE_FINGERPRINT)
        self.assertNotEqual(fingerprint(256), fingerprint(512))

    def test_queries_have_instructions_documents_do_not(self):
        encoder = Encoder.__new__(Encoder)
        with patch.object(encoder, '_encode', return_value=[[1.0]]) as encode:
            self.assertEqual(encoder(['Movie metadata']), [[1.0]])
            encode.assert_called_once_with(['Movie metadata'])
        with patch.object(encoder, '_encode', return_value=[[1.0]]) as encode:
            encoder.encode_queries(['Holiday family reunions'], 'Retrieve festive movies.')
            encode.assert_called_once_with(
                ['Instruct: Retrieve festive movies.\nQuery: Holiday family reunions'])
        with self.assertRaises(ValueError):
            query_prompt('Movie', '  ')

    def test_configuration_cannot_bypass_memory_safe_caps(self):
        limits = [('MODEL_BATCH_SIZE', 4, 1, 4),
                  ('MODEL_MAX_TOKENS', 512, 64, 512),
                  ('MODEL_GPU_MEMORY_MIB', 2560, 1536, 2560)]
        for name, default, lower, upper in limits:
            with patch.dict(os.environ, {name: str(upper + 1)}):
                with self.assertRaises(ValueError):
                    bounded_setting(name, default, lower, upper)
            with patch.dict(os.environ, {name: str(lower - 1)}):
                with self.assertRaises(ValueError):
                    bounded_setting(name, default, lower, upper)
            with patch.dict(os.environ, {name: str(default)}):
                self.assertEqual(bounded_setting(name, default, lower, upper), default)


@unittest.skipUnless(importlib.util.find_spec('torch'), 'PyTorch not installed in policy-only test environment')
class PoolingTests(unittest.TestCase):
    def test_left_and_right_padding_select_real_final_token(self):
        import torch
        hidden = torch.tensor([[[10., 11.], [20., 21.], [30., 31.]],
                               [[40., 41.], [50., 51.], [60., 61.]]])
        left = last_token_pool(hidden, torch.tensor([[0, 1, 1], [1, 1, 1]]))
        self.assertEqual(left.tolist(), [[30., 31.], [60., 61.]])
        right = last_token_pool(hidden, torch.tensor([[1, 1, 0], [1, 1, 1]]))
        self.assertEqual(right.tolist(), [[20., 21.], [60., 61.]])

    def test_empty_token_sequences_fail_instead_of_returning_padding(self):
        import torch
        with self.assertRaises(ValueError):
            last_token_pool(torch.zeros((1, 3, 2)), torch.zeros((1, 3), dtype=torch.long))


if __name__ == '__main__':
    unittest.main()
