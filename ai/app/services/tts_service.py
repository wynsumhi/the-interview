from app.models.tts.supertonic_tts import synthesize_speech


def text_to_speech(question_text: str) -> bytes:
    # 호출자는 provider를 몰라도 되도록 서비스 함수로 감싼다.
    return synthesize_speech(question_text)
