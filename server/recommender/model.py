"""Pinned BGE-small ONNX inference; no PyTorch, GPU, or remote inference API."""
import os
from engine import MODEL, REVISION


class Encoder:
    def __init__(self):
        import numpy as np
        import onnxruntime as ort
        from huggingface_hub import hf_hub_download
        from tokenizers import Tokenizer
        self.np = np
        cache = os.getenv('MODEL_CACHE_DIR', '/data/models')
        model_file = hf_hub_download(MODEL, 'onnx/model.onnx', revision=REVISION, cache_dir=cache)
        tokenizer_file = hf_hub_download(MODEL, 'tokenizer.json', revision=REVISION, cache_dir=cache)
        self.tokenizer = Tokenizer.from_file(tokenizer_file)
        self.tokenizer.enable_truncation(max_length=256)
        self.tokenizer.enable_padding(pad_id=0, pad_token='[PAD]')
        options = ort.SessionOptions()
        options.intra_op_num_threads = max(1, min(4, int(os.getenv('MODEL_THREADS', '2'))))
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(model_file, sess_options=options, providers=['CPUExecutionProvider'])
        self.inputs = {value.name for value in self.session.get_inputs()}
        # Startup fails if the real model can't load/run; never substitute fake embeddings.
        self(['A movie about friendship.'])

    def __call__(self, texts):
        np = self.np
        result = []
        for start in range(0, len(texts), 8):
            tokens = self.tokenizer.encode_batch(texts[start:start + 8])
            inputs = {'input_ids': np.array([t.ids for t in tokens], dtype=np.int64),
                      'attention_mask': np.array([t.attention_mask for t in tokens], dtype=np.int64),
                      'token_type_ids': np.array([t.type_ids for t in tokens], dtype=np.int64)}
            output = self.session.run(None, {k: v for k, v in inputs.items() if k in self.inputs})[0]
            # BGE's documented sentence embedding uses the CLS token, normalized.
            vectors = output[:, 0] if output.ndim == 3 else output
            vectors /= np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-12)
            result.extend(vectors.tolist())
        return result
