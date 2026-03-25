from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from PyPDF2 import PdfReader
from docx import Document


@dataclass(frozen=True)
class DocumentChunk:
    text: str
    metadata: dict[str, str | int]


class DocumentProcessor:
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 100) -> None:
        if chunk_overlap >= chunk_size:
            raise ValueError("chunk_overlap must be smaller than chunk_size")
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap

    def process_document(
        self,
        *,
        document_id: str,
        user_id: str,
        filename: str,
        content: bytes,
    ) -> list[DocumentChunk]:
        text = self.extract_text(filename=filename, content=content)
        return self.chunk_text(
            text=text,
            document_id=document_id,
            user_id=user_id,
            filename=filename,
        )

    def extract_text(self, *, filename: str, content: bytes) -> str:
        suffix = Path(filename).suffix.lower()
        if suffix == ".pdf":
            reader = PdfReader(BytesIO(content))
            return "\n".join(page.extract_text() or "" for page in reader).strip()
        if suffix == ".docx":
            document = Document(BytesIO(content))
            return "\n".join(paragraph.text for paragraph in document.paragraphs).strip()
        raise ValueError("Unsupported file type. Only PDF and DOCX are supported.")

    def chunk_text(
        self,
        *,
        text: str,
        document_id: str,
        user_id: str,
        filename: str,
    ) -> list[DocumentChunk]:
        normalized = "\n\n".join(
            paragraph.strip() for paragraph in text.splitlines() if paragraph.strip()
        ).strip()
        if not normalized:
            return []

        chunks: list[DocumentChunk] = []
        step = self.chunk_size - self.chunk_overlap
        start = 0
        chunk_index = 0
        while start < len(normalized):
            end = min(start + self.chunk_size, len(normalized))
            chunk_text = normalized[start:end].strip()
            if chunk_text:
                chunks.append(
                    DocumentChunk(
                        text=chunk_text,
                        metadata={
                            "document_id": document_id,
                            "user_id": user_id,
                            "filename": filename,
                            "chunk_index": chunk_index,
                        },
                    )
                )
                chunk_index += 1
            start += step
        return chunks
