import requests

from app.core.settings import GMS_GEMINI_BASE_URL, GMS_KEY


def create_gemini_message(
    *,
    model: str,
    user_content: str,
    system: str | None = None,
    max_tokens: int,
) -> str:
    payload = {
        "contents": [{"parts": [{"text": user_content}]}],
        "generationConfig": {"maxOutputTokens": max_tokens},
    }
    if system:
        payload["system_instruction"] = {"parts": [{"text": system}]}

    response = requests.post(
        f"{GMS_GEMINI_BASE_URL}/{model}:generateContent",
        params={"key": GMS_KEY},
        headers={"Content-Type": "application/json"},
        json=payload,
        timeout=60,
    )
    response.raise_for_status()

    data = response.json()
    candidates = data.get("candidates") or []
    if not candidates:
        raise ValueError(f"No candidates in Gemini response: {data}")

    parts = (candidates[0].get("content") or {}).get("parts") or []
    texts = [str(part.get("text", "")).strip() for part in parts if isinstance(part, dict)]
    texts = [text for text in texts if text]
    if texts:
        return "\n".join(texts)

    finish_reason = candidates[0].get("finishReason")
    raise ValueError(
        f"Gemini response did not contain text. finish_reason={finish_reason}, raw={data}"
    )
