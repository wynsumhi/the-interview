import os

import anthropic
from dotenv import load_dotenv

# .env 파일에서 환경 변수 로드
load_dotenv()

client = anthropic.Anthropic(
    api_key=os.getenv("GMS_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/",
)


def test_simple_message():
    """
    간단한 메시지 생성 테스트 함수
    """
    response = client.messages.create(
        model="claude-sonnet-4-5-20250929",
        max_tokens=128,
        messages=[{"role": "user", "content": "Hello"}],
    )

    assert response is not None
    assert len(response.content) > 0
    assert response.content[0].type == "text"
    assert len(response.content[0].text) > 0
