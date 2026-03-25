from __future__ import annotations

from fastapi import APIRouter, File, Form, HTTPException, Query, Request, UploadFile

from src.api.schemas import ChatRequest, ChatResponse, DeleteResponse, HealthResponse, IndexResponse

router = APIRouter(prefix="/api/v1/ai", tags=["ai"])


@router.get("/health", response_model=HealthResponse)
async def health(request: Request) -> HealthResponse:
    return HealthResponse(
        status="UP",
        vector_backend=request.app.state.vector_store.backend_name(),
        llm_model=request.app.state.settings.ollama_model,
    )


@router.post("/index", response_model=IndexResponse)
async def index_document(
    request: Request,
    document_id: str = Form(...),
    user_id: str = Form(...),
    file: UploadFile = File(...),
) -> IndexResponse:
    content = await file.read()
    try:
        chunks = request.app.state.document_processor.process_document(
            document_id=document_id,
            user_id=user_id,
            filename=file.filename or "document",
            content=content,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if not chunks:
        raise HTTPException(status_code=400, detail="Il documento non contiene testo indicizzabile.")

    texts = [chunk.text for chunk in chunks]
    metadatas = [chunk.metadata for chunk in chunks]
    embeddings = request.app.state.embedding_service.embed(texts)
    request.app.state.vector_store.add_chunks(
        user_id=user_id,
        chunks=texts,
        embeddings=embeddings,
        metadatas=metadatas,
    )

    return IndexResponse(
        document_id=document_id,
        user_id=user_id,
        chunk_count=len(chunks),
        message="Documento indicizzato con successo",
    )


@router.post("/chat", response_model=ChatResponse)
async def chat(request: Request, payload: ChatRequest) -> ChatResponse:
    answer, context_chunks = await request.app.state.rag_service.answer_question(
        user_id=payload.user_id,
        query=payload.query,
    )
    return ChatResponse(answer=answer, context_chunks=context_chunks)


@router.delete("/index/{document_id}", response_model=DeleteResponse)
async def delete_document(
    request: Request,
    document_id: str,
    user_id: str = Query(..., min_length=1),
) -> DeleteResponse:
    deleted = request.app.state.vector_store.delete_document(user_id=user_id, document_id=document_id)
    return DeleteResponse(document_id=document_id, user_id=user_id, deleted=deleted)
