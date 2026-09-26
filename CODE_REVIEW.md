# aluminium 代码审查报告

审查日期：2026-09-19 ｜ 范围：`main` @ `929b460`（工作区干净）｜ 方式：只读静态审查（含针对性数据/数值复算）

## 0. 方法与基线

| 项 | 说明 |
|---|---|
| 基线 | 工作区干净、无未提交改动。**最后一次记录的测试运行：23 个套件 / 172 个用例全绿**（`build/test-results/*.xml`，2026-09-16T14:11:54Z） |
| 一致性 | 源码最后修改（22:11:48）**早于** `build/classes` 与测试结果（22:11:54）→ "全绿"覆盖的就是当前 HEAD |
| 覆盖 | 主代码 **70 个类 / 约 8.5k 行** + 测试 23 个类；`data/` 下 17 个 JSON 全部校验可解析 |
| 分工 | 核心引擎（`Battle`/`Enemy`/`Damage`/`SkillExecutor`/`Dot`/`Constant`/`JSONReader`）人工逐行；数据层、面板与 buff、队列与技能、属性代数与能量四个分区独立审查，**每条结论都回到源码核对了行号**，跨分区重复项已合并 |
| **限制** | **本次没有运行测试或编译。** 文件沙箱为 `workspace-write`，Gradle 写不到 `~/.gradle`（`.lck` 被拒）。除注明"实测数据"的几条（JSON 解析、等级覆盖、段数统计、IEEE-754 复算）外，均为静态推理 |
| 排除 | `README.md`/`ROADMAP.md` 的**设计意图**不算缺陷；只审代码与数据，以及代码与文档互相矛盾之处 |

**总体结论**：伤害流水线的**架构**质量明显高于一般业余项目——`Damage` 的四条乘区不变量是结构性成立的（`getRate()` 为 `final`、钳制属于区自己、"这段不吃那个区"由 `DamageType` 声明），"唯一结算入口"甚至用反射锁住了；数据层的已知坑（幽灵字段、补丁合并、加值语义）都有测试兜底且经实测属实。

问题不在架构，而在**"测试绿"与"真的对"之间的落差**，集中在三类：

1. **口径错但测试恰好绕过**（`H-3` 削韧按段翻倍、`H-4` 击破伤害用标称值）——测试用例的数据凑巧都落在正确区间。
2. **测试替身掩盖了真实路径**（`EnergyTest` 用手写 `SkillData`、`BuffManagerTest` 绕开 `Battle`、`startBattle()` 一次都没被调用）。
3. **半接完的线**：`Constant` 的八个数据表只裹了一个、`maxEnergy` 从 JSON 读出来却never接线、`BuffManager` 缺 buff 查询口。

**最该先看的 4 条**：`C-1`（易伤/减伤会改到持有者自己的输出）、`H-3`（多段技能削韧翻几倍）、`H-1`（`Constant` 全局数据可被运行时清空）、`H-7`（`BuffManager` 没有查询口，下一个任务 P4-6 照 ROADMAP 写会编译不过）。

### 严重度总览

| 编号 | 严重度 | 一句话 | 触发条件 |
|---|---|---|---|
| C-1 | **Critical** | 易伤/减伤会 modifier 到持有者**自己打人**的伤害上 | 敌人会攻击后必现（P5-5） |
| H-1 | High | `Constant` 八个数据表有七个是可变的全局状态 | 任何调用方 `clear()`/`put()` |
| H-2 | High | `character_data.json` 的 `max_energy` 从未接进角色 | 数据造的角色全部无能量条（注意：1407 的 `null` 是设计，不是缺值） |
| H-3 | High | BOUNCE 每段都按技能总削韧值扣，段数越多削几倍 | 现在就算错（1304 的 7 段 = 70 点） |
| H-4 | High | 击破伤害用"标称削韧值"而非"实际削掉的值" | 现在就算错（没打空时偏高） |
| H-5 | High | 终结技先结算后清零，抹掉大招自己赚的击杀/击破回能 | 现在就算错（大招击杀少 5 点） |
| H-6 | High | `CanHit` 拷贝构造器浅拷贝属性数组，实例间共享面板 | 多波次/重试/快照（P7-4） |
| H-7 | High | `BuffManager` 没有 buff 查询口 | P4-6 照 ROADMAP 写编译失败 |
| H-8 | High | `Queue` 同行动值无裁决，**且显示顺序与实际出手顺序不一致** | 两个同速单位 |
| H-9 | High | `startBattle()` 从未被任何测试调用 | 开场事件链零覆盖 |

### 修复进度（2026-09-19 更新）

本轮先修了 4 条（用户选定的范围 A），**167/167 测试全绿**，并做了变异验证：把源码改回旧行为后，6 个相关新用例确实全部失败（见下方各条的"已修"记录）。

| 编号 | 状态 | 改动 |
|---|---|---|
| C-1 | ✅ 已修 | `Damage.isOnDefenderSide/isOnAttackerSide` + `AbstractBuff.owner`（`BuffManager.addBuff` 写入）+ 两个 buff 按侧注入 |
| H-3 | ✅ 已修 | `SkillExecutor` 弹射按 `hits` 均摊 `stance_list.single` |
| H-4 | ✅ 已修 | `Enemy.reduceStance` 返回实际消耗值，`Battle.reduceToughness` 用它构造击破伤害 |
| H-5 | ✅ 已修 | `Battle.castUltra` 把 `setCurrentEnergy(0)` 提到 `processRequests()` 之前 |
| H-7 | ✅ 已修 | `BuffManager.hasBuff(Class)` + ROADMAP P4-6 示例同步 |
| 其余 | ⬜ 未动 | H-1/H-2/H-6/H-8/H-9 与全部 Medium/Low/Nit 见下文 |

新增/扩展的测试：`DamageHookTest` +4、`ToughnessBattleTest` +2、`EnergyBattleTest` +2、`BuffManagerTest` +2（共 158 → 167）。

---

## 1. 先确认哪些是好的（避免误伤）

