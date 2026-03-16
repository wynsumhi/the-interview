from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status

from app.api.v1.schemas import InterviewPipelineResponse
from app.services.interview_service import run_interview_pipeline_from_upload

router = APIRouter()


@router.post(
    "/pipeline",
    response_model=InterviewPipelineResponse,
    summary="Run interview pipeline",
)
def run_interview_pipeline_endpoint(
    audio_file: UploadFile = File(...),
    is_first: bool = Form(default=False),
    sequence: int | None = Form(default=None),
    nickname: str | None = Form(default=None),
    previous_question: str | None = Form(default=None),
    max_tokens: int = Form(default=1024),
) -> InterviewPipelineResponse:
    """면접 답변 음성을 입력받아 다음 질문과 TTS 음성을 반환한다.

    최초 인터뷰(is_first=True)와 일반 인터뷰(is_first=False) 모두 이 엔드포인트에서 처리한다.
    백엔드가 is_first_finished 여부에 따라 is_first 파라미터를 설정해 호출한다.

    Args:
        audio_file: 면접자의 답변이 담긴 `webm` 오디오 파일.
        is_first: 최초 인터뷰 여부. True이면 최초 인터뷰 프롬프트를 사용한다.
        sequence: 최초 인터뷰 질문 순서 (1~5). is_first=True일 때만 사용한다.
        nickname: 사용자 닉네임. is_first=True일 때 LLM 호칭으로 활용한다.
        previous_question: 직전 AI 질문 텍스트. is_first=True이고 sequence >= 2일 때 사용한다.
        max_tokens: LLM 응답 생성 시 사용할 최대 토큰 수.

    Returns:
        InterviewPipelineResponse: STT 원문, 다음 질문 텍스트와
            base64 인코딩된 TTS 음성을 담은 응답 모델.

    Raises:
        HTTPException: 요청 파일 형식이 잘못되어 서비스 계층에서 `ValueError`가 발생한 경우
            400 상태 코드와 함께 반환한다.
    """

    try:
        result = run_interview_pipeline_from_upload(
            audio_file=audio_file,
            is_first=is_first,
            sequence=sequence,
            nickname=nickname,
            previous_question=previous_question,
            max_tokens=max_tokens,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewPipelineResponse.model_validate(result)
