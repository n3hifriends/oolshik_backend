from __future__ import annotations

import re


def normalize_text(text: str) -> str:
    text = text.strip()
    text = re.sub(r"\s+", " ", text)
    return text


def deduplicate_repeated_text(text: str, min_span_words: int = 5, match_threshold: float = 0.85) -> str:
    """Collapse a suffix that is a near-repeat of the chunk immediately preceding it.

    Whisper sometimes produces output like "A B C A B C" because the gzip
    compression_ratio_threshold does not reject short repeated phrases — their
    ratio stays below 1.8 due to gzip overhead. This is the engine-level safeguard.
    """
    words = text.split()
    n = len(words)
    if n < min_span_words * 2:
        return text
    lower = [w.lower() for w in words]
    for span in range(n // 2, min_span_words - 1, -1):
        s = n - span
        prev_start = s - span
        if prev_start < 0:
            continue
        matches = sum(1 for i in range(span) if lower[prev_start + i] == lower[s + i])
        if matches >= int(span * match_threshold):
            return " ".join(words[:s]).strip()
    return text
