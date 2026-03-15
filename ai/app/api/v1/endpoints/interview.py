from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status

from app.api.v1.schemas import InterviewPipelineResponse
from app.api.v1.schemas.interview import FirstInterviewRequest, FirstInterviewResponse
from app.services.interview_service import (
    generate_first_interview_question,
    run_interview_pipeline_from_upload,
)

router = APIRouter()


@router.post(
    "/first",
    response_model=FirstInterviewResponse,
    summary="최초 인터뷰 질문 생성",
)
def generate_first_interview_question_endpoint(
    request: FirstInterviewRequest,
) -> FirstInterviewResponse:
    """인터뷰 기록이 없는 신규 사용자(is_first_finished=false)를 위한 질문을 생성한다.

    백엔드가 사용자의 is_first_finished 여부를 확인한 뒤, 최초 인터뷰에 해당할 때만
    이 엔드포인트를 호출한다. sequence=1이면 이전 맥락 없이 첫 질문을 생성하고,
    sequence 2~5이면 직전 Q&A를 참고해 다음 질문을 생성한다.

    Args:
        request: 닉네임, 질문 순서, 직전 질문/답변을 담은 요청 본문.

    Returns:
        FirstInterviewResponse: LLM이 생성한 질문 텍스트와 base64 인코딩된 TTS 음성.
    """
    result = generate_first_interview_question(
        nickname=request.nickname,
        sequence=request.sequence,
        previous_question=request.previous_question,
        previous_answer=request.previous_answer,
    )
    return FirstInterviewResponse.model_validate(result)


@router.post(
    "/pipeline",
    response_model=InterviewPipelineResponse,
    summary="Run interview pipeline",
)
def run_interview_pipeline_endpoint(
    audio_file: UploadFile = File(...),
    max_tokens: int = Form(default=1024),
) -> InterviewPipelineResponse:
    """면접 답변 음성을 입력받아 다음 꼬리질문과 TTS 음성을 반환한다.

    이 엔드포인트는 백엔드 서버가 multipart/form-data 요청으로 전달한 `webm`
    음성 파일을 받아 인터뷰 파이프라인을 실행하고, 최종적으로 생성된 STT 원문,
    꼬리질문 텍스트와 음성 데이터를 JSON으로 응답한다.

    Args:
        audio_file: 면접자의 답변이 담긴 `webm` 오디오 파일.
        max_tokens: LLM 응답 생성 시 사용할 최대 토큰 수.

    Returns:
        InterviewPipelineResponse: STT 원문, 다음 꼬리질문 텍스트와
            base64 인코딩된 TTS 음성을 담은 응답 모델.

    Raises:
        HTTPException: 요청 파일 형식이 잘못되어 서비스 계층에서 `ValueError`가 발생한 경우
            400 상태 코드와 함께 반환한다.
    """

    # 백엔드에서 multipart/form-data로 전달한 음성 파일과 옵션을 받아
    # 서비스 계층에 위임한 뒤, 응답 스키마로 정규화해서 반환한다.
    # system_prompt는 더 이상 외부 입력으로 받지 않고 기본 프롬프트만 사용한다.
    try:
        result = run_interview_pipeline_from_upload(
            audio_file=audio_file,
            max_tokens=max_tokens,
        )
    except ValueError as exc:
        # 업로드 확장자 오류처럼 클라이언트 입력 문제는 400으로 명확하게 반환한다.
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewPipelineResponse.model_validate(result)
