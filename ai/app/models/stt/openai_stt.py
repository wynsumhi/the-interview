from openai import OpenAI

from app.core.settings import DEFAULT_STT_LANGUAGE, DEFAULT_STT_MODEL, GMS_KEY, GMS_OPENAI_BASE_URL

# OpenAI 기반 STT 클라이언트는 provider 구현 세부사항이므로 models 레이어에 둔다.
client = OpenAI(
    api_key=GMS_KEY,
    base_url=GMS_OPENAI_BASE_URL,
)


def transcribe_audio_file(
    audio_file_path: str,
    model: str = DEFAULT_STT_MODEL,
    language: str = DEFAULT_STT_LANGUAGE,
) -> str:
    # 파일 입출력과 외부 SDK 호출은 이 계층에서 끝내고 텍스트만 반환한다.
    with open(audio_file_path, "rb") as audio_file:
        response = client.audio.transcriptions.create(
            file=audio_file,
            model=model,
            language=language,
        )
    return response.text
