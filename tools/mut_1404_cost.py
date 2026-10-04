"""Mutants for the cost reading (round 5 of the goal).

  a: the CONSUME_HP effect is dropped             -> nothing is paid, so the equality fails;
  b: the share becomes `owner_max_hp` (35% of MAX) -> the spend is too large, so the "NOT a share of the maximum" half fails.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "off", "on"):
    sys.exit("usage: mut_1404_cost.py a|b|off|on")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
cost = [effect for effect in rule["do"] if effect.get("op") == "CONSUME_HP"]

if mode == "on":
    if not cost:
        rule["do"].insert(1, {"op": "CONSUME_HP", "scale": "target_current_hp", "percent": 0.35, "target": "self"})
    else:
        cost[0]["scale"] = "target_current_hp"
    print("restored: the cost reads the current value")
else:
    if not cost or cost[0].get("scale") != "target_current_hp":
        sys.exit("REFUSING: the cost is not in its shipped shape")
    if mode in ("a", "off"):
        rule["do"] = [effect for effect in rule["do"] if effect.get("op") != "CONSUME_HP"]
    if mode in ("b", "off"):
        cost[0]["scale"] = "owner_max_hp"
    print("MUTATION %s" % mode)

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
