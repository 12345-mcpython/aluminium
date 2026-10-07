"""Mutations for the Pearl (1503) talent's two halves.

Motivated by a measurement, not a hunch: the shipped judge asserts
`hasState("吞纳沙砾")`, i.e. that the STATE is there - not that the state's
`-0.15` does anything. A buff that carries the right name and the wrong number
passes that assertion, so this script exists to find out whether the judge would
notice the number moving.

Line-targeted for the reason tools/mut_pearl_ult.py records: repeated text.

Usage:
    python tools/mut_pearl_talent.py                 # list
    python tools/mut_pearl_talent.py <name>
    python tools/mut_pearl_talent.py restore
"""
import pathlib
import shutil
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

EDITS = {
    # The magnitude of the reduction: does anything notice -0.15 -> -0.50?
    "talent_percent": (286, '"percent": -0.15,', '"percent": -0.5,'),
    # The threshold: flipping it back must break BOTH sides of the judge.
    "talent_threshold": (281, '"target_hp_percent <= 0.5"', '"target_hp_percent >= 0.5"'),
    # The removal half: dropping the HEALED cleanup must make the state stick above half HP.
    "talent_removal": (307, '"op": "REMOVE_STATE",', '"op": "REMOVE_STATE_DISABLED",'),
}


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(EDITS):
        print(__doc__)
        print("mutations:", ", ".join(sorted(EDITS)))
        return 1

    if sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        print("restored", TARGET)
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    name = sys.argv[1]
    line_no, expected, replacement = EDITS[name]
    lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)
    actual = lines[line_no - 1].strip()
    if actual != expected:
        print("LINE", line_no, "does not hold the expected anchor; nothing written")
        print("  expected:", expected)
        print("  actual  :", actual)
        return 2

    lines[line_no - 1] = lines[line_no - 1].replace(expected, replacement)
    TARGET.write_text("".join(lines), encoding="utf-8")

    landed = TARGET.read_text(encoding="utf-8").splitlines()[line_no - 1].strip()
    if landed != replacement:
        print("EDIT DID NOT LAND: line", line_no, "reads", landed, "- restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        return 3
    print("applied", name, "at line", line_no, "->", landed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
