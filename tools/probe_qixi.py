"""Search the WHOLE data tree for 奇袭 (round 1670, objective ①-b).

Previous rounds searched the corpus pages and our own weapons.json and found nothing. The data tree also holds Config/, which
is where a light cone's actual sentence would live -- and "the document does not give it" is not the same as "the data does
not have it".
"""
import io
import os
import re

ROOTS = ["E:/turnbasedgamedata"]
KEYWORDS = ["奇袭", "奇袭结束", "Surprise Attack", "Ambush"]
out = []
scanned = 0
skipped = 0

for root in ROOTS:
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d != ".git"]
        for name in files:
            lower = name.lower()
            if not lower.endswith((".json", ".txt", ".md", ".html", ".csv")):
                continue
            path = os.path.join(base, name)
            try:
                size = os.path.getsize(path)
            except OSError:
                continue
            if size > 80_000_000:
                skipped += 1
                continue
            try:
                body = io.open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            scanned += 1
            for keyword in KEYWORDS:
                if keyword in body:
                    rel = os.path.relpath(path, root)
                    hits = [m.start() for m in re.finditer(re.escape(keyword), body)]
                    out.append("HIT %-64s [%s] x%d" % (rel, keyword, len(hits)))
                    for start in hits[:2]:
                        snippet = re.sub(r"\s+", " ", body[max(0, start - 170):start + 230])
                        out.append("     ...%s..." % snippet[:400])
                    break

header = "scanned %d files, skipped %d huge ones, hits %d" % (scanned, skipped, len([l for l in out if l.startswith("HIT")]))
io.open("tools/_tmp_qixi.txt", "w", encoding="utf-8", newline="\n").write(header + "\n\n" + "\n".join(out))
print(header)
