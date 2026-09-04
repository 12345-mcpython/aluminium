# aluminium 开发路线图

一个用 Java 编写的类《崩坏：星穹铁道》回合制战斗引擎。数据来自
[Dimbreath/turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata)，
已解析到 `src/main/resources/data/`。

> 机制规则参考 `[HSR.md](HSR.md)`(战斗机制完整总结 AI 实现版)。
> 任务模板统一为：**目标 / 涉及文件 / 验收**。✅ = 已完成。

## 进度概览

| 模块 | 进度 |
|---|---|
| A0 元素枚举（`DamageElement`） | ✅ 已完成 |
| A1.1 技能携带元素/效果（`beans.Skill.element` + `SkillData.effect`） | ✅ 已完成 |
| `SkillEffectType` 11 值完整分类 | ✅ 已完成 |
| A1.2 ~ A8（Damage 类 + 精确乘区 + 击破/能量/辅助机制） | ⬜ 未开始 |
| B / C / D / E 线路 | ⬜ 未开始 |

## 当前状态（已完成）

- 战斗循环：`Battle` / `Queue`（行动条 `10000 / speed`、推条/拉条）
- 伤害结算：`Battle.calculateDamage` / `applyDamage`（简易暴击、简易乘区）
- 技能系统：`Skill` 抽象类 + `DefaultSkill` + `SkillData`（倍率参数懒加载）
- 属性系统：`DoubleValue` 修饰符 + `AttributeBuilder` + `CanHit#getAttribute`
- 角色构建：`Character.builder`（等级/遗器/光锥/星魂加成）
- 光锥、遗器：`Weapon`、`Relic`、`RelicSuit`（主词条/副词条/套装）
- Buff 系统：`BuffManager` + `AbstractBuff`（含 3 个示例 Buff）
- 数据加载：`Constant` + `JSONReader`（角色/光锥/遗器/技能/怪物原始 JSON）
- 元素分类：`DamageElement`（7 元素；`fromString` 对 `"Unknown"` 返回 null）
- 效果分类：`SkillEffectType`（11 值，覆盖 `skills.json` 全部 `skill_effect`）
- 单元测试：`src/test/java/com/laosun/aluminium/test/`（7 个测试类）

## 缺失（本路线图目标）

敌人回合被跳过、无弱点/韧性/击破、无能量消耗、无等级体系（防御区/击破基数依赖等级）、
伤害公式未按 HSR 精确乘区（缺易伤/减伤/虚弱/防御区/抗性穿透边界）、无仇恨系统、
无效果命中/抵抗、无治疗/护盾乘区、Summon 无忆灵模型、无胜负判定、无关卡、无界面。

---

# 线路 A：HSR 战斗核心机制补全（推荐优先）

依赖链：`A0 → A1 → A2 → A3 → A4 → A5`；`A6` 可并行。

## A0 基础设施：元素枚举 ✅ 已完成

### A0.1 元素枚举 ✅
- **目标**：`PHYSICAL / FIRE / ICE / THUNDER / WIND / QUANTUM / IMAGINARY`，
  带 string 名（`"Fire"` 等），与 JSON 的 `stance_weak`、`damage_resistance` 的 key 对拍
- **涉及文件**：`enums/DamageElement.java`（已实现）
- **验收**：`DamageElement.fromString("Fire") == FIRE` ✅（`A0VerifyTest`）

### A0.2 元素 ↔ 属性映射 ✅
- **目标**：`DamageElement` ↔ `AttributeType`（`FIRE` ↔ `FIRE_DAMAGE_BOOST`）
- **涉及文件**：`AttributeType.getBoostByElement(...)`（已实现）
- **验收**：每个元素都能取到对应的 `X_DAMAGE_BOOST` ✅（`A0VerifyTest`）

## A1 Damage 类：伤害统一结算

> 为什么：① 多段/多目标技能需要多次结算同一套乘区，`calculateDamage` 返回 `double`
> 无法携带元素/暴击/伤害类型信息；② 乘区分组是 Buff 拦截（增伤/易伤/减伤/虚弱）的
> 唯一扩展点；③ 击破/超击破/持续伤害复用同一流水线。
> **依赖链**：`A0 → A1 → A2 → A3`。

### 【设计决策】非伤害技能没有 element，如何处理

- **原则 1：`Element` 只描述「伤害元素」，HSR 不存在无元素伤害——物理也是元素。**
  `Damage.element` 非空 + `requireNonNull` 断言；数据中 `"Unknown"` → null（非伤害技能）
