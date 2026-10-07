"""Put Pearl's stat-trace rule FIRST among the BATTLE_START rules, so later clauses read the raised panel.

Measured: the healing link ("a healing bonus equal to 20% of her own Elation") reads `self_attr:ELATION_DAMAGE_BOOST`
when its rule fires. With the stat traces appended at the END of the file, that read happened before the traces'
+0.10 landed, so the bonus came out 0.064 = 0.2 x 0.32 instead of 0.084 = 0.2 x 0.42.

Rule order inside one event is the file's order (an established contract in this project - "the amendment has to be
placed before the rule it amends"), so the fix is positional: the traces are a loadout fact and belong before
anything that reads the panel they build.

Usage:
    python tools/fix_pearl_trace_order.py
    python tools/fix_pearl_trace_order.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "stat_traces_the_ten_panel_rows"
# The rule it must precede (first of the two clauses that read the panel).
INSIGHT_ID = "trace_insight_raises_elation_at_2400_defence"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [r["id"] for r in rules]
    if RULE_ID not in ids or INSIGHT_ID not in ids:
        print("expected both rules present; traces:", RULE_ID in ids, "insight:", INSIGHT_ID in ids)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    trace_rule = next(r for r in rules if r["id"] == RULE_ID)
    rules.remove(trace_rule)
    insert_at = next(i for i, r in enumerate(rules) if r["id"] == INSIGHT_ID)
    rules.insert(insert_at, trace_rule)
    data["rules"] = rules

    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    order = [r["id"] for r in check["rules"]]
    if order.index(RULE_ID) >= order.index(INSIGHT_ID):
        print("WRITE CHECK FAILED: the traces still come after the insight rule - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"moved {RULE_ID} to index {order.index(RULE_ID)} (insight is at {order.index(INSIGHT_ID)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
