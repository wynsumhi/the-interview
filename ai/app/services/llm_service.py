import os

import requests
from dotenv import load_dotenv

# .env 파일에서 환경 변수 로드
load_dotenv()

# SDK 사용 방식이 아닌 requests 라이브러리를 사용하여 직접 API 호출
GMS_API_URL = "https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages"
GMS_KEY = os.getenv("GMS_KEY")
MODEL_NAME = "claude-sonnet-4-5-20250929"

HEADERS = {
    "Content-Type": "application/json",
    "x-api-key": GMS_KEY,
    "anthropic-version": "2023-06-01",
}


def create_message(
    messages: list[dict],
    system: str = None,
    max_tokens: int = 1024,
) -> str:
    """
    GMS Claude API에 메시지를 전송하고 응답 텍스트를 반환하는 함수
    """
    payload = {
        "model": MODEL_NAME,
        "max_tokens": max_tokens,
        "messages": messages,
    }

    if system:
        payload["system"] = system

    response = requests.post(GMS_API_URL, headers=HEADERS, json=payload)
    response.raise_for_status()

    return response.json()["content"][0]["text"]
