from stt_worker.retry import backoff_ms, classify_error, ErrorInfo


class DummyError(Exception):
    def __init__(self) -> None:
        self.code = "X"
        self.retryable = False
        super().__init__("fail")


def test_backoff_ms_increases():
    b1 = backoff_ms(1, 100, 1000)
    b2 = backoff_ms(2, 100, 1000)
    assert b2 >= b1


def test_classify_error_uses_attrs():
    info = classify_error(DummyError())
    assert isinstance(info, ErrorInfo)
    assert info.code == "X"
    assert info.retryable is False
