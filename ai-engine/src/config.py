from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ai-engine"
    host: str = "0.0.0.0"
    port: int = 8000
    chunk_size: int = 500
    chunk_overlap: int = 100
    top_k: int = Field(default=5, validation_alias="AI_TOP_K")
    chroma_host: str = Field(default="localhost", validation_alias="CHROMA_HOST")
    chroma_port: int = Field(default=8100, validation_alias="CHROMA_PORT")
    ollama_base_url: str = Field(default="http://localhost:11434", validation_alias="OLLAMA_BASE_URL")
    ollama_model: str = Field(default="mistral:7b", validation_alias="OLLAMA_MODEL")
    embedding_model: str = "all-MiniLM-L6-v2"
    request_timeout_seconds: float = Field(default=60.0, validation_alias="AI_REQUEST_TIMEOUT_SECONDS")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="",
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
