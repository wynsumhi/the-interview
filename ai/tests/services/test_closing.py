import os
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.interview_service import generate_closing_message

result_dir = Path(__file__).parent.parent / "result"

with patch("app.services.interview_service.text_to_speech", return_value=b""):
    for interview_file in ["interview_2.md", "interview_3.md"]:
        context_text = (result_dir / interview_file).read_text(encoding="utf-8")
        result = generate_closing_message(interview_context=context_text)
        print(f"[{interview_file}]")
        print(result["closing_message"])
        print()
