"""Récupération de la transcription d'une vidéo YouTube."""
from __future__ import annotations

import asyncio
import re

LANGS = ("fr", "en", "es", "pt", "de")
_ID = re.compile(r"(?:v=|youtu\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})")


def video_id(url: str) -> str | None:
    m = _ID.search(url or "")
    return m.group(1) if m else None


def _fetch(vid: str) -> str:
    from youtube_transcript_api import YouTubeTranscriptApi

    fetched = YouTubeTranscriptApi().fetch(vid, languages=LANGS)
    return " ".join(s.text.replace("\n", " ") for s in fetched).strip()


async def transcript(url: str) -> tuple[str | None, str | None]:
    """Renvoie (texte, None) ou (None, explication en français)."""
    vid = video_id(url)
    if not vid:
        return None, "lien non reconnu comme une vidéo YouTube"
    try:
        text = await asyncio.wait_for(asyncio.to_thread(_fetch, vid), timeout=45)
    except asyncio.TimeoutError:
        return None, "YouTube n'a pas répondu à temps"
    except Exception as e:  # la librairie lève une exception typée par cas
        name = type(e).__name__
        why = {
            "TranscriptsDisabled": "les sous-titres sont désactivés sur cette vidéo",
            "NoTranscriptFound": "aucune transcription en français ou en anglais",
            "VideoUnavailable": "vidéo indisponible",
            "IpBlocked": "YouTube bloque l'adresse IP du serveur",
            "RequestBlocked": "YouTube bloque l'adresse IP du serveur",
            "AgeRestricted": "vidéo soumise à une limite d'âge",
        }.get(name, f"échec de la récupération ({name})")
        return None, why
    return (text, None) if text else (None, "transcription vide")
