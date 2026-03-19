from fastapi import APIRouter

from app.api.v1.endpoints.health import router as health_router
from app.api.v1.endpoints.interview import router as interview_router
from app.api.v1.endpoints.stt import router as stt_router

api_router = APIRouter()
# 서버 생존 여부 확인용 라우터.
api_router.include_router(health_router, prefix="/health", tags=["health"])
# 인터뷰 음성 업로드 및 파이프라인 실행용 라우터.
api_router.include_router(interview_router, prefix="/interview", tags=["interview"])
api_router.include_router(stt_router, prefix="/stt", tags=["stt"])
