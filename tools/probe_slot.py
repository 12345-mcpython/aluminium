"""Can 1404's 150-charge clause be written? (round 3 of the goal)

Two prerequisites, both measured rather than assumed:
  (a) an op for 「立即获得 1 个额外回合」 -- the repo's op list is the source of truth;
  (b) which skill slot 【弑神登神】 is -- the data tree is asked by name, and the repo's own 1404 rules are dumped to see how his
      other named skills are addressed.
"""
import io
import json
import os
import re

out = []

# (a) the ops the interpreter knows, and anything turn-shaped
path = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
text = io.open(path, encoding="utf-8").read()
ops = sorted(set(re.findall(r'case "([A-Z_]+)"', text)))
out.append("=== ops (%d) ===" % len(ops))
out.append("   " + ", ".join(ops))
out.append("   turn-shaped: " + ", ".join(op for op in ops if "TURN" in op or "ADVANCE" in op or "EXTRA" in op))

# (b) 1404's own rules, in full, for the named-skill spellings
with io.open("src/main/resources/characters/1404.json", encoding="utf-8") as handle:
    doc = json.load(handle)
for rule in doc["rules"]:
    if isinstance(rule, dict) and rule.get("id") in ("turn_start_autocasts_skill", "talent_enters_bloodfeud_at_a_hundred"):
        out.append("")
        out.append("=== %s ===" % rule["id"])
        out.append("   when: %s" % rule.get("when"))
        out.append("   do:   %s" % json.dumps(rule.get("do"), ensure_ascii=False))

# (c) the data tree, by name
root = "E:/turnbasedgamedata"
HIT = []
scanned = 0
for base, dirs, files in os.walk(root):
    for name in files:
        if not name.lower().endswith((".json", ".md", ".html", ".txt", ".csv")):
            continue
        full = os.path.join(base, name)
        try:
            body = io.open(full, encoding="utf-8", errors="replace").read()
        except Exception:
            continue
        scanned += 1
        if "弑神登神" in body:
            flat = re.sub(r"\s+", " ", body)
            match = re.search("弑神登神", flat)
            HIT.append("FILE %s\n     ...%s..." % (full.replace("\\", "/").replace(root, ""),
                                                   flat[max(0, match.start() - 220):match.start() + 260]))
            if len(HIT) >= 4:
                break
    if len(HIT) >= 4:
        break
out.append("")
out.append("=== data tree: %d files scanned, %d mention it ===" % (scanned, len(HIT)))
out.extend(HIT)
io.open("tools/_probe_slot.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
