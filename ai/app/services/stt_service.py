from app.models.stt.openai_stt import transcribe_audio_file


def speech_to_text(audio_file_path: str) -> str:
    # 서비스 계층은 상위 코드가 사용할 안정적인 진입점만 제공한다.
    return transcribe_audio_file(audio_file_path)
