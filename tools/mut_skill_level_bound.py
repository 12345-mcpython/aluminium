"""Mutation for the skill-level table bound: remove the clamp and watch the silent zero come back.

The clamp is one line in `CanHit.skillLevel`. Removing it is exactly the state the tree was in until this round, and
the judge must notice: the level goes past the table, the damage path finds no row and returns silently, and the basic
deals 0.

Restore is `git checkout HEAD -- <path>` (a `.bak` goes stale, and `checkout --` restores the INDEX rather than HEAD -
both mistakes have cost this project rounds).

Usage:
    python tools/mut_skill_level_bound.py
"""
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/java/com/laosun/aluminium/models/CanHit.java")
CLAMP = "        return Math.min(level, tableRows(skill));"
UNCLAMPED = "        return level;"


def main():
    text = TARGET.read_text(encoding="utf-8")
    if text.count(CLAMP) != 1:
        print(f"NOT APPLIED: the clamp line occurs {text.count(CLAMP)} times, expected 1")
        return 1

    TARGET.write_text(text.replace(CLAMP, UNCLAMPED), encoding="utf-8")
    landed = TARGET.read_text(encoding="utf-8")
    if UNCLAMPED not in landed or CLAMP in landed:
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
        return 2
    print("applied: the clamp is removed")
    print("now run:  .\\gradlew.bat test --tests '*SkillLevelTableBoundTest*'")
    print("then:     python tools\\mut_skill_level_bound.py restore")
    return 0


def restore():
    result = subprocess.run(["git", "checkout", "HEAD", "--", str(TARGET)], capture_output=True, text=True)
    print("restored from HEAD" if result.returncode == 0 else f"failed: {result.stderr.strip()}")
    return 0 if result.returncode == 0 else 1


if __name__ == "__main__":
    sys.exit(restore() if len(sys.argv) == 2 and sys.argv[1] == "restore" else main())
