"""Split the live-aura row in two: the guard wants ONE judge per §2 row, and the two forms have different judges.

Flat attribute -> per_stack + per_stack_live (PerStackLiveTest); ratio/absolute -> scale + per_stack_live (JadeLiveGoodsTest,
which runs 1314's real file).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

BAD = "| **\u6309\u5c42\u6570\u5b9e\u65f6\u7f29\u653e**"
hits = [i for i, line in enumerate(lines) if line.startswith(BAD)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows to replace" % len(hits))
lines.pop(hits[0])

ROWS = [
    ("| **\u6309\u5c42\u6570\u5b9e\u65f6\u7f29\u653e\uff08\u5e73\u5766\u5c5e\u6027\uff09**\uff08\u300c\u6bcf\u62e5\u6709 1 \u5c42\u2026\u63d0\u9ad8 X%\u300d\u7684\u6301\u7eed\u5149\u73af\uff09 "
     "| `MODIFY_ATTR` \u4e0a `per_stack: self_stacks:<NAME>` \uff0b **`per_stack_live: true`**\uff08\u4efd\u989d = `percent \u00d7 \u5f53\u524d\u5c42\u6570`\uff0c**\u6bcf\u6b21\u8bfb\u53d6\u91cd\u7b97**\uff09 "
     "| `src/main/java/com/laosun/aluminium/models/DoubleValue.java`\u3001`src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java` "
     "| `PerStackLiveTest` |"),
    ("| **\u6309\u5c42\u6570\u5b9e\u65f6\u7f29\u653e\uff08\u6bd4\u7387\uff0f\u7edd\u5bf9\u503c\uff09**\uff08\u540c\u4e00\u65cf\u7684\u6d3e\u751f\u5199\u6cd5\uff09 "
     "| `MODIFY_ATTR` \u4e0a `scale: self_stacks:<NAME>` \uff0b **`per_stack_live: true`**\uff08\u7edd\u5bf9\u503c = `percent \u00d7 \u5f53\u524d\u5c42\u6570 + amount`\uff09 "
     "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`\u3001`src/main/resources/characters/1314.json` "
     "| `JadeLiveGoodsTest` |"),
]
ANCHOR = "| \u300c\u83b7\u5f97 N \u4e2a**\u7b11\u70b9**\u300d"
target = [i for i, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   two rows, one judge each")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
