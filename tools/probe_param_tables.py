"""The parameter tables, in full (round 1671).

The document sentences state only the HEALTH share, but every skill carries a 参数 table, and the truncation I saw
("params [0.5, 1...") may hold a second number -- a speed share. Printing the tables whole for 1409's talent and 1415's
ultimate decides whether the missing number is in the document after all.
"""
import io
import os
import re

CORPUS = "E:/turnbasedgamedata/aluminium_texts"
out = []

for name, marker in (("1409_\u98ce\u5807.md", "\u7597\u6108\u4e16\u95f4\u7684\u6668\u66e6"),
                     ("1415_\u6614\u6d9f.md", "\u8bd7\u7684\u300c\u25e6\u300d\u8a93\u7ea6\u7684\u300c\u221e\u300d")):
    path = os.path.join(CORPUS, name)
    out.append("=== %s ===" % name)
    if not os.path.exists(path):
        out.append("  (missing)")
        continue
    body = io.open(path, encoding="utf-8", errors="replace").read()
    index = body.find(marker)
    if index < 0:
        out.append("  marker not found")
        continue
    chunk = body[index:index + 3000]
    # the sentence, then every parameter row
    for row in re.finditer(r"\|\s*(\d{1,2})\s*\|\s*\[([^\]]*)\]\s*\|", chunk):
        out.append("  L%-3s [%s]" % (row.group(1), row.group(2)))
    sentence = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", chunk[:700]))
    out.append("  sentence: %s" % sentence[:600])

io.open("tools/_tmp_param_tables.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
