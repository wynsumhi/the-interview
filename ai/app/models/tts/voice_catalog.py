SUPERTONIC_SAMPLE_TEXT = "안녕하세요. 만나서 반갑습니다!"

# Supertonic SDK exposes internal voice ids such as F1 or M3.
SUPERTONIC_VOICE_IDS = (
    "F1",
    "F2",
    "F3",
    "F4",
    "F5",
    "M1",
    "M2",
    "M3",
    "M4",
    "M5",
)

SUPERTONIC_VOICE_NAME_TO_ID = {
    "Robert": "M3",
    "Sam": "M4",
    "Sarah": "F1",
    "Jessica": "F3",
}

SUPERTONIC_DEFAULT_VOICE_NAME = "Jessica"
SUPERTONIC_DEFAULT_VOICE_ID = "F3"


def resolve_supertonic_voice_id(voice: str | None) -> str:
    normalized_voice = (voice or "").strip()
    if not normalized_voice:
        return SUPERTONIC_DEFAULT_VOICE_ID
    if normalized_voice in SUPERTONIC_VOICE_IDS:
        return normalized_voice
    if normalized_voice in SUPERTONIC_VOICE_NAME_TO_ID:
        return SUPERTONIC_VOICE_NAME_TO_ID[normalized_voice]
    raise ValueError(f"Unsupported TTS voice: {voice}")
