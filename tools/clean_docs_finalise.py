"""Fix the pointer by CONTENT match, and insert section 四 (2026-10-02).

The first attempt matched the pointer line literally and found nothing -- the file's line differs in punctuation. Matching on the phrase it contains is what actually works.
"""
import io
import sys

SRC = "GAPS.md"
lines = io.open(SRC, encoding="utf-8").read().split("\n")

# 1) the pointer: any line that talks about 历史流水与逐轮记录
hits = [i for i, l in enumerate(lines) if "\u5386\u53f2\u6d41\u6c34\u4e0e\u9010\u8f6e\u8bb0\u5f55" in l]
print("pointer lines found: %s" % [i + 1 for i in hits])
POINTER = ("> \u9010\u8f6e\u65e5\u5fd7\uff08\u6bcf\u4e00\u6b21\u8bfb\u6570\u3001\u5931\u8d25\u4e0e\u56de\u6eda\uff0c\u539f\u6587\u672a\u6539\uff09\u5728 "
           "[`GAPS_LOG.md`](GAPS_LOG.md)\uff1b\u66f4\u957f\u5386\u53f2\u5728 [`ROADMAP.md`](ROADMAP.md)\uff1b"
           "\u80fd\u529b\u7684**\u8bed\u6c47\u624b\u518c**\u662f [`engine.md`](engine.md)\u3002")
if hits:
    lines[hits[0]] = POINTER
else:
    # \u26a0 the old pointer was itself a `> ` block, so the log split carried it away -- a fresh one goes right after the title
    lines = lines[:1] + ["", POINTER] + lines[1:]
    print("no pointer left -- a fresh one was inserted after the title")

# 2) section 四, right before section 三 so the numbering stays in order
SECTION = io.open("tools/gaps_section4.md", encoding="utf-8").read().rstrip()
three = [i for i, l in enumerate(lines) if l.startswith("## \u4e09\u3001\u5df2\u6539\u6b63\u7684\u8fc7\u65f6\u767b\u8bb0")]
print("section 三 at: %s" % [i + 1 for i in three])
if len(three) != 1:
    sys.exit("REFUSING: found %d section-三 headings" % len(three))
lines = lines[:three[0]] + SECTION.split("\n") + ["", ""] + lines[three[0]:]

io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(lines).rstrip() + "\n")
print("ok   pointer fixed and section 四 inserted")