1. **乘区体系**：`Area.getRate()` 是 `final`；钳制边界由各区自己声明（易伤 3.5 / 减伤 0.01 / 虚弱 0.2 / 抗性 [0.1,2.0] / 暴击 ≥1）；"不吃那个区"由 `Area.applies(DamageType)` 决定。`DamageZoneTest` 24 个用例覆盖边界、顺序无关、按来源撤销、容器只读。
2. **唯一结算入口**：`assemble` 为 `private`，`DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint` 用反射断言旧签名取不到。
3. **`DoubleValue` 代数正确**：`compute()` 严格等于 `base × (1+Σadd) × Π(1+mul) + Σpure`；`clone()` 是真深拷贝；`removeModifier` 用非短路 `|=` 扫三个列表，配合 `Modifier.equals` 含 `modifierType`，按来源撤销**不会撤错、不会 CME**。
4. **buff id 无泄漏**：`AbstractBuff.id` 来自单个 `AtomicInteger`（起始 1），进程内唯一、不与 `roleId == 0` 冲突；`addBuff` 换同类时用**旧 id** 摘掉旧 modifier 再挂新的。early/late tick 互斥且每回合恰好一次（duration-1 后置 buff 活过 `beforeMove` 死在 `afterMove`；duration-1 `StunBuff` 挡住它到期那回合）。`AbstractBuff` 不重写 `equals/hashCode`，所以 `remove`/`removeIf` 是身份语义。
5. **Gson 绑定实测全部正确**：`main_attribute`/`sub_attribute`/`monster_config`/`monster_template_config`/`skills.json` 的键名与 bean 声明**逐一枚举核对**，无一处因键名不匹配静默读 0；`"Unknown"` 元素 212/702 处按设计映射为 `null`（引擎用"空元素 = 非伤害技能"表达，且只对伤害技能 fail fast）。幽灵字段 `hp_modify_ratio` 确实没被绑定，`health_modify_ratio` 取值与锚点一致（100201101→0.266667、802501003→1.979167）。
6. **攻击修正补丁链路完整**：`raw 2649 条` / `补丁 444 条` / `补丁键不在 monster_config 的 0 条`；归一化前 2649 条 `attackRatio` 全为 null，归一化后 444 条来自补丁、2205 条默认 1.0。
7. **面板管线对得上 JSON**：`CharacterTest` 四个断言从 `character_data/weapons/main_attribute/sub_attribute/point.json` 端到端复算通过（HP 5379.05 / DEF 1312.79 / ATK 1217.16 / SPD 220.03）；等级倍率 7.35（角色 L80）、22.05（光锥 L80）与 `LevelPromotionCalcTest` 完全一致。`PERCENT_TO_BASE` 重定向在作用域内每条路径恰好触发一次、无双重换算，`build()` 把 percent 槽位置 null 所以读不到陈旧值。
8. **等级/数据覆盖无缺口**：`breaking_rate.json` 与 `hard_level_group.json` 等级集合**完全相同**（1–100 与 120，共 101 个）——我原本怀疑的"101–119 级击破时抛异常"**实测已排除**；2649 条 `monster_config` 的 `template_id` **0 条**缺失；`skills.json` 的 `param_list` 长度与 `max_level` **702/702 一致**。
9. **`EnemyScaler` 对得上锚点**：`69.75×236.53471=16498.296`、`210×5.238095=1099.99995`、速度 132、效果抵抗 `0.2+0.1=0.3`（加值）、绝境王虫 `×6.2≈53,099,830`。
10. **能量实现细节正确**：`hasEnergyBar` 用 `>0`、`isEnergyFull` 用 `>=`；上限截断并返回实际入账值；`null`/零/负 gain 均为 no-op；五个 `EnergyProvider` 调用点**都**做了 null 检查；技能回能是"每次施放一次"而非按段；`StandardEnergyProvider` 的 switch 字符串与 `skills.json` 真实 `attack_type` 一致（含 104 个 `null` 的守卫）。
11. **`SkillEffectType` 覆盖数据里全部 11 种 `skill_effect`**，`SkillData.init` 的"未知 effect"抛异常在当前数据集不可达；全部 462 条伤害技能都有真实元素，所以"伤害技能缺元素"的 fail-fast 也不会被数据触发。
12. **`JSONReader` 是 UTF-8 + try-with-resources**，无泄漏；中文名（三月七、碎星王虫•斯喀拉卡巴兹（拟造））加载正确。
13. **`Battle` 的随机数纪律**：无 `Math.random()`，只有注入的 `Random`；`SkillExecutor` 用 `battle.getRng()`。
14. **`Queue` 无重复信号泄漏、无 CME**：`removeCombatant` 用 `removeIf` 清全部匹配项、`addCombatant` 按身份去重、堆是私有的；`snapshot()` 走 `toArray` 而非迭代器，`initialize()`/`rebuildHeap()` 是"复制后清空"；`Battle.removeDeadCombatants()` 也是遍历副本再删。
15. **`DefaultSkill` 并没有硬编码 `skillId`**（ROADMAP 那句话不准确）：`getData()` 用的是构造器传入的 `cid/skillId`。引擎侧也确实没有 `cid` **判断**，`cid` 只作数据键与缓存键。

---

## 2. 缺陷清单

### C-1（**Critical**）易伤/减伤 buff 会改到**持有者自己打人**的伤害上

> ✅ **已修（2026-09-19）**：`Damage` 加 `isOnDefenderSide(CanHit)` / `isOnAttackerSide(CanHit)`；
> `AbstractBuff` 加 `owner` 字段（由 `BuffManager.addBuff` 在挂载时写入）；
> `VulnerabilityBuff`/`ReductionBuff` 的 `onDamage` 开头判 `!damage.isOnDefenderSide(owner)` 就 return。
> `DamageHookTest` 新增 4 条（正反各两条），变异验证确认旧代码下会红。

`Battle.assemble` 对**双方**都广播 `DamageEvent`：

```java
// Battle.java:541-544
// 5) 钩子：实体级 DamageEvent（HSR.md §2.2：虚弱=攻击方负面、易伤=受击方负面、减伤=受击方增益）
attacker.onDamage(this, damage);
defender.onDamage(this, damage);
```

但注入乘区的两个 buff **无从知道自己的持有者是攻方还是守方**：

```java
// VulnerabilityBuff.java:48-51（ReductionBuff.java:44-47 同构）
@Override
public void onDamage(Battle battle, Damage damage) {
    damage.addVulnerable(ratio, ModifierSource.DEBUFF, id);   // 无条件注入
}
```

`AbstractBuff` 从不记录自己被挂在谁身上（`applyEffect(CanHit target)` 把 `target` 丢掉；`source` 字段全仓库无人 `setSource`，恒为 null），`DamageEvent.onDamage(Battle, Damage)` 也不传持有者。于是：

- **易伤**挂在敌人身上（它自己的 javadoc 就写着"挂在**受击方**身上"）→ 该敌人**每次打人**也 +50%；
- **减伤**挂在角色身上（防御性增益）→ 该角色**自己的输出**乘 0.7。

两个类的 javadoc、`DamageEvent` 的注释、`DamageHookTest` 的用例名都写着"易伤=受击方、虚弱=攻击方"，**与实现相反**。

定 Critical 的理由：这是"伤害数值静默算错"里最严重的一档，而它唯一的挡箭牌是"**目前还没有敌人会攻击**"。P5-5 一实装敌方回合，任何挂在敌人身上的易伤立刻变成"给敌人加输出"。修法干净：`applyEffect` 里记下持有者（或把持有者传进回调），注入前判 `damage.getDefender() == owner`（攻击方侧如虚弱判 `getAttacker() == owner`）。当前只有测试挂这两个 buff（`grep` → `DamageHookTest`），所以**没有回归，但也没有防护**。

### H-1（High）`Constant` 的八个数据表有**七个**是可变全局状态

`normalizeMonsterConfigs` 用 `Map.copyOf/List.copyOf` 处理得很干净，但那只覆盖了 `MONSTER_CONFIGS` 一个。其余七个直接是 Gson 造的可变 `LinkedHashMap`：

```java
// Constant.java:185-208
WEAPONS          = JSONReader.fromJSON("weapons.json", ...);              // 可变
CHARACTERS       = JSONReader.fromJSON("character_data.json", ...);       // 可变
SKILL_POINTS     = JSONReader.fromJSON("point.json", ...);                // 可变
SKILLS           = JSONReader.fromJSON("skills.json", ...);               // 可变
MONSTER_TEMPLATES= JSONReader.fromJSON("monster_template_config.json",...);// 可变
HARD_LEVEL_GROUPS= JSONReader.fromJSON("hard_level_group.json", ...);     // 可变
BREAKING_RATE    = JSONReader.fromJSON("breaking_rate.json", ...);        // 可变
MONSTER_CONFIGS  = normalizeMonsterConfigs(...);                          // ✅ 不可变
```

`public static final` 只锁引用，不锁内容。任何调用方（`Main`、测试、将来的 UI/插件）执行 `Constant.SKILLS.clear()` 或 `Constant.CHARACTERS.put(...)`，同 JVM 内后续所有消费者都会静默用上被破坏的数据。嵌套层同样裸奔：`SKILLS[1001]` 是可变 `LinkedHashMap`、`HARD_LEVEL_GROUPS[1]` 是可变 `LinkedHashMap`、`SKILL_POINTS[1001]` 是可变 `ArrayList`（正是 `SkillPoint.init` 遍历建缓存的那个 list）。`RELIC_MAIN_ATTRIBUTES`/`RELIC_SUB_ATTRIBUTES` 内的嵌套 map 同理。

