from openai import OpenAI
from dotenv import load_dotenv
import os

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url= "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

# 최초 인터뷰를 위한 질문의 TTS 결과를 저장할 디렉토리 경로 설정
# ai/app/static/tts 디렉토리에 저장
TTS_SAVE_DIR = os.path.join(os.path.dirname(__file__), "..", "static", "tts")

# TTS 음성 설정
TTS_VOICE = "alloy"

# a. 최초 인터뷰 진행
#   1. DB에 저장된 질문 목록을 받아
#   2. TTS로 변환
#   3. 변환된 음성 데이터를 AI 서버에 파일로 저장
#   4. {question_id: 파일 경로} 형태의 딕셔너리 반환

def first_interview_tts(questions: list[dict]) -> dict:
    """
    최초 인터뷰 질문 목록 전체를 TTS로 변환하고 파일로 저장하는 함수
    질문이 추가되거나 변경될 때 다시 호출하면 덮어쓴다
    """
    # TTS 결과를 저장할 디렉토리가 존재하지 않으면 생성
    os.makedirs(TTS_SAVE_DIR, exist_ok=True)

    # 질문 ID와 TTS 파일 경로를 매핑하는 딕셔너리
    result = {}

    for question in questions:
        question_id = question["id"]
        question_text = question["text"]

        # 저장 파일명: question_{id}.mp3
        file_name = f"question_{question_id}.mp3"
        file_path = os.path.join(TTS_SAVE_DIR, file_name)

        # GMS API를 통해 TTS 변환 요청
        response = client.audio.speech.create(
            model="gpt-4o-mini-tts",
            input=question_text,
            voice=TTS_VOICE,
        )

        # 음성 파일 저장
        with open(file_path, "wb") as f:
            f.write(response.content)

        # 백엔드에서 경로를 DB에 저장 (프론트엔드에서 접근할 수 있도록 저장)
        result[question_id] = file_path

    return result


# b. n회차 인터뷰 진행
#   1. 실시간으로 TTS로 변환
#   2. 변환된 음성 데이터를 반환하지만 파일을 저장하지는 않음
#   3. LLM을 통해 생성되는 꼬리 질문은 인터뷰 진행 중에 실시간으로 TTS 변환하여 반환

def realtime_interview_tts(question_text: str) -> bytes:
    """
    실시간으로 TTS 변환하여 음성 데이터를 반환하는 함수
    파일로 저장하지 않고 바로 반환

    텍스트의 출처가 DB에 저장된 질문이든 LLM이 생성한 꼬리 질문이든 상관없이 동일하게 처리한다
    """
    response = client.audio.speech.create(
        model="gpt-4o-mini-tts",
        input=question_text,
        voice=TTS_VOICE,
    )

    return response.content