"""Run the standalone JMH jar on Java 21 in a bounded Docker container."""
import argparse
import json
import math
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--smoke", action="store_true")
parser.add_argument("--warmup-iterations", type=int, default=10)
parser.add_argument("--output", default="results/generated/jmh-factorial")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
target = root / "benchmark/target"
output = root / args.output
output.mkdir(parents=True, exist_ok=True)
if not (target / "gateway-chain-benchmark.jar").exists():
    raise RuntimeError("Build the optional benchmark module first")
name = "jmh-smoke" if args.smoke else "jmh"
command = ["docker", "run", "--rm", "--cpus=1", "--memory=768m",
           "--mount", f"type=bind,source={target.as_posix()},target=/bench,readonly",
           "--mount", f"type=bind,source={output.as_posix()},target=/results",
           "maven:3.9.9-eclipse-temurin-21", "java", "-Xms128m", "-Xmx128m",
           "-jar", "/bench/gateway-chain-benchmark.jar",
           "-jvmArgs", "-Xms256m -Xmx256m -XX:ActiveProcessorCount=1 -Dlab.instrumentation=false",
           "-f", "1" if args.smoke else "3", "-wi", "0" if args.smoke else str(args.warmup_iterations),
           "-i", "1" if args.smoke else "5", "-w", "2s", "-r", "100ms" if args.smoke else "2s",
           "-prof", "gc", "-rf", "json", "-rff", f"/results/{name}.json"]
if args.smoke:
    command += ["-p", "bodyBytes=64", "-p", "extraHeaders=8"]
(output / f"{name}-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
with (output / f"{name}.log").open("w", encoding="utf-8") as log:
    subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
results = json.loads((output / f"{name}.json").read_text(encoding="utf-8"))
expected = 2 if args.smoke else 12
if len(results) != expected or any(not math.isfinite(item["primaryMetric"]["score"]) for item in results):
    raise RuntimeError("JMH failed to measure every expected variant/workload")
print(f"PASS {name}: {len(results)} measured variant/workload combinations")
