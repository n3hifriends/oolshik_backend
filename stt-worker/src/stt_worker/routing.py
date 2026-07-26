from __future__ import annotations

from typing import Optional


def decide_auto_route_to_primary(
    detected_lang: Optional[str],
    detected_confidence: Optional[float],
    primary_accepts_detected_lang: bool,
    min_confidence: float,
) -> tuple[bool, str]:
    """Decide whether an auto-detected language should be routed to the primary engine.

    English is always excluded: `primary_accepts_detected_lang` reflects a static
    supported-languages check, not per-engine capability, and evaluates true for "en"
    even though the primary (IndicConformer) engine is not meant to transcribe English.
    Any other case that isn't routed keeps the fallback engine's own transcript unchanged
    rather than remapping to a fixed fallback language.
    """
    if not detected_lang:
        return False, "missing_detected_language"
    if detected_lang == "en":
        return False, "detected_english_keeps_fallback"
    if not primary_accepts_detected_lang:
        return False, "unsupported_by_primary_engine"
    if detected_confidence is None:
        return False, "missing_detection_confidence"
    if detected_confidence < min_confidence:
        return False, "low_detection_confidence"
    return True, "detected_language_supported_by_primary"
