"""Sharpen 1415's row into something measurable (round 27 of the goal).

Measured this round:
  * `SkillExecutor.canDeliver` is exactly `skill.getData().getEffect().isDamaging() || deliverableSpec(skill) != null` (L580) -- so a
    commanded cast is refused only when a skill is BOTH non-damaging AND has no `skill_effects.json` entry.
  * `SkillData.isLoaded()` (`maxLevel > 0`) is the engine's own name for "this is a real row, not the placeholder the loader hands back
    for an id the data lacks". 「键在」 and 「有这技能」 are different facts.
  * `skills.json` is keyed by GAME SKILL ID, and all nine of 1415's rows are HER OWN kit (`141501`…`141519`: 普攻/战技/终结技/天赋/秘技).
    The memosprite's skills are NOT under her cid.
  * `skill_effects.json` (the non-damaging table, which DOES carry `1409` -- another memosprite master) has no 1415 entry.

So the row's "缺什么" becomes a measurement instead of a judgement: where does a summon's skill data live, and does 德谬歌 have a skill 8
row at all.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2b50 \u672c\u8f6e\u628a\u5b83\u4ece\u201c\u5224\u65ad\u201d\u6539\u6210**\u53ef\u6d4b\u7684\u4e00\u6b65** \u2713\uff1a\u2757 **\u8be5\u6280\u80fd\u5728\u6570\u636e\u91cc\u6ca1\u6709\u884c** \u2713 \u2014\u2014 "
    "\u5b9e\u6d4b\uff1a\u2605 `skills.json` \u7684\u952e\u662f**\u6e38\u620f\u6280\u80fd id** \u2713\uff0c\u800c `1415` \u7684**\u4e5d\u4e2a\u884c\u5168\u662f\u5979\u81ea\u5df1\u7684**\uff08`141501`\u2026`141519`\uff1a\u666e\u653b\uff0f\u6218\u6280\uff0f\u7ec8\u7ed3\u6280\uff0f\u5929\u8d4b\uff0f\u79d8\u6280 \u2713\uff09"
    "\uff0c**\u5fc6\u7075\u7684\u6280\u80fd\u4e0d\u5728\u5979\u540d\u4e0b** \u2717\uff1b\u2605 `skill_effects.json`\uff08**\u975e\u4f24\u5bb3**\u8868 \u2713\uff0c\u5b83**\u786e\u5b9e\u5e26** `1409` \u2713 \u2014\u2014 "
    "\u53e6\u4e00\u4f4d\u5fc6\u7075\u4e3b\u4eba \u2713\uff09**\u6ca1\u6709 1415 \u6761\u76ee** \u2717\u3002"
    "\u26a0 \u800c `canDeliver` \u7684\u5224\u636e\u5df2\u91cf\u6e05 \u2713\uff1a`isDamaging() \u2228 deliverableSpec != null` \u2713\uff08`SkillExecutor:580` \u2713\uff09 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u4e00\u6b21\u6d4b\u91cf\uff1a\u2605 **\u5fc6\u7075\u7684\u6280\u80fd\u6570\u636e\u653e\u5728\u54ea** \u2717\uff08\u5b83\u5230\u5e95\u6709\u6ca1\u6709 skill 8 \u8fd9\u4e00\u884c \u2713\uff09 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1415's row is now a measurement")
