"""Item 4's registration was too pessimistic; this corrects it to what was measured (2026-10-02).

What section 3 said: 「1407 月茇之庇」 needs a five-part capability (an action boundary, the in-action victims, an action-end
announcement, a selector, a limit).

What the tree says:
  * `TURN_START` is fired at Battle.java:1031 and `TURN_END` at Battle.java:1301, while `performAction` is at :1245 and `afterMove` at
    :1276 -- so the two events ALREADY bracket the action, and the "action boundary" piece exists.
  * the limit machinery exists too: 1217's own clause uses the `救主计数` counter.
  * what is genuinely missing is ONE concept -- the set of units that took a lethal blow during the current action -- plus the selector
    that reads it. `TURN_END` cannot stand in for it either: the save has to happen at the moment of the blow, not at the end.
So the row's "前置" becomes two named pieces instead of five.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| 仓库技「**获得该角色即生效，无需上场**」"
hits = [i for i, l in enumerate(lines) if l.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for the warehouse family" % len(hits))
lines[hits[0]] = (
    "| 仓库技「**获得该角色即生效，无需上场**」 "
    "| ✅ `1506` 全部出货 ✓。❗ `1407` 月茉之庇：只差「**一次行动**中受到致命攻击的**全体**」选择器 ✓。"
    "⭐ **本轮把“五位一体”改成了两件** ✓（量过 ✓）："
    "① 行动边界 **已有** ✓ —— `TURN_START`（`Battle:1031`）与 `TURN_END`（`Battle:1301`）"
    "正好夹住了 `performAction`（1245）与 `afterMove`（1276） ✓；"
    "② 限额配合 **也已有** ✓（`1217` 自己那条就用的 `救主计数` 计数器 ✓）。"
    "❗ **真正缺的只有一个概念** ✓：「**本次行动内受到致命攻击的单位集合**」 ✗，"
    "加上读它的选择器 ✗。⚠ `TURN_END` **不能代替** ✗：那句话要在**致命的那一刻**就救 ✓，"
    "而不是到行动末尾 ✗。 "
    "| `1407`（1 位） "
    "| ① 一个“本次行动内受过致命攻击的全体”选择器（含那个集合本身） "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   item 4's row is corrected")
