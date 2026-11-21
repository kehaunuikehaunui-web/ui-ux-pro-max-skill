"""Serveur web : interface pixel, API de commandes et flux temps réel (WebSocket). Protégé par mot de passe."""
from __future__ import annotations

import asyncio
import contextlib
import hashlib
import hmac
import json
import math
import os
import secrets
import time
from contextlib import asynccontextmanager
from urllib.parse import parse_qs, urlparse

from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse

from .bot import Bot
from .config import Config

WEB_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "web")
COOKIE = "khn_session"
MAX_AGE = 30 * 86400


def clean(o):
    """Remplace NaN/Infini par None pour produire du JSON valide côté navigateur."""
    if isinstance(o, float):
        return o if math.isfinite(o) else None
    if isinstance(o, dict):
        return {k: clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [clean(v) for v in o]
    return o


def dumps(o) -> str:
    return json.dumps(clean(o), ensure_ascii=False, separators=(",", ":"))


def _secret(cfg: Config) -> bytes:
    if cfg.secret:
        return cfg.secret.encode()
    path = os.path.join(cfg.data_dir, "secret.key")
    os.makedirs(cfg.data_dir, exist_ok=True)
    if not os.path.exists(path):
        with open(path, "w") as f:
            f.write(secrets.token_hex(32))
        os.chmod(path, 0o600)
    with open(path) as f:
        return f.read().strip().encode()


LOGIN = """<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>KHN · connexion</title><style>
body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0a1020;color:#e7eef9;font:15px/1.5 system-ui,sans-serif}
form{background:#111a2e;border:1px solid #223150;border-radius:8px;padding:24px;display:grid;gap:12px;width:min(320px,90vw)}
h1{margin:0;font:700 34px monospace;color:#f5c862;letter-spacing:3px;text-shadow:3px 3px 0 #6b4c10}
input{background:#0d1527;border:1px solid #2e4166;border-radius:5px;padding:10px;color:inherit;font:inherit}
button{background:#3fe0c5;border:0;border-radius:5px;padding:10px;font-weight:600;color:#06231f;cursor:pointer}
p{margin:0;color:#97a8c7;font-size:13px} .err{color:#ff6b6b}
</style></head><body><form method="post" action="/login"><h1>KHN</h1><p>Bureau de trading · accès privé</p>
<label for="pw">Mot de passe</label><input id="pw" name="password" type="password" autocomplete="current-password" autofocus required>
<button type="submit">Entrer</button>__ERR__</form></body></html>"""


def create_app(cfg: Config | None = None, bot: Bot | None = None, start_bot: bool = True) -> FastAPI:
    cfg = cfg or Config.from_env()
    if not cfg.password:
        raise SystemExit("KHN_PASSWORD est vide : choisis un mot de passe dans le fichier .env avant de lancer le serveur.")
    key = _secret(cfg)
    bot = bot or Bot(cfg)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        task = asyncio.create_task(bot.run()) if start_bot else None
        yield
        if task:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError, Exception):
                await task

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.bot = bot

    def token() -> str:
        exp = str(int(time.time()) + MAX_AGE)
        return exp + "." + hmac.new(key, exp.encode(), hashlib.sha256).hexdigest()

    def valid(tok: str | None) -> bool:
        if not tok or "." not in tok:
            return False
        exp, sig = tok.split(".", 1)
        good = hmac.new(key, exp.encode(), hashlib.sha256).hexdigest()
        return hmac.compare_digest(sig, good) and exp.isdigit() and int(exp) > time.time()

    def denied() -> JSONResponse:
        return JSONResponse({"error": "non connecté"}, status_code=401)

    @app.get("/healthz")
    async def healthz():
        return {"ok": True, "mode": cfg.mode, "ready": bot.ready, "status": bot.status}

    @app.get("/login", response_class=HTMLResponse)
    async def login_page():
        return LOGIN.replace("__ERR__", "")

    @app.post("/login")
    async def login(request: Request):
        form = parse_qs((await request.body()).decode("utf-8", "replace"))
        pw = (form.get("password") or [""])[0]
        if not hmac.compare_digest(pw.encode(), cfg.password.encode()):
            await asyncio.sleep(1.5)  # freine les essais en série
            return HTMLResponse(LOGIN.replace("__ERR__", '<p class="err">Mot de passe incorrect.</p>'), status_code=401)
        resp = RedirectResponse("/", status_code=303)
        https = request.url.scheme == "https" or request.headers.get("x-forwarded-proto") == "https"
        resp.set_cookie(COOKIE, token(), max_age=MAX_AGE, httponly=True, samesite="strict", secure=https)
        return resp

    @app.get("/logout")
    async def logout():
        resp = RedirectResponse("/login", status_code=303)
        resp.delete_cookie(COOKIE)
        return resp

    @app.get("/")
    async def index(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return RedirectResponse("/login", status_code=303)
        return FileResponse(os.path.join(WEB_DIR, "index.html"), headers={"Cache-Control": "no-store"})

    @app.get("/api/state")
    async def state(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        return HTMLResponse(dumps({**bot.snapshot(), "logs": list(bot.logs)}), media_type="application/json")

    async def body(request: Request) -> dict:
        try:
            d = await request.json()
            return d if isinstance(d, dict) else {}
        except (json.JSONDecodeError, UnicodeDecodeError):
            return {}

    @app.post("/api/command")
    async def command(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        if not bot.ready:
            return JSONResponse({"error": "KHN démarre encore, réessaie dans un instant"}, status_code=503)
        await bot.command(str((await body(request)).get("text", "")))
        return {"ok": True}

    @app.post("/api/learn")
    async def learn(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        d = await body(request)
        sp = bot.learn(str(d.get("url", "")).strip(), str(d.get("notes", ""))[:60000])
        return {"ok": bool(sp)}

    @app.post("/api/risk")
    async def risk(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        bot.set_risk(await body(request))
        return {"ok": True, "risk": bot.risk}

    @app.post("/api/confirm")
    async def confirm(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        d = await body(request)
        try:
            pid = int(d.get("id"))
        except (TypeError, ValueError):
            return JSONResponse({"error": "identifiant manquant"}, status_code=400)
        await bot.confirm(pid, bool(d.get("accept")))
        return {"ok": True}

    @app.post("/api/live/arm")
    async def arm(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        ok, msg = await bot.arm(str((await body(request)).get("phrase", "")))
        return JSONResponse({"ok": ok, "message": msg}, status_code=200 if ok else 400)

    @app.post("/api/live/disarm")
    async def disarm(request: Request):
        if not valid(request.cookies.get(COOKIE)):
            return denied()
        bot.disarm()
        return {"ok": True}

    @app.websocket("/ws")
    async def ws(websocket: WebSocket):
        origin = websocket.headers.get("origin")
        if not valid(websocket.cookies.get(COOKIE)) or (origin and urlparse(origin).netloc != websocket.headers.get("host")):
            await websocket.close(code=1008)
            return
        await websocket.accept()
        q: asyncio.Queue = asyncio.Queue(maxsize=500)
        bot.listeners.add(q)
        try:
            await websocket.send_text(dumps({**bot.snapshot(), "logs": list(bot.logs)}))
            last = time.monotonic()
            while True:
                try:
                    ev = await asyncio.wait_for(q.get(), timeout=1.5)
                    await websocket.send_text(dumps(ev))
                except asyncio.TimeoutError:
                    pass
                if time.monotonic() - last >= 1.5:  # état complet régulier, même quand les événements s'enchaînent
                    await websocket.send_text(dumps(bot.snapshot()))
                    last = time.monotonic()
        except (WebSocketDisconnect, RuntimeError):
            pass
        finally:
            bot.listeners.discard(q)

    return app
