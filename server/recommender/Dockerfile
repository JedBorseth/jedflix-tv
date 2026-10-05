FROM python:3.12-slim
WORKDIR /app
COPY recommender/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --uid 10001 --create-home recommender \
    && mkdir -p /data && chown recommender:recommender /data
COPY recommender/engine.py recommender/model.py recommender/service.py ./
USER recommender
ENV TOKENIZERS_PARALLELISM=false OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 PYTHONUNBUFFERED=1
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=3)" || exit 1
CMD ["python", "service.py"]
