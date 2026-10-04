"""Checklist sync for the shipped class filter (round 15 of the goal).

Ships: `debuff_class:control|dot` -- the family of the debuff that just landed, recorded at the one chokepoint every landed debuff
passes through (and already read there for the resistance roll). Two-way judged: the 控制类 rule fires (ATTACK +273.42), the 持续伤害类
rule does not (DEFENCE +0.0), with the landing copied from the green `ControlImmunityTest`.

That closes the biggest blocked row: 1506's warehouse skill needed three things, and the class filter was one. It also makes 1415's
「解除万敌陷入的所有控制类负面状态」 expressible.

What the row still waits on is now only: whether that memosprite skill can be cast at all (its `skill_effects.json` entry), which is a
thing to MEASURE rather than assume.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# §3: the merged row's blocker is gone for the filter, so the row is rewritten around what is actually left
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2b50 \u4e24\u4e2a\u66fe\u7ecf\u7684\u524d\u7f6e**\u90fd\u5df2\u51fa\u8d27** \u2713\uff1a\u201c\u547d\u4ee4\u4ed6\u4eba\u65bd\u653e\u201d \u2713\uff08`CAST_SKILL` \u7684\u65bd\u653e\u8005\u5c31\u662f `target` \u2713\uff0c`1412` \u5728\u7528 \u2713\uff09"
    "\u4e0e\u201c**\u63a7\u5236\u7c7b**\u7b5b\u9009\u201d \u2713\uff08`debuff_class:control` \u2713\uff0c\u672c\u8f6e\u51fa\u8d27 \u2713\uff09\u3002"
    "\u2757 \u771f\u6b63\u5269\u4e0b\u7684\uff1a\u8be5**\u5fc6\u7075\u6280\u80fd 8 \u672c\u8eab\u80fd\u4e0d\u80fd\u88ab\u65bd\u653e** \u2717\uff08\u5b83\u9700\u8981 `skill_effects.json` \u6761\u76ee \u2713\uff09\u2014\u2014 \u26a0 \u8fd9\u4ef6\u8981**\u6d4b**\uff0c\u4e0d\u80fd\u5047\u8bbe \u2713 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u4e00\u6b21\u6d4b\u91cf\uff1a\u8be5\u6280\u80fd\u80fd\u5426\u65bd\u653e \u2713 |")

# §2: the shipped spelling
ANCHOR = "| **\u300c**\u672a**\u5904\u4e8e\u3010X\u3011\u72b6\u6001\u65f6\u2026\u300d**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **\u300c\u65bd\u52a0\u7684\u662f**\u63a7\u5236\u7c7b**\uff0f**\u6301\u7eed\u4f24\u5bb3\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d** "
       "| **`debuff_class:control`**\uff0f**`debuff_class:dot`**\uff08\u521a\u843d\u5730\u7684\u8d1f\u9762\u72b6\u6001\u7684**\u65cf** \u2713\uff1b"
       "\u4e8b\u5b9e\u5728\u54bd\u5589\u70b9\u8bb0\u5f55 \u2713\uff0c\u800c\u90a3\u91cc\u672c\u6765\u5c31\u8bfb\u5b83\u7b97\u6297\u6027 \u2713\uff09 "
       "| `src/main/java/com/laosun/aluminium/Battle.java`\u3001`src/main/java/com/laosun/aluminium/models/TriggerTable.java` "
       "| `DebuffClassConditionTest` |")
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the class filter; the blocked row now names one measurement")