注：**我最初误判为"`Constant` 的全局状态不可变"，那是错的**——只看了 `normalizeMonsterConfigs` 就外推了。已修正。当前没有调用方真的去改它，所以定 High 而非 Critical。

建议：把每个 map 及其嵌套层都过一遍 `Map.copyOf`/`List.copyOf`（或字段改 `private` + 暴露只读视图）。

### H-2（High）`character_data.json` 的 `max_energy` 从未接进角色——数据造出来的角色全都没有能量条

`Character.Builder.Calculator.calculate`（`Character.java:280-295`）只用了 `health/attack/defence/speed/critChance/critAttack`。而 `CanHit.maxEnergy` 默认 `0`（`CanHit.java:88`），全仓库 `setMaxEnergy` 的调用者**只有测试**（`EnergyTest:132`、`EnergyBattleTest:76,92,107,129,166`、`BreakStateTest:64`、`ToughnessBattleTest:51,67`）。

后果：**`Character.builder().cid(x).build()` 造出来的角色 `hasEnergyBar() == false`**，于是回能全部 no-op、`castUltra` 恒 false。93 个角色的 `max_energy` 数据在引擎里是完全惰性的。

这条与 H-1 是并列的"半接线"问题：`EnergyBattleTest` 之所以绿，是因为它**手动** `setMaxEnergy`。

> **更正（2026-09-19）**：本条初稿把 `1407 遐蝶的 max_energy = null` 写成了"数据陷阱"，**那是误判**。
> 遐蝶本来就没有常规能量条，她的资源是【新蕊】（上限 `5.3125 × 队伍最高等级²`，钳到 2000；
> 来源是"我方每损失 1 点生命 +1 / 治疗量 100% 转化"，见 ROADMAP P3-0 的 A 表与 P8-8）。
> 所以 `null` 是**数据在如实表达"这一列对该角色不适用"**，是设计而非缺值。
> bean 用 `Double`（而非 `double`）正是为了容纳它 —— `double` 表示不了 null，
> 用它就不得不随便兜一个数（比如 100），那才是错的。
> 接线时只需注意**消费侧不要 auto-unbox**（ROADMAP 第 1726 行已给出正确写法
> `cd.maxEnergy() != null ? cd.maxEnergy() : 0`），这是调用方的注意点，不是数据的问题。

### H-3（High）BOUNCE 的削韧按"技能总值"逐段扣——段数越多削几倍

> ✅ **已修（2026-09-19）**：`SkillExecutor` 的 `hit(...)` 改为接收算好的 `stanceDamage`；BOUNCE 分支用
> `stanceValue(data, true) / Math.max(1, hits)` 均摊。`ToughnessBattleTest` 新增
> `bounceSplitsItsTotalStanceValueAcrossTheHits`（真实数据：达丽娅 1321 槽位 4，`hits=5`、`single=9`
> → 韧性 60 打掉 9、剩 51），变异验证确认旧代码下会红（旧行为剩 15）。
> **注**：核实过程中还发现 `params.get(1)` 在部分技能里是**小数**（如瓦尔特的 0.65、桑博的 0.28、
>  景元的 0.33），当前 `(int)(double)` 会把它读成 0 段。这些技能都不在 `DefaultSkill` 会用到的槽位上，
>  所以今天无害，但 P8-2 接真实槽位时必须一起处理（已补记到 §4 待核实项）。

`SkillExecutor.stanceValue`（`:199-207`）对 BOUNCE 走 `default` → `stance.single()`，而 `hit()` 是**每段调一次** `reduceToughness`：

```java
case BOUNCE -> {
    int hits = params.size() > 1 ? (int) (double) params.get(1) : 1;
    for (int i = 0; i < hits; i++) { ... hit(...); }   // 每段都削一次 single
}
```

`skills.json` 里全部 `hits > 1` 的 Bounce 技能（实测统计）：

| cid | 槽位 | 段数 | `stance_list.single` | 当前实现总削韧 | 若 `single` 是技能总值 |
|---|---|---|---|---|---|
| 1304 | 4 | 7 | 10 | **70** | 10 |
| 1321 | 4 | 5 | 9 | **45** | 9 |
| 1405 | 2 | 4 | 30 | **120** | 30 |
| 1506 | 21 | 6 | 30 | **180** | 30 |

自洽性判断：若 `single` 是每段值，则 1304 的 7 段合计 70 点会**强于** 1506 的 6 段合计 180 点（30/段）——强度关系矛盾；若 `single` 是总值，则 7 段分摊 10 点（1.43/段）与 4 段分摊 30 点（7.5/段）自洽。**数据支持"总值"**，且该值会原样流进击破伤害（见 H-4）。

建议：BOUNCE 每段取 `stance.single() / hits`，并确认 `all`/`spread` 是否同为总值语义；补一条"多段技能打完，总削韧 == 数据值"的测试（现在完全没有）。

### H-4（High）击破伤害用"技能标称削韧值"而非"这次实际削掉的值"

> ✅ **已修（2026-09-19）**：`Enemy.reduceStance` 改为返回实际消耗值（`Math.min(stance, amount)`，
> 已击破/非正数返回 0），`Battle.reduceToughness` 用返回值构造击破伤害。
> `ToughnessBattleTest` 新增 `breakDamageUsesTheToughnessActuallyConsumed`：先削 30 再吃一发标称 60 的战技，
> 断言结算值等于"技能伤害 + 按 30 算的击破伤害"，并加了**反向断言**（按 60 算的结果必须不同）。
> 变异验证确认旧代码下会红。
> `ToughnessTest` 里三处 `reduceStance(...)` 的返回值被丢弃，仍能通过——返回值语义是新增的，不破坏既有调用。

```java
// Battle.java:359-365
enemy.reduceStance(stanceDamage);      // Enemy.java:136 内部 Math.max(0,...) 夹过，返回 void
if (enemy.getStance() > 0) return false;
enemy.breakEnemy(element);
applyDamage(enemy, BreakDamageCalculator.build(attacker, enemy, element, stanceDamage));  // ← 仍是标称值
```

`reduceStance` 返回 `void`，实际削掉多少（可能被夹到剩余韧性）拿不回来，于是"剩 10 点韧性、30 点技能"会用 **30** 算击破伤害。`BreakDamageCalculator.build` 的 javadoc 自己把参数定义成"这一次削掉的韧性点数"，与传入值不符。

后果：任何"没一下打空、下一段才击破"的常规情形（绝大多数实战）击破伤害偏高。无测试覆盖（`ToughnessBattleTest` 恰好每次打在 0 上，`BreakDamageTest` 直接对满韧性假人调 `build()`）。

建议：`Enemy.reduceStance` 返回实际消耗值（`Math.min(stance, amount)`），`Battle` 用它构造击破伤害。

> **H-3 与 H-4 是同一条数值链的两端**：H-3 让 `single` 虚高、H-4 让虚高值原样进公式。修 H-4 是让口径自洽，修 H-3 才是修数值。建议一起改并补端到端断言。

### H-5（High）终结技先结算、后清零，把大招自己赚到的击杀/击破回能吃掉了

> ✅ **已修（2026-09-19）**：`Battle.castUltra` 把 `user.setCurrentEnergy(0)` 提到
> `requestSkill(...)` 成功之后、`processRequests()` 之前，与 ROADMAP P3-2 的顺序定义一致。
> `EnergyBattleTest` 新增 `ultraKeepsTheKillEnergyItEarned`（大招击杀 → 10 而不是 5）与
> `ultraKeepsTheBreakEnergyItEarned`（大招打空韧性 → 10）；变异验证确认旧代码下双双会红。

