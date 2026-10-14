"""Summarize every factorial combination and pressure run without selecting wins."""
import argparse
from datetime import date
import json
import math
from pathlib import Path
import statistics

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--jmh-date", default=date.today().isoformat())
parser.add_argument("--http-date", default=date.today().isoformat())
parser.add_argument("--implementation", default="yaml-factories")
args = parser.parse_args()
generated = root / "results/generated"
jmh = json.loads((generated / "jmh-factorial/jmh.json").read_text(encoding="utf-8"))
http = json.loads((generated / "http-pressure/summary.json").read_text(encoding="utf-8"))
probes = json.loads((generated / "boundary-probes/summary.json").read_text(encoding="utf-8"))
container_memory = json.loads((generated / "container-resources.json").read_text(encoding="utf-8"))
if len(jmh) != 12 or len(http["runs"]) != len(http["rates"]) * http["rounds"] * 3:
    raise RuntimeError("Incomplete experiments; do not publish partial summaries")
if any(item["elapsed_seconds"] > http["seconds"] * 10 for item in http["runs"]):
    raise RuntimeError("Host-interrupted or severely backlogged run; preserve it outside the final series")
micro = []
for item in jmh:
    primary = item["primaryMetric"]
    allocation = item["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
    if not all(math.isfinite(value) for value in (primary["score"], primary["scoreError"], allocation)):
        raise RuntimeError("Non-finite JMH measurement")
    data = primary["rawData"]
    early = statistics.mean(value for fork in data for value in fork[:2])
    late = statistics.mean(value for fork in data for value in fork[-2:])
    micro.append({"variant": "mapped" if item["benchmark"].endswith("mappedChain") else "native",
                  "body_bytes": int(item["params"]["bodyBytes"]), "extra_headers": int(item["params"]["extraHeaders"]),
                  "mean_us": primary["score"] / 1000, "error_99_9_us": primary["scoreError"] / 1000,
                  "allocation_kib_per_op": allocation / 1024, "early_to_late_measured_ratio": early / late,
                  "java": item["jdkVersion"], "forks": item["forks"],
                  "warmup_iterations": item["warmupIterations"], "measurement_iterations": item["measurementIterations"]})


def median_range(values):
    return {"median": statistics.median(values), "min": min(values), "max": max(values)}


pressure = []
for rate in http["rates"]:
    for variant in ("native", "mapped", "upstream"):
        runs = [item for item in http["runs"] if item["rate"] == rate and item["variant"] == variant]
        row = {"variant": variant, "offered_rps": rate, "requests": sum(item["requests"] for item in runs),
               "errors": sum(item["errors"] for item in runs), "runs": len(runs)}
        for metric in ("valid_rps", "slo_met_percent", "cpu_us_per_request", "upstream_cpu_us_per_request",
                       "client_cpu_fraction", "throttled_periods", "rss_kib_before", "rss_kib_after", "gc_pauses", "gc_pause_ms"):
            row[metric] = median_range([item[metric] for item in runs])
        for metric in ("scheduled_ms", "service_ms", "dispatch_ms"):
            row[metric] = {percent: median_range([item[metric][percent] for item in runs]) for percent in ("p50", "p95", "p99", "mean", "max")}
        row["all_runs_meet_illustrative_slo"] = all(item["slo_met_percent"] >= 99 for item in runs)
        pressure.append(row)
summary = {"date": max(args.jmh_date, args.http_date), "jmh_date": args.jmh_date, "http_date": args.http_date,
           "implementation": args.implementation, "purpose": "Assess a concrete abstraction's reuse and runtime costs; no universal architecture winner",
           "jmh": sorted(micro, key=lambda item: (item["body_bytes"], item["extra_headers"], item["variant"])),
           "http_protocol": {key: value for key, value in http.items() if key != "runs"}, "pressure": pressure,
           "boundary_probes": probes, "container_memory": container_memory,
           "limits": ["Factorial JMH measures entire simulated chain including request construction and body consumption",
                      "HTTP levels are ascending; three repeats per level and variant are local observations, not a capacity ceiling",
                      "SLO includes failures and scheduled-arrival client delay; valid-only latency never replaces it",
                      "Direct echo control diagnoses limits and cannot be subtracted from gateway latency",
                      "GC and CPU belong to complete processes; RSS endpoints are not peak memory",
                      "Boundary probes cover metadata policies, not an entire reactive framework migration",
                      "No maintenance hours, defect rate or economic return measured"]}
(generated / "performance-deep.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print("Validated complete summary: 12 JMH combinations, all pressure runs, 24 boundary executions")
for row in summary["jmh"]:
    print("JMH", row["variant"], row["body_bytes"], row["extra_headers"], round(row["mean_us"], 2), round(row["allocation_kib_per_op"], 2), "trend", round(row["early_to_late_measured_ratio"], 3))
for row in pressure:
    print("HTTP", row["offered_rps"], row["variant"], "p95", round(row["scheduled_ms"]["p95"]["median"], 2),
          "SLO", round(row["slo_met_percent"]["median"], 2), "errors", row["errors"],
          "dispatch p95", round(row["dispatch_ms"]["p95"]["median"], 2), "CPU", round(row["cpu_us_per_request"]["median"], 1))
