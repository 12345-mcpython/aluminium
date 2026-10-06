"""Mutations for the Pearl (1503) advance / extra-turn / technique judges.

TWO LESSONS ARE BAKED INTO THIS SCRIPT, both learned by the first version of it coming back green:

1.  **Anchors must use the bytes the file actually holds.** The first version wrote anchors with `\\uXXXX`
    escapes for the Chinese, which match NOTHING: the content files hold literal characters (the escapes are
    what *I* type, not what Gson wrote). Two mutants silently did not apply, and a green run looked like a
    surviving mutant. The `apply` path below now PROVES the edit landed by re-reading the file and comparing.
2.  **Line-targeted edits for repeated text.** `allies_with_path:欢愉 >= 3` appears three times (the advance
    tier, the Eidolon 1 tier and the Elation-skill tier), so "the last occurrence" is not a safe anchor.
    Each mutation states the line it means; the script asserts that line still holds what it expects.

Usage:
    python tools/mut_pearl_ult.py                 # list
    python tools/mut_pearl_ult.py <name>          # apply
    python tools/mut_pearl_ult.py restore         # revert
"""
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")

# name -> (1-based line number, expected text on that line, replacement text)
EDITS = {
    # The three tier magnitudes: each must be load-bearing for the compounding assertion.
    "tier1_percent": (121, '"percent": 0.1,', '"percent": 0.2,'),
    "tier3_percent": (155, '"percent": 0.3,', '"percent": 0.25,'),
    # The tier THRESHOLDS: the 30% tier at >= 4 means three Elation allies no longer qualify.
    "tier3_threshold": (153, '"allies_with_path:欢愉 >= 3"', '"allies_with_path:欢愉 >= 4"'),
    # The extra turn's own threshold: 3 instead of 4 must hand the extra turn to the three-ally fixture.
    "extra_turn_threshold": (170, '"allies_with_path:欢愉 >= 4"', '"allies_with_path:欢愉 >= 3"'),
    # The technique's gate: dropping it must make the opening counter arrive without the marker.
    "technique_gate": (59, '"self has_state 秘技"', '"self has_state 秘技或不存在"'),
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
        shutil.copyfile(BACKUP, TARGET)
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

    before = lines[line_no - 1]
    lines[line_no - 1] = before.replace(expected, replacement)
    TARGET.write_text("".join(lines), encoding="utf-8")

    # Prove the edit landed: re-read and compare. Without this check a no-op mutation reads as a survivor.
    landed = TARGET.read_text(encoding="utf-8").splitlines()[line_no - 1].strip()
    if landed != replacement:
        print("EDIT DID NOT LAND: line", line_no, "reads", landed, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("applied", name, "at line", line_no, "->", landed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
