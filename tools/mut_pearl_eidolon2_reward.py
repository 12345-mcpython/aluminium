"""Mutations for Eidolon 2's reward boost.

  grant      the added grant's 好活当赏 amount 30 -> 1
  punchline  the added grant's 笑点 amount 60 -> 1
  gate       the rank moved 2 -> 6, so the boost would only apply at the top rank
  removal    the shipped removal's 笑点 amount 120 -> 60, so the boost would not fully come off

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_eidolon2_reward.py <variant>
    python tools/mut_pearl_eidolon2_reward.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
GRANT = "extra_turn_pays_the_archetype_at_e2"

VARIANTS = {
    # The added grant's own two numbers, in the clause that carries them.
    "grant": (f'"id": "{GRANT}",\n      "min_eidolon": 2,\n      "when": [\n        "eidolon >= 2",'
              '\n        "actor has_state 美学底本"\n      ],\n      "do": [\n        {\n          "op": "GAIN_RESOURCE",'
              '\n          "resource": "好活当赏",\n          "amount": 30',
              f'"id": "{GRANT}",\n      "min_eidolon": 2,\n      "when": [\n        "eidolon >= 2",'
              '\n        "actor has_state 美学底本"\n      ],\n      "do": [\n        {\n          "op": "GAIN_RESOURCE",'
              '\n          "resource": "好活当赏",\n          "amount": 1'),
    "punchline": (f'"id": "{GRANT}",\n      "min_eidolon": 2,\n      "when": [\n        "eidolon >= 2",'
                  '\n        "actor has_state 美学底本"\n      ],\n      "do": [\n        {\n          "op": "GAIN_RESOURCE",'
                  '\n          "resource": "好活当赏",\n          "amount": 30\n        },\n        {\n'
                  '          "op": "GAIN_RESOURCE",\n          "resource": "笑点",\n          "amount": 60',
                  f'"id": "{GRANT}",\n      "min_eidolon": 2,\n      "when": [\n        "eidolon >= 2",'
                  '\n        "actor has_state 美学底本"\n      ],\n      "do": [\n        {\n          "op": "GAIN_RESOURCE",'
                  '\n          "resource": "好活当赏",\n          "amount": 30\n        },\n        {\n'
                  '          "op": "GAIN_RESOURCE",\n          "resource": "笑点",\n          "amount": 1'),
    "gate": (f'"id": "{GRANT}",\n      "min_eidolon": 2,\n      "when": [\n        "eidolon >= 2",',
             f'"id": "{GRANT}",\n      "min_eidolon": 6,\n      "when": [\n        "eidolon >= 6",'),
    # The shipped removal's punchline: leaving it at 60 would strand half the boost.
    "removal": ('"op": "REMOVE_RESOURCE",\n          "resource": "笑点",\n          "amount": 120',
                '"op": "REMOVE_RESOURCE",\n          "resource": "笑点",\n          "amount": 60'),
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