- **原则 2：`Damage` 只服务「伤害段」。治疗/护盾/回能/上 Buff/控制/召唤都不构造
  `Damage`**。已由 `SkillEffectType.Category` 承载：
  仅 `DAMAGE`（SingleAttack / MazeAttack / AoEAttack / Blast / Bounce）构造 Damage；
  其余走效果执行
- **原则 3：既伤害又治疗的技能**：先结算 `Damage`、再执行效果段，互相独立
- **原则 4：Dot 与击破/超击破伤害也是 Damage**——element 由来源提供，不得为 null
- **判别口诀：有伤害要结算 ⇒ 构造 `Damage`；不造 Damage ⇒ 无伤害**

### 【设计决策】技能对多目标怎么办

- `Damage` 是**「单段 · 单目标」**单元，不持有目标列表；一次技能 = N 个 `Damage`：
  `SINGLE_ATTACK`→1、`AOE_ATTACK`→全体各 1、`BLAST`→主目标+相邻、`BOUNCE`→循环 N 次
- 每个 `Damage` **独立判定暴击、独立结算**（HSR 每段伤害各自 roll，多段暴击独立）
- 多段技能（弹射/扩散）有伤害分裂比，单段计算需计入；将来如需汇总，引入 `SkillCast`

### 【设计决策】乘区可以在 Battle 中处理吗——分工如下

- **`Damage` 只负责乘区运算**：纯代数、无属性读取、无 `CanHit` 依赖 → 独立单测
- **`Battle` 负责乘区数据装配**：`calculateDamage(...)` 是唯一伤害入口，按顺序：
  1. 读攻击方增伤属性 → 增伤乘区（A1.5）
  2. 掷暴击 → 爆伤乘区（A1.4）
  3. 读防御等级/减防穿透 → 防御乘区（A1.9）
  4. 读抗性/穿透 → 抗性乘区（A2.3）
  5. `onDamage` 钩子：Buff 注入易伤/减伤/虚弱（A1.7）
  6. `toValue()` 结算 → `applyDamage`
- **好处**：每个乘区贡献可见可查；Buff 拦截只改 Battle 一处；
  `DamageTest`（纯乘区）+ `BattleTest`（装配顺序/集成）各测一半；
  将来复杂化可抽 `DamageFactory`，接口不变

### A1.1 技能携带攻击属性 ✅ 已完成
- **目标**：`beans.Skill.element`（`@SerializedName("element")`）、
  `SkillData.element + effect`、`SkillEffectType` 11 值 + `Category` + `isDamaging()`
- **涉及文件**：`models/SkillData.java`、`beans/Skill.java`、`enums/SkillEffectType.java`
- **验收**：伤害技能 `effect.category == DAMAGE` 且 `element` 非空 ✅；全量数据覆盖 ✅
- **遗留**：`DefaultSkill.execute` 未按 effect 分派（属 A1.6）

### A1.2 Damage 骨架 + 伤害类型
- **目标**：新建 `models/Damage.java`：
  - 字段：`attacker`、`defender`（CanHit）、`element`（非空）、`baseValue`（倍率×属性+固定）、
    **`attackType(DamageType)`**（新增枚举）、`crit` 标记、**乘区分组列表**
  - 乘区分组按 HSR 顺序：基础伤害区 → 伤害修饰区（增伤/易伤/减伤/虚弱）→ 暴击区 →
    防御区 → 抗性区 → 特殊乘区
- **新增**：`enums/DamageType.java`：`NORMAL / SKILL / ULTRA / ADDITIONAL / BREAK /
  SUPER_BREAK / DOT / EXTRA / TECHNIQUE / MEMORY / ELATION / TRUE`
  ——击破、超击破、持续伤害**不可暴击**（仅部分类型可暴）
- **涉及文件**：新建 `models/Damage.java`、`enums/DamageType.java`、`models/DefaultSkill.java`
- **验收**：`new Damage(c1, e1, FIRE, NORMAL, 1000)` 正常；element 为 null 抛异常；
  `DamageType.BREAK.isCrittable() == false`

### A1.3 乘区实现（精确版，参照 [HSR.md](HSR.md) §2）
- **目标**：按 HSR 精确实现全部乘区：
  1. **增伤区** `1 + Σ(属性增伤 + 攻击类型增伤)`：`addModifier` 聚合
  2. **易伤区** `1 + Σ(易伤)`：上限 **3.5**（clamp)
  3. **减伤区** `Π(1 - 减伤)`：**乘算**，下限 **0.01**（clamp）
  4. **虚弱区** `1 - Σ(虚弱)`：下限 **0.2**（clamp）
  5. **暴击区**：暴击 `1 + 爆伤`；不暴 `1`（击破/持续/超击破强制不暴）
  6. **防御区** `(200 + 10×攻击方等级) / (防御 + 200 + 10×攻击方等级)`（A1.9 提供等级）
  7. **抗性区** `1 - (原始抗性 + 提高 - 降低 - 穿透)`，抗性 clamp 在 `-100% ~ 90%`
  - `toValue()` 按序连乘；每区 clamp 规则进 `DamageModifier`/`ComputingBlock`
