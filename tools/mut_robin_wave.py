"""Mutant: the technique gate is dropped, so the wave pays even without the field (round 20)."""
import io
import json
import sys

CHAR = "src/main/resources/characters/1309.json"
RULE_ID = "technique_field_recovers_five_at_each_wave"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_robin_wave.py on|off")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc if isinstance(entry, dict) and entry.get("id") == RULE_ID)

if mode == "off":
    if not rule.get("when"):
        sys.exit("REFUSING: the gate is already gone")
    rule.pop("when")
    print("MUTATION: the 秘技 gate is gone")
else:
    if rule.get("when"):
        sys.exit("REFUSING: the gate is still there")
    rule["when"] = ["self has_state \u79d8\u6280"]
    print("restored: the gate is back")

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
