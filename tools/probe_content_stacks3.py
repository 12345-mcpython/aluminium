"""Probe v3: the same content effect, with `max_stacks` spelled the way the files spell it.

Suspicion: `maxStacks` is a Java name, and Gson only knows `@SerializedName("max_stacks")` -- so the key was accepted by the
key guard and then dropped, leaving the stackable state with a cap of 1, which is exactly "one instance".
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1513.json"
PROBE_ID = "probe_turn_start_stacks"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next((entry for entry in doc["rules"] if isinstance(entry, dict) and entry.get("id") == PROBE_ID), None)
if rule is None:
    sys.exit("REFUSING: the probe is not there")
rule["do"] = [{
    "op": "APPLY_BUFF",
    "buff": "probeState",
    "permanent": True,
    "target": "self",
    "stackable": True,
    "max_stacks": 99,
    "amount": 4,
}]
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the probe spells the cap the way the files do")
