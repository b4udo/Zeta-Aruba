from __future__ import annotations

import logging

from src.chat.llm_client import LlmClient
from src.indexing.embedding_service import EmbeddingService
from src.indexing.vector_store import VectorStore

logger = logging.getLogger(__name__)


class RagService:
    def __init__(
        self,
        *,
        embedding_service: EmbeddingService,
        vector_store: VectorStore,
        llm_client: LlmClient,
        top_k: int,
    ) -> None:
        self.embedding_service = embedding_service
        self.vector_store = vector_store
        self.llm_client = llm_client
        self.top_k = top_k

    async def answer_question(self, *, user_id: str, query: str) -> tuple[str, list[str]]:
        query_embedding = self.embedding_service.embed([query])[0]
        results = self.vector_store.search(user_id=user_id, query_embedding=query_embedding, top_k=self.top_k)
        context_chunks = [item["document"] for item in results if item.get("document")]

        if not context_chunks:
            return (
                "Non ho trovato documenti indicizzati per questo utente. Indicizza prima un PDF o un DOCX e poi riprova.",
                [],
            )

        prompt = self._build_prompt(query=query, context_chunks=context_chunks)
        try:
            answer = await self.llm_client.generate_answer(prompt)
        except Exception as exc:  # pragma: no cover - network defensive fallback
            logger.warning("Falling back to contextual answer because Ollama is unavailable: %s", exc)
            answer = self._build_fallback_answer(query=query, context_chunks=context_chunks)
        return answer, context_chunks

    @staticmethod
    def _build_prompt(*, query: str, context_chunks: list[str]) -> str:
        context = "\n\n".join(f"- {chunk}" for chunk in context_chunks)
        return (
            "Rispondi basandoti solo sui seguenti documenti. "
            "Se l'informazione non è presente, dichiaralo esplicitamente.\n\n"
            f"Contesto:\n{context}\n\n"
            f"Domanda: {query}"
        )

    @staticmethod
    def _build_fallback_answer(*, query: str, context_chunks: list[str]) -> str:
        preview = " ".join(context_chunks[:2])[:500]
        return (
            "Ollama non è al momento raggiungibile, quindi restituisco un riepilogo basato sul contesto recuperato. "
            f"Domanda: {query}. Contesto più rilevante: {preview}"
        )
