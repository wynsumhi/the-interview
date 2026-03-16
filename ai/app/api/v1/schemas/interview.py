from pydantic import BaseModel


class InterviewPipelineResponse(BaseModel):
    """면접 파이프라인 API 응답 본문.

    Attributes:
        stt_text: 업로드된 답변 음성을 STT로 변환한 텍스트.
        question_text: 후속 질문 생성 결과로 TTS 입력에도 사용되는 질문 텍스트.
        tts_audio_base64: `question_text`를 TTS로 변환한 mp3 바이트를 base64로 인코딩한 값.
        tts_audio_mime_type: base64 오디오의 MIME 타입.
    """

    # STT 결과 텍스트를 그대로 응답에 담아 백엔드가 후속 처리에 활용할 수 있게 한다.
    stt_text: str
    # LLM이 만든 다음 질문 텍스트를 응답에 포함해 화면 표기와 디버깅에 함께 사용한다.
    question_text: str
    # 바이너리 오디오를 JSON 응답으로 전달하기 위해 base64 문자열로 직렬화한다.
    tts_audio_base64: str
    # 현재 TTS 출력 포맷은 mp3이므로 MIME 타입 기본값은 audio/mpeg로 둔다.
    tts_audio_mime_type: str = "audio/mpeg"


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
        tts_audio_base64: `question_text`를 TTS로 변환한 mp3 바이트를 base64 문자열로 인코딩한 값.
        tts_audio_mime_type: base64 오디오의 MIME 타입.
    """

    # 템플릿 치환 또는 DB 질문 선택이 끝난 뒤 최종적으로 TTS에 전달된 질문이다.
    question_text: str
    # 바이너리 오디오를 JSON 응답에 담기 위해 base64 문자열로 직렬화한다.
    tts_audio_base64: str
    # 현재 TTS provider가 mp3 바이트를 반환하므로 기본 MIME 타입은 audio/mpeg로 고정한다.
    tts_audio_mime_type: str = "audio/mpeg"
