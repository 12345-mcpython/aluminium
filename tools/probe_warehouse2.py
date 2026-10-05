"""The two warehouse skills' actual effects, and the config the data names (round 8 of the goal).

The index says exactly two characters have one: 1407 (月茧之庇) and 1506 (999安全卫士). The pages also name the mechanism:
`AvatarGlobalBuffConfig`. So both halves are asked of the data rather than guessed.
"""
import io
import os
import re

corpus = "E:/turnbasedgamedata/aluminium_texts"
out = []

for cid in ("1407", "1506"):
    pages = [name for name in sorted(os.listdir(corpus)) if name.startswith(cid) and name.endswith(".md")]
    out.append("=== cid %s pages: %s ===" % (cid, pages))
    for name in pages:
        flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", io.open(
            os.path.join(corpus, name), encoding="utf-8", errors="replace").read()))
        for match in list(re.finditer("仓库技", flat))[:3]:
            out.append("   [%s] ...%s..." % (name, flat[max(0, match.start() - 200):match.start() + 420]))
    out.append("")

# the config the pages name, anywhere under the data tree
root = "E:/turnbasedgamedata"
found = []
scanned = 0
for base, dirs, files in os.walk(root):
    for name in files:
        if not name.lower().endswith((".json", ".md", ".txt", ".csv", ".html")):
            continue
        path = os.path.join(base, name)
        try:
            body = io.open(path, encoding="utf-8", errors="replace").read()
        except Exception:
            continue
        scanned += 1
        if "AvatarGlobalBuffConfig" in body:
            flat = re.sub(r"\s+", " ", body)
            match = re.search("AvatarGlobalBuffConfig", flat)
            found.append("FILE %s\n     ...%s..." % (path.replace("\\", "/").replace(root, ""),
                                                     flat[max(0, match.start() - 200):match.start() + 300]))
            if len(found) >= 3:
                break
    if len(found) >= 3:
        break
out.append("=== AvatarGlobalBuffConfig under the data tree: %d hits (scanned %d) ===" % (len(found), scanned))
out.extend(found)
io.open("tools/_probe_warehouse2.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
