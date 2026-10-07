"""Show the first failure message from each failing suite - readable output, no PowerShell quoting."""
import glob
import xml.etree.ElementTree as ET

shown = 0
names = {}
for path in sorted(glob.glob("build/test-results/test/*.xml")):
    tree = ET.parse(path)
    suite = tree.getroot()
    failures = suite.findall(".//failure")
    if not failures:
        continue
    names[suite.get("name")] = len(failures)
    if shown < 6:
        message = (failures[0].get("message") or "").strip().replace("\n", " ")
        print(f"{suite.get('name')}  ({len(failures)} failure(s))")
        print(f"    {message[:220]}")
        shown += 1

print()
print("failing suites:", len(names), "; failing cases:", sum(names.values()))
