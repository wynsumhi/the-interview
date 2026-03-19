from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

import httpx


class EmbeddingClient(Protocol):
    """임베딩 제공 객체가 따라야 하는 최소 인터페이스를 정의한다."""

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """여러 문자열을 같은 순서의 임베딩 벡터 목록으로 변환한다."""


@dataclass(frozen=True)
class Qwen3EmbeddingConfig:
    """Qwen3 임베딩 서버 호출에 필요한 설정값을 묶어 둔다."""

    model: str = "Qwen/Qwen3-Embedding-0.6B"
    base_url: str = "http://localhost:8000/v1"
    api_key: str | None = None
    timeout_seconds: float = 10.0


class Qwen3EmbeddingClient:
    """OpenAI 호환 `/embeddings` 엔드포인트로 Qwen3 임베딩을 호출한다.

    테스트 단계에서는 실제 서버가 없어도 되도록 HTTP 클라이언트를 외부에서 주입받는다.
    이 구조를 사용하면 이후 `app` 계층으로 옮길 때도 동일한 구현을 재사용할 수 있다.
    """

    def __init__(
        self,
        config: Qwen3EmbeddingConfig,
        http_client: httpx.Client | None = None,
    ) -> None:
        """설정과 선택적 HTTP 클라이언트를 받아 임베딩 클라이언트를 초기화한다."""

        self._config = config
        # 테스트에서는 MockTransport 기반 클라이언트를 넣고,
        # 실제 사용 시에는 기본 httpx.Client를 생성해 쓸 수 있게 한다.
        self._http_client = http_client or httpx.Client(
            base_url=config.base_url,
            timeout=config.timeout_seconds,
        )
        # 내부에서 클라이언트를 생성한 경우에만 close 책임을 가진다.
        self._owns_client = http_client is None

    def close(self) -> None:
        """내부에서 생성한 HTTP 클라이언트가 있으면 명시적으로 정리한다."""

        if self._owns_client:
            self._http_client.close()

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """여러 텍스트를 한 번에 Qwen3 임베딩 벡터로 변환한다.

        Args:
            texts: 임베딩할 문자열 목록이다. 빈 리스트는 허용하지 않는다.

        Returns:
            list[list[float]]: 입력 순서를 유지한 임베딩 벡터 목록이다.

        Raises:
            ValueError: 비어 있는 입력이 들어오면 의미 있는 호출이 아니므로 예외를 던진다.
            httpx.HTTPError: 원격 임베딩 서버 호출이 실패하면 상위로 전파한다.
        """

        if not texts:
            raise ValueError("At least one text is required to create embeddings.")

        headers: dict[str, str] = {}
        if self._config.api_key:
            # OpenAI 호환 서버를 가정하므로 Bearer 인증 헤더를 사용한다.
            headers["Authorization"] = f"Bearer {self._config.api_key}"

        response = self._http_client.post(
            "/embeddings",
            json={
                "model": self._config.model,
                "input": texts,
            },
            headers=headers,
        )
        response.raise_for_status()

        payload = response.json()
        data = payload["data"]
        # 응답 순서는 index 기준으로 정렬해 두어야 호출자가 안정적으로 사용할 수 있다.
        sorted_rows = sorted(data, key=lambda row: row["index"])
        return [row["embedding"] for row in sorted_rows]
