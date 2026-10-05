"""Are the 「使<名字>获得」 sentences really cross-character? (round 1675)

Only a cross-character sentence needs a way to name somebody else's character from a rule; a self-reference does not. Prints
each hit with its page, so "which page says it" answers that directly.
"""
import io
import os
import re

CORPUS = "E:/turnbasedgamedata/aluminium_texts"
PATTERNS = ["使昔涟获得", "使风堇获得", "使阿格莱雅获得",
            "使刻律德菈获得", "使灵砂获得", "使景元获得",
            "使托帕获得"]

out = []
for pattern in PATTERNS:
    out.append("=== %s" % pattern)
    found = 0
    for name in sorted(os.listdir(CORPUS)):
        if not name.endswith(".md"):
            continue
        text = re.sub(r"\s+", " ", io.open(os.path.join(CORPUS, name), encoding="utf-8", errors="replace").read())
        index = text.find(pattern)
        if index < 0:
            continue
        out.append("  [%s] ...%s..." % (name, text[max(0, index - 150):index + 160]))
        found += 1
        if found >= 3:
            break
io.open("tools/_tmp_cross_character.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
