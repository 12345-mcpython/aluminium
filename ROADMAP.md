# aluminium 开发路线图（可执行版 v2）

一个用 Java 写的类《崩坏：星穹铁道》回合制战斗引擎。数据来自
[turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata)，已解析到 `src/main/resources/data/`。

> **v2 为什么重写**：旧版任务颗粒度太大（一个任务塞 7 个乘区、10 个设计决策），
> 只写"目标"不写"怎么做"，验收数值没算好。
> **v2 原则：一个任务 ≤ 1.5 小时，只改 ≤ 3 个文件，自带一个测试类，做完就绿。**

---

## 0. 使用说明（先读 30 秒）

### 0.1 每个任务的标准动作（铁律）

```
1. 看【怎么做】，只动【涉及文件】里列的文件
2. 改完跑 .\gradlew.bat test
3. 全绿 → git add -A && git commit -m "feat: P1-3 damage zones"（任务编号进 commit）
4. 勾掉【进度总览】表里对应的框
5. 不绿 → 看是哪个断言失败，只修那个任务的范围，不要顺手改别的
```

### 0.2 全局约定（写代码时永远遵守）

| 约定       | 内容                                                                                                                         |
|------------|------------------------------------------------------------------------------------------------------------------------------|
| 常量       | 所有数值（回能、击破基数、clamp 上限、税率）一律进 `Constant`，代码里不写魔法数字                                            |
| 随机数     | 暴击/目标选择一律走注入的 `java.util.Random`（构造时给 seed，可复现），禁止 `Math.random()`                                  |
| 测试       | JUnit 5；断言 `assertEquals(expected, actual, 1e-6)`；测试类放 `src/test/java/com/laosun/aluminium/test/`，命名 `XxxTest`    |
| 数据       | 真实怪物数据只从 `data/*.json` 经 Gson 读（`Constant` / `JSONReader`），不硬编码；测试/演示里允许手写常量并标 `// TODO data` |
| 单段单目标 | `Damage` 永远是「单段·单目标」；一个技能 = N 个 Damage（AOE→每敌 1 个；BLAST→主目标+相邻；BOUNCE→循环 N 次）                 |
| 判别口诀   | **有伤害要结算 ⇒ 构造 `Damage`；不造 `Damage` ⇒ 无伤害**（治疗/护盾/回能/上 Buff 不构造 Damage）                             |
| 每段独立   | 每段伤害独立判定暴击、独立结算（HSR 规则）                                                                                   |

### 0.3 现有代码速查（实现前扫一眼）

| 类                              | 作用                                                             | 你要知道的口子                                                                                             |
|---------------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `Battle`                        | 战斗循环/行动条/伤害入口                                         | `calculateDamage` / `applyDamage` / `processRequests` / `castUltra` / `advanceRequest` / `addRequestItems` |
| `models.Damage`                 | 伤害对象（已有 attacker/defender/element/skillBaseValue 4 字段） | P1-2 起扩展                                                                                                |
| `models.CanHit`                 | 所有参战实体基类                                                 | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / 无 level（P1-4 加）                            |
| `models.DoubleValue`            | 属性值（base × (1+Σadd%) × Π(1+mul%) + Σpure）                   | `Modifier.addPercent / multiplyPercent / pure`                                                             |
| `models.BuffManager`            | Buff 挂载/到期                                                   | `addBuff` / `canAct` / `beforeMove` / `afterMove`                                                          |
| `models.AbstractBuff`           | Buff 基类                                                        | `applyEffect` / `removeBuff` / `tickEffect` / `duration()`                                                 |
| `models.Skill` / `DefaultSkill` | 技能抽象 + 默认实现（打 target.getFirst()）                      | P1-8 改造                                                                                                  |
| `models.SkillData`              | 技能运行时数据                                                   | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）                               |
| `Queue`                         | 行动条（10000/speed，heap）                                      | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `addCombatant`                          |
| `Constant`                      | 数据加载总入口                                                   | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_POINTS`                                                       |
| `models.Character`              | 角色（builder 已含 level 但没存下来）                            | P1-4 补 `getLevel()`                                                                                       |
| `models.Enemy`                  | 敌人（无弱点/韧性/抗性）                                         | P1-6 / P2-2 / P4-1 扩展                                                                                    |
| `enums.SkillEffectType`         | 11 值 + Category                                                 | `isDamaging()` 判断是否走伤害流水线                                                                        |

### 0.4 数据文件速查（P2 之后离不开）

| 文件                           | 内容                                                                                                                                                                                                              | 已确认的锚点数据                                                                                                                                          |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `monster_config.json`          | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak[7元素], hp/defence/health/speed/stance_modify_ratio, damage_resistance{元素:值}, debuff_resistance{STAT_*:值}, summon_id}` | **1002011 冰锋**：template_id=1002011，弱 `[Fire, Thunder]`，抗 `{Physical .2, Ice .2, Wind .2, Quantum .2, Imaginary .2}`，冰冻免疫 `STAT_CTRL_Frozen=1` |
| `monster_template_config.json` | 模板基础属性：`template_id → {attack, defence, health, speed, stance, stance_count, stance_type, effect_resistance}`                                                                                              | **1002011**：attack=18, defence=210, health=69.75, speed=100, stance=60, stance_count=1, stance_type=Ice, effect_resistance=0.2                           |
| `hard_level_group.json`        | `{组: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}`                                                                                                                      | 组1 Lv90: `{36.821384, 5.238095, 236.53471, 1.32, 1, 0.32, 0.1}`                                                                                          |
| `breaking_rate.json`           | `等级 → 击破基数（原始 10 倍值）`                                                                                                                                                                                 | Lv80 = **3767.5535 → 公式里 /10 = 376.75535**                                                                                                             |
| `skills.json`                  | `cid → {槽位 → {attack_type, max_level, param_list, skill_effect, stance_list{single,all,spread}, element}}`                                                                                                      | 槽位 1=普攻 2=战技 3=终结技（敌人技能不在此文件，见 P5-3）                                                                                                |
| `stage.json`                   | `stage_id → {type, hard_level_group, level, monster:[{Monster0:id,...}(一波)]}`                                                                                                                                   | `103201`：level 29，monster = 1022020/1023010/1022020                                                                                                     |

---

## 1. 进度总览

**主线顺序**：`P1 → P2 → P4(能量) → P3(韧性击破) → P5(仇恨+AI) → P6(命中/治疗/护盾) → P7(轮次/胜负/关卡) → P8(收尾)`。 P4
零依赖可随时插入；P5-1/P5-2 需在 P5-3 前。

| 阶段                  | 任务                                           | 状态 |
|-----------------------|------------------------------------------------|------|
| **P1 伤害流水线**     | P1-1 DamageType 枚举                           | ☐   |
|                       | P1-2 Damage 挂 damageType                      | ☐   |
|                       | P1-3 乘区累加器 + toValue                      | ☐   |
|                       | P1-4 CanHit.level                              | ☐   |
|                       | P1-5 Battle 装配（增伤/暴击/防区）+ 旧入口删除 | ☐   |
|                       | P1-6 抗性区接入                                | ☐   |
|                       | P1-7 onDamage 钩子（易伤/减伤/虚弱）           | ☐   |
|                       | P1-8 技能执行器（单→多目标分派）               | ☐   |
|                       | P1-9 附加伤害 + 真伤                           | ☐   |
| **P2 真实怪物数据**   | P2-1 MonsterBean + Constant 加载               | ☐   |
|                       | P2-2 Enemy 弱点/抗性字段                       | ☐   |
|                       | P2-3 EnemyScaler 等级属性公式                  | ☐   |
|                       | P2-4 EnemyFactory                              | ☐   |
| **P3 能量系统**       | P3-1 能量字段 + gainEnergy                     | ☐   |
|                       | P3-2 回能接入 + 大招条件                       | ☐   |
|                       | P3-3 击破回能联动                              | ☐   |
| **P4 韧性/击破**      | P4-1 Enemy 韧性字段                            | ☐   |
|                       | P4-2 削韧判定                                  | ☐   |
|                       | P4-3 击破伤害                                  | ☐   |
|                       | P4-4 击破状态/推条/跳回合                      | ☐   |
|                       | P4-5 DOT                                       | ☐   |
|                       | P4-6 超击破                                    | ☐   |
| **P5 仇恨 + 敌人AI**  | P5-1 Path 仇恨值                               | ☐   |
|                       | P5-2 受击概率 + 嘲讽                           | ☐   |
|                       | P5-3 EnemySkill（敌人普攻）                    | ☐   |
|                       | P5-4 TargetSelector                            | ☐   |
|                       | P5-5 敌方回合执行                              | ☐   |
| **P6 命中/治疗/护盾** | P6-1 效果命中与抵抗                            | ☐   |
|                       | P6-2 治疗乘区                                  | ☐   |
|                       | P6-3 护盾                                      | ☐   |
| **P7 轮次/胜负/关卡** | P7-1 轮次制行动值（150/100）                   | ☐   |
|                       | P7-2 额外回合                                  | ☐   |
|                       | P7-3 胜负状态机                                | ☐   |
|                       | P7-4 StageBean + 波次                          | ☐   |
|                       | P7-5 StageFactory + 难度                       | ☐   |
| **P8 收尾**           | P8-1 Main 修复 + demo 包拆分                   | ☐   |
|                       | P8-2 真实内容演示（冰锋战）                    | ☐   |
|                       | P8-3 测试总盘点 + 基准 + 零警告                | ☐   |

---

## 2. 阶段 P1：伤害流水线（最高优先，一切的地基）

**本阶段结束时的成果**：任何一条伤害都走「基础值 × 增伤 × 易伤 × 减伤 × 虚弱 × 暴击 × 防御 × 抗性」精确流水线， Buff
能拦在清算前改乘区，多段/多目标技能按 effect 自动分派。

**阶段内顺序**：`P1-1 → P1-2 → P1-3 → P1-4 → P1-5 → P1-6 → P1-7 → P1-8 → P1-9`，严格串行。

---

### P1-1 DamageType 枚举

