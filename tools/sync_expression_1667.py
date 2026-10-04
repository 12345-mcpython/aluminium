"""Checklist sync (round 1667): the live per-stack modifier is SHIPPED, so it moves from §3 to §2.

§3's row said "14 documents, this round only read the registration". It now has content, a spelling and two judges, so it
belongs in §2 -- and the registered row goes away, because a family that can be written must not sit in the register.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| \u300c**\u6bcf\u5c42 +X%**\u300d\u7684**\u6d3b\u503c\u4fee\u6b63\u5668**"
hits = [i for i, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with the §3 prefix" % len(hits))
lines.pop(hits[0])
print("ok   the registered row is gone")

ROW = ("| **\u6309\u5c42\u6570\u5b9e\u65f6\u7f29\u653e**\uff08\u300c\u6bcf\u62e5\u6709 1 \u5c42\u2026\u63d0\u9ad8 X%\u300d\u7684**\u6301\u7eed\u5149\u73af**\uff09 "
       "| \u4e24\u79cd\u5199\u6cd5\uff1a\u5e73\u5766\u5c5e\u6027\u7528 `per_stack: self_stacks:<NAME>` \uff0b **`per_stack_live: true`** "
       "\uff08\u4efd\u989d = `percent \u00d7 \u5f53\u524d\u5c42\u6570`\uff09\uff1b\u6bd4\u7387\u5c5e\u6027\uff0f\u7edd\u5bf9\u503c\u7528 `scale: self_stacks:<NAME>` \uff0b **`per_stack_live: true`** "
       "\uff08\u7edd\u5bf9\u503c = `percent \u00d7 \u5f53\u524d\u5c42\u6570 + amount`\uff09 "
       "| `src/main/java/com/laosun/aluminium/models/DoubleValue.java`\uff08`livePercent` \uff0f `livePure`\uff09\u3001"
       "`src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java`\u3001`src/main/resources/characters/1314.json` "
       "| `PerStackLiveTest`\u3001`JadeLiveGoodsTest` |")
ANCHOR = "| \u300c\u83b7\u5f97 N \u4e2a**\u7b11\u70b9**\u300d"
target = [i for i, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
print("ok   the §2 row is in")

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
