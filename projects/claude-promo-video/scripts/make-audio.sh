#!/usr/bin/env bash
# Regenerate the French voice-over (Piper TTS) and the ambient pad into public/.
# Requires: python3, ffmpeg, network access to PyPI and github.com.
set -euo pipefail
cd "$(dirname "$0")/.."
VOICE_DIR=.voice
mkdir -p "$VOICE_DIR" public
[ -x "$VOICE_DIR/venv/bin/piper" ] || { python3 -m venv "$VOICE_DIR/venv"; "$VOICE_DIR/venv/bin/pip" install -q piper-tts; }
if [ ! -f "$VOICE_DIR/fr-siwis-medium.onnx" ]; then
  curl -sSL https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-fr-siwis-medium.tar.gz | tar xz -C "$VOICE_DIR"
fi
# Phonetic spellings: "you aïe, you ex" -> UI UX (English), "prompte" -> prompt.
lines=(
  "Bonjour, je suis Claude."
  "Je raisonne. Je code. Et je construis avec vous."  # periods stop the "code, et" -> "condé" liaison
  "Équipé de you aïe, you ex, Pro Max : styles, palettes, typographies, et règles UX."
  "Du prompte à l'interface. Pro, en quelques secondes."
)
for i in "${!lines[@]}"; do
  echo "${lines[$i]}" | "$VOICE_DIR/venv/bin/piper" -m "$VOICE_DIR/fr-siwis-medium.onnx" \
    --length_scale 0.95 --sentence_silence 0.15 -f "public/l$((i + 1)).wav" 2>/dev/null
done
ffmpeg -y -loglevel error -f lavfi -i "aevalsrc='0.18*sin(2*PI*110*t)+0.12*sin(2*PI*164.81*t)+0.10*sin(2*PI*220*t)*(0.6+0.4*sin(2*PI*0.25*t))+0.07*sin(2*PI*246.94*t)*(0.5+0.5*sin(2*PI*0.17*t))+0.05*sin(2*PI*329.63*t)':s=48000:d=20" \
  -af "lowpass=f=1200,afade=t=in:d=1.5,afade=t=out:st=18:d=2,volume=0.9" -ac 2 -c:a libmp3lame -b:a 160k public/pad.mp3
echo "Audio regenerated in public/"
