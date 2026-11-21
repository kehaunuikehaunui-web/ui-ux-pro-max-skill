"""Équipe de robots : file de tâches, spécialités, recrutement quand KHN est débordé.

Chaque tâche est réellement exécutée par un robot (le handler du bot) après sa durée de travail.
Les événements émis servent à animer le bureau pixel.
"""
from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from typing import Awaitable, Callable

# type: (durée en s, priorité, station, rôle spécialiste)
TASKS = {
    "scan": (1.0, 3.0, "scan", "SCOUT"),
    "order": (0.6, 5.0, "exec", "EXEC"),
    "post": (3.0, 2.0, "board", "QUANT"),
    "corr": (6.0, 2.1, "rack", "QUANT"),
    "bt": (5.0, 2.5, "rack", "QUANT"),
    "video": (8.0, 2.2, "tv", "LEARN"),
    "audit": (2.0, 1.5, "risk", "RISK"),
}
TASK_FR = {"scan": "surveillance", "order": "ordre", "post": "analyse de trade", "corr": "auto-correction",
           "bt": "backtest", "video": "étude vidéo", "audit": "contrôle du risque"}
ROLES = {
    "KHN": ("Chef trader", []),
    "SCOUT": ("Surveillance des marchés", ["scan"]),
    "EXEC": ("Exécution des ordres", ["order"]),
    "QUANT": ("Analyse et auto-correction", ["post", "corr", "bt"]),
    "LEARN": ("Étude des stratégies", ["video"]),
    "RISK": ("Contrôle du risque", ["audit"]),
}
CRITICAL = {"scan": ("KHN", "SCOUT"), "order": ("KHN", "EXEC")}  # tâches urgentes : pas de renfort non spécialiste


@dataclass
class Task:
    id: int
    type: str
    data: dict
    created: float = field(default_factory=time.monotonic)
    expires: float | None = None
    agent: str | None = None


@dataclass
class Agent:
    name: str
    state: str = "idle"  # idle | work | rest
    task: Task | None = None
    done: int = 0
    idle_since: float = field(default_factory=time.monotonic)

    @property
    def spec(self) -> list[str]:
        return ROLES[self.name][1]


Handler = Callable[[Task, Agent], Awaitable[None]]
Emit = Callable[[dict], None]


