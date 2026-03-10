import os
import sys

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.llm_service import create_message

response = create_message(
    messages=[{"role": "user", "content": "안녕하세요. 오늘 날씨가 좋네요."}],
)
print("응답 텍스트:", response)
