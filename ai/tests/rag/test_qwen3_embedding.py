from __future__ import annotations

import httpx

from tests.rag.qwen3_embedding import Qwen3EmbeddingClient, Qwen3EmbeddingConfig


def test_qwen3_embedding_client_sends_openai_compatible_request() -> None:
    """Qwen3 임베딩 클라이언트가 OpenAI 호환 요청 형식을 지키는지 검증한다."""

    def handler(request: httpx.Request) -> httpx.Response:
        # 테스트에서는 요청 본문과 헤더를 직접 검증해 실제 서버 없이 계약을 확인한다.
        assert request.method == "POST"
        assert str(request.url) == "http://embedding.local/v1/embeddings"
        assert request.headers["Authorization"] == "Bearer test-key"
        assert request.content == (
            b'{"model":"Qwen/Qwen3-Embedding-0.6B","input":["first","second"]}'
        )
        return httpx.Response(
            status_code=200,
            json={
                "data": [
                    {"index": 1, "embedding": [0.0, 1.0]},
                    {"index": 0, "embedding": [1.0, 0.0]},
                ]
            },
        )

    http_client = httpx.Client(
        base_url="http://embedding.local/v1",
        transport=httpx.MockTransport(handler),
    )
    client = Qwen3EmbeddingClient(
        config=Qwen3EmbeddingConfig(
            base_url="http://embedding.local/v1",
            api_key="test-key",
        ),
        http_client=http_client,
    )

    embeddings = client.embed_texts(["first", "second"])

    assert embeddings == [[1.0, 0.0], [0.0, 1.0]]


def test_qwen3_embedding_client_rejects_empty_input() -> None:
    """빈 입력은 잘못된 사용이므로 명확한 예외를 내보내야 한다."""

    client = Qwen3EmbeddingClient(
        config=Qwen3EmbeddingConfig(),
        http_client=httpx.Client(
            base_url="http://embedding.local/v1",
            transport=httpx.MockTransport(lambda request: httpx.Response(status_code=500)),
        ),
    )

    try:
        client.embed_texts([])
    except ValueError as error:
        assert str(error) == "At least one text is required to create embeddings."
    else:
        raise AssertionError("ValueError was not raised for empty input.")
