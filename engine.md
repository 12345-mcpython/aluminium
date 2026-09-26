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
实体层   CanHit（抽象基类）├── Character   ├── Enemy   └── Summon ⚠类存在、敌方阵营收得下，但内容侧还没创建（P9-4）
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
| 追加攻击专属 | `FOLLOW_UP_DAMAGE_BOOST`（只在 `DamageType.ADDITIONAL` 时进增伤区） |
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

1. **增伤区**：`getBoostByElement(element)` 拿元素增伤属性 + `ALL_DAMAGE_TYPE_BOOST`；
   若 `damage.getType() == ADDITIONAL`（追加攻击）再叠加 `FOLLOW_UP_DAMAGE_BOOST`。
   ⚠ **"追加攻击专属增伤"必须是一条独立属性**，不能拿 `ALL_DAMAGE_TYPE_BOOST` 顶替 ——
   后者会把普攻/战技/终结技也一起抬高，而遗器套装 115 的文案只说 Follow-Up ATK。
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
| `AttackEvent` | `afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage)` | `SkillExecutor` 在一次技能全部段结算完后 | **我方全体** |
| `SkillCastEvent` | `onSkillCast(battle, user, skill, hitTargets, targets)` | `SkillExecutor`，伤害已展开、能量未结算之间 | **我方全体** |
| `EnergyEvent` | `onEnergyGain(battle, target, actuallyAdded)` | `Battle.applyEnergyGain`（唯一回能入口） | 相关方 + 我方 |
| `HpLossEvent` | `onHpLoss(battle, target, before, after, source, amount)` | `Battle.applyDamage` 扣血后 | 相关方 + 我方 |
| `HealEvent` | `onHeal(battle, healer, target, actuallyHealed)` | `Battle.heal` | 相关方 + 我方 |
| `KillEvent` | `onKill(battle, attacker, victim)` | `Battle.applyDamage` 目标由生转死 | 相关方 + 我方 |
| `BreakEvent` | `onBreak(battle, attacker, target, element)` | `Battle.reduceToughness` 韧性归零那一刻 | 相关方 + 我方 |
| `SkillPointGainedEvent` | `onSkillPointGained(battle, amount)` | 策略真的入账后 → `Battle` 广播 | **只我方** |
| `SkillPointSpentEvent` | `onSkillPointSpent(battle, amount)` | 策略真的花掉后 → `Battle` 广播 | **只我方** |

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
| `SkillCastEvent` | ⚠ **非伤害技能也发**（`hitTargets` 为空）。治疗/护盾/纯 buff 技的触发源靠它 —— 布洛妮娅「施放战技时 50% 概率 +1 战技点」如果写成"没打中就不发"就永远收不到。一次施放**只发一次**（群攻打 3 个目标也是 1 次） |
| `EnergyEvent` | `actuallyAdded` 是**实际入账值**（被上限截断后）。已满 → 0 → **不发**。**没有能量条的角色没有本事件**（走层数资源的 6 个角色，provider 恒返回 null，压根走不到回能口） |
| `HpLossEvent` | ⚠ `amount` 是**真的掉了多少血**，**不含被护盾吸走的量**。盾没破 → 不发。损血转资源的角色（遐蝶【新蕊】/万敌【血仇】/刃【充能】）靠这条区分"掉血"与"受到伤害" |
| `HealEvent` | 是**实际回复量**。满血被治疗 → 0 → **不发**。⚠ `CanHit.heal(double)`（原始加血）**不发** —— 它是 `Battle.heal` 与"直接改血量"共用的底层口子 |
| `KillEvent` | **不看** `countsAsAttack`（与击杀回能同口径）：附加伤害/真伤/DOT 补刀击杀**也发**。目标已死再挨打、无敌期间都不发。⚠ 见 §4.5「被写错的一条验收标准」 |
| `BreakEvent` | 只在**击破那一刻**发一次 —— 已击破的敌人继续挨打（超击破路径）不会重复发 |
| `SkillPointGained`/`Spent` | **没花出去就不算消耗**：战技点不足、出手不成立时**不发**（否则米沙/花火那类"每消耗 1 点"的计数器会为没发生的消耗记账）。已满时普攻实际入账 0 → 也不发 |
| `TriggerEvent.TURN_START`（**无 buff 接口**） | `Battle.beforeMove()`：该单位 buff tick **之后**、`MoveEvent.beforeMove` **之前**。`actor` = 轮到的角色，`target` **也**是它（"回合开始时"的两种写法等价，写哪种都不会哑掉）。**一次回合发一次**（额外回合也算一次）。⚠ 这不是新的 buff 接口 —— 回合边界对 buff 仍然是 `MoveEvent`（见 §4.6） |
| `TriggerEvent.TAKING_HIT`（**无 buff 接口**） | `Battle.applyDamage`：一次伤害实例**落在活着的、非无敌的目标身上**就发（被盾全额吸收**也算**）。⚠ **与 `HP_LOST` 是两件事**：`HP_LOST` 的口径是"真的掉了血"（`hpLoss > 0` 才发），`TAKING_HIT` 的口径是"挨打了"。遗器/天赋里"受到攻击后"要的是后者 —— 用前者会让带盾角色永远不叠层。`actor` = 伤害来源，`target` = 被打的人（"我被打" = `target == self`），投递口径与 `HP_LOST` 相同（`fireTriggersForAlly`，敌方主体不发） |

