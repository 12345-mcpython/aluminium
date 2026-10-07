"""Decide which Elation figure the healing link reads, by changing the STAT TRACE's Elation row and watching the
healing bonus.

Two candidate answers, and the experiment separates them without needing to see inside the reader:

  * if the healing rule reads the panel INCLUDING the stat traces, then raising the trace row from 0.10 to 0.50
    raises the bonus from 0.064 to 0.2 x (0.32 + 0.50) = 0.164 (a change of +0.10 x 0.2 = +0.02 per 0.10 of trace);
  * if it reads only the gated tier's own 0.32, the bonus does not move at all.

The probe itself is the existing judge `PearlInsightTraceTest`, whose output line prints both the final Elation and the
bonus the clause added.

Usage:
    python tools/probe_healing_read.py
"""
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RESULT = pathlib.Path("build/test-results/test/"
                      "TEST-com.laosun.aluminium.test.content.characters.PearlInsightTraceTest.xml")
ANCHOR = ('          "attribute": "ELATION_DAMAGE_BOOST",\n          "percent": 0.1,\n'
          '          "permanent": true,\n          "target": "self",\n          "coexist": true')
RAISED = ('          "attribute": "ELATION_DAMAGE_BOOST",\n          "percent": 0.5,\n'
          '          "permanent": true,\n          "target": "self",\n          "coexist": true')


def bonus_now():
    result = subprocess.run([".\\gradlew.bat", "test", "--tests", "*PearlInsightTraceTest*",
                             "--console=plain", "-q"], capture_output=True, text=True)
    if not RESULT.exists():
        print("no result file:", result.stdout[-400:], result.stderr[-400:])
        return None, None
    root = ET.parse(RESULT).getroot()
    system_out = root.find("system-out")
    final = bonus = None
    for line in (system_out.text or "").splitlines():
        match = re.search(r"final Elation = ([\d.]+) ; the healing bonus this clause added = ([\d.]+)", line)
        if match:
            final, bonus = float(match.group(1)), float(match.group(2))
    return final, bonus


def main():
    text = TARGET.read_text(encoding="utf-8")
    if text.count(ANCHOR) != 1:
        print("anchor occurs", text.count(ANCHOR), "times, expected 1")
        return 1

    final_before, bonus_before = bonus_now()
    print(f"with the trace row at 0.10: final Elation = {final_before} ; bonus = {bonus_before}")

    TARGET.write_text(text.replace(ANCHOR, RAISED), encoding="utf-8")
    try:
        final_after, bonus_after = bonus_now()
        print(f"with the trace row at 0.50: final Elation = {final_after} ; bonus = {bonus_after}")
    finally:
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        print("content file restored from HEAD")

    if bonus_before is None or bonus_after is None:
        return 1
    if abs(bonus_after - bonus_before) < 1e-9:
        print("=> the healing link does NOT see the stat traces (it reads the gated tier alone)")
    else:
        print(f"=> the healing link DOES see the stat traces (the bonus moved by "
              f"{bonus_after - bonus_before}; 0.2 x 0.40 = 0.08 would be the full effect)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
