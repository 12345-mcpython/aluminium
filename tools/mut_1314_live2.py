"""Toggle every `per_stack_live` in 1314: off = both clauses are snapshots (the mutation), on = live again.

Under `off` neither the flat ATTACK clause nor the derived CRIT clause follows four added layers, so both readings are 0.0
where the judge wants 0.02 and 0.096.
"""
import io
import json
import sys

PATH = "src/main/resources/characters/1314.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_1314_live2.py on|off")

with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
touched = 0
for rule in rules:
    for effect in rule.get("do", []):
        if not isinstance(effect, dict):
            continue
        if mode == "off" and effect.get("per_stack_live") is True:
            effect.pop("per_stack_live")
            touched += 1
        elif mode == "on" and effect.get("per_stack_live") is None and (
                effect.get("per_stack") or str(effect.get("scale", "")).startswith("self_stacks:")):
            if effect.get("op") == "MODIFY_ATTR":
                effect["per_stack_live"] = True
                touched += 1
if touched < 1:
    sys.exit("REFUSING: nothing to toggle for %r" % mode)
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("%s: %d clause(s)" % ("MUTATION (snapshots)" if mode == "off" else "restored (live)", touched))
