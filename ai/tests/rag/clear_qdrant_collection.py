from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlsplit

from dotenv import load_dotenv
from qdrant_client import QdrantClient


def _build_client(qdrant_url: str, qdrant_api_key: str) -> QdrantClient:
    parsed = urlsplit(qdrant_url.rstrip("/"))
    if parsed.scheme in {"http", "https"} and parsed.hostname:
        return QdrantClient(
            host=parsed.hostname,
            port=parsed.port or (443 if parsed.scheme == "https" else 6333),
            https=parsed.scheme == "https",
            api_key=qdrant_api_key or None,
            timeout=30.0,
            prefer_grpc=False,
        )

    return QdrantClient(
        url=qdrant_url.rstrip("/"),
        api_key=qdrant_api_key or None,
        timeout=30.0,
        prefer_grpc=False,
    )


def main() -> None:
    load_dotenv(Path(__file__).resolve().parents[2] / ".env")
    qdrant_url = os.getenv("QDRANT_URL", "").strip()
    qdrant_api_key = os.getenv("QDRANT_API_KEY", "").strip()
    collection_name = os.getenv("QDRANT_COLLECTION_NAME", "interview_history").strip()

    if not qdrant_url:
        raise RuntimeError("QDRANT_URL is required.")

    client = _build_client(qdrant_url=qdrant_url, qdrant_api_key=qdrant_api_key)
    try:
        existing = {collection.name for collection in client.get_collections().collections}
        if collection_name not in existing:
            print(f"Collection '{collection_name}' does not exist.")
            return

        client.delete_collection(collection_name=collection_name)
        print(f"Deleted collection '{collection_name}'.")
    finally:
        client.close()


if __name__ == "__main__":
    main()
