from __future__ import annotations

from typing import Protocol

import httpx

from app.core import (
    DEFAULT_EMBEDDING_API_KEY,
    DEFAULT_EMBEDDING_BASE_URL,
    DEFAULT_EMBEDDING_MODEL,
    DEFAULT_EMBEDDING_TIMEOUT_SECONDS,
)


class EmbeddingClient(Protocol):
    """런타임 구현과 테스트 스텁이 함께 따르는 임베딩 클라이언트 규약이다."""

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """텍스트 목록을 임베딩 벡터 목록으로 변환한다."""


class OpenAIEmbeddingClient:
    """OpenAI 호환 형식의 임베딩 엔드포인트를 호출한다."""

    def __init__(
        self,
        model: str = DEFAULT_EMBEDDING_MODEL,
        base_url: str = DEFAULT_EMBEDDING_BASE_URL,
        api_key: str | None = DEFAULT_EMBEDDING_API_KEY,
        timeout_seconds: float = DEFAULT_EMBEDDING_TIMEOUT_SECONDS,
        http_client: httpx.Client | None = None,
    ) -> None:
        self._model = model
        self._api_key = api_key
        self._http_client = http_client or httpx.Client(
            base_url=base_url,
            timeout=timeout_seconds,
        )
        self._owns_client = http_client is None

    def close(self) -> None:
        if self._owns_client:
            self._http_client.close()

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            raise ValueError("At least one text is required to create embeddings.")

        headers: dict[str, str] = {}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"

        response = self._http_client.post(
            "/embeddings",
            json={"model": self._model, "input": texts},
            headers=headers,
        )
        response.raise_for_status()

        payload = response.json()
        # 제공자가 응답 행을 뒤섞어 보내도 입력 순서를 그대로 복원한다.
        sorted_rows = sorted(payload["data"], key=lambda row: row["index"])
        return [row["embedding"] for row in sorted_rows]
