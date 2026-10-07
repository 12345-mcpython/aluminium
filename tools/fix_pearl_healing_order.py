"""Move the healing link to the END of the BATTLE_START list, so it reads the panel the other clauses built.

Measured: with the healing rule BEFORE the stat traces, its `scale: self_attr:ELATION_DAMAGE_BOOST` read 0.32 (traces
not yet applied) and produced 0.064; the panel afterwards shows 0.42, so the correct bonus is 0.084. Moving the
TRACES earlier instead (the previous attempt) fixed this but broke the Elation arithmetic, so the cheap and correct
move is the other one: a clause that READS the panel goes after everything that writes it.

Zero-based index 36 (the end) is where it goes - after `trace_insight_...` (33) and the stat traces (36).

Usage:
    python tools/fix_pearl_healing_order.py
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
HEALING_ID = "trace_insight_links_healing_to_elation"
INSIGHT_ID = "trace_insight_raises_elation_at_2400_defence"
TRACES_ID = "stat_traces_the_ten_panel_rows"


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [r["id"] for r in rules]
    for needed in (HEALING_ID, INSIGHT_ID, TRACES_ID):
        if needed not in ids:
            print("rule not found:", needed)
            return 1
    if ids.index(HEALING_ID) > ids.index(TRACES_ID):
        print("already after the stat traces:", HEALING_ID)
        return 0

    rule = next(r for r in rules if r["id"] == HEALING_ID)
    rules.remove(rule)
    rules.append(rule)
    data["rules"] = rules
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    order = [r["id"] for r in check["rules"]]
    if order.index(HEALING_ID) < order.index(TRACES_ID):
        print("WRITE CHECK FAILED: the healing link still precedes the traces")
        return 2
    print(f"healing link moved to index {order.index(HEALING_ID)} of {len(order)} "
          f"(insight at {order.index(INSIGHT_ID)}, traces at {order.index(TRACES_ID)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
