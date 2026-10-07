"""Mutation for the talent's damage-reduction magnitude, now that the zone's relationship is known.

The judge asserts `withTalent / without == 1 - 0.30`. Two mutations worth running:

  * change the content to -0.50: the ratio must leave 0.70 and turn the case red;
  * change it back to -0.15: this is the interesting one - the ORIGINAL judge asserted 1/1.15 = 0.8696 with a
    tolerance of 0.02, and the engine's own reading for -0.15 is 0.85. That is a 1.9% error, INSIDE the tolerance,
    which is how a wrong relationship (division instead of multiplication) survived a passing judge.

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_talent_percent.py              # apply -0.50
    python tools/mut_pearl_talent_percent.py fifteen      # apply -0.15 (the old value)
    python tools/mut_pearl_talent_percent.py restore
"""
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
ANCHOR = '"percent": -0.3,'
VARIANTS = {"fifty": '"percent": -0.5,', "fifteen": '"percent": -0.15,'}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"failed: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    replacement = VARIANTS[sys.argv[1]]
    text = TARGET.read_text(encoding="utf-8")
    if text.count(ANCHOR) != 1:
        print(f"NOT APPLIED: the anchor occurs {text.count(ANCHOR)} times, expected 1")
        return 2
    TARGET.write_text(text.replace(ANCHOR, replacement), encoding="utf-8")
    if replacement not in TARGET.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]}: {ANCHOR} -> {replacement}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
