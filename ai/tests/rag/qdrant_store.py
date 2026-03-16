from __future__ import annotations

import hashlib
from dataclasses import dataclass
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client import models as qdrant_models


@dataclass(frozen=True)
class QdrantCollectionConfig:
    """테스트용 Qdrant 컬렉션 설정을 한곳에 모아 둔 구조체다."""

    collection_name: str = "interview_history"
    vector_size: int = 5
    distance: qdrant_models.Distance = qdrant_models.Distance.COSINE
    location: str = ":memory:"


@dataclass(frozen=True)
class QdrantSearchResult:
    """Qdrant 검색 결과에서 현재 RAG 로직에 필요한 값만 정리한 구조체다."""

    point_id: str
    score: float
    payload: dict[str, Any]


class QdrantInterviewStore:
    """`qdrant-client`를 직접 사용하는 테스트 전용 인터뷰 저장소다.

    Qdrant Python 클라이언트는 `location=":memory:"` 로컬 모드를 지원한다.
    이 모드를 사용하면 별도 Qdrant 서버 없이도 실제 컬렉션 생성, 포인트 적재,
    벡터 검색 API를 테스트할 수 있다.
    """

    def __init__(
        self,
        config: QdrantCollectionConfig,
        client: QdrantClient | None = None,
    ) -> None:
        """컬렉션 설정과 선택적 Qdrant 클라이언트를 받아 저장소를 준비한다."""

        self._config = config
        self._client = client or QdrantClient(location=config.location)
        self._owns_client = client is None
        self._ensure_collection()

    def close(self) -> None:
        """내부에서 생성한 Qdrant 클라이언트가 있으면 정리한다."""

        if self._owns_client:
            self._client.close()

    def _ensure_collection(self) -> None:
        """테스트에 필요한 컬렉션이 없으면 생성하고, 있으면 재사용한다."""

        existing_collections = self._client.get_collections().collections
        collection_names = {collection.name for collection in existing_collections}
        if self._config.collection_name in collection_names:
            return

        self._client.create_collection(
            collection_name=self._config.collection_name,
            vectors_config=qdrant_models.VectorParams(
                size=self._config.vector_size,
                distance=self._config.distance,
            ),
        )

    def upsert(
        self,
        ids: list[str],
        vectors: list[list[float]],
        payloads: list[dict[str, Any]],
    ) -> None:
        """인터뷰 턴들을 Qdrant 포인트로 변환해 컬렉션에 upsert 한다."""

        if not (len(ids) == len(vectors) == len(payloads)):
            raise ValueError("ids, vectors, payloads must have the same length.")

        points: list[qdrant_models.PointStruct] = []
        for point_id, vector, payload in zip(ids, vectors, payloads, strict=True):
            if len(vector) != self._config.vector_size:
                raise ValueError("Point vector size does not match collection configuration.")
            # Qdrant 로컬 모드는 문자열 ID에 대해 UUID 형식을 기대하므로,
            # 사람이 읽는 외부 ID는 payload에 유지하고 실제 저장 ID는 결정적 정수로 변환한다.
            points.append(
                qdrant_models.PointStruct(
                    id=self._to_numeric_point_id(point_id),
                    vector=vector,
                    payload={
                        **payload,
                        "external_point_id": point_id,
                    },
                )
            )

        self._client.upsert(
            collection_name=self._config.collection_name,
            points=points,
        )

    def search(
        self,
        query_vector: list[float],
        limit: int,
        score_threshold: float,
    ) -> list[QdrantSearchResult]:
        """질의 벡터로 실제 Qdrant 검색 API를 호출해 결과를 반환한다."""

        if len(query_vector) != self._config.vector_size:
            raise ValueError("Query vector size does not match collection configuration.")

        query_response = self._client.query_points(
            collection_name=self._config.collection_name,
            query=query_vector,
            limit=limit,
            score_threshold=score_threshold,
            with_payload=True,
            with_vectors=False,
        )
        scored_points = query_response.points
        return [
            QdrantSearchResult(
                point_id=str((point.payload or {}).get("external_point_id", point.id)),
                score=float(point.score),
                payload=dict(point.payload or {}),
            )
            for point in scored_points
        ]

    def count(self) -> int:
        """현재 컬렉션에 저장된 포인트 개수를 조회한다."""

        collection_info = self._client.count(
            collection_name=self._config.collection_name,
            exact=True,
        )
        return int(collection_info.count)

    def _to_numeric_point_id(self, point_id: str) -> int:
        """문자열 포인트 ID를 Qdrant 로컬 모드가 허용하는 정수 ID로 바꾼다."""

        digest = hashlib.sha1(point_id.encode("utf-8")).hexdigest()
        return int(digest[:16], 16)
