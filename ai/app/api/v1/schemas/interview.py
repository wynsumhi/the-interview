from pydantic import BaseModel


class SpeechToTextResponse(BaseModel):
    """STT-only API response payload."""

    stt_text: str


class NextOpeningRequest(BaseModel):
    """다음 회차 첫 질문 생성 요청 본문.

    Attributes:
        interview_context: 직전 인터뷰 내용을 백엔드가 텍스트로 조합해 전달한 값.
            어떤 내용을 포함할지는 백엔드가 interview_id 기반으로 판단한다.
            - 2회차 생성 시: 1회차의 5번째 Q&A만 포함.
            - 3회차+ 생성 시: 직전 인터뷰 전체 Q&A 포함.
            포맷은 아래 형식을 따른다 (Q&A 번호 필수):
                Q1. [질문 텍스트]
                A1. [사용자 답변 텍스트]
                Q2. [질문 텍스트]
                A2. [사용자 답변 텍스트]
        nickname: 사용자 닉네임. 환영 인사에 자연스러운 경우 사용한다.
    """

    interview_context: str
    nickname: str | None = None


class NextOpeningResponse(BaseModel):
    """다음 회차 첫 질문 생성 응답 본문.

    Attributes:
        db_question: LLM이 생성한 다음 회차 첫 질문 텍스트.
            백엔드가 DB에 저장한 뒤 StartTTSRequest의 db_question 필드로 그대로 사용한다.
    """

    db_question: str


class InterviewPipelineResponse(BaseModel):
    """인터뷰 파이프라인 API 응답 본문.

    Attributes:
        stt_text: 업로드된 답변 음성을 STT로 변환한 텍스트.
        question_text: 후속 질문 생성 결과로 TTS 입력에도 사용되는 질문 텍스트.
        tts_audio_base64: `question_text`를 TTS로 변환한 wav 바이트를 base64로 인코딩한 값.
        tts_audio_mime_type: base64 오디오의 MIME 타입.
    """

    # STT 결과 텍스트를 그대로 응답에 담아 백엔드가 후속 처리에 활용할 수 있게 한다.
    stt_text: str
    # LLM이 만든 다음 질문 텍스트를 응답에 포함해 화면 표기와 디버깅에 함께 사용한다.
    question_text: str
    # 바이너리 오디오를 JSON 응답으로 전달하기 위해 base64 문자열로 직렬화한다.
    tts_audio_base64: str
    # 현재 TTS 출력 포맷은 wav이므로 MIME 타입 기본값은 audio/wav로 둔다.
    tts_audio_mime_type: str = "audio/wav"


class MagazineTitleRequest(BaseModel):
    """매거진 타이틀 생성 요청 본문.

    Attributes:
        interview_context: 인터뷰 전체 Q&A 내용을 백엔드가 텍스트로 조합해 전달한 값.
            포맷은 아래 형식을 따른다 (Q&A 번호 필수):
                Q1. [질문 텍스트]
                A1. [사용자 답변 텍스트]
                Q2. [질문 텍스트]
                A2. [사용자 답변 텍스트]
    """

    interview_context: str


class MagazineTitleResponse(BaseModel):
    """매거진 타이틀 생성 응답 본문.

    Attributes:
        title: LLM이 생성한 매거진 스타일 타이틀 텍스트.
    """

    title: str


class InterviewStartTTSRequest(BaseModel):
    """인터뷰 시작 질문 TTS 생성 요청 본문.

    Attributes:
        nickname: 신규 유저일 때 하드코딩 시작 질문에 삽입할 사용자 닉네임.
        is_first_finished: 첫 인터뷰를 이미 마친 사용자 여부.
        db_question: 첫 인터뷰를 이미 마친 사용자일 때 DB에 저장된 시작 질문 텍스트.
    """

    # 신규 유저 시작 질문 템플릿에 닉네임을 치환하기 위해 사용한다.
    nickname: str | None = None
    # false면 첫 인터뷰 시작으로 간주하고, true면 이전 인터뷰가 끝난 사용자로 해석한다.
    is_first_finished: bool
    # 첫 인터뷰를 이미 마친 사용자라면 DB 질문을 그대로 TTS 입력으로 사용한다.
    db_question: str | None = None


class InterviewStartTTSResponse(BaseModel):
    """인터뷰 시작 질문 TTS 생성 응답 본문.

    Attributes:
        question_text: 신규 여부 분기 후 실제로 TTS에 사용된 최종 질문 텍스트.
        tts_audio_base64: `question_text`를 TTS로 변환한 wav 바이트를 base64 문자열로 인코딩한 값.
        tts_audio_mime_type: base64 오디오의 MIME 타입.
    """

    # 템플릿 치환 또는 DB 질문 선택이 끝난 뒤 최종적으로 TTS에 전달된 질문이다.
    question_text: str
    # 바이너리 오디오를 JSON 응답에 담기 위해 base64 문자열로 직렬화한다.
    tts_audio_base64: str
    # 현재 TTS provider가 wav 바이트를 반환하므로 기본 MIME 타입은 audio/wav로 고정한다.
    tts_audio_mime_type: str = "audio/wav"
