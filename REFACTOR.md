# 重构交接（2026-10-08 · god-class 拆分批）

> 本文记录**已完成的结构重构**与**剩余部分已量到的刀口**。所有数字都是本轮实测，
> 每条都带"怎么验的"。开工前先读 `ROADMAP.md` §1（三条红线）—— 重构**不改行为**，红线照旧。

## 一、已完成（分支 `refactor/god-class-split`）

护栏：**752 suites / 2578 tests / 0 failures** ＋ `run` / `run --args=mechanics` /
`run --args=all` **三份 demo 输出内容级一致**（**302 / 45 / 347 行**）。每批都跑这四项 ＋ 逐行内容核对 ＋
javadoc 引用审计，**每批一个提交**。

| 原 | 现 | 拆出的类 | 行数 | 职责 |
|---|---|---|---|---|
| `TriggerInterpreter` 6813 | **2110** | `TriggerVocabulary` | 234 | 合法拼写的封闭集合（装载期） |
| | | `TriggerValidator` | 2127 | 这条效果**凭什么存在**（装载期） |
| | | `TriggerTargets` | 674 | 这条效果**打到谁**（触发期） |
| | | `TriggerMagnitudes` | 795 | 这个效果**是多少**（触发期） |
| | | `TriggerEffects` | 964 | 这个效果**做什么**（触发期） |
| | | `TriggerLifetimes` | 125 | 一个 buff **什么时候结束**、**谁的回合花掉它**（§三之一） |
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


## 三、`TriggerInterpreter`：已落地的刀口与剩下的

### 三之一、`TriggerLifetimes`（**已落地**）

✅ 提交 `e57d4363`：`TriggerInterpreter` **2205 → 2110**，新类 `TriggerLifetimes` **125 行**（5 个声明）。

**为什么是这一组**：它**不是解释器的内部**，而是解释器与**消费者**之间的接缝 ——
`TriggerEffects`（上一批拆出来的）调 `withLifetime` **6** 次、`unticked` **3** 次、
`withTickOwner` **3** 次；`TriggerValidator` 调 `eventBound` **6** 次。

| 声明 | 行 | 职责 |
|---|---|---|
| `unticked` / `eventBound` | 3 / 3 | 这个 buff **不许被 tick**（`permanent` 或事件结束） |
| `withLifetime` | 4 | `until` 命名的**事件**（寿命挂在 buff 上） |
| `withTickOwner` | 6 | `ticks_on` 命名的**谁的回合边界**花掉它 |
| `lifetimesOf` | 22 | 把 `until` 的名字翻成 `AbstractBuff.Lifetime`（唯一私有成员） |

**实测**（`refactor_movable.py --set`）：读 **0** 字段、**0** 阻塞调用、**0** 私有类型 ⇒ 干净可搬。
块 **1325-1418 连续**（`refactor_spans.py`），一刀切下、没有夹带邻居。

⚠ **两处代价**（都记下来）：
1. **patch 脚本第一版锚点差一行**（两处都差）—— 是"先校验锚点再动笔"**当场抓住**的 ✗；
2. **调用点数目我自己数错了**：我按 `grep` 行数报 **6**，工具按**出现次数**报 **8** ✓ ——
   因为 `withSource(withTickOwner(withLifetime(` **一行里有三个** ✓。
   ⇒ 教训与 §五 同族：**"数调用点"要数出现次数，不是数行数** ✓。

⭐ **顺带修掉上一批留下的一处坏 javadoc**：`TriggerInterpreter` 里
`{@link #applyControl}` **解析不到任何东西** ✓（`applyControl` 早在上一批就搬进
`TriggerEffects` 了）⇒ 改成 `{@link TriggerEffects#applyControl}` ✓（本文件已有两处这个写法 ✓）。
`refactor_docrefs.py` 现在报**整个 trigger 包全解析** ✓。

### 这个文件剩下的刀口（修好仪器之后重量，2026-10-09）

`TriggerInterpreter` 现在 **2110 行 / 47 个名字 / 53 个声明**，`refactor_clusters.py` 报
**7 个直接边簇**，最大一个是 **41 方法 / 1302 行**。可动的连贯组按大小排：

