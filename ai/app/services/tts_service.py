import os

from dotenv import load_dotenv
from openai import OpenAI

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)


def text_to_speech(question_text: str) -> bytes:
    """
    인터뷰 질문을 실시간으로 TTS 변환하여 음성 데이터를 반환하는 함수
    파일로 저장하지 않고 바로 반환한다.
    """
    response = client.audio.speech.create(
        model="gpt-4o-mini-tts",
        input=question_text,
        voice="alloy",
    )

    return response.content
