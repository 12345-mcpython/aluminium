"""Doc-only fix: the segment-count row of EXPRESSION.md §3, corrected to what the tree actually says.

Measured this round:
  * the readers are 8009 and 8010 (their own notes carry the sentence 「8 次随机单体 20% + 最后 60% 均分」, data row
    `[8, 0.1, 0.3]` with the leading column being a HIT COUNT) -- NOT the five the row claimed: 1505/1502/1506 register
    other things entirely, so the count was never read;
  * the engine's "N hits" is read per skill KIND (e.g. `BOUNCE` takes the SECOND param), so a row that STARTS with a hit
    count has no representation;
  * and our own `data/` carries no elation skill row at all (scan: 0).

Line-based matching on purpose: the row contains Chinese full-width brackets and straight ASCII quotes, and a literal
substring built inside a shell one-liner kept failing.
"""
import io

PATH = "EXPRESSION.md"
PREFIX = "| 角色技能「按列位／段数结算」"
NEW = ("| 欢乐技「**8 次**随机单体…」的**按次数结算** "
       "| 引擎的“N 次命中”是**按技能种类**读的（如 `BOUNCE` 取**第二个**参数 ✓），"
       "而**欢乐技的行首列就是次数**这一种没有表示 ✗；⚠ 另缺："
       "**我方 `data/` 里一个 elation 技能行都没有**（扫到 0 个 ✗） "
       "| `8009`／`8010`（**2 位**，逐条读到 ✓；⚠ 原登记写 5 位而 "
       "`1505`／`1502`／`1506` **逐条读不到** ✗ ⇒ 按本节规矩改为 2 位） "
       "| 技能行模型 ＋ 欢乐技的数据行 |")

lines = io.open(PATH, encoding="utf-8").read().split("\n")
hits = [i for i, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    raise SystemExit("REFUSING: %d lines start with the old row" % len(hits))
lines[hits[0]] = NEW
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the segment-count row now states the measured readers (2) and the real missing piece")
