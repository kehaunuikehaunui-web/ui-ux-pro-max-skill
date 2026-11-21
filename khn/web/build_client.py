"""Assemble web/index.html : gabarit du client + code de rendu partagé avec le prototype (pixel art, robots, police).

Lancer depuis khn/ : python web/build_client.py
"""
from __future__ import annotations

import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
proto = open(os.path.join(ROOT, "prototype", "index.html"), encoding="utf-8").read()
tpl = open(os.path.join(ROOT, "web", "template.html"), encoding="utf-8").read()


def section(start: str, end: str) -> str:
    i, j = proto.index(start), proto.index(end)
    return proto[i:j].rstrip() + "\n"


head = proto[: proto.index("</style>") + len("</style>")]
font = section("/* ================= Police pixel", "/* ================= Marché simulé")
carte = section("/* ================= Monde pixel : carte & stations", "/* ================= Robots")
parts = section("/* ================= Particules", "/* ================= Rendu du monde")
render = section("/* ================= Rendu du monde", "/* ================= Graphique")

# Adaptations au client : marchés configurables et montants affichés en XPF.
swaps = [
    ("const keys = ['BTC', 'ETH', 'SOL'];", "const mk = Object.keys(M), keys = [0, 1, 2].map(i => mk[i % mk.length]);"),
    ("[[19, 'BTC'], [35, 'ETH'], [67, 'SOL'], [83, 'BTC']]", "[[19, keys[0]], [35, keys[1]], [67, keys[2]], [83, keys[0]]]"),
    ("miniLine(M[i === 3 ? 'ETH' : k]", "miniLine(M[i === 3 ? keys[1] : k]"),
    ("nf0.format(Math.abs(Math.round(e - d0)))", "nf0.format(Math.abs(Math.round(toX(e - d0))))"),
]
for a, b in swaps:
    if a not in render:
        raise SystemExit(f"Passage introuvable dans le prototype : {a}")
    render = render.replace(a, b)

out = (tpl.replace("<!--@@HEAD@@-->", head).replace("/*@@FONT@@*/", font).replace("/*@@MAP@@*/", carte)
       .replace("/*@@PARTS@@*/", parts).replace("/*@@RENDER@@*/", render))
out = re.sub(r"<title>[^<]*</title>", "<title>Bureau KHN</title>", out, count=1)
html = "<!doctype html>\n<html lang=\"fr\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n" + out.replace("<style>", "</head>\n<body>\n<style>", 0)
# Le gabarit commence par l'en-tête du prototype (title, liens, style) : on ferme <head> après le premier </style>.
first = html.index("</style>") + len("</style>")
html = html[:first] + "\n</head>\n<body>" + html[first:] + "\n</body>\n</html>\n"
with open(os.path.join(ROOT, "web", "index.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("web/index.html généré", len(html), "octets")
