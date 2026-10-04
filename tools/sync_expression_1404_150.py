"""Checklist sync: the 150-charge sentence ships, the negation gets a row, and one piece remains (round 7 of the goal).

Ships:
  * 1404's 「【血仇】状态期间充能达到 150 点时，万敌立即获得 1 个额外回合并自动施放【弑神登神】」 -- spend 150, extra turn, swap slot 11, cast.
    Readings: charge 0, the enemy takes 1953.85 (against 767.585 for the plain row, i.e. 110% of max HP), and the slot reads 11.
  * the ≥100 rule's gate `!self has_state 血仇`. Measured before it: the charge arriving in 【血仇】 was drained again, so 150 was
    unreachable and the sentence above could never fire.
  * the negation spelling itself is worth a §2 row: it was ALREADY supported (the `!` prefix is parsed, and `has_state` reads the
    unit's buffs, which is the negatable family) -- but nothing in the tree used it until now.

Remains (one piece, one reader): 1415's memosprite skill 8 needs a COMMANDED CAST ON ANOTHER UNIT.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| **\u300c\u5145\u80fd 150 \u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped sentence leaves §3")

ANCHOR = "| **\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROWS = [
    ("| **\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u5145\u80fd\u8fbe\u5230 **150** \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d** "
     "| `SPEND_RESOURCE{150}` \uff0b `EXTRA_TURN` \uff0b **`REPLACE_SKILL{skill: SKILL, skill_id: 11, turns: 1}`** \uff0b "
     "`CAST_SKILL{skill: SKILL, target: self}` "
     "| `src/main/resources/characters/1404.json` "
     "| `MydeiBloodfeudSkillsTest` |"),
    ("| **\u300c**\u672a**\u5904\u4e8e\u3010X\u3011\u72b6\u6001\u65f6\u2026\u300d**\uff08\u5426\u5b9a\u4e00\u4e2a\u72b6\u6001\u6761\u4ef6\uff09 "
     "| **`!self has_state X`**\uff08`!` \u524d\u7f00 \u2713\uff0c\u53ea\u5141\u8bb8\u5426\u5b9a `PartyCondition` \u2713\uff0c\u800c `has_state` \u8bfb\u7684\u5c31\u662f\u5355\u4f4d\u7684\u589e\u76ca \u2713\uff09 "
     "| `src/main/resources/characters/1404.json` "
     "| `MydeiBloodfeudSkillsTest` |"),
]
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   §2 gains the 150-charge sentence and the negation spelling")

ROW3 = ("| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
        "| \u5728**\u522b\u4eba\u7684\u8868**\u91cc\u547d\u4ee4**\u4ed6**\u65bd\u653e \u2717 \u2014\u2014 `CAST_SKILL` \u653e\u7684\u662f**\u89c4\u5219\u62e5\u6709\u8005\u81ea\u5df1**\u7684\u69fd \u2713"
        "\uff08`requireCharacterOwner` \u2713\uff1b\u5b9e\u6d4b\uff1a\u628a `target` \u5199\u6210\u4e8b\u4ef6\u7684\u76ee\u6807\u65f6\u5b83\u4f1a\u547d\u4ee4**\u654c\u4eba**\u65bd\u653e \u2717 \u2014\u2014 \u88c5\u8f7d\u5668\u62a5 *\u201c\u51b0\u950b has no SKILL skill\u201d* \u2713\uff09 "
        "| `1415`\uff081 \u4f4d\uff09 "
        "| \u4e00\u79cd**\u547d\u4ee4\u4ed6\u4eba\u65bd\u653e**\u7684\u5199\u6cd5\uff08`CAST_SKILL` \u52a0\u4e00\u4e2a\u201c\u8c01\u6765\u653e\u201d\u7684\u5bfb\u5740 \u2713\uff09 |")
lines.append(ROW3)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §3 keeps the one remaining piece with its reader")
