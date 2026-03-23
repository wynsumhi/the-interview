from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status

from app.api.v1.schemas import (
    InterviewClosingRequest,
    InterviewClosingResponse,
    InterviewPipelineResponse,
    InterviewStartTTSRequest,
    InterviewStartTTSResponse,
    InterviewStateSaveRequest,
    InterviewStateSaveResponse,
    KeywordRequest,
    KeywordResponse,
    MagazineTitleRequest,
    MagazineTitleResponse,
    NextOpeningRequest,
    NextOpeningResponse,
)
from app.services.interview_service import (
    generate_closing_message,
    generate_interview_start_tts,
    generate_keyword,
    generate_magazine_title,
    generate_next_opening_question,
    run_interview_pipeline_from_stt_payload,
    run_interview_pipeline_from_upload,
    save_interview_state_temp,
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
    """인터뷰 시작 질문과 TTS 오디오를 생성하는 엔드포인트다.

    이 엔드포인트는 실제 답변 음성 처리와는 별개로,
    인터뷰를 시작할 때 화면에 보여주고 읽어줄 첫 질문을 준비하는 역할을 맡는다.

    동작 방식은 다음 두 갈래로 나뉜다.
    1. `is_first_finished=false`
       아직 첫 인터뷰를 끝내지 않은 사용자인 경우다.
       이때는 `nickname`을 기본 템플릿에 넣어 시작 질문을 만든다.
    2. `is_first_finished=true`
       이미 이전 인터뷰가 있는 사용자인 경우다.
       이때는 백엔드가 내려준 `db_question`을 그대로 사용한다.

    서비스 계층에서 입력값 검증 실패 시 `ValueError`를 던지므로,
    API 계층에서는 이를 400 응답으로 변환해 클라이언트에 전달한다.
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
    """다음 회차 인터뷰의 첫 질문을 생성하는 엔드포인트다.

    백엔드는 이전 인터뷰 내용을 하나의 문자열로 조합해 `interview_context`에 담아 넘기고,
    AI 서버는 이를 바탕으로 다음 회차를 여는 질문을 생성한다.
    생성된 결과는 이후 백엔드가 저장한 뒤,
    다시 `/start-tts` 흐름에서 `db_question`으로 사용하게 된다.

    `nickname`은 필수는 아니며,
    질문 문장에 자연스러운 호명 표현을 넣고 싶을 때만 참고용으로 사용한다.
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
    """인터뷰 전체 맥락을 바탕으로 매거진 제목을 생성하는 엔드포인트다.

    백엔드가 인터뷰 전체 Q&A를 하나의 텍스트 맥락으로 전달하면,
    AI 서버는 이를 읽고 매거진 스타일 제목을 생성해 반환한다.
    이 엔드포인트는 답변 음성 처리 파이프라인과 별개로,
    인터뷰가 어느 정도 완료된 뒤 편집용 결과를 만드는 목적에 가깝다.
    """

    result = generate_magazine_title(
        interview_context=request.interview_context,
    )
    return MagazineTitleResponse.model_validate(result)


@router.post(
    "/keyword",
    response_model=KeywordResponse,
    summary="Generate bookmark keyword from Q&A pair",
)
def generate_keyword_endpoint(
    request: KeywordRequest,
) -> KeywordResponse:
    """북마크로 등록할 질문-답변 쌍에서 대표 키워드를 생성하는 엔드포인트다.

    사용자가 북마크한 질문-답변 쌍을 받아,
    해당 답변을 가장 잘 대표하는 짧은 영문 키워드(2–5단어)를 생성해 반환한다.
    """

    result = generate_keyword(
        question=request.question,
        answer=request.answer,
    )
    return KeywordResponse.model_validate(result)


@router.post(
    "/pipeline",
    response_model=InterviewPipelineResponse,
    summary="Run interview pipeline",
)
def run_interview_pipeline_endpoint(
    answer_id: int = Form(...),
    audio_file: UploadFile = File(...),
    is_first_finished: bool = Form(default=False),
    user_id: str | None = Form(default=None),
    sequence: int | None = Form(default=None),
    nickname: str | None = Form(default=None),
    previous_question: str | None = Form(default=None),
    max_tokens: int = Form(default=1024),
) -> InterviewPipelineResponse:
    """답변 음성을 받아 인터뷰 파이프라인 전체를 실행하는 핵심 엔드포인트다.

    이 엔드포인트는 사용자 답변 음성 1건을 입력으로 받아 다음 단계를 한 번에 처리한다.
    1. STT 수행
    2. 인터뷰 상태 분석 수행
    3. DB 저장용 상태 payload 생성
    4. LLM 참고용 상태 payload 생성
    5. 후속 질문 생성
    6. TTS 생성

    주요 입력값 의미는 다음과 같다.
    - `answer_id`
      이번 답변을 백엔드 DB 레코드와 연결하기 위한 식별자다.
      인터뷰 상태 DB 저장 payload에도 그대로 포함된다.
    - `audio_file`
      사용자의 실제 답변 음성 파일이다.
      현재는 `webm`만 허용한다.
    - `is_first_finished`
      첫 인터뷰 진행 중인지, 일반 인터뷰 단계인지 구분하는 플래그다.
    - `sequence`
      첫 인터뷰 진행 중일 때 현재 질문 번호를 의미한다.
    - `nickname`, `previous_question`
      첫 인터뷰 전용 프롬프트를 구성할 때 맥락 정보로 사용한다.
    - `max_tokens`
      후속 질문 생성 시 LLM 최대 토큰 수다.

    서비스 계층에서 업로드 포맷 검증이나 필수값 검증 실패 시 `ValueError`를 던지므로,
    API 계층에서는 이를 400 응답으로 변환해 클라이언트에 전달한다.
    """

    try:
        result = run_interview_pipeline_from_upload(
            audio_file=audio_file,
            answer_id=answer_id,
            is_first_finished=is_first_finished,
            user_id=user_id,
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


@router.post(
    "/pipeline/after-stt",
    response_model=InterviewPipelineResponse,
    summary="Run interview pipeline after external STT",
)
def run_interview_pipeline_after_stt_endpoint(
    stt_text: str = Form(...),
    audio_file: UploadFile = File(...),
    is_first_finished: bool = Form(default=False),
    user_id: str | None = Form(default=None),
    sequence: int | None = Form(default=None),
    nickname: str | None = Form(default=None),
    previous_question: str | None = Form(default=None),
    max_tokens: int = Form(default=1024),
) -> InterviewPipelineResponse:
    """외부 STT 결과를 받아 STT 이후 인터뷰 파이프라인만 실행한다."""

    try:
        result = run_interview_pipeline_from_stt_payload(
            audio_file=audio_file,
            stt_text=stt_text,
            is_first_finished=is_first_finished,
            user_id=user_id,
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


@router.post(
    "/closing",
    response_model=InterviewClosingResponse,
    summary="Generate interview closing message and TTS",
)
def generate_closing_message_endpoint(
    request: InterviewClosingRequest,
) -> InterviewClosingResponse:
    """사용자가 종료 버튼을 눌렀을 때 인터뷰 마무리 멘트를 생성하고 TTS로 변환하는 엔드포인트다.

    백엔드가 지금까지의 인터뷰 대화 내용을 `interview_context`로 전달하면,
    AI 서버는 대화 흐름을 자연스럽게 마무리하는 짧은 종료 멘트를 생성한 뒤
    TTS로 변환해 텍스트와 오디오를 함께 반환한다.
    """

    result = generate_closing_message(
        interview_context=request.interview_context,
    )
    return InterviewClosingResponse.model_validate(result)


@router.post(
    "/state/temp-save",
    response_model=InterviewStateSaveResponse,
    summary="Temporary interview-state save request",
)
def save_interview_state_temp_endpoint(
    request: InterviewStateSaveRequest,
) -> InterviewStateSaveResponse:
    """임시 인터뷰 상태 저장 요청을 검증하는 엔드포인트다.

    실제 백엔드 저장 API가 아직 준비되지 않았기 때문에,
    우선 AI 서버가 어떤 구조의 상태 저장 payload를 보낼지 계약을 먼저 고정하는
    목적의 임시 엔드포인트다.

    이 라우트는 실제 DB에 저장하지 않고,
    요청 본문이 기대한 형태인지 확인한 뒤 그대로 감싸서 반환한다.
    따라서 현재 단계에서는 영속화 API라기보다
    AI 서버와 백엔드 사이의 request schema를 맞추기 위한 계약 확인용 라우트에 가깝다.
    """

    result = save_interview_state_temp(request.model_dump())
    return InterviewStateSaveResponse.model_validate(result)
