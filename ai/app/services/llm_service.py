from app.models.llm.claude_client import create_claude_message


def create_message(
    messages: list[dict],
    system: str | None = None,
    max_tokens: int = 1024,
) -> str:
    # 현재는 Claude 구현을 사용하지만, 상위 레이어는 이 서비스 함수만 보도록 유지한다.
    return create_claude_message(messages=messages, system=system, max_tokens=max_tokens)
