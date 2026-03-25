from __future__ import annotations

import asyncio
import logging

import httpx

logger = logging.getLogger(__name__)

RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}
RETRYABLE_EXCEPTIONS = (
    httpx.ConnectError,
    httpx.ConnectTimeout,
    httpx.ReadTimeout,
    httpx.RemoteProtocolError,
)


class LlmClient:
    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        timeout_seconds: float,
        max_attempts: int = 3,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.max_attempts = max_attempts

    async def generate_answer(self, prompt: str) -> str:
        url = f"{self.base_url}/api/chat"
        payload = {
            "model": self.model,
            "stream": False,
            "messages": [
                {
                    "role": "user",
                    "content": prompt,
                }
            ],
        }

        last_error: Exception | None = None
        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            for attempt in range(1, self.max_attempts + 1):
                try:
                    response = await client.post(url, json=payload)
                    if response.status_code in RETRYABLE_STATUS_CODES:
                        message = f"Ollama returned retryable status {response.status_code}"
                        last_error = RuntimeError(message)
                        raise last_error
                    response.raise_for_status()
                    body = response.json()
                    return body.get("message", {}).get("content", "").strip()
                except RETRYABLE_EXCEPTIONS as exc:
                    last_error = exc
                    logger.warning("Retrying Ollama request after transient error (attempt %s/%s): %s", attempt, self.max_attempts, exc)
                except RuntimeError as exc:
                    last_error = exc
                    logger.warning("Retrying Ollama request after transient status (attempt %s/%s): %s", attempt, self.max_attempts, exc)
                except httpx.HTTPStatusError as exc:
                    raise RuntimeError(f"Ollama request failed with status {exc.response.status_code}") from exc

                if attempt < self.max_attempts:
                    await asyncio.sleep(min(2 ** (attempt - 1), 8))

        raise RuntimeError("Ollama is unavailable after multiple attempts") from last_error
