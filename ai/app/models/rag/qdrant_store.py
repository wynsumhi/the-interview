from __future__ import annotations

import hashlib
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlsplit

from qdrant_client import QdrantClient
from qdrant_client import models as qdrant_models

from app.core import (
    QDRANT_API_KEY,
    QDRANT_COLLECTION_NAME,
    QDRANT_TIMEOUT_SECONDS,
    QDRANT_URL,
    QDRANT_VECTOR_SIZE,
)


@dataclass(frozen=True)
class QdrantSearchResult:
    """Qdrant 검색 결과에서 필요한 최소 필드만 정리한 구조체다."""

    point_id: str
    score: float
    payload: dict[str, Any]


class QdrantInterviewStore:
    """인터뷰 RAG 포인트를 Qdrant에 저장하고 관리한다."""

    def __init__(
        self,
        collection_name: str = QDRANT_COLLECTION_NAME,
        vector_size: int = QDRANT_VECTOR_SIZE,
        url: str = QDRANT_URL,
        api_key: str | None = QDRANT_API_KEY,
        timeout_seconds: float = QDRANT_TIMEOUT_SECONDS,
        client: QdrantClient | None = None,
    ) -> None:
        if client is not None:
            self._client = client
            self._owns_client = False
        elif url:
            self._client = self._create_remote_client(
                url=url,
                api_key=api_key,
                timeout_seconds=timeout_seconds,
            )
            self._owns_client = True
        else:
            self._client = QdrantClient(location=":memory:")
            self._owns_client = True

        self._collection_name = collection_name
        self._vector_size = vector_size
        self._ensure_collection()
        self._ensure_payload_indexes()

    def _create_remote_client(
        self,
        url: str,
        api_key: str | None,
        timeout_seconds: float,
    ) -> QdrantClient:
        parsed = urlsplit(url.rstrip("/"))
        if parsed.scheme in {"http", "https"} and parsed.hostname:
            return QdrantClient(
                host=parsed.hostname,
                port=parsed.port or (443 if parsed.scheme == "https" else 6333),
                https=parsed.scheme == "https",
                api_key=api_key,
                timeout=timeout_seconds,
                prefer_grpc=False,
            )

        return QdrantClient(
            url=url.rstrip("/"),
            api_key=api_key,
            timeout=timeout_seconds,
            prefer_grpc=False,
        )

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def _ensure_collection(self) -> None:
        existing = self._client.get_collections().collections
        if self._collection_name in {collection.name for collection in existing}:
            return

        self._client.create_collection(
            collection_name=self._collection_name,
            vectors_config=qdrant_models.VectorParams(
                size=self._vector_size,
                distance=qdrant_models.Distance.COSINE,
            ),
        )

    def _ensure_payload_indexes(self) -> None:
        for field_name in ("user_id", "interview_id", "question_id", "answer_id"):
            self._client.create_payload_index(
                collection_name=self._collection_name,
                field_name=field_name,
                field_schema=qdrant_models.PayloadSchemaType.KEYWORD,
                wait=True,
            )

    def upsert(
        self,
        ids: list[str],
        vectors: list[list[float]],
        payloads: list[dict[str, Any]],
    ) -> None:
        if not (len(ids) == len(vectors) == len(payloads)):
            raise ValueError("ids, vectors, payloads must have the same length.")

        points: list[qdrant_models.PointStruct] = []
        for point_id, vector, payload in zip(ids, vectors, payloads, strict=True):
            if len(vector) != self._vector_size:
                raise ValueError("Point vector size does not match collection configuration.")

            points.append(
                qdrant_models.PointStruct(
                    id=self._to_numeric_point_id(point_id),
                    vector=vector,
                    payload={**payload, "external_point_id": point_id},
                )
            )

        self._client.upsert(collection_name=self._collection_name, points=points)

    def delete_by_interview(self, user_id: str, interview_id: str) -> int:
        self._client.delete(
            collection_name=self._collection_name,
            points_selector=qdrant_models.FilterSelector(
                filter=qdrant_models.Filter(
                    must=[
                        qdrant_models.FieldCondition(
                            key="user_id",
                            match=qdrant_models.MatchValue(value=user_id),
                        ),
                        qdrant_models.FieldCondition(
                            key="interview_id",
                            match=qdrant_models.MatchValue(value=interview_id),
                        ),
                    ]
                )
            ),
        )
        return 1

    def delete_by_user(self, user_id: str) -> None:
        self._client.delete(
            collection_name=self._collection_name,
            points_selector=qdrant_models.FilterSelector(
                filter=qdrant_models.Filter(
                    must=[
                        qdrant_models.FieldCondition(
                            key="user_id",
                            match=qdrant_models.MatchValue(value=user_id),
                        )
                    ]
                )
            ),
        )

    def search(
        self,
        *,
        user_id: str,
        query_vector: list[float],
        limit: int,
        score_threshold: float,
    ) -> list[QdrantSearchResult]:
        if len(query_vector) != self._vector_size:
            raise ValueError("Query vector size does not match collection configuration.")

        query_response = self._client.query_points(
            collection_name=self._collection_name,
            query=query_vector,
            query_filter=qdrant_models.Filter(
                must=[
                    qdrant_models.FieldCondition(
                        key="user_id",
                        match=qdrant_models.MatchValue(value=user_id),
                    )
                ]
            ),
            limit=limit,
            score_threshold=score_threshold,
            with_payload=True,
            with_vectors=False,
        )
        return [
            QdrantSearchResult(
                point_id=str((point.payload or {}).get("external_point_id", point.id)),
                score=float(point.score),
                payload=dict(point.payload or {}),
            )
            for point in query_response.points
        ]

    @property
    def collection_name(self) -> str:
        return self._collection_name

    def _to_numeric_point_id(self, point_id: str) -> int:
        """문자열 기반 외부 포인트 ID를 숫자 ID로 변환한다."""

        digest = hashlib.sha1(point_id.encode("utf-8")).hexdigest()
        return int(digest[:16], 16)
