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
SHIPPED = "| \u300c\u961f\u53cb\u6301\u6709\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011"
hits = [index for index, line in enumerate(lines) if line.startswith(SHIPPED)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows in §3 start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped row is out of §3")

# 2. §2 gains the three rows it needed, after the 笑点 row
ROWS = [
    ("| **\u961f\u53cb\u7684\u72b6\u6001\u7ed3\u675f\u65f6\u53d6\u4e00\u90e8\u5206**\uff08\u300c\u5176\u4e2d\u7684 50% \u8f6c\u5316\u4e3a\u81ea\u8eab\u7684\u2026\u300d\uff09 "
     "| `STATE_ENDED` \uff0b **\u4e8b\u4ef6\u643a\u5e26\u7684\u91cf**\uff08\u88ab\u7ed3\u675f\u72b6\u6001\u7684**\u5b9e\u4f8b\u6570**\uff09\uff0b `amountFromEvent` \u00d7 `amountPercent` "
     "| `src/main/resources/characters/1505.json`\u3001`src/main/resources/characters/1513.json` | `GiftCarriesTheLaughsTest`\u3001`KaiBuBaiTest` |"),
    ("| **\u5c42\u6570\uff1d\u961f\u7ea7\u8ba1\u6570**\uff08\u300c\u5c06\u672c\u6b21\u2026\u8ba1\u5165\u8be5\u72b6\u6001\u300d\uff09 "
     "| `scale: \"party_resource:<NAME>\"`\uff08\u6218\u6597\u7ea7\u8ba1\u6570\u5f53\u6570\u503c\u8bfb\uff09 | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java` "
     "| `PartyResourceScaleTest` |"),
    ("| **\u4e00\u6b21\u65bd\u52a0 N \u4e2a\u53ef\u53e0\u52a0\u72b6\u6001**\uff08\u300c\u5c42\u6570\uff1d\u67d0\u4e2a\u6570\u300d\u7684\u53e6\u4e00\u534a\uff09 "
     "| `APPLY_BUFF` \uff0b `stackable: true` \uff0b **`max_stacks`** \uff0b `amount`\uff0f`scale` "
     "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`\u3001`src/main/resources/characters/1513.json` "
     "| `StackTimesTest` |"),
]
ANCHOR = "| \u300c\u83b7\u5f97 N \u4e2a**\u7b11\u70b9**\u300d"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   %d rows are in §2" % len(ROWS))

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