class Team:
    def __init__(self, handler: Handler, emit: Emit, log: Callable[[str, str, str], None], speed: float = 1.0):
        self.handler, self.emit, self.log = handler, emit, log
        self.speed = speed  # < 1 accélère (tests)
        self.agents: list[Agent] = [Agent("KHN")]
        self.queue: list[Task] = []
        self._seq = 0
        self._last_recruit = -1e9
        self._last_miss_log = -1e9
        self._workers: set[asyncio.Task] = set()
        self.missed: list[tuple[float, str]] = []

    def get(self, name: str) -> Agent | None:
        return next((a for a in self.agents if a.name == name), None)

    def push(self, type_: str, data: dict | None = None, ttl: float | None = None) -> Task:
        self._seq += 1
        t = Task(self._seq, type_, data or {}, expires=(time.monotonic() + ttl) if ttl else None)
        self.queue.append(t)
        return t

    def pending(self, type_: str, pred=lambda d: True) -> bool:
        return any(t.type == type_ and pred(t.data) for t in self.queue)

    def recruit(self, name: str, reason: str = "", overload: bool = False) -> bool:
        if name not in ROLES or self.get(name) or len(self.agents) >= 6:
            return False
        self.agents.append(Agent(name))
        if overload:  # seul un recrutement pour surcharge déclenche le délai avant le suivant
            self._last_recruit = time.monotonic()
        self.emit({"type": "team", "action": "join", "name": name})
        self.log("KHN", "team", f"{name} rejoint l'équipe ({ROLES[name][0].lower()}){' : ' + reason if reason else ''}.")
        return True

    def dismiss(self, name: str) -> bool:
        a = self.get(name)
        if not a or name == "KHN" or a.state == "work":
            return False
        self.agents.remove(a)
        self.emit({"type": "team", "action": "leave", "name": name})
        self.log("KHN", "team", f"{name} quitte le bureau.")
        return True

    def _score(self, a: Agent, t: Task) -> float | None:
        crit = CRITICAL.get(t.type)
        if crit and a.name not in crit:
            return None
        mine = t.type in a.spec
        sc = TASKS[t.type][1] + (2.5 if mine else 0)
        if a.name != "KHN" and a.spec and not mine:
            sc -= 1.2
        if not mine and any(o is not a and o.state != "work" and t.type in o.spec for o in self.agents):
            sc -= 6
        return sc if sc > 0 else None

    def _assign(self) -> None:
        now = time.monotonic()
        for t in list(self.queue):
            if t.expires and not t.agent and now > t.expires:
                self.queue.remove(t)
                self.missed.append((now, t.type))
                if now - self._last_miss_log > 30 * self.speed:
                    self._last_miss_log = now
                    self.log("KHN", "error", f"Débordé : {TASK_FR[t.type]} {t.data.get('k', '')} non faite à temps.")
        for a in self.agents:
            if a.state == "work":
                continue
            best, bs = None, -1.0
            for t in self.queue:
                if t.agent:
                    continue
                sc = self._score(a, t)
                if sc is not None and sc > bs:
                    best, bs = t, sc
            if best:
                best.agent, a.task, a.state = a.name, best, "work"
                w = asyncio.create_task(self._work(a, best))
                self._workers.add(w)
                w.add_done_callback(self._workers.discard)
            elif a.state == "idle" and a.name != "KHN" and now - a.idle_since > 120:
                a.state = "rest"

    async def _work(self, a: Agent, t: Task) -> None:
        dur, _, station, _ = TASKS[t.type]
        dur = dur / (1.6 if t.type in a.spec else 1.0) * self.speed
        self.emit({"type": "task", "phase": "start", "agent": a.name, "task": t.type, "k": t.data.get("k"), "station": station, "dur": dur})
        try:
            await asyncio.sleep(dur)
            await self.handler(t, a)
        except asyncio.CancelledError:
            raise
        except Exception as e:  # une tâche ratée ne doit jamais arrêter le bureau
            self.log(a.name, "error", f"Tâche {TASK_FR[t.type]} en échec : {e}")
        finally:
            if t in self.queue:
                self.queue.remove(t)
            a.done += 1
            a.task, a.state, a.idle_since = None, "idle", time.monotonic()
            self.emit({"type": "task", "phase": "done", "agent": a.name, "task": t.type, "k": t.data.get("k")})

    def _recruit_check(self) -> None:
        now = time.monotonic()
        if now - self._last_recruit < 60 * self.speed or len(self.agents) >= 6:
            return
        self.missed = [m for m in self.missed if now - m[0] < 900 * self.speed]
        waiting = [t for t in self.queue if not t.agent and now - t.created > 4 * self.speed]
        if len(self.missed) >= 2 or waiting:
            cnt: dict[str, int] = {}
            for typ in [m[1] for m in self.missed] + [t.type for t in waiting]:
                role = TASKS[typ][3]
                cnt[role] = cnt.get(role, 0) + 1
            for role, _ in sorted(cnt.items(), key=lambda x: -x[1]):
                if not self.get(role):
                    why = f"{len(self.missed)} tâches ratées récemment" if len(self.missed) >= 2 else f"{len(waiting)} tâche(s) en attente"
                    self.recruit(role, why, overload=True)
                    break

    async def run(self) -> None:
        tick = 0
        while True:
            self._assign()
            tick += 1
            if tick % 8 == 0:
                self._recruit_check()
            await asyncio.sleep(0.25 * min(1.0, self.speed * 4))

    def snapshot(self) -> list[dict]:
        return [{"name": a.name, "role": ROLES[a.name][0], "state": a.state, "done": a.done,
                 "task": {"type": a.task.type, "k": a.task.data.get("k")} if a.task else None} for a in self.agents]

    def queue_counts(self) -> dict[str, int]:
        out: dict[str, int] = {}
        for t in self.queue:
            out[t.type] = out.get(t.type, 0) + 1
        return out
