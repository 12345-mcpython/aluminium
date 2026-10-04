"""Targeted mutation: restore everything, then unmark ONLY the derived (`scale: self_stacks:`) clauses.

Why: the previous mutation removed all four flags, and the judge stopped at its first assertion, so the derived half's
mutation was never actually read. Here the flat ATTACK clause stays live (its assertion must pass) and only the derived
CRIT clause becomes a snapshot (its assertion must fail) -- which is the missing half of the proof.
"""
import io
import json
import sys

PATH = "src/main/resources/characters/1314.json"
with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

restored, unmarked = 0, 0
for rule in rules:
    for effect in rule.get("do", []):
        if not isinstance(effect, dict) or effect.get("op") != "MODIFY_ATTR":
            continue
        derived = str(effect.get("scale", "")).startswith("self_stacks:")
        if derived:
            if effect.pop("per_stack_live", None) is not None:
                unmarked += 1
        elif effect.get("per_stack") and effect.get("per_stack_live") is None:
            effect["per_stack_live"] = True
            restored += 1

if unmarked < 1:
    sys.exit("REFUSING: no derived clause carried the flag to remove")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("derived unmarked=%d, flat restored=%d" % (unmarked, restored))
