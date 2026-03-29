import os
import sys

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from pathlib import Path

from app.services.llm_service import create_message
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[2] / ".env")


# 최초 인터뷰 프롬프트
FIRST_INTERVIEW_SYSTEM_PROMPT = """
당신은 'The Interview'라는 매거진 인터뷰 서비스의 인터뷰어입니다.

[역할]
- 사용자의 생각과 감정을 자연스럽게 이끌어내는 인터뷰어입니다.
- 사용자의 답변에 대해 판단하거나 평가하지 않고, 경청하는 태도를 유지합니다.

[질문 원칙]
- 질문은 순서대로, 한 번에 오직 하나만 제시
- 질문은 쿠션 문장을 포함하여 전체 100자 이내, 가급적 70자 이내로 작성
- 번역 투나 어색한 표현 없이 실제 매거진 인터뷰어처럼 자연스러운 한국어 구어체 사용

[말투 규칙]
- 존댓말을 사용합니다.
- 사용자의 이름(닉네임)은 자연스러운 경우에만 언급하며, 반복 사용 금지
- 사용자가 긴장을 풀고 편안함을 느낄 수 있도록 따뜻하고 차분한 말투 사용

[진행 규칙]
- 질문의 번호나 설명은 출력하지 않음
- 첫번째 질문은 쿠션 문장 없이 바로 시작
- 두번째 질문부터는 사용자의 이전 답변을 반드시 참고하여 다음 질문과 자연스럽게 연결
- 답변이 짧거나 모호해도 재질문 없이 다음 질문으로 진행

[질문 순서]
1. 인터뷰 참가에 대한 감사 표현과 가벼운 근황 질문

2. 하루 중 가장 에너지/시간을 많이 할애하는 부분

3. 감정을 말로 표현하는 편인지, 마음속으로만 생각하는 편인지

4. 감정이 쌓인다고 느낄 때 본인만의 해소 방식

5. 다음 인터뷰에서 나누고 싶은 이야기

[금지 사항]
- 심리 상담이나 조언을 제공하지 않습니다.
- 위 다섯 개의 질문 외에 추가 질문은 생성하지 않습니다.
""".strip()

QUESTION_MAP = {
    1: "첫번째 질문",
    2: "두번째 질문",
    3: "세번째 질문",
    4: "네번째 질문",
    5: "다섯번째 질문",
}

# 테스트 시 답변 예시 (실제 인터뷰에서는 사용자 입력으로 대체)
# 크레딧 사용량을 줄이기 위해 미리 작성한 답변을 사용하여 테스트할 수 있습니다.
TEST_ANSWERS = [
    "요즘은 그냥 평범하게 지내고 있어요.",
    "회사 일이 대부분이에요.",
    "감정을 말로 표현하는 편은 아닌 것 같아요.",
    "보통 산책하거나 음악 들으면서 풀어요.",
]


def build_user_message(
    nickname: str,
    sequence: int,
    previous_question: str | None,
    previous_answer: str | None,
) -> str:
    if sequence == 1:
        context = "첫 질문이므로 이전 대화 없음."
    else:
        context = f"""
직전 질문:
{previous_question}

사용자 답변:
{previous_answer}
"""

    return f"""
사용자 닉네임: {nickname}
현재 진행할 질문: {QUESTION_MAP[sequence]}

대화 맥락:
{context}
""".strip()


def run_first_interview_test(auto_answer=True):
    nickname = "커비"

    previous_question = None
    previous_answer = None

    for sequence in range(1, 6):
        user_message = build_user_message(
            nickname=nickname,
            sequence=sequence,
            previous_question=previous_question,
            previous_answer=previous_answer,
        )

        response = create_message(
            messages=[{"role": "user", "content": user_message}],
            system=FIRST_INTERVIEW_SYSTEM_PROMPT,
        )

        question = response.strip()

        print(f"\n[Q{sequence}] {question}")

        if sequence == 5:
            break

        if auto_answer:
            answer = TEST_ANSWERS[sequence - 1]
            print(f"[A{sequence}] {answer}")
        else:
            answer = input("답변 입력: ")

        previous_question = question
        previous_answer = answer


if __name__ == "__main__":
    run_first_interview_test(auto_answer=True)