### 4.3 其余关键语义

- **`DamageEvent` 广播给双方**，但回调签名里**不告诉 buff 它挂在谁身上**。
  因此注入乘区的 buff 必须自己判侧：`Damage.isOnDefenderSide(entity)` / `isOnAttackerSide(entity)`
  （这就是易伤必须判侧、否则持有者自己打人也会被加伤的原因）。
- **`AttackEvent` / `SkillCastEvent` 广播给我方 `battle.characters`**（不含召唤物 ❌ 因为召唤物没进任何列表），
  这是因为"我方攻击后 / 施放后"的效果（知更鸟【协奏】、缇宝结界）挂在**别人**身上。
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

### 4.4 还没做的

- **敌人技能不发 `SkillCastEvent`**：`EnemySkill` 有自己的 `execute`（不走
  `SkillExecutor`），等 P9-2 把敌人技能接进统一执行器时对齐。
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
  "source": "1403 缇宝 trace 1403103",         ← 出处（引擎不读，给人看）
  "note": "…" }                                ← 为什么是这个数
```

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
target == self    这件事发生在我身上   ← 克拉拉「受到攻击后」
target != self    发生在我方的别人身上
hit_count > 0     这次攻击打中了至少 1 个目标
hit_count == 2    精确命中数
hp_percent <= 0.5 我自己的血量比例（0.5 = 50%）← 风雪交加 4 件套「回合开始时，若生命百分比 ≤ 50%」
```

左右可以互换（`0 < hit_count`、`0.5 >= hp_percent` 也成立）。**变量是封闭集合**：写错变量名
在**加载时**就报错，报错信息会列出**全部**已知变量名（由集合本身排序生成，不是手写的，
所以加变量时消息不会落后于解析器）。

> ⚠ **`hp_percent` 读的是"主人"（触发规则所属角色）自己的血量**，不是事件里的谁 ——
> 它是关于"我"的事实，所以没有任何事件需要携带它。空主人 / 最大生命为 0 → `NaN` →
> 一切比较为 false（不成立就是"不触发"，不是"按 0% 触发"）。

> ⚠ **`actor` 与 `target` 是两件事，混用是最容易犯的错。**
> `actor` 是"谁干的"，`target` 是"发生在谁身上"。**我被打中时，`actor` 是敌人**，
> 所以克拉拉的反击必须写 `target == self`；写成 `self`（或 `actor == self`）
> 是在说"敌人动手时也算我动手"，永远不成立。加载期的变量校验抓不到这个
> —— 两个名字都合法 —— 只能靠 §4.7 那条"拆掉条件后测试必须变红"来守。

> ⚠ **`actor` 与 `target` 是两件事，混用是最容易犯的错。**
> `actor` 是"谁干的"，`target` 是"发生在谁身上"。**我被打中时，`actor` 是敌人**，
> 所以克拉拉的反击必须写 `target == self`；写成 `self`（或 `actor == self`）
> 是在说"敌人动手时也算我动手"，永远不成立。加载期的变量校验抓不到这个
> —— 两个名字都合法 —— 只能靠 §4.7 那条"拆掉条件后测试必须变红"来守。

#### 效果 op 词表（只做引擎已有的能力）