- **涉及文件**：`models/Damage.java`（复用 `DoubleValue.Modifier` 类型）
- **验收**：`DamageTest`：易伤叠加触发 3.5 上限；减伤 99% 后伤害下限 1% 浮动；
  抗性 clamp：敌人 120% 抗性按 90% 算,穿透-40% 按 -40% 算；防御区公式精确
  （80 级 vs 90 级 → `1000/1150+1000`）

### 【设计决策】等级贯穿伤害体系
防御区、击破基数、敌人属性都需要等级;为支持 A1.9/A3/A6:**`CanHit` 增加 `level` 字段**:
- `Character` 构造已有 level;`Enemy` 构造时显式传 level(默认 90)
- enemy:90 级防御基准 = `200 + 10 × 等级`(扑满 `300 + 15 × 等级`),基础免疫率 0%、
  暴击伤害 20%;效果命中 50 级后每级 +0.8%,抵抗每级 +0.4%(见 A5/A6) —
  数值写进 `utils/EnemyScaler`(后续数据化)

### A1.4 暴击判定接入（确定性先行）
- **目标**：`judgeCrit(double critRate, Random rng)` 从 attacker 读数；命中注入爆伤；
  **替换** `Math.random()`（注入 `Random` 可复现）
- **职责边界**：读取在 `Battle` 装配（A1.3 分工）；`Damage` 只收注入结果
- **涉及文件**：`models/Damage.java`、`Battle.java`
- **验收**：rate=0.6,rng 产出 0 → 暴击；0.99 → 不暴；
  `DamageType.BREAK` 一律不暴（强制1）

### A1.5 伤害修饰区自动读取
- **目标**：`Battle` 装配时读攻方属性增伤（`getBoostByElement` + `ALL_DAMAGE_TYPE_BOOST`
  + 攻击类型增伤 `PHYSICAL_DAMAGE_BOOST` 等）+ 攻击类型增伤（战技/终结技/追加增伤——
  属性表已有 `AttributeType` 相关项可先引子集）
- **涉及文件**：`models/Damage.java`、`Battle.java`
- **验收**：火增伤 0.3 时 `Damage(element=FIRE, NORMAL)` 放大 1.3;普攻不吃战技增伤

### A1.6 Battle 集成改造（关键接口变更）
- **目标**：
  1. `calculateDamage(...)` 内部构造并结算 `Damage`，返回 `Damage`
  2. `applyDamage(target, Damage)` 对 `toValue()` 调 `target.takeDamage`
  3. `applyDamage` 重复算 hp 的旧逻辑删除（= E2）
- **涉及文件**：`Battle.java`、`DefaultSkill.java`、`TestSkillGroup1.java`、`models/CanHit.java`
- **验收**：Main 演示跑通且数值与旧公式完全等价（`BattleTest` 对拍一次）

### A1.7 攻击事件钩子（Buff/状态拦截）
- **目标**：`BattleEvent.onDamage(attacker, defender, Damage)`：清算前可改乘区、
  清算后可改结果；`BuffManager` 注册入口；A3 削韧、A5 命中均走此钩子
- **涉及文件**：`models/event/BattleEvent.java`、`models/BuffManager.java`、`models/buffs/*`
- **验收**：挂「受到易伤 50%」Buff 后一切 Damage ×1.5

### A1.8 确定性测试收尾
- **目标**：`DamageTest`（全乘区+clamp+暴击+元素增伤+防御区）、`BattleTest`（顺序）；
  `ElementTest`/`SkillEffectTypeTest` 已存在
- **验收**：`gradlew test` 全绿

### A1.9 等级与防御区(HSN 规则)
- **目标**：`CanHit.level`；`Battle` 装配第 3 步:防御区 = `(200 + 10×lv) / (def + 200 + 10×lv)`;
  支持减防百分比/防御穿透:受击方防御 = 原始 × (1 - 减防% - 穿透%),防御不为负(上限1)
  ；`AttributeType.DEFENCE_IGNORE` 已存在
- **涉及文件**：`models/CanHit.java`、`Battle.java`、`utils/EnemyScaler.java`
- **验收**：80 级攻击者 vs 95 级 1150 防御敌人,防区 = `1000/2150 ≈ 0.4651` 精确断言

