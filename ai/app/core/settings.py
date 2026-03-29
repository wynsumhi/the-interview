import os

from dotenv import load_dotenv

# Centralized app settings.
load_dotenv()

GMS_KEY = os.getenv("GMS_KEY")
GMS_BASE_URL = "https://gms.ssafy.io/gmsapi"
GMS_OPENAI_BASE_URL = f"{GMS_BASE_URL}/api.openai.com/v1"
GMS_ANTHROPIC_MESSAGES_URL = f"{GMS_BASE_URL}/api.anthropic.com/v1/messages"
GMS_GEMINI_BASE_URL = f"{GMS_BASE_URL}/generativelanguage.googleapis.com/v1beta/models"

DEFAULT_STT_MODEL = "whisper-1"
DEFAULT_STT_LANGUAGE = "ko"

# Energy-based no-speech gate defaults.
STT_NO_SPEECH_RMS_THRESHOLD = float(os.getenv("STT_NO_SPEECH_RMS_THRESHOLD", "0.006"))
STT_NO_SPEECH_PEAK_THRESHOLD = float(os.getenv("STT_NO_SPEECH_PEAK_THRESHOLD", "0.02"))
STT_NO_SPEECH_MIN_VOICED_MS = int(os.getenv("STT_NO_SPEECH_MIN_VOICED_MS", "120"))
STT_NO_SPEECH_FRAME_MS = int(os.getenv("STT_NO_SPEECH_FRAME_MS", "30"))
STT_NO_SPEECH_HOP_MS = int(os.getenv("STT_NO_SPEECH_HOP_MS", "10"))

LLM_MODEL_NAME = "claude-sonnet-4-5-20250929"
TITLE_LLM_MODEL_NAME = os.getenv("TITLE_LLM_MODEL_NAME", "gemini-2.5-flash-lite")
KEYWORD_LLM_MODEL_NAME = os.getenv("KEYWORD_LLM_MODEL_NAME", "gemini-2.5-flash-lite")
DEFAULT_LLM_MAX_TOKENS = 1024
ANTHROPIC_VERSION = "2023-06-01"
DEFAULT_EMBEDDING_MODEL = os.getenv(
    "DEFAULT_EMBEDDING_MODEL",
    "Qwen/Qwen3-Embedding-0.6B",
)
DEFAULT_EMBEDDING_BASE_URL = os.getenv(
    "DEFAULT_EMBEDDING_BASE_URL",
    GMS_OPENAI_BASE_URL,
)
DEFAULT_EMBEDDING_API_KEY = os.getenv("DEFAULT_EMBEDDING_API_KEY", GMS_KEY)
DEFAULT_EMBEDDING_TIMEOUT_SECONDS = float(os.getenv("DEFAULT_EMBEDDING_TIMEOUT_SECONDS", "10.0"))

# Format: token|scope1,scope2;token2|scope3
AUTH_TOKENS = os.getenv("AUTH_TOKENS", "")
AUTH_ENABLED = (
    os.getenv(
        "AUTH_ENABLED",
        "true" if AUTH_TOKENS.strip() else "false",
    ).lower()
    == "true"
)

APP_ENV = os.getenv("APP_ENV", "local").strip().lower()
BACKEND_INTERNAL_BASE_URL_LOCAL = os.getenv("BACKEND_INTERNAL_BASE_URL_LOCAL", "").rstrip("/")
BACKEND_INTERNAL_BASE_URL_PROD = os.getenv("BACKEND_INTERNAL_BASE_URL_PROD", "").rstrip("/")
_BACKEND_INTERNAL_BASE_URL = os.getenv("BACKEND_INTERNAL_BASE_URL", "").rstrip("/")
if _BACKEND_INTERNAL_BASE_URL:
    BACKEND_INTERNAL_BASE_URL = _BACKEND_INTERNAL_BASE_URL
elif APP_ENV in {"prod", "production"}:
    BACKEND_INTERNAL_BASE_URL = BACKEND_INTERNAL_BASE_URL_PROD
else:
    BACKEND_INTERNAL_BASE_URL = BACKEND_INTERNAL_BASE_URL_LOCAL
BACKEND_INTERNAL_TOKEN = os.getenv("BACKEND_INTERNAL_TOKEN", "")
BACKEND_INTERNAL_TIMEOUT_SECONDS = float(os.getenv("BACKEND_INTERNAL_TIMEOUT_SECONDS", "5.0"))

QDRANT_URL = os.getenv("QDRANT_URL", "")
QDRANT_API_KEY = os.getenv("QDRANT_API_KEY")
QDRANT_COLLECTION_NAME = os.getenv("QDRANT_COLLECTION_NAME", "interview_history")
QDRANT_VECTOR_SIZE = int(os.getenv("QDRANT_VECTOR_SIZE", "1024"))
QDRANT_TIMEOUT_SECONDS = float(os.getenv("QDRANT_TIMEOUT_SECONDS", "30.0"))

DEFAULT_TTS_MODEL = os.getenv("DEFAULT_TTS_MODEL", "supertonic-2")
DEFAULT_TTS_VOICE = os.getenv("DEFAULT_TTS_VOICE", "F3")
DEFAULT_TTS_LANGUAGE = os.getenv("DEFAULT_TTS_LANGUAGE", "ko")
DEFAULT_TTS_PROVIDER = os.getenv("DEFAULT_TTS_PROVIDER", "cuda")
DEFAULT_TTS_STEPS = int(os.getenv("DEFAULT_TTS_STEPS", "5"))
DEFAULT_TTS_SPEED = float(os.getenv("DEFAULT_TTS_SPEED", "1.05"))
DEFAULT_TTS_AUTO_DOWNLOAD = os.getenv("DEFAULT_TTS_AUTO_DOWNLOAD", "false").lower() == "true"
DEFAULT_TTS_PRELOAD_ON_STARTUP = (
    os.getenv("DEFAULT_TTS_PRELOAD_ON_STARTUP", "false").lower() == "true"
)
DEFAULT_TTS_WARMUP_TEXT = os.getenv(
    "DEFAULT_TTS_WARMUP_TEXT",
    (
        "지금의 당신을 가장 선명하게 보여주는 한 장면이 있다면 무엇인가요? "
        "그 순간의 감정과 지금까지 남아 있는 이유를 들려주세요."
    ),
)