| op | 参数 | 状态 |
|---|---|---|
| `GAIN_ENERGY` | `amount`，可选 `per_target` | ✅ |
| `GAIN_SKILL_POINT` | `amount` | ✅ |
| `HEAL` / `SHIELD` | `amount`，可选 `target` | ✅ |
| `EXTRA_TURN` | 可选 `target` | ✅ |
| `ADVANCE` | `percent`（0.0–1.0，跳过目标**剩余**行动时间的比例；负值不支持） | ✅ |
| `GAIN_RESOURCE` / `SPEND_RESOURCE` | `resource` / `amount` | ✅（P8-8） |
| `DAMAGE` | `skill` / `damage_param`，可选 `target`、`per_target`、`as_attack` | ✅（P8-3，见 §4.7） |
| `MODIFY_ATTR` | `attribute` / `percent` / **`turns` 与 `permanent` 二选一**，可选 `target`、`max_stacks`（别名 `stacks`） | ✅（P10-3） |
| `APPLY_BUFF` | `buff` / `turns` | ☐ P10-3 |
| `REDUCE_TOUGHNESS` | `amount` | ☐ 要定元素与敌方目标 |

> ⚠ **未接线的 op 是在加载时"响亮地"拒绝的**，报错里点名它归哪个阶段。
> 否则内容作者写了规则、看不到任何反应，却分不清"我的条件写错了"和"引擎压根不发这个事件"。

#### `target` 选择器（同样是封闭集合）

```
不写              默认 = 触发器的主人自己（最常见，所以允许省略）
self             同上，写出来更明确
target           这件事的承受者（掉血的/被治疗的那个人）  ← 给队友上 buff
attacker         这件事的起因（打我的人）               ← 反击
all_allies       我方全体（别名 party）                ← 「我方全体攻击力 +X%」
```

> ⚠ **`target` 曾经写错就等于 `self`**：解析器对不认识的取值一律**回退到"主人"**，
> 于是 `"atacker"` 和 `"self"` 行为完全一样 —— 规则照常触发、什么都不报，只是默默地改错了人。
> 现在它和条件变量一样是**封闭集合**，加载时就拒绝（`unknownTargetSelectorIsRejected`）。

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
  ⚠ 这两个新参数写在 `MODIFY_ATTR` **以外**的 op 上也会被拒 —— Gson 对不认识的字段只会
  留 `null`，不检查的话"规则照常加载、效果永远不叠"又成了那种不报错的空操作。

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
  `BuffManager.countBuffs` / `removeOneBuff` 是给"现在几层了 / 消耗一层"用的查询口
  （当前只有测试在用 —— 触发器表还**没有**"消耗一层"的 op，见 `F-10` 的"仍缺什么"），
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
  └─ Battle.fireTriggers(ULT_CAST, caster, hits, 0)        ← 施放的是终结技时（按解析出的 SkillCategory.ULTRA）
  └─ Battle.fireTriggers(SKILL_CAST, caster, hits, 0)      ← 其余施放（两者互斥）
  └─ Battle.fireTriggers(ALLY_ATTACK, caster, hits, 0)     ← 只在命中了目标时（终结技命中同样算）
        └─ 遍历我方每个角色：拿它自己的表
              ├─ 用 (self=它, actor=施放者, target=承受者, hit_count) 匹配规则
              └─ TriggerInterpreter.apply(...) → 真的去 grantEnergy / grantSkillPoint / …
