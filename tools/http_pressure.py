"""Open-arrival pressure experiment. Retains failures and client queue delay.

The direct echo control diagnoses shared limits; it is not a latency subtraction.
Run after JMH, never at the same time. No production capacity claim.
"""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import http.client
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import threading
import time

parser = argparse.ArgumentParser()
parser.add_argument("--rates", default="100,300,600,900")
parser.add_argument("--seconds", type=int, default=20)
parser.add_argument("--warmup-seconds", type=int, default=60)
parser.add_argument("--rounds", type=int, default=3)
parser.add_argument("--workers", type=int, default=64)
parser.add_argument("--slo-ms", type=float, default=20)
parser.add_argument("--output", default="results/generated/http-pressure")
args = parser.parse_args()
rates = [int(value) for value in args.rates.split(",")]
if min(rates) <= 0 or min(args.seconds, args.rounds, args.workers) <= 0:
    raise ValueError("Rates, duration, rounds and workers must be positive")
root = Path(__file__).resolve().parents[1]
output = root / args.output
output.mkdir(parents=True, exist_ok=True)
compose = ["docker", "compose", "-f", "compose.yaml", "-f", "compose.perf.yaml"]
ports = {"native": int(os.environ.get("NATIVE_PORT", "18081")),
         "mapped": int(os.environ.get("MAPPED_PORT", "18082")),
         "upstream": int(os.environ.get("UPSTREAM_PORT", "18080"))}
payload = b"x" * 64
encoded_payload = base64.b64encode(payload).decode("ascii")
thread_state = threading.local()
containers = {name: subprocess.check_output(compose + ["ps", "--quiet", name], cwd=root, text=True).strip() for name in ports}
if not all(containers.values()):
    raise RuntimeError("Start all comparison containers first")


def container_text(name, path):
    return subprocess.check_output(["docker", "exec", containers[name], "cat", path], text=True)


def metrics(name):
    cpu = {key: int(value) for key, value in (line.split() for line in container_text(name, "/sys/fs/cgroup/cpu.stat").splitlines())}
    status = container_text(name, "/proc/1/status")
    rss = int(re.search(r"VmRSS:\s+(\d+)", status).group(1))
    gc_text = container_text(name, "/tmp/lab-gc.log") if name != "upstream" else ""
    pauses = [float(value) for value in re.findall(r"Pause .*? ([\d.]+)ms", gc_text)]
    return {"cpu": cpu, "rss_kib": rss, "gc_pauses": len(pauses), "gc_pause_ms": sum(pauses)}


def request(name, scheduled):
    started = time.perf_counter()
    valid, status, error = False, None, None
    try:
        if not hasattr(thread_state, "connections"):
            thread_state.connections = {}
        connection = thread_state.connections.get(name)
        if connection is None:
            connection = http.client.HTTPConnection("127.0.0.1", ports[name], timeout=3)
            thread_state.connections[name] = connection
        connection.putrequest("POST", "/inspect/catalog?tag=a&tag=b")
        headers = [("X-Workspace", "alpha" if name == "upstream" else "AlPhA"),
                   ("X-Note", "one"), ("X-Note", "two"),
                   ("Content-Type", "application/octet-stream"), ("Content-Length", str(len(payload)))]
        headers += [("X-Lab-Subject", "bob"), ("X-Lab-Action", "write")] if name == "upstream" else [("Authorization", "Bearer demo-alpha-writer")]
        for key, value in headers:
            connection.putheader(key, value)
        for index in range(8):
            connection.putheader(f"X-Extra-{index}", f"value-{index}")
        connection.endheaders(payload)
        response = connection.getresponse()
        status = response.status
        raw = response.read()
        finished = time.perf_counter()
        received_headers = {key.lower(): value for key, value in response.getheaders()}
        body = json.loads(raw)
        valid = (status == 200 and body["method"] == "POST"
                 and body["path"] == "/inspect/catalog?tag=a&tag=b"
                 and body["bodyBase64"] == encoded_payload and body["bodyBytes"] == len(payload)
                 and body["headers"].get("x-workspace") == ["alpha"]
                 and body["headers"].get("x-lab-subject") == ["bob"]
                 and body["headers"].get("x-lab-action") == ["write"]
                 and body["headers"].get("x-note") == ["one", "two"]
                 and "authorization" not in body["headers"]
                 and "x-lab-trace" not in received_headers and "x-lab-mapping" not in received_headers)
        if not valid:
            error = "unexpected response or instrumentation enabled"
    except Exception as exception:
        finished = time.perf_counter()
        error = type(exception).__name__ + ": " + str(exception)
        connection = getattr(thread_state, "connections", {}).pop(name, None)
        if connection:
            connection.close()
    return {"scheduled_ms": (finished - scheduled) * 1000, "service_ms": (finished - started) * 1000,
            "dispatch_ms": (started - scheduled) * 1000, "status": status, "valid": valid,
            "slo_met": valid and (finished - scheduled) * 1000 <= args.slo_ms,
            "error": error, "finished": finished}


def percentile(values, percent):
    ordered = sorted(values)
    position = (len(ordered) - 1) * percent / 100
    lower = int(position)
    return ordered[lower] + (position - lower) * (ordered[min(lower + 1, len(ordered) - 1)] - ordered[lower])


