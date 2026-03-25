from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    user_id: str = Field(..., min_length=1)
    query: str = Field(..., min_length=1)


class ChatResponse(BaseModel):
    answer: str
    context_chunks: list[str] = Field(default_factory=list)


class IndexResponse(BaseModel):
    document_id: str
    user_id: str
    chunk_count: int
    message: str


class DeleteResponse(BaseModel):
    document_id: str
    user_id: str
    deleted: bool


class HealthResponse(BaseModel):
    status: str
    vector_backend: str
    llm_model: str
