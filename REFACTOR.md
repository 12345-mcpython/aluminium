# 重构交接（2026-10-08 · god-class 拆分批）

> 本文记录**已完成的结构重构**与**剩余部分已量到的刀口**。所有数字都是本轮实测，
> 每条都带"怎么验的"。开工前先读 `ROADMAP.md` §1（三条红线）—— 重构**不改行为**，红线照旧。

## 一、已完成（分支 `refactor/god-class-split`）

护栏：**751 suites / 2574 tests / 0 failures** ＋ `run` / `run --args=mechanics` /
`run --args=all` **三份 demo 输出内容级一致**。每批都跑这四项 ＋ 逐行内容核对 ＋
javadoc 引用审计，**每批一个提交**。

| 原 | 现 | 拆出的类 | 行数 | 职责 |
|---|---|---|---|---|
| `TriggerInterpreter` 6813 | **2310** | `TriggerVocabulary` | 234 | 合法拼写的封闭集合（装载期） |
| | | `TriggerValidator` | 2127 | 这条效果**凭什么存在**（装载期） |
| | | `TriggerTargets` | 674 | 这条效果**打到谁**（触发期） |
| | | `TriggerMagnitudes` | 795 | 这个效果**是多少**（触发期） |
| | | `TriggerEffects` | 964 | 这个效果**做什么**（触发期） |
| `Battle` 3844 | **3621** | `BattleDamage` | 307 | 伤害六乘区装配 ＋ 吸收 ＋ 击破两骑手 |

⚠ **`Battle` 的刀口是被一条不变量逼出来的，别拆错**：`DEALING_DAMAGE` 公告**在装配中途**发射
（应答它的规则可以**改写这个实例** —— `BOOST_DAMAGE`，而其后每一乘区必须读到改写后的数）。
所以刀口**只能落在那里**：`BattleDamage.applyZones` 管公告**之前**那一半，`Battle.assemble`
管公告**之后**。我第一版把整个方法搬走、**漏了公告**，90 个测试当场变红 —— 这正是护栏的价值。

⚠ 同一条不变量还决定了两件事（都写进了 `DamagePipelineTest`）：
`Battle.fireTriggers(…, Damage, …)` **必须保持 private**，`assemble` **必须 private** ——
该测试数的是"`Battle` 的公共方法里有多少个收 `Damage`"，要求**恰好 1 个**。
所以 `applyAdditionalDamage` / `applyTrueDamage` **留在 `Battle`**：它们经私有的
`applyDamage(…, EnergyGrant.KILL_ONLY)` 结算。

## 二、量刀口的工具（`tools/refactor_*.py`，都已纳入版本控制）

**为什么需要工具**：6800 行的类不能靠读来拆。三个脚本各自纠正过一次**假结论**：

| 脚本 | 回答的问题 | 它纠正过的假结论 |
|---|---|---|
| `refactor_callmap.py` | 方法体与调用点（括号配平，非正则） | ① 跨行声明的**漏读**；② `if(...) {` 被当成方法名 `if`，其配平**截断**外层方法；③ 4 空格缩进写成 `\s{4}` ⇒ `\s` 吃换行 ⇒ 缩进捕成 `"\n   "` ⇒ 声明**从图里消失**（表现为"没人调用它"） |
| `refactor_closure.py` | 从种子集反复吸收"调用者全在集内"的方法 | **可达性不能选刀口**：`resolveTarget` 被调度器调用，任何可达性查询都答"共享" |
| `refactor_movable.py` | 有状态类：候选集**读哪些实例字段** | 第一版字段检测器把 `return monsters;` 当字段声明 ⇒ 给 33 个字段的类报 **132 个** |
| `refactor_extract.py` | 逐字节搬运（含各自 javadoc） | 手抄 javadoc = 悄悄改文档（`GAPS.md` 已有教训） |
| `refactor_demodiff.py` | demo 内容级对比 | **哈希护栏是错的**：同代码连跑三次三个哈希（`Resist {}`/`Weakness []` 是 `Set` 打印）。工具用"同代码两次运行必须 MATCH"自检 |
| `refactor_docrefs.py` | javadoc 引用是否还解析得到 | 拆分类会让同文件 `{@link #x}` **静默失效** |
| `refactor_reclose.py` / `refactor_bisect.py` | 修坏形状 / 定位编译失败源 | javac 报"压缩源文件不应有程序包声明"时，**病根在 200 行以下** |

