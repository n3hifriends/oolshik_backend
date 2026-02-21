from __future__ import annotations

import concurrent.futures
import contextlib
import io
import inspect
import os
import sys
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import torch
from faster_whisper import WhisperModel
from transformers import AutoConfig, AutoModel
from transformers.dynamic_module_utils import get_class_from_dynamic_module

from stt_worker.transcribe.normalization import normalize_text


ENGINE_INDICCONFORMER = "indicconformer"
ENGINE_FASTERWHISPER = "fasterwhisper"

_DEFAULT_LANG = "mr"
_SUPPORTED_LANGUAGES = {
    "as",
    "bn",
    "brx",
    "doi",
    "en",
    "gom",
    "gu",
    "hi",
    "kn",
    "kok",
    "mai",
    "ml",
    "mni",
    "mr",
    "ne",
    "or",
    "pa",
    "sa",
    "sat",
    "sd",
    "ta",
    "te",
    "ur",
}


_NOISY_INIT_LINES = (
    "Please check FRAME_DURATION_MS. The timestamps can be inaccurate",
)


@contextlib.contextmanager
def _suppress_known_init_noise() -> Any:
    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    with contextlib.redirect_stdout(stdout_buf), contextlib.redirect_stderr(stderr_buf):
        yield
    for text, stream in ((stdout_buf.getvalue(), sys.stdout), (stderr_buf.getvalue(), sys.stderr)):
        for line in text.splitlines():
            if any(noisy in line for noisy in _NOISY_INIT_LINES):
                continue
            stream.write(line + "\n")


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


class BaseEngine(ABC):
    engine_name: str
    model_version: str

    def __init__(self, default_lang: str = _DEFAULT_LANG) -> None:
        self.default_lang = (default_lang or _DEFAULT_LANG).strip().lower()
        if self.default_lang not in _SUPPORTED_LANGUAGES:
            self.default_lang = _DEFAULT_LANG

    def resolve_lang(self, lang: Optional[str]) -> str:
        candidate = (lang or self.default_lang).strip().lower()
        if candidate not in _SUPPORTED_LANGUAGES:
            return self.default_lang
        return candidate

    @abstractmethod
    def transcribe(self, wav_tensor: torch.Tensor, lang: str) -> Dict[str, Any]:
        ...


class FasterWhisperEngine(BaseEngine):
    def __init__(
        self,
        model_size: str,
        device: str,
        compute_type: Optional[str] = None,
        timeout_sec: int = 120,
        default_lang: str = _DEFAULT_LANG,
    ) -> None:
        super().__init__(default_lang=default_lang)
        if compute_type is None:
            compute_type = "float16" if device == "cuda" else "int8"

        self.engine_name = ENGINE_FASTERWHISPER
        self.model_version = model_size
        self.timeout_sec = timeout_sec
        self.model = WhisperModel(model_size, device=device, compute_type=compute_type)

    def transcribe(self, wav_tensor: torch.Tensor, lang: str) -> Dict[str, Any]:
        language = self.resolve_lang(lang)

        def _run() -> Dict[str, Any]:
            audio_np = wav_tensor.squeeze(0).detach().cpu().numpy()
            segments_out: List[Segment] = []
            segments, info = self.model.transcribe(
                audio_np,
                language=language,
                vad_filter=True,
            )
            text_parts: List[str] = []
            for seg in segments:
                seg_text = normalize_text(seg.text)
                if seg_text:
                    text_parts.append(seg_text)
                segments_out.append(Segment(start=float(seg.start), end=float(seg.end), text=seg_text))

            full_text = normalize_text(" ".join(text_parts))
            return {
                "text": full_text,
                "language": getattr(info, "language", language) if info else language,
                "confidence": getattr(info, "language_probability", None) if info else None,
                "segments": [
                    {"start": seg.start, "end": seg.end, "text": seg.text}
                    for seg in segments_out
                ],
            }

        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(_run)
            try:
                return future.result(timeout=self.timeout_sec)
            except concurrent.futures.TimeoutError as exc:
                raise TranscribeError("TRANSCRIBE_TIMEOUT", "Transcription timed out", True) from exc
            except TranscribeError:
                raise
            except Exception as exc:  # noqa: BLE001
                raise TranscribeError("TRANSCRIBE_FAILED", str(exc), True) from exc


