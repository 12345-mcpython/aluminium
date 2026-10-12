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
| `TriggerTable` 4049 | **1844** | `TriggerConditionParser` | 2353 | 条件 DSL（`when` 的解释器：19 方法 ＋ 26 个条件类 ＋ 38 个常量） |

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

## 三、`TriggerTable` 的刀口（**已落地**；本节留作"为什么量了三次"的记录）

✅ **第三次尝试成功并已提交**（`41de90fc`）：`TriggerTable` **4049 → 1844**，
新类 `TriggerConditionParser` **2353**。全量 751/2574/0、三份 demo 一致、逐行零丢失。

**最终边界（照这个做就一次成）**：

| 搬走 | 留下 |
|---|---|
| 19 个方法（`parseCondition` ＋ 18 助手）＋ 后补的 `requirePath` / `isCompanion` | `CompiledRule` / `TriggerContext` / `Condition`（**公开 API**） |
| 26 个私有条件类（改 package-private） | `PartyCondition`（**接口没有 `static`**，正则搜不到）、`HasShield`、`Numeric` |
| **38 个常量**（含 `NUMERIC_VARIABLES` / `SELF_ATTR_PREFIX` / 三个 `*_STACKS_PREFIX` / 各关键字 Pattern） | — |

⚠⚠ **关键方向**：常量**跟着 DSL 走**，而 `TriggerMagnitudes` / `TriggerValidator` **反过来指向新家**
（`TriggerConditionParser.X`）。我第二轮搞反了这一条 —— 当时以为"共享 ⇒ 留在原地"，于是把解析器
改成读 `TriggerTable.X`，结果 180 个错误。**共享不意味着留在原地，意味着其他读者要跟着走。**

⚠ 留下的三个类型，解析器要**反过来限定**它们：`new TriggerTable.HasShield(...)`、
`new TriggerTable.Numeric(...)`，且这两者与其被读的字段都放宽为 package-private。

⚠ **动手前先量**：`refactor_callmap.py` 在这个文件上**给错 span**（把 `isNumericVariable` 报成
2226-2290，与 `requireCarriedParty` 的 2226-2255 重叠 ⇒ 并集会把 **65 行邻居**带走）。
可用的量法在 §三之三末（最短 span 胜出、声明要求修饰符、常量扫到 `;`、javadoc 只跨注释行）。

⚠ **脚本红线**：给共享常量批量加限定符时**必须跳过声明行**（判据：行内含 `static final`），
否则会把声明本身改成 `public static final String TriggerTable.X = ...` 这种非法形式。

以下两节保留两次失败的过程与理由，供后来者看清"哪一步会量漏"。

## 三之一、第一次试抽

⚠ **第一次实测订正**：条件 DSL 那一刀**不是"19 个方法"那么小**。我按"19 声明 / 678 行 / 出向依赖 0"
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

## 三之二、⚠⚠ 第二次试抽的订正：**常量要"往外挂"，不能往里搬**（2026-10-08 第二轮实测）

第二轮试抽（把 19 方法 ＋ 34 常量 ＋ **29 个私有嵌套类型**一起搬）**已经能编译到只剩收尾**，
但暴露出一条上一轮没量到的、决定性的方向问题：

> **那些"看起来只有解析器在用"的常量，其实被别的类用；所以它们不能搬进解析器，
> 只能留在 `TriggerTable` 并把解析器改成引用 `TriggerTable.X`。**

实测到的共享面（**跨 3 个类**，不是 2 个）：

| 常量 | 谁在用 | 结果 |
|---|---|---|
| `SELF_ATTR_PREFIX` | 解析器 ＋ **`TriggerMagnitudes`** | 留 |
| `SELF_STACKS_PREFIX` / `ACTOR_STACKS_PREFIX` / `TARGET_STACKS_PREFIX` | 解析器 ＋ **`TriggerMagnitudes`** | 留 |
| `NUMERIC_VARIABLES` | 解析器 ＋ **`TriggerMagnitudes`** | 留 |
| `ALLIES_WITH_PATH_PREFIX` / `SELF_RESOURCE_PREFIX` / `SUMMON_RESOURCE_PREFIX` / `STATE_SUBJECTS` | 解析器 ＋ `TriggerTable` 自己的效果侧 | 留 |

⇒ **10 个常量留下、全部改 package-private**（本轮实测订正：不是 4 个）。
`TriggerMagnitudes` 是第 3 个消费者 —— 它读 `self_attr:` / `self_stacks:` / `target_stacks:` /
`actor_stacks:` 来解 scale，正是"同一套拼写、两个半边都要读"的又一处。

⚠ **迁移脚本的红线（本轮踩到）**：给"共享常量"批量加 `Owner.` 前缀时，**声明行本身也被改写**了 ——
`public static final String TriggerTable.SELF_STACKS_PREFIX = ...` 这种非法形式。
⇒ **限定符脚本必须跳过声明行**（判据：行里含 `static final`），不能只看"是否注释行"。

### 另一条订正：`PartyCondition`

