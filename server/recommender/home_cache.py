"""Persistent model outputs and atomic call budgets, never raw viewing signals."""
import json
from datetime import timedelta, timezone
from zoneinfo import ZoneInfo


class QuotaExceeded(RuntimeError):
    pass


class HomeCache:
    def __init__(self, cache, home_zone='America/Vancouver', home_limit=2, discovery_limit=40):
        self.cache = cache
        self.zone = ZoneInfo(home_zone)
        self.home_limit = min(2, max(1, home_limit))
        self.discovery_limit = min(40, max(1, discovery_limit))
        with cache.lock:
            cache.db.execute('CREATE TABLE IF NOT EXISTS home_plans (token TEXT PRIMARY KEY, saved REAL, data TEXT)')
            cache.db.execute('CREATE TABLE IF NOT EXISTS model_calls (day TEXT, kind TEXT, count INTEGER, PRIMARY KEY(day,kind))')
            cache.db.execute('CREATE TABLE IF NOT EXISTS home_retry (token TEXT PRIMARY KEY, after REAL)')
            cache.db.commit()

    def get(self, token, now):
        with self.cache.lock:
            row = self.cache.db.execute('SELECT saved,data FROM home_plans WHERE token=?', (token,)).fetchone()
        if row and 0 <= now.timestamp() - row[0] <= 7 * 86400:
            return json.loads(row[1])
        return None

    def save(self, token, plan, now):
        generated = plan.get('generatedAt', int(now.timestamp() * 1000)) / 1000
        with self.cache.lock:
            self.cache.db.execute('INSERT OR REPLACE INTO home_plans VALUES (?,?,?)',
                                  (token, generated, json.dumps(plan, separators=(',', ':'))))
            self.cache.db.execute('DELETE FROM home_plans WHERE saved < ?', (now.timestamp() - 7 * 86400,))
            self.cache.db.execute('DELETE FROM home_plans WHERE token NOT IN (SELECT token FROM home_plans ORDER BY saved DESC,rowid DESC LIMIT 128)')
            self.cache.db.execute('DELETE FROM home_retry WHERE token=?', (token,))
            self.cache.db.commit()

    def reserve(self, kind, now, token=''):
        """Count attempts before network IO; crashes, failures and restarts cannot reset limits."""
        utc_day = now.astimezone(timezone.utc).date().isoformat()
        day = now.astimezone(self.zone).date().isoformat() if kind == 'home' else utc_day
        limit = self.home_limit if kind == 'home' else self.discovery_limit
        with self.cache.lock:
            db = self.cache.db
            db.execute('BEGIN IMMEDIATE')
            try:
                if kind == 'home':
                    retry = db.execute('SELECT after FROM home_retry WHERE token=?', (token,)).fetchone()
                    if retry and retry[0] > now.timestamp():
                        raise QuotaExceeded('Home generation is cooling down')
                for budget_day, budget_kind, ceiling in ((day, kind, limit), (utc_day, 'total', 45)):
                    row = db.execute('SELECT count FROM model_calls WHERE day=? AND kind=?', (budget_day, budget_kind)).fetchone()
                    if row and row[0] >= ceiling:
                        raise QuotaExceeded('Daily discovery allowance reached')
                    db.execute('INSERT INTO model_calls VALUES (?,?,1) ON CONFLICT(day,kind) DO UPDATE SET count=count+1',
                               (budget_day, budget_kind))
                if kind == 'home':
                    db.execute('INSERT OR REPLACE INTO home_retry VALUES (?,?)', (token, now.timestamp() + 3600))
                db.execute('DELETE FROM model_calls WHERE day < ?', ((now - timedelta(days=8)).date().isoformat(),))
                db.execute('DELETE FROM home_retry WHERE after < ?', (now.timestamp() - 86400,))
                db.commit()
            except Exception:
                db.rollback()
                raise
