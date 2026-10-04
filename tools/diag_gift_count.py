"""Discriminator: a literal count instead of the party scale.

If the gift then carries four instances, the reward fires and the problem is inside `applyState`'s scale resolution; if it
still carries one, the problem is elsewhere in the rule.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1513.json"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"] if isinstance(entry, dict) and entry.get("id") == "elation_moment_reward")
effect = rule["do"][0]
effect.pop("scale", None)
effect.pop("percent", None)
effect["amount"] = 4
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the count is a literal four now")
