import os

import anthropic
from dotenv import load_dotenv

# .env 파일에서 환경 변수 로드
load_dotenv()

client = anthropic.Anthropic(
    api_key=os.getenv("GMS_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/",
)