def distribution(values):
    return {"p50": percentile(values, 50), "p95": percentile(values, 95), "p99": percentile(values, 99),
            "mean": statistics.mean(values), "max": max(values)} if values else None


def run(executor, name, seconds, rate):
    start = time.perf_counter() + 0.2
    client_cpu_before = time.process_time()
    futures = []
    for index in range(seconds * rate):
        scheduled = start + index / rate
        wait = scheduled - time.perf_counter()
        if wait > 0:
            time.sleep(wait)
        futures.append(executor.submit(request, name, scheduled))
    records = [future.result() for future in futures]
    elapsed = max(record["finished"] for record in records) - start
    return records, elapsed, time.process_time() - client_cpu_before


environment = {}
for name, identifier in containers.items():
    config = json.loads(subprocess.check_output(["docker", "inspect", "--format",
        '{"image":{{json .Image}},"nanoCpus":{{.HostConfig.NanoCpus}},"memory":{{.HostConfig.Memory}}}', identifier], text=True))
    # A probe JVM must not inherit the app's file logger and rotate its GC log.
    runtime_command = ["env", "JAVA_TOOL_OPTIONS=", "java", "-version"] if name != "upstream" else ["python", "--version"]
    runtime = subprocess.run(["docker", "exec", identifier, *runtime_command], capture_output=True, text=True, check=True)
    environment[name] = {**config, "runtime": (runtime.stdout + runtime.stderr).strip()}
    probe = request(name, time.perf_counter())
    if not probe["valid"]:
        raise RuntimeError(f"Readiness failed: {name}: {probe}")
summaries = []
metadata = {"protocol": "fixed arrivals; every request retained including failures; scheduled latency includes client delay",
            "environment": environment, "rates": rates, "seconds": args.seconds, "rounds": args.rounds,
            "workers": args.workers, "slo_ms": args.slo_ms, "body_bytes": 64, "extra_headers": 8,
            "warmup_seconds_each": args.warmup_seconds, "warmup_rate": 300,
            "gc": "pause events from JVM GC log; not allocation bytes or a peak heap measure",
            "rss": "process RSS only at run boundaries; not peak memory", "runs": summaries}
with ThreadPoolExecutor(max_workers=args.workers) as executor:
    for name in ("native", "mapped", "upstream"):
        if args.warmup_seconds:
            records, elapsed, _ = run(executor, name, args.warmup_seconds, 300)
            warm = {"variant": name, "requests": len(records), "errors": sum(not record["valid"] for record in records), "elapsed": elapsed}
            (output / f"warmup-{name}.json").write_text(json.dumps(warm, indent=2) + "\n")
            if warm["errors"]:
                raise RuntimeError(f"Warmup failed: {warm}")
            print(f"Warmup {name}: {warm}", flush=True)
    for rate in rates:
        for round_number in range(1, args.rounds + 1):
            order = ("native", "mapped", "upstream") if round_number % 2 else ("mapped", "native", "upstream")
            for name in order:
                before = metrics(name)
                upstream_before = metrics("upstream") if name != "upstream" else before
                records, elapsed, client_cpu = run(executor, name, args.seconds, rate)
                after = metrics(name)
                upstream_after = metrics("upstream") if name != "upstream" else after
                count = len(records)
                valid = [record for record in records if record["valid"]]
                summary = {"rate": rate, "round": round_number, "variant": name, "requests": count,
                           "errors": count - len(valid), "valid_rps": len(valid) / elapsed,
                           "elapsed_seconds": elapsed, "slo_met_percent": sum(record["slo_met"] for record in records) * 100 / count,
                           "cpu_us_per_request": (after["cpu"]["usage_usec"] - before["cpu"]["usage_usec"]) / count,
                           "upstream_cpu_us_per_request": (upstream_after["cpu"]["usage_usec"] - upstream_before["cpu"]["usage_usec"]) / count,
                           "client_cpu_fraction": client_cpu / elapsed,
                           "throttled_periods": after["cpu"].get("nr_throttled", 0) - before["cpu"].get("nr_throttled", 0),
                           "rss_kib_before": before["rss_kib"], "rss_kib_after": after["rss_kib"],
                           "gc_pauses": after["gc_pauses"] - before["gc_pauses"],
                           "gc_pause_ms": after["gc_pause_ms"] - before["gc_pause_ms"],
                           "gc_before": {key: before[key] for key in ("gc_pauses", "gc_pause_ms")},
                           "gc_after": {key: after[key] for key in ("gc_pauses", "gc_pause_ms")}}
                for metric in ("scheduled_ms", "service_ms", "dispatch_ms"):
                    summary[metric] = distribution([record[metric] for record in records])
                    summary["valid_" + metric] = distribution([record[metric] for record in valid])
                for record in records:
                    record.pop("finished")
                ordinal = len(summaries) + 1
                (output / f"run-{ordinal}-{rate}-{name}.json").write_text(json.dumps({"summary": summary, "samples": records}, indent=2) + "\n", encoding="utf-8")
                summaries.append(summary)
                (output / "summary.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
                print(json.dumps(summary), flush=True)
for name in ("native", "mapped"):
    (output / f"gc-{name}.log").write_text(container_text(name, "/tmp/lab-gc.log"), encoding="utf-8")
print("Pressure experiment complete; interpret client and upstream limits before claiming capacity", flush=True)
