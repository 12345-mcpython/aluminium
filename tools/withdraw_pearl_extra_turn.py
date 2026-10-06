"""Withdraw the extra-turn reward rule, keeping the engine events and leaving a recipe behind.

Why withdrawn rather than shipped: its gate could not be made load-bearing in a judge. The control run ("an extra
turn taken by somebody who is not the archetype") kept paying, and tracing it turned up that the battle's own traffic
marks units as 美学底本 in more places than the fixture's single cast - so a judge that looked green would have been
green for the wrong reason. This project's rule is that an unproven clause is registered, not left in the tree
looking finished.

The engine half stays and is proven: `EXTRA_TURN_STARTED` / `EXTRA_TURN_ENDED` with `ExtraTurnEventTest` (3 cases,
3 mutations red). What is missing for the content is the RECIPIENT binding - see the note written into GAPS.

Usage:
    python tools/withdraw_pearl_extra_turn.py
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RULE_ID = "extra_turn_pays_the_archetype_thirty_and_sixty"


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    before = len(data["rules"])
    data["rules"] = [rule for rule in data["rules"] if rule["id"] != RULE_ID]
    if len(data["rules"]) == before:
        print("rule not present:", RULE_ID)
        return 1
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    check = json.loads(TARGET.read_text(encoding="utf-8"))
    if any(rule["id"] == RULE_ID for rule in check["rules"]):
        print("WITHDRAWAL DID NOT LAND")
        return 2
    print(f"rules: {before} -> {len(check['rules'])} (withdrew {RULE_ID})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
