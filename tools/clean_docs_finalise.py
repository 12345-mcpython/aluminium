"""Fix the pointer by CONTENT match, and insert section 四 (2026-10-02).

The first attempt matched the pointer line literally and found nothing -- the file's line differs in punctuation. Matching on the phrase it contains is what actually works.
"""
import io
import sys

SRC = "GAPS.md"
lines = io.open(SRC, encoding="utf-8").read().split("\n")

# 1) the pointer: any line that talks about 历史流水与逐轮记录
hits = [i for i, l in enumerate(lines) if "历史流水与逐轮记录" in l]
print("pointer lines found: %s" % [i + 1 for i in hits])
POINTER = ("> 逐轮日志（每一次读数、失败与回滚，原文未改）在 "
           "[`GAPS_LOG.md`](GAPS_LOG.md)；更长历史在 [`ROADMAP.md`](ROADMAP.md)；"
           "能力的**语汇手册**是 [`engine.md`](engine.md)。")
if hits:
    lines[hits[0]] = POINTER
else:
    # ⚠ the old pointer was itself a `> ` block, so the log split carried it away -- a fresh one goes right after the title
    lines = lines[:1] + ["", POINTER] + lines[1:]
    print("no pointer left -- a fresh one was inserted after the title")

# 2) section 四, right before section 三 so the numbering stays in order
SECTION = io.open("tools/gaps_section4.md", encoding="utf-8").read().rstrip()
three = [i for i, l in enumerate(lines) if l.startswith("## 三、已改正的过时登记")]
print("section 三 at: %s" % [i + 1 for i in three])
if len(three) != 1:
    sys.exit("REFUSING: found %d section-三 headings" % len(three))
lines = lines[:three[0]] + SECTION.split("\n") + ["", ""] + lines[three[0]:]

io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(lines).rstrip() + "\n")
print("ok   pointer fixed and section 四 inserted")
