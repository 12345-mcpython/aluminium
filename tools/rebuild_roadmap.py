"""Rebuild ROADMAP.md as principles + todos + pitfalls, dropping the stale registries.

Why not by hand: the file is 1864 lines and the parts being dropped are the parts a hand-copy
would mis-slice. Everything kept is a byte slice of the original, and the new tail is appended
text, so no existing prose is retyped (a retyped paragraph is how "trim a doc" silently becomes
"edit the doc").

What is dropped, and why:

  §12 遗留缺陷登记 (1292-1469) -- its own top warning says "只做过抽查，不是逐条核过代码的...
      动手前先看代码，别信这张表", and its first row H-1 is marked 已修 while sitting under
      "High（未修）". A registry whose rows are shipped work misleads in the direction of
      redoing it. GAPS.md is now the one registry.
  §13 角色实现：引擎还缺什么 (1486-1691) -- a 2026-09-27 snapshot; character coverage is 94/94.
  附录 A 状态快照 (1692-end) -- a 2026-09-29 snapshot whose every figure is stale.
  附：文档分工 (1470-1485) -- superseded by the routing table that ends this file and by HANDOFF.md.

Usage:
    python tools/rebuild_roadmap.py [--check]
"""

import sys

SRC = "ROADMAP.md"
# 1303 is the last line of §11's prose ("每步保持：可编译 → ... → 提交。"), followed by a blank line.
# The `---` at 1304 belongs to §12's block and is dropped with it; the appended tail supplies its own
# separator. Cutting at 1291 was wrong -- that is a blank line INSIDE §11, so it would have removed the
# section's conclusion while leaving its heading behind.
KEEP_THROUGH = 1303

NEW_TAIL = '''
---

## 12. 文档分工（改文档前先读这一节）

| 文件 | 放什么 | 不放什么 |
|---|---|---|
| `engine.md` | **引擎当前行为**，从代码推导，逐条给实现位置；§15 是"已实现 vs 未实现"总表 | 设计意图、进度、历史 |
| `ROADMAP.md` | **本文件**：设计原则（§1 三条红线 ＋ 三分法）、待办、踩坑记录 | 已完成的逐轮叙事、缺口清单 |
| `GAPS.md` | **未出货的登记**：缺什么、读者是谁、前置是什么 | 逐轮台账 |
| `HANDOFF.md` | **纪律手册 ＋ 当前实测状态** —— 动手前读它 | 历史 |
| `REFACTOR.md` | **结构重构**：已完成、量刀口的工具、剩余刀口 | 内容/机制的事 |
| `EXPRESSION.md` | 「完全表达」判定清单 —— ⚠ **被 `ExpressionChecklistTest` 机器校验** | 印象式结论 |
| `HSR.md` | 游戏机制的**权威公式**（正典副本在仓库外） | 本实现的差异（那在 `engine.md` §18） |

> ⚠⚠ **2026-10-12 从本文件删掉三节**，全部是**带日期的一次性快照**，且都在误导读者：
>
> * **§12 遗留缺陷登记**（原 179 行）—— 它自己的抬头就写着"**只做过抽查，不是逐条核过代码的**…
>   **动手前先看代码，别信这张表**"，而表内第一行 `H-1` 标着 **✅ 已修**却排在"High（未修）"下。
>   **一个把已出货项列成待办的登记册，比没有登记册更贵。** 缺口请读 `GAPS.md`。
> * **§13 角色实现：引擎还缺什么**（2026-09-27 快照）—— 角色覆盖现在是 **94/94**。
> * **附录 A 状态快照**（2026-09-29）—— 里面每一笔账都已过时（它写 89 份角色 / 299 测试类）；
>   本文件抬头已给出实测数字并说明怎么量。
>
> 三节的全文都在 git 历史里：`git log --diff-filter=D --name-only` 找出删除提交，
> 再 `git show <commit>:ROADMAP.md`。⚠ **不要用 `>` 重定向**（Windows PowerShell 下写 UTF-16）。
'''


def main():
    lines = open(SRC, encoding="utf-8").read().split("\n")
    if "--check" in sys.argv:
        print(f"{SRC}: {len(lines)} lines")
        print(f"  would keep 1..{KEEP_THROUGH} ({len(lines[0:KEEP_THROUGH])} lines)")
        print(f"  would drop {KEEP_THROUGH + 1}..{len(lines)} ({len(lines[KEEP_THROUGH:])} lines)")
        print(f"  would append {len(NEW_TAIL.splitlines())} lines")
        print(f"  line {KEEP_THROUGH}: {lines[KEEP_THROUGH - 1]!r}")
        print(f"  line {KEEP_THROUGH + 1}: {lines[KEEP_THROUGH]!r}")
        print(f"  line 1306 (start of dropped §12): {lines[1305]!r}")
        return

    # Anchors must hold before writing anything (a half-sliced doc is worse than a stale one).
    for n, expected in (
        (31, "## 1. 设计原则（改代码前先读这一节）"),
        (1306, "## 12. 遗留缺陷登记（原 `CODE_REVIEW.md` + `DOC_VS_CODE.md` 并入，2026-09-26）"),
        (1486, "## 13. 角色实现：引擎还缺什么（2026-09-27 汇总）"),
        (1692, "## 附录 A：2026-09-29 状态快照（第 120/150 轮）"),
    ):
        actual = lines[n - 1]
        if actual != expected:
            raise SystemExit(f"anchor moved: line {n} is {actual!r}, expected {expected!r}")

    kept = "\n".join(lines[:KEEP_THROUGH]).rstrip("\n")
    open(SRC, "w", encoding="utf-8", newline="\n").write(kept + "\n" + NEW_TAIL)
    new_count = len(open(SRC, encoding="utf-8").read().split("\n"))
    print(f"{SRC}: {len(lines)} -> {new_count} lines "
          f"(dropped {len(lines) - KEEP_THROUGH}, appended {len(NEW_TAIL.splitlines())})")


if __name__ == "__main__":
    main()
