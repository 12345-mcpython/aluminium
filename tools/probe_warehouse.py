"""Who actually has a 仓库技, and what does it say? (round 8 of the goal)

The §3 row says the reader is `1407` with 1 reader. My rows have been wrong before (a mis-attributed 1408 row, a dismissed "slot 11"),
so this asks the data: the index's own column, then each such page's sentence.
"""
import io
import os
import re

corpus = "E:/turnbasedgamedata/aluminium_texts"
out = []

index = os.path.join(corpus, "index.md")
if os.path.isfile(index):
    flat = io.open(index, encoding="utf-8", errors="replace").read()
    rows = [line for line in flat.split("\n") if line.startswith("|") and re.search(r"\|\s*\d{4}\s*\|", line)]
    out.append("=== index rows mentioning a 仓库技 column (%d rows) ===" % len(rows))
    for line in rows:
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) >= 7 and cells[-1] not in ("\u2014", "-", ""):
            out.append("   %s -> %s" % (" | ".join(cells[:3]), cells[-1]))
    out.append("   (rows with a non-empty last column are listed above)")

out.append("")
out.append("=== pages whose text states 仓库技 ===")
hits = []
for name in sorted(os.listdir(corpus)):
    if not name.endswith(".md"):
        continue
    body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
    if "\u4ed3\u5e93\u6280" in body:
        flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", body))
        for match in list(re.finditer("\u4ed3\u5e93\u6280", flat))[:1]:
            hits.append("[%s] ...%s..." % (name, flat[max(0, match.start() - 120):match.start() + 260]))
out.append("   pages: %d" % len(hits))
out.extend(hits[:12])
io.open("tools/_probe_warehouse.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
