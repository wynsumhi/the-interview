import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import api_router
from app.core import DEFAULT_TTS_PRELOAD_ON_STARTUP
from app.models.tts.supertonic_tts import warm_up_speech_engine

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """애플리케이션 startup과 shutdown 사이에서 공통 리소스 준비를 관리한다.

    TTS preload 옵션이 켜져 있으면 서버 기동 시점에 Supertonic 엔진을 미리 로드하고
    짧은 warmup 합성을 한 번 수행해 첫 요청에서만 발생하던 초기화 지연을 줄인다.
    """

    if DEFAULT_TTS_PRELOAD_ON_STARTUP:
        logger.info("Preloading Supertonic TTS engine during FastAPI startup.")
        warm_up_speech_engine()

    yield


"""애플리케이션 전역에서 사용할 FastAPI 인스턴스를 생성한다.

라우터 연결과 lifespan 기반 startup 초기화가 모두 여기에서 묶이므로,
서버 프로세스가 시작될 때 필요한 공통 준비 절차를 한곳에서 관리할 수 있다.
"""
app = FastAPI(
    title="The Interview AI API",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(api_router, prefix="/api/v1/ai")


@app.get("/", summary="Root")
def read_root() -> dict[str, str]:
    """루트 경로 호출 시 AI API 서비스 식별용 메시지를 반환한다.

    Returns:
        dict[str, str]: 현재 서버가 정상적으로 기동했는지 빠르게 확인할 수 있는 기본 응답.
    """

    return {
        "message": "The Interview AI API",
    }