它是 `private interface`（**接口没有 `static` 关键字**），所以按 `private static` 找嵌套类型会**漏掉它**。
第二轮里它因此没被搬走，而 28 个实现类却搬走了 ⇒ 编译失败。**正确做法：留在 `TriggerTable`、改
package-private**（它 `extends Condition`，而 `Condition` 是公开 API）。

### 还有两个类型不能搬

- **`HasShield`**：`TriggerTable.requireShieldSource(...)` 的**形参类型**就是它 ⇒ 必须从新类可见
  （package-private ＋ 两个被 `TriggerTable` 读的字段 `ruleId` / `raw` 也要放宽）；
- **`Numeric`**：`TriggerTable` 有 `condition instanceof Numeric` 两处（`stacksName` / `resource` 判空）
  ⇒ 同理，字段也要放宽。

⇒ **最终正确的可搬移边界**：19 方法 ＋ **24 个**私有嵌套类型（29 减去 `PartyCondition`、
`HasShield`、`Numeric` —— 这三个留）＋ **0 个常量**（10 个共享常量全部留 `TriggerTable`）。
`compile` 调用改成 `TriggerConditionParser.parseCondition(...)`。

## 三之三、第一次的原始测量（不完整，保留以看清“哪一步量漏了”）

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

## 四之一、⚠⚠ 仪器本身错了两次，`Battle` 的结论要重来（2026-10-09 实测）

**先用仪器，再信仪器。** 上面 §四 引的 `refactor_movable.py` 当时有两个盲点，
**都朝"看起来可拆"的方向错**：

### 盲点 1：**重载只算了第一个声明**（`scan` 按名字建表，`setdefault`）

委派式重载在这个代码库里是**常规写法**，所以"第一个胜出"错的不是边角，而是**大部分有意思的名字**：

| 名字 | 仪器当时算的 | 实际 | 谁被藏了 |
|---|---|---|---|
| `applyDamage` | 3 行 | **100 行** | 真正结算那 84 行（私有的 3 参重载） |
| `reduceToughness` | 5 行 | **77 行** | 真正削韧那 56 行 |
| `fireTriggers` | 3 行 | **190 行** | 62 行的 `…, Damage, …` 重载 |
| `grantShield` | 3 行 | **62 行** | 16 行的 `…, ruleId` 重载 |

实测代价：`Battle` **328 行**、`TriggerInterpreter` **276 行**、`BuffManager` 73 行、
`CanHit` 37 行对**每一个消费者**不可见。⚠ **`TriggerInterpreter` 就是用这台仪器拆的**，
`TriggerTable` / `TriggerConditionParser` 恰好 0 行不可见 —— 那两次成功**有运气成分**。

⚠ 连带一处更贵的：`refactor_extract` 继承了同一个塌缩。`reduceToughness` 的并集 span 是
**1849-1925**，中间夹着它的 javadoc 与别的声明 ⇒ 一次 `move` 会把**整段切走**并报成功。

### 盲点 2：**私有嵌套类型**（读字段/调方法都看不见）

一个方法可以**不读任何字段**、**只调一起走的方法**，却仍然搬不动 —— 因为它**提到一个私有类型**。
DOT/回复那一组正是如此：读 0 字段、0 阻塞调用，却经
`applyDamage(target, damage, EnergyGrant.KILL_ONLY)` 结算，而
**`EnergyGrant` 是私有嵌套 enum** ⇒ **整组必须留下**。

⇒ 修好之后，仪器**自己**把 `applyAdditionalDamage` / `applyTrueDamage` 排除掉了 ✓ ——
与 §一 那段**人读源码得出的**结论一致 ✓。⚠ 这是一次**已知答案的复现**，不是新发现：
真正的不变量写在测试里（`DamagePipelineTest` 数"收 `Damage` 的公开方法恰好 1 个"），
**测量看得见访问权限，看不见语义** ⇒ 仪器是**筛子**，留下的候选仍要过成文不变量。

### 修好之后重量的结果：**机械刀口基本用完了**

| 候选组 | 行数 | 字段 | 阻塞调用 | 私有类型 |
|---|---|---|---|---|
| `grantSkillEnergy` / `isUltraReady` / `ultraEnergyCost` | 37 | 0 | 0 | 0 |
| `hitChance` / `registerWarehouseListener` / `noteAppliedDebuffClass` / `laughSnapshotHolders` | 28 | 0 | 0 | 0 |
| `gainBreakEnergy` / `handleBrokenTurn` | 17 | 0 | 0 | 0 |
| `calculateHeal` / `boostedShield` | 12 | 0 | 0 | 0 |
| ~~DOT / 回复三件套~~ | ~~61~~ | 0 | 0 | **1 (`EnergyGrant`) ✗ 搬不动** |

⇒ **`Battle` 现在最大的可动连贯集合是 37 行**（`grantSkillEnergy` ＋ 两个终结技门槛）。
`refactor_clusters.py` 仍然报 **1421 行 / 68 方法的连通块**，而那一块**每一组都读私有字段
或调私有方法** —— 剩下的行数不是"还没找到刀口"，而是**访问约束**：
`Battle` 的私有面就是它的真正内聚核。

