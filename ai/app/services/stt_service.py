from openai import OpenAI
from dotenv import load_dotenv
import os

# .env 파일에서 환경 변수 로드
load_dotenv()

client = OpenAI(
    api_key=os.getenv("GMS_KEY"),
    base_url= "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

def speech_to_text(audio_file_path: str) -> str:
    """
    음성 파일을 텍스트로 변환하는 함수

    Args:
        audio_file_path (str): 변환할 음성 파일의 경로
    """
    with open(audio_file_path, "rb") as audio_file:
        response = client.audio.transcriptions.create(
            file=audio_file,
            model="whisper-1",
            language="ko", # 한국어로 설정
        )
    return response.text


def create_session(is_first_interview: bool) -> dict:
    """
    인터뷰 진행 중 질문과 답변을 임시로 담아두는 함수

    Args:
        is_first_interview (bool): 최초 인터뷰 여부
            - True: STT 변환은 하되, 꼬리 질문 생성은 하지 않음
            - False: STT 변환 후 꼬리 질문 생성 진행
    """
    session = {
        "is_first_interview": is_first_interview,
        # 세션 동안 누적된 질문-답변 텍스트를 저장하는 리스트
        "session_texts": [],
    }
    return session


def add_answer(session: dict, question: str, audio_file_path: str) -> str:
    """
    사용자의 답변 음성을 STT 변환 후 세션에 저장하는 함수

    Args:
        session (dict): create_session()으로 만든 세션 딕셔너리
        question (str): 해당 답변에 대응하는 질문 텍스트
        audio_file_path (str): 사용자가 답변한 음성 파일의 경로
    """
    answer_text = speech_to_text(audio_file_path)

    # 변환된 텍스트 누적 저장
    session["session_texts"].append({
        "question": question,
        "answer": answer_text,
    })

    return answer_text


# 인터뷰 종료 (사용자가 종료 버튼 클릭 시 호출)
def finalize(session: dict) -> list:
    """
    인터뷰를 종료하고 최종 텍스트 목록을 반환하는 함수
    반환된 리스트를 LLM에 넘겨서 인터뷰 글을 정제하는 데 활용

    Args:
        session (dict): create_session()으로 만든 세션 딕셔너리
    """
    return session["session_texts"]
