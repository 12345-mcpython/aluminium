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

# 1) the 121row leaves section 3 (shipped)
P1217 = "| **「缩短【穹命】的持续**时长**」**"
hits = [i for i, l in enumerate(lines) if l.startswith(P1217)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1217" % len(hits))
del lines[hits[0]]

# 2) the 1415 row is rewritten: the prerequisite landed, the sentence itself is what is left
P1415 = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [i for i, l in enumerate(lines) if l.startswith(P1415)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1415" % len(hits))
lines[hits[0]] = (
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 忆灵技能 8 ✓） "
    "| ✅ **前置已满足** ✓：忆灵的技能**已是 `Skill` 对象** ✓（`MemospriteSpec` 带 `servant_id` ✓ 与 `skills` 行 ✓；"
    "`SummonFactory` 用 `DefaultSkill(11415, slot, level)` 建 ✓；`skill_effects.json` 里 `\"11415\": {\"8\": {\"effect\": \"Rules\"}}` ✓）"
    "—— ❗ 现在只差把那句话**逐句写出** ✓（─「解除控制类负面状态」✓、「若处于【血仇】则使其自动施放【弑神登神】」✓、"
    "「否则行动提前 100%」✓） "
    "| `1415`（1 位） "
    "| 写出该句（词都已出货 ✓） |")

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   section 3 now has %d rows" % sum(1 for l in lines if l.startswith("| ") and "位" in l))
