FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home recommender \
    && mkdir -p /data && chown recommender:recommender /data
COPY recommender/engine.py recommender/model.py recommender/home_cache.py recommender/catalog.py recommender/themes.py recommender/service.py ./
USER recommender
ENV PYTHONUNBUFFERED=1
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=3)" || exit 1
CMD ["python", "service.py"]