- **目标**：区分伤害类型（普攻/战技/大招/击破/持续/真伤…），知道哪些类型不可暴击。
- **涉及文件**：新建 `src/main/java/com/laosun/aluminium/enums/DamageType.java`、新建 `test/DamageTypeTest.java`
- **怎么做**：
    1. 枚举 12 值：`NORMAL, SKILL, ULTRA, ADDITIONAL, BREAK, SUPER_BREAK, DOT, EXTRA, TECHNIQUE, MEMORY, ELATION, TRUE`
    2. 加 `boolean isCrittable()`，规则：
        - 可暴击：`NORMAL / SKILL / ULTRA / ADDITIONAL / EXTRA / TECHNIQUE / MEMORY / ELATION`
        - **不可暴击**：`BREAK / SUPER_BREAK / DOT / TRUE`（HSR：击破、超击破、持续伤害、真实伤害不吃双暴）
    3. 加 `static DamageType fromString(String)`，按 `name()` 大小写不敏感查找，未知抛 `IllegalArgumentException`
- **验收**：`DamageTypeTest`：
    - `values().length == 12`
    - `!DamageType.BREAK.isCrittable()`、`!DamageType.SUPER_BREAK.isCrittable()`、`!DamageType.DOT.isCrittable()`、
      `!DamageType.TRUE.isCrittable()`
    - `DamageType.fromString("break") == DamageType.BREAK`
- **依赖**：无（纯新增）

---

### P1-2 Damage 挂 damageType

- **目标**：`Damage` 增加伤害类型字段，旧构造器保持兼容。
- **涉及文件**：`models/Damage.java`、新建 `test/DamageSkeletonTest.java`
- **怎么做**：
    1. `Damage` 加字段 `private final DamageType damageType;`
    2. 新构造器
       `Damage(CanHit attacker, CanHit defender, DamageElement element, double skillBaseValue, DamageType damageType)`
    3. 旧 4 参构造器委托：`this(attacker, defender, element, skillBaseValue, DamageType.NORMAL)`
    4. `element` 保持 `@NonNull`（Lombok 会生成空检查 + `Objects.requireNonNull` 已有）
- **验收**：`DamageSkeletonTest`：
    - `new Damage(a, d, DamageElement.FIRE, 1000)` 的 `getDamageType() == DamageType.NORMAL`
    - `new Damage(a, d, null, 1000, DamageType.BREAK)` 抛 `NullPointerException`
    - `DamageType.BREAK.isCrittable()` 为 false（防回归）
- **依赖**：P1-1

---

### P1-3 乘区累加器 + toValue（纯代数，不碰 Battle）

- **目标**：`Damage` 能自己算最终值。 **这是全引擎最核心的一个任务，公式抄下面，别自由发挥。**
- **涉及文件**：`models/Damage.java`、`Constant.java`（加 clamp 常量）、新建 `test/DamageZoneTest.java`
- **怎么做**：
    1. `Constant` 加常量块：
       ```java
       public static final double VULNERABLE_CAP    = 3.5;   // 易伤区上限
       public static final double REDUCTION_MIN     = 0.01;  // 减伤区整体下限
       public static final double WEAKNESS_MIN      = 0.2;   // 虚弱区下限
       public static final double RESIST_MIN        = -1.0;  // 抗性下限(-100%)
       public static final double RESIST_MAX        = 0.9;   // 抗性上限(90%)
       public static final boolean TRUE_DMG_SKIP_ZONES = true; // 真伤跳过乘区开关
       ```
    2. `Damage` 加私有累加器 + 公开设置方法（全部返回 `this` 以便链式）：
       ```java
       private final List<Double> boosts          = new ArrayList<>();   // 增伤（加算）
       private final List<Double> vulnerabilities = new ArrayList<>();   // 易伤（加算, cap 3.5）
       private final List<Double> reductions      = new ArrayList<>();   // 减伤（乘算Π）
       private final List<Double> weaknesses      = new ArrayList<>();   // 虚弱（加算, min 0.2）
       private boolean crit;
       private double critDmg;
       private int attackerLevel = 80;
       private double defenderDef;
       private double defIgnore;
       private double resist;
       private double penetration;
       private boolean trueDamage;
       private boolean countsAsAttack = true;   // P1-9/P3/P4 用：附加/真伤段 = false（类级 @Getter 生成 isCountsAsAttack()）
  
       private static double sum(List<Double> list) {
           double s = 0;
           for (double d : list) s += d;
           return s;
       }
  
       public Damage addBoost(double pct)      { boosts.add(pct); return this; }
       public Damage addVulnerable(double pct) { vulnerabilities.add(pct); return this; }
       public Damage addReduction(double pct)  { reductions.add(pct); return this; }
       public Damage addWeakness(double pct)   { weaknesses.add(pct); return this; }
       public Damage crit(boolean isCrit, double criticalDamage) {
           this.crit = isCrit;
           this.critDmg = criticalDamage;
           return this;
       }
       public Damage defence(int level, double def, double ignore) {   // ignore clamp [0,1]
           this.attackerLevel = level;
           this.defenderDef = def;
           this.defIgnore = Math.max(0, Math.min(1, ignore));
           return this;
       }
       public Damage resist(double raw, double pen) {   // 抗性 = clamp(raw - pen, RESIST_MIN, RESIST_MAX)
           this.resist = raw;
           this.penetration = pen;
           return this;
       }
       public Damage trueDamage()        { this.trueDamage = true; return this; }
       public Damage notCountsAsAttack() { this.countsAsAttack = false; return this; }
       ```
    3. `toValue()` 按 HSR 顺序连乘：
       ```java
       public double toValue() {
           if (trueDamage && Constant.TRUE_DMG_SKIP_ZONES) return skillBaseValue;
           double v = skillBaseValue;
           v *= 1 + sum(boosts);                                    // 1. 增伤
           v *= Math.min(1 + sum(vulnerabilities), Constant.VULNERABLE_CAP); // 2. 易伤 (cap)
           double red = 1; for (double r : reductions) red *= Math.max(0, Math.min(1, r));
           v *= Math.max(red, Constant.REDUCTION_MIN);              // 3. 减伤 (min)
           v *= Math.max(1 - sum(weaknesses), Constant.WEAKNESS_MIN); // 4. 虚弱 (min)
           v *= crit ? 1 + critDmg : 1;                             // 5. 暴击
           double defEff = Math.max(0, defenderDef * (1 - defIgnore));
           v *= (200 + 10.0 * attackerLevel) / (defEff + 200 + 10.0 * attackerLevel); // 6. 防御
           double res = Math.max(Constant.RESIST_MIN, Math.min(Constant.RESIST_MAX, resist - penetration));
           v *= 1 - res;                                            // 7. 抗性
           return v;
       }
       ```
    4. clamp 常量一律用 `Constant.*`， **不要**在公式里写数字。
- **验收**：`DamageZoneTest`（base 一律 1000，逐区断言，每区单独一个用例）：
    - 增伤：`addBoost(0.3).addBoost(0.2)` → 1500
    - 易伤上限：`addVulnerable(2.0).addVulnerable(2.0)` → 3500（第 2 个被 cap：1+4=5 → 3.5）
    - 减伤下限：`addReduction(0.9).addReduction(0.9).addReduction(0.9)` → 10（0.1³=0.001 → clamp 0.01）
    - 虚弱下限：`addWeakness(0.9).addWeakness(0.3)` → 200（1-1.2 → 0.2）
    - 暴击：`crit(true, 1.0)` → 2000；`crit(false, …)` → 1000
    - 防御区：`defence(80, 1150, 0)` → 1000 × (1000/2150) ≈ 465.116（精确断言
      `assertEquals(1000.0 * 1000.0 / 2150.0, v, 1e-6)`）
    - 防御穿透：`defence(80, 1150, 0.5)` → 1000 × (1000/ (575+1000)) ≈ 635.0
    - 抗性区：`resist(0.2, 0.4)` → 1200（0.2-0.4 = -0.2）
    - 抗性 clamp：`resist(1.2, 0)` → 100（1.2 → 0.9）
    - 真伤跳过：填满所有区 + `trueDamage()` → 1000（原样）
- **依赖**：P1-2

---

### P1-4 CanHit.level（等级贯穿伤害）

- **目标**：防御区、击破基数、敌人属性都要等级。`CanHit` 增加 level，角色把 builder 的 level 存下来。
- **涉及文件**：`models/CanHit.java`、`models/Character.java`、新建 `test/LevelTest.java`
- **怎么做**：
    1. `CanHit` 加 `private int level = 80;` + `@Setter`（默认 80，老代码不用改）
    2. `Character.Builder.build()` 末尾：`character.setLevel(level);`
    3. `Enemy.fromAttributes(...)` 不用改（默认 80；真实怪物由 P2-4 传等级）
- **验收**：`LevelTest`：
    - `Character.builder().cid(1409).level(90).build().getLevel() == 90`（需要 Constant，先跑一次确认数据加载 OK）
    - `Character.fromAttributes("x", 100, 100, 100, 100).getLevel() == 80`
    - `Enemy.fromAttributes("e", 100, 100, 100, 100).getLevel() == 80`
- **依赖**：无（独立，做完错开也行）

---

### P1-5 Battle 装配（增伤 + 暴击 + 防区）+ 旧入口删除

- **目标**：`Battle.calculateDamage` 从「简易公式」改成「装配 Damage 乘区」；`applyDamage` 重复算 hp 的代码删掉（旧 E2 在此完成）。
- **涉及文件**：`Battle.java`、`models/DefaultSkill.java`、`models/tests/TestSkillGroup1.java`、新建
  `test/DamagePipelineTest.java`
