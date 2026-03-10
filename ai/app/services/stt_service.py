import os

from dotenv import load_dotenv
from openai import OpenAI

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)


def speech_to_text(audio_file_path: str) -> str:
    """
    음성 파일을 STT 변환하여 텍스트를 반환하는 함수

    Args:
        audio_file_path (str): 변환할 음성 파일의 경로
    """
    with open(audio_file_path, "rb") as audio_file:
        response = client.audio.transcriptions.create(
            file=audio_file,
            model="whisper-1",
            language="ko",  # 한국어로 설정
        )
    return response.text
