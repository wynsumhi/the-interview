from pydantic import BaseModel


class FirstInterviewRequest(BaseModel):
    """
    최초 인터뷰 질문 생성 API 요청 본문 모델.

    Attributes:
        nickname: 사용자 닉네임. LLM이 자연스러운 호칭으로 활용한다.
        sequence: 생성할 질문 순서 (1~5).
        previous_question: 직전에 AI가 생성한 질문 텍스트. sequence가 1이면 None.
        previous_answer: 직전 질문에 대한 사용자의 STT 변환 답변. sequence가 1이면 None.
    """

    nickname: str
    sequence: int = 1
    previous_question: str | None = None
    previous_answer: str | None = None


class FirstInterviewResponse(BaseModel):
    """
    최초 인터뷰 질문 생성 API 응답 본문 모델.

    Attributes:
        question_text: LLM이 생성한 질문 텍스트.
        tts_audio_base64: question_text를 TTS로 변환한 오디오 바이너리를
            base64 문자열로 인코딩한 값.
        tts_audio_mime_type: 백엔드가 base64를 디코딩한 뒤 사용할 오디오 MIME 타입.
    """

    question_text: str
    tts_audio_base64: str
    tts_audio_mime_type: str = "audio/mpeg"


class InterviewPipelineResponse(BaseModel):
    """인터뷰 파이프라인 API가 백엔드에 반환하는 응답 본문 모델.

    Attributes:
        stt_text: 업로드한 답변 음성에서 STT가 추출한 원문 텍스트.
        question_text: 면접자의 답변을 바탕으로 LLM이 생성한 다음 꼬리질문 텍스트.
        tts_audio_base64: `question_text`를 TTS로 변환한 오디오 바이너리를
            base64 문자열로 인코딩한 값.
        tts_audio_mime_type: 백엔드가 base64를 디코딩한 뒤 사용할 오디오 MIME 타입.
    """

    # STT가 업로드 음성에서 추출한 원문 텍스트를 백엔드에 전달한다.
    stt_text: str
    # LLM이 생성한 다음 꼬리질문 텍스트를 백엔드에 전달한다.
    question_text: str
    # TTS 결과 바이너리를 JSON으로 전달하기 위해 base64 문자열로 인코딩한다.
    tts_audio_base64: str
    # 백엔드가 디코딩한 뒤 어떤 오디오 타입으로 다뤄야 하는지 명시한다.
    tts_audio_mime_type: str = "audio/mpeg"