⇒ 结论按"能搬 ≠ 该搬"：**`Battle` 再切只能切出几十行，收益低于风险**。
要真正再降一个量级，需要的**不是**找刀口，而是一次**新抽象**（把私有面本身重新划界）。
按收益排序，下一刀更该落在**仍然藏着 276 行的 `TriggerInterpreter`**（读者更多、行为面更宽）。

### 新增的三件仪器（都已纳入版本控制）

| 脚本 | 回答什么 |
|---|---|
| `refactor_overloads.py` | 这个文件因"第一个重载胜出"丢了哪些行、丢在哪 |
| `refactor_spans.py` | 候选组多大、**是否连续**（不连续 = 中间的行要留下） |
| `refactor_verify_spans.py` | span 是否真的对：花括号配平 / 首行是签名 / 无嵌套包含 |


## 五、测试重复清理：**三批已落地**（2026-10-08）

`HANDOFF.md` 末节给的那张"下一批"清单（批 2–5）**数字全都不准** —— 它自己就写着
"下一批清单也会过时"。**重新量过**之后，三批做完：

| 批次 | 改写 | 结果 | 提交 |
|---|---|---|---|
| 标准敌人 | `private static final int MONSTER = 1002011;` ＋ `EnemyFactory.create(MONSTER, 90, 1)` | **495 文件 / 708 处** → `BattleFixture.monster()` | `182610b9` |
| 标准种子 | 内联 `new Random(0)` | **496 文件 / 787 处** → `BattleFixture.seeded()` | `ed7b7ec9` |
| 固定掷骰 | 手写 `Random` 子类（只覆写 `nextDouble`） | **98 文件 / 98 方法** → `BattleFixture.alwaysRoll(v)` | `9e781550` |

`BattleFixture.` 的使用点从 30 涨到 **1616**。行为零改变（751/2574/0 ＋ 三份 demo 一致）。

### ⚠⚠ 这一节真正的价值：三次"量法"各自漏了一种形状

**每次都是编译器/栈溢出抓住的，不是我先看出来的。** 所以做这类清扫的规矩是：

1. **判据要用"精确字符串"，不要用"看起来像"。** 第一版 preflight 只检查"`MONSTER` 是某个
   `EnemyFactory.create` 的参数"，**没检查 level** ⇒ 10 个文件用 level 100、4 个 group 2、
   1 个 level 91，清扫删了常量却没改那些调用 ⇒ **38 个文件编译不过**，全量回退重来。
2. **有些形状的正则要带上"后面那个字符"。** `new Random(0)` 与 `new Random(0) {` 是两回事
   （后者是**匿名子类**，改成 `seeded() {` 不编译）；12 个文件两者混用 ⇒ 第一次清扫又炸。
3. **排除要按解析后的路径，不能按文件名。** `BattleFixture.java` 按 basename 排除、而候选清单
   存的是路径 ⇒ 排除没生效 ⇒ **夹具把自己重写成了自我调用**（`alwaysRoll` 调用 `alwaysRoll`）
   ⇒ 264 个测试 `StackOverflowError`。现在 preflight 比 `abspath`，清扫里还加了第二道
   "不许把自我调用写进定义它的那个文件"。

### ⭐ 让结果**可查**而不只是"绿的"

两件事让这三批不是"跑绿了就提交"：

- **preflight 把每份文件预期的改写次数写进清单，清扫断言自己正好做了那么多次** ——
  正则静默漏掉变成**硬失败**，而不是"400 个文件之后才发现编译不过"。
- **事后对账**：全库剩余 45 个 `MONSTER` 声明、26 处 `new Random(0)`、2 处手写
  `new Random() {` —— **每一个都能点名到"为什么留下"**（少数派 id / 匿名子类 /
  `TriggerChanceTest.sequence()` 是真的多值生成器 / 夹具自己的实现），**没有一处是漏掉的**。

### 还没做，以及**为什么不做**（重新量过之后）

- ⚠ **"1M 血 ＋ heal"惯用法：`HANDOFF.md` 说"9 处 ⇒ `withHp`"，重新量完全不是**。
  实测：`fromAttributes(name, hp, ...)` 在测试里有 **42 种不同形状**
  （`dummy` hp=1_000_000 有 15 个文件，但还有 `hero` 10_000 ×10、`attacker` 1000 ×7、
  `Dummy` 60000 ×5、`dummy` 100_000 ×4、`boss` 1_000_000 ×1、`solo` 50_000 ×1 …），
  **每个名字与血量都是那个判据自己的前提**，不是样板。而且
  **`BattleFixture.dummy()` 当前的调用点是 0** —— 抽象在那儿但没人用。
  ⇒ 光"能搬"不等于"该搬"，这一项**不做**，理由如上。
- 少数派守卫留下的 **37 个 `MONSTER` 声明**、**12 个匿名子类文件**、**`TriggerChanceTest.sequence()`**
  **有意保留** —— 它们各自测的是别的东西。
