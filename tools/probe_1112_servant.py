"""What does 1112's document state about 账账's panel, and what summons it?

Reads her corpus page and prints the talent / technique lines that mention 账账, plus any percentage tied to a panel attribute,
so the servant file can be written from measured numbers instead of guessed ones.
"""
import io
import os
import re

CANDIDATES = ["1112_托帕&账账.html", "1112_托帕.html"]
root = "E:/turnbasedgamedata/aluminium_texts"
path = None
for name in CANDIDATES:
    if os.path.exists(os.path.join(root, name)):
        path = os.path.join(root, name)
        break
if path is None and os.path.isdir(root):
    for name in os.listdir(root):
        if name.startswith("1112"):
            path = os.path.join(root, name)
            break
if path is None:
    raise SystemExit("no 1112 page found")

body = io.open(path, encoding="utf-8", errors="replace").read()
text = re.sub(r"<[^>]+>", " ", body)
text = re.sub(r"&nbsp;?", " ", text)
text = re.sub(r"\s+", " ", text)

out = ["page: %s" % os.path.basename(path)]
for keyword in ("账账", "行迹", "天赋", "秘技", "星魂"):
    hits = [m.start() for m in re.finditer(keyword, text)]
    out.append("\n=== %s (%d hits) ===" % (keyword, len(hits)))
    for start in hits[:6]:
        out.append("  ...%s..." % text[max(0, start - 120):start + 260])
io.open("tools/_tmp_1112.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
