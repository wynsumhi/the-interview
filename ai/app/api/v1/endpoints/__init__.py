from app.api.v1.endpoints.health import router as health_router
from app.api.v1.endpoints.interview import router as interview_router
from app.api.v1.endpoints.rag import router as rag_router
from app.api.v1.endpoints.stt import router as stt_router

__all__ = ["health_router", "interview_router", "rag_router", "stt_router"]
