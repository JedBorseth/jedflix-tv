"""Internal-only bounded HTTP sidecar. Bodies and profile activity aren't logged."""
import hashlib
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from engine import ContentCache, Engine, MODEL, TMDB, validate
from model import Encoder

MAX_BODY = 1024 * 1024


class Service:
    def __init__(self, engine):
        self.engine = engine
        self.gate = threading.BoundedSemaphore(1)
        self.cache = {}
        self.lock = threading.Lock()

    def recommend(self, request):
        validate(request)
        digest = hashlib.sha256(json.dumps(request, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        now = time.time()
        with self.lock:
            entry = self.cache.get(digest)
        if entry and now - entry[0] < 600:
            return entry[1]
        if not self.gate.acquire(blocking=False):
            raise BlockingIOError('recommendation worker busy')
        try:
            result = self.engine.recommend(request)
            with self.lock:
                # Only short-lived response hashes/results in RAM; never profiles on disk.
                self.cache = {k: v for k, v in self.cache.items() if now - v[0] < 600}
                if len(self.cache) >= 32:
                    self.cache.pop(next(iter(self.cache)))
                self.cache[digest] = (time.time(), result)
            return result
        finally:
            self.gate.release()


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
                self.reply(200, {'status': 'ok', 'model': MODEL})
            else:
                self.reply(404, {'error': 'not found'})

        def do_POST(self):
            if self.path != '/recommendations':
                self.reply(404, {'error': 'not found'})
                return
            self.connection.settimeout(10)
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length <= 0 or length > MAX_BODY:
                    self.reply(413, {'error': 'request body too large or missing'})
                    return
                request = json.loads(self.rfile.read(length))
                result = service.recommend(request)
                self.reply(200, result)
            except (ValueError, TypeError, KeyError):
                self.reply(400, {'error': 'invalid recommendation request'})
            except BlockingIOError:
                self.reply(429, {'error': 'recommendation worker busy'})
            except Exception as error:
                # Exception type only: external errors can contain the TMDB query API key.
                print(f'recommendations failed: {type(error).__name__}', flush=True)
                self.reply(503, {'error': 'recommendations temporarily unavailable'})
    return Handler


def main():
    api_key = os.environ.get('TMDB_API_KEY', '').strip()
    if not api_key:
        raise RuntimeError('TMDB_API_KEY is required')
    cache = ContentCache(os.environ.get('CONTENT_CACHE_DB', '/data/content.sqlite3'))
    encoder = Encoder()
    service = Service(Engine(TMDB(api_key, cache), cache, encoder))
    print(f'recommendation model ready: {MODEL}', flush=True)
    ThreadingHTTPServer(('0.0.0.0', 8090), handler(service)).serve_forever()


if __name__ == '__main__':
    main()
