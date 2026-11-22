#!/usr/bin/env python3
"""Builds the APK's assets/ from ../index.html (the same page as the claude.ai artifact).

- wraps the page in the document skeleton the artifact host normally adds
- sets window.POCHE_APP so the page stores data on the phone without the claude.ai banner
- swaps Google Fonts for the bundled copies in fonts/ (works offline)
"""
import json, os, re, shutil, sys
from pathlib import Path

here = Path(__file__).resolve().parent
out = Path(sys.argv[1] if len(sys.argv) > 1 else here / "build" / "assets")
page = (here.parent / "index.html").read_text(encoding="utf-8")

page, n = re.subn(r'<link rel="preconnect"[^>]*>\s*', "", page)
page, m = re.subn(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com/[^"]*">',
                  '<link rel="stylesheet" href="fonts/fonts.css">', page)
if m != 1:
    sys.exit("make_assets: Google Fonts link not found in index.html")

head = ('<!doctype html><html lang="fr"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
        '<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);'
        'padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui}'
        'img{max-width:100%}[hidden]{display:none!important}</style>'
        '<script>window.POCHE_APP=true;window.POCHE_VERSION='
        + json.dumps(os.environ.get("POCHE_VERSION", "")) + '</script></head><body>')

if out.exists():
    shutil.rmtree(out)
(out / "fonts").mkdir(parents=True)
(out / "index.html").write_text(head + page + "</body></html>", encoding="utf-8")
for f in (here / "fonts").iterdir():
    shutil.copy(f, out / "fonts" / f.name)
print(f"assets -> {out}")
