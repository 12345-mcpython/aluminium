"""Probe (round 1653): 1505's 「开不败」 sentence in full, and every use of 【好活当赏】 in our tree.

Two questions, both needed before any model change:
  1. the sentence's own number(s) and condition (the corpus piece found last round was truncated);
  2. how the resource is used today -- which files declare it, and which rules read or grant it.
"""
import io
import json
import glob
import os
import re

ROOT = r"E:\turnbasedgamedata\aluminium_texts"
out = []

# 1. the sentence in full, from her own page
path = os.path.join(ROOT, "1505_绯英.html")
raw = io.open(path, encoding="utf-8", errors="replace").read()
text = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", raw))
at = text.find("开不败")
out.append("== 1505 page, the 开不败 passage ==")
out.append(text[at:at + 400] if at >= 0 else "NOT FOUND")

# 2. every use of the name in our content
out.append("")
out.append("== our tree: files mentioning it ==")
for p in sorted(glob.glob("src/main/resources/**/*.json", recursive=True)):
    try:
        raw_json = io.open(p, encoding="utf-8", errors="replace").read()
    except Exception:
        continue
    if "好活当赏" not in raw_json:
        continue
    try:
        doc = json.load(io.open(p, encoding="utf-8"))
    except Exception:
        out.append("  %s (unreadable)" % p)
        continue
    rules = doc.get("rules") if isinstance(doc, dict) else doc
    resources = doc.get("resources") if isinstance(doc, dict) else None
    out.append("  %s" % p)
    if resources:
        for res in resources:
            if res.get("id") == "好活当赏":
                out.append("     DECLARES id=%s max=%s scope=%s" % (res.get("id"), res.get("max"), res.get("scope")))
    for rule in (rules or []):
        if not isinstance(rule, dict):
            continue
        blob = json.dumps(rule, ensure_ascii=False)
        if "好活当赏" in blob:
            out.append("     rule %s on=%s when=%s ops=%s" % (
                rule.get("id"), rule.get("on"), json.dumps(rule.get("when"), ensure_ascii=False),
                [(e.get("op"), e.get("resource"), e.get("buff"), e.get("attribute")) for e in rule.get("do", [])]))

io.open("tools/_tmp_haohuo.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
