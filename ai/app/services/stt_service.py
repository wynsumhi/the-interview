from openai import OpenAI
from dotenv import load_dotenv
import os

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url= "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

def speech_to_text(audio_file_path: str):
    """
    음성 파일을 텍스트로 변환하는 함수.

    Args:
        audio_file_path (str): 변환할 음성 파일의 경로.
    
    Returns:
        변환된 텍스트 문자열
    """
    response = client.audio.transcriptions.create(
        file=open(audio_file_path, "rb"), # 음성 파일을 바이너리 모드로 읽기
        model="whisper-1",
        language="ko", # 한국어로 설정

    )
    return response.text