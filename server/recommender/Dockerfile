FROM python:3.12-slim AS runtime-dependencies
WORKDIR /app
COPY recommender/requirements.txt ./requirements.txt
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir -r requirements.txt \
    && useradd --uid 10001 --create-home recommender \
    && mkdir -p /data && chown recommender:recommender /data
FROM runtime-dependencies
COPY recommender/engine.py recommender/model.py recommender/catalog.py recommender/themes.py recommender/service.py ./
COPY recommender/benchmark_model.py ./
USER recommender
ENV TOKENIZERS_PARALLELISM=false OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 PYTHONUNBUFFERED=1
ENV NVIDIA_VISIBLE_DEVICES=all NVIDIA_DRIVER_CAPABILITIES=compute,utility
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=600s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=3)" || exit 1
CMD ["python", "service.py"]