```java
// Battle.java:94-101
if (!requestSkill(ultra, user, targets)) return false;
processRequests();                      // 大招本体结算 ← 这里发击杀/击破回能
user.setCurrentEnergy(0);               // 然后清零 ← 赚到的被抹掉
EnergyGain ultraGain = user.getEnergyProvider().onUltCast(user, ultra);
```

`processRequests()` 会走 `applyDamage` → `grantHitAndKillEnergy`（`:281`→`:451-454`）与 `grantBreakEnergy`（`:368`），收益记给 `damage.getAttacker()`，正是放大招的人。ROADMAP P3-2 写的是"**先清零**，再让 provider 结算终结技自身回能"，代码是反的。

后果：大招**打死人或打空韧性**时本该回 5+5，实际只回 5。`EnergyBattleTest.ultraNeedsFullEnergyThenClearsAndRegainsFive` 只覆盖"没打死人"的分支，所以是绿的。

建议：把清零提到 `processRequests()` 之前。若团队意图确实是"大招放完恰好剩 5 点"，应改注释与 ROADMAP，而不是留静默丢弃。

### H-6（High）`CanHit` 拷贝构造器浅拷贝属性数组，两个实体共享每一个 `DoubleValue`

```java
// CanHit.java:127-135
public CanHit(CanHit other) {
    // need to clone
    this.attributes = other.attributes.clone();   // 只 clone 了数组，元素还是同一批对象
```

`DoubleValue.clone()` 是真深拷贝，但这条路径没调。而 buff **就是**就地改这些对象：`BoostDamageBuff.java:23` 直接 `target.getAttribute(ALL_DAMAGE_TYPE_BOOST).addModifier(...)`。

后果：任何经拷贝构造器造出的副本与模板**共享面板**——给副本挂 buff 会改到原体，`removeModifiersFrom(source, roleId)` 也会把对方的修正一起撤掉；`currentHp` 分叉而面板共享。`Character` 的拷贝构造器（`:74-79`）连 `relicSuit`/`weapon`/`skillLevel` 都记得 clone，**偏偏属性数组漏了**，注释还写着 `// need to clone`，所以是疏忽而非取舍。

当前无调用者（`grep` 确认），是埋着的雷，P7-4 波次第一个会踩。建议同时补上漏掉的 `invulnerable`（现在也没拷）。

### H-7（High）`BuffManager` 没有 buff 查询口——P4-6 照文档写会编译失败

> ✅ **已修（2026-09-19）**：`BuffManager` 加 `public boolean hasBuff(Class<? extends AbstractBuff> kind)`，
> 按 `getClass()` 精确匹配（与 `isSameKind` 同口径）、`null` 返回 false、遍历留在 manager 内部
> （延续 P1-7 不暴露内部列表的决定）。`ROADMAP.md` 的 P4-6 示例已同步改成
> `attacker.getBuffManager().hasBuff(SuperBreakBuff.class)`。`BuffManagerTest` 新增 2 条。

`ROADMAP.md` 的 P4-6（**下一个要做的任务**）给出的代码是：

```java
for (AbstractBuff b : attacker.getBuffManager().getBuffs()) { ... }
```

但 `BuffManager` 只有 `addBuff`/`removeBuff`/`canAct`/`beforeMove`/`afterMove`/`onDamage`/`afterAttack`/`clearAll`/`isEmpty`，`buffs` 是私有字段（`:12`），**没有 `getBuffs()`**。P1-7 的设计注释还专门写了"遍历留在 manager 内部，**因此不需要 `getBuffs()`**"——两处文档直接冲突。

更本质的是：**"某实体身上有没有 X 类型 buff"这个能力现在不存在**，而 P8-7 触发器表、P10-2 控制状态机都会需要它。

建议：加 `public boolean hasBuff(Class<? extends AbstractBuff> kind)`（判定留在 manager 内，延续 P1-7 的封装意图），并把 ROADMAP 的示例改成它。

### H-8（High）`Queue` 在同行动值上无裁决，**而且显示顺序与实际出手顺序不一致**

```java
// Signal.java:71-73
public int compareTo(@NotNull Signal o) {
    return Double.compare(this.nextActionTime, o.nextActionTime);   // 相等 → 0
}
// Queue.java:357-361
List<Signal> list = new ArrayList<>(heap);
list.sort(Comparator.comparingDouble(Signal::getNextActionTime));   // 稳定排序
```

`compareTo` 只比时间，`Signal` 没有平局键（`id` 字段存在但**从未赋值**，恒 0），所以 `PriorityQueue` 对相等键的顺序不受保证。而 `snapshot()` 是对**堆内部数组顺序**做稳定排序，这与 `poll()` 的顺序**不是一回事**（JDK 的 `siftUpComparable` 用严格 `<`、`siftDownComparable` 平局取左子，三个相等键 A/B/C 入堆后数组是 `[A,B,C]`，但 `poll()` 出 **A,C,B**）。

两个后果：

1. 同速单位的出手顺序取决于堆布局，而**一个不相关的事件会改变它**：`[A,B,C]` 全相等时先移除 C，数组变 `[A,B]` → B 第二个动；C 还活着时是 C 第二个动。也就是说"第三个单位死了"会静默改变另外两个的顺序。
2. 更糟的是 `snapshot()`、`getCombatant(i)`（注释写"0 = next to act"）和 `printActionQueue()` 展示的是稳定排序结果，**它们会显示错误的"下一个行动者"**。`Battle.startBattle()` 也用 `snapshot()` 决定 `onBattleStart` 的广播顺序。

这比"不可复现"更严重：这是一个**可观察的错误**（UI/日志与实际出手不一致）。修法：`Queue` 给 `Signal` 分配单调递增序号，`compareTo` 比较 `(nextActionTime, seq)`，于是出堆顺序 = FIFO = 稳定排序结果（`Signal.id` 看起来原本就是为此准备的）。

### H-9（High）`Battle.startBattle()` 从未被任何测试调用——开场事件链零覆盖

`startBattle()` 只出现在 `Battle.java:106`（定义）与 `Main.java:148`（demo）；23 个测试类里 **0 次调用**。于是 `BattleEvent.onBattleStart` 从来没在测试里触发过，而 ROADMAP P3-0 的 C 表把"战斗开始回能"列为一**整类**角色机制（瓦尔特+30、银枝+20、藿藿+30、缇宝+30、长夜月+70……）。

另外 `CanHit.onBattleStart` 默认转发给 `BuffManager`（`CanHit.java:273-276`），但 `BuffManager` **没有** `onBattleStart`，所以"开场挂 buff / 开场回能"目前只有 `CanHit` 上那个测试用 `Runnable` 一条路。

建议：补 `BattleStartTest`（两个角色带回调 → 断言都被调用，并用一个开场 `requestSkill` 验证 `processRequests()` 走过）。这条测试会顺带逼出"开场回能该挂哪"的设计。

---

### Medium

