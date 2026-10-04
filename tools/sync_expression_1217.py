"""The stale §3 row becomes a §2 row (2026-10-02, round 2 of the goal).

§3 said 1217's 「使【禳命】的持续回合数 −1」 needs "a shorten-duration spelling, because EXTEND_BUFF only takes positive turns".
Measured: the clause is not about shortening at all -- 「藿藿**每回合开始时**持续回合数减 1」 is about WHOSE CLOCK spends the duration, and
`ticks_on: "self"` already says it (the same field 8006's 伴舞, 星期日's 蒙福者 and the 结界 half use).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| \u300c\u4f7f\u3010\u79b3\u547d\u3011\u7684\u6301\u7eed\u56de\u5408\u6570"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the stale row is out of §3")

ROW = ("| **\u300c\u67d0\u4e2a\u72b6\u6001\u7684\u6301\u7eed\u56de\u5408\u6570\u5728**\u5b83\u81ea\u5df1**\u7684\u56de\u5408\u5f00\u59cb\u65f6\u51cf 1\u300d** "
       "| `APPLY_BUFF` \uff0b `turns` \uff0b **`ticks_on: \"self\"`**\uff08\u8c01\u7684\u56de\u5408\u82b1\u6389\u65f6\u957f \u2713\uff09 "
       "| `src/main/resources/characters/1217.json` "
       "| `HuohuoTalismanDurationTest` |")
ANCHOR = "| **\u961f\u53cb\u7684\u72b6\u6001\u7ed3\u675f\u65f6\u53d6\u4e00\u90e8\u5206**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   it is a §2 row now")
