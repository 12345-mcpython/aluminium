"""Clear a resource with SPEND_RESOURCE + spendAll, not REMOVE_RESOURCE.

`REMOVE_RESOURCE` is the CLAMPED take ("take what is there") and `SPEND_RESOURCE` is the one that accepts `spendAll`
("consume all of it"); the validator refuses the pair, which is how this was caught. Fixes both the live file and the
shipping script that writes it.
"""
import json
import pathlib

LIVE = pathlib.Path("src/main/resources/characters/1506.json")
SCRIPT = pathlib.Path("tools/ship_1506.py")

OLD = '"op": "REMOVE_RESOURCE", "resource": HIDDEN, "spendAll": True'
NEW = '"op": "SPEND_RESOURCE", "resource": HIDDEN, "spendAll": True'

data = json.loads(LIVE.read_text(encoding="utf-8"))
changed = 0
for rule in data["rules"]:
    for effect in rule.get("do", []):
        if effect.get("op") == "REMOVE_RESOURCE" and effect.get("spendAll"):
            effect["op"] = "SPEND_RESOURCE"
            changed += 1
LIVE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"live file: flipped {changed} effect(s)")

text = SCRIPT.read_text(encoding="utf-8")
found = text.count(OLD)
text = text.replace(OLD, NEW)
SCRIPT.write_text(text, encoding="utf-8")
print(f"script: replaced {found} occurrence(s)")

check = json.loads(LIVE.read_text(encoding="utf-8"))
left = sum(1 for r in check["rules"] for e in r.get("do", [])
           if e.get("op") == "REMOVE_RESOURCE" and e.get("spendAll"))
print("remaining bad pairs:", left)
