import os
import sys
from pathlib import Path

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.interview_service import generate_next_opening_question

interview_context = Path(__file__).parent / "interview_context.md"
context_text = interview_context.read_text(encoding="utf-8")

result = generate_next_opening_question(
    interview_context=context_text,
    nickname="길동",
)
print(result["db_question"])
