from src.indexing.document_processor import DocumentProcessor


def test_chunk_text_preserves_metadata_and_size() -> None:
    processor = DocumentProcessor(chunk_size=80, chunk_overlap=20)
    text = ("Questo e un documento di prova. " * 20).strip()

    chunks = processor.chunk_text(
        text=text,
        document_id="doc-1",
        user_id="user-1",
        filename="sample.docx",
    )

    assert chunks
    assert all(len(chunk.text) <= 80 for chunk in chunks)
    assert chunks[0].metadata["document_id"] == "doc-1"
    assert chunks[0].metadata["user_id"] == "user-1"
    assert chunks[0].metadata["chunk_index"] == 0
    assert chunks[-1].metadata["chunk_index"] == len(chunks) - 1
