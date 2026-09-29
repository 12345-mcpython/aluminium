# aluminium 引擎机制说明

本文档**完全从代码推导**（`src/main/java`，截至 `929b460` + 本轮 C-1/H-3/H-4/H-5/H-7 修复），
不引用任何外部设计文档。每条机制都给出实现位置，方便对照源码；凡是**没有实现**或**只是占位**的，
本文会明确标注，不把"打算做"写成"已经做"。

> **规范文档**：游戏机制的权威公式在 `HSR.md`（"崩坏：星穹铁道 战斗机制总结（全版本 · AI 实现版）"，
> 以 4.4.0 数据为准）。本文与它的差异集中在 §18，**不重复它的内容**。
> 该文件目前**不在仓库里**（只存在于作者本机 `E:\code\blog\hsr\`），
> 要长期依赖它需要入库或把关键结论内联进 ROADMAP。（原 `DOC_VS_CODE.md` C-3 已随该文档删除。）

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
实体层   CanHit（抽象基类）├── Character   ├── Enemy   └── Summon ⚠两个阵营都能创建：敌方小怪与我方忆灵（P9-4，见 §24）
                                              │
战斗层   Battle ── Queue（行动条）──▶ 回合推进
           │
           ├─ applyDamage  ← 唯一伤害结算入口
           ├─ SkillExecutor ← 技能展开成 N 段 Damage
           └─ 事件广播：11 个事件家族，见 §4
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

每个修正都带 `source`（`ModifierSource`：`BASE`/`RELIC`/`WEAPON`/`SKILL_TRACE`/`EXTRA`/`BUFF`/`DEBUFF`/`UNKNOWN`）
与 `roleId`（来源实体的唯一 id），因此可以**按来源撤销**：`filterBySource(source)` 返回快照，
`removeModifier(modifier)` 三个列表都扫（用非短路的 `|=`），不会撤错也不会 `ConcurrentModificationException`。

其他要点：
- `clone()` 是**真深拷贝**（重建三个列表 + 逐个 `Modifier.clone()`）。
- `zero()` / `one()` 每次返回新实例，不是共享单例。
- 批量装配用的 `*NoCompute` 方法不会立即结算，必须由 `AttributeBuilder.build()` 统一调 `commit()` → `compute()`。

### 2.2 属性清单（`enums/AttributeType.java`，共 33 个值）✅

| 分类 | 属性 |
|---|---|
| 基础值（`isPercent=false`） | `HEALTH` `DEFENCE` `ATTACK` `SPEED` |
| 百分比占位（**在最终数组里恒为 `null`**） | `HEALTH_PERCENT` `DEFENCE_PERCENT` `ATTACK_PERCENT` `SPEED_PERCENT` |
| 双爆 | `CRIT_CHANCE` `CRIT_ATTACK` |
| 命中/抵抗 | `EFFECT_HIT_RATE` `EFFECT_RESISTANCE` ⚠️ |
| 治疗 | `OUTGOING_HEALING_BOOST` `HEAL_TAKEN_RATIO` ❌未使用 |
| 护盾（2026-09-28） | `SHIELD_BOOST`（「装备者**提供的**护盾量提高 X%」，**从给盾者**读，见 §20.3） |
| 击破/能量 | `BREAKING_EFFECT` `ENERGY_REGENERATION_RATE` |
| 元素增伤（7 个） | `PHYSICAL_` `FIRE_` `ICE_` `THUNDER_` `WIND_` `QUANTUM_` `IMAGINARY_DAMAGE_BOOST` |
| 通用 | `ALL_DAMAGE_TYPE_BOOST` |
| 追加攻击专属 | `FOLLOW_UP_DAMAGE_BOOST`（只在 `DamageType.ADDITIONAL` 时进增伤区） |
| 攻击类型专属（3 个） | `BASIC_ATTACK_` / `SKILL_` / `ULTIMATE_DAMAGE_BOOST`（按 `Damage.getCastCategory()` 进增伤区，见 §18.2） |
| 持续伤害专属 | `DOT_DAMAGE_BOOST`（只在 `DamageType.DOT` 时进增伤区） |
| 穿透 | `DAMAGE_PENETRATION`（抗性区用） `DEFENCE_IGNORE`（防御区用） |
| 欢愉 | `ELATION_DAMAGE_BOOST` ⚠️定义了但全仓库无读取者 |

> ⚠ **新属性一律追加在枚举末尾**：`CanHit` 的属性数组按 `ordinal()` 索引，插在中间会**静默**重排
> 每一个后随属性（`AttributeBuilder` 有守卫，但读的人不会知道）。`SHIELD_BOOST`、`DOT_DAMAGE_BOOST`、
> 三个攻击类型专属都是这么加的。

### 2.3 百分比重定向 ✅

`Constant.PERCENT_TO_BASE` 把 `HEALTH_PERCENT→HEALTH`、`ATTACK_PERCENT→ATTACK`、
`DEFENCE_PERCENT→DEFENCE`、`SPEED_PERCENT→SPEED` 四个"百分比属性"**重定向到对应基础属性**。

`AttributeBuilder.addPercent` / `addPure` 会先查这张表再决定往哪个属性上挂修正，
`build()` 则把四个百分比槽位显式置 `null`。所以：

> **`CanHit.getAttribute(HEALTH_PERCENT)` 永远返回 `null`** —— 想读生命值请读 `HEALTH`。

超过 0 个调用方踩这个坑，是因为 `RelicSuit`/`Weapon`/`SkillTrace` 三处都各自先判了
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
| `VulnerableArea` | `1 + Σ` | **≤ 3.5** | 受击方负面（`DamageEvent` 注入；数据侧由 `MODIFY_DAMAGE_TAKEN` 的**正数**创建） |
| `ReductionArea` | `Π(1-r)` | **≥ 0.01**，入参 clamp [0,1] | 受击方增益（数据侧由 `MODIFY_DAMAGE_TAKEN` 的**负数**创建） |
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

> ✅ **「无视目标 X% 的防御力」已经是数据，不需要新 op**（2026-09-27 核实，并写了第一个用户）：
> 这个"穿透"参数取自**攻击者的 `DEFENCE_IGNORE` 属性**，所以
> `MODIFY_ATTR DEFENCE_IGNORE 0.12` 就能表达它 —— 翡翠 1314 星魂 4
> （「施放终结技时…无视敌方目标 12% 的防御力，持续 3 回合」）是仓库里**第一个**用它的人，
> `DefenceIgnoreTest` 9 条把"规则 → 属性 → 防御区"这条**此前从未跑过**的链路钉住。
> ⚠ 属性是**比率**，所以百分比走 `pure` 加法（0.12 就是 12%）；⚠ 默认 `max_stacks: 1` 意味着
> **两个来源是"替换"而不是"相加"**（波提欧 16% + 阮·梅 20% 会得到 20%），要相加得显式写
> `max_stacks`。
> **仍未表达的**是把无视**限定在某一击**的那些（银枝「施放终结技时」、云璃「发动反击造成伤害时」、
> 遗器 119 的击破伤害）：按回合挂 buff 会漏到同一窗口里的其它伤害上，它们需要**实例级**的写法
> 加上"这一击是什么类型"的条件，仍登记在 `_unmodelled.json` 里。

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

1. **增伤区**：`getBoostByElement(element)` 拿元素增伤属性 + `ALL_DAMAGE_TYPE_BOOST`；
   若 `damage.getType() == ADDITIONAL`（追加攻击）再叠加 `FOLLOW_UP_DAMAGE_BOOST`；
   若这段伤害来自一次战斗内施放，再按 `damage.getCastCategory()` 叠加
   `BASIC_ATTACK_` / `SKILL_` / `ULTIMATE_DAMAGE_BOOST`（「普攻/战技/终结技造成的伤害提高」，§18.2）。
   ⚠ **"追加攻击专属增伤"必须是一条独立属性**，不能拿 `ALL_DAMAGE_TYPE_BOOST` 顶替 ——
   后者会把普攻/战技/终结技也一起抬高，而遗器套装 115 的文案只说 Follow-Up ATK。
   ⚠ 同理，**"普攻增伤"也必须是独立属性**：普攻与战技都是 `DamageType.NORMAL`，靠 `type` 分不开，
   只能靠 `Damage` 上那个显式的施放类别。三条属性都加算进**同一个** `BoostArea`，即 `1 + Σ(…)`。
   ⚠ 最后，**按目标状态条件化的增伤**（「对处于 X 状态的目标造成的伤害提高 Y%」）走的是另一条路：
   `assemble` 在这里发 `DEALING_DAMAGE`，把待结算的实例交给规则，`BOOST_DAMAGE` 直接往这次的
   `BoostArea` 里加一项 —— 实例本身就是"状态"，所以没有 buff 要挂、没有东西要清、也不会漏到下一次。
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

## 4. 事件家族 ✅（P8-6 补齐：4 → 11）

全部挂在 `CanHit` 上，默认实现转发给各自的 `BuffManager`。
**子类重写时必须调 `super`**，否则自己的 buff 会失效（`CanHit` 的注释专门警告）。

> ⚠ **buff 要收到事件，必须显式 `implements` 那个事件接口** ——
> `BuffManager` 是靠 `instanceof` **逐个**转发的，不是"所有 buff 收所有事件"。
> 这是最容易被漏掉的一环：buff 写了 `onSkillCast` 却没 implements，编译能过（多一个方法），
> 但**永远不会被调用**。

| 事件 | 签名 | 触发时机 | 投给谁 |
|---|---|---|---|
| `BattleEvent` | `onBattleStart(Battle)` | `Battle.startBattle()` | `queue.snapshot()` 全体 |
| `MoveEvent` | `beforeMove(Battle)` / `afterMove(Battle)` | 回合前后 | 该单位 |
| `DamageEvent` | `onDamage(Battle, Damage)` | `assemble` 第 5 步，**攻守双方各发一次** | 攻守双方 |
| `AttackEvent` | `afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage)` | `Battle.fireAfterAttack`：一次技能全部段结算完后（`SkillExecutor`），或一次召唤物攻击全部段结算完后（`EnemySkill`，P9-4 忆灵） | **我方全体** |
| `SkillCastEvent` | `onSkillCast(battle, user, skill, hitTargets, targets)` | `SkillExecutor`，伤害已展开、能量未结算之间 | **我方全体** |
| `EnergyEvent` | `onEnergyGain(battle, target, actuallyAdded)` | `Battle.applyEnergyGain`（唯一回能入口） | 相关方 + 我方 |
| `HpLossEvent` | `onHpLoss(battle, target, before, after, source, amount)` | `Battle.applyDamage` 扣血后 | 相关方 + 我方 |
| `HealEvent` | `onHeal(battle, healer, target, actuallyHealed)` | `Battle.heal` | 相关方 + 我方 |
| `KillEvent` | `onKill(battle, attacker, victim)` | `Battle.applyDamage` 目标由生转死 | 相关方 + 我方 |
| `BreakEvent` | `onBreak(battle, attacker, target, element)` | `Battle.reduceToughness` 韧性归零那一刻 | 相关方 + 我方 |
| `SkillPointEvent` | `onSkillPointGained(battle, amount)` / `onSkillPointSpent(battle, amount)` | 策略真的入账 / 真的花掉后 → `Battle` 广播 | **只我方** |

> 战技点增减共用一个接口 `SkillPointEvent`，两个方法都是 **`default` 空实现** ——
> 所以只想关心"被消耗"的 buff 只覆盖 `onSkillPointSpent` 就行，不必被迫实现另一个
> （原来它是 `SkillPointGainedEvent` / `SkillPointSpentEvent` **两个接口**，2026-09-26 合并，
> 粒度靠 `default` 保住）。

**触发器表专用事件（不是新的 buff 接口）**：`TURN_START` / `TAKING_HIT` 只有
`TriggerEvent` 一侧，**没有**对应的 `XxxEvent` 接口 —— 见 §4.6「`TURN_START` 与 `MoveEvent` 的关系」。

**为什么"回合开始"没有单独事件**：`MoveEvent.beforeMove/afterMove` 已经表达它，
再加一层是重复的抽象（`EventBusTest.turnBoundariesAreStillMoveEvent` 把这条钉住，
防止以后有人再加一个 `TurnStartEvent`）。⚠ 这条**只管 buff 接口**：`TriggerEvent.TURN_START`
是给**数据**用的（JSON 规则没法 implement 一个 Java 接口），两者不是一回事，见 §4.6。

### 4.1 广播口径（统一，别再造第三种写法）

```
dispatch(consumer, 直接相关方...)
  ├─ 直接相关方：总是收到（**即使是敌人**）—— 事件描述的是「事实」，与阵营无关；
  │               精英/Boss 的反击、免疫也要订阅发生在自己身上的事
  ├─ 我方额外全员收到 —— AttackEvent 已确立的惯例（知更鸟【协奏】/缇宝结界挂在辅助身上）
  └─ 我方成员去重 —— 若已作为相关方收到，不再收第二遍（否则同一 buff 被调两次、效果翻倍）
```

例外：**两个战技点事件只投我方** —— 战技点是我方队伍的**资源**，敌方没有份额。

### 4.2 每个事件的口径（坑都在这里）

| 事件 | 关键口径 |
|---|---|
| `TriggerEvent.BATTLE_START`（**无 buff 接口**） | `Battle.startBattle()`，在开场钩子 `onBattleStart` **之后**、`processRequests` 之前，投给**每个角色自己的表**。⚠ **它不带 `actor`、也不带 `target`** —— 所以在那里写 `"when": ["actor == self"]` 的规则**永远不会触发**（2026-09-27 手写时踩过，现在装载期直接拒绝这种拼写）。"我自己的战斗开始"**不需要条件**：被触发的表本身就是主人的。⚠ 别被"每个战斗单位各发一次"误导：那是**投递**口径，不是 `context` 口径 |
| `SkillCastEvent` | ⚠ **非伤害技能也发**（`hitTargets` 为空）。治疗/护盾/纯 buff 技的触发源靠它 —— 布洛妮娅「施放战技时 50% 概率 +1 战技点」如果写成"没打中就不发"就永远收不到。一次施放**只发一次**（群攻打 3 个目标也是 1 次） |
| `TriggerEvent.SKILL_CAST` / `BASIC_ATTACK` / `ULT_CAST`（**无 buff 接口**） | `actor` = 施放者，**`target` = 这次施放「瞄准」的那个单位**（调用方选的**主目标**，补于 2026-09-28 / M-35）。⚠ 它**不是**"效果打到的所有人"：群体技能打到好几个，这里只有第一个，覆盖面仍然由 `hit_count` 回答 —— 「指定我方单体」（布洛妮娅/停云/星期日的战技）靠它才写得出来。⚠ `ALLY_ATTACK` **刻意不带**这个目标：它是我方发起的攻击，瞄的永远是对面阵营，而我们的规则没有"某个敌人"这种选择器，传了也没有读者。⚠ 改动前已核：**没有任何已出货规则**把施放类事件和 `target` 条件配在一起，所以补上它没有改变任何现有规则的行为（全套测试确认） |
| `EnergyEvent` | `actuallyAdded` 是**实际入账值**（被上限截断后）。已满 → 0 → **不发**。**没有能量条的角色没有本事件**（走层数资源的 6 个角色，provider 恒返回 null，压根走不到回能口） |
| `HpLossEvent` | ⚠ `amount` 是**真的掉了多少血**，**不含被护盾吸走的量**。盾没破 → 不发。损血转资源的角色（遐蝶【新蕊】/万敌【血仇】/刃【充能】）靠这条区分"掉血"与"受到伤害" |
| `HealEvent` | 是**实际回复量**。满血被治疗 → 0 → **不发**。⚠ `CanHit.heal(double)`（原始加血）**不发** —— 它是 `Battle.heal` 与"直接改血量"共用的底层口子 |
| `KillEvent` | **不看** `countsAsAttack`（与击杀回能同口径）：附加伤害/真伤/DOT 补刀击杀**也发**。目标已死再挨打、无敌期间都不发。⚠ 见 §4.5「被写错的一条验收标准」 |
| `BreakEvent` | 只在**击破那一刻**发一次 —— 已击破的敌人继续挨打（超击破路径）不会重复发 |
| `SkillPointGained`/`Spent` | **没花出去就不算消耗**：战技点不足、出手不成立时**不发**（否则米沙/花火那类"每消耗 1 点"的计数器会为没发生的消耗记账）。已满时普攻实际入账 0 → 也不发 |
| `TriggerEvent.TURN_START`（**无 buff 接口**） | `Battle.beforeMove()`：该单位 buff tick **之后**、`MoveEvent.beforeMove` **之前**。`actor` = 轮到的角色，`target` **也**是它（"回合开始时"的两种写法等价，写哪种都不会哑掉）。**一次回合发一次**（额外回合也算一次）。⚠ 这不是新的 buff 接口 —— 回合边界对 buff 仍然是 `MoveEvent`（见 §4.6） |
| `TriggerEvent.TAKING_HIT`（**无 buff 接口**） | `Battle.applyDamage`：一次伤害实例**落在活着的、非无敌的目标身上**就发（被盾全额吸收**也算**）。⚠ **与 `HP_LOST` 是两件事**：`HP_LOST` 的口径是"真的掉了血"（`hpLoss > 0` 才发），`TAKING_HIT` 的口径是"挨打了"。遗器/天赋里"受到攻击后"要的是后者 —— 用前者会让带盾角色永远不叠层。`actor` = 伤害来源，`target` = 被打的人（"我被打" = `target == self`），投递口径与 `HP_LOST` 相同（`fireTriggersForAlly`，敌方主体不发） |
| `TriggerEvent.SUMMONED`（**无 buff 接口**，2026-09-28 补） | `Battle.fireSummoned`（由 `processRequests` 在**行动条排完之后**调一次）：一次召唤物**入场**，`actor` = 那个召唤物。⚠ **为什么不在召唤调用里发**：召唤物是走 `addRequestItems` 进行动条的，调用期间它**还没有行动条信号** —— 而「被召唤时」的规则几乎必然要动它的行动值（`ADVANCE`，即「使自身立即行动」），对没有信号的单位推条会**静默丢失**。⚠ **也不能直接在 `processAddRequests` 里发**：那个队列与**波次**共用，一波怪入场不是召唤。两个阵营都发，`actor == summon` 才是"我的"。⚠ `SUMMON` 按召唤者幂等 → 已在场时再召唤**不发**这个事件，这正是「若已在场，则…」需要的 |
| `TriggerEvent.SUMMON_ATTACK`（**无 buff 接口**，2026-09-28 补） | `EnemySkill.execute`：一次召唤物攻击的**全部段**结算完之后，`actor` = 那个召唤物、`hit_count` = 实际命中的目标数。**两个阵营都发**（Boss 的小怪也是召唤物）；**规则侧为什么不用 `ALLY_ATTACK`**：`ALLY_ATTACK` 是三条已出货规则（`1309` / `1403`「我方其他目标攻击后」/ 遗器 105）在用的，忆灵算不算那些「目标」，文档没说 → 直接加宽会**悄悄改变它们触发的内容**，所以像 `SKILL_CAST`/`BASIC_ATTACK`/`ULT_CAST` 一样在发射点分清。⚠ 规则要写「**我的**忆灵」得配 `actor == summon`：事件投给每个角色的表，不加条件时**队友的忆灵**攻击也会触发它。一个目标都没打到就不发（"发生了一次攻击"不是"场上有这个单位"） |
| `TriggerEvent.DEALING_DAMAGE`（**无 buff 接口**） | `Battle.assemble`：一次伤害实例**结算之前**就发，所以规则还来得及改这次伤害 —— 事件把待结算的实例交出来（`TriggerContext.damage()`），`BOOST_DAMAGE` 改的就是它。`actor` = 打人的一方，`target` = 将要挨打的一方（与 `TAKING_HIT` 同口径、从另一侧看）。⚠ **这是唯一携带 `Damage` 的事件**，也是「对处于 X 状态的目标造成的伤害提高 Y%」唯一可能的落点：`ALLY_ATTACK` 在结算**之后**才发，那时数字已经定了。DOT 跳伤 / 击破 / 附加伤害实例**也发**（它们同样是伤害），"只要攻击"由条件自己收窄 |
| `TriggerEvent.CAST_SETUP`（**无 buff 接口**，2026-09-27 补） | `SkillExecutor.execute`：**伤害展开之前**，`actor` = 施放者，`target` = `null`，正在进行的这次施放可从 `Battle.currentCast()` 拿到。⚠ **为什么要有"施放之前"这个事件**：其余施放事件（`BASIC_ATTACK`/`SKILL_CAST`/`ULT_CAST`/`ALLY_ATTACK`）都在伤害结算**之后**才发 —— 那对"施放后"是对的，对"改变这次挥击本身"就太晚了。`DEALING_DAMAGE` 管"改这一次伤害实例"，这个事件管"**这次施放的伤害不该由我来打**"，而后者是**施放**的属性、必须在实例存在之前就知道。首个用户：长夜月终结技（她自己的 `damage_list` 会以**她的攻击力**为基数挥一次，而文档说这段伤害是忆灵的；交付它的规则挂在 `ULT_CAST` 上，来不及拦住第一次挥击）→ `DELEGATE_DAMAGE`（§24.10）。⚠ 我方**每一次**施放都会发（含非伤害技能），规则必须自己用 `actor == self` 收窄，op 也会核对 |

- ⚠ **新加的 `EffectSpec` 字段必须带 `@SerializedName`**（2026-09-28，1309 那轮）：`speed` / `crit_rate` / `crit_damage` / `suspends_turns` 四个字段是照着 Java 字段名直接加的，**Gson 读不到 JSON 里的 snake_case** —— 而**单元测试全部通过**，因为它们用 `TriggerSpecs.set` 直接写字段；是**内容级**测试（`RobinConcertoTest`）把洞抓出来的：状态在了、`suspends_turns` 却是 false。**规矩**：`EffectSpec` 加字段时第一件事是写 `@SerializedName("<snake_case>")`，并且**至少有一条从文件读的用例**。
- ⚠ **harness：晚结算 buff 的"外来钟"在 `afterMove` 里跑**（2026-09-28，第二次）：`APPLY_BUFF` 造的是**晚结算** buff，而「（施放者的）每回合开始时持续回合数减 1」是 `tickForeignBuffs(actor, false)` 在 `Battle.afterMove` 里交付的。只调 `beforeMove()`、或只手发一个 `TURN_START`，**永远不会**让这种 buff 过期（第 58 轮是同一类：`StateBuff` 的**寿命**也在 `afterMove` 走）。**规矩**：凡是要验证"持续 N 回合，到期没了"的用例，必须跑**完整一回合**（`beforeMove()` + `afterMove()`）。

- ⚠ **量出来的字段语义：`ADD_STACK` 的 `amount` 不是层数**（2026-09-28，第 94 轮）：给 1111 卢卡写「获得 2 层【斗志】」时，用例断言"1+2=3"、实测读到 **2**；六次施放停在 **4**（上限生效）⇒ **每次触发只加 1 层**，`amount` 对该 op 无意义。**规矩**：op 的字段含义要**用行为测出来**再写进内容；`REMOVE_STACK` 的 `amount` 是"移除几层"，与 `ADD_STACK` **同名不同义**。

#> **2026-09-28（第 119–120 轮，写 1110 玲可时实测）三个 op 事实**：
> 1. **`scale` 的词表按 op 而不同** —— `MODIFY_ATTR` 只认 `self_attr:<ATTRIBUTE>`（且指**规则主人**的属性，例如 `self_attr:HEALTH` 表示"玲可的生命上限"）；而 `APPLY_REGEN` 只认 **`owner_def` / `owner_max_hp` / `target_max_hp`** —— 把 `owner_max_hp` 写给 `MODIFY_ATTR`、或把 `self_attr:HEALTH` 写给 `APPLY_REGEN`，装载器都会当场拒绝。
> 2. **`APPLY_REGEN` 必须给 `buff`**（规则文本里点名的那个状态名）；**两份叠加的再生必须起不同的名字**，否则后一份会把前一份顶掉，「额外回复」就退化成「替换回复」（1110 用的是 `持续治疗` 与 `持续治疗·额外`）。
> 3. **`MODIFY_ATTR` 的 `buff` 只是显示名，不是状态**：`has_state("求生反应")` 查不到它 ⇒ 「附上【X】，且提高…」这类句子要写成 **两个效果**（`APPLY_BUFF` 造状态 + `MODIFY_ATTR` 给数值），1110 的战技就是这么写的。

> ⚠ **2026-09-28（第 141–142 轮实测）引擎 bug：`BOUNCE` 的段数取自错误的参数下标**。
> `SkillExecutor` 的形状分派里写的是 `int hits = params.size() > 1 ? (int) (double) params.get(1) : 1;`，而 1004 瓦尔特战技行（100402）的等级 10 参数是 **`[0.72, 0.75, 0.1, 2]`** ⇒ `(int) 0.75 = 0` ⇒ **循环一次都不跑** ⇒ 整次施放**没有任何伤害** ✓（探针实测：技能存在、`effect = BOUNCE`、15 行参数、等级 10、攻击力 ≈795，HP 一点没动 ✗，两种目标都一样 ✓）。文档点名的"额外 2 次伤害"其实在 **下标 3**。
> ⚠ 今天**没有出货内容依赖弹射**（潜伏 bug ✓），所以先登记不盲修 ✓：正确的修法需要先**普查所有 `Bounce` 行**，确认段数是否恒在下标 3、以及它表示的是"**额外**段数"还是"**总**段数" ✓。

> ⚠ **2026-09-28 补充（`Bounce` 普查）**：本次普查 `AvatarSkillConfig.json` 得到 **220 行 / 15 个不同的弹射技能**，参数布局**各不相同**：`100402 [0.9,0.8,0.1,2]`、`120404 [60,0.825,10,3,0.25,10]`、`130404 [1,7,0.3125,0.625,2]`、`131203 [3,0.72,0.24,0.36,10]`、`150608`（8 项）…… ⇒ **没有一个统一的下标** ✗，而这 15 个技能在下标 1 上都是**系数** ⇒ `(int) params.get(1)` 对**全部**弹射技能都得 0 ✗（即：弹射技能的伤害目前**一律为 0** ✓，所幸没有出货内容依赖它 ✓）。
> ⇒ **正确的修法是数据任务**：段数应从**技能描述的结构化引用**里读（文档写「额外造成 `#4[i]` 次伤害」⇒ 指的是第 4 个参数 ✓），而不是把常量换成另一个下标 ✗。在拿到该映射之前**不盲修** ✓。


## 4.3 其余关键语义

- **`DamageEvent` 广播给双方**，但回调签名里**不告诉 buff 它挂在谁身上**。
  因此注入乘区的 buff 必须自己判侧：`Damage.isOnDefenderSide(entity)` / `isOnAttackerSide(entity)`
  （这就是易伤必须判侧、否则持有者自己打人也会被加伤的原因）。
- **`AttackEvent` / `SkillCastEvent` 广播给我方阵营 `battle.allies`**（我方召唤物也收得到，因为它就是
  `CanHit`；见 §24.4），这是因为"我方攻击后 / 施放后"的效果（知更鸟【协奏】、缇宝结界）挂在**别人**身上。
  ⚠ **`AttackEvent` 不管攻击者是谁都投给我方**（P9-4 忆灵之后由 `Battle.fireAfterAttack` 统一发）：听众
  各自带着自己的问题来（`AbstractBuff.afterAttack` 判的就是 `attacker == owner`），所以敌人的攻击投过来
  也没人会动它，而"挨打时触发"这类 buff 将来有地方可写。**谁在意是听众的事，有没有发生是引擎的事。**
- `hitTargets` 是**实际命中过**的目标（含当场死亡的，按命中顺序去重）；
  `mainTarget` 是调用方选的主目标（AOE 时它不是命中顺序里的第一个）。
- **"一次攻击行为" vs "多种伤害类型"**（重要区分）：
  - 引擎的计数单位是**行为**，不是伤害类型：`SkillExecutor.execute` 每次施放只调
    **一次** `grantSkillEnergy`、只广播**一次** `AttackEvent`（召唤物那边同理：`EnemySkill.execute` 把它
    所有段结算完，只发一次）；
  - 一次攻击行为里可以包含多种伤害类型（技能伤害 + 击破伤害 + 超击破伤害 + DOT…），
    它们都属于同一次攻击 —— **不要把某个伤害类型当成一次攻击行为**；
  - `totalDamage` 因此是**整条攻击链之和**：技能段 + 击破伤害 + 超击破伤害都计入
    （击破伤害由 `Battle.StanceResult.breakDamage` 带出来，见 §8.3）；
  - 真正"不算一次攻击"的只有附加伤害 / 真伤（`Damage.notCountsAsAttack()`，
    由 `Battle.applyAdditionalDamage` / `applyTrueDamage` 置位），它们不削韧、不广播、**受击方**不回能；
    但**击杀时仍给攻击者回能**（见 §9.2 的两条口径）。
  - **多目标时逐目标触发**：AOE/扩散/弹射对每个受击目标各自结算，超击破也**每目标各产生一发**
    （各自用自己的剩余韧性算超出部分）。
  - **一发未命中就不算攻击**：`Battle.fireAfterAttack` 在"一个目标都没打到"时直接返回，所以对着空场
    挥一下不会吃掉 `until: next_attack` 的 buff（`BuffLifetimeTest` 钉住）。
- 附加伤害/真伤段**不经过 `SkillExecutor`、也不发 `AttackEvent`**，所以不会递归触发
  ——这正对上官方定义"不视为造成了 1 次攻击"。⚠ 代价是**追击攻击不会消耗 `until`**（少消耗，登记 M-27）；
  这不是遗漏而是 `AttackEvent` 契约的一部分：听众**允许**用伤害回应一次攻击（第三方协同就是这么落的），
  派生伤害再宣布一次"发生了一次攻击"就会一直递归到 `MAX_TRIGGER_DEPTH` 抛错。

### 4.4 还没做的

- **敌人技能不发 `SkillCastEvent`**：`EnemySkill` 有自己的 `execute`（不走
  `SkillExecutor`），等 P9-2 把敌人技能接进统一执行器时对齐。⚠ **`AttackEvent` 则是发的**（2026-09-28）：
  它对所有攻击都成立，所以 `EnemySkill.execute` 结算完自己调 `Battle.fireAfterAttack` ——
  忆灵的攻击因此会被我方听到，见 §24.8。
- **递归安全**靠"不重复触发同一事件"的构造保证（`SkillCastEvent` 只从
  `SkillExecutor.execute` 发，附加伤害/真伤/DOT/击破都不经过它），
  ⚠ 但**没有**通用的"事件触发的效果会不会再发同一事件"的防护 ——
  P8-7 的触发器表落地时要自己保证效果不递归（附加伤害/真伤那条现成的安全边界仍然有效）。

### 4.5 ⚠ 被写错的一条验收标准（原 P8-6 计划）

原计划写"**DOT/附加伤害不发 `KillEvent`**"。**这条是错的**，实测相反：

`Battle.tickDots` 走的是 `Battle.applyDamage(target, damage, EnergyGrant.KILL_ONLY)` ——
与普攻**同一条**结算路径，所以 DOT 打死人**会发** `KillEvent`。
`KILL_ONLY` 只影响**回能**种类（不给受击方回能），不影响"死亡"这个**事实**。

而且"会发"才是对的：姬子「终结技每消灭 1 敌 +5 能量」那类效果要知道
"DOT / 附加伤害补刀也算消灭"，这与击杀回能的口径一致（见 §9.2 的两条口径）。

已按**实测行为**写测试（`EventBusTest.dotKillAlsoFiresKillEvent` /
`additionalDamageKillStillFiresEvent` / `trueDamageKillFiresEvent`），
并把更正记进 `ROADMAP` P8-6 —— 原计划那句留作对照。

### 4.6 触发器表：角色机制变成数据 ✅（P8-7）

上一节的 11 个事件是**宿主**；这一节是**住在里面的东西**。

角色机制不再写 `XxxTalent.java`，而是一张 JSON 表（`resources/characters/<cid>.json`），
引擎只做解释：

```
{ "on": "ALLY_ATTACK",                       ← 订阅哪个事件（TriggerEvent）
  "when": ["actor != self", "hit_count > 0"], ← 条件（全部成立才触发）
  "do": [ { "op": "GAIN_ENERGY", "amount": 1.5, "per_target": true } ],
  "cooldown": 1,                               ← 可选：隔几个"自己的回合"才能再触发
  "per_turn": 2,                               ← 可选：一回合内最多触发几次（「每回合可触发2次」）
  "once_per_battle": true,                     ← 可选：一场战斗只触发一次（与 cooldown 二选一；可与 per_turn 叠加）
  "chance": 0.35,                              ← 可选：「有 35% 的固定概率…」；掷骰用战斗注入的 Random
  "min_eidolon": 1,                            ← 可选：这条规则属于星魂，需要至少 1 级（「星魂 N 解锁」）
  "source": "1403 缇宝 trace 1403103",         ← 出处（引擎不读，给人看）
  "note": "…" }                                ← 为什么是这个数
```

#### 文件形状：规则数组 或 `{ resources, rules }` ✅（2026-09-27）

一个角色的文件是**两种形状之一**：

```
[ { "on": "BREAK", … } ]                    ← 只有规则：13 个既有文件都是这个形状，仍然合法
{ "resources": [ { "id": "充能", "max": 3 } ],
  "rules":     [ { "on": "BREAK", … } ] }   ← 声明了资源的角色必须用对象形状
```

- **为什么要有声明。** `ResourceManager.gain` 对**没登记过的 id** 返回 0（"什么都没加"），`value` 也返回 0
  （"空的"）—— 所以没有声明时，一条 `GAIN_RESOURCE` 规则会照常触发、**什么都不做**，一条
  `self_resource:充能 >= 3` 条件会读到 0（"没到上限"）。两个都是**没有症状的错数字**，正是本项目最不能接受的那种。
  声明就是「上限3点」这个**数字**被写下来的地方（`ResourceSpec`：`id` / `max`（必填）/ 可选 `initial` / `source` / `note`）。
- **它是配置，不是数值**：运行时的值在**战斗单位**身上（`CanHit.getResources()`，P8-8 的 `ResourceManager`），
  开局取 `initial`、逐场独立，与 `currentEnergy` 同一口径。所以「战斗开始时获得1点充能」写成 `BATTLE_START` 规则，
  而不是写成 `initial: 1` —— 那句话留在能被文本对照的地方。
- **注册点在唯一的装配点**（`CharacterFactory.create`）：按声明 `register(id, max, initial)`；
  `requireReadableResources` 还会拿**合并后**的整张表核对**每条规则引用到的资源名**，没声明就**在装配期**抛错
  （`CharacterException`，消息里说清该往 `resources/characters/<cid>.json` 里加什么）。与 `SUMMON` 的检查同一个位置、
  同一个理由：规则文件不知道自己的 cid，而**遗器规则**是所有穿戴者共用的，只有装配点同时知道这两件事。
- ⚠ **两种形状的 key 都是封闭的，往下也封闭**：顶层只认 `resources` / `rules`（写成 `"trigger"` → 装载期拒绝），
  声明里只认 `ResourceSpec` 自己的字段（写成 `"intial": 1` → 装载期拒绝）。Gson 对不认识的 key 是**静默丢弃**，
  而被丢掉的 `intial` 会被读成「从 0 开始」。
- ⚠ Gson 构造 record 时会**包一层**异常（`Failed to invoke constructor …` 且不带原因），所以 `TriggerTables`
  报的是 **cause 链里最具体的那条消息** —— 否则一条内容错误会退化成"构造函数调用失败"，作者只能猜。
- 契约：`CharacterResourceTest` **19 条**（两种形状 / 五种非法声明 / 注册 / 装配期拒绝 / 合并必须保留声明）。
  **变异 4 处各自独立变红**：读不到的资源返回 **0**、合并只看 `isEmpty()`、不收集**条件**里的资源名、
  不拆 Gson 的包装异常 —— 四处都是"少了一行、规则照常跑"的形状。

#### 限额：`cooldown` / `per_turn` / `once_per_battle` ✅

游戏文本里满是「该效果**每回合**只能触发1次」「该效果有1回合的触发**冷却**」「**单场战斗**中只能触发1次」
（青雀的行迹、布洛妮娅 E1、知更鸟 / 停云的 E2 …），还有一种是**次数**：「该效果每回合可触发**2**次」
（三月七天赋的反击）。没有这几个字段，作者只能在"多触发"和"不建模"之间二选一，而多触发是一个
**没人报错的错数字** —— 正是这个项目最不能接受的那种失败。

- `cooldown: 1` 就是「每回合 1 次」：计数在**规则持有者自己**的回合开始处递减
  （`Battle.beforeMove` 里先 `actor.tickTriggerCooldowns()`，再发 `TURN_START`）。所以"我挂在队友攻击上的
  规则"仍然是"**我的**每回合一次"；`cooldown: 2` 要等自己两个回合。
- `per_turn: N` 是**一回合内的次数上限**（2026-09-27 为三月七天赋而加）：与 `cooldown` **同一个时钟**
  （同一个回合开始处清零），但**不是同一件事** —— `cooldown: 1` 只是 `per_turn: 1` 那一个特例，
  「每回合可触发 2 次」写不出来。⚠ 两者**同时写会被装载期拒绝**：N > 1 时"每回合最多 N 次"与"每隔 M 个回合"
  的两个计数各自该从第几次触发开始算，有**两种读法**，引擎不愿替作者挑一种。`once_per_battle` 则可以叠加
  （"每回合两次，且整场只一次"没有第二种读法）。
- `once_per_battle` 永不回归（和冷却**不是**一回事）；`cooldown` + `once_per_battle` 同写在**加载期**被拒。
- `cooldown: 0` / `per_turn: 0` 也被拒：它们读起来像"没有限制"，而"没有限制"的表达方式就是**不写这个字段**。
- 计数存在**战斗单位**上（`CanHit`），**不是**在规则上：触发器表按 cid 编译一次并缓存，遗器规则还会并进
  同一张表给每个穿戴者共用 —— 放规则上就是"同 JVM 内所有战斗共享一个冷却"，也就是 N-1 那一类泄漏。
  开局 `onBattleStart` 清空，第二场战斗不会继承上一场的冷却。
- 规则的稳定标识是 **`source` + 文件内序号**：1403 的文件里两条规则共用一个 source（同一条行迹写了两件事），
  只用 source 做键的话，其中一条的冷却会**静默**挡住另一条。
- **规则也可以有"名字"**（2026-09-28）：可选的 `"id"`（`CompiledRule.id`），给**别的规则**指向它用 ——
  「每回合次数增加1次」/「基础概率提高15%」这类"抬高另一个数"的句子（op `MODIFY_RULE`，见 §4.6）。
  ⚠ 与 `key` 是两件事：`key`（source + 序号）是**引擎**的稳定标识、用来记限额；`id` 是**内容**起的名字、
  只用来被引用，缺省为空。⚠ 同一个文件里重名在装载期拒绝（引用会歧义），而且引用**只在同一张表内**解析。
- 契约：`TriggerLimitTest` **16 条**（三种限额各自的边界、按谁的回合、两个额度独立、叠加与互斥的装载期拒绝，
  外加 JSON 拼写）。变异 → 不校验限额 7 红、每回合给所有人递减 **1** 红、键只用 source **1** 红、
  开局不重置 **1** 红、触发后不记限额 7 红 —— 三个语义各自只有一条测试负责。
  ⚠ 2026-09-27 补 `per_turn` 时的两处变异：**不把 `per_turn` 传给 `isTriggerReady` → 4 红**
  （`TriggerLimitTest` 三条 + 内容侧 1001 的那条），**`tickTriggerCooldowns` 不清 `triggerTurnUses`
  → 3 红**（"自己的回合才还次数"两半各自被抓）。

**四个组件**（职责刻意分开）：

| 类 | 职责 |
|---|---|
| `beans.TriggerSpec` / `beans.EffectSpec` | JSON 形状（纯数据，无逻辑） |
| `models.TriggerTable` | 编译 + 校验 + **匹配**（无副作用，可单测） |
| `models.TriggerInterpreter` | **执行**效果（唯一碰引擎状态的地方） |
| `data.TriggerTables` | 按 cid 懒加载 + 缓存 + 容忍"没有文件" |

#### 条件 DSL（故意做小）

```
self              施放者是"我"（等价于 actor == self）
actor == self     施放者是我
actor != self     我方的**别人**动了   ← 知更鸟「我方目标攻击后」
actor == summon   **我自己的召唤物**动了   ← 遗器 123「装备者的忆灵攻击时」
target == self    这件事发生在我身上   ← 克拉拉「受到攻击后」
target != self    发生在我方的别人身上
target == summon  这件事发生在**我自己的召唤物**身上   ← 「装备者的忆灵受到攻击后」
hit_count > 0     这次攻击打中了至少 1 个目标
hit_count == 2    精确命中数
hp_percent <= 0.5 我自己的血量比例（0.5 = 50%）← 风雪交加 4 件套「回合开始时，若生命百分比 ≤ 50%」
target_hp_percent <= 0.5  **这件事承受者**的血量比例    ← 三月七战技「若**该目标**当前生命值百分比大于等于 30%」（⚠ 与 `hp_percent` 的区别：那个读的是**主人**）
target_debuff_count >= 3  事件的承受者身上有 3 个负面  ← 银狼「若目标的负面效果数量 ≥ 3，则减抗额外降低」
self_attr:SPEED >= 145    **我自己**的某个属性值      ← 位面饰品 2 件套的那一大类「当装备者的速度 ≥ 145 时」
self_summon_count >= 1    我自己有召唤物在场        ← 忆灵那一类「装备者的忆灵在场时」「当存在装备者召唤的目标时」
self_max_energy >= 200    **我自己**的能量上限        ← 生命的翁法罗斯 328「若装备者能量上限大于等于 200 点…」（⚠ 它不是属性）
self_resource:充能 >= 3   **我自己**某个**已声明资源**的层数  ← 姬子天赋「若姬子的**充能**达到上限（3点）…并消耗全部充能」（⚠ 声明见 §4.6 的文件形状；读**没声明**的资源一律 `NaN`，不是 0）
self has_same_path_ally  **我方还有别人跟我同命途**    ← 出云显世与高天神国 314「若至少存在一名与装备者命途相同的队友」
target_summon_count >= 1  **这件事的承受者**有召唤物在场  ← 星期日战技「若目标拥有召唤物，则造成的伤害提高额外提高…」
self has_state 协奏   我处于具名状态【协奏】      ← 知更鸟「处于【协奏】状态时」
target has_state 触电 这件事的承受者处于【触电】  ← 卡芙卡「触电状态下的敌方目标」（⚠ 认四种 DOT 与三种**控制**：冻结/纠缠/禁锢也可问，见 §8.6）
target has_path 同谐  这件事的承受者**命途**是「同谐」 ← 星期日战技「对「同谐」命途的角色施放时无法触发立即行动」
target is_ally      这次施放瞄准的单位**在我方**    ← 遗器 114/118/121「对**己方角色**施放终结技/战技时」（`!target is_ally` 是反面；⚠ 事件不说那个单位的阵营，而打敌人的终结技同样带 target）
target has_weakness Fire  **这件事承受者**弱该元素   ← 遗器 316「命中具有**火属性弱点**的敌方目标时」（⚠ 只有 `Enemy` 有弱点条，角色/召唤物一律为假）
target has_shield   这件事的承受者**身上有护盾**     ← 三月七天赋「当**持有护盾的**我方目标受到敌方目标攻击后…立即向攻击者发起反击」（⚠ 读的是**还立着**的护盾：被打光的那面不算）
target has_shield from_rule 战技盾  **那面盾是"哪条规则"造的**  ← 三月七星魂 6「在**战技提供的**护盾保护下的我方目标」（⚠ 只问"有盾"会连她星魂 2 的开局盾一起算 —— 同一个人给的、但不是同一条规则）
from_skill SKILL    **造成这件事的实例**来自哪个槽位  ← 姬子星魂 4「**施放战技**对敌方目标造成弱点击破时，姬子额外获得1点充能」（槽位拼写与规则里的 `skill` 字段同一套；⚠ 只在**带实例的**事件上合法：BREAK / KILL / DEALING_DAMAGE）
!target has_path 同谐 **取反**：上面那条不成立时成立  ← 「无法触发」那半句的唯一写法（条件表是**与**关系）
```

> 👥 **`self has_same_path_ally` 是第一条读"队伍组成"的条件**（2026-09-27，遗器 314）：遗器谁都能穿，
> 所以「与装备者命途相同」**不能**写成一个命途名字 —— 规则必须拿穿它的人去和场上其他人比。它是**读战场**的
> （与 `self_summon_count` 同族），并且**只认这三样**：另一个**活着**的队友（尸体不算，与 `summonsOf` 同一口径）、
> 不是自己、而且命途**是引擎认得的那个**（两边都是 `Path.OTHER` 不是"同命途"，是"都不知道" —— 把它们判为相等
> 是巧合装成规则）。⚠ 主体只能是 `self`，也不收参数：问的是"**我这边**还有谁跟我同路"。

> 🧭 **条件读的"主体"必须真在这件事里**（2026-09-27 补）：`actor` / `target` 只有当事件**带**它们时才有值，而在 `BATTLE_START` 上两个都是 `null` —— 所以在那里写 `actor == self`（或 `target == …`、`actor has_state X`）是**永不触发**的规则，装载期现在直接拒绝，报错会说明"这张表本身就是主人的，不需要条件"。⚠ 同族的坑：`self` 永远存在（主人总是有），所以 `self` 一律允许。
> 🧭 **`has_path` 与 `!` 前缀（2026-09-27 补，M-41）**：`has_path` 与 `has_state` **同形**（左边是
> `self`/`actor`/`target`），命途本身就是引擎已有的知识（`Character.getPath()`，仇恨分层那一列），
> 名字用**中文**（与状态名一致），**不在九个之内就在装载期拒绝** —— 注意这与 `Path.fromName` 的
> "不认识就降级成 OTHER"**刻意相反**：数据里出现新命途该降级，而**规则文件**里写错名必须响亮报错，
> 否则「对同谐…无法触发」会悄悄变成"对某个别的命途可以触发"。
> ⚠ `!` **只允许用在读主体的条件上**（`has_state` / `has_path`），因为对**数值**而言"读不到"和"等于 0"
> 是两件事（`NaN` 的任何比较都是 false，所以 `!` 会把它翻成 true）—— 想要后者就写 `self_summon_count == 0`，
> 报错信息会这么说。⚠ 否定**不会**把"主体不存在"翻成"成立"：`Negated` 先问内层条件它的主体是谁，
> 主体不在就直接不成立（与正向拼写同一条保证）。

> ⚠ **数值比较支持 `> >= < <= == !=`**（`==`/`!=` 是 2026-09-27 补的：此前 `==` 一律走**身份**分支，
> 于是文档里写了很久的 `hit_count == 2` **根本解析不了**，会被报成"比较了两个变量"；现在按"
> 有一边是 `self` 吗"区分两种读法，两边都是名字时仍然给那条更清楚的报错）。

> 🛡️ **`has_shield` 问的是"那面盾现在还立着吗"**（2026-09-27）：条件 DSL 新增的第四个子句，`<谁> has_shield`
> 与 `is_ally` 同形（无参数，主体是 `self`/`actor`/`target`，`!` 取反照常）。⚠ 读的是 `CanHit.getShield()`
> 这个**活着的值**，不是"曾经被给过盾"：被打光的盾就该不再满足「持有护盾的」。
> **它顺手把盾的寿命补上了**：在这之前 `SHIELD` 的 `turns` 被**读取、校验、然后静默丢掉** —— 三月七战技写着
> 「持续3回合」，而盾一旦给出去就**永远不掉**。那不是"少一点效果"，而是**条件**会一直成立：靠「持有护盾的」
> 触发的天赋会为整场战斗计数，而不是三个回合。现在有时长的盾是一个 `ShieldBuff`（挂在**被保护的人**身上，
> 按**他自己的**回合扣，因为「持续3回合」说的就是他的三个回合），落地时装上数值、到期时摘掉，
> **数值和寿命是同一件事**。⚠ 一个**新的**盾会**覆盖**旧的（盾本就不叠加），而"到期时摘掉"必须只摘
> **自己那面**：值本身分不出来（两面一样大的盾长得一样），所以盾记住**是哪个 buff 装的**
> （`CanHit.installShield` / `removeShieldFrom`）—— `Battle.grantShield` 那种裸写会把所有权清空，
> 于是"之后又是一次裸写"的盾不会被过期的旧 buff 摘掉。`HEAL` 那边则**装载期拒绝** `turns`
> （治疗没有时长，回血就是回血），一起拒的还有同样会被静默丢掉的 `permanent`。
> 契约：`ShieldDurationTest` **13 条**（寿命 / 按谁的回合 / 裸写不被摘 / 部分消耗仍到期 / 二次覆盖 /
> 条件三态 / 四种装载期拒绝）。**变异**：`has_shield` 写成 `>= 0`（"0 也算有盾"）→ **3 红**；
> `removeShieldFrom` 不看所有权 → **1 红**（后来的裸写盾被过期旧 buff 摘掉）。

> 🎒 **`self_resource:<NAME>` 是第二条"读我自己"的参数化变量**（2026-09-27，语料缺口里最大的一条）：
> 层数/充能既不是属性（`self_attr:` 够不着）、不是能量（`self_max_energy` 够不着）、也不是状态
> （`has_state` 够不着），它是 `ResourceManager` 上的一个数。97 份角色文档里 **41 份**拿某个层数当门槛
> （「获得充能，上限3点」「若【残梦】达到 9 层」…），而**写入**层的 op 从 P8-8 就在了，缺的一直是**读**。
> ⚠ 三条口径：**主体只能是 `self`**（和 `self_attr:` 一样，条件读的是规则主人，不是事件的施放者 —— 否则
> 别人动一下就会按**别人**的层数决定我的机制）；**读没声明过的资源回答 `NaN` 而不是 0**（"读不到 → 条件不成立"
> 与 `self_summon_count` 同一条规矩：`ResourceManager.value` 对未知 id 回答 0，而"悄悄按 0 判"正是要避免的
> 错答案）；**名字里不能有空格**（条件是按运算符切开再 trim 的，`self_resource:我 的资源` 会被读成「我」）。
> 首个用户**姬子 1003 天赋「乘胜追击」**：`self_resource:充能 >= 3` 是「若姬子的充能达到上限」，
> 随后的 `SPEND_RESOURCE amount: 3` 就是「消耗**全部**充能」—— 不是近似：规则只在**上限**处跑，而上限**就是** 3。

> 🧩 **身份比较的两套词汇**（2026-09-28 补的 `summon`）：左边是哪一方（`actor` / `target`），
> 右边是**谁**参与比较 —— 目前只有 `self`（规则主人）和 `summon`（**规则主人自己的**召唤物，两边写法等价：
> `summon == actor` 与 `actor == summon` 是同一条）。刻意**不收**其余目标选择器：`all_allies`/`party`
> 不是一个单位、`attacker` 只能是 actor 本身、`target == target` 是同义反复 —— 永远不可能有意义的名字，
> 正是这套 DSL 在装载期就拒的东西（拒绝的消息会把两套词汇都列出来）。
> ⚠ 与 `self_summon_count` 一致：**读不到战场就是不成立**，而且**对 `!=` 也一样**。
> 否则"没有战场 → 不是我的召唤物"会让 `actor != summon` 对**每一个**事件都为真 ——
> 一条"除了我的忆灵谁攻击都算"的规则会在所有事情上触发，且没有任何迹象。

> ⚠ **`self_summon_count` 读的是战场，不是事件。** 它是"**规则主人自己**有几个活着的召唤物在场"，
> 因此 `TriggerContext` 现在携带 `Battle`（见该 record 的 javadoc）—— 有些问题是关于**战场**的
> （谁在场 / 「我方全体」是谁），事件根本携带不了。⚠ 手搓的 context（不少测试这么用）没有战场，
> 这类条件于是**不成立**：**"读不到"绝不能退化成"0 个"**，否则一条「忆灵在场时」的规则会被静默禁用。
> `SummonFieldTest.aContextWithoutABattleFailsTheCondition` 用 `== 0` 这种**反向问法**钉住这一点
> （只问 `>= 1` 的话两种读法结果相同，测不出区别）。

> 🎯 **目标选择器多了 `"summon"`**：解析为**主人自己的召唤物**（`Battle.summonOf`），
> 因为「装备者及其忆灵」点名了一个**没有任何事件携带**的单位 —— 技能只可能由两者之一发出。
> 找不到时会**响亮报错**，并告诉你加 `self_summon_count >= 1`：作者多半是忘了那条件，
> 而静默回退到主人会给错单位上 buff。
> 于是「装备者及其忆灵」的标准写法是**两条规则**：一条打自己，一条 `target: "summon"` +
> `self_summon_count >= 1`。

`self_attr:<属性>` 是唯一的**带参数**成员：属性名走 `AttributeType`（已经是一个封闭且被校验过的集合），
所以**加新内容只需要写属性名，不需要动引擎** —— 这就是它存在的理由（要的是可扩展性，不是多一个变量）。
它是**取主人的属性**，不是取事件里的谁：触发器表永远拿自己当 `self`，读成 `actor` 会让"别人快"决定"我"的机制，
而战斗日志里什么都看不出来（`SelfAttributeConditionTest` 专门钉这一条）。

> ⚠ **量纲：字面量用属性自己的单位。** 四条基础属性是绝对值（`self_attr:SPEED >= 145`），
> 其余**比率属性是分数**（`self_attr:CRIT_CHANCE >= 0.7` 就是 70%，`self_attr:BREAKING_EFFECT >= 1.5`
> 就是 150%）。写成 `>= 70` 能加载、看着也对，然后**永远不触发** —— 正是 ROADMAP L-9 记过一次的
> 100× 陷阱；两种刻度在同一个测试里各钉一条。
> 装载期**拒绝** `*_PERCENT` 那四个：它们是 `AttributeBuilder` 的输入键，运行时槽位是 `null`，
> 读它会在战斗中途（伤害结算里）NPE，所以报错信息直接告诉你该读哪个基础属性。
> 目前只有 `self_attr:`（没有 `target_attr:`）：数据里的属性门槛**全部**是关于装备者的，
> 一条没人用的参数轴等于没测过的参数轴；将来真有内容要，再加是同处的几行。

> 💡 **两级阈值（「≥ A / ≥ B」）不是缺口，写法是"上界互斥"** —— 这一点我一开始在登记表里
> 判错了，同一天改回来（`_unmodelled.json` 里 7 条都曾拿它当阻塞理由）。文字说「速度 ≥ 135/160 时
> 造成伤害提高 12%/18%」，**不是**写两条各带一个门槛的规则（那样 160 速会两条都中、拿 30%），
> 而是让高的一档带下界、低的一档带上界：
> ```json
> { "when": ["self_attr:SPEED >= 160"], "do": [ …0.18 ] }
> { "when": ["self_attr:SPEED >= 135", "self_attr:SPEED < 160"], "do": [ …0.12 ] }
> ```
> `when` 是**与**关系（多条全成立才触发），所以任意速度下恰好一条命中，**不需要新词表**。

`has_state` 也认**四种 DoT 状态名**：灼烧 = Fire DoT、触电 = Thunder、裂伤 = Physical、风化 = Wind。
它们不是 `StateBuff`，而是 `DotBuff(element)` —— 引擎从 P10-0 起就是这么表示的，这里只是把两种拼写对上
（`BuffManager.hasState` 是唯一知道"两个名字是同一件事"的地方）。⚠ **控制状态（冻结 / 纠缠 / 禁锢）还没有**：
P10-2 把它们建模成"控制 buff + 推条"的**组合**，"这个单位是否被冻结"需要单独定义，不能靠猜；加进来时
JSON 写法不变。

`has_state` 左边可以是 `self` / `actor` / `target`（和身份比较不同，`self` 在这里**是**一个有意义的问题），
右边是**状态名**：原样匹配、不做大小写折叠（名字是数据，两边的拼写必须一致）。主体不存在时
（比如无主体的事件问 `target`）**条件不成立**，与身份/数值条件同一条规矩。

左右可以互换（`0 < hit_count`、`0.5 >= hp_percent` 也成立）。**变量是封闭集合**：写错变量名
在**加载时**就报错，报错信息会列出**全部**已知变量名（由集合本身排序生成，不是手写的，
所以加变量时消息不会落后于解析器），并点名 `self_attr:<属性>` 这个写法。

> ⚠ **`hp_percent` 读的是"主人"（触发规则所属角色）自己的血量**，不是事件里的谁 ——
> 它是关于"我"的事实，所以没有任何事件需要携带它。空主人 / 最大生命为 0 → `NaN` →
> 一切比较为 false（不成立就是"不触发"，不是"按 0% 触发"）。

> ⚠ **`actor` 与 `target` 是两件事，混用是最容易犯的错。**
> `actor` 是"谁干的"，`target` 是"发生在谁身上"。**我被打中时，`actor` 是敌人**，
> 所以克拉拉的反击必须写 `target == self`；写成 `self`（或 `actor == self`）
> 是在说"敌人动手时也算我动手"，永远不成立。加载期的变量校验抓不到这个
> —— 两个名字都合法 —— 只能靠 §4.7 那条"拆掉条件后测试必须变红"来守。

#### 效果 op 词表（只做引擎已有的能力）

| op | 参数 | 状态 |
|---|---|---|
| `GAIN_ENERGY` | `amount` 或 **`scale` + `percent`**（`target_max_energy` = 按**目标能量上限**的百分比，M-44；**`cast_applied:<状态名>`** = 按**这次施放真的落到的目标数**，M-49），可选 `per_target` | ✅ ⚠ `cast_applied:` 只在**施放事件**上合法（装载期拒绝其它事件），状态名必须是引擎真的会掷的状态（冻结/纠缠/禁锢 + 四种 DOT），见下面的「一次施放的落点」 |
| `GAIN_SKILL_POINT` | `amount` | ✅ |
| `HEAL` / `SHIELD` | `amount`，可选 `target` —— **或** `scale` + `percent`（+ 可选 `amount` 作**常数项**）；`SHIELD` 还可选 `turns`（**有时长的盾**） | ✅ `scale` 有 `target_max_hp` / `owner_max_hp` / **`owner_def`**（按**规则主人**的防御力，2026-09-27 为三月七的护盾而加：「57% 防御力 **+ 760**」）。⚠ `amount` 与 `scale` 同时出现**不再是冲突**：它是那个**常数项**（旧策略拒绝两者同写，所以放开它**不改动任何既有内容**）；仍拒的是「有 scale 有 amount 却没有 percent」——scale 只说明份额*是什么的*。⚠ `SHIELD` 的 `turns` 从 2026-09-27 起**真的生效**（`ShieldBuff`，按**被保护者自己的**回合扣；在这之前它被静默丢掉，盾永不掉 —— 见上面的 `has_shield` 那段）；`HEAL` 写 `turns` / `permanent` 是**装载期拒绝**（治疗没有时长）。⚠ 2026-09-28 起护盾量还要乘**给盾者的** `SHIELD_BOOST`（「装备者提供的护盾量提高 X%」），两条路同一个公式，见 §20.3 |
| `EXTRA_TURN` | 可选 `target` | ✅ |
| `ADVANCE` | `percent`（0.0–1.0，跳过目标**剩余**行动时间的比例；负值不支持），可选 `target`（**可以是群体**：`all_allies` / `other_allies` / `target_and_summon` 会逐个推） | ✅ |
| `GAIN_RESOURCE` / `SPEND_RESOURCE` | `resource` / `amount` | ✅（P8-8）⚠ 2026-09-27 起，`resource` 指的名字**必须**在角色文件的 `resources` 块里声明过（见 §4.6 的文件形状）：没声明时 `gain` 记 0、`value` 读 0，规则会**照常触发却什么都不发生**，所以装配期直接拒绝。`SPEND_RESOURCE` 从不存在的资源/不够扣时**响亮报错**（不是静默少扣） |
| `DAMAGE` | `skill` / `damage_param`，可选 `damage_level`、`target`（**可以是群体**：`all_enemies` 会**逐个**结算一次实例）、`per_target`、`as_attack` | ✅（P8-3，见 §4.7） |
| `MODIFY_ATTR` | `attribute` / `percent` / **`turns` 与 `permanent` 与 `until` 三选一**，可选 `target`、`max_stacks`（别名 `stacks`）、**`scale`**（派生值，见下）、**`ticks_on`**（按谁的回合扣时长，见下） | ✅（P10-3） |
| ↳ **派生值** `scale: "self_attr:<属性>"` 或 `"self_max_energy"` | `percent` × **规则主人**那条属性的当前值（或**能量上限**）+ 可选 `amount`（P11-2，M-42；`self_max_energy` 见下） | ✅ 首个用户**大丽花行迹「又一场葬礼」**「使其他角色的击破特攻提高，提高数值等同于 **24% 大丽花的击破特攻 + 50%**」。⚠ 结果是**绝对值**（即使目标是基础属性）—— 文档给的是"数值"，不是"目标基数的百分比"；⚠ 触发时**算一次就冻结**（引擎既有的快照口径 §24.5），所以 `amount` 是那个常数项；⚠ 没有 `scale` 却写了 `amount` **装载期拒绝**（以前是**静默忽略**）。⚠ 前缀与条件 DSL 的 `self_attr:` 共用一处定义（`TriggerTable.SELF_ATTR_PREFIX`），免得两种拼写各自漂移 |
| `MODIFY_DAMAGE_TAKEN` | `percent` / **`turns` 与 `permanent` 与 `until` 三选一**，可选 `target` | ✅ 正数 = 易伤、负数 = 减伤（两个**乘区**都不是属性，所以 `MODIFY_ATTR` 够不着） |
| `BOOST_DAMAGE` | `percent`（只能挂在 `DEALING_DAMAGE` 上） | ✅ 改**正在结算的那一次**伤害：不改属性、不挂 buff、不会漏到下一次。是「对处于 X 状态的目标造成的伤害提高 Y%」的实现 |
| `RAISE_SKILL_LEVEL` | `skill`（槽位：`COMMON`／`SKILL`／`ULTRA`／`TALENT`…）+ `amount`（等级数，≥1 的整数）；**只能挂在 `BATTLE_START` 上** | ✅（2026-09-28）「战技等级+1」「终结技等级+1」（1001 星魂 3/5，以及大多数角色星魂里的同一句）。⚠ 等级 = **基础 + 本场加成**，由**唯一一处**解析（`CanHit.skillLevel`），三个读表点共用：技能自身的施放（`SkillExecutor` 的伤害路径**与**生成效果表路径）、规则驱动的 `DAMAGE`（`TriggerInterpreter.multiplierOf`）。⚠ 加成分**本场**（`CanHit.skillLevelBonus`，与开火限额一起清空）：同一个 `Character` 会被关卡放进第二场战斗，把等级写进技能实例就会**每场叠一次且永不撤下**。⚠ 规则写了 `damage_level` 时**以它为准**（那是作者在说"文档引的就是这一行"，与"我的技能是 N 级"是两句话）。⚠ 加成一进来就暴露了一个**静默的 bug**：第一版按 `skill.getSkillSlot()`（**int**）查 `Map<SkillType,Integer>` —— 能编译（int 装箱成 Object）却永远查不中，加成记下了没人读，而所有测试在"读 map 自身"的断言下依然全绿（变异 m2 现在 3 红）。⚠ 生成效果表那条路径曾是**测试盲区**（变异 m3 第一轮 **0 红**）：补了 1105（有 `healer_max_hp` 缩放的治疗生成效果、且**没有角色文件**，所以不会被自己的规则覆盖）之后 m3 = 1 红。**变异**（实测，跑完 1198+ 条）：解析不叠加 → **3 红**；按 int 槽位查 → **3 红**；生成效果表路径用原始等级 → **1 红**；伤害路径用原始等级 → **1 红**；`damage_level` 失效 → **4 红**；op 空转 → **4 红**。⚠ **等级口径的两半**：**基础等级**目前由**内容自己**用这条 op 表达（`1001` 的 `BATTLE_START` 规则把 TALENT 升到它文档引用的 Lv10 —— 取代了那条规则原来的 `damage_level: 10`），星魂的「+2」再叠在它上面；于是 `damage_level` 在 `1001` 里已经退休，`1107`（克拉拉反击引 Lv10）与 `1413`（长夜月终结技引 Lv10）**仍靠它**，属于同一块内容的迁移。⚠ 「最多不超过15级」这类**上限**没有单独表达：那正是技能参数表的行数（ULTRA 15 行、COMMON 10 行），越界会**响亮地**在开火时失败，而不是读到一行不存在的数 |
| `DAMAGE`（固定暴击的两个字段） | 在 `DAMAGE` 上可加 `crit_rate`（**只能是 1.0**）+ `crit_damage`（如 1.5 = 150%） | ✅（2026-09-28）「该伤害暴击率**固定为100%**，暴击伤害**固定为150%**」（知更鸟终结技的附加伤害）。⚠ `Damage.fixedCrit(boolean,double)` 早就存在（暴击区对"已经知道结果"的实例跳过掷骰），但**没有任何 op 字段能说出它** —— 这句话因此一直写不出来。⚠ **只接受 `crit_rate: 1.0`**：**概率性**暴击率是 `CRIT_CHANCE` 属性、一向如此，而「固定为」说的是**不掷**（`fixedCrit`）；"固定 50%" 是第三种谁也没法读的东西，所以装载期按名字拒绝。⚠ 两个字段**同进同出**（只有一半时无法知道用哪个数掷/不掷）。⚠ 口径：这个字段按**面板的口径**写（1.5 = 150%），暴击区乘的是 `1 + 该值`，所以 150% 在结算里表现为 **×2.5**（就像面板 0.5 表现为 ×1.5）—— 把它当"倍率"读会得到 1.5 vs 2.5 的静默分歧（变异 m2 正是这个）。**测试** `FixedCritTest` 3 条。**变异**（实测）：固定暴击不落地 → **2 红**；把 stat 当倍率读 → **2 红**；允许任意 `crit_rate` → **1 红** |
| `DAMAGE` 的 `element`（**自带元素**）与"读非伤害技能的行" | 在 `DAMAGE` 上可加 `element`；当规则**写了元素**时，`skill` 可以指向一个**非伤害技能** | ✅（2026-09-28）知更鸟【协奏】的附加伤害：「额外造成1次等同于其自身 **120%** 攻击力的**物理**属性附加伤害」—— 那个 120% 就在她**终结技自己**的参数行里（`damage_param: 3`），而她的终结技是 **Support** 技能、**没有元素**，所以旧版会被两个检查分别挡住（"指向的技能不是伤害技能" / 元素为空）。规则**自带元素**正是这一句的写法；⚠ 没有元素时仍然**响亮拒绝**读非伤害技能（那才是这个检查要防的静默洞）。**测试** `RobinConcertoTest`（内容级）覆盖 |
| `actor == countdown`（身份词） | 条件 `actor == countdown` / `target == countdown`：事件主体是**我放的**倒计时 | ✅（2026-09-28，M-49 收尾）与 `summon` 同形（`actor == summon` = 我召唤的那个）。⚠ 它读的是**场上关系**（`Countdown.owner` + `Battle.countdownsOf(owner)`），所以**没有战场上下文的规则一律为假**（对两个极性都是假）—— 与 `summon` 同一条约定：回答"没有战场、所以不是我的"会让 `actor != countdown` 在**任何**事件上悄悄为真。⚠ 读的是**谁放的**，不是"哪个倒计时"：一个战斗里可以有多个。**测试** `CountdownTest.somebodyElsesCountdownIsNotMine`（别人的倒计时不触发、我的触发）。**变异**（实测）：任何倒计时都算我的 **1 红**；主人链没记上 **2 红** |
| `APPLY_BUFF` 的 `suspends_turns`（**布尔**） | 见左列 op；为真时**该单位不会进入自己的回合** | ✅（2026-09-28）「【协奏】状态结束前**不会进入自己的回合**且无法行动」（知更鸟 ⑦）。⚠ **它不是控制**：`ControlBuff` 让单位**不能行动**但**回合照常到来**（DOT 照跳、按时长计数的 buff 照样走），而这一句说的是**回合本身不发生** —— 正因如此倒计时才能**代替她行动**；把两者混为一谈会**静默改变**语料里每一条「被冻结仍会掉血」的读法。⚠ 它**挂在状态上**（`APPLY_BUFF` 是唯一设置它的 op，`BuffManager.suspendsTurns()` 扫 `StateBuff`），所以 `REMOVE_STATE` 把回合还回去时**没有第二个开关**会忘记。⚠ 跳过位置在 `Battle.beforeMove` 的**最前面**：不跳 DOT、不发 `TURN_START`、不调自己的钩子。**测试** `TurnSuspensionTest` 3 条。**变异**（实测）：跳过被去掉 **1 红**；标志没盖上 **3 红** |
| `MODIFY_ATTR` / `RESIST_DEBUFF` 的 `buff`（**名字**） | 见左列 op 的字段；`REMOVE_STATE <名字>` 会 **连同**带这个名字的修饰一起移除 | ✅（2026-09-28）「处于【协奏】状态时，我方全体攻击力提高…」与「…免疫控制类负面状态」—— 这两个增益的**时长跟着状态走**，而状态由倒计时结束（不是回合数）。此前**按名移除只能移除状态**（`APPLY_BUFF` 造裸 `StateBuff`），属性修饰只能写成 `permanent`（**永不撤下** = 没有症状的持续错误）。做法：`AbstractBuff.buffName`（空 = 未命名）＋ `BuffManager.removeState` 多一个"按名收命名修饰"的循环（⚠ **未命名的跳过** —— 这个字段不改变任何既有内容的寿命）；`MODIFY_ATTR` 与 `RESIST_DEBUFF` 读取 `buff` 并盖章（后者原本**拒绝**这个字段）。⚠ 状态与它的命名效果**可以同名**，于是一句 `REMOVE_STATE 协奏` 把它们一起撤掉。**测试** `NamedBuffTest` 2 条。**变异**（实测）：名字没盖上 **1 红**；按名移除不收修饰 **1 红**；未命名的也被移除 **1 红** |
| `START_COUNTDOWN` | `speed`（正数，倒计时的固定速度；可选 `buff` 作为日志里的名字） | ✅（2026-09-28，M-49）「行动序列上出现【协奏】倒计时…倒计时固定拥有 90 点速度」。它放一个**只为了"有一回合"而存在**的单位（`Countdown extends CanHit`）进 `Queue` —— 于是所有已经会**移动行动序列**的机制（推条、延迟、弱点击破）**自动**也移动它（这就是为什么「直到倒计时回合」不能用 `turns` 表达）。⚠ **两条刻意的限制**：它**不在** `Battle.allies` 里（在我们阵营、但不属于队伍 —— 否则「我方全体」会把它算进去、`lowest_hp_ally` 可能选中它、它还会成为所有指向队友的效果的合法目标），并且它**不能被打死也吃不到伤害**（`isDeath` 恒 false、`takeDamage` 空实现：一个能被流弹删掉的时钟会**静默地**改掉一个持续时间）。⚠ 它的回合会发事件 `COUNTDOWN_TURN`（`actor` = 该倒计时），内容用**现成**的词回答（`REMOVE_STATE` + `EXTRA_TURN self` = 「退出【协奏】状态并立即行动」）。**测试** `CountdownTest` 4 条。**变异**（实测）：回合不发事件 → **1 红**；把它加进 `allies` → **1 红**；`takeDamage` 直通 → **1 红**（⚠ 第一版变异改的是 `isDeath`，被**错的用例**报红 —— 那是"看起来被覆盖"的弱变异） |
| `MODIFY_RULE` 的 `effect_percent` / `effect_turns`（**第三形态**） | `rule` + `effect_percent`（抬数值）或 `effect_turns`（抬时长）；一次只能写一种 | ✅（2026-09-28）「终结技的**持续时间**额外增加1回合」「天赋的伤害提高效果**额外提高10%**」（1215 星魂 4/6）。⚠ **不能写成"再一条更大的规则"**：同类修饰是**刷新**不是叠加，第二条 0.1 会**覆盖** 0.3（结果 0.1，看起来还不坏），时长同理（第二条 `turns` 只会替换）。实现在**读效果的地方**：`fire` 的效果循环按被指名的规则取一份 `EffectSpec.copy()` 调整后传下去（无加成时**原样传同一个实例**，常见路径零成本）；装载期要求被指名的规则里**真的有** `percent`/`turns`，否则响亮拒绝。⚠ **`EffectSpec.copy()` 是手写的全字段拷贝** —— 漏一个字段就会在"刚好有加成的那几次"里静默丢值，由 `RuleEffectAmendmentTest.theCopyCarriesEveryField`（反射逐 getter 比较）钉住。⚠ **修正案规则必须排在它要改的规则前面**（同一事件按文件顺序执行；排在后面只是"改晚了"，一点效果都没有）。**测试** `RuleEffectAmendmentTest` 4 条；**变异**：不抬数值 **1 红**、copy 漏字段 **1 红**、不抬时长 **1 红**。⚠ 时长那一条**第一次是 0 红**：只断言"修正案已登记"挡不住"应用代码被删"。补行为用例时又踩了一个 harness 细节 —— **`StateBuff` 是晚结算 buff**（`super(turns, false, …)` ⇒ `isEarlyBuff = false`），寿命在 `Battle.afterMove()` 里走；只调 `beforeMove()` 的 harness **永远不会让状态过期**，于是"2 回合后应该没了"的前置断言也一起假绿（已写进该用例的注释） |
| `target_when`（**逐目标条件**，任何 op 都可用） | 效果上多一个 `target_when: ["<条件>"]`；条件的 **`target` 绑定到候选单位**，逐个候选求值 | ✅（2026-09-28，`M-53`）「对**所有触电状态下的**敌方目标造成…附加伤害」（1103 天赋）、「使**非触电状态下的**敌方目标陷入触电」（1103 星魂 4）、「对**生命百分比 ≤ 30% 的**我方目标附加持续治疗」（1105 星魂 2）。⚠ **为什么需要它**：规则的 `when` 过滤的是**规则**，所以「所有触电的敌人」旧写法只能是"我打的那个敌人触电了" —— 于是**没触电的敌人也一起吃伤害**（一个没有症状的错数字）。**选择器**说"哪些单位"，`target_when` 说"其中哪些合格"。⚠ 实现在**唯一一处**：`resolveTargets` 返回前逐候选过一遍（`ctx.targetFilter()` + `passesTargetFilter(candidate)`），29 个 op **一行都不用改**；条件在**装载期**解析（写错立刻报，而不是被忽略），且**按效果下标**各存一份（一条规则的两个效果可以过滤得不同）。⚠ 求值候选时 `target` 被换成该候选、并且**丢掉过滤器本身**（否则会自问自答地递归）。**测试** `TargetFilterTest` 4 条（正/反极性、数值条件读候选的面板、装载期拒绝写错的条件）。**变异**（实测）：不应用过滤器 **3 红**；用**事件的 target** 而不是候选求值 **3 红**；装载期不解析过滤器 **4 红** |
| `APPLY_REGEN` | `scale`（`owner_max_hp` / `target_max_hp` / `owner_def`）+ `percent`（可选 `amount` 作常数项）+ `turns` + `buff`（名字，必填）+ `target` | ✅（2026-09-28）「目标每回合开始时为其回复等同于娜塔莎 7.20% 生命上限 + 192，持续 2 回合」（1105 战技第二句）—— **`APPLY_DOT` 的治疗孪生**：载体是 `RegenBuff`（**早结算** buff，和 `DotBuff` 一样），结算放在 `Battle.tickRegens`，紧跟 DOT 那一趟、且在**寿命结算之前**。⚠ 这正是为什么它不能写成 `TURN_START` + 状态：buff 的寿命在早期 tick 里结算，而那个 tick 在 `TURN_START` **之前**，所以 `turns: 2` 的**状态**在第二个回合开始时已经没了（只跳 1 次，而文档说 2 次）；写 `turns: 3` 能让次数对，却把时长说小了 —— 两种都是**看起来对**的错。⚠ 幅值走**治疗/护盾**的 scale 词表（`SCALES`），不是 `MODIFY_ATTR` 那套 `self_attr:`；`buff` 必填，因为「施放战技产生的持续回复效果延长 1 回合」要靠名字找到它。**测试** `NatashaHealTest`（`turns: 2` 真跳两次 + 行迹 +1 第三次 + 之后停 + 重复施放是刷新）。**变异**（实测）：不结算 **1 红**；`isSameKind` 改成可叠加 **1 红**；改成晚结算 **0 红**（⚠ 早/晚在今天的出货内容里**不可观测**，如实记下而不是宣称已钉）|
| `MODIFY_RULE` 的 `effect_max_stacks`（**第四形态**） | `rule` + `effect_max_stacks`（抬**可叠加上限**）；一次只写一种修正 | ✅（2026-09-28）「使天赋的效果**可叠加上限提高 2 层**」（1302 星魂 4 后半）。⚠ **上限不是数值**：差别只在"内容本来会超过旧上限"时才看得见 —— 所以写成"再补两层"是**错的**（补的层仍受 10 层上限约束）。⚠ 修正案在**战斗开始**登记、层数在之后一次次命中时叠上去，所以新上限来得及生效 （`max_stacks` 是 buff 实例上的值，事后抬没用）。⚠ 断言要量**属性**而不是"按名字计数的层数"（多个修饰组可能同名）。**变异**（实测）：不应用加成 **1 红**、`copy` 漏字段 **1 红** |
| `REMOVE_STACK` 的 `buff`（**按名字减层**） | `attribute` **或** `buff`（名字），二选一；+ `amount`（层数） | ✅（2026-09-28）「每次我方目标回合结束时，移除驭空 1 层【鸣弦号令】」（1207 驭空）。⚠ 另两种拼写都做不到：`REMOVE_STATE <名字>` 的设计是**整个拿掉**（装载期注释写着 `amount` 会被静默忽略），旧的 `REMOVE_STACK` 只认**属性**。与同文件里 `EXTEND_BUFF` 的"名字或属性二选一"**同形**，两者现在对称。⚠ 减不到不是错误（规则在每个回合结束都会跑）。⚠ **命名纪律**：层与它带来的增益**不能同名** —— `stacksOf(<名字>)` 数的是所有带该名字的 buff，同名会让"2 层 + 1 个增益"读成 3 层，并让按名减层**吃掉那个增益**（实测抓到）|
| 事件 `TURN_END` | 无需字段；`actor`/`target` 都是**刚结束回合**的那个单位 | ✅（2026-09-28）「每次我方目标**回合结束时**…」（1207 驭空）。在 `Battle.afterMove` 里、该单位自己的 `afterMove` 钩子与**晚结算**之后发出 ⇒ 规则看到的是"这一回合结束时的状态"。⚠ **不能**用下一个单位的 `TURN_START` 顶替：一场仗的最后一回合没有下一个单位；buff 的寿命结算也不是事件。⚠ 同一事件内"读减层前"与"读减层后"可以共存 —— 条件在**轮到该规则时**求值（第 54 轮），所以把读旧值的规则排在前面、读新值的排在后面即可 （1207 的回能 vs 归零清理就是这么写的）|
| `BOOST_TOUGHNESS` | `percent`（正数=更高）+ `turns`/`permanent` + `target` | ✅（2026-09-28）「使本次攻击的**削韧值提高 100%**」（1207 天赋；语料同族 **68 处 / 20 文件**）。读者是攻击者身上的一个带时长 buff，**唯一读取处**是 `SkillExecutor.applyStanceDamage` —— 把"名义削韧值"变成实际削减的地方，也是削韧发生的**唯一**入口（另外两处调用是 `EnemySkill` 与演示脚本）。⚠ **`Battle.reduceToughness` 故意不读它**：那方法拿到的是"这一次实例的削韧值"，把修正塞进去会连带改变敌方技能与演示脚本。⚠ 次序（从代码读出，不必猜）：`CAST_SETUP` → `applyDamage`（`DEALING_DAMAGE` 在此发出）→ `applyStanceDamage` ⇒ 挂在 `DEALING_DAMAGE` 上的规则来得及。⚠ 「本次攻击」的拼法是**一回合**的增益（buff 没有"只此一次"的寿命）。**测试** `ToughnessBoostTest` 2 条；⚠ **harness 两个坑**（第一次测出 0）：① 攻击必须用**真实技能对象**（手搭的 `DefaultSkill` 不带削韧值）；② 怪要**问出来**弱什么（`getStanceWeak()`，非弱点削韧为 0）；③ 驱动攻击要用 `castImmediate`，`useSkill` + 手设 `currentMove` 测不出削韧 |
| `SUPER_BREAK` | `percent`（转化倍率）+ `target`；**只能挂 `DEALING_DAMAGE`** | ✅（2026-09-28）「会将本次伤害的**削韧值**转化为 1 次 X% 的超击破伤害」（1321 战技/天赋族、8006 终结技）。幅值 = `percent × ctx.damage().getStance()`，按触发攻击的**元素**结算一次 `DamageType.SUPER_BREAK`。⚠ **幅值取的是削韧值而不是伤害**：两者差一个数量级，取错也"看起来合理"。⚠ 读到"本次实例的削韧值"要靠 `Damage.stance` —— 由 `SkillExecutor` 在 `battle.applyDamage` **之前**写入（`DEALING_DAMAGE` 正是在 `applyDamage` 里发出）。⚠ 只能挂 `DEALING_DAMAGE` 的校验放在 `TriggerTable.compile`：**装载期那层 switch 没有 event**（只有 effect/op/spec）。**测试** `SuperBreakTest` 4 条 + `SuperBreakContentTest` 3 条；**变异**（实测）：去掉 `setStance` **1 红**；`isBroken` 恒假 **3 红** |
| 条件 `has_state 弱点击破` | 普通条件，无需字段 | ✅（2026-09-28）「对**处于弱点击破状态**的敌方目标…」（语料 **58 处 / 15 文件**）。它是**场上的事实**（韧性条被削到 0），不是谁加的 buff，所以加在 `BuffManager.hasState` 的**引擎状态表**里，与「灼烧」「冻结」同表：`instance instanceof Enemy && enemy.isBroken()`。⚠ 角色永不处于该状态（用例钉了）。**测试** 同上；**变异**：恒假 **3 红** |
| `REMOVE_BUFF` | `amount`（解除几个）+ 可选 `target` | ✅（2026-09-28）「解除指定敌方单体的 1 个**增益效果**」（1106 佩拉 战技；语料同族 6 处 / 4 文件：1106×2、武器×2、1111、1203）。它是 `DISPEL` 的**镜像**：那个清的是**我方身上的负面**，这个扒的是**敌方的正面** —— 方向相反，所以是独立 op 而不是 `DISPEL` 的一个开关（同一个词「解除」在两个方向上意思相反，各自校验、各自测试）。两者都建立在**按 buff 类**给定的 `isDebuff()` 上，因此都不会拿错半个。⚠ **永久 buff 被跳过**：遗器/行迹给的加成不是句子里的「增益效果」（没有谁的技能能驱散一套配装），扒掉它就是没有症状的错数字。**测试** `PelaDebuffTest.herSkillStripsABenefitAndNotADebuff`（同时钉方向：拿增益、留负面）；**变异**（实测）：把判定反过来（`isDebuff()`）**1 红**；去掉"跳过永久"**0 红**（今天没有出货内容带永久增益 ⇒ 如实记，不宣称已钉）|
| `APPLY_BUFF` 的 `base_chance` | `buff` + `base_chance`（可选，`(0,1]`）+ `turns`/`permanent` + `target` | ✅（2026-09-28）「有 **100% 的基础概率**使敌方每个单体目标陷入【通解】状态」（1106 佩拉；语料同族 **69 处 / 24 文件**，已出货角色里 6 位）。状态走 `Battle.tryApplyDebuff` —— 与 `APPLY_DOT`/`APPLY_CONTROL` **同一条路**，所以**效果抵抗照常参与**。⚠ **「100% 基础概率」不是"必定施加"**：基础概率是**抵抗之前**的数（用例实测：0.999999 的掷骰对 1.0 也会失败）—— 无条件施加就是把「基础概率」变成"永远"，那是**换机制**。⚠ **不写 `base_chance` 时行为与从前完全一致**（直接挂上）⇒ 既有内容一个数都不变。⚠ 裸 `StateBuff` 的 `isDebuff() == false`、没有 `debuffClass` ⇒ 不受**类别抵抗**影响（符合"没有文档称它为控制或 DOT"），这是该拼写的**上限**。**测试** `RolledStateTest` 3 条；**变异**（实测）：去掉掷骰（改成无条件挂上）**2 红** |
| `GAIN_ENERGY` 的**群体形态** | `amount` 或 `scale`（`target_max_energy` / `cast_applied:<状态>`）+ `percent` + `target`（可为 `all_allies`/`party`/`other_allies`） | ✅（2026-09-28）「为**除自身以外**的队友恢复等同于**各自** 20.00% 能量上限的能量」（1217 藿藿 终结技）。⚠ 2026-09-28 之前这个 op 只解析**一个**目标，所以群体句只能登记；让**解析**变成列表（而不是把同一个数发 N 次）才是对的 —— 因为每个接收者的份额取自**它自己**的能量上限。`scale` + `per_target` 依旧被拒（有了列表就重复了，一个含义两种拼写正是错数字的源头）。**测试** `HuohuoTest.herUltimatePaysTheOthersNotHerself`（队友能量上升、她自己不升、攻击力同样只给队友）；**变异**（实测）：把 `other_allies` 的"排除拥有者"去掉 ⇒ **6 红**（含 3 条既有守卫）|
| `MODIFY_DAMAGE_TAKEN` 的 `damage_type` 与 `base_chance` | `percent`（正=易伤/负=减伤）+ `turns`/`permanent` + 可选 `damage_type`（`DamageType` 的名字）+ 可选 `base_chance` + `target` | ✅（2026-09-28）「受到的**击破伤害**提高 12%」（1301）与「有 **100% 的基础概率**使目标**受到的持续伤害**提高 30%」（1108 桑博 终结技）。⚠ **`damage_type` 是"哪一类** incoming **伤害"**：DOT 一档能写，是因为 `Battle.tickDots` 把持续伤害结算成 `DamageType.DOT`（本轮核实）；⚠ 与之相对的是**施加侧**的 `DOT_DAMAGE_BOOST` / `BREAKING_EFFECT`（"**我**打得有多疼"），两侧选错也"看起来合理"。⚠ **`base_chance` 不是"必定"**：它走与 `APPLY_BUFF`/`APPLY_DOT` 同一条 `tryApplyDebuff`（含效果抵抗），无条件施加会把「基础概率」变成"永远"；不写则保持既有行为（直接挂上）。**测试** `ScopedDamageTakenTest` 6 条（两条作用域各用**比例**断言、一条掷骰、一条拼错拒绝）；**变异**（实测）：忽略作用域 ⇒ **2 红**；去掉掷骰（恒挂）⇒ **1 红** |
| `APPLY_DOT` 的 `max_stacks` | `element` + `base_chance` + 伤害（`percent`/`scale`/**`amount`**）+ `turns` + 可选 `max_stacks`（层数上限）+ `target` | ✅（2026-09-28）「风化状态**最多叠加 5 层**」（1108 桑博 天赋）。**只实现句子断言的那一半**：**至多 N 层产生伤害**，超出的层照旧挂着（`DotBuff.isSameKind` 为 `false`，DOT 永不被顶掉）但**不结算**。⚠ 上限按**文档状态**计（引擎里"元素即状态"：两次风化是同一状态，风化与灼烧不是），且总量与顺序无关。⚠ **没有发明刷新策略**：语料里「重复施加会刷新」是 **0 处**。⚠ **仍未实现**（1307 黑天鹅那句）：「层数达到上限后可继续叠加、**产生伤害后移除超出上限的层数**」—— 它只对**一个**状态陈述过，推广到所有带上限的 DOT 属于**推测**，已登记。**测试** `DotCapTest` 3 条（无上限 3 层＝3 倍、上限 2 时 5 层仍＝2 倍、上限非正数装载期拒绝）；**变异**（实测）：关掉上限 **1 红**、上限差一 **1 红** |
| `APPLY_DOT` 的 `cap_scale`/`cap_percent`（派生上限） | `element` + `base_chance` + 幅值（`amount` 或 `scale`+`percent`，`scale` 可为 `self_attr:<属性>` / **`target_max_hp`**）+ `turns` + 可选 `max_stacks` + 可选 `cap_scale`+`cap_percent` + `target` | ✅（2026-09-28）「受到等同于**自身 24.00% 生命上限**的…持续伤害，**最多不超过卢卡攻击力的 338%**」（1111 卢卡 战技）。⚠ 这是 **`min_of_two`**：幅值取两个派生值的**较小者** —— 是**上限**、不是下限也不是替换（小血量目标保留自己那份）。⚠ **`target_max_hp` 是 DOT 唯一能用的"目标侧"份额**：在 DOT 校验里单独放行，**不动共享的 `scaleAttribute`**（它的另一个读者 `MODIFY_ATTR` 必须继续拒绝）。⚠ 两者都在 DOT **落地时读取并冻结**（DOT 的既有约定）。⚠ 只有 `APPLY_DOT` 读 `cap_scale`：其它 op **拒绝**它而不静默忽略。**测试** `DotCeilingTest` 3 条（大血量被上限压住、小血量保留自身份额、其它 op 拒绝）；**变异**（实测）：上限当替换 ⇒ **1 红**；取错一侧 ⇒ **2 红** |
| `REPLACE_SKILL` | `skill`（槽位，如 `COMMON`）+ `skill_id`（**数据行 id**）+ 寿命（`turns`/`permanent`/`until`）+ `target` | ✅（2026-09-28）「将**下一次**普攻强化为【酒花奔涌】」（1301 加拉赫；强化普攻 = 数据行 **130108**；同族语料 **108 处 / 30 文件**、**5 位已出货角色**命中）。⚠ **换技能由 buff 承载**（`SkillSwapBuff`）⇒ 「下一次普攻」**直接复用**既有的 `until: next_attack` 寿命，且该寿命的契约是「**after the owner finishes an attack that landed**」⇒ 强化普攻会被这次普攻**用上**、用完还原（这一点已由用例钉住）。⚠ 安装前**先捕获原技能** ⇒ 两次重叠的替换会**嵌套**而不是互相覆盖；`isDebuff()` 为 `false` ⇒ 「解除负面效果」拿不走一次强化普攻。⚠ **`skill_id` 是装载键（槽位）**：`data/skills.json` 的键是 1,2,3,4,6,7,8（8 = 强化普攻）；**传数据行 id 会载到空技能**（由 `EnhancedSkillDataProbeTest` 两侧守卫），`DefaultSkill` 那条"参数其实是槽位"的注释是**过时的**（第 98 轮据此下过错误结论）。⚠ 寿命支持 `turns`/`permanent`/`until`（`OPS_WITH_DURATION` 已含 `REPLACE_SKILL`，2026-09-28）；⚠ 1111 的**阈值版**（「≥2 层时强化」）还依赖**文件顺序**：条件在被走到时才求值，所以加层规则必须排在它之前。**变异**（实测）：把强化规则移到加层规则之前 ⇒ **1 红**（顺序论断被验证）；删掉 `self_stacks:斗志 >= 2` 条件 ⇒ **0 红**（在该子集里该条件是**冗余**的：加层规则已先给到 2 层）—— 如实记录，并保留它以免偏离文档。 **测试** `SkillSwapTest` 3 条 + `GallagherTest.theShippedSwapReplacesAndRestores`（内容级：该普攻**用到了**强化技能，并在 `Battle.fireAfterAttack` 广播后**还原**）；⚠ 该用例的第一版用 `castImmediate` 驱动、**没有**触发那次广播 ⇒ 看到替换仍在而误判为引擎错 —— **脚手架错、语义对**（与 `useSkill`/`castImmediate` 那次削韧踩坑同族）；**变异**（实测）：不安装 ⇒ **2 红**；把 `cap_scale` 的 `@SerializedName` 删掉 ⇒ **10 红** |
| `TICK_DOT` | `element`（**哪种状态**：引擎里"元素即状态"）+ `percent`（该状态**当前**伤害的份额）+ `target` | ✅（2026-09-28）「使其当前承受的裂伤状态**立即产生 1 次**相当于原伤害 85% 的伤害」（1111 卢卡 天赋；窄口径语料 **2 处 / 1 文件**，就是他那句）。⚠ 它**完全照抄 `Battle.tickDots` 的结算配方**（同 source / element / `DamageType.DOT` / `EnergyGrant.KILL_ONLY`）⇒ 是"这个状态又多跳了一次"，不是一种新伤害。⚠ **不改寿命**（句子要的是一次额外**伤害**，不是让状态变老）——用例用"再跑一次普通 tick 伤害不变"来观测它没被消耗。⚠ 幅值 = 该状态**当前**伤害的 `percent`，因此求和时**照旧套层数上限**（带上限的 DOT 跳的是**封顶后**的总量）。**测试** `TickDotNowTest` 4 条；**变异**（实测）：忽略份额 **1 红**、结算所有状态 **1 红** |
| `from_skill_id`（条件变量） | 数值变量：本次事件由**哪个技能键**产生（`data/skills.json` 的装载键，强化普攻 = **8**） | ✅（2026-09-28）「当**强化普攻**的【碎天拳】击中陷入裂伤状态下的敌方目标后…」与「强化普攻**消耗 2 层**【斗志】」（1111 卢卡；同族 108 处 / 30 文件）。⚠ 取值是**装载键/槽位**，**不是数据行 id**（第 115 轮实测：传行 id 会载到空技能）⇒ 条件必须写 `from_skill_id == 8`。⚠ 上下文的所有拷贝点都要带上它（第 111 轮的教训：按参数个数自动补零会把拷贝点一并归零）。**测试** `LukaTest.theEnhancedAttackPaysTwoLayers`（**深前置**：先断言换入的技能真的载到数据，再断言代价）；**变异**（实测）：槽位 8→9 ⇒ **1 红** |
| `DEALING_DAMAGE` 携带技能键 | 事件的实例上多一路 `Damage.skillKey`（= 施放它的技能**装载键**）| ✅（2026-09-28）「**并使目标攻击力降低 15.00%**，持续 2 回合」（1301 加拉赫强化普攻）。⚠ 这是**唯一同时带目标与技能键**的事件：`ALLY_ATTACK` 刻意不带被瞄准的单位（否则「使目标…」永远解析不到目标），而 `DEALING_DAMAGE` 一带有目标、二在 `Damage` 上带了键。⚠ 实现路径：`Damage` 加 `skillKey` → `SkillExecutor.hit(...)` 多收一个键（**7 个调用点**，全在同一形状分派方法内）→ `Battle.applyDamage` 用带键的重载发射。**测试** `GallagherEnhancedAttackTest`（正向 + **负向对照**：普通普攻必须一点不降）；**变异**（实测）：发射处把键换成 `0` ⇒ **1 红** |
| `MODIFY_RULE` 的分派 | 按效果**带了哪个字段**决定改什么：`amount` = 每回合可触发**次数**、`effect_turns` = 该规则效果**时长**、`effect_max_stacks` = 效果**叠层上限**、`effect_percent` = 效果**百分比**、**裸 `percent` = 基础概率修正** | ✅（2026-09-28）读者：1104 杰帕德星魂 1「施放战技时，使受到攻击的敌方目标陷入冻结状态的基础概率提高 35%」（同族：「冻结敌方目标的基础概率提高 15%」等）。⚠ 取值处是 `baseChance += ctx.owner().ruleBaseChanceBonus(ctx.ruleId())` —— 修正**按施加该效果的那条规则 id** 入账，所以 `rule` 必须写**那条规则自己的 id**。**测试** `GepardEidolonTest`（**确定性**：E0 读数为 0.0、E1 恰为 0.35 —— 读数本身就是断言，不碰骰子）|
> **2026-09-28（第 131 轮，写控制类效果时实测）控制类效果的验证陷阱**：`Battle.hitChance` 是
> `baseChance * (1 + EFFECT_HIT_RATE) * (1 - EFFECT_RESISTANCE) * (1 - specific)`，其中 `specific` 对敌人取
> `enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0)`（冻结的键是它自己的控制抗性键）。
> ⚠ **本项目的 fixture 怪物（1002011）对该控制抗性为 1.0** ⇒ 命中率被钳成 **0** ⇒ 骰子**永远不过** ✗。这不是内容错，而是**目标免疫**。⇒ 凡验证控制类效果，请用 **`Enemy.fromAttributes(name, health, defence, attack, speed)`** 手工造一个**没有任何抗性**的目标（此时 E1 的 65%+35% = 100% ⇒ 完全确定 ✓）。
> ⚠ 另记：控制状态**自带**每回合的附加伤害（`APPLY_CONTROL` 上的 `element` + `scale` + `percent`），由 `Battle.tickDots` 结算 —— 所以「冻结状态下…每回合开始时受到…冰属性附加伤害」不需要另写一条 DOT 规则 ✓。

| `ADD_DAMAGE` **vs** `DAMAGE`+`damage_type` | `ADD_DAMAGE` = 给**正在结算的那一次**伤害**加一个固定值**（`damage.addFlat(...)`）；文档里的「**附加伤害**」是**另一次伤害实例**，要用 `DAMAGE` 并标 `damage_type: ADDITIONAL` | ⚠（2026-09-28 实测）**这是个命名陷阱**：两个名字互为反义。`ADD_DAMAGE` 的读者＝「本次伤害提高 X 点」类句子；附加伤害的读者＝「追加 1 次…伤害」（虎克天赋、克劳斯等）。⚠ `ADD_DAMAGE` 必须在 `DEALING_DAMAGE` 上（上下文要带 `damage`，否则装载/执行期报错）|
| `damage_is_attack`（条件，无主语无值） | 布尔：**正在结算的这次伤害算不算一次攻击**（`Damage.countsAsAttack`；附加伤害实例为假） | ✅（2026-09-28）读者：1109 虎克天赋「攻击处于灼烧状态的敌方目标时，**追加 1 次**…附加伤害」——它必须能**不自我触发**。⚠ 为什么是**正向**写法：`!` 取反只允许作用于队伍类条件（`parseCondition` 原话 *only those can be negated*）⇒ 写不出"非附加伤害" ✗。⚠ 只在**携带伤害实例的事件**上合法（与 `from_skill` 同一条校验 ✓）。**测试** `HookTalentTest`（用例**跑完**本身就是"没有递归"的证明 ✓）；**变异**（实测）：把 `damage_is_attack` 从 `when` 里去掉 ⇒ **2 红**，报错正是 `Trigger recursion exceeded 8 levels while firing DEALING_DAMAGE` ✓ |
| `DAMAGE`（额外伤害实例） | 造出一次 `DamageType.ADDITIONAL` 实例（`Battle.applyAdditionalDamage`：`notCountsAsAttack` + 受害者不给能量）| ✅（2026-09-28）读者：同上的虎克天赋。⚠ **必须给 `damage_param`**（倍率在技能参数行里的下标，**无默认值** ✗ —— 装载器原话 *there is no default because the index depends on…*）；倍率本身取自**技能参数行**，可用 `damage_level` 换行 ✓。⚠ 与 `ADD_DAMAGE` 的区别：后者是"给**当前**这次伤害加**固定值**"（`damage.addFlat`）✗，两者名字互为反义 ✓ |
| `is_other_ally`（谓词） | 主体 + 谓词："**该单位是我们的队友且不是规则主人**"（`!` 取反**不能**用于等值比较 ⇒ `!actor == self` 会被拒 ✗，而 `actor is_ally` 会把主人自己算进去 ✗） | ✅（2026-09-28）读者：1005 卡芙卡天赋「当卡芙卡的**队友**…施放普攻后…」（同族 16–20 文件 ✓）。**测试** `KafkaTest`（正向 ✓；负向对照**尚未通过变异** ✗，见 `GAPS` 第二十条 ✓）|
> ⚠ **2026-09-29（第 151 轮实测 + 已修）`DEALING_DAMAGE` 的 `fromCast` 一直是 `null`** ⇒ **`from_skill` 在该事件上永远不可能匹配** ✗。
> 证据是一次三分法探针（手工构造、只差条件字符串的两张表，观察对象只是"触电是否落地" ✓）：
> 修复**前**：`otherOnly=true  fromSkillOnly=false  both=false  noConditions=true` ✗；修复**后**：`otherSkillOnly=true  both=true` ✓。
> 原因：`Damage` **自身**带着 `castCategory`（来自技能数据 ✓），而发射处把 `fromCast` 传了 `null` ✗ ⇒ 一行可修 ✓（已修 ✓）。
> **读者**：语料里含「施放X后/时」从句的文档 **94 份** ✓；**8 个已出货角色文件**用了 `from_skill`（**36 处** ✓），其中**挂在伤害事件上的那些此前全是死的** ✗（挂在 `ALLY_ATTACK` / `*_CAST` 上的不受影响 ✓）。
> **变异**（实测）：把发射处改回 `null` ⇒ **1 红**，正是 `KafkaTest.anAllysBasicAttackTriggersHerFollowUp` ✓（该用例本轮已强化为观察**触电**，此前只断言"掉血"，而那对**任何**普攻都成立 ✗ ⇒ 是一条不判别的弱断言 ✓）。

> ⚠ **2026-09-29（第 152 轮）`DEALING_DAMAGE` 是"逐段伤害"事件，`ALLY_ATTACK` 才是"每次施放一次"** ✓（引擎自己的注释写明：在 `DEALING_DAMAGE` 上计数会**数段数**而不是施放次数 ✗）。
> ⇒ **凡文档写「施放 X 时」的规则，挂在 `DEALING_DAMAGE` 上会在多段技能里**过度触发** ✗**；但 `ALLY_ATTACK` **刻意不带被瞄准的单位** ✗（见 `engine.md` 中 `DEALING_DAMAGE` 携带技能键那一行 ✓）⇒ 需要目标的那类句子**两头都不合适** ✗。

> ✅ **2026-09-29（第 154 轮）`ALLY_ATTACK` 现在带上被瞄准的单位**（`aimed`）。
> 它的旧注释写着"刻意不传，因为没有规则会问它" ✗ —— 第 147/152 轮**证伪**了这一点：1215 的三条标记规则、1005 的追加攻击、1001/1302 的星魂规则**都需要目标** ✓，而把它们挂在 `DEALING_DAMAGE`（**逐段伤害** ✗）上会让多段技能**过度触发** ✗。
> ⇒ 现在**"每次施放一次"且"带目标"**的事件存在了 ✓（`ALLY_ATTACK` ✓），代价是那条注释被如实改写 ✓。
> **测试**：`KafkaTest`（她的天赋已迁到该事件 ✓，端到端验证追加伤害与触电 ✓）、`HanyaBurdenTest`（标记的**主体**修正为"另一个队友" ✓）；**变异**（实测）：把 `aimed` 改回 `null` ⇒ **1 红**（`KafkaTest.anAllysBasicAttackTriggersHerFollowUp` ✓）。

> ⚠ **2026-09-29（第 156 轮）`cap_scale`/`cap_percent` 仍只有 `APPLY_DOT` 会读** ✗（用 `MODIFY_ATTR` 写它会被装载器拒绝，原话就是 *only APPLY_DOT reads today*）。
> 尝试过放行给 `MODIFY_ATTR` 并复用 DOT 的 `applyDerivedCeiling`，**已回滚** ✗，原因值得记住：
> `applyDerivedCeiling` 解出的 ceiling 是**绝对值**（如「停云当前攻击力的 25%」✓），而 `MODIFY_ATTR` 的 `percent` 是**份额**（目标攻击力的 50%）⇒ `min(份额, 绝对值)` **单位不同** ✗，上限形同虚设（我的断言就是这么失败的 ✗）。
> **正确形状**：**逐目标**把上限换算成份额（`ceiling / 目标该属性值`）再 min ✓，而不是在得到份额后直接 min ✗。
> **读者**：`1202` 停云战技「攻击力提高 50%，**最高不超过停云当前攻击力的 25%**」✓；语料里「最高不超过 / 至多不超过 / 上限为」共 **14 个文件** ✓。

| `MODIFY_ATTR` + `cap_scale`/`cap_percent` | **有上限的属性修正**：`percent`（份额）或 `scale`+`percent`（绝对值），外加 `cap_scale` + `cap_percent` 给出的**上限** | ✅（2026-09-29）读者：1202 停云战技「攻击力提高 50%，**最高不超过停云当前攻击力的 25%**」；语料里「最高不超过/至多不超过/上限为」共 **14 个文件**。⚠ **两种形态的换算不同**（这正是第 156 轮回滚的原因 ✗）：**绝对值**形态（带 `scale`）直接与上限比大小 ✓；**份额**形态必须**逐目标**换算 —— `min(percent, 上限 / 目标该属性的 baseValue())` ✓，因为份额按目标的**基础值**结算 ✓，而直接把份额与绝对值比大小**什么也没比** ✗。⚠ 为此给 `DoubleValue` 加了 **`baseValue()`** 读取器 ✓（它原本只有 `base(...)` 设值器 ✗；若用 `get()` 的**总值**做分母，目标已有增益时上限会被**收紧** ✗，与文档不符 ✓）。**测试** `TingyunTest.herSkillBlessesWithACappedGain`（断言"**增幅 ≤ 上限**" ✓）；**变异**（实测）：去掉 `cap_scale`/`cap_percent` ⇒ **1 红**，正是该用例 ✓ |
> ⚠ **2026-09-29（第 158 轮实测）`BOOST_DAMAGE` 挂在 `DEALING_DAMAGE` 上实测**没有任何效果** ✗**（两次测量）：
> * 完整条件（`actor == self` + `from_skill SKILL` + `target_hp_percent >= 50`）：两只等防御木桩掉血 **712.5148800000024 vs 712.5148800000006**，比值 **1.0000000000000024** ✗；
> * 条件收窄到只剩 **`actor == self`**：**828.9388800000015 vs 828.9388799999997**，比值 **1.0000000000000022** ✗。
> ⇒ 条件从来不是原因，**增伤本身没生效** ✗。而 `Battle` 在 `DEALING_DAMAGE` 发射处的注释明写：该事件是"在 `applyDamage` 内部"**发出**的，**好让规则还能改它** ✓ —— 实测与此**矛盾** ✗。
> ⚠ 当前**没有任何已出货内容使用 `BOOST_DAMAGE`** ✓（其余出现都在测试里 ✗）；两条依赖它的规则（1013 的战技增伤与行迹「冰结」）**已撤下** ✗。
> **下一武**：读 `Battle.applyDamage` 的**顺序**（各区是否在事件之前已经算完/快照 ✗），以及 `Damage.addBoost` 写的那个区在结算时**是否还会被读** ✗。

> ⚠ **2026-09-29（第 158–159 轮）`BOOST_DAMAGE` 在 `DEALING_DAMAGE` 上**静默失效** ✗**（已用探针逐层排查）：
> 1. 同一条规则在 `BOOST_DAMAGE` 旁边再带一个 **`GAIN_ENERGY`** ⇒ **能量 5.0 → 45.0** ✓ ⇒ **规则确实触发、效果确实执行了** ✓（排除"规则没跑"）；
> 2. 同一条规则把 op 换成 **`ADD_DAMAGE`** ⇒ 掉血 **828.9388800000015 → 1068.7723200000037** ✓ ⇒ **固定加值照文档生效** ✓（排除"该事件上改不了实例"）；
> 3. ⇒ **只有 `BOOST_DAMAGE`（百分比区）是静默无效的** ✗。
> ⚠ **假设已被实测否定** ✗："百分比区读的是缓存的 `DoubleValue`，广播后没人 `commit()`" ✗ —— 我加了 `Damage.commitAreas()` 并在 `assemble` 的广播之后调用，**测量一个 ULP 都没动**（828.9388800000015 两次相同 ✗），该改动**已回滚** ✗。
> ⚠ **下一武的精确入口**：在测试里**自己构造** `Damage`（带 `BPSKILL` 类别 ✓）、调 `battle.applyDamage(target, damage)`，然后直接读 **`damage.boostArea().raw().get()`** ✓（都是 public ✓）—— 这能一步区分"加进去了但结算没读" 与"根本没加进去" ✓。

> ✅ **2026-09-29（第 160 轮）更正第 158/159 轮的错误结论：`BOOST_DAMAGE` 没有问题** ✓。
> 对照测量（**同一个 `Damage` 实例、同一场战斗、只删一个效果** ✓）：
> | 探针规则 | `boostArea().raw().get()` | `settled` |
> |---|---|---|
> | `BOOST_DAMAGE 0.2` + `GAIN_ENERGY` | **1.424** | **1424.0** |
> | 只留 `GAIN_ENERGY` | **1.224** | **1224.0** |
> ⇒ 百分比**写进了实例** ✓ 且**结算读了它** ✓。当初"无效"的读数来自：① 那条规则的条件**永远为假** ✗（见下）；② 后一次探针把条件收窄到 `actor == self` 后，**两只木桩都正确地被加成** ✓ ⇒ 相等是**应该**的 ✓，而我把它读成了"无效" ✗。
> ⚠ **`target_hp_percent` 是分数（0~1）** ✓：`TriggerTable` 用 `hpPercent(ctx.target())` 解析 ✓，引擎自己的注释就写着 `hp_percent <= 0.5` ✓。文件里写 `>= 50` ✗ ⇒ **条件恒假** ✗。把 `>= 50` 改回去的**变异会让恢复后的用例变红** ✓（实测 1 红 ✓）—— 这是这个"幻影 bug"最精确的陈述：**错在内容，不在引擎** ✓。

> ✅ **2026-09-29（第 162 轮，读源码确认）治疗侧的现有能力** ✓：
> * **`HEALED` 事件** ✓：`fireTriggersForAlly(HEALED, healer, target, healed)`（`Battle:1671`）—— **actor 是治疗者、target 是被治疗者、数值是治疗量** ✓；已出货读者：**1105、1321** ✓。
> * **治疗量属性** ✓：`OUTGOING_HEALING_BOOST`（从**治疗者**读 ✓）与 `HEAL_TAKEN_RATIO`（从**被治疗者**读、**负值即减少** ✓）—— 「治疗量提高 X%」因此**可表达** ✓（语料里 13 个文件 ✓）。
> * ⚠ **`HEAL` 不接受固定加值与倍率同时出现** ✗：校验是 `requireAmountOrScale` ✓ ⇒ 「8.00%生命上限**+160**」**无法表达** ✗。

> ⚠ **2026-09-29（第 163 轮实测）`HEAL` 的“份额 + 固定加值”取值与声明不一致** ✗**：
> 校验器允许 `scale` + `percent` + `amount` 同时出现 ✓（注释写的就是「X% 生命上限 + 760」✓），但实测（同一场、**不暴击**的 rng ✓、施放前取她的生命上限 ✓）：
> | 规则 | 声明 | 应得 | 实测 |
> |---|---|---|---|
> | 1409 战技 | `owner_max_hp` 8%% + 160 | 255.622912 | **304.8711988316728** |
> | 1409 终结技 | `owner_max_hp` 10%% + 200 | 319.52864 | **同一个 304.8711988316728** |
> ⇒ **两个不同声明得到同一个数** ✗ ⇒ "scale 在 amount 存在时是否被忽略" 之类的问题**必须读实现** ✓，不能靠注释反推 ✗。⚠ 304.87 = 255.62 × 1.193 ✓（看上去像有治疗加成 ✓），但终结技同样不符合该读法 ✗。
> **读者**：语料里「等同于…生命上限+N」共 **12 个文件** ✓。

> ⚠ **2026-09-29（第 164 轮实测）`HEAL` 的 `owner_max_hp`不是主人侧，且参数与声明不符** ✗**：
> 探针（**同一条规则、两个队友、生命上限不同** ✓）：
> ```
> ownerMaxHp=1195.2864000000002 | ally1002maxHp=882.0000000000001 healed=304.8711988316728
>                               | ally1001maxHp=1058.4 healed=310.87489370951243
>                               | ownerShare=255.622912
> ```
> 1. **同一条规则给两个队友不同的治疗量** ✗ ⇒ `owner_max_hp` **实际不是主人侧** ✗（而 `grantAmount` 的代码路径明明走的是 `ownerAttributeOf(ctx, "owner_max_hp", HEALTH)` ✗）。
> 2. 用那两个点反解 `heal = share × targetMaxHp + flat` 得 **share ≈ 0.034、flat ≈ 274.85** ✗ —— **与文件里声明的 8%%/160 与 10%%/200 都不符** ✗；而同一队友在两条规则下得到同一个数 ✗ ⇒ 两份声明也**没有被分开** ✗。
> ⚠ 两条都是从**外部观察**推出来的 ✓；下一件仪器是**最小的手建表**（`TriggerSpecs` ✓），只放**一条已知取值的 `HEAL`** ✓，把 op 从文件/装载器路径里**隔离出来** ✓。

> ✅ **2026-09-29（第 165 轮实测）技能**自身的数据就会治疗** ✓ —— 这是本轮最重要的基线：
> 在**完全不装规则**（`setTriggerTable(null)` ✓）、只施放一次战技的情形下：
> ```
> allyHealed=87.81145600000002 | hyacineHealed=0.0
> hyacineMaxHp=1195.2864000000002 | targetMaxHp=882.0000000000001
> ```
> ⇒ **队友仍然被治疗了 87.81** ✗ ⇒ 引擎会**从技能数据**里读出治疗量并在任何规则之前生效 ✓。
> ⚠ **因此每一次基于规则的治疗读数都是“基线 + 规则贡献”** ✗：304.8711988316728 − 87.81145600000002 = **217.0597428316728** ✓ 就是规则的贡献。
> ⚠ 待解：217.06 与两份声明（`owner_max_hp` 8%%+160 ⇒ 255.62；10%%+200 ⇒ 319.53）**都不符** ✗ —— 但现在这个问题**只关于规则那一部分** ✓，不再被基线混淆 ✓。

> ✅ **2026-09-29（第 166 轮实测）`HEAL` 的 `scale` + `percent` + `amount` **逐位精确** ✓**，而**技能自身的数据也会治疗** ✓ —— 两者**不得重复计算** ✗：
> | 测量 | 结果 |
> |---|---|
> | **纯规则**（手工发 `TURN_START` + `lowest_hp_ally`）`owner_max_hp` 10%% + 100 | healed **219.52864** vs declared **219.52864000000002**，比值 **0.9999999999999999** ✓ |
> | **无规则**，只施放一次战技 | 队友仍被回复 **87.81145600000002** ✗ |
> | 双向基线（风堇→丹恒 / 丹恒→风堇） | 贡献 **217.06** / **188.20000000000002**（后者**逐位符合**声明 ✓） |
> ⇒ `owner_max_hp` **确实是主人侧** ✓（纯规则时精确 ✓）；差值只在**同一次施放里同时有技能数据治疗**时出现 ✗（残留 约 1.1%%，已登记 ✓）。
> ⚠ **内容建模结论**：**治疗角色的规则不要重复写治疗** ✗（技能数据已经做了 ✓），否则**双重计算** ✗ —— 这正是第 163–165 轮那些“不可能的读数”的来源 ✓（304.87 = 技能数据 87.81 + 规则 217.06 ✓）。

> ✅ **2026-09-29（第 167 轮）相邻/位置：对**伤害**而言已经是已解决形状** ✓：
> `SkillExecutor` 的 `case BLAST` 用 `center = alive.indexOf(mainTarget)`（顺序 = `battle.targetableEnemies()`）取中心 ✓，再依次打 `center`、`center - 1`、`center + 1` ✓，中心与相邻各用自己的韧性系数 ✓。因此**语料里 40 份"对相邻造成伤害"不是缺口** ✓；真正缺的是**"相邻"作为条件**（5 份 ✓）。

> ⚠ **2026-09-29（第 168 轮）「每层 +X%」的正确形状是“读取时求值”** ✗**：
> 语料共 **14 份**（1009、1212、1302、1306、1307、1314、1315、1401、1402、1408、8001、8002、GLOS、RELI ✓），几乎全是**持续光环**：“每拥有 1 层…提高 X%” ✓ ⇒ 其份额必须在**被读取时**解析 ✗，而不是在 `MODIFY_ATTR` 应用时取一次快照 ✗（20 行的 `stacks_from` 尝试已回滚 ✗）。
> ⚠ 实现方向：让 `DoubleValue` 支持**一类活修正**（读取时取值 ✓），而不是新增 `scale` 拼写 ✗。

> ✅ **2026-09-29（第 169 轮）修正：`BLAST` 的**相邻**此前吃了中心的倍率** ✗** —— 影响**所有扩散技能** ✗：
> * 证据：`data/skills.json` 里 **1008 阿兰** 的终结技行（槽位 3、`Ultra`、`effect=Blast`）每级带**两个**倍率 ✓：`[1.92, 0.96]`…`[3.2, 1.6]` ✓ —— 第二个**正好是第一个的一半** ✓，与「320%…同时对其相邻目标造成 160%」完全一致 ✓。
> * 原来：`case BLAST` 只算一个 `base = ATK × params.getFirst()` ✗，并把它用于**中心与两侧** ✗ ⇒ 每个扩散技能的侧翼都打了**双倍** ✗。
> * 修正：数据给了第二个数就用它（`params.get(1)` ✓），没给就回退到第一个 ✓。
> **测试**：三个**完全相同**的木桩，对中间那个施放终结技，断言**中心 : 相邻 = 2 : 1** ✓（文档自己的比例 ✓）；**变异**（实测）：把相邻改回 `base` ⇒ **1 红**，正是该用例 ✓。

> ⚠ **2026-09-29（第 170 轮实测）弹射（`Bounce`）的参数布局逐个不同** ✗**：
> `1009 [0.25]`（命中数**不在数据里** ✗）、`1108 [4, 0.28]`（**命中数在前** ✗）、`1004 [0.36, 0.65, 0.1, 2]`（**命中数在最后** ✗）。代码只读 `params.getFirst()`/`get(1)` ✗ ⇒ 艾丝妲少打 4 次 ✗、桑博倍率变成 400% ✗、**维尔特（已出货）命中数算成 0 ⇒ 一次都不打** ✗✗。
> 正确来源：**描述里的 `#N[...]` 占位符** ✓（`SkillData` 已保留原文 + 正则 ✓）。

> ✅ **2026-09-29（第 170 轮）修正：`Bounce` 从**描述的占位符**取伤害份额与命中数** ✓**：
> 实测三行（`skill_effect = Bounce` ✓）：`1009 [0.25]`（命中数只在**文案**里 ✗）、`1108 [4, 0.28]`（命中数**在前** ✗）、`1004 [0.36, 0.65, 0.1, 2]`（命中数**在后** ✗）。
> 旧代码读 `params.getFirst()` 与 `(int) params.get(1)` ✗ ⇒ 已出货的**维尔特命中数算出 0** ✗（战技一次都不打 ✗）。
> 修正：伤害份额 = 被 `#N[i]%` 引用的参数 ✓；**额外**命中数 = 未被 `%` 引用的整数参数 ✓；`hits = 额外数 + 1` ✓（对应「额外造成 N 次」✓）。来源与 `SkillData.debuffChance()` 同一套占位符解析 ✓。
> **测试**：新增 `WeltTest.hisSkillActuallyDealsDamage`（已出货角色的战技**必须打得出伤害** ✓）与两条既有弹射用例（fixture 现在也声明描述 ✓）；**变异**（实测）：恢复固定下标 ⇒ **3 红** ✓。

> ⚠ **2026-09-29（第 171 轮）修正器可用的生命周期只有三个** ✗**：`next_attack`、`next_skill`、`next_ultimate` ✓（装载器原话 ✓）。「持续至自身回合结束」无精确写法 ✗；读者 **2 份** ✓（1213、1312）。
> ⚠ 时长由**回合机制**结算 ✓：手发 `TURN_END` 并不清空带 `turns` 的状态 ✗ —— 验证必须**真推进一个回合** ✓。

> ⚠ **2026-09-29（第 172 轮）`ADD_STACK` 的 `amount` 不是层数** ✗ —— 一次触发只加**一层** ✓（实测 ✓）。
> 这是**故意为之、且已登记**的设计 ✓（`LukaTest` 的类注释 + `1111.json` 的登记 ✓），不是 bug ✗；我试图让 op 读 `amount` 时，**该不变量被守住它的用例挡下了** ✓ ⇒ 已回滚 ✓。
> 若未来真要支持"一次 N 层"，必须**同时**更新 1111 的登记与 `LukaTest` ✓。

> ✅ **2026-09-29（第 173 轮）技能效果种类的归属地图** ✓（数据与代码对扫的结果 ✓）：
> 数据里共 **11 种** `skill_effect` ✓，**每一种都有归属** ✓：
> | `skill_effect` | 处理位置 |
|---|---|
| `SingleAttack` / `AoEAttack` / `Blast` / `Bounce` / `MazeAttack` | `SkillExecutor` 的伤害分支 ✓（Blast 读两个倍率 ✓、Bounce 读描述占位符 ✓）|
| `Defence` / `Restore` | **数据表** `data/skill_effects.json` ✓（20 位角色 ✓，由 `SkillEffects` 加载 ✓，`SkillExecutor:248` 使用 ✓）|
| `Support` / `Impair` / `Enhance` | **角色自己的规则** ✓（例：1202 停云的【赐福】是 `SKILL_CAST` 规则 ✓）|
| `Summon` | 忆灵/召唤物机制 ✓（`SUMMON` + `SUMMONED` + `summon` 选择器 ✓）|

> ⚠ 我曾把 `SkillExecutor` 的 `default -> {}` 当成"非伤害类没人管" ✗ —— 那是**漏读了它前面的百多行** ✗：`Defence`/`Restore` 走的是 **数据表** ✓。
> ⚠ 同一张表还是"哪个参数是什么意思"的官方图 ✓（`params: [{index, kind}]` ✓），第 170 轮的弹射修正当初是从**描述占位符**推出来的 ✓ —— 若先读到这张表，会更快 ✓。

> ✅ **2026-09-29（第 174 轮）伤害基数取自描述命名的属性** ✓**：
> 旧行为：所有伤害形状都算 `ATTACK × params.getFirst()` ✗，而描述里有两类例外 ✗：
> * 「等同于砂金 100% **防御力**」⇒ `DEFENCE` ✓；实测**3 份**（1304、8003、8004）✓；
> * 「等同于风堇 50% **生命上限**」⇒ `HEALTH` ✓；实测**18 份**（1105、1110、1111、1205、1208、1212、1217、1401、1403、1404、1407、1409、1410、1413…）✓；
> * 其余保持 `ATTACK` ✓。
> 现在基数由 `SkillData.damageBaseAttribute()` 从**描述**判定 ✓（引擎本就保留了原文与去标签的正则 ✓），**中心、扩散两侧、弹射份额**都用同一个基属性 ✓。
> ⚠ **21 份此前打的是错的伤害** ✗，其中多位**已出货** ✗ ⇒ 现已修正 ✓。
> **测试**：映射单测（琳希→`HEALTH`、砂金→`DEFENCE`、丹恒→`ATTACK` ✓）；**变异**（实测）：让映射恒答 `ATTACK` ⇒ **2 红**（恰是非 ATK 的两条 ✓）。
> ⚠ 我先试了**伤害比值**断言（`掉血 / 份额` 对比属性 ✗）并两条都失败 ✗：那个比值是**基数 × 结算各区** ✗，测的是各区而不是基数 ✗。

> ✅ **2026-09-29（第 176 轮）`SHIELD`/`HEAL` 新增 `owner_attack` 刻度** ✓**：
> * 旧词汇：`target_max_hp` / `owner_max_hp` / `owner_def` ✗ —— **没有攻击力形态** ✗，而「等同于…**攻击力**」的护盾与治疗：实测**护盾 2 份**（1203、1414）✓、**治疗 6 份**（1203、1215、1221、1222、8001、8002）✓ ⇒ 共 **7 份** ✓（其中 **1203 已出货** ✓）。
> * 现在：`SCALES` 多一个名字 ✓ + `grantAmount` 多一个分支（复用 `ATTACK` ✓）。
> **出货读者**：1414 丹恒•腾荒（战技的【同袍】+ 全队护盾 ✓、终结技的全队护盾 ✓）；**测试**：护盾数值 == `攻击力 × 0.2 + 400` ✓；**变异**（实测）：去掉固定加值 ⇒ **1 红** ✓。

> ✅ **2026-09-29（第 178 轮）秘技门槛：**用过秘技**变成一个普通状态** ✓**：
> * **为什么这么小**：秘技发生在**战斗之外** ✗，引擎观察不到 ✗ ⇒ `Battle.markTechniqueUsed(unit)` 由**调用方**声明 ✓，`startBattle()` 在**任何 `BATTLE_START` 规则之前** 把它变成一个普通的 `StateBuff("秘技")` ✓；而 `hasState` **本来就能按名字问任何 `StateBuff`** ✓ ⇒ 内容用**现有词汇**就能问（`self has_state 秘技` ✓），**不新增条件关键字、不新增 op** ✓。
> * **读者（本轮实测 ✓）**：「使用秘技后」**68 份** ✓、「下一次战斗开始时」**29 份** ✓，且**已有 10 个已出货文件登记了秘技从句** ✗ 在等它 ✓。
> * **首个读者**：8001 的「使用秘技后立即为我方全体回复等同于**各自生命上限 15%」**（`HEAL` + `scale: target_max_hp` + `all_allies` ✓ ——"各自"正是 `target_max_hp` ✓）。
> * **测试**：**成对**（声明 / 不声明 ✓）且断言回复量 == **该队友自己生命上限的 15%** ✓（得先证明引擎**允许战前结算伤害** ✓ —— 它确实允许 ✓）；**变异**（实测）：取消加状态 ⇒ **2 红** ✓。
> ⚠ 开战时全员**满血** ✓ ⇒ 开场治疗在满血时治疗量为 0 ✓且引擎**不会发 `HEALED`** ✓（它只在真正恢复时发 ✓）⇒ 测试必须**先扣血再开战** ✓。

> ✅ **2026-09-29（第 181 轮）`target_hp_percent_before`（阈值跨越用的“损失前”分数）** ✓**：
> `HP_LOST` 以 `(attacker, target, hpLoss)` 发射 ✓ ⇒ `ctx.amount` = 损失量 ✓，故 **损失前** = `(当前 + 损失) / 上限` ✓（不需新数据 ✓）。「降到 50% 或以下」因此写成 `target_hp_percent <= 0.5` **且** `target_hp_percent_before > 0.5` ✓。
> ⚠ 变量名集是**闭集** ✓：新增一个名字会被 `UnitDisciplineTest` 的量纲守卫拦下 ✓，直到守卫的清单同步 ✓（这是设计意图 ✓）。

> ✅ **2026-09-29（第 186 轮）`DAMAGE` 的字面倍率形态** ✓**：
> * 旧词汇：**必须**指定技能槽位 + `damage_param` ✗ ⇒ 倍率只能取自**技能数据行** ✗，而「造成等同于 X% 攻击力的伤害」在天赋追加/星魂/秘技里到处都是 ✓（实测：天赋类 **53 份** ✓、星魂类 **15 份** ✓）。
> * 现在：可只写 `scale: self_attr:ATTACK` + `percent` + `element` ✓（因为**无技能可借元素**，校验器**强制要求** `element` ✓）；基数由**伤害专用**的 `literalBase` 计算 ✓。
> * ⚠ **关键区别（实测）**：字面路径**不能**用 `derivedMagnitude` ✗ —— 它解析的是**基础属性** ✓（对 `MODIFY_ATTR` 而言是对的 ✓：百分比加成叠在基础值上 ✓），而**伤害实例用的是结算后的值** ✓。实测：等级钉到 Lv10 后，按行实例 674.365 ✓ 而字面 0.8 只有 385.35 = 0.8 × **481.7**（基础值 ✗）。
> * **出货读者**：1206 素裳的秘技（80% 攻击力全体物理伤害 ✓，门槛用第 178 轮的状态 ✓）；**测试**：两条字面规则（0.8 / 0.4 ✓）必须差**恰好两倍** ✓，并有无门槛的对照 ✓；**变异**（实测）：让 `literalBase` 忽略 `percent` ⇒ **1 红** ✓。

> ℹ️ **2026-09-29（第 187 轮）技能数据的位置与普攻行的实测偏差** ✓**：
> * **技能行在 `data/skills.json`** ✓（结构 `cid → slot → Skill` ✓，`slot` 为字符串键 ✓）；`data/character_data.json` **只有属性** ✓（`attack`/`defence`/`health`/`speed`/`crit_*` 等 ✓），**没有技能表** ✗。
> * **实测（1206）**：`slot 2` Lv10 = `[2.1, 1, 0.33]` ✓ 与 `slot 3` Lv10 = `[3.2]` ✓ **逐项等于文档的 210%/100%/33% 与 320%** ✓；而 `slot 1`（普攻）Lv10 = `[1.4]` ✗ 对文档的 **100%** ✗ ⇒ **普攻行是差异点** ✓。
> ⇒ 写内容时：**文档给了倍率就用字面形态** ✓（`scale` + `percent` ✓），不要想当然地指向行 ✗。

> ✅ **2026-09-29（第 189 轮）字面倍率伤害新增“生命上限”刻度** ✓**：
> * 旧词汇：字面形态只认 `self_attr:<属性>` ✗ ⇒ 「造成等同于 X% **生命上限**的伤害」无法表达 ✗（实测：这类句子 **16 份** 文档 ✓）。
> * 现在：**`owner_max_hp`**（施放者自己的 ✓）与 **`target_max_hp`**（**受害者的** ✓，因此`literalBase` 现在会收到 victim ✓）。
> * ⚠ **校验器只在 `DAMAGE` 的字面分支放宽** ✓：共用的 `requireDerivedScale` **保持严格** ✓（`MODIFY_ATTR` 也用它 ✓，而**属性修饰不应能命名生命上限** ✓）。
> * **出货读者**：**1105 娜塔莎** 行迹「施放普攻时，额外造成等同于娜塔莎生命上限40%的物理属性伤害」✓（它是规则**自己**造的附加伤害 ✓，与她由数据表负责的治疗不同 ✓）；**测试**：内容 0.4 对手建 0.5（**同一管线、同一刻度** ✓）⇒ 断言比值 0.8 ✓；**变异**（实测）：倍率减半 ⇒ **1 红** ✓。

> ✅ **2026-09-29（第 195 轮）`MODIFY_ATTR` 支持**固定加值**（`amount` 单独使用）** ✓**：
> * 旧约定：**硬性要求** `percent` ✗，且有一条**有意的**拒绝：「`amount` 没有 `scale` ⇒ 它会被忽略」✗（原因写在注释里 ✓）。
> * 现在：无 `scale` 时 **`percent` / `amount` 恰有其一** ✓（`amount` = **绝对值** ✓，如「速度提高 50 **点**」✓）；写**两个**会被拒绝 ✗（消息里说明了“若也写 `scale`，两者就是 `percent × scale + amount`” ✓）。
> * ⚠ **实测陷阱** ✓：`amount: 50` 在 SPEED 上曾给出 **+5500** ✗ = 50 × 目标基础速度 110 ✓ —— 因为普通修饰会被当成 **`add_percent` 分数** ✓。「点」必须走**绝对值路径** ✓（与派生量同一道理 ✓）。
> * **读者：17 份** ✓（速度 7 ✓、生命上限 10 ✓）；首个出货读者 **1009 艾丝妲** ✓；**变异**（50 → 25）⇒ **1 红** ✓。

> ✅ **2026-09-29（第 208 轮）`MODIFY_DAMAGE_TAKEN` 的约定（**实测**）** ✓**：
> 「使敌方全体**受到的伤害提高 20%**」实测为 **伤害 × (1 + percent)** ✓：一次固定伤害在前后分别为 **571.43 / 685.71** ✓（比值 **1.2** ✓）。
> * ⚠ 我最初断言的 **1.5 ✗** 是错的 —— 那是**另一个伤害区**的拼写 ✗。测试现在同时断言：**测得的因子 1.2** ✓ 与 **线性份额** `(1.2-1)/(1.4-1) = 0.5` ✓（参照为同管线手建的 40% 规则 ✓）。
> * ⇒ 写测试时：**先把"增益区"的因子测出来** ✓，不要用从文档数字直接推出的倍数 ✗。

> ✅ **2026-09-29（第 215 轮，**撤回一个错误的缺口声明**）** ✓：
> 数值条件**已支持** `> / >= / < / <= / == / !=` ✓（`Numeric.test`），且 `hp_percent` / `target_hp_percent` 等变量**是分数** ✓（**0.5 = 50%** ✓）。**既有测试**：`TriggerEventWiringTest.hpPercentMatchesAtAndBelowTheThreshold` ✓。⚠ 一行变异（`<=` → `<`）会让 **3 个**测试变红 ✓。

> ✅ **2026-09-29（第 229 轮）`ADD_STACK` 的 `max_stacks` 缺省值（**实测**）** ✓**：
> **综略 `max_stacks` 不是"无上限"，而是"上限为 1"** ✗ —— 实测：不写该字段时，**十次施放仍读 1 层** ✓。
> ⇒ 引擎**没有**表达"无上限层数"的写法 ✗ ⇒ 凡文档**没给上限**的层数，**整条登记** ✓（不编数、也不写一个永远不长的层数 ✗）。
> 参照：1308 的【集真赤】✓（已撤回那条规则 ✓）。

> ✅ **2026-09-29（第 5/270 轮）`MODIFY_DAMAGE_TAKEN` 支持具名 `buff`** ✓：
> * **语义**：与 `MODIFY_ATTR` 自 2026-09-28 起就有的字段**对称** ✓ —— 写了 `buff: <名字>` 之后，`REMOVE_STATE <同名>` 能把它摘掉 ✓（`BuffManager.removeState` 的最后一圈按名遍历**任意类型**的 buff ✓）。**不写就不变** ✓（匿名修饰仍然摘不掉 ✓，既有内容一字未改 ✓）。
> * **读者**：1507 千冶•刃 行迹「千锻魂」（「**结界持续期间**…受到的伤害降低 50%」✓）—— 不具名就只能写成 `permanent` ⇒ **整场不摘** ✗，那是**没有症状的持续错误** ✗。
> * ⭐ **配套的"结界的钟"写法**（既有词汇，值得记一笔）✓：结界的寿命就是那条**倒计时**（文档：「行动序列上出现对应倒计时…**倒计时回合开始时结界解除**」✓）⇒ **创建**时 `START_COUNTDOWN {"buff": <名字>, "speed": <文档给的速度>}` ✓，效果**具名 + permanent** 挂上 ✓，在 **`COUNTDOWN_TURN`**（`actor == countdown` ✓）里 `REMOVE_STATE <同名>` ✓。⚠ 这比"用 `turns` 近似"**严格得多** ✓：倒计时是行动序列上真单位 ✓，会被行动提前/延后真实影响 ✓；⚠ 但**只适用于作用在结界主人自己身上**的效果 ✓ —— 我方全体的效果各自按**各人**的回合计时 ✗，与结界的钟不同 ✗。
> ✅ **2026-09-29（第 29/270 轮）结界时钟的选型（idiom，非新字段）** ✓：
> * 文档给"**自身每回合开始时减 1**"的结界 ⇒ **`ticks_on: "self"` + `turns: N`** ✓（1303 阮•梅 ✓）—— 这就是"每个状态的时长按**谁的回合**计"的既有语义 ✓，**不需要倒计时** ✗。
> * 文档给"**结界存在期间…**"且**到期时还有事要做**（摘除具名效果 ✓、结算伤害 ✓）⇒ **倒计时 idiom** ✓：`START_COUNTDOWN {"buff": …, "speed": N}` + `COUNTDOWN_TURN` + `REMOVE_STATE`（1507 ✓、1403 ✓ 已出货 ✓）。
> * ⚠ **判断标准只有一条**：**到期只是"效果消失"还是"还要执行动作"** ✓ ⇒ 前者用 `ticks_on` ✓、后者用倒计时 ✓。
> ✅ **2026-09-29（第 24/270 轮）"要读层数，计数器必须先有名字"** ✓（idiom，非新字段）：
> * `scale: self_stacks:<名字>` / `per_stack: <名字>` 都按**名字**读层数 ✓ ⇒ 若那条产生层数的规则用的是**无名修饰** ✗，就**没有可读的名字** ✓。**给修饰加一个 `buff` 名字** ✓（上限与数值不变 ✓）即可 ✓ —— 花火的天赋就是这么被"打开"的 ✓。
> * ⚠ 具名的副作用只有两条 ✓：① `REMOVE_STATE <同名字>` 会摘掉它 ✓；② **叠层分组**可能因此改变 ✓（`max_stacks` 的上限本身不变 ✓）。⇒ 改完要**跑用例**确认上限行为 ✓（本轮实测 3 层上限仍成立 ✓）。
> ✅ **2026-09-29（第 23/270 轮）`MODIFY_ATTR` 新增 `per_stack: <名字>`（份额 × 层数）** ✓：
> * **语义**：幅值 = **plain 幅值 × 目标身上的层数** ✓。plain 幅值在平值属性上就是"**基数的份额**" ✓（`statModifier` 的 `add_percent` 约定 ✓）⇒ 所以 `percent: 0.005` 配 3 层 = **基数的 1.5%** ✓。
> * ⚠ **为什么不能复用 `scale: *_stacks:`** ✗：派生算式产出的**单位是绝对值** ✓（`0.005 × 3 = 0.015 点` ✗）；而本条要的是**基数的 0.005 × 3** ✓ ⇒ 两种乘法的**参照物不同** ✓（一个是层数 × 常量，一个是层数 × **基数份额**）。⚠ 因此装载期**同时出现 `scale` 与 `per_stack` 会被拒绝** ✓（否则值会变成两者的乘积 ✓）。
> * ⚠ **按目标读取** ✓（在 `modifyAttr` 的目标循环内 ✓，不在循环外算一次 ✓）；⚠ **`EffectSpec.copy()` 必须带上新字段** ✓ —— 有反射守卫会失败并点名 ✓（`copy()` 的注释原文即"每个字段都必须列在这里" ✓）。
> * **读者**：1314 绝当品（已出货 ✓）、1306、1402（待收 ✓）✓。
> ✅ **2026-09-29（第 21/270 轮）`ADD_STACK` 按 `amount` 给层** ✓：
> * **改动**：`addStack` 由"每次调用加**一层**" ✗ 改为"加 `amount` 层（默认 1），到 `max_stacks` 为止" ✓ （上限检查仍在**每一层**之前 ✓，用 `break` 而不是 `continue` ✓）。
> * **为什么重要**：`amount` 此前**被完全忽略** ✗ ⇒ 「获得 **N** 层」这一族**写不出来** ✗，而它在语料里很多 ✓（1111 卢卡 `amount: 2` ✓、1314 翡翠 5/15/1/3 ✓ ……）。⚠ 该限制曾被**实测并登记** ✓（`LukaTest` 的类注释与他的文件 ✓）—— 也就是说：**登记是对的、能力是缺的** ✓。
> * ⚠ **不写 `amount` 时行为不变** ✓（默认 1 层 ✓ ⇒ 所有既有文件不受影响 ✓）；⚠ 受影响的只有**本来就写了 `amount` 的内容** ✓，而它们写的正是文档里的层数 ✓。
> ⚠ **2026-09-29（第 20/270 轮）`scale: *_stacks:` 的适用边界（实测）** ✓：
> * 派生算式是 `percent × 层数 + amount` ✓ ⇒ 结果的**单位是属性自身的单位** ✓：
>   * **比率型属性**（`CRIT_ATTACK`、`RESISTANCE_REDUCTION`、`MODIFY_DAMAGE_TAKEN` 一类 ✓）：`percent` 就是"每层几个百分点" ✓ ⇒ 直接用 ✓（1218 的两条按层从句 ✓）；
>   * **平值属性**（`DEFENCE`/`SPEED`/`ATTACK` 等 ✓）：`percent × 层数` 是**绝对值** ✓ ⇒ 只有当从句说的是「每层提高 **N 点**」时才对 ✓；若从句说的是「每层提高 **X%**」（= 基数的份额 ✓）⇒ **用同名可叠修饰** ✓（实测：用按层缩放写"每层 +10% 防御"只推动了 **0.1** ✗，而可叠修饰给出基数的 10% ✓）。
> * ⚠ **不要混用** ✗：按层缩放的修饰**自己也会叠**时，层数会被平方 ✓（两个乘数各含层数 ✓）。
> * **判据提示** ✓：量"每层 X%"这类从句时，用**绝对增量**（`share × baseValue` ✓）而不是比值 ✓ —— 单位身上常另有一份既有加成 ✓（实测比值读成 1.1252 而增量精确 ✓）。
> ✅ **2026-09-29（第 16/270 轮）`scale` 新增按层缩放：`self_stacks:<名字>` / `target_stacks:<名字>`** ✓：
> * **语义**：幅值 = **`percent × 该单位身上的层数`** ✓（再 `+ amount` ✓，与其它派生幅值同一算式 ✓）。`self_stacks:` 读**规则持有者**的计数器 ✓、`target_stacks:` 读**事件目标**的 ✓ —— 与条件 DSL 的两条**同拼写** ✓（`TriggerTable.SELF_STACKS_PREFIX` / `TARGET_STACKS_PREFIX` 已提为 public ✓，两处不会漂移 ✓）。
> * **接入点**：`derivedMagnitude`（`MODIFY_ATTR`/`ADD_DAMAGE` 一类**幅值** ✓）与 `resolveScale`（**封顶** ✓）**两处**都在最前面分流 ✓；装载期 `requireDerivedScale` 对这两个前缀**只校验形状与 percent** ✓（不再当作属性名 ✗）。
> * ⚠ **它是"发射时读一次、然后冻结"** ✓（与其它派生幅值一致 ✓）⇒ **要让值跟着层数走，就必须在层数变化的那个事件上重新施加** ✓（**同名修饰互相替换** ✓ ⇒ 最新一次生效 ✓）。⚠ **层数归零/被清除后不会自动撤下** ✗ ⇒ 目前依赖内容侧写对时长或清理规则 ✓。
> * **读者（实测 15–17 份）** ✓：1212（每层【月色】暴伤 ✓）、1218（每层【烬煨】抗性降低 ✓）、1302（每层【升格】暴伤 ✓）、1306（每层【幻相】易伤 ✓）、1307（每层【奥迹】伤害倍率 ✓）、1314（每层【当品】✓）、1315（每层【优势口袋】削韧 ✓）、1402（每层速度 ✓）、1408（每层【弑魂之炽】反击倍率 ✓）、8001/8002（每层防御 ✓）等 ✓。
> ✅ **2026-09-29（第 13/270 轮）`MODIFY_ATTR` 支持 `base_chance`（掷骰）** ✓：
> * **改动**：`modifyAttr` 的收尾由**直接 `addBuff`** ✗ 改为统一的 **`attachRolled(battle, target, buff, effect, ctx)`** ✓ —— 与 `APPLY_DOT`/`APPLY_BUFF`/`APPLY_STATE`/`MODIFY_DAMAGE_TAKEN` **同一处**管线 ✓（`Battle.tryApplyDebuff` ✓：`基础概率 × (1+效果命中) × (1−效果抵抗) × (1−特定抵抗)` ✓）。
> * **契约**：**不写 `base_chance` 就完全照旧** ✓（`attachRolled` 的注释原文："Unstated = applied directly, so no existing file changes" ✓）；⚠ 实测：此前**没有任何已出货文件**在 `MODIFY_ATTR` 上写过 `base_chance` ✓ ⇒ 全量随之仍绿 ✓。
> * **读者（实测 17 份）** ✓：如 1006（全属性抗性降低 10% ✓）、1106（防御力降低 / 冰属性抗性降低 ✓）、1217（攻击力降低 ✓）、1002/1004（速度降低 ✓）、1111（受到的伤害提高 ✓）等 ✓。
> * ⚠ **验证的写法（重要）**：`base_chance: 1.0` **证不了**掷骰是否发生 ✗ —— 要**钉住随机源**：`nextDouble()=0.0` ⇒ 落地 ✓、`=1.0` ⇒ **绝不落地** ✓（见 `RolledAttributeModifierTest` ✓）。
> ✅ **2026-09-29（第 10/270 轮）新增属性 `RESISTANCE_REDUCTION`（全属性抗性降低）** ✓：
> * **语义**：**受击方**的属性 ✓，在 `Battle.assemble` 的抗性区里**从 `rawResist` 里减去** ✓，然后才做 `damage.resist(rawResist, 攻击方的 DAMAGE_PENETRATION)` ✓。⚠ **为什么不与 `DAMAGE_PENETRATION` 合并** ✗：穿透属于**攻击方**、不能无视本来没有的抗性 ✓，而抗性降低属于**受击方**、可以把抗性压成**负数** ✓ —— 而负数本来就被引擎视为"完全生效" ✓（`ResistArea.rate()` = `1 − clamp(RES − 穿透, RESIST_MIN, RESIST_MAX)` ✓，区间 **[0.1, 2.0]** ✓）。
> * **读者（实测 19 份）**：1004、1006、1203、1218、1304、1308、1321、1405、1407、1410、1504、1507（+1222/1505 无文件 + 光锥/词条 ✓）。⚠ 此前**没有任何已出货文件**用过任何"抗性降低"拼写 ✓。
> * **实测**：对物理抗性 20% 的目标降低 20% ⇒ 同一发攻击 **×1.25** ✓（= 1/0.8 ✓）⇒ 这也是"抗性区把伤害乘 `(1 − 抗性)`"的直接证据 ✓。
> * **已出货的读者** ✓：**1321**（在场光环 20% ✓）、**1203**（终结技 20%、2 回合、全体 ✓）、**1304**（普攻 12%、3 回合、单体 ✓）、**1507**（星魂 1，20%，**跟着结界的倒计时** ✓ —— 同一条倒计时收尾规则里两条 `REMOVE_STATE` 各管一侧 ✓，**第一次两条从句共用同一个结界的钟** ✓）⇒ 语料 19 份里已收 3；仍待回收：1004、1006、1218、1308、1405、1407、1410、1504、1507 ✓。⚠ **写法提醒**：「**固定概率**」是**掷骰类型**（不受命中/抵抗影响 ✓），100% 时**不写 `chance`** ✓；小于 100% 时用**规则级 `chance`** ✓，不要与按目标掷、走命中管线的 `base_chance` 混用 ✗。
> * ⚠ **一处脆 fixture 被它照出来** ✓（`SuperBreakTest` 的敌人会被自己的击破伤害打死 ✓，20% 的降低就把那一击推过线 ✓）⇒ fixture 已改为取"血量最大的元素弱候选" ✓ —— **这不是引擎问题** ✓，但值得记一笔：**任何会抬高伤害的能力，都可能把"勉强活着"的 fixture 推死** ✓。
> ✅ **2026-09-29（第 232/233 轮之间）新增属性 `AGGRO_ADDED_RATIO`（软仇恨权重）** ✓：
> * **语义**：`Battle.aggroOf` = **原权重 × (1 + 该属性值)** ✓（`aggroOf` 现在是"`baseAggroOf` + 比率"两层）✓。比率属性按**分数**书写 ⇒ **+500% 写作 `5`** ✓（与 `CRIT_CHANCE 0.6` 同一约定 ✓）。
> * **来源（不是编的）**：1001 战技第 3 句正文不给数值，但该技能 `param_list` 每级 **5** 个槽位、正文只用 4 个 ⇒ 第 5 个（**5**）是软仇恨比例 ✓；上游能力配置把护盾 modifier 挂给**护盾持有者**并写 `AggroAddedRatio` ✓，且按 `ByCompareHPRatio`（参数 #3）分档：≥30% 带动态值、<30% **固定 0** ✓。
> * **与嘲讽的区别**：`TauntBuff` 是硬约束（只能被选中）✓；本属性是**软权重**（同一套加权抽签）✓。「受到攻击的概率大幅提高」属于后者 ✓。
> * ⚠ **护栏**：`1 + ratio ≤ 0` 时**保留原权重** ✓（拒绝把活着的单位从仇恨表里抹掉）✓，测试 `SoftAggroWeightTest` 钉住 ✓。
> * **读者（2026-09-29 二次更正）**：**11 份语料文档**有此句 —— 1001、1002、1102、1104、1107、1110、1205、1206、1209、1413、1507，外加光锥文件 WEAPONS.md ✓。⚠ 两次更正的由来：先写"20 条"（那是 **TextMap 条目数** ✗），改成"7 份"（只搜了三种措辞 ✗），放宽措辞后找到 **1002 丹恒、1102 希儿、1206 素裳、1107 克拉拉**，而他们**各自都有上游 writer**（`Avatar_DanHeng_SkillTree01`、`Avatar_Seele_SkillTree01`、`MAvatar_Sushang_00_SkillTree_AggroDown`、`Avatar_Klara_00_*`）✓ —— 语料与配置互相印证 ✓。⚠ **`仇恨` 这个词不能用来搜从句** ✗：97 份文档命中，因为每个角色表都有一行 `仇恨: 75` 的面板数值 ✗。
> * ⚠ **1205 刃 已划出本工作流** ✓：他那句「使用战技后提升自身的受击概率」在文档里属于 **「## 角色加强」**（来源 `AvatarEnhancedHintConfig.json` ✓），是**强化后**的效果 ⇒ 按纪律写强化前、强化后逐条登记 ✓，不出货 ✓。
> * ✅ **引擎用法（本轮新增的写法，不需要新 op）**：「**若…则…**」这类**持续成立**的条件不是一个事件 ✗，引擎也没有"条件寿命"的修饰 ✗；但游戏自己的实现就是**在生命值变化时重判** ✓ —— 丹恒的 `MAvatar_DanHeng_00_LowHP_AggroDown` 在 **`OnStack` 与 `OnHPChange`** 上挂了同一个 `ByCompareHPRatio` **LessEqual** 谓词 ✓。引擎里与它**等价**的一对是：
>   * **挂**：`HP_LOST` + `hp_percent <= 0.5`（生命值只可能因这两件事改变 ✓）⇒ `MODIFY_ATTR` + 具名 `buff` + `permanent` ✓；
>   * **摘**：`HEALED` + `hp_percent > 0.5` ⇒ `REMOVE_STATE` + 同名 `buff` ✓。
>   ⚠ **具名是必须的** ✓（`REMOVE_STATE` 按名字摘 ✗ 无名摘不掉）；⚠ 同名 buff 上限 1 ⇒ 重复挂**不叠加** ✓（测试里用"再掉一次血"钉住 ✓）；⚠ 登记两处差异：生命值**不经治疗**回到门槛之上（如生命上限被提高）不会触发摘除 ✗、战斗开始时已在门槛之下不会挂上 ✗。
>   * **遗器**：`relic_sets/*.json` 41 份内容文件里 **0 份**有这句 ✓，生成表 `data/relic_sets.json` 也没有 ✓ ⇒ 遗器暂时没有这个读者 ✓。
>   * **属性词典**：`AggroAddedRatio` **不出现在任何遗器/光锥表里**（含首次探针跳过的 53MB `SpecialAvatarRelicMainValue.json` ✓）⇒ `AttributeType.BY_GAME_PROPERTY` **不加条目** ✓ —— 那张表每个条目都必须是生成器词典的原样条目 ✓，没有证据就不加 ✓。
>   * **光锥**：上游**有**数值（`ExcelOutput/EquipmentSkillConfig.json` 845 行，每行带 `ParamList` 与 `AbilityName` ✓），但引擎**没有光锥效果通路**（遗器有 `relic_sets/` 内容目录，光锥目前只是数值条 ✗）⇒ 这是**内容类**缺口（缺一条内容管线），不是能力缺口 ⇒ 登记 ✓。
> * ✅ **求值方法（可复用）**：要给一句"概率提高"定案，就在 `Config/ConfigAbility/**` 里找写 `AggroAddedRatio` 的 `StackProperty`，再找**谁挂**了带它的 modifier，然后读**挂载者**的参数表 ✓。两个**全局** modifier 承担了行迹那一类：`M_SkillTree_AggroUp`（操作码 `AQAR`、无 `FixedValues` ⇒ **+参数** ✓）与 `M_SkillTree_AggroDown`（`AAABAAMR` + `FixedValues [0]` ⇒ **0 − 参数** ✓）。
>   * ✅ **"未被正文引用的参数"已经四次命中** ✓：1001 战技（5 个参数、正文用 4 个 ⇒ 第 5 个 = **5** ✓）、1107 秘技（`[2, 5]`、正文只点名 2 回合 ⇒ **5** ✓）、1110 战技（`[0.075, 200, 2, 0.12, 320, 5]`、占位符只用 #1–#5 ⇒ **5** ✓）、**1413 忆灵技能2**（`[0.25…0.7, 3]`、正文只点名 50% ⇒ **3** ✓）。⚠ **通用的是方法，不是数值** ✓：正文不引用的槽位就是机制 ✓，而值可以是 5、也可以是 3 ✓（上一版这里写"两者的值都是 5，是设计值而不是巧合"，第四次命中把它**证伪**了，故更正 ✓）。⚠ 反向也要查 ✓：1107 的**终结技** modifier 写 `AggroAddedRatio` 而正文**不提** ✗ ⇒ 只登记、不写进内容 ✓。
> ⭐ **方法自检（杰帕德）**：行迹「刚正」的 `AvatarSkillTreeConfig` 行 `ParamList = [3]`，其 sibling「战意」是 `[0.35]`，而文档把「战意」渲染成 **35%** ✓ —— 参数与正文对得上，说明这条路是对的 ✓。⚠ **符号约定由此坐实**：「降低」= **负比率** ✓，所以 `Battle.aggroOf` 的 `1 + ratio <= 0` 护栏是必要的 ✓。

| `ADD_STACK` | `buff`（计数器名字）+ `max_stacks`（可选，默认 1）+ `turns` / `permanent` + `target` | ✅（2026-09-28）「每当我方目标…施放 **2** 次…后，立即为我方恢复 1 个战技点」（1215 寒鸦）——「累计几次」此前**完全无法表达**（引擎能堆叠、能 `REMOVE_STACK`，但**没有东西能读计数**）。载体是 `StackBuff`：有名字、有寿命、**不挂任何属性**（计数器不该改面板），`isStackable()` 恒真且按名字分组 —— ⚠ 第一版忘了报 `maxStacks()`，于是第二次标记被 `addStackable` 按默认上限 1 丢掉，**计数永远是 1**。**读取侧**：条件 `self_stacks:<名字>` / `target_stacks:<名字>`（两个拼写，主体在名字里，与 `hp_percent`/`target_hp_percent` 同族）；**装载期**要求该名字在本文件里真被某个造名字的 op 创建过（`ADD_STACK`/`APPLY_BUFF`/`MODIFY_ATTR`/`RESIST_DEBUFF`/`SHIELD`），否则是永不成立的条件。⚠ `REMOVE_STATE <名字>` 是**清空**整个计数器（不是减一层），这正是「触发 2 次后自动解除」要的。**测试** `StackCounterTest` 3 条。**变异**（实测）：标记不落地 **2 红**；条件改回一次性求值 **1 红** |
| `ADD_DAMAGE` | `scale`（`self_attr:<属性>` 或 `self_max_energy`）+ `percent`（可选 `amount` 作常数项）；**只能挂在 `DEALING_DAMAGE` 上** | ✅（2026-09-28）「伤害值提高，提高数值等同于<某属性>的 Y%」。⚠ 它是 `BOOST_DAMAGE` 的**绝对值**兄弟：`BOOST_DAMAGE` 往**增伤区**加百分比（`1 + Σ`，是**乘法**），这个把 `percent × 规则主人的属性` 加进实例的**基数层**（`Damage.addFlat` / `toValue`）—— 于是它和技能倍率一样吃增伤/暴击/防御/抗性。⚠ 把这类句子写成 `BOOST_DAMAGE` 只有当"这段伤害的基数恰好等于那条属性"时才相等，是"看起来像做完了"的错数字（`AddDamageOpTest` 用**基数 400** 钉住这一对不相等；基数取 100 时两者数学上相同，测不出来 —— 变异 m2 第一轮 0 红就是这么来的）。⚠ 算术与 `MODIFY_ATTR` 的派生值**共用一处**（`derivedMagnitude` = `percent × 主人的属性 + amount`），校验也共用（`scaleAttribute`：只认 `self_attr:`，写错属性名装载期报错）。首个用户 **1001 星魂 4 第二句**（`actor == self` + `from_skill TALENT` 只圈住她的反击）。⚠ **`HEAL` 侧的对称件仍缺**（读者 1222/1301 的角色文件都还没写）→ 登记，不造没有读者的能力 |
| `REMOVE_STACK` | `attribute` / `amount`，可选 `target` | ✅ 按属性取回最多 `amount` 层叠层（「每回合移除 1 层」；`amount` 必须为正，取不到不算错） |
| `DISPEL` | `amount`，可选 `target` | ✅ 移除最多 `amount` 个**负面效果**（「解除 N 个负面效果」），**最新的先走**；"什么算负面"由每个 buff 类自己回答（`AbstractBuff.isDebuff()`，见 §10.4） |
| `APPLY_CONTROL` | `control`（**封闭集合**：冻结/纠缠/禁锢）+ `turns`（必填、正数）+ 可选 `base_chance`（基础概率，**按目标**掷、走效果命中/抵抗），可选 `element` + 幅值（`amount` 或 `scale`+`percent`）= **状态自带的每回合伤害**，可选 `target` | ✅（2026-09-27）「有 50% 基础概率使敌方目标陷入冻结状态，持续 1 回合。冻结状态下，敌方目标不能行动同时每回合开始时受到等同于三月七60%攻击力的冰属性附加伤害」。⚠ 状态**做什么**是引擎的表（`Constant.CONTROL_STATES`），规则只说哪个/多久/多可能；⚠ 写错名字装载期拒绝并列出已知三种；⚠ 有幅值却没 `element`（或反过来）都**拒绝**；⚠ `attribute`/`buff` 一律拒绝。见 §8.6 |
| `APPLY_DOT` | `element`（`DamageElement` 拼写，必填）+ 幅值（`amount`，或 `scale`+`percent`，两者都从**规则主人**的属性派生）+ `turns`（必填、正数），可选 `base_chance`、`target` | ✅（2026-09-27）「使目标陷入灼烧状态，每回合造成等同于…#1[i]%攻击力的火属性伤害」。⚠ 幅值在**落地时算一次并冻结进 DOT**（「等同于三月七60%攻击力」说的是那一刻她的攻击力）；⚠ 元素同时也是**状态身份**（Fire ↔ 灼烧，翻译只有 `BuffManager.DOT_STATES` 一处）；⚠ 写错元素装载期拒绝（`fromString` 对"Unknown"与拼错都返回 null，不查就会挂上一个什么都不做的 DOT） |
| `EXTEND_BUFF` | **二选一**：`buff`（状态名：灼烧/触电/裂伤/风化、冻结/纠缠/禁锢、护盾，或任意具名状态）**或** `attribute`（`AttributeType` 名，给「伤害提高效果」那种只以属性为handle的 buff）；外加 `turns`（必填、正数），可选 `target` | ✅（2026-09-27）「…的持续时间**增加 N 回合**」。⚠ 它只延长**规则主人自己施加的**、且**名字对得上**的那一个（`AbstractBuff.source` + 名字/属性两个过滤器）；⚠ **两个过滤器都必填**——"我在这人身上的一切"是**故意不提供的写法**（布洛妮娅的 `BATTLE_START` 防御行迹可能还在跳，星魂 6 会顺手把它也加一回合）；⚠ `permanent`/`until` 的 buff **没有倒计时可延长**，跳过；找不到任何可延长的**不算错**。见下面的「时长延长」一段 |
| `RESIST_DEBUFF` | `kind`（**封闭集合**：`control` / `dot`）+ `percent`（(0,1]，`1.0` = 免疫）+ **`turns` 或 `permanent` 二选一**，可选 `target`（**可以是召唤物**：`summon`） | ✅（2026-09-28）「抵抗**控制类**负面状态的概率提高35%」（克拉拉 1107 行迹「守护」）/「**免疫控制类**负面状态」（长夜月 1413 忆灵「长夜」）。⚠ 说的是**一整个族**而不是一串键：状态属于哪一族由**状态自己**回答（`AbstractBuff.debuffClass()`），所以**明天新写的控制**自动被今天的抵抗覆盖 —— 这也正是文档自己给出的理由（1107/1413 都把「控制类负面状态」展开成同样 12 个名字）。⚠ 它**只安装数字**：掷骰在 `Battle.tryApplyDebuff`（连**没写概率**的控制也掷，见 §8.6）。⚠ 身份是 **(族, 施加者)**：两个施加者**相加**（35% + 100% 是两笔贡献，钳在 1），同一个施加者再施加只是**刷新自己那一笔** —— 否则一次性的 100% 会把克拉拉**永久的** 35% 顶掉、过期后连行迹一起消失。⚠ `1.0` 就是免疫：同一个机制取到极限，**不留第二套"免疫"词汇**。见下面的「负面效果抗性」一段 |
| `MODIFY_RULE` | `rule`（**另一条规则的 `id`**）+ **二选一**：`amount`（每回合多触发几次，正整数）或 `percent`（基础概率提高多少，`(0,1]`）；**只能挂在 `BATTLE_START`** | ✅（2026-09-28）「天赋的反击效果每回合可触发的次数**增加1次**」/「冻结敌方目标的**基础概率**提高15%」。⚠ 它**不是**"再写一条规则"：再写一条 `per_turn: 3` 是**多触发**（2+3=5 次/回合），再写一条 `base_chance: 0.65` 是**掷两次**（1−0.5×0.35=82.5%）。⚠ 目标在**同一文件内**解析（规则表按 cid 编译一次并缓存、遗器规则还被每个穿戴者共用），抬高量存在**战斗单位**上（`CanHit`），永远不写回规则。见下面的「抬高另一条规则」一段 |
| `REMOVE_STATE` | `buff`（**状态名**），可选 `target` | ✅ **把具名状态整个拿掉**（2026-09-27，M-42 ②）：与 `APPLY_BUFF` / `has_state` **同一套名字**，「触电」这类 DoT 拼写也认（`BuffManager.removeState` 与 `hasState` 走同一张表，一侧改名两侧一起改）。⚠ 不在场**不是错误**（「只对最新目标生效」这类规则每次施放都跑，多数时候没什么可拿的）；⚠ **不收 `amount`** —— 「解除…状态」是拿掉整个状态，不是拿掉 N 个，写了会装载期报错。首个读者是**星期日终结技的【蒙福者】**「仅对…**最新的**施放目标生效」，但那条内容还差"按谁的回合计时"（M-42 ④），所以这个 op 目前是**已验证的能力、暂无出货规则** |
| `TAUNT` | `turns`（必填、正数），可选 `target` | ✅ **使目标陷入嘲讽状态**（2026-09-27）：挂的是引擎早就有的 `TauntBuff`（**纯标记**，硬约束在**选目标阶段**：只要它还挂在活着的单位身上，单体攻击与扩散中心**只能选它**）。⚠ 与「被敌方攻击的概率提高」（**软**权重，且**文档里没有任何数字**）**不是一回事**，后者登记为数据缺口。首个用户 **1507 千冶·刃战技**「使目标陷入嘲讽状态，持续1回合」；⚠ 云璃终结技的「**敌方全体**陷入嘲讽」还缺一个**敌方群体选择器**（现有群体选择器都读我方阵营） |
| `APPLY_BUFF` | `buff` / **`turns` 与 `permanent` 与 `until` 三选一**，可选 `target`、**`ticks_on`**（见下） | ✅ 具名状态（见下） |
| `SUMMON` | **无参数**（连 `target` 都不收） | ✅ 把**规则主人自己的忆灵**带上场（§24.5/§24.6）。没有 `target` 是因为忆灵属于召唤者、替它那一方打，没有可指的对象 —— 而多写一个 `target` 会被**拒绝**而不是被忽略。**按召唤者幂等**：再触发一次保留已在场的那一个（「若已在场，则使其生命值回复至上限」的刷新**没有**建模，无操作是诚实替身）。⚠ 用了这个 op 却没有忆灵文件的角色在**装配时**就被拒（`CharacterFactory`），不是战斗中途 |
| `COMMAND_SUMMON` | `skill` / `damage_param` / `attribute`，可选 `damage_level`（**不收** `target`） | ✅ **指令忆灵立刻打一次**（§24.10，2026-09-28）：倍率 / 元素 / 形状 / **削韧**全部来自规则**指名的那条技能**（主人自己的），基数属性来自 `attribute`（读的是**召唤物**的），攻击者也是召唤物。首个用户 **1413 长夜月终结技**「随后使忆灵「长夜」对敌方全体造成等同于「长夜」#1[i]%生命上限的冰属性伤害」 |
| `DELEGATE_DAMAGE` | `skill`（只能挂在 `CAST_SETUP` 上） | ✅ **这次施放的伤害不由我来打**（§24.10，M-40，2026-09-27）：`skill` 指名**正在施放的那个槽位**（条件 DSL 没有"哪个槽位"这个变量，所以这个比对**就是**那道闸），命中则这次施放不展开伤害、也不削韧 —— 两件事一起移交给真正交付它的规则（长夜月那条在 `ULT_CAST` 上用 `COMMAND_SUMMON` 交付）。⚠ op 同时核对"这次施放确实是规则主人的"（`CAST_SETUP` 投给每张表），报错直接告诉作者加 `actor == self` |
| `REDUCE_TOUGHNESS` | `amount` | ☐ 要定元素与敌方目标 |

> ⚠ **未接线的 op 是在加载时"响亮地"拒绝的**，报错里点名它归哪个阶段。
> 否则内容作者写了规则、看不到任何反应，却分不清"我的条件写错了"和"引擎压根不发这个事件"。

> ⚠ **`damage_param` 是 0 基下标，文档的 `#N[i]` 是 1 基**（2026-09-28 踩到）。141303 的
> `param = [2, 2, 0.6, 0.3]`、行文说「`#1[i]`% 生命上限」→ 列号是 **0**；写成 `1` 会读到 `#2[i]`（那个
> **充能数，每一级都是 2**），而在 Lv10 它恰好等于 2.0 —— **看起来完全正确，直到等级一变**。
> 抓到它的是"同一技能三个等级的行号比值"这类用例（`SummonCommandTest.theMultiplierComesFromTheStatedRow`）。

> ⚠ **`damage_level`（可选）说的是"读哪一行"**：`damage_param` 管**列**，它管**行**，不写就用技能自身的等级。
> 为什么需要它：**本引擎里角色的技能等级恒为 1**（`Character.Builder` 每个槽位初始化为 1，没有人改过），
> 而行文引用的是**各自技能写作时的那个等级** —— 长夜月终结技引 Lv10（2.0）、她的忆灵技1 引 Lv6（0.50）。
> 所以"读技能自身等级"与"读文里那个数"是两件事。克拉拉 1107 的规则选了前者并**写明了**（Lv1 天赋 ⇒ 80%），
> 长夜月终结技选了后者（`damage_level: 10` ⇒ 200%）；两者都能自洽，而真正的修法是**把技能等级建模成角色数据**
> （星魂/行迹的「战技等级+2」），那时 `damage_level` 就该退休 —— 登记为 `M-32`。

#### 三种时长：`turns` / `permanent` / `until`（三选一）

| 写法 | 含义 | 谁在用 |
|---|---|---|
| `turns: N` | N 个**自己的回合**（早/晚 tick 由 buff 自己决定） | 绝大多数 buff |
| `permanent: true` | 到战斗结束（**从不 tick**） | 风雪交加 4 件套、命运 109… |
| `until: "next_attack"` / `"next_skill"` / `"next_ultimate"` | 到**主人做出那件事**（自己攻击 / 施放战技 / 施放终结技） | **星体差分机 305**「持续到施放首次攻击后结束」 |
| `until: ["next_attack", "next_skill"]` | 同上，但**多个事件任一到就结束** | **再创天地的救世主 127**「持续至装备者下次施放普攻**或**战技后」（M-38） |

- **为什么必须有第三种**：「持续到施放首次攻击后结束」不是回合数。写成 `turns: 1` 会在**错的回合边界**到期
  （而且"这一回合没打人"也照样掉），写成 `permanent: true` 则**整场都在** —— 两个都是"没人报错的错误数字"。
- **`until` 修饰的 buff 同样"从不 tick"**：`permanent` 这个标志**机制上就是"不 tick"**
  （`AbstractBuff.isPermanent()`），事件绑定的 buff 正需要它活下去 —— 否则它会在第一次 `afterMove` 掉掉。
  `TriggerInterpreter.unticked()` 就是这件事的唯一判据，顺带挡住了另一个坑：`until` 会让 `turns` 为 null，
  直接拆箱会在**战斗中途**抛 NPE。
- **判定的是"主人"**：引擎把"发生了一次攻击"广播给我方**全员**（知更鸟/缇宝那类第三方机制就靠它），
  所以没有主人判定的话，**谁先出手就会吃掉别人的**"下次攻击"buff。
- **召唤物的攻击算它自己的一次攻击**（§24.8）：挂在**忆灵**身上的 `until: next_attack` 会被它自己的攻击吃掉，
  而**召唤者**身上的不会被吃 —— 同一个 `attacker == owner` 判定，两边都成立。
- **一发未命中不算攻击**：`Battle.fireAfterAttack` 在"一个目标都没打到"时提前返回。
- **可以一次写多个事件（M-38）**：「持续至装备者下次施放普攻**或**战技后」（127）是**一个时长、两个结束条件**，
  所以 `until` 收**列表**，**任一到就结束**。只写一个事件是"没报错的错误时长"（辅助战技不会结束它），
  而写成两条 buff 更糟：同 kind 同 target 会**互相替换**，最后只有后一条活着。⚠ 列表里**每一个名字都会被校验**，
  第二个名字写错不会因为"控制流没走到"而混过去。
- ⚠ **创造它的那次事件不消耗它**：`SkillExecutor.execute` 先结算落地攻击（`resolveHits` → `Battle.fireAfterAttack`）与
  buff 级施放通知（`broadcastSkillCast`），**之后**才发施放触发事件（`BASIC_ATTACK` / `SKILL_CAST` / `ULT_CAST`），
  所以「施放普攻后…持续至下次施放普攻后」是**整整一个间隔**，而不是刚给上就被这次施放收走。
- ⚠ **追击攻击不消耗它**：`AttackEvent` 的契约是"只宣布引擎从头到尾驱动的一次攻击"，派生伤害（附加/真伤/DOT/击破）
  故意排除在外 —— 这条边界同时是**递归安全**的保证（听众允许用伤害回应攻击）。代价就是这处**已知的少消耗**
  （buff 会多留一会儿），登记为 ROADMAP M-27，而不是把事件接到派生伤害上去。

#### 谁的回合扣这个时长：`ticks_on: "self"`（M-42 ④）

`turns` 说的是"几个回合"，但**谁的**回合？默认是**携带它的那个单位**（引擎一贯的口径），而
星期日终结技的【蒙福者】偏偏相反：「使**目标及其召唤物**成为【蒙福者】…**星期日自身**每回合开始时【蒙福者】
状态持续回合减1，共持续#3[i]回合」。状态挂在队友身上，时钟却是**他的** —— 按默认口径会变成"队友的三个回合"，
在每一场战斗里都是不同的回合数，而且没有任何迹象。

- 写法：在创建 buff 的那条效果上写 `"ticks_on": "self"`（`self` = **规则主人**）；**不写就是默认**（携带者自己的回合）。
- 实现：`AbstractBuff.ticksOn(unit)` 回答"这条 buff 的时长是不是被这个单位的回合扣"，`Battle` 在**每个单位的回合边界**
  顺带扫一遍场上**其他**单位的 buff 管理器（`BuffManager.tickForeign`）—— 时钟是"战斗中谁在行动"，不是"buff 挂在谁身上"。
- ⚠ **锚点死亡 ⇒ 把 buff 拿掉**（M-42 ③）：时钟再也不会来了，留着它就不是"时长长"而是**泄漏**（星期日的原文也这么说：
  「当星期日陷入无法战斗状态时，【蒙福者】效果也会被解除」）。⚠ 这件事**不能**写成他表上的一条规则：`fireTriggers`
  **会跳过已阵亡单位**，需要反应的那个单位在轮到它之前就已经没了 —— 所以它是引擎侧的清理
  （`Battle.releaseBuffsAnchoredToTheDead`）。
- ⚠ `permanent: true` + `ticks_on` 是**装载期拒绝**：从不被扣的 buff 声明时钟是自相矛盾。

#### `target` 选择器（同样是封闭集合）

```
不写              默认 = 触发器的主人自己（最常见，所以允许省略）
self             同上，写出来更明确
target           这件事的承受者（掉血的/被治疗的那个人）  ← 给队友上 buff
attacker         这件事的起因（打我的人）               ← 反击
all_allies       我方全体（别名 party）                ← 「我方全体攻击力 +X%」
other_allies     **除规则主人以外**的我方              ← 知更鸟「使**除自身以外的队友**立即行动」
summon           主人自己的召唤物（忆灵）               ← 「装备者**及其忆灵**」的后半，§24.6
target_and_summon **这次施放瞄准的那个单位**及其召唤物   ← 星期日战技「指定我方单体**及其召唤物**立即行动」
all_enemies      **对面全体**（读的是**对方阵营**）      ← 姬子天赋「对**敌方全体**目标造成等同于姬子140%攻击力的火属性伤害」
lowest_hp_ally   **生命值百分比最低的我方目标**（一个单位，但走**列表**解析） ← 三月七星魂 2「为当前**生命值百分比最低**的我方目标提供…护盾」、藿藿【禳命】/灵砂【浮元】的治疗
```

> 🩸 **`lowest_hp_ally` 是"最惨的那个"**（2026-09-27）：它要的东西**条件写不出来** —— 条件筛的是**规则**（这件事是不是我的），
> 不是"一个效果该落到哪个单位"。⚠ 三条口径写死：① **百分比**，不是**点数** —— 我方 500/1000（50%，500 点）与
> 900/10000（9%，900 点）时，答案是**后者**，写成"点数最低"会在每两个角色的测试里都对、在真打里错；
> ② **平手取队伍里靠前的那个** —— `BATTLE_START` 时所有人都是 100%，所以每一条星魂 2 式的规则**必然**撞上平手，
> 这个选择必须是**说定的**而不是"map 恰好怎么迭代"；③ **阵亡的不算**（0% 的"最惨"没有意义，治疗也落不到它身上），
> 而**一个都不剩时是"没有目标"**（和其它群体选择器一样是空操作，不是错误）。
> ⚠ 只做了**百分比**这一种拼写（四份读者里三份这么写）；灵砂 1222 的「**生命值最低**」（绝对值）是**另一个问题**，
> 登记在 ROADMAP §13.2 里等它自己的角色，而不是现在猜一个名字。
> 契约：`LowestHpAllyTest` **6 条**（百分比 vs 点数 / 平手取靠前 / 跳过阵亡 / 无人存活是空操作 /
> 没有战场时响亮报错 / 单体 op 拒绝它）。**变异**：按点数比较 **1 红**、平手取靠后 **1 红**、
> 不跳过阵亡 **1 红**、内容里换成 `all_allies` **1 红**。

> ⚔️ **`all_enemies` 是第一个"落到对面群体"的选择器**（2026-09-27）：此前**所有**群体选择器读的都是
> `battle.allies`（`all_allies` / `party` / `other_allies`），于是「敌方全体」这类句子**没有写法** ——
> 只能一个一个打，而那是另一种机制。首批读者：姬子天赋的追加攻击，以及云璃终结技的「使**敌方全体**陷入嘲讽状态」。
> 它经 `Battle.getOpponents` 求值，所以「敌方」= **与规则主人对立的那个阵营**（对我方角色就是 `enemies`，
> 对敌方单位就是 `allies` —— 同一条规则两种视角都读得对）。⚠ 它**不**过滤阵亡（与 `all_allies` 同一口径：
> 选择器回答"那个阵营里有谁"，能不能作用由 op 决定，`DAMAGE` 自己会跳过尸体）。⚠ 它是**列表**：
> 写在只解析一个目标的 op 上（`EXTRA_TURN` / `GAIN_ENERGY`…）会在触发时**响亮拒绝**。
> 契约：`AllEnemiesTargetTest` **4 条**（每个敌人都吃到同一份实例 / 我方一点没碰 / 没有战场时响亮报错 /
> 单体 op 拒绝群体选择器）+ `HimekoChargeTest` 里那条真实的追加攻击。**变异**：改读 `battle.allies` →
> **3 条红、跨 2 个 suite**。

> ⚠ **`other_allies` 为什么不能省**（2026-09-28 补）：`all_allies` 是"我方**全体**"，**包含规则主人自己** ——
> 302 不老者的仙舟的「我方全体攻击力提高」必须给自己也加上，那是有用例钉住的；而「除自身以外」既不是它，
> 也**不能靠条件写**：条件筛的是**规则**（这件事是不是我的），不是**一个效果能落到哪些单位**（除了某一个之外的全体）。
> 两个不同的问题，第二个是效果的目标选择器。⚠ `all_allies` / `other_allies` **读的是阵营 `battle.allies`**，
> 所以**我方召唤物也算"我方"**（与"我方全体 buff"给忆灵加上是同一条口径）。

> ⚠ **`summon` 找不到召唤物时是响亮报错**，而不是回退到主人：写「装备者及其忆灵」却忘了
> `self_summon_count >= 1` 的作者会得到一条**指出该加哪个条件**的报错；
> 静默回退则会给**错的单位**上 buff。
>
> ⚠ **`target_and_summon` 为什么不是 `target` + `summon` 两条效果**（2026-09-27 补，M-37）：它要的是
> **被指定那个队友的**召唤物，而 `summon` 是**规则主人自己**的 —— 星期日战技推的是队友的忆灵，不是他的。
> 第二条效果也没有办法引用"第一条效果解析出来的那个单位"，所以这只能是**一个**选择器。
> 三处口径：被指定的单位**没有召唤物时就是它自己**（不是错误，这类技能大多数施放都是这种）；
> **只算场上的那个**（`Battle.summonsOf` 已滤掉阵亡）；因此它需要 `battle`，与 `all_allies` / `other_allies` 同类。
> ⚠ 它是**列表**：把它写在只解析一个目标的 op 上（`HEAL` / `EXTRA_TURN` / `GAIN_ENERGY`…）会在触发时
> **响亮拒绝**（报错说"这个选择器能落到多个单位"），而不是悄悄只作用于本人 —— 后者会丢掉「及其召唤物」的一半。
> ⚠ **`target` 曾经写错就等于 `self`**：解析器对不认识的取值一律**回退到"主人"**，
> 于是 `"atacker"` 和 `"self"` 行为完全一样 —— 规则照常触发、什么都不报，只是默默地改错了人。
> 现在它和条件变量一样是**封闭集合**，加载时就拒绝（`unknownTargetSelectorIsRejected`）。
> 🚧 **仍未堵上的同族漏洞**：把 `target` 写在**本来就只作用于主人**的 op 上（`GAIN_ENERGY`、
> `GAIN_SKILL_POINT`、`BOOST_DAMAGE`…）会被**静默忽略**。出货内容里没有任何一条这么写（已核），
> 所以它是潜在而非现实的；`SUMMON` 已经在加载期拒绝 `target`（`requireNoTarget`），
> 其余六个 op 的同类守卫登记在 ROADMAP 里 —— 一次收紧七个 op 的"被接受的词汇表"是另一件事。

#### 负面效果抗性：op `RESIST_DEBUFF` ✅（2026-09-28）

```json
{ "op": "RESIST_DEBUFF", "kind": "control", "percent": 0.35, "permanent": true }              ← 1107 克拉拉 行迹「守护」
{ "op": "RESIST_DEBUFF", "kind": "control", "percent": 1.0, "permanent": true, "target": "summon" }  ← 1413 忆灵「长夜」
```

**为什么需要它。** 引擎早就能对**一个具名状态**免疫（`STAT_CTRL_Frozen` 这类，直接来自怪物自己的数据），
而文档要的是另一件事：**38/97** 份文档提到免疫/抵抗，其中 **8 份**说的是"免疫**控制类负面状态**"
（克拉拉 1107、长夜月 1413、万敌【血仇】、卡厄斯兰那、银狼LV.999【防火墙】、小伊卡…），另外两份说"抵抗**某类**的概率提高"
（1107 守护 35%、1008 坚韧 50% 持续伤害类）。**一串键说不出这件事** —— 明天写的控制会掉在名单外面。
文档自己就证明了这一点：1107 和 1413 都把「控制类负面状态」展开成同样 12 个名字
（冻结，纠缠，禁锢，支配，怒噪，强烈震荡，异梦，缠禁，恐惧，行动锁定，幸福傀儡，怨火灼身），
而这个展开**每加一个角色就可能变长**。

- **一族而不是一串键**：被施加的状态自己回答它属于哪一族（`AbstractBuff.debuffClass()`，`ControlBuff` → `CONTROL`、
  `DotBuff` → `DOT`），`Battle.tryApplyDebuff` 问**受害者的** `BuffManager.debuffResistOf(族)`，把剩下的概率乘上
  `1 − r`。⚠ 默认是 `null`（不属于任何族）——**安全方向**：没分类的状态**不会**被任何族抵抗挡掉，引擎不可能
  悄悄让内容免疫掉它文本里没提的东西。这也是「嘲讽」目前**不在**控制类里的原因（见下面的缺口）。
- **两种拼写一个机制**：「免疫」是 `percent: 1.0` 的抵抗。留一套"免疫"词汇只会让两者漂移。
- **它是 buff**：`permanent: true` 覆盖行迹（克拉拉 守护），`turns: N` 覆盖限时状态（【防火墙】1 回合）；
  它会自己过期、能被具名移除，走的是引擎已有的一切机制。⚠ 它是**正面**效果（`isDebuff() == false`）——
  "更难被控"对携带者是好事，「解除 N 个负面效果」不能把它拿走。
- **身份是 (族, 施加者)，不是族**：两笔贡献**相加**并钳在 1（两处 35% 是 70%，不是两次独立掷骰），
  同一个施加者再施加只是刷新自己那一笔。⚠ 这不是装饰：一次性的 100% 若按"族"顶替，会把克拉拉**永久的** 35% 行迹
  一起顶掉，免疫过期后行迹也消失了 —— 一个没有任何症状的错数字。
- ⚠ **没写概率的控制现在也要掷骰**：`APPLY_CONTROL` / `APPLY_DOT` / `TAUNT` 不写 `base_chance` 时是
  **100% 基础概率**，而 100% 基础概率**仍然**要过效果命中/效果抵抗（含这一节的族抵抗）。在这之前嘲讽是
  **无条件挂上**的，于是「免疫控制类负面状态」对它**完全无效** —— 没有任何文档说过嘲讽必中。
- 契约：`DebuffResistTest` **12 条**（掷骰算术与边界 0.65/0.7 / 两类不串 / 没写概率也挡得住 / 两笔相加并钳在 1 /
  同施加者刷新 / 限时到期 / 永久不掉 / 不是负面 / 嘲讽同管道 / 未归类的状态不被挡 / `kind` 必填且封闭 /
  `percent` 必须在 (0,1]）；出货内容由 `ClaraTraceTest`（35%、永久、不串族）与 `MemospriteTest`
  （免疫挂在**忆灵**身上、召唤者本人**不**免疫、灼烧照样能上）各自钉住。
  **变异**（实测；每次都是全套 1136 条跑完再读）：`debuffResistOf` 恒返回 0 → **10 红**（`DebuffResistTest` 7、
  `ClaraTraceTest` 2、`MemospriteTest` 1）；族不比对（谁挡谁）→ **4 红**（两个"两族不串"用例 + 两处内容断言）；
  去掉钳位 → **1 红**（`twoResistancesOfTheSameClassAddAndClampAtOne`）；身份去掉施加者 → **2 红**
  （「同施加者刷新」与「两笔相加」）；控制/持续伤害/嘲讽改回不掷骰 → **9 红**
  （`ControlTest` 4、`DebuffResistTest` 3、`DotTest` 1、`March7thKitTest` 1）。

#### 一次施放的"落点"：`scale: cast_applied:<状态名>` ✅（2026-09-28）

```json
{ "op": "GAIN_ENERGY", "scale": "cast_applied:冻结", "percent": 6 }   ← 1001 三月七 星魂 1
```

**为什么需要它。** 「终结技**每冻结1个目标**，为三月七恢复6点能量」里的那个数，**不是**
"这次施放瞄准了几个目标" —— 那是事件自带的 `hit_count`，`per_target: true` 已经在乘它了。
它是"**掷骰放过了几个**"：三月七的终结技按目标各 50% 基础概率，对面三个敌人时答案可能是 **0/1/2/3**，
而这个数**只有引擎知道**。照 `per_target` 写，会在三个敌人全部抵抗时照样付 18 点能量 —— 一个没有任何
症状的错数字（而两个拼写并排存在，正是因为游戏两种都写：罗宾是"每次攻击 2 点"，缇宝是"每个命中目标 1.5 点"）。

- **记账点**：`Battle.recordCastApplied(状态名)`，由 `APPLY_CONTROL` / `APPLY_DOT` 调用，
  并且**只在 `tryApplyDebuff` 返回 true 时**——被抵抗的应用不算应用，这正是它存在的理由。
  DOT 的状态名走 `BuffManager.dotStateName(element)`（元素→名字只有那一张表）。
- **读法**：`cast_applied:<状态名>` 是**派生幅值**家族的一员（与 `self_attr:` / `target_max_hp` /
  `owner_def` 并列），解析为 `percent × 落点数`。前缀常量与 `self_attr:` 放在同一处（`TriggerTable`），
  免得两处拼写漂移。
- ⚠ **记录比"施放令牌"活得长**：施放窗口在事件**之前**就关了（`endCast` 在 `finally` 里，
  `ULT_CAST` 之后才广播）—— 而那正是规则能读到它的时刻。所以清空点是**两处**：
  `beginCast`（新的一次施放从零开始：施放**外**的施加 —— 例如「受到攻击时把攻击者冻住」——
  绝不能给下一次施放付钱）与投递完这次施放的事件之后（`SkillExecutor.execute` 的 `finally`）。
- ⚠ **两处装载期拒绝**，都是"静默 0"的形状：① 事件不是施放事件（`TAKING_HIT` / `KILL` 上读它，
  拿到的会是上一次施放的数或 0）；② 状态名不是引擎真会掷的状态（拼错就永远计 0）。
  已知名字 = 三个控制状态 + 四种 DOT 状态；⚠ 刻意**不含**「嘲讽」（没有文档按它计数，而且它算不算
  控制类仍是未决问题）。
- ⚠ **同事件的效果按顺序跑**，所以读它的规则必须写在**产生它的那条规则之后**（1001 的星魂 1 就排在
  冻结规则后面）；这条是**内容顺序契约**，`CastAppliedCountTest` 直接钉住出货文件。
- ⚠ **击破冻住的目标不计**：计数器数的是走抗性管线的施加，而击破施加的控制**故意**不过抗性判定。
  「每冻结1个目标」说的是"这个技能造成的那个状态"，把击破的也算进来是**更激进**的读法。
- 契约：`CastAppliedCountTest` **10 条**（全部命中 18 / 部分命中 12 / 0 命中就是 0（配"没有这条规则"的
  对照）/ 瞄准数≠落点数 / 逐次施放清零 + 施放后记录已擦 / **施放外的施加不污染下一次施放** /
  非施放事件拒绝 / 状态名错拒绝 / DOT 拼写也认 / 出货内容 18 且星魂 0 为 0）。
  **变异**（实测；每次跑完 1156 条）：不记账 → **4 红**；记瞄准的 → **4 红**；施放开始不清 → **1 红**
  （**第一轮 0 红** ⇒ 补了"施放外的施加"那条用例）；投递后不清 → **1 红**；不校验事件 → **1 红**；
  不校验状态名 → **1 红**。

#### 抬高另一条规则的数字：op `MODIFY_RULE` ✅（2026-09-28）

```json
{ "op": "MODIFY_RULE", "rule": "counter",         "amount": 1 }      ← 1001 星魂 4（每回合次数 2 → 3）
{ "op": "MODIFY_RULE", "rule": "ultimate_freeze", "percent": 0.15 }  ← 1001 行迹「冰咒」（基础概率 0.5 → 0.65）
```

**为什么需要它。** 这两句话都**不新建任何东西**，而是抬高**另一条规则已有的数**：「天赋的反击效果每回合可触发的次数
**增加1次**」、「冻结敌方目标的**基础概率**提高15%」。两种"看起来对"的写法都是错的，而且错得看不出来：

- 再写一条 `per_turn: 3` 的规则 → 引擎按**两条规则各自的计数**跑，结果是 **2 + 3 = 每回合 5 次**（不是把上限提到 3）；
- 再写一条 `base_chance: 0.65` 的规则 → **掷两次**，命中率是 1 − 0.5×0.35 = **82.5%** 而不是 65%
  （而且冻结自带的每回合伤害也会被上两遍）。

- **规则要有个名字**：`TriggerSpec` 多了可选的 `"id"`（`CompiledRule.id`）。⚠ **同一文件内唯一**
  （重名装载期拒绝：引用会歧义），而且**引用只在同一张表里解析** —— 遗器规则被每个穿戴者共用，
  它没有"我落在谁的桌上"这个知识，所以"改别人的规则"不会因为疏忽而成立。
- **抬哪一个数由"写了哪个字段"决定**：`amount` 抬高**每回合次数**（正整数），`percent` 抬高**基础概率**
  （`(0,1]` 的分数）。两个字段同时写、都不写、非整数、越界、以及任何它不读的字段（`turns`/`target`/…）
  **全部装载期拒绝** —— 那种含糊正是这个 op 存在的理由。
- ⚠ **目标规则必须真的有那个数**：没有 `per_turn` 时 `+1` 会**静默强加**一个"每回合 1 次"的限制；
  没写 `base_chance` 时"未写 = 100%"根本没有数可抬。两种都在装载期拒绝并说明。
- ⚠ **只能挂在 `BATTLE_START`**：这是"整场战斗有效"的修正（星魂/行迹本来就是无条件的），中途加的加成
  没有东西会在条件结束时收回 —— 与其发明"什么时候撤"，不如拒绝。
- ⚠ **抬高量存在战斗单位上**（`CanHit.rulePerTurnBonus` / `ruleBaseChanceBonus`，与限额计数一起在
  `resetTriggerLimits` 里清空）：规则表按 cid 编译一次并缓存，写回规则会泄漏到 JVM 里**每一场**战斗。
  文件里那条规则**保留原数**（`per_turn: 2` / `base_chance: 0.5`），抬高是**另一条**规则陈述的事实 ——
  与「加护」不把 +1 折进技能的 `turns` 同一个理由。
- **两个消费点**：`fire` 里 `rule.perTurn() + owner.rulePerTurnBonus(rule.id())`（**检查与记账用同一个
  表达式**，否则抬高过的规则会永远触发下去），以及 `applyControl` 里 `stated + owner.ruleBaseChanceBonus(ruleId)`
  —— 规则 id 沿 `apply → applyOne → applyControl` 往下传，而**不是**塞进 `TriggerContext`：嵌套触发会互相
  覆盖，而这个 id 是每条规则自己的。
- 契约：`RuleAmendmentTest` **11 条**（+1 次/回合真的变成 3 次而不是 5 次 / 概率边界 0.65 卡在 0.6 与 0.7 之间
  并配 0.5 的对照 / 未知 id / 重名 id / 目标没有 `per_turn` / 目标没写 `base_chance` / 两个字段互斥 /
  非整数与越界 / 非 `BATTLE_START` / 未读字段 / 出货内容两张星魂对照）。
  **变异**（实测；每次跑完 1168 条）：不读 per_turn 抬高 → **2 红**；不读概率抬高 → **2 红**；
  不做引用/形状校验 → **3 红**；允许重名 id → **1 红**；允许任意事件 → **1 红**。
  ⚠ **踩到一次自己的坑**：第一次 m1 报"0 红"，原因是那个表达式在源码里出现**两次**，替换脚本按"必须恰好一处"
  拒绝了改动，而我把它的输出丢掉了 —— **没生效的变异和存活的变异长得一模一样**。改成"替换两处并检查退出码"后
  才是 2 红。

#### 「这面盾是哪条规则给的」：`has_shield from_rule <id>` ✅（2026-09-28）

```json
{ "on": "TURN_START", "when": ["target has_shield from_rule skill_shield"],
  "do": [ { "op": "HEAL", "scale": "target_max_hp", "percent": 0.04, "amount": 106, "target": "target" } ],
  "min_eidolon": 6 }                                              ← 1001 三月七 星魂 6
```

**为什么需要它。** 「在**战技提供的**护盾保护下的我方目标…」问的不是"有没有盾"，而是"这面盾出自哪个技能"。
三月七自己就有**两面**盾（战技的、星魂 2 的开局盾），所以只问"有盾"、甚至只问"我给的盾"，都会顺手用星魂 2 那面
回血（它能撑 3 回合）—— 一个没有任何症状的错数字。

- **每条规则造的 buff 都记下是哪条规则造的**：`AbstractBuff.ruleId`，由 `withSource`（每个造 buff 的 op 唯一的
  盖章点）从 `TriggerContext.ruleId()` 写入；而"正在跑的是哪条规则"由 `fire` 用 `ctx.withRule(rule.id())`
  **逐条**写入上下文。⚠ 逐次一份**副本**、不是可变字段：一条规则可以在另一条规则的效果里再触发（反击），
  共享字段会把内层规则的名字报给外层剩下的效果。
- **盾把来源记在自己身上**：`CanHit.shieldProvider` / `shieldRuleId`，两条授予路径都写（裸授予走
  `setShield(value, provider, ruleId)`，有时长的盾走 `installShield` 读 installer 的 `source` + `ruleId`）。
  ⚠ 裸 `setShield(value)`（"有人直接把数写上去了"）**两者都清空** —— 一个没有来源的盾不该回答关于来源的问题，
  `from_rule` 对它一律为假。
- ⚠ **跨规则引用仍只在同一张表内解析**，而且装载期要求那条规则**真的造盾**（`SHIELD` 效果），否则条件永远不成立
  —— 与 `MODIFY_RULE` 同一条纪律：静默永不成立一律拒绝。
- ⚠ **不写限定词时行为不变**（`has_shield` 仍是"还立着的盾就算"），所以既有内容一字不改。
- 契约：`ShieldOriginConditionTest` **9 条**（指名的盾算 / 同一个人的**另一条**规则的盾不算 / 只有开局盾时不算 /
  不写限定词时不变 / 裸授予没有来源 / 裸授予仍算有盾 / 未知 id 拒绝 / id 指到不造盾的规则拒绝 / 未知限定词拒绝）
  + 出货内容端到端（**战技盾下每回合回血、只有星魂 2 盾时不回**）。
  **变异**（实测；每次跑完 1188 条）：不看来源 → **4 红**；不给 buff 盖规则名 → **1 红**；
  有时长的盾不记来源 → **2 红**；不校验引用 → **2 红**。

#### 「是哪条技能造成的」：条件 `from_skill` ✅（2026-09-28）

```json
{ "on": "BREAK", "when": ["actor == self", "from_skill SKILL"], "do": [ … ] }   ← 1003 姬子 星魂 4
```

**为什么需要它。** `BREAK` / `KILL` 一直带着**谁**造成的（`actor`），所以姬子天赋的「当有敌方目标的弱点被击破时」
（**任何人**的击破）写得出来；但「**施放战技**对敌方目标造成弱点击破时」写不出来 —— 只写 `actor == self` 会连她
**普攻**（削韧 30）与**天赋追加攻击**留下的击破一起付钱，而文本把这两种排除在外。那是一个没有任何症状的
+1 充能。

- **类别从实例来**：`Damage` 本来就记着产生它的施放的 `SkillCategory`（`assemble` 用它挑"普攻/战技/终结技增伤"），
  所以 `Battle.reduceToughness(…, SkillCategory fromCast)` 与 KILL 事件把它带上，`TriggerContext` 多了第 8 个分量
  `fromCast`（旧的 5 参/7 参构造保留，缺省 `null` = "没有施放造成它"）。
  ⚠ **传类别而不是实例**：公共 API 里能收 `Damage` 的只允许有 `applyDamage` 一个
  （`DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint` 的不变量，本轮**正是它拦下了第一版**），
  而且条件只关心"哪个槽位"。
- **两套拼写只有一张翻译表**：内容用规则里 `skill` 字段那套（`COMMON` / `SKILL` / `ULTRA` / `TALENT`），数据用
  `SkillCategory`（`Normal` / `BPSkill` / `Ultra` / 空），映射只在 `SkillCategory.of(SkillType)` 一处。
  ⚠ `TALENT` → `UNSPECIFIED` 是**对的**：天赋与追加攻击在数据里没有 attack_type，因为它们不是主动施放。
- ⚠ **不收主体**：「谁造成的」是它自己的条件（`actor == self`），写 `self from_skill SKILL` 装载期拒绝
  （否则同一件事有两种说法）。
- ⚠ **装载期拒绝两种"永不成立"**：不在战斗内的槽位（`TECHNIQUE` / `MAZE` 永远没有战斗内实例）与**不携带实例的事件**
  （`TURN_START` 之类）。名字写错也会列出已知的四个。
- ⚠ **没有实例的击破一律为假**：规则直接削韧（`reduceToughness` 四参重载）或敌人技能内联造伤害时，事件照常发生，
  但**没有东西能说清是哪个槽位** —— 这不是缺省值，而是诚实的"不知道"。
- ⚠ **顺带量到的一条事实**：**追加攻击不削韧**（`Battle.applyAdditionalDamage` 从不调用 `reduceToughness`），
  所以「天赋的追加攻击造成击破」在这个引擎里**不可能发生** —— 对照用例因此用终结技（AoE 削韧 60）而不是追加攻击。
- 契约：`SkillAttributionTest` **11 条**（战技击破付钱 / 普攻击破不付 / 终结技击破不付 / 队友战技不付 /
  没有实例的击破不付 / 非战斗内槽位拒绝 / 名字写错拒绝 / 非携带实例的事件拒绝 / 带主体拒绝 /
  出货内容端到端（星魂 4 下这次击破把充能推到上限、触发她的追加攻击；星魂 3 只有 2 点）/ 出货规则形状）。
  **变异**（实测；每次跑完 1179 条）：槽位映射改错 → **4 红**；只问"有没有施放"不问哪个槽位 → **2 红**；
  击破不带类别 → **2 红**；不校验事件 → **1 红**；不校验槽位 → **1 红**。

#### 时长延长：op `EXTEND_BUFF` ✅（2026-09-27）

```json
{ "op": "EXTEND_BUFF", "buff": "护盾", "turns": 1, "target": "target" }        ← 三月七 行迹「加护」
{ "op": "EXTEND_BUFF", "attribute": "ALL_DAMAGE_TYPE_BOOST", "turns": 1 }      ← 布洛妮娅 星魂 6
```

**为什么需要它。** 语料里 **10/97** 份文档说的是"把**已经挂着**的那个东西延长 N 回合"，而不是新建一个：
三月七「战技提供的**护盾**持续时间增加1回合」、布洛妮娅「战技对指定我方目标造成的**伤害提高效果**…」、桑博
「天赋使敌方目标陷入的**风化**状态…」、姬子「战技使敌方目标陷入的**灼烧**状态…」、白露【生息】、藿藿【禳命】、
加拉赫【酩酊】、银狼【缺陷】… 在这之前唯一的写法是**把 +1 折进被延长的那个技能里** —— 那会**抹掉行迹自己那一行**，
并让技能写下一个不属于它的时长（而且行迹将来可能自己带门槛）。所以它是**两个数字两条规则**。

- **两个过滤器，而且都必填**：`buff`（状态名）或 `attribute`（属性名，给「伤害提高效果」这种只以属性为 handle 的
  buff）。⚠ **"我在这人身上的一切"是故意不提供的写法**：布洛妮娅的「作战再部署」行迹给全队 +20% 防御、2 回合，
  她放战技时那个 buff 可能还在跳 —— 若过滤器是"全部"，星魂 6 会**顺手把它也加一回合**，一个没有任何症状的错数字。
  `ExtendBuffTest.itDoesNotLengthenTheOwnersOtherBuffs` 就是这一条。
- **来源也算过滤器**：句子全都写明了来源（「战技**提供的**」「天赋**使敌方目标陷入的**」），而引擎本来就记着这件事
  （`AbstractBuff.source`）。⚠ 为此**顺手做了一次审计**：以前只有构造函数要求的地方才写 `source`
  （`DotBuff` 要它算击杀归属），`StateBuff`/属性 modifier 可以是匿名的 —— 匿名的 buff 会让"我给的"过滤器
  **静默地什么都不延长**。现在**每一条造 buff 的 op 都盖上**（`withSource`），并由
  `ExtendBuffTest.everyBuffARuleCreatesRecordsItsSource` 逐类钉住。
- **`permanent` / `until` 跳过**（没有倒计时可延长），**找不到可延长的不是错**（规则在它的事件上照常触发，
  多半就是"这一发刚上的那个 buff"，但"我在这人身上没东西"也是普通情况，与 `DISPEL` 取不到同一条口径）。
- 契约：`ExtendBuffTest` **9 条**（同一条规则内先挂后延 / 状态名 / 属性名 / 别人给的不延 / 自己的别的 buff 不延 /
  永久跳过 / 来源审计 / 过滤器必填且唯一 / turns 必填且别的字段一律拒绝）。
  **变异**（实测）：过滤器改成"全部" → **5 红**（含布洛妮娅星魂 6 那条）；不看 source → **1 红**；
  永久 buff 也延长 → **1 红**；`withSource` 不盖章 → **3 红**（含来源审计那条）。

`all_allies` 是 P10-3 补的，理由很实际：**真实数据里"我方全体 +X%"占 buff 天赋的多数**，
没有它，触发器表只能表达自身 buff。

#### `MODIFY_ATTR`：通用属性 buff / debuff ✅（P10-3）

93 个角色里 **72 个**的天赋槽是 buff / 强化。这个 op 的存在就是为了让它们**不必各自写一个 Java 类**：

```json
{ "op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.33, "turns": 2 }
```

- `percent` 是**小数**（`0.33` = +33%）。它的含义**取决于属性种类**，这不是修饰：
  - **基础属性**（`HEALTH` / `ATTACK` / `DEFENCE` / `SPEED`）落成 `ADD_PERCENT` modifier ——
    与行迹/遗器/光锥的同类加成**相加**，不是再乘一层；
  - **比率属性**（`CRIT_CHANCE` / `CRIT_ATTACK` / 各类增伤 / `BREAKING_EFFECT` /
    `ENERGY_REGENERATION_RATE` …）里，`percent` **就是值本身**（`0.25` = +25 个百分点）。
    理由：这些属性的值整个是"平铺 modifier"（构造时走 `AttributeBuilder.addPercentPoint`），
    **base 恒为 0**，所以 `ADD_PERCENT` 只会乘 0 —— 规则照常触发、modifier 也挂上了、
    数值却一动不动。这正是本项目最不能接受的那种"不报错的空操作"。
    口径与 `RelicSuit.appendTo` 一致（"其余 `isPercent` 属性按百分点值处理"）。
- **正负号决定 buff 还是 debuff**：`percent < 0` 走 `DEBUFF` 那一半。这不是修饰 ——
  它决定了 modifier 落在属性的哪一半，所以「攻击力 +50%」和「攻击力 −30%」能同时挂在一个人身上、各自移除，而不是互相顶掉。
- **时长必须写且只能写一个**：`turns`（N 个回合）或 `permanent: true`（**整场战斗**）。
  两个都不写 → 加载时拒绝（同 `damage_param` 的理由：引擎不该替内容编一个数）；
  两个都写 → 也拒绝（两个答案互相矛盾，让引擎挑一个等于让规则和文本不一致）。
  ⚠ `turns: 0` 仍是错的，报错会**点名 `permanent`**，因为作者想要的显然就是"没有回合上限"。
- 四个 `*_PERCENT` 变体**在加载时被拒** —— 它们是 `AttributeBuilder` 的输入键、不是运行时属性
  （`getAttribute` 对它们返回 `null`），挂 buff 会"看起来生效但什么都没变"。
- `max_stacks`（别名 `stacks`，**两个都写会被拒**）让重复施加**累加**而不是刷新，
  上限由它给出；不写就是 1，也就是本项目一直以来的"同名 buff 覆盖"。
  上限为 0/负数、超过 `Constant.MAX_STACKS_LIMIT` 都在加载时被拒。
  ⚠ `max_stacks` 写在 `MODIFY_ATTR` **以外**的 op 上会被拒 —— Gson 对不认识的字段只会
  留 `null`，不检查的话"规则照常加载、效果永远不叠"又成了那种不报错的空操作。
  （`permanent` 不在这条里：它是**时长**，所以 `APPLY_BUFF` 也接受它 —— 见下一节。）

#### `APPLY_BUFF`：具名状态 ✅

游戏文本里到处是「处于【协奏】状态时」「【转魄】状态下」「触电状态下的敌方目标」，而条件 DSL 之前
**问不出任何状态** —— 一个本可以纯数据的机制被迫要为每个角色写一个 Java 类。现在两半都齐了：

```json
{ "on": "SKILL_CAST", "when": ["actor == self"],
  "do": [ { "op": "APPLY_BUFF", "buff": "增幅", "turns": 2 } ] }          ← 施加状态

{ "on": "ALLY_ATTACK", "when": ["self has_state 增幅"],
  "do": [ { "op": "MODIFY_ATTR", "attribute": "DAMAGE_PENETRATION", "percent": 0.2, "turns": 2 } ] }
```

- **状态不是新东西，就是一个"带名字的普通 buff"**（`StateBuff`）—— 正是 DOT 迁移那条教训
  （DOT 变成普通 buff 之后就白拿了时长 / 叠层 / 驱散 / `hasBuff` 可见性）。所以状态也有回合数、
  也是 `clearAll` 能清的、也走同一套刷新规则。
- **它自己不做事**：`applyEffect` / `removeBuff` 是空的。"状态是什么"和"状态改什么"分开写，
  于是一个状态能驱动多条效果、多条规则能读同一个状态，而状态本身什么都不需要知道。
- **身份是名字，不是类**：`StateBuff.isSameKind` 比的是**状态名**。默认那套"同类即同名"
  （`getClass()` 比较，见 L-14）会让施加【协奏】把【转魄】**顶掉** —— 同一个角色的两个无关状态，
  其中一个静默消失。同名再上仍是**刷新**（引擎一贯的规则）。
- **时长晚 tick**（`afterMove`）：一个"持续 2 回合"的状态在自己这一回合被施加后，不该在**同一回合**
  的开头就被减一。
- `permanent: true` 对状态是**真用法**：镜流的【转魄】就是整场战斗。
- **尚未覆盖**：`has_state` 现在只认 `StateBuff` 的名字。「触电」这种**由 DOT 表达**的状态、
  「冻结 / 纠缠 / 禁锢」这种**控制**状态，要等 DOT / 控制那两件把各自的查询接进同一个条件变量
  —— **JSON 写法不变**，所以到时候数据不用改。
- 契约：`TriggerStateTest` 11 条。变异 → `target` 读成主人 / 身份退回按类比较 / 条件不看名字 /
  `permanent` 当成普通状态 / 缺主体反而通过，分别红 **1 / 1 / 2 / 1 / 1**。

#### `permanent` 与 `max_stacks` 的落地形态 ⚠（P10-3 后半）

**"整场战斗"是标志位，不是一个大数字。** `remainingDuration` 是个 `int`，
`BuffManager.processBuffTick` 每回合减一 —— 写成 `Integer.MAX_VALUE` 只是"很久"，
而且**仍在倒计时**，总有一天会到期。所以 `AbstractBuff` 多了一个 `permanent` 标志，
`processBuffTick` 对**永久 buff 整个跳过**：它的时长不会被读，也不会漂移，
只能被显式移除（驱散 / 死亡 / `clearAll`）。

**叠层是 opt-in 的，而且不碰 `isSameKind`。** 这是本节最容易做错的地方：
`StatModifierBuff.isSameKind` 的语义是"同名再上一次该不该**覆盖**"，而
`BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking` /
`BuffRuleTest.theSameBuffAgainRefreshesInsteadOfStacking` 把"该覆盖"钉住了。
所以叠层走的是**另一个谓词**：

```java
isStackable()          // maxStacks > 1 才为 true，默认 false
stackGroupKey()        // 同一组才能互相叠；(attribute, modifierType, sourceRole) —— 与 isSameKind 同一元组
```

`BuffManager.addBuff` 对 `isStackable()` 的 buff 走另一条路（`addStackable`）：
数一下同组已有几层，**到上限就什么也不做**，否则照常挂上去。

- **为什么到上限是"什么都不做"**：另外两种选择都不对 —— "淘汰最旧的一层"是滑动窗口，
  一个"最多 5 层"的效果永远达不到文本写的最大效果；"刷新已有层的时长"是另一种机制
  （"再上一次会延长时间"），对"整场战斗"这种没有时长可刷新的情况尤其错。
- **每一层都是普通 buff 实例**（各有自己的 `id` 和自己的 modifier），所以
  `removeBuff` / 到期 / `clearAll` 都是**精确地摘掉一层**，属性回到"剩下几层该有的值"，
  没有残渣 —— 与 `BuffRuleTest.expiryRestoresTheOriginalValueExactly` 同一条口径。
  `BuffManager.countBuffs` / `removeOneBuff` / `removeStacks` 是给"现在几层了 / 消耗一层 / 按属性取回最多 N 层"
  用的查询口。触发器侧对应 `REMOVE_STACK`（2026-09-27 接线，第一个用户是遗器套装 131「星如我见的领航员」：
  「每次战技 +1 层，回合开始或终结技后移除 1 层」——在它之前，叠层只能长不能消，这条文案只能建模一半）。
  仍然**不发** `getBuffs()`（P1-7 的决定）。

**两个规则写同一件事时叠层是共享的**（105 的"攻击**或**受击"就靠这个）：
`max_stacks` 按 `stackGroupKey` 分组，与"哪条规则触发的"无关。

#### `TURN_START` 与 `MoveEvent` 的关系 ⚠

`EventBusTest.turnBoundariesAreStillMoveEvent` 钉住的是**"不要再加一个 `TurnStartEvent` 接口"**，
而 `TriggerEvent.TURN_START` 加的是**数据的订阅能力**，两者不是一回事：

| | buff 接口 | 触发器表事件 |
|---|---|---|
| 谁用 | Java 里 `implements MoveEvent` 的 buff | `resources/**/*.json` 里的 `"on": "TURN_START"` |
| 为什么不能互相替代 | JSON 规则没法 implement 一个 Java 接口 | buff 接口拿不到"某条数据规则" |
| 挂在哪 | `MoveEvent.beforeMove/afterMove`（不变） | `Battle.beforeMove()` 里 `fireTriggers(TURN_START, actor, actor, 0, 0)` |

所以这次**没有**新增任何 buff 接口，`MoveEvent` 的语义一个字没动。

#### 两个必须区分的口径（本项目实测踩到）

| 角色 | 文本 | 建模 |
|---|---|---|
| 知更鸟 1309 天赋 | 「我方目标攻击敌方目标后，额外为自身恢复 **2** 点能量」 | **每次攻击平摊 2 点**，与命中数无关 |
| 缇宝 1403 行迹 `1403103` | 「我方其他目标攻击后，**每击中 1 个目标**使缇宝恢复 **1.50** 点能量」 | **每命中 1 个目标 1.5 点** ⇒ 打 3 个给 4.5 |

差别就是命中数倍。所以效果有一个**显式**的 `per_target` 开关（默认关）——
不让解释器"看到攻击事件就自动按命中数放大"，那样写 `2` 的知更鸟会被算成 `2 × 命中数`。

#### 一轮触发是怎么走的

```
SkillExecutor.execute
  └─ Battle.fireTriggers(ULT_CAST, caster, hits, 0)        ← 施放的是终结技（解析出的 SkillCategory.ULTRA）
  └─ Battle.fireTriggers(SKILL_CAST, caster, hits, 0)      ← 施放的是战技（BPSkill）
  └─ Battle.fireTriggers(BASIC_ATTACK, caster, hits, 0)    ← 施放的是普攻（Normal）
  └─ Battle.fireTriggers(ALLY_ATTACK, caster, hits, 0)     ← 只在命中了目标时（终结技命中同样算）
        └─ 遍历我方每个角色：拿它自己的表
              ├─ 用 (self=它, actor=施放者, target=承受者, hit_count) 匹配规则
              └─ TriggerInterpreter.apply(...) → 真的去 grantEnergy / grantSkillPoint / …
```

> ⚠ **三个"施放"事件互斥，且"其余"什么都不发**：条件 DSL 里没有"这次施放是普攻/战技/终结技"这个变量
> （只有 `actor` / `target` / `hit_count`），所以「当装备者使用战技时」这条规则只可能靠
> **发出端分开**来避免连带在普攻或终结技上触发。判定读的是**解析出的数据**
> （`skills.json` 的 `attack_type` → `SkillCategory`），不看技能名、不看槽位。
> 秘技 / 地图普攻 / 助战 / 天赋**三者都不发** —— 它们不是"战斗内施放"。
> 契约由 `UltCastTriggerTest` 钉住（一次终结技恰好一次、战技只发战技、普攻只发普攻、
> 地图普攻与天赋一个都不发、`ALLY_ATTACK` 不受影响）。
>
> ⚠ **2026-09-27 之前这里只有两路**（"终结技 vs 其余"），于是 `SKILL_CAST` 连**普攻**一起收：
> 遗器套装 109「施放战技时攻击力提高 20%」和知更鸟的 `模进乐段` 都在普攻上白给了一次。
> 是"把知更鸟做完整"时发现的（`ROBIN` 那条规则本该只在战技上触发），见 `ROADMAP` §12 M-24。

#### `FOLLOW_UP`：追加攻击是**独立事件**，不是"一种攻击"

```
Battle.applyAdditionalDamage          ← 全引擎**唯一**的追加伤害结算点（P8-3 那条路）
  └─ Battle.fireTriggersWithSubject(FOLLOW_UP, 攻击者, 承受者, 已结算量)
        └─ actor = 打出追加攻击的人，target = 挨打的人
```

> ⚠ **不能用 `ALLY_ATTACK` 代替**：那条对**任何**攻击都发（普攻／战技／终结技），
> 挂在它上面的「当装备者使用追加攻击时」会连带触发——是**静默多触发**，不是差一点。
>
> **口径**：引擎对"不算一次攻击的攻击"只有一个表示——`DamageType.ADDITIONAL`
> （天赋驱动的追加攻击，如克拉拉的反击，就是这么结算的），所以这个事件只从那一处发出。
>
> **不设"是否打出了伤害"的门槛**：文案说的是"**使用**了追加攻击"，被盾全挡、
> 或打在无敌目标上的那一次**依然是用过了**。所以每次结算都发。
> （曾经加过 `settled > 0` 的门槛，后来删掉：语义本就可疑，而且**测试无法稳定造出
> `settled == 0`**，那个分支会以"没被验证过"的状态上线。）
>
> ⚠ 由此产生的**递归**：用 `DAMAGE` op 回应 `FOLLOW_UP` 就是"追加攻击回应追加攻击"，
> 由 `MAX_TRIGGER_DEPTH` 抛异常收住（同 §4.7 反击的乒乓）。

其余事件的挂点：`BATTLE_START`（`startBattle`，在 `onBattleStart` 之后）、
`ENERGY_GAINED`（`applyEnergyGain`）、`HP_LOST` / `KILL`（`applyDamage`）、
`HEALED`（`heal`）、`SHIELD_GRANTED`（`Battle.grantShield` **与**解释器的 `SHIELD` 分支，见下）、
`BREAK`（`reduceToughness`）、
`SKILL_POINT_GAINED` / `SKILL_POINT_SPENT`（战技点入账/消耗）。

#### `SHIELD_GRANTED`：**"盾被给上了"是独立事件**（2026-09-28，M-43）

```
Battle.grantShield(provider, target, amount, ruleId)     ← 裸授予（没有 turns 的盾）
  └─ value > 0 时 fireTriggersForAlly(SHIELD_GRANTED, provider, target, value)

TriggerInterpreter 的 SHIELD 分支（有 turns → ShieldBuff）  ← ⚠ 第二条路径
  └─ addBuff 之后 amount > 0 时 fireTriggersForAlly(SHIELD_GRANTED, ctx.owner(), target, amount)
```

- `actor` = **给予者**、`target` = 被给盾的人 —— 与 `HEALED` 同约定，于是
  「受到**队友提供的**治疗效果或护盾时」（1321 大丽花 行迹 `1321101`）不需要任何新词：
  `target == self` + `actor is_ally` + `actor != self`（⚠ `is_ally` 只回答"在我方"，**她自己也在我方**，
  所以「队友」必须再加一条 `actor != self`；`!` 不能否定身份比较，`!=` 才是这个 DSL 的写法）。
- ⚠ **两条路径都要发，这是本事件唯一的不对称**：有时长的盾不是经 `grantShield` 落的，而是
  `ShieldBuff` 交给 buff 管理器落的，而 buff 管理器**没有 `Battle` 句柄** —— 所以解释器自己发那一次。
  只做一边的后果是"半数盾隐形"：三月七的战技盾（有时长）正是最需要被看见的那一类。
- ⚠ **只有真的立起一面盾才发**（`value > 0` / `amount > 0`）：`≤ 0` 是这套 API 表示"**清掉**护盾"的写法，
  不是授予 —— 与 `HEALED`"真的回了血才发"同口径。
- ⚠ **裸授予没有给予者**（`actor = null`），所以 `actor is_ally` 为假：一个没人认领的盾不该回答来源问题。
- ⚠ **顺带修实的一处归因**：`HEAL` op 原先调用 `Battle.heal(null, …)`，于是**没有任何规则驱动的治疗有提供者**
  —— 「队友提供的治疗」那一半同样写不出来。现在传 `ctx.owner()`。改动前已量过影响面：
  **没有出货内容订阅 `HEALED`**、**没有出货内容授予 `OUTGOING_HEALING_BOOST`**（§2 标"未使用"），
  所以没有任何数字变化，只是归因变成真的。
- 契约：`ShieldGrantedEventTest` **8 条**（队友给盾 / **有时长的盾** / 她给自己的盾不算 / 裸授予不算 /
  `0` 不是授予 / 一回合一次 / 治疗半边：自愈不算、队友治疗算 / 出货文件结构钉住）。
  **变异**（实测，跑完 1197+ 条）：裸授予路径静默 → **2 红**；有时长路径静默 → **1 红**；
  `0` 也当授予 → **1 红**；内容去掉 `actor != self` → **1 红**。
**事件接线现状**：`TriggerEvent` 里的**每一个**值现在都有发出点（`TURN_START` 与 `TAKING_HIT`
在 P10-3 后半挂上），由 `TriggerTableTest.everyDeclaredTriggerEventIsEmitted` 钉住 ——
声明了却没有发出点的事件对作者是个陷阱，加了值而忘了接线必须让测试变红。
（加载时拒绝未接线事件的通道仍然在，只是当前没有数据能触发它。）

**遗器套装规则走的是同一条通道**：`resources/relic_sets/<setId>.json` 与角色文件同形，
只是按件数阈值分组（`{"4": [ … ]}`），由 `data.RelicTriggerTables` 懒加载，
在装配点 `CharacterFactory` 并入角色自己的表（`TriggerTable.plus`）。
表达不了的那部分登记在 `_unmodelled.json`（见 `ROADMAP.md` §12.5 `F-10`）。

> ⚠ **"数值 + 具名 ability"混合的效果曾经是登记表的盲区**（2026-09-27 发现并修掉，`M-25`）。
> `_unmodelled.json` 原先只覆盖 `properties` **为空**的纯 ability（35 条），而数据里还有
> **29 条**写法是「先给一个无条件数值，再给一个带条件的额外效果」—— 平面饰品 2 件套全是这个形状
> （「使装备者的攻击力提高 12%。当装备者的速度 ≥ 120 时，攻击力额外提高 12%」）。
> 这类效果的第一句由 `properties` 正常生效，**第二句此前被静默丢掉，而登记表里没有它**，
> 所以"没写规则文件"和"忘了写"在这一类上长得一样 —— 正是 `RelicTriggerTableTest` 的
> `everyAbilityBearingBonusIsEitherAuthoredOrRegistered` 想守住、但因为 `properties().isEmpty()`
> 这个过滤条件而没守到的地方。
>
> **现在的账本是两半都算**：`35 纯 + 29 混合 = 17 已写 + 47 已登记`，两个分母**各自钉住**
> （这样"某个效果靠长出一个 `properties` 逃出分区"会立刻变红）。29 条混合里
> **7 条已写**（301 / 302 / 304 / 306 / 307 / 308 / 309，全靠 `self_attr` 与 `ADVANCE`），
> 其余 22 条逐条登记了缺哪种能力；其中 **7 条**的能力其实已经有了、只是还没写文件，
> 它们在 `reason` 开头写 `Writable now:`，这个数字也被钉住
> （`theWritableBacklogIsLabelledAndCounted`）—— 否则"引擎补了口子、登记表还在说缺口子"
> 是这份文件最容易腐烂的方式。

**只对我方开火**：敌人的事件不是我们的内容（P9 才管怪物），而且"敌人挨打"不该让
我方角色被触发两次。带主体的事件用 `fireTriggersForAlly` 做这道阵营判断。

**递归护栏**：触发器产生的效果本身可能再发事件（治疗 → 治疗类 buff → …）。
引擎不试图聪明地判环，而是**限制嵌套深度**（`MAX_TRIGGER_DEPTH = 8`）并**响亮报错**，
让跑飞的表被抓住，而不是把战斗挂死。

#### 加载与"没登记"的语义

- **没有文件 = 空表**（`TriggerTable.EMPTY`），这是**正常状态**不是错误 —— 93 个角色
  目前只数据化了 4 个（缇宝 1403 / 知更鸟 1309 / 克拉拉 1107 / 希儿 1102）。
  `Character.triggerTable` **永不为 null**，所以调用点不用判空。
- **一个角色的文件里可以放多条规则**：知更鸟 1309 是第一个**把行迹（额外能力）也数据化**的 ——
  天赋 `ALLY_ATTACK`（队友攻击 +2 能量）+ 行迹 `华彩花腔`（`BATTLE_START` → `ADVANCE` 25%）
  + 行迹 `模进乐段`（`SKILL_CAST` → `GAIN_ENERGY` 5），三条**触发时机各不相同**的规则共处一张表，
  而且**全程零引擎改动**。这就是"同一角色多机制"的组合测试（`RobinTraceTest` 5 条），
  也是"角色机制 = 数据"这个说法第一次被一个**完整**角色验证。
- **文件存在但写得不对 = 抛异常**（未知事件 / 未接线事件 / 条件写错 / op 不存在 /
  缺必填参数），在**加载那一刻**就炸，而不是等战斗打到一半。
- **懒加载 + 缓存**：按 cid 首次访问才读 classpath，负结果也缓存。
  `TriggerTables.loadCount()` 是可观测的，测试据此断言"第二次不再读盘"。
- 装配点是 `CharacterFactory.create`（P8-0 允许出现 `cid` 的地方之一）。

#### 与 `EnergyProvider` / `SkillPointPolicy` 的分工

| 机制长什么样 | 用什么 | 为什么 |
|---|---|---|
| "我放战技时回 30 能量"（**按技能类型查表**就能表达） | `EnergyProvider` | 每人一份、每次施放问一次，最省 |
| "队友攻击后我回 2 能量"（**要监听别人的事件**） | 触发器表 | provider 的钩子是"自己施放"的，看不到别人的行为 |
| "X 事件后做一件引擎已有的事"（跨事件、带条件） | 触发器表 | 这就是它的定义 |

> 判定口径（P8-0）：**"在某事件后，做一件引擎已有能力的事" ⇒ 数据（触发器）**；
> **"需要引擎还不具备的能力" ⇒ 先补引擎**。触发器表**不是**第二套引擎 ——
> 它的 op 词表严格限制在引擎已有操作上。

### 4.7 天赋与追加攻击：payload 就在天赋槽里 ✅（P8-3）

这一节原本被一个**假前置**卡了很久，结论值得原样记下来。

**假前置**：追加攻击看起来缺一个字段 —— 数据里没有 `is_follow_up`，
`Config/ConfigAbility` 里也没有类似标记（全库只有 2 个 GridFight 文件含 `FollowUp`），
槽位 4 的 `attack_type` 还是 `null`。于是"哪个技能算追加攻击"似乎无从判断，
一度准备手工维护一张表。

**真相**：**不需要那个字段**。追加攻击的 payload 本来就在
**天赋槽（4）自己的技能数据**里 —— 元素、削韧、每级 `params` 一应俱全，
唯一缺的是"**什么时候**放"。而"什么时候"正是触发器表提供的东西。
所以 P8-3 没有新增分类能力，只是给触发器表补了 `DAMAGE` 这一个 op：

| 字段 | 作用 |
|---|---|
| `skill` | 从哪个槽取这次攻击（`TALENT` / `BASIC` / …），元素与削韧跟着走 |
| `damage_param` | 用 `params` 行里的**第几个**数当倍率 |
| `target` | 打谁（`attacker` = 打回打我的人） |
| `per_target` / `as_attack` | 见下 |

> ⚠ **`damage_param` 索引是每个技能自己的，绝对不能猜。**
> 实测：姬子 1003 是 `0`，克拉拉 1107 是 `1`，貊泽 1223 是 `2`，大丽花 1321 是 `2`。
> 写错索引**不会报错**（只要还在行内就仍是合法 double），只会打出一个借来的数，
> 所以 `TalentTest.theShippedRuleDeclaresTheRightParameterIndex` 专门断言
> "出货的这条规则声明的就是 `1`"。

倍率按**技能当前等级**取（`multiplierOf` 用 `skill.getLevel()` 选行），不是硬编码满级。
文档里的 160% / 140% 都是 **10 级**值，与 `params[x][9]` 对得上（两处独立核对过）。

**结算口径**：走 `Battle.applyAdditionalDamage` → `DamageType.ADDITIONAL` ——
**计入击杀归属**（所以希儿能靠它拿到额外回合），但**不算"1 次攻击"**：
目标不回能、不削韧、不发攻击类事件。`as_attack` 字段收下了但**暂时不改变这条路径**
（引擎目前只有一种附加伤害结算），留作将来"某些追加攻击确实算一次攻击"的开关。

> ⚠ **反噬**：附加伤害造成掉血 → 再次发 `HP_LOST`。两个互相反击的角色会乒乓到
> `Battle.MAX_TRIGGER_DEPTH` 然后**抛异常**——响亮地失败，不是把战斗挂死。

**几个样本（都是纯 JSON，零 Java 角色类）**：

| 角色 | 规则 | 说明 |
|---|---|---|
| 克拉拉 1107 `因为我们是家人` | `on: HP_LOST` + `when: ["target == self"]` → `DAMAGE`(`TALENT`, `damage_param: 1`, `target: "attacker"`) | "**我**是挨打的那个" → 打回去 |
| 希儿 1102 `再现` | `on: KILL` + `when: ["actor == self"]` → `EXTRA_TURN` | "**我**是击杀者" → 立即再动 |
| 知更鸟 1309 `模进乐段`（行迹） | `on: SKILL_CAST` + `when: ["actor == self"]` → `GAIN_ENERGY` 5 | 是**自己的**战技：`SKILL_CAST` 广播给我方，靠条件收窄 |
| 知更鸟 1309 `华彩花腔`（行迹） | `on: BATTLE_START` → `ADVANCE` 25% | 没有 actor/subject 的事件照样能用：效果默认作用于**规则持有者** |

希儿为什么不是 `DAMAGE`：她的天赋槽是 `Enhance` / stance 0，**本身不带攻击**，
所以"消灭敌方目标后立即获得 1 个额外回合"只能落到 `EXTRA_TURN`。
这也正好说明槽位 4 的 `attack_type == null` **不是数据缺失** —— 被动本来就不是一次挥击。

**范围**：93 个角色里 **21 个**的槽位 4 带伤害效果（候选承载者，但**不全是**追加攻击），
其余 72 个是 buff / 强化。P8-3 只数据化上面这两个代表，剩下的按
"一次一个 + `source` / `note` 标出处"推进，不批量猜。

**变异验证**（3 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| JSON 的 `damage_param` 1 → 0 | `theShippedRuleDeclaresTheRightParameterIndex` 红 ❌ |
| `when` 里 `target == self` → `actor == self`（把"谁干的"当成"发生在谁身上"） | `claraCountersTheEnemyThatHitHer`、`theShippedRuleRequiresClaraToBeTheVictim`、`theShippedRuleDeclaresTheRightParameterIndex` 红 ❌ |
| 删掉希儿的 `when`（规则变成无条件） | `seeleDoesNotGetATurnFromATeammatesKill` 红 ❌ |

> 第三条是 P8-3 里最值钱的一次验证：`EXTRA_TURN` 本身工作正常，
> 只有"条件确实在承重"被证明了，这条规则才算真的对。

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

> ⚠ **`progress` 只有下界、没有上界**（L-26 修的）。被**推条**过的单位 `remaining > 旧预约长度`，
> 也就是它合法地**离行动点超过一整轮**；此时把 `progress` 上钳到 1，等于把推条**截断成恰好一轮**。
> 踩到的样子：量子/虚数击破是"推条 + 减速"，减速触发重排时推条被吃掉 ——
> 实测**加不加那 20% 额外推条都是 28.409**，完全不可观测。
> 现在 `progress = max(0, remaining / 旧预约长度)`：下界保留（预约不能为负），上界去掉。

### 5.2 行动条操纵

| 方法 | 语义 |
|---|---|
| `delayAction(target, amount)` | 推条：`nextActionTime += amount`（击破用 25% 周期） |
| `advanceAction(target, amount)` | 拉条：`nextActionTime = max(elapsed, nextActionTime - amount)` |
| `advanceActionByPercent(target, pct)` | 按剩余时间百分比提前（`p ∈ [0,1]`），**两侧都 clamp** |
| `resetSignal(signal)` | 重置到 `elapsed + cycleTime()` |

> ⚠ **推条/拉条必须同时写两个账本**（L-26）。`remaining` 与 `nextActionTime` 是同一个状态的两种记法
> （§5.1），而 `delayAction`/`advanceAction` 以前**只写后者** —— 于是下一次 `refreshSpeed`
> 拿**陈旧的** `remaining` 重算，把推条整个丢掉。两个方法现在都调
> `Signal.setRemaining(elapsed, nextActionTime - elapsed)`（那个方法本来就是为"调用方不必自己保证同步"存在的）。
> 回归由 `QueueActionManipulationTest.aDelaySurvivesASpeedChange` 钉住（两半**各自**都能让它变红：
> 恢复上钳 → 两个单位都回到 187.5；去掉账本同步 → 同样两个都回到 187.5）。

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
beforeMove()            → tickDots(actor) → buffManager.beforeMove() → actor.beforeMove(this)
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

三种请求：`SkillRequest`（技能）、`addRequestItems`（新增参战者 —— 由 `WaveManager`
逐波入场时写入，见 §21）、`AdvanceRequest`（拉条 ⚠️ 目前无生产调用者）。

`castImmediate(skill, user, targets)` 是**绕过队列**直接执行的测试/演示入口。

### 6.2 死亡处理 ✅

- HP 只在 `CanHit.takeDamage(double)` 里减，减到 ≤0 置 `death = true` 并把 HP 夹到 0。
- `removeDeadCombatants()` 把死者移出 `Queue`，并清空其 buff（`clearAll()`）。
- **死者仍留在 `battle.characters` / `battle.enemies` 列表里** → 所有需要"活人"的地方必须自己判
  `isDeath()`；`Battle.targetableEnemies()` 是"谁可以被选为目标"的**唯一出口**。
- ⚠ **`perish()` 是"没被打就退场"**（P9-4 加的，唯一调用者是召唤物随主人一起消失）：它只翻 `death`，
  **不碰 HP**。为什么要单独一个方法：**"它走了"和"它被打到 0 血"是两件事**，
  后来问"它伤得多重"的内容不该被告知后者（`SummonTest.theMasterFallingTakesItsSummonWithIt` 钉住 HP 不变）。
  > 🔴 **一条被变异测试纠正的错判**：我最初写的是"走 `takeDamage` 会发 `HpLoss`/`Kill`，
  > 所以会为没人杀死的单位付钱" —— **这是错的**。`CanHit.takeDamage` **自己一个事件都不发**；
  > `HpLoss`/`Kill` 是 `Battle.applyDamage`（唯一结算入口）发的。把 `perish()` 换成
  > `takeDamage(maxHp)` 的变异体**全绿**，就是这个错判的证据。真正让"随主人消失不付钱"成立的，
  > 是这次清扫**跑在结算入口之外**；`perish()` 负责的只是"别谎报它受了伤"。
  它**不负责**把人从 `enemies` 或行动条上摘掉 —— 那是 `removeDeadCombatants()` 的活，两件事在同一个地方发生。

> ⚠ **`Battle.enemies` 是"敌方阵营"，不是"怪物列表"**（L-8）。类型是 `List<CanHit>`，因为敌方阵营
> 除了怪还可以有召唤物（P9-4）；`Battle.enemyUnits()` 才是"其中的 `Enemy`"。这条分工是有意的：
> **"在那一侧"和"是只怪"是两个问题**，需要怪物机制（韧性 / 弱点 / 分元素抗性 / 阶段表）的代码
> 必须显式走 `enemyUnits()`，而不是假设每一格都是怪 —— 所以放宽之后**没有任何地方静默跳过召唤物**。
> `targetableEnemies()` 同样返回 `CanHit`（否则放宽在"谁会被打到"这一步就白费了），
> 胜负判定 `enemies.stream().allMatch(isDeath)` 也是**整阵营**（场上还有召唤物就不算赢）。
> 回归：`EnemyCampSummonTest` 4 条 —— 阵营收得下非怪、引擎目标表包含它、**打死所有怪但召唤物还站着不算赢**、
> 它在行动条上有自己的 Signal。变异：把胜负判定改成 `enemyUnits()`、或把目标表限回 `Enemy`，各红一条。
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

## 7. 技能系统 ✅（数据层与槽位映射都已实现）

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

### 7.2b 非伤害技能：分派表 + 可开关诊断 ✅（P8-2 → P10-3）

**治疗与护盾现在走引擎。** `SkillExecutor.dispatchNonDamaging` 查
`data/skill_effects.json` 拿到"缩放属性 + 参数下标"，算出数值并调用
`Battle.heal` / `Battle.grantShield`；调用方只需要**选谁**。
（这张表怎么来的、为什么必须是一张表：见 `ROADMAP.md` §12.5 的 `F-9`。）

在此之前 `resolveHits` 对非伤害技能**直接 return** —— 技能放出去、战技点扣了、能量也涨了，
**唯独没有任何效果**。而 demo 里"治疗能用"是因为 `Main` 自己长出了**第二条手搓路径**
（`ATK × param[0]`，对 Natasha 是错的）。那条路径已删除。

| 效果类别 | 谁负责 | 现状 |
|---|---|---|
| `RESTORE`（治疗） | `SkillExecutor` 查表 → `Battle.heal` | ✅ P10-3，数值由数据决定 |
| `DEFENCE`（护盾） | `SkillExecutor` 查表 → `Battle.grantShield` | ✅ P10-3 |
| `SUPPORT`（增益） | P10-3 后半（触发器侧 `MODIFY_ATTR` 已可用） | 🚧 技能侧未接 |
| `IMPAIR`（控制/减益） | P10-6 | ❌ |
| `SUMMON`（召唤） | P9-4 | 🚧 **能力有了、数据没有**：`Battle.summon` 已能把召唤物放上场（§24），但"这次施放召谁"在数据里没有一列，所以技能侧仍未分派 |
| `ENHANCE` | 纯被动 | 本就不该作为"行动"施放 |

⚠ **表里没有的条目会被拒绝并报告，而不是当成"没事可做"。** 判据是"游戏陈述效果量的固定语法"
（`equal to / for / by #N% of <谁的> <属性> [plus #M]`）——找不到它就不导参数，
因为剩下的（概率增益、伤害分摊、减伤、嘲讽）本来就不是治疗/护盾。
被拒绝的治疗/护盾是**看得见**的，猜错的数值看不见。

**这个静默很难察觉**：日志上技能"放出去了"、能量也涨了，只是没有任何效果。
所以加了一个**默认关闭**的诊断开关：

```java
SkillExecutor.setLogNotDispatched(true);   // 排查时打开
// → [SkillExecutor] NOT DISPATCHED: BPSkill / RESTORE (Natasha, 1 target(s))
//   → owned by P6-2 implemented (goes through Battle.heal, not this executor)
```

> ⚠ 计划里原本想用 `IO.println` **无条件**打印，实测会刷屏（demo 每回合都在治疗/护盾），
> 故改为开关。护栏：`SkillExecutorDiagnosticTest`。
>
> ⚠ **那三条护栏在 P10-3 被反转了，不是删掉。** 其中一条原本断言
> "`diagnosticDoesNotChangeBehaviour` —— 打开日志后治疗**仍然不生效**"，
> 存在意义是防止"加了个 log"冒充"实现了效果"。效果真的实现之后，它被改写成
> `healingSkillsHealForTheDocumentedAmount`（断言 Natasha 终结技 = 自身生命上限 9.2% + 92），
> 拒绝类改用它真正还做不了的那一类。**当初把设计意图写进注释，回报就在这里** ——
> 一眼看得出它该被反转，而不是该被保留。

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
delayMovePercent(enemy, BREAK_DELAY_RATIO)             推条 25% 周期（固定部分）
attachBreakDot(attacker, enemy, element)               挂 DOT（仅 hasDot() 的元素）
attachBreakControl(enemy, element)                     控制状态 + 元素额外推条（§8.6）
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

`models/buffs/DotBuff` = `{来源, 元素, 每次基础伤害}` + 继承来的 `duration`，**就是一个普通 buff**，
挂在 `BuffManager` 里（与其它 buff 同一条 `List`，**List 不是 Set**，因为规则是"先上先结算"）。

> **它曾经是 `models/Dot` + `Enemy.dots` + `Battle.tickDots(Enemy)` 的独立模型，已迁进 buff 体系。**
> 那不是风格问题：`tickDots` 收 `Enemy`、列表挂在 `Enemy` 上，使得"**boss 给我们挂燃烧**"
> 在类型上就**无法表达**（而 HSR 里这是常态）。迁移后角色与敌人走同一条 DOT 路径，
> 并且顺带拿到了驱散、`hasBuff` 查询、统一的刷新/叠层规则。

- **挂载条件**：击破元素 ∈ `Constant.BREAK_EFFECTS` 中 `hasDot()` 为真的那些，即
  `{FIRE, THUNDER, PHYSICAL, WIND}`（`Constant.DOT_ELEMENTS` 由该表派生，不再手写）；
  冰（冻结）/量子（纠缠）/虚数（禁锢）属控制类，不挂 DOT（P10-1 统一）。
- **每次结算伤害** = `击破基数 × BreakEffect.dotRatio()`（当前四个元素共用 `DOT_RATIO = 0.5`）
  —— 注意这个 base **既不含击破特攻、也不含削韧值**（公式里那个 `削韧值` 因子在这里没有出现，
  只有击破基数本身）。物理裂伤在游戏里还按目标生命上限算，这里也统一成了同一个式子。
- **结算 / 生命周期是两件事，分在两个对象里**（这是迁移的核心设计）：
  - **生命周期**（时长、到期、驱散）= `DotBuff` 自己，由 `BuffManager` 驱动。
    `DotBuff` 是 **early buff**（`super(turns, true)`），所以倒计时发生在 `beforeMove` ——
    这正是"持续伤害"的含义，late buff 会整体错开一回合。
  - **结算**（伤害本身，需要完整装配区）= `Battle.tickDots(CanHit)`，它通过
    `BuffManager.allBuffsOf(DotBuff.class)` 查询后逐个造 `Damage`。
    **为什么伤害不写进 `tickEffect`**：`AbstractBuff.tickEffect(CanHit)` 拿不到 `Battle`，
    为一个 buff 去加宽这个签名会把 `Battle` 泄漏给所有 buff。
- **结算时机**：**任意单位**回合开始时（`Battle.beforeMove` 里的 `tickDots(actor)`），
  按施加顺序（`allBuffsOf` 按挂载顺序返回快照）逐个结算；被 DOT 打死则剩下的不再结算（不鞭尸）。
- ⚠️ **`beforeMove` 里两行的顺序是契约**：先结算、后倒计时，所以 N 回合的 DOT 恰好结算 N 次；
  写反就变成 N-1 次，而对 1 回合的 DOT 则是"倒计时到 0 先被移除、一个伤害都没打出来"。
  `DotTest.aOneTurnDotStillSettlesBeforeItExpires` 就是钉这个边界的（2 回合以上两种顺序看不出差别）。
- DOT 伤害通过 `Damage(type = DOT)` 走完整装配 → 吃增伤、吃防御/抗性/易伤，**不可暴击**。
- **叠层**：`DotBuff.isSameKind` 恒为 `false` —— DOT 的身份是**实例**而不是**种类**。
  默认的 buff 规则是"同名再挂 = 刷新"，套在这里会让第二次击破**顶掉**第一层燃烧并丢掉剩余结算；
  引擎的 DOT 规则一直是"先上先结算、同元素多份并存"。若将来内容需要"再击破刷新燃烧"，改这一个方法即可。
- 🚧 `DOT_RATIO = 0.5` 与 `DOT_TURNS = 3` 是**示例值**（代码注释标了 `TODO data`），
  两个都还没有真实数据来源。

### 8.6 控制状态（P10-2 击破施加；2026-09-27 起也由技能施加）✅ 数值仍是示例值

冰 / 量子 / 虚数击破不留 DOT，而留一个**控制状态**。表在 `Constant.CONTROL_EFFECTS`
（`ControlEffect(key, name, resistKey, turns, blocksAct, slowPercent)`），由 `BreakEffect.control` 按键引用；
同名索引 `Constant.CONTROL_STATES` 按**文档用的中文名**（冻结/纠缠/禁锢）取，规则与 `has_state` 走这一个。

**⚠ 计划里"冻结期受伤害 +30%"是错的，数据说的相反。** 词条原文（六条独立来源，冰系全部一致）：

```
"冻结状态下，敌方目标不能行动同时每回合开始时受到等同于<施法者>#4%攻击力的冰属性伤害"
   —— 深寒徘徊者 / 永冬灾影 / 三月七 / 杰帕德 / 镜流
"禁锢状态下，敌方目标行动延后#2%，速度降低#4%"                        —— 瓦尔特
"「纠缠」会使敌人行动延后，并在敌人下次行动时对其造成额外的量子属性伤害"   —— 明写「弱点击破」量子
```

所以冻结是**每回合冰伤**（一个 DOT），不是"受伤加成"；禁锢/纠缠是**推条 + 减速**，而且**仍然能行动**。

| 元素 | 不能行动 | 减速 | 额外推条 |
|---|---|---|---|
| 冰 → 冻结 | **是** | 否 | 是（+50%） |
| 量子 → 纠缠 | 否 | 是（−20%） | 是（+20%） |
| 虚数 → 禁锢 | 否 | 是（−20%） | 是（+20%） |

**控制是一个 `ControlBuff`**（2026-09-27 起；在那之前它是"`StunBuff`，可能再加一个减速"，写在
`Battle.attachBreakControl` 里）。给类而不是继续组合的理由只有两条，但都致命：

- **它得有名字。** 组合出来的状态没有名字，于是「冻结状态」**问不出来** —— `has_state` 看不见它、
  条件 DSL 无法据此 gate，而**击破冻住的单位会答 `false`**（一个条件在它最该成立的一半场合失效，且没有任何症状）。
- **两条路必须是同一个状态。** 击破能施加控制、而技能不能（技能要的是同一套组合**再加一次抗性判定**），
  两边各写一遍组合，迟早会漂 —— 现在 `APPLY_CONTROL` 与击破各建一个 `ControlBuff`，名字/部件/移除全一致。

| 部分 | 怎么实现 |
|---|---|
| 名字 | `ControlBuff.getName()`（冻结/纠缠/禁锢），`BuffManager.hasState` / `removeState` 认它 |
| `blocksAct` | 同一个 buff 的 `canAct() == false`（`StunBuff` 仍是"纯不能行动"的原语，`BuffManagerTest` 等在用） |
| `slowPercent` | 由 `ControlBuff` 自己挂一个 `StatModifierBuff.percentDebuff(SPEED, …)` 并在 `removeBuff` 里摘掉 |
| 每回合伤害 | `ControlBuff` 可带一个 `DotBuff`（同一个 `removeBuff` 摘掉）—— ⚠ 目前**只有击破那条路没用**，见下 |
| 推条 | `Battle.delayMovePercent`（**瞬时**推，不是 buff，故意不属于这个状态） |
| `resistKey` | `Battle.tryApplyDebuff`（**只给技能施加那条路**） |

- ⚠ **击破不走抵抗判定。** 破韧是"韧性条空了"，不是被抵抗的 debuff —— 若让 `STAT_CTRL_*` 取消一次击破，
  弱点击破会**静默什么都不发生**。冰锋本身就带 `STAT_CTRL_Frozen = 1`，
  `ControlTest.anIceBreakFreezesEvenAMonsterThatIsImmuneToFreezeSkills` 钉住"照样冻结"。
- ⚠ **顺序是契约：先加状态、最后推条。** 速度变化会**重排**行动条（`Signal.refreshSpeed` 按已走进度
  重算 `nextActionTime`），所以"先推条、后减速"会把额外推条重算掉。实测：推条在前时，量子击破的
  行动值变化**加不加额外推条都是 28.409**（完全不可观测）；推条在后才是 `≥ 42.61`（固定 25% + 20% 的下限，
  实际因重排略大）。一次性推条必须是对行动条的**最后一次写入**。
- 🚧 **冻结/纠缠的伤害段有意没做**：词条说冻结每回合受冰伤、纠缠下次行动受量子伤（两者都是 DOT），
  但**没有来源给出"击破施加"的比例**。留在 `BreakEffect.dotRatio = 0`，
  `BreakEffectTableTest` 会因为有人乱填而变红；真要填时 `attachBreakDot` 一行都不用改。
- 🚧 示例值 + `TODO data`：三个 `turns`（都取 1）、三个额外推条（0.5 / 0.2 / 0.2）、两个减速（0.2）。
  数据里没有击破控制表（`breaking_rate.json` 是"等级→击破基数"），词条正文只给机制、不给击破数值。
- ✅ **「控制类负面状态」这个概念是文档定义的，而且可以量**（2026-09-28）：语料 **28 处**给出**同一个十二个名字**的
  定义 —— 冻结，纠缠，禁锢，支配，怒噪，强烈震荡，异梦，缠禁，恐惧，行动锁定，幸福傀儡，怨火灼身。这是
  §4.6 的「族抵抗」（`RESIST_DEBUFF`）选**族**而不是**键表**的直接依据：名单是游戏的、很长，而且每加一个角色就
  可能变长。⚠ **两边都要看清**：① 引擎只实现了其中**三个**（冻结/纠缠/禁锢），所以今天一条
  「免疫控制类负面状态」挡得住的只有引擎会施加的那三种（其余九个登记为 `M-52`）；② 「嘲讽」**不在**这十二个名字里，
  所以 `TauntBuff` 留在族外是**照文档办事**，不是回避决定（`DebuffResistTest` 钉住）。

#### 技能施加控制：op `APPLY_CONTROL` ✅（2026-09-27）

```json
{ "op": "APPLY_CONTROL", "control": "冻结", "turns": 1, "base_chance": 0.5, "target": "all_enemies" }
```

- `control` 走**封闭集合**（`Constant.CONTROL_STATES`），写错名字装载期拒绝并列出已知的三种：
  控制是"一个东西"，规则只说**哪一个**、**多久**、**多可能**，不说它做什么。
- `turns` **必填**（「持续1回合」）：没有回合数的控制就是**永久锁**，而它读起来像一条正常规则 —— 正是这套词汇
  最不能接受的形状。
- `base_chance` 是**基础概率**（基础概率 ≠ 规则级 `chance` 的固定概率，所以两个字段用了两个词）：
  它按**每个目标**掷，并且走真管线（`Battle.hitChance`：基础 × (1+施加者效果命中) × (1−受击者效果抵抗) ×
  (1−它对该状态的专属抗性)）。⚠ 规则级 `chance` 是**整条规则一次**的骰子，永远表达不出"三个敌人各 50%"。
- ⚠ 首个用户 **1001 三月七终结技**「受到攻击的敌方目标有 50% 基础概率陷入冻结状态，持续 1 回合」。
  契约：`ControlTest` 16 条（击破三元素 + 12 条技能施加：落点/名字/管线两端/专属抗性/与击破同状态/
  gate/按名字移除带走减速/可被驱散/四种装载期拒绝）。**变异**：`hasState` 不认控制名 **4 红**、
  忽略 `base_chance`（不掷骰）**3 红**、控制不带走减速 **1 红**、不校验状态名 **1 红**、
  内容里把 `冻结` 写成 `纠缠` **1 红**。
- ✅ **状态自己的"每回合伤害"已经接上**（2026-09-27）：`APPLY_CONTROL` 可以带 `element` + 幅值，`ControlBuff`
  就把它编成一个 `DotBuff` 一起挂、一起摘 —— 所以「冻结状态下…每回合受到冰属性附加伤害」**只在那次冻结真的命中时**
  才出现（抵抗掉的那次不会留下一段火在烧），而「解除冻结」也不会留下半个状态。
- ✅ **规则也能挂 DOT 了**（2026-09-27，`APPLY_DOT`）：在这之前**只有击破**能挂 DOT
  （`Battle.attachBreakDot`），于是语料里 11/97 的「使目标陷入**灼烧/触电/裂伤/风化**状态，每回合造成 X% 攻击力的
  伤害」一整族没有写法。元素用引擎自己的拼写（`Fire`），幅值可以是字面量或**从规则主人属性派生**
  （`scale: self_attr:ATTACK` + `percent`），落地时算一次就冻结进 DOT。
  ⚠ **元素同时也是状态身份**：`has_state 灼烧` 问的就是"身上有没有一个 Fire 的 `DotBuff`"，
  两套拼写之间只有 `BuffManager.DOT_STATES` 一张翻译表。
  ⚠ 代码里的 `base_chance` 走 `Battle.tryApplyDebuff(..., null)`：**没有专属抗性键**，因为数据里的 `STAT_*`
  是按**具名状态**给的，而 DOT 的状态就是元素 —— 四个元素都没有这个键。这是数据的事实，不是偷懒：
  哪天有了，就是在这里多传一个参数。
  契约：`DotTest` **16 条**（原有 9 条 DOT 机制 + 7 条"规则挂 DOT"：落点/名字/派生幅值且**落地即冻结**/
  常量项/基础概率两端/三种装载期拒绝）。**变异**：字面量幅值被忽略 **1 红**、派生幅值清零 **4 红**、
  基础概率不掷 **1 红**、控制不编载荷 **3 红**、内容里 `percent` 0.6→0.5 **1 红**。

### 8.7 超击破 ✅（P4-6，2026-09-19 实现）

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

### 9.6 战技点（SP）✅（P8-4，2026-09-23）

**全队共享**的一个池子，不是每个角色各有一条。引擎实现的**基础规则**：

| 项 | 引擎值 | 与游戏是否一致 |
|---|---|---|
| 上限 | **5**（`Constant.SKILL_POINT_MAX`） | ✅ 基础值 5，但**可被改变**（见下） |
| 开局 | **3**（`Constant.SKILL_POINT_START`） | ✅ 常规开局 3，但**可被改变**（见下） |
| 普攻（`Normal`） | **+1** | 🚧 一刀切，强化普攻有例外（见下） |
| 战技（`BPSkill`） | **-1**，不够则**不能出手** | ✅ |
| 终结技（`Ultra`） | 中性（不涨不跌） | ✅ |
| 追加攻击 / 天赋（`attack_type == null`） | 中性 | ✅ |
| 地图普攻 / 秘技（`MazeNormal` / `Maze`） | 中性 | ✅（地图普攻在战斗外，那时没有战技点） |
| 每场战斗重置 | 是 | ✅（战技点不跨战斗继承） |

**规则来源**：⚠ 项目的规格 `HSR.md`（`E:\code\blog\hsr\HSR.md`）**没有战技点这一节**——
全文只有 §6.1 一句「笑点：战技点上方计数」提到它。所以上面这套基础值是从游戏机制与
角色文档反推的：全队共享与「上限 5 / 普攻 +1」来自机制攻略，开局 3 来自玩家问答
（「正常情况下开局都是三个战技点的」）。**这条规则没有规格背书，只有交叉印证。**

#### 🚧 与游戏的三处已知差距

> 📋 **这些缺口的权威登记处是 `ROADMAP.md` §12.5**（`F-1` 上限可变 / `F-3` 强化普攻 /
> `F-4` 角色级供点 / `F-5` 敌人绕过 / `F-7` 语义过窄 / `F-9` 技能效果参数布局 / `F-10` 套装 ability 的表达范围；
> `F-2`/`F-6`/`F-8` 已解决）。**本节只记结论，细节一律以 §12.5 为准** —— 免得两处各写一份然后漂移。
> （原登记处 `DOC_VS_CODE.md` §F 已于 2026-09-26 删除并入 ROADMAP §12。）
>
> 处理原则：**引擎保持通用、可扩展、稳定，不替角色机制背锅**。下列差距**本轮只标记、
> 不改实现**。`SkillPointGameParityTest` 把每条的**引擎当前行为**钉在断言里。

1. **`F-1` 上限不是恒定 5**。花火天赋「上限额外 +2」、欢愉光锥「每有 1 名欢愉命途角色 +1，
   最多 3」；甚至有光锥的触发条件是「上限 **≥ 6**」。引擎的 `SKILL_POINT_MAX` 是常量，
   **没有**"改队伍级资源上限"的口子。

2. **`F-2` 开局不是恒定 3** —— **✅ 已解锁（P10-3 后半）**。`RELICS.md`：过客 4 件套
   「战斗开始时立即为我方恢复 1 个战技点」→ 开局 4（两人穿就 5）。
   根因曾是"那条套装效果属于**具名 ability**，引擎没有执行通道"；现在通道就是**触发器表**：
   规则写在 `resources/relic_sets/101.json`（`BATTLE_START` → `GAIN_SKILL_POINT`），
   在装配点并入角色自己的表。顺序也验证过：开局值在 `Battle` 构造时写入（策略的 start），
   `BATTLE_START` 由 `startBattle()` 之后才发，所以 +1 不会被覆盖 ——
   由 `RelicAbilityBattleTest.openingSkillPointsIncludeThePasserbyFourPiece` /
   `theOpeningValueIsAssignedBeforeBattleStartFires` 两条钉住。
   ⚠ 剩下的 `F-1`（上限可变）与 `F-7`（`hasSkillPoint` 语义）与套装 ability 无关，
   仍要等光锥/角色侧的口子；套装 ability 的其余 31 条见 `F-10`。

3. **`F-3` ⚠「普攻 +1」一刀切，强化普攻有例外 —— 这条会让引擎算错**。
   `1315_波提欧.md`：强化普攻「**无法恢复战技点**」；但 `1201_青雀.md`：
   「施放强化普攻后，**恢复 1 个战技点**」。数据里强化普攻也是 `"Normal"`
   （**没有单独类型**，实测 122 条 `Normal` = 93 角色 × 1 + 饮月/镜流/青雀/波提欧的多档），
   所以引擎一刀切：对青雀**对**、对波提欧**错**。
   ⚠ **不要改成"强化普攻一律 +0"**（会把青雀改坏）—— 正解是**每个技能自带战技点增量字段**。

角色级供点机制（布洛妮娅 50% 概率 +1、素裳打击破目标 +1、青雀争番单场一次 +1、寒鸦【承负】
每 2 次行动 +1、貊泽/大丽花追加攻击 +1、海瑟音开场结界 +1、米沙"每消耗 1 点 → 下次终结技
+1 段 + 回 2 能量"、花火"消耗战技点时回能 + 溢出储存"）此前**全都没接** → **`F-4`**。
其中**布洛妮娅那条已经接上了**（2026-09-27）：星魂 1 写成 `characters/1101.json` 里的一条规则
（`SKILL_CAST` + `actor == self` + `chance: 0.5` + `cooldown: 1` + `min_eidolon: 1` → `GAIN_SKILL_POINT 1`），
由 `BronyaEidolonTest` 钉住 —— 它不需要新钩子，只需要**规则级几率 + 冷却 + 星魂门槛**三件（同日一起补的）。
其余几条的**形状**也清楚了：都是"触发器表上一条给点的规则"，缺的是各自的数据而不是机制。
其中米沙/花火要监听的是"**战技点被消耗**"这件**事**，是 `EnergyProvider` 那种
"按技能类型查表"的钩子**表达不了**的 —— 需要新事件 `SkillPointEvent`（`onSkillPointSpent` / `onSkillPointGained`），
那两件**已经存在**（见 §4），所以差的也只是规则。

#### 收口点与实现细节（P8-4 重构后）

**唯一收口点在 `Battle.useSkill`**，但 `Battle` 只**转发**、不认识规则：

```
performAction(skill, targets)          ← 我方与敌方 AI 都走这里
   └─ useSkill(skill, targets)
        ├─ skillPointPolicy.onSkillCast(user, skill)   ← 规则全在策略里
        │     └─ false → 出手不成立（不排队 → 没有"没花钱却打出去"）
        └─ skillRequest(...)
```

规则本身在 `StandardSkillPointPolicy` 里（`"Normal" → +1` / `"BPSkill" → -1` /
其余中性，**外加阵营判断**）。`Battle` 侧只留一层门面：`getSkillPoints()` /
`getSkillPointMax()` / `hasSkillPoint()` / `gainSkillPoint(n)` / `spendSkillPoint()`。

**为什么这样拆**（用户定的原则：*引擎要稳定、拓展性强，不替角色机制背锅*）：

| 关注点 | 归属 | 理由 |
|---|---|---|
| 战技点的**基础规则** | `StandardSkillPointPolicy` | 规则会长大，但不该长在 `Battle` 里 |
| **阵营判断** | 策略（**不是** `Battle`） | 由 `SkillPointPolicyExtensibilityTest` 证明：换成"不判阵营"的策略，敌方普攻就会涨点 —— 说明这个判断真的在策略里 |
| **上限/开局/增量** | `Resource` + 策略构造参数 | 花火上限 +2、过客 4 件套开局 +1 都有挂靠点，**不需要改引擎** |
| **角色级供点** | 覆盖 `gainForCast`（将来由 P8-7 效果表驱动） | 子类注入，装配点决定用哪个策略 |

三处**刻意不碰**战技点的入口：
- `castImmediate` —— 绕过队列的测试/演示入口（按定义就不该有资源成本）；
- `castUltra` / `requestSkill` —— 终结技本来就不消耗，走 `requestSkill` 直连队列；
- `EnemySkill.getData()` **恒为 `null`** —— 敌人技能不走角色倍率表，所以敌方行动
  在策略的 null 保护处就返回了。

> ⚠ **写这条时踩的坑**：`switch (skill.getData().getSkillType())` 对 **`null` 字符串会抛 NPE**
> —— 而天赋/追加攻击的 `attack_type` 在数据里就是 `null`。现在改成先解析成
> `SkillCategory`（`null` → `UNSPECIFIED`），**从类型上就不可能再踩**。

> ⚠ **重构时踩的坑（值得记住）**：我一度让 `applySkillPointCost(skill)` 从
> `currentMove` 猜出手者。在"没有行动者"的场景（直接调用的测试、演示的治疗分支）
> 它猜出 `null`，而 `null != Camp.PLAYER` 让策略**静默变成空操作** ——
> 一个**不报错的错误答案**。现在签名是
> `applySkillPointCost(skill, user)`，**必须显式传出手者**，误用直接编译不过。

> ⚠ **测试陷阱**：拿敌人默认的 `EnemySkill` 去测"敌方不影响战技点"是**空转** ——
> 它的 `getData()` 恒为 `null`，无论有没有阵营判断都会通过（我第一版就是这么写的，
> 靠变异测试才发现）。`SkillPointTest.enemyBasicAttackDoesNotFeedThePlayerPool` 因此
> **手工给敌人装了一个真实角色普攻**，让"阵营判断"成为唯一能挡住它的东西。

**"原子性"**：0 点时策略返回 `false` 且点数**不变**（`Resource.spendExactly` 的语义：
不够就一点都不扣），`performAction` 因此返回 `false`、**不排队**，所以不会有
"没花钱却打出去了"。

**阵营判断的边界**：判的是 `Camp.PLAYER`，不是"是不是玩家操控"。将来加**友方召唤物**
（忆灵属 P9-4）时，它们用 `Normal` 出手**也会给我方加战技点** —— 游戏里忆灵行动同样供点，
所以这个行为大概正确；要调它**改策略即可**（`SkillPointPolicyExtensibilityTest.
campJudgementBelongsToThePolicyNotTheBattle` 演示了这一点）。

**不产生能量条的角色照样受约束**：战技点是队伍级资源，与个人的能量条/层数无关 ——
黄泉（`NoConventionalEnergyProvider`）照样要花战技点放战技，`SkillPointGameParityTest`
有覆盖。

#### 重构落地的两个通用抽象

**`enums.SkillCategory`** —— 数据里的 `attack_type` 枚举化（`F-6` 已解决）：
`NORMAL` / `BPSKILL` / `ULTRA` / `MAZE_NORMAL` / `MAZE` / `ASSIST` / `ELATION_DAMAGE` /
`UNSPECIFIED`（数据里的合法空）/ `UNKNOWN`（引擎不认识的数据）。
`SkillData.getCategory()` 是入口，**判分支一律用它**。
- **不抛异常**：数据是外部产物，多一个新类型就炸引擎是稳定性问题 → 降级成 `UNKNOWN`
  并用 `isKnownValue()` 让调用方决定要不要出声；
- **大小写不敏感 + 去空白**：建表与查表走同一个 `normalize()`。
  ⚠ 第一版我只在 javadoc 里写了"大小写不敏感"却没实现（键存原样、查表用小写），
  被 `knownValuesRoundTrip` 抓到 —— **文档承诺要有测试兜着**；
- 原先两处裸字符串 `switch`（战技点、回能）都已改用它。

**`models.Resource`** —— 有边界的队伍级数值资源：`max` / `min=0` / `maxOverflow`，
`gainClamped`（不溢出）/ `gain`（显式溢出、封顶）/ `spend`（能扣多少扣多少）/
`spendExactly`（不够就一点都不扣）。
- **溢出默认关闭**（`maxOverflow = 0`）："不小心用 gain 就溢出"是不可能的，
  要溢出必须显式 `setMaxOverflow`（对应花火"记录溢出最多 10 点"那类机制）；
- **不变式 `value ∈ [0, max + maxOverflow]` 永远成立**：所以下调额度会把越界存量夹掉。
  ⚠ 第一版只改额度不夹值，能造出 `max=5, overflow=0, value=15` 的**静默非法状态**，
  被测试抓到后改成夹取；
- 战技点是它的第一个用户，P8-8 的层数资源（【残梦】/【飞黄】/【火种】/【追忆】/【新蕊】）
  是第二个 —— 那正是它被抽出来的理由。

#### 剩余隐患

- **~~`F-6` 裸字符串~~** ✅ 已解决（`SkillCategory`）。
- **~~`F-8` `useSkill` 机制堆积~~** ✅ 已解决（策略抽出，`Battle` 只转发一次）。
- **~~`F-2` 开局恒定 3~~** ✅ 已解决（套装 ability 有了执行通道：过客 4 件套开局 3 → 4）。
- **`F-1` / `F-3` / `F-4` / `F-7` 仍然只是"有挂靠点"，没有接**：
  上限/开局的**接口**已经有了（换策略构造参数即可，见
  `SkillPointPolicyExtensibilityTest`），但**谁在什么时候改**还没定 ——
  角色级供点要 P8-7 触发器表（表已可用，缺的是内容），**改上限**要套装 ability
  （那条落在 `F-10` 登记的 31 条里），强化普攻的增量要数据补全。
  **`F-3` 仍是唯一会让引擎算错数值的一条。**

---

## 10. 增益与减益（Buff 体系）

### 10.1 生命周期 ✅

`BuffManager`（每个 `CanHit` 一个）持有 `List<AbstractBuff>`。

- `addBuff(buff)`：普通 buff 先移除**同类**旧 buff（`isSameKind`）并调其 `removeBuff`，
  然后写入持有者（`setOwner`）、加入列表、调 `applyEffect`。
  ⚠ **叠层 buff（`isStackable()`）走另一条路**（`addStackable`）：不移除同类，
  而是数够 `maxStacks()` 层就**拒绝新的**，否则再挂一层 —— 默认（不声明上限）行为一个字没变。
  见 §4.6「`permanent` 与 `max_stacks` 的落地形态」。
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

- `AbstractBuff(duration, isEarlyBuff[, permanent])`：`isEarlyBuff=true` 的在 `beforeMove()` 递减，
  `false` 的在 `afterMove()` 递减；每个拥有者回合**恰好 tick 一次**。
- **`permanent = true` 的 buff 完全不 tick**（`processBuffTick` 直接跳过）：这是"整场战斗"
  的落地形态 —— 不是"一个很大的回合数"，而是一个**永远不会被读到的**回合数。
  它只能被显式移除（驱散 / 死亡 / `clearAll`）。
- 到期在 `processBuffTick` 里移除；若到期的 buff 让 `canAct()` 为 false，
  置 `blocked = true`（"晕眩最后一回合仍然挡住行动"）。
- ⚠ **`BuffManager` 里每一次遍历 `buffs` 都走 `List.copyOf`**（M-12，2026-09-26 修）：
  派发方法会**调进 buff 代码**，而"受击时给自己/对手挂一个 buff"是正常内容（附加伤害上易伤、
  反击挂标记…），遍历活列表会让它在**伤害结算内部**抛 `ConcurrentModificationException`，
  或者静默跳过某个 buff。`processBuffTick` 原来的 `removeIf` 有同一个洞（谓词里调 `tickEffect`）。
  规则写在 `BuffManager` 的类 javadoc 里 —— 改回活列表就会重新打开这个洞。
- ⚠ `clearAll()` 会连 `blocked` 一起清（M-5）：否则"控制 buff 在它挡人的那回合到期"之后，
  即使把所有 buff 清空，`canAct()` 仍是 false，**驱散也救不回来**。
- ⚠️ `blocked` 只在 `beforeMove()` 开头清零，所以**只对 early buff 生效**：
  后置控制 buff 在 `afterMove()` 到期时，它的最后一回合挡不住。`StunBuff` 恰好是 early 所以看不出来。
- ⚠️ `clearAll()`（死亡时调用）不重置 `blocked`，清空后仍可能 `canAct() == false` 直到下次 `beforeMove()`。

### 10.4 查询口 ✅

`BuffManager.hasBuff(Class<? extends AbstractBuff>)` —— 按 `getClass()` 精确匹配、`null` 返 false。
`countBuffs(Class)` / `removeOneBuff(Class)` / `removeStacks(AttributeType, int)` 是叠层用的三个延伸口
（"现在几层" / "消耗一层" / "按属性取回最多 N 层，**最新的先走**"）。
`debuffCount()` / `removeDebuffs(int)` 是"负面效果"的两个口 —— 它们问的是每个 buff 类的
**`isDebuff()`**：DOT、控制、易伤、嘲讽、以及**符号为负**的属性修正算负面；减伤、加速、具名状态不算
（状态的立场属于施加它的那条规则，所以它答 `false`，将来由 `APPLY_BUFF` 带立场）。默认 `false` 是**安全方向**
（新 buff 类不会被 `DISPEL` 误删），五个 debuff 类的答案在 `DebuffTest` 里列成一张表。
**不提供 `getBuffs()`**：遍历与判定留在 manager 内部，避免外部改列表导致 CME。

### 10.5 现存 buff 实现

| 类 | 类型 | 说明 |
|---|---|---|
| `BoostDamageBuff` | 属性型 | `ALL_DAMAGE_TYPE_BOOST` 加 `rate`（平值），用 `id` 精确摘除 |
| `StatModifierBuff` | 属性型 | **通用**属性 buff/debuff（`MODIFY_ATTR` 的落地形态）：(属性, modifier 种类, 数值, 时长或 `permanent`, 可选 `maxStacks`)。`isSameKind` = (属性, modifier 种类, buff/debuff) 三元组，`stackGroupKey()` 用同一个三元组决定谁能和谁叠 |
| `VulnerabilityBuff` | 注入型 | 易伤，受击方负面，`ModifierSource.DEBUFF`；数据侧由 `MODIFY_DAMAGE_TAKEN` 正数创建；三参构造 `(duration, ratio, permanent)` 支持"整场战斗"，且构造器拒绝非正 ratio（"负的易伤"是减伤，另一个类） |
| `ReductionBuff` | 注入型 | 减伤，受击方增益，`ModifierSource.BUFF`；数据侧由 `MODIFY_DAMAGE_TAKEN` 负数创建，同样支持 `permanent` 与正 ratio 校验 |
| `StunBuff` | 控制 | early buff，`canAct() == false` |
| `DotBuff` | **生命周期型** | 击破 DOT（P4-5 / P10-0）：只持 `{元素, 每次基础伤害}` + 继承的时长，**不含伤害逻辑** —— 伤害由 `Battle.tickDots` 结算（`tickEffect` 拿不到 `Battle`）。early buff；`canAct()` 恒 `true`（否则燃烧结束时会把主人多冻一回合）；`isSameKind` 恒 `false`（实例身份，见 `engine.md` §8.5）；构造器校验四项输入：来源/元素非 null、`turns >= 1`、`baseDamage` 有限且 `>= 0`（L-12） |
| `SpeedBoostBuff` / `SuperBreakBuff` / `TauntBuff` | 属性/注入 | 早于 `StatModifierBuff` 的专用类，见各自 Javadoc |
| `StateBuff` | **生命周期型** | 具名状态（`APPLY_BUFF` 的落地形态）：只持一个**名字** + 继承的时长，`applyEffect` / `removeBuff` 为空 —— 状态是"关于这个单位的事实"，改什么由 `has_state` 条件 + 别的 op 表达（见 §4.6）。`canAct()` 恒 `true`（否则状态一到期就把主人冻一回合）；`isSameKind` **按名字**比（按类比会让【协奏】顶掉【转魄】，见 L-14）；时长**晚 tick**；构造器校验名字非空、`turns >= 1` |
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
  + 行迹（SkillTrace 树，skill_traces.json）
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

### 12.5 行迹（`SkillTrace`）✅

`skill_traces.json[cid]` 是带 `prev_trace` 的节点列表，`SkillTrace.init(cid)` 按 `prev_trace.getFirst()`
连成树（无前置 = 根），`sumAttributes` DFS 汇总，`appendTo` 追加为 `SKILL_TRACE` 来源的修正。
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
`stanceWeak`/`stance`/`maxStance`/`stanceCount`/`stanceType`、`summonIds`（召唤名单，P9-4 见 §24）。
> ⚠️ 漏了 `effectHitRate`（见 §11）。

**同一份数据的第二个出口（P9-4）**：`EnemyFactory.resolve(...)` 是"查配置 / 查模板 / 查等级组 / 缩放"
这四步，`SummonFactory.create(...)` 复用它但只填面板与技能 —— 召唤物不是 `Enemy`，装不下
抗性 / 弱点 / 韧性 / 阶段表。两个工厂共用同一半，是为了让缩放规则不可能出现第二份（见 §24.1）。

数据规模实测：`monster_config` 2649 条、模板 2649 个 id 全覆盖、
`breaking_rate` 与 `hard_level_group` 等级集合完全一致（1–100 与 120）。

---

## 14. 数据装载（`Constant` / `JSONReader`）

`Constant` 的静态块（类首次加载时执行）装载 8 组 / 10 张表（`MONSTER_CONFIGS` 吃两个文件）：

| 表 | 文件 | 可变性 |
|---|---|---|
| `RELIC_MAIN_ATTRIBUTES` / `RELIC_SUB_ATTRIBUTES` | `main_attribute.json` / `sub_attribute.json` | ⚠️ **bean**（不是表）：`frozen()` 只认 Map/List，会原样放行；其内部 map 仍可变 → **N-11** |
| `WEAPONS` | `weapons.json` | ✅ 容器 `frozen(...)`（H-1） |
| `CHARACTERS` | `character_data.json` | ✅ 容器 `frozen(...)`（H-1） |
| `SKILL_TRACES` | `skill_traces.json` | ✅ 容器 `frozen(...)`（H-1，含嵌套 `List`） |
| `SKILLS` | `skills.json` | ✅ 容器 `frozen(...)`（H-1，含嵌套 `Map`） |
| `MONSTER_TEMPLATES` | `monster_template_config.json` | ✅ 容器 `frozen(...)`（H-1） |
| `HARD_LEVEL_GROUPS` | `hard_level_group.json` | ✅ 容器 `frozen(...)`（H-1，含嵌套 `Map`） |
| `MONSTER_CONFIGS` | `monster_config.json` + 补丁 `monster_attack_modify_ratio.json` | ✅ `Map.copyOf`（本来就不变） |
| `BREAKING_RATE` | `breaking_rate.json` | ✅ 容器 `frozen(...)`（H-1） |

> H-1 的边界是**容器、不是 bean**：表本身再也不能被 `clear()`/`put()`，但表里的 bean 字段仍可写。
> 上表那两行 `RELIC_*` 是唯一还暴露可变内部 map 的地方（`RelicMainAttribute.getAttributeByStar` 与其
> `RelicSubAttribute` 孪生，见 ROADMAP §12 N-11）。

懒加载（不占静态块）：`Constant.stages()` ← `stage.json`（见下）。
B 组的 `enemy_skills.json` 与手写补丁也是静态块里读的（`ENEMY_SKILLS` / 合并进 `MONSTER_CONFIGS`）。

> 另外 `Benchmark` 会读 `dump_data.json`，但 **generator 不产出它**（是个遗留的基准输入）；

> ⚠️ `public static final` 只锁引用不锁内容，**这些表可被运行时修改**（`Constant.SKILLS.clear()` 是合法的）。

### 14.1 生成器产出 vs 引擎实际加载

`generate_data.py` 现在产出 **28 个文件**（27 个 JSON + `text_ids.txt`），引擎用到其中 **12 个**
（另外还读 2 个非 generator 产出的文件）。差额登记如下 —— 免得再出现
"文档说没有、其实文件早就在"的偏差（本仓库此前那份 9/1 的快照就少了 11 个文件、
`character_data.json` 也没有 `rarity`）。

> ⚠ 计数在 2026-09-26 重新数过：`skill_effects.json`（P10-3 的技能效果参数表，引擎通过
> `SkillEffects` 读取）是后来加的，所以"产出 27 / 引擎 11"这两个数字各 +1。

**A. generator 产出且引擎已加载（12）**

`main_attribute` · `sub_attribute` · `weapons` · `character_data` · `point` · `skills` ·
`skill_effects` · `monster_template_config` · `hard_level_group` · `monster_config` ·
`breaking_rate` · `stage`

**B. 非 generator 产出但引擎已加载（2）**

| 文件 | 说明 |
|---|---|
| `monster_attack_modify_ratio` | 仓库内的补丁文件（人工维护），由 `normalizeMonsterConfigs` 合并 |
| `enemy_skills` | 仓库内手写技能表（P5-3） |

**C. generator 产出但引擎完全不读（16）** —— 都是"为后续阶段准备 / 喂给文档导出脚本"的：

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
| `relic_sets.json` | 遗器套装效果（60 套 / 92 条） | 已加载（`data.RelicSets`）：2/4 件套的**数值**生效；**具名 ability** 走触发器表（`resources/relic_sets/<setId>.json` + `data.RelicTriggerTables`），其中能表达的已写盘、其余登记在 `_unmodelled.json`（`F-10`） |
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

> 账目（2026-09-26 用脚本重新核对）：generator 产出 **28** 个 = **A 12 个已加载** + **C 16 个未加载**。
> B（`monster_attack_modify_ratio`、`enemy_skills`）与 D（4 个辅助文件）都**不在**这 28 个里。
> C 那张表按"用途"合行写了，所以行数（14）少于文件数（16）——
> `challenge_*` 3 个、`eidolons`+`enhanced_ranks` 2 个都是各占一行。
> 要查"引擎读哪些"，看 A/B 两组即可。
> （P10-3 之前这组数字是 27 = 11 + 16；`skill_effects.json` 加进来后各 +1。
> C 组表头原先写"（12）"与正文的 16 自相矛盾，已一并修正。）

> **`stage.json`（9 MB / 约 2.9 万条关卡）是懒加载的**，走 `Constant.stages()`（P7-4）：
> 它是最大的数据表，而多数测试与 demo 根本不碰关卡，塞进静态块等于每次 `Constant`
> 初始化都多付 ~35 MB 堆。另外它**缺失时返回空表而不抛异常** —— 一个可选功能不该把
> 整个测试套件拖下水（护栏见 `StageLazyLoadTest`）。

`JSONReader.fromJSON` 用 UTF-8 + try-with-resources 读 classpath `/data/`。
> 两种"数据不可用"都在**检测处**报同一个 `IllegalStateException`，并带上文件路径：
> 文件**缺失**，以及文件存在但解析成 `null`（空文件 / 字面 `null`）—— 后者原先会变成
> `Constant.WEAPONS = frozen(null)`，在很远的某行 NPE，或表现得像"这张表本来就是空的"（M-17）。
> 空表仍然合法（`{ }` → 空 map，不是 `null`）。
> 而且 `src/main/resources/data/` 被 `.gitignore` 排除，**新克隆的仓库必须先生成数据**，否则所有测试全红。

---

## 15. 已实现 vs 未实现总表

### ✅ 已实现并接入

- 属性代数（`base × (1+Σadd) × Π(1+mul) + Σpure`，带来源可撤销）
- 完整伤害乘区（增伤/易伤/减伤/虚弱/暴击/防御/抗性）+ 唯一结算入口
- 12 种伤害类型及其"可暴击/吃增伤"规则
- 11 个事件家族（BattleStart / Move / Damage / Attack / SkillCast / Energy / HpLoss / Heal / Kill / Break / 战技点增减），见 §4
- 触发器表：角色机制 = 数据（`resources/characters/<cid>.json`），引擎只解释，见 §4.6
- 层数资源：`Resource` + `ResourceManager` + `EnergyProvider.canCastUltra` 闸门 —— 没有能量条的角色也能开大，见 §23
- 行动条（绝对时间 + 堆），推条/拉条 API
- 技能展开：单体/AOE/扩散/弹射 + 每段独立结算
- 韧性、弱点削韧、击破伤害、击破推条、击破跳回合（**需调用方主动调**）
- 击破 DOT（火/雷/物理/风，先上先结算，敌人回合开始结算）
- 能量（字段、回能效率、5 个回能钩子、终结技门槛与清零回能）
- 战技点（P8-4）：全队共享池，开局 3 / 上限 5，我方普攻 +1 / 战技 -1 / 终结技中性，见 §9.6
- 通用队伍级资源 `Resource`（有边界的值 + 显式溢出 + 原子消耗；战技点是第一个用户，P8-8 复用）
- 技能类别枚举 `SkillCategory`（数据 `attack_type` 的类型化，消灭裸字符串 `switch` 的静默失配）
- 可替换的战技点策略 `SkillPointPolicy`（`Battle` 不认识规则，只转发一次）
- Buff 生命周期（同类替换、early/late tick、控制阻断、按侧注入）
- 角色面板（等级缩放 + 光锥 + 遗器 + 行迹 + 额外加成）
- 敌人面板（模板 × 等级组 × 实例 × 精英组）
- 附加伤害 / 真实伤害及其"不算一次攻击"语义

### 🚧 占位 / 简化

| 项 | 现状 |
|---|---|
| 技能槽位 | ✅ P8-2 已接（`Constant.SKILL_SLOT` 六槽，见 §7.2）；剩下的占位是 `Character.fromAttributes(...)` 这个测试入口（写死槽位 1） |
| 击破 DOT 数值 | `DOT_RATIO=0.5`、`DOT_TURNS=3` 是示例值，base 不含击破特攻与削韧值 |
| 神君/账账类追加攻击 | 未进入事件体系 |
| 召唤物 | **两个阵营都能创建**（P9-4 + L-8 友方那一半，§24）：`summon_id` 名单 → `SummonFactory` → `Battle.summon`，主人倒下带走它；我方召唤物进 `allies`（阵营），`characters` 是它的子集视图。**忆灵已完整**：面板继承（§24.5）、在场可问可指（§24.6）、规则召上场（§24.7）、能打且伤害按自己的属性缩放（§24.8）。⚠ 仍缺：忆灵技 2~5、攻击削韧、连携攻击、"指令忆灵行动"、技能侧 `SkillEffectType.SUMMON` 分派（数据里没有"召谁"那一列） |
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
| 忆灵 | ✅ P9-4（§24）：面板继承 / 在场可问可指 / 规则召上场 / 能打且伤害按自己的属性缩放。仍缺忆灵技 2~5、削韧、连携攻击、"指令忆灵行动" |
| 欢愉 | 只有属性/类型占位，无机制（`DamageElement`/`DamageType` 里有名字，没有来源） |
| 七系击破异常 | 只有 4 种 DOT，冻结/纠缠/禁锢未做 |
| 多韧性条 | `stanceCount > 1` 未处理（恢复时直接回满） |
| 转阶段 / 多血量阶段 | 只有 `invulnerable` 标志，无阶段切换 |

---

## 16. 测试与可验证性

- **53 个测试类 / 495 个用例**（截至 P10-3），全部通过（`.\gradlew.bat test`）。
- 覆盖重心：伤害乘区（`DamageZoneTest` 24 条）、技能展开（`SkillExecutorTest` 13 条）、
  能量（`EnergyTest` 8 + `EnergyBattleTest` 16）、韧性击破（`ToughnessTest` 6 +
  `ToughnessBattleTest` 8 + `BreakDamageTest` 5 + `BreakStateTest` 4 + `DotTest` 6）、
  怪物数据（`MonsterDataTest` 7 + `EnemyScalerTest` 4 + `EnemyFactoryTest` 5 +
  `EnemySkillTest` 5）、行动条（`QueueTest` 4 + `QueueRoundTest` 7 +
  `QueueActionManipulationTest` 8 + `QueueTieBreakTest` 8 + `ExtraTurnTest` 10）、
  关卡波次（`StageFactoryTest` 9 + `StageLazyLoadTest` 2 + `WaveManagerTest` 15）、
  角色装配（`CharacterFactoryTest` 15 + `SkillSlotMappingTest` 13）、
  战技点（`SkillPointTest` 12：池子边界、真实链路增删、0 点原子性、
  敌方不送点、`null` / `MazeNormal` 中性；`SkillPointGameParityTest` 12：
  逐条对照游戏规则，并把三处已知差距固定在注释与断言里）、
  重构引入的通用抽象（`SkillCategoryAndResourceTest` 18：枚举解析的稳健性
  —— 空值/未知值/大小写、`Resource` 的三个边界与不变式；
  `SkillPointPolicyExtensibilityTest` 6：**不改引擎**只换策略就能改
  增量/上限/开局，并证明阵营判断确实在策略里）、
  事件契约（`EventBusTest` 22：8 个新事件的时机/次数/过滤条件，
  含四条易错边界 —— **非伤害技能也发施放事件**、**被盾全挡不算掉血**、
  **没花出去不发消耗事件**、**没有能量条就没有能量事件**）、
  触发器表（`TriggerTableTest` 20 + `TriggerDataBindingTest` 5：两个真实角色**纯数据**跑通
  —— 缇宝「开局 +30、每命中 1 目标 +1.5」与知更鸟「队友每次攻击 +2」；
  并钉住 `per_target` 的"每命中"与知更鸟的"每次攻击"这两种口径不能混）、
  层数资源（`ResourceTest` 19：manager 增删 / `PARTY` 被拒 / "满了"是**上升沿** /
  溢出上限 / `HP_LOST` 真实链路 / 两个 op / **资源满 → 开大可用**）、
  真实队伍（`RealTeamTest` 11：4 人 4 命途 / 元素非 null / 锥的 `type` 对上命途 /
  锥**真的进面板** / 每次调用给新实例 / `WeaponData.rarity` 绑定没静默失守 / 占位入口已消失）、
  天赋与追加攻击（`TalentTest` 11：克拉拉受击反击打回**攻击者** / 倍率取自天赋槽的
  `damage_param` / **`target == self` 与 `actor == self` 不可混用** / 别人挨打她不动 /
  希儿击杀后额外回合、**队友击杀不给回合**；另钉住两条数据事实 ——
  "文档百分比 = 10 级值"与"倍率索引因技能而异"）、
  通用属性 buff（`BuffRuleTest` 14：百分比/固定值的算术次序 / **不同属性与不同 modifier 类型互不顶掉** /
  **buff 与 debuff 在同一属性上共存** / 到期精确还原 / 同类再上只刷新不叠加 /
  移除按 modifier id 精确 / **只有 SPEED 变化才通知行动条** / 非法输入在构造时就炸）。
- **可复现性**：`Battle` 接受注入的 `java.util.Random`；全仓库无 `Math.random()`。
  > ⚠️ 但 `Relic.createRandomLevelZero` / `MapUtils` 用的是不可播种的 `ThreadLocalRandom`，
  > 所以"同一份遗器"无法跨进程复现。
  > 同速单位的出手顺序**已确定**（E4：`Signal.sequence` 平局裁决，见 §5.4）。
- **测试盲区**（重要）：
  - ~~`Battle.startBattle()` 从未被任何测试调用~~ —— **已补**：`BattleResultTest`、
    `WaveManagerTest`、`SkillSlotMappingTest`、`SpecialEnergyProviderTest`、
    `UltraThresholdTest`、`SkillExecutorDiagnosticTest` 都走真实 `startBattle()`。
    仍缺的是**开场事件链本身**的断言（`BattleStartEvent` 的监听者行为没有专门的用例）。
  - `BuffManagerTest` 直接调 `BuffManager`，**绕开 `Battle`** → 真实事件顺序、控制阻断的时序未验证。
  - `EnergyTest` 用手写 `SkillData` 而非真实 `skills.json` → 测不出数据字段绑定错误
    （真实数据的绑定由 `EnergyGainDataTest` 的继任者 `SkillSlotMappingTest` /
    `CharacterFactoryTest` 覆盖）。
  - 无 `AttributeTypeTest`；`CharacterTest` 不覆盖拷贝构造器。

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
| §附录 4 持续伤害"先上先结算" | `DotBuff` 挂在 `BuffManager.buffs`（`List`），`allBuffsOf` 按挂载顺序返回快照，`tickDots` 据此迭代 |

### 18.2 攻击类型增伤 ✅（2026-09-27）

`HSR.md` §2.2 的两条括号里各有**两个来源**：

```
增伤区 = 1 + Σ(伤害类型增伤 + 攻击类型增伤)
易伤区 = 1 + Σ(伤害类型易伤 + 攻击类型易伤)
```

**引擎现状（按字段核实）**：

- **伤害类型增伤**：✅ `Battle.assemble` 按元素拿 `FIRE_DAMAGE_BOOST` 一类的属性 +
  `ALL_DAMAGE_TYPE_BOOST`，一起 `addBoost` 进**同一个** `BoostArea`，符合 `1 + Σ(…)`。
- **攻击类型增伤**（「普攻 / 战技 / 终结技造成的伤害提高」）：✅ **已做**。
  三条属性 `BASIC_ATTACK_` / `SKILL_` / `ULTIMATE_DAMAGE_BOOST` 加算进**同一个**增伤区；
  选哪一条由 `Damage.getCastCategory()` 决定 —— 这个事实**必须显式携带**，因为普攻与战技都是
  `DamageType.NORMAL`，`type` 根本分不开。（上一版这一节把 `DamageType` 写成
  "NORMAL/SKILL/ULTRA/…"，正是这处混淆的痕迹：`DamageType` 说的是**伤害种类**，不是施放类别。）
- **攻击类型易伤**（「受到战技伤害提高」）：❌ 仍没有 —— 易伤区还没有"按施放类别过滤"的那一路。
- **追加攻击**：✅ 走 `FOLLOW_UP_DAMAGE_BOOST`，**按伤害类型**（`DamageType.ADDITIONAL`）判，
  不是按施放类别；所以"由普攻触发的追加攻击"拿的是追加攻击增伤，不会顺带拿普攻增伤。

**落地口径（故意的保守）**：只有**施放本身那一段**伤害携带施放类别；击破 / 超击破 / DOT /
附加伤害 / 真实伤害一律 `UNSPECIFIED`。否则「战技造成的伤害提高」会悄悄变成"战技引起的击破伤害
也提高" —— 要那种口径得单独拍板，不能当作接线的副作用。

**变异验证**：`SkillExecutor` 不透传类别 → 3 红；映射写错 → 3 红；三条属性全指向同一条 → 4 红
（含"不能碰战技"那条，也就是"作用域"这个性质本身）；`assemble` 不读类别 → 3 红。
契约：`DamageScopeBoostTest` 8 条。

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
| §3.4 **嘲讽** | ✅ 已实现（P5-2）：`TauntBuff` 是纯标记，**硬指定目标**（单体 / 扩散中心）而非仇恨加权 —— 与 §3.4 的"按百分比提高仇恨值"写法不同，见 §19.2。⚠ 规格与实际规则不符这点记在这里，不再重复 |
| §3.5 **效果命中与抵抗的生效概率公式** | ✅ 已实现（P6-1）：公式与 §3.5 一致，三个因子乘算。顺手修了 `EnemyFactory` 漏写 `effectHitRate` 的问题（之前敌人命中恒 0）。见 §20.1 |
| §4 **护盾** | ✅ 已实现（P6-3）：`CanHit.shield` 先于 HP 被扣、不叠加。🚧 规格里的"护盾量提高"没有对应属性，护盾量目前就是传入值 |
| §4 **治疗乘区** | ✅ 已实现（P6-2）：`Battle.calculateHeal/heal`。⚠ 规格的 `(1 - 治疗降低)` 与 `(1 + 受疗加成)` 合并成一个因子（`HEAL_TAKEN_RATIO` 取负即降低），因为属性表里没有单独的"治疗降低" |
| §5 **忆灵系统**（独立单位/面板快照/连携攻击） | 🚧 **面板与伤害都做了**（§24.5 / §24.8：面板是**推导**而不是快照 —— `值 = percent × 召唤者该属性 + flat`，读召唤者**已解析**的面板；攻击按**忆灵自己**的属性缩放）。**仍缺连携攻击**（"一次行动中的两次独立攻击"）与"指令忆灵行动" |
| §6 **欢愉体系**（阿哈速度/笑点/好活当赏/欢愉伤害公式） | ❌ 只有 `DamageType.ELATION` 与 `AttributeType.ELATION_DAMAGE_BOOST` 两个占位；`elation_basic_level_damage.json`（101 条）**从未被加载** |
| §7 **超击破** | ✅ 已实现（P4-6，2026-09-19）：`SuperBreakBuff` + `BreakDamageCalculator.buildSuperBreak` + `SkillExecutor` 里追加 `SUPER_BREAK` 段。**但**公式里的 `(1 + 削韧值提高)` 与 `(1 + 弱点击破效率提高)` 仍缺（属性不存在），`SUPER_BREAK_BOOST = 0.4` 是示例值 |
| §8.1 **忆灵伤害 / 欢愉伤害** 作为独立类型 | 类型枚举里有 `MEMORY` / `ELATION`，但无来源 —— 忆灵的伤害**有来源了**（§24.8，按自己的属性缩放），但走的是普通元素伤害而不是 `MEMORY` 类型；欢愉仍然没有 |
| §附录 5 **特殊免疫判定**（如"记忆"祝福对冻结先查免疫） | 🚧 **部分**：`monster_config` 的 `debuff_resistance` **已接**（`EnemyFactory` 落进 `Enemy.debuffResist`，在 §20.1 的效果命中公式里作 `(1 - specific)` 因子，为 0 即完全免疫）。缺的是"祝福/机制级的免疫豁免"那一层 |

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

### 19.4 敌方技能（`EnemySkill` + `enemy_skills.json`；忆灵也用这个类，见 §24.8）

```
base = 施法者的 baseAttribute × multiplier        // 每段一次；默认 ATK
Damage(type = damage_type, element)               // 走 Battle.applyDamage 统一装配
```

- 敌人的技能**不走角色的倍率表**（`SkillData` 是角色技能的结构），所以 `EnemySkill.getData()`
  返回 `null`、`execute` 全自定义。它也不削韧（敌人不打韧性条）。
- 每段独立走 `applyDamage`：**每段独立判定暴击、独立结算**。
- **形状决定打谁 —— 按施法者的对立阵营**（`battle.getOpponents(user)`）：`SingleAttack` 打主目标、
  `AoEAttack` 打对立阵营全体、`Blast` 打主目标与相邻。⚠ 这里原先**写死** `battle.allies`
  （这段代码诞生时只有敌人在用），所以忆灵（§24.8）第一次用 AOE 会打**自己人**、怪物一点不掉 ——
  一个方向完全反了却毫无提示的错误。
- **`baseAttribute`**（P9-4 忆灵）：倍率乘哪条属性是可配的，默认 `ATTACK` 所以敌人逐位不变；
  忆灵的伤害写的是「等同于忆灵 X% **生命上限**」，于是同一个类读自己的 `HEALTH`。
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
- **不叠加**：`Battle.grantShield(provider, target, amount)` 直接**覆盖**当前值（≤0 视为清盾）。
- ✅ **「使装备者提供的护盾量提高 X%」= `AttributeType.SHIELD_BOOST`（2026-09-28）**，四个读者：遗器 **103**
  净庭教宗的圣骑士 4 件套（20%）、遗器 **128** 自匿星芒的隐士 2 件套（10%）与 4 件套（12%）、一件光锥
  （12/15/18/21/24%）。它们此前都躺在 `_unmodelled.json` 里，理由一模一样：*"需要一个把装备者造的每一面盾放大的东西"*。
  - ⚠ **这不是 op，是一个属性**：文档写的是「装备者**提供的**护盾量」，指明的是**给盾的人**，所以这个数属于
    造盾的那一方、并且随盾走 —— 与「受到伤害提高」（挂在承受者身上）方向**相反**，这也是它必须与
    `OUTGOING_HEALING_BOOST`（治疗的孪生兄弟）分开的原因：合成一个会让「提供的护盾量提高」顺手强化治疗。
  - **在授予那一刻算一次就冻结**（与 `MODIFY_ATTR` 的派生值同一条口径）：已经立着的盾不会因为给盾的人后来变强而变大。
  - ⚠ **两条路必须是同一个数**：`SHIELD` op 有"裸授予"（没写 `turns`）与"有时长的盾"（`ShieldBuff`）两个形态。
    `Battle.boostedShield(provider, amount)` 是**唯一**的公式，`ShieldBuff` 在**构造时**用它、裸授予在
    `grantShield` 里用它 —— 否则三月七那面「持续3回合」的盾会是"3 回合 120、之后永远 100"。
  - ⚠ **技能面板那条路也要盖章**：护盾还能由**技能数据**装（`SkillExecutor` 的非伤害分支读 `Defence` 效果的参数，
    三月七 100102 就是一条「38% 防御力 + 190」）。那一路同样以**施法者**为提供者 —— 否则数据驱动的护盾技能
    会是全场唯一吃不到「提供的护盾量提高」的盾。
  - ⚠ **两参重载 = "来源不明 → 不放"**（`grantShield(target, amount)`）：夹具与示例用它，既有的数字一字不变；
    一个无法归属到给盾者的加成若照放，会把全场每一面盾都悄悄加强。
  - ⚠ 比例属性在 `MODIFY_ATTR` 里落地的是**纯值（pure）**修饰符而不是 `ADD_PERCENT`
    （`statModifier`: `attribute.isPercent ? "pure" : "add_percent"`）—— 因为"零基数的百分比"还是零。实测踩到过：
    用 `addPercent` 写这条用例时两笔加成"互相抵消"成 100，换成 `pure` 才是 122。
  - 契约：`ShieldBoostTest` **11 条**（倍率 / **给盾者的**属性而不是承受者的 / 来源不明不放 / 两笔相加 /
    0 与清盾 / 两条路同一个数 / 技能面板那条路 / 103 与 128 的出货内容 / 套装加成不外溢）。
    **变异**（实测，每次都跑完 1147 条）：整个加成去掉 → **5 红**；加成改从**承受者**读 → **4 红**；
    只有裸授予吃加成（`ShieldBuff` 不盖）→ **1 红**；`SHIELD` op 不传提供者 → **1 红**；
    `SkillExecutor` 不传提供者 → **1 红**（第一轮是 **0 红** —— 说明那条路当时没人测，见下）。
  - 🚧 **仍缺两件**（都登记着）：① 「我方目标持有**装备者提供的**护盾时…」这种**逐目标**的条件（`M-53`）；
    ② 「同一单位的两条规则改同一个属性时是**刷新**而不是相加」（`M-54`，实测 0.4 与 0.15 两条规则给同一个队友
    → 结果是 **0.15** 而不是 0.55）—— 128 的 4 件套因此把 12% 写成"累计 22%"，理由在那个文件的 note 里。
- `CanHit.lastShieldAbsorbed`：上一次 `takeDamage` 被盾吸走的量。
  ⚠ 它在 `takeDamage` 里**必须在任何 `return` 之前赋值** —— 盾把伤害全吃掉时会提前 return，
  漏掉就会让调用方读到上一次的陈旧值（实测过一次：返回伤害正好翻倍）。
- `Battle.applyDamage` 的返回值 = **被盾吸走的 + 真的掉的血**。
  为什么不是"直接返回乘区后的伤害"：目标是"打在有盾的目标上不能显示成 0"
  （否则 `AttackEvent.totalDamage` 与"本次攻击总伤害 × %"这类效果会失真）。
  ⚠ 恒等式：`乘区后的伤害 == shieldAbsorbed + hpLoss`（盾先吃、吃完才扣血），
  所以**不能**把"乘区后的伤害"与盾吸收量相加（会正好翻倍）。
- ✅ **"护盾量提高"属性已经存在**（`AttributeType.SHIELD_BOOST`，2026-09-28）—— 见上面那一段。

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

✅ **队伍是真的**（P8-5 起）：默认队伍来自 `StageFactory.realTeam()` ——
4 个 `CharacterFactory` 造的真角色（景元 1204 / 希儿 1102 / 克拉拉 1107 / 娜塔莎 1105，
覆盖 4 个不同命途），每人带一条**本命途**的光锥。

选锥规则是"**稀有度最高，同稀有度取 id 最小**"：只看 id 会选中 3★ 新手锥，对 80 级队伍很怪。
⚠ 只吃光锥的**面板**，它的被动不生效（那要 P10-3 的 buff 系统）。
⚠ **没有遗器**：没有可用的遗器实例数据，所以队伍裸装上场。

> ⚠ **光锥必须在 `Character.Builder` 上装，不能建完再 `setWeapon`**：
> `Calculator` 在 `build()` 里把光锥当**输入**消费，所以对已建好的角色 `setWeapon(...)`
> 只改字段、**面板不重算** —— 锥"装上了"却一点属性都没加，而且**运行时不报任何异常**。
> 用 `CharacterFactory.create(cid, level, promoted, weapon)`（装配点）。
> `Character.weapon` 的字段注释里也写了这条，因为 `setWeapon` 全项目零调用者、纯属陷阱。

（历史：P7-5 时还没有 `CharacterFactory`，默认队曾是 `temporaryTeam()` 的 3 个
`fromAttributes` 占位角色 —— 没有元素/命途/光锥/真实技能。该入口已在 P8-5 删除，
并有测试断言 `StageFactory` 不再暴露任何 temporary 方法。）

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

注意**行迹是无条件应用的**（`build()` 里调 `SkillTrace.appendTo`），所以面板不等于
`数据 × 倍率`。这一点很容易误判成"属性索引错位"（P8-1 时我就误诊了一轮）。

⚠ `Character.fromAttributes(...)`（测试/占位入口，P8 后新代码禁止使用）造的技能
是写死的 `DefaultSkill(1001, 1, 1)`——**六个槽位全是槽位 1 的普攻**。这是刻意的：
它没有 `cid`，查不到真实技能数据；真实角色一律走
`CharacterFactory.create(cid, level)`（槽位映射见 §7.2）。
本类**不负责**填技能倍率；天赋触发的追加攻击走 §4.7 的触发器表，
敌方阵营的召唤物归 P9-4（§24，已落地），敌人侧的追加攻击仍在 P9。

---

## 23. 层数资源：角色没有能量条怎么办 ✅（P8-8）

> 放在文末而不是插在 §9 能量系统之后：本文的 §6–§22 被多处交叉引用（约 25 处），
> 插一节会让后面每个编号和引用全部移位。编号连续不是重点，**引用能对上才是**。

游戏里有一类角色**不攒能量，攒层数**（飞霄【飞黄】/ 黄泉【残梦】/ 白厄【火种】/
昔涟【追忆】/ 遐蝶【新蕊】）。他们的终结技不是"能量满了"，而是"**层数到了**"。
本节的机制让这类角色**不需要专用类**。

### 23.1 三个部件

| 部件 | 作用 |
|---|---|
| `models.Resource` | 一个**有界**的计数器：`max` / `min=0` / `maxOverflow`；`gainClamped`（不溢出）/ `gain`（显式溢出）/ `spend`（能扣多少扣多少）/ `spendExactly`（不够就一点都不扣） |
| `models.ResourceManager` | 一个单位**拥有**的全部资源，按 id 索引。挂在 `CanHit.resources` 上（**永不为 null**） |
| `EnergyProvider.canCastUltra(user, cost)` | **开大闸门**：默认还是"能量够不够"，层数角色换成"层数满没满" |

`Resource` 在 P8-4 就被抽出来了（战技点是它的第一个用户）；P8-8 补的是
**每个单位一个 manager**、**"满了"信号**、两个触发器 op、以及**闸门挂钩**。

### 23.2 闸门为什么要挂在 `EnergyProvider` 上

⚠ 这是本项最关键的一处。改之前 `Battle.isUltraReady` 是：

```java
if (user == null || !user.hasEnergyBar()) return false;      // 没有能量条 ⇒ 永远放不出
return user.getCurrentEnergy() >= ultraEnergyCost(user);
```

而层数角色的**能量恒为 0**（`NoConventionalEnergyProvider` 五个钩子全返回 null）——
所以旧逻辑会把他们**永久锁死**。现在：

```java
return user.getEnergyProvider().canCastUltra(user, ultraEnergyCost(user));
```

默认实现里**仍然是**老规则（有能量条 + 够阈值），所以常规角色行为**一字不变**；
层数角色换成自己的 provider，读自己的资源。`Battle` 依旧不认识任何角色（P8-0）。

> ⚠ 闸门拿到的是**阈值**而不是"满不满"：5 个角色的开大阈值低于上限
> （云璃 120/240、银枝 90/180、绯英 240/480、飞霄 6/12、昔涟 12/24，见 §9.4），
> 所以传进去的是 `ultraEnergyCost(user)` 的结果，而不是 `maxEnergy`。

### 23.3 "满了"信号：**上升沿**，不是电平

`Resource.setOnBecameFull` 只在"**这次入账让它从不满变成满**"时触发一次。两个条件都要满足：

- 之前**不满**、现在**满** —— 所以"已经满了还继续加"不会重复触发；
- 这次**真的入账了**（`gained > 0`）—— 被完全截掉的入账不是"到达上限"，而是"早就在那、什么也没发生"。

为什么不能用电平：昔涟的池子是 24、**可以继续溢出存到 27**（§9.5）。若按电平，
她到达 24 之后的每一次溢出入账都会再触发一次"满了"，而"到达上限"只发生了一次。

### 23.4 溢出与 `scope`

- **溢出默认关**：`maxOverflow = 0` 时 `gain` 与 `gainClamped` 完全等价。
  要溢出必须**显式** `setMaxOverflow`（昔涟 24 → 27）。
- **不变式** `value ∈ [0, max + maxOverflow]` **永远成立**：下调额度会把越界存量夹掉。
- `ResourceScope.SELF`（每人一份）**已接**；`ResourceScope.PARTY`（全队共享）**被显式拒绝** ——
  共享池需要一个比单个角色活得久的持有者（per-battle 注册表），挂在每个角色身上会得到
  **四份各自独立的计数器**，而游戏里只有**一个共享池**。这种错**运行时不报任何异常**，
  所以宁可在注册时就响亮拒绝。

### 23.5 三个来源

| 来源 | 怎么走 |
|---|---|
| 事件触发获得 | 触发器 op `GAIN_RESOURCE`（`resource` + `amount`，可用 `per_target`） |
| 主动消耗 | 触发器 op `SPEND_RESOURCE` |
| 损血 / 治疗转化 | 触发器订阅 `HP_LOST` / `HEALED` 事件 |

> ⚠ **`SPEND_RESOURCE` 不够时会抛异常**，不静默失败。理由：战技点不够是"玩家按不动按钮"
> （合法状态），而触发规则里的消耗是**作者写了就认为层数会在那** —— 静默吃掉会让内容 bug 隐形。

### 23.6 两处已知限制（都写成了测试，不是藏起来）

1. **效果的 amount 是字面量，没有算术**。所以"每损失 1 点生命 +1 层"这种**按事件数量缩放**
   的规则目前表达不出来：触发器能可靠表达的是"**发生了一次**损血 → 给 1 层"，
   而"给 `lost` 层"需要在 effects 里做算术。
   测试因此拆成两半：`ResourceTest.hpLossReachesTheOwnersTriggerTable`（接线对不对）
   与 `oneStackPerPointOfLossIsAManagerCall`（1:1 的算术本身，属 manager 层）。
2. **条件 DSL 不能表达"只有我自己受伤"**。`self` 比的是 **actor**（谁造成事件）与表的持有者，
   而 `HP_LOST` 是**全队事件**（受击者 + 每个我方成员都会收到，见 §4.1），
   所以规则分不清"我被打"和"队友被打" —— 这对需要全队损血的遐蝶是对的，
   对个人层数角色是错的。要修得给条件 DSL 加一个 `target`（事件主体）变量。
   现状由 `ResourceTest.subjectFilterIsNotExpressibleYet` 钉住。

---

## 24. 召唤物：两个阵营 + 忆灵 ✅（P9-4，2026-09-27 起，2026-09-28 补忆灵攻击）

> **§24.1~24.3 讲"敌方阵营的召唤物"**：把 `monster_config.json` 的 `summon_id` 变成一个真的站在场上、
> 能被打、会死的单位。**§24.4~24.8 是我方阵营与忆灵**：阵营拆分、面板继承、在场条件、规则召唤、**攻击**。

### 24.1 三段接线

```
数据   monster_config.json 的 summon_id（列表）──Constant.normalizeMonsterConfigs──▶ 只保留正数
              │
装配   EnemyFactory.resolve ──┬─▶ EnemyFactory.create    → Enemy（+ 韧性/弱点/抗性/阶段表 + 名单）
                              └─▶ SummonFactory.create    → Summon（只有面板 + 技能）
              │
上场   Battle.summon(master, summonId, hardLevelGroup)
              │        ├─ enemies.add
              │        ├─ addRequestItems.add  ← 与波次同一扇门：从"当前"行动值起算，不重开一轮
              │        └─ setSpeedChangeListener ← 构造期只给开场名单接了，后到的单位要自己接
              │
退场   Battle.removeDeadCombatants()：perishOrphanedSummons() → 死者移出 Queue → checkResult()
```

- **名单是数据，不是触发器**。`summon_id` 只回答"这只怪能召唤谁"，**不回答"什么时候召"**；
  什么时候由调用方决定（`Battle.summon`）。这与 `Enemy.phases` 是同一个分工：
  "多强"和"放哪个技能"分开写。692/2649 只怪有非空名单（1450 条引用、556 个不同怪），
  例如银鬃尉官 1003010 → 银鬃近卫 1002040 ×2。名单**保留顺序与重复**（它是名单不是集合）。
- ⚠ **`[0]` 是"没有"，而且真的在数据里**（405301004 的名单就是 `[0]`）。
  把 0 交给工厂会去查怪物 0 然后报错；而"跳过查不到的 id"更坏 —— 它会让一条数据错误
  与"这只怪没有召唤物"长得一模一样。所以约定在**装载期**（`normalizeMonsterConfigs`）一次性执行，
  `SummonTest.theZeroEntryMeansNoSummonAndIsDroppedAtLoad` 钉住选了哪个约定。
- **面板走同一条缩放链**。`EnemyFactory.resolve` 是共享的那一半（查配置 / 查模板 / 查等级组 / 缩放），
  `SummonFactory` 复用它，所以召唤物的 HP/ATK/DEF/SPD 与"同 id 的怪"逐位相同
  （`theSummonScalesByTheSameRulesAsAMonsterWithThatId`）—— 两条路各抄一份缩放规则是这类代码
  最典型的腐烂方式。
- **它是 `Summon`，不是 `Enemy`**，所以**没有**怪物专属机制：无韧性条（不可击破）、无弱点表、
  无分元素抗性、无具体负面抵抗、无阶段表。这正是 L-8 的分工（见 §6.2 的注）：需要这些机制的代码
  显式走 `enemyUnits()`，因此放宽之后**没有任何地方静默跳过召唤物**。

### 24.2 生命周期：召唤物活不过主人

- `Summon.master` 是指向主人的**单向**指针，理由是可从召唤物自己回答"我属于谁"，不需要一张要保持同步的注册表。
- `perishOrphanedSummons()` 跑在 `removeDeadCombatants()` **最前面**，所以紧接着的清扫会在同一趟里
  把它移出行动条 —— 它停止行动、不再被选为目标、不再算"还活着"（`checkResult` 因此能看到 WIN），
  全程走 `isDeath()` 这一条既有通道，**不引入第二种"还在不在"的判据**。
- **主人死是唯一信号**：判的是 `master.isDeath()`，不是"主人不在 `enemies` 里" ——
  因为死者本来就会留在名单里（§6.2），`isDeath()` 是引擎里每条移除路径都会置的那一个事实。
- 退场**不是击杀**：这次清扫跑在 `Battle.applyDamage`（唯一结算入口）**之外**，而 `HpLoss`/`Kill`
  只在那个入口里发 —— 否则每一个"消灭敌人"的天赋都会为一个**没人杀死的**单位付钱。
  这也是 `perish()` 与 `takeDamage` 唯一真正可区分的地方：**HP**（见 §6.2 那条被打错的红字）。

### 24.3 三处刻意留下的边界

1. **两个阵营都能召唤了**（2026-09-27 补齐友方那一半）：召唤物加入**主人自己的阵营** —— 敌方的进
   `enemies`，我方的进 `allies`。于是「我们打谁」看 `enemies`（我方召唤物**不是**我方的合法目标），
   而「敌方打谁」看 `allies`（会被打、也吃我方全体增益）。`Camp.NEUTRAL` **响亮拒绝**：它没有自己的
   名单，替它挑一个等于悄悄替它决定了立场。
   > 这条原先写的是"只做敌方阵营，我方主人响亮拒绝"，理由是当时我们唯一的名单是 `List<Character>`、
   > 放不下 `Summon`。现在有了 `allies`（§6.2 的 L-8 友方那一半），剩下的拒绝对象只有"没有名单的阵营"。
2. **等级组是参数，不是推断的**。怪物自己的 `hard_level_group` 几乎总是 1，真正决定难度的是关卡，
   所以没有可推断的来源；猜一个（见 ROADMAP §5 教训 3：猜出来的 `null` 让策略静默变空操作）
   比要求调用方传一个参数坏得多。等级取自主人（它是这一场战斗的等级，主人身上就有）。
3. **`SkillEffectType.SUMMON` 仍未分派**（§7.2b 的表里还是 ❌）：数据里没有"这次召唤召谁"这一列，
   所以"敌方技能召唤"还差内容，而不是差能力 —— 能力就是 `Battle.summon`。
   ~~**忆灵仍未做**~~ → **忆灵已完整**：面板（§24.5）、在场（§24.6）、召上场（§24.7）、**能打**（§24.8）；
   仍缺的是**连携攻击**（"一次行动中的两次独立攻击"）与**指令忆灵行动**，见 §18.4。

**验收**：`SummonTest` **18 条** —— 名单随数据落到主人身上（顺序 + 重复）、无名单为空、
`[0]` 在装载期被丢掉、面板与同 id 的怪逐位相同、自带名字与技能、每次调用给独立实例、
战斗中入场（阵营 + 行动条 + 当前时钟 + 引擎目标表包含它 + **速度变化仍会重排**）、被打死只减自己、
主人倒下带走它（含"还没入场就被带走"、**且它的 HP 一点没掉**）、
**随主人消失不付击杀奖励**（与"真杀死会付钱"对照）、**阵营取自主人**（同一调用分别落进两个名单），
以及三种拒绝（已死主人 / 未知 id / null 主人）。友方一侧另见 §24.4。

**变异验证（10 处）**：去掉 `[0]` 过滤 → 只红 `theZeroEntryMeansNoSummonAndIsDroppedAtLoad`；
名单去重 / 不落名单 → 各红 `theRosterTravelsFromTheDataOntoTheMaster`；
摘掉清扫调用 / 把清扫挪到队列清扫**之后** → 分别红"主人倒下"那条（2 条 / 3 条不等）；
摘掉我方阵营判断 / 已死主人判断 / null 主人判断 / 速度监听接线 → 各红对应一条。
⚠ **唯一没被抓住的是"把 `perish()` 换成 `takeDamage(maxHp)`"**，而那暴露了我一个**错判**
（见 §6.2 的红字）：`CanHit.takeDamage` 不发事件。补上"HP 不变"这条断言后该变异体才变红 ——
所以 `theMasterFallingTakesItsSummonWithIt` 里那句 HP 断言不是装饰，它是这个方法存在的理由。

全套 **87 套 / 780 例全绿**；demo `victory (10 rounds / 43 actions)` 与 `mechanics` demo 输出不变。

### 24.4 我方阵营：`allies` 与 `characters` ✅（2026-09-27，L-8 的友方那一半）

```
Battle.allies      = 我方**阵营**（List<CanHit>）：我们的角色 + 我方召唤物
Battle.characters  = 我们的**角色**（List<Character>）：allies 的子集视图
Battle.enemies     = 敌方**阵营**（List<CanHit>）
Battle.enemyUnits()= 其中的**怪**（List<Enemy>）
```

⚠ 命名不对称是**故意的**：敌方那侧是 `enemies`（阵营）/ `enemyUnits()`（子集），我方这侧却是
`characters`（子集）/ `allies`（阵营），因为 `characters` 被读 **179 处**、其中绝大多数问的是
"我们的角色"（技能 / 能量 / 遗器规则）。把它改名放宽成阵营、再加一个子集视图（与敌方完全对称的那条路）
能让**编译器**替我们找出每一处旧假设，代价是 179 处改动换零行为变化 —— 所以这里选了"给阵营一个新名字"，
把真正的**阵营级**读取点逐个搬过去。

> 🔴 **这个选择的代价必须说清楚，并且用测试补上**：写在 `characters` 上的阵营级读取点**不会编译失败**，
> 它会**静默忽略我方召唤物** —— 敌方 AOE 打不到它、我方全体增益漏掉它、胜负判定看不见它。
> 所以每一个阵营级读取点都在 `PlayerSideSummonTest` 里有**对应的一条**（阵营划分 / 我方不能打它 /
> 它有自己的行动条 / 敌方看得到它 / 敌方 AOE 打得到它 / `all_allies` 增益给到它 / 事件广播给到它 /
> 主人倒下带走它 / 全灭判负 / 它不挡胜利 / 中立阵营被拒），**这组用例就是编译器欠下的那部分类型安全**。
> 以后新增阵营级读取点，应该往这个类里加一条。

**刻意留在 `characters` 上的三处**（它们要的是"角色才有"的东西，不是"我方"）：
`attachBattleSkills`（地图技能，`Summon` 没有）、`fireTriggers`（只有 `Character` 带触发器表）、
以及技能点事件的两条广播（`Summon` 既不持有战技点也没有表；读阵营只是为了与"我方全员"的口径一致）。

**同一批改动里换掉的其它阵营级读取点**：`checkResult`（全灭判定）、`getOpponents`
（敌方 AI / 选目标的入口）、`EnemySkill.struckBy`（敌方 AOE / 扩散扫我方）、
`SkillExecutor` 的两条广播（`SkillCastEvent` / `AttackEvent` 的"我方全员"）、
`TriggerInterpreter` 的 `all_allies` 目标解析、`Battle.printHp`（显示）、
以及 `perishOrphanedSummons`（**两个阵营都要扫** —— 只扫 `enemies` 会让我方小怪在主人死后继续站着）。

**验收**：`PlayerSideSummonTest` **12 条**（上面清单里的每一条）。**变异验证（7 处）**：把 `getOpponents` / `EnemySkill.struckBy` / `all_allies` 解析 / `dispatch` /
`SkillExecutor` 的技能广播 / `checkResult` 各自改回 `characters`，以及让清扫**只扫敌方** ——
各红对应一条；全套 90 套 / 818 例全绿；demo 全文对比 281 行逐行一致（只有 3 行 `Resist {...}`
的 Set 顺序是既有不稳定），说明"场上没有召唤物时 `allies` 与 `characters` 完全等价"。
⚠ `checkResult` 那条**必须用无主的召唤物**才能区分两种读法（有主的会在主人倒下时一起消失），
第一版用了有主的、变异不红 —— 这与 `perish()` 那次是同一类教训：**能区分的行为才算断言**。

### 24.5 忆灵面板：从召唤者**继承** ✅（2026-09-27）

> 文档对每一个忆灵的面板都是**同一种写法**，而且比例因角色而异 —— 所以面板是**数据**，
> 而且写的是"怎么推导"，不是数值：
>
> | 召唤者 | 忆灵 | 速度 | 生命上限 |
> |---|---|---|---|
> | 1402 阿格莱雅 | 衣匠 | 35% of 她自己 | 66% of 她 + **720** |
> | 1413 长夜月 | 「长夜」 | **160 点**（平的） | 50% of 她 |
> | 1512 知更鸟·晴歌 | 晴空乐手 | 180% of 她 | 70% of 她 |
> | 8007/8008 开拓者 | 迷迷 | **130 点** | 80% of 她 + **640** |
> | 1204 景元 | 【神君】 | **60 点** | 文档未给 → 暂不建 |
> | 1409 风堇 / 1415 昔涟 | 小伊卡 / 德谬歌 | 文档未给 → 暂不建 | 50% / 100% of 她 |
> | 1407 遐蝶 | 死龙 | 165 点 | 100% of **【新蕊】上限**（是资源不是属性）→ 暂不建 |

**数据**（`resources/memosprites/<召唤者 cid>.json`）：
```json
{ "name": "衣匠",
  "source": "1402 阿格莱雅 忆灵天赋: 「…」 param = [0.35, 0.66, 720]",
  "panel": [ { "attribute": "HEALTH", "percent": 0.66, "flat": 720 },
             { "attribute": "SPEED",  "percent": 0.35 } ] }
```
同一个文件还带一个**可选**的 `aggro`（仇恨，2026-09-28）：忆灵的 servant 块自带一行「ServantID 11413 · 仇恨: **125**」，
而 `Battle.aggroOf` 此前对所有非角色一律返回兜底 100 —— 于是敌人把忆灵当成普通单位来选目标，而游戏里它**更吸打 25%**。
⚠ 不写 ≠ 写 100：不写走兜底，写 0 或负数在装载期被拒（那会让忆灵在仇恨表里占 0 份额，敌人**永远不会**选它）。

同一个文件还带一个**可选**的 `attack` 块（忆灵技，§24.8）：
```json
{ "name": "长夜", "source": "…", "panel": [ … ],
  "attack": { "element": "Ice", "base": "HEALTH", "percent": 0.5, "shape": "SingleAttack" } }
```
`base` 必须是**面板确实给了**的属性之一（否则它是 0、每一下都打 0 —— 装载期就拒），
`element` / `shape` 用数据里已有的拼写（`Ice` / `AoEAttack`），`hits` 省略即 1 段。
- **一条 entry 的公式**：`值 = percent × 召唤者该属性 + flat`，两项都可省 —— 同一个形状因此覆盖了
  "35% 的速度"（只有 `percent`）、"160 点速度"（只有 `flat`）、"66% + 720"（两者都有）；
- **文件名是召唤者而不是忆灵**：比例是"相对召唤者"的，用忆灵命名就得在文件里重复主人一次，两边还可能对不上；
- **读的是召唤者**已解析**的面板**（`getAttribute(...)`）—— 光锥 / 遗器 / 行迹 / buff 都已经算进去，
  忆灵因此自动跟着走，这条性质有专门的用例（换遗器 + 改速度，忆灵面板随之变化）；
- ⚠ **一条 entry 是"替换"而不是"叠加"**：没提到的属性保持 `AttributeBuilder` 的默认 0。
  所以 `HEALTH` 与 `SPEED` 是**必填**的，装载期直接拒绝并说明后果（0 血上限会被一次 tick 打死；
  0 速**排不进行动条**）；属性名走 `AttributeType`，`*_PERCENT` 那四个仍在装载期拒绝；
  同一属性写两次、只写 `percent` 却非正、`flat` 为负、没有值、名字为空 —— 都在装载期报错。
- 工厂是 `SummonFactory.memosprite(master)`，与怪物那条**同一个类**（"造 `Summon` 的地方只有一处"），
  但读的是**两套数据**：怪物走 `monster_config` + 等级组，忆灵走主人自己的面板。
  另有一个 `memosprite(master, spec)` 重载作为**可测的接缝**（比例属性的那条分支目前没有出货数据用到，
  靠它才能被测到，而不是为了测它去编一个忆灵）。

**上车点是 `Battle.summonMemosprite(Character)`**：进 `allies`、走 `addRequestItems` 入行动条、
接速度监听、`master` 指向召唤者（所以主人倒下时由 §24.2 的清扫带走）。
**同一召唤者只会有一个**：已经有一个活着的忆灵时这个调用**什么都不做**并返回那一个 ——
文档其实写的是「若已在场，则使其生命值回复至上限」，**刷新**没有建模，而"什么都不做"至少不会造出
一个**状态错误**（两个单位、玩家只看见一个）；刷新已登记为下一步。召唤者已死 / 没有 spec 的文件
都**响亮拒绝**（后者直接给出该写哪个文件）。

**刻意留下的缺口**（都不是"忘了"，是数据里真的没有）：
1. ~~**忆灵技与行动**~~ 🚧 **攻击那一半已做**（§24.8）：技能表不在生成数据里，所以它写在
   `memosprites/<cid>.json` 的 `attack` 块里（面板与攻击同一个文件、同一个 `source`）；
   仍缺忆灵技 2~5（增伤 / 被召唤立即行动 / 离场加速 / 忆质缩放的全体攻击）与"指令忆灵行动"
   （长夜月终结技的 200% 全体是她**指挥**忆灵打的）；
2. ~~**在场条件**「忆灵在场时」~~ ✅ **已做**（§24.6）：`self_summon_count >= 1` 问得出、
   `"target": "summon"` 指得到，「装备者及其忆灵」的标准写法是两条规则。这一族里
   **318 与 123 已整条写掉**（123 还差"忆灵攻击时"那一半，2026-09-28 补上：`SUMMON_ATTACK` + `actor == summon`）、
   **124/319/320 变成可写**（登记为 `Writable now:`），
   剩下 321/323 各自卡在别处（队伍人数 / "while" 时长）—— **127 已整条写掉**（它的最后一块是
   「持续至…普攻**或**战技后」这个**析取时长**，M-38 关闭后 `until` 收列表，六个 effect 现在都写两个事件）；
3. **ATTACK / DEFENCE / 抗性**：没有任何文档给出这些数，本数据也没有忆灵面板表 → 保持 0，
   后果是"忆灵吃满伤害"（见 ROADMAP 的登记）。**这正是 §24.8 让攻击按 `HEALTH` 缩放的理由**：
   50% 乘一个面板**确实给了**的数，而不是猜一个面板没给的；
4. ~~**忆灵造成的伤害**~~ 🚧 **忆灵那一半已做**（§24.8）：`attack.base` 指定乘**它自己**哪条属性；
   §23.6 的"`EffectSpec.amount` 是字面量"在**召唤者属性**侧（「等同于召唤者攻击力 Y%」）仍未做，
   现在的写法是让面板把那条属性继承下来（如 66% × 召唤者攻击力），见 §24.8；
5. **刷新而不是无操作**（阿格莱雅的「若已在场，则回复至上限」）；
6. **遐蝶的死龙**：它的生命上限是【新蕊】上限的 100%，而【新蕊】是资源不是属性 ——
   要么把资源接进面板推导，要么单独处理。

**验收**：`MemospriteTest` **14 条** —— 面板 = 召唤者的份额 + 平项（两个断言分别钉 66% + 720 与 35%）、
跟着召唤者当前的装备走、平项不是任何东西的份额（长夜 160 且她速度变了也不动）、
比例属性按分数继承、进场进 `allies` 不进 `characters` 且有行动条信号、
重复召唤只有一个（死的可以换新）、主人倒下带走它、没有 spec 的角色被拒并给出文件路径、
已死召唤者被拒，以及装载期的 9 种拒绝 + `HEALTH`/`SPEED` 缺失各自带后果 + 零值被拒、
惰性 / 缓存 / 缺失即"没有"的契约，还有出货 spec 与其出处（`source` 里带 1402 / 0.66）。
**变异验证（9 处）**：丢掉平项 / 忽略份额 / 忽略平项 / 不要求 `HEALTH` / 允许重复属性 /
不写缓存 / 去掉幂等 / 把死忆灵也算"在场" / 进错阵营 —— 各红对应条目。

### 24.6 「在场」问得出来、也指得到 ✅（2026-09-27）

面板让忆灵**存在**，这一节让它**可被问到、可被指到** —— 这是那 8 条「忆灵在场时」族
（123/124/127/318/319/320/321/323）的最后一块地基：

| 工具 | 写法 | 例子 |
|---|---|---|
| 条件 | `self_summon_count >= 1` | 「装备者的忆灵在场时」「当存在装备者召唤的目标时」 |
| 目标 | `"target": "summon"` | 「装备者**及其忆灵**」的第二半 |

- 计数是**主人自己的、活着的**召唤物（`Battle.summonCountOf`），走**主人所在阵营**的名单，
  所以敌方 Boss 的小怪也算（同一个查询）。**不只看第一个**：知更鸟·晴歌的「晴空乐手」是三个。
- 上下文因此带上了 `Battle`（§4.6 那条红字）；**没有战场就是不成立**，不是 0。
- **首个用户：奇想蕉乐园 318 的 2 件套**（「暴击伤害提高 16%，当存在装备者召唤的目标时，暴击伤害额外
  提高 **32%**」）。⚠ 参数是 0.32 而不是 0.16（登记表里我原先按句子写成 16%，是错的）。
  ⚠ 为什么**不写 `permanent`**：「当…时」是 **while** 条件，永久 buff 会在召唤物死后继续留着 ——
  一个没人报错的错误状态。所以它写成 `TURN_START` + `actor == self` + 本条件 + **1 回合**：
  每到自己回合重新判定一次，而暴击伤害本来就只在自己出手时用得上；召唤物中途死了，buff 会在
  回合边界掉掉。**残留近似**：mid-turn 死掉的召唤物会保留到该回合结束。
- **同样的技巧对 323 不够**：「我方全体速度 +8%」是给**别人**的，只在装备者回合活着会让行动条来回抖 ——
  它需要"跟着条件活的时长"，仍登记着。这类"while 时长"缺口同时挡着 132 / 127 / 107。

**验收**：`SummonFieldTest` **10 条** —— 只数自己的、死的不算、多个都数、敌方也算、
条件真的门控、无战场则**不成立**（含 `== 0` 反向问法）、`target: summon` 打在忆灵身上而不是主人、
没有召唤物时**响亮报错并给出该加的条件**、318 的两条数字与条件数、318 端到端只在有召唤物时生效（**以差值
断言**，因为她的面板本来就有暴击伤害）。
**变异验证（8 处）**：无战场读成 0 / 计数不看主人 / 计数含尸体 / 只数第一个 / 阵营恒取我方 /
找不到召唤物时回退到主人 / 318 丢掉条件 / 318 改数字 —— 各红对应条目。
⚠ 其中两条**第一版没抓住**：一条是"回退到主人"的变异我写在了一个**不可达**的分支上（等价代码），
另一条暴露出我的变异脚本把**编译失败**当成了"测试通过"（`cmd` 下 gradle 退出码不可靠，改成看 `BUILD FAILED` 文本）。
两次都是**变异验证本身**要修，不是被测代码。

### 24.7 让**规则**把忆灵召上场：`SUMMON` ✅（2026-09-27）

面板（§24.5）让它存在、条件（§24.6）让它可问可指，但**内容还是写不出"召唤"这句话**。
`SUMMON` 就是那句话，无参数、作用于规则主人：

| 角色 | 原文 | 规则 |
|---|---|---|
| **1413 长夜月** | 「进入战斗时召唤忆灵「长夜」。」 | `BATTLE_START → SUMMON` |
| **1402 阿格莱雅** | 终结技「召唤忆灵衣匠，若衣匠已在场，则使其生命值回复至上限。」 | `ULT_CAST` + `actor == self` → `SUMMON` |

- **不收 `target`**（多写就报错而不是忽略）：忆灵属于召唤者、替它那一方打，没有可指的对象；
- **按召唤者幂等**：第二次触发保留已在场的那一个 —— 两个同样的忆灵是**玩家看不见的错误状态**。
  文档说的刷新（「若已在场，则使其生命值回复至上限」）**没有**建模，无操作是诚实替身，已登记；
- ⚠ **用了 `SUMMON` 却没有忆灵文件的角色，在装配时就被拒**（`CharacterFactory.requireSummonable`），
  不是战斗中途。为什么检查点必须在装配处：**规则文件不知道自己属于谁**（`TriggerTable` 只从文件编译），
  而**遗器规则是每个穿戴者共享的** —— 装配点是第一个同时知道 cid 与"最终合并出哪些规则"的地方。
  顺带把它写成**一个出口**：第一版写在两个 return 上，变异验证立刻显示"不穿遗器那条分支"没有任何用例覆盖。

**验收**：`SummonOpTest` **9 条** —— 1413 的 `BATTLE_START` 真的把它带出来且面板来自自己的 spec、
她的文件就是那条规则、1402 的大招召唤而队友的大招不召唤、第二次触发还是同一个、
`target` 被拒、`turns` 被拒、没有 spec 的角色在装配时被拒（含"同一条表在 1413 上没问题"的对照）、
**装遗器**的路径也被拒（用测试资源里的一条遗器规则，见下）、手工挂表时运行时也响亮报错。
⚠ 那个装配用例是**遗器**夹具而不是角色夹具：`SkillSlotMappingTest` / `UltraThresholdTest` 会**遍历全部
角色**，所以给任何"数据里存在的 cid"加角色夹具都会打挂它们（试过）；遗器夹具选了 103 的 2 件套
（纯数值、无具名 ability），因此也**不会**扰动 §4.6 那套分区测试。
**变异验证（7 处）**：op 变空操作 / 重新允许 `target` / 重新允许 `turns` / `usesOp` 恒 false /
装配不检查 / 1402 丢掉 `actor == self` / 1402 听错事件 —— 各红对应条目。

### 24.8 忆灵能打了：伤害按**它自己**的属性缩放 ✅（2026-09-28）

面板（§24.5）让它有血有条、`SUMMON`（§24.7）把它召上场，但上场之后它**不会做出有意义的行为** ——
因为**数据里根本没有忆灵技能表**：`skills.json` 里只有召唤者那条天赋（141304），`11413*` 这类
servant 技能 id **一个都没有**（逐个搜过 `skills` / `monster_config` / `enhanced_skills` / `skill_effects`）。
文档里忆灵技是有的（忆灵技 1~5），所以它和面板一样属于**手写内容**，写在同一个文件里。

```jsonc
// resources/memosprites/1413.json —— 面板 + 它自己的攻击
"attack": { "element": "Ice", "base": "HEALTH", "percent": 0.5, "shape": "SingleAttack" }
```

- **为什么 `base` 是它自己的属性**：1413 忆灵技1 = 「对敌方单体造成等同于「长夜」50%生命上限的冰属性伤害」，
  而面板给「长夜」的是长夜月**一半**的生命上限 → 把"50%"读成召唤者的生命上限会打出**两倍**伤害，
  一个看起来完全合理、只有对数才知道错的数。⚠ 这也是为什么"没人会写出这个数"不能当理由。
- **为什么复用 `EnemySkill` 而不是新写一个类**：它要的正是那里的东西 —— 直接伤害（不进角色倍率表）、
  形状分派、逐段独立结算；唯一被写死的假设是"倍率乘 ATK"，而那一行现在读 `baseAttribute`
  （默认 `ATTACK`，所以敌人逐位不变）。顺带抓出一个**既有 bug**：形状分派写死扫 `battle.allies`
  （诞生时只有敌人用它），我方忆灵一次 AOE 会打**自己人**、怪物一点不掉；现在按**施法者的对立阵营**
  选边，见 §19.4。
- **谁驱动忆灵的回合**：引擎**不规定**。行动条照常排它（`Summon` 是 `CanHit`）、`Battle.performAction`
  照常施放，**打谁由调用方决定**。`Main.summonTurn` 是 demo 的策略：打对立阵营、主目标用仇恨加权选。
  文档的策略（忆灵技1「优先攻击长夜月上次攻击的敌方目标」）**未建模**、已登记。
- ⚠ `Main.step` 从**两个分支变三个**：此前只有 `Enemy` / `Character`，忆灵当回合主时会
  `ClassCastException`。scene 3 的敌方召唤物只是**没轮到它**才没暴露。
- 伤害在**每次结算时**读属性（`strike` 里现读），所以中途落在忆灵身上的 buff 会反映；
  面板决定它**初始**是多少，不决定它一直是多少。
- **它这一次攻击会被告知给我方**（M-30 的另一半）：`EnemySkill.execute` 把全部段结算完之后调
  `Battle.fireAfterAttack` —— 与角色技能走**同一个出口**（原先那段广播是 `SkillExecutor` 的私有方法，
  现在搬到 `Battle` 上，因为"一次攻击结束了"这件事对两者都成立）。后果是**挂在忆灵自己身上的
  `until` buff 会被它自己的攻击吃掉**（此前谁都不发这个通知，buff 就一直在），而**召唤者身上的不会被吃**
  —— 听众各自判 `attacker == owner`，忆灵挥一下不是它的召唤者在挥。⚠ 敌人的攻击同样投给我方
  （攻击者是谁都投），我们这边没人会动它，那是听众自己的判定。
- **装载期拒绝**（`Memosprites.validateAttack`）：元素拼错、`base` 面板**根本没给**（面板 entry 是**替换**
  不是叠加 → 值是 0，攻击照打、日志照印、每一下都是 0 —— 最该在装载期拦的就是这条）、`base` 写成
  `*_PERCENT`（builder 专用键，运行时槽是 null）、`percent` 非正、`hits` < 1、`shape` 不是伤害类。
  spec 接缝（`SummonFactory.memosprite(master, spec)`）**也**校验一次，否则测试最好用的那个入口能绕过全部检查。

**仍缺**（都登记）：**衣匠的攻击**（1402 的忆灵技能1 是扩散/雷/「110% 攻击力」，但文档**没有给它任何攻击力** ——
面板只列了生命与速度；按阿格莱雅的攻击力算就是猜，而装载期那道防线本来就会拒绝「乘一条面板没给的属性」，
所以这里没有悄悄写错的余地，缺的是数据 → `M-36`），忆灵技 **2~5**（在场全体增伤 50% / 「被召唤时使自身立即行动」/ 离场加速 /
按【忆质】缩放的全体攻击）、~~攻击**削韧**~~ ✅ **已做（2026-09-28）**：`MemospriteSpec.attack.stance`（忆灵技1 「破韧值 单体 30」）→ `EnemySkill.stanceDamage` → 每段命中后 `Battle.reduceToughness`；默认 0，所以**敌人那条路逐位不变**。⚠ 削韧只在**打中弱点**时发生（引擎既有规则），所以用例要像 demo scene 1 那样显式改写目标弱点集。~~**指令攻击**的削韧~~ ✅ **不需要新参数**（2026-09-27 实测 + M-40 修完后依然成立，见 §24.10）：那 90 写在 **141303 自己的 `stance_list.all`** 里，由**真正的挥击**削（`SkillData.stanceFor` 是形状→削韧的唯一入口，两个调用方共用它：执行器的 AOE/BLAST/BOUNCE 分支与 `COMMAND_SUMMON`），300 点条实测 **300 → 210**；op 若再收一个削韧参数就是第二份数字、会削两次。仍缺的是战技点（回复 20×0.5、消耗 -1）、
**仇恨 125**（现在是 `aggroOf` 的兜底 100）、以及**等级表** —— 引用的 50% 是文档行文所在的那一级（Lv6），
完整表是 0.25（Lv1）→ 0.70（Lv10），而忆灵技没有生成数据可读，所以它和 `EnemySkill` 的敌人倍率一样是
**单一数值、无等级**。还有**"指令忆灵行动"**：长夜月终结技那句「使忆灵「长夜」对敌方全体造成 200% 生命上限」
是**她**指挥忆灵打的，不是忆灵自己的回合 —— 两者不是一个功能。

**验收**：`MemospriteAttackTest` **13 条** —— 出货那条攻击的四个数字（元素/份额/基数/段数）与"基数不是
召唤者的"对照、`baseAttribute` 真的是 `HEALTH`、没写攻击的 spec 就**不装**技能（引擎不编一个文档没有的
ATK 兜底）、伤害落在怪物身上且我方**一点不掉**、伤害与**自己的**生命上限**成正比**（两套只差面板份额的
spec、同一随机种子 → 比值恰好 2，这样就不必在测试里重算乘区）、`hits` 逐段（3×20% == 1×60%，把暴击钉成 0
让总数精确）、AOE 打到**每一只**怪、8 条装载期拒绝、spec 接缝也校验、纯面板 spec 仍合法；
**外加攻击通知那 4 条**：忆灵自己的 `until` buff 被它自己的攻击吃掉、**召唤者的不会**、
敌人的攻击投过来我方什么都不掉、以及用测试内记录器钉住"**一次攻击只发一次**、带的是**实际命中的目标**与
**结算总和**"（载荷没有出厂听众，只能从外面看）。
**变异验证（6 处）**：忆灵攻击不发通知 / 载荷只带主目标 / 结算总额不累加 / 命中目标不记录 /
`AbstractBuff` 去掉 `attacker == owner`（红 3 条，含原有的"队友攻击不消耗"）/ `Battle.fireAfterAttack`
去掉"一个都没打到就不算攻击"的守卫 —— 各红对应条目，且**全部**还原（逐次 SHA-256 核对）。
（攻击本身另有一套 5 处变异：基数改回 `ATTACK` / 选边改回 `allies` / 不装技能 /
去掉"面板有没有这条属性"检查 / 去掉接缝校验。）

整套 **96 套 / 891 例全绿**；整场战斗 demo 全输出与上一提交逐行一致（仅 3 行 `Resist {}` 的 Set 顺序不同，
是老问题）；`mechanics` demo 加 **scene 4**：1413 长夜月按**自己的规则**召唤「长夜」、面板按 50% 生命上限
与 160 固定速度推出来，然后它自己那一回合打出 50% 自己生命上限的冰伤。

### 24.9 「装备者的忆灵攻击时」 = 数据 ✅（2026-09-28）

忆灵能打了（§24.8），但**数据碰不到那件事**：条件 DSL 只能把 `actor`/`target` 和 `self` 比，所以"**我的**忆灵"
写不出来；而规则侧的 `ALLY_ATTACK` 不为召唤物的攻击而发（它只从角色施放发出）。于是两半都补上：

| 半边 | 写法 | 为什么 |
|---|---|---|
| **条件** | `actor == summon` / `target == summon`（`summon == actor` 等价） | 身份比较的右边从此有两套词：`actor`/`target`（哪一方）× `self`/`summon`（谁）。刻意不收其余目标选择器：`all_allies`/`party` 不是一个单位、`attacker` 只能是 actor、`target == target` 是同义反复 |
| **事件** | `TriggerEvent.SUMMON_ATTACK` | 由 `EnemySkill.execute` 在全部段结算完后发一次，两个阵营都发。**不加宽 `ALLY_ATTACK`**：三条已出货规则（1309 / 1403「我方其他目标攻击后」/ 遗器 105）在用它，忆灵算不算那些「目标」文档没说 |

**两个容易写错的点，都钉住了**：
- **谁的召唤物**：事件投给每个角色的表，所以 `actor == summon` 不能省 —— 否则**队友的忆灵**攻击也会让装备者
  拿到效果（错触发，没有任何迹象）。这条用"两个角色各有一只忆灵"的用例钉住；
- **读不到战场就是不成立，`!=` 也一样**：与 `self_summon_count` 同一约定。否则"没有战场 → 不是我的召唤物"
  会让 `actor != summon` 对每个事件都为真。

⚠ **`summon` 是"任意一只"而不是"第一只"**：`Battle.summonsOf` 是这件事唯一的判据（`summonOf` 取首、
`summonCountOf` 数个数、条件问"在不在其中"），因为数据里迟早会有多召唤物的角色（知更鸟·晴歌的晴空乐手是三个，
文档里有）。两条路各自循环一遍"哪些是我的"，就是三个答案开始分叉的地方。变异验证里"只取第一只"这条**红了**
（`anyOfMySummonsCountsNotJustTheFirst` + 既有的 `severalSummonsAreAllCounted`）。

**首个用户：遗器 123 凯歌祝捷的英豪 4 件套**（「装备者的忆灵在场时，装备者速度 +6%；装备者的忆灵攻击时，
装备者和忆灵的暴击伤害 +30%，持续 2 回合」），两条规则：`TURN_START` + `self_summon_count >= 1` 的"while"
写法（§24.6 的技术）+ `SUMMON_ATTACK` + `actor == summon` 的双效果（`self` 与 `target: "summon"`）。
⚠ **参数 vs 句子，第三次**：登记理由照英文句子写成"3 turn(s)"，而 `param = [0.06, 0.3, **2**]` ——
作者得读 `param`（前两次是 305 的 0.6 与 318 的 32%）。

**验收**：`SummonConditionTest` **15 条** —— 谁的召唤物（队友的 / 敌方小怪的都不算）、`actor == self` 在这种事件上
永远不成立、`!=` 只排除自己那只、镜像写法等价、`target == summon` 问的是承受者、**任意一只**（两个召唤物）、
无战场时两种极性都不成立、4 条装载期拒绝（多余的选择器 / 未知名 / 两边都是名词）、真实忆灵 AOE 攻击**只发一次**且
`hit_count` = 命中数（单目标攻击不满足 `>= 2`）、以及出货 123 的两条规则数字（含 `turns: 2`）与三个行为
（双方都涨 30%、队友的忆灵不算、速度那半要忆灵在场）。**变异验证（7 处）**：`summon` 不再特殊处理 /
无战场守卫去掉 / 只取第一只召唤物 / 从词汇表里删掉 `summon` / 事件不发 / 命中数恒为 1 / `summonsOf` 不过滤死者
—— 各红对应条目，且**全部**还原（逐次 SHA-256 核对）。⚠ 其中"只取第一只"第一次跑出来是**编译失败**
（锚点同时命中了 `memospriteOf` 里的同一行），换唯一锚点后才真正红 —— 变异脚本必须把"编译失败"当成一种**独立结果**，
而不是当成"被抓住"。

### 24.10 指令忆灵行动：`COMMAND_SUMMON` ✅（2026-09-28）

忆灵能自己打了（§24.8）、规则也能对它的攻击做反应（§24.9），但**主人还不能叫它动手** —— 长夜月终结技那句
「召唤忆灵「长夜」，**随后使忆灵「长夜」对敌方全体造成**等同于「长夜」#1[i]%生命上限的冰属性伤害」正是这件事。
新 op `COMMAND_SUMMON`：

```jsonc
{ "op": "COMMAND_SUMMON", "skill": "ULTRA", "damage_param": 0, "damage_level": 10, "attribute": "HEALTH" }
```

**每个数从哪里来，就是这个设计本身**：

| 数 | 来源 | 为什么 |
|---|---|---|
| 倍率 2.0 | `skill: "ULTRA"` + `damage_param: 0` + `damage_level: 10` | 从 **141303 自己的参数表**读，所以不会和 `skills.json` 分叉；行号也要写（见下） |
| 元素 冰 / 形状 全体 | 同一条技能的 `skill_effect` / 元素 | 那三个数**文档本来就在技能数据里**，抄进规则就是第二份真相。⚠ **第一版我给这个 op 加了 `element`/`shape`/`percent` 三个字段**，写完才发现数据里都有 |
| 基数 = **忆灵**的生命上限 | `attribute: "HEALTH"`，读的是**召唤物**的属性 | 这是技能行**没有**说的那件事：不是长夜月的攻击力 |
| 谁动手 | 规则主人的**召唤物**；技能却是**主人的** | 「使忆灵造成…」= 用她的技能表、由忆灵挥出去。⚠ 第一版把技能查到召唤物身上，立刻报"长夜 has no ULTRA skill" —— 那句话正好把语义说清楚了 |
| 削韧 90 | **同一条技能的 `stance_list`**（141303 是 `single 0 / all 90`），由**这一下真正的挥击**削 | ✅ 2026-09-27 实测：300 点韧性条一次施放 **300 → 210**（正好 90），`SummonCommandTest.castingTheUltimateRemovesTheToughnessItsOwnSkillStates` 钉住。⚠ 削韧跟着**挥击**走：她这次施放是**委派**出去的（`DELEGATE_DAMAGE`，见下），所以执行器不展开伤害、也不削韧，这一列由 `COMMAND_SUMMON` 读出来交给忆灵那次 `EnemySkill` —— 与元素、形状、倍率同一条"只有一个真相"的规矩，所以这个 op 仍然**不收**削韧参数（写进规则就是第二份数字，写错就是削两次） |

**行为边界**：
- **不占忆灵的回合**：这是额外一次攻击（像追加攻击那样），忆灵自己的回合照旧用**它自己的**技能
  （50% 生命上限、单体）—— 两者是不同的两件事，混起来会把它的回合伤害抬成 4 倍；
- 伤害记在**召唤物**头上：击杀归它、事件也按它发（`SUMMON_ATTACK` + `Battle.fireAfterAttack` 都走
  `EnemySkill` 那条路，所以上一节的条件/事件**直接适用**）；
- **目标只取活着的敌人**：`EnemySkill` 拿列表头一个当主目标，不过滤就会对着尸体挥（规则"触发了但什么都没发生"）；
- 没召唤物时**响亮报错**并告诉作者加 `self_summon_count >= 1`（与 `"target": "summon"` 同一口径）；
- 装载期拒绝：缺 `skill` / `damage_param` / `attribute`、`damage_level < 1`、写了 `turns`/`until`、
  **写了 `target`**（打谁由技能形状决定，指到自己人身上就是把忆灵的伤害打给自己）；
- 指名非伤害技能（如她的天赋 `Enhance`）在**触发时**拒绝 —— 规则读的时候还不知道主人是谁，那张表要等开火才拿到。

**⚠ 曾经的错：她终结技的伤害被算两次 —— `M-40`，2026-09-27 量到并已修**。真正施放 141303 时，引擎的通用伤害路径
会按 `skills.json` 里 141303 自己的 `damage_list` 打一遍（基数是**长夜月自己的攻击力**），本 op 再按「忆灵的
生命上限」打第二遍 —— 而文档只描述**一段**伤害（就是忆灵那一段）。在 1002011 上实测：清空触发表只走通用路径
= **8818.5**，带上规则 = **9411.7**，忆灵生命上限 778.5（`2.0 × 778.5 = 1557`）→ 两份伤害叠在一起。

**修法（已落地，P11-1）**：新增"施放之前"的事件 `TriggerEvent.CAST_SETUP`（`SkillExecutor.execute` 在伤害展开
**之前**发，`Battle.beginCast/endCast` 提供这次施放的令牌）与 op `DELEGATE_DAMAGE`。长夜月的内容因此多了一条
`CAST_SETUP` 规则 —— `{ "when": ["actor == self"], "do": [{ "op": "DELEGATE_DAMAGE", "skill": "ULTRA" }] }` ——
它说的是"这次施放的伤害不由我来打"：执行器于是**不展开伤害、也不削韧**（削韧跟着挥击走），
而 `ULT_CAST` 那条规则照旧用 `COMMAND_SUMMON` 把这一段交付给忆灵（连同 90 削韧）。
⚠ 三条边界写死在 op 里：只能在 `CAST_SETUP` 上（装载期校验）、只能移交**规则主人自己**正在施放的那一次
（`CAST_SETUP` 投给每张表，忘了 `actor == self` 就是"把队友的伤害交出去"）、`skill` 必须**就是**正在施放的那个槽位
（条件 DSL 没有槽位变量，这个比对就是闸；写错槽位是"装载成功、触发成功、什么也没发生"）。
`CastSetupTest` 六条钉住它，且每条"什么都没打出去"的断言都配了**同一施放但不委派的对照**（只断言 0 会在
"这条技能本来就没伤害"时同样通过）。

⚠ **"读哪一行"必须能说**（`damage_level`）：本引擎里角色技能等级恒为 1，而行文引用各自技能写作时的等级。
不写行号就读 Lv1 → 100%（**一半，无人报错**）。克拉拉的反击规则选了"就读 Lv1"并写明（80%），这条选了 200%。

**验收**：`SummonCommandTest` **12 条** —— 攻击者是召唤物（用上一节的 `SUMMON_ATTACK` + `actor == summon` 交叉验证）、
**基数是它自己的生命上限**（把召唤物的生命上限翻倍 → 伤害翻倍；把**主人**的翻倍 → 伤害不变）、
形状来自技能所以打到**每一只**、倍率来自**写明的行**（Lv1/Lv10/Lv15 的比值 = 1 : 2 : 2.5）、
不占回合（行动条上的剩余值不变，自己的技能还在）、首敌已死时仍打到活的那个、
最后一行越界时**开火即拒**、7 条装载期拒绝、非伤害技能开火即拒、没召唤物时响亮报错，
以及出货的 1413 终结技（先召唤后指令 —— **顺序可观测**：把指令写在前面会报"没有召唤物"）与它的四个参数。
**变异验证（8 处）**：忽略 `damage_level` / 让**主人**挥（而不是召唤物）/ 基数写死 `ATTACK` /
形状写死单体 / 不过滤死者 / 允许 `target` / `damage_level` 不校验下界 / 行号越界不校验 ——
各红对应条目，且**全部**还原（逐次 SHA-256 核对）。⚠ 脚本这次还教了一条：**多行锚点匹配不上**是因为文件是
CRLF 而锚点用 `\n`，最后一条改用**按行定位**才跑起来 —— 变异脚本的锚点必须与真实行尾一致。

整套 **97 套 / 903 例全绿**；整场战斗 demo 全输出与上一提交逐行一致（仅 3 行 `Resist {}` 的 Set 顺序不同）。

### 24.11 「被召唤时」= 数据：`SUMMONED` ✅（2026-09-28）

忆灵技能3「**被召唤时，使自身立即行动**」只差一件事：`ADVANCE` 早就够用（`percent: 1.0` = 推掉目标**剩余**的全部
行动值 = 立即行动），缺的是"**入场**"这件事本身没有事件。新事件 `SUMMONED`：

| 半边 | 做法 | 为什么 |
|---|---|---|
| 事件 | `SUMMONED`，`actor` = 入场的召唤物 | 入场是战场事实，只有引擎知道 |
| 效果 | `ADVANCE percent: 1.0 target: summon` | 已存在的 op + 已存在的目标选择器，一行都不用新加 |
| 条件 | `actor == summon` | 事件投给每个角色的表 → 否则**队友的忆灵**入场也会推我的条 |

⚠ **时机就是契约**：召唤物走 `addRequestItems` 进行动条，所以**召唤调用期间它还没有信号** ——
在调用里发事件，等于让每个这样的规则拿到一个"推条静默失效"的单位。所以事件在 `processRequests` 里、
`processAddRequests()` **之后**发（`Battle.fireSummoned`，用一个**独立的** `justSummoned` 列表记账 ——
`processAddRequests` 的队列与**波次**共用，一波怪入场不是召唤）。钉住这条的是"把速度压到 1、只能靠推条才
排到第一"的用例：变异成"提前到队列推送之前发"时，它和出货内容那条**同时红**。

⚠ **`SUMMON` 幂等 ⇒ 已在场时再召唤不发这个事件** —— 这正好是「若衣匠已在场，则…」那类条件插话需要的语义，
不是巧合而是同一条事实的两种说法。

**验收**：`SummonEntryTest` **6 条** —— 一次入场一次事件（且再 settle 一次不再发）、
**入场后才发**（速度 1 的单位被推到队首）、队友的召唤物不算、已在场时再召唤不发、
出货规则的形状（`ADVANCE 1.0` + `target: summon`），以及端到端：长夜月开战 → 天赋召出「长夜」→
**它是下一个行动的单位**（行动值 0 且排在队首）。**变异验证（4 处）**：事件提前到队列推送之前发 /
`justSummoned` 不过账 / 忆灵路径不记账 / 怪物路径不记账 —— 各红对应条目、全部还原（逐次 SHA-256 核对）。

**顺带查清的「立即行动」家族**（同一句话，不同的缺口）：`ADVANCE` 能表达的是**单体**的立即行动 ——
素裳/丹恒/三月七那种"自己立即行动"、以及布莱德那种"被指定的那个队友"里的**自身**部分都够了；
缺的是**多目标**与**资源计数条件**，两件事 2026-09-28 各推进了一半：

- ✅ **多目标那一半做了**：`ADVANCE` 改走列表版（`resolveTargets`，与 `HEAL`/`SHIELD` 同一个），
  并补了选择器 **`other_allies`**（「除自身以外」）—— 知更鸟终结技第一句
  「使除自身以外的队友立即行动」已出货，`OtherAlliesTargetTest` **6 条**，见 §4.6 的选择器表。
- ✅ **「指定我方单体」也做了**（2026-09-28，M-35）：施放类事件的 `target` 现在就是**这次施放瞄准的那个单位**
  （调用方选的主目标），所以布洛妮娅「解除指定我方单体的 1 个负面效果，并使该目标立即行动」**整句已出货**
  （`CastTargetTest` 5 条，见 §4.2 的事件表）。
- ⚠ **星期日那种仍缺**：「使**指定我方单体**及其召唤物立即行动」要的是**两个**目标 —— 一个"被指定的"加上"它的召唤物"，
  而这不是任何一个现成选择器能指的（`ADVANCE` 现在能推一组，但"这一组"= 指定者 + 它的召唤物 没有拼法）。
  登记为 `M-37`。
- ⚠ **资源计数**（三月七「充能 ≥ 7」、长夜月天赋「【忆质】≥ 16」）仍未做，登记 `M-34`。

整套 **101 套 / 926 例全绿**。


