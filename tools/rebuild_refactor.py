"""Rebuild REFACTOR.md: what is done, the tools, and the seams that are left.

Slices the existing file byte-exactly and re-frames it. Nothing that stays is retyped.

Dropped, with the reason each one is safe to drop:
  84-199   the three attempts at the TriggerTable cut (v1, v2, the original measurement). All three
           describe a split that has SHIPPED, and each one's transferable lesson is carried into
           the "刀口怎么选" section that replaces them.
  338-386  the three test-dedup sweeps (done: 495/496/98 files rewritten, commits named). Their
           lasting rule is in the same replacement section.

Usage:
    python tools/rebuild_refactor.py [--check]
"""

import sys

SRC = "REFACTOR.md"

# Byte slices of the original that survive (1-based, inclusive).
KEEP = [
    (1, 54),      # 一、已完成 (split table + the Battle invariant)
    (200, 246),   # 三之四、TriggerLifetimes + TriggerInterpreter's remaining seams
    (247, 337),   # 四、Battle 剩余 + 四之一、instrument defects
]

NEW_SECTIONS = '''
## 四之二、刀口怎么选（把上面三次失败与三批清扫的教训合起来）

这四条是**花钱买来的**，合并了原本散在 §三之一/§三之二/§三之三 与 §五 里的三份过程记录：

1. **可达性不能选刀口。** `resolveTarget` 被调度器调用 ⇒ 任何"谁调用了它"的查询都答"共享"。
   要问的是**"这个候选集读哪些实例字段、调哪些非公开方法、提到哪些私有类型"**（三样都算）。
2. **共享常量"往外挂"，不往里搬。** 方向搞反过一次，180 个编译错误。
   ⭐ **共享不意味着留在原地，意味着其他读者要跟着走** —— 常量跟 DSL 走，
   `TriggerMagnitudes` / `TriggerValidator` 反过来指向新家。
3. **测量看得见访问权限，看不见语义。** 仪器把 `applyAdditionalDamage` / `applyTrueDamage`
   排除了 ✓，与"人读源码"的结论一致 —— 但那是**复现已知答案**，不是新发现：
   真正的不变量写在测试里（`DamagePipelineTest` 数"收 `Damage` 的公开方法恰好 1 个"）。
   ⇒ **仪器是筛子，留下的候选仍要过成文不变量。**
4. **能搬 ≠ 该搬。** 三批测试清扫里，"1M 血 ＋ heal"那一项**不做**：实测 `fromAttributes` 有
   **42 种形状**，每个名字与血量都是那个判据的前提。⭐ 反过来，"下一批清单也会过时" ——
   抄旧注记之前先 grep 内容。

⚠ **别再犯的形状错误**（每次都是编译器或栈溢出抓出来的，不是看出来的）：

- **判据要用"精确字符串"，不要用"看起来像"**（清扫按 `MONSTER` 是某次 `create` 的参数就放行，
  没检查 level ⇒ 10 个文件用别的 level ⇒ 38 个文件编译不过）。
- **有些正则要带上后面那个字符**：`new Random(0)` 与 `new Random(0) {`（匿名子类）是两回事。
- **排除要按解析后的路径**，不能按文件名（否则夹具把自己重写成自我调用 ⇒ 264 个 `StackOverflowError`）。
- **重载名不能整体搬**：`reduceToughness` 的名字级 span 是 1849-1925，中间夹着别的声明与它自己的
  javadoc ⇒ 一次 `move` 会把整段切走并报成功（`refactor_extract.py` 现在**拒绝**重载名）。
- **声明行号取"名字所在行"，不是正则匹配起点**：`DECL` 在 DOTALL 下起点会漂到 javadoc 上
  （`isCastParamScale` 曾被报成 852，实际 859）⇒ 全库 **97 个**声明的行号是错的。

## 五、测试与内容侧的结构性清理（**已落地**）

| 批次 | 改写 | 结果 | 提交 |
|---|---|---|---|
| 标准敌人 | 内联 `EnemyFactory.create(MONSTER, 90, 1)` | **495 文件 / 708 处** → `BattleFixture.monster()` | `182610b9` |
| 标准种子 | 内联 `new Random(0)` | **496 文件 / 787 处** → `BattleFixture.seeded()` | `ed7b7ec9` |
| 固定掷骰 | 手写 `Random` 子类（只覆写 `nextDouble`） | **98 文件 / 98 方法** → `BattleFixture.alwaysRoll(v)` | `9e781550` |
| 夹具层抽出 | `new Battle(..., new Random(0))` ＋ 标准敌人 | **23 个纯样板 engine 文件** → `test/support/BattleFixture.java` | `重构批 1` |

`BattleFixture.` 的使用点从 30 涨到 **1616**。行为零改变（全量 ＋ 三份 demo 一致）。
⭐ **夹具承重的证据**：`seeded()` 改种子 ⇒ 红（红的正是迁过的那条判据）；
而 `monster()` 等级 90→91 ⇒ **绿** ✗ ⇒ 把标准敌人收成一处是**可读性**收益，**不是**风险收益。
'''


def main():
    lines = open(SRC, encoding="utf-8").read().split("\n")
    if "--check" in sys.argv:
        kept = sum(b - a + 1 for a, b in KEEP)
        print(f"{SRC}: {len(lines)} lines")
        for a, b in KEEP:
            print(f"  keep {a}-{b} ({b - a + 1} lines)  first={lines[a - 1][:70]!r}")
        print(f"  kept total {kept}; dropped {len(lines) - kept}; "
              f"appended {len(NEW_SECTIONS.splitlines())}")
        return

    anchors = {
        6: "## 一、已完成（分支 `refactor/god-class-split`）",
        200: "## 三之四、`TriggerInterpreter` 的第二刀：`TriggerLifetimes`（**已落地**）",
        247: "## 四、`Battle` 剩余部分：不要按名字猜子系统",
        338: "## 五、测试重复清理：**三批已落地**（2026-10-08）",
    }
    for n, expected in anchors.items():
        if lines[n - 1] != expected:
            raise SystemExit(f"anchor moved: line {n} is {lines[n - 1]!r}, expected {expected!r}")

    parts = ["\n".join(lines[a - 1:b]) for a, b in KEEP]
    # Re-number the surviving sections into one clean sequence.
    text = "\n\n".join(parts)
    text = text.replace("## 三之四、`TriggerInterpreter` 的第二刀：`TriggerLifetimes`（**已落地**）",
                        "## 三、`TriggerInterpreter`：已落地的刀口与剩下的\n\n"
                        "### 三之一、`TriggerLifetimes`（**已落地**）")
    text = text.replace("## 四、`Battle` 剩余部分：不要按名字猜子系统",
                        "## 四、`Battle` 剩余部分：不要按名字猜子系统")
    text = text.replace("## 四之一、⚠⚠ 仪器本身错了三次，`Battle` 的结论要重来（2026-10-09 实测）",
                        "### 四之一、⚠⚠ 仪器本身错了三次，`Battle` 的结论要重来（2026-10-09 实测）")
    out = text.rstrip("\n") + "\n" + NEW_SECTIONS
    open(SRC, "w", encoding="utf-8", newline="\n").write(out)
    print(f"{SRC}: {len(lines)} -> {len(open(SRC, encoding='utf-8').read().split(chr(10)))} lines")


if __name__ == "__main__":
    main()
