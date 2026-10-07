"""Mutations for the two capabilities judged this round: the excluding selector, and the `eidolon` condition.

Engine-side, so the anchors are Java source. Each mutation removes one decision the judge exists for.

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_selector_and_eidolon.py <variant>
    python tools/mut_selector_and_eidolon.py restore
"""
import pathlib
import subprocess
import sys

INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
TABLE = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerTable.java")

VARIANTS = {
    # The selector stops honouring its exclusion list: every Path mate is reached, including the two it names.
    "no_exclusions": (INTERPRETER,
                      "if (ally instanceof Character character && character.getPath() == wanted && !excluded.contains(ally)) {",
                      "if (ally instanceof Character character && character.getPath() == wanted) {"),
    # The selector stops honouring the Path: every ally is reached.
    "no_path": (INTERPRETER,
                "if (ally instanceof Character character && character.getPath() == wanted && !excluded.contains(ally)) {",
                "if (ally instanceof Character character && !excluded.contains(ally)) {"),
    # The condition always answers a rank high enough to unlock anything.
    "eidolon_always_six": (TABLE,
                           "case \"eidolon\" -> ctx.owner() instanceof com.laosun.aluminium.models.Character owner\n"
                           "                        ? owner.getEidolonRank()\n"
                           "                        : 0;",
                           "case \"eidolon\" -> 6;"),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        for path in {INTERPRETER, TABLE}:
            result = subprocess.run(["git", "checkout", "HEAD", "--", str(path)],
                                    capture_output=True, text=True)
            print(f"restored {path.name}" if result.returncode == 0 else f"FAILED {path}: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    path, old, new = VARIANTS[sys.argv[1]]
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times in {path.name}, expected 1")
        return 2
    path.write_text(text.replace(old, new), encoding="utf-8")
    if new not in path.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(path)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]} to {path.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
