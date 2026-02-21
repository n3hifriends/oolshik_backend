from __future__ import annotations

import os
import time
from typing import Optional

import httpx

from stt_worker.audio.fetcher import FetchResult


class AudioDownloadError(Exception):
    def __init__(self, code: str, message: str, retryable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable


def _retryable_for_status(status: int) -> bool:
    if status == 429:
        return True
    return 500 <= status <= 599


class HttpAudioFetcher:
    def fetch(
        self,
        url: str,
        dest_path: str,
        timeout_sec: int,
        max_bytes: int,
        retries: int = 2,
        backoff_base_sec: float = 0.5,
    ) -> FetchResult:
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        attempts = retries + 1
        timeout = httpx.Timeout(timeout_sec)

        last_error: Optional[AudioDownloadError] = None
        for attempt in range(attempts):
            try:
                with httpx.stream("GET", url, timeout=timeout, follow_redirects=True) as resp:
                    if resp.status_code >= 400:
                        retryable = _retryable_for_status(resp.status_code)
                        raise AudioDownloadError("DOWNLOAD_FAILED", f"HTTP {resp.status_code}", retryable)

                    content_type: Optional[str] = resp.headers.get("content-type")
                    content_length = resp.headers.get("content-length")
                    if content_length:
                        try:
                            declared_size = int(content_length)
                            if declared_size > max_bytes:
                                raise AudioDownloadError(
                                    "AUDIO_TOO_LARGE",
                                    f"Audio exceeds {max_bytes} bytes",
                                    False,
                                )
                        except ValueError:
                            pass

                    size = 0
                    with open(dest_path, "wb") as out:
                        for chunk in resp.iter_bytes():
                            if not chunk:
                                continue
                            size += len(chunk)
                            if size > max_bytes:
                                raise AudioDownloadError(
                                    "AUDIO_TOO_LARGE",
                                    f"Audio exceeds {max_bytes} bytes",
                                    False,
                                )
                            out.write(chunk)

                if size == 0:
                    raise AudioDownloadError("DOWNLOAD_FAILED", "Empty audio", False)

                return FetchResult(path=dest_path, size_bytes=size, content_type=content_type)
            except AudioDownloadError as exc:
                last_error = exc
                if (not exc.retryable) or (attempt >= attempts - 1):
                    raise
            except httpx.TimeoutException as exc:
                last_error = AudioDownloadError("DOWNLOAD_TIMEOUT", str(exc), True)
                if attempt >= attempts - 1:
                    raise last_error from exc
            except httpx.RequestError as exc:
                last_error = AudioDownloadError("DOWNLOAD_FAILED", str(exc), True)
                if attempt >= attempts - 1:
                    raise last_error from exc

            sleep_sec = backoff_base_sec * (2 ** attempt)
            time.sleep(sleep_sec)

        if last_error:
            raise last_error
        raise AudioDownloadError("DOWNLOAD_FAILED", "Unknown download error", True)
