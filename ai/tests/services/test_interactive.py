import json
import os
import sys
import time
from pathlib import Path

import requests
from dotenv import load_dotenv

"""
GPT 5.2 / Gemini 2.5 Flash Lite / Claude Sonnet 4.5
모델 선택형 인터랙티브 테스트

실행 후 모델을 선택하고, 직접 답변을 입력하며 인터뷰를 진행합니다.
종료하려면 답변 입력 시 'q' 또는 'quit'를 입력하세요.
"""

ROOT_DIR = Path(__file__).resolve().parents[2]
load_dotenv(ROOT_DIR / ".env")

GMS_KEY = os.getenv("GMS_KEY")
GMS_BASE = "https://gms.ssafy.io/gmsapi"

if not GMS_KEY:
    print("[오류] .env 파일에 GMS_KEY가 없습니다.")
    sys.exit(1)


# 시스템 프롬프트
SYSTEM_PROMPT = """
당신은 'The Interview'라는 AI 기반 인터뷰 서비스의 인터뷰어입니다.

[역할]
- 사용자가 자신의 생각과 감정을 자연스럽게 표현하고,
때로는 스스로를 탐색할 수 있도록 돕는 인터뷰어입니다.

[질문 원칙]
- 질문은 반드시 한국어로 작성합니다.
- 사용자의 단답형이 아닌, 서사형 답변을 유도하는 질문을 만듭니다.
- 사용자의 가치관, 감정, 경험을 깊이 탐색하는 질문을 합니다.
하지만 지나치게 무겁거나 부담스러운 질문은 피합니다.
- 질문은 1개만 생성합니다. 짧고 자연스럽게 작성합니다.
- 번역 투나 어색한 표현 없이 실제 인터뷰어처럼 자연스러운 한국어를 사용합니다.
- 종교, 정치, 사회적 이슈 등의 민감한 주제는 피합니다.
인터뷰의 목적은 사용자가 편안하게 자신의 내면을 탐색할 수 있도록 돕는 것입니다.

[금지 사항]
- 여러 개의 질문을 한 번에 하지 않습니다.
""".strip()


# 모델별 호출 함수
def call_openai(user_content: str) -> str:
    url = f"{GMS_BASE}/api.openai.com/v1/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {GMS_KEY}",
    }
    body = {
        "model": "gpt-5.2",
        "messages": [
            {"role": "developer", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ],
        "max_completion_tokens": 300,
        "temperature": 0.8,
    }
    response = requests.post(url, headers=headers, json=body, timeout=60)
    response.raise_for_status()
    return response.json()["choices"][0]["message"]["content"].strip()


def call_gemini_flash_lite(user_content: str) -> str:
    GEMINI_FLASH_LITE_PATH = (
        "generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
    )
    url = f"{GMS_BASE}/{GEMINI_FLASH_LITE_PATH}"
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": GMS_KEY,
    }
    body = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"parts": [{"text": user_content}]}],
        "generationConfig": {
            "maxOutputTokens": 300,
            "temperature": 0.8,
        },
    }
    response = requests.post(url, headers=headers, json=body, timeout=60)
    response.raise_for_status()
    data = response.json()
    candidate = data["candidates"][0]
    if "content" in candidate:
        parts = candidate["content"].get("parts", [])
        if parts:
            return parts[0]["text"].strip()
    raise ValueError(f"파싱 실패. 실제 응답: {json.dumps(data, ensure_ascii=False)}")


def call_claude(user_content: str) -> str:
    url = f"{GMS_BASE}/api.anthropic.com/v1/messages"
    headers = {
        "Content-Type": "application/json",
        "x-api-key": GMS_KEY,
        "anthropic-version": "2023-06-01",
    }
    body = {
        "model": "claude-sonnet-4-5-20250929",
        "max_tokens": 300,
        "system": SYSTEM_PROMPT,
        "messages": [
            {"role": "user", "content": user_content},
        ],
    }
    response = requests.post(url, headers=headers, json=body, timeout=60)
    response.raise_for_status()
    return response.json()["content"][0]["text"].strip()


# 모델 목록

MODELS = [
    {"name": "GPT 5.2", "credit": 20, "caller": call_openai},
    {"name": "Gemini 2.5 Flash Lite", "credit": 1, "caller": call_gemini_flash_lite},
    {"name": "Claude Sonnet 4.5", "credit": 30, "caller": call_claude},
]


# 프롬프트 구성 함수


def build_first_question_prompt(theme: str, username: str) -> str:
    return f"""
새로운 인터뷰를 시작합니다. 아래 조건을 고려하여 인터뷰의 첫 번째 질문을 생성해 주세요.

사용자 이름: {username}
인터뷰 테마: {theme}

[조건]
- 사용자가 편안하게 답변을 시작할 수 있는 열린 질문이어야 합니다.
- 너무 무겁거나 부담스럽지 않게, 자연스럽게 대화를 열 수 있는 질문이어야 합니다.
- {username}이라는 이름을 질문에 자연스럽게 녹여주세요. 이름을 부르는 위치는 정해져 있지 않습니다.
""".strip()