### A1.10 附加伤害（Additional / EXTRA Damage）
- **背景**：引擎侧对应 `DamageByAttackProperty` + `IsConvert=true` 的二次入账伤害
  （如缇宝结界【偷家的鸽子】对各标记目标追加附加伤害段;缇宝星魂 1 的"真伤段"见 A1.11）
- **目标**：
  1. `Damage` 增加 `damageType` 字段,枚举含 `ADDITIONAL`/`EXTRA`（借 A1.2 的
     `DamageType.java` 规划）;**附加段默认 `countsAsAttack=false`**:
     不触发回能(除角色特殊配置)、不触发削韧、不触发"攻击后"类回调链
  2. 数值挂钩:附加伤害的**倍率引用"前段命中伤害"**（如"本次攻击总伤害的 x%"）——
     `Battle` 在钩子(A1.7)中记录 `lastHitDamage`,追加段构造时读取
  3. AOE 语义:结界机制下**每个标记目标各自追加**,段数与目标数 1:1
- **涉及文件**：`models/Damage.java`、`Battle.java`、`models/event/BattleEvent.java`
- **验收**：`AdditionalDamageTest`:主伤害 20,000,倍率 24% → 追加 4,800;
  追加段不使攻击者回能/不扣韧性

### A1.11 真实伤害（True DMG）
- **背景**：引擎侧 `AttackType: "TrueDamage" + FinalFormulaType: "ByBaseDamage"`
  （tbgd 大量能力文件引用;e.g. 缇宝星魂1、4.0 角色）。游戏中"不受任何效果影响的
  无属性伤害,本次伤害不视为造成 1 次攻击"
- **目标**：
  1. `Damage.damageType = TRUE`:唯一无元素约束?——**仍带 element**（缺陷:
     由来源提供,如缇宝星魂1 为物理?标记 `element` 属性保持;公式只跳过乘区）
  2. 乘区开关:**跳过 防御区 × 抗性区 × 减伤区**(对应 ByBaseDamage 直入账),
     保留 **基础值×（增伤/暴击?待引擎验证——先按 HSR 惯例:
     真伤不吃攻击/属性增伤、不吃双暴,显示值=基础段值）**;计划内置
     `trueDamageZones=TODO_FLAG` 常量开关,便于实测后切换
  3. `countsAsAttack=false`:不视为一次攻击(同上 A1.10)
- **涉及文件**：`models/Damage.java`、`Battle.java`(装配处按 damageType 分支)
- **验收**：`TrueDamageTest`:100% 防御/抗性的敌人,真伤 = 基础值全额入账;
  BattleTest:真伤段不回能、不削韧、不触发"攻击后"回调

> 依赖:A1.2(骨架+DamageType)、A1.3(乘区)、A1.7(钩子)、A1.9(防御区实现,
> 真伤才具备"跳过"的对象)。实现顺位:放在 A1.9 之后,先 A1.10 后 A1.11。

## A2 弱点和抗性

### A2.1 怪物配置 bean
- **目标**：解析 `monster_config.json`: `stance_weak`、`stance_modify_ratio`、
  `hp_modify_ratio` 等倍率、`damage_resistance`、`debuff_resistance`、`summon_id`、
  `elite_group`
- **涉及文件**：新建 `beans/MonsterBean.java`
- **验收**：`MonsterBeanTest`:`1002011` 弱点 `[Fire, Thunder]`;抗性 `Ice: 0.2`

### A2.2 Enemy 挂弱点和抗性
- **目标**：`Set<DamageElement> stanceWeak`、`Map<DamageElement, Double> damageResist`;
  `fromAttributes` 旧构造默认无弱点
- **涉及文件**：`models/Enemy.java`
- **验收**：能查询某元素是否弱点

### A2.3 抗性区接入（精确版）
- **目标**：抗性区 = `1 - (原始 + 提高 - 降低 - 穿透)`，clamp `-100%~90%`
  （即抗性区 `0.1 ~ 2.0`）；击破状态的抗性**不改变**；是否植入弱点降低抗性看技能描述；
  `isWeak(element)` 供削韧判断
- **涉及文件**：`Battle.java`
- **验收**：120% 原始抗性 clamp 到 90% 减伤;穿透 40% 时抗性从 20% → -20%(0.2→1.2)

### A2.4 真实怪物接入
- **目标**：`EnemyFactory`(或测试先行):读 `MonsterBean` → `Enemy(level=90)`，
  经验公式(防御=200+10×90、暴击0%/暴伤20%、命中/抵抗按 90 级算)
