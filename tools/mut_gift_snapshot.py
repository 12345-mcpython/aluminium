"""Mutations for the 【好活当赏】 snapshot model (the structural step).

  no_snapshot   the gain records no count, so every snapshot reading is 0
  live_pool     the count is read from the pool at READ time instead of frozen at grant time
  no_merge      the merged figure reports one holder instead of their sum
  one_tick      the duration is one turn-END, so it does not survive the document's two rounds

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_gift_snapshot.py <variant>
    python tools/mut_gift_snapshot.py restore
"""
import pathlib
import subprocess
import sys

BATTLE = pathlib.Path("src/main/java/com/laosun/aluminium/Battle.java")
INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")

VARIANTS = {
    "no_snapshot": (INTERPRETER,
                    "        ctx.battle().noteElationGift(holder, ELATION_GIFT_TURNS);",
                    "        // MUTANT: no count taken"),
    "live_pool": (BATTLE,
                  "        laughSnapshots.put(holder, new Snapshot(partyResourceValue(\"\\u7b11\\u70b9\"), Math.max(1, turns)));",
                  "        laughSnapshots.put(holder, new Snapshot(0, Math.max(1, turns)));  // MUTANT: frozen at zero"),
    "no_merge": (BATTLE,
                 "            if (!snapshot.hasExpired()) {\n                total += snapshot.points();",
                 "            if (!snapshot.hasExpired()) {\n                total = snapshot.points();  // MUTANT: last one wins"),
    "one_tick": (INTERPRETER,
                 '    private static final int ELATION_GIFT_TURNS = 3;',
                 '    private static final int ELATION_GIFT_TURNS = 2;  // MUTANT'),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        for path in {BATTLE, INTERPRETER}:
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
    print(f"applied {sys.argv[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
