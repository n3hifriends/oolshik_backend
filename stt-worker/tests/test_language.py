from stt_worker.language import canonical_lang_code, is_supported_lang, resolve_requested_lang


def test_canonical_lang_code_accepts_locale_variants():
    assert canonical_lang_code("mr-IN") == "mr"
    assert canonical_lang_code("en_US") == "en"


def test_resolve_requested_lang_missing_uses_auto_default():
    assert resolve_requested_lang("", "auto") == (None, True)
    assert resolve_requested_lang(None, "auto") == (None, True)


def test_resolve_requested_lang_explicit_english_routes_without_auto_detect():
    assert resolve_requested_lang("en", "auto") == ("en", False)
    assert resolve_requested_lang("en-IN", "auto") == ("en", False)


def test_resolve_requested_lang_explicit_marathi_routes_without_auto_detect():
    assert resolve_requested_lang("mr", "auto") == ("mr", False)
    assert resolve_requested_lang("mr-IN", "auto") == ("mr", False)


def test_resolve_requested_lang_unsupported_hint_uses_auto_detect():
    assert resolve_requested_lang("xx", "auto") == (None, True)


def test_resolve_requested_lang_explicit_auto_forces_detection_even_with_non_auto_default():
    # An explicit "auto"/"detect" hint must force real detection regardless of a
    # deployment's configured STT_DEFAULT_LANG -- otherwise a caller that explicitly
    # asks for detection could still be silently forced onto a fixed language (e.g. "mr")
    # by an environment whose default isn't "auto".
    assert resolve_requested_lang("auto", "mr") == (None, True)
    assert resolve_requested_lang("detect", "mr") == (None, True)
    assert resolve_requested_lang("AUTO", "mr") == (None, True)


def test_resolve_requested_lang_missing_hint_uses_configured_non_auto_default():
    # A genuinely missing/blank hint (the field wasn't sent) still consults
    # STT_DEFAULT_LANG, even when that default is a specific language rather than "auto".
    assert resolve_requested_lang(None, "mr") == ("mr", False)
    assert resolve_requested_lang("", "mr-IN") == ("mr", False)


def test_is_supported_lang_identifies_indic_detection_candidates():
    assert is_supported_lang("bn")
    assert is_supported_lang("hi")
    assert is_supported_lang("mr")
    assert not is_supported_lang("xx")
