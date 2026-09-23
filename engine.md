# aluminium 引擎机制说明

本文档**完全从代码推导**（`src/main/java`，截至 `929b460` + 本轮 C-1/H-3/H-4/H-5/H-7 修复），
不引用任何外部设计文档。每条机制都给出实现位置，方便对照源码；凡是**没有实现**或**只是占位**的，
本文会明确标注，不把"打算做"写成"已经做"。

> **规范文档**：游戏机制的权威公式在 `HSR.md`（"崩坏：星穹铁道 战斗机制总结（全版本 · AI 实现版）"，
> 以 4.4.0 数据为准）。本文与它的差异集中在 §18，**不重复它的内容**。
> 该文件目前**不在仓库里**（只存在于作者本机 `E:\code\blog\hsr\`），
> 要长期依赖它需要入库或把关键结论内联进 ROADMAP —— 见 `DOC_VS_CODE.md` C-3。

阅读约定：

| 标记 | 含义 |
|---|---|
| ✅ | 已实现且接入战斗循环，有测试覆盖 |
| ⚠️ | 已实现但**未接线**（有代码、有数值，战斗里不生效） |
| 🚧 | 占位/简化（有明确的 TODO，当前行为与最终设计不同） |
| ❌ | 完全没有实现 |

---

## 1. 总体架构

```
数据层   resources/data/*.json ──Gson──▶ beans/* ──Constant（静态装载）──▶ 全局数据表
                                              │
数值层   AttributeBuilder ──▶ DoubleValue[]（按 AttributeType.ordinal() 索引）
                                              │
实体层   CanHit（抽象基类）├── Character   ├── Enemy   └── Summon ❌未被实例化
                                              │
战斗层   Battle ── Queue（行动条）──▶ 回合推进
           │
           ├─ applyDamage  ← 唯一伤害结算入口
           ├─ SkillExecutor ← 技能展开成 N 段 Damage
           └─ 事件广播：BattleEvent / MoveEvent / DamageEvent / AttackEvent
```

### 1.1 三条贯穿全局的硬约定

1. **一段伤害 = 一个 `Damage` 对象**（永远是"单段·单目标"）。
   一个技能产生 N 段 → 构造 N 个 `Damage`。`Damage` 的类注释明确写死了这条。
2. **有伤害要结算 ⇒ 造 `Damage`；不造 `Damage` ⇒ 无伤害。**
   治疗/护盾/回能/上 buff 一律不构造 `Damage`（`SkillExecutor` 用 `SkillEffectType.isDamaging()` 分流）。
3. **引擎不认角色**：`Battle`/`SkillExecutor`/`Damage`/`CanHit` 里没有任何 `cid` **判断**
   （`cid` 只作为数据键与缓存键出现）。角色特有机制要么数据化，要么写专用类，不许在流程里 `if (cid == ...)`。

---

## 2. 属性系统（`DoubleValue` / `AttributeType` / `AttributeBuilder`）

### 2.1 属性值的代数 ✅

`models/DoubleValue.java`

```
最终值 = baseValue × (1 + Σ addPercent) × Π (1 + multiplyPercent) + Σ pureValue
```

三类修正（`DoubleValue.Modifier.ModifierType`）：

| 类型 | 语义 | 工厂方法 |
|---|---|---|
| `ADD_PERCENT` | 加算进同一个括号 | `Modifier.addPercent(pct, source, roleId)` |
| `MULTIPLY_PERCENT` | 各自乘一个 `(1+pct)` | `Modifier.multiplyPercent(...)` |
| `PURE_VALUE` | 最后加的平值 | `Modifier.pure(value, source, roleId)` |

每个修正都带 `source`（`ModifierSource`：`BASE`/`RELIC`/`WEAPON`/`SKILL_POINT`/`EXTRA`/`BUFF`/`DEBUFF`/`UNKNOWN`）
与 `roleId`（来源实体的唯一 id），因此可以**按来源撤销**：`filterBySource(source)` 返回快照，
`removeModifier(modifier)` 三个列表都扫（用非短路的 `|=`），不会撤错也不会 `ConcurrentModificationException`。

其他要点：
- `clone()` 是**真深拷贝**（重建三个列表 + 逐个 `Modifier.clone()`）。
- `zero()` / `one()` 每次返回新实例，不是共享单例。
- 批量装配用的 `*NoCompute` 方法不会立即结算，必须由 `AttributeBuilder.build()` 统一调 `commit()` → `compute()`。

### 2.2 属性清单（`enums/AttributeType.java`，共 27 个值）✅

| 分类 | 属性 |
|---|---|
| 基础值（`isPercent=false`） | `HEALTH` `DEFENCE` `ATTACK` `SPEED` |
| 百分比占位（**在最终数组里恒为 `null`**） | `HEALTH_PERCENT` `DEFENCE_PERCENT` `ATTACK_PERCENT` `SPEED_PERCENT` |
| 双爆 | `CRIT_CHANCE` `CRIT_ATTACK` |
| 命中/抵抗 | `EFFECT_HIT_RATE` `EFFECT_RESISTANCE` ⚠️ |
| 治疗 | `OUTGOING_HEALING_BOOST` `HEAL_TAKEN_RATIO` ❌未使用 |
| 击破/能量 | `BREAKING_EFFECT` `ENERGY_REGENERATION_RATE` |
| 元素增伤（7 个） | `PHYSICAL_` `FIRE_` `ICE_` `THUNDER_` `WIND_` `QUANTUM_` `IMAGINARY_DAMAGE_BOOST` |
| 通用 | `ALL_DAMAGE_TYPE_BOOST` |
| 穿透 | `DAMAGE_PENETRATION`（抗性区用） `DEFENCE_IGNORE`（防御区用） |
| 欢愉 | `ELATION_DAMAGE_BOOST` ⚠️定义了但全仓库无读取者 |

### 2.3 百分比重定向 ✅

`Constant.PERCENT_TO_BASE` 把 `HEALTH_PERCENT→HEALTH`、`ATTACK_PERCENT→ATTACK`、
`DEFENCE_PERCENT→DEFENCE`、`SPEED_PERCENT→SPEED` 四个"百分比属性"**重定向到对应基础属性**。

`AttributeBuilder.addPercent` / `addPure` 会先查这张表再决定往哪个属性上挂修正，
`build()` 则把四个百分比槽位显式置 `null`。所以：

> **`CanHit.getAttribute(HEALTH_PERCENT)` 永远返回 `null`** —— 想读生命值请读 `HEALTH`。

超过 0 个调用方踩这个坑，是因为 `RelicSuit`/`Weapon`/`SkillPoint` 三处都各自先判了
`PERCENT_TO_BASE.containsKey(...)`（这个守卫被复制了 3~4 次，是重构点）。

### 2.4 属性数组 ✅

`AttributeBuilder.build()` 返回 `DoubleValue[AttributeType.values().length]`，**按 `ordinal()` 索引**
（`CanHit.getAttribute(type)` 就是 `attributes[type.ordinal()]`）。
数组里没有贡献的槽位是 `DoubleValue.zero()`（不是 `null`，四个百分比槽除外）。

---

## 3. 伤害结算（核心）

### 3.1 入口唯一性 ✅

`Battle.applyDamage(CanHit target, Damage damage)` 是**唯一公开结算入口**：

```java
if (target.isDeath() || target.isInvulnerable()) return 0;   // 尸体 / 转阶段无敌：不结算
double settled = assemble(damage);                           // 装配乘区
boolean died = target.takeDamage(settled);                   // HP 只在 takeDamage 里减
grantHitAndKillEnergy(target, damage, died);                 // 回能钩子
return settled;
```

`assemble` 是 `private`。为什么强调唯一：装配是**追加**语义（`addBoost` 往乘区 append 修正），
对同一个 `Damage` 装配两次会把增伤/易伤/减伤/虚弱各算两份，症状是"数值虚高但看着不离谱"。
`DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint` 用反射断言了这一点。

**想"只看数值不扣血"目前没有口子**（`previewDamage` 未实现）。

### 3.2 乘区体系 ✅

`models/Damage.java`。`Damage` 内部是 `List<Area> damageArea`，只有被用过的区才会进列表
（没进列表的区 == ×1.0）。`toValue()` 遍历列表，对 `applies(type)` 为真的区连乘。

```
Damage = skillBaseValue
       × BoostArea        (1 + Σ增伤)
       × VulnerableArea   min(1 + Σ易伤, 3.5)
       × ReductionArea    max(Π(1 - 减伤_i), 0.01)
       × WeaknessArea     max(1 - Σ虚弱, 0.2)
       × CritArea         crit ? 1 + 暴伤 : 1
       × DefenceArea      (200 + 10L) / (有效防御 + 200 + 10L)
       × ResistArea       1 - clamp(抗性 - 穿透, -1, 0.9)
```

| 区 | 计算 | 钳制 | 谁提供 |
|---|---|---|---|
| `BoostArea` | `1 + Σ` | 无上限 | 攻击方面板（元素增伤 + 全增伤） |
| `VulnerableArea` | `1 + Σ` | **≤ 3.5** | 受击方负面（`DamageEvent` 注入） |
| `ReductionArea` | `Π(1-r)` | **≥ 0.01**，入参 clamp [0,1] | 受击方增益 |
| `WeaknessArea` | `1 - Σ` | **≥ 0.2** | 攻击方负面 |
| `CritArea` | 暴击则 `1+暴伤` | ≥ 1.0 | `Battle` 掷骰后写入 |
| `DefenceArea` | 见下 | ≤ 1.0 | 攻击者等级 + 受击者防御 |
| `ResistArea` | `1 - clamp(抗性-穿透)` | 系数 ∈ [0.1, 2.0] | 受击者抗性表 |

**防御区**：`有效防御 = max(0, 防御 × (1 - clamp(无视防御, 0, 1)))`，
`等级项 = 200 + 10 × 攻击者等级`（`Constant.DEFENCE_CONST` / `DEFENCE_PER_LEVEL`）。

> ⚠️ **`defence(level, def, ignore)` 只暴露一个"穿透"参数**，而游戏里减防与穿透是
> **加算进同一个括号**的：`有效防御 = 防御 × (1 - clamp(减防% + 穿透%, 0, 1))`。
> 所以 P4 之后接入"减防 debuff"时，**必须把两者相加后作为单个参数传入**，
> 不要各自调一次 `defence(...)`（后一次会覆盖前一次，结果只按最后一个算）。
> 等真正需要两个来源各自可撤销时，再把签名拆成 `(level, def, defenceReduce, penetration)`。

**四条不变量**（违反即 bug）：
1. `skillBaseValue` 不进任何区，只是连乘起点。
2. **随机数不进乘区**：暴击骰子在 `Battle`（用注入的 `Random`），`CritArea` 只记"暴没暴、暴伤多少"。
3. 钳制不可绕过：`Area.getRate()` 是 `final`，用 `Math.clamp(rate(), min(), max())`；基类默认 `±∞` 不设策略，
   官方边界一律由区自己声明。
4. **"这段不吃那个区"是声明的**：由 `Area.applies(DamageType)` 决定，不靠调用方自律。

辅助口：`breakdown()` 返回逐区倍率的有序 Map（给日志/UI 做公式分解）；
`getDamageArea()` 返回只读快照。

### 3.3 装配顺序 ✅

`Battle.assemble` 固定 5 步：

1. **增伤区**：`getBoostByElement(element)` 拿元素增伤属性 + `ALL_DAMAGE_TYPE_BOOST`。
2. **暴击区**：仅当 `type.isCrittable() && !damage.isCritFixed()` 时掷骰
   （`critRate > 0 && rng.nextDouble() < critRate`）。**这是全引擎唯一的随机点。**
3. **防御区**：攻击者等级 / 受击者防御 / 攻击者 `DEFENCE_IGNORE`。
4. **抗性区**：受击者是 `Enemy` 时取其 `damageResist`（表里没有的元素 = 0 抗性，非敌人 = 0），
   减去攻击者 `DAMAGE_PENETRATION`，再 clamp。
5. **事件钩子**：`attacker.onDamage(...)` 与 `defender.onDamage(...)` **双方都发**。

最后 `Math.max(1, damage.toValue())` —— **最小伤害钳制 1 在 `Battle` 里，不在 `Damage`**。

### 3.4 伤害类型（`enums/DamageType.java`，12 个值）✅

| 类型 | 可暴击 | 吃增伤 |
|---|---|---|
| `NORMAL` `SKILL` `ULTRA` `ADDITIONAL` `EXTRA` `TECHNIQUE` `MEMORY` | ✅ | ✅ |
| `DOT` | ❌ | ✅ |
| `ELATION`（欢愉） | ✅ | ❌ |
| `BREAK` `SUPER_BREAK` `TRUE` | ❌ | ❌ |

两个 flag（`isCrittable()` / `isBoostable()`）是**类型自带的数据**，
`BoostArea.applies()` 与 `CritArea.applies()` 直接消费，所以"击破不吃增伤"不需要在每个调用点记得排除。

`DamageType.fromString(String)` 大小写不敏感（先 `toLowerCase(Locale.ROOT)`），未知值抛 `IllegalArgumentException`。

### 3.5 `Damage` 的两个特殊开关 ✅

- `trueDamage()`：跳过**全部**乘区（受 `Constant.TRUE_DMG_SKIP_ZONES` 控制，当前 `true`），
  `toValue()` 直接返回 `skillBaseValue`。
- `notCountsAsAttack()`：这一段"不视为造成了 1 次攻击"→ **受击方**不回能、不削韧、不触发攻击级事件。
  **但不影响击杀回能**：它仍然归属攻击者，击杀时照给攻击者结算（见 §9.2）。
  附加伤害与真实伤害都会置它。

---

## 4. 四个事件家族 ✅

全部挂在 `CanHit` 上，默认实现转发给各自的 `BuffManager`。
**子类重写时必须调 `super`**，否则自己的 buff 会失效（`CanHit` 的注释专门警告）。

| 事件 | 签名 | 触发时机 |
|---|---|---|
| `BattleEvent` | `onBattleStart(Battle)` | `Battle.startBattle()` 遍历 `queue.snapshot()` |
| `MoveEvent` | `beforeMove(Battle)` / `afterMove(Battle)` | 回合前后 |
| `DamageEvent` | `onDamage(Battle, Damage)` | `assemble` 第 5 步，**攻守双方各发一次** |
| `AttackEvent` | `afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage)` | `SkillExecutor` 在一次技能全部段结算完后**广播给全体我方** |

几个关键语义：

- **`DamageEvent` 广播给双方**，但回调签名里**不告诉 buff 它挂在谁身上**。
  因此注入乘区的 buff 必须自己判侧：`Damage.isOnDefenderSide(entity)` / `isOnAttackerSide(entity)`
  （这就是易伤必须判侧、否则持有者自己打人也会被加伤的原因）。
- **`AttackEvent` 只广播给我方 `battle.characters`**（不含召唤物 ❌ 因为召唤物没进任何列表），
  这是因为"我方攻击后"的效果（知更鸟【协奏】、缇宝结界）挂在**别人**身上。
- `hitTargets` 是**实际命中过**的目标（含当场死亡的，按命中顺序去重）；
  `mainTarget` 是调用方选的主目标（AOE 时它不是命中顺序里的第一个）。
- **"一次攻击行为" vs "多种伤害类型"**（重要区分）：
  - 引擎的计数单位是**行为**，不是伤害类型：`SkillExecutor.execute` 每次施放只调
    **一次** `grantSkillEnergy`、只广播**一次** `AttackEvent`；
  - 一次攻击行为里可以包含多种伤害类型（技能伤害 + 击破伤害 + 超击破伤害 + DOT…），
    它们都属于同一次攻击 —— **不要把某个伤害类型当成一次攻击行为**；
  - `totalDamage` 因此是**整条攻击链之和**：技能段 + 击破伤害 + 超击破伤害都计入
    （击破伤害由 `Battle.StanceResult.breakDamage` 带出来，见 §8.3）；
  - 真正"不算一次攻击"的只有附加伤害 / 真伤（`Damage.notCountsAsAttack()`，
    由 `Battle.applyAdditionalDamage` / `applyTrueDamage` 置位），它们不削韧、不广播、**受击方**不回能；
    但**击杀时仍给攻击者回能**（见 §9.2 的两条口径）。
  - **多目标时逐目标触发**：AOE/扩散/弹射对每个受击目标各自结算，超击破也**每目标各产生一发**
    （各自用自己的剩余韧性算超出部分）。
- 附加伤害/真伤段**不经过 `SkillExecutor`**，所以不会递归触发 `AttackEvent`
  ——这正对上官方定义"不视为造成了 1 次攻击"。

---

## 5. 行动条（`Queue` / `Signal`）✅

### 5.1 模型

`Queue` 用 **绝对时间 + 最小堆**（`PriorityQueue<Signal>`）而不是相对剩余值：

- 每个 `Signal` 同时记两个量：
  - `nextActionTime`（**绝对全局时间**，堆的排序键）；
  - `remaining`（**距离行动点还剩多少行动值**，速度无关）。
- 一个行动周期 `cycleTime() = 10000 / speed`（`Queue.ACTION_THRESHOLD = 10000`）。
- 全局时钟 `elapsed`：`move()` 把 `elapsed` 推进到堆顶的 `nextActionTime`，把堆顶设为 `currentActor`，
  并按推进量扣减所有信号的 `remaining`。
- `setTopZero()`：把**行动者**（`currentActor`，不是堆顶）重置为 `elapsed + cycleTime()`，然后清空 `currentActor`。
- 展示用派生值：`timeRemaining = max(0, nextActionTime - elapsed)`；
  `actionLength = max(0, 10000 - timeRemaining × speed)`。

#### 为什么 `remaining` 要单独记（而不是用百分比）

首轮（§5.4）的一次预约被拉长到 `1.5 × cycleTime()`，于是"预约长度"这个分母在首轮和之后不同
（速度 100 时是 150 vs 100）。**距离是速度无关的**：行动条上"还差多少格"不随速度变化，
速度只决定"每单位时间走几格"。所以速度变化时按
`新 remaining = (旧 remaining / 旧预约长度) × 新预约长度` 换算 —— 已经走掉的进度不变，
没走完的那部分按新速度重算。

反过来，如果把进度记成百分比，速度一变就没法同时处理"分母换了"和"分子要缩放"两件事，
首轮系数会被算成 `nextActionTime / cycleTime() = 1.5`，clamp 之后变成"立刻行动"。

### 5.2 行动条操纵

| 方法 | 语义 |
|---|---|
| `delayAction(target, amount)` | 推条：`nextActionTime += amount`（击破用 25% 周期） |
| `advanceAction(target, amount)` | 拉条：`nextActionTime = max(elapsed, nextActionTime - amount)` |
| `advanceActionByPercent(target, pct)` | 按剩余时间百分比提前（`p ∈ [0,1]`），**两侧都 clamp** |
| `resetSignal(signal)` | 重置到 `elapsed + cycleTime()` |

`Battle` 侧对应 `delayMovePercent(target, percent)`（周期 × percent）与
`advanceRequest(target, rate)`（排队后由 `processRequests` 处理）。

### 5.3 速度变化即时重排（P7 修正 E2）✅

速度变化 **立刻**反映到行动条上：触发点只有两个 —— `CanHit.setAttribute(SPEED, …)`（数值真的变了才通知）
与属性型 buff 的 `applyEffect/removeBuff`（它们显式调 `notifySpeedChanged()`）。
`CanHit.notifySpeedChanged()` → `Battle.onSpeedChanged` → `Queue.refreshSpeed(target)`。

语义是"**已经走掉的进度不变，剩余等待按新速度重算**"（见 §5.1）。两个边界：

- 刚行动完（进度 0）→ 按新速度重排整整一轮；
- 刚好要行动 → **预约长度不打折**：加速不会让人凭空提前，只是把等待等比缩短。

### 5.4 首轮行动值 150 / 后续 100（P7-1）✅

- 一轮 = 100 行动值；**首轮 = 150**。
- 实现上不是"整体延后 150"，而是 **每个单位的第一次预约乘 1.5**：
  速度 100 的单位首轮等 150，之后每 100 动一次；速度 200 的单位首轮等 75。
  所以首轮里高速单位能多动几次（速度 240 周期 41.67，150 之内能动 3 次）。
- **只在 `Queue.initialize()`（战斗开场）施加**；`setTopZero()` / `addCombatant()` 之后都按正常周期排队
  （`Signal.endFirstRound()` 会在该信号第一次行动后清掉系数）。
- `Queue.getRound()` 按累计 `elapsed` 分轮，区间**闭右端**：
  第 1 轮 `[0, 150]`、第 2 轮 `(150, 250]`、第 3 轮 `(250, 350]` ……

### 5.5 同行动值裁决（P7 修正 E4）✅

`nextActionTime` 相等时 **`compareTo` 比排期序号**（`Signal.sequence`，全局递增，越小越先）：

```
先比 nextActionTime；相等 → 比 sequence（先排期的先行动）
```

为什么需要：`PriorityQueue` 只保证堆顶是最小元素，**相等元素的先后是未定义的**；
而 `snapshot()` 原来是对堆数组做稳定排序 —— 于是"两个同速单位谁先出手"变成碰运气，
而且**显示出来的顺序可能不等于实际出手顺序**。

序号在什么时候换：

| 时机 | 是否换号 | 理由 |
|---|---|---|
| `addCombatant()`（建 Signal） | ✅ | 按入场顺序发号 |
| `setTopZero()`（行动后重新预约） | ✅ | 行动者排到同级末尾 |
| `resetSignal()` | ✅ | 等价于重新排期 |
| `initialize()` | ❌ | 它迭代的是 **heap 内部数组**，顺序由堆结构决定；在这里换号会破坏"同速按入场顺序出手" |
| 推条 / 拉条 / 按比例拉条 | ❌ | 只改行动值。若拉条也换号，就变成"谁刚被拉条谁先手" |

`Queue.snapshot()` 现在**直接复用 `Signal.compareTo`** 排序（不再另写一份比较规则），
所以"显示顺序 == 出手顺序"。

### 5.6 额外回合（P7-2）✅

`Battle.grantExtraTurn(actor)`：下一次 `stepForward()` 由他行动，**时钟不动**
⇒ 不消耗行动值、轮次不变、**他的正常回合排期原封不动**。

```
   t=0    grantExtraTurn(actor)                  他原本排在 107.14
   t=0    stepForward() → actor 行动             时钟仍为 0（move() 返回 0）
   之后   actor 的周期被 setTopZero() 重排到 71.43
   下一次 stepForward()：先还原成 107.14 → fast 在 75 行动 → actor 在 107.14 行动
```

**与"拉条"的区别**（最容易混淆的一点）：

| | 拉条 `advanceActionByPercent` | 额外回合 `grantExtraTurn` |
|---|---|---|
| 对正常回合 | **消耗**它（提前到当前） | 保留它，额外白送一次 |
| 时钟 | 可能前进 | 不动 |

实现要点（三处，都在 `Queue` 里）：

1. `grantExtraTurn()` 记下他**当时的** `nextActionTime`（`extraTurnOriginalTime`），
   并在 `move()` 里把他的行动时间**临时**按到 `elapsed`，让下游
   （`Battle.afterMove()` → `setTopZero()`）按"他此刻就是队首"正常收尾。
2. **信号必须留在堆里**（只改键 + 重建堆）。取出去的话 `setTopZero()` 的
   `heap.remove(acting)` 会失败，行动者被静默丢掉。
3. 原本的排期**不能**在额外回合里还 —— 还了之后堆顶又是他，下一次 `move()` 会直接推他的
   **正常**回合。所以推迟到下一次 `move()` 开头（`pendingRestore`）。

规则与边界：

- 每次 `grantExtraTurn` 只生效一次，重复调用同一个目标等价于一次（不会攒多次）。
- 同一时刻只有一个人持有额外回合，再给别人会**替换**掉上一个。
- 目标已死 / 不在队列里 → `false`，没有额外回合。
- 拿到额外回合之后死掉 → 这次额外回合作废，正常推进继续（不会卡住行动条）。
- **额外回合期间禁止插入别人的终结技**（`Battle.castUltra` 里拦）；额外回合本人可以放。
  不拦的话"额外回合"能被终结技无限续下去。
- 额外回合里 DOT 会照常结算（它按"回合"递减，额外回合是一次真正的回合）。

### 5.7 已知薄弱点 ⚠️（尚未修）

- **`Signal.remaining` 与 `nextActionTime` 是两份状态**：除法/乘法不是精确二进制运算，
  多次 `refreshSpeed` 后两者会有 ulp 级漂移。目前 `remaining` 只在"重排"时被读，
  而 `nextActionTime` 是唯一的排序键，所以漂移不影响出手顺序，但将来若要拿 `remaining` 当权威值，
  得先合并成一个字段。
- **`Signal` 仍实现 `Cloneable` 且 `clone()` 是浅拷贝**：克隆会连 `sequence` 一起复制，
  两个克隆同时进堆就会有相同序号。目前没有任何调用方，属预防性备注。

---

## 6. 回合流程（`Battle`）✅

```
startBattle()           → 给每个单位发 onBattleStart，然后 processRequests()
  ↓
stepForward()           → queue.move()，currentMove = queue.getCurrentActor()
  ↓
beforeMove()            → [敌人] tickDots(enemy) → buffManager.beforeMove() → actor.beforeMove(this)
  ↓
performAction(skill, targets) / useSkill(...) / castImmediate(...) / castUltra(...)
  ↓                     （技能进队列，processRequests() 时统一结算）
afterMove()             → 死亡则 clearAll()+移出队列；存活则 setTopZero()
                       → actor.afterMove(this) → buffManager.afterMove() → processRequests()
```

### 6.1 请求队列 ✅

改动战斗状态的操作**不立即执行**，而是排队，由 `processRequests()` 按固定顺序处理：

```
processSkillRequests() → processAddRequests() → processAdvanceRequests() → removeDeadCombatants()
```

三种请求：`SkillRequest`（技能）、`addRequestItems`（新增参战者 🚧 目前是死代码）、
`AdvanceRequest`（拉条 ⚠️ 目前无生产调用者）。

`castImmediate(skill, user, targets)` 是**绕过队列**直接执行的测试/演示入口。

### 6.2 死亡处理 ✅

- HP 只在 `CanHit.takeDamage(double)` 里减，减到 ≤0 置 `death = true` 并把 HP 夹到 0。
- `removeDeadCombatants()` 把死者移出 `Queue`，并清空其 buff（`clearAll()`）。
- **死者仍留在 `battle.characters` / `battle.enemies` 列表里** → 所有需要"活人"的地方必须自己判
  `isDeath()`；`Battle.targetableEnemies()` 是"谁可以被选为目标"的**唯一出口**。
- `isInvulnerable()`（转阶段无敌）与死亡正交：无敌目标**仍然可被选中**（AOE 会"打中"它、伤害为 0），
  但 `applyDamage` 不结算。

### 6.3 胜负判定 ✅ P7-3

`Battle` 带一个四态状态机：

```
  NOT_STARTED ──startBattle()──▶ RUNNING ──一方全灭──▶ WIN / LOSE（终态，不回退）
```

- `Battle.getStatus()` / `Battle.isOver()` 是查询口；`checkResult()` 是判定口（幂等）。
- 判定时机：`removeDeadCombatants()` 末尾（清完尸体顺手判），以及 `startBattle()` 开头。
- **终态之后 `stepForward()` 不再推进**行动条（`isOver()` 直接返回），
  所以"打完之后时钟还在走"这种问题不会再出现。
- 口径：
  - 某一方**全灭**即负 —— `allMatch(isDeath)`，所以**空列表也算全灭**（被清光了）；
  - `NOT_STARTED` 时**不判**（战斗还没开场，谈不上胜负）；
  - 两边同时全灭 → **LOSE**（先判负后判胜，且有终态保护，不会来回改判）。
- ⚠ 死者仍留在 `characters` / `enemies` 列表里，所以判定用的是列表上的 `isDeath()`，
  而不是"队列里还有没有人"（尸体早就被移出行动条了）。

---

## 7. 技能系统 ✅（数据层完整，槽位映射是占位）

### 7.1 数据链

```
skills.json[cid][槽位] ──Gson──▶ beans.Skill（record）
    attack_type      → String（"Normal"/"BPSkill"/"Ultra"/"Maze"/"MazeNormal"/"Assist"/"ElationDamage"/null）
    max_level        → 等级上限
    param_list       → List<List<Double>>，按等级索引（level-1）的倍率/参数表
    skill_effect     → String（映射到 SkillEffectType）
    skill_id         → int
    stance_list      → {single, all, spread}（削韧值，单位「点」）
    element          → DamageElement（"Unknown" → Gson 读成 null）
```

`models/SkillData` 是运行时视图（`SkillData.init(cid, skillID)` 从 `Constant.SKILLS` 解析），
字段全部 `final`，但 `skills`/`stanceList` 是 Gson 造的可变对象（javadoc 声称 immutable，实际不是）。

`models/DefaultSkill` 是**唯一的 `Skill` 生产实现**，`getData()` 用 `static ConcurrentMap` 按
`cid + "_" + skillId` 缓存；`execute()` 一行委托给 `SkillExecutor`。

### 7.2 技能槽位映射 ✅（P8-2 的槽位部分，2026-09-21）

映射表只有一份：`Constant.SKILL_SLOT`。

| `SkillType` | 槽位 | 数据里的攻击类型 | 何时装配 |
|---|---|---|---|
| `COMMON` | **1** | `Normal` | 造角色时（常驻） |
| `SKILL` | **2** | `BPSkill` | 造角色时（常驻） |
| `ULTRA` | **3** | `Ultra` | 造角色时（常驻） |
| `TALENT` | **4** | 空（`null`） | 造角色时（常驻） |
| `MAZE` | **6** | `MazeNormal` | **`Battle.startBattle()` 附加** |
| `TECHNIQUE` | **7** | `Maze` | **`Battle.startBattle()` 附加** |

数据约定：**1 普攻 / 2 战技 / 3 终结技 / 4 天赋 / 5（数据里不存在）/ 6 地图普攻 / 7 秘技**，
且 `skill_id = 角色id × 100 + 槽位`（638 条技能**全部**满足，已核对）。

**为什么地图普攻/秘技不在造角色时装**（`SkillType.isIntrinsic()` 是那条分界线）：
它们是**地图上的东西**，只在"进入战斗"这一刻才有意义 ——
地图普攻（槽位 6）是大地图上打怪、以及"进入战斗时削韧"那一下，
而**战斗内普攻是槽位 1** 的 `Normal`，两者不是一回事。
所以 `CharacterFactory` 造出来的角色身上没有它们，`Battle.startBattle()` 才挂上
（`attachBattleSkills()` 只给我方角色、且不覆盖已显式装过的）。

> ⚠ 秘技的**效果**（例如景元"下一场战斗开始时【神君】+3 段"）还没做 ——
> 那要等 P8-6 的事件补齐 + 触发器表。目前只把技能本身挂上（数据可读、可执行）。

> ✅ 已修：修之前 `Character.Builder.build()` 与 `Character.fromAttributes(...)` 都写死
> `new DefaultSkill(cid, 1, level)` —— **六个槽位解析到的全是槽位 1（普攻）的数据**，
> 于是"战技/终结技/天赋"的倍率、削韧、元素、`sp_need` 全是普攻的。
> ⚠ 这个 bug 长期没被发现，因为既有测试（`SkillExecutorTest`/`SuperBreakTest`）都**自己构造**
> `new DefaultSkill(cid, 槽位, …)`，而 `EnergyTest` 验的是 provider 分派 ——
> "角色实际拿到什么技能"这条路没人走过。现由 `SkillSlotMappingTest` 覆盖。

✅ **技能等级是接进伤害的**：`SkillExecutor` 用 `int index = skill.getLevel() - 1` 取
`SkillData.getSkills()`（整张逐级表）的第 N 行，所以 8 级打的是第 8 档倍率。
端到端验证在 `SkillSlotMappingTest.skillLevelScalesActualDamage`：景元普攻
8 级 / 1 级的伤害比 = `1.2 / 0.5 = 2.4`。

> ⚠ **更正**：我在上一版这里写过"等级还没接进伤害"——**那是错的**。
> 起因是我构造了 8 级技能却断言 `getData().getSkills().getFirst()` 是 1.2，
> 而 `getSkills()` 返回整张表，取 `getFirst()` 当然还是第 1 档：
> **我把"自己取错行"当成了"引擎没取行"。**
> 教训：别用 `getFirst()` 去验证"某一档次"的东西。

⚠ 注意**装配出来的角色默认技能等级是 1**（`Builder.skillLevel` 初值），
要更高得调 `skillLevel(type)` 或 `setSkillLevel(type, level)` —— 技能等级属于 P8 的成长系统
（行迹/星魂加等级），不是本项范围。

`SkillData.init` 在查不到 `cid`/槽位时返回 `EMPTY`（`PHYSICAL` + `ENHANCE` + 空参数）
→ 因为 `ENHANCE` 不是伤害类，技能会**静默零伤害**且 `requestSkill` 仍返回 `true`。这是易踩的坑。

### 7.2b 非伤害技能：静默不分派 + 可开关诊断 ⚠（P8-2）

`SkillExecutor.resolveHits` 在"不是伤害类技能"时**直接 return** —— 也就是说
治疗 / 护盾 / buff / 控制 / 召唤技能**被施放后什么都不发生**（只有**回能**照给，见 §7.4）。

这是**分阶段设计**，不是遗漏：各效果的宿主在别处。

| 效果类别 | 谁负责 | 现状 |
|---|---|---|
| `RESTORE`（治疗） | `Battle.heal`（P6-2） | ✅ 已实现，**但没有任何地方自动分派** —— 要调用方自己调（`Main` 就是这么做的） |
| `DEFENCE`（护盾） | `Battle.grantShield`（P6-3） | ✅ 已实现，同样靠调用方分派 |
| `SUPPORT`（增益） | P10-3 Buff 体系 | ❌ |
| `IMPAIR`（控制/减益） | P10-6 | ❌ |
| `SUMMON`（召唤） | P9-4 | ❌ |
| `ENHANCE` | 纯被动 | 本就不该作为"行动"施放 |

**这个静默很难察觉**：日志上技能"放出去了"、能量也涨了，只是没有任何效果。
所以加了一个**默认关闭**的诊断开关：

```java
SkillExecutor.setLogNotDispatched(true);   // 排查时打开
// → [SkillExecutor] 未分派：BPSkill / RESTORE（Natasha，目标 1 个）
//   → 归属 P6-2 已实现（走 Battle.heal，不经本执行器）
```

> ⚠ 计划里原本想用 `IO.println` **无条件**打印，实测会刷屏（demo 每回合都在治疗/护盾），
> 故改为开关。护栏：`SkillExecutorDiagnosticTest` —— 其中
> `diagnosticDoesNotChangeBehaviour` 明确断言"**打开日志后治疗仍然不生效**"，
> 把"加日志 ≠ 实现效果"钉住。

### 7.3 技能展开（`SkillExecutor`）✅

唯一入口 `SkillExecutor.execute(battle, skill, user, targets)`，调用方**只给主目标**
（`targets.getFirst()`）——"打几个"是技能属性，不是调用方的选择：

| effect | 受击集合 | 每段削韧取值 |
|---|---|---|
| `SINGLE_ATTACK` / `MAZE_ATTACK` | 主目标 | `stance.single()` |
| `AOE_ATTACK` | `battle.targetableEnemies()` 全部 | `stance.all()` |
| `BLAST` | 主目标 + 战场序列左右相邻各 1 | 中心 `single` / 相邻 `spread` |
| `BOUNCE` | N 段，每段从存活敌人随机取 | **`single() / N`（总值均摊）** |
| 其它（HEAL/BUFF/CONTROL/SUMMON/PASSIVE） | 直接 return（留 P6/P7/P9） | — |

判定顺序（都是被真实数据逼出来的）：

1. 先判 `effect.isDamaging()`，**再取 params** —— 护盾技的 `param_list[0][0]` 是护盾系数不是倍率。
2. 空参数真实存在（`param_list = [[]]`）→ 判空跳过，不能抛。
3. `isDamaging()` 但 `element == null` → **抛 `IllegalStateException`**（数据错误，fail fast 好过静默消失）。

每段独立走 `battle.applyDamage(...)`：**每段独立判定暴击、独立结算**。
`base = 攻击力 × params.getFirst()`（所有技能都是攻击力倍率，没有生命/防御倍率）。

削韧值现在由调用方算好传给 `hit(...)`，弹射类按段数均摊。

> ⚠️ **弹射段数的来源存疑**：代码用 `hits = (int)(double) params.get(1)`（缺省 1）。
> 但实测 `skills.json` 里这个位置**并不总是整数**——瓦尔特的 `0.65`、桑博的 `0.28`、景元的 `0.33`、
> 米沙的 `0.36`、菲农的 `0.225`、银狼 999 的 `0.0`，而砂金(1304) 的 `7`、大丽花(1321) 的 `5` 是整数。
> 强制转换会让那些小数**变成 0 段**（技能完全不出手）。今天无害（`DefaultSkill` 只解析槽位 1，
> 这些技能都在其它槽位），但接真实槽位映射前必须先搞清这个字段的真实语义。

### 7.4 技能回能 ✅

`SkillExecutor.execute` 里，**不管这条技能有没有伤害**（增益/护盾/治疗也回能），
也不管参数是否为空，施放结束都会调一次 `battle.grantSkillEnergy(user, skill, hitTargets)`。
规则由施放者自己的 `EnergyProvider` 决定（见 §9）。

---

## 8. 韧性 · 击破 · 持续伤害 ✅

### 8.1 韧性字段（`Enemy`）✅

| 字段 | 来源 |
|---|---|
| `stance` / `maxStance` | `EnemyScaler`：模板 `stance` × 等级组 × 实例系数 × 精英组 |
| `stanceWeak` | `monster_config.json` 的 `stance_weak`（弱点元素集合） |
| `stanceCount` / `stanceType` | 模板的 `stance_count` / `stance_type`（多韧性条：字段已有，机制未做 ❌） |
| `broken` / `brokenElement` / `brokenRemainTurns` | 击破状态机 |

两个判定唯一出口：`isWeakTo(element)`（弱点）、`hasToughnessBar()`（`maxStance > 0`）。
`reduceStance(amount)` **归零不自动击破**（只扣数并夹到 0），并**返回实际消耗值**。

### 8.2 削韧判定 ✅

`Battle.reduceToughness(attacker, enemy, element, stanceDamage)` 是**唯一削韧入口**，规则：

- **只有命中弱点才削韧**（非弱点元素一点都不削）；
- 已死亡 / 已击破 / 没有韧性条 → 不削；
- 只有"算一次攻击"的段才削（附加伤害/真伤被 `notCountsAsAttack()` 挡掉，`SkillExecutor` 里判）。

### 8.3 击破链（顺序固定）✅

韧性归零时由 `reduceToughness` 依次触发：

```
breakEnemy(element)                                    标记击破状态
setBrokenRemainTurns(2)                                击破持续 2 回合
applyDamage(BreakDamageCalculator.build(...))          击破伤害
delayMovePercent(enemy, 0.25)                          推条 25% 周期
attachBreakDot(attacker, enemy, element)               挂 DOT（仅火/雷/物理/风）
gainBreakEnergy(attacker, enemy)                       击破回能
```

**击破伤害公式**（`BreakDamageCalculator`）：

```
base = 击破基数(攻击者等级) × (1 + 击破特攻) × 实际削掉的韧性值
Damage(type = BREAK)   → 走完整装配，但 BoostArea/CritArea 自动跳过
```

- 击破基数 = `breaking_rate.json[等级] / 10`（数据文件是 10 倍值；80 级 = **376.75535**）。
- 等级没有数据 → **抛 `IllegalArgumentException`**（fail fast）。实测等级表覆盖 1–100 与 120，与等级组一致。
- **单位口径**：本项目统一用「点」刻度（普攻 = 30 点 = 3 个单位），所以基数必须先 `/10`。

### 8.4 击破状态与恢复 ✅

- 击破的敌人**轮到自己回合时**要由调用方调 `Battle.handleBrokenTurn(enemy)`：
  递减剩余回合，到 0 则 `recoverFromBroken()`（韧性回满、清 `brokenElement`），并返回 `true` 表示"本回合被跳过"。
- ⚠️ **这个方法目前只有 `Main` 的 demo 在调**，`Battle` 自己不会调；"击破跳回合"依赖调用方自觉，
  真正的敌方回合执行在 `P5-5`。
- 多韧性条（`stanceCount > 1`，如 8025011 是 3 条）**未实现** ❌，只恢复满值。

### 8.5 持续伤害 DOT ✅

`models/Dot` = `{来源, 元素, 每次基础伤害, 剩余结算次数}`，挂在 `Enemy.dots`（**List 不是 Set**，
因为规则是"先上先结算"）。

- **挂载条件**：击破元素 ∈ `Constant.DOT_ELEMENTS = {FIRE, THUNDER, PHYSICAL, WIND}`；
  冰（冻结）/量子（纠缠）/虚数（禁锢）属控制类，不挂 DOT（P10-1 统一）。
- **每次结算伤害** = `击破基数 × Constant.DOT_RATIO(0.5)`
  —— 注意这个 base **既不含击破特攻、也不含削韧值**（公式里那个 `削韧值` 因子在这里没有出现，
  只有击破基数本身）。物理裂伤在游戏里还按目标生命上限算，这里也统一成了同一个式子。
- **结算时机**：敌人**回合开始时**（`Battle.beforeMove` 里 `tickDots(enemy)`），
  按施加顺序逐个结算；被 DOT 打死则剩下的不再结算（不鞭尸）。
- DOT 伤害通过 `Damage(type = DOT)` 走完整装配 → 吃增伤、吃防御/抗性/易伤，**不可暴击**。
- 🚧 `DOT_RATIO = 0.5` 与 `DOT_TURNS = 3` 是**示例值**（代码注释标了 `TODO data`），
  两个都还没有真实数据来源。

### 8.6 超击破 ✅（P4-6，2026-09-19 实现）

触发标记：`models/buffs/SuperBreakBuff`（挂在**攻击方**身上，**纯标记、没有数值**）。
一旦有它，攻击已击破的敌人时，"打不进韧性条的那部分削韧值"会转化成一段
`DamageType.SUPER_BREAK` 伤害。

**削韧值的分配**（设剩余韧性 `T`、技能标称削韧 `S`）——`Battle.StanceResult` 把它拆成两个数：

| 情形 | 击破伤害用 | 超击破伤害用 |
|---|---|---|
| 未击破 且 `T > S` | 无 | 无 |
| 未击破 且 `S ≥ T` | `consumed = min(S,T)` | `overkill = S − consumed` |
| 已击破（`T = 0`） | 无 | `S`（整发都算超出） |

相加恒等于 `S`，不重不漏。所以"破韧的那一发"会**同时**产生击破伤害与超击破伤害
（例：技能 60、怪物 30 韧性 → 技能伤害 + 30 击破伤害 + 30 超击破伤害）。没有 buff 时
超出部分才是真的浪费。

**公式**（`BreakDamageCalculator.buildSuperBreak`）：

```
base = 击破基数(等级) × (1 + 击破特攻) × 超出部分 × (1 + Constant.SUPER_BREAK_BOOST)
Damage(type = SUPER_BREAK)   → 走完整装配，但 BoostArea/CritArea 自动跳过
```

- 与击破伤害的差别只有：输入是**超出部分**、多乘一个独立增伤、类型不同。
- **不受"只有弱点才削韧"限制**：敌人**已处于击破状态**时整发削韧都算超出，
  **与元素是否命中弱点无关**（官方条件的措辞里只有"敌人处于弱点击破状态"）。
  但**未击破**时非弱点攻击什么都不产生（那一步仍然严格只有弱点才削）。
- **多目标逐目标触发**：AOE/扩散/弹射对每个受击目标各自算超出部分、各产生一发超击破。
- 击破 / 超击破段**不置** `notCountsAsAttack()` —— 它们是同一次攻击行为的一部分，
  不是独立的一次攻击（见 §4 的"行为 vs 伤害类型"）。
- 击破伤害的结算值由 `StanceResult.breakDamage()` 带出，超击破段由 `SkillExecutor` 追加结算，
  两者都累加进本次攻击的 `AttackEvent.totalDamage`。
- 🚧 `SUPER_BREAK_BOOST = 0.4` 是**示例值**；公式里的 `(1 + 削韧值提高)` 与
  `(1 + 弱点击破效率提高)` **尚未实现**（这两个属性在 `AttributeType` 里还不存在，
  它们同时也会修正 §3.2 的破韧公式）。

---

## 9. 能量系统 ✅

### 9.1 字段与入账口（`CanHit`）✅

| 字段 | 说明 |
|---|---|
| `currentEnergy` / `maxEnergy` | `maxEnergy == 0` ⇒ **没有能量条**（如 1407 遐蝶，她的资源是【新蕊】，见 §9.4 / §9.5），任何回能都是 no-op |
| `energyProvider` | 默认 `StandardEnergyProvider`，特殊角色各自实现 |

唯一入账口 `gainEnergy(EnergyGain gain)`：

```
实际入账 = min(maxEnergy - currentEnergy, 基础值 × (1 + 能量恢复效率%))
```

`EnergyGain.affectedByEfficiency()` 为 `false` 时不吃效率（`EnergyGain.fixed(...)`，如"按上限百分比回能"）。
返回的是**实际入账值**（被上限截断后），不是理论值。

> ⚠️ 已知缺陷（未修）：`ENERGY_REGENERATION_RATE ≤ -1` 或 `currentEnergy > maxEnergy` 时
> `added` 会为负（"回能"反而扣能量）；`amount()` 为 `NaN` 时守卫拦不住，会永久污染 `currentEnergy`。

### 9.2 回能规则（`EnergyProvider`）✅

接口 5 个钩子，全部 `default` 返回 `null`（= 不回能）：

| 钩子 | 调用点 | 标准值 | 触发条件 |
|---|---|---|---|
| `onSkillCast(user, skill, hitTargets)` | `SkillExecutor.execute` | 普攻 **20** / 战技 **30** / 其它 **0**（常量） | 每次施放一次（**不是**每段） |
| `onUltCast(user, skill)` | `Battle.castUltra`（清零后） | **5**（常量） | 放出终结技 |
| `onTakingHit(target, damage)` | `applyDamage` | **10**（常量） | **没死** 且这一发「算一次攻击」 |
| `onKill(attacker, target)` | `applyDamage` | **5**（常量） | **打死了**，记录给 `damage.getAttacker()`；**与伤害类型无关** |
| `onBreak(attacker, target)` | `reduceToughness` | **5**（常量） | 触发击破 |

**为什么这些值仍是常量**（2026-09-21 试过又退回）：数据里有 `sp_base`
（tbgd `AvatarSkillConfig.SPBase`），但**多段/弹射技能的 `sp_base` 是"每段"值**
（艾丝妲 6、瓦尔特 10），乘段数才对，而段数乘算依赖能力配置的 `SPHitRatio`
（**本项目数据里没有**）。直接取原值会让那 6 个角色偏低，而常量给出的正是**正确总量**。
数据化的正路见 ROADMAP P3-4（先聚合 `SPHitRatio`）。

**受击回能与击杀回能是两条不同的口径，别用同一个开关卡**（2026-09-19 修正）：

| | 受击回能（给挨打方） | 击杀回能（给 `damage.getAttacker()`） |
|---|---|---|
| 门槛 | ① 这一发必须「算一次攻击」② 必须是**这一次攻击的主段** | **只看有没有打死**，与伤害类型、与是否主段都无关 |
| 附加伤害 / 真伤 | ❌ 不给 | ✅ 照给 |
| 击破 / 超击破段 | ❌ 不给（派生段） | ✅ 照给（主段没打死、它补刀时） |
| DOT | ❌ 不给（DOT 不是"一次攻击行为"） | ✅ 照给 |

**"一次攻击行为只给受击方回一次能"**：一次攻击可以派生多种伤害类型
（技能伤害 → 击破伤害 → 超击破伤害），如果每段都给受击方回能，受击方就会因为
"被打得更狠"而回更多能。所以只有**主段**（角色主动施放技能的那一段）发受击回能；
按**受击目标**计数 —— AOE 打 3 个目标，每个目标各回 1 次。

实现上用两条互补的机制表达"派生段"（因为两个派生入口的结算上下文不同）：

| 派生段 | 怎么表达"不给受击回能" | 为什么 |
|---|---|---|
| 击破伤害 | `Battle.reduceToughness` 显式传 `EnergyGrant.KILL_ONLY` | 它在 `Battle` 内部结算，能直接传参数；因此**不置** `notCountsAsAttack()`，保住"击破伤害是攻击伤害"的语义 |
| 超击破伤害 | `BreakDamageCalculator.buildSuperBreak` 里置 `notCountsAsAttack()` | 它由 `SkillExecutor` 经**公开入口** `applyDamage` 结算，没地方传参数 |
| 附加伤害 / 真伤 | 同上（`notCountsAsAttack()`），并显式传 `KILL_ONLY` | 官方定义"不视为一次攻击" |
| DOT | 显式传 `EnergyGrant.KILL_ONLY` | 不是攻击行为 |

`EnergyGrant` 是 `Battle` 的私有枚举（`ALL` / `KILL_ONLY` / `NONE`），
刻意不做成公开重载 —— 那会把内部回能策略泄漏成公开 API 决策点，并破坏
"公开 API 里接收 `Damage` 的方法只能有一个"这条约束（`DamagePipelineTest` 有断言）。

理由：附加伤害与真实伤害「不视为造成了 1 次攻击」，所以**受击方**不该因此回能；
但"击杀"这件事确实发生了，而该伤害**归属某个角色**，所以那个角色拿击杀回能。
任何归属到角色的伤害（普攻、战技、终结技、击破、超击破、DOT、附加伤害、真伤）
只要打死了怪，都会走 `onKill` —— 引擎不需要逐个类型枚举。

`StandardEnergyProvider` 按 `SkillData.skillType`（即 `attack_type`）switch：
`"Normal"` → 20，`"BPSkill"` → 30，`default` → 0（含 `"Ultra"`，它走 `onUltCast`）。
**`attack_type` 为 `null` 的槽位（天赋/追加攻击）必须先判空再 switch**（真实数据里有 104 个 `null`，
直接 switch 会 NPE）。

### 9.3 终结技流程 ✅

```java
castUltra(user, targets):
  user 为 null / 死了 / !isEnergyFull()  → false
  没有 ULTRA 槽技能                        → false
  requestSkill(...)
  user.setCurrentEnergy(0)                 ← 先清零
  processRequests()                        ← 再结算本体（击杀/击破回能因此能留下）
  onUltCast → 回自身（读数据，终结技一律 5）
```

`isEnergyFull()` 要求 `maxEnergy > 0 && currentEnergy >= maxEnergy` —— 所以没有能量条的角色永远放不了大招。

**但真正的门槛是"开大阈值"而不是"满能量"**（P3-4 跟进）：`Battle.isUltraReady(user)` 判
`currentEnergy >= ultraEnergyCost(user)`，而 `ultraEnergyCost` 优先读技能数据的
`spNeed`，缺失时退回 `maxEnergy`。93 个角色里有 5 个阈值**低于**上限：

| 角色 | 需要 | 上限 |
|---|---|---|
| 云璃 1221 | **120** | 240 |
| 银枝 1302 | **90** | 180 |
| 绯英 1505 | **240** | 480 |
| 飞霄 1220 | **6** | 12 |
| 昔涟 1415 | **12** | 24（层数，见 §9.5） |

角色文档写的是「**释放所需能量** 120（上限 240）」——"所需"是门槛。
放开后**清零**：对"阈值 == 上限"的多数角色与老行为等价，对上面的例外等价于"消耗掉阈值那部分"。
`Main` 的 demo 也用 `battle.isUltraReady(...)` 判，不再自己看 `isEnergyFull()`。
护栏：`UltraThresholdTest`（含"恰好 5 个角色阈值低于上限"的穷举登记）。

### 9.4 特殊供能角色走独立 provider ✅（2026-09-21）

**6 个角色不走常规能量**，而是层数/特殊资源：

| 角色 | `maxEnergy` | 实际资源 |
|---|---|---|
| 飞霄 1220 | 12 | 层数（开大阈值 6） |
| 黄泉 1308 | 9 | 层数 |
| 遐蝶 1407 | **null（无能量条）** | 【新蕊】 |
| 白厄 1408 | 12 | 【火种】 |
| 昔涟 1415 | 24 | 【追忆】（见 §9.5） |
| 银狼LV.999 1506 | 60 | 欢愉体系 |

他们在 `CharacterFactory` 装配点上被换成
`NoConventionalEnergyProvider` —— 5 个钩子**全部**返回
`null`（任何来源都不入账）。

**为什么必须拦满 5 个钩子，而不是只改技能那两条**：`castUltra` 的门槛是
`currentEnergy >= maxEnergy`，而他们的上限很低（黄泉 **9**、飞霄/白厄 **12**）。
只堵 `onSkillCast`/`onUltCast` 的话，`onTakingHit`（+10）等三条照发 ——
**黄泉挨一下就能凑满并放出一个本不该存在的终结技**（他们槽位 3 确实是 `Ultra`）。
护栏：`SpecialEnergyProviderTest`（provider 全钩子 + 工厂注入 + 端到端"能量恒为 0"）。

**为什么判定放装配点而不是 `StandardEnergyProvider` 里**：这是**设计归类**
（"这个角色不用常规能量体系"），不是单条数据事实。P8-0 的三分法把这类判断归给
provider / 装配点，那也是唯一允许出现 `cid` 的地方。等 P8-8 的 `Resource` 抽象落地，
把这张 `cid` 表演化成"角色 → 资源实现"的注册表即可。

**与 `maxEnergy == 0` 的区别**：那是**没有能量条**（遐蝶），`CanHit.gainEnergy` 本身即
no-op，用不用这个 provider 都一样；本 provider 管的是"**有**能量池但不该从常规途径涨"的角色。

#### `sp_base` / `sp_need`：已读进模型，但**不驱动回能**

`SkillData` 带两个字段（`Skill` bean 直读数据）：

| 字段 | 来源 | 含义 |
|---|---|---|
| `spBase` | `sp_base` | 施放这个技能回多少能量（tbgd `AvatarSkillConfig.SPBase`） |
| `spNeed` | `sp_need` | **开大阈值**（只有终结技有值） |

⚠ **回能仍走常量**，没有用 `spBase`：数据里多段/弹射技能的 `sp_base` 是**每段值**
（艾丝妲 6、瓦尔特 10），乘段数才对，而段数乘算要能力配置的 `SPHitRatio`（本项目没有）。
直接用原值会让那 6 个角色偏低，常量反而是**正确总量**。数据化的正路见 ROADMAP P3-4。

`spNeed` 也**还没接进 `castUltra`**：93 个角色里有 5 个 `sp_need ≠ max_energy`
（云璃 240/120、银枝 180/90、绯英 480/240、飞霄 12/6、昔涟 24/12）。
其中云璃/银枝/绯英是标准能量、只是"攒到阈值就能放"，所以引擎现在会让他们攒过头。

> ✅ 已修：早先这里记着"`max_energy` 读出来了但从未接进角色，数据造出来的角色全都没有能量条"。
> P8-1 已在 `Character.Builder.build()` 里接上（`null → 0`，不兜底成 100），
> 所以 `CharacterFactory.create(1204, 80).getMaxEnergy() == 130`、`昔涟 == 24`。

### 9.5 昔涟 1415：不是能量系统，是【追忆】层数 ⚠（P8-8）

她的面板写着「最大能量 24」、终结技行写着「释放所需能量 12（上限 24）」，
但读角色文档（`1415_昔涟.md`）后结论完全不同 —— **她攒的是【追忆】点数，不是能量**：

- 天赋：战斗开始或昔涟行动后，其他队友及其忆灵获得【未来】；持有者行动时消耗【未来】，
  使昔涟获得 **1 点【追忆】**。
- **【追忆】达到 24 点可激活终结技**；进入【往昔的涟漪】状态后，**达到 12 点**即可激活；
  池子 24，可溢出至 **27**。
- 终结技（`sp_need = 12`，参数 `[1, 24, 0.25, 12]`）还会**激活全体队友的终结技**，
  且**单场战斗只能放 1 次**。
- 数据侧印证：她的普攻/战技/终结技 `sp_base` **全是 `null`** → 一个技能都不回能。

**所以数据里的 `sp_need = 12` 是"涟漪状态下的阈值"，不是"消耗 12"**；第一次的 24
只出现在天赋文本与参数表里。别把它读成"前 24 后 12 的消耗"。

引擎当前的表达力：`maxEnergy = 24` 读到了，但**没有【追忆】这个资源**，
`castUltra` 判的是 `currentEnergy >= maxEnergy` —— 对她只能靠外部灌能量模拟。
按 P8-0 的三分法，这属于"引擎还不具备的能力" → **P8-8 的 `Resource` 抽象**，不在 P8-2 里特判。

---

## 10. 增益与减益（Buff 体系）

### 10.1 生命周期 ✅

`BuffManager`（每个 `CanHit` 一个）持有 `List<AbstractBuff>`。

- `addBuff(buff)`：先移除**同类**旧 buff（`isSameKind` = `getClass()` 相同）并调其 `removeBuff`，
  然后写入持有者（`setOwner`）、加入列表、调 `applyEffect`。
- `removeBuff(buff)`：按**身份**移除（`AbstractBuff` 不重写 `equals`）并调 `removeBuff`。
- `canAct()`：`blocked` 标志 + 任一 buff 的 `canAct()` 为 false ⇒ 不能行动（控制效果）。

### 10.2 两种 buff，别混 ✅

| | 属性型（如 `BoostDamageBuff`） | 注入型（`VulnerabilityBuff` / `ReductionBuff`） |
|---|---|---|
| 持久状态 | 有：`applyEffect` 往属性挂 `Modifier`（用 buff 的唯一 `id` 作 `roleId`） | **没有** |
| `applyEffect` / `removeBuff` | 必须成对挂/摘 | **留空** |
| 生效方式 | 改面板，影响所有后续结算 | 每次结算时注入到那**一段** `Damage` 的乘区 |
| `tickEffect` | 递减时长 | 递减时长 |

照抄属性型去写注入型（在 `applyEffect` 里改属性）会导致"易伤对所有人生效 + 每次结算叠加"。
注入型的 `onDamage` **必须判侧**（`damage.isOnDefenderSide(owner)`），否则持有者打人也会被自己的易伤加伤。

### 10.3 时长与 tick 时机 ✅

- `AbstractBuff(duration, isEarlyBuff)`：`isEarlyBuff=true` 的在 `beforeMove()` 递减，
  `false` 的在 `afterMove()` 递减；每个拥有者回合**恰好 tick 一次**。
- 到期在 `processBuffTick` 里用 `removeIf` 移除；若到期的 buff 让 `canAct()` 为 false，
  置 `blocked = true`（"晕眩最后一回合仍然挡住行动"）。
- ⚠️ `blocked` 只在 `beforeMove()` 开头清零，所以**只对 early buff 生效**：
  后置控制 buff 在 `afterMove()` 到期时，它的最后一回合挡不住。`StunBuff` 恰好是 early 所以看不出来。
- ⚠️ `clearAll()`（死亡时调用）不重置 `blocked`，清空后仍可能 `canAct() == false` 直到下次 `beforeMove()`。

### 10.4 查询口 ✅

`BuffManager.hasBuff(Class<? extends AbstractBuff>)` —— 按 `getClass()` 精确匹配、`null` 返 false。
**不提供 `getBuffs()`**：遍历与判定留在 manager 内部，避免外部改列表导致 CME。

### 10.5 现存 buff 实现

| 类 | 类型 | 说明 |
|---|---|---|
| `BoostDamageBuff` | 属性型 | `ALL_DAMAGE_TYPE_BOOST` 加 `rate`（平值），用 `id` 精确摘除 |
| `VulnerabilityBuff` | 注入型 | 易伤，受击方负面，`ModifierSource.DEBUFF` |
| `ReductionBuff` | 注入型 | 减伤，受击方增益，`ModifierSource.BUFF` |
| `StunBuff` | 控制 | early buff，`canAct() == false` |
| `TestBuff` / `TestBuff1` | 测试替身 | 只打日志 |
| `WeaknessBuff` | ❌ 不存在 | 只有 `DamageHookTest` 里的内嵌测试替身（攻击方侧负面的代表） |

---

## 11. 增减伤 · 治疗 · 护盾 · 命中

| 机制 | 状态 |
|---|---|
| 增伤 / 易伤 / 减伤 / 虚弱 | ✅ 四个区都实现，前三个有事件钩子 |
| 暴击 / 防御 / 抗性 / 穿透 / 无视防御 | ✅ |
| **效果命中 / 抵抗** | ✅ P6-1：`Battle.hitChance` / `rollDebuff` / `tryApplyDebuff`，见 §20.1 |
| **治疗** | ✅ P6-2：`Battle.calculateHeal` / `heal`，见 §20.2 |
| **护盾** | ✅ P6-3：`CanHit.shield` + 先扣盾再扣血 + `Battle.grantShield`，见 §20.3 |
| **Debuff 基础概率** | 🚧 公式与入口已就绪（P6-1），但**技能数据里还没有"基础概率"字段** —— 各 debuff 技的概率值要等 P9/P10 数据化 |
| **控制异常状态机** | 🚧 只有最简单的 `StunBuff`（`canAct()=false`）；冻结/纠缠/禁锢等七系效果未做 |

---

## 12. 角色面板构建 ✅

### 12.1 属性来源链（`Character.Builder`）

```
基础值（character_data.json 的 Lv1 值）
  × 等级/突破缩放（LevelPromotionCalc）
  + 光锥基础值（Weapon，已按光锥等级缩放）
  + 遗器主副词条（RelicSuit）
  + 行迹（SkillPoint 树，point.json）
  + 额外加成（ExtraBasicPromote）
  + 基础双爆（character_data 的 crit_chance / crit_attack）
  → AttributeBuilder.build() → DoubleValue[]
```

### 12.2 等级缩放公式（`LevelPromotionCalc`）

**角色**：`rate = 1 + (level-1)×0.05 + max(0, promoteCount)×0.4`
　`promoteCount = level/10 - (突破 ? 1 : 2)`；`level==80 且突破` 再 `-1`；`level<=20 且未突破` 时归 0。

> ⚠️ **`max(0, …)` 是 P8-1 补的下界**：低等级配"已突破"原本会算出**负晋阶**
> （Lv1 → `1/10 - 1 = -1`），把 1 级面板压到 **0.6 倍**（景元生命 158.4 → 95.04）。
> 而 95.04 恰好是他的**攻击**值，所以现象看起来像"属性数组索引错位"，极易误诊
> （P8-1 时我就误诊了一轮）。1 级角色不可能"负晋阶"，所以下界必须是 0；见下表 Lv1 突破列。
> 上表其余档位（20/21/70/79/80/90）**不受影响**，作者验证过的锚点依然成立。

**光锥**：`rate = 1 + (level-1)×0.15 + 1.6×promoteCount + (首次突破 ? 1.2 : 0)`
　`promoteCount` 仅在 `level>20` 时算：`level/10 - (突破 ? 2 : 3)`；`level==80 且突破` 再 `-1`。

**测试锚点**（`LevelPromotionCalcTest`）：角色 `rate(1)=1.0`、`rate(20, 未突破)=1.95`、
`rate(80)=7.35`（默认重载无突破）；光锥 `rate(1)=1.0`、`rate(20, 未突破)=3.85`、`rate(20, 突破)=5.05`。

**实测各档位倍率**（我跑了探针打印的，不是推算）：

| 等级 | 角色 未突破 | 角色 突破 | 光锥 未突破 | 光锥 突破 |
|---|---|---|---|---|
| 1 | 1.00 | 1.00 | 1.00 | 1.00 |
| 20 | 1.95 | 2.35 | 3.85 | 5.05 |
| 21 | 2.00 | 2.40 | 3.60 | 5.20 |
| 40 | 3.75 | 4.15 | 9.65 | 11.25 |
| 70 | 6.45 | 6.85 | 18.95 | 20.55 |
| 79 | 6.90 | 7.30 | 20.30 | 21.90 |
| **80** | **7.35** | **7.35** | **22.05** | **22.05** |
| 90 | 8.25 | 8.65 | 25.15 | 26.75 |

两条要记住的特征：

- **80 级时"突破"标志不再区分**（两条路都收在 7.35 / 22.05）。这正是 `level==80 且突破` 那个额外 `-1`
  的作用，也是 `LevelPromotionCalcTest` 把两种调用**都断言成 7.35** 的原因 —— 不是笔误。
- **20 级时两者是分开的**（角色 1.95 vs 2.35；光锥 3.85 vs 5.05），所以"突破"在 20 级是有意义的。
- 光锥在 20→21 级之间，**未突破的倍率会掉**（3.85 → 3.60）—— 数据如此，不是 bug。

> ✅ **公式已用游戏数值验证过**（作者确认）。`character_data.json` 存的是 Lv1 面板
> （例：风堇 1409 `health=147.84`），乘 `rate(80)=7.35` 得到 Lv80 `≈1086.62`，与游戏内面板一致。
>
> **撤回**：我早前在本文里写过"这两个倍率是项目自造的线性近似、绝对值与游戏不符"，
> 那是错的 —— 起因是 `character_data.json` 里没有 Lv80 参照物，我把"找不到参照"
> 误判成了"未经校验"，并进一步推成了结论。**推断错、且当时写成了断言。**
> 实际倍率曲线（上表）在 20/21、70/79、80、90 各档都平滑且符合游戏档位。
>
> ⚠️ **但中间档位与游戏「面板成长」表并不相等，别拿去对拍**（P8-1 补记）：
> 该表每一档的基准值是 **`1 + 等级档×0.4`**（景元晋阶 5/70 → `475.2/158.4 = 3.00`），
> 而本公式在 Lv70 给 **6.85**。**只有 Lv1 与 Lv80 两个锚点和游戏表重合**
> （Lv80：`538.56/158.4 = 3.40` 是档位基准，满级面板 `1164.24/158.4 = 7.35` 才是本公式）。
> 这与 ROADMAP P1-4 记的"模拟器倍率公式"口径一致 —— 它本来就是首尾对齐的近似，
> `CharacterFactoryTest.onlyTheLevel1AndLevel80AnchorsMatchTheGameTable` 把这条差异钉住了。

### 12.3 光锥（`Weapon`）🚧

`Weapon.build(wid, level[, isPromote])` 从 `Constant.WEAPONS` 取基础值与技能属性，
`appendTo(atb)` 按属性类型分发（`PERCENT_TO_BASE` → `addPercent`；`isPercent` → `addPercentPoint`；否则 `addPure`）。

> ⚠️ **叠影恒为 1**：`weaponSkillData().getFirst()` 取的是第 1 档被动，`Weapon` 没有叠影字段/参数。

### 12.4 遗器（`Relic` / `RelicSuit`）✅

- 六个槽位 `RelicType`：`HEAD` `HAND` `BODY` `BOOT` `BALL` `LINE`。
- 主词条值表 `main_attribute.json`（按星级 + 槽位）、副词条值表 `sub_attribute.json`（按星级）。
- 主词条最终值 = `base + bonus × level`（level 0–15）。
- 副词条最终值 = `base × (promoteLevel + 1) + bonus × attributeLevel`。
- 副词条候选池固定 12 种（`Relic.SUB_ATTRIBUTE_LIST`），随机生成时排除与主词条重复的。
- ⚠️ `addToSuit` 替换同槽位时**不移除旧的**，`total` 只增不减 → `addMore(bodyA, bodyB)` 会把
  两件身甲都算进面板；`clone()` 只从 6 个槽位重建，因此克隆件与原件可能不一致。

### 12.5 行迹（`SkillPoint`）✅

`point.json[cid]` 是带 `pre_point` 的节点列表，`SkillPoint.init(cid)` 按 `pre_point.getFirst()`
连成树（无前置 = 根），`sumAttributes` DFS 汇总，`appendTo` 追加为 `SKILL_POINT` 来源的修正。
> ⚠️ 缓存是**进程级、按 cid**（不是按角色），且节点字段是 public 可变的。
> 另外 `Builder.build()` 无条件套用**整棵**树，没有解锁门槛。

### 12.6 存在的缺口 ⚠️

- ✅ 已修（P8-1）：`character_data` 的 `attribute`（元素）、`mt`（命途）、`aggro`、`max_energy`
  现在都接进了 `Character`/`CanHit`；真实角色一律走 `CharacterFactory.create(cid, level)`。
- `Character` 的拷贝构造器**浅拷贝属性数组**（`DoubleValue` 与原件共享）→
  给副本挂 buff 会改到原件的面板。当前无调用者。
- ⚠️ 中间档位晋阶倍率是**线性近似**，与游戏「面板成长」表不相等（只有 Lv1/Lv80 两个锚点重合），
  见 §12.2 与 `CharacterFactoryTest.onlyTheLevel1AndLevel80AnchorsMatchTheGameTable`。

---

## 13. 敌人面板构建 ✅

```
EnemyFactory.create(monsterId, level, hardLevelGroup)
  ├─ Constant.MONSTER_CONFIGS[monsterId]     实例系数 / 弱点 / 抗性
  ├─ Constant.MONSTER_TEMPLATES[template_id] 模板基础值
  ├─ Constant.HARD_LEVEL_GROUPS[组][等级]    等级组系数
  └─ EnemyScaler.scale(...)                  → EnemyStats
```

**缩放公式**：

```
生命 = 模板.health × 等级组.health × 实例.hpRatio        × 精英.healthRatio
攻击 = 模板.attack × 等级组.attack × 实例.attackRatio    × 精英.attackRatio
防御 = 模板.defence × 等级组.defence × 实例.defenceRatio × 精英.defenceRatio
速度 = 模板.speed × 等级组.speed × 实例.speedRatio       × 精英.speedRatio
韧性 = 模板.stance × 等级组.stance × 实例.stanceRatio    × 精英.stanceRatio
效果命中   = 等级组.effectHitRate                            （加值，不乘模板）
效果抵抗   = 模板.effectResistance + 等级组.effectResistance  （**加值，不是系数！**）
```

三条已实测确认的规则：

1. 血量用 `health_modify_ratio`（bean 的 `hpRatio`），**不是**同文件里那个恒为 1 的幽灵字段 `hp_modify_ratio`。
2. 效果抵抗是**加值**：0.2 + 0.1 = 0.3；相乘会得到 0.02（错）。
3. 攻击修正列在本项目数据里整体缺失，由补丁文件 `monster_attack_modify_ratio.json`
   （444 条 ≠1）在 `Constant.normalizeMonsterConfigs` 装载时合并；其余缺失系数按 1.0 补。

`EnemyFactory` 还会设：`level`（进防御区与击破基数）、`damageResist`（抗性区）、
`stanceWeak`/`stance`/`maxStance`/`stanceCount`/`stanceType`。
> ⚠️ 漏了 `effectHitRate`（见 §11）。

数据规模实测：`monster_config` 2649 条、模板 2649 个 id 全覆盖、
`breaking_rate` 与 `hard_level_group` 等级集合完全一致（1–100 与 120）。

---

## 14. 数据装载（`Constant` / `JSONReader`）

`Constant` 的静态块（类首次加载时执行）装载 8 组 / 10 张表（`MONSTER_CONFIGS` 吃两个文件）：

| 表 | 文件 | 可变性 |
|---|---|---|
| `RELIC_MAIN_ATTRIBUTES` / `RELIC_SUB_ATTRIBUTES` | `main_attribute.json` / `sub_attribute.json` | ⚠️ 可变（含嵌套 map） |
| `WEAPONS` | `weapons.json` | ⚠️ 可变 |
| `CHARACTERS` | `character_data.json` | ⚠️ 可变 |
| `SKILL_POINTS` | `point.json` | ⚠️ 可变 |
| `SKILLS` | `skills.json` | ⚠️ 可变（含嵌套） |
| `MONSTER_TEMPLATES` | `monster_template_config.json` | ⚠️ 可变 |
| `HARD_LEVEL_GROUPS` | `hard_level_group.json` | ⚠️ 可变（含嵌套） |
| `MONSTER_CONFIGS` | `monster_config.json` + 补丁 `monster_attack_modify_ratio.json` | ✅ `Map.copyOf`（唯一不可变的） |
| `BREAKING_RATE` | `breaking_rate.json` | ⚠️ 可变 |

懒加载（不占静态块）：`Constant.stages()` ← `stage.json`（见下）。
B 组的 `enemy_skills.json` 与手写补丁也是静态块里读的（`ENEMY_SKILLS` / 合并进 `MONSTER_CONFIGS`）。

> 另外 `Benchmark` 会读 `dump_data.json`，但 **generator 不产出它**（是个遗留的基准输入）；

> ⚠️ `public static final` 只锁引用不锁内容，**这些表可被运行时修改**（`Constant.SKILLS.clear()` 是合法的）。

### 14.1 生成器产出 vs 引擎实际加载

`generate_data.py` 现在产出 **27 个文件**，引擎只用到其中 **11 个**
（另外还读 2 个非 generator 产出的文件）。差额登记如下 —— 免得再出现
"文档说没有、其实文件早就在"的偏差（本仓库此前那份 9/1 的快照就少了 11 个文件、
`character_data.json` 也没有 `rarity`）。

**A. generator 产出且引擎已加载（11）**

`main_attribute` · `sub_attribute` · `weapons` · `character_data` · `point` · `skills` ·
`monster_template_config` · `hard_level_group` · `monster_config` · `breaking_rate` · `stage`

**B. 非 generator 产出但引擎已加载（2）**

| 文件 | 说明 |
|---|---|
| `monster_attack_modify_ratio` | 仓库内的补丁文件（人工维护），由 `normalizeMonsterConfigs` 合并 |
| `enemy_skills` | 仓库内手写技能表（P5-3） |

**C. generator 产出但引擎完全不读（12）** —— 都是"为后续阶段准备 / 喂给文档导出脚本"的：

| 文件 | 大概内容 | 为什么没读 |
|---|---|---|
| `challenge_maze.json` / `challenge_story_maze.json` / `challenge_boss_maze.json` | 挑战模式关卡与波次 | 未加载；P7-4 只做了 `stage.json` |
| `character_id_mappings.json` | 角色 id → 译名 | 用不上（`character_data.json` 自带 name） |
| `elation_basic_level_damage.json` | 欢愉（阿哈）体系基础等级伤害（101 条） | 未加载；P10 欢愉体系要用，见 §18.4 |
| `eidolons.json` | 星魂 | 未加载；P8 星魂相关 |
| `enhanced_ranks.json` | 强化形态星魂 | 未加载；同上 |
| `enhanced_skills.json` | **强化形态技能**（10 个角色，基础 id + 1,000,000） | 未加载；这也是 `skills.json` 比早先那份小的原因（强化角色搬了出去） |
| `global_buffs.json` | 全局辅助技能（仓库技，仅 1407 / 1506） | 未加载；属 P8-0 三分法的"跨系统"类 |
| `growth.json` | 各晋阶基准面板 + 晋阶消耗（93 角色 / 651 行） | 未加载；**它是 `LevelPromotionCalc` 那份游戏表的原始数据**，见 §12.2 的近似说明 |
| `relic_sets.json` | 遗器套装效果（60 套 / 92 条） | 未加载；引擎目前只用 `main_attribute`/`sub_attribute` 的**数值**，**套装效果没接**（P10-3） |
| `materials.json` | 培养材料 | 纯展示 |
| `recommend.json` | 游戏内置推荐光锥 / 遗器 / 词条 | 纯展示 |
| `enhanced_hints.json` | 角色加强说明（10 个角色） | 纯展示 |
| `property_names.json` | 属性的官方中文名（56 条） | 日志 / UI 本地化可用 |
| `text_ids.txt` | 翻译缺失的 text id 清单 | 当前 0 行（无缺失） |

**D. 既不是 A/B、也不是 generator 产出的辅助文件（4）**

| 文件 | 说明 |
|---|---|
| `versions.json` | generator **读**它做末尾的覆盖率统计（不是它的产出） |
| `pending_text_ids.txt` | 翻译待查清单（`export_glossary.py` 一类脚本用） |
| `skill_segments.json` / `skill_segments.csv` | 技能分段数据，`export_skill_segments.py` 为文档生成 |

> 账目（已用脚本核对）：generator 产出 **27** 个 = **A 11 个已加载** + **C 16 个未加载**。
> B（`monster_attack_modify_ratio`、`enemy_skills`）与 D（4 个辅助文件）都**不在**这 27 个里。
> C 那张表按"用途"合行写了，所以行数（14）少于文件数（16）——
> `challenge_*` 3 个、`eidolons`+`enhanced_ranks` 2 个都是各占一行。
> 要查"引擎读哪些"，看 A/B 两组即可。

> **`stage.json`（9 MB / 约 2.9 万条关卡）是懒加载的**，走 `Constant.stages()`（P7-4）：
> 它是最大的数据表，而多数测试与 demo 根本不碰关卡，塞进静态块等于每次 `Constant`
> 初始化都多付 ~35 MB 堆。另外它**缺失时返回空表而不抛异常** —— 一个可选功能不该把
> 整个测试套件拖下水（护栏见 `StageLazyLoadTest`）。

`JSONReader.fromJSON` 用 UTF-8 + try-with-resources 读 classpath `/data/`。
> ⚠️ 它的 javadoc 说"资源缺失返回 null"，实际是 `Objects.requireNonNull` **抛 NPE**；
> 在 `Constant` 静态块里会变成 `ExceptionInInitializerError`，此后该 JVM 内每次访问 `Constant` 都失败。
> 而且 `src/main/resources/data/` 被 `.gitignore` 排除，**新克隆的仓库必须先生成数据**，否则所有测试全红。

---

## 15. 已实现 vs 未实现总表

### ✅ 已实现并接入

- 属性代数（`base × (1+Σadd) × Π(1+mul) + Σpure`，带来源可撤销）
- 完整伤害乘区（增伤/易伤/减伤/虚弱/暴击/防御/抗性）+ 唯一结算入口
- 12 种伤害类型及其"可暴击/吃增伤"规则
- 四个事件家族（BattleStart / Move / Damage / Attack）
- 行动条（绝对时间 + 堆），推条/拉条 API
- 技能展开：单体/AOE/扩散/弹射 + 每段独立结算
- 韧性、弱点削韧、击破伤害、击破推条、击破跳回合（**需调用方主动调**）
- 击破 DOT（火/雷/物理/风，先上先结算，敌人回合开始结算）
- 能量（字段、回能效率、5 个回能钩子、终结技门槛与清零回能）
- Buff 生命周期（同类替换、early/late tick、控制阻断、按侧注入）
- 角色面板（等级缩放 + 光锥 + 遗器 + 行迹 + 额外加成）
- 敌人面板（模板 × 等级组 × 实例 × 精英组）
- 附加伤害 / 真实伤害及其"不算一次攻击"语义

### 🚧 占位 / 简化

| 项 | 现状 |
|---|---|
| 技能槽位 | 六槽全解析成槽位 1（普攻） |
| 击破 DOT 数值 | `DOT_RATIO=0.5`、`DOT_TURNS=3` 是示例值，base 不含击破特攻与削韧值 |
| 神君/账账类追加攻击 | 未进入事件体系 |
| 召唤物 | `Summon` 类存在但**从未被实例化**；`Battle.addRequest` 是死代码；`battle.enemies` 是 `List<Enemy>`，敌方召唤物无处安放 |
| 控制 | 只有 `StunBuff` 一种 |
| 治疗 | 只有 `heal()` 方法，无乘区、无调用者 |

### ❌ 未实现

| 项 | 说明 |
|---|---|
| 敌方**战斗循环**（谁在什么时候驱动敌人回合） | `Battle` 不自己驱动敌方回合：`enemyTurn` 在 `Main` 里（P5-5 按 ROADMAP 的做法）。完整循环封装留给 P11-1。**AI 本身（选目标 + 技能）已实现**，见 §19 |
| 胜负判定 | ✅ P7-3（`Battle.Status` + `stepForward` 终态保护），见 §6.3 |
| 关卡与波次 | ✅ P7-4 + P7-5：`stage.json` 懒加载、`WaveManager` 逐波进怪、`StageFactory.load(id)` 一键组装，见 §21 |
| 效果命中判定 | ✅ P6-1（公式 + 掷骰 + 施加入口），但**基础概率还没有数据来源** |
| 护盾 | ✅ P6-3（先扣盾再扣血、不叠加、吸收量计入"造成伤害"） |
| 终结技插入 | 无（`castUltra` 只是立即排队结算，不是插入行动轴）；额外回合期间禁止插入**别人**的终结技 ✅ P7-2 |
| 速度操纵 | ✅ P7-1b / E2（速度变化立刻重排行动条） |
| 忆灵 / 欢愉 | 只有属性/类型占位，无机制 |
| 七系击破异常 | 只有 4 种 DOT，冻结/纠缠/禁锢未做 |
| 多韧性条 | `stanceCount > 1` 未处理（恢复时直接回满） |
| 转阶段 / 多血量阶段 | 只有 `invulnerable` 标志，无阶段切换 |

---

## 16. 测试与可验证性

- **23 个测试类 / 167 个用例**（截至本次修复），全部通过。
- 覆盖重心：伤害乘区（`DamageZoneTest` 24 条）、技能展开（`SkillExecutorTest` 13 条）、
  能量（`EnergyTest` 8 + `EnergyBattleTest` 11）、韧性击破（`ToughnessTest` 6 + `ToughnessBattleTest` 8 +
  `BreakDamageTest` 5 + `BreakStateTest` 4 + `DotTest` 6）、怪物数据（`MonsterDataTest` 7 +
  `EnemyScalerTest` 4 + `EnemyFactoryTest` 5）。
- **可复现性**：`Battle` 接受注入的 `java.util.Random`；全仓库无 `Math.random()`。
  > ⚠️ 但同速单位的出手顺序不受保证（`Signal.compareTo` 无平局裁决），
  > 且 `Relic.createRandomLevelZero` / `MapUtils` 用的是不可播种的 `ThreadLocalRandom`。
- **测试盲区**（重要）：
  - `Battle.startBattle()` **从未被任何测试调用** → 开场事件链零覆盖。
  - `BuffManagerTest` 直接调 `BuffManager`，**绕开 `Battle`** → 真实事件顺序、控制阻断的时序未验证。
  - `EnergyTest` 用手写 `SkillData` 而非真实 `skills.json` → 测不出数据字段绑定错误。
  - 无 `AttributeTypeTest`；`CharacterTest` 不覆盖拷贝构造器；
    没有"过量削韧"与"多段削韧总量"用例（H-3/H-4 修复后才补上）。

---

## 17. 给后续开发的注意点（踩坑清单）

1. **改动伤害数值前先确认乘区归属**：新效果属于哪个区由 `Area.applies(type)` 与 `DamageType` 的
   两个 flag 决定，不要新增平行公式。
2. **注入型 buff 必须判侧**（`isOnDefenderSide` / `isOnAttackerSide`），属性型不必。
3. **削韧值单位是「点」**（普攻 30），击破基数要用 `breakingRate / 10`；
   弹射类总值要按段数均摊。击破伤害用 `reduceStance` 的**返回值**（实际削掉多少），
   而超击破要用**标称值** —— 两者相反，别抄错。
4. **`getAttribute(PERCENT)` 永远返回 null**，读基础属性。
5. **死者仍在 `characters`/`enemies` 列表里**，遍历时必须判 `isDeath()`；选目标走 `targetableEnemies()`。
6. **所有战斗内随机走注入的 `Random`**（`battle.getRng()`）。
7. **Gson 静默失败**是这个项目的历史重灾区：新增 bean 字段一律加 `@SerializedName` 并核对 JSON 键名。
8. **`Constant` 的七张表是可变的**，不要往上面写，也不要假设别人不会写。
9. 引擎层**不准写 `cid` 判断**。

---

## 18. 与规范文档（`HSR.md`）的对照

把 `HSR.md` 当规格，逐节核对本文描述的引擎行为。结论分三类：**一致**、**实现方式不同但等价**、
**规格有、引擎没有**。

### 18.1 一致（公式可以对上）✅

| `HSR.md` | 引擎 |
|---|---|
| §1.1 `最终属性 = (角色基础 + 光锥基础) × (1 + 加成%) + 固定加成` | `DoubleValue` 的 `base × (1+Σadd) × Π(1+mul) + Σpure` + `AttributeBuilder` 的来源链 |
| §1.2 `敌人属性 = 基础值 × 等级组系数 × 自身调整系数 × Π精英组系数` | `EnemyScaler.scale`（引擎用单组 `EliteGroup` 而非 `Π` 多组） |
| §1.2 90 级防御 ≈ `200 + 10 × 等级`、效果命中 90 级 32%、效果抵抗 90 级 30–40% | 防御区等级项；组1·Lv90 的 `effectHitRate = 0.32`；冰锋 `0.2 + 0.1 = 0.3`（加值口径） |
| §2.2 易伤 cap 3.5 / 减伤 floor 0.01 / 虚弱 floor 0.2 | `Constant.VULNERABLE_CAP` / `REDUCTION_MIN` / `WEAKNESS_MIN`，由各区自己声明 |
| §2.3 `暴击区 = 暴击 ? 1+暴伤 : 1`，多段独立判定 | `CritArea` + `SkillExecutor` 每段各调一次 `applyDamage`（每段独立掷骰） |
| §2.4 `防御区 = (200+10L)/(防御+200+10L)` | `DefenceArea`（**但减防/穿透的入参口径不同，见 18.3**） |
| §2.5 抗性 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`，**负抗全效** | `RESIST_MIN/MAX` + `ResistArea` 的 `min()/max()`；`ResistZoneTest` 有 `negativeResistanceKeepsFullEffect` 与 `resistanceIsClampedToNinetyPercent` |
| §2.5 弱点击破**不改变抗性** | `Battle.assemble` 第 4 步的注释与实现都没有对 `broken` 做特判 |
| §3.3 `最终获得能量 = 基础 × (1 + 能量恢复效率%)` | `CanHit.gainEnergy(EnergyGain)` |
| §3.4 基础仇恨 存护 150 / 毁灭 125 / 其他 100 | ❌ 未实现（`Path` 枚举都没有），数值待 P5-1 落地 |
| §4 `治疗量 = 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)` | `AttributeType` 有 `OUTGOING_HEALING_BOOST` / `HEAL_TAKEN_RATIO`，但**无公式无调用者** |
| §附录 3 扩散/弹射有伤害分裂比 | `stance_list` 的 `spread` / 弹射按段分摊（削韧侧已做） |
| §附录 4 持续伤害"先上先结算" | `Enemy.dots` 用 `List`，`tickDots` 按插入顺序迭代 |

### 18.2 规格要求"加算进同一区"，引擎的容器结构支持，但**攻击类型增伤还没做** ⚠️

`HSR.md` §2.2 的两条括号里各有**两个来源**：

```
增伤区 = 1 + Σ(伤害类型增伤 + 攻击类型增伤)
易伤区 = 1 + Σ(伤害类型易伤 + 攻击类型易伤)
```

**引擎现状（按字段核实）**：

- **伤害类型增伤/易伤**：✅ 有。`Battle.assemble` 按元素拿 `FIRE_DAMAGE_BOOST` 一类的属性 +
  `ALL_DAMAGE_TYPE_BOOST`，一起 `addBoost` 进**同一个** `BoostArea`，符合 `1 + Σ(…)`。
- **攻击类型增伤/易伤**（"战技增伤/终结技增伤/追加攻击增伤"）：❌ **完全没有**。
  全仓库 grep `skill_type` / `ATTACK_TYPE` / `bySkillType` **零命中** ——
  既没有对应属性，也没有"按技能类型过滤"的机制。

所以 ROADMAP 里"这类效果**直接往 `BoostArea` 灌**（Buff 侧按 skill_type 过滤）"这句话，
前半句（灌进同一个区）是对的、容器结构支持，**后半句（按 skill_type 过滤）目前没有任何实现**：
`Damage` 上除了 `type`（`DamageType`：NORMAL/SKILL/ULTRA/…）之外没有"攻击类型"标签，
Buff 也拿不到"这一段是用什么槽位打出来的"。

要落地这条，至少需要：① `Damage` 带上技能槽位/攻击类型；② Buff 的 `onDamage`
能读到它（目前 `DamageEvent.onDamage(Battle, Damage)` 只有这两个参数，读得到）；
③ 一套"这个 buff 只对某类攻击生效"的过滤规则。这属于 P8-2/P8-7 的范围。

### 18.3 实现方式不同，需要注意口径

| 项 | `HSR.md` | 引擎 | 影响 |
|---|---|---|---|
| 减防与穿透 | `防御 = 原始 × (1 - 减防% - 穿透%)`，**两者加算** | `defence(level, def, ignore)` **只有一个参数** | 接减防 debuff 时必须**先相加再传入**；分成两次调用会互相覆盖（见 §3.2 的警告） |
| 抗性 | `抗性 = 原始 + 抗性提高 - 抗性降低 - 抗性穿透` | `resist(抗性, 穿透)` 也是两个参数，`抗性提高/降低` 需要调用方先合并 | 同上：多来源要先在调用方合算 |
| 破韧 | §3.2 `破韧 = 基础破韧 × (1 + 弱点击破效率)`；基准"普攻对单 1 / 战技 2 / 终结技 3 / 扩散 2-1" | 引擎读 `stance_list` 的**点**刻度（普攻 30 / 战技 60 / 扩散 60-30，正好是 ×30 的关系），**没有 `(1 + 弱点击破效率)` 这个乘数** | 缺口：`弱点击破效率` / `削韧值提高` 两个属性**在 `AttributeType` 里不存在**，超击破要用到它们（见 18.4） |
| 精英组 | `Π精英组系数`（多个精英组相乘） | `EnemyScaler.scale(..., EliteGroup elite)` 单个 | 多精英组叠加未支持 |
| 数值精度 | §1.1：游戏内显示**向下取整**，计算用完整精度 | 全程 `double` 完整精度，**从不取整**（无取整函数） | 对拍数值时注意：游戏里看到的面板可能比引擎输出小不到 1 点（引擎给的是"计算值"） |
| 敌人"自身调整值" | `… + 自身调整值`（加法项） | 只有乘法的 `*Ratio`，**没有加法调整值** | 数据里暂时没见到该列，但公式上是缺的 |

### 18.4 规格有、引擎完全没有（缺口清单）

| `HSR.md` | 状态 |
|---|---|
| §3.1 **首轮行动值 150、后续每轮 100** | ✅ 已实现（P7-1）：`Constant.ROUND_ACTION_VALUE = 100` / `Constant.FIRST_ROUND_MULTIPLIER = 1.5`，`Queue.initialize()` 施加首轮系数、`Queue.getRound()` 按累计行动值分轮。见 §5.4 |
| §3.1 **额外回合**（不消耗回合数、期间不可插入终结技） | ✅ 已实现（P7-2）：`Battle.grantExtraTurn` / `Queue.grantExtraTurn`；期间 `castUltra` 拦住非本人的终结技。见 §5.6 |
| §3.2 **弱点击破效率** / **削韧值提高** | ❌ 属性都不存在；超击破公式（§7.3）需要它们 |
| §3.4 **仇恨系统 / 受击概率** | ✅ 已实现（P5-1/P5-2）：`Path` + `CharacterData.aggro` + `Battle.aggroOf/getAggroTable`。见 §19.1 |
| §3.4 **嘲讽** | ✅ 已实现（P5-2）：`TauntBuff` 是纯标记，**硬指定目标**（单体 / 扩散中心）而非仇恨加权 —— 与 §3.4 的"按百分比提高仇恨值"写法不同，见 §19.2 与 `DOC_VS_CODE.md` A-1 |
| §3.4 **嘲讽** | ❌ 未实现。**且规格这里与实际规则不符**：§3.4 写"按百分比提高角色仇恨值"（加权），实际规则是**硬指定目标** —— 嘲讽 buff 被附加后，攻击方（角色或怪物）的**单体攻击**与**扩散攻击的中心**只能选中该个体（双向）。所以嘲讽不是 `aggroOf` 里的乘法，而是目标选择阶段的强制约束；`TauntBuff` 应是**纯标记、无数值**。详见 `DOC_VS_CODE.md` A-1 |
| §3.5 **效果命中与抵抗的生效概率公式** | ✅ 已实现（P6-1）：公式与 §3.5 一致，三个因子乘算。顺手修了 `EnemyFactory` 漏写 `effectHitRate` 的问题（之前敌人命中恒 0）。见 §20.1 |
| §4 **护盾** | ✅ 已实现（P6-3）：`CanHit.shield` 先于 HP 被扣、不叠加。🚧 规格里的"护盾量提高"没有对应属性，护盾量目前就是传入值 |
| §4 **治疗乘区** | ✅ 已实现（P6-2）：`Battle.calculateHeal/heal`。⚠ 规格的 `(1 - 治疗降低)` 与 `(1 + 受疗加成)` 合并成一个因子（`HEAL_TAKEN_RATIO` 取负即降低），因为属性表里没有单独的"治疗降低" |
| §5 **忆灵系统**（独立单位/面板快照/连携攻击） | ❌ `Summon` 类从未被实例化 |
| §6 **欢愉体系**（阿哈速度/笑点/好活当赏/欢愉伤害公式） | ❌ 只有 `DamageType.ELATION` 与 `AttributeType.ELATION_DAMAGE_BOOST` 两个占位；`elation_basic_level_damage.json`（101 条）**从未被加载** |
| §7 **超击破** | ✅ 已实现（P4-6，2026-09-19）：`SuperBreakBuff` + `BreakDamageCalculator.buildSuperBreak` + `SkillExecutor` 里追加 `SUPER_BREAK` 段。**但**公式里的 `(1 + 削韧值提高)` 与 `(1 + 弱点击破效率提高)` 仍缺（属性不存在），`SUPER_BREAK_BOOST = 0.4` 是示例值 |
| §8.1 **忆灵伤害 / 欢愉伤害** 作为独立类型 | 类型枚举里有 `MEMORY` / `ELATION`，但无来源 |
| §附录 5 **特殊免疫判定**（如"记忆"祝福对冻结先查免疫） | ❌ 无免疫机制；`monster_config` 的 `debuff_resistance` 列也从未被读取 |

### 18.5 引擎有、但 `HSR.md` 没写的

| 引擎实现 | 说明 |
|---|---|
| `AttributeType.ALL_DAMAGE_TYPE_BOOST` | 一种"全类型增伤"，规格里归在 §2.2 增伤区的"伤害类型增伤"里 |
| `defenceIgnore` 的 clamp 到 [0,1] | 规格只写"防御不为负、防御区上限 1" |
| `Damage.fixedCrit(isCrit, critDmg)` | 由效果**指定**本段双暴（知更鸟附加伤害固定 100%/150%），规格 §2.3 没提这种特例 |
| `notCountsAsAttack()` | 附加伤害/真伤"不视为一次攻击"→ 不削韧、不触发攻击级事件、**受击方**不回能（但击杀仍给攻击者回能） |
| `invulnerable` | 转阶段无敌：可被选中但不结算 |
| 五级乘区钳制中的"非负 sanity 下限" | `PercentArea.min() = 0.0`，注释已标明**不是游戏规则**，只是防止负系数翻符号 |

### 18.6 由这次对照得出的两条结论

1. **超击破落地的前置比 ROADMAP P4-6 写的多**：公式里的 `(1 + 削韧值提高)` 与
   `(1 + 弱点击破效率提高)` 需要**两个新属性**，而 ROADMAP 只提了新建 buff、加常量、改 `Battle`。
   这两个属性同时也会修正 §3.2 的破韧公式，属于同一批工作。
2. **"首轮 150 / 后续 100" 影响所有速度阈值的校验**：✅ 已在 P7-1 落地并复核。
   首轮的行动时间 = `1.5 × 10000 / 速度`（冰锋 132 速 → **113.64**，而不是 75.76），
   之后的周期才是 `10000 / 速度`。`QueueTest` 的锚点已按此更新。

---

## 19. 敌人 AI（仇恨 · 嘲讽 · 敌方技能）✅ P5

### 19.1 仇恨值（`Path` / `CharacterData.aggro`）

**仇恨值是权重的绝对值**（不是百分比）：`受击概率 = 该单位仇恨 / 候选总仇恨`。

档位（**已用 `character_data.json` 的 `aggro` 列全量核对**，93 个角色每个档位都对得上）：

| 命途 | `mt` | 仇恨 |
|---|---|---|
| 存护 | `protection` | **150** |
| 毁灭 | `destruction` | **125** |
| 同谐 / 虚无 / 丰饶 / 欢愉 / 记忆 | `help` / `debuff` / `healing` / `elation` / `memory` | **100** |
| 巡猎 / 智识 | `single` / `all` | **75** ← 比常规低，别当成 100 |

**关键：`aggro` 列就是游戏倍率本身**，不需要换算。所以 `Character` 直接接数据
（`Builder.build()` 从 `CharacterData` 读 `mt` → `Path`、`aggro` → 仇恨值），
`Path` 枚举只在**没有该数据时**兜底。非角色（敌人/召唤物）统一给 100。

入口：`Battle.aggroOf(entity)` / `Battle.getAggroTable(allies)`（概率之和为 1）。

### 19.2 嘲讽（`TauntBuff`）——硬约束，不是加权

> 嘲讽 buff 只要被附加，攻击方的**单体攻击**与**扩散攻击的中心**就只能选中被嘲讽的那个个体。
> **双向生效**（我方单体/扩散打敌方时同理）。

- `TauntBuff` 是**纯标记、没有数值**（`extraPercent` 那套设计是错的：乘法只能提高概率，
  永远做不到"只能选中"）。
- 落点在 `TargetSelector`，**不在 `aggroOf`**：嘲讽不改仇恨表本身。
- 只对 `SINGLE` / `BLAST` 生效；`AOE` 本来打全体、`RANDOM`（弹射）每段各自随机，都不受约束。
- 嘲讽者死亡或不在候选集里 → 约束失效，退回仇恨加权（不能强制选中尸体）。
- `BuffManager.findBuff(Class)` 是配套的口子：`TargetSelector` 需要拿到**嘲讽者本人**，
  光知道"有没有"（`hasBuff`）不够。

### 19.3 目标选择（`TargetSelector`）

```
select(battle, candidates, intent, rng):
  1. intent ∈ {SINGLE, BLAST} 且候选里存在活着的嘲讽者 → 返回它（跳过随机）
  2. 否则按 仇恨 / 总仇恨 的累计权重抽一个
```

- 纯静态、无状态；随机走**注入的 `Random`** → 可复现。
- **候选集口径**：调用方必须传"活着的对手"（敌方走 `Battle.targetableEnemies()`）。
  别在这里自己判阵营 —— "谁能被选中"只能有一个出口，否则会选到尸体（鞭尸的来源）。

### 19.4 敌方技能（`EnemySkill` + `enemy_skills.json`）

```
base = 敌人攻击力 × multiplier        // 每段一次
Damage(type = damage_type, element)   // 走 Battle.applyDamage 统一装配
```

- 敌人的技能**不走角色的倍率表**（`SkillData` 是角色技能的结构），所以 `EnemySkill.getData()`
  返回 `null`、`execute` 全自定义。它也不削韧（敌人不打韧性条）。
- 每段独立走 `applyDamage`：**每段独立判定暴击、独立结算**。
- 数据表：`Constant.ENEMY_SKILLS`，键 = **`monster_config.json` 的怪物实例 id**
  （不是 `template_id`；写错会静默走兜底）。
- **⚠ 倍率是猜的**：数据源里没有敌人技能表（`skills.json` 只有角色，tbgd 也没下发）。
  每条数据带 `guessed: true`。没配条目的怪走**兜底**：倍率 `1.0`、单段、
  元素取自身 `stance_type` → 物理。所以任何怪都能打人，不会站着不动。
  P9-1/P9-2 接真实表时只换数据文件。

### 19.5 敌方回合（调用方驱动）

`Battle` **不自己驱动敌方回合** —— 现在由 `Main.enemyTurn` 做（P5-5 按 ROADMAP 的做法）：

```
1. battle.handleBrokenTurn(enemy)   // 击破中 → 本回合跳过
2. 候选 = 活着的我方
3. target = TargetSelector.select(battle, 候选, SINGLE, battle.getRng())
4. battle.performAction(enemy.getSkills().get(COMMON), List.of(target))
5. battle.processRequests()          // ⚠ performAction 只是排队，结算在这里
```

- 胜负判定已由 `Battle.Status` 提供（✅ P7-3，见 §6.3）；**完整的战斗循环封装**
  （自动驱动双方回合）仍留给 P11-1 —— `Main` 里现在是一段手写循环。
- `Battle.getOpponents(self)` 给出对手阵营列表（不过滤死亡）。

---

## 20. 命中 · 治疗 · 护盾 ✅ P6

### 20.1 效果命中与抵抗（P6-1）

```
生效概率 = 基础概率 × (1 + 施加方效果命中) × (1 - 受击方效果抵抗) × (1 - 特定负面效果抵抗)
```

**三个因子都是乘算**（不是"命中减抵抗"），结果 clamp 到 `[0, 1]`：

| 因子 | 来源 |
|---|---|
| 效果命中 | 施加者的 `EFFECT_HIT_RATE`（敌人从 `EnemyScaler` 的 `group.effectHitRate()` 来，90 级 = 0.32） |
| 效果抵抗 | 受击者的 `EFFECT_RESISTANCE`（敌人是**加值**：模板 + 等级组） |
| 特定负面效果抵抗 | 只有 `Enemy` 有：`debuffResist`（来自 `monster_config.json` 的 `debuff_resistance`，键是 `STAT_*` 串）。`(1 - specific)` 为 0 ⇒ **完全免疫** |

三个入口，**别绕过**：

- `hitChance(caster, target, base, key)` —— **只算概率，不掷骰**（AI 可以只看期望）。
- `rollDebuff(...)` —— 用注入的 `rng` 掷骰（同种子 → 同结果）。
- `tryApplyDebuff(caster, target, buff, base, key)` —— **技能侧施加 debuff 的统一入口**：
  先过判定，命中才 `addBuff`。直接调 `target.getBuffManager().addBuff(...)` 会把命中/抵抗绕过去。

数据实测：2649 条怪里 **999 条**带 `debuff_resistance`；出现过的键有
`STAT_CTRL_Frozen / STAT_CTRL / STAT_Confine / STAT_Entangle / STAT_DOT_Burn / STAT_DOT_Electric / STAT_DOT_Poison`。

> 🚧 **技能数据里还没有"基础概率"字段**（`skills.json` 没有这一列），所以现在
> `baseChance` 只能由调用方写死。要把各 debuff 技的概率数据化，得等 P9/P10。

### 20.2 治疗（P6-2）

```
治疗量 = 基础量 × (1 + 治疗加成) × (1 + 受疗加成)
```

- 两个因子来自**不同的人**：`OUTGOING_HEALING_BOOST` 读奶妈，`HEAL_TAKEN_RATIO` 读被治疗者。
- **没有单独的"治疗降低"属性**：`HEAL_TAKEN_RATIO` 取负数就是治疗降低
  （公式里 `(1 - 治疗降低)` 与 `(1 + 受疗加成)` 合并成同一个因子）。
- `Battle.calculateHeal(healer, target, base)` 只算数值；`Battle.heal(...)` 执行并返回
  **实际回复量**（撞 `maxHp` 后截断，不是理论治疗量）。
- 治疗**不碰 `Damage`**：它是独立的一套乘区（不暴击、不吃增伤、不吃防御/抗性/易伤）。
- 基础量由调用方算（`倍率 × 属性 + 固定值`）；`heal` 的倍率位、选谁当目标都是调用方的事。

### 20.3 护盾（P6-3）

- `CanHit.shield`（当前护盾）+ `CanHit.takeDamage` **先扣盾再扣血**：
  盾吸收 `min(shield, damage)`，剩下的才进 HP。所以"**盾破前不死**"是自动成立的。
- **不叠加**：`Battle.grantShield(target, amount)` 直接**覆盖**当前值（≤0 视为清盾）。
- `CanHit.lastShieldAbsorbed`：上一次 `takeDamage` 被盾吸走的量。
  ⚠ 它在 `takeDamage` 里**必须在任何 `return` 之前赋值** —— 盾把伤害全吃掉时会提前 return，
  漏掉就会让调用方读到上一次的陈旧值（实测过一次：返回伤害正好翻倍）。
- `Battle.applyDamage` 的返回值 = **被盾吸走的 + 真的掉的血**。
  为什么不是"直接返回乘区后的伤害"：目标是"打在有盾的目标上不能显示成 0"
  （否则 `AttackEvent.totalDamage` 与"本次攻击总伤害 × %"这类效果会失真）。
  ⚠ 恒等式：`乘区后的伤害 == shieldAbsorbed + hpLoss`（盾先吃、吃完才扣血），
  所以**不能**把"乘区后的伤害"与盾吸收量相加（会正好翻倍）。
- 🚧 **没有"护盾量提高"属性**（`AttributeType` 里没有），所以护盾量就是传入值 ——
  与"治疗降低"同类的缺口，等有真实效果引用时再加。

---

## 21. 关卡与波次 ✅ P7-4

### 21.1 数据（`stage.json`）

```
"103201": {
    "type": "Mainline",
    "hard_level_group": 1,
    "level": 29,
    "monster": [ { "Monster0": 1022020, "Monster1": 1023010, "Monster2": 1022020 } ]
}
```

- **`monster` 的每一项是一波**，所以 `monster.size()` 就是波数。
  数据里既有单波（103201：3 只）、也有多波（310030：3 波，最后一波 5 只）。
- 波内是 `{"MonsterN": id}`：`N` 只是**位置序号**，所以要按 `Monster0, Monster1, …`
  的顺序读（Gson 给的是 `LinkedHashMap`，保持插入顺序）。
  `StageBean.monsterIds(i)` 就是干这个的。
- 同一只怪可以在同一波里出现多次（`Monster0` 与 `Monster2` 同 id）—— 那是**多个独立实例**。
- ⚠ `hard_level_group` 必须写 `@SerializedName("hard_level_group")`：JSON 是下划线风格、
  Java 是驼峰，Gson 不会自动换算。漏了会静默拿到 **0**，直到
  `EnemyFactory` 报 `No hard level group 0 at level 29` 才暴露。
- 加载方式见 §18：**懒加载**（`Constant.stages()`），且文件缺失时返回空表。

### 21.2 波次（`WaveManager`）

```java
Battle battle = new Battle(team, new ArrayList<>(), rng);   // 敌队先空着
WaveManager waves = new WaveManager(battle, Constant.stages().get(103201));
battle.startBattle();
waves.nextWave();          // 进第 1 波
battle.processRequests();  // ⚠ 必须调，进怪是"排队入场"（addRequestItems）
```

- 进怪走 `Battle.addRequestItems` → `processAddRequests()` → `Queue.addCombatant`，
  所以新怪是从**当前行动值**起跑的，不会回到 0 重开一轮 —— 这正是波次该有的表现。
- ⚠ **`nextWave()` 之后必须 `processRequests()`**，否则怪只躺在 `addRequestItems` 里。
- **胜负与波次的接缝**（最容易踩的一处）：`Battle.checkResult()` 的判据是"一方全灭"，
  而波次模式里"敌队是空的"只是**这一波还没进**。所以 `Battle` 会问
  `WaveManager.hasPendingWaves()`：**还有波没进就不判胜**。
  敌方空 + 有待进的波 → 仍 `RUNNING`。
- 我方全灭则照常判负 —— 有没有待进的波都救不了团灭。
- 🚧 **没有"波间清理"配置**：数据里没有这一项，所以 `nextWave()` 只做"进怪"，
  不清 buff、不重置行动条。将来拿到配置时扩展点就在 `nextWave()` 里。

### 21.3 关卡工厂（`StageFactory`，P7-5）

```java
Battle battle = StageFactory.load(103201);          // 队伍就位、第 1 波已入场、已开打
Battle battle = StageFactory.load(103201, team, rng); // 自带队伍（P8-5 换真队走这条）
```

`load()` 做完四件事，缺任何一件都会得到"看起来能跑但状态不对"的战斗：

1. `new Battle(team, new ArrayList<>(), rng)` —— **敌队先空着**（怪由波次追加）；
2. `new WaveManager(battle, stage)` —— 同时把波次登记进 `Battle`（见 §21.2 的接缝）；
3. `battle.startBattle()` —— 否则状态停在 `NOT_STARTED`，`stepForward()` 也不会推进；
4. `waves.nextWave()` + `battle.processRequests()` —— 进第 1 波并**结算入场**，
   少了最后这步怪只躺在 `addRequestItems` 里，行动条上是空的。

**难度完全来自关卡数据**：`StageBean.hardLevelGroup` + `level` 直接喂给
`EnemyFactory.create(id, level, hardLevelGroup)`，所以同一个怪在不同关卡里不一样强，
调用方不需要传任何系数。

⚠ **队伍是临时的**：P7-5 时还没有 `CharacterFactory`（P8-1 已提供），所以默认队伍来自
`StageFactory.temporaryTeam()` —— 3 个 `fromAttributes` 占位角色（速度 100 / 134 / 90，
带 120 能量上限，否则永远放不出终结技）。它**不是角色**：没有光锥、遗器、真实技能与命途。
生命周期到 P8-5 为止，那时换成 `CharacterFactory` 造的 4 人真队。

---

## 22. 角色工厂（`CharacterFactory`）✅ P8-1

```java
Character jingYuan = CharacterFactory.create(1204, 80);
jingYuan.getElement();     // THUNDER
jingYuan.getPath();        // ERUDITION
jingYuan.getMaxEnergy();   // 130
jingYuan.getAggro();       // 75
```

它只是 `Character.builder()` 的薄封装 —— 面板管线（等级缩放 / 光锥 / 遗器 / 行迹 / 额外加成）
在 P2 就完备了，P8-1 补的是**角色身份字段**：

| 字段 | 来源 | 说明 |
|---|---|---|
| `element` | `character_data.attribute` | 该字段是**全小写**（`"thunder"`），而 `skills.json` 的 `element` 是首字母大写（`"Thunder"`）→ 解析必须大小写不敏感（P8-1 加的） |
| `path` | `character_data.mt` | 数据里的命途名与游戏内不同（`protection`=存护、`all`=智识、`help`=同谐…），映射在 `Path.fromMt`（P5 就有） |
| `aggro` | `character_data.aggro` | 游戏倍率本身：存护 150 / 毁灭 125 / 其他 100 / 巡猎·智识 75 |
| `maxEnergy` | `character_data.max_energy` | ⚠ **null 必须落成 0（= 没有能量条），不能兜底成 100** —— 93 个角色里只有 1407 遐蝶是 null |

**与 `Character.fromAttributes` 的分工**：那个是测试/占位入口（无元素、命途兜底 `OTHER`、
能量上限 0、技能全 `DefaultSkill`），P8-1 起在 javadoc 里标了"新代码禁止使用"。
真实角色一律走 `CharacterFactory`。

**面板是什么**（P8-1 实测，景元 Lv80 满晋阶）：

```
生命 158.4 × 7.35             = 1164.24      （他没有生命行迹）
攻击  95.04 × 7.35 × (1+0.28) =  894.13632   （行迹：攻击 4+4+6+6+8 = 28%）
防御  66   × 7.35 × (1+0.125) =  545.7375    （行迹：防御 5+7.5 = 12.5%）
速度  99                                      （不吃等级缩放）
```

注意**行迹是无条件应用的**（`build()` 里调 `SkillPoint.appendTo`），所以面板不等于
`数据 × 倍率`。这一点很容易误判成"属性索引错位"（P8-1 时我就误诊了一轮）。

🚧 **技能仍是占位**：`create()` 造出来的技能是 `DefaultSkill`（槽位 1），
真实倍率是 P8-2；天赋/追加攻击是 P8-3。本类**不负责**填技能。

