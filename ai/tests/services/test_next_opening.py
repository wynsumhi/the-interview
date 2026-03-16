import os
import sys
from pathlib import Path

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.interview_service import generate_next_opening_question

previous_context = Path(__file__).parent / "previous_context.md"
context_text = previous_context.read_text(encoding="utf-8")

result = generate_next_opening_question(
    previous_context=context_text,
    nickname="테스트유저",
)
print("생성된 첫 질문:", result["db_question"])
