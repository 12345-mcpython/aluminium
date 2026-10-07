"""Progress and interim findings of the parallel coverage sweep, readable while it runs.

Reads the workers' logs and any part files written so far, so a run in progress can be reported on without disturbing it.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(".")
RESULTS = ROOT / "tools/_rule_coverage.json"
REPORT = ROOT / "tools/_rule_coverage_report.txt"


def main():
    total = 0
    guarded = 0
    unguarded = []
    per_worker = []
    for log in sorted(ROOT.glob("tools/_sweep_worker_*.log")):
        text = log.read_text(encoding="utf-8", errors="replace")
        done = re.findall(r"\[\d+/\d+\] (GUARDED|UNGUARDED|SKIP)\s+(\S+)", text)
        last = text.strip().splitlines()[-1] if text.strip() else "(no output yet)"
        per_worker.append((log.name, len(done), last))
        for verdict, key in done:
            total += 1
            if verdict == "GUARDED":
                guarded += 1
            elif verdict == "UNGUARDED":
                unguarded.append(key)

    print("== workers ==")
    for name, count, last in per_worker:
        print(f"  {name}: {count} done")
        print(f"      {last}")
    print()
    print(f"verdicts so far: {total}  guarded {guarded}  UNGUARDED {len(unguarded)}")
    if unguarded:
        print("== unguarded so far ==")
        for key in unguarded:
            print("   " + key)

    if REPORT.exists():
        print()
        print("== last merged report ==")
        print(REPORT.read_text(encoding="utf-8")[-1500:])
    return 0


if __name__ == "__main__":
    sys.exit(main())