| # | 位置 | 问题 |
|---|---|---|
| M-1 | `Queue.java:226` | `setTopZero()` 重置**堆顶**而非 `currentActor`（`:65` 有该字段却没用）。今天恰好成立（`Battle.afterMove` 紧跟 `move()`，且此刻无人严格早于 `elapsed`），但一旦在 `move()`→`afterMove()` 窗口内动了键就会错：`delayAction`（把行动者推后 → 换个非行动者当堆顶，行动者的周期重置被丢弃）或 M-2 的浮点情形（被拉条者落到 `elapsed` 之下 → **行动者连动两次**，拉条白给）。`currentActor == null` 时调用还会静默跳过某个单位的回合。类 javadoc 说 `nextActionTime += 10000/speed`，实际是 `elapsed + cycleTime()`（会丢掉加在行动者身上的延迟） |
| M-2 | `Queue.java:328` | `advanceActionByPercent` **缺 `advanceAction` 有的 clamp**：`a-(a-e)*p >= e` 在 binary64 下不成立。实测（p=1.0，`elapsed=10000/源速度`，目标速度 80..400）**102961 组里 8814 组结果严格小于 `elapsed`**（差 4.5e-13~7.4e-13）。而 `move()`（`:213-215`）把 `elapsed = next.getNextActionTime()` **不做 clamp** → 全局时钟倒走，同时 `timePassed` 却报 0。违反方法自己声明的"percent=1.0 → `nextActionTime == elapsed`" |
| M-3 | `AttributeBuilder.java:124` | `build()` **直接交出内部 `DoubleValue`**（未 clone）。`getOrCreate` 用 `computeIfAbsent` 缓存，所以同一 builder 两次 `build()` 拿到同一批对象，之后再 `addPercent`/`addPure` 会改到已造好的实体面板。与 H-6 同族（实体侧 / builder 侧各一个） |
| M-4 | `EnemyFactory.java:59` | 只灌了 5 个属性，**漏了 `EFFECT_HIT_RATE`**。`EnemyScaler` 已算出（冰锋·组1·Lv90=0.32、组3·Lv120=0.5）、`EnemyStats` 也带着，但没进 `Enemy` → P6-1 时敌人命中率恒 0，`effectHitRate` 成了"看着被用了其实没有"的死输出 |
| M-5 | `BuffManager.java:109` | `clearAll()` **不重置 `blocked`**：控制 buff 在 tick 里到期置位后，清空列表后 `canAct()` 仍返回 false 直到下次 `beforeMove()`。`BuffManagerTest.clearAllRemovesEverything` 从不走 `blocked` 路径所以没抓到 |
| M-6 | `BuffManager.java:54` | `blocked` 机制**只对 early buff 生效**：`beforeMove()` 先把标志清掉再 tick，而后置控制 buff 是在 `afterMove()` 到期的 → "它最后一回合"什么也挡不住。"晕眩最后一回合是否还挡"取决于 tick 时序而非控制语义。`StunBuff` 恰好是 early，测试也只覆盖这一种 |
| M-7 | `Character.java:84,91` | 两个 `fromAttributes` 工厂各自残留 null：`(Translate, DoubleValue[])` 版 **`skillLevel` 为 null** → `setSkillLevel`/`setSkillByClass` 直接 NPE；`(String,double...)` 版 **`relicSuit`/`weapon` 为 null** → `getWeapon()`/`getRelicSuit()` NPE。测试用的都是后者，所以两个 NPE 都是潜在的 |
| M-8 | `Character.java:235` | `build()` 把自己的可变 `relicSuit`/`weapon`/`skillLevel`(EnumMap) 直接交给角色（无防御拷贝）→ 同一 builder 造的两个角色共享一套遗器（叠上 M-10 的"只增不减"）与一个 map，`c1.setSkillLevel(SKILL,9)` 会改到 `c2` |
| M-9 | `Weapon.java:71` | **光锥永远取叠影 1 的被动**：`weaponSkillData.getFirst()`，而该表按叠影档位索引（`"level":1..5`），`Weapon` 没有叠影字段/参数 → 23042 永远 +18% 速度，拿不到 +21%…+30%。`getFirst()` 对空列表抛 `NoSuchElementException`（169 件内置光锥都非空，测试手写的 `WeaponData` 不一定） |
| M-10 | `RelicSuit.java:55` | `addToSuit` 覆盖槽位字段（`body = relic`）但 **`total.add(relic)` 不移除旧的** → `addMore(bodyA, bodyB)` 把两件身甲一起算进面板（白送一件遗器）。还让 `clone()`（只从 6 个槽位字段重建）与原件**不一致**——同一套遗器的两个克隆可以有不同的面板 |
| M-11 | `CanHit.java:242` | `gainEnergy` 可能返回**负值**把能量减下去：`ENERGY_REGENERATION_RATE <= -1`（或经 public `setCurrentEnergy` 造成 `currentEnergy > maxEnergy`）→ `Math.min(maxEnergy-currentEnergy, gain*eff)` 为负。另外 `amount()` 为 `NaN` 时 `NaN <= 0` 为 false、守卫拦不住，`currentEnergy` 永久 NaN → `isEnergyFull()` 从此恒 false（大招再也放不出来） |
| M-12 | `BuffManager.java:70,82` | `onDamage`/`afterAttack` **遍历活列表**，而这两个回调恰恰是"buff 再挂 buff"的地方（附加伤害→新易伤；`ExtraTrueDamageTest.DamageReactor` 已是原型），`addBuff`/`removeBuff` 会改 `buffs` → CME 或静默跳过。`processBuffTick` 的 `removeIf` 里调 `tickEffect` 同理 |
| M-13 | `AbstractBuff.java:52` 等 6 处 | 生产代码留着 `IO.println`，其中 `decreaseDuration` 那条**每次 buff tick 都打**。`log4j2.xml` 已配好、`slf4j`+`log4j-slf4j-impl` 也在依赖里，但**主代码 0 处使用 logger**（只有 `Main.java:21` 一行注释掉的）→ P10-3 之后一场战斗日志绝大部分是这行 |
| M-14 | `SkillData.java:41,82` | **未知 `cid`/`skillId` 静默返回"假的非伤害技能"**（`EMPTY` = `PHYSICAL` + `ENHANCE` + 空参数）：打错/未实现的 id 与被动技能无法区分 → 0 伤害、无报错、施放者照样回能。`@throws` 只覆盖"存在但 skill_effect 坏了" |
| M-15 | `SkillData.java:20,56` | javadoc 声称 "Access is immutable read-only"，但 `skills` 及其内层 list 是 Gson 造的**可变** `ArrayList`（`Collections` 只用于 `EMPTY_PARAMS`）。而 `DefaultSkill.DATA_CACHE` 把同一实例共享给所有实体/克隆/战斗 → 一次 `getSkills().get(0).set(...)` 污染全进程 |
| M-16 | `Character.java:104,243` | `int skillId = 1` / `new DefaultSkill(cid, 1, level)` **把六个技能槽全解析成槽位 1（普攻）** → 普攻/战技/终结技/天赋/秘技/迷宫共用普攻倍率与 `stance_list`。ROADMAP 已登记为 P8-2 占位；补两个被忽略的连带点：① `Main` 的 demo 展示的**所有**伤害数值都是错的；② `SkillData.EMPTY` 的静默零伤害会把将来"技能没伤害"的 bug 伪装成"设计如此"（建议 P8-2 改 fail fast） |
| M-17 | `JSONReader.java:25` | javadoc 写 "or `null` if the resource is missing"，实际 `Objects.requireNonNull` **抛 NPE**；在 `Constant` 静态块里就变成 `ExceptionInInitializerError`，此后该 JVM 内**每次**访问 `Constant` 都 `NoClassDefFoundError`，无法恢复。`Benchmark.java:40` 的 `data == null` 分支因此**永远走不到** |
| M-18 | `MonsterDataTest.java:56` | `everyModifyRatioIsPresentAfterNormalisation` **不可能失败**：`normalizeMonsterConfigs` 总会用 `orOne` 把缺失系数替成 1.0，所以它断言的是归一化自己的后置条件。即使 `health_modify_ratio` 整列从数据里消失，它照样绿。（`hpRatioUsesHealthModifyRatioAndNotTheGhostField` 钉了真实值，所以"整列丢失"仍会被别处抓到，但这条测试本身零信息） |
| M-19 | `AttributeBuilder.java:95` | `addPercentPoint` 的 javadoc 说"**不应**用于 `HEALTH_PERCENT` 等"，但它实现成 `addPure`，而 `addPure` 会把 percent 类型**重定向成平值**（`health_percent → HEALTH`）→ `addPercentPoint(HEALTH_PERCENT, 0.3)` 会加 0.3 点血而不是 30%（且 `build()` 反正会把 percent 槽位置 null，没有任何断言能发现）。今天安全**只是因为** 4 个调用点各自重复了 `PERCENT_TO_BASE.containsKey` 守卫——整条属性管线的正确性依赖这个守卫被抄 4 遍。建议让该方法直接拒绝 percent 类型 |
| M-20 | `MapUtils.java:48` | `getRandomList` 用 `ints().distinct().limit(n)` 的拒绝采样：`sampleSize` 接近 `list.size()` 时期望工作量是 `n·H(n)` 且**尾部无界**（流的 int 生成数没有上限）；`sampleSize < 0` 会静默返回空列表。另外它先解引用 `list` 再谈契约 → null 给的是裸 NPE 而非 javadoc 承诺的 `IllegalArgumentException`。当前调用点（11 选 3–4）安全 |
| M-21 | `EliteGroup.java:17` | record **没有 `@SerializedName`**，而它自己的 javadoc 就在警告"别重演 `HardLevelGroup` 键名不匹配静默读 0 的坑"。实测 Gson 会把 `{"healthRatio":6.2}` 绑上，但项目导出的风格是 `health_modify_ratio` 这类 snake_case、tbgd 是 `HPRatio` → 一旦真加 `elite_group.json`，用 `{"HPRatio":6.2}` 会让**除一个字段外全部读成 0.0**，血量乘 0，且无校验。当前 `EliteGroup` 只被 `EnemyScaler` 及测试用到，所以今天不炸 |
| M-22 | `SkillData.java:47` | `maxLevel` **从未被读取**（无 `getMaxLevel()` 调用者），而 `SkillExecutor.java:86-89` 在 `level-1` 越界时**静默 return** → 0 级或超上限技能零伤害且无报错。存在的校验字段是死的 |
| M-23 | `TestSkillGroup1.java:15` | 测试脚手架放在 `src/main`：静态初始化里 `SkillData.init(1001, 3)` 硬编码 cid/槽位、绕过 `DefaultSkill` 缓存、仅仅加载该类就会强制全量数据加载（可能 `ExceptionInInitializerError`）。注释说是"单体冰伤"，而 cid 1001 槽位 3 实测是 **AoEAttack**（`SkillExecutorTest:221` 知道这件事）；`:37-38` 还描述了一个不存在的技能 |

