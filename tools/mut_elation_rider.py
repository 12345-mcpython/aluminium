"""Mutations for the enhanced basic's own Elation rider.

  percent   the shipped 0.15 -> 0.01 (the magnitude)
  gate      the resource condition dropped (`self_resource:好活当赏 >= 1`), so the rider pays unconditionally
  slot      the slot gate moved from 8 to 9, i.e. the rider lands on the other form

`gate` is the one the case exists for: the sentence is 「持有【好活当赏】时」, and a rule that paid without holding would
look right in every reading that only compares two runs with the counter present.

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_elation_rider.py <variant>
    python tools/mut_elation_rider.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")

VARIANTS = {
    "percent": ('"op": "ADD_DAMAGE",\n          "scale": "elation_base",\n          "percent": 0.15',
                '"op": "ADD_DAMAGE",\n          "scale": "elation_base",\n          "percent": 0.01'),
    "gate": ('        "self_resource:好活当赏 >= 1",\n', ''),
    "slot": ('"actor == self",\n        "self_resource:好活当赏 >= 1",\n        "from_skill_id == 8"',
             '"actor == self",\n        "self_resource:好活当赏 >= 1",\n        "from_skill_id == 9"'),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")
        print("NOTE: this restores the COMMITTED file - re-run the ship script if the rule was not committed yet.")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    old, new = VARIANTS[sys.argv[1]]
    text = CONTENT.read_text(encoding="utf-8")
    if text.count(old) != 1:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times, expected 1")
        return 2
    CONTENT.write_text(text.replace(old, new), encoding="utf-8")
    if new not in CONTENT.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
