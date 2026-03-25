from __future__ import annotations

import hashlib
import logging
import math
from typing import Any

logger = logging.getLogger(__name__)


class EmbeddingService:
    def __init__(self, model_name: str, vector_size: int = 384) -> None:
        self.model_name = model_name
        self.vector_size = vector_size
        self._model: Any | None = None
        self._load_attempted = False

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        model = self._get_model()
        if model is not None:
            vectors = model.encode(texts, normalize_embeddings=True)
            return [list(map(float, vector)) for vector in vectors]
        return [self._fallback_embedding(text) for text in texts]

    def _get_model(self) -> Any | None:
        if self._load_attempted:
            return self._model

        self._load_attempted = True
        try:
            from sentence_transformers import SentenceTransformer

            self._model = SentenceTransformer(self.model_name)
        except Exception as exc:  # pragma: no cover - defensive fallback
            logger.warning("Falling back to deterministic embeddings because the sentence-transformers model is unavailable: %s", exc)
            self._model = None
        return self._model

    def _fallback_embedding(self, text: str) -> list[float]:
        raw = hashlib.sha256(text.encode("utf-8")).digest()
        values = [raw[index % len(raw)] / 255.0 for index in range(self.vector_size)]
        norm = math.sqrt(sum(value * value for value in values)) or 1.0
        return [value / norm for value in values]
