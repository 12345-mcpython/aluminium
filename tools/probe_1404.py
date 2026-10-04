"""What 1404 already says, and what its 150-charge clause would need (round 3 of the goal).

Measured: 「弑神登神」 is 万敌's (1404), not 1408's; `CAST_SKILL` is the shipped spelling for 「自动施放」; and his file already
autocasts 【弑王成王】 at his turn start. The corpus sentence still to place is 「【血仇】状态期间充能达到 150 点时，万敌立即获得1个额外回合
并自动施放【弑神登神】」.
"""
import io
import json
import re

out = []
with io.open("src/main/resources/characters/1404.json", encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
out.append("=== 1404: %d rules ===" % len(rules))
for rule in rules:
    if not isinstance(rule, dict):
        continue
    ops = ",".join(effect.get("op", "?") for effect in rule.get("do", []))
    skills = ",".join(str(effect.get("skill")) for effect in rule.get("do", []) if effect.get("skill"))
    out.append("   %-42s on=%-16s when=%-46s ops=%-34s skill=%s" % (
        str(rule.get("id"))[:42], rule.get("on"), str(rule.get("when"))[:46], ops[:34], skills))
out.append("")
out.append("=== declared resources ===")
for entry in (doc.get("resources") or []):
    out.append("   %s scope=%r max=%s" % (entry.get("id"), entry.get("scope"), entry.get("max")))
out.append("")
blob = io.open("src/main/resources/characters/1404.json", encoding="utf-8").read()
out.append("=== mentions: 150 -> %d ; 弑神登神 -> %d ; 额外回合 -> %d ; 血仇 -> %d"
           % (blob.count("150"), blob.count("弑神登神"), blob.count("额外回合"), blob.count("血仇")))
for match in re.finditer("150", blob):
    out.append("   ...%s..." % re.sub(r"\s+", " ", blob[max(0, match.start() - 150):match.start() + 150]))
io.open("tools/_probe_1404.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
