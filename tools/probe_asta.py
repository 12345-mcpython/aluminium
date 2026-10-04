"""Find Asta's id and her sentence, and list the modifier-attach sites (round 1663).

Console is GBK, so everything goes to a UTF-8 file that the read tool can print.
"""
import io
import json
import os
import re

out = []

# 1. which character is 艾丝妲, and what does her text say around 蓄能?
data = io.open("src/main/resources/data/character_data.json", encoding="utf-8").read()
for match in re.finditer(r'"(\d{4})"\s*:\s*\{(.{0,600}?)"', data, re.S):
    if "\u827e\u4e1d\u5992" in match.group(2):
        out.append("id near 艾丝妲: %s" % match.group(1))
        break
for hit in [m.start() for m in re.finditer("\u84c4\u80fd", data)][:3]:
    out.append("CONTEXT ...%s..." % data[max(0, hit - 160):hit + 200].replace("\n", " "))
out.append("occurrences of 蓄能 in character_data: %d" % data.count("\u84c4\u80fd"))

# 2. which of our character files mention it?
for path in sorted(os.listdir("src/main/resources/characters")):
    if not path.endswith(".json"):
        continue
    text = io.open(os.path.join("src/main/resources/characters", path), encoding="utf-8").read()
    if "\u84c4\u80fd" in text:
        out.append("character file mentioning 蓄能: %s" % path)

# 3. which corpus documents say 每拥有 1 层 (the family)
corpus = None
for root in ("E:/turnbasedgamedata", "E:/turnbasedgamedata/aluminium_texts"):
    if os.path.isdir(root):
        corpus = root
        break
if corpus:
    hits = []
    for name in os.listdir(corpus):
        if name.endswith(".html"):
            body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
            if "\u6bcf\u62e5\u6709" in body and "\u5c42" in body:
                hits.append(name)
    out.append("corpus files with 每拥有 … 层: %d" % len(hits))
    out.append("  " + ", ".join(hits[:14]))
else:
    out.append("corpus root not found")

io.open("tools/_tmp_asta.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