- **涉及文件**：新建 `utils/EnemyFactory.java`、`utils/EnemyScaler.java`
- **验收**：冰锋(1002011)面板可打印,弱火/雷

### A2.5 敌人属性公式（精确版，[HSR.md](HSR.md) §1.2）
- **目标**：敌人属性 = 基础值 × 等级组系数 × 自身调整(monster_config 倍率) ×
  Π精英组别系数 + 自身调整值;等级组系数先在 `EnemyScaler` 按经验值
  (`200+10×lv`防御等)实现,精英/难度由 `EliteGroup`/`HardLevelGroup`
  数据(已加载)提供
- **涉及文件**：`utils/EnemyScaler.java`、`beans/EliteGroup.java`、`beans/HardLevelGroup.java`
- **验收**：给定等级与组别，敌人 HP/防御/攻击/速度可复现断言

## A3 韧性 + 击破 + 超击破（核心玩法）

### A3.1 Enemy 韧性字段
- **目标**：`maxToughness`、`currentToughness`、`isBroken`、`brokenRemainTurns`、
  `brokenElement`(本次击破元素)
- **涉及文件**：`models/Enemy.java`
- **验收**：初始=满值;削韧单位统一为「点」(普攻单=10点,[HSR.md](HSR.md) 超击破章节)

### A3.2 韧性初值计算
- **目标**：基础韧性(数据/常量) × `stance_modify_ratio`;弱点击破效率属性补充
  （`AttributeType` 缺失则新增 `WEAKNESS_BOOST`/`BREAK_RATE`）
- **涉及文件**：`utils/EnemyFactory.java`、`beans/MonsterBean.java`
- **验收**：冰锋韧性值稳定可断言

### A3.3 攻击削韧（逐段）
- **目标**:每段命中:破韧 = 基础破韧(技能 `stanceList.single/all/spread`,技能数据已是
  30/60 等) × (1 + 弱点击破效率);弱点击中削满、非弱点减半(简化期)；多段技能
  **每段扣对应占比**;韧性归零 → A3.4;后续段继续打(可触发超击破 A3.8)
- **涉及文件**：`Battle.java`(装配到 `onDamage` 后)
- **验收**：`ToughnessTest`:3 段技能(30/10)对 28 韧性敌人,第 1 段后剩 8→ 第 2 段击破

### A3.4 击破伤害（精确版，[HSR.md](HSR.md) 超击破§4）
- **目标**：
  `击破伤害 = 击破基数(等级表:80级≈376.7;按 38.5×等级 近似,后续数据表校准) × (1 + 击破特攻 BREAKING_EFFECT) × 技能削韧值 × 防御区 × 抗性区 × 减伤区`
  ——**不可暴击、不吃攻击力/增伤**；先手写 `LevelPromotionCalc` 扩展或常量表；
  击破瞬间:伤害 + 施放方回能(HSR 5 点基础,见 A4) + `isBroken=true` + A3.5 推条
- **涉及文件**：`Battle.java`、新建 `models/BreakDamageCalculator.java`
- **验收**：`BreakDamageTest`:样例(击破特攻300%、削韧112.5、防区0.4651 → 判定≈110,381
  量级一致,先按点单位校验)

### A3.5 击破推条
- **目标**：击破时敌方 `queue.delayAction(25% 行动条)`;`Battle.delayRequest` 助手
- **涉及文件**：`Battle.java`
- **验收**：击破后该敌人行动时间确认推迟

### A3.6 击破状态 + 持续伤害(DOT)
- **目标**：简化版:被击破敌人轮到时跳过,`brokenRemainTurns--`,归零恢复。
  **DOT**:按击破元素附着状态(火→灼烧、雷→触电、物理→裂伤、风→风暴→伪裂,查技能
  表)**「先上先结算」顺序**;DOT 是 Damage(DamageType.DOT,不可暴击),每回合按段走
  `onDamage` 钩子结算
- **涉及文件**：`models/Enemy.java`、`Battle.java`、`models/buffs/*`(新增 `AbstractDot`)
- **验收**：击破后敌每回合受灼烧扣血、状态时长受控

### A3.7 战场面板展示
- **目标**：`printHp` 增当前韧性/击破标记/弱点/DOT 状态
- **验收**：CLI 能看到韧性条和 `[BROKEN]`、`[SCORCH]`

