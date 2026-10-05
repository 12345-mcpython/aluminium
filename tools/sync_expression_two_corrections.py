"""Two corrections from the measurements this goal asked for (round 1 of the new goal).

① 1217: the 「持续回合数减 1」 family is NOT a shortening mechanic. Eighteen documents state it, and reading any of them shows why they
   matched: 「施放战技后藿藿获得【禳命】，持续 2 回合，**藿藿每回合开始时**持续回合数减1」 -- that is the normal TICK of a timed buff, which
   `APPLY_BUFF`'s `turns` already expresses. What is actually missing is 星魂 2's EXTRA reduction (「…使【禳命】的持续回合数减 1」, on top of
   the tick), and that has ONE reader. The hard evidence for "cannot be written": `BuffManager.extendBuffsFrom:296` and `extendAllBuffs:319`
   both refuse `turns <= 0`.

② 1415: the memosprite DOES have its own data home -- `ServantID 11415` -- and its skills are numbered 1…18. 忆灵技能 8 is
   「献予「纷争」之诗 / Ode to Strife」, 效果 `辅助`, 元素 无, with 效果ID `[10000001, 10000011]`. Our two tables have no `11415` key at all, so the
   gap is an IMPORT gap, not the engine's inability to hold a rules-only skill (which is what the row used to claim).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

REPLACEMENTS = [
    ("| **「缩短【穹命】的持续**时长**」**",
     "| **「缩短【穹命】的持续**时长**」**（`1217` 藿藿 星魂 2 ✓） "
     "| ⭐ **订正（本轮测定 ✓）**：「持续回合数减 1」这一族（**18 篇** ✓）"
     "**不是缩短机制** ✗ —— 读任一篇就知道：「施放战技后藿藿获得【穹命】，**持续 2 回合**，"
     "**藿藿每回合开始时**持续回合数减1」✓ —— 那是**定时 buff 的跳数** ✓，`APPLY_BUFF` 的 `turns` 本来就表达 ✓。"
     "❗ 真正缺的是**星魂 2 的“额外再减 1”** ✗（「…使【穹命】的持续回合数减 1」，跳数之外 ✓），"
     "**读者 1 位** ✗。🛡 “写不出”的硬证据："
     "`BuffManager.extendBuffsFrom:296` 与 `extendAllBuffs:319` **都拒绝** `turns <= 0` ✓ "
     "| `1217`（1 位） "
     "| 一个**缩短具名状态时长**的 op ✗；❗**读者不足 2 位前不造** ✓ |"),
    ("| **「使万敌自动施放1次不消耗充能的【弑神登神】」**",
     "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 忆灵技能 8 ✓） "
     "| ⭐ **订正（本轮测定 ✓）**：忆灵**有自己的数据位置** ✓ —— `ServantID` **11415** ✓，"
     "技能编号 **1…18** ✓。该技能＝**献予「纷争」之诗 / Ode to Strife** ✓，"
     "效果 `辅助`、元素 `无`、最高等级 10、**效果ID `[10000001, 10000011]`** ✓。"
     "而我们两张表里**没有 `11415` 键** ✗ ⇒ ❗ 缺的是**导入** ✗，"
     "而不是“引擎放不下一个只触发规则的技能” ✗（后者是本行原先的说法 ✗）—— "
     "引擎的机制是“**主人 cid ＋ 槽位**” ✓（`1409` 就是例子 ✓），它能承载 ✓。"
     "⭐ **不造**：读者 **1 位** ✗ ⇒ 登记 ✓ "
     "| `1415`（1 位） "
     "| 把忆灵（`ServantID 11415` ✓）的技能行导入我们的数据；❗**读者不足 2 位前不造** ✓ |"),
]

for prefix, replacement in REPLACEMENTS:
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:24], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows carry the corrections")
