# Claude × UI UX Pro Max — 20 s promo video

Remotion project for a 20-second promo (French voice-over) presenting Claude equipped with the UI UX Pro Max skill.

| Composition | Size | Output |
|---|---|---|
| `Promo` | 1920×1080 (16:9) | `renders/claude-promo-20s-16x9.mp4` |
| `PromoSquare` | 1080×1080 (1:1) | `renders/claude-promo-20s-1x1.mp4` |
| `PromoVertical` | 1080×1920 (9:16) | `renders/claude-promo-20s-9x16.mp4` |

All compositions share `src/Promo.tsx`. A square or vertical canvas uses the narrow layout, and 9:16 enlarges it and keeps subtitles above the Reels/TikTok/Shorts UI area.

## Design system

Generated with `python3 src/ui-ux-pro-max/scripts/search.py "AI assistant design toolkit tech premium dark" --design-system --variance 6 --motion 7`:
HUD / Sci-Fi FUI style, background `#0B0B10`, foreground `#F8FAFC`, accent `#3B82F6`, Space Grotesk + DM Sans.

## Timeline (30 fps, 600 frames)

| Time | Voice-over | Visual |
|---|---|---|
| 0–4 s | « Bonjour, je suis Claude. » | Spark mark, letter-by-letter "Claude" |
| 4–9 s | « Je raisonne, je code, et je construis avec vous. » | Three cards lighting up in turn |
| 9–16 s | « Équipé de UI UX Pro Max : styles, palettes, typographies et règles UX. » | Counters 80 / 192 / 74 / 120, swatches |
| 16–20 s | « Du prompt à l'interface. Pro, en quelques secondes. » | Typed prompt → dashboard → CTA end card |

Subtitles are burned in for muted autoplay.

## Usage

```bash
npm install
npm run studio          # preview
npm run render          # 16:9
npm run render:square   # 1:1
npm run render:vertical # 9:16
./scripts/make-audio.sh # optional: regenerate voice-over (Piper TTS, fr-siwis-medium) and pad
```

If Remotion cannot download its headless Chrome, pass `--browser-executable=<path to chrome>`.

The spark is a generic shape, not an official logo. Check Anthropic's brand guidelines before any public distribution.
