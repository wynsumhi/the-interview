from __future__ import annotations

import base64
import shutil
import tempfile
from pathlib import Path

from fastapi import UploadFile

from app.pipeline.interview_pipeline import run_interview_pipeline
from app.prompts.interview_prompts import (
    FIRST_INTERVIEW_QUESTION_MAP,
    FIRST_INTERVIEW_SYSTEM_PROMPT,
)
from app.services.llm_service import create_message
from app.services.tts_service import text_to_speech

# 현재 인터뷰 업로드 API는 백엔드 연동 규격상 `webm` 오디오만 허용한다.
ALLOWED_AUDIO_EXTENSIONS = {".webm"}
# 현재 TTS 구현이 mp3 계열 바이트를 반환하므로 응답 MIME 타입을 고정해 내려준다.
DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"


def validate_audio_upload(audio_file: UploadFile) -> None:
    """업로드된 음성 파일이 현재 인터뷰 API 규격에 맞는지 검증한다.

    Args:
        audio_file: FastAPI가 multipart/form-data 요청에서 파싱한 업로드 파일 객체.

    Raises:
        ValueError: 파일 확장자가 `.webm`이 아니어서 현재 인터뷰 API 규격에 맞지 않을 때 발생한다.
    """

    # 업로드 파일명 기준으로 확장자를 검사해 잘못된 요청을 초기에 차단한다.
    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def run_interview_pipeline_from_upload(
    audio_file: UploadFile,
    max_tokens: int = 1024,
) -> dict[str, str]:
    """업로드된 음성 파일로 인터뷰 파이프라인을 실행하고 API 응답 형태로 변환한다.

    이 함수는 FastAPI 라우터와 실제 파이프라인 실행 로직 사이의 서비스 계층 역할을 한다.
    업로드 파일 검증, 임시 파일 저장, 파이프라인 실행, 응답 직렬화용 base64 변환,
    임시 파일 정리를 한 곳에서 처리한다.

    Args:
        audio_file: 백엔드가 전달한 면접 답변 음성 파일. 현재는 `.webm`만 허용한다.
        max_tokens: LLM이 생성할 꼬리질문의 최대 토큰 수.

    Returns:
        dict[str, str]: API 응답으로 바로 사용할 수 있는 사전.
            `stt_text`, `question_text`, `tts_audio_base64`, `tts_audio_mime_type` 키를 포함한다.

    Raises:
        ValueError: 업로드 파일이 허용된 오디오 형식이 아닐 때 발생한다.
    """

    # 라우터에서는 HTTP 처리만 담당하고, 실제 파일 검증과 파이프라인 실행은 서비스에서 맡는다.
    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        # STT 클라이언트는 파일 경로를 입력으로 받기 때문에 업로드 스트림을 임시 파일로 저장한다.
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        # 저장한 음성 파일을 인터뷰 파이프라인에 넘겨
        # STT -> LLM -> TTS 전체 흐름을 한 번에 실행한다.
        # 시스템 프롬프트는 파이프라인 내부에서 app/prompts 값을 고정 사용한다.
        pipeline_result = run_interview_pipeline(
            str(temp_file_path),
            max_tokens=max_tokens,
        )
        return {
            # STT가 업로드 음성에서 추출한 원문 텍스트도 함께 내려주어 디버깅과 검증에 활용한다.
            "stt_text": pipeline_result.stt_text,
            # 백엔드에는 LLM이 생성한 다음 꼬리질문 텍스트를 그대로 전달한다.
            "question_text": pipeline_result.llm_text,
            # TTS 바이너리는 JSON 응답으로 내보내기 위해 base64 문자열로 변환한다.
            "tts_audio_base64": base64.b64encode(pipeline_result.tts_audio).decode("ascii"),
            "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
        }
    finally:
        # 업로드 스트림과 임시 파일을 정리해 파일 핸들 누수와 디스크 잔여 파일을 방지한다.
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()


def generate_first_interview_question(
    nickname: str,
    sequence: int = 1,
    previous_question: str | None = None,
    previous_answer: str | None = None,
    max_tokens: int = 1024,
) -> dict[str, str]:
    """최초 인터뷰에서 지정된 순서의 질문을 LLM으로 생성하고 TTS 음성으로 변환한다.

    인터뷰 기록이 없는 신규 사용자(is_first_finished=false)를 위한 5개 질문 생성 흐름에서
    사용한다. sequence=1일 때는 이전 Q&A 없이 첫 질문을 생성하고, sequence 2~5일 때는
    직전 질문과 사용자 답변(STT 변환 텍스트)을 맥락으로 활용해 다음 질문을 생성한다.

    Args:
        nickname: 사용자 닉네임. LLM이 자연스러운 호칭으로 활용한다.
        sequence: 생성할 질문 순서 (1~5).
        previous_question: 직전에 AI가 생성한 질문 텍스트. sequence가 1이면 None.
        previous_answer: 직전 질문에 대한 사용자의 STT 변환 답변. sequence가 1이면 None.
        max_tokens: LLM 응답 생성 시 사용할 최대 토큰 수.

    Returns:
        dict[str, str]: `question_text`, `tts_audio_base64`, `tts_audio_mime_type` 키를 포함한다.
    """

    # 질문 순서와 이전 대화 맥락을 조합해 LLM에 전달할 유저 메시지를 구성한다.
    if sequence == 1:
        context = "첫 질문이므로 이전 대화 없음."
    else:
        context = f"직전 질문:\n{previous_question}\n\n사용자 답변:\n{previous_answer}"

    user_message = (
        f"사용자 닉네임: {nickname}\n"
        f"현재 진행할 질문: {FIRST_INTERVIEW_QUESTION_MAP[sequence]}\n\n"
        f"대화 맥락:\n{context}"
    )

    question_text = create_message(
        messages=[{"role": "user", "content": user_message}],
        system=FIRST_INTERVIEW_SYSTEM_PROMPT,
        max_tokens=max_tokens,
    ).strip()

    tts_audio = text_to_speech(question_text)

    return {
        "question_text": question_text,
        "tts_audio_base64": base64.b64encode(tts_audio).decode("ascii"),
        "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
    }
