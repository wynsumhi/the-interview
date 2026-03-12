from pydantic import BaseModel


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
