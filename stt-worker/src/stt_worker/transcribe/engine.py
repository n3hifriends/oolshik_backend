from __future__ import annotations

import concurrent.futures
from dataclasses import dataclass
from typing import List, Optional

from faster_whisper import WhisperModel

from stt_worker.transcribe.normalization import normalize_text


@dataclass
class Segment:
    start: float
    end: float
    text: str


@dataclass
class TranscriptionResult:
    text: str
    language: Optional[str]
    language_probability: Optional[float]
    segments: List[Segment]


class TranscribeError(Exception):
    def __init__(self, code: str, message: str, retryable: bool = True) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable


class FasterWhisperEngine:
    def __init__(self, model_size: str, device: str, compute_type: Optional[str] = None) -> None:
        if compute_type is None:
            compute_type = "float16" if device == "cuda" else "int8"
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self.model = WhisperModel(model_size, device=device, compute_type=compute_type)

    def transcribe(self, audio_path: str, language_hint: Optional[str], timeout_sec: int) -> TranscriptionResult:
        def _run() -> TranscriptionResult:
            segments_out: List[Segment] = []
            segments, info = self.model.transcribe(
                audio_path,
                language=language_hint,
                vad_filter=True,
            )
            text_parts: List[str] = []
            for seg in segments:
                seg_text = normalize_text(seg.text)
                if seg_text:
                    text_parts.append(seg_text)
                segments_out.append(Segment(start=seg.start, end=seg.end, text=seg_text))

            full_text = normalize_text(" ".join(text_parts))
            return TranscriptionResult(
                text=full_text,
                language=info.language if info else None,
                language_probability=getattr(info, "language_probability", None) if info else None,
                segments=segments_out,
            )

        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(_run)
            try:
                return future.result(timeout=timeout_sec)
            except concurrent.futures.TimeoutError as exc:
                raise TranscribeError("TRANSCRIBE_TIMEOUT", "Transcription timed out", True) from exc
            except Exception as exc:  # noqa: BLE001
                raise TranscribeError("TRANSCRIBE_FAILED", str(exc), True) from exc
