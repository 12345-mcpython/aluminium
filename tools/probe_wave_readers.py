"""How many documents state a per-wave limit? (round 19 of the goal)

The engine has cooldown / per_turn / per_subject / once_per_battle / once_per_attack and NO per-wave limit, while 1506's warehouse skill
says 「该效果每个波次最多触发 1 次」. Before building one, count the readers -- 「每个波次」 in the corpus.
"""
import io
import os
import re

corpus = "E:/turnbasedgamedata/aluminium_texts"
hits = []
for name in sorted(os.listdir(corpus)):
    if not name.endswith(".md"):
        continue
    body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
    flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", body))
    for match in list(re.finditer("\u6bcf\u4e2a\u6ce2\u6b21", flat))[:2]:
        hits.append("[%s] ...%s..." % (name, flat[max(0, match.start() - 110):match.start() + 130]))
out = ["=== documents stating 每个波次: %d ===" % len(hits)]
out.extend(hits[:16])
io.open("tools/_wave_readers.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("documents: %d" % len(hits))
