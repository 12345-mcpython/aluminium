"""Mutations for the extra-turn boundary events (engine side) and their judge.

Engine file, so the anchors are Java identifiers - but the same two rules apply: the anchor must be the bytes the
file holds, and every apply proves it landed. Restore goes through `git checkout` (a .bak goes stale, which cost
three confusions in one round).

Usage:
    python tools/mut_extra_turn.py                # list
    python tools/mut_extra_turn.py <name>
    python tools/mut_extra_turn.py restore
"""
import pathlib
import subprocess
import sys

BATTLE = pathlib.Path("src/main/java/com/laosun/aluminium/Battle.java")
QUEUE = pathlib.Path("src/main/java/com/laosun/aluminium/Queue.java")

# name -> (file, line, expected, replacement)
EDITS = {
    # The START never fires: the whole pair goes dark.
    "no_start": (BATTLE, 942, "        if (startingExtraTurn && currentMove != null) {",
                 "        if (false && startingExtraTurn && currentMove != null) {"),
    # The END never fires: the pair is half-wired.
    "no_end": (BATTLE, 1394, "        if (extraTurnBeingTaken != null && extraTurnBeingTaken == actor) {",
               "        if (false && extraTurnBeingTaken != null && extraTurnBeingTaken == actor) {"),
    # The queue reports "pending" always: the start would fire on ordinary turns too.
    "pending_always": (QUEUE, 464, "        return extraTurnActor != null;", "        return true;"),
}


def git_restore():
    result = subprocess.run(["git", "checkout", "--", str(BATTLE), str(QUEUE)],
                            capture_output=True, text=True)
    if result.returncode == 0:
        print("restored the engine files (git)")
        return 0
    print("git checkout failed:", result.stderr.strip())
    return 1


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(EDITS):
        print(__doc__)
        print("mutations:", ", ".join(sorted(EDITS)))
        return 1
    if sys.argv[1] == "restore":
        return git_restore()

    name = sys.argv[1]
    target, line_no, expected, replacement = EDITS[name]
    lines = target.read_text(encoding="utf-8").splitlines(keepends=True)
    if line_no > len(lines) or lines[line_no - 1].rstrip("\n") != expected:
        print(f"NOT APPLIED: {target}:{line_no} does not hold the anchor")
        if line_no <= len(lines):
            print("  expected:", repr(expected))
            print("  actual  :", repr(lines[line_no - 1].rstrip("\n")))
        return 2

    lines[line_no - 1] = lines[line_no - 1].replace(expected, replacement)
    target.write_text("".join(lines), encoding="utf-8")

    landed = target.read_text(encoding="utf-8").splitlines()[line_no - 1].rstrip("\n")
    if landed != replacement:
        print("EDIT DID NOT LAND:", landed, "- restoring")
        git_restore()
        return 3
    print("applied", name, "at", target.name, "line", line_no)
    return 0


if __name__ == "__main__":
    sys.exit(main())
