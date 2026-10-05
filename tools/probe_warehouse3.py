"""The warehouse skills' effect text (round 8 of the goal). The heading says "详见下方生效范围", so the window has to be wider."""
import io
import os
import re

corpus = "E:/turnbasedgamedata/aluminium_texts"
out = []
for name in ("1407_遐蝶.md", "1506_银狼LV.999.md"):
    path = os.path.join(corpus, name)
    if not os.path.isfile(path):
        out.append("(no %s)" % name)
        continue
    flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", io.open(path, encoding="utf-8", errors="replace").read()))
    match = re.search("仓库技（全局辅助）", flat)
    if match is None:
        out.append("(%s: no warehouse section heading)" % name)
        continue
    out.append("=== %s ===" % name)
    out.append(flat[match.start():match.start() + 1400])
    out.append("")
io.open("tools/_probe_warehouse3.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