class IndicConformerEngine(BaseEngine):
    def __init__(
        self,
        model_id: str = "ai4bharat/indic-conformer-600m-multilingual",
        revision: Optional[str] = None,
        decoding: str = "rnnt",
        timeout_sec: int = 120,
        default_lang: str = _DEFAULT_LANG,
        allow_runtime_model_download: bool = False,
    ) -> None:
        super().__init__(default_lang=default_lang)
        normalized_revision = (revision or "").strip() or None
        self.engine_name = ENGINE_INDICCONFORMER
        self.model_version = normalized_revision or model_id
        self.timeout_sec = timeout_sec
        self.decoding = decoding or "rnnt"

        model_ref = os.getenv("ASR_MODEL_PATH", "").strip() or model_id
        local_only = not allow_runtime_model_download

        if local_only and os.path.isabs(model_ref):
            if not os.path.isdir(model_ref):
                msg = (
                    f"Local IndicConformer model directory not found: {model_ref}. "
                    "Place model files there or enable STT_ALLOW_RUNTIME_MODEL_DOWNLOAD."
                )
                raise TranscribeError("MODEL_LOAD_FAILED", msg, False)
            config_path = os.path.join(model_ref, "config.json")
            if not os.path.isfile(config_path):
                msg = (
                    f"Invalid IndicConformer model directory: {model_ref} (missing config.json). "
                    "Ensure the full model snapshot is present."
                )
                raise TranscribeError("MODEL_LOAD_FAILED", msg, False)

        token = os.getenv("HF_TOKEN") or None
        try:
            load_kwargs: Dict[str, Any] = {
                "trust_remote_code": True,
                "revision": normalized_revision,
                "local_files_only": local_only,
            }
            if token:
                load_kwargs["token"] = token
            with _suppress_known_init_noise():
                self.model = AutoModel.from_pretrained(model_ref, **load_kwargs)
        except ValueError as exc:
            # Some remote-code repos can trigger AutoModel registry class identity issues.
            if "config_class" not in str(exc):
                raise
            config_kwargs: Dict[str, Any] = {
                "trust_remote_code": True,
                "revision": normalized_revision,
                "local_files_only": local_only,
            }
            if token:
                config_kwargs["token"] = token
            with _suppress_known_init_noise():
                config = AutoConfig.from_pretrained(model_ref, **config_kwargs)

                auto_map = getattr(config, "auto_map", {}) or {}
                class_ref = auto_map.get("AutoModel")
                if not class_ref:
                    raise
                if isinstance(class_ref, (list, tuple)) and class_ref:
                    class_ref = class_ref[-1]

                dynamic_kwargs: Dict[str, Any] = {
                    "revision": normalized_revision,
                    "local_files_only": local_only,
                }
                if token:
                    dynamic_kwargs["token"] = token
                model_class = get_class_from_dynamic_module(class_ref, model_ref, **dynamic_kwargs)
                self.model = model_class.from_pretrained(model_ref, config=config, **dynamic_kwargs)
        except Exception as exc:  # noqa: BLE001
            msg = f"Failed to load IndicConformer model (ref={model_ref}, local_files_only={local_only})"
            raise TranscribeError("MODEL_LOAD_FAILED", msg, False) from exc

    @staticmethod
    def _filter_kwargs(func: Any, kwargs: Dict[str, Any]) -> Dict[str, Any]:
        try:
            signature = inspect.signature(func)
        except (TypeError, ValueError):
            return kwargs

        params = signature.parameters
        if any(p.kind == inspect.Parameter.VAR_KEYWORD for p in params.values()):
            return kwargs
        return {k: v for k, v in kwargs.items() if k in params}

    def _invoke_model(self, waveform: Any, lang: str) -> Any:
        method_candidates = ["transcribe", "generate", "infer"]
        kwargs_candidates = [
            {"audio": waveform, "language": lang, "decoding": self.decoding},
            {"audio": waveform, "lang": lang, "decoding": self.decoding},
            {"speech": waveform, "language": lang, "decoding": self.decoding},
            {"input": waveform, "language": lang, "decoding": self.decoding},
            {"waveform": waveform, "language": lang, "decoding": self.decoding},
            {"audio": waveform, "language": lang, "decoding_method": self.decoding},
            {"audio": waveform, "language": lang},
            {"audio": waveform, "lang": lang},
            {"waveform": waveform, "language": lang},
            {"input": waveform, "language": lang},
        ]

        for method_name in method_candidates:
            if not hasattr(self.model, method_name):
                continue
            method = getattr(self.model, method_name)
            for kwargs in kwargs_candidates:
                filtered = self._filter_kwargs(method, kwargs)
                if not filtered:
                    continue
                try:
                    return method(**filtered)
                except TypeError:
                    continue

        if callable(self.model):
            callable_attempts = (
                lambda: self.model(waveform, lang=lang),
                lambda: self.model(waveform, language=lang),
                lambda: self.model(waveform, src_lang=lang),
                lambda: self.model(waveform, lang),
                lambda: self.model(waveform),
            )
            last_type_error: Optional[TypeError] = None
            for attempt in callable_attempts:
                try:
                    return attempt()
                except TypeError as exc:
                    last_type_error = exc
                    continue
                except Exception as exc:  # noqa: BLE001
                    raise TranscribeError("TRANSCRIBE_FAILED", str(exc), True) from exc
            if last_type_error is not None:
                raise TranscribeError("TRANSCRIBE_FAILED", str(last_type_error), True) from last_type_error

        raise TranscribeError("TRANSCRIBE_FAILED", "No compatible decode method found", True)

    def _normalize_output(self, raw: Any, requested_lang: str) -> Dict[str, Any]:
        if isinstance(raw, str):
            return {
                "text": normalize_text(raw),
                "language": requested_lang,
                "confidence": None,
                "segments": [],
            }

        payload: Dict[str, Any]
        if isinstance(raw, dict):
            payload = raw
        else:
            payload = {
                "text": getattr(raw, "text", None),
                "language": getattr(raw, "language", None),
                "confidence": getattr(raw, "confidence", None),
                "segments": getattr(raw, "segments", None),
            }

        text = payload.get("text") or payload.get("transcript") or payload.get("prediction") or ""
        if not text and isinstance(payload.get("segments"), list):
            text = " ".join(str(seg.get("text", "")).strip() for seg in payload["segments"] if isinstance(seg, dict))

        normalized_segments: List[Dict[str, Any]] = []
        raw_segments = payload.get("segments") or []
        if isinstance(raw_segments, list):
            for seg in raw_segments:
                if isinstance(seg, dict):
                    normalized_segments.append(
                        {
                            "start": float(seg.get("start", 0.0)),
                            "end": float(seg.get("end", 0.0)),
                            "text": normalize_text(str(seg.get("text", ""))),
                        }
                    )

        confidence = payload.get("confidence")
        if confidence is None:
            confidence = payload.get("score")

        return {
            "text": normalize_text(str(text)),
            "language": (payload.get("language") or payload.get("lang") or requested_lang),
            "confidence": float(confidence) if isinstance(confidence, (int, float)) else None,
            "segments": normalized_segments,
        }

    def transcribe(self, wav_tensor: torch.Tensor, lang: str) -> Dict[str, Any]:
        language = self.resolve_lang(lang)

        def _run() -> Dict[str, Any]:
            waveform = wav_tensor.detach().cpu()
            if waveform.ndim == 1:
                waveform = waveform.unsqueeze(0)
            raw = self._invoke_model(waveform, language)
            return self._normalize_output(raw, language)

        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(_run)
            try:
                return future.result(timeout=self.timeout_sec)
            except concurrent.futures.TimeoutError as exc:
                raise TranscribeError("TRANSCRIBE_TIMEOUT", "Transcription timed out", True) from exc
            except TranscribeError:
                raise
            except Exception as exc:  # noqa: BLE001
                raise TranscribeError("TRANSCRIBE_FAILED", str(exc), True) from exc
