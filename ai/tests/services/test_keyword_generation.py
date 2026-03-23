import os
import sys

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.prompts.interview_prompts import KEYWORD_GENERATION_SYSTEM_PROMPT
from app.services.llm_service import create_message

"""
북마크 키워드 생성 프롬프트 테스트

질문-답변 쌍을 직접 지정하여
KEYWORD_GENERATION_SYSTEM_PROMPT 가 적절한 키워드를 반환하는지 수동으로 확인합니다.

실행 방법:
    cd ai
    python tests/services/test_keyword_generation.py
"""

# 테스트용 Q&A 쌍
# interview_2와 interview_3에서 북마크할 만한 질문-답변 쌍을 직접 지정했습니다.
BOOKMARKED_QA_PAIRS = [
    # interview_2
    {
        "question": (
            "그 순간이 소희님에게 특별한 이유가 있을 것 같아요. "
            "디저트를 함께 먹으며 나눴던 대화나 느꼈던 감정 중에서 "
            "기억에 남는 장면이 있나요?"
        ),
        "answer": "별 거 아닌 이야기지만 말하다가 함께 웃음을 터뜨린 순간 같은 게 기억에 남아요.",
    },
    {
        "question": (
            "함께 웃는 순간이 주는 특별함이 있죠. 소희님에게 그 사람과의 웃음은 어떤 의미인가요?"
        ),
        "answer": "걱정 없는, 편안함 그 자체의, 즐거움",
    },
    {
        "question": (
            "그런 편안함을 느낄 수 있는 관계가 소중하게 느껴지네요. "
            "소희님은 평소에 그런 편안함을 자주 느끼는 편인가요, "
            "아니면 특정한 사람이나 순간에만 느껴지나요?"
        ),
        "answer": "특정한 사람들 앞에서 느끼는 편 같아요",
    },
    # interview_3
    {
        "question": "일에서 느끼는 재미는 어떤 순간에 가장 크게 느껴지나요?",
        "answer": (
            "무언가를 해결했을 때. 문제가 발생하지 않아도 제가 생각한 방향으로 "
            "흘러갈 때 재밌어요. 문제가 발생하면 해결하는 즐거움도 있고."
        ),
    },
]


# 키워드 생성
def generate_keyword(question: str, answer: str) -> str:
    user_content = f"Q: {question}\nA: {answer}"
    return create_message(
        messages=[{"role": "user", "content": user_content}],
        system=KEYWORD_GENERATION_SYSTEM_PROMPT,
        max_tokens=32,
    )


# 실행
if __name__ == "__main__":
    print(f"{'=' * 60}")
    print("  북마크 키워드 생성 테스트")
    print(f"{'=' * 60}")

    for i, pair in enumerate(BOOKMARKED_QA_PAIRS, start=1):
        keyword = generate_keyword(pair["question"], pair["answer"])
        print(f"\n[{i}]")
        print(f"Q.  {pair['question']}")
        print(f"A.  {pair['answer']}")
        print(f"→  keyword: [{keyword}]")