### A3.8 超击破(进阶,[HSR.md](HSR.md) 超击破章节)
- **目标**：触发条件:开拓者·同谐终结技生效期间 + 攻击处于击破状态的敌人;
  `超击破 = 击破基数 × (1+击破特攻) × 技能最终削韧值 × (1+超击破伤害提高) × 易伤×防御×抗性×减伤`;
  技能最终削韧值 = 基础×(1+削韧值提高)×(1+弱点击破效率提高);
  **独立增伤区**(`SUPER_BREAK_*` 常量);防御区取攻击者等级;**双击破**:多段中击破段
  之后的段触发
- **涉及文件**：`Battle.java`、`models/Damage.java`(DamageType.SUPER_BREAK)
- **验收**：`SuperBreakTest`:样例数据(376.7×4×112.5×1.4×0.4651≈110,381)复算通过

## A4 能量系统（与 A 其余并行）

### A4.1 能量字段 + 回能公式（精确版）
- **目标**：`CanHit.currentEnergy/maxEnergy`;公式:
  `最终获得能量 = 基础回能 × (1 + ENERGY_REGENERATION_RATE)`;基础回能表:
  普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5(**示例值**,查询参数数据表后校准;
  追加攻击回能依角色而异:如景元 0、黑塔 5)
- **涉及文件**：`models/CanHit.java`、`Character.java`、`Battle.java`、`Constant.java`
- **验收**：`EnergyTest`:普攻一次能量 = 20 × (1+回能率)

### A4.2 大招条件
- **目标**：`castUltra` 检查满能量,释放后清零
- **涉及文件**：`Battle.java`
- **验收**：能量不足 false;放完清零

### A4.3 击破/追加回能联动
- **目标**：击破回能 5(基础);追加攻击按其角色配置回能
- **验收**：击破一次能量 +5×(1+回能率)

## A5 辅助机制：仇恨 / 命中 / 治疗 / 护盾（[HSR.md](HSR.md) §3.4-3.5, §4）

### A5.1 仇恨系统（敌人目标选择基础）
- **目标**：`Path` 枚举(存护/毁灭/其他→150/125/100)入 `Character.builder`；
  `受击概率 = 角色当前仇恨值 / 全队总仇恨值`;嘲讽状态 = 仇恨值 × (1+百分比);
  `Battle.getAggroTable()` ;B2 目标选择基于此加权重置
- **涉及文件**：新建 `enums/Path.java`、`models/Character.java`、`Battle.java`
- **验收**：`AggroTest`:存护在场受击概率最高;嘲讽生效后概率提升

### A5.2 效果命中与抵抗
- **目标**：`生效概率 = 基础概率 × (1 + 施加方命中 EFFECT_HIT_RATE) × (1 - 受击方抵抗
  EFFECT_RESISTANCE) × (1 - 特定抵抗)`;特定抵抗读 `monster_config.debuff_resistance`;
  判定挂在 Buff 施加流程(A1.7 钩子)
- **涉及文件**：`Battle.java`、`beans/MonsterBean.java`、`models/BuffManager.java`
- **验收**：`HitResistTest`:基础 100% 命中,命中 50%,抵抗 30% → 69%;冰冻免疫的怪 0%

### A5.3 治疗与护盾乘区
- **目标**：`治疗量 = 基础(倍率×属性+固定) × (1 + Σ治疗加成 OUTGOING_HEALING_BOOST) ×
  (1 - Σ治疗降低)`;`护盾量 = 基础 × (1 + Σ护盾提高 + Σ获得护盾提高)`;
  **护盾不叠加、受到伤害所有护盾同减**;盾破前不计死——`CanHit.shield`
- **涉及文件**：`models/CanHit.java`、`Battle.java`、`SkillEffectType.RESTORE/DEFENCE 执行`
- **验收**：`HealShieldTest`:治疗公式正确;有盾时伤害先扣盾

## A6 行动轮次与额外回合（[HSR.md](HSR.md) §3.1）

### A6.1 轮次制行动值
- **目标**：首轮总行动值 150、后续每轮 100;`Queue.initialize` 对第 1 个圈套用
  `150/speed`,后续 `move()` 碰撞后推进 `100`/圈(保持现有 heap 结构,加
  `roundBase`/`turnCounter` 推导)
- **涉及文件**：`Queue.java`、`Battle.java`
- **验收**：`QueueTest` 补充:速度 150→首轮 1.0s;速度 100→第一圈 1.5s、之后 1.0s

### A6.2 额外回合
- **目标**：`Battle.grantExtraTurn(canHit)`(立即行动,不消耗回合、不可插入终结技、
  `turnCounter` 不变);现有 `advanceRequest` 可用于近似,但语义单独定义
- **验收**：额外回合角色行动后轮次不动,行动条正常

## A7 收尾

