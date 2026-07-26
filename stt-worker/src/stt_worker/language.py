from __future__ import annotations

from typing import Optional


SUPPORTED_LANGUAGES = frozenset(
    {
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
)


def canonical_lang_code(value: object) -> Optional[str]:
    candidate = str(value or "").strip().lower()
    if candidate in {"", "auto", "detect"}:
        return None
    for sep in ("-", "_"):
        if sep in candidate:
            candidate = candidate.split(sep, 1)[0].strip()
            break
    return candidate or None


def is_supported_lang(lang: Optional[str]) -> bool:
    candidate = canonical_lang_code(lang)
    return bool(candidate and candidate in SUPPORTED_LANGUAGES)


def resolve_requested_lang(raw_hint: Optional[str], default_hint: str) -> tuple[Optional[str], bool]:
    hint = (raw_hint or "").strip().lower()
    # An explicit "auto"/"detect" hint always forces real detection, regardless of
    # STT_DEFAULT_LANG: a caller that explicitly asks for detection must get it even in
    # a deployment whose configured default isn't "auto". Only a genuinely missing/blank
    # hint (the field wasn't sent at all) falls through to the configured default below.
    if hint in {"auto", "detect"}:
        return None, True
    if hint == "":
        hint = (default_hint or "").strip().lower()
    if hint in {"", "auto", "detect"}:
        return None, True

    lang = canonical_lang_code(hint)
    if is_supported_lang(lang):
        return lang, False
    return None, True
