"""Read the shipped autocast rule's own note, to a FILE (round 3 of the goal).

(The console is GBK here, so printing Chinese raises UnicodeEncodeError -- the lesson this session keeps relearning.)
"""
import io
import json

out = []
with io.open("src/main/resources/characters/1404.json", encoding="utf-8") as handle:
    doc = json.load(handle)
for rule in doc["rules"]:
    if not isinstance(rule, dict):
        continue
    if rule.get("id") == "turn_start_autocasts_skill":
        out.append("=== turn_start_autocasts_skill / source ===")
        out.append(str(rule.get("source")))
        out.append("")
        out.append("=== turn_start_autocasts_skill / note ===")
        out.append(str(rule.get("note")))
    if rule.get("id") == "level_convention":
        out.append("")
        out.append("=== level_convention / note (the register) ===")
        out.append(str(rule.get("note")))
io.open("tools/_probe_notes.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written")
