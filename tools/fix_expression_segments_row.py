"""Doc-only fix: the segment-count row of EXPRESSION.md §3, corrected to what the tree actually says.

Measured this round:
  * the readers are 8009 and 8010 (their own notes carry the sentence 「8 次随机单体 20% + 最后 60% 均分」, data row
    `[8, 0.1, 0.3]` with the leading column being a HIT COUNT) -- NOT the five the row claimed: 1505/1502/1506 register
    other things entirely, so the count was never read;
  * the engine's "N hits" is read per skill KIND (e.g. `BOUNCE` takes the SECOND param), so a row that STARTS with a hit
    count has no representation;
  * and our own `data/` carries no elation skill row at all (scan: 0).

Line-based matching on purpose: the row contains Chinese full-width brackets and straight ASCII quotes, and a literal
substring built inside a shell one-liner kept failing.
"""
import io

PATH = "EXPRESSION.md"
PREFIX = "| \u89d2\u8272\u6280\u80fd\u300c\u6309\u5217\u4f4d\uff0f\u6bb5\u6570\u7ed3\u7b97\u300d"
NEW = ("| \u6b22\u4e50\u6280\u300c**8 \u6b21**\u968f\u673a\u5355\u4f53\u2026\u300d\u7684**\u6309\u6b21\u6570\u7ed3\u7b97** "
       "| \u5f15\u64ce\u7684\u201cN \u6b21\u547d\u4e2d\u201d\u662f**\u6309\u6280\u80fd\u79cd\u7c7b**\u8bfb\u7684\uff08\u5982 `BOUNCE` \u53d6**\u7b2c\u4e8c\u4e2a**\u53c2\u6570 \u2713\uff09\uff0c"
       "\u800c**\u6b22\u4e50\u6280\u7684\u884c\u9996\u5217\u5c31\u662f\u6b21\u6570**\u8fd9\u4e00\u79cd\u6ca1\u6709\u8868\u793a \u2717\uff1b\u26a0 \u53e6\u7f3a\uff1a"
       "**\u6211\u65b9 `data/` \u91cc\u4e00\u4e2a elation \u6280\u80fd\u884c\u90fd\u6ca1\u6709**\uff08\u626b\u5230 0 \u4e2a \u2717\uff09 "
       "| `8009`\uff0f`8010`\uff08**2 \u4f4d**\uff0c\u9010\u6761\u8bfb\u5230 \u2713\uff1b\u26a0 \u539f\u767b\u8bb0\u5199 5 \u4f4d\u800c "
       "`1505`\uff0f`1502`\uff0f`1506` **\u9010\u6761\u8bfb\u4e0d\u5230** \u2717 \u21d2 \u6309\u672c\u8282\u89c4\u77e9\u6539\u4e3a 2 \u4f4d\uff09 "
       "| \u6280\u80fd\u884c\u6a21\u578b \uff0b \u6b22\u4e50\u6280\u7684\u6570\u636e\u884c |")

lines = io.open(PATH, encoding="utf-8").read().split("\n")
hits = [i for i, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    raise SystemExit("REFUSING: %d lines start with the old row" % len(hits))
lines[hits[0]] = NEW
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the segment-count row now states the measured readers (2) and the real missing piece")