- **怎么做**：
    1. `Battle` 加 `private final Random rng;`，构造器：老签名 `Battle(List<Character>, List<Enemy>)` 委托
       `this(…, new Random())`；新签名 `Battle(List<Character>, List<Enemy>, Random rng)`；加 `public Random getRng()`
       （P1-8 / P5-4 要用）
    2. **新入口**（保留同名，改签名）：
       ```java
       public double calculateDamage(Damage damage) {
           CanHit attacker = damage.getAttacker();
           CanHit defender = damage.getDefender();
           // 1) 增伤区：元素增伤 + 全增伤
           AttributeType boost = AttributeType.getBoostByElement(damage.getElement());
           if (boost != null) damage.addBoost(attacker.getAttribute(boost).get());
           damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
           // 2) 暴击区（读属性，注入 rng；不可暴类型强制不暴）
           if (damage.getDamageType().isCrittable()) {
               double rate = attacker.getAttribute(AttributeType.CRIT_CHANCE).get();
               boolean isCrit = rate > 0 && rng.nextDouble() < rate;
               damage.crit(isCrit, attacker.getAttribute(AttributeType.CRIT_ATTACK).get());
           }
           // 3) 防御区（攻击者等级 / 受击者防御 / 攻击者减防穿透）
           double defIgnore = attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get();
           damage.defence(attacker.getLevel(), defender.getAttribute(AttributeType.DEFENCE).get(), defIgnore);
           // 抗性区在 P1-6 接
           return damage.toValue();
       }
       public void applyDamage(CanHit target, Damage damage) {
           if (target.isDeath()) return;
           target.takeDamage(calculateDamage(damage));  // hp 只减一次，都在 takeDamage
       }
       ```
    3. **删除**旧 `calculateDamage(CanHit, CanHit, double, List<DoubleValue.Modifier>)` 和旧
       `applyDamage(CanHit, double)`； 编译报错的话把 `DefaultSkill` 与 `TestSkillGroup1` 的调用改为：
       ```java
       Damage dmg = new Damage(user, c, element, baseDamage);
       battle.applyDamage(c, dmg);
       ```
       （element 从 `getData().getElement()` 取；若为 null 说明是非伤害技能，走 P1-8 的分派，先给个 `DamageElement.PHYSICAL`
       占位并标 `// TODO P1-8`）
    4. `judgeCrit` 用 `rng.nextDouble()`，不再 `Math.random()`
- **验收**：`DamagePipelineTest`（构造 `Character.fromAttributes` + `setAttribute` 塞属性）：
    - 火增伤 0.3、暴击率 0：`base 1000` + `FIRE_DAMAGE_BOOST` 0.3 → `calculateDamage` == 1300
    - 暴击确定性：`CRIT_CHANCE=0.6, CRIT_ATTACK=1.0`，用 `new Battle(characters, enemies, new Random(0))` → 对种子 0 的
      `nextDouble()` 首值断言暴击与否（先跑一次打印 rng 输出再写死断言，保证复现）
    - 防御区：攻击者 level 80，受击者 DEFENCE=1150 → 1000 × (1000/2150)
    - `takeDamage` 后 HP 精确 = 原 HP - 结算值（确认无重复扣血）
    - 老 `applyDamage(target, double)` 编译不通过 = 已删除（E2 完成）
- **依赖**：P1-3、P1-4

---

### P1-6 抗性区接入

- **目标**：`Enemy` 挂 `damageResist`，`Battle` 装配第 4 步接抗性区。
- **涉及文件**：`models/Enemy.java`、`Battle.java`（P1-5 代码里加一行）、新建 `test/ResistZoneTest.java`
- **怎么做**：
    1. `Enemy` 加 `@Setter private Map<DamageElement, Double> damageResist = Map.of();`
    2. `Battle.calculateDamage` 的 3) 之后加：
       ```java
       // 4) 抗性区：受击者抗性表 + 攻击者抗性穿透(暂无: DAMAGE_PENETRATION 已有属性)
       double raw = defender instanceof Enemy e ? e.getDamageResist().getOrDefault(damage.getElement(), 0.0) : 0.0;
       double pen = attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
       damage.resist(raw, pen);
       ```
    3. 击破状态不改变抗性（P4 再验证，先留注释）
- **验收**：`ResistZoneTest`：
    - 敌人 `damageResist = {ICE: 0.2, FIRE: 1.2}`：
        - 冰伤穿透 0.4 → 1200（0.2-0.4）
        - 火伤穿透 0 → 100（1.2 clamp 0.9）
        - 无表中元素（如 PHYSICAL）→ 1000
- **依赖**：P1-5

---

### P1-7 onDamage 钩子（Buff 拦截乘区）

- **目标**：伤害清算前允许 Buff 改乘区（易伤/减伤/虚弱都从这里进）。
- **涉及文件**：`models/event/BattleEvent.java`（加接口）、`models/BuffManager.java`（加 getter）、 新建
  `models/buffs/DamageListener.java`、新建 `models/buffs/VulnerabilityBuff.java`、新建 `test/DamageHookTest.java`
- **怎么做**：
    1. `BuffManager` 加 `public List<AbstractBuff> getBuffs() { return buffs; }`
    2. 新接口：
       ```java
       public interface DamageListener {
           void onDamage(Battle battle, Damage damage);  // 在 toValue 前被调用
       }
       ```
    3. `Battle.calculateDamage` 在 `toValue()` 之前插入：
       ```java
       for (AbstractBuff b : defender.getBuffManager().getBuffs()) {
           if (b instanceof DamageListener listener) {
               listener.onDamage(this, damage);
           }
       }
       ```
    4. 示例 Buff `VulnerabilityBuff extends AbstractBuff implements DamageListener`：
        - 构造 `(duration, double ratio)`，`onDamage` 里 `damage.addVulnerable(ratio)`（易伤 50% 就是
          `new VulnerabilityBuff(2, 0.5)`）
        - `applyEffect/removeBuff/tickEffect` 参照 `BoostDamageBuff` 模板（本 buff 不改属性，前两个留空）
- **验收**：`DamageHookTest`：
    - 挂易伤 50% → 基础 1000 结算 1500；2 回合后（`beforeMove`+`afterMove` 各触发一次 tick）再打回 1000
    - 再写一个 `ReductionBuff`（`damage.addReduction(0.3)`）→ 结算 700
    - 钩子对 `DamageType.BREAK` 也生效（P4 复用它）
- **依赖**：P1-5、P1-6

---

### P1-8 技能执行器（single → aoe → blast → bounce）

- **目标**：伤害技能不再只打第一个目标；按 `SkillEffectType` 自动分派多段/多目标。
- **涉及文件**：新建 `models/SkillExecutor.java`、`models/DefaultSkill.java`、新建 `test/SkillExecutorTest.java`
- **怎么做**：
    1. 新建 `SkillExecutor`，唯一静态入口 + 内部分派（整段可以直接抄进新文件）：
       ```java
       public final class SkillExecutor {
  
           private SkillExecutor() {
           }
  
           public static void execute(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets) {
               SkillData data = skill.getData();
               SkillEffectType effect = data.getEffect();
               if (!effect.isDamaging()) {
                   return;                              // 非伤害段：TODO 后续阶段再分派
               }
               if (targets.isEmpty()) {
                   return;
               }
               double attack = user.getAttribute(AttributeType.ATTACK).get();
               List<Double> params = data.getSkills().get(skill.getLevel() - 1);
               double multiplier = params.getFirst();   // 简化：倍率取第 1 个参数
               double base = attack * multiplier;
               DamageElement element = data.getElement();   // isDamaging 已保证非 null
               switch (effect) {
                   case SINGLE_ATTACK, MAZE_ATTACK ->
                           hit(battle, user, element, base, List.of(targets.getFirst()));
                   case AOE_ATTACK -> {
                       for (CanHit t : targets) {
                           hit(battle, user, element, base, List.of(t));
                       }
                   }
                   case BLAST -> {                      // 主目标 + 相邻（按传入列表顺序取第 2 个）
                       hit(battle, user, element, base, List.of(targets.getFirst()));
                       if (targets.size() > 1) {
                           hit(battle, user, element, base, List.of(targets.get(1)));
                       }
                   }
                   case BOUNCE -> {                     // 段数 = params 第 2 项，如 [0.5, 3]；目标随机
                       int times = (int) (double) params.get(1);
                       for (int i = 0; i < times; i++) {
                           CanHit t = targets.get(battle.getRng().nextInt(targets.size()));
                           hit(battle, user, element, base, List.of(t));
                       }
                   }
                   default -> {
                   }
               }
           }
  
           private static void hit(Battle battle, CanHit user, DamageElement element, double base,
                                   List<? extends CanHit> targets) {
               CanHit target = (CanHit) targets.getFirst();
               battle.applyDamage(target, new Damage(user, target, element, base));
           }
       }
       ```
    2. `DefaultSkill.execute` 改为一行委托：`SkillExecutor.execute(battle, this, user, target);`
- **验收**：`SkillExecutorTest`（`Character.fromAttributes` + `DefaultSkill(1001, 1, 1)` 的 SINGLE_ATTACK 直接调 execute）：
    - 单目标：1 敌，伤害 = 攻击 100 × 倍率 0.5 × 乘区 = 50
    - AOE：3 敌各受 50（且只扣一次 HP）
    - BLAST：2 敌 → 主目标 + 第二个都被打
    - BOUNCE：param `[0.5, 3]` → 3 段，每段 50，目标每次从列表中取
- **依赖**：P1-5（applyDamage 新签名）→ 其实 P1-5 已把 DefaultSkill 改过再改一次，正常
- **说明**：AOE 的"全体"目标由调用方传入（Main / AI 决定打谁），SkillExecutor 只负责按 effect 把 targets 摊开。

---

### P1-9 附加伤害 + 真伤

- **目标**：两套特殊伤害类型：`ADDITIONAL`（附加伤害，引用前段伤害倍率）与 `TRUE`（真伤，跳过乘区）。
- **涉及文件**：`models/Damage.java`（`notCountsAsAttack` 已有则直接用）、`Battle.java`、新建 `test/ExtraTrueDamageTest.java`
- **怎么做**：
    1. `Battle` 加字段 `private double lastHitDamage = 0;`（P1-7 钩子里结算完后更新：`lastHitDamage = <该段结算值>`）
    2. 追加段构造（示例：缇宝式"对目标追加 24% 结算伤害"）：
       ```java
       Damage extra = new Damage(attacker, target, element, lastHitDamage * 0.24, DamageType.ADDITIONAL);
       extra.notCountsAsAttack();   // 不回能、不削韧（P3/P4 判断 countsAsAttack）
       battle.applyDamage(target, extra);
       ```
    3. 真伤段：
       ```java
       Damage trueDmg = new Damage(attacker, target, element, baseValue, DamageType.TRUE);
       trueDmg.trueDamage().notCountsAsAttack();
       battle.applyDamage(target, trueDmg);
       ```
    4. 真伤的 `toValue()` 已由 P1-3 的 `TRUE_DMG_SKIP_ZONES` 开关控制（跳过防/抗/减伤，保留 base）
