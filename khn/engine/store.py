"""Mémoire persistante (SQLite, une table clé → JSON)."""
from __future__ import annotations

import json
import os
import sqlite3
import time


class Store:
    def __init__(self, path: str):
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL, updated REAL NOT NULL)")
        self.db.commit()

    def get(self, k: str, default=None):
        row = self.db.execute("SELECT v FROM kv WHERE k = ?", (k,)).fetchone()
        return json.loads(row[0]) if row else default

    def put_many(self, items: dict) -> None:
        now = time.time()
        self.db.executemany("INSERT INTO kv (k, v, updated) VALUES (?, ?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v, updated = excluded.updated",
                            [(k, json.dumps(v, ensure_ascii=False), now) for k, v in items.items()])
        self.db.commit()

    def close(self) -> None:
        self.db.close()
