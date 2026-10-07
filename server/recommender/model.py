"""Dedicated, bounded Qwen3 GPU inference for JedFlix TV.

No CPU/fake-vector fallback: a missing GPU or failed model makes startup fail.
The music application's model and service are deliberately independent.
"""
import json
import os
import threading

MODEL = 'Qwen/Qwen3-Embedding-0.6B'
REVISION = '97b0c614be4d77ee51c0cef4e5f07c00f9eb65b3'
DIMENSION = 1024
QUERY_INSTRUCTION = ('Given a viewing theme, retrieve movie or television descriptions '
                     'that match its mood and subject.')


def bounded_setting(name, default, minimum, maximum):
    value = int(os.getenv(name, str(default)))
    if not minimum <= value <= maximum:
        raise ValueError(f'{name} must be between {minimum} and {maximum}')
    return value


def fingerprint(max_tokens=512):
    """All vector-producing choices belong in persistent cache identity."""
    return json.dumps({'model': MODEL, 'revision': REVISION, 'dimensions': DIMENSION,
                       'document_recipe': 'plain-title-metadata-v2',
                       'query_recipe': 'Instruct: {instruction}\\nQuery: {text}',
                       'pooling': 'last-nonpadding-token', 'normalization': 'l2-float32',
                       'dtype': 'float16', 'max_tokens': max_tokens}, sort_keys=True)


CACHE_FINGERPRINT = fingerprint()


def query_prompt(text, instruction):
    if not isinstance(instruction, str) or not instruction.strip():
        raise ValueError('query instruction must be non-empty')
    return f'Instruct: {instruction.strip()}\nQuery: {text}'


def last_token_pool(hidden, mask):
    # Encoder always left pads, so the last slot is a real token. Keep right
    # padding support for the reference pooling behavior and regression tests.
    if bool(mask[:, -1].all()):
        return hidden[:, -1]
    lengths = mask.sum(dim=1) - 1
    if bool((lengths < 0).any()):
        raise ValueError('cannot pool an empty token sequence')
    import torch
    return hidden[torch.arange(hidden.shape[0], device=hidden.device), lengths]


class Encoder:
    def __init__(self):
        import torch
        from transformers import AutoModel, AutoTokenizer

        if not torch.cuda.is_available():
            raise RuntimeError('JedFlix TV recommendations require an NVIDIA CUDA GPU')
        self.torch = torch
        self.lock = threading.Lock()
        self.batch_size = bounded_setting('MODEL_BATCH_SIZE', 4, 1, 4)
        self.max_tokens = bounded_setting('MODEL_MAX_TOKENS', 512, 64, 512)
        # Leave room for driver/context allocations within the ~3 GiB total
        # process target. PyTorch's allocator cap excludes those allocations.
        budget_mib = bounded_setting('MODEL_GPU_MEMORY_MIB', 2560, 1536, 2560)
        torch.cuda.set_per_process_memory_fraction(
            min(1.0, budget_mib * 1024**2 / torch.cuda.get_device_properties(0).total_memory), 0)
        torch.set_num_threads(bounded_setting('MODEL_THREADS', 2, 1, 4))
        self.cache_fingerprint = fingerprint(self.max_tokens)
        cache = os.getenv('MODEL_CACHE_DIR', '/data/models')
        self.tokenizer = AutoTokenizer.from_pretrained(
            MODEL, revision=REVISION, cache_dir=cache, padding_side='left', trust_remote_code=False)
        self.model = AutoModel.from_pretrained(
            MODEL, revision=REVISION, cache_dir=cache, torch_dtype=torch.float16,
            device_map={'': 'cuda:0'}, attn_implementation='sdpa',
            low_cpu_mem_usage=True, trust_remote_code=False, use_safetensors=True)
        self.model.eval()
        self.model.config.use_cache = False
        # Health is never successful until genuine model inference succeeds.
        self(['A movie about friendship.'])

    def __call__(self, texts):
        return self._encode(texts)

    def encode_queries(self, texts, instruction=QUERY_INSTRUCTION):
        return self._encode([query_prompt(text, instruction) for text in texts])

    def _encode(self, texts):
        if not texts:
            return []
        if any(not isinstance(text, str) or not text.strip() for text in texts):
            raise ValueError('embedding inputs must be non-empty strings')
        torch = self.torch
        from torch.nn.attention import SDPBackend, sdpa_kernel
        result = []
        # Serialize whole jobs: catalog warming and concurrent HTTP requests
        # cannot multiply resident activation memory. Turing supports efficient
        # SDPA with the math implementation as a fallback, not FlashAttention-2.
        with self.lock, torch.inference_mode(), sdpa_kernel(
                backends=[SDPBackend.EFFICIENT_ATTENTION, SDPBackend.MATH]):
            for start in range(0, len(texts), self.batch_size):
                tokens = self.tokenizer(texts[start:start + self.batch_size], padding=True,
                                        truncation=True, max_length=self.max_tokens,
                                        return_tensors='pt').to('cuda:0')
                output = self.model(**tokens, use_cache=False, return_dict=True)
                pooled = last_token_pool(output.last_hidden_state, tokens['attention_mask']).float()
                if pooled.shape[1] != DIMENSION or not bool(torch.isfinite(pooled).all()):
                    raise RuntimeError('Qwen returned invalid embedding vectors')
                norms = pooled.norm(p=2, dim=1, keepdim=True)
                if bool((norms <= 1e-12).any()):
                    raise RuntimeError('Qwen returned an empty embedding vector')
                result.extend((pooled / norms).cpu().tolist())
                del output, pooled, norms, tokens
        return result
