"""Run in an isolated TV container before starting the main GPU service.

Example: docker compose run --rm --no-deps --entrypoint python recommender benchmark_model.py
This loads its own weights; do not run alongside the TV recommender process.
"""
import argparse
import json
import math
import statistics
import time

from model import Encoder

DOCUMENTS = [
    'Home Alone. Family comedy. Christmas holiday. A young boy left home alone '
    'defends his house from burglars and reunites with his family.',
    'Halloween. Horror thriller. A masked murderer terrorizes teenagers in a '
    'small town on Halloween night.',
    'Interstellar. Science fiction adventure. Astronauts cross a wormhole to '
    'find a new home for humanity and a father tries to reunite with his daughter.',
    'The Office. Television workplace comedy. Employees of a paper company '
    'navigate everyday office life, friendships, romance and awkward managers.',
]


def benchmark(iterations):
    started = time.perf_counter()
    encoder = Encoder()
    torch = encoder.torch
    startup = time.perf_counter() - started
    torch.cuda.reset_peak_memory_stats()
    timings = {}
    long_docs = [text + (' Detailed movie synopsis.' * 300) for text in DOCUMENTS]
    for name, texts, query in [('document_1', DOCUMENTS[:1], False),
                             ('documents_4', DOCUMENTS, False),
                             ('theme_query_1', ['Christmas family celebrations and festive reunions'], True),
                             ('max_context_documents_4', long_docs, False)]:
        measurements = []
        for _ in range(iterations):
            torch.cuda.synchronize()
            started = time.perf_counter()
            vectors = encoder.encode_queries(texts) if query else encoder(texts)
            torch.cuda.synchronize()
            measurements.append((time.perf_counter() - started) * 1000)
            if any(len(v) != 1024 or abs(sum(x*x for x in v) - 1) > 1e-4 for v in vectors):
                raise RuntimeError('invalid dimensions or normalization')
        timings[name] = {'median_ms': round(statistics.median(measurements), 2),
                         'p95_ms': round(sorted(measurements)[math.ceil(.95 * iterations) - 1], 2)}
    vectors = encoder(DOCUMENTS)
    single = encoder(DOCUMENTS[:1])[0]
    padding_similarity = sum(a*b for a, b in zip(single, vectors[0]))
    if padding_similarity < .999:
        raise RuntimeError('embedding changed with batch padding')
    query = encoder.encode_queries(['Christmas family celebrations and festive reunions'])[0]
    scores = [sum(a*b for a, b in zip(query, vector)) for vector in vectors]
    similarities = [[sum(a*b for a, b in zip(left, right)) for right in vectors]
                    for left in vectors]
    if scores.index(max(scores)) != 0:
        raise RuntimeError('Christmas theme failed the basic document retrieval check')
    return {'model': json.loads(encoder.cache_fingerprint),
            'device': torch.cuda.get_device_name(0), 'startup_seconds': round(startup, 2),
            'warm_inference': timings, 'christmas_document_scores': scores,
            'document_similarity': similarities,
            'single_vs_padded_batch_cosine': padding_similarity,
            'peak_allocator_mib': round(torch.cuda.max_memory_allocated() / 1024**2, 2),
            'peak_reserved_mib': round(torch.cuda.max_memory_reserved() / 1024**2, 2),
            'process_vram_note': 'Measure total process VRAM separately with nvidia-smi; allocator excludes CUDA context.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--iterations', type=int, default=10)
    args = parser.parse_args()
    if not 1 <= args.iterations <= 100:
        parser.error('--iterations must be between 1 and 100')
    print(json.dumps(benchmark(args.iterations), indent=2))
