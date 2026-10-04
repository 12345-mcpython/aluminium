"""Discriminator: drop the `when` clause, to tell the condition apart from the two amount spellings.

Measured so far: the sweep took all four instances (so the event fired, carrying 4) and her resource did not move. Either the
condition `actor state_ended 好活当赏` never matches, or `amountFromEvent` + `amountPercent` do not combine.
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
        rule.pop("when", None)
        touched += 1
if touched != 1:
    sys.exit("REFUSING: found %d" % touched)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the condition is off for the discriminator")