- **验收**：`ExtraTrueDamageTest`：
    - 主伤害结算 20,000 → `lastHitDamage == 20000` → 追加段 24% → 入账 4,800
    - `extra.getCountsAsAttack() == false`（`Damage` 加 `@Getter`）
    - 真伤：敌人防御 10000 / 抗性 0.9 全区填满 → `trueDamage()` 后 `toValue() == baseValue`
- **依赖**：P1-7（钩子记录 lastHitDamage）、P1-3（trueDamage 开关）

---

## 3. 阶段 P2：真实怪物数据

**本阶段结束时的成果**：`EnemyFactory.create(1002011, 90)` 一条命令造出面板属性正确的冰锋（弱火/雷、冰抗 0.2、Lv90
HP≈16498）。 依赖链严格 `P2-1 → P2-2 → P2-3 → P2-4`。

---

### P2-1 MonsterBean + Constant 加载

- **目标**：把 `monster_config.json` 解析成 bean，能查询弱点/抗性/倍率。
- **涉及文件**：新建 `beans/MonsterBean.java`、`Constant.java`、新建 `test/MonsterBeanTest.java`
- **怎么做**：
    1. `MonsterBean`（record，Gson 直接映射，字段名与 JSON 一致，`damage_resistance` 用 `@SerializedName`）：
       ```java
       public record MonsterBean(Translate name,
               @SerializedName("template_id") int templateId,
               @SerializedName("elite_group") int eliteGroup,
               @SerializedName("hard_level_group") int hardLevelGroup,
               @SerializedName("stance_weak") List<String> stanceWeak,
               @SerializedName("hp_modify_ratio") double hpModifyRatio,
               @SerializedName("defence_modify_ratio") double defenceModifyRatio,
               @SerializedName("health_modify_ratio") double healthModifyRatio,
               @SerializedName("speed_modify_ratio") double speedModifyRatio,
               @SerializedName("stance_modify_ratio") double stanceModifyRatio,
               @SerializedName("damage_resistance") Map<String, Double> damageResistance,
               @SerializedName("debuff_resistance") Map<String, Double> debuffResistance,
               @SerializedName("summon_id") List<Integer> summonId) {}
       ```
       另建 `beans/MonsterTemplateBean.java` 映射 `monster_template_config.json`：
       `(Translate name, double attack, double defence, double health, double speed, double stance, int stanceCount, String stanceType, double effectResistance)`
    2. `Constant` 加：
       ```java
       public static final Map<Integer, MonsterBean> MONSTERS =
               JSONReader.fromJSON("monster_config.json", new TypeToken<Map<Integer, MonsterBean>>() {}.getType());
       public static final Map<Integer, MonsterTemplateBean> MONSTER_TEMPLATES =
               JSONReader.fromJSON("monster_template_config.json", new TypeToken<Map<Integer, MonsterTemplateBean>>() {}.getType());
       ```
    3. 顺手把 `beans/HardLevelGroup` 的 record 组件加 `@SerializedName`（现在没有，Gson 按 record 组件名匹配不上 JSON 的
       `"attack"` 等短名）：
       ```java
       public record HardLevelGroup(
               @SerializedName("attack") double attackRatio,
               @SerializedName("defence") double defenceRatio,
               @SerializedName("health") double healthRatio,
               @SerializedName("speed") double speedRatio,
               @SerializedName("stance") double stanceRatio,
               @SerializedName("effect_hit_rate") double effectHitRateRatio,
               @SerializedName("effect_resistance") double effectResistanceRatio) {
       }
       ```
    4. `Constant` 加 `public static final Map<Integer, Map<Integer, HardLevelGroup>> HARD_LEVEL_GROUPS;`，加载
       `hard_level_group.json`
- **验收**：`MonsterBeanTest`：
    - `Constant.MONSTERS.get(1002011)` 非空；`stanceWeak()` == `["Fire", "Thunder"]`；`damageResistance().get("Ice")` ==
      0.2；`templateId() == 1002011`
    - `Constant.MONSTER_TEMPLATES.get(1002011).health() == 69.75`、`.stance() == 60`
    - `Constant.HARD_LEVEL_GROUPS.get(1).get(90).healthRatio() == 236.53471`（±1e-4）
- **依赖**：无（纯数据层）

---

### P2-2 Enemy 挂弱点/抗性

- **目标**：`Enemy` 能回答"某元素是不是我弱点""我对某元素抗性多少"。
- **涉及文件**：`models/Enemy.java`、新建 `test/WeaknessTest.java`
- **怎么做**：
    1. `Enemy` 加 `@Setter private Set<DamageElement> stanceWeak = Set.of();`
    2. 加 `public boolean isWeak(DamageElement e) { return stanceWeak.contains(e); }`
    3. 加便捷构造：
       `Enemy(String name, DoubleValue[] attributes, Set<DamageElement> stanceWeak, Map<DamageElement, Double> damageResist)`（
       `damageResist` 字段 P1-6 已有）
    4. `damage_resistance` 的 String key → 元素转换用 `DamageElement.fromString`（对 `"Unknown"` 返回 null，防御一下）
- **验收**：`WeaknessTest`：
    - 冰锋式构造（weak `{FIRE, THUNDER}`，resist `{ICE:0.2}`）→ `isWeak(FIRE)` true、`isWeak(ICE)` false
    - 天然免疫检查：`getDamageResist().get(DamageElement.ICE) == 0.2`
- **依赖**：P1-6、P2-1

---

### P2-3 EnemyScaler 等级属性公式

- **目标**：敌人属性 = 模板基础值 × `hard_level_group[组][等级]` 乘区 × `monster_config` 自身修改倍率。
- **涉及文件**：新建 `utils/EnemyScaler.java`、新建 `test/EnemyScalerTest.java`
- **怎么做**：
    1. 一段纯静态函数，入参：`(MonsterTemplateBean tpl, MonsterBean cfg, int level)`：
       ```java
       public static ScaledStats scale(MonsterTemplateBean tpl, MonsterBean cfg, int level) {
           HardLevelGroup hlg = Constant.HARD_LEVEL_GROUPS.get(cfg.hardLevelGroup()).get(level);
           return new ScaledStats(
               tpl.health()  * hlg.healthRatio()  * cfg.healthModifyRatio(),
               tpl.defence() * hlg.defenceRatio() * cfg.defenceModifyRatio(),
               tpl.attack()  * hlg.attackRatio()  * cfg.hpModifyRatio(),      // 注意: 攻击对应 hp_modify_ratio
               tpl.speed()   * hlg.speedRatio()   * cfg.speedModifyRatio(),
               tpl.stance()  * hlg.stanceRatio()  * cfg.stanceModifyRatio(),
               hlg.effectHitRateRatio(), hlg.effectResistanceRatio()
           );
       }
       public record ScaledStats(double health, double defence, double attack, double speed,
                                 double stance, double effectHitRate, double effectResistance) {}
       ```
       （先照抄，数值对不对由验收断言校准：如果 80/90 级跟游戏面板对不上，微调乘区顺序即可，不影响结构）
    2. `elite_group` 暂无数据文件，先 `×1.0` 并注释 `// TODO elite_group`
- **验收**：`EnemyScalerTest`（冰锋 template 1002011 + cfg 1002011 + Lv90）：
    - `health ≈ 69.75 × 236.53471 ≈ 16498.30`
    - `defence ≈ 210 × 5.238095 ≈ 1100.00`
    - `attack ≈ 18 × 36.821384 ≈ 662.78`（×hpModifyRatio=1）
    - `speed == 132.00`、`stance == 60`、`effectResistance == 0.1`
    - 容差 `1e-2`
- **依赖**：P2-1

---

### P2-4 EnemyFactory

- **目标**：一条命令造出真实敌人（属性 + 弱点 + 抗性 + 等级）。
- **涉及文件**：新建 `utils/EnemyFactory.java`、新建 `test/EnemyFactoryTest.java`
- **怎么做**：
    1. `public static Enemy create(int monsterId, int level)`：
       ```java
       MonsterBean cfg = Constant.MONSTERS.get(monsterId);
       MonsterTemplateBean tpl = Constant.MONSTER_TEMPLATES.get(cfg.templateId());
       ScaledStats s = EnemyScaler.scale(tpl, cfg, level);
       AttributeBuilder atb = new AttributeBuilder();
       atb.setBase(HEALTH, s.health()).setBase(DEFENCE, s.defence())
          .setBase(ATTACK, s.attack()).setBase(SPEED, s.speed());
       Enemy e = new Enemy(cfg.name().english(), atb.build(), parseWeak(cfg), parseResist(cfg));
       e.setLevel(level);
       return e;
       ```
    2. `parseWeak`：`stance_weak` 字符串列表 → `EnumSet<DamageElement>`；`parseResist`：key→`DamageElement.fromString`（null
       跳过）
- **验收**：`EnemyFactoryTest`：
    - `EnemyFactory.create(1002011, 90)`：
        - `isWeak(FIRE)` / `isWeak(THUNDER)` true
        - `getDamageResist().get(ICE) == 0.2`
        - `getLevel() == 90`、`getMaxHp() ≈ 16498.30`
- **依赖**：P2-2、P2-3

---

## 4. 阶段 P3：能量系统（零依赖，可提前插队做）

**本阶段结束时的成果**：普攻/战技/终结技/受击/击杀都能回能，满能量才能放大招。

---

### P3-1 能量字段 + gainEnergy

- **目标**：`CanHit` 有能量与回能公式。
- **涉及文件**：`models/CanHit.java`、`Constant.java`、新建 `test/EnergyTest.java`
- **怎么做**：
    1. `CanHit` 加：
       ```java
       @Setter private double currentEnergy = 0;
       @Setter private double maxEnergy = 100;   // 数据化 TODO: character_data 有 energy 表则替换
       ```
    2. `Constant` 加回能基础值（示例值，先按 HSR 通用惯例，数据校准后只改这里）：
       ```java
       public static final double ENERGY_GAIN_BASIC = 20;
       public static final double ENERGY_GAIN_SKILL = 30;
       public static final double ENERGY_GAIN_ULTRA = 5;
       public static final double ENERGY_GAIN_HIT = 10;
       public static final double ENERGY_GAIN_KILL = 5;
       public static final double ENERGY_GAIN_BREAK = 5;
       ```
    3. `CanHit` 加：
       ```java
       public double gainEnergy(double base) {
           double gained = base * (1 + getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get());
           currentEnergy = Math.min(maxEnergy, currentEnergy + gained);
           return gained;
       }
       ```
