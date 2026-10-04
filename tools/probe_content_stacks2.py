"""Probe v2 (round 1687): the content effect made byte-equivalent to the hand-built shape that DID repeat.

Variant A (permanent, maxStacks 99, hand-built) repeated 7 times; the content probe (turns 2, resource-named state, amount 4)
gave 1. This probe keeps the JSON path but removes both other differences: a plain state name and `permanent`.
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
    "maxStacks": 99,
    "amount": 4,
}]
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the probe is byte-equivalent to the hand-built shape now")

JUDGE = "src/test/java/com/laosun/aluminium/test/ProbeContentStacksTest.java"
text = io.open(JUDGE, encoding="utf-8").read()
text = text.replace('private static final String GIFT = "\\u597d\\u6d3b\\u5f53\\u8d4f";',
                    'private static final String GIFT = "probeState";', 1)
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge reads the probe state")
