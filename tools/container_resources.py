"""Point-in-time container memory and lifetime peak, collected after pressure.

The peak includes startup, warmup and diagnostic commands, not one workload tier.
Load CPU is measured separately by http_pressure.py, not by an idle stats sample.
"""
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
compose = ["docker", "compose", "-f", "compose.yaml", "-f", "compose.perf.yaml"]
rows = {}
for name in ("native", "mapped", "upstream"):
    identifier = subprocess.check_output(compose + ["ps", "--quiet", name], cwd=root, text=True).strip()
    if not identifier:
        raise RuntimeError(f"Missing {name} container")
    def read(path):
        return subprocess.check_output(["docker", "exec", identifier, "cat", path], text=True)
    current = int(read("/sys/fs/cgroup/memory.current"))
    peak = int(read("/sys/fs/cgroup/memory.peak"))
    memory = {key: int(value) for key, value in (line.split() for line in read("/sys/fs/cgroup/memory.stat").splitlines())}
    rss = int(re.search(r"VmRSS:\s+(\d+)", read("/proc/1/status")).group(1)) * 1024
    rows[name] = {"container_current_bytes": current, "container_lifetime_peak_bytes": peak,
                  "process_rss_bytes": rss, "memory_stat": memory}
summary = {"collected_at": datetime.now(timezone.utc).isoformat(),
           "scope": "Current memory after all measured load; lifetime peak includes startup, warmup and commands, not a per-tier peak",
           "cpu": "Use cgroup usage deltas per request in the pressure results; an idle CPU snapshot is not load CPU",
           "values": rows}
output = root / "results/generated/container-resources.json"
output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
for name, row in rows.items():
    print(name, "current MiB", round(row["container_current_bytes"] / 1048576, 1),
          "lifetime peak MiB", round(row["container_lifetime_peak_bytes"] / 1048576, 1))
