"""Mutation: drop the 50%, so the whole four transfers instead of half.

Expected under `off`: 20 -> 24 instead of 20 -> 22, which is exactly the share the sentence states.
"""
import io
import json
import sys

PATH = "src/main/resources/characters/1505.json"
RULE_ID = "kai_bu_bai_half_of_a_fallen_gift"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()

doc = json.load(io.open(PATH, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
rule = next((entry for entry in rules if isinstance(entry, dict) and entry.get("id") == RULE_ID), None)
if rule is None:
    sys.exit("REFUSING: the rule is not there")
effect = rule["do"][0]
if mode == "off":
    if "amountPercent" not in effect:
        sys.exit("REFUSING: no share to remove")
    effect.pop("amountPercent")
    print("MUTATION: the whole amount transfers, not half")
elif mode == "on":
    if "amountPercent" in effect:
        sys.exit("REFUSING: the share is already there")
    effect["amountPercent"] = 0.5
    print("restored: half of it")
else:
    sys.exit("usage: mut_kai_bu_bai.py on|off")

with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
