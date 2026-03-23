from pydantic import BaseModel


class SpeechToTextResponse(BaseModel):
    """STT 전용 API 응답 스키마다.

    이 스키마는 음성 파일을 텍스트로만 변환하는 단일 목적 엔드포인트에서 사용한다.
    후속 질문 생성이나 인터뷰 상태 분석 결과는 포함하지 않고,
    오직 최종 STT 텍스트만 전달하는 가장 단순한 응답 형태다.
    """

    stt_text: str


class AudioFeaturesResponse(BaseModel):
    """답변 음성에서 추출한 상세 음성 특징 응답 스키마다.

    각 필드는 현재 인터뷰 상태 분석의 근거로 사용되며,
    이후에는 사용자별 평균 보이스 특성이나 회차별 발화 패턴을 분석하는
    원본 데이터로도 활용될 수 있다.
    따라서 단순 참고값이 아니라 DB 저장 대상의 일부라는 전제를 가진다.
    """

    duration_sec: float
    rms_mean: float
    rms_std: float
    zcr_mean: float
    silence_ratio: float
    voiced_ratio: float
    speech_rate_proxy: float
    pitch_mean_hz: float
    pitch_std_hz: float


class InterviewStateDbPayloadResponse(BaseModel):
    """백엔드 DB 저장에 바로 사용할 인터뷰 상태 응답 스키마다.

    현재 저장 계약은 답변 단위(`answer_id`) 기준으로 다음 항목만 유지한다.
    1. `top_state`: 최종 융합 상태의 대표 라벨
    2. `fused_scores`: 다섯 상태 축의 최종 점수
    3. `audio_features`: 보이스 특성 분석 재사용을 위한 상세 음성 특징

    텍스트/음성 개별 상태는 내부 계산에는 사용하지만, 현재 저장 최소 집합에서는 제외한다.
    """

    answer_id: int
    top_state: str
    fused_scores: dict[str, float]
    audio_features: AudioFeaturesResponse


class InterviewStatePromptResponse(BaseModel):
    """LLM 참고용 인터뷰 상태 응답 스키마다.

    이 스키마는 모델에게 상태를 강하게 단정해 주입하기 위한 목적이 아니라,
    후속 질문의 톤과 깊이를 조절할 수 있도록 참고용 신호를 전달하는 목적을 가진다.

    따라서 현재 구조는 다음처럼 최소화돼 있다.
    1. `top_state`: 최종 융합 상태의 대표 라벨
    2. `fused_scores`: 다섯 상태 축 점수 전체
    3. `formatted_prompt`: 위 값을 참고용으로 설명하는 짧은 문자열
    """

    top_state: str
    fused_scores: dict[str, float]
    formatted_prompt: str


class NextOpeningRequest(BaseModel):
    """다음 회차 인터뷰 첫 질문 생성 요청 본문이다.

    백엔드가 이전 인터뷰 맥락을 문자열로 조합해 전달하면,
    AI 서버는 그 텍스트를 바탕으로 다음 회차를 여는 첫 질문을 생성한다.
    `nickname`은 자연스러운 호명이나 인사 표현이 필요할 때만 선택적으로 사용한다.
    """

    interview_context: str
    nickname: str | None = None


class NextOpeningResponse(BaseModel):
    """다음 회차 인터뷰 시작 질문 생성 결과 응답이다."""

    db_question: str


class InterviewPipelineResponse(BaseModel):
    """답변 음성 하나를 처리한 인터뷰 파이프라인의 전체 응답 스키마다.

    이 응답은 사용자 답변 음성 1건을 처리한 결과를 한 번에 담는다.
    포함되는 값은 다음과 같다.
    1. `stt_text`: 답변 음성을 텍스트로 변환한 결과
    2. `interview_state_db`: 백엔드 DB에 저장할 인터뷰 상태 payload
    3. `interview_state_prompt`: LLM 참고용 상태 payload
    4. `question_text`: 후속 인터뷰 질문 생성 결과
    5. `tts_audio_base64`: 후속 질문을 음성으로 합성한 base64 문자열
    6. `tts_audio_mime_type`: 오디오 MIME 타입

    즉 이 스키마는 STT, 상태 분석, 질문 생성, TTS 결과를 모두 묶는 파이프라인 최종 응답이다.
    """

    stt_text: str
    masked_stt_text: str
    pii_masking_duration_seconds: float
    interview_state_db: InterviewStateDbPayloadResponse
    interview_state_prompt: InterviewStatePromptResponse
    question_text: str
    tts_audio_base64: str
    tts_audio_mime_type: str = "audio/wav"


