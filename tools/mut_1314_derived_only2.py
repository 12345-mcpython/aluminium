"""Set the exact state for the targeted mutation: flat clauses LIVE, derived clauses SNAPSHOTS.

The earlier mutations left all four flags removed, so "remove the derived flag" had nothing to remove and the file was not
written. This sets the state directly instead of toggling it.
"""
import io
import json

PATH = "src/main/resources/characters/1314.json"
with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

live, snapshot = 0, 0
for rule in rules:
    for effect in rule.get("do", []):
        if not isinstance(effect, dict) or effect.get("op") != "MODIFY_ATTR":
            continue
        if str(effect.get("scale", "")).startswith("self_stacks:"):
            effect.pop("per_stack_live", None)
            snapshot += 1
        elif effect.get("per_stack"):
            effect["per_stack_live"] = True
            live += 1
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("state set: flat live=%d, derived snapshot=%d" % (live, snapshot))
