"""Are the 「使<名字>获得」 sentences really cross-character? (round 1675)

Only a cross-character sentence needs a way to name somebody else's character from a rule; a self-reference does not. Prints
each hit with its page, so "which page says it" answers that directly.
"""
import io
import os
import re

CORPUS = "E:/turnbasedgamedata/aluminium_texts"
PATTERNS = ["\u4f7f\u6614\u6d9f\u83b7\u5f97", "\u4f7f\u98ce\u5807\u83b7\u5f97", "\u4f7f\u963f\u683c\u83b1\u96c5\u83b7\u5f97",
            "\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97", "\u4f7f\u7075\u7802\u83b7\u5f97", "\u4f7f\u666f\u5143\u83b7\u5f97",
            "\u4f7f\u6258\u5e15\u83b7\u5f97"]

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
