"""Mutant: remove the swap and measure the SAME scene (round 6 of the goal).

Same content, same table, same levels, same enemy -- only the swap differs. If the damage is identical, the swap does not reach
the commanded cast; if it changes, it does.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_1404_swap.py on|off")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
swap = [effect for effect in rule["do"] if effect.get("op") == "REPLACE_SKILL"]

if mode == "off":
    if not swap:
        sys.exit("REFUSING: the swap is not there")
    rule["do"] = [effect for effect in rule["do"] if effect.get("op") != "REPLACE_SKILL"]
    print("MUTATION: the swap is gone")
else:
    if swap:
        sys.exit("REFUSING: the swap is still there")
    rule["do"].insert(0, {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 9, "turns": 1, "target": "self"})
    print("restored: the swap is back")

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
