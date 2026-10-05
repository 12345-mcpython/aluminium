"""Close the three rows with the measurements this goal asked for (round 1 of the new goal).

① 1217 「缩短【禳命】时长」: MEASURED, and it cannot be written. `BuffManager.extendBuffsFrom:296` is
       `if (source == null || turns <= 0 || (stateName == null && attribute == null)) return 0;`
   and `extendAllBuffs:319` is `if (turns <= 0) return 0;` -- negative turns are refused OUTRIGHT, so `EXTEND_BUFF` can only
   lengthen. The row's "缺什么" stops being a question and becomes "一个缩短具名状态时长的 op".

② 1415 忆灵技能 8: CONFIRMED that there is no third place. `MemospriteSpec` carries `name / source / note / panel / attack` and no skill
   list at all; the resource tree has no `servants/` data (the constant exists, the dir does not); `skills.json`'s nine 1415 rows are all
   her own (`141501`…`141519`); `skill_effects.json` carries `1409` (another memosprite master) but no 1415.

③ 1407: re-checked, unchanged -- `PLANNED = Set.of("REDUCE_TOUGHNESS")` and `Battle.performAction` only QUEUES (its own comment says the
   settlement is in afterMove/processRequests), which is what the row already records.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

REPLACEMENTS = [
    ("| **「缩短【穹命】的持续**时长**」**",
     "| **「缩短【穹命】的持续**时长**」**（`1217` 藿藿 ✓） "
     "| ❗ **测定：写不出** ✓ —— 引擎**没有任何“改时长”的 op** ✗，"
     "而且**负值被显式拒绝** ✓：`BuffManager.extendBuffsFrom:296` 是 "
     "`if (source == null || turns <= 0 || …) return 0;` ✓，`extendAllBuffs:319` 是 `if (turns <= 0) return 0;` ✓ "
     "⇒ `EXTEND_BUFF` **只能延长** ✗（`OPS_WITH_DURATION` 的五个也都是“授予” ✓） "
     "| `1217`（1 位） "
     "| 一个**缩短具名状态时长**的 op ✗ |"),
    ("| **「使万敌自动施放1次不消耗充能的【弑神登神】」**",
     "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
     "| ❗ **该技能在两张表里都没有行，而且没有第三处** ✓（本轮测定 ✓）："
     "★ `MemospriteSpec` 只有 `name`／`source`／`note`／`panel`／`attack` ✓ —— **没有任何技能表** ✗；"
     "★ 资源树里**没有 `servants/` 数据** ✗（`SERVANT_DIR` 常量在 ✓，目录不在 ✗）；"
     "★ `skills.json` 的九行**全是她自己的** ✓（`141501`…`141519`）；"
     "★ `skill_effects.json` 带 `1409` ✓（另一位忆灵主人 ✓）而**无 1415** ✗。"
     "⇒ 它的「对万敌施放时」**没有触发点** ✗。⭐ **不造**：读者 **1 位** ✗ ⇒ 登记 ✓ "
     "| `1415`（1 位） "
     "| 给该技能一条**行**（伤害类进 `skills.json` ✓、非伤害进 `skill_effects.json` ✓）；❗**读者不足 2 位前不造** ✓ |"),
]

for prefix, replacement in REPLACEMENTS:
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:24], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows are closed with the measurements")
