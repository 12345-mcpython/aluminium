"""Toggle the content flag in 1314's ATTACK clause: off = a snapshot (the mutation), on = live."""
import io
import json
import sys

PATH = "src/main/resources/characters/1314.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

touched = 0
for rule in rules:
    for effect in rule.get("do", []):
        if not isinstance(effect, dict) or effect.get("attribute") != "ATTACK":
            continue
        if mode == "off" and effect.get("per_stack_live") is True:
            effect.pop("per_stack_live")
            touched += 1
        elif mode == "on" and effect.get("per_stack") and effect.get("per_stack_live") is None:
            effect["per_stack_live"] = True
            touched += 1
if touched < 1:
    sys.exit("REFUSING: nothing to toggle for mode %r" % mode)
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("%s: %d ATTACK effect(s)" % ("MUTATION (snapshot)" if mode == "off" else "restored (live)", touched))
