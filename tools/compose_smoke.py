"""Exercise the same real HTTP contract against both Compose applications."""
import base64
import argparse
import http.client
import json
import os
import time

parser = argparse.ArgumentParser()
parser.add_argument("--performance", action="store_true", help="Require disabled lab instrumentation")
args = parser.parse_args()

ports = {"native": int(os.environ.get("NATIVE_PORT", "18081")), "mapped": int(os.environ.get("MAPPED_PORT", "18082"))}
evidence = {}
for name, port in ports.items():
    deadline = time.monotonic() + 60
    while True:
        try:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=3)
            connection.request("GET", "/inspect/catalog", headers={"X-Workspace": "alpha", "Authorization": "Bearer demo-alpha-reader"})
            response = connection.getresponse()
            response.read()
            connection.close()
            if response.status == 200:
                break
        except (OSError, http.client.HTTPException):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError(f"{name} did not become ready")
        time.sleep(1)
    payload = "árbol\n文".encode("utf-8")
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    connection.putrequest("POST", "/inspect/catalog?tag=a&tag=b&encoded=a%2Fb")
    for header, value in [("X-Workspace", " AlPhA "), ("Authorization", "Bearer demo-alpha-writer"),
                          ("X-Note", "first"), ("X-Note", "second"), ("X-Lab-Role", "reader"),
                          ("X-Lab-Untrusted", "remove-me"), ("Content-Type", "application/octet-stream"),
                          ("Content-Length", str(len(payload)))]:
        connection.putheader(header, value)
    connection.endheaders(payload)
    response = connection.getresponse()
    assert response.status == 200, response.status
    headers = dict(response.getheaders())
    body = json.loads(response.read())
    connection.close()
    assert body["path"] == "/inspect/catalog?tag=a&tag=b&encoded=a%2Fb"
    assert body["bodyBase64"] == base64.b64encode(payload).decode("ascii")
    assert body["headers"]["x-note"] == ["first", "second"]
    assert body["headers"]["x-workspace"] == ["alpha"]
    assert body["headers"]["x-lab-subject"] == ["bob"]
    assert body["headers"]["x-lab-action"] == ["write"]
    assert "authorization" not in body["headers"]
    assert "x-lab-untrusted" not in body["headers"]
    lookup = {key.lower(): value for key, value in headers.items()}
    if args.performance:
        assert "x-lab-trace" not in lookup and "x-lab-mapping" not in lookup
    else:
        assert lookup["x-lab-trace"] == "size,normalize,token,authorize"
        assert lookup["x-lab-mapping"] == ("reads=4; applies=4" if name == "mapped" else "reads=0; applies=0")
    evidence[name] = {"status": response.status, "trace": lookup.get("x-lab-trace"), "mapping": lookup.get("x-lab-mapping"), "bodyBytes": body["bodyBytes"], "headers": body["headers"]}
    for description, extra, expected in [("unknown token", {"Authorization": "Bearer unknown"}, 401),
                                           ("reader cannot write", {"Authorization": "Bearer demo-alpha-reader"}, 403),
                                           ("oversized body", {"Authorization": "Bearer demo-alpha-writer"}, 413)]:
        connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        connection.request("POST", "/inspect/catalog", body=b"x" * (1025 if expected == 413 else 0),
                           headers={"X-Workspace": "alpha", **extra})
        denied = connection.getresponse()
        assert denied.status == expected, (description, denied.status)
        denied.read()
        connection.close()
    print(f"PASS {name}: transformed request plus three rejection cases")
from pathlib import Path
output = Path(__file__).resolve().parents[1] / ("results/generated/compose-smoke-performance.json" if args.performance else "results/generated/compose-smoke.json")
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
