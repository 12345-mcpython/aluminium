"""Probe a test class from the LAST full-suite run: its result, its stdout, and whether it even ran.

Reading the XML by hand is how the last several confusing readings happened; this prints the three things that
answer "did the test run, what did it see, and did it pass" in one go.

Usage:
    python tools/where_test.py ExtraTurnEventTest
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

RESULTS = pathlib.Path("build/test-results/test")


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    needle = sys.argv[1]
    hits = [p for p in RESULTS.glob("*.xml") if needle.lower() in p.name.lower()]
    if not hits:
        print("no result file matches", needle, "- the class did not run")
        return 1
    for path in hits:
        root = ET.parse(path).getroot()
        print("=" * 70)
        print(path.name)
        print(f"tests={root.get('tests')} failures={root.get('failures')} errors={root.get('errors')} "
              f"skipped={root.get('skipped')} time={root.get('time')}")
        for case in root.iter("testcase"):
            bad = case.find("failure") is not None or case.find("error") is not None
            print(f"  {'FAIL' if bad else 'ok  '} {case.get('name')}")
        out = root.find("system-out")
        if out is not None and out.text:
            for line in out.text.splitlines():
                if line.strip():
                    print("  |", line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
