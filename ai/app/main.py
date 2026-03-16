from fastapi import FastAPI

from app.api import api_router

# 애플리케이션 메타데이터와 기본 FastAPI 인스턴스를 정의한다.
app = FastAPI(
    title="The Interview AI API",
    version="0.1.0",
)
# 모든 버전드 API는 /api/v1/ai 하위로 노출한다.
app.include_router(api_router, prefix="/api/v1/ai")


@app.get("/", summary="Root")
def read_root() -> dict[str, str]:
    """서버가 정상 기동 중인지 빠르게 확인할 수 있는 루트 응답을 반환한다.

    Returns:
        dict[str, str]: 현재 AI API 서버가 기동 중임을 나타내는 간단한 메시지.
    """

    # 루트 엔드포인트는 서버 기동 여부를 빠르게 확인하는 용도로 사용한다.
    return {
        "message": "The Interview AI API",
    }
