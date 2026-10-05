"""Checklist sync (round 1667): the live per-stack modifier is SHIPPED, so it moves from §3 to §2.

§3's row said "14 documents, this round only read the registration". It now has content, a spelling and two judges, so it
belongs in §2 -- and the registered row goes away, because a family that can be written must not sit in the register.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| 「**每层 +X%**」的**活值修正器**"
hits = [i for i, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with the §3 prefix" % len(hits))
lines.pop(hits[0])
print("ok   the registered row is gone")

ROW = ("| **按层数实时缩放**（「每拥有 1 层…提高 X%」的**持续光环**） "
       "| 两种写法：平坦属性用 `per_stack: self_stacks:<NAME>` ＋ **`per_stack_live: true`** "
       "（份额 = `percent × 当前层数`）；比率属性／绝对值用 `scale: self_stacks:<NAME>` ＋ **`per_stack_live: true`** "
       "（绝对值 = `percent × 当前层数 + amount`） "
       "| `src/main/java/com/laosun/aluminium/models/DoubleValue.java`（`livePercent` ／ `livePure`）、"
       "`src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java`、`src/main/resources/characters/1314.json` "
       "| `PerStackLiveTest`、`JadeLiveGoodsTest` |")
ANCHOR = "| 「获得 N 个**笑点**」"
target = [i for i, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
print("ok   the §2 row is in")

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
