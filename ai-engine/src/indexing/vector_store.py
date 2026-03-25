from __future__ import annotations

import logging
import math
from collections import defaultdict
from typing import Any

logger = logging.getLogger(__name__)


class VectorStore:
    def __init__(self, host: str, port: int) -> None:
        self.host = host
        self.port = port
        self._client: Any | None = self._create_client()
        self._memory_store: dict[str, list[dict[str, Any]]] = defaultdict(list)

    def backend_name(self) -> str:
        return "chroma" if self._client is not None else "memory"

    def add_chunks(
        self,
        *,
        user_id: str,
        chunks: list[str],
        embeddings: list[list[float]],
        metadatas: list[dict[str, Any]],
    ) -> None:
        ids = [f"{metadata['document_id']}-{metadata['chunk_index']}" for metadata in metadatas]
        collection_name = self._collection_name(user_id)

        if self._client is not None:
            collection = self._client.get_or_create_collection(name=collection_name)
            collection.upsert(
                ids=ids,
                documents=chunks,
                embeddings=embeddings,
                metadatas=metadatas,
            )
            return

        for item_id, chunk, embedding, metadata in zip(ids, chunks, embeddings, metadatas, strict=True):
            self._memory_store[collection_name].append(
                {
                    "id": item_id,
                    "document": chunk,
                    "embedding": embedding,
                    "metadata": metadata,
                }
            )

    def search(self, *, user_id: str, query_embedding: list[float], top_k: int) -> list[dict[str, Any]]:
        collection_name = self._collection_name(user_id)
        if self._client is not None:
            collection = self._client.get_or_create_collection(name=collection_name)
            response = collection.query(
                query_embeddings=[query_embedding],
                n_results=top_k,
                where={"user_id": user_id},
                include=["documents", "metadatas", "distances"],
            )
            documents = response.get("documents", [[]])[0]
            metadatas = response.get("metadatas", [[]])[0]
            distances = response.get("distances", [[]])[0]
            return [
                {
                    "document": document,
                    "metadata": metadata,
                    "distance": distance,
                }
                for document, metadata, distance in zip(documents, metadatas, distances, strict=False)
            ]

        scored = []
        for item in self._memory_store[collection_name]:
            score = self._cosine_similarity(query_embedding, item["embedding"])
            scored.append(
                {
                    "document": item["document"],
                    "metadata": item["metadata"],
                    "distance": 1 - score,
                }
            )
        return sorted(scored, key=lambda item: item["distance"])[:top_k]

    def delete_document(self, *, user_id: str, document_id: str) -> bool:
        collection_name = self._collection_name(user_id)
        if self._client is not None:
            collection = self._client.get_or_create_collection(name=collection_name)
            collection.delete(where={"document_id": document_id})
            return True

        original_size = len(self._memory_store[collection_name])
        self._memory_store[collection_name] = [
            item
            for item in self._memory_store[collection_name]
            if item["metadata"]["document_id"] != document_id
        ]
        return len(self._memory_store[collection_name]) != original_size

    def _create_client(self) -> Any | None:
        try:
            import chromadb

            return chromadb.HttpClient(host=self.host, port=self.port)
        except Exception as exc:  # pragma: no cover - defensive fallback
            logger.warning("ChromaDB is not available, falling back to in-memory vector store: %s", exc)
            return None

    @staticmethod
    def _collection_name(user_id: str) -> str:
        return f"user_{user_id}"

    @staticmethod
    def _cosine_similarity(left: list[float], right: list[float]) -> float:
        numerator = sum(a * b for a, b in zip(left, right, strict=False))
        left_norm = math.sqrt(sum(value * value for value in left)) or 1.0
        right_norm = math.sqrt(sum(value * value for value in right)) or 1.0
        return numerator / (left_norm * right_norm)
