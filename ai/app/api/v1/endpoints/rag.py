from fastapi import APIRouter, HTTPException, status

from app.api.v1.schemas.rag import (
    InterviewRAGDeleteRequest,
    InterviewRAGDeleteResponse,
    InterviewRAGReindexRequest,
    InterviewRAGReindexResponse,
    InterviewRAGUpsertRequest,
    InterviewRAGUpsertResponse,
)
from app.services.rag_service import (
    delete_interview_history_rag,
    reindex_interview_history_rag,
    upsert_interview_history_rag,
)

router = APIRouter()


@router.post(
    "/upsert",
    response_model=InterviewRAGUpsertResponse,
    summary="Upsert interview history into user RAG",
)
def upsert_interview_history_rag_endpoint(
    request: InterviewRAGUpsertRequest,
) -> InterviewRAGUpsertResponse:
    """인터뷰 히스토리 하나를 사용자 RAG 인덱스에 추가한다."""

    try:
        result = upsert_interview_history_rag(request.model_dump())
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewRAGUpsertResponse.model_validate(result)


@router.delete(
    "/delete",
    response_model=InterviewRAGDeleteResponse,
    summary="Delete interview history from user RAG",
)
def delete_interview_history_rag_endpoint(
    request: InterviewRAGDeleteRequest,
) -> InterviewRAGDeleteResponse:
    """인터뷰 히스토리 하나를 사용자 RAG 인덱스에서 제거한다."""

    try:
        result = delete_interview_history_rag(request.model_dump())
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewRAGDeleteResponse.model_validate(result)


@router.post(
    "/reindex",
    response_model=InterviewRAGReindexResponse,
    summary="Reindex user interview history RAG",
)
def reindex_interview_history_rag_endpoint(
    request: InterviewRAGReindexRequest,
) -> InterviewRAGReindexResponse:
    """사용자 전체 히스토리로 RAG 인덱스를 다시 만든다."""

    try:
        result = reindex_interview_history_rag(request.model_dump())
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return InterviewRAGReindexResponse.model_validate(result)
