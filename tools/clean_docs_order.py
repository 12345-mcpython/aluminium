"""Put the new section after section 三, and measure whether the OTHER docs drifted the same way (2026-10-02)."""
import io
import sys

SRC = "GAPS.md"
lines = io.open(SRC, encoding="utf-8").read().split("\n")

FOUR = "## 四、本次弧"
THREE = "## 三、已改正的过时登记"
APPENDIX = "## 附："

def find(prefix, start=0):
    for i in range(start, len(lines)):
        if lines[i].startswith(prefix):
            return i
    return -1

four = find(FOUR)
three = find(THREE)
app = find(APPENDIX)
print("sections at: 四=%d 三=%d 附=%d" % (four + 1, three + 1, app + 1))
if min(four, three, app) < 0 or not (four < three < app):
    sys.exit("REFUSING: the section order is not the expected four < three < appendix")

# lift 四 out and drop it in after 三 (i.e. just before the appendix)
section = lines[four:three]
rest = lines[:four] + lines[three:]
app2 = None
for i, l in enumerate(rest):
    if l.startswith(APPENDIX):
        app2 = i
        break
if app2 is None:
    sys.exit("REFUSING: appendix not found after the move")
out = rest[:app2] + section + rest[app2:]
io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(out).rstrip() + "\n")
print("section 四 is now after 三, before the appendix")

# ---- and how much drift do the other docs carry? ----
for name in ("engine.md", "ROADMAP.md", "HANDOFF.md", "EXPRESSION.md", "README.md"):
    try:
        txt = io.open(name, encoding="utf-8").read()
    except FileNotFoundError:
        continue
    lines_n = txt.count("\n") + 1
    blocks = sum(1 for l in txt.split("\n") if l.startswith("> **20"))
    heads = sum(1 for l in txt.split("\n") if l.startswith("#"))
    print("%-14s %8d lines  %6d bytes  %5d block-quotes  %4d headings"
          % (name, lines_n, len(txt.encode("utf-8")), blocks, heads))
