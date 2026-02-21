from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Dict

_ready = threading.Event()
_engine = "unknown"
_state_lock = threading.Lock()


def set_ready(ready: bool) -> None:
    if ready:
        _ready.set()
    else:
        _ready.clear()


def set_engine(engine: str) -> None:
    global _engine
    with _state_lock:
        _engine = engine


def health_payload() -> Dict[str, object]:
    with _state_lock:
        engine = _engine
    model_ready = _ready.is_set()
    return {
        "status": "UP" if model_ready else "DOWN",
        "engine": engine,
        "model_ready": model_ready,
    }


class _HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path in ("/health",):
            payload = health_payload()
            body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
            status = 200 if payload["model_ready"] else 503
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if self.path in ("/healthz",):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"UP"}')
            return

        if self.path in ("/readyz", "/ready"):
            status = 200 if _ready.is_set() else 503
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"UP"}' if status == 200 else b'{"status":"DOWN"}')
            return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return


def start_health_server(port: int) -> None:
    server = ThreadingHTTPServer(("0.0.0.0", port), _HealthHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
