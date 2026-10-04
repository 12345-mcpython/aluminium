"""Mutation: remove 1415's ode rule, so the coup's end pays nothing.

Expected under `off`: the same battle ends at 2 instead of 3 (6 + 1 + 1 - 6 + 0), which is exactly the one charge the
sentence promises.
"""
import io
import json
import sys

PATH = "src/main/resources/characters/1415.json"
RULE_ID = "memosprite_ode_to_law_pays_charge"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
doc = json.load(io.open(PATH, encoding="utf-8"))
rules = doc["rules"]

if mode == "off":
    kept = [rule for rule in rules if not (isinstance(rule, dict) and rule.get("id") == RULE_ID)]
    if len(kept) == len(rules):
        sys.exit("REFUSING: the rule is not there")
    doc["rules"] = kept
    saved = [rule for rule in rules if isinstance(rule, dict) and rule.get("id") == RULE_ID][0]
    io.open("tools/_tmp_ode_rule.json", "w", encoding="utf-8", newline="\n").write(
        json.dumps(saved, ensure_ascii=False, indent=2))
    print("MUTATION: 1415's ode rule is removed")
elif mode == "on":
    if any(isinstance(rule, dict) and rule.get("id") == RULE_ID for rule in rules):
        sys.exit("REFUSING: the rule is already there")
    saved = json.load(io.open("tools/_tmp_ode_rule.json", encoding="utf-8"))
    rules.append(saved)
    print("restored: 1415's ode rule is back")
else:
    sys.exit("usage: mut_ode_rule.py on|off")

with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
