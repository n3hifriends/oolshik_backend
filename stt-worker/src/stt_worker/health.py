from __future__ import annotations

import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

_ready = threading.Event()


def set_ready(ready: bool) -> None:
    if ready:
        _ready.set()
    else:
        _ready.clear()


class _HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path in ("/healthz", "/health"):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")
            return
        if self.path in ("/readyz", "/ready"):
            status = 200 if _ready.is_set() else 503
            self.send_response(status)
            self.end_headers()
            self.wfile.write(b"ready" if status == 200 else b"not-ready")
            return
        self.send_response(404)
        self.end_headers()

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return


def start_health_server(port: int) -> None:
    server = ThreadingHTTPServer(("0.0.0.0", port), _HealthHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
