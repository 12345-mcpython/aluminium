"""Mutants for the gate and the 150-charge clause (round 7 of the goal).

  a: the `!self has_state 血仇` gate is removed -> the charge arriving in 【血仇】 is drained again;
  b: the charge threshold becomes 160          -> nothing fires at 150.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "off", "on"):
    sys.exit("usage: mut_1404_gate.py a|b|off|on")

GATE = "!self has_state \u8840\u4ec7"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
hundred = next(entry for entry in doc["rules"]
               if isinstance(entry, dict) and entry.get("id") == "talent_enters_bloodfeud_at_a_hundred")
fifty = next(entry for entry in doc["rules"]
             if isinstance(entry, dict) and entry.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty")
THRESHOLD = "self_resource:\u5929\u8d4b\u5145\u80fd >= 150"

if mode == "on":
    if GATE not in hundred["when"]:
        hundred["when"] = list(hundred["when"]) + [GATE]
    fifty["when"] = [THRESHOLD if ">= 1" in str(term) else term for term in fifty["when"]]
    print("restored: the gate and the 150 threshold")
else:
    if GATE not in hundred["when"]:
        sys.exit("REFUSING: the gate is not there")
    if mode in ("a", "off"):
        hundred["when"] = [term for term in hundred["when"] if term != GATE]
    if mode in ("b", "off"):
        fifty["when"] = [("self_resource:\u5929\u8d4b\u5145\u80fd >= 160" if ">= 1" in str(term) else term)
                         for term in fifty["when"]]
    print("MUTATION %s" % mode)

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
