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
SUPERTONIC_DEFAULT_VOICE_ID = SUPERTONIC_VOICE_NAME_TO_ID[SUPERTONIC_DEFAULT_VOICE_NAME]