| 候选组 | 行 | 字段 | 阻塞调用 | 私有类型 | 读者 |
|---|---|---|---|---|---|
| 资源 op：`gainResource` / `spendResource` / `removeResource` / `raiseResourceCap` ＋ `isCastParamScale` / `scaledAmount` / `normalizeOp` / `eventShare` / `noteElationGiftIfThisIsOne` | **207** | 0 | 0 | 0 | ⚠ **只有本类** ✓ |
| 量级/计数算术：`perStackFactor` / `grantAmount` / `measureForTheDelta` / `resourceAmount` / `statModifier` / `weightOf` / `counterValue` / `scaledAmount` | **134** | 0 | 0 | 0 | `TriggerEffects` 4 处 ✓ |
| 计数与步进：`ctxFreeCounter` / `counterName` / `wholeSteps` / `parseCounterSteps` / `perStepsRepeat` / `spendSteps` / `counterValue` / `fireResourceChanged` | **104** | 0 | 0 | **1 (`CounterSteps` ✗ 一起搬走)** | `TriggerEffects` / `TriggerValidator` ✓ |

⚠ 三组都**不连续**（8 / 7 / 7 处空隙）⇒ 每次都要**逐个切块**，成本高于 §三之四 那一刀 ✗。

⚠⚠ **剩下的大头是三个派发器**，而它们**不可搬**：
`validate` 3 ＋ `apply` 127 ＋ `fire` 90 ＋ **`applyOne` 312** ＝ **532 行 / 4 个方法** ✓ ——
`applyOne` 就是那张 47 个 op 的 `switch` 本身 ✓。**它就是"解释器"的定义** ✓：
把 `switch` 拆成映射表只是**换一种写法**，不是换一个刀口 ✓ ⇒ 按"能搬 ≠ 该搬"，**不动** ✓。


## 四、`Battle` 剩余部分：不要按名字猜子系统

实测（`refactor_movable.py`）：`Battle` 140 方法 / **33 个实例字段**，140 个方法里
**63 个在同一个 938 行连通块**里互相缠绕。**方法名不是关于刀口的证据**：

- `energy` 组（12 方法 / 126 行）看起来最明显，实测读 **3 个字段**
  （`queue` / `characters` / `lastUltEnergySpent`）**且**调一个私有 `dispatch` ⇒ **不可动**；
- 伤害组之所以能拆，是因为它**只读 2 个字段**（都已是公开 API）**且出向调用全是 public**。

⇒ 下一刀的选法：**先数候选集读几个私有字段**，再决定。

### 四之一、⚠⚠ 仪器本身错了三次，`Battle` 的结论要重来（2026-10-09 实测）

**先用仪器，再信仪器。** 上面 §四 引的 `refactor_movable.py` 当时有**三个**盲点，
**每一个都朝"看起来可拆"的方向错**：

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

### 盲点 3：**声明的行号取的是正则匹配起点**（找到 `TriggerInterpreter` 上）

`DECL` 在 DOTALL 下把 `[A-Z]\w*` 当返回类型的备选、把 `[^)]*` 当参数表，`type` 后面还是 `\s+`
（`\s` 吃换行）⇒ **匹配起点会漂** ✓：`TriggerInterpreter.isCastParamScale` **实际声明在 859**，
仪器报 **852**（它的 `/**` 那一行）；`TriggerEffects.modifyAttr` 报 **43** 而非 **83** ✓。
**src/main/java 全库 97 个声明的行号是错的** ✓ —— 而每个消费者都从这一行开始切正文 ✓
⇒ 切出来的块**带着一段 javadoc** ✓（内容不丢失，但 span 与行数全错）。

⇒ 修法：**行号锚在 `name` 分组上**（实测 1140 个声明里它一次都没漂 ✓）。
⚠ 我为此先写了**两版补丁式守卫**（比较匹配起点与类型所在行）✗，两版都**误杀真实声明**
（第二版丢了 **95 个方法名** ✓）⇒ 是**全库计数**（丢了什么、还剩什么）把误杀抓出来的 ✓，
不是读代码看出来的 ✓。**根治位置错的毛病，不要在症状上加守卫** ✓。

⚠ 订正：本节早先写的 "`Battle` 44.7% / `TriggerInterpreter` 56.7% 的声明行覆盖" 是**在
守卫误杀期间量的** ⇒ **两个数都作废** ✓（那期间 `TriggerInterpreter` 只剩 38 个名字 ✓）。

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

### 新增的四件仪器（都已纳入版本控制）

| 脚本 | 回答什么 |
|---|---|
| `refactor_overloads.py` | 这个文件因"第一个重载胜出"丢了哪些行、丢在哪 |
| `refactor_spans.py` | 候选组多大、**是否连续**（不连续 = 中间的行要留下） |
| `refactor_verify_spans.py` | span 是否真的对：花括号配平 / 首行是签名 / 无嵌套包含 |
| `refactor_javadoc_starts.py` ＋ `refactor_check_spans.py` | **全库**有几处 span 起点落在注释行（盲点 3 的判据） |
| `refactor_decls.py` | 一个类的**布局**：顺序 / 可见性 / 每个名字的行数 |

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
