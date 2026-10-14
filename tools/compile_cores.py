"""Verify type independence separately for the two self-contained projects."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
empty = root / "results/generated/empty-classpath"
empty.mkdir(parents=True, exist_ok=True)
for project, package in (("without-mappers", "nativeimpl"), ("with-mappers", "mapped")):
    source = root / project / "src/main/java/com/areina/gatewaylab" / package
    rules = source / "infrastructure/Rules.java"
    cases = [("rules", [rules])]
    if project == "with-mappers":
        cases.append(("processors", [rules, source / "RequestSnapshot.java", source / "HeaderUpdate.java", source / "MappedRules.java"]))
    for name, sources in cases:
        output = root / "results/generated" / project / name
        output.mkdir(parents=True, exist_ok=True)
        subprocess.run(["javac", "--release", "21", "-classpath", str(empty), "-d", str(output), *map(str, sources)], check=True)
        print(f"PASS {project}/{name}: explicit empty external classpath")