- **验收**：`EnergyTest`：
    - 回能率 50%（`setAttribute(ENERGY_REGENERATION_RATE, new DoubleValue(0.5))`）→ `gainEnergy(20) == 30`、
      `currentEnergy == 30`
    - 上限：`gainEnergy(200)` 后 `currentEnergy == maxEnergy`
- **依赖**：无（`DoubleValue` 已有；没属性值的 getAttribute 返回 0 值对象，安全）

---

### P3-2 回能接入 + 大招条件

- **目标**：战斗行为自动回能；`castUltra` 检查满能量、释放后清零。
- **涉及文件**：`Battle.java`、新建 `test/EnergyBattleTest.java`
- **怎么做**：
    1. `Battle` 加 `public void grantEnergy(CanHit c, double base) { c.gainEnergy(base); }`
    2. 回能挂点（先做最小集合，够演示）：
        - 技能释放：在 `SkillExecutor.execute`（P1-8）末尾按 `attack_type` 区分：
          ```java
          // SkillExecutor.execute 末尾：
          String attackType = skill.getData().getSkillType();   // "Normal" / "BPSkill" / "Ultra"
          double baseEnergy = switch (attackType) {
              case "Normal" -> Constant.ENERGY_GAIN_BASIC;
              case "BPSkill" -> Constant.ENERGY_GAIN_SKILL;
              case "Ultra" -> Constant.ENERGY_GAIN_ULTRA;
              default -> 0;
          };
          battle.grantEnergy(user, baseEnergy);
          ```
        - 受击回能：放 `Battle.applyDamage`：入账后 `grantEnergy(target, Constant.ENERGY_GAIN_HIT);`（敌人也能回能，无妨）
        - 击杀回能：`applyDamage` 里 `if (target.isDeath()) grantEnergy(attacker, Constant.ENERGY_GAIN_KILL);`
    3. `castUltra` 改造（先清零，再结算终结技自身回能 5）：
       ```java
       public boolean castUltra(CanHit user, List<? extends CanHit> targets) {
           if (user == null || user.isDeath() || user.getCurrentEnergy() < user.getMaxEnergy()) {
               return false;
           }
           Skill ultra = user.getSkills().get(SkillType.ULTRA);
           if (ultra == null) {
               return false;
           }
           if (!requestSkill(ultra, user, targets)) {
               return false;
           }
           processRequests();
           user.setCurrentEnergy(0);                          // 先清零
           user.gainEnergy(Constant.ENERGY_GAIN_ULTRA);       // 再回 5 × (1+回能率)
           return true;
       }
       ```
    4. `Constant.ENERGY_GAIN_*` 全部替换魔法数字
- **验收**：`EnergyBattleTest`：
    - 回能率 0：普攻后 `currentEnergy == 20`；战技后 +30；释放终结技后清零，且终结技自身回 5（ **顺序定义**：先清零再回
      5，测试按此写）
    - 角色被打 1 次 → +10；打死敌人者 → +5
    - `castUltra` 能量不满 → false；满 → true 且清零
- **依赖**：P3-1、P1-8（技能类型区分挂点）

---

### P3-3 击破回能联动

- **目标**：击破瞬间给施放方回能 5（P4-4 调用这一个口子）。
- **涉及文件**：`Battle.java`（加
  `public void gainBreakEnergy(CanHit attacker) { grantEnergy(attacker, Constant.ENERGY_GAIN_BREAK); }`）
- **验收**：`EnergyBattleTest` 补一条：调 `gainBreakEnergy(x)` 后 `x.currentEnergy == 5 × (1+回能率)`
- **依赖**：P3-1

---

## 5. 阶段 P4：韧性 · 击破 · 超击破 · DOT（核心玩法）

**本阶段结束时的成果**：敌人有韧性条，同元素打弱点削韧，削满触发击破伤害 + 推条 + 跳回合 + DOT。 顺序
`P4-1 → P4-2 → P4-3 → P4-4 → P4-5 → P4-6` 严格串行。

---

### P4-1 Enemy 韧性字段

- **目标**：敌人有韧性/击破状态。
- **涉及文件**：`models/Enemy.java`、新建 `test/ToughnessTest.java`
- **怎么做**：
    1. `Enemy` 加：
       ```java
       @Getter private double maxToughness;
       @Getter private double currentToughness;
       @Getter private boolean broken = false;
       @Getter @Setter private int brokenRemainTurns = 0;
       @Getter private DamageElement brokenElement;
       public void setToughness(double toughness) { this.maxToughness = toughness; this.currentToughness = toughness; }
       ```
    2. 削韧方法（P4-2 调用）：
       ```java
       public void reduceToughness(double amount) {
           if (broken || amount <= 0) return;
           currentToughness = Math.max(0, currentToughness - amount);
       }
       public void breakEnemy(DamageElement element) {
           broken = true; brokenElement = element; brokenRemainTurns = 2; currentToughness = 0;
       }
       public void recoverFromBroken() { broken = false; brokenElement = null; currentToughness = maxToughness; }
       ```
    3. 韧性单位 =「点」（`EnemyScaler` 的 stance 直接就是点，冰锋 60）
- **验收**：`ToughnessTest`：
    - `setToughness(60)` → 当前 60；`reduceToughness(30)` → 30；再 `reduceToughness(30)` → 0 且 `isBroken()` 仍
      false（归零不自动破，由 P4-2 判定）
    - `breakEnemy(FIRE)` → `isBroken()` true、`brokenElement == FIRE`
    - `recoverFromBroken()` → `currentToughness == 60`
- **依赖**：无

---

### P4-2 削韧判定

- **目标**：每段伤害按技能 `stance_list` 削韧（先只做「弱点命中才削韧」，非弱点减半留 TODO）；削满归零触发击破。
- **涉及文件**：`Battle.java`、`models/SkillExecutor.java`、`Constant.java`、新建 `test/ToughnessBattleTest.java`
- **怎么做**：
    1. `Constant` 加：`public static final double TOUGHNESS_NON_WEAK_RATIO = 0.5;`
    2. `SkillExecutor.hit(...)` 里、`applyDamage` 之前插入（注意 `hit` 已持有 `SkillData data`，直接传下去）：
       ```java
       reduceToughness(battle, user, element, data, dmg);
       ```
       方法实现（削韧值按技能类型从 `stance_list` 取）：
       ```java
       private static void reduceToughness(Battle battle, CanHit user, DamageElement element,
                                          SkillData data, Damage dmg) {
           if (!dmg.isCountsAsAttack()) {                  // 附加/真伤不削韧
               return;
           }
           CanHit target = dmg.getDefender();
           if (!(target instanceof Enemy e)) {
               return;
           }
           if (e.isBroken()) {                             // 已击破不再削
               return;
           }
           if (!e.isWeak(element)) {                       // 先只对弱点削（非弱点减半留 TODO）
               return;
           }
           double amount = switch (data.getEffect()) {
               case AOE_ATTACK -> data.getStanceList().all();
               case BLAST -> data.getStanceList().spread();
               default -> data.getStanceList().single();
           };
           e.reduceToughness(amount);
           if (e.getCurrentToughness() <= 0) {
               e.breakEnemy(element);
               Damage d = new BreakDamageCalculator().build(user, e, element, amount);   // P4-3
               battle.applyDamage(e, d);                      // 易伤/减伤由 onDamage 钩子注入
               battle.gainBreakEnergy(user);                  // P3-3
           }
       }
       ```
    3. AOE 的"全体削韧"= 每个目标单独扣 `all` 值；单目标扣 `single`（`hit` 里对每个目标各调一次 `reduceToughness`，`BLAST`
       主目标传 `spread`）
- **验收**：`ToughnessBattleTest`：
    - 冰锋（韧性 60，弱火）吃普攻（stance 30）：`currentToughness == 30`
    - 非弱点攻击（冰锋被冰打）：韧性 **不变**（先只做弱点命中，减半规则 TODO）
    - 连续两段普攻 → 第 2 段触发 `isBroken() == true`
- **依赖**：P1-8、P4-1

---

### P4-3 击破伤害

- **目标**：击破瞬间结算：`击破伤害 = 击破基数(等级) × (1+击破特攻) × 技能削韧值 × 防御区 × 抗性区 × 减伤区`。
  **不可暴击、不吃攻击力/增伤**。
- **涉及文件**：新建 `models/BreakDamageCalculator.java`、`Battle.java`、新建 `test/BreakDamageTest.java`
- **怎么做**：
    1. 新建类（ **只需 1 个方法**）：
       ```java
       public class BreakDamageCalculator {
           public Damage build(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
               double breakBase = Constant.BREAKING_RATE.get(attacker.getLevel()) / 10.0; // 数据文件是 10 倍值
               double be = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
               // 把 (1+BE) × 削韧值 折进 base，防御/抗性/易伤/减伤复用 Damage 乘区
               Damage d = new Damage(attacker, enemy, element, breakBase * (1 + be) * stanceDamage, DamageType.BREAK);
               d.defence(attacker.getLevel(), enemy.getAttribute(AttributeType.DEFENCE).get(),
                         attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get());
               d.resist(enemy.getDamageResist().getOrDefault(element, 0.0),
                        attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
               return d;   // 易伤/减伤由 P1-7 的 onDamage 钩子注入，Battle 端 applyDamage 直接结算
           }
       }
       ```
    2. `Constant` 加 `public static final Map<Integer, Double> BREAKING_RATE;`（加载 `breaking_rate.json`，key 是 int 等级）
    3. P4-2 的调用处（`reduceToughness` 里）已写好：`build(...)` → `battle.applyDamage(e, d)` → 推条（P4-4）→
       `gainBreakEnergy(user)`（P3-3），本任务只需把 `build` 写出来
- **验收**：`BreakDamageTest`（攻击者 Lv80、击破特攻 300%、削韧值 112.5、敌防 1150、无抗性、无减伤）：
    - `breakBase = 376.75535`；`(1+3.0) × 112.5 = 450`；防御区 `1000/2150`
    - 期望 `376.75535 × 4.0 × 112.5 × (1000.0/2150.0) ≈ 78855.8`（容差 1.0）
    - `DamageType.BREAK.isCrittable() == false`（回归）
