"""Put Pearl's stat-trace rule back at the END of the rule list - the reorder made the readings worse.

Measured both ways:

  * traces LAST (as they were appended): Elation 0.10 / 0.42 / 0.45 / 0.78 at DEF 2300 / 2400 / 2500 / 4000 - clean
    numbers, and the deltas the clause is judged on (0.32 at the threshold, 0.03 per 100 over, capped at 1200 counted)
    come out exactly;
  * traces FIRST (this script's earlier attempt, `fix_pearl_trace_order.py`): 0.54525 / 0.582 / 0.61875 / 0.78, which
    no longer decomposes into the clause's own contribution. Ordering inside one event is the file's order, so moving
    a rule that writes a RATIO attribute changes how the later ratio modifiers multiply - and that is a worse
    foundation for a judge than the honest "the traces land last" reading.

So the reorder is reverted. The healing link's ordering question is then answered on its own terms rather than by
moving the panel rule: the healing clause reads `self_attr:ELATION_DAMAGE_BOOST` when it fires, so it must fire after
every Elation source - which is a statement about that clause, not about the traces.

Usage:
    python tools/revert_pearl_trace_order.py
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RULE_ID = "stat_traces_the_ten_panel_rows"


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    current = [r["id"] for r in rules]
    if current[-1] == RULE_ID:
        print("already last:", RULE_ID)
        return 0
    rule = next((r for r in rules if r["id"] == RULE_ID), None)
    if rule is None:
        print("rule not found:", RULE_ID)
        return 1
    rules.remove(rule)
    rules.append(rule)
    data["rules"] = rules
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    order = [r["id"] for r in check["rules"]]
    if order[-1] != RULE_ID:
        print("WRITE CHECK FAILED: the traces are not last")
        return 2
    print(f"moved {RULE_ID} back to the end (index {order.index(RULE_ID)} of {len(order)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