### Low / Nit

| # | 位置 | 问题 |
|---|---|---|
| L-1 | `AttributeType.java:115` | `fromString` 的"大小写不敏感"是**假的**（key 小写、查询原样），javadoc 却说 case-insensitive；`DamageType.fromString` 做了 `toLowerCase(Locale.ROOT)`，这里漏了。唯一调用点 `Weapon.java:72` 吃的 `weapons.json` 610 处取值全小写 → 潜在而非现行。顺带 `:4` `EliteGroup`、`:7` `ElementType` 两个 import 未使用 |
| L-2 | `Signal.java:67`、`Queue.java:50`、`Battle.java:388` | `10000` 这个行动周期常量在三处硬编码（`Queue` 有自己的 `ACTION_THRESHOLD`，另两处是裸字面量），违反"数值一律进 `Constant`"；`Signal.java:17-18` 还把该公式用散文复述了一遍，会漂移 |
| L-3 | `Signal.java:59` | `refreshSpeed()` **跳过**构造器有的 `speed > 0` 校验，之后 `cycleTime()` 对 ≤0 返回 `Double.MAX_VALUE` → 一个 0/负速 debuff 会让该单位**永远不再行动**而不是 fail fast。javaoc 大喊 "IT'S IMPORTANT TO CALL WHEN CHANGING SPEED!!!" 却无人调用（见 H-8 附近的 M 级问题） |
| L-4 | `Signal.java:23,81` | `Cloneable` + `clone()` 无人使用，而克隆会产生**同一 `CanHit` 的第二个 Signal**，`addCombatant` 的身份去重拦不住 → 潜在"一个单位一个回合动两次"。`id` 字段文档写"Unique identifier"却从未赋值/读取（恒 0），看起来正是 H-8 那个修复的半成品 |
| L-5 | `Queue.java:47` | 类级 `@Getter` 暴露 **`getHeap()`（活的内部 `PriorityQueue`）**，调用方可直接破坏排序不变量；`resetSignal(Signal)`（`:239`）与 `getCombatant(int)`（`:129`）无调用者；`rebuildHeap()`（`:386`）也无调用者 |
| L-6 | `Queue.java:175` | `removeCombatant` **不清 `currentActor`** → `Battle.afterMove()` 移除死亡行动者后，`queue.getCurrentActor()` 仍返回那个死人的 Signal 直到下次 `move()`；而 `Battle` 另有一个 `currentMove`，两个"当前行动者"可能不一致 |
| L-7 | `Battle.java:509` | 无敌目标的 DOT **照样消耗层数**却零伤害（`tickDots` 无条件结算 + `BuffManager` 无条件倒计时，`applyDamage` 对 `isInvulnerable()` 返回 0）→ 转阶段无敌的 Boss 白吃 DOT 结算次数。（同段"被 DOT 打死就 break"是对的，不鞭尸）⚠ P10-0 后行号已变（原 `Battle.java:145`），**缺陷本身未变**；修法变了：现在是"跳过 `beforeMove` 里的倒计时"，不再是 `Dot.tick()` |
| L-8 | `Battle.java:257` | `addRequest` 是 private 且**零调用者**，`addRequestItems` 却是 public 字段且每次 `processRequests()` 都遍历；`Summon` 因此从未被实例化。而 `Battle.enemies` 是 `List<Enemy>` 不是 `List<? extends CanHit>` → 敌方召唤物**无处安放**（ROADMAP P9-4 没写"要先放宽类型"）。`advanceRequest`（`:559`）也是 public 零调用 |
| L-9 | `DoubleValue.java:310-335` | 六个 `Modifier.*PercentNumber` 工厂 + `Modifier.pure(double)` **零调用者**，而它们除以 100、旁边的 `addPercent(double)` 不除 → 命名毫无提示，典型 100× 陷阱。建议删掉或改名 `fromWholePercent` |
| L-10 | `BreakDamageCalculator.java:41`、`DoubleValue.java:311+` | `/10.0`、`/100.0` 写死在代码里（违反 L-2 同一条约定）；`/10` 这个数据约定在类 javadoc、`Constant.BREAKING_RATE` javadoc、两个测试里**各自复述一遍**，改一处不会连带提醒 |
| L-11 | `AttributeBuilder.java:43` | `setBase`/`addBase` **不做** `PERCENT_TO_BASE` 重定向（`addPure`/`addPercent` 做），而 `build()` 又把 percent 槽位置 null → `setBase(HEALTH_PERCENT, 0.43)` 静默消失。当前无调用者传 percent 类型 |
| L-12 | `DotBuff.java:89` | 构造器不校验：`turns <= 0` 会"第一次结算就过期"（⚠ 迁移后更隐蔽：结算发生在倒计时**之前**，所以 0 回合的 DOT 仍会打出**一次**伤害再消失），任意元素都收（`BREAK_EFFECTS` 的过滤只在 `Battle.attachBreakDot`），负 `baseDamage` 静默不造成伤害。⚠ 文件已由 P10-0 从 `Dot.java:42` 迁到 `DotBuff.java`，**缺陷原样随迁，未修** |
| L-13 | `EnemyScaler.java:52` | `scale` 对 template/config/group 无 null 检查，缺 id 直接 NPE 且不说是哪个 id（`EnemyFactory` 有守卫，测试与将来直调没有）。`config.hpRatio()` 非空只因为 `normalizeMonsterConfigs` 填过 |
| L-14 | `AbstractBuff.java:47` | `isSameKind` 用 `getClass()` 身份比较 → 任何子类/匿名子类（`DamageHookTest:118`、`ExtraTrueDamageTest:222` 都是子类）**永远不与父类同类** → 同类替换失效、无限叠加。建议改为 buff 自报的 `kind()` 键 |
| L-15 | `Relic.java:174-183` | `checkLegal` 只用 `Setting.getMainAttributeLevel()` 校验，而 `:149` 实际用 `level` 参数造主词条 → JSON 里的主词条等级是死数据（两者矛盾也能通过）。子词条上界那条消息把**违规值**插进 "between 0 and N"（对违规 5 报"0 到 8"，真实上界是 2），且接受负 `attributeLevel` |
| L-16 | `Relic.java:92,124,329` | `getSubAttributes()` 的可变性**随工厂而变**：`create` 别名调用方的 list、`Builder.build` 返回 `ArrayList`、`createRandomLevelZero` 返回 `Stream.toList()`（不可变）→ 同一个 API 有时 `add` 成功、有时抛 `UnsupportedOperationException` |
| L-17 | `Relic.java:329` | `Relic.Builder` 不执行 `createRandomLevelZero` 有的任何不变量（`:121-122` 会把主词条从候选池剔除）：允许重复副词条、允许副词条等于主词条、不校验 `attributeLevel` 上界与 `star` → 手搓遗器可以 HSP 两次、面板虚高 |
| L-18 | `SkillPoint.java:31` | 静态 `CACHE<Integer, List<SkillPoint>>` 持有**公共可变字段 + 公共可变 `children`** 的节点，同 cid 的所有角色共享；javadoc 写 "once per character" 其实 "per cid, process-wide" → 任何调用方改一个节点会污染之后所有同 cid 角色。`Character.java:233` 还无条件套用**整棵**行迹树，没有解锁门槛 |
| L-19 | `ExtraBasicPromote.java:29` | 平属性用 `addPure`（在百分比**之后**加），而 javadoc 说"加到基础属性"，光锥平属性用的是 `addBase`（百分比**之前**）→ 同样 +500 HP，行迹的不会随 HP% 放大、光锥的会 |
| L-20 | `ExtraBasicPromote.java:14` | 分量顺序是 `(health, defence, attack, speed, …)`，其它模型一律 `(health, attack, defence)`（`Weapon`/`CharacterData`/`fromAttributes`）→ 位置构造会**静默交换 ATK/DEF**。仓库内调用点这两项都传 0 所以没暴露 |
| L-21 | `SkillAttackType.java:6` | 枚举 `SINGLE/THREE/ALL` **全仓库零引用**，且值与 `skills.json` 的 `attack_type`（Normal/BPSkill/Ultra/Maze/MazeNormal/Assist/ElationDamage/null）**完全对不上** → 将来有人拿它去映射数据会静默什么都匹配不到 |
| L-22 | `Relic.java:114`、`MapUtils.java:28,53` | `createRandomLevelZero` 的 `star <= 2` 把**合法的 2★** 拒了（javadoc 与异常消息都说 2-5，`main_attribute.json` 也有 `"2"` 表）；随机数走 `ThreadLocalRandom`（不可播种）**违反"随机数一律走注入 `Random`"** → 随机遗器不可复现、不可测 |
| L-23 | `Camp`/`Summon` | `getCamp()` 全仓库无调用者 → `CanHit.camp` 只写不读、`Camp.NEUTRAL` 未用，与 `Camp` 的 javadoc 矛盾；`new Summon` 全仓库零处，其 javadoc 承诺的"继承召唤者属性"没有任何实现 |
| L-24 | `beans/Skill.java:11,15` | `skillID` 解析了但从不读取（槽位 id 来自外层 map 键，无人校验两者一致）；`StanceList` 无 `@SerializedName`，靠 record 分量名匹配（今天 702/702 都对，但改名会静默读 0 → 削韧恒 0） |
| L-25 | `SkillEffectType.java:88,97` | `getCategory()` 无调用者；每个常量上的 `@SerializedName` **从不生效**（`beans.Skill` 把 `skill_effect` 留作 `String`，`init` 走 `fromString`）；`BY_STRING` 是可变的 static `HashMap`（可 `Map.copyOf`） |
| N-1 | `DefaultSkill.java:16,10` | `static ConcurrentMap` 缓存把可变对象当共享单例（`computeIfAbsent` 只保证创建一次，不保护 value）；`SkillData.init` 会在 `computeIfAbsent` **内部抛异常** → 一个 getter 在战斗时抛错且每次调用都重抛；`:10` 的 TODO 说该删除这个类，而它是唯一的 `Skill` 生产实现 |
| N-2 | `Benchmark.java:38` | 读的 `dump_data.json` 仓库里不存在；`data == null` 的兜底永远走不到（见 M-17 先抛 NPE）；`main()` 是包私有、`gradle run` 进不去；`:91-92` 的结果被丢弃 |
| N-3 | `CanHit.java:99-104` | 同时有 `Runnable` **字段** `beforeMove`/`afterMove`/`onBattleStart` 与**同名方法**。Java 合法但极易读错（`Main.java:137` 正是这种用法）；更危险的是直接调 `MoveEvent.beforeMove(battle)` 会**静默跳过 buff tick**。建议改 `setBeforeMoveHook(Runnable)` |
| N-4 | `Enemy`/`Summon` | 无拷贝构造器：`new Enemy(...)` 走 `CanHit(CanHit)` 会**丢掉** `damageResist`/`stanceWeak`/`stance`/`broken`/`dots` 全部子类字段（`Character` 就正确重写了）。当前无调用者，但"多波次刷同一种怪"很容易用到 |
| N-5 | `RelicSuit.java:153` | `appendAttribute` 是死方法，与它逐行相同的代码被内联在 `appendTo`；同一段"按属性类型分发"逻辑在 `RelicSuit`/`Weapon`/`SkillPoint` **三处复制**。建议下沉成 `AttributeBuilder.add(type, value, source)`（顺带消掉 M-19 那个 4 处重复的守卫） |
| N-6 | `models/Buff.java:3` | 接口上留着裸 `// TODO` 与重构便签；`Buff.setSource/getSource` 全仓库无人调用（持有者经构造器传），`AbstractBuff.source` 恒 null → 接口契约实际未实现 |
| N-7 | `BreakDamageCalculator.java:21` | javadoc 例子"30 点普攻 × 2.5 击破加成 = 112.5"在仓库里**无法复现**（没有任何 2.5 系数，测试直接传 112.5 字面量），会误导读者以为调用方要预乘 |
| N-8 | `Queue.java:269,299,324` | 在 `for-each` 遍历 `heap` 的同时改键，然后 `rebuildHeap()` 清空同一个 heap —— **只因为每个分支立刻 `return` 才安全**；任何人在 `rebuildHeap()` 之后补一句就会 CME |
| N-9 | `QueueTest.java:17,42` | 对没有 `equals` 的 `Character` 用 `assertEquals` 其实是身份断言（`assertSame` 更能表达意图）；`:18,29` 的 `50` 是从私有 `ACTION_THRESHOLD` 推出来的 |
| N-10 | `SkillData.java:4,97` | 在声明了 `models.Skill` 的包里 import `beans.Skill` 合法但易混（`SkillExecutor:201` 得用全限定名绕开）；6 参 `@AllArgsConstructor` 里有两个 `List` 参数，调换顺序就能静默编译通过并错绑 |
| N-11 | `RelicMainAttribute`/`RelicSubAttribute` | 同一个"非法 star"错误一个抛 `IllegalArgumentException`、一个抛 `IllegalStateException`（都写了 javadoc，所以不是 bug，但逼调用方 catch 两个无关类型）；且 `getAttributeByStar` 返回 Gson 造的可变 map，调用方可改掉它、破坏之后所有遗器构建（与 H-1 同类，修 H-1 时一并解决） |
| N-12 | `LevelPromotionCalc.java:23,44` | 边界行为无文档：`level == 80` 的 clamp 让突破/未突破**相等**（70/79/90 却不相等）；光锥的倍率从 20 级未突破 3.85 **降到** 21 级突破后的 3.60（是游戏行为，但反直觉到该写注释）。未发现整除或 off-by-one 缺陷 |