- **依赖**：P1-3、P4-2

---

### P4-4 击破状态：推条 + 跳回合 + 恢复

- **目标**：击破后敌人行动被推迟 25% 行动条；击破期间敌人的回合被跳过；2 回合后恢复。
- **涉及文件**：`Battle.java`、`models/Enemy.java`（若需）、`Main.java`（演示分支）、新建 `test/BreakStateTest.java`
- **怎么做**：
    1. 击破瞬间（P4-2 的 `reduceToughness` 里、`gainBreakEnergy` 之前）加：
       ```java
       battle.queue.delayAction(enemy, 10000.0 / enemy.getAttribute(AttributeType.SPEED).get() * 0.25);  // 25% 行动条
       ```
       或加 `Battle` 助手 `delayMovePercent(CanHit c, double percent)`（内部算 cycleTime）
    2. 敌人回合开头跳过：`Main.round()` 的敌人分支改成：
       ```java
       if (current.getCanHit() instanceof Enemy e && e.isBroken()) {
           e.setBrokenRemainTurns(e.getBrokenRemainTurns() - 1);
           if (e.getBrokenRemainTurns() <= 0) {
               e.recoverFromBroken();
           }
           IO.println("[BROKEN] " + e.getName() + " skips this turn");   // 不行动，直接 afterMove
       } else if (current.getCanHit() instanceof Enemy e) {
           // P5-5 之后再接敌人行动；现在只打印 TODO
       }
       ```
    3. 击破回能：`reduceToughness` 末尾 `battle.gainBreakEnergy(user);`（步骤 2 的代码里已含）
- **验收**：`BreakStateTest`：
    - 击破前记录 `getTimeRemaining(enemySignal)` → 击破后增大（≈ 原剩余 + 0.25×cycle）
    - `isBroken()` 期间敌人轮到时 `[BROKEN]` 输出且不造成伤害
    - `brokenRemainTurns` 从 2 递减到 0 后 `isBroken() == false`、韧性回满
- **依赖**：P3-3、P4-2/P4-3

---

### P4-5 DOT（持续伤害）

- **目标**：击破元素附着 DOT（火→灼烧、雷→触电、物理→裂伤、风→风暴），敌人回合开始结算，"先上先结算"。
- **涉及文件**：新建 `models/Dot.java`、`models/Enemy.java`、`Battle.java`、新建 `test/DotTest.java`
- **怎么做**：
    1. 新类（ **完全独立，不依赖 Buff 体系**，最简单）：
       ```java
       @Getter
       public class Dot {
           private final CanHit source;          // 施放者（Damage.attacker 不允许为 null，必须带来源）
           private final DamageElement element;  // 灼烧=FIRE / 触电=THUNDER / 裂伤=PHYSICAL / 风暴=WIND
           private final double baseDamage;      // 每回合伤害（击破基数 × DOT_RATIO，见步骤 4）
           private int remainingTurns;
  
           public Dot(CanHit source, DamageElement element, double baseDamage, int remainingTurns) {
               this.source = source;
               this.element = element;
               this.baseDamage = baseDamage;
               this.remainingTurns = remainingTurns;
           }
  
           /** 结算一次；返回 true 表示本回合是最后一遍，结算后应移除 */
           public boolean tick() {
               remainingTurns--;
               return remainingTurns <= 0;
           }
       }
       ```
    2. `Enemy` 加：
       ```java
       private final List<Dot> dots = new ArrayList<>();
  
       public void addDot(Dot dot) {
           dots.add(dot);
       }
  
       public void removeDot(Dot dot) {
           dots.remove(dot);
       }
       ```
    3. `Battle.beforeMove` 里对 Enemy 分支结算（先上先结算 = 按列表顺序）：
       ```java
       for (Enemy enemy : enemies) {
           if (enemy.isDeath()) {
               continue;
           }
           for (Dot dot : new ArrayList<>(enemy.getDots())) {
               Damage d = new Damage(dot.getSource(), enemy, dot.getElement(), dot.getBaseDamage(), DamageType.DOT);
               d.defence(dot.getSource().getLevel(), enemy.getAttribute(AttributeType.DEFENCE).get(), 0);
               d.resist(enemy.getDamageResist().getOrDefault(dot.getElement(), 0.0), 0);
               enemy.takeDamage(d.toValue());   // 不走 calculateDamage：DOT 段不接受暴击/增伤
               if (dot.tick()) {
                   enemy.removeDot(dot);
               }
           }
       }
       ```
       易伤/减伤对 DOT 生效（HSR 规则）留 TODO：把 `d.toValue()` 换成走 `onDamage` 钩子的版本即可。
    4. 击破瞬间自动挂 DOT：在 P4-2 的 `reduceToughness` 里 `breakEnemy` 之后：
       ```java
       double dotPerTurn = Constant.BREAKING_RATE.get(user.getLevel()) / 10.0 * Constant.DOT_RATIO;
       enemy.addDot(new Dot(user, element, dotPerTurn, Constant.DOT_TURNS));   // 按击破元素匹配灼烧/触电/…
       ```
       `Constant` 加：`public static final double DOT_RATIO = 0.5;`、`public static final int DOT_TURNS = 3;`（示例值，TODO
       数据校准）
- **验收**：`DotTest`：
    - 敌挂灼烧 `enemy.addDot(new Dot(source, FIRE, 500, 2))`，调用 `battle.beforeMove()` 后 HP -500；再 1 次又 -500；第 3
      次不再扣（DOT 已移除）
    - 先上先结算：灼烧 + 触电各 500 → 每回合先扣灼烧再扣触电（顺序可断言伤害日志顺序 or 只断言总扣血 1000）
    - 伤害类型是 `DamageType.DOT` 且不可暴击（回调确认）
- **依赖**：P1-2（DOT 类型）、P4-3、P4-4

---

### P4-6 超击破（进阶）

- **目标**：同谐开拓者终结技生效期间（简化：队友挂 `SuperBreakBuff`），攻击 **已被击破**的敌人时，该段按超击破公式结算。
- **涉及文件**：新建 `models/buffs/SuperBreakBuff.java`、`Battle.java`、新建 `test/SuperBreakTest.java`
- **怎么做**：
    1. `SuperBreakBuff extends AbstractBuff`：只标记，`applyEffect` 空实现（模板同 `VulnerabilityBuff`，
       `canAct() return true`）
    2. `Battle` 加：
       ```java
       public boolean isSuperBreakActive(CanHit attacker) {
           for (AbstractBuff b : attacker.getBuffManager().getBuffs()) {
               if (b instanceof SuperBreakBuff) {
                   return true;
               }
           }
           return false;
       }
       ```
    3. `Constant` 加：`public static final double SUPER_BREAK_BOOST = 0.4;`（示例常量的 0.4；削韧提高/弱点击破效率留 TODO）
    4. 超击破公式（参照 P4-3 组织，加在 `SkillExecutor` 里；`stanceDamage` = P4-2 该段实际削韧值）：
       ```java
       // SkillExecutor.hit 内：目标已击破 && 施放方有 SuperBreakBuff 时，用超击破替换普通伤害段
       private static Damage superBreak(Battle battle, CanHit user, CanHit target,
                                        DamageElement element, double stanceDamage) {
           double breakBase = Constant.BREAKING_RATE.get(user.getLevel()) / 10.0;
           double be = user.getAttribute(AttributeType.BREAKING_EFFECT).get();
           Damage d = new Damage(user, target, element,
                   breakBase * (1 + be) * stanceDamage * (1 + Constant.SUPER_BREAK_BOOST), DamageType.SUPER_BREAK);
           d.defence(user.getLevel(), target.getAttribute(AttributeType.DEFENCE).get(),
                     user.getAttribute(AttributeType.DEFENCE_IGNORE).get());
           d.resist(target instanceof Enemy e ? e.getDamageResist().getOrDefault(element, 0.0) : 0.0,
                    user.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
           return d;   // 易伤/减伤走 onDamage 钩子；不调 addBoost（超击破不吃攻击/属性增伤）
       }
       ```
- **验收**：`SuperBreakTest`（Lv80、BE 300%、削韧 112.5、超击破提高 40%、敌防 1150、无抗/易伤/减伤）：
    - `376.75535 × 4.0 × 112.5 × 1.4 × (1000.0/2150.0) ≈ 110398`（容差 2.0）
    - 未挂 Buff 或敌人未击破 → 不触发（走普通伤害）
- **依赖**：P4-3、P1-7

---

## 6. 阶段 P5：仇恨 + 敌人 AI

**本阶段结束时的成果**：敌人有自己的回合，会按仇恨加权随机选我方目标、普攻打人。 顺序 `P5-1 → P5-2 → P5-3 → P5-4 → P5-5`。

---

### P5-1 Path 仇恨值

- **目标**：角色命途决定基础仇恨（存护/毁灭/其他 = 150/125/100）。
- **涉及文件**：新建 `enums/Path.java`、`models/Character.java`、新建 `test/AggroTest.java`
- **怎么做**：
    1. 枚举 + 查询：
       ```java
       public enum Path {
           PRESERVATION(150), DESTRUCTION(125), OTHER(100);
  
           @Getter
           private final int aggro;
  
           Path(int aggro) {
               this.aggro = aggro;
           }
  
           public static Path fromName(String name) {
               return switch (name) {
                   case "存护" -> PRESERVATION;
                   case "毁灭" -> DESTRUCTION;
                   default -> OTHER;
               };
           }
       }
       ```
    2. `Character` 加 `@Setter private Path path = Path.OTHER;`（builder `path(Path)` 链）
- **验收**：`AggroTest`：一个 `path = Path.PRESERVATION` 的角色 `getPath().getAggro() == 150`；
  `Path.fromName("毁灭") == DESTRUCTION`

---

### P5-2 受击概率 + 嘲讽

- **目标**：`Battle` 提供仇恨表与加权选择工具。
- **涉及文件**：`Battle.java`、`models/CanHit.java`（嘲讽 = 已有 Buff 系统实现 `TauntBuff`）、新建
  `test/AggroBattleTest.java`
