from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI

from src.api.routes import router
from src.chat.llm_client import LlmClient
from src.chat.rag_service import RagService
from src.config import get_settings
from src.indexing.document_processor import DocumentProcessor
from src.indexing.embedding_service import EmbeddingService
from src.indexing.vector_store import VectorStore


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    app.state.settings = settings
    app.state.document_processor = DocumentProcessor(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
    )
    app.state.embedding_service = EmbeddingService(model_name=settings.embedding_model)
    app.state.vector_store = VectorStore(host=settings.chroma_host, port=settings.chroma_port)
    app.state.llm_client = LlmClient(
        base_url=settings.ollama_base_url,
        model=settings.ollama_model,
        timeout_seconds=settings.request_timeout_seconds,
    )
    app.state.rag_service = RagService(
        embedding_service=app.state.embedding_service,
        vector_store=app.state.vector_store,
        llm_client=app.state.llm_client,
        top_k=settings.top_k,
    )
    yield


app = FastAPI(
    title="Piattaforma Zeta AI Engine",
    description="Servizio FastAPI per indexing documentale e chat RAG locale.",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(router)
