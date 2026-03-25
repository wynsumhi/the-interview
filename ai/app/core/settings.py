import os

from dotenv import load_dotenv

# 앱 전역 설정은 한 곳에서 로드해 각 레이어가 같은 값을 공유하도록 둔다.
load_dotenv()

GMS_KEY = os.getenv("GMS_KEY")
GMS_BASE_URL = "https://gms.ssafy.io/gmsapi"
GMS_OPENAI_BASE_URL = f"{GMS_BASE_URL}/api.openai.com/v1"
GMS_ANTHROPIC_MESSAGES_URL = f"{GMS_BASE_URL}/api.anthropic.com/v1/messages"
GMS_GEMINI_BASE_URL = f"{GMS_BASE_URL}/generativelanguage.googleapis.com/v1beta/models"

# 모델 이름과 기본 파라미터를 상수로 분리해 하드코딩 중복을 줄인다.
DEFAULT_STT_MODEL = "whisper-1"
DEFAULT_STT_LANGUAGE = "ko"
DEFAULT_STT_PROMPT = os.getenv(
    "DEFAULT_STT_PROMPT",
    (
        "면접 답변 음성만 정확히 받아쓰기 하세요. "
        "의미 있는 한국어 음성이 없으면 빈 문자열로 반환하세요. "
        "무음, 배경 소음, 음악, 방송 클로징 멘트는 텍스트로 만들지 마세요."
    ),
)
# 서버 측 no-speech 게이트의 기본 임계값이다.
# - RMS_THRESHOLD: 프레임 평균 에너지의 절대 하한선
# - PEAK_THRESHOLD: 파일 전체에서 가장 큰 에너지조차 이 값보다 작으면 거의 확실한 무음으로 본다
# - MIN_VOICED_MS:
#   "조금이라도 소리가 있었다"가 아니라 "말소리로 볼 만한 길이"가 있었는지 확인하는 기준
# - FRAME/HOP_MS: no-speech 판정용 단기 프레임 크기와 이동 간격
# 실제 운영에서는 녹음 환경에 따라 이 값들만 조정하면 동작을 미세하게 튜닝할 수 있다.
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

# `token|scope1,scope2;token2|scope3` 형식의 간단한 서비스 간 인증 설정값이다.
AUTH_TOKENS = os.getenv("AUTH_TOKENS", "")
# AUTH_ENABLED를 명시하지 않으면, 토큰이 설정된 경우에만 자동으로 인증을 활성화한다.
AUTH_ENABLED = (
    os.getenv(
        "AUTH_ENABLED",
        "true" if AUTH_TOKENS.strip() else "false",
    ).lower()
    == "true"
)

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
        "그 순간이 만들어낸 감정과, 시간이 지난 지금까지 마음에 남아 있는 이유를 들려주세요."
    ),
)
