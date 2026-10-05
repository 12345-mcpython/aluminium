"""Second discriminator: a constant amount, to see whether the rule fires at all.

  * if the resource moves by 2, the rule fires and the problem is the `amountFromEvent` + `amountPercent` combination;
  * if it does not move, a STATE_ENDED rule in 1505's file never runs for a TEAMMATE's state.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1505.json"
RULE_ID = "kai_bu_bai_half_of_a_fallen_gift"
doc = json.load(io.open(CHAR, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
touched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == RULE_ID:
        rule["do"] = [{"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 2.0, "target": "self"}]
        touched += 1
if touched != 1:
    sys.exit("REFUSING: found %d" % touched)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the effect is a constant for the discriminator")
