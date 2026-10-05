"""Corpus probe (round 1652): pin the two remaining objective-① sentences verbatim.

  1. which light cone says 「奇袭结束后…」 (the goal's fifth named reader);
  2. 1505's 【好活当赏】 ending sentence (「结束时转化 50%」 per the goal).

The corpus is the documentation set under E:\\turnbasedgamedata\\aluminium_texts; output goes to a UTF-8 file because this
shell mangles Chinese on stdout.
"""
import io
import glob
import os
import re

ROOT = r"E:\turnbasedgamedata\aluminium_texts"
out = []


def strip(html):
    text = re.sub(r"<[^>]+>", " ", html)
    return re.sub(r"\s+", " ", text)


for path in sorted(glob.glob(os.path.join(ROOT, "*.html"))):
    name = os.path.basename(path)
    try:
        raw = io.open(path, encoding="utf-8", errors="replace").read()
    except Exception:
        continue
    text = strip(raw)
    for keyword in ("奇袭结束后", "好活当赏"):
        for match in re.finditer(re.escape(keyword) + r".{0,120}", text):
            piece = match.group(0)
            if keyword == "好活当赏" and "结束" not in piece and "转化" not in piece:
                continue
            out.append("[%s] %s" % (name, piece))

# dedupe, keep the first 24
seen = set()
lines = []
for line in out:
    key = line[:160]
    if key in seen:
        continue
    seen.add(key)
    lines.append(line)

io.open("tools/_tmp_corpus.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines[:24]))
print("written", len(lines), "hits")
