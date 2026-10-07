"""Mutations for the widened selector and the Eidolon ceiling.

  ceiling   the shipped tiers' `max_eidolon` 1 -> 2, so at E2 both sets fire and the archetype is advanced twice
            (ADVANCE is multiplicative: twice 10% is 19%, not 20%)
  noexcept  the new selector drops its exclusion list, so the archetype is advanced again - i.e. it behaves like
            `all_allies` rather than "the other Elation allies"
  paths     the new selector no longer restricts to the Path, so every ally is advanced

All three bear on the same thing: which units the Ultimate reaches. Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_e2_advance.py <variant>
    python tools/mut_pearl_e2_advance.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
WIDE = "allies_of_path_except:欢愉:self,target"

VARIANTS = {
    "ceiling": ('"id": "ult_advances_the_target_at_1_elation_allies",\n      "max_eidolon": 1',
                '"id": "ult_advances_the_target_at_1_elation_allies",\n      "max_eidolon": 2'),
    "noexcept": (f'"target": "{WIDE}"', '"target": "all_allies"'),
    "paths": (f'"target": "{WIDE}"', '"target": "other_allies"'),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    old, new = VARIANTS[sys.argv[1]]
    text = CONTENT.read_text(encoding="utf-8")
    expected = 3 if sys.argv[1] in ("noexcept", "paths") else 1
    if text.count(old) != expected:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times, expected {expected}")
        return 2
    CONTENT.write_text(text.replace(old, new), encoding="utf-8")
    if new not in CONTENT.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]} ({expected} site(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