### A7.1 真实内容演示
- **目标**：冰锋(弱火/雷) + 火/雷角色演示:普攻→削韧→击破伤害→推条→跳过回合→DOT→回满
- **验收**：Main 一遍跑通

### A7.2 测试补齐
- **目标**：`ToughnessTest`、`BreakDamageTest`、`SuperBreakTest`、`EnergyTest`、
  `HitResistTest`、`AggroTest`、`HealShieldTest`、`MonsterBeanTest`(`ElementTest`
  已存在)
- **验收**：`gradlew test` 全绿

---

# 线路 B：敌人 AI（现在敌人回合是 Skip）

**依赖**：A1(伤害)、A5.1(仇恨)。

### B1 敌人技能数据
- **目标**：`Enemy.skills`(`SkillType → Skill`)加载真实敌人技能;新建 `EnemySkill`
  (懒加载,按 `monster_config`/`skills.json`);敌人普攻直接取 attackType=`"Attack"`
  (MazeNormal)对应技能
- **涉及文件**：新建 `models/EnemySkill.java`
- **验收**：真实怪物普攻能打带元素有倍率的伤害

### B2 目标选择策略（基于仇恨）
- **目标**：`TargetSelector.select(battle, self)`;主策略 = **仇恨加权随机**
  (`受击概率 = 仇恨值/总仇恨`,A5.1);异常/干扰策略可选:
  `LOWEST_HP_TARGET`、`HIGHEST_SPEED_TARGET`
- **涉及文件**：新建 `models/ai/TargetSelector.java`
- **验收**：存护命途的角色被选概率 ≈ 150/全队总仇恨,统计 1000 次偏差 < 3%

### B3 敌方回合执行
- **目标**：Main 的 `"Skip"` 分支改为:敌人回合 → `select skill → select target →
  useSkill`;复用 `performAction` 死亡/控制检查
- **涉及文件**：`Main.java`、`Battle.java`
- **验收**：敌人会普攻打玩家,被打死移除

### B4 Boss 机制
- **目标**：受击反击(攻击回调)、双阶段(半血换招)、召唤(use `summon_id`,参考 C 线)
- **涉及文件**：`models/Enemy.java`、`models/event/BattleEvent.java`
- **验收**：boss 半血后换招、会召唤小怪

### B5 敌方 Buff / 特定免疫
- **目标**：敌人初始 Buff 列表可配置;`debuff_resistance` 数据作用于 A5.2
- **涉及文件**：`models/Enemy.java`、`beans/MonsterBean.java`
- **验收**：冰锋对禁锢免疫,冰冻类技能命中率 0

---

# 线路 C：Summon 扩展（传统召唤物 vs 忆灵）

**依赖**：B 线不强制;忆灵部分依赖 A1 伤害流水线与 A4 能量。

### C1 模型拆分（[HSR.md](HSR.md) §2.1 对比表）
- **目标**：消灭「Summon 是空壳」——原 `Summon` 作为**传统召唤物基类**;
  新增 `Memosprite`(忆灵)子类:
  | 特性 | 传统召唤物(`Summon`) | 忆灵(`Memosprite`) |
  |---|---|---|
  | 伤害类型 | 追加攻击(ADDITIONAL) | **忆灵伤害(MEMORY)** |
  | 承接 | 无实体,不可受击/无能量 | 独立血条,可受击、可选中、独立能量 |
  | 面板 | 跟随本体 | 战斗开始**快照**本体战斗外面板(攻防双爆/抗性穿透/击破特攻),生命速度天赋定 |
  | 本体死亡 | 取决于设定 | **不受影响**可继续行动 |
- **涉及文件**：`models/Summon.java`、新建 `models/Memosprite.java`、`models/CanHit.java`
- **验收**：Memosprite 可被技能选中、被敌人攻击、有独立能量条

### C2 特性细节
- **目标**：连携攻击(本体+忆灵一次行动两次攻击段,可穿透多次受击屏障);
  增益声明「对忆灵也有效」才同步;装备特效可触发「召唤物」条件但本身不吃召唤物加成;
  「忆灵」专属词条另算
- **涉及文件**：`models/event/BattleEvent.java`、`models/buffs/*`
- **验收**：给本体加攻 buff,Memosprite 攻击力不变;写明「对忆灵也有效」时变化

### C3 行动/能量/清理
- **目标**：继承位置入队(独立行动值)、回合可手动选目标;
  能量各自独立可相互充能;死亡清理钩子挂在 `BattleEvent`
- **涉及文件**：`Battle.java`、`Queue.java`、`models/Memosprite.java`
- **验收**：忆灵有自己的回合与技能选择;能量互充;本体死忆灵不退场