---

## 3. 建议的修复顺序

1. **C-1**（buff 持有者侧别）—— 唯一一条 Critical；修法小（记持有者 + 判 `getDefender()/getAttacker()`），且必须在第一个 debuff 技能落地前修掉，否则会污染对伤害数值的信任。
2. **H-3 + H-4**（削韧与击破伤害口径）—— **当前就在算错数值**，一起改并补"多段技能总削韧 == 数据值"的端到端测试。
3. **H-1 + H-2**（`Constant` 不可变化 + `maxEnergy` 接线）—— 都是"把已经读出来的东西接对"，且会影响 P3/P6/P10 的后续判断。接线时对 1407 的 `null` 要**按"该角色无常规能量条"处理**（不是兜底成某个数值）。
4. **H-7**（`BuffManager.hasBuff`）—— 不修就写不出 P4-6。
5. **H-5**（终结技能量顺序）—— 一行位置调整 + 一条"大招击杀回能"测试。
6. **H-6 + M-3 + M-8**（三个"该 clone 没 clone"）—— 一起修、一起补"给副本挂 buff 不影响原体"的测试。
7. **H-8 + M-1 + M-2 + L-3 + L-4 + L-6**（行动条一组）—— 一次改完一起测（平局序号 + `setTopZero` 认人 + clamp + 速度校验 + `currentActor` 清理）；**必须在 P10-4 之前**。
8. **H-9**（`startBattle` 测试）—— 半天工作量，补上整条开场链。
9. **M-5 + M-6 + M-11 + M-12 + M-13**（BuffManager 一组）—— 逐条独立、都不大。
10. **M-4 + M-9 + M-10 + M-14~M-23**（各模型正确性）—— 按需排期。
11. **清理项**（L/N 全部）—— 可并入下一次提交顺手做掉；其中 `M-19`/`N-5` 建议合并处理（抽出 `AttributeBuilder.add(...)` 一并消掉 3 处复制与 4 处重复守卫）。
12. **M-17 + M-18 + H-1 的数据校验**（README 警告 + 友好报错 + 会红的数据断言）—— 影响下一个克隆仓库和 CI。