def build_followup_prompt(theme: str, username: str, history: list) -> str:
    history_text = ""
    for i, turn in enumerate(history, 1):
        history_text += f"\nQ{i}. {turn['question']}\nA{i}. {turn['answer']}\n"

    return f"""
인터뷰 테마: {theme}
사용자 이름: {username}

[지금까지의 인터뷰 내용]
{history_text}
위 전체 맥락을 고려해서, 다음 질문을 생성해 주세요.
지나치게 사용자의 자기 성찰을 유도할 필요는 없습니다.
자연스럽게 대화를 이어갈 수 있는 질문이면 충분합니다.
때로는 성찰을 유도하는 질문을 다음 질문으로 생성할 수도 있습니다.
인터뷰의 흐름에 맞게 적절히 질문을 만들어 주세요.
""".strip()


# 단일 모델 호출


def call_model(model: dict, prompt: str) -> str:
    start = time.time()
    try:
        response = model["caller"](prompt)
        elapsed = round(time.time() - start, 2)
        print(f"\n  ⏱️  {elapsed}초")
        print(f"\n  💬 {response}\n")
        return response
    except requests.HTTPError as e:
        elapsed = round(time.time() - start, 2)
        error = f"HTTP {e.response.status_code} — {e.response.text}"
        print(f"\n  ❌ ({elapsed}초) [오류] {error}\n")
        return ""
    except Exception as e:
        elapsed = round(time.time() - start, 2)
        print(f"\n  ❌ ({elapsed}초) [오류] {e}\n")
        return ""


# 메인 실행


def main():
    print("\n" + "═" * 60)
    print("  🎙️  인터랙티브 인터뷰 테스트")
    print("═" * 60)

    # 모델 선택
    print("\n테스트할 모델을 선택하세요.\n")
    for i, m in enumerate(MODELS, 1):
        print(f"  {i}. {m['name']}  ({m['credit']} 크레딧)")
    print()

    while True:
        model_input = input("번호를 입력하세요: ").strip()
        if model_input.isdigit() and 1 <= int(model_input) <= len(MODELS):
            selected_model = MODELS[int(model_input) - 1]
            break
        print("  올바른 번호를 입력해 주세요.")

    print(f"\n  선택된 모델: {selected_model['name']}")
    print("─" * 60)

    # 사용자 정보 입력
    print()
    username = input("사용자 이름을 입력하세요: ").strip()

    print("\n인터뷰 테마를 선택하세요.\n")
    themes = [
        "나의 일상과 소소한 행복",
        "나의 관계와 사람들",
        "나의 꿈과 미래",
        "나의 감정과 내면",
        "직접 입력",
    ]
    for i, t in enumerate(themes, 1):
        print(f"  {i}. {t}")
    print()

    while True:
        theme_input = input("번호를 입력하세요: ").strip()
        if theme_input.isdigit() and 1 <= int(theme_input) <= 4:
            theme = themes[int(theme_input) - 1]
            break
        elif theme_input == "5":
            theme = input("테마를 직접 입력하세요: ").strip()
            break
        print("  올바른 번호를 입력해 주세요.")

    print(f"\n  모델: {selected_model['name']}  |  테마: {theme}  |  사용자: {username}")
    print("═" * 60)
    print("  종료하려면 답변 입력 시 'q' 또는 'quit'를 입력하세요.")
    print("═" * 60)

    history = []

    # 첫 질문 생성
    print("\n  첫 번째 질문을 생성 중입니다...\n")
    first_prompt = build_first_question_prompt(theme, username)
    current_question = call_model(selected_model, first_prompt)

    # 대화 루프
    turn = 1
    while True:
        print("─" * 60)
        answer = input(f"  [{turn}번째 답변] > ").strip()

        if answer.lower() in ("q", "quit"):
            print("\n  테스트를 종료합니다.")
            break
        if not answer:
            print("  답변을 입력해 주세요.\n")
            continue

        history.append({"question": current_question, "answer": answer})

        print("\n  꼬리 질문을 생성 중입니다...\n")
        followup_prompt = build_followup_prompt(theme, username, history)
        current_question = call_model(selected_model, followup_prompt)

        turn += 1

    # 세션 저장
    if history:
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        model_tag = selected_model["name"].replace(" ", "_").replace(".", "")
        output_path = Path(__file__).parent / f"result_interactive_{model_tag}_{timestamp}.json"
        session_data = {
            "model": selected_model["name"],
            "theme": theme,
            "username": username,
            "history": history,
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(session_data, f, ensure_ascii=False, indent=2)
        print(f"\n  💾 세션 저장 완료: {output_path}")
    else:
        print("\n  저장할 대화 내용이 없습니다.")


if __name__ == "__main__":
    main()
