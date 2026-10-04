"""Two more mutants for this batch's new readings (round 1694).

  C: the trace's `max_stacks: 2` is dropped -> the third layer lands, so ATK moves and the cap reading fails;
  D: the battle-start seed becomes 2 -> the reading that pins 「战斗开始时，获得 1 点【火种】」 fails.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1408.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_cap_and_seed.py on|off")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
attack = [rule for rule in doc["rules"] if isinstance(rule, dict)
          and str(rule.get("id", "")).startswith("trace_hero_true_colors")]
seed = next(rule for rule in doc["rules"]
            if isinstance(rule, dict) and rule.get("id") == "trace_worlds_end_one_seed_at_battle_start")
if len(attack) != 2:
    sys.exit("REFUSING: expected two attack rules, found %d" % len(attack))

if mode == "off":
    if any("max_stacks" not in effect for rule in attack for effect in rule["do"]):
        sys.exit("REFUSING: a cap is already gone")
    if seed["do"][0].get("amount") != 1:
        sys.exit("REFUSING: the seed is %r" % seed["do"][0].get("amount"))
    for rule in attack:
        for effect in rule["do"]:
            effect.pop("max_stacks")
    seed["do"][0]["amount"] = 2
    print("MUTATION C: the caps are gone; MUTATION D: the battle-start seed is 2")
else:
    if any("max_stacks" in effect for rule in attack for effect in rule["do"]):
        sys.exit("REFUSING: a cap is still there")
    if seed["do"][0].get("amount") != 2:
        sys.exit("REFUSING: the seed is %r" % seed["do"][0].get("amount"))
    for rule in attack:
        for effect in rule["do"]:
            effect["max_stacks"] = 2
    seed["do"][0]["amount"] = 1
    print("restored C and D")

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
