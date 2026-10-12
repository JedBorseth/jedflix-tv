"""Internal-only bounded HTTP sidecar. Bodies and profile activity aren't logged."""
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from engine import ContentCache, Engine, MODEL, TMDB, validate
from model import OpenRouter, ProviderError
from catalog import CatalogIndex
from home_cache import QuotaExceeded

MAX_BODY = 1024 * 1024


class Service:
    def __init__(self, engine):
        self.engine = engine
        self.home_gate = threading.BoundedSemaphore(1)
        self.discovery_gate = threading.BoundedSemaphore(1)

    def recommend(self, request):
        validate(request)
        if not self.home_gate.acquire(blocking=False):
            raise BlockingIOError('recommendation worker busy')
        try:
            return self.engine.recommend(request)
        finally:
            self.home_gate.release()

    def discover(self, request):
        validate(request)
        if not self.discovery_gate.acquire(blocking=False):
            raise BlockingIOError('discovery worker busy')
        try:
            return self.engine.discover(request)
        finally:
            self.discovery_gate.release()


def handler(service):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def reply(self, status, data):
            body = json.dumps(data, separators=(',', ':')).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            if status == 429:
                self.send_header('Retry-After', '30')
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == '/health':
                catalog = getattr(service.engine, 'catalog', None)
                self.reply(200, {'status': 'ok', 'model': service.engine.model.model,
                                 'modelVersion': getattr(service.engine, 'model_version', ''),
                                 'catalogReady': catalog is not None and catalog.ready,
                                 'catalogTitles': len(catalog.snapshot()) if catalog is not None else 0,
                                 'catalogVersion': catalog.version if catalog is not None else 0})
            else:
                self.reply(404, {'error': 'not found'})

        def do_POST(self):
            if self.path not in ('/recommendations', '/discover'):
                self.reply(404, {'error': 'not found'})
                return
            self.connection.settimeout(10)
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length <= 0 or length > MAX_BODY:
                    self.reply(413, {'error': 'request body too large or missing'})
                    return
                request = json.loads(self.rfile.read(length))
                result = service.discover(request) if self.path == '/discover' else service.recommend(request)
                self.reply(200, result)
            except (ValueError, TypeError, KeyError):
                self.reply(400, {'error': 'invalid recommendation request'})
            except (BlockingIOError, QuotaExceeded):
                self.reply(429, {'error': 'discovery temporarily unavailable; try again later'})
            except Exception as error:
                # Exception type only: external errors can contain the TMDB query API key.
                safe_reasons = {'Invalid model shelf names', 'Invalid model shelf size',
                                'Invalid or duplicate model title', 'Model returned an invalid discovery response'}
                reason = str(error) if str(error) in safe_reasons else type(error).__name__
                if isinstance(error, ProviderError):
                    reason = f'OpenRouter HTTP {error.status}'
                print(f'recommendations failed: {reason}', flush=True)
                self.reply(503, {'error': 'recommendations temporarily unavailable'})
    return Handler


def main():
    api_key = os.environ.get('TMDB_API_KEY', '').strip()
    if not api_key:
        raise RuntimeError('TMDB_API_KEY is required')
    cache = ContentCache(os.environ.get('CONTENT_CACHE_DB', '/data/content.sqlite3'))
    model = OpenRouter()
    tmdb = TMDB(api_key, cache)
    catalog = CatalogIndex(tmdb, cache)
    engine = Engine(tmdb, cache, model, catalog)
    catalog.start()
    service = Service(engine)
    print(f'OpenRouter discovery ready: {model.model}', flush=True)
    ThreadingHTTPServer(('0.0.0.0', 8090), handler(service)).serve_forever()


if __name__ == '__main__':
    main()
