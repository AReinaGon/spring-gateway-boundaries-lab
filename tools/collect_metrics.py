"""Collect verification evidence; counts are descriptive, not design scores."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--output", default="results/generated/metrics.json")
args = parser.parse_args()
report = {"modules": {}}
for module in ("without-mappers", "with-mappers"):
    result = {}
    for kind, folder in (("in_process", "surefire-reports"), ("real_http_docker", "failsafe-reports")):
        counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        for path in (root / module / "target" / folder).glob("TEST-*.xml"):
            suite = ET.parse(path).getroot()
            for key in counts:
                counts[key] += int(suite.get(key, 0))
        result[kind] = counts
    files = list((root / module / "src/main/java").rglob("*.java"))
    result["project_source"] = {"files": len(files), "nonblank_lines": sum(sum(bool(line.strip()) for line in p.read_text(encoding="utf-8").splitlines()) for p in files)}
    report["modules"][module] = result
infrastructure = list((root / "without-mappers/src/main/java/com/areina/gatewaylab/nativeimpl/infrastructure").glob("*.java"))
report["duplicated_infrastructure_per_project"] = {"files": len(infrastructure), "nonblank_lines": sum(sum(bool(line.strip()) for line in p.read_text(encoding="utf-8").splitlines()) for p in (root / "without-mappers/src/main/java/com/areina/gatewaylab/nativeimpl/infrastructure").glob("*.java"))}
report["scope"] = "Each project count includes all its production Java source, including duplicated infrastructure and rules. Tests are excluded. Counts do not measure maintenance effort or runtime performance."
destination = root / args.output
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
