"""Reconnaissance for the first servant (round 1668): the factory branch, the spec record, an existing spec with an attack,
and everything 1112 states about 账账."""
import io
import json
import os
import re

out = []


def find_java(name):
    for base, _dirs, files in os.walk("src/main/java"):
        if name in files:
            return os.path.join(base, name)
    return None


def dump_java(path, start, count):
    lines = io.open(path, encoding="utf-8").read().split("\n")
    for index in range(start - 1, min(start - 1 + count, len(lines))):
        out.append("%s:%d: %s" % (os.path.basename(path), index + 1, lines[index].strip()[:150]))


out.append("=== SummonFactory around the servant branch ===")
dump_java(find_java("SummonFactory.java"), 128, 42)

out.append("")
out.append("=== MemospriteSpec record header ===")
dump_java("src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java", 40, 14)

out.append("")
out.append("=== which memosprites state an attack ===")
for name in sorted(os.listdir("src/main/resources/memosprites")):
    body = io.open("src/main/resources/memosprites/" + name, encoding="utf-8").read()
    if '"attack"' in body:
        out.append("  %s" % name)

out.append("")
out.append("=== 1112: rules ===")
doc = json.load(io.open("src/main/resources/characters/1112.json", encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
out.append("  rule count: %d ; resources: %s" % (len(rules), json.dumps(doc.get("resources") if isinstance(doc, dict) else None, ensure_ascii=False)))
for rule in rules:
    if not isinstance(rule, dict):
        continue
    out.append("  id=%s on=%s when=%s" % (rule.get("id"), rule.get("on"), json.dumps(rule.get("when"), ensure_ascii=False)))
    out.append("     do=%s" % json.dumps(rule.get("do"), ensure_ascii=False)[:260])
    note = rule.get("note") or ""
    if "账账" in note or "金融动荡" in note:
        out.append("     note=%s" % note[:600])

out.append("")
out.append("=== the data behind 金融动荡 / 账账 (corpus sweep) ===")
for root in ("E:/turnbasedgamedata/aluminium_texts", "E:/turnbasedgamedata"):
    if not os.path.isdir(root):
        continue
    hits = 0
    for name in sorted(os.listdir(root)):
        if not name.endswith(".html"):
            continue
        body = io.open(os.path.join(root, name), encoding="utf-8", errors="replace").read()
        for match in re.finditer("金融动荡", body):
            snippet = re.sub(r"<[^>]+>", "", body[max(0, match.start() - 260):match.start() + 320])
            out.append("  [%s] ...%s..." % (name, snippet.replace("\n", " ")[:420]))
            hits += 1
            break
        if hits >= 3:
            break
    if hits:
        break
if not any("金融动荡" in line for line in out):
    out.append("  (not found in the corpus sweep)")

io.open("tools/_tmp_servant.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
