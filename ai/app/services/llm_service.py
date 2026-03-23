import logging
import time

from app.models.llm.claude_client import create_claude_message

logger = logging.getLogger("uvicorn.error")


def create_message(
    messages: list[dict],
    system: str | None = None,
    max_tokens: int = 1024,
) -> str:
    """LLM 호출 시간과 생성된 응답 텍스트를 서비스 로그로 남긴다.

    다음 질문 생성 결과와 응답 지연을 같은 위치에서 확인할 수 있도록
    모델이 반환한 최종 텍스트를 로그에 기록한다.
    """

    started = time.perf_counter()
    llm_text = create_claude_message(messages=messages, system=system, max_tokens=max_tokens)
    elapsed = time.perf_counter() - started

    logger.info(
        "[LLM] completed in %.3fs max_tokens=%s text=%s",
        elapsed,
        max_tokens,
        llm_text,
    )
    return llm_text