### C4 样例
- **目标**：一个「翔鹰」传统召唤物(每回合 50% 攻击力追加伤害) +
  一个忆灵 demo;Main.java 演示

---

# 线路 D：内容 / 关卡层（资源数据基本没用上）

**依赖**：B3(敌人会动)、E1(胜负判定)。

### D0 关卡 bean
- **目标**：`StageBean`(`stage.json`:波次、怪物 id 列表)、
  `ChallengeBean`(`challenge_maze.json` / `challenge_boss_maze.json`);抽样解析验证
- **验收**：能解析某 stage 的怪物 id 列表

### D1 胜负判定
- **目标**：`Battle` 状态机 `NOT_STARTED / RUNNING / WIN / LOSE`;
  敌人全灭→WIN,我方全灭→LOSE,结束后停 Queue;`getResult()`
- **涉及文件**：`Battle.java`
- **验收**：团灭/全灭后状态正确且不再推进

### D2 波次系统
- **目标**：`WaveManager` 按 `StageBean` 顺序上怪;波间清 Buff/回能量/回血(可配置);
  `addRequestItems` 进场复用
- **涉及文件**：新建 `models/WaveManager.java`
- **验收**：第 1 波打完第 2 波进场不中断

### D3 关卡构造器 + 难度
- **目标**：`StageFactory.load(stageId)`:敌人(A2/A3 精确属性)+ 难度乘区
  (`hard_level_group` HP/攻击/速度乘区,对应 A2.5 公式)→ 组装 Battle
- **验收**：`load` 得到可运行 Battle,难度参数生效

### D4 奖励结算
- **目标**：胜利后读 `stage.json` reward 结算打印;引擎出 `Reward` 对象,UI 将来接
- **验收**：WIN 后打印奖励

### D5 模拟宇宙（3.0/4.0 远期）
- **目标**：祝福池化=`BuffManager`,每层抽 3 选 1;「记忆」命途祝福→忆灵相关增强、
  「欢愉」命途→ A 线远期扩展:阿哈行动单位(速度 = 80 + 最快/5 + 第二/10 + 第三/20
  + 最慢/50)、笑点计数器独立于战技点、欢愉伤害**禁用攻击力/属性增伤**、参演编号排序、
  欢愉伤害公式 `基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1 + 笑点×5/(笑点+240)) ×
  抗性穿透×韧性减伤×减防×易伤×双爆×真实伤害`——先在文档记规格,实现排在 B/D 之后
- **涉及文件**：后续新建 `models/elation/*`
- **验收**：3 层手选祝福打完

---

# 线路 E：测试与稳定性（小碎活，随时可做）

### E1 Battle 行为测试
- **目标**：`BattleTest`:队列顺序、死亡移除、add/advance/delay 一致性、
  `performAction` 控制时 false
- **验收**：全场景有断言

### E2 重构：伤害入口统一（被 A1.6 吸收）
- **目标**：`applyDamage` 重复 hp 计算删除,合并到 `takeDamage` 单一出口
- **验收**：无重复 hp 计算

### E3 常量表化
- **目标**：`Constant` 增 `ENERGY_GAIN_*`、削韧值、击破基数、抗性/易伤 clamp 常量
- **验收**：数值改动只动 Constant

### E4 基准性能
- **目标**：`Benchmark.java` 10 万轮战斗前后对比;暴击用 `Random` 注入(A1.4)
- **验收**：出前后耗时对比

### E5 代码质量
- **目标**：`gradlew test` + 零警告;Main 拆 `demo/` 包(属性预览和战斗演示分开)
- **验收**：Main 只剩入口调用

---

# 推荐实施顺序

1. **A1.2 → A1.3 → A1.4 → A1.5 → A1.6 → A1.9**(Damage + 精确乘区 + 等级/防御区)
2. **A1.10 → A1.11**(附加伤害/真伤,依赖 A1.7 钩子,可与 A2 并行)
3. **A2**(弱点/抗性,数据打通)
4. **A3**(削韧/击破/击破伤害/DOT)
4. **A5.1 → B1 → B2 → B3**(仇恨+敌人动起来)
5. **A4**(能量)
6. **A1.7**(onDamage 钩子)+ A7.2 测试收尾
7. **A5.2 / A5.3 / A6**(命中抵抗、治疗护盾、轮次)
8. **E1**(行为测试防回归)
9. **C1 → C2 → C3**(忆灵)
10. **D1 → D3 → D4**(关卡可玩)
11. B4 / C4 / D5(欢愉·远期)/ E4 / E5 为可选扩展

每一步保持：可编译 → 跑 `gradlew test` → 提交。
