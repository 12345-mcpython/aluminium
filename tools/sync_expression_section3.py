"""Bring section 3 back in line with the tree (2026-10-02).

Measured just now: section 3 still lists three families, but two of them no longer belong there:
  * 1217's 「缩短【禳命】时长」 SHIPPED (the signed `EXTEND_BUFF`, judged by `HuohuoTalismanLosesATurnTest`), so the row comes out --
    section 3 is for families that cannot be written yet.
  * 1415's row said the memosprite's skills are not `Skill` objects. They are now (`MemospriteSkillTest`: cid 11415, slot 8,
    canDeliver = true), so the row is rewritten around what is actually left: writing the sentence itself.
  * 1407's row stays: the action-wide lethal selector is still missing.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# 1) the 1217 row leaves section 3 (shipped)
P1217 = "| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**"
hits = [i for i, l in enumerate(lines) if l.startswith(P1217)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1217" % len(hits))
del lines[hits[0]]

# 2) the 1415 row is rewritten: the prerequisite landed, the sentence itself is what is left
P1415 = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [i for i, l in enumerate(lines) if l.startswith(P1415)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1415" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2705 **\u524d\u7f6e\u5df2\u6ee1\u8db3** \u2713\uff1a\u5fc6\u7075\u7684\u6280\u80fd**\u5df2\u662f `Skill` \u5bf9\u8c61** \u2713\uff08`MemospriteSpec` \u5e26 `servant_id` \u2713 \u4e0e `skills` \u884c \u2713\uff1b"
    "`SummonFactory` \u7528 `DefaultSkill(11415, slot, level)` \u5efa \u2713\uff1b`skill_effects.json` \u91cc `\"11415\": {\"8\": {\"effect\": \"Rules\"}}` \u2713\uff09"
    "\u2014\u2014 \u2757 \u73b0\u5728\u53ea\u5dee\u628a\u90a3\u53e5\u8bdd**\u9010\u53e5\u5199\u51fa** \u2713\uff08\u2500\u300c\u89e3\u9664\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u300d\u2713\u3001\u300c\u82e5\u5904\u4e8e\u3010\u8840\u4ec7\u3011\u5219\u4f7f\u5176\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d\u2713\u3001"
    "\u300c\u5426\u5219\u884c\u52a8\u63d0\u524d 100%\u300d\u2713\uff09 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u5199\u51fa\u8be5\u53e5\uff08\u8bcd\u90fd\u5df2\u51fa\u8d27 \u2713\uff09 |")

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   section 3 now has %d rows" % sum(1 for l in lines if l.startswith("| ") and "\u4f4d" in l))
