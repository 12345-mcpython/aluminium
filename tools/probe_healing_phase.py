"""Decide whether the healing link's read is a TIMING question, by moving the Elation grant to a later phase.

Established so far:
  * a derived read (`scale: self_attr:<X>`) DOES see a value granted earlier in the same event - proven by
    tools/../DerivedReadTimingTest (measured: grant +0.5 then derive -> 20% of the NEW value);
  * Pearl's healing clause is LAST among her BATTLE_START rules (index 36), the stat-trace grant is index 35, and yet
    the bonus is 0.064 = 20% of 0.32 (the gated tier alone);
  * raising the stat-trace row from 0.10 to 0.50 moves her panel from 0.42 to 0.82 and leaves the bonus at 0.064
    exactly.

So either the trace grant is not applied yet when the healing rule runs (a phase/order question), or the read
resolves something narrower than the panel. `Battle.startBattle` fires BATTLE_START first and BATTLE_READY second, so
moving the Elation grant to BATTLE_READY puts it unambiguously BEFORE the healing read - if the bonus then becomes
20% of the new panel, it is timing; if it stays 0.064, the reader is looking somewhere else.

Usage:
    python tools/probe_healing_phase.py
"""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RESULT = pathlib.Path("build/test-results/test/"
                      "TEST-com.laosun.aluminium.test.content.characters.PearlInsightTraceTest.xml")
TRACE_EVENT = '      "on": "BATTLE_START",\n      "id": "stat_traces_the_ten_panel_rows"'
LATE_EVENT = '      "on": "BATTLE_READY",\n      "id": "stat_traces_the_ten_panel_rows"'


def readings():
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
    if text.count(TRACE_EVENT) != 1:
        print("anchor occurs", text.count(TRACE_EVENT), "times, expected 1")
        return 1

    before = readings()
    print(f"traces on BATTLE_START: final Elation = {before[0]} ; healing bonus = {before[1]}")

    TARGET.write_text(text.replace(TRACE_EVENT, LATE_EVENT), encoding="utf-8")
    try:
        after = readings()
        print(f"traces on BATTLE_READY: final Elation = {after[0]} ; healing bonus = {after[1]}")
    finally:
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        print("content file restored from HEAD")

    if before[1] is None or after[1] is None:
        return 1
    if abs(after[1] - before[1]) < 1e-9:
        print("=> TIMING IS NOT THE CAUSE: the bonus did not move even with the grant in a later phase")
    else:
        print(f"=> TIMING: the bonus moved by {after[1] - before[1]} when the grant moved later "
              f"(0.2 x {after[0]} = {0.2 * after[0]} would be the full read)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
