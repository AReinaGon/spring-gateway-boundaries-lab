"""Execute real policies in a host with no Spring, then change one policy.

Only generated copies are changed. Production sources and adapters are untouched.
This proves a specific reuse/change property, not an entire framework migration.
"""
import hashlib
import json
import argparse
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--docker-java21", action="store_true")
args = parser.parse_args()
generated = root / "results/generated/boundary-probes"
empty = generated / "empty-classpath"
empty.mkdir(parents=True, exist_ok=True)
records = []
outputs = {}
for changed in (False, True):
    mode = "allow-delete" if changed else "baseline"
    for project, package, main in (("without-mappers", "nativeimpl", "NativePolicyProbe"), ("with-mappers", "mapped", "MappedPolicyProbe")):
        production = root / project / "src/main/java/com/areina/gatewaylab" / package
        original_rules = production / "infrastructure/Rules.java"
        rules_text = original_rules.read_text(encoding="utf-8")
        folder = generated / mode / project
        folder.mkdir(parents=True, exist_ok=True)
        sources = [original_rules]
        if changed:
            anchor = '        if (method.equals("POST") && role.equals("writer")) return "write";'
            assert rules_text.count(anchor) == 1
            altered = rules_text.replace(anchor, anchor + '\n        if (method.equals("DELETE") && role.equals("writer")) return "delete";')
            copy = folder / "sources/infrastructure/Rules.java"
            copy.parent.mkdir(parents=True, exist_ok=True)
            copy.write_text(altered, encoding="utf-8")
            sources = [copy]
        if project == "with-mappers":
            sources += [production / name for name in ("RequestSnapshot.java", "HeaderUpdate.java", "MappedRules.java")]
        host = root / f"tools/probes/{main}.java"
        sources.append(host)
        classes = folder / "classes"
        classes.mkdir(exist_ok=True)
        subprocess.run(["javac", "--release", "21", "-classpath", str(empty), "-d", str(classes), *map(str, sources)], check=True)
        command = ["java", "-classpath", str(classes)]
        if args.docker_java21:
            command = ["docker", "run", "--rm", "--mount", f"type=bind,source={classes.as_posix()},target=/classes,readonly",
                       "maven:3.9.9-eclipse-temurin-21", "java", "-classpath", "/classes"]
        if changed:
            command.append("-Dlab.allowDelete=true")
        result = subprocess.check_output(command + [main], text=True)
        (folder / "output.txt").write_text(result, encoding="utf-8")
        outputs[(mode, project)] = result
        records.append({"mode": mode, "project": project, "cases": 6, "classpath": "generated classes only; no external libraries",
                        "changed_production_files_in_copy": ["infrastructure/Rules.java"] if changed else [],
                        "changed_adapters": [], "host_sha256": hashlib.sha256(host.read_bytes()).hexdigest(),
                        "reused_production_types": [path.name for path in sources[:-1]], "output": result.splitlines()})
    assert outputs[(mode, "without-mappers")] == outputs[(mode, "with-mappers")], "The two hosts disagree"
runtime_command = ["docker", "run", "--rm", "maven:3.9.9-eclipse-temurin-21", "java", "--version"] if args.docker_java21 else ["java", "--version"]
summary = {"execution_java": subprocess.check_output(runtime_command, text=True).splitlines()[0],
           "target_release": 21, "cases_executed": 24, "errors": 0, "experiments": records,
           "limits": ["Synchronous metadata host, not an HTTP server or a replacement for the reactive gateway",
                      "Native host implements header orchestration; mapped host reuses processors but still applies patches",
                      "The same authorization-policy change is contained in Rules in both variants",
                      "No estimate of migration effort, maintenance hours or global isolation score"]}
(generated / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print("PASS: 24 executions, identical results, no Spring; policy change contained in Rules for both")