⭐ **纪律**：改动前后各跑一次全量 ＋ 三份 demo；**内容核对**（原文件每个非空行仍在
N 个文件之一里，归一化掉 `private` 与新增限定符）；**javadoc 引用审计**；每批独立提交。

⚠ **`gradlew clean` 会删掉 `build/` 下的基线**。基线要重建时用
`git worktree add build/_baseline_wt HEAD` ＋ 把 `src/main/resources/data/` 拷进去，
在那个 worktree 里跑 demo 取基线，取完 `git worktree remove`。

## 三、剩余：`TriggerTable` 的刀口已量到（未执行）

`TriggerTable` 4049 行 / 51 方法。**条件 DSL 的解析器是一个可整体搬移的集合**，实测：

- **19 个声明 / 678 行**：`parseCondition`(461 行) ＋ 18 个单调用者助手
  （`selfAttributeOf` / `selfResourceOf` / `containsOperator` / `pathOf` / `stacksNameOf` /
  `knownNumericVariables` / `requireIdentityVariable` / `requireStateSubject` /
  `requireSkillSlot` / `requireCarriedParty` / `splitOperator` / `normalize` /
  `isNumericVariable` / `isNumeric` / `requireElement` / `requireInBattleSlot` /
  `skillSlots` / `inBattleSlots`）；
- **接口只有 1 个**：`parseCondition` ← `compile`（唯一调用者）；
- **出向依赖 0 个**：不调用任何留下的助手。

⚠ **常量**：35 个是解析器独占的（`SELF_ATTR_PREFIX` / `ALLIES_WITH_PATH_PREFIX` /
`SELF_RESOURCE_PREFIX` / `SUMMON_RESOURCE_PREFIX` / `HAS_STATE` / `IS_ALLY` … /
`DAMAGE_CARRYING_EVENTS` / `CAST_CARRYING_EVENTS` / `IDENTITY_TERMS` 等，含
`SELF_STACKS_PREFIX` / `ACTOR_STACKS_PREFIX` / `TARGET_STACKS_PREFIX`）⇒ **随解析器搬走**。

⚠⚠ **只有 `STATE_SUBJECTS` 是两个半边共用的**（`1220` 声明；解析器用 `2167/2170`，
留在 `TriggerTable` 的 `3519/3522` 也用）⇒ **留下**、改 package-private，解析器引用
`TriggerTable.STATE_SUBJECTS`。

落法：新类 `TriggerConditionParser`（静态工具，照 `TriggerTargets` 的范式），
`parseCondition` 改 package-private 供 `compile` 调用。

## 四、`Battle` 剩余部分：不要按名字猜子系统

实测（`refactor_movable.py`）：`Battle` 140 方法 / **33 个实例字段**，140 个方法里
**63 个在同一个 938 行连通块**里互相缠绕。**方法名不是关于刀口的证据**：

- `energy` 组（12 方法 / 126 行）看起来最明显，实测读 **3 个字段**
  （`queue` / `characters` / `lastUltEnergySpent`）**且**调一个私有 `dispatch` ⇒ **不可动**；
- 伤害组之所以能拆，是因为它**只读 2 个字段**（都已是公开 API）**且出向调用全是 public**。

⇒ 下一刀的选法：**先数候选集读几个私有字段**，再决定。

## 五、还没做

- 测试重复清理（`HANDOFF.md` 末节的批 2–5）：`MONSTER` 常量清扫（**带少数派守卫**，
  230 文件）、内联 `new Random(0)`（410 处）、"1M 血 ＋ heal"惯用法（9 处 ⇒ `withHp`）。
  ⚠ 那节的 `MONSTER` 计数是当时的数，**动手前先重新 grep**（`HANDOFF.md` 自己写着
  "下一批清单也会过时"）。
