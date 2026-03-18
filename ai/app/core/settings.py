import os

from dotenv import load_dotenv

# 앱 전역 설정은 한 곳에서 로드해 각 레이어가 같은 값을 공유하도록 둔다.
load_dotenv()

GMS_KEY = os.getenv("GMS_KEY")
GMS_BASE_URL = "https://gms.ssafy.io/gmsapi"
GMS_OPENAI_BASE_URL = f"{GMS_BASE_URL}/api.openai.com/v1"
GMS_ANTHROPIC_MESSAGES_URL = f"{GMS_BASE_URL}/api.anthropic.com/v1/messages"

# 모델 이름과 기본 파라미터를 상수로 분리해 하드코딩 중복을 줄인다.
DEFAULT_STT_MODEL = "whisper-1"
DEFAULT_STT_LANGUAGE = "ko"

LLM_MODEL_NAME = "claude-sonnet-4-5-20250929"
DEFAULT_LLM_MAX_TOKENS = 1024
ANTHROPIC_VERSION = "2023-06-01"

DEFAULT_TTS_MODEL = os.getenv("DEFAULT_TTS_MODEL", "supertonic-2")
DEFAULT_TTS_VOICE = os.getenv("DEFAULT_TTS_VOICE", "F3")
DEFAULT_TTS_LANGUAGE = os.getenv("DEFAULT_TTS_LANGUAGE", "ko")
DEFAULT_TTS_PROVIDER = os.getenv("DEFAULT_TTS_PROVIDER", "cuda")
DEFAULT_TTS_STEPS = int(os.getenv("DEFAULT_TTS_STEPS", "5"))
DEFAULT_TTS_SPEED = float(os.getenv("DEFAULT_TTS_SPEED", "1.05"))
DEFAULT_TTS_AUTO_DOWNLOAD = os.getenv("DEFAULT_TTS_AUTO_DOWNLOAD", "false").lower() == "true"
