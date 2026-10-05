"""Audit probe (round 1652): the two remaining readers of objective ①.

  1. 1505's 【好活当赏】 ending clause -- the exact sentence and its number;
  2. the light cone that grants 奇袭 -- which id, and whether our tree has a rule file for it.

Output to a UTF-8 file: this shell mangles Chinese on stdout.
"""
import io
import json
import glob
import os
import re

out = []

# 1. 1505's sentences that talk about the resource ENDING
for path in ("src/main/resources/data/character_data.json", "src/main/resources/data/skills.json"):
    if not os.path.exists(path):
        continue
    text = io.open(path, encoding="utf-8").read()
    for match in re.finditer(r'[^"]{0,140}好活当赏[^"]{0,200}', text):
        sentence = match.group(0)
        if "结束" in sentence or "转化" in sentence:
            out.append("[%s] %s" % (os.path.basename(path), sentence.strip()))

# 2. any weapon whose text mentions 奇袭 (the "raid"/coup the light cone is named for)
for path in sorted(glob.glob("src/main/resources/data/*.json")):
    text = io.open(path, encoding="utf-8", errors="replace").read()
    if "奇袭" not in text:
        continue
    doc = json.load(io.open(path, encoding="utf-8"))
    if not isinstance(doc, dict):
        continue
    for key, value in doc.items():
        blob = json.dumps(value, ensure_ascii=False)
        if "奇袭" in blob:
            desc = value.get("skill_description") if isinstance(value, dict) else None
            out.append("[cone %s in %s] %s" % (key, os.path.basename(path),
                                               json.dumps(desc or value, ensure_ascii=False)[:400]))

# 3. do we ship a rule file for those cone ids?
cone_ids = sorted({line.split("cone ")[1].split(" in ")[0] for line in out if line.startswith("[cone ")})
for cone_id in cone_ids:
    shipped = os.path.exists("src/main/resources/light_cones/%s.json" % cone_id)
    out.append("shipped rule file for cone %s: %s" % (cone_id, shipped))

io.open("tools/_tmp_audit.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out[:20]))
print("written", len(out), "blocks")
