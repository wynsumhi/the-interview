from openai import OpenAI
from dotenv import load_dotenv
import os

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url= "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

def text_to_speech(text: str):
    response = client.audio.speech.create(
        model="gpt-4o-mini-tts",
        input=text,
        voice="alloy"
    )
    return response