"""Checklist sync (round 1689): what shipped has to leave the register.

§3 currently still lists 「队友持有的【好活当赏】结束时取 50%」 as registered -- it shipped in round 1687, with an end-to-end judge.
That row moves to §2, and the two spellings it needed join it (a party counter as a magnitude, and N instances of a stackable
state), each with the judge that proves it.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# 1. the shipped row leaves §3
SHIPPED = "| 「队友持有的【好活当赏】"
hits = [index for index, line in enumerate(lines) if line.startswith(SHIPPED)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows in §3 start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped row is out of §3")

# 2. §2 gains the three rows it needed, after the 笑点 row
ROWS = [
    ("| **队友的状态结束时取一部分**（「其中的 50% 转化为自身的…」） "
     "| `STATE_ENDED` ＋ **事件携带的量**（被结束状态的**实例数**）＋ `amountFromEvent` × `amountPercent` "
     "| `src/main/resources/characters/1505.json`、`src/main/resources/characters/1513.json` | `GiftCarriesTheLaughsTest`、`KaiBuBaiTest` |"),
    ("| **层数＝队级计数**（「将本次…计入该状态」） "
     "| `scale: \"party_resource:<NAME>\"`（战斗级计数当数值读） | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java` "
     "| `PartyResourceScaleTest` |"),
    ("| **一次施加 N 个可叠加状态**（「层数＝某个数」的另一半） "
     "| `APPLY_BUFF` ＋ `stackable: true` ＋ **`max_stacks`** ＋ `amount`／`scale` "
     "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1513.json` "
     "| `StackTimesTest` |"),
]
ANCHOR = "| 「获得 N 个**笑点**」"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   %d rows are in §2" % len(ROWS))

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
