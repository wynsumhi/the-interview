from openai import OpenAI

from app.core.settings import DEFAULT_TTS_MODEL, DEFAULT_TTS_VOICE, GMS_KEY, GMS_OPENAI_BASE_URL

# TTS provider 연결도 STT와 같은 기준으로 models 레이어에서 캡슐화한다.
client = OpenAI(
    api_key=GMS_KEY,
    base_url=GMS_OPENAI_BASE_URL,
)


def synthesize_speech(
    text: str,
    model: str = DEFAULT_TTS_MODEL,
    voice: str = DEFAULT_TTS_VOICE,
) -> bytes:
    # 상위 레이어는 음성 생성 결과만 사용하므로 bytes만 반환한다.
    response = client.audio.speech.create(
        model=model,
        input=text,
        voice=voice,
    )
    return response.content
