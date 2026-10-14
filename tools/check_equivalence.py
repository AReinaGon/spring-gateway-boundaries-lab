"""Audit copied infrastructure and acceptance contracts without linking the projects."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
projects = (("without-mappers", "nativeimpl"), ("with-mappers", "mapped"))

def normalized(text):
    return re.sub(r"com\.areina\.gatewaylab\.(nativeimpl|mapped)\.(infrastructure|acceptance)", r"com.areina.gatewaylab.\2", text).replace("\r\n", "\n")

for kind, tail in (("main", "infrastructure"), ("test", "acceptance")):
    directories = [root / name / f"src/{kind}/java/com/areina/gatewaylab" / package / tail for name, package in projects]
    names = [set(p.name for p in directory.glob("*.java")) for directory in directories]
    if names[0] != names[1] or not names[0]:
        raise RuntimeError(f"Different {tail} file sets")
    for name in sorted(names[0]):
        if normalized((directories[0] / name).read_text(encoding="utf-8")) != normalized((directories[1] / name).read_text(encoding="utf-8")):
            raise RuntimeError(f"Different {tail}/{name} contracts")
    print(f"PASS duplicated {tail}: {len(names[0])} equivalent files")
fixture = [root / name / "src/test/resources/echo.py" for name, _ in projects]
if fixture[0].read_bytes() != fixture[1].read_bytes():
    raise RuntimeError("Different echo fixtures")
print("PASS duplicated echo fixture")
configs = [(root / name / "src/main/resources/application.yml").read_text(encoding="utf-8") for name, _ in projects]
if configs[0].replace("gateway-without-mappers", "gateway") != configs[1].replace("gateway-with-mappers", "gateway"):
    raise RuntimeError("Different YAML route/filter contracts")
print("PASS equivalent YAML route/filter configuration")
