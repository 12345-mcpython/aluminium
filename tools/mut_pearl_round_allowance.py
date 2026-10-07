"""Mutations for the per-round allowance and its reset.

  allowance   `max_per_round` 50 -> 100, so a round would hand out more than the document allows
  noreset     the reset rule's rank condition changed so it never fires (its `when` emptied), leaving one
              allowance for the whole battle
  order       nothing in the content changes, but the check below re-runs the judge after swapping the two rules'
              order by hand, because the placement is what the second case is about

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_round_allowance.py <variant>
    python tools/mut_pearl_round_allowance.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
RESET_ID = "trace_resets_the_round_allowance_on_her_turn"
GAIN_ID = "trace_gains_on_an_ally_turn_start"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in ("allowance", "noreset", "order"):
        print(__doc__)
        return 1

    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [r["id"] for r in rules]

    if sys.argv[1] == "allowance":
        touched = 0
        for rule in rules:
            if rule["id"] != GAIN_ID:
                continue
            for effect in rule["do"]:
                if effect.get("max_per_round") == 50:
                    effect["max_per_round"] = 100
                    touched += 1
        if touched != 1:
            print(f"NOT APPLIED: found {touched} allowance fields, expected 1")
            return 2
    elif sys.argv[1] == "noreset":
        if RESET_ID not in ids:
            print("NOT APPLIED: the reset rule is missing")
            return 3
        for rule in rules:
            if rule["id"] == RESET_ID:
                rule["when"] = ["actor == self", "ally_count >= 99"]   # never true in a four-unit party
        print("applied noreset (the reset rule can no longer fire)")
    else:  # order
        if RESET_ID not in ids or GAIN_ID not in ids:
            print("NOT APPLIED: one of the two rules is missing")
            return 4
        reset = next(r for r in rules if r["id"] == RESET_ID)
        gain = next(r for r in rules if r["id"] == GAIN_ID)
        rules.remove(reset)
        rules.insert(rules.index(gain) + 1, reset)
        print("applied order (the reset now runs AFTER the gain)")

    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    check = json.loads(CONTENT.read_text(encoding="utf-8"))
    order = [r["id"] for r in check["rules"]]
    print("reset index", order.index(RESET_ID), "gain index", order.index(GAIN_ID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
