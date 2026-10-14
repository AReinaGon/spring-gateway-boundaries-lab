"""Disposable HTTP destination for the gateway contract tests."""
import base64
import json
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

calls = 0
lock = Lock()


class Echo(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

    def log_message(self, *args):
        pass

    def read_body(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    return b"".join(parts)
                parts.append(self.rfile.read(size))
                self.rfile.read(2)
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))

    def respond(self):
        global calls
        if self.path == "/health":
            payload = {"status": "ready"}
        elif self.path == "/stats":
            with lock:
                payload = {"calls": calls}
        else:
            body = self.read_body()
            with lock:
                calls += 1
            payload = {"method": self.command, "path": self.path,
                       "headers": {key.lower(): self.headers.get_all(key) for key in self.headers.keys()},
                       "bodyBytes": len(body), "bodyBase64": base64.b64encode(body).decode("ascii")}
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    do_GET = do_POST = do_DELETE = respond


ThreadingHTTPServer(("0.0.0.0", 8080), Echo).serve_forever()
