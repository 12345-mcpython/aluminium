"""Checklist sync: the turn-start sentence ships, and what is left is named (round 6 of the goal).

Ships now: 1404's 「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」 -- gate + swap slot 9 + pay the cost + cast, with the swap proven
to reach the commanded cast by a same-level comparison (767.585 both ways; 628.024 without it).

Left, each with its reader and prerequisite:
  * 「充能 150」 needs the ≥100 rule to stop draining the charge (measured: that rule has no 【血仇】 gate);
  * 1415's memosprite skill 8 needs a COMMANDED CAST ON ANOTHER UNIT -- `CAST_SKILL` casts the rule owner's own slot, so a rule in
    another character's table cannot make 万敌 cast.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| **\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f3a\u5316\u6218\u6280\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped half leaves §3")

# the shipped row
ROW = ("| **\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d** "
       "| `TURN_START` \uff0b `self has_state \u8840\u4ec7` \uff0b **`REPLACE_SKILL{skill: SKILL, skill_id: 9, turns: 1}`** \uff0b "
       "`CONSUME_HP{scale: target_current_hp, percent: 0.35}` \uff0b `CAST_SKILL{skill: SKILL}` "
       "| `src/main/resources/characters/1404.json` "
       "| `MydeiBloodfeudSkillsTest` |")
ANCHOR = "| **\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u2026**\u5f53\u524d**\u751f\u547d\u503c X% \u7684\u751f\u547d\u503c\u300d**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
print("ok   §2 gains the turn-start autocast")

# what remains, as its own §3 row
ROW3 = ("| **\u300c\u5145\u80fd 150 \u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d** \u4e0e **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d** "
       "| \u4e24\u4ef6 \u2717\uff1a\u2460 \u300c\u5145\u80fd \u2265 100\u300d\u90a3\u6761**\u6ca1\u6709\u3010\u8840\u4ec7\u3011\u95e8** \u2717 \u21d2 **\u53cd\u590d**\u89e6\u53d1\u3001\u628a\u5145\u80fd\u62bd\u5e72 \u2717"
       "\uff08\u5b9e\u6d4b \u2713\uff09\u21d2 \u6512\u4e0d\u5230 150 \u2717\uff1b"
       "\u2461 \u5728**\u522b\u4eba\u7684\u8868**\u91cc\u547d\u4ee4**\u4ed6**\u65bd\u653e \u2717 \u2014\u2014 `CAST_SKILL` \u653e\u7684\u662f**\u89c4\u5219\u62e5\u6709\u8005\u81ea\u5df1**\u7684\u69fd \u2713\uff08`requireCharacterOwner` \u2713\uff09 "
       "| `1404`\uff081 \u4f4d\uff09\u3001**`1415` \u7684\u5fc6\u7075\u6280\u80fd 8**\uff081 \u4f4d\uff09 **\u5171 2 \u4f4d** \u2713 "
       "| \u2460 \u4e00\u4e2a**\u5426\u5b9a\u6761\u4ef6**\uff08\u5982 `self not_state \u8840\u4ec7`\uff09\uff0c\u6216\u8ba9\u90a3\u6761\u53ea\u5728**\u8de8\u8fc7**\u9608\u503c\u65f6\u89e6\u53d1 \u2713\uff1b"
       "\u2461 \u4e00\u79cd**\u547d\u4ee4\u4ed6\u4eba\u65bd\u653e**\u7684\u5199\u6cd5\uff08`CAST_SKILL` \u52a0\u4e00\u4e2a\u201c\u8c01\u6765\u653e\u201d\u7684\u5bfb\u5740 \u2713\uff09 |")
lines.append(ROW3)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §3 names the two remaining blockers")