---

## 4. 待最终核实项

1. **BOUNCE 削韧的官方拆分规则**：H-3 的结论建立在"`stance_list.single` 是技能总值"的数据自洽推断上。若实际是"每段值"，H-3 应降级为"口径待确认"——但**无论哪种，当前实现把总值当每段用都需要一条测试来钉死语义**。（已按"总值"落地并加了测试；若日后拿到官方口径说相反，改 `SkillExecutor` 那一行 + 那条测试即可。）
1b. **`params.get(1)` 在小数段数技能上的语义**（修 H-3 时新发现的）：`skills.json` 里多段技能的"段数"字段不全是整数——瓦尔特的 0.65、桑博的 0.28、景元的 0.33、米沙的 0.36、菲农的 0.225、银狼 999 的 0.0。当前 BOUNCE 分支用 `(int) (double) params.get(1)`，对这些值会算出 **0 段**（技能变成完全不出手）。今天无害，因为 `DefaultSkill` 只会解析到槽位 1，而这些小数段数技能都在槽位 2/4/3/8/11/21；**但 P8-2 接真实槽位映射时必须先搞清这个字段的真实语义**（是段数？是段数缩放？还是别的），否则会出现"放技能没反应"。
2. **`StandardEnergyProvider` 读的 `attack_type` 是否真能拿到值**（分区审查提出，我未能证实）。`EnergyTest.standardProviderMapsSkillSlotsToBaseEnergy` 是绿的，但它**手写 `SkillData`**，走不到 `Constant.SKILLS` 的真实字段绑定，所以测试绿不能证明真实路径正确。建议补一条"从 `Constant.SKILLS` 取真实普攻/战技 → 断言回能 20/30"。
3. **`H-8` 的精确出堆顺序**：A/C/B 那个例子是从 JDK 的 `siftUpComparable`/`siftDownComparable` 推导的（沙箱内无法执行 Java 验证）。但结论不依赖该例子——"平局无保证、迭代顺序 ≠ 出堆顺序"是 `PriorityQueue` 的文档行为，而"`snapshot()` 显示的顺序会与实际出手不一致"这一点已经足够成立。
4. **C-1 的修法归属**：`Battle.assemble` 到底该不该向攻击方广播 `onDamage`？若"攻击方侧 buff 也要能反应"是设计（虚弱就是攻击方侧），则 C-1 必须在 **buff 内部**判持有者；若否，则要在 `Battle` 里按侧分发。两者不能都保持现状。
5. **H-5 是否有意为之**：若意图是"大招放完必定恰好剩 5 点"，应改注释与 ROADMAP，而非改代码。
6. **击破 DOT 缺 `(1 + 击破特攻)`**：`Battle.attachBreakDot` 用 `breakBaseOf × DOT_RATIO`，不含击破特攻；`DOT_RATIO` 已标"示例值 TODO data"，`DotTest:95` 把无 BE 的值锁死了。若 BE 应参与，测试也要改。
7. **`EnemyStats.effectHitRate` / `ELATION_DAMAGE_BOOST` / `elation_basic_level_damage.json` 是否是有意的 P6-1/P8 占位**：若是，需要的只是"未接"标记而不是行为改动。（补充实测：`elation_basic_level_damage.json` 共 101 条、80 级 = 7535.107，**零处被引用**，`Constant` 也不加载它。）
8. **`stage.json`(9 MB) / `challenge_*.json` / `character_id_mappings.json` / `data_path.txt` 从不由 classpath 加载**：全仓库 `grep` 只命中 `JSONReader` 的实现本身。推测它们是导出器的参考产物，但**若它们本该是 P7-4"等级组/等级来自关卡"的数据源，那这条线是断的**。
9. **`Character.Builder.build` 无条件套用整棵行迹树**（`Character.java:233`）是否是"L80 全解锁"的有意简化——只有一条穷举测试对得上它。
10. **`speedRatio`/`stanceRatio` 在全部 2649 条里恒为 1.0**：可能是数据/导出问题，导致 `EnemyScaler` 这两条乘法路径**永远没有真实数据覆盖**。
11. **`EnergyGain.fixed` 目前无生产使用**：效率旁路分支只有测试在走（`StandardEnergyProvider` 五个钩子全用 `normal`）。不是缺陷，但要知道这条分支在生产路径上是空的。
12. **`getActionLength` 混用了"当前 speed"与"旧 speed 算出的 `nextActionTime`"**：中周期变速时的语义未定义（该值目前只用于显示）。
13. **Lombok/编译行为是推断的**（未能真编译）：`Relic.Setting` 依赖 Lombok 在已有同名零参方法时**不**生成 getter（否则重复方法报错）；`Character.java:78` 依赖 `EnumMap.clone()` 返回 `EnumMap`。都与"当前能编译"一致，但未经实际构建验证。