- **怎么做**：
    1. `Battle` 加：
       ```java
       public Map<CanHit, Double> getAggroTable(List<? extends CanHit> allies) {
           double total = allies.stream().mapToDouble(this::aggroOf).sum();
           return allies.stream().collect(toMap(a -> a, a -> aggroOf(a) / total));
       }
  
       public double aggroOf(CanHit a) {                       // public：P5-4 的 TargetSelector 要跨包调用
           double aggro = a instanceof Character c ? c.getPath().getAggro() : 100;
           for (AbstractBuff b : a.getBuffManager().getBuffs()) {   // getBuffs：P1-7 已加
               if (b instanceof TauntBuff t) {
                   aggro *= 1 + t.getExtraPercent();           // 嘲讽：仇恨 × (1 + 百分比)
               }
           }
           return aggro;
       }
       ```
       （`toMap` 需要 `import static java.util.stream.Collectors.toMap;`）
    2. 嘲讽 Buff（新建 `models/buffs/TauntBuff.java`）：
       ```java
       public class TauntBuff extends AbstractBuff {
           @Getter
           private final double extraPercent;
  
           public TauntBuff(int duration, double extraPercent) {
               super(duration, false);
               this.extraPercent = extraPercent;
           }
  
           @Override
           public boolean canAct() {
               return true;
           }
  
           @Override
           public void applyEffect(CanHit target) {
           }
  
           @Override
           public void removeBuff(CanHit target) {
           }
  
           @Override
           public void tickEffect(CanHit target) {
               decreaseDuration();
           }
       }
       ```
- **验收**：`AggroBattleTest`：
    - 2 角色：存护 (150) + 其他 (100) → 概率 0.6 / 0.4（`assertEquals(0.6, table.get(preservation), 1e-6)`）
    - 给其他挂 `TauntBuff(2, 1.0)` → 其概率 = 200/350 ≈ 0.5714
- **依赖**：P5-1、P1-7（getBuffs）

---

### P5-3 EnemySkill（敌人普攻）

- **目标**：敌人有能执行的技能。数据里 **没有**敌人技能表（skills.json 只有角色），先用模板简化：普攻 = 攻击力 × 100%，元素取
  `stance_type`。
- **涉及文件**：新建 `models/EnemySkill.java`、`models/Enemy.java`（挂 skills）、`utils/EnemyFactory.java`、新建
  `test/EnemySkillTest.java`
- **怎么做**：
    1. `EnemySkill extends Skill`：
       ```java
       public class EnemySkill extends Skill {
           private final DamageElement element;
           private final double multiplier;    // 简化: 1.0
  
           public EnemySkill(DamageElement element, double multiplier) {
               this.element = element;
               this.multiplier = multiplier;
           }
  
           @Override
           public int getLevel() {
               return 1;
           }
  
           @Override
           public SkillData getData() {
               return null;                    // EnemySkill 不走 getData（execute 全自定义）
           }
  
           @Override
           public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
               if (target == null || target.isEmpty()) {
                   return;
               }
               CanHit t = (CanHit) target.getFirst();
               double base = user.getAttribute(AttributeType.ATTACK).get() * multiplier;
               battle.applyDamage(t, new Damage(user, t, element, base));
           }
       }
       ```
    2. `EnemyFactory.create` 末尾：
       ```java
       DamageElement e = DamageElement.fromString(tpl.stanceType());
       e.setSkill(SkillType.COMMON, new EnemySkill(e != null ? e : DamageElement.PHYSICAL, 1.0));
       ```
- **验收**：`EnemySkillTest`：
    - 冰锋 Lv90（attack ≈ 662.78）普攻一个防御 0 的玩家（level 80, def 0）→ 伤害 = 662.78 × (1000/1000) = 662.78
      ±0.1（玩家防御区 = (200+800)/ (0+1000)=1.0）
    - `Damage` 的 element == ICE（stance_type）
- **依赖**：P2-4、P1-8（Damage 用法）

---

### P5-4 TargetSelector

- **目标**：给出选择策略：主策略 = 仇恨加权随机。
- **涉及文件**：新建 `models/ai/TargetSelector.java`、新建 `test/TargetSelectorTest.java`
- **怎么做**：
    1. 纯静态，按累计权重选：
       ```java
       public final class TargetSelector {
           public static CanHit select(Battle battle, CanHit self, List<? extends CanHit> candidates, Random rng) {
               double[] weights = candidates.stream().mapToDouble(battle::aggroOf).toArray();
               double total = Arrays.stream(weights).sum();
               double roll = rng.nextDouble() * total;
               for (int i = 0; i < weights.length; i++) {
                   roll -= weights[i];
                   if (roll <= 0) return candidates.get(i);
               }
               return candidates.getLast();
           }
       }
       ```
       **注意 dead 过滤**：`candidates.removeIf(CanHit::isDeath)` 在调用侧做；`aggroOf` 是 P5-2 加的 **public** 方法，跨包可调
- **验收**：`TargetSelectorTest`：
    - 存护 (150)+其他 (100)，用 `new Random(42)` 执行 1000 次 → 存护被选频率 ≈ 0.6，偏差 < 3%
    - 死亡角色（`takeDamage(99999)` 后）不返回
- **依赖**：P5-2

---

### P5-5 敌方回合执行

- **目标**：`Main.round()` 的 `"Skip"` 分支改成"敌人真正行动"。
- **涉及文件**：`Main.java`、`Battle.java`（如需公共工具 `getOpponents`）
- **怎么做**：
    1. `Battle` 加 `public List<? extends CanHit> getOpponents(CanHit self)`：
       `self.getCamp() == Camp.PLAYER ? enemies : characters`（先只返回列表，不过滤死亡：Main 里过滤）
    2. `Main.round()` 敌人分支：
       ```java
       Enemy e = (Enemy) current.getCanHit();
       if (e.isBroken()) { /* P4-4 已有 */ }
       else {
           List<CanHit> alive = battle.characters.stream().filter(c -> !c.isDeath()).toList();
           CanHit target = TargetSelector.select(battle, e, alive, battle.getRng());
           if (battle.performAction(e.getSkills().get(SkillType.COMMON), List.of(target))) {
               IO.println(e.getName() + " attacks " + target.getName());
           }
       }
       ```
    3. `battle.afterMove()` 已有死亡清理，无需改
- **验收**：`BattleEnemyAiTest`（或手动跑 Main）：
    - 敌人回合不再打印 `"Skip"`；敌人攻击后玩家 HP 减少
    - 玩家被打死后从行动条移除（`queue.size()` 减 1）
- **依赖**：P5-3、P5-4

---

## 7. 阶段 P6：命中 / 治疗 / 护盾

---

### P6-1 效果命中与抵抗

- **目标**：`生效概率 = 基础概率 × (1+施加方命中) × (1-受击方抗性) × (1-特定抵抗)`，clamp [0,1]。
- **涉及文件**：`Battle.java`、`models/BuffManager.java`（挂判定）、`beans/MonsterBean.java`（debuff_resistance 已有）、新建
  `test/HitResistTest.java`
- **怎么做**：
    1. `Battle` 加：
       ```java
       public double hitChance(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
           double hit = caster.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
           double resist = target.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
           double specific = 0;
           if (target instanceof Enemy e) {
               specific = e.getDebuffResist().getOrDefault(specificResistKey, 0.0);  // 步骤 2 的 map
           }
           return Math.max(0, Math.min(1, baseChance * (1 + hit) * (1 - resist) * (1 - specific)));
       }
       ```
    2. `Enemy` 加 `@Setter private Map<String, Double> debuffResist = Map.of();`（`EnemyFactory.create` 填
       `cfg.debuffResistance()`）
    3. 挂点：`BuffManager.addBuff` 前由技能侧调用 `battle.hitChance(...)`——先做一个测试帮手
       `Battle.applyDebuffChance(...)`，P1 之后的 Buff 施加流程再接入（DEMO 阶段先手动调）
- **验收**：`HitResistTest`：
    - base 1.0, hit 0, resist 0.3 → 0.7
    - base 1.0, hit 0.5, resist 0.3 → 1.0（clamp）
    - base 0.8, hit 0.25, resist 0.2 → 0.8
    - 冰锋（`STAT_CTRL_Frozen: 1`）冰冻技能 → 0.0
- **依赖**：P2-4（enemy 数据）、P1-7（getBuffs 若需要）

---

### P6-2 治疗乘区

- **目标**：`治疗量 = 基础(倍率×属性+固定) × (1+治疗加成) × (1+受疗加成) × (1-治疗降低)`。
- **涉及文件**：`Battle.java`（加 `public double calculateHeal(...)`）、`Constant.java`、新建 `test/HealShieldTest.java`
- **怎么做**：
    1. 独立静态公式（不碰 Damage）：
       ```java
       public double calculateHeal(CanHit healer, CanHit target, double baseAmount) {
           return baseAmount
                   * (1 + healer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get())
                   * (1 + target.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get());
       }
       ```
    2. 治疗执行：`target.heal(calculateHeal(healer, target, amount))`（`CanHit.heal` 已有、不会超上限）
- **验收**：`HealShieldTest`：治疗加成 30%、受疗 20%、基础 1000 → 1560；`heal` 后 HP 精确、且不超 `maxHp`

---

### P6-3 护盾

- **目标**：`CanHit.shield`，伤害先扣盾，盾破前不死；护盾不叠加。
- **涉及文件**：`models/CanHit.java`（`takeDamage` 改造）、新建 `test/ShieldTest.java`
- **怎么做**：
    1. `CanHit` 加 `@Setter private double shield = 0;`
    2. `takeDamage` 开头：
       ```java
       if (shield > 0) {
           double absorbed = Math.min(shield, damage);
           shield -= absorbed; damage -= absorbed;
           if (damage <= 0) return false;
       }
       ```
    3. 护盾量 = `基础 × (1 + 护盾提高)`，先以 `Battle.grantShield(CanHit target, double amount)` 实现（提高词条 ATTRIBUTE
       暂无 → `×1`，TODO）
- **验收**：`ShieldTest`：
    - 盾 500、受 300 → HP 不减、盾 200
    - 再受 300 → 盾 0、HP -100、不死
    - 盾破时 `isDeath() == false`
- **依赖**：无

---

## 8. 阶段 P7：轮次 · 胜负 · 关卡

---

### P7-1 轮次制行动值

