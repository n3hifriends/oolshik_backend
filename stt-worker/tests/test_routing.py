from stt_worker.routing import decide_auto_route_to_primary


def test_formerly_allowlisted_languages_route_to_primary():
    for lang in ("mr", "hi"):
        should_route, reason = decide_auto_route_to_primary(lang, 0.9, True, 0.30)
        assert should_route is True
        assert reason == "detected_language_supported_by_primary"


def test_formerly_excluded_but_supported_languages_now_route_to_primary():
    # Tamil/Telugu were previously force-remapped to a hardcoded Marathi fallback
    # because they weren't in the old STT_AUTO_ROUTE_PRIMARY_LANGS allowlist. They must
    # now route to the primary engine using their own detected language.
    for lang in ("ta", "te"):
        should_route, reason = decide_auto_route_to_primary(lang, 0.9, True, 0.30)
        assert should_route is True
        assert reason == "detected_language_supported_by_primary"


def test_detected_english_never_routes_to_primary():
    # primary_accepts_detected_lang is True for "en" too (it's a static
    # SUPPORTED_LANGUAGES membership check), but IndicConformer must never receive
    # auto-detected English -- it stays on the faster-whisper fallback transcript.
    should_route, reason = decide_auto_route_to_primary("en", 0.95, True, 0.30)
    assert should_route is False
    assert reason == "detected_english_keeps_fallback"


def test_unsupported_by_primary_keeps_fallback():
    should_route, reason = decide_auto_route_to_primary("ta", 0.9, False, 0.30)
    assert should_route is False
    assert reason == "unsupported_by_primary_engine"


def test_missing_detected_language_keeps_fallback():
    should_route, reason = decide_auto_route_to_primary(None, None, False, 0.30)
    assert should_route is False
    assert reason == "missing_detected_language"


def test_missing_confidence_keeps_fallback():
    should_route, reason = decide_auto_route_to_primary("mr", None, True, 0.30)
    assert should_route is False
    assert reason == "missing_detection_confidence"


def test_low_confidence_keeps_fallback():
    should_route, reason = decide_auto_route_to_primary("mr", 0.1, True, 0.30)
    assert should_route is False
    assert reason == "low_detection_confidence"
