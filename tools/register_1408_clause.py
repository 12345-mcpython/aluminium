"""Add the 1408 clause to §3's register (round 1691).

① now stands at four of five readers: 1408's three 「变身结束时」 sentences are named and their machinery exists, but the third of
them cannot be written yet. Registered with what is missing, the reader, and the prerequisite -- the shape §3 requires.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
ROW = ("| 「变身结束时…」（白厄那三句：全队速度 +15% ✓、获得 3 点【火种】✓、"
       "「进入战斗**或**变身结束时攻击力 +50%」） "
       "| 第三句需要：两条**不同规则**挂在**同一属性**上时**共存** ✗"
       "（实测：`StatModifierBuff.isSameKind` 是 `(attribute, modifierType, sourceRole)` ⇒ 同类 ⇒ 后挂的**顶掉**先挂的 ✗；"
       "而语料说两者**同时生效** ✓） "
       "| `1408`（1 位） | 一个**收窄的、可选入**的语义 "
       "（⚠ “不同规则一律共存”已实测**破坏 7 例已出货读数** ✗ ⇒ 挤出是**载荷** ✓） |")
ANCHOR = "| 仓库技「获得该角色即生效"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §3 anchor rows" % len(target))
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1408 clause is registered in §3")
