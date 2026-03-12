import requests

from app.core.settings import (
    ANTHROPIC_VERSION,
    DEFAULT_LLM_MAX_TOKENS,
    GMS_ANTHROPIC_MESSAGES_URL,
    GMS_KEY,
    LLM_MODEL_NAME,
)

# Claude 호출에 필요한 헤더는 모델 계층에서만 관리한다.
HEADERS = {
    "Content-Type": "application/json",
    "x-api-key": GMS_KEY,
    "anthropic-version": ANTHROPIC_VERSION,
}


def create_claude_message(
    messages: list[dict],
    system: str | None = None,
    max_tokens: int = DEFAULT_LLM_MAX_TOKENS,
) -> str:
    # 서비스 계층에서는 메시지와 옵션만 넘기고, 실제 요청 포맷은 여기서 완성한다.
    payload = {
        "model": LLM_MODEL_NAME,
        "max_tokens": max_tokens,
        "messages": messages,
    }

    if system:
        payload["system"] = system

    response = requests.post(GMS_ANTHROPIC_MESSAGES_URL, headers=HEADERS, json=payload)
    response.raise_for_status()
    return response.json()["content"][0]["text"]
