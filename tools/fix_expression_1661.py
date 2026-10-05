"""Correct the checklist row for the live-modifier family, from the GAPS sweep (round 1661).

Measured: the row said one reader (`23000`). The sweep of GAPS §2 found the real entry (2026-09-29, entry 39): 「每层 +X%」
with FOURTEEN documents, all sustained auras like Asta's 「每拥有 1 层蓄能，我方全体攻击力提高 14%，最多叠加 5 层」, whose number
has to be re-read as the stack count changes -- and the engine's `DoubleValue.Modifier` carries a rate, not a function. The
row is rewritten with the measured reader count, the exact shape it needs, and the fact that this round only READ the
registration rather than re-checking the tree.
"""
import io

PATH = "EXPRESSION.md"
START = "| 「按数量／计数**缩放属性**"
NEW = ("| 「**每层 +X%**」的**活值修正器**（长驻光环：数值随层数**被重新读取**） "
       "| 一个在**读取时解析其份额**的修正器（`DoubleValue` 的一类**活**修正）："
       "快照只能在**应用时刻**算一次 ✗，而重算又会让 buff 自己叠加（0.14 × (1+2+3+4+5)）✗；"
       "⚠ 树里只见到持有**数值**的修正器（`multiplyPercent(rate, …)` ✓） "
       "| **14 个文件**（GAPS 第 39 条，2026-09-29 量测 ✓；⚠ 本轮**只读到登记**，未逐条复核 ✗） "
       "| 一类活修正器（读取时取当前层数）\n")

lines = io.open(PATH, encoding="utf-8").read().split("\n")
hits = [i for i, line in enumerate(lines) if line.startswith(START)]
if len(hits) != 1:
    raise SystemExit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = NEW.rstrip("\n")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the row now states 14 readers and the exact shape needed")
