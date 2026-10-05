"""Item 1 is done the way the goal allows: written where it could be, registered where it could not (2026-10-02).

Five branches, measured one by one:
  1. 血仇 ⇒ 不消耗充能的【弑神登神】        SHIPPED (commit 2651219a; judge OdeToStrifeBloodfeudTest)
  2. 不在血仇 ⇒ 行动提前 100%              SHIPPED (commit 325bbd78; judge OdeToStrifeAdvanceTest)
  3. 本次攻击中暴击伤害 +200%              SHIPPED (commit c2717ec6; judge OdeToStrifeCritTest)
  4. 解除控制类负面状态                    REGISTERED: DISPEL carries only an amount, no class filter
  5. 施放前目标被消灭 ⇒ 对新入场敌方目标    REGISTERED: target_else_random_enemy picks ANY enemy, not a newly-arrived one

So section 3's row for 1415 stops saying the sentence is waiting to be written, and says what is measured: three fifths shipped, two
fifths registered with the exact missing piece.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [i for i, l in enumerate(lines) if l.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1415" % len(hits))
lines[hits[0]] = (
    "| **`1415` 忆灵技能 8 「献予「纷争」之诗」的五句**（`1415` ✓） "
    "| ✅ **三句已出货** ✓：① 血仇⇒使其自动施放不消耗充能的【弑神登神】 ✓；"
    "② **不**处于血仇⇒行动提前 100% ✓（否定用现成的 `!` ✓）；"
    "③ 本次攻击中暴击伤害 +200% ✓（`MODIFY_ATTR{CRIT_ATTACK, percent: 2.0, until: \"cast_end\"}` ✓）。"
    "❗ **两句已登记** ✓：④ 解除**控制类**负面状态 ✗（`DISPEL` 只有 `amount` ✗，"
    "无类别过滤 ✗，而 `target_when` 是**选单位**的 ✗、不等价 ✗）；"
    "⑤ 若施放前目标被消灭则对**新入场**敌方目标施放 ✗"
    "（`target_else_random_enemy` 选的是**任意**敌人 ✗，不是新入场的 ✗）。"
    "| `1415`（1 位） "
    "| ④ 给 `DISPEL` 一个**类别过滤**；⑤ 一个“**新入场的敌方目标**”选择器 "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   section 3 says what is measured for 1415")
