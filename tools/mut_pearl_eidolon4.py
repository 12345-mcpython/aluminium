"""Mutations for Eidolon 4's doubling.

  gate4     the rule-level gate moved 4 -> 3 (so the doubling would apply one rank early)
  percent   one tier's percentage changed, so the sum is no longer exactly double
  drop      one of the four added clauses removed

`gate4` is read by the CONTENT assertion (the rank also lives in `when`), so it is expected to be caught there as
well as by the ratio.

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_eidolon4.py <variant>
    python tools/mut_pearl_eidolon4.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
ONE = "eidolon4_doubles_the_elation_skill_at_1_elation_allies"


def variants(text):
    """Each variant is (label, old, new, expected occurrences)."""
    gate_anchor = ('"id": "' + ONE + '",\n      "min_eidolon": 4,')
    return {
        "gate4": (gate_anchor, gate_anchor.replace('"min_eidolon": 4', '"min_eidolon": 3'), 1),
        "percent": ('"id": "' + ONE + '",\n      "min_eidolon": 4,\n      "when": [\n        "eidolon >= 4",'
                    '\n        "actor has_state 渲染理性，解构欢榆",\n        "allies_with_path:欢愉 >= 1"\n      ],'
                    '\n      "do": [\n        {\n          "op": "ADD_DAMAGE",\n          "scale": "elation_base",'
                    '\n          "percent": 0.1',
                    '"id": "' + ONE + '",\n      "min_eidolon": 4,\n      "when": [\n        "eidolon >= 4",'
                    '\n        "actor has_state 渲染理性，解构欢榆",\n        "allies_with_path:欢愉 >= 1"\n      ],'
                    '\n      "do": [\n        {\n          "op": "ADD_DAMAGE",\n          "scale": "elation_base",'
                    '\n          "percent": 0.05', 1),
    }


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2:
        print(__doc__)
        print("variants: gate4, percent, drop")
        return 1

    text = CONTENT.read_text(encoding="utf-8")
    if sys.argv[1] == "drop":
        start = text.find('    {\n      "on": "DEALING_DAMAGE",\n      "id": "' + ONE + '"')
        if start == -1:
            print("NOT APPLIED: could not find that clause")
            return 2
        end = text.find('    {\n      "on": "DEALING_DAMAGE"', start + 10)
        if end == -1:
            print("NOT APPLIED: could not find the next clause")
            return 3
        CONTENT.write_text(text[:start] + text[end:], encoding="utf-8")
        print("applied drop")
        return 0

    table = variants(text)
    if sys.argv[1] not in table:
        print("unknown variant:", sys.argv[1])
        return 1
    old, new, expected = table[sys.argv[1]]
    if text.count(old) != expected:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times, expected {expected}")
        return 4
    CONTENT.write_text(text.replace(old, new), encoding="utf-8")
    if new not in CONTENT.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        return 5
    print(f"applied {sys.argv[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
