"""Split the live-aura row in two: the guard wants ONE judge per §2 row, and the two forms have different judges.

Flat attribute -> per_stack + per_stack_live (PerStackLiveTest); ratio/absolute -> scale + per_stack_live (JadeLiveGoodsTest,
which runs 1314's real file).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

BAD = "| **按层数实时缩放**"
hits = [i for i, line in enumerate(lines) if line.startswith(BAD)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows to replace" % len(hits))
lines.pop(hits[0])

ROWS = [
    ("| **按层数实时缩放（平坦属性）**（「每拥有 1 层…提高 X%」的持续光环） "
     "| `MODIFY_ATTR` 上 `per_stack: self_stacks:<NAME>` ＋ **`per_stack_live: true`**（份额 = `percent × 当前层数`，**每次读取重算**） "
     "| `src/main/java/com/laosun/aluminium/models/DoubleValue.java`、`src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java` "
     "| `PerStackLiveTest` |"),
    ("| **按层数实时缩放（比率／绝对值）**（同一族的派生写法） "
     "| `MODIFY_ATTR` 上 `scale: self_stacks:<NAME>` ＋ **`per_stack_live: true`**（绝对值 = `percent × 当前层数 + amount`） "
     "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1314.json` "
     "| `JadeLiveGoodsTest` |"),
]
ANCHOR = "| 「获得 N 个**笑点**」"
target = [i for i, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   two rows, one judge each")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
