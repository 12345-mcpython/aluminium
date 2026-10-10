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

## 三、剩余：`TriggerTable` 的刀口已量到，但**比看上去大**（本轮试抽后回退）

⚠ **本轮实测订正**：条件 DSL 那一刀**不是"19 个方法"那么小**。我按"19 声明 / 678 行 / 出向依赖 0"
抽了一次，编译失败 —— 失败**不是**常量或助手，而是：**`parseCondition` 会构造 28 个嵌套类型**，
它们是这套 DSL 的真正实现：

```
AppliedDebuffClass  DamageElementIsSelf  DamageHasNoCast  DamageIsAdditional  DamageIsAttack
Equality  FromCategory  FromSkill  HasCompanionAlly  HasDebuffOn  HasPath  HasSamePathAlly
HasShield  HasSkill  HasState  HasWeakness  IsAlly  IsCompanion  IsEnemy  IsOtherAlly
IsPartyFirst  IsSameElementAsSelf  IsSummon  Negated  Numeric  ResourceChanged
SharesPathWithAnAlly  StateEnded
```

（`TriggerTable` 里 2582–3720，都实现公共的 `TriggerTable.Condition`。已整体回退，树干净、
全量仍 751/2574/0。）

⇒ **可搬移集合的正确边界是**：19 个方法 ＋ 34 个常量 ＋ **这 28 个嵌套类型**。
动手前必须先定三件事，否则会像我这次一样抽到一半：

1. **`Condition` 接口留在 `TriggerTable`** —— 它是**公开 API**（`CompiledRule` 的字段类型、
   测试在用），不能跟着搬。
2. **28 个实现类的引用方式**：留在 `TriggerTable` 就得写 `TriggerTable.HasState`（28 处）；
   搬进新类就必须**先量**有没有测试/内容直接引用 `TriggerTable.HasState` 之类的名字。
3. **`public` 收窄**：这些嵌套 record 现在是 public 的，搬进 package-private 新类会收窄公开面 ——
   需先确认无外部引用。

⭐ **量法（已验证可靠）**：不要用 `refactor_callmap.py` —— 它在这个文件上**给出错的 span**
（把 `requireCarriedParty` 报成 2226-2255、`isNumericVariable` 报成 2226-2290，两者重叠，
并集会把 65 行邻居一起带走）。用 `build/_refactor_baseline/_move_parser.py` 的做法：

- 声明要**最短 span 胜出**（调用点也会匹配声明正则）；
- 声明正则应**要求修饰符**（`private static` 等），否则 `    requireSkillSlot(x, y, z);` 这种
  第 5 列开始的**调用**会被当成声明，而"最短胜出"会让它**赢过真正的 12 行方法**；
- 常量的**结束位置要扫到 `;`**：`Pattern.compile(...)` 与 `Set.of(...)` 都跨多行，把它们当成
  单行会让之后每个常量都**少切一行**（表现为"每个块都丢了开头那行 javadoc"）；
- javadoc 的起点要**只向上跨注释行**：跳过空行会越过单行 javadoc（造成重叠），
  多跨一行又会把上一段末尾的空行圈进来（虽然能编译，但块不再以 `/**` 或声明开头 ⇒ 无法校验）。

⚠ **`gradlew clean` 会删掉 `build/`**，包括这些分析脚本与基线。基线要重建时用
`git worktree add build/_baseline_wt HEAD` ＋ 把 `src/main/resources/data/` 拷进去，
在那个 worktree 里跑 demo 取基线，取完 `git worktree remove`。

## 三之二、原始测量（供参考，边界见上）

`TriggerTable` 4049 行 / 51 方法。**条件 DSL 的解析器是一个可整体搬移的集合**，实测：

- **19 个声明 / 678 行**：`parseCondition`(461 行) ＋ 18 个单调用者助手
  （`selfAttributeOf` / `selfResourceOf` / `containsOperator` / `pathOf` / `stacksNameOf` /
  `knownNumericVariables` / `requireIdentityVariable` / `requireStateSubject` /
  `requireSkillSlot` / `requireCarriedParty` / `splitOperator` / `normalize` /
  `isNumericVariable` / `isNumeric` / `requireElement` / `requireInBattleSlot` /
  `skillSlots` / `inBattleSlots`）；
- **接口只有 1 个**：`parseCondition` ← `compile`（唯一调用者）；
- **出向依赖 0 个**：不调用任何留下的助手。

⚠ **常量**（本轮实测订正为 **34 个解析器独占 ＋ 4 个共用**）：解析器独占的
（`NUMERIC_VARIABLES` / `SELF_ATTR_PREFIX` / `STATE_ENDED_KEYWORD` / `HAS_STATE` / `DEBUFF_ON` /
`HAS_PATH` / `IS_ALLY` … / `DAMAGE_CARRYING_EVENTS` / `CAST_CARRYING_EVENTS` /
`SELF_STACKS_PREFIX` / `ACTOR_STACKS_PREFIX` / `TARGET_STACKS_PREFIX` / `IDENTITY_VARIABLES` /
`IDENTITY_TERMS`）⇒ **随解析器搬走**（本轮的抽取已证明这一步是通的）。

⚠⚠ **4 个是两个半边共用的** ⇒ **留下**、改 package-private，解析器引用 `TriggerTable.X`：
`ALLIES_WITH_PATH_PREFIX`（`pathOf` 是**留下**的方法，在 1973/1976/1979 读它）、
`SELF_RESOURCE_PREFIX`（`selfResourceOf` 同理，2079）、
`SUMMON_RESOURCE_PREFIX`（2080 ＋ 3854）、
`STATE_SUBJECTS`（3519/3522）。
⚠ 这解释了一处容易误判的细节：**`pathOf` 与 `selfResourceOf` 的实际范围越过 `parseCondition`
的结束行（1945）**，所以它们的助手 span 与解析器 span 是分开的两段 —— 本轮的
`_move_parser.py` 已按此处理（19 个 span、0 重叠、逐块校验头部合法）。

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
