from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status

from app.api.v1.schemas import (
    InterviewPipelineResponse,
    InterviewStartTTSRequest,
    InterviewStartTTSResponse,
    MagazineTitleRequest,
    MagazineTitleResponse,
    NextOpeningRequest,
    NextOpeningResponse,
)
from app.services.interview_service import (
    generate_interview_start_tts,
    generate_magazine_title,
    generate_next_opening_question,
    run_interview_pipeline_from_upload,
)

router = APIRouter()


@router.post(
    "/start-tts",
    response_model=InterviewStartTTSResponse,
    summary="Generate interview start question TTS",
)
def generate_interview_start_tts_endpoint(
    request: InterviewStartTTSRequest,
) -> InterviewStartTTSResponse:
    """인터뷰 시작 시점에 읽어줄 질문 텍스트를 결정하고 TTS 오디오를 생성한다.

    `is_first_finished=false`면 닉네임을 치환한 하드코딩 질문을 사용하고,
    `is_first_finished=true`면 DB 질문을 그대로 사용한다.
    최종 질문 텍스트와 mp3 오디오를 base64로 인코딩한 값을 함께 반환한다.

    Args:
        request: 백엔드가 전달한 첫 인터뷰 완료 여부, 닉네임, DB 질문을 담은 JSON 요청 본문.

    Returns:
        InterviewStartTTSResponse: 실제로 사용된 질문 텍스트와 base64 인코딩된 TTS 오디오 응답.

    Raises:
        HTTPException: 완료 여부 분기에 필요한 입력값이 비어 있으면 400 응답을 반환한다.
    """

    try:
        result = generate_interview_start_tts(
            is_first_finished=request.is_first_finished,
            nickname=request.nickname,
            db_question=request.db_question,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewStartTTSResponse.model_validate(result)


@router.post(
    "/next-opening",
    response_model=NextOpeningResponse,
    summary="Generate next interview opening question",
)
def generate_next_opening_question_endpoint(
    request: NextOpeningRequest,
) -> NextOpeningResponse:
    """이전 인터뷰 내용을 바탕으로 다음 회차의 첫 질문을 생성한다.

    백엔드가 interview_id 기반으로 참고할 인터뷰 내용을 선별해 `interview_context`로 전달한다.
    생성된 질문은 백엔드가 DB에 저장하고, 다음 인터뷰 시작 시 /start-tts의 db_question으로 사용한다.

    Args:
        request: 이전 인터뷰 내용 텍스트와 사용자 닉네임을 담은 JSON 요청 본문.

    Returns:
        NextOpeningResponse: 다음 회차 첫 질문 텍스트.
    """

    result = generate_next_opening_question(
        interview_context=request.interview_context,
        nickname=request.nickname,
    )
    return NextOpeningResponse.model_validate(result)


@router.post(
    "/title",
    response_model=MagazineTitleResponse,
    summary="Generate magazine title from interview",
)
def generate_magazine_title_endpoint(
    request: MagazineTitleRequest,
) -> MagazineTitleResponse:
    """인터뷰 전체 내용을 바탕으로 매거진 스타일 타이틀을 생성한다.

    백엔드가 인터뷰 전체 Q&A를 `interview_context`로 전달하면
    LLM이 20자 이내의 매거진 제목을 반환한다.

    Args:
        request: 인터뷰 전체 Q&A 텍스트를 담은 JSON 요청 본문.

    Returns:
        MagazineTitleResponse: 생성된 매거진 타이틀 텍스트.
    """

    result = generate_magazine_title(
        interview_context=request.interview_context,
    )
    return MagazineTitleResponse.model_validate(result)


@router.post(
    "/pipeline",
    response_model=InterviewPipelineResponse,
    summary="Run interview pipeline",
)
def run_interview_pipeline_endpoint(
    audio_file: UploadFile = File(...),
    is_first_finished: bool = Form(default=False),
    sequence: int | None = Form(default=None),
    nickname: str | None = Form(default=None),
    previous_question: str | None = Form(default=None),
    max_tokens: int = Form(default=1024),
) -> InterviewPipelineResponse:
    """인터뷰 답변 음성을 받아 STT, 질문 생성, TTS까지 한 번에 수행한다.

    첫 인터뷰 진행 여부와 질문 순서를 함께 전달받아 파이프라인 내부 분기 로직에 넘긴다.
    업로드 파일은 현재 webm만 허용하며, 생성된 질문 오디오는 base64 문자열로 반환한다.

    Args:
        audio_file: 사용자의 인터뷰 답변 음성 파일.
        is_first_finished: 첫 질문 응답이 끝난 뒤인지 여부.
        sequence: 첫 인터뷰 질문 순서.
        nickname: 첫 인터뷰 질문 생성에 필요한 닉네임.
        previous_question: 이전 질문 텍스트.
        max_tokens: LLM 최대 토큰 수.

    Returns:
        InterviewPipelineResponse: STT 결과, 후속 질문 텍스트, base64 인코딩된 TTS 오디오 응답.

    Raises:
        HTTPException: 업로드 형식이나 파이프라인 입력이 잘못되면 400 응답을 반환한다.
    """

    try:
        result = run_interview_pipeline_from_upload(
            audio_file=audio_file,
            is_first_finished=is_first_finished,
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
