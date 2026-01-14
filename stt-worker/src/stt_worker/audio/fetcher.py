from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass
class FetchResult:
    path: str
    size_bytes: int
    content_type: str | None


class AudioFetcher(Protocol):
    def fetch(self, url: str, dest_path: str, timeout_sec: int, max_bytes: int) -> FetchResult:
        ...
