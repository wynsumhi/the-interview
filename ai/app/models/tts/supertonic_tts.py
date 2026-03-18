from __future__ import annotations

from functools import lru_cache
from io import BytesIO

import onnxruntime as ort
import soundfile as sf
import supertonic.loader as loader
from supertonic import TTS
from supertonic.core import Style

from app.core.settings import (
    DEFAULT_TTS_AUTO_DOWNLOAD,
    DEFAULT_TTS_LANGUAGE,
    DEFAULT_TTS_MODEL,
    DEFAULT_TTS_PROVIDER,
    DEFAULT_TTS_SPEED,
    DEFAULT_TTS_STEPS,
    DEFAULT_TTS_VOICE,
)


def _configure_provider(provider: str) -> None:
    """Supertonic 초기화 전에 ONNX Runtime provider 우선순위를 고정한다.

    models 레이어에서 실행 환경별 세부 설정을 캡슐화해 상위 계층이
    CUDA, TensorRT, CPU 선택 로직을 몰라도 되도록 둔다.
    기본값은 cuda지만, 실제 사용 가능한 DLL이나 디바이스가 없으면
    provider 순서에 따라 CPU로 자연스럽게 폴백되도록 구성한다.
    """

    if provider == "cpu":
        loader.DEFAULT_ONNX_PROVIDERS = ["CPUExecutionProvider"]
        return

    """Windows 환경에서는 CUDA/cuDNN DLL을 먼저 로드해 두어야
    이후 InferenceSession 생성 시 provider 초기화 실패를 줄일 수 있다.
    """
    ort.preload_dlls()
    if provider == "cuda":
        loader.DEFAULT_ONNX_PROVIDERS = ["CUDAExecutionProvider", "CPUExecutionProvider"]
        return

    loader.DEFAULT_ONNX_PROVIDERS = [
        "TensorrtExecutionProvider",
        "CUDAExecutionProvider",
        "CPUExecutionProvider",
    ]


@lru_cache(maxsize=1)
def _get_tts_engine() -> TTS:
    """Supertonic 모델과 ONNX 세션을 프로세스 단위로 한 번만 생성한다.

    모델 로딩과 세션 초기화 비용이 커서 요청마다 새로 만들지 않고
    캐시된 엔진을 재사용해 지연 시간을 줄인다.
    """
    _configure_provider(DEFAULT_TTS_PROVIDER)
    return TTS(model=DEFAULT_TTS_MODEL, auto_download=DEFAULT_TTS_AUTO_DOWNLOAD)


@lru_cache(maxsize=8)
def _get_voice_style(voice: str) -> Style:
    """voice name 기준으로 화자 스타일을 캐시한다.

    같은 voice를 반복 사용할 때 스타일 파일을 매번 다시 읽지 않도록
    캐시해 요청 지연 시간을 줄인다.
    """
    return _get_tts_engine().get_voice_style(voice)


def synthesize_speech(
    text: str,
    voice: str = DEFAULT_TTS_VOICE,
    lang: str = DEFAULT_TTS_LANGUAGE,
    steps: int = DEFAULT_TTS_STEPS,
    speed: float = DEFAULT_TTS_SPEED,
) -> bytes:
    """텍스트를 Supertonic TTS로 합성해 WAV bytes로 반환한다.

    상위 서비스 계층은 TTS 엔진 내부 표현을 몰라도 되도록
    최종 응답에 바로 사용할 수 있는 WAV 바이트만 반환한다.
    """

    engine = _get_tts_engine()
    voice_style = _get_voice_style(voice)
    wav, _ = engine.synthesize(
        text,
        voice_style=voice_style,
        lang=lang,
        total_steps=steps,
        speed=speed,
    )

    buffer = BytesIO()
    """API 응답 base64 인코딩과 테스트 검증에서 그대로 사용할 수 있도록
    메모리 버퍼에 WAV 포맷으로 직렬화한 뒤 bytes로 반환한다.
    """
    sf.write(buffer, wav.squeeze(), engine.sample_rate, format="WAV")
    return buffer.getvalue()