- **目标**：首轮总行动值 150、之后每轮 100（当前是 10000/speed 恒定）。
- **涉及文件**：`Queue.java`、新建 `test/QueueRoundTest.java`
- **怎么做**：
    1. `Queue.initialize()` 里首圈系数 1.5：
       ```java
       s.setNextActionTime(s.cycleTime() * 1.5);   // 首轮 150 行动值
       ```
       `setTopZero()/resetSignal()/addCombatant()` 保持 `+cycleTime()`（100 行动值/圈）
    2. 加 `public int getRound()`（按 `elapsed` 推算：`(int)(elapsed / 100) + 1`，够演示用）
- **验收**：`QueueRoundTest`：speed 100 → `move()` 后 elapsed=150；再 3 次各 +100；speed 200 → 首圈 75
- **依赖**：无

---

### P7-2 额外回合

- **目标**：`Battle.grantExtraTurn(canHit)`：立即行动，不消耗回合计数。
- **涉及文件**：`Battle.java`、`Queue.java`（复用 `advanceActionByPercent(target, 1.0)` 即可）
- **怎么做**：
    1. `Battle` 加：
       ```java
       public void grantExtraTurn(CanHit c) {
           queue.advanceActionByPercent(c, 1.0);  // 立即行动
           // 回合计数不变: Queue 无回合计数, 由 P7-1 的 getRound 推算 elapsed — 天然满足
       }
       ```
- **验收**：`QueueRoundTest` 补：`grantExtraTurn` 后 `queue.peekNext() == c` 且 `c` 行动后 `getRound()` 不变（一次性）

---

### P7-3 胜负状态机

- **目标**：`Battle` 有 `NOT_STARTED / RUNNING / WIN / LOSE`，双方全灭后停表。
- **涉及文件**：`Battle.java`、新建 `test/BattleResultTest.java`
- **怎么做**：
    1. `Battle` 加：
       ```java
       public enum Status { NOT_STARTED, RUNNING, WIN, LOSE }
  
       @Getter
       private Status status = Status.NOT_STARTED;
  
       public void startBattle() {
           status = Status.RUNNING;
           // 原有 onBattleStart / processRequests 逻辑保留
       }
       ```
    2. `removeDeadCombatants()` 末尾：
       ```java
       if (enemies.stream().allMatch(CanHit::isDeath)) status = Status.WIN;
       else if (characters.stream().allMatch(CanHit::isDeath)) status = Status.LOSE;
       if (status != Status.RUNNING) { /* 标记: queue 不再 move */ }
       ```
    3. `stepForward()` 开头：`if (status == Status.WIN || status == Status.LOSE) return;`
- **验收**：`BattleResultTest`：把敌人打死（`takeDamage(999999)` 后调公开的 `battle.processRequests()` 触发死亡清理）→
  `status == WIN` 且 `stepForward()` 不再推进；全员死 → LOSE
- **依赖**：无

---

### P7-4 StageBean + 波次

- **目标**：解析 `stage.json`，按 `monster` 列表（每项一波）依次进怪，波间可配置清理。
- **涉及文件**：新建 `beans/StageBean.java`、`Constant.java`、新建 `models/WaveManager.java`、新建
  `test/WaveManagerTest.java`
- **怎么做**：
    1. Bean：
       ```java
       public record StageBean(String type,
               @SerializedName("hard_level_group") int hardLevelGroup,
               int level,
               List<Map<String, Integer>> monster) {}
       ```
       `Constant.STAGES = fromJSON("stage.json", TypeToken<Map<Integer, StageBean>>)`
    2. `WaveManager`：
       ```java
       public class WaveManager {
           private final Battle battle;
           private final StageBean stage;
           private int waveIndex = -1;
  
           public WaveManager(Battle battle, StageBean stage) {
               this.battle = battle;
               this.stage = stage;
           }
  
           public boolean nextWave() {
               if (++waveIndex >= stage.monster().size()) {
                   return false;
               }
               for (int id : stage.monster().get(waveIndex).values()) {
                   Enemy e = EnemyFactory.create(id, stage.level());
                   battle.enemies.add(e);
                   battle.addRequestItems.add(e);   // 复用 addRequestItems 进场
               }
               return true;
           }
       }
       ```
       注意进怪时机：`Battle.processRequests` 的 `processAddRequests` 会入队
- **验收**：`WaveManagerTest`：stage 103201（3 怪 1 波）→ `nextWave()` true，`battle.enemies.size() == 3`；第 2 次调
  false（若多波依次减数量）

---

### P7-5 StageFactory + 难度

- **目标**：`StageFactory.load(stageId)`：按 stage 的 `hard_level_group`/`level` 组装一个可运行 Battle。
- **涉及文件**：新建 `utils/StageFactory.java`、新建 `test/StageFactoryTest.java`
- **怎么做**：
    1. `public static Battle load(int stageId)`：
       ```java
       public static Battle load(int stageId) {
           StageBean stage = Constant.STAGES.get(stageId);
           List<Character> team = List.of(
                   Character.fromAttributes("P1", 100, 100, 100, 100),
                   Character.fromAttributes("P2", 100, 100, 100, 100));   // 临时，TODO 数据化
           Battle battle = new Battle(team, List.of());
           new WaveManager(battle, stage).nextWave();
           return battle;
       }
       ```
- **验收**：`StageFactoryTest`：`load(103201)` 返回 Battle；`battle.enemies.size() == 3`；`stepForward()` 首轮后
  `status != NOT_STARTED`（P7-3 后的 RUNNING）
- **依赖**：P7-4、P2-4、P7-3

---

## 9. 阶段 P8：演示与收尾

---

### P8-1 Main 修复 + demo 包拆分

- **目标**：`Main` 变成 `public static void main(String[] args)` 入口，逻辑拆进 `demo/`。
- **涉及文件**：`Main.java`、新建 `src/main/java/com/laosun/aluminium/demo/CharacterDemo.java`、`demo/BattleDemo.java`
- **怎么做**：
    1. `static void main()` → `public static void main(String[] args)`
    2. 属性预览逻辑（遗器/角色构建部分）搬 `CharacterDemo`；战斗部分搬 `BattleDemo`
    3. `Main.main` 只留 `BattleDemo.run()`（+可选 `CharacterDemo.run()`）
- **验收**：`.\gradlew.bat run` 能跑；Main 只剩入口调用

---

### P8-2 真实内容演示（冰锋战）

- **目标**：冰锋（弱火/雷）+ 2 角色（火/雷属性技能）演示完整循环：普攻→削韧→击破→推条→跳回合→DOT→回能→大招。
- **涉及文件**：`demo/BattleDemo.java`、按需小修 `Battle`/`Enemy`
- **怎么做**：
    1. 角色：`Character.builder().cid(...)` 选火/雷角色的真实 cid（如雷电将军风格角色），或 `fromAttributes` +
       `DefaultSkill` 指定 `element`（`DefaultSkill` 目前 element 走数据——简化：用 `Character.fromAttributes` + 自定义
       `Skill` 子类固定 element=FIRE）
    2. 回合循环：
       ```java
       while (battle.getStatus() == Battle.Status.RUNNING) {
           battle.stepForward();
           battle.beforeMove();
           // 我方：ask 玩家输入或自动按顺序放技能（battle.performAction(...)）
           // 敌方：P5-5 的目标选择 + 普攻
           battle.afterMove();
       }
       ```
    3. 每步 `battle.printBattle()` + 韧性打印（P4 之后补 `printHp` 加韧性/`[BROKEN]`/DOT 标记）
- **验收**：一遍跑通：削韧 → 击破伤害 → 敌人跳回合 → DOT 扣血 → 我方满能量放大招 → 敌人死亡 → WIN

---

### P8-3 测试总盘点 + 基准 + 零警告

- **目标**：全部验收测试在，`gradlew test` 全绿，出 Benchmark 对比，零编译警告。
- **涉及文件**：`Benchmark.java`、各测试类
- **怎么做**：
    1. 对照【进度总览】表：每个 ☐ 变成 ✅ 时对应测试类已在
    2. `Benchmark.java`：`Random` 固定种子，10 万轮战斗计时（改造前数值存 one 个字段对比）
    3. `.\gradlew.bat build` 无 warning；`git add -A && git commit`
- **验收**：全绿 + Benchmark 输出前后耗时对比

---

## 10. 远期（只记规格，不排实现）

> 做完 P1–P8 再开。以下只保证"有规格锚点"，不承诺顺序。

| 项目                 | 规格锚点                                                                                                                                                                                                   |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **C 召唤物/忆灵**    | `Summon` 现有基类；忆灵 = 独立血条/能量/可受击/可选中，伤害类型 `MEMORY`，面板快照本体；传统召唤物（追加攻击 `ADDITIONAL`、无实体）。`models/Memosprite` 新类                                              |
| **B4 Boss 机制**     | 受击反击（`onDamage` 钩子已具备）、半血换招（Enemy 加 `phase` 字段）、召唤（`summon_id` 数据已有，`WaveManager` 复用）                                                                                     |
| **D5 模拟宇宙/欢愉** | 祝福池化 = `BuffManager` 抽 3 选 1；欢愉伤害公式：`基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))`，禁攻击力/属性增伤；阿哈行动单位：`速度 = 80 + 最快/5 + 第二/10 + 第三/20 + 最慢/50` |
| **界面**             | `Battle.getQueueSnapshot / printHp` 已有 CLI 化出口；UI 层未来接 `Battle` 事件流即可                                                                                                                       |

---

## 附：为什么这样排（给"想改顺序"的你）

1. **P1 先行**：所有机制（削韧、击破、DOT、命中判定）最终都要"过一遍 Damage 流水线"，地基不牢后面全返工。
2. **P2 紧跟**：数据 bean 是纯 IO，最无聊但最不容易出错，先做掉后面测试全是真实数据，验收不用编数字。
3. **P3 能量零依赖**：卡住了就抽空做它，5 个常量 + 2 个方法，半天完事。
4. **P4 击破**：这是"像不像 HSR 游戏"的分水岭，做完就能看到冰锋被火打的完整表演。
5. **P5 敌人 AI**：靠 P2 的真实攻击力 + P1 的伤害流水线，敌人第一次"会打人"。
6. **P7 关卡**：全部机制齐了才谈关卡，之前 StageFactory 造出来也是死的。

**每步保持：可编译 → `.\gradlew.bat test` → 提交。**