```

> ⚠ **`ULT_CAST` 与 `SKILL_CAST` 必须互斥**：条件 DSL 里没有"这次施放是战技还是终结技"这个变量
> （只有 `actor` / `target` / `hit_count`），所以「当装备者使用战技时」这条规则只可能靠
> **发出端分开**来避免连带在终结技上触发。判定读的是**解析出的数据**
> （`skills.json` 的 `attack_type` → `SkillCategory.ULTRA`），不看技能名、不看槽位。
> 契约由 `UltCastTriggerTest` 钉住（一次终结技恰好一次、其余槽位不触发、`ALLY_ATTACK` 不受影响）。

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
`HEALED`（`heal`）、`BREAK`（`reduceToughness`）、
`SKILL_POINT_GAINED` / `SKILL_POINT_SPENT`（战技点入账/消耗）。
**事件接线现状**：`TriggerEvent` 里的**每一个**值现在都有发出点（`TURN_START` 与 `TAKING_HIT`
在 P10-3 后半挂上），由 `TriggerTableTest.everyDeclaredTriggerEventIsEmitted` 钉住 ——
声明了却没有发出点的事件对作者是个陷阱，加了值而忘了接线必须让测试变红。
（加载时拒绝未接线事件的通道仍然在，只是当前没有数据能触发它。）

**遗器套装规则走的是同一条通道**：`resources/relic_sets/<setId>.json` 与角色文件同形，
只是按件数阈值分组（`{"4": [ … ]}`），由 `data.RelicTriggerTables` 懒加载，
在装配点 `CharacterFactory` 并入角色自己的表（`TriggerTable.plus`）。
表达不了的那部分登记在 `_unmodelled.json`（见 `ROADMAP.md` §12.5 `F-10`）。

**只对我方开火**：敌人的事件不是我们的内容（P9 才管怪物），而且"敌人挨打"不该让
我方角色被触发两次。带主体的事件用 `fireTriggersForAlly` 做这道阵营判断。

**递归护栏**：触发器产生的效果本身可能再发事件（治疗 → 治疗类 buff → …）。
引擎不试图聪明地判环，而是**限制嵌套深度**（`MAX_TRIGGER_DEPTH = 8`）并**响亮报错**，
让跑飞的表被抓住，而不是把战斗挂死。

#### 加载与"没登记"的语义

- **没有文件 = 空表**（`TriggerTable.EMPTY`），这是**正常状态**不是错误 —— 93 个角色
  目前只数据化了 4 个（缇宝 1403 / 知更鸟 1309 / 克拉拉 1107 / 希儿 1102）。
  `Character.triggerTable` **永不为 null**，所以调用点不用判空。
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

**两个样本（都是纯 JSON，零 Java 角色类）**：

| 角色 | 规则 | 说明 |
|---|---|---|
| 克拉拉 1107 `因为我们是家人` | `on: HP_LOST` + `when: ["target == self"]` → `DAMAGE`(`TALENT`, `damage_param: 1`, `target: "attacker"`) | "**我**是挨打的那个" → 打回去 |
| 希儿 1102 `再现` | `on: KILL` + `when: ["actor == self"]` → `EXTRA_TURN` | "**我**是击杀者" → 立即再动 |

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
| `SUMMON`（召唤） | P9-4 | ❌ |
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

### 8.6 击破控制状态（P10-2）✅ 数值仍是示例值

冰 / 量子 / 虚数击破不留 DOT，而留一个**控制状态**。表在 `Constant.CONTROL_EFFECTS`
（`ControlEffect(resistKey, turns, blocksAct, slowPercent)`），由 `BreakEffect.control` 按键引用。

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

**没有新的 buff 类 —— 控制是组合出来的**（P8-0 的规矩）：

| 部分 | 用哪个现成原语 |
|---|---|
| `blocksAct` | `StunBuff`（`canAct() == false`，早就在） |
| `slowPercent` | `StatModifierBuff.percentDebuff(SPEED, …)` |
| 推条 | `Battle.delayMovePercent`（**瞬时**推，不是 buff） |
| `resistKey` | `Battle.hitChance` 的第 4 个参数（**只给技能施加那条路**） |

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
+1 段 + 回 2 能量"、花火"消耗战技点时回能 + 溢出储存"）全都没接 → **`F-4`，归 P8-7 触发器表**。
其中米沙/花火要监听的是"**战技点被消耗**"这件**事**，是 `EnergyProvider` 那种
"按技能类型查表"的钩子**表达不了**的 —— 需要新事件 `SkillPointSpentEvent` / `SkillPointGainedEvent`。

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
`countBuffs(Class)` / `removeOneBuff(Class)` 是叠层用的两个延伸口（"现在几层" / "消耗一层"）。
**不提供 `getBuffs()`**：遍历与判定留在 manager 内部，避免外部改列表导致 CME。

### 10.5 现存 buff 实现

| 类 | 类型 | 说明 |
|---|---|---|
| `BoostDamageBuff` | 属性型 | `ALL_DAMAGE_TYPE_BOOST` 加 `rate`（平值），用 `id` 精确摘除 |
| `StatModifierBuff` | 属性型 | **通用**属性 buff/debuff（`MODIFY_ATTR` 的落地形态）：(属性, modifier 种类, 数值, 时长或 `permanent`, 可选 `maxStacks`)。`isSameKind` = (属性, modifier 种类, buff/debuff) 三元组，`stackGroupKey()` 用同一个三元组决定谁能和谁叠 |
| `VulnerabilityBuff` | 注入型 | 易伤，受击方负面，`ModifierSource.DEBUFF` |
| `ReductionBuff` | 注入型 | 减伤，受击方增益，`ModifierSource.BUFF` |
| `StunBuff` | 控制 | early buff，`canAct() == false` |
| `DotBuff` | **生命周期型** | 击破 DOT（P4-5 / P10-0）：只持 `{元素, 每次基础伤害}` + 继承的时长，**不含伤害逻辑** —— 伤害由 `Battle.tickDots` 结算（`tickEffect` 拿不到 `Battle`）。early buff；`canAct()` 恒 `true`（否则燃烧结束时会把主人多冻一回合）；`isSameKind` 恒 `false`（实例身份，见 `engine.md` §8.5） |
| `SpeedBoostBuff` / `SuperBreakBuff` / `TauntBuff` | 属性/注入 | 早于 `StatModifierBuff` 的专用类，见各自 Javadoc |
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
> ⚠️ 它的 javadoc 说"资源缺失返回 null"，实际是 `Objects.requireNonNull` **抛 NPE**；
> 在 `Constant` 静态块里会变成 `ExceptionInInitializerError`，此后该 JVM 内每次访问 `Constant` 都失败。
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
| 召唤物 | `Summon` 类存在；**敌方阵营现在收得下它**（L-8：`battle.enemies` 是 `List<CanHit>`，`enemyUnits()` 才是"其中的怪"）—— 但内容侧仍未创建任何召唤物，`SkillEffectType.SUMMON` 也没分派（P9-4）。我方入场/波次走 `WaveManager` → `Battle.addRequestItems`，那条路是活的 |
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
| §3.4 **嘲讽** | ✅ 已实现（P5-2）：`TauntBuff` 是纯标记，**硬指定目标**（单体 / 扩散中心）而非仇恨加权 —— 与 §3.4 的"按百分比提高仇恨值"写法不同，见 §19.2。⚠ 规格与实际规则不符这点记在这里，不再重复 |
| §3.5 **效果命中与抵抗的生效概率公式** | ✅ 已实现（P6-1）：公式与 §3.5 一致，三个因子乘算。顺手修了 `EnemyFactory` 漏写 `effectHitRate` 的问题（之前敌人命中恒 0）。见 §20.1 |
| §4 **护盾** | ✅ 已实现（P6-3）：`CanHit.shield` 先于 HP 被扣、不叠加。🚧 规格里的"护盾量提高"没有对应属性，护盾量目前就是传入值 |
| §4 **治疗乘区** | ✅ 已实现（P6-2）：`Battle.calculateHeal/heal`。⚠ 规格的 `(1 - 治疗降低)` 与 `(1 + 受疗加成)` 合并成一个因子（`HEAL_TAKEN_RATIO` 取负即降低），因为属性表里没有单独的"治疗降低" |
| §5 **忆灵系统**（独立单位/面板快照/连携攻击） | ❌ `Summon` 类从未被实例化 |
| §6 **欢愉体系**（阿哈速度/笑点/好活当赏/欢愉伤害公式） | ❌ 只有 `DamageType.ELATION` 与 `AttributeType.ELATION_DAMAGE_BOOST` 两个占位；`elation_basic_level_damage.json`（101 条）**从未被加载** |
| §7 **超击破** | ✅ 已实现（P4-6，2026-09-19）：`SuperBreakBuff` + `BreakDamageCalculator.buildSuperBreak` + `SkillExecutor` 里追加 `SUPER_BREAK` 段。**但**公式里的 `(1 + 削韧值提高)` 与 `(1 + 弱点击破效率提高)` 仍缺（属性不存在），`SUPER_BREAK_BOOST = 0.4` 是示例值 |
| §8.1 **忆灵伤害 / 欢愉伤害** 作为独立类型 | 类型枚举里有 `MEMORY` / `ELATION`，但无来源 |
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

注意**行迹是无条件应用的**（`build()` 里调 `SkillPoint.appendTo`），所以面板不等于
`数据 × 倍率`。这一点很容易误判成"属性索引错位"（P8-1 时我就误诊了一轮）。

⚠ `Character.fromAttributes(...)`（测试/占位入口，P8 后新代码禁止使用）造的技能
是写死的 `DefaultSkill(1001, 1, 1)`——**六个槽位全是槽位 1 的普攻**。这是刻意的：
它没有 `cid`，查不到真实技能数据；真实角色一律走
`CharacterFactory.create(cid, level)`（槽位映射见 §7.2）。
本类**不负责**填技能倍率；天赋触发的追加攻击走 §4.7 的触发器表，
召唤物与敌人侧的追加攻击归 P9-4。

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


