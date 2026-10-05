"""Bring EXPRESSION.md in step with the tree (round 1660).

Two edits:
  * §2 gains the capability that shipped this segment -- a STACKABLE state (a state that carries a count), with the class
    and the judge that prove it;
  * §3 gains 1505's 「开不败」 as the registered sentence it is, with what is missing, who the reader is, and the
    prerequisites -- including the fact measured last round (STATE_ENDED does reach content tables; the hand-built-table
    route could not be used to judge it).
"""
import io

PATH = "EXPRESSION.md"
text = io.open(PATH, encoding="utf-8").read()

# 1. a §2 row, right after the "counter as a condition" row
ANCHOR = "| 欢乐技「**8 次**随机单体…」的**按次数结算**"
NEW_ROW = ("| **带计数的状态**（“将 N 计入该状态”） | `APPLY_BUFF` 上的 `\"stackable\": true` ＋ `max_stacks`"
           "（同名实例累加，且仍会公告自己的结束） "
           "| `src/main/java/com/laosun/aluminium/models/buff/StackableStateBuff.java` | `AttachedBuffProbeTest` |\n")
if ANCHOR not in text:
    raise SystemExit("REFUSING: the §2 anchor is absent")
text = text.replace(ANCHOR, NEW_ROW + ANCHOR, 1)

# 2. a §3 row for 1505's clause
ANCHOR3 = "| 「按数量／计数**缩放属性**"
NEW_ROW3 = ("| 「队友持有的【好活当赏】**结束时**，绯英会将其中的 **50%** 转化为自身的【好活当赏】」 "
           "| 三件：① `STATE_ENDED` **要带上结束时的量**（已写好、已回滚：只有内容表能当判据）；"
           "② 【好活当赏】在树里**同名两模型并存**（`1513` 当状态、`1505`／`8009`／`8010` 当资源）；"
           "③ “将笑点计入该状态”的**数量还没有来源** "
           "| `1505`（1 位） | 一条内容判据（手工表到不了 `STATE_ENDED`）＋模型统一 |\n")
if ANCHOR3 not in text:
    raise SystemExit("REFUSING: the §3 anchor is absent")
text = text.replace(ANCHOR3, NEW_ROW3 + ANCHOR3, 1)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok   EXPRESSION.md: one new §2 row and one new §3 row")