class InterviewStateSaveRequest(BaseModel):
    """임시 인터뷰 상태 저장 요청 스키마다.

    실제 백엔드 저장 API가 아직 준비되지 않았기 때문에,
    우선 AI 서버가 어떤 구조로 상태 저장 요청을 보낼지 계약을 먼저 확정하기 위해 사용한다.
    현재 필드 구성은 `InterviewStateDbPayloadResponse`와 동일하게 맞춰 두었다.
    """

    answer_id: int
    top_state: str
    fused_scores: dict[str, float]
    audio_features: AudioFeaturesResponse


class InterviewStateSaveResponse(BaseModel):
    """임시 저장 요청을 그대로 감싸서 돌려주는 응답 스키마다.

    이 응답은 실제 저장 성공/실패를 의미하는 영속화 결과가 아니라,
    현재 요청 payload가 어떤 구조로 전달되는지 확인하기 위한 계약 확인용 응답이다.
    """

    status: str
    message: str
    request: InterviewStateSaveRequest


class MagazineTitleRequest(BaseModel):
    """인터뷰 전체 맥락을 바탕으로 매거진 제목을 생성하는 요청 본문이다.

    백엔드가 인터뷰 전체 Q&A를 문자열로 조합해 전달하면,
    AI 서버는 그 내용을 바탕으로 매거진 스타일 제목을 생성한다.
    """

    interview_context: str


class MagazineTitleResponse(BaseModel):
    """생성된 매거진 제목 응답 스키마다."""

    title: str


class InterviewClosingRequest(BaseModel):
    """인터뷰 종료 메시지 생성 요청 본문이다.

    백엔드가 지금까지의 인터뷰 대화 내용을 문자열로 조합해 전달하면,
    AI 서버는 이를 바탕으로 자연스러운 마무리 멘트를 생성하고 TTS로 변환한다.
    """

    interview_context: str


class InterviewClosingResponse(BaseModel):
    """인터뷰 종료 멘트 텍스트와 TTS 오디오를 함께 반환하는 응답 스키마다."""

    closing_message: str
    tts_audio_base64: str
    tts_audio_mime_type: str = "audio/wav"


class KeywordRequest(BaseModel):
    """북마크 키워드 생성 요청 본문이다.

    북마크로 등록할 질문-답변 쌍을 전달하면,
    해당 답변을 대표하는 짧은 영문 키워드를 생성해 반환한다.
    """

    question: str
    answer: str


class KeywordResponse(BaseModel):
    """생성된 북마크 키워드 응답 스키마다."""

    keyword: str


class InterviewStartTTSRequest(BaseModel):
    """인터뷰 시작 질문을 TTS로 변환하기 위한 요청 본문이다.

    신규 사용자라면 `nickname`을 사용해 기본 시작 질문을 만들고,
    이미 이전 인터뷰가 있는 사용자라면 `db_question`을 그대로 읽는다.
    `is_first_finished`는 이 두 흐름을 구분하기 위한 플래그다.
    """

    nickname: str | None = None
    is_first_finished: bool
    db_question: str | None = None


class InterviewStartTTSResponse(BaseModel):
    """인터뷰 시작 질문 텍스트와 TTS 오디오를 함께 반환하는 응답 스키마다.

    클라이언트는 `question_text`로 화면 문구를 표시하고,
    `tts_audio_base64`와 `tts_audio_mime_type`으로 시작 질문 음성을 재생할 수 있다.
    """

    question_text: str
    tts_audio_base64: str
    tts_audio_mime_type: str = "audio/wav"
