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
| 角色机制   | 引擎不认角色：`Battle`/`SkillExecutor`/`Damage`/`CanHit` 里**禁止 `cid` 判断**。角色机制先数据化（P8-7 触发器表 + 效果词表），写不成数据的才允许专用类，并在 P8-0 的 triage 表登记 |

### 0.3 现有代码速查（实现前扫一眼）

| 类                              | 作用                                                             | 你要知道的口子                                                                                             |
|---------------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `Battle`                        | 战斗循环/行动条/伤害入口                                         | `applyDamage`（**唯一结算入口**：装配+扣血+返回结算值）/ `assemble`（私有装配）/ `applyAdditionalDamage` / `applyTrueDamage` / `targetableEnemies`（可被选目标的唯一出口）/ `getRng` |
| `models.Damage`                 | 伤害对象（attacker/defender/element/type/skillBaseValue + `List<Area>` 乘区） | `toValue()` / `breakdown()` / 7 个乘区 accessor / `addBoost` 等装配口 / `crit`·`fixedCrit` / `trueDamage` / `notCountsAsAttack` |
| `models.CanHit`                 | 所有参战实体基类                                                 | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / `getLevel`（P1-4）/ `onDamage`（`DamageEvent`）/ `afterAttack`（`AttackEvent`）/ `isInvulnerable` |
| `models.DoubleValue`            | 属性值（base × (1+Σadd%) × Π(1+mul%) + Σpure）。**P1-3 起被 `Damage.PercentArea`（可累加乘区）复用** | `Modifier.addPercent / multiplyPercent / pure`（带 source/roleId，可按来源撤销）                          |
| `models.BuffManager`            | Buff 挂载/到期                                                   | `addBuff` / `canAct` / `beforeMove` / `afterMove`                                                          |
| `models.AbstractBuff`           | Buff 基类                                                        | `applyEffect` / `removeBuff` / `tickEffect` / `duration()`                                                 |
| `models.Skill` / `DefaultSkill` | 技能抽象 + 默认实现（打 target.getFirst()；**skillId 写死 1**）   | P1-8 改造；P8-2 接真实槽位映射                                                                              |
| `models.SkillData`              | 技能运行时数据                                                   | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）                               |
| `Queue`                         | 行动条（10000/speed，heap）                                      | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `addCombatant`                          |
| `Constant`                      | 数据加载总入口                                                   | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_POINTS`                                                       |
| `models.Character`              | 角色。**Builder 面板管线已全**（level 缩放/光锥/遗器/行迹），缺 element/path/aggro 字段 | P1-4 补 `getLevel()`；P8-1 补 element/path/aggro + `CharacterFactory`                                     |
| `models.Enemy`                  | 敌人（无弱点/韧性/抗性）                                         | P1-6 / P2-2 / P4-1 扩展                                                                                    |
| `enums.SkillEffectType`         | 11 值 + Category                                                 | `isDamaging()` 判断是否走伤害流水线                                                                        |

### 0.4 数据文件速查（P2 之后离不开）

| 文件                           | 内容                                                                                                                                                                                                              | 已确认的锚点数据                                                                                                                                          |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `monster_config.json`          | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak[7元素], hp/defence/health/speed/stance_modify_ratio, damage_resistance{元素:值}, debuff_resistance{STAT_*:值}, summon_id}` | **1002011 冰锋**：template_id=1002011，弱 `[Fire, Thunder]`，抗 `{Physical .2, Ice .2, Wind .2, Quantum .2, Imaginary .2}`，冰冻免疫 `STAT_CTRL_Frozen=1` |
| `monster_template_config.json` | 模板基础属性：`template_id → {attack, defence, health, speed, stance, stance_count, stance_type, effect_resistance}`                                                                                              | **1002011**：attack=18, defence=210, health=69.75, speed=100, stance=60, stance_count=1, stance_type=Ice, effect_resistance=0.2                           |
| `hard_level_group.json`        | `{组: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}`                                                                                                                      | 组1 Lv90: `{36.821384, 5.238095, 236.53471, 1.32, 1, 0.32, 0.1}`                                                                                          |
| `breaking_rate.json`           | `等级 → 击破基数（原始 10 倍值）`                                                                                                                                                                                 | Lv80 = **3767.5535 → 公式里 /10 = 376.75535**                                                                                                             |
| `skills.json`                  | `cid → {槽位 → {attack_type, max_level, param_list, skill_effect, stance_list{single,all,spread}, element}}`                                                                                                      | 槽位 1=普攻 2=战技 3=终结技 4=天赋 6=迷宫攻击 7=秘技；**敌人技能不在本文件 → P9-1 自建**                                                        |
| `character_data.json`          | 角色基础：`cid → {name, attribute(元素小写), mt(命途), max_energy, aggro, attack/defence/health/speed, crit_*}`                                                                                                 | 景元 1204：thunder / mt=all / energy=130 / aggro=75 / hp158.4 / atk95.04 / def66 / speed99                                                      |
| `character_id_mappings.json`   | `cid → {chinese, english}` 名字对照（character_data 已含 name，此文件仅生成脚本/校验用）                                                                                                                          | 1204 → 景元 / Jing Yuan                                                                                                                         |
| `enemy_skills.json`            | **P9-1 自建**：`template_id → [{name, attack_type, effect, element, multiplier, stance, hits, ai_weight, condition}]`                                                                                            | 数据源缺失（turnbasedgamedata 未下发怪物技能表）；自建最小表，找到源数据后只换 `Constant` 加载                                                                             |
| `stage.json`                   | `stage_id → {type, hard_level_group, level, monster:[{Monster0:id,...}(一波)]}`                                                                                                                                   | `103201`：level 29，monster = 1022020/1023010/1022020                                                                                                     |

### 0.5 机制文档（公式以此为准）

| 文档 | 内容 | 用法 |
|---|---|---|
| `E:\code\blog\hsr\HSR.md` | 乘区总览（§2）、行动/破韧/仇恨（§3）、治疗护盾（§4）、忆灵（§5）、欢愉（§6）、超击破（§7） | **公式有疑义时以它为准**；本文档里所有乘区公式都已按它核对过 |
| `E:\code\blog\hsr\GLOSSARY.md` / `GLOSSARY_EXTRA.md` | 游戏名词表（含官方描述原文） | 查"某词到底什么意思"、判断某个效果属于哪个乘区 |

已核对结论（P1-3 落地时逐条对过）：

- §2.2 伤害修饰区 = 增伤 `1+Σ` × 易伤 `1+Σ`（cap **3.5**）× 减伤 `Π(1-r)`（floor **0.01**）× 虚弱 `1-Σ`（floor **0.2**）—— 与 `Damage` 的四个区一致。
- §2.5 抗性区 = `1 - 抗性`，抗性范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`；**负抗是全效**（不是半效）。
- §2.4 防御区 `(200+10L)/(def+200+10L)`，其中 `def = 原始防御 × (1 - 减防% - 防御穿透%)`（**减防与穿透加算**），上限 1、不为负。
- §6.4/§6.5 欢愉伤害：**吃双爆区、不吃增伤区**（"不受伤害提高类效果所影响"）⇒ `DamageType.ELATION(isCrittable=true, isBoostable=false)`。
- §7.2 超击破：不吃攻击力/常规增伤/双暴，吃等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤。
- §7.1 单位：80 级**基础击破基数 3767**（削韧单位 1）vs **超击破 376.7**（削韧单位 10）——两套刻度不可混用。
- §2.2 增伤区/易伤区各自都是「**伤害类型** + **攻击类型**」加算进同一个区：所以 P7/P8 的"战技/终结技/追加攻击增伤"
  也往 `BoostArea` 灌（Buff 侧按 skill_type 过滤），不是新开一个区。
- §4 治疗/护盾（P6-2 用）：`治疗 = 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)`；`护盾 = 基础 × (1 + Σ护盾量提高)`。

---

### 0.6 怪物数据陷阱（P2 实测记录，别再踩）

| # | 陷阱 | 事实 | 处理 |
|---|---|---|---|
| 1 | **`hp_modify_ratio` 是幽灵字段** | tbgd 只有 `HPModifyRatio`，**没有** `HealthModifyRatio`；解析器猜错字段名后留了个恒为 1 的 `hp_modify_ratio`。真实血量系数在本数据的 **`health_modify_ratio`**（100201101：真实 0.266667 vs 幽灵 1；802501003：1.979167 vs 1） | bean 不读幽灵字段；`Constant` 注释写明 |
| 2 | **`attack_modify_ratio` 未导出** | tbgd 的 `AttackModifyRatio` 有 **444/2649** 个怪 ≠ 1（如 100201506 = 0.33333302），本数据整体缺这一列 | 已从 tbgd 生成补丁文件 `monster_attack_modify_ratio.json`（只含 ≠1 的 444 条），`Constant` 装载时合并 |
| 3 | **`effect_hit_rate` / `effect_resistance` 是加值不是系数** | 组1·Lv90 给 0.32 / 0.1；冰锋模板 `effect_resistance` 0.2 → **0.2 + 0.1 = 0.3（30%）** 才对得上 HSR.md §1.2；相乘会得到 0.02 | bean 字段名去掉 `Ratio` 后缀 + 注释说明 |
| 4 | **等级组的字段名 ≠ bean 字段名** | JSON 键是 `attack/defence/health/speed/stance`，bean 若写成 `attackRatio` 之类，Gson 静默读成 **0** | 一律用 `@SerializedName`（已修 `HardLevelGroup`） |
| 5 | **组号/等级来自关卡** | 怪的 `hard_level_group` 通常是 1；真正决定难度的是 `StageConfig` 的 `HardLevelGroup` + `Level` | P2 由调用方显式传参；关卡驱动留 P7-4 |
| 6 | **精英组有两张表** | 普通关卡 `EliteGroup`；无限波次（`_StageInfiniteGroup`）用 `InfiniteEliteGroup`（绝境王虫 ×6.2、破晓之眼 ×5.0）。用错表血量差几倍；两者都来自**波组**而非怪自身 | 本数据暂无 `elite_group.json`；P2 只留系数参数（缺省 1），接表留 P7-4/P9 |

同时验证过：**tbgd 与本仓库数据同版本**（2649 个 id 全部对得上；`HPModifyRatio` 不一致 0 条、`SpeedModifyRatio` 0 条、`DefenceModifyRatio` 仅 1 条 = 800205073）。

> ⚠ `monster_attack_modify_ratio.json` 放在被 `.gitignore` 忽略的 `data/` 目录里，但**已用 `git add -f` 纳入版本控制**（否则新克隆会缺文件、`MonsterDataTest` 直接红；同时 `git clean -xdf` 也删不掉它）。
> **正解在导出脚本**：等 `monster_config.json` 自己带上 `attack_modify_ratio` 列之后，删掉这个补丁文件与 `Constant.normalizeMonsterConfigs` 里的合并代码即可。
>
> 再生成命令（需 `E:\turnbasedgamedata`，即 `data/data_path.txt` 指向的数据源）：
> ```powershell
> $t = Get-Content 'E:\turnbasedgamedata\ExcelOutput\MonsterConfig.json' -Raw -Encoding UTF8 | ConvertFrom-Json
> $o = Get-Content 'src\main\resources\data\monster_config.json' -Raw -Encoding UTF8 | ConvertFrom-Json
> $m = @{}; foreach ($e in $t) { if ($e.MonsterID) { $m[[int]$e.MonsterID] = $e } }
> $r = @{}
> foreach ($p in $o.PSObject.Properties) {
>   $e = $m[[int]$p.Name]
>   if ($e -and $e.AttackModifyRatio -and [math]::Abs([double]$e.AttackModifyRatio.Value - 1) -gt 1e-9) {
>     $r[[string]$p.Name] = [double]$e.AttackModifyRatio.Value
>   }
> }
> [System.IO.File]::WriteAllText('src\main\resources\data\monster_attack_modify_ratio.json',
>     (($r | ConvertTo-Json -Depth 2) + "`n"), (New-Object System.Text.UTF8Encoding($false)))
> # 期望：444 条，约 12KB
> ```

---

## 1. 进度总览

**主线顺序**：`P1 → P2 → P3(能量) → P4(韧性击破) → P5(仇恨+AI) → P6(命中/治疗/护盾) → P7(轮次/胜负/关卡) → P8(角色数据化) → P9(怪物全机制) → P10(机制补完) → P11(收尾)`。 P3
零依赖可随时插入；P5-1/P5-2 需在 P5-3 前。

**"角色和怪物什么时候进来"一句话版**：

- **角色**：P1–P7 一律 `Character.fromAttributes` 占位 → **P8 起全部换真实角色**（`CharacterFactory`：面板/元素/命途/能量/真实技能/战技点）。天赋与追加攻击 P8-3，SP P8-4。
- **怪物**：**P2 进真实面板数据**（属性/弱点/抗性/等级换算）→ **P5 进"会普攻打人"**（仇恨选目标 + 敌人回合）→ **P9 进全机制**（真实技能表、技能选择 AI、召唤、精英/Boss 换招/反击/控制免疫）。
- **全机制补完**：七系击破异常、控制状态机、通用 Buff 与刷新规则、速度操纵、终结技插入、Debuff 数据化 → 集中在 **P10**，全部是"把已开口子填满"，无新架构。

| 阶段                  | 任务                                           | 状态 |
|-----------------------|------------------------------------------------|------|
| **P1 伤害流水线** ✅  | P1-1 DamageType 枚举                           | ✅   |
|                       | P1-2 Damage 挂 DamageType                      | ✅   |
|                       | P1-3 Area 乘区体系 + toValue                   | ✅   |
|                       | P1-4 CanHit.level                              | ✅   |
|                       | P1-5 Battle 装配（增伤/暴击/防区）+ 旧入口删除 | ✅   |
|                       | P1-6 抗性区接入                                | ✅   |
|                       | P1-7 DamageEvent 钩子（易伤/减伤/虚弱）        | ✅   |
|                       | P1-8 技能执行器（单→多目标分派）               | ✅   |
|                       | P1-9 附加伤害 + 真实伤害                      | ✅   |
| **P2 真实怪物数据** ✅ | P2-1 MonsterBean + Constant 加载               | ✅   |
|                       | P2-2 Enemy 弱点 + 韧性数值                     | ✅   |
|                       | P2-3 EnemyScaler 等级属性公式                  | ✅   |
|                       | P2-4 EnemyFactory                              | ✅   |
| **P3 能量系统**       | P3-0 能量机制调研（数据 + 文档双证）             | ✅   |
|                       | P3-1 能量字段 + gainEnergy + EnergyProvider     | ✅   |
|                       | P3-2 回能接入 + 大招条件                       | ✅   |
|                       | P3-3 击破回能联动                              | ✅   |
|                       | P3-4 技能回能数据化（SPBase 落库，部分完成）   | 部分 |
| **P4 韧性/击破**      | P4-1 Enemy 韧性字段                            | ✅   |
|                       | P4-2 削韧判定                                  | ✅   |
|                       | P4-3 击破伤害                                  | ✅   |
|                       | P4-4 击破状态/推条/跳回合                      | ✅   |
|                       | P4-5 DOT                                       | ✅   |
|                       | P4-6 超击破                                    | ✅   |
| **P5 仇恨 + 敌人AI**  | P5-1 Path 仇恨值                               | ✅   |
|                       | P5-2 受击概率 + 嘲讽                           | ✅   |
|                       | P5-3 EnemySkill（敌人普攻）                    | ✅   |
|                       | P5-4 TargetSelector                            | ✅   |
|                       | P5-5 敌方回合执行                              | ✅   |
| **P6 命中/治疗/护盾** | P6-1 效果命中与抵抗                            | ✅   |
|                       | P6-2 治疗乘区                                  | ✅   |
|                       | P6-3 护盾                                      | ✅   |
| **P7 轮次/胜负/关卡** | P7-1 轮次制行动值（150/100）                   | ✅   |
|                       | P7-1b 行动条修正 E1/E2/E3/E4                   | ✅   |
|                       | P7-2 额外回合                                  | ✅   |
|                       | P7-3 胜负状态机                                | ✅   |
|                       | P7-4 StageBean + 波次                          | ✅   |
|                       | P7-5 StageFactory + 难度                       | ✅   |
| **P8 角色数据化**     | P8-0 角色机制数据化（架构总纲，先读）          | ✅   |
|                       | P8-1 CharacterFactory + 角色字段补全           | ✅   |
|                       | P8-2 技能装配（真实槽位 → 真实倍率）           | ✅   |
|                       | P8-3 天赋 + 追加攻击                           | ☐   |
|                       | P8-4 战技点（SP）                              | ☐   |
|                       | P8-5 真实队伍装配（StageFactory 换真角色）     | ☐   |
|                       | P8-6 事件补齐（触发器宿主）                    | ☐   |
|                       | P8-7 触发器表 + 效果词表（角色内容数据化）     | ☐   |
|                       | P8-8 层数资源 Resource（替代能量条）           | ☐   |
| **P9 怪物全机制**     | P9-1 敌人技能数据（enemy_skills.json 自建）    | ☐   |
|                       | P9-2 EnemySkill 全效果                         | ☐   |
|                       | P9-3 敌方 AI 技能选择器                        | ☐   |
|                       | P9-4 召唤物（summon_id）                       | ☐   |
|                       | P9-5 Boss 机制（换招/反击/免疫）               | ☐   |
| **P10 机制补完**      | P10-1 七系击破异常全量                         | ☐   |
|                       | P10-2 控制异常状态机                           | ☐   |
|                       | P10-3 Buff 体系完善（属性 + 刷新规则）         | ☐   |
|                       | P10-4 速度与行动条操纵                         | ☐   |
|                       | P10-5 终结技插入                               | ☐   |
|                       | P10-6 Debuff 基础概率数据化                    | ☐   |
| **P11 收尾**          | P11-1 Main 修复 + demo 包拆分                  | ☐   |
|                       | P11-2 真实内容演示（冰锋战）                   | ☐   |
|                       | P11-3 测试总盘点 + 基准 + 零警告               | ☐   |

---

## 2. 阶段 P1：伤害流水线（最高优先，一切的地基）

**本阶段结束时的成果**：任何一条伤害都走「基础值 × 增伤 × 易伤 × 减伤 × 虚弱 × 暴击 × 防御 × 抗性」精确流水线， Buff
能拦在清算前改乘区，多段/多目标技能按 effect 自动分派。

**阶段内顺序**：`P1-1 → P1-2 → P1-3 → P1-4 → P1-5 → P1-6 → P1-7 → P1-8 → P1-9`，严格串行。

---

### P1-1 DamageType 枚举 ✅

- **目标**：区分伤害类型（普攻/战技/大招/击破/持续/真伤…），并把「不可暴击」「不吃增伤」两条规则变成类型自带的数据。
- **涉及文件**：`src/main/java/com/laosun/aluminium/enums/DamageType.java`、`test/DamageTypeTest.java`
- **落地**：
    1. 12 值：`NORMAL, SKILL, ULTRA, ADDITIONAL, BREAK, SUPER_BREAK, DOT, EXTRA, TECHNIQUE, MEMORY, ELATION, TRUE`
    2. `boolean isCrittable()`：
        - 可暴击：`NORMAL / SKILL / ULTRA / ADDITIONAL / EXTRA / TECHNIQUE / MEMORY / ELATION`
          （欢愉伤害吃双爆：HSR.md §6.4 的欢愉伤害公式里有双爆区）
        - **不可暴击**：`BREAK / SUPER_BREAK / DOT / TRUE`（击破、超击破、持续伤害、真实伤害不吃双暴）
    3. `boolean isBoostable()`：**击破 / 超击破 / 真伤 / 欢愉不吃增伤**，其余（含 DOT）都吃——P1-3 的
       `BoostArea.applies()` 直接消费这个标志，所以不再靠"记得别调 addBoost"。
       （欢愉依据：HSR.md §6.5 + GLOSSARY「欢愉伤害不受伤害提高类效果所影响」）
    4. `static DamageType fromString(String)`：按枚举上的 `name` 字符串**大小写不敏感**查找（key 就是 `MP` 表里的
       `normal / skill / super_break / …`），未知抛 `IllegalArgumentException`。
- **验收**：`DamageTypeTest`（5 个用例：12 值、crit 规则、boost 规则、大小写不敏感、未知抛异常）。
- **依赖**：无（纯新增）

---

### P1-2 Damage 挂 DamageType ✅

- **目标**：`Damage` 增加伤害类型字段，旧构造器保持兼容。
- **涉及文件**：`models/Damage.java`、`test/DamageZoneTest.java`（骨架断言并入乘区测试，不再单开文件）
- **落地**：
    1. 字段 `private final DamageType type;`（getter 由类级 `@Getter` 生成 → `getType()`）
    2. 新构造器 `Damage(CanHit attacker, CanHit defender, DamageElement element, DamageType type, double skillBaseValue)`
    3. 旧 4 参构造器委托：`this(attacker, defender, element, DamageType.NORMAL, skillBaseValue)`
    4. `element` / `type` 都自己写 `Objects.requireNonNull`——**注意**：字段上的 Lombok `@NonNull` 只对"它自己生成的
       构造器/setter"插检查，对手写构造器**不生效**（旧文档那句"Lombok 会生成空检查"是错的，已修正）
- **验收**：`DamageZoneTest` 的 `legacyConstructorDefaultsToNormalType`、`nullElementOrTypeIsRejected`。
- **依赖**：P1-1

---

### P1-3 乘区体系：Area 继承 + ArrayList 统一清算（纯代数，不碰 Battle）✅

- **目标**：`Damage` 能自己算最终值。**每个乘区是一个 `Area` 子类：吃自己需要的参数，吐一个倍率；
  `Damage` 把它们装进 `List<Area>`，`toValue()` 里统一连乘。**
- **涉及文件**：`models/Damage.java`、`Constant.java`（乘区常量块）、`enums/DamageType.java`（`isBoostable`）、
  `test/DamageZoneTest.java`
- **代码即规范**：本任务已落地，实现见 `models/Damage.java`；下面只留设计与不变量，不再复制整段代码。
- **结构**：
    ```
    Damage
     ├─ double skillBaseValue                         ← 基础值，不进任何乘区
     ├─ List<Area> damageArea = new ArrayList<>()      ← 统一容器，只装被用到过的区（没用过的区 = 1.0）
     └─ toValue():  for (Area a : damageArea) if (a.applies(type)) v *= a.getRate();

    Area (abstract) ·············· double getRate()   ← 唯一出口，final：rate() 经 min()/max() 钳制后输出倍率
     │                              double rate()      ← 子类实现：原始（未钳制）倍率
     │                              boolean applies(DamageType)
     ├─ PercentArea (abstract) ···· 内部 base 1.0 的 DoubleValue；addPercent / multiplyPercent / raw / removeModifiersFrom
     │   ├─ BoostArea       add(pct)           → 1 + Σ增伤                applies: type.isBoostable()
     │   ├─ VulnerableArea  add(pct)           → min(1 + Σ易伤, 3.5)
     │   ├─ ReductionArea   add(r) r∈[0,1]      → max(Π(1-r), 0.01)
     │   └─ WeaknessArea    add(w)             → max(1 - Σw, 0.2)
     ├─ CritArea      set(crit, critDmg)       → crit ? 1+暴伤 : 1       applies: type.isCrittable()
     ├─ DefenceArea   set(level, def, ignore)  → (200+10L) / (defEff + 200+10L)
     └─ ResistArea    set(resist, pen)         → 1 - clamp(resist - pen)
    ```
- **四条不变量**（破了就是 bug，不是风格问题）：
    1. **基础值不进乘区**：`skillBaseValue` 只是连乘的起点，永远不塞进 `DoubleValue`。
    2. **随机数不进乘区**：暴击骰子在 `Battle`（用注入的 `Random`），`CritArea` 只记「暴没暴、暴伤多少」——测试因此
       不需要给 `Damage` 播种，P5-3 / P9-3 的 AI 选招也能改切"期望暴击"。
    3. **钳制不可绕过，且边界必须写在"拥有它"的那个区里**：`getRate()` 是 `final`，用
       `Math.clamp(rate(), min(), max())` 施加（JDK 21+；`min > max` 会 fail fast）。**基类默认 `min() = -∞`、
       `max() = +∞`（不设策略）**；官方边界一律由区自己声明：易伤 cap 3.5、减伤 floor 0.01、虚弱 floor 0.2（§2.2）、
       抗性 `1 - clamp(res)` ∈ [0.1, 2.0]（§2.5）、暴击 ≥ 1。`PercentArea` 另加一条 **sanity** 下限 0（"系数非负"，
       游戏里不存在负增伤/负易伤，0 只为防"负系数把伤害翻符号"）——注释里已标明**它不是游戏规则**，别当成官方下限。
    4. **"这段不吃那个区"必须是声明的**：由 `Area.applies(DamageType)` 决定（击破/超击破/真伤不吃增伤走
       `isBoostable()`；DOT/击破/真伤不吃双暴走 `isCrittable()`），不靠调用方自律。
- **容器约定**：一个区最多一个实例（`boostArea()` 等 7 个懒创建口，未用过就不进 List）；区间是纯连乘、可交换，
  遍历顺序不影响结果；`getDamageArea()` 返回只读快照（测试/日志用，战斗循环内部直接走字段）。
- **附带产出**：`breakdown()` 逐区输出倍率（`base → 各区 → final`），给日志/前端做公式分解；
  `PercentArea.removeModifiersFrom(source, roleId)` 支持按来源撤销（P10-3 用）。
- **验收**：`DamageZoneTest`（23 个用例，base 一律 1000）：
    - 空区 → 1000（`getDamageArea().isEmpty()`）
    - 增伤加算：`addBoost(0.3).addBoost(0.2)` → 1500
    - 易伤 cap：`addVulnerable(2.0)×2` → 3500（`raw().get() == 5.0`，`getRate() == 3.5`）
    - 减伤：`addReduction(0.9)³` → 10（0.001 → floor 0.01）；入参 clamp：`addReduction(1.5).addReduction(-0.5)` → 10
    - 虚弱：`addWeakness(0.9).addWeakness(0.3)` → 200
    - 暴击：`crit(true, 1.0)` → 2000；`crit(false, 1.0)` → 1000
    - 防御：`defence(80, 1150, 0)` → `1000 × 1000/2150`；穿透 `defence(80, 1150, 0.5)` → `1000 × 1000/1575`
    - 抗性：`resist(0.2, 0.4)` → 1200；clamp `resist(1.2, 0)` → 100
    - 真伤：全区填满 + `trueDamage()` → 1000
    - 容器：同类型只建一个区；`getDamageArea()` 不可变（`clear()` 抛 `UnsupportedOperationException`）
    - 区可脱开 `Damage` 单测：`new Damage.VulnerableArea().add(2.0).add(2.0).getRate() == 3.5`
    - 顺序无关：先抗性后增伤 == 先增伤后抗性 == 1200
    - 类型适用：BREAK 段跳过增伤/暴击但保留防御；DOT 段吃增伤、不吃暴击
    - 按来源撤销：`addVulnerable(0.5, BUFF, 7)` → 1500，`removeModifiersFrom(BUFF, 7)` → 1000
- **已确认（HSR.md §2.5）**：抗性区 = `1 - 抗性`，抗性范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`，**负抗全效**——所以
  `resist(0.2, 0.4) → 1200`、`resist(-1.5, 0) → 2000` 都是正确值（早前怀疑的"负抗半效"是那个 Python 项目的自家
  简化，不采纳）。
- **后续接线注意（HSR.md §2.4）**：防御区里 `减防%` 与 `防御穿透%` 是**加算**进同一个括号
  （`def × (1 - clamp(减防 + 穿透))`）。当前 `defence(level, def, ignore)` 只暴露"穿透"一个参数，P4 接入减防 debuff
  时要把两者**合并后传入**——别各自乘一遍，否则会重复减防。
- **依赖**：P1-2

---

### P1-4 CanHit.level（等级贯穿伤害）✅

- **目标**：防御区、击破基数、敌人属性都要等级。`CanHit` 增加 level，角色把 builder 的 level 存下来。
- **涉及文件**：`models/CanHit.java`、`models/Character.java`、`test/LevelTest.java`
- **落地**：
    1. `CanHit` 加 `@Setter private int level = 80;`（默认 80，老代码不用改）；拷贝构造器里补 `this.level = other.level;`
       （`Character(CanHit other)` 走这条链，不补会静默重置成 80）
    2. `Character.Builder.build()` 末尾：`character.setLevel(level);`（builder 自己的 level 默认 1，是等级缩放用的那个值）
    3. `Enemy.fromAttributes(...)` 不用改（默认 80；真实怪物由 P2-4 传等级）
- **验收**：`LevelTest`（3 个用例）：
    - `Character.builder().cid(1409).level(90).build().getLevel() == 90`
      （builder 的 weapon/relicSuit/extraBasicPromote 都有非空默认值，所以只给 cid + level 就能 build）
    - `Character.fromAttributes("x", 100, 100, 100, 100).getLevel() == 80`、`Enemy.fromAttributes(...)` 同理
    - `setLevel(95)` 后 `getLevel() == 95`（可变，供测试与后续等级缩放用）
- **依赖**：无（独立，做完错开也行）

---

### P1-5 Battle 装配（增伤 + 暴击 + 防区）+ 旧入口删除 ✅

- **目标**：伤害结算从「简易公式」改成「装配 Damage 乘区」；旧入口删除（旧 E2 在此完成）。
- **涉及文件**：`Battle.java`、`models/DefaultSkill.java`、`models/tests/TestSkillGroup1.java`、
  `test/DamagePipelineTest.java`
- **落地**：
    1. `Battle` 注入随机源：老签名 `Battle(List<Character>, List<Enemy>)` 委托 `this(…, new Random())`；新签名
       `Battle(List<Character>, List<Enemy>, Random rng)`；加 `public Random getRng()`（P1-8 / P5-4 要用）
    2. **唯一公开结算入口**：
       ```java
       public double applyDamage(CanHit target, Damage damage) {
           if (target.isDeath()) return 0;
           double settled = assemble(damage);
           target.takeDamage(settled);          // HP 只在 takeDamage 里减
           return settled;                      // 想知道"这一击打多少" → 用返回值
       }

       /** 乘区装配 + 清算。private：外部只能经 applyDamage 进入。 */
       private double assemble(Damage damage) {
           CanHit attacker = damage.getAttacker();
           CanHit defender = damage.getDefender();
           // 1) 增伤区：元素增伤 + 全增伤（击破/超击破/真伤由 BoostArea.applies() 自动跳过）
           AttributeType elementBoost = AttributeType.getBoostByElement(damage.getElement());
           if (elementBoost != null) damage.addBoost(attacker.getAttribute(elementBoost).get());
           damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
           // 2) 暴击区：只有可暴类型才骰；全引擎唯一的随机点，用注入的 rng（可复现）
           if (damage.getType().isCrittable()) {
               double critRate = attacker.getAttribute(AttributeType.CRIT_CHANCE).get();
               boolean isCrit = critRate > 0 && rng.nextDouble() < critRate;
               damage.crit(isCrit, attacker.getAttribute(AttributeType.CRIT_ATTACK).get());
           }
           // 3) 防御区：攻击者等级 / 受击者防御 / 攻击者无视防御
           damage.defence(attacker.getLevel(),
                   defender.getAttribute(AttributeType.DEFENCE).get(),
                   attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get());
           // 4) 抗性区 → P1-6
           return Math.max(1, damage.toValue());   // 最小伤害钳制留在 Battle（P1-3 已定）
       }
       ```
    3. **为什么只有一个公开入口**（本任务的关键设计）：装配是**追加**语义（`addBoost` 往增伤区 append Modifier），
       所以对同一个 `Damage` 装配两次会把增伤/易伤/减伤/虚弱各算两份——而且是**部分**翻倍（暴击/防御/抗性是 `set`
       赋值，不受影响），症状是 1300 → 1600 这种"看着不离谱"的虚高，最难查。原先设想的
       `public calculateDamage(Damage)` + `applyDamage` 双入口，正好制造了"先看一眼数值、再让它生效"的踩雷路径。
       **改成单入口后这个错误在结构上不可能发生**，也不必给 `Damage` 加幂等标志。
       （将来真出现"不扣血只要数值"的需求——P5-3 AI 干跑、P11 日志预演——再加 `previewDamage(Damage)`，**那时**才需要
       幂等守卫。）
    4. **删除**旧 `calculateDamage(CanHit, CanHit, double, List<DoubleValue.Modifier>)` 与
       `applyDamage(CanHit, double)`（顺带删掉里面"算了 `currentHp/newHp` 又丢掉"的死代码）；`DefaultSkill` 与
       `TestSkillGroup1` 改为：
       ```java
       DamageElement element = getData().getElement();
       if (element == null) {
           return;   // 非伤害技能不构造 Damage（全局约定：不造 Damage ⇒ 无伤害）；TODO P1-8 正经分派
       }
       // TODO P1-8：伤害类型先一律 NORMAL，之后按技能槽位映射 普攻/战技/终结技
       battle.applyDamage(c, new Damage(user, c, element, DamageType.NORMAL, baseDamage));
       ```
- **验收**：`DamagePipelineTest`（9 个用例；攻击者 = `Character.fromAttributes`，默认 Lv80、无光锥遗器，所以增伤与
  暴击属性都从 0 起）：
    - 元素增伤：`FIRE_DAMAGE_BOOST=0.3`、受击者 DEFENCE=0 → 1300
    - 加算进同一区：再加 `ALL_DAMAGE_TYPE_BOOST=0.2` → 1500（增伤区是 `1+Σ`，不是各乘一遍）
    - 暴击可复现：`Random(0)` 首值 ≈ 0.7310 → `CRIT_CHANCE=0.5` 不暴（1000）、`=0.9` 暴击（2000）
    - 不可暴类型：`DamageType.BREAK` + `CRIT_CHANCE=1.0` → `1000 × 1000/2150`（不骰暴击，防御区照常生效）
    - 防御区：受击者 DEFENCE=1150 → `1000 × 1000/2150`
    - 防御穿透：`DEFENCE_IGNORE=0.5` → `1000 × 1000/1575`
    - HP 只减一次：`applyDamage` 返回值 == `HP前 − HP后`
    - 已死目标：返回 0、HP 不变
    - **入口唯一性（可执行断言）**：旧两个签名反射取不到；`assemble` 是 private；`Battle` 公开方法里接收 `Damage`
      的只有 1 个
- **依赖**：P1-3、P1-4

---

### P1-6 抗性区接入 ✅

- **目标**：`Enemy` 挂 `damageResist`，`Battle` 装配第 4 步接抗性区。
- **涉及文件**：`models/Enemy.java`、`Battle.java`、`test/ResistZoneTest.java`
- **落地**：
    1. `Enemy` 加 `private Map<DamageElement, Double> damageResist = Map.of();`（类上已有 `@Getter @Setter`，字段即可；
       表里没有的元素视为 0 抗性）。**P2-2 直接从这个字段灌 `monster_config.json` 的 `damage_resistance`**，结构不变。
    2. `Battle.assemble` 第 4 步：
       ```java
       // 4) 抗性区：受击者抗性 - 攻击者穿透，再 clamp（HSR.md §2.5，负抗全效）
       //    注：弱点击破不改变抗性（§2.5；P4 复核）
       double rawResist = defender instanceof Enemy enemy
               ? enemy.getDamageResist().getOrDefault(damage.getElement(), 0.0)
               : 0.0;
       damage.resist(rawResist, attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
       ```
- **验收**：`ResistZoneTest`（5 个用例，受击者 DEFENCE=0 以隔离抗性区）：
    - ICE 抗 0.2 + 穿透 0.4 → 1200（抗性 -0.2）
    - ICE 抗 0.2 + 穿透 0.5 → 1300（**负抗全效**，不是半效）
    - FIRE 抗 1.2 + 穿透 0 → 100（clamp 0.9）
    - 表里没有的元素（PHYSICAL）→ 1000
    - 受击者是 `Character`（没有抗性表）→ 1000
- **依赖**：P1-5

---

### P1-7 DamageEvent 钩子（Buff / 天赋拦截乘区）✅

- **目标**：伤害清算前允许 Buff（以及角色天赋、Boss 机制）改乘区——易伤 / 减伤 / 虚弱都从这里进。
- **涉及文件**：新建 `models/event/DamageEvent.java`、`models/CanHit.java`（实现 + 转发）、
  `models/BuffManager.java`（`onDamage` 转发）、新建 `models/buffs/VulnerabilityBuff.java`、
  新建 `models/buffs/ReductionBuff.java`、新建 `test/DamageHookTest.java`
- **落地**：
    1. 事件接口与既有事件同族（`models/event/`，`default` 空实现）：
       ```java
       public interface DamageEvent {
           default void onDamage(Battle battle, Damage damage) {
           }
       }
       ```
       于是三件事件并排：`BattleEvent.onBattleStart` / `MoveEvent.beforeMove|afterMove` / `DamageEvent.onDamage`。
    2. `CanHit implements BattleEvent, MoveEvent, DamageEvent`，默认把事件转发给 BuffManager：
       ```java
       @Override
       public void onDamage(Battle battle, Damage damage) {
           buffManager.onDamage(battle, damage);   // 子类重写时必须调 super，否则自己的 buff 失效
       }
       ```
       这样"角色天赋 / Boss 机制直接改承伤"有了落点，不必伪装成 Buff。
    3. `BuffManager.onDamage` 转发给关心的 Buff（遍历留在 manager 内部，**因此不需要 `getBuffs()`**，
       也不存在外部改列表导致 `ConcurrentModificationException` 的口子）：
       ```java
       public void onDamage(Battle battle, Damage damage) {
           for (AbstractBuff buff : buffs) {
               if (buff instanceof DamageEvent event) {
                   event.onDamage(battle, damage);
               }
           }
       }
       ```
    4. `Battle.assemble` 第 5 步（抗性区之后、`toValue()` 之前）**双方都发**：
       ```java
       // HSR.md §2.2：虚弱=攻击方负面、易伤=受击方负面、减伤=受击方增益
       attacker.onDamage(this, damage);
       defender.onDamage(this, damage);
       ```
    5. `VulnerabilityBuff` / `ReductionBuff extends AbstractBuff implements DamageEvent`：
       构造 `(duration, ratio)`，`super(duration, false)`（后置 buff，随 `afterMove` 递减），
       `onDamage` 里 `damage.addVulnerable(ratio, DEBUFF, id)` / `damage.addReduction(ratio, BUFF, id)`。
- **关键设计：这类 buff 与 `BoostDamageBuff` 不是一类**
    - `BoostDamageBuff` 是**改属性**：`applyEffect` 往属性上挂 Modifier，`removeBuff` 摘掉，有持久状态。
    - 易伤 / 减伤 / 虚弱**没有持久状态**：只在每段伤害结算时注入到那一段的 `Damage` 乘区上。所以
      `applyEffect` / `removeBuff` **留空**，只有 `tickEffect` 减时长——照抄属性 buff 会在 `applyEffect`
      里改属性，导致"易伤对所有人生效 + 每次结算叠加"。
    - 来源标记按 §2.2：易伤 `DEBUFF`、减伤 `BUFF`、虚弱 `DEBUFF`；配合 `AbstractBuff.id` 唯一，
      可按来源撤销（P10-3 到期/驱散）。
- **验收**：`DamageHookTest`（7 个用例；受击者 DEFENCE=0、攻击者无增伤/暴击属性，base 1000）：
    - 易伤 50% → 1500
    - `beforeMove()` 后仍 1500（后置 buff 不该在 `beforeMove` tick）
    - 2 次 `afterMove()` 后（duration 2 走完）→ 1000（修正被摘掉）
    - 减伤 30% → 700
    - 同类替换：连挂 50% / 90% → 1900（不是叠加成 2250）
    - 钩子对 `DamageType.BREAK` 也生效 → 1500（击破跳过增伤/双暴，但吃易伤，P4 复用）
    - **攻击方侧**虚弱 40%（测试内嵌 buff）→ 600
- **依赖**：P1-5、P1-6

---

### P1-8 技能执行器（single → aoe → blast → bounce）✅

- **目标**：伤害技能不再只打第一个目标；按 `SkillEffectType` 自动分派多段/多目标。
- **涉及文件**：新建 `models/SkillExecutor.java`、`models/DefaultSkill.java`（一行委托）、
  `models/tests/TestSkillGroup1.java`（一行委托）、新建 `test/SkillExecutorTest.java`
- **落地**：唯一静态入口 `SkillExecutor.execute(battle, skill, user, targets)`。
    1. **调用方只给"主目标"**（`targets.getFirst()`）——"打几个"是技能属性，不是调用方的选择：
       | effect | 受击集合 |
       |---|---|
       | `SINGLE_ATTACK` / `MAZE_ATTACK` | 主目标 |
       | `AOE_ATTACK` | `battle.enemies` 里全部存活者 |
       | `BLAST` | 主目标 + 战场序列（`battle.enemies` 顺序）左右相邻各 1 |
       | `BOUNCE` | N 段，每段从存活敌人随机（N = `params.get(1)`，缺省 1） |
       非伤害 effect（HEAL / BUFF / CONTROL / SUMMON / PASSIVE）直接 return，留给 P6/P7/P9 分派。
    2. **判定顺序很关键**（三条都是真实数据逼出来的）：
        - **先判 `effect.isDamaging()`，再取 params**：护盾技的 param 第 1 项不是伤害倍率
          （cid 1001 槽位 2 是 `Defence`，`[0.38, 3, …]` 是护盾系数 / 持续回合）；
        - **空参数真实存在**（cid 1001 槽位 6 `MazeAttack` 的 `param_list = [[]]`）→ 判空跳过，不能抛；
        - `isDamaging()` 但 `element == null` → **抛 `IllegalStateException`**：这是数据错误，fail fast
          好过静默跳过让这一击凭空消失。
    3. 每段独立走 `battle.applyDamage(...)`：**每段独立判定暴击、独立结算**（全局约定）。
       伤害类型暂时一律 `NORMAL`（4 参构造器），P8-2 接真实槽位后再按 普攻/战技/终结技 映射。
- **验收**：`SkillExecutorTest`（10 个用例；攻击者 ATK=100、受击者 DEFENCE=0、无增伤/暴击 ⇒ 一击 = ATK × 倍率）：
    - 单目标：真实数据 `DefaultSkill(1001, 1, 1)`（倍率 0.5）→ 主目标 50、副目标 0
    - AOE：真实数据 `DefaultSkill(1001, 3, 1)`（倍率 0.9）→ 3 敌**各** 90
    - 非伤害技能：`DefaultSkill(1001, 2, 1)`（护盾）→ 0 伤害（0.38 没被当成倍率）
    - 空参数：`DefaultSkill(1001, 6, 1)` → 0 伤害且不抛
    - BLAST：主目标取中间 → 3 敌全中；主目标取最左 → 只中左侧两位（证"相邻按站位"而不是"按传参顺序"）
    - BOUNCE：`params [0.5, 3]` → 3 段共 150；`params [0.5]` → 1 段 50
    - 主目标已死：AOE 照打存活者，尸体不再受伤
    - 委托：`TestSkillGroup1.TestSkill1` 与 `DefaultSkill` 行为一致
- **遗留**：`BLAST` 的"相邻"用 `battle.enemies` 顺序当站位，P7-4 波次 / 召唤物进场后要复核；
  `aliveEnemies()` 目前只看 `battle.enemies`，敌方召唤物（P9-4）进场后要一起算。
- **依赖**：P1-5

---

### P1-9 附加伤害 + 真实伤害 ✅

- **目标**：两套特殊伤害类型——`ADDITIONAL`（附加伤害）与 `TRUE`（真实伤害），并给"角色特定效果生成伤害"
  一个落点（`AttackEvent`）。
- **涉及文件**：新建 `models/event/AttackEvent.java`、`models/CanHit.java`、`models/BuffManager.java`、
  `models/SkillExecutor.java`、`models/Damage.java`、`Battle.java`、新建 `test/ExtraTrueDamageTest.java`
- **两份角色文档定下来的语义**（`E:\code\blog\hsr\1309_知更鸟.md` / `1403_缇宝.md`）：
    | 来源 | 触发 | base | 元素 | 双暴 |
    |---|---|---|---|---|
    | 1309 知更鸟 终结技【协奏】 | 我方**每次施放攻击后**（1 次） | 自身攻击力 × 120% | 固定物理 | **固定 100% / 150%**（星魂6 再 +450%） |
    | 1403 缇宝 战技结界 | 我方攻击后，**每有 1 名目标受到攻击**（AOE 打 3 → 3 次） | 缇宝生命上限 × 12% | 固定量子 | 面板 |
    | 1403 缇宝 E1 | 同上 | **本次攻击总伤害值 × 24%** | 量子 | — |
    官方定义：**「附加伤害：使受击者额外受到 1 次伤害，本次伤害不视为造成了 1 次攻击」**。
- **落地**：
    1. **`AttackEvent`（攻击级事件，第四个事件家族成员）**：
       ```java
       default void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) { }
       ```
       - 由 `SkillExecutor` 在一次技能全部段结算完**广播给全体我方**（`battle.characters`）——知更鸟的【协奏】、
         缇宝的结界挂在他们自己身上，主C 攻击时他们才出手，所以只遍历"攻击者的 buff"是不够的；
       - `hitTargets` = **实际命中过**的目标（含当场死亡的，用于缇宝的"每有 1 名目标受到攻击"计数）；
         `mainTarget` = 调用方选的主目标（AOE 时它不是命中顺序里的第一个）；
       - `totalDamage` = 各段结算值之和（`TODO data (D2)`：溢出是否计入尚未定论）。
    2. **触发不递归**：附加伤害 / 真伤段走 `Battle.applyDamage`，**不经过 `SkillExecutor`** → 不会再触发
       `AttackEvent`；这也正对上官方那句"不视为造成了 1 次攻击"。
    3. **附加伤害走完整乘区**（base 是面板值：攻击力 / 生命上限 × 倍率），入口：
       `Battle.applyAdditionalDamage(attacker, target, element, base)`，内部 `notCountsAsAttack()`。
    4. **真伤跳过全部乘区**（base 通常是"本次攻击总伤害 × %"的衍生值），入口：
       `Battle.applyTrueDamage(attacker, target, element, base)`，内部 `trueDamage().notCountsAsAttack()`。
    5. **`Damage.fixedCrit(isCrit, critDamage)`**：由效果**指定**本段双暴（知更鸟固定 100%/150%）；
       `Battle.assemble` 在此标志下**不再按面板骰、也不覆盖**——补上了"面板双暴会覆盖固定值"的缺口。
    6. **D1 = 主目标**：知更鸟附加伤害的目标取 `mainTarget`，主目标已死则本次不产生伤害（文档没写目标，按
       忠于"1 次 + 打被攻击的那个"处理；`TODO data`：P8-3 做真实知更鸟时按游戏实测校准）。
    7. **附加伤害不鞭尸（规则固化，P8-3 照抄）**：每次**选取目标**都要求"**当前存活**"，没有存活目标时该次不产生
       伤害、**不转火**。于是"主伤害已击杀"时：
        - 知更鸟式（1 次、打主目标）→ 本次直接不产生；
        - 缇宝式（按被击目标数计次）→ **计数不变**（"每有 1 名目标**受到攻击**"看的是"被攻击过"），但每次选取
          都避开尸体；被击目标**全灭** → 剩余次数全部作废（也不转火到未被攻击的敌人）。
       引擎只负责"打不到尸体"（`applyDamage` 对死/无敌目标返回 0）与"选择时看得见谁死了"
       （`hitTargets` 含当场死亡的、`isDeath()` 可查）；这条规则落在**角色效果**里。
- **验收**：`ExtraTrueDamageTest`（9 个用例；辅助角色 ATK 100 / 生命上限 1000、无增伤与暴击面板）：
    - 知更鸟式：AOE（每敌 90）→ 附加伤害**只触发 1 次**、只落在**主目标**（120 × 固定暴击 2.5 = 300）
    - **固定双暴不被面板覆盖**：攻击者面板暴击率 0，附加伤害仍是 300（不是 120）
    - **不递归**：附加伤害段不再触发 `AttackEvent`（triggerCount 仍是 1）
    - 缇宝式：AOE 打 3 敌 → 3 次 ×（12% × 1000 = 120），每次都落在"被击目标中当前 HP 最高"者
    - 缇宝 E1 式：真伤 = 本次攻击总伤害 × 24%，**不被防御 10000 / 抗性 0.9 削减**
    - 语义差：同样 base 1000，附加伤害吃防御区（×1000/2150），真伤原样 1000
    - **击杀即停（规则 7）**：主目标被主伤害当场击杀 → 触发计数为 1 但附加伤害 0（总伤害仍只有 50）
    - **击杀即停 · 部分**：3 敌中当前 HP 最高的那个被主伤害击杀 → 3 次附加伤害全部落到**存活**的次高者（尸体不受伤）
    - **击杀即停 · 全灭**：被击目标全灭 → 附加伤害一次都不产生（不转火到未被攻击的目标）
- **遗留**：`TODO data (D2)` 溢出是否计入 `totalDamage`；`TODO P8-3` 真实角色实现（知更鸟【协奏】/缇宝结界
  + E1/E2 完整链路）。**另有一处概念区分**：HSR.md §2 的「**真实伤害乘区**」`1 + 真伤加成%` 是给"真伤段"额外乘的
  **独立乘区**（某些光锥/遗器/星魂给"造成的真实伤害提高 X%"），与 `DamageType.TRUE`（这一段**跳过**全部乘区）
  是两回事——**目前未实现**（`AttributeType` 里没有该字段、`Damage` 也没有对应 `Area`），等 P8-3 / P10-3 有真实
  效果引用时再加。
- **依赖**：P1-7（`AttackEvent` 与 `DamageEvent` 同族）、P1-5

---

## 3. 阶段 P2：真实怪物数据（只做数值）

**本阶段结束时的成果**：`EnemyFactory.create(1002011, 90, 1)` 一条命令造出面板正确的冰锋（HP≈16498.296、
防御≈1100、速度 132、弱火/雷、冰抗 0.2、韧性 60），并且抗性直接进入伤害流水线。

**本阶段只做数值**（用户明确要求）：弱点与韧性只是把数据搬到 `Enemy` 上，**削韧与击破机制留 P4**；
免控（`debuff_resistance`）P6-1、召唤 P9-4、多阶段血量 P9-5。等级与等级组来自**关卡**，所以由调用方传参
（关卡驱动留 P7-4）。数据陷阱先读 **§0.6**。

---

### P2-1 MonsterBean + Constant 加载 ✅

- **目标**：把 `monster_template_config.json` / `monster_config.json` / `hard_level_group.json` 装进 `Constant`。
- **涉及文件**：新建 `beans/MonsterTemplate.java`、`beans/MonsterConfig.java`、修 `beans/HardLevelGroup.java`、
  `Constant.java`、`data/monster_attack_modify_ratio.json`（补丁数据）、新建 `test/MonsterDataTest.java`
- **落地**：
    1. `Constant` 加 `MONSTER_TEMPLATES` / `MONSTER_CONFIGS` / `HARD_LEVEL_GROUPS`
    2. `HardLevelGroup` 必须用 `@SerializedName`（JSON 键是 `attack` 而不是 `attackRatio`，否则 Gson 静默读成 0）；
       `effect_hit_rate` / `effect_resistance` 是**加值**，字段名因此去掉 `Ratio` 后缀
    3. 攻击修正列在数据里整个缺失 → 从 tbgd 生成补丁文件，装载时由 `Constant.normalizeMonsterConfigs` 合并
       （其余系数缺失按 1.0；`stance_weak` 缺失 → 空集合、`damage_resistance` 缺失 → 空表）
    4. 与 tbgd 的版本一致性已核实：2649 个 id 全部对得上，`HPModifyRatio`/`SpeedModifyRatio` 不一致 **0** 条、
       `DefenceModifyRatio` **1** 条（800205073）
- **验收**：`MonsterDataTest`（7 个用例）：冰锋模板/实例字段、五个系数装载后无 null、
  **血量字段陷阱**（802501003 = 1.979167、100201101 = 0.266667）、攻击修正来自补丁（100201506 = 0.33333302）、
  组1·Lv90 与组3·Lv120 的七个系数
- **依赖**：无

---

### P2-2 Enemy 挂弱点 + 韧性数值 ✅

- **目标**：`Enemy` 带上击破所需的**数据**——弱点集合与韧性数值（机制留 P4）。
- **涉及文件**：`models/Enemy.java`（验收并入 `EnemyFactoryTest`，不单开"测 setter"的空用例）
- **落地**：`Enemy` 加 `stanceWeak`（默认空集合）、`stance` / `maxStance`、`stanceCount`、`stanceType`，
  以及判定入口 `public boolean isWeakTo(DamageElement)`——它是 P4-2「弱点削韧」的**唯一调用点**，
  别再直接摸集合（否则以后改判定规则会漏调用点）。抗性字段 `damageResist` P1-6 已有。
- **验收**：`EnemyFactoryTest.weaknessAndToughnessAreCarriedOver`：弱火/雷、`isWeakTo(FIRE)` 真 /
  `isWeakTo(ICE)` 假 / `isWeakTo(null)` 假、韧性 60/60、条数 1、韧性属性冰
- **依赖**：P2-1（数据来源）、P1-6（抗性字段）

---

### P2-3 EnemyScaler 等级属性公式 ✅

- **目标**：`敌人属性 = 模板基础值 × 等级组系数 × 实例自身调整 × 精英组系数`。
- **涉及文件**：新建 `models/EnemyStats.java`、`models/EnemyScaler.java`、修 `beans/EliteGroup.java`、
  新建 `test/EnemyScalerTest.java`
- **落地**：
    1. `EnemyStats` = **纯数值**结果（hp/attack/defence/speed/stance/effectHitRate/effectResistance），
       不依赖 `Enemy`，便于单独对拍公式
    2. `EnemyScaler.scale(template, config, group[, elite])`：血量用 `config.hpRatio()`（= `health_modify_ratio`，
       **不是**幽灵字段 `hp_modify_ratio`）；**效果抵抗是加值**（模板 + 等级组），不是相乘
    3. 精英组系数用现成的 `EliteGroup` record 作参数（`NO_ELITE_BONUS` 为缺省）——「从 `EliteGroup` 还是
       `InfiniteEliteGroup` 取、怎么随关卡走」留给 P7-4 / P9
- **验收**：`EnemyScalerTest`（4 个用例）：
    - 冰锋 × 组1·Lv90：HP `69.75 × 236.53471 = 16498.296`、攻击 `18 × 36.821384 = 662.784912`、
      防御 `210 × 5.238095 = 1099.99995`（≈ `200 + 10×90`）、速度 132、韧性 60
    - 效果抵抗加值：`0.2 + 0.1 = 0.3`（相乘会得到 0.02）
    - 缺省重载 == `NO_ELITE_BONUS`
    - **会话对拍值**：绝境碎星王虫（802501003 × 组3·Lv120 × `EliteGroup(6.2, 1.1, 1, 1, 1)`）
      → HP `2232 × 1938.7634 × 1.979167 × 6.2 ≈ 53,099,832` ✅（与游戏实测吻合；不带精英组 ≈ 8,564,489）
- **依赖**：P2-1

---

### P2-4 EnemyFactory ✅

- **目标**：`EnemyFactory.create(monsterId, level, hardLevelGroup)` 一条命令造出面板正确的敌人。
- **涉及文件**：新建 `models/EnemyFactory.java`、新建 `test/EnemyFactoryTest.java`
- **落地**：查三段数据（实例 → 模板 → 等级组）→ `EnemyScaler.scale` → `AttributeBuilder` 灌
  HEALTH / ATTACK / DEFENCE / SPEED / EFFECT_RESISTANCE → `new Enemy(name, attributes)`，再设
  `level`（P1-4，进防御区）、`damageResist`（P1-6，抗性区直接生效）、`stanceWeak`（P2-2 弱点）、
  `stance`/`maxStance`/`stanceCount`/`stanceType`。实例 / 模板 / 等级组等级缺任一 → `IllegalArgumentException`（fail fast）。
- **验收**：`EnemyFactoryTest`（5 个用例）：
    - `create(1002011, 90, 1)`：HP 16498.296（初始 HP = 上限）、防御 ≈1100、攻击 662.784912、速度 132、等级 90
    - 弱点/韧性三态断言（见 P2-2）
    - **抗性进流水线**：用冰伤打它 → 结算 = `base × 1000/(def+1000) × 0.8`（防御区 × 冰抗 0.2）
    - 补丁攻击修正进面板：100201506 → 攻击 = 模板 × 组1·Lv90 × 0.33333302
    - 未知实例 / 未知等级组等级 → 抛异常
- **依赖**：P2-1、P2-2、P2-3

---

## 4. 阶段 P3：能量系统（零依赖，可提前插队做）

**本阶段结束时的成果**：普攻/战技/终结技/受击/击杀都能回能，满能量才能放大招。

---

### P3-0 能量机制调研（数据 + 文档双证，开工前必读）✅

**三个数据源（优先级从高到低）**

| 用途 | 来源 | 说明 |
|---|---|---|
| 能量上限 | `src/main/resources/data/character_data.json` 的 `max_energy` | 93 个角色；`null` 只有 1407 遐蝶 |
| 技能基础回能 | tbgd `ExcelOutput/AvatarSkillConfig.json` 的 `SPBase` | **本项目 `skills.json` 里没有这个字段**，要用得先补数据（见 P3-4） |
| 机制文案 | `E:\code\blog\hsr\<id>_<名>.md`（88 篇角色文档）+ tbgd `TextMap/TextMapCHS.json` 技能描述 | 星魂/行迹文案在配置里是**命名键**（`AvatarRankDesc_120201`），TextMap 只有哈希键 → 星魂/行迹只能看 blog 文档 |

**数据口径（实测，别再猜）**

1. `AvatarConfig.SPNeed` = 能量上限，与 `character_data.max_energy` 93/93 完全一致（可互相校验）。
2. `AvatarSkillConfig.SPBase` = 该技能基础回能。常规档：**普攻 20 / 战技 30 / 终结技 5**。
   **终结技一律 5**（饮月 3 段、米沙多段、银枝弹射 6 次，SPBase 都是 5）→ 终结技不做段数乘算。
3. **弹射/多段技能的 SPBase 是"每段值"**：艾丝妲/桑博/那刻夏/同谐开拓者（1+4 段）SPBase=6 → 6×5=30；
   瓦尔特（1+2 段）SPBase=10 → 10×3=30。**总量仍是常规 30**，不要误判成"战技只回 6"。
4. 每段是否回能由能力配置 `Config/ConfigAbility/Avatar/Avatar_*_Ability.json` 的 `SPHitRatio` 控制
   （默认 1，可以给分数）。实测：饮月 `{1, 0.2, 0.14, 0.15}`、大黑塔 `{1, 0.3, 0.4}`、艾丝妲 `{1}`。
   → **引擎不能对所有多段技能做"段数 × SPBase"**，段数乘算只对"每段 SPHitRatio=1"的弹射类成立。
5. 反例文案：1224 三月七（巡猎）强化普攻明写「恢复的能量不随段数提高而提高」（天赋 122408）。
6. 受击基准 10：文档没有直接数值，由 `1105 娜塔莎 星魂4「受到攻击后额外恢复5点」`、
   `1221 云璃「受到攻击后额外恢复15点」` 反推存在基准值；击杀/击破基准（暂定 5）只在文档里以"额外"形式出现，
   ExcelOutput 内**没有**这些常量表，等 P9 数据校准（`EnergyBarConfig.json` 是空对象）。
7. 真离档的技能（不是弹射折算，是真比其他角色少）：1212 镜流 战技 20、1402 阿格莱雅 战技 20、
   1502 爻光 普攻 30、饮月强化普攻 20/30/35/40（分技能固定）。
8. 完全不回能的技能（SPBase 空）：1201 青雀（战技）、1205 刃（战技，文案「该战技无法恢复能量」）、
   1213 饮月（战技，牌面「不视为使用战技」）、1310 流萤（战技，改为固定恢复 60% 能量上限）、
   1315 波提欧（战技，文案同刃）、1501 火花（战技，牌面「不视为使用战技」）。
9. 追加攻击基础回能（数据，0=不写）：景元 0、砂金 1、雪衣/大丽花 2、
   黑塔/布洛妮娅/克拉拉/三月七(巡猎)/真理医生/缇宝/赛飞儿/不死途 5、
   三月七/姬子/卡芙卡/刃/彦卿/云璃/貊泽/翡翠/阿格莱雅 10、大黑塔 30。

**A. 没有常规能量条（替代资源，优先级最高）**

| id | 角色 | 替代资源 | 获取方式（文档实测） |
|---|---|---|---|
| 1407 | 遐蝶 | 【新蕊】 | `max_energy=null`；上限 = 5.3125×队伍最高等级²（≤2000 时钳到 2000）；我方每损失 1 点生命 +1（死龙在场时不涨）；治疗量 100% 转化（每目标累计 ≤上限 12%）；秘技 / 星魂2 = 上限 30% |
| 1308 | 黄泉 | 【残梦】上限 9 | 任意单位施放技能期间使敌方陷入负面 → +1（每次技能最多 1）；战技 +1；行迹开场 +5；星魂2 回合开始 +1；溢出转【四相断我】 |
| 1220 | 飞霄 | 【飞黄】上限 12，6 点开大 | 技能 SPBase 全空；我方每施放 2 次攻击 +1；秘技 +1；行迹开场 +3；星魂2 追加攻击 +1（每回合 ≤6） |
| 1408 | 白厄 | 【火种】上限 12（可溢出 3） | 战技 +2；成为任意技能目标 +1；行迹开场 +1、变身结束 +3；星魂6 开场 +6 且取消溢出上限 |
| 1415 | 昔涟 | 【追忆】上限 24（12 点开大） | 普攻 +1、强化普攻/战技 +3；队友消耗【未来】行动 +1；行迹开场 +2/+3/+6；星魂2 +12；另可为阿格莱雅/海瑟音/风堇回 70/60/24 能量 |
| 1506 | 银狼LV.999 | 【隐藏分】60 激活，可溢出 240 | 四个技能 SPBase 全空 |

**B. 独立"充能/层数"资源（不是能量条，但会换成能量）**

| id | 角色 | 资源 → 能量 |
|---|---|---|
| 1003 | 姬子 | 【充能】上限 3（击破弱点 +1、开场 +1）→ 满 3 发动追加攻击 |
| 1205 | 刃 | 受伤/耗血 +1【充能】（≤5 层，每次受击最多 1 层）→ 满层追加攻击；星魂6 上限降为 4 |
| 1223 | 貊泽 | 战技 +9【充能】→ 每消耗 3 点发动 1 次追加攻击 |
| 1224 | 三月七(巡猎) | 【充能】上限 10（普攻 +1、师父攻击/终结技 +1、星魂2 +1）→ 满 7 立即行动、强化普攻 |
| 1314 | 翡翠 | 每命中 1 敌 +1 充能 → 满 8 发动追加攻击 |
| 1317 | 乱破 | 击破 +1【充能】（≤10）→ 强化普攻第 3 段消耗 |
| 1321 | 大丽花 | 独立资源不涉及 |
| 1404 | 万敌 | 每损失 1% 生命 +1【充能】（≤200）→ 100 进【血仇】、150 额外回合 |
| 1412 | 刻律德菈 | 【充能】上限 8（战技 +1/终结技 +2/军功持有者普攻战技 +1）→ 满 6 升【爵位】；能量另靠军功者普攻/战技 +5 |
| 1413 | 长夜月 | 【忆质】/【至暗之谜】充能，与能量双轨 |
| 1504 | 不死途 | 【充能】初始 2 上限 3（终结技 +3、秘技 +1）→ 每次消耗 1 点发动追加攻击 |
| 1507 | 千冶·刃 | 结界期间我方每次攻击 +1 充能 → 满 9 且生命>1 时消耗 9 换 **25 能量** |
| 1304 | 砂金 | 【盲注】（受击 +1~+2）→ 满 7 发动追加攻击（不换能量） |
| 1308/1220/1408/1415/1506 | 见 A 表 | 直接替代能量条 |
| 8007/8008 | 开拓者·记忆 | 我方全体每累计恢复 10 点能量 → 迷迷 +1% 充能 |

**C. 额外回能触发源（按钩子分类，P3-2 的挂点清单）**

| 触发 | 角色（数值） |
|---|---|
| 战斗开始 | 瓦尔特+30、银狼+20、希露瓦+15、景元+15、饮月+15、藿藿+30、椒丘+15、加拉赫+20(魂1)、貊泽+20(魂1)、大丽花+35、缇宝+30、长夜月+70、三月七(巡猎)+30(秘技)、白厄(队友)+25(秘技)、开拓者毁灭+15、黄泉+5(残梦)、昔涟+2/+3/+6(魂2+12)、星魂类同下 |
| 波次开始 | 知更鸟(秘技领域)+5 |
| 回合开始 | 银狼+5、阮·梅+5、三月七(巡猎)+5(魂4)、星期日+8(魂4)、开拓者存护+5(持盾)、姬子·启行+5(条件)、那刻夏+30(无【质性揭露】时)、黄泉+1(魂2) |
| 受击 | 云璃+15(天赋额外)、娜塔莎+5(魂4)、符玄+5(魂4，队友受击)、玲可+2(魂，持【求生反应】者受击) |
| 击杀 | 姬子+5/每消灭 1 敌(终结技)、卡芙卡+5(行迹，触电目标被消灭)、佩拉+5(魂1)、希儿+15(魂4)、黑天鹅+8(魂4，敌方回合开始或被消灭)、开拓者毁灭+10(魂1，终结技击杀) |
| 击破 | 同谐开拓者+10(天赋)、乱破+10(行迹)/+20(魂1 退出结印)、忘归人+3(魂2) |
| 队友攻击 / 命中目标数 | 银枝 每命中 1 目标 +3、大黑塔 每命中 1 目标 +3(≤5 目标)、缇宝 每命中 1 目标 +1.5、知更鸟 队友攻击 +2(魂2 +3)、驭空 队友行动 +2、丹恒·腾荒 【同袍】攻击 +6、不死途 【饲饵】被队友攻击 +8、刻律德菈 军功持有者普攻/战技 +5、千冶·刃 我方攻击 +1 充能 |
| 消耗战技点 | 米沙 每消耗 1 点 +2、寒鸦 承负恢复触发 +2、花火 队友消耗战技点 +1 |
| 治疗/生命 | 藿藿 每次治疗 +1、遐蝶 损血→【新蕊】、流萤 战技耗 40% 生命上限换 60% 能量上限、万敌 损血→充能 |
| DOT 跳伤 | 卡芙卡+2(魂4 触电)、桂乃芬+2(魂4 灼烧)、虎克+5(攻击灼烧目标)、佩拉+10(攻击负面目标) |
| 忆灵/召唤物 | 风堇 被召唤 +15(首次额外 +30)、知更鸟·晴歌 被召唤 +20、长夜月 我方忆灵施放技能 +5、开拓者·记忆 忆灵行动 +8(魂2)、托帕 账账攻击 +10/魂2 +5/秘技 +60、景元 神君每段 +2(魂4)、真理医生 天赋追加攻击 +15(魂4)、灵砂 普攻 +10(行迹) |
| 能量恢复效率 | 艾丝妲 +15%(魂4 条件)、彦卿 +10%(魂2 条件)、同谐开拓者 +25%(魂2，3 回合) |
| 为队友充能 | 停云 终结技单体 +50(魂6 +60)、藿藿 队友各 20% 上限、星期日 20% 上限(下限 40)、知更鸟·晴歌 20% 上限、白厄秘技队友 +25、姬子·启行 助战技 +4、白露 +8(魂1)、刻律德菈 +2(魂1)、昔涟 给阿格莱雅/海瑟音/风堇 70/60/24 |

**D. 能量上限/门槛离档（P3 不能写死 100）**

240：云璃（耗 120）/流萤/长夜月；350：阿格莱雅；480：绯英；220：大黑塔；180：银枝（双档 90/180）/爻光；
160：知更鸟/万敌/千冶·刃/开拓者记忆与欢愉；150：姬子·启行/不死途；135：符玄/丹恒·腾荒；115：波提欧；
12：飞霄（耗 6）/白厄；9：黄泉；24：昔涟（耗 12）；60：银狼LV.999；无：遐蝶。
另有溢出存储：千冶·刃（行迹【百炼骨】最多存 80 点溢出能量）、银狼LV.999（+240）、白厄【火种】（+3）。

**E. 文档未提及能量机制（判"未知"，别当常规）**

1002 丹恒、1008 阿兰、1013 黑塔、1101 布洛妮娅、1107 克拉拉、1214 雪衣、1410 海瑟音。

**F. 明确只有常规档的角色**（普攻 20 / 战技 30 / 终结技 5 / 受击 10，无额外条目）

1203 罗刹、1206 素裳、1304 砂金、1314 翡翠、1404 万敌、1406 赛飞儿、1501 火花、1513 砂金·戏浪。

**G. 对 P3 设计的影响**

1. `maxEnergy` 必须来自 `character_data.json`（缺失 = 0，即"无能量条"，别默认 100）。
2. 回能入口只有 `EnergyProvider`：**机制挂在 provider 上，不写在 Battle 里**。
   常规角色用 `StandardEnergyProvider`（普攻 20/战技 30/终结技 5/受击 10/击杀 5/击破 5），
   特殊角色各自实现 provider（P8-3 才落地具体角色，P3 只给接口 + 标准实现 + 测试替身）。
3. 触发源清单（**只是调研结论，不落成接口**）：P3 只落 5 个钩子
   —— `onSkillCast(user, skill, hitTargets)` / `onUltCast` / `onTakingHit` / `onKill` / `onBreak`；
   开场 / 波次 / 回合开始 / 队友攻击 / 战技点消耗 / 治疗 / 损血 / DOT 跳伤 / 忆灵召唤 / 回能效率
   这些来源等真做角色（P8-3）时按 C 表逐个补，别提前铺。
4. **技能回能取数据 SPBase 而不是写死 20/30/5**（P3-4 补 `skills.json` 的 `sp_base`；
   弹射类还要 `sp_hit_ratio_sum`，见口径 3/4）。写死的常量只做"没有数据时的兜底"。
5. 结算顺序固定三点：**终结技先清零再回自身 5**（由 provider 决定）；
   **击杀回能记给 `damage.getAttacker()`**；**受击方是 `target`**（受击是被动，不看"是否算攻击"以外的条件）。

---

### P3-1 能量字段 + gainEnergy ✅

- **目标**：`CanHit` 有能量字段与唯一入账口 `gainEnergy`；**回能规则全部由 `EnergyProvider` 提供**，
  常规角色 = `StandardEnergyProvider`，特殊角色各自实现（P8-3 落地），P3 只给接口 + 标准实现 + 测试替身。
- **涉及文件**：`models/CanHit.java`、`Constant.java`、新建 `models/energy/EnergyGain.java`、
  `models/energy/EnergyProvider.java`、`models/energy/StandardEnergyProvider.java`、新建 `test/EnergyTest.java`
- **怎么做**：
    1. 新建 `EnergyGain`（record：一次回能的语义，区分是否吃回能效率）：
       ```java
       public record EnergyGain(double amount, boolean affectedByEfficiency) {
           public static EnergyGain normal(double amount) { return new EnergyGain(amount, true); }
           public static EnergyGain fixed(double amount)  { return new EnergyGain(amount, false); } // 如流萤 60% 上限、按上限百分比回能
       }
       ```
    2. 新建 `EnergyProvider`（接口，全部 `default` 空实现；P3 只落这 5 个钩子，够挂 P3-2/P3-3 的调用点；
       其余触发源（开场/回合/队友攻击/战技点/治疗/DOT/忆灵…）**等真做角色时再加**，别提前铺接口）：
       ```java
       public interface EnergyProvider {
           default EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) { return null; }
           default EnergyGain onUltCast(CanHit user, Skill skill) { return null; }     // 终结技自身回能（标准=5，清零后结算）
           default EnergyGain onTakingHit(CanHit target, Damage damage) { return null; }
           default EnergyGain onKill(CanHit attacker, CanHit target) { return null; }
           default EnergyGain onBreak(CanHit attacker, CanHit target) { return null; }
       }
       ```
       > 触发源的完整清单留在 P3-0 的 C 表（调研结论），**不落成接口**。
    3. `StandardEnergyProvider implements EnergyProvider`（常规档 + 数据兜底）：
       ```java
       public class StandardEnergyProvider implements EnergyProvider {
           // 普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5 / 击破 5
           // 技能优先读技能数据 sp_base（P3-4 接进来），没有数据才退回 Constant 兜底
       }
       ```
    4. `Constant` 加兜底常量（**只是没有技能数据时的默认值**，不是唯一真相）：
       ```java
       public static final double ENERGY_GAIN_BASIC = 20;
       public static final double ENERGY_GAIN_SKILL = 30;
       public static final double ENERGY_GAIN_ULTRA = 5;
       public static final double ENERGY_GAIN_HIT = 10;
       public static final double ENERGY_GAIN_KILL = 5;
       public static final double ENERGY_GAIN_BREAK = 5;
       ```
    5. `CanHit` 加字段与入账口（**只这一条写能量的路径**）：
       ```java
       @Setter private double currentEnergy = 0;
       @Setter private double maxEnergy = 0;        // 0 = 无能量条（1407 遐蝶就是这种）；取值见 P3-0 G1
       @Setter private EnergyProvider energyProvider = new StandardEnergyProvider();
       public boolean hasEnergyBar() { return maxEnergy > 0; }
       public boolean isEnergyFull() { return hasEnergyBar() && currentEnergy >= maxEnergy; }

       /** 返回实际入账值（会被上限截断），不是理论回能值 */
       public double gainEnergy(EnergyGain gain) {
           if (gain == null || gain.amount() <= 0 || !hasEnergyBar()) return 0;
           double efficiency = gain.affectedByEfficiency()
                   ? 1 + getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()
                   : 1;
           double added = Math.min(maxEnergy - currentEnergy, gain.amount() * efficiency);
           currentEnergy += added;
           return added;
       }
       public double gainEnergy(double amount) { return gainEnergy(EnergyGain.normal(amount)); }
       ```
       > 溢出能量（`overflowEnergy`）暂不实现（P3-0 口径 6 / 千冶·刃 80 点溢出存储），
       > 等 P8 有真实角色再补，不要现在设计。
       > 拷贝构造里 `maxEnergy` / `energyProvider` 要跟着复制，`currentEnergy` 故意从 0 开始（新战斗实例）。
    6. **实测踩到的数据坑（已修）**：追加攻击 / 天赋槽位的 `attack_type` 在 `skills.json` 里是 `null`，
       `switch` 直接炸 NPE → `StandardEnergyProvider` 必须先判空再 switch（P8-2 接真实技能槽时注意同一个坑）。
    7. **设计取舍：为什么能量放在 `CanHit` 而不是 `Character`**（评审结论 = 保持现状）：
        - 回能钩子全部以 `CanHit` 类型结算：`Battle.applyDamage(target, damage)` 的 `target`、
          `damage.getAttacker()` / `getDefender()`、`SkillExecutor` 的 `user`、`Battle.castUltra(CanHit user, …)`。
          放 `Character` 就要在这 5 处 `instanceof Character`。
        - **忆灵是要能量的**（HSR.md §5.1：独立单位，有血条/能量/手动技能），而 `Summon extends CanHit`；
          放 `Character` 的话 P9 加忆灵还得再改这一层。
        - 与既有字段同级：`currentHp` / `takeDamage` / `heal` / `buffManager` / `isInvulnerable` 本来就在
          `CanHit` 上，只把能量下沉会造出"有 HP 没能量"的不对称。
        - **已知代价**：`Enemy` / 传统召唤物（神君、账账）没有能量条，现在白拿一个 `maxEnergy = 0` 的字段
          （靠 0 静默成 no-op，`EnergyBattleTest` 有断言）。数据侧也印证能量是玩家侧资源：
          `character_data.json` 有 `max_energy`，而 `AvatarServantConfig`（7 条召唤物）与怪物表都没有。
        - 备选方案（**已否决，别再改**）：B = `HasEnergy` 接口 + 5 处 instanceof；C = 组合出 `EnergyBar`。
- **验收**：`EnergyTest`：
    - 回能率 50%（`setAttribute(ENERGY_REGENERATION_RATE, new DoubleValue(0.5))`）→ `gainEnergy(20) == 30`、
      `currentEnergy == 30`
    - `EnergyGain.fixed(20)` 不吃回能率 → 加 20
    - 上限：`maxEnergy = 100` 时 `gainEnergy(200)` → 实际入账 100、`currentEnergy == 100`、`isEnergyFull()`
    - `maxEnergy = 0`（无能量条）：`gainEnergy(20) == 0`、`hasEnergyBar()` false、`isEnergyFull()` false
    - 返回实际入账值：离满只差 5 时 `gainEnergy(20) == 5`
- **依赖**：无（`DoubleValue` 已有；没属性值的 getAttribute 返回 0 值对象，安全）

---

### P3-2 回能接入 + 大招条件 ✅

- **目标**：战斗行为自动回能（走 `EnergyProvider`）；`castUltra` 检查满能量、释放后清零并按 provider 回能。
- **涉及文件**：`Battle.java`、`models/SkillExecutor.java`、新建 `test/EnergyBattleTest.java`
- **怎么做**：
    1. `Battle` 加唯一入账口（团队充能也走这里）：
       ```java
       /** 战斗内唯一回能入口：规则由 target 自己的 provider 决定 */
       public double applyEnergyGain(CanHit target, EnergyGain gain) {
           return target == null ? 0 : target.gainEnergy(gain);
       }
       public double grantEnergy(CanHit target, double amount) { return applyEnergyGain(target, EnergyGain.normal(amount)); }
       ```
    2. 技能回能：**挂点在 `SkillExecutor.execute`，规则在 `Battle.grantSkillEnergy`**
       （原来打算放 `Battle.executeSkill` 层，但技能是经 `skill.execute(...)` 多态进入的，
       Battle 里没有现成的 hitTargets；真正知道命中集的地方只有 `SkillExecutor`）：
       ```java
       // SkillExecutor.execute：把主体拆成 private resolveHits(...)，命中集往外传
       Set<CanHit> hitTargets = new LinkedHashSet<>();
       resolveHits(battle, skill, user, targets, hitTargets);
       battle.grantSkillEnergy(user, skill, hitTargets);   // 一条路径、必然执行一次
       ```
       这样**非伤害技能（护盾/增益/治疗）也回能**（`resolveHits` 提前 return 不影响），
       参数为空 / 段数为 0 的情况同样只给一次。标准实现按 `attack_type`：`Normal` → 20、
       `BPSkill` → 30、其它 → 0（终结技不在这一步给，见第 4 条）。
    3. 受击 / 击杀：放 `Battle.applyDamage`，**用真实结算结果，不要读请求参数**：
       ```java
       double settled = assemble(damage);
       boolean died = target.takeDamage(settled);
       grantHitAndKillEnergy(target, damage, died);
       ```
       私有方法里的口径：`!damage.isCountsAsAttack()` → 两边都不回能（附加伤害/真伤「不视为造成了
       1 次攻击」）；没死 → 受击方按自己的 provider 回能；死了 → 击杀回能记给
       `damage.getAttacker()`（挨打的那方已经死了就不涨能量）。
    4. `castUltra` 改造（先清零，再让 provider 结算终结技自身回能）：
       ```java
       if (user == null || user.isDeath() || !user.isEnergyFull()) return false;
       ...
       processRequests();
       user.setCurrentEnergy(0);                                        // 先清零
       EnergyGain gain = user.getEnergyProvider().onUltCast(user, ultra); // 标准实现 = 5
       if (gain != null) applyEnergyGain(user, gain);
       ```
       `isEnergyFull()` 对 `maxEnergy == 0`（1407 遐蝶这类）永远 false → 走不了终结技，符合"没有常规能量条"。
    5. 战技点消耗回能（米沙/花火/寒鸦）：不归本任务，P8-4 接 `performAction` 时再补钩子。
- **验收**：`EnergyBattleTest`（7 条）：
    - 普攻 +20、非伤害战技 +30（护盾技照样回能）、回能率 50% 时普攻 +30
    - 终结技：不满 → false 且能量不变；满 → 清零后 +5
    - 受击 +10；打死敌人者 +5；附加伤害/真伤不给受击方回能
    - `maxEnergy = 0`：打人/被打能量恒 0、`hasEnergyBar()` false、`castUltra` false
    - 测试替身 provider（技能回 7 / 受击回 99）→ 挂点确实读各自的 `energyProvider`
- **依赖**：P3-1、P1-8
- **注意**：`Main.java` 里有旧 demo 调 `castUltra`，那时角色还没有能量（P11-1 修 Main 时一起处理）。

---

### P3-3 击破回能联动 ✅

- **目标**：击破瞬间给施放方回能（P4-4 只调这一个口子，规则仍归 provider）。
- **涉及文件**：`Battle.java`、`test/EnergyBattleTest.java`
- **怎么做**：
    ```java
    public double gainBreakEnergy(CanHit attacker, CanHit target) {
        if (attacker == null) return 0;
        EnergyGain gain = attacker.getEnergyProvider().onBreak(attacker, target);
        return gain == null ? 0 : applyEnergyGain(attacker, gain);
    }
    ```
    标准实现给 5（P3-0 口径 6：基准值来自文档"额外"反推，等 P9 校准）。
- **验收**：`EnergyBattleTest` 补 2 条：`gainBreakEnergy(x, enemy)` 后 `x.currentEnergy == 5`；
    回能率 50% 时 `== 7.5`；`gainBreakEnergy(null, enemy) == 0` 不炸。
    乱破 +10、同谐开拓者 +10、忘归人 +3(魂2) 这类留给角色 provider（暂不做）。
- **依赖**：P3-1、P3-2

---

### P3-4 技能回能数据化（SPBase 落库）🚧 部分完成

- **状态**（2026-09-21，含一次试错与退回）：
    - ✅ **`sp_base` 落库**：`skills.json` 有新字段，`Skill`/`SkillData` 能读。
      （**但回能仍走常量** —— 见下条。）
    - ❌ **试过又退回**：一度把 `StandardEnergyProvider` 改成读 `sp_base`。**这是错的** ——
      多段/弹射技能的 `sp_base` 是**每段值**（艾丝妲 6、瓦尔特 10），乘段数才对，
      而段数乘算要 `SPHitRatio`（本项目数据里没有）。直接取原值会让那 6 个角色偏低；
      **常量给出的才是正确总量**，所以退回常量。
    - ✅ **顺带修掉的那个真缺陷换了实现方式**：6 个"特殊资源"角色
      （飞霄/黄泉/遐蝶/白厄/昔涟/银狼LV.999）此前被凭空发能量。现在**不在 provider 里判空**，
      而是由 `CharacterFactory` 在装配点注入 `NoConventionalEnergyProvider`（5 个钩子全不入账）——
      因为这是**设计归类**而非单条数据事实，且只堵技能那两条会漏掉受击/击杀/击破
      （黄泉上限 9，挨一下就能凑满、放出不该有的终结技）。见 `engine.md` §9.4。
- **还没做的那一步**（数据源已确认存在）：
    1. `Config/ConfigAbility/Avatar/Avatar_*_Ability.json` 里有 `SPHitRatio`
       （实测艾丝妲为 `{"IsDynamic": false, "FixedValue": {"Value": 1}}`，**每个伤害动作一个**）。
       248 个文件，需要**按技能把 SPHitRatio 求和**（默认 1）。
    2. 聚合结果落到 `sp_base` 旁边（或新建 `data/skill_energy.json`）。
    3. 那时再把 `StandardEnergyProvider` 接成 `sp_base × sp_hit_ratio_sum`；
       **不要再额外乘段数**（弹射类的 `sp_base` 已是每段值，`sp_hit_ratio_sum` 已含段数）。
- **验收**（补 `SkillEnergyDataTest`）：银枝战技 130202 → 30；艾丝妲战技 100902 → 30（6 × 5）；
  瓦尔特战技 100402 → 30（10 × 3）；景元追加攻击 → 0；黑塔追加攻击 → 5。
- **依赖**：P3-1（provider 接口）；真角色接线仍是 P8-3

---

## 5. 阶段 P4：韧性 · 击破 · 超击破 · DOT（核心玩法）

**本阶段结束时的成果**：敌人有韧性条，同元素打弱点削韧，削满触发击破伤害 + 推条 + 跳回合 + DOT。 顺序
`P4-1 → P4-2 → P4-3 → P4-4 → P4-5 → P4-6` 严格串行。

---

### P4-1 Enemy 韧性字段 ✅

- **目标**：敌人有韧性/击破状态。
- **涉及文件**：`models/Enemy.java`、新建 `test/ToughnessTest.java`
- **怎么做**（实测修订：**沿用 P2-2 已有的 `stance` / `maxStance` 命名**，不新造 `maxToughness`/`currentToughness`）：
    1. `Enemy` 加击破状态（`@Getter/@Setter` 是类级的，直接加字段就有访问器）：
       ```java
       private boolean broken;                      // 是否处于击破状态
       private DamageElement brokenElement;         // 击破元素（P4-3 击破伤害 / P4-5 DOT 用）
       private int brokenRemainTurns;               // 剩余回合数，由 P4-4 维护（P4-1 只留字段，不写死 2）
       ```
    2. 判定 + 状态机（P4-2/P4-4 调用）：
       ```java
       public boolean hasToughnessBar() { return maxStance > 0; }   // 数据里确有韧性 0 的怪

       public void reduceStance(double amount) {
           if (broken || amount <= 0) return;                        // 击破期间韧性条是空的
           stance = Math.max(0, stance - amount);                    // 归零**不自动击破**
       }
       public void breakEnemy(DamageElement element) { broken = true; brokenElement = element; stance = 0; }
       public void recoverFromBroken() { broken = false; brokenElement = null; brokenRemainTurns = 0; stance = maxStance; }
       ```
    3. 韧性单位 =「点」（`EnemyScaler` 的 stance 直接就是点，冰锋 60）；多韧性条 `stanceCount` 的逐条消耗留 P4-4。
- **验收**：`ToughnessTest`（6 条，全绿）：
    - 冰锋 @90/组1：`maxStance == stance == 60`、`hasToughnessBar()`、初始未击破
    - `reduceStance(30)` → 30；再 30 → 0 且 `isBroken()` 仍 false（归零≠击破，判定在 P4-2）；再削不变负
    - `breakEnemy(FIRE)` → `broken` + `brokenElement == FIRE` + `stance == 0`；`recoverFromBroken()` → 未击破、元素清空、`stance == 60`
    - 击破期间 `reduceStance(30)` 无效；`reduceStance(0/-5)` 无效
    - 没有韧性条的怪（`fromAttributes`）恒为 0，击破/恢复都不炸
- **依赖**：无

---

### P4-2 削韧判定 ✅

- **目标**：每段伤害按技能 `stance_list` 削韧（命中弱点才削），削空触发击破。
- **涉及文件**：`Battle.java`、`models/SkillExecutor.java`、新建 `test/ToughnessBattleTest.java`
- **怎么做**（实测修订 2 处，见下）：
    1. **削韧入口放 `Battle.reduceToughness(attacker, enemy, element, stanceDamage)`**（不是 `SkillExecutor` 私有方法）：
       理由——击破链要碰 `queue`/`gainBreakEnergy`/`applyDamage`，全是 Battle 级资源；P5-3 敌人攻击也能复用。
       ```java
       public boolean reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
           if (attacker == null || enemy == null || stanceDamage <= 0) return false;
           if (enemy.isDeath() || enemy.isBroken() || !enemy.hasToughnessBar()) return false;
           if (!enemy.isWeakTo(element)) return false;          // ← 修订 1
           enemy.reduceStance(stanceDamage);
           if (enemy.getStance() > 0) return false;
           enemy.breakEnemy(element);
           gainBreakEnergy(attacker, enemy);                    // P3-3（P4-3/4/5 往这条链上加东西）
           return true;
       }
       ```
    2. `SkillExecutor.hit(...)` 结算完伤害后按技能形状削韧：
       ```java
       private static double stanceValue(SkillData data, boolean mainTarget) {
           var stance = data.getStanceList();       // beans.Skill.StanceList（与 models.Skill 同名不同包）
           return switch (data.getEffect()) {
               case AOE_ATTACK -> stance.all();
               case BLAST -> mainTarget ? stance.single() : stance.spread();   // ← 修订 2
               default -> stance.single();        // 单体 / 秘技 / 弹射
           };
       }
       ```
       只有 `damage.isCountsAsAttack()` 且目标是 `Enemy` 才削（附加伤害/真伤不削韧）。
- **两处修订（原计划写错了，以数据为准）**：
    1. **非弱点不削韧**，没有「非弱点减半」这回事（HSR.md §3.2 是"弱点击破"）。
       `Constant.TOUGHNESS_NON_WEAK_RATIO` **不加**；"无视弱点削韧"（乱破/姬子·启行）是角色特性 → P8-7。
    2. **扩散（BLAST）不是"主目标传 spread"**：全量统计 `skills.json` 后确认
       `single` 是中心值、`spread` 是相邻值（姬子战技 = `60/0/30`、大黑塔 = `45/0/30`）。
- **验收**：`ToughnessBattleTest`（6 条，全绿）：
    - 姬子普攻（Fire 30）→ 冰锋韧性 60 → 30；冰属性普攻（非弱点）→ 韧性不变
    - 两段普攻削空 → `isBroken()` + `brokenElement == FIRE` + 击破回能 5（能量 20×2+5=45）
    - 击破后再打：韧性仍 0、击破回能只给一次（20×3+5=65）
    - 战技 Blast：中心 −60（削空→击破）、左右各 −30；终结技 AoE：全体 −60 全破
- **注意**：扩散的"相邻"按 `battle.enemies` 站位顺序（`targetableEnemies().indexOf(mainTarget)`），
  写测试时**主目标必须放在中间**，否则只有一侧相邻。
- **依赖**：P1-8、P4-1

---

### P4-3 击破伤害 ✅

- **目标**：击破瞬间结算：`击破伤害 = 击破基数(等级) × (1+击破特攻) × 技能削韧值 × 防御区 × 抗性区 × 减伤区`。
  **不可暴击、不吃攻击力/增伤**。
  ⚠ **单位必须成套（HSR.md §7.1）**：文档给的是 80 级**基础击破基数 3767**（削韧单位"常规"，普攻=1）、**超击破 376.7**
  （削韧单位"点"，普攻=10）。本任务用 `breaking_rate.json / 10 = 376.75535`，因此**传入的削韧值必须是"点"刻度**
  （例：112.5 = 30 × 2.5 × 1.5）。哪天改用 3767，削韧值要同步 /10，否则差 10 倍；P4-6 与本任务同刻度。
- **涉及文件**：新建 `models/BreakDamageCalculator.java`（**实测改成静态工具类**，与 `EnemyFactory`/`EnemyScaler` 同风格，
  不是 `new BreakDamageCalculator().build(...)`）、`Constant.java`、`Battle.java`、新建 `test/BreakDamageTest.java`
- **怎么做**：
    1. `Constant` 加 `public static final Map<Integer, Double> BREAKING_RATE;`（静态块里 `JSONReader` 加载
       `breaking_rate.json`，key = 等级 1..120）。
    2. 静态工厂（**只需 1 个方法**）：
       ```java
       public static Damage build(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
           Double raw = Constant.BREAKING_RATE.get(attacker.getLevel());
           if (raw == null) throw new IllegalArgumentException("No breaking rate for level " + attacker.getLevel());
           double breakBase = raw / 10.0;                                    // 数据文件是 10 倍值
           double be = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
           return new Damage(attacker, enemy, element, DamageType.BREAK,
                   breakBase * (1 + be) * stanceDamage);                     // 防御/抗性交给 applyDamage 装配
       }
       ```
    3. P4-2 的击破链里插一行：`applyDamage(enemy, BreakDamageCalculator.build(...))` → 再 `gainBreakEnergy`。
- **验收**：`BreakDamageTest`（5 条，全绿）：基数 3767.5535/10；3.0 击破特攻 + 112.5 削韧 + 敌防 1150
  → **78855.8**（容差 1.0）；增伤 +500% 结果不变（同时断言 `BREAK.isCrittable()==false`）；
  击破特攻 0 时退化成 `376.75535 × 30 × 防御区`；等级缺数据 → `IllegalArgumentException`。
- **依赖**：P1-3、P4-2

---

### P4-4 击破状态：推条 + 跳回合 + 恢复 ✅

- **目标**：击破后敌人行动被推迟 25% 行动条；击破期间敌人的回合被跳过；2 回合后恢复。
- **涉及文件**：`Battle.java`、`Constant.java`、`Main.java`（演示分支）、新建 `test/BreakStateTest.java`
- **怎么做**（实测：跳回合逻辑放 **Battle**，不放 Main——P5-5 的敌方回合执行要复用同一个口子）：
    1. `Constant` 加 `BREAK_DELAY_RATIO = 0.25`、`BROKEN_REMAIN_TURNS = 2`（**常量不许写在 Enemy 里**，
       所以 `Enemy.breakEnemy(element)` 只做状态转换，持续回合数由 Battle 装配）。
    2. `Battle` 加两个助手：
       ```java
       public boolean delayMovePercent(CanHit target, double percent) {   // 推条 = 行动周期 × percent
           if (target == null || percent <= 0) return false;
           double speed = target.getAttribute(AttributeType.SPEED).get();
           if (speed <= 0) return false;
           return queue.delayAction(target, 10000.0 / speed * percent);
       }

       /** 击破中的敌人轮到自己回合时调用；返回 true = 本回合不行动 */
       public boolean handleBrokenTurn(Enemy enemy) {
           if (enemy == null || !enemy.isBroken()) return false;
           enemy.setBrokenRemainTurns(enemy.getBrokenRemainTurns() - 1);
           if (enemy.getBrokenRemainTurns() <= 0) enemy.recoverFromBroken();
           return true;
       }
       ```
    3. 击破链插两行（顺序固定）：`breakEnemy` → 击破伤害 → **推条** → **`setBrokenRemainTurns(2)`** → 击破回能。
    4. `Main.round()` 的敌人分支改成先问 `handleBrokenTurn`：true → 打 `[BROKEN] ... skips this turn` 不行动；
       false → 现在的占位打印（P5-5 接真实敌人行动）。
- **验收**：`BreakStateTest`（4 条，全绿）：
    - 击破后目标 `timeRemaining` 增加**恰好 0.25 × 周期**（冰锋速度 132 → 周期 75.76）
    - `handleBrokenTurn` 第 1 次 → 剩 1 回合仍击破；第 2 次 → 剩 0、`isBroken()` false、韧性回满 60；
      再调返回 false（不跳过）
    - 韧性回满后能**再破一次**，击破回能再给一次
    - `delayMovePercent` 对 null / 0 / 负数返回 false
- **依赖**：P3-3、P4-2/P4-3

---

### P4-5 DOT（持续伤害）✅

- **目标**：击破元素附着 DOT（火→灼烧、雷→触电、物理→裂伤、风→风化），敌人回合开始结算，"先上先结算"。
  （冰/量子/虚数三系击破效果——冻结/纠缠/禁锢，见 P10-1 统一成表）
- **涉及文件**：新建 `models/Dot.java`、`models/Enemy.java`、`models/BreakDamageCalculator.java`、
  `Battle.java`、`Constant.java`、新建 `test/DotTest.java`
- **怎么做**（实测修订：**DOT 挂在"敌人自己的回合开始"，不是每次 beforeMove 都全体结算**）：
    1. `Dot`（独立类，不依赖 Buff 体系）：`source / element / baseDamage / remainingTurns` +
       `boolean tick()`（返回 true = 最后一次，调用方移除）。
    2. `Enemy` 加 `private final List<Dot> dots`（**List 不是 Set**，顺序 = 先上先结算）+ `addDot/removeDot`。
    3. `Battle.tickDots(Enemy)`：快照迭代、按顺序造 {@code DamageType.DOT} 走 `applyDamage`
       （吃增伤/防御/抗性，易伤/减伤由 `onDamage` 钩子注入；不可暴击由 `DamageType.DOT` 自己挡），
       `tick()` 为 true 就移除；**被 DOT 打死就停止后续结算**。
       调用点：`Battle.beforeMove()` 里 `actor instanceof Enemy` 时先结算一次（= 该敌人回合开始）。
    4. 击破链挂 DOT：`breakEnemy` → 击破伤害 → 推条 → `attachBreakDot` → 击破回能；
       `attachBreakDot` 只对 `Constant.DOT_ELEMENTS`（火/雷/物理/风）生效，
       base = `BreakDamageCalculator.breakBaseOf(attacker) × Constant.DOT_RATIO`
       （顺手把 `/10` 单位换算收进 `breakBaseOf`，避免两处各写一遍）。
    5. `Constant` 加 `DOT_RATIO = 0.5`、`DOT_TURNS = 3`（**示例值 TODO data**：HSR.md 只写
       "基础倍率由等级与击破特攻决定（查数值表）"，逐元素倍率还没拿到）、
       `DOT_ELEMENTS = EnumSet.of(FIRE, THUNDER, PHYSICAL, WIND)`。
- **验收**：`DotTest`（6 条，全绿）：
    - base 500、防 100、Lv80 → 每回合结算 `500 × 1000/1100`；2 次后自身移除，第 3 次 `tickDots` = 0
    - **先上先结算**：记录 `onDamage` 的 `Enemy` 子类断言收到 `[THUNDER, FIRE]`（施加顺序）
    - DOT 吃增伤（+100% 翻倍）、`DOT.isCrittable() == false`、`DOT.isBoostable() == true`
    - 姬子击破冰锋 → 挂 1 个 FIRE DOT，`baseDamage = 376.75535 × DOT_RATIO`
    - 冰属性击破（临时把弱点改成 ICE）→ **不挂** DOT（冻结不是 DOT）
    - `stepForward() + beforeMove()`：先动的敌人（速度 200）回合开始时自动结算 DOT
- **依赖**：P1-2（DOT 类型）、P4-3、P4-4

---

### P4-6 超击破（进阶）✅

> **状态：已实现（2026-09-19）**。全量测试 174/174 绿。`Constant.SUPER_BREAK_BOOST = 0.4` 仍是
> **示例值（TODO data）**，且**削韧值提高 / 弱点击破效率**两个属性尚未加入 `AttributeType`——
> 它们同时影响 §3.2 的破韧公式，属后续任务。

- **目标**：同谐开拓者终结技生效期间（简化：队友挂 `SuperBreakBuff`），我方攻击时把**打不进去的那部分削韧值**
  转化成超击破伤害。两种场景：① 敌人**已被击破**（整发削韧都转化）；② **这一发把敌人打破**（超出的那部分转化，
  于是同一发里同时吃到击破伤害与超击破伤害）。
- **涉及文件**：新建 `models/buffs/SuperBreakBuff.java`、`Battle.java`、`models/SkillExecutor.java`、
  新建 `test/SuperBreakTest.java`
- **怎么做**：
    1. `SuperBreakBuff extends AbstractBuff`：只标记，`applyEffect` 空实现（模板同 `VulnerabilityBuff`，
       `canAct() return true`）
    2. `Battle` 加（用 `BuffManager.hasBuff`，**不要**遍历内部列表——`BuffManager` 不暴露 `getBuffs()`，
       这是 P1-7 的封装决定，见 `CODE_REVIEW.md` 的 H-7）：
       ```java
       public boolean isSuperBreakActive(CanHit attacker) {
           return attacker != null && attacker.getBuffManager().hasBuff(SuperBreakBuff.class);
       }
       ```
    3. `Constant` 加：`public static final double SUPER_BREAK_BOOST = 0.4;`（示例常量的 0.4；削韧提高/弱点击破效率留 TODO）
    4. **削韧值口径（2026-09-19 确认，别写错）**：设敌人剩余韧性 `T`、技能标称削韧 `S`（= `stance_list` 的值）：

       | 情形 | 击破伤害用 | 超击破伤害用 |
       |---|---|---|
       | 未击破 且 `T > S`（没打空） | 无 | 无 |
       | 未击破 且 `S ≥ T`（这一发把韧性打空） | `min(S, T)` = **实际值**（`reduceStance` 的返回值） | `max(0, S − T)` = **超出部分** |
       | 敌人已 `broken`（`T = 0`） | 无 | `S`（整发都算超出） |

       即 `S` 被**拆成两半**给两条链，相加恒等于 `S`，不重不漏。
       例（技能 60、怪物 30 韧性、带 buff）→ **技能伤害 + 30 击破伤害 + 30 超击破伤害**。
       > ⚠️ 不要理解成"超击破用标称值、击破用实际值"就完事 —— 关键在**超出部分** `S − T`。
       > 若按"整发 `S` 都算超击破"，破韧的那一发会把 `S` 用两次（击破 + 超击破），重复计算。

       > ⚠️ **超击破不受"只有弱点才削韧"的限制**：敌人**已处于击破状态**时，整发标称削韧都算超出，
       > **与元素是否命中弱点无关**（官方文案的条件里只有"敌人处于弱点击破状态"，没有元素限制）。
       > 所以非弱点攻击打已击破的敌人**照样**产生超击破段；但**未击破**时非弱点攻击什么都不产生
       > （那一步仍然严格"只有弱点才削"）。`SuperBreakTest` 两条对调用例各钉一头。

       ⚠️ 因此 `Battle.reduceToughness` 目前只返回 `boolean`（是否破韧）**不够用**：
       它内部把超出部分丢掉了。本任务的实现把它换成了
       `public record StanceResult(double consumed, double overkill, double breakDamage, boolean broke)`。
       `breakDamage` 是**额外**加进来的：击破伤害是在 `reduceToughness` 内部经 `applyDamage` 结算的，
       调用方拿不到 —— 若不带出来，一次攻击的 `AttackEvent.totalDamage` 会漏掉整条击破链。
       `Enemy.reduceStance` 的返回值够用（`S − consumed` 即超出部分），不必改 `Enemy`。
    5. 超击破公式（参照 P4-3 组织，加在 `SkillExecutor` 里；`superBreakStance` = 上表的**超出部分**）：
       ```java
       // SkillExecutor.hit 内：施放方有 SuperBreakBuff 时，把"超出部分"转化成一发超击破伤害
       private static Damage superBreak(Battle battle, CanHit user, CanHit target,
                                        DamageElement element, double superBreakStance) {
           double breakBase = Constant.BREAKING_RATE.get(user.getLevel()) / 10.0;
           double be = user.getAttribute(AttributeType.BREAKING_EFFECT).get();
           Damage d = new Damage(user, target, element,
                   breakBase * (1 + be) * superBreakStance * (1 + Constant.SUPER_BREAK_BOOST), DamageType.SUPER_BREAK);
           d.defence(user.getLevel(), target.getAttribute(AttributeType.DEFENCE).get(),
                     user.getAttribute(AttributeType.DEFENCE_IGNORE).get());
           d.resist(target instanceof Enemy e ? e.getDamageResist().getOrDefault(element, 0.0) : 0.0,
                    user.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
           return d;   // 易伤/减伤走 onDamage 钩子；不调 addBoost（超击破不吃攻击/属性增伤）
       }
       ```
       触发条件：**施放方**身上有 `SuperBreakBuff`（用 `BuffManager.hasBuff`，已就绪）
       **且** 超出部分 `> 0` —— 该条件同时覆盖两种场景：敌人本来就已经击破（整发削韧都算超出），
       以及这一发把敌人打破（剩余韧性之外的那部分算超出）。
- **验收**：`SuperBreakTest`（✅ 已实现，9 条用例，2026-09-19）：
    - **破韧那一发（核心用例）**：敌人 `T = 30`、技能 `S = 60`、带 buff
      → 同一发里既有击破伤害（按 **30**）又有超击破伤害（按 **30**），两者**都不为零**
    - **整发都是超出**（敌人已 `broken`，`T = 0`）→ 超击破用整发 `S = 60`
    - **刚好打空**（`S = T = 60`）→ 有击破、**无**超击破（超出部分为 0）
    - **`T > S`（没打空）** → 两者都不产生
    - **未挂 Buff** → 不产生超击破段，超出部分就是浪费（只有击破伤害）
    - **非弱点打已击破的敌人** → **照样产生超击破**（用整发标称削韧）
    - 对照：**未击破 + 非弱点** → 什么都不产生（削韧那一步仍然只有弱点才削）
    - **多目标 AOE** → **每个目标各触发一次超击破**（各自用自己的剩余韧性算超出部分）
    - **一次攻击行为只算一次**：`AttackEvent` 恰好广播一次，且 `totalDamage`
      包含 技能伤害 + 击破伤害 + 超击破伤害 三种类型（不能只算技能那一段）
- **依赖**：P4-3、P1-7、P4-6 自身的 `SuperBreakBuff` + `BuffManager.hasBuff`

---

## 6. 阶段 P5：仇恨 + 敌人 AI ✅

**本阶段结束时的成果**：敌人有自己的回合，会按仇恨加权随机选我方目标、普攻打人。 顺序 `P5-1 → P5-2 → P5-3 → P5-4 → P5-5`。

> **状态：已实现（2026-09-19）**，测试 197/197 绿。落地时的三处与本文档的偏差，以代码为准：
> 1. **P5-1 直接用角色数据**：`character_data.json` 的 `aggro` 列**就是游戏倍率本身**
>    （存护 150 / 毁灭 125 / 其他 100 / 巡猎·智识 75，93 个角色全量核对一致），
>    所以 `Character` 直接接它，`Path` 枚举只作为缺数据时的兜底 —— 不用等 P8-1。
> 2. **P5-2 的嘲讽是纯标记**（无 `extraPercent`），且落点在 P5-4 的目标选择，不在 `aggroOf`。
> 3. **P5-3 用数据表而不是硬编码**：新建了 `enemy_skills.json`（键 = **怪物实例 id**）。
>    ⚠ 数据源里没有敌人技能表，**倍率是猜的**，每条带 `guessed: true`；没配条目的怪走兜底
>    （倍率 1.0、单段、元素取自身 `stance_type`）。P9-1/P9-2 接真实表时只换数据文件。

---

### P5-1 Path 仇恨值 ✅

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
    3. **数据校准**：`character_data.json` 每角色有 `aggro` 字段（景元 1204 = 75），比枚举硬编码更准 ——
       P8-1 接入后 `Battle.aggroOf` 优先读角色数据，本枚举降级为兜底（无数据时 150/125/100）
- **验收**：`AggroTest`：一个 `path = Path.PRESERVATION` 的角色 `getPath().getAggro() == 150`；
  `Path.fromName("毁灭") == DESTRUCTION`

---

### P5-2 受击概率 + 嘲讽 ✅

- **目标**：`Battle` 提供仇恨表与加权选择工具；`TauntBuff` 提供"硬指定目标"的标记。
- **涉及文件**：`Battle.java`、`models/CanHit.java`、`models/BuffManager.java`（加 `findBuff`）、
  新建 `models/buffs/TauntBuff.java`、新建 `test/AggroBattleTest.java`
- **怎么做**：
    1. `Battle` 加纯仇恨表（**嘲讽不在这里**，见第 3 条）：
       ```java
       public Map<CanHit, Double> getAggroTable(List<? extends CanHit> allies) {
           double total = allies.stream().mapToDouble(this::aggroOf).sum();
           return allies.stream().collect(toMap(a -> a, a -> aggroOf(a) / total));
       }

       public double aggroOf(CanHit a) {                       // public：P5-4 的 TargetSelector 要跨包调用
           return a instanceof Character c ? c.getPath().getAggro() : 100;
       }
       ```
       （`toMap` 需要 `import static java.util.stream.Collectors.toMap;`）
    2. 嘲讽 Buff（新建 `models/buffs/TauntBuff.java`）——**纯标记，没有数值**：
       ```java
       public class TauntBuff extends AbstractBuff {
           public TauntBuff(int duration) {
               super(duration, false);
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
    3. **嘲讽是"硬指定目标"，不是仇恨加权**（原设计写成 `aggro *= 1 + extraPercent` 是错的：
       乘法只能提高概率，永远做不到"只能选中"）。正确语义：
       > 嘲讽 buff 只要被附加，攻击方（角色或怪物）的**单体攻击**与**扩散攻击的中心**
       > 就只能选中被附加嘲讽的那个个体。**双向生效**（我方单体/扩散打敌方时同理）。
       - 落点在 **P5-4 的 `TargetSelector`**，不在 `aggroOf`；
       - `TargetSelector` 需要一个**攻击意图**参数（单体 / 扩散 / 群攻）：只有单体与扩散的中心受约束，
         群攻本来打全体、不受影响；弹射待定（见 `DOC_VS_CODE.md` A-1 的边界表）；
       - 嘲讽者**已死亡**或**不在被打的那一方** → 约束失效，退回仇恨加权（不能强制选中尸体）；
       - `TauntBuff` 因此不需要 `extraPercent`，也不该有 `getExtraPercent()`。
    4. `BuffManager` 加"按类型取实例"的口子（`TargetSelector` 要拿到嘲讽者**本人**，
       光知道"有没有"不够；**不要**暴露 `getBuffs()`，遍历留在 manager 内部是 P1-7 的决定）：
       ```java
       /** 取身上第一个该类型的 buff，没有则 null。它是 hasBuff 的严格超集。 */
       public <T extends AbstractBuff> T findBuff(Class<T> kind) {
           for (AbstractBuff buff : buffs) {
               if (buff.getClass() == kind) {
                   return kind.cast(buff);
               }
           }
           return null;
       }
       ```
- **验收**：`AggroBattleTest`：
    - 2 角色：存护 (150) + 其他 (100) → 概率 0.6 / 0.4（`assertEquals(0.6, table.get(preservation), 1e-6)`）
    - 给其他挂 `TauntBuff(2)` → **仇恨表本身不变**（嘲讽不改数值），
      但 `TargetSelector` 的单体/扩散中心**恒为该角色**（用固定种子跑多次断言每次都是他）
- **依赖**：P5-1、P1-7（封装约定：遍历留在 manager 内，用 `findBuff`）

---

### P5-3 EnemySkill（敌人普攻） ✅

- **目标**：敌人有能执行的技能。数据里 **没有**敌人技能表（skills.json 只有角色），先用模板简化：普攻 = 攻击力 × 100%，元素取
  `stance_type`。（P9-1 建 `enemy_skills.json` 后本任务被 P9-2 替换，简化实现保留为兜底）
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

### P5-4 TargetSelector ✅

- **目标**：给出选择策略：主策略 = 仇恨加权随机。
- **⚠ 候选集口径**：必须用 `Battle.targetableEnemies()`（未死目标）——它现在是"能否被选中"的**唯一出口**
  （`SkillExecutor` 已在用）。别在上层自己 filter，否则上下层口径会分叉，而"选到尸体"正是鞭尸的来源。
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

### P5-5 敌方回合执行 ✅

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

## 7. 阶段 P6：命中 / 治疗 / 护盾 ✅

> **状态：已实现（2026-09-19）**，测试 214/214 绿。三处与本文档的偏差，以代码为准：
> 1. **P6-1 顺手修了 M-5**：`EnemyScaler` 早就算出了敌人的效果命中（组1·Lv90 = 0.32），
>    但 `EnemyFactory` 漏了往面板写 → 敌人命中恒为 0。现已接线（`effectHitRate` + `debuffResist`）。
>    另外 `tryApplyDebuff(caster, target, buff, base, key)` 成了**正式的施加入口**，
>    不是"测试帮手"（技能侧别直接调 `addBuff`，否则命中/抵抗被绕过）。
> 2. **P6-2 没有 `HEAL_TAKEN_RATIO` 之外的"治疗降低"属性** —— `AttributeType` 里没有这一项，
>    所以负的 `HEAL_TAKEN_RATIO` 就是治疗降低（公式里的 `(1-治疗降低)` 与 `(1+受疗加成)` 合并成一个因子）。
> 3. **P6-3 没有护盾提高属性**：`grantShield` 现在就是传入值（🚧 等有真实效果引用时再加），
>    与 P6-2 的缺口同类。另外 `Battle.applyDamage` 的返回值改成"被盾吸走的 + 掉的血"
>    （否则打在有盾的目标上会显示造成 0 伤害，`AttackEvent.totalDamage` 会失真）。

---

### P6-1 效果命中与抵抗 ✅

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
- **依赖**：P2-4（enemy 数据）、P1-7（封装约定：遍历留在 `BuffManager` 内，需要取实例时用 `findBuff`）

---

### P6-2 治疗乘区 ✅

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

### P6-3 护盾 ✅

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

### P7-1 轮次制行动值 ✅

- **目标**：首轮总行动值 150、之后每轮 100（原先是 10000/speed 恒定）。
- **涉及文件**：`Queue.java`、`Constant.java`、`Signal.java`、新建 `test/QueueRoundTest.java`、
  `test/QueueActionManipulationTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. 首圈系数 1.5 **不是**只改 `initialize()`：`Signal` 需要记住"这一次预约是否含首轮系数"
       （`firstRound` 标记），否则该单位中途变速 / 被重置周期时无法判断新周期要不要乘 1.5。
    2. `Signal` 另外记 **`remaining`（剩余行动值，速度无关）**。原因见 `engine.md` §5.1：
       首轮的预约长度是 150、之后是 100，用百分比记账在速度变化时无法换算。
    3. 常量进 `Constant`（`ROUND_ACTION_VALUE = 100`、`FIRST_ROUND_MULTIPLIER = 1.5`），
       而不是留在 `Queue` 里当 `private` —— `Signal` 也要用它。
    4. `getRound()`：`(int)(elapsed / 100) + 1` 在 `elapsed == 150` 时会给 2，**是错的**；
       改成闭右端区间（见 `engine.md` §5.4）。
- **验收**：`QueueRoundTest` + `QueueActionManipulationTest`（speed 100 → 首轮 elapsed 150；
  speed 200 → 首轮 75；`setTopZero` 之后 +100）
- **依赖**：无

---

### P7-1b 行动条四处修正（E1/E2/E3/E4）✅

P7-1 落地后复查 `Queue`/`Signal` 时发现的四个真缺陷，与 P7-1 同批修掉：

| 编号 | 缺陷 | 修法 |
|---|---|---|
| **E1** | `setTopZero()` 重置的是**堆顶**而不是 `currentActor`；在 `move()`→`afterMove()` 窗口里动过键（推/拉条）就会重置错人 → 行动者连动两次 | 重置 `currentActor`；为 `null` 或已被移出队里时直接返回 |
| **E2** | 速度变化后**没人调** `refreshSpeed()`：加速要等该单位下一次行动才生效，减速却因为 `nextActionTime` 是绝对时间而"看起来立刻生效"（不对称） | `CanHit.setAttribute(SPEED…)` 与属性型 buff 显式 `notifySpeedChanged()` → `Battle.onSpeedChanged` → `Queue.refreshSpeed(target)`，按 §5.1 的剩余距离换算 |
| **E3** | `advanceActionByPercent` 缺 clamp：`a-(a-e)·p` 在 binary64 下可能小于 `elapsed`，`move()` 会把全局时钟往回拨 | 两侧都 clamp（`remaining` 取 max 0、结果取 max `elapsed`）；`move()` 里时钟也改成只增不减 |
| **E4** | `Signal.compareTo` 只比 `nextActionTime`，**相等时顺序未定义** → 同速单位谁先出手碰运气；且 `snapshot()` 按堆数组排序，"显示顺序 ≠ 出手顺序" | `Signal` 记全局递增的**排期序号**，`compareTo` 相等时比它（先排期的先动）；`snapshot()` 复用同一个比较规则 |

- **验收**：`QueueActionManipulationTest`（8 条）+ `QueueTieBreakTest`（8 条）。四条修正都做过**变异验证**：
  把每处修回错误实现，对应测试必须变红（E1: 2 红，E2 通知: 1 红，E2 公式: 2 红，E3 clamp: 2 红，E4: 4 红）。
- **E4 的两个决定**（避免以后被"优化"掉）：
    1. **`initialize()` 不重新取号**：它迭代的是 heap 内部数组，顺序由堆结构决定；
       在那里换号会破坏"同速单位按入场顺序出手"。
    2. **推条 / 拉条不重新取号**：把它们算成"重新排期"会导致"谁刚被拉条谁就先手"这种反直觉结果。

---

### P7-2 额外回合 ✅

- **目标**：`Battle.grantExtraTurn(canHit)`：立即行动，不消耗回合计数。
- **涉及文件**：`Battle.java`、`Queue.java`、`Signal.java`、新建 `test/ExtraTurnTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. 计划里写"复用 `advanceActionByPercent(target, 1.0)` 即可" —— **这是错的**。
       按比例拉条是把他的**正常**回合提前（消耗掉它），而额外回合是**白送一次、
       正常回合排期原封不动**。两者不是一回事，所以需要独立的插队机制。
    2. `Queue` 记 `extraTurnActor` + `extraTurnOriginalTime`；`move()` 见到它就
       "时钟不动地"让他行动（`moveExtraTurn()`），并把他的行动时间临时按到 `elapsed`，
       好让 `Battle.afterMove()` → `setTopZero()` 照常收尾。
    3. ⚠ **信号必须留在堆里**（只改键 + 重建堆）。第一版我把它 `heap.remove` 出去了，
       `setTopZero()` 的 `heap.remove(acting)` 随之失败 —— **行动者被静默丢掉**，
       行动条直接空掉。这是本项最隐蔽的坑。
    4. ⚠ **原本的排期不能在额外回合里还原**：还了之后堆顶又是他，下一次 `move()`
       会直接推他的正常回合，额外回合等于没生效。所以记进 `pendingRestore`，
       推迟到下一次 `move()` 开头处理。
    5. `castUltra` 加规则：额外回合期间禁止插入**别人**的终结技（本人可以）。
- **验收**：`ExtraTurnTest`（9 条）：立刻行动 / 时钟不动 / 轮次不变 / 正常回合不被消耗 /
  插队 / 只生效一次 / 死人拿不到 / 队外拿不到 / 拿到后死亡作废 / 终结技插入限制。
- **变异验证**：① 不还原原排期 → 1 红；② 额外回合推进时钟 → 5 红；③ 去掉终结技拦截 → 1 红。
- **依赖**：E1（已随 P7-1b 修好）

---

### P7-3 胜负状态机 ✅

- **目标**：`Battle` 有 `NOT_STARTED / RUNNING / WIN / LOSE`，双方全灭后停表。
- **涉及文件**：`Battle.java`、`Main.java`（demo 改用状态机）、新建 `test/BattleResultTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. `Battle.Status` 四态 + `status` 字段 + `getStatus()` / `isOver()`；
       判定口是**公开的幂等方法 `checkResult()`**（计划里没有这个口子，但它让测试与将来的
       关卡驱动都能直接问"现在判出来了吗"）。
    2. 判定时机放在 `removeDeadCombatants()` 末尾（清完尸体顺手判）—— 覆盖
       `processRequests()` 与 `afterMove()` 两条路，不必在每个出口各写一遍。
    3. 另外在 `startBattle()` 末尾判一次：否则"敌方列表为空"的开场会永远停在 `RUNNING`
       （没人可打，也就没人触发判定）。
    4. **`NOT_STARTED` 时不判**：战斗还没开场，谈不上胜负。计划里没写这条，
       少了它"开场前把人打死"会立刻判负。
    5. 终态**不回退**：`checkResult()` 在非 `RUNNING` 时直接返回，所以"已判胜之后我方又团灭"
       不会改判成 `LOSE`。
- **口径**：一方全灭即负（`allMatch(isDeath)`，**空列表也算全灭**）；
  两边同时全灭 → `LOSE`（先判负后判胜）。
- **验收**：`BattleResultTest`（10 条）：初始态 / 开战转 RUNNING / 敌灭判胜且时钟停 /
  我方灭判负 / 同时全灭判负 / 开场前不判 / 终态不改判 / 空阵营算全灭 /
  状态按实例（非 static）/ 中途保持 RUNNING。
- **变异验证**：① `stepForward` 去掉终态保护 → 1 红；② `checkResult` 去掉终态保护 → 2 红；
  ③ 把"判胜"提到"判负"之前 → 1 红。
- **依赖**：无

---

### P7-4 StageBean + 波次 ✅

- **目标**：解析 `stage.json`，按 `monster` 列表（每项一波）依次进怪。
- **涉及文件**：新建 `beans/StageBean.java`、`Constant.java`、新建 `models/WaveManager.java`、
  `Battle.java`、新建 `test/WaveManagerTest.java`、新建 `test/StageLazyLoadTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. `StageBean` 按计划写，另加两个读法辅助：`waveCount()`、`monsterIds(i)`
       （波内 id 必须按 `MonsterN` 的 N 序读，不能当无序集合用）。
    2. `Constant.STAGES` **没有**做成静态常量字段，改成 **懒加载** `Constant.stages()`。
       原因：实测 `stage.json` = 29303 条 / 解析 72 ms / **+35 MB 堆**，
       比其它所有数据加起来还大，而多数测试和 demo 根本不碰关卡。
       实测数据：没有它，全套测试的 `Constant` 初始化都要多付这笔钱。
       实现用嵌套类 `StageHolder`（`LOADED` 是它的静态字段 → 首次引用才初始化）。
    3. ⚠ 与其它数据表的第二点差别：`stage.json` **缺失时返回空表而不抛**。
       它只服务关卡驱动，而 `Constant` 静态块是"抛一下就整个测试套件一起挂"的地方 ——
       一个可选功能不该把全套测试拖下水。取不到关卡时由 P7-5 的 `StageFactory.load` 给明确报错。
    4. `EnemyFactory.create` 的实际签名是 `(monsterId, level, hardLevelGroup)`（**三个**参数），
       计划里写的两参数版本不存在。`WaveManager` 用三参数版。
    5. `WaveManager.nextWave()` 里加了 `battle.checkResult()` 重新判定，并给它加了 `hasPendingWaves()`。
    6. **`Battle.checkResult()` 必须接入波次**（计划里完全没提，是本项真正的坑）：
       P7-3 的判据是"一方全灭"，而波次模式里"敌队为空"只代表**这一波还没进**。
       所以 `Battle` 记一个 `waveManager`，`checkResult()` 在"还有待进的波"时不判胜。
       不接这一步，`startBattle()` 会因为敌队为空**立刻判胜**，战斗根本开不起来。
- **验收**：`WaveManagerTest`（15 条）+ `StageLazyLoadTest`（2 条，懒加载护栏）
- **变异验证**：① 去掉 `!pendingWaves` 判断 → 4 红；② 把 `stages()` 挪回静态块 → 懒加载护栏 1 红。
- **⚠ 关于懒加载护栏的教训**（写下来免得重犯）：第一版用"运行期解析计数快照"做断言，
  结果**单独跑绿、全套红** —— 共享 JVM 里别的测试类先调了 `stages()`，快照自然失效。
  改成反汇编 `Constant.class`、只切出 `static {}` 那一段来检查（编译产物是死的，与顺序无关）。
  期间还踩了两次：字符串常量其实在**嵌套类**的常量池里（不在 `Constant` 里）；
  以及编译器会把 `stages()` 内联，所以要断言 `Method stages:` 而不是 `StageHolder`。
- **依赖**：P7-3（胜负状态机）
### P7-5 StageFactory + 难度 ✅

- **目标**：`StageFactory.load(stageId)`：按 stage 的 `hard_level_group`/`level` 组装一个可运行 Battle。
- **涉及文件**：新建 `utils/StageFactory.java`、新建 `test/StageFactoryTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. 计划里写 `Constant.STAGES.get(stageId)` —— 那张表在 P7-4 改成了**懒加载**
       `Constant.stages()`（9 MB，不放进静态块），所以这里用新入口。
    2. `load()` 一共要做四件事，计划里只写了一件半：
       ① 建 Battle（敌队先空着）；② 建 `WaveManager`（同时把波次登记进 `Battle`）；
       ③ **`startBattle()`**（否则状态停在 `NOT_STARTED`，`stepForward()` 不推进；
       计划的验收里写了"`stepForward()` 后 status != NOT_STARTED"，但没写这一步）；
       ④ `nextWave()` + **`processRequests()`**（进怪是排队入场，漏了就只躺在
       `addRequestItems` 里，行动条上是空的 —— 计划的代码片段就漏了这个）。
    3. 加了重载 `load(stageId, team, rng)`：P8-5 要换真队、测试要固定种子，都得走它。
       另外 `requireStage(id)` / `hasStage(id)` 把"关卡不存在"的报错说清楚 ——
       包含 `stage.json` 未生成（返回空表）与 id 写错两种情况，信息不同。
    4. 临时队伍给了 **3 人**（速度 100/134/90，各不相同以便行动条有区分度），
       并**设了 120 能量上限** —— `fromAttributes` 的 `maxEnergy` 默认 0 表示"没有能量条"，
       那种角色永远放不出终结技，关卡跑起来会缺一条主分支。
- **验收**：`StageFactoryTest`（9 条）：能开打 / 难度来自关卡（高等级关卡怪血量更高）/
  怪与关卡数据一致 / 固定种子可复现 / 能连跑回合 / 多波可进下一波 / 临时队伍可用 /
  可自带队伍 / 非法输入被拒（含自解释报错）。
- **变异验证**：① 去掉 `processRequests()` → 2 红；② 去掉 `startBattle()` → 3 红。
- **依赖**：P7-4（`StageBean` + `WaveManager`）、P7-3（状态机）

---

## 9. 阶段 P8：角色数据化（真实角色全机制）

**本阶段结束时的成果**：`CharacterFactory.create(1204, 80)` 一条命令造出真实景元（面板/能量/元素/命途全对），普攻·战技·终结技走
`skills.json` 真实倍率，天赋触发追加攻击，战技点（SP）真实流转。 **"角色什么时候进来" = P8**：P1–P7 全部用
`Character.fromAttributes` 占位，P8 之后一律 `CharacterFactory` + 真实技能。

**阶段内顺序**：`P8-1 → P8-2 → P8-3 → P8-4 → P8-5`（P8-4 零依赖可插队）。
**P8-6 / P8-7 / P8-8 是 P8-3 的正路**：先补事件与触发器表，再让代表角色尽量**纯数据化**；
P8-3 里的 `switch (cid)` 只是过渡实现。

---

### P8-0 角色机制数据化（架构总纲，先读）✅

> 起因：调研完能量机制后发现「一堆机制各自依赖特定角色」（93 个角色 × 天赋/行迹×3/星魂×6/秘技/追加攻击/光锥/遗器）。
> 一个角色一个 Java 类必然失控，所以先把架构定死。

**1) 三分法判据（先问"引擎有没有这个能力"，而不是"这是哪个角色"）**

| 机制长什么样 | 归到哪 | 例子 |
|---|---|---|
| 在某事件后，做一件**引擎已有能力**的事（加成 / 附加伤害 / 真伤 / 回能 / 上 buff / 额外回合 / 削韧 / 治疗 / 护盾） | **数据**（触发器 + 条件 + 效果） | 知更鸟「队友每次攻击后 +2 能量」、缇宝「我方每命中 1 目标 +1.5」、驭空「队友行动后 +2」、姬子「终结技每消灭 1 敌 +5」 |
| 需要引擎**还不具备**的能力 | **引擎任务**（先补能力，再做数据） | 「层数替代能量条」（黄泉/飞霄/白厄/昔涟）、「全队损血/治疗转资源」（遐蝶）、「按能量上限百分比回能」（停云/藿藿/星期日） |
| 需要跨系统状态机，数据表达不出来 | **逃生舱**：一个 Java 类 + 在本文档登记 | 刻律德菈【军功】↔【爵位】双轨 + 目标变更重置充能；万敌【血仇】阈值切换 |

**反模式（违反即返工）**：`Battle` / `SkillExecutor` / `Damage` / `CanHit` 里**禁止出现 `cid` 判断**；
唯一允许的地方是装配点（`CharacterFactory`）与 provider/触发器注册表。

**2) 缺的三样基础设施**

1. **事件补齐**（现在只有 `BattleEvent / MoveEvent / DamageEvent / AttackEvent`）→ P8-6。
   还缺 `SkillCastEvent`、`EnergyEvent`、`HpLossEvent`、`HealEvent`、`KillEvent`、`BreakEvent`；
   补完这 6 个，P3-0 C 表里的 12 类触发源就全部有宿主。
2. **触发器表 + 效果词表**（角色内容 = 数据，`resources/characters/<cid>.json`）→ P8-7。
3. **层数资源升一等公民**（`Resource`）→ P8-8。P3-0 B 表那 15 个「非常规」角色
   （姬子/刃/貊泽/三月七/翡翠/乱破/万敌/刻律德菈/长夜月/不死途/千冶·刃/黄泉/飞霄/白厄/昔涟）
   **本质是同一个东西**：`Resource{id, scope, max, onGain, onFull, onSpend}`。
   做了它，"特殊能量条"从「9 个角色各写一个类」变成「9 条数据 + 1 个引擎类」。

**3) 落地纪律**

- **机制先行、角色当验收**：每补一个引擎能力，顺手数据化 1–2 个代表角色（克拉拉/希儿就是 P8-3 的样本），
  不要试图一次做 93 个。
- **triage 表**（下面这张）是"依赖角色的机制"的唯一 backlog：可枚举、可排序、可勾。

**4) 角色机制 triage 表（骨架；能量类已由 P3-0 完成调研）**

| cid | 角色 | 机制 | 归类 | 落地 |
|---|---|---|---|---|
| 1309 | 知更鸟 | 队友每次攻击 +2 能量（魂2 +3）；战技额外 +5；秘技每波次 +5 | 数据 | P8-7（`onAllyAttack` + `GAIN_ENERGY`） |
| 1403 | 缇宝 | 我方每命中 1 目标 +1.5 能量；开场 +30 | 数据 | P8-7 |
| 1202 | 停云 | 终结技为单体回 50 能量（魂6 60）；秘技自回 50 | 数据（需 op 支持"目标=队友"） | P8-7 |
| 1207 | 驭空 | 持【鸣弦号令】时队友每次行动后 +2 | 数据 | P8-7 + P8-6（回合事件） |
| 1003 | 姬子 | 击破弱点 +1【充能】；终结技每击杀 +5 能量 | 数据 + Resource | P8-6（击破事件）+ P8-8 |
| 1205 | 刃 | 战技不回能；受伤/耗血攒【充能】→ 满层追击 | 引擎能力 + 数据 | P8-6（`HpLossEvent`）+ P8-8 |
| 1308 | 黄泉 | 【残梦】替代能量条（上限 9；负面/战技/开场获取） | 引擎能力 | P8-8 + `EnergyProvider` 桥接 |
| 1407 | 遐蝶 | 无能量条；【新蕊】按全队损血 1:1 / 治疗转化 | 引擎能力 | P8-8 + P8-6 |
| 1220 | 飞霄 | 【飞黄】每 2 次我方攻击 +1（上限 12，6 点开大） | 引擎能力 | P8-8 + 攻击计数器 |
| 1412 | 刻律德菈 | 【军功】↔【爵位】双轨；目标变更充能重置为 0 | **专用类（逃生舱）** | P8-3 模式，登记在案 |

> 后续每补一类机制（削韧/击破/DOT/受击反击/Buff 体系/光锥/遗器），照这张表的格式各开一张。

---

### P8-1 CharacterFactory + 角色字段补全 ✅

- **目标**：一条命令造真实角色；把 `character_data.json` 里已有的字段接进战斗模型。
- **涉及文件**：`models/Character.java`、`enums/DamageElement.java`、
  `utils/LevelPromotionCalc.java`（修 bug）、新建 `utils/CharacterFactory.java`、
  新建 `test/CharacterFactoryTest.java`
- **实际怎么做**（与计划有出入，记录差异）：
    1. 计划要做的三件事里**有两件 P5 已经做了**：`CharacterData` 早有 `aggro` 组件、
       Builder 早就在 `setPath(Path.fromMt(mt))` + `setAggro(...)`。所以 P8-1 只剩：
       加 `element` 字段、按数据设 `maxEnergy`、让 `DamageElement.fromString` 大小写不敏感。
    2. `DamageElement.fromString` 原来只吃首字母大写（`skills.json` 的口径），
       而 `character_data.attribute` 是**全小写**（`"thunder"`）→ 解析会**静默返回 null**。
       改成大小写不敏感 + 忽略首尾空白；`"Unknown"` 仍然返回 null。
    3. `maxEnergy` 直接来自数据，`null → 0`（= 无能量条）。**不兜底成 100** ——
       93 个角色里只有 1407 遐蝶是 null。
    4. 选了 **5 个真实角色**做完整字段核对（覆盖 5 种元素 / 5 种命途 / 4 档仇恨 / 4 档能量）：
       1204 景元（雷·智识·130·75）、1102 希儿（量子·巡猎·120·75）、
       1107 克拉拉（物理·毁灭·110·125）、1105 娜塔莎（物理·丰饶·90·100）、
       1001 三月七（冰·存护·120·**150**）。
       前三+娜塔莎是 P8-5 的目标队伍，三月七补上 150 仇恨那一档。
    5. 另选 3 个**数据边界**角色只做能量断言：1407 遐蝶（null）、1220 飞霄（12）、1308 黄泉（9）。
- **⚠ 顺手修掉一个潜伏 bug（`calcCharacterRate` 负晋阶）**：
  低等级配 `promotion=true` 会算出负晋阶次数（Lv1 → `1/10 - 1 = -1`），
  把 **Lv1 面板压到 0.6 倍**（景元生命 158.4 → 95.04）。加了 `max(0, promoteCount)` 下界。
  **这个 bug 极难诊断**：95.04 恰好等于景元的**攻击**值，所以现象看起来完全像
  "属性数组索引错位"（我先按这个方向查了半天）。修完 Lv1 倍率 = 1.0。
  20/21/70/79/80/90 各档**不受影响**，作者验证过的 7.35 等锚点依然成立。
- **⚠ 另一处误判（我自己）**：断言面板时漏了 `point.json` 的行迹加成
  （`build()` 里无条件 `SkillPoint.appendTo`）。景元攻击 894.136 = `95.04 × 7.35 × 1.28`、
  防御 545.7375 = `66 × 7.35 × 1.125`。失败值又长得像"错位"，害我第二次误诊。
  现在测试从 `SkillPoint.sumAttributes` 显式把行迹算进去，并断言那 28% / 12.5%。
- **验收**：`CharacterFactoryTest`（14 条）：景元身份字段 / 面板（含行迹）/ 晋阶锚点 /
  负晋阶回归 / 5 人字段全表 / 大小写不敏感 / 全部 93 角色元素可解析 /
  3 个能量边界 / 有能量条的角色 / 等级影响面板 / 占位入口无身份 / 未知 cid /
  工厂角色能进战斗。
- **变异验证**：① `null → 100` 兜底 → 1 红；② `fromString` 改回大小写敏感 → 4 红；
  ③ 去掉 `max(0, …)` 下界 → 2 红。
- **依赖**：P1-4（level）、P5-1（Path 映射）、P2-2（DamageElement）
### P8-2 技能装配（真实槽位 → 真实倍率）✅

- **目标**：`SkillType` 槽位对上 `skills.json` 的 skill_id；`DefaultSkill`/`SkillExecutor` 用的全是真实倍率、元素、削韧值。
- **涉及文件**：`Constant.java`、`models/Character.java`、`models/Battle.java`、新建
  `test/SkillSlotMappingTest.java`、新建 `test/UltraThresholdTest.java`
- **实际怎么做**（2026-09-21）：
    1. `Constant.SKILL_SLOT` 只放**4 项**（`COMMON=1 / SKILL=2 / ULTRA=3 / TALENT=4`）。
       计划里的 `SkillType.MAZE` / `SkillType.TECHNIQUE` **这两个枚举值不存在** ——
       `SkillType` 只有 `COMMON/SKILL/ULTRA/TALENT/SUMMON_SKILL/SUMMON_TALENT`。
       数据里槽位 6/7 确实是地图普攻与秘技，但要覆盖它们得先**加枚举值**，本项没做。
    2. `Character.Builder.build()` 改成按 `SKILL_SLOT` 取各自槽位（`SKILL_SLOT` 没有的类型跳过）。
       ⚠ 这一行改动的影响比计划说的大：修之前**六个槽位全是普攻**，所以
       "倍率/削韧/元素"从来没用过真实值。demo 里姬子战技因此从"几百"变成"16938"，
       敌人血量要跟着从 12000 提到 30000 才不至于一发秒。
    3. ⚠ **计划第 4 条是错的**：写着"P3-2 的 `castUltra` 无需改"，但 `castUltra` 判的是
       `isEnergyFull()`（`currentEnergy >= maxEnergy`），而 5 个角色的**开大阈值低于上限**
       （云璃 120/240、银枝 90/180、绯英 240/480、飞霄 6/12、昔涟 12/24）——
       不改的话云璃会攒到 240 才肯放。所以补了 `Battle.isUltraReady` / `ultraEnergyCost`
       （读 `spNeed`，缺失退回 `maxEnergy`），**放开后清零**。
       角色文档写的是「释放所需能量 120（上限 240）」——"所需"是门槛。
    4. 没做计划第 3 条（`SkillExecutor` 的 default 分支加提示）：本项聚焦槽位与阈值，
       非伤害 effect 的处理仍留给 P10-3/P10-6/P9-4。
- **验收**：`SkillSlotMappingTest`（13 条）+ `UltraThresholdTest`（9 条）+
  `SkillExecutorDiagnosticTest`（5 条）。
- **仍未做**（都是**其它阶段**的活，不是本项遗留）：
    - **非伤害技能的"效果"**：`SkillExecutor` 对非伤害类**静默 return**，
      所以治疗/护盾/buff/控制/召唤技能被施放后**什么都不发生**（只有回能照给）。
      这是设计上的分阶段：治疗走 `Battle.heal`（P6-2 已实现，但**没有任何地方自动分派**）、
      buff 是 P10-3、控制是 P10-6、召唤是 P9-4。
      本项只加了**可开关的诊断日志**让这个静默可见（见下条）。
    - **秘技的效果**：例如景元"下一场战斗开始时【神君】+3 段" —— 需要 P8-6 事件 + P8-7 触发器表。
- **✅ 计划第 3 条（`default` 分支提示）已做，但改了形式**：
  计划想直接 `IO.println`，但那会在**每次治疗/护盾**时打印 —— demo 每回合都在放，
  会刷屏。所以改成**默认关闭的开关** `SkillExecutor.setLogNotDispatched(boolean)`，
  日志内容还标注了**归属阶段**（`P6-2 已实现（走 Battle.heal，不经本执行器）` / P10-3 / P10-6 / P9-4）。
  覆盖：`SkillExecutorDiagnosticTest`（开了会打印 / 默认不打印 / 护盾也报 /
  伤害技能不报 / **诊断不改变行为**——治疗仍然不生效，这条把"加日志 ≠ 实现效果"钉住）。
- **⚠ 一处自我更正**：我曾把"技能等级没接进伤害"记成缺口，**这是错的**。
  `SkillExecutor` 一直用 `int index = skill.getLevel() - 1` 取逐级参数表的第 N 行。
  出错原因很蠢：我构造了 8 级技能，却断言 `getData().getSkills().getFirst()` 是 1.2 ——
  而 `getSkills()` 返回整张表，`getFirst()` 永远是第 1 档。**把自己取错行当成了引擎没取行。**
  现已改为端到端断言：8 级 / 1 级的**实际伤害**比 = 2.4（`skillLevelScalesActualDamage`）。
  真正成立的事实只是"**装配出来的角色默认技能等级为 1**"，那属于 P8 成长系统，不是缺口。
- **变异验证**：把槽位映射改回恒为 1 → `UltraThresholdTest` 4 红；
  去掉 `CharacterFactory` 的特殊 provider 注入 → `SpecialEnergyProviderTest` 3 红；
  去掉 `attachBattleSkills()` → `SkillSlotMappingTest` 3 红。
- **依赖**：P8-1、P1-8

---

### P8-3 天赋 + 追加攻击（两个代表角色）

- **目标**：`TALENT`（skill_id=4）真正生效。先做两个无召唤的代表：克拉拉 1107（受击反击）、希儿 1102（击杀额外回合）。
- **涉及文件**：新建 `models/ai/TalentTrigger.java`、新建 `models/talents/ClaraCounterTalent.java`、
  `models/talents/SeeleKillTalent.java`、`Battle.java`、新建 `test/TalentTest.java`
- **怎么做**：
    1. 触发器接口 + 注册（复用已有 `models/event` 事件体系）：
       ```java
       public interface TalentTrigger { void onEvent(Battle battle, BattleEvent event); }
       ```
       `Battle` 加 `List<TalentTrigger> talents = new ArrayList<>();` + `registerTalent(...)`；
       `BattleEvent` 加 `type` 字段（`HIT / KILL / TURN_END / ULTRA`），在 `applyDamage`（命中/击杀后）与 `afterMove`（回合后）各发一次。
    2. `ClaraCounterTalent`：收到 `HIT` 且受击者是本人 → 读 `SkillData.init(1107, 4)` 的 param[0] 倍率 →
       `SkillExecutor` 追加段（`DamageType.ADDITIONAL`，`notCountsAsAttack`）
    3. `SeeleKillTalent`：收到 `KILL` 且击杀者是本人 → `battle.grantExtraTurn(self)`（P7-2）+ 自身增伤 40%（固定值
       `Constant.SEELE_ULTRA_BOOST`，TODO 数据校准）
    4. `CharacterFactory.create` 末尾按 cid 注册（`switch (cid) { case 1107 -> register(new ClaraCounterTalent(c)); ... }`，未知角色跳过）
- **验收**：`TalentTest`：
    - 克拉拉被打：追加 1 段（HP 再降 `attack × 0.8`），`DamageType.ADDITIONAL` 且 `getCountsAsAttack() == false`（不回能不削韧）
    - 希儿击杀敌人：该角色行动条立即提前（`peekNext()` 是她的一次新行动）
- **依赖**：P1-9（追加伤害）、P7-2（额外回合）、P3-2（回能挂点）

---

### P8-4 战技点（SP）

- **目标**：战斗资源"战技点"：开局 3 点、上限 5；普攻 +1、战技 -1、终结技不消耗。
- **涉及文件**：`Battle.java`、`Constant.java`、新建 `test/SkillPointTest.java`
- **怎么做**：
    1. `Constant`：`public static final int SKILL_POINT_MAX = 5;`、`public static final int SKILL_POINT_START = 3;`
    2. `Battle`：
       ```java
       @Getter private int skillPoints = Constant.SKILL_POINT_START;
       public void gainSkillPoint(int n) { skillPoints = Math.min(Constant.SKILL_POINT_MAX, skillPoints + n); }
       public boolean spendSkillPoint() { if (skillPoints <= 0) return false; skillPoints--; return true; }
       ```
    3. `performAction` 里按 `skill.getData().getSkillType()`：
       ```java
       case "Normal" -> gainSkillPoint(1);
       case "BPSkill" -> { if (!spendSkillPoint()) { IO.println("[SP] 战技点不足"); return false; } }
       case "Ultra" -> { /* 不消耗 */ }
       ```
- **验收**：`SkillPointTest`：开局 3；普攻 → 4，连放 2 次封顶 5；战技 → 减 1；0 点放战技 → `performAction` 返回 false 且无伤害
- **依赖**：P8-2（skillType 判定立足点）；零依赖可插队

---

### P8-5 真实队伍装配（StageFactory 换真角色）

- **目标**：P7-5 的双人 `fromAttributes` 临时队换成 4 人真队；光锥/遗器沿用 Builder 已有管线。
- **涉及文件**：`utils/StageFactory.java`、新建 `test/RealTeamTest.java`
- **怎么做**：
    1. `StageFactory.load(stageId)` 里：
       ```java
       List<Character> team = List.of(
               CharacterFactory.create(1204, 80),   // 景元（雷）
               CharacterFactory.create(1102, 80),   // 希儿（量子）
               CharacterFactory.create(1107, 80),   // 克拉拉（物理）
               CharacterFactory.create(1105, 80));  // 娜塔莎（治疗，P10-3 后真生效）
       ```
    2. 每个角色再 `.weapon(...)` / `.relicSuit(...)`：先从 `Constant.WEAPONS` 挑同命途光锥（数值被动 P10-3 再接，先只吃面板）
- **验收**：`RealTeamTest`：`load(103201)` → `team.size()==4`、每个 `getElement()` 非 null、Battle 能完整跑一轮不炸
- **依赖**：P8-1、P7-5

---

### P8-6 事件补齐（触发器宿主）

- **目标**：把 P3-0 C 表的 12 类触发源都变成事件，让"角色机制"只订阅事件，不再往 `Battle` 里塞逻辑。
- **涉及文件**：新建 `models/event/SkillCastEvent.java`、`EnergyEvent.java`、`HpLossEvent.java`、
  `HealEvent.java`、`KillEvent.java`、`BreakEvent.java`、`TurnStartEvent.java`；
  `models/CanHit.java`（实现并转发给 `BuffManager`）；`Battle.java`、`models/SkillExecutor.java`（发事件）；
  新建 `test/EventBusTest.java`
- **怎么做**：
    1. 照 `AttackEvent` 的现成模式：接口 + 全部 `default` 空实现 + `CanHit` 转发 `BuffManager` + `Battle` 广播给友方。
    2. 每个事件必须携带足够还原事实的字段：
       - `SkillCastEvent(battle, user, skill, hitTargets)`（非伤害技能也要发）
       - `EnergyEvent(battle, target, actuallyAdded)`（用 `gainEnergy` 返回的**实际入账值**）
       - `HpLossEvent(battle, target, before, after, source)`（`after - before` 就是损血量，遐蝶/万敌/刃要）
       - `HealEvent(battle, healer, target, amount)`、`KillEvent(battle, attacker, target)`、
         `BreakEvent(battle, attacker, target, element)`、`TurnStartEvent(battle, actor)`
    3. 发送点：`SkillExecutor.execute`（技能）、`Battle.applyEnergyGain`（能量）、`CanHit.takeDamage`/`heal`
       （损血/治疗，注意附加伤害/真伤口径与 `isCountsAsAttack()` 一致）、`Battle.grantHitAndKillEnergy`
       （击杀）、`Battle.gainBreakEnergy`（击破）、`Battle.beforeMove`（回合开始）。
    4. **不要**顺手把效果实现也写了——本任务只发事件 + 测试收到事件，效果归 P8-7/P8-8。
- **验收**：`EventBusTest`：挂一个测试 buff，断言收到的 `(事件, 角色, 数值)` 序列；
  DOT/附加伤害不发 `KillEvent`；无能量条角色不发 `EnergyEvent`。
- **依赖**：P1-7（事件模式已成型）

---

### P8-7 触发器表 + 效果词表（角色内容数据化）

- **目标**：角色机制 = 数据表（`resources/characters/<cid>.json`），引擎只做解释；从此**不写 `XxxTalent.java`**。
- **涉及文件**：新建 `beans/TriggerSpec.java`、`beans/EffectSpec.java`、`models/TriggerTable.java`（解释器）、
  `Constant.java`（加载）、新建 `src/main/resources/characters/*.json`、新建 `test/TriggerTableTest.java`
- **怎么做**：
    1. 表结构：
       ```json
       { "cid": 1403,
         "resources": [{ "id": "tribbie_charge", "scope": "SELF", "max": 3 }],
         "triggers": [
           { "on": "SKILL_CAST",  "when": ["self"],            "do": [{ "op": "GAIN_ENERGY", "amount": 30 }] },
           { "on": "ALLY_ATTACK", "when": ["attacker!=self"],   "do": [{ "op": "GAIN_ENERGY", "amount": 1.5 }] } ] }
       ```
    2. 第一版 op 词表（**只做引擎已有能力**，别扩）：`MODIFY_ATTR` / `ADD_DAMAGE` / `TRUE_DAMAGE` /
       `GAIN_ENERGY` / `GAIN_RESOURCE` / `SPEND_RESOURCE` / `APPLY_BUFF` / `EXTRA_TURN` / `ADVANCE` /
       `REDUCE_TOUGHNESS` / `HEAL` / `SHIELD`；每个 op 一个 `record` 实现，`switch` 只在解释器里。
    3. 装载：`CharacterFactory` 造角色时把该 cid 的表挂成触发器列表；**未登记 = 空表（不是错误）**。
    4. 数据源：blog 文档（`E:\code\blog\hsr\<id>_<名>.md`）+ tbgd 技能文案；每条效果写清出处，数值缺失标 `TODO data`。
- **验收**：`TriggerTableTest`：**不写任何 Java 角色类**，纯数据跑通两个真实角色——
  缇宝 1403（我方每命中 1 目标 +1.5 能量）与知更鸟 1309（队友每次攻击 +2 能量）；
  再补一条"未登记角色 = 空表、战斗不炸"。
- **依赖**：P8-6、P3-1（能量入账口）

---

### P8-8 层数资源 Resource（替代能量条）

- **目标**：一个通用 `Resource`，让"层数当能量/层数触发大招"的角色（P3-0 A/B 表）不写专用类。
- **涉及文件**：新建 `models/Resource.java`、`models/ResourceManager.java`；`CanHit.java`（挂 manager）、
  `Constant.java`、新建 `test/ResourceTest.java`
- **怎么做**：
    1. `Resource(id, scope, max, initial)`；`ResourceManager.gain/spend/get/isFull`，满了不溢出并发"满"事件
       （`onFull` 效果仍归触发器表，别硬编码）。
    2. **资源不等于能量**：黄泉是"【残梦】满 9 才能放大招"——用 `EnergyProvider` 把资源接到
       `isEnergyFull()`/`castUltra` 上（P3 已经把这条口子留成 provider），**不要**改 `CanHit` 的能量语义。
    3. 三个来源先做：事件触发获得（P8-7 `GAIN_RESOURCE`）、主动消耗（`SPEND_RESOURCE`）、
       转化（损血/治疗 → 资源，需要 P8-6 的 `HpLossEvent`/`HealEvent`）。
    4. `scope` 至少支持 `SELF` / `PARTY`（三月七【充能】、大丽花全队共享那类以后再说）。
- **验收**：`ResourceTest`：加满触发"满"且不溢出；消耗到 0；`HpLossEvent` 下"每损失 1 点生命 +1 资源"正确；
  测试替身 provider 让"资源满 → `castUltra` 可用"。
- **依赖**：P8-6、P8-7

---

## 10. 阶段 P9：怪物全机制（多技能 · 精英 · Boss · 召唤）

**本阶段结束时的成果**：敌人不再只有"平 A"。技能表数据化（P9-1）→ 全效果执行（P9-2）→ 按权重选技能（P9-3）→ 会召唤
（P9-4）→ Boss 会换招/反击/免疫控制（P9-5）。 **"怪物什么时候进来" = P2（面板数据）→ P5（会普攻）→ P9（全机制）**。

**阶段内顺序**：`P9-1 → P9-2 → P9-3 → P9-4 → P9-5` 串行。

---

### P9-1 敌人技能数据（enemy_skills.json 自建）

- **目标**：敌人技能表。 **turnbasedgamedata 没下发怪物技能**（`skills.json` 只有角色），先自建最小表；找到源数据后只替换
  `Constant` 加载处，其余代码不动。
- **涉及文件**：新建 `src/main/resources/data/enemy_skills.json`、新建 `beans/EnemySkillBean.java`、`Constant.java`、新建
  `test/EnemySkillDataTest.java`
- **怎么做**：
    1. bean：
       ```java
       public record EnemySkillBean(String name, String attackType,   // COMMON / SKILL / ULTRA
               String effect,        // SingleAttack / AoEAttack / Blast / Impair / Summon
               String element, double multiplier, int stance, int hits,   // hits: 目标数（0=全体）
               double aiWeight, String condition,   // condition: null / "hp<0.5" / "firstTurn"
               int summonId, String debuffKey, double debuffChance) {}
       ```
    2. 首批手写 3 个怪（对齐 P9-2/P9-4 验收）：
       - 冰锋 1002011：`{普攻 SingleAttack Ice ×1.0 stance30 w1.0}`、`{战技 AoEAttack Ice ×0.6 stance30 w1.2 condition "hp<0.5"}`
       - 奥钦 8034010：普攻 ×1.0 + `Impair` 技能（`debuffKey="STAT_CTRL"`，P10-2 生效）
       - 灯塔 8033020：普攻 ×1.0 + `{Summon ×0 stance30 w1.0 condition "hp<0.8" summonId=8032040}`
    3. `Constant.ENEMY_SKILLS = JSONReader.fromJSON("enemy_skills.json", ...)`（key = monster **template_id**，
       `EnemyFactory` 按 `cfg.templateId()` 查）
- **验收**：`EnemySkillDataTest`：`Constant.ENEMY_SKILLS.get(1002011).size() == 2`；普攻条目 `multiplier == 1.0`、`stance == 30`
- **依赖**：P2-1（加载模式参照）

---

### P9-2 EnemySkill 全效果（替换 P5-3 简化版）

- **目标**：`EnemySkill` 从 bean 构造，按 effect 复用 P1-8 伤害分派（AOE/BLAST/SINGLE），不再硬编码 ×1.0 单目标。
- **涉及文件**：`models/EnemySkill.java`、`utils/EnemyFactory.java`、新建 `test/EnemySkillFullTest.java`
- **怎么做**：
    1. `EnemySkill` 改造成持有 `EnemySkillBean`；`getElement()/getEffect()/getStance()` 透传 bean 字段
    2. 给 `SkillExecutor` 加"无 SkillData 入口"（敌人技能不占用 `skills.json`）：
       ```java
       public static void executeSimple(Battle battle, DamageElement element, SkillEffectType effect,
                                        int stance, double multiplier, CanHit user, List<? extends CanHit> targets)
       ```
       内部复用现有 switch（single/aoe/blast/bounce），削韧挂点（P4-2）原样生效；`EnemySkill.execute` 一行委托它。
    3. `EnemyFactory.create`：`Constant.ENEMY_SKILLS.get(cfg.templateId())` 全部挂上；无表 → 兜底普攻（P5-3 代码保留）
- **验收**：`EnemySkillFullTest`：冰锋 Lv90 战技打 3 人 → 每敌 `attack × 0.6 × 防区`；普攻仍 ×1.0；按 bean 削韧 30
- **依赖**：P9-1、P5-3、P1-8

---

### P9-3 敌方 AI 技能选择器

- **目标**：敌人回合先选技能再选目标（P5-4 已有目标选择）。
- **涉及文件**：新建 `models/ai/SkillSelector.java`、`Main.java`、新建 `test/SkillSelectorTest.java`
- **怎么做**：
    1. 纯静态：
       ```java
       public static EnemySkill next(Enemy e, Battle battle, Random rng) {
           List<EnemySkill> usable = e.getSkillList().stream()
                   .filter(s -> s.conditionOk(e))   // "hp<0.5" / "firstTurn" / null
                   .toList();
           double total = usable.stream().mapToDouble(EnemySkill::getAiWeight).sum();
           double roll = rng.nextDouble() * total;
           for (EnemySkill s : usable) { roll -= s.getAiWeight(); if (roll <= 0) return s; }
           return usable.getLast();
       }
       ```
    2. `Main.round()` 敌人分支：
       ```java
       EnemySkill s = SkillSelector.next(e, battle, battle.getRng());
       List<? extends CanHit> targets = s.hits() == 0 ? aliveAllies : List.of(TargetSelector.select(...));
       battle.performAction(e.getSkills().get(SkillType.COMMON), targets);  // P9-2 后每个技能独立注册
       ```
       （技能直接查 `e.getSkillList()`，不塞 `SkillType` 枚举也行——以 P9-2 结构为准）
- **验收**：`SkillSelectorTest`：w1.0/w1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%）；`condition "hp<0.5"` 满血时选不到战技
- **依赖**：P9-2、P5-5

---

### P9-4 召唤物（summon_id 机制）

- **目标**：`monster_config.summon_id` 生效：敌人技能召唤实体入战，实体可受击、会死亡移除。
- **涉及文件**：`models/Summon.java`（现有基类）、新建 `utils/SummonFactory.java`、`Battle.java`、`models/Enemy.java`、新建
  `test/SummonTest.java`
- **怎么做**：
    1. `SummonFactory.create(summonId, level)`：召唤物同样有 `monster_config` entry（如 8032030 `"All or Nothing"`）→ 直接
       `EnemyFactory.create` + `setSummon(true)`（`Enemy` 加 `@Getter @Setter private boolean summon;`）
    2. `Battle` 加 `public void summon(Enemy boss, int summonId)`：`summonId` 若在 `MONSTERS` 里 → `EnemyFactory.create` →
       `enemies.add` + 请求队列进场（复用 `addRequestItems`，见 P7-4）
    3. `Enemy` 加 `@Getter private final List<Enemy> summons = new ArrayList<>();`；本体重伤 → 召唤物同判移除
       （`removeDeadCombatants` 清理）
    4. 技能触发：P9-1 bean `effect == "Summon"` → `battle.summon(self, bean.summonId())`
- **验收**：`SummonTest`：灯塔 8033020（hp<0.8 触发）→ `battle.enemies.size() == 2`；召唤物被打死 → 数量回落；本体死 → 召唤物全清
- **依赖**：P9-2、P5-5、P7-4
- **注**：忆灵（角色专属召唤、面板快照本体、`DamageType.MEMORY`）仍属远期（见 §13），本任务只做通用怪物召唤。

---

### P9-5 Boss 机制（phase 换招 / 受击反击 / 控制免疫）

- **目标**：精英/Boss 行为：HP 阈值换招、受击反击、按 `debuff_resistance` 免疫控制。
- **涉及文件**：`models/Enemy.java`、`models/buffs/CounterMechanic.java`、`Main.java`、新建 `test/BossMechanicTest.java`
- **怎么做**：
    1. `Enemy` 加 `@Getter @Setter private int phase = 0;`；敌方回合开场：
       ```java
       if (e.getPhase() == 0 && e.getHpRatio() < 0.5) e.setPhase(1);   // 换招 = 技能列表切第二套（condition 里表达）
       ```
    2. 受击反击：用 `DamageEvent`（P1-7，注意已从 `DamageListener` 改名）→ 新建 `CounterMechanic`（挂 boss；
       被非召唤伤害命中 → 追加一段 `DamageType.ADDITIONAL`、`notCountsAsAttack()` 的反击，**反击目标 = 该段的
       `damage.getAttacker()`，即"施放技能的个体"**，倍率走 `Constant.BOSS_COUNTER_RATIO`，TODO 数据校准）
    3. 控制免疫：`Enemy` 加 `public boolean isImmuneTo(String resistKey)`（查 `debuffResist`）；奥钦 `debuff_resistance`
       `STAT_CTRL: 0.5` → P6-1 的 `hitChance` 传 `"STAT_CTRL"` 自然半减，无需新代码
    4. ⚠ **阶段推进绝不能用"0 血不死"实现**：`CanHit.takeDamage` 在 HP≤0 时立刻 `death = true`。要演出/锁血，
       用 `setInvulnerable(true)`（`Battle.applyDamage` 对它返回 0，P1-9 补丁加的语义位），再**显式重置 HP** 进入下一段；
       否则要么阶段被跳过（Boss 提前退场），要么后续每段继续走完整结算 = **鞭尸**。
       多血条（`hp_bars`）同样按"打空一段 → invulnerable → 重置 HP → 下一段"实现。
- **验收**：`BossMechanicTest`：奥钦 HP 降到 50% 以下 → 下个回合用 phase 1 技能；boss 受击后追加反击段且
  `getCountsAsAttack() == false`；冰锋（`STAT_CTRL_Frozen=1`）对冻结免疫
- **依赖**：P9-2、P1-7、P6-1

---

## 11. 阶段 P10：机制补完（全战斗规则闭环）

**本阶段结束时的成果**：战斗里所有"规则"齐了——七系击破异常、控制状态机、通用 Buff 与刷新规则、速度操纵、终结技插入、
Debuff 数据化。 **没有新系统，只有把已开口子填满。**

**阶段内顺序**：`P10-1 → P10-2 → P10-3 → P10-4 → P10-5 → P10-6`（P10-4/P10-5 零依赖可插队）。

---

### P10-1 七系击破异常全量

- **目标**：P4-5 只做了 4 系 DOT（灼烧/触电/裂伤/风暴）；补冰（冻结）、量子（纠缠）、虚数（禁锢）。统一成"表驱动"。
- **涉及文件**：`Constant.java`、`Battle.java`（P4-5 挂点改查表）、新建 `test/BreakEffectAllTest.java`
- **怎么做**：
    1. `Constant`：
       ```java
       public record BreakEffect(double dotRatio, int dotTurns, double delayPercent, String control) {}
       public static final Map<DamageElement, BreakEffect> BREAK_EFFECTS = Map.of(
               DamageElement.FIRE,      new BreakEffect(0.5, 3, 0.0,  null),        // 灼烧 DOT
               DamageElement.THUNDER,   new BreakEffect(0.5, 3, 0.0,  null),        // 触电 DOT
               DamageElement.PHYSICAL,  new BreakEffect(0.5, 3, 0.0,  null),        // 裂伤 DOT
               DamageElement.WIND,      new BreakEffect(0.5, 3, 0.0,  null),        // 风暴 DOT
               DamageElement.ICE,       new BreakEffect(0.0, 0, 0.5,  "FREEZE"),    // 冻结：跳回合 + 推条
               DamageElement.QUANTUM,   new BreakEffect(0.5, 3, 0.2,  null),        // 纠缠：DOT + 延迟
               DamageElement.IMAGINARY, new BreakEffect(0.0, 0, 0.3,  "IMPRISON")); // 禁锢：延迟
       ```
       （比例先按示例值，TODO 数据校准）
    2. P4-5 `reduceToughness` 里的挂 DOT 代码改为查 `BREAK_EFFECTS.get(element)`；击破推条统一 = 固定 25% + `delayPercent`
    3. `FREEZE` 的"跳回合"接 P10-2 状态机（本任务先只做 DOT 与推条差异）
- **验收**：`BreakEffectAllTest`：冰击破 → 无 DOT、推条 = 25%+50%；量子击破 → DOT 每回合 500×3 且推条 25%+20%；物理击破 →
  仅 DOT（回归 P4-5）
- **依赖**：P4-5、P4-4

---

### P10-2 控制异常状态机

- **目标**：冻结/禁锢/纠缠/眩晕统一成"效果命中 → 上状态 → 状态期 canAct/延迟生效"。
- **涉及文件**：新建 `models/buffs/ControlBuff.java`、`models/BuffManager.java`、`Battle.java`、新建 `test/ControlTest.java`
- **怎么做**：
    1. `ControlBuff extends AbstractBuff`：`canAct() return false`（冻结/眩晕）；禁锢/纠缠不改 canAct，靠 `delayPercent` 在
       `applyEffect` 时推条（P10-4 的 `delayMovePercent`）
    2. `BuffManager` 加 `public boolean hasControl()`（复用已有 `canAct` 接口，P4-4 的 BROKEN 跳回合分支旁加同款判断）
    3. 冻结特有：跳过回合时受伤害 +30%（`Constant.FROZEN_VULN = 0.3`，示例值 TODO）
    4. 施加方：技能 `IMPAIR` → `battle.applyDebuffChance(...)`（P6-1 已写帮手）→ 命中才 `addBuff`；先 `Enemy.isImmuneTo`（P9-5）
       过滤——**注意 key 精确匹配**（冰锋 `STAT_CTRL_Frozen=1` → 冻结 0%；奥钦 `STAT_CTRL=0.5` → 全部控制半减）
- **验收**：`ControlTest`：冰锋被冻结技能命中 → `hitChance == 0`（免疫）；普通怪冻结 → 该敌回合跳过、结束后恢复；禁锢 →
  行动条延迟 30%；冻结期受伤害 ×1.3
- **依赖**：P6-1、P9-5、P4-4

---

### P10-3 Buff 体系完善（属性类 + 刷新规则）

- **目标**：`StatModifierBuff`（攻击/防御/速度/减伤百分比）+ 刷新规则（同源覆盖、异源叠加）；治疗/护盾技能（RESTORE/DEFENCE）
  落地。
- **涉及文件**：新建 `models/buffs/StatModifierBuff.java`、`models/BuffManager.java`、`models/SkillExecutor.java`、新建
  `test/BuffRuleTest.java`
- **怎么做**：
    1. `StatModifierBuff extends AbstractBuff implements DamageListener`：构造 `(duration, AttributeType, double pct)`；
       `applyEffect` → `target.addPercent(attr, pct)`；`removeBuff` → 减去同等值（`tickEffect` 参照 `BoostDamageBuff` 模板）
    2. `BuffManager.addBuff` 加刷新规则：**同 class + 同 target → 覆盖**（移除旧的、重置 duration，取新值）；不同 class → 叠加；
       同 class 不同来源 → 取绝对值大者（HSR 近似规则，TODO 数据校准）
    3. `SkillExecutor` 非伤害分支落地：
       - `RESTORE` → `battle.calculateHeal`（P6-2），倍率读 `param_list[0]`、固定值 `param_list[1]`
       - `SUPPORT/DEFENCE` → `StatModifierBuff` / `grantShield`（P6-3）
       - `ENHANCE` → 纯被动，不执行（打印 TODO）
    4. 光锥/遗器数值被动（P8-5 遗留）用本 buff 表达：`Weapon` 加 `passives: List<BuffSpec>`（先常量表，TODO 数据校准）
- **验收**：`BuffRuleTest`：攻 +50% 2 回合 → 伤害 ×1.5；同 buff 再上 → duration 刷新不叠加；到期 → 伤害回到 ×1.0；娜塔莎
  治疗打出 1560（P6-2 数字回归）
- **依赖**：P6-2、P6-3、P1-7

---

### P10-4 速度与行动条操纵

- **目标**：速度 buff/debuff 真实影响行动条；公开"推条/拉条" API（P4-4 的内部逻辑上移）。
- **涉及文件**：`Queue.java`、`Battle.java`、新建 `test/SpeedBuffTest.java`
- **怎么做**：
    1. `Queue` 加 `public void notifySpeedChanged(CanHit c)`：按当前 `SPEED` 重算 `cycleTime`（保留 `nextActionTime`，只改后续步进）
    2. `StatModifierBuff`（P10-3）挂 `AttributeType.SPEED` 时 → `battle.getQueue().notifySpeedChanged(target)`
    3. `Battle` 公开两个 API（P4-4 的击破推条改调这里，删重复代码）：
       ```java
       public void delayMovePercent(CanHit c, double pct) {
           queue.delayAction(c, 10000.0 / c.getAttribute(AttributeType.SPEED).get() * pct);
       }
       public void advanceMovePercent(CanHit c, double pct) { queue.advanceActionByPercent(c, pct); }
       ```
- **验收**：`SpeedBuffTest`：speed 100 减速 30% → 下次行动间隔 ≈ 10000/70 = 142.857；拉条 50% → 行动提前半圈
- **依赖**：P10-3、P7-1（行动值口径）

---

### P10-5 终结技插入

- **目标**：终结技任意时点可放（P3-2 已支持任意时点调用）；明确规则：不消耗行动条、不占回合、先清零再回自身 5。
- **涉及文件**：`Battle.java`、`Main.java`、新建 `test/UltraInsertTest.java`
- **怎么做**：
    1. 规则确认：`castUltra` 只要求 `currentEnergy >= maxEnergy`（P3-2 已有）；`Main.round()` 我方分支加"回合开始前可放大招"的
       输入位（demo：`if (IO.ask("放 ult? ")) battle.castUltra(...)`）
    2. 防御性：`castUltra` 后不触发 `afterMove`；`processRequests` 不产生新回合（`stepForward` 不推进）
    3. 追击角色先清后回口径（P3-2）不变
- **验收**：`UltraInsertTest`：满能量 → 任意时点 `castUltra` true；行动条无变化（`getTimeRemaining` 前后一致）；能量 =
  5 × (1+回能率)
- **依赖**：P3-2、P7-2

---

### P10-6 Debuff 基础概率数据化

- **目标**：`IMPAIR` 技能的基础概率从 `param_list` 读，喂给 P6-1 的 `hitChance`。
- **涉及文件**：`models/SkillData.java`、`Battle.java`、新建 `test/DebuffChanceDataTest.java`
- **怎么做**：
    1. `SkillData` 加 `public double debuffChance()`：约定 `param_list` 第 3 项为几率（先校准 2 个技能，失败再调 index；加注释
       说明约定来源与校准方法）
    2. `SkillExecutor` 的 `IMPAIR` 分支：
       ```java
       double p = data.debuffChance();
       if (battle.applyDebuffChance(caster, target, p, "STAT_CTRL")) { /* P10-2 上 ControlBuff */ }
       ```
- **验收**：`DebuffChanceDataTest`：读出的概率 ≠ 0 且与手写 baseChance 一致；沿用 P6-1 三例做数字回归
- **依赖**：P10-2、P6-1、P8-2

---

## 12. 阶段 P11：演示与收尾

---

### P11-1 Main 修复 + demo 包拆分

- **目标**：`Main` 变成 `public static void main(String[] args)` 入口，逻辑拆进 `demo/`。
- **涉及文件**：`Main.java`、新建 `src/main/java/com/laosun/aluminium/demo/CharacterDemo.java`、`demo/BattleDemo.java`
- **怎么做**：
    1. `static void main()` → `public static void main(String[] args)`
    2. 属性预览逻辑（遗器/角色构建部分）搬 `CharacterDemo`；战斗部分搬 `BattleDemo`
    3. `Main.main` 只留 `BattleDemo.run()`（+可选 `CharacterDemo.run()`）
- **验收**：`.\gradlew.bat run` 能跑；Main 只剩入口调用

---

### P11-2 真实内容演示（冰锋战）

- **目标**：冰锋（弱火/雷）+ 2 真角色（火/雷属性技能）演示完整循环：普攻→削韧→击破→推条→跳回合→DOT→回能→大招→WIN。
- **涉及文件**：`demo/BattleDemo.java`、按需小修 `Battle`/`Enemy`
- **怎么做**：
    1. 角色：`CharacterFactory.create(1109, 80)`（虎克·火）+ `CharacterFactory.create(1204, 80)`（景元·雷）；敌方
       `EnemyFactory.create(1002011, 29)`（stage 103201 的 level 29）
    2. 回合循环：
       ```java
       while (battle.getStatus() == Battle.Status.RUNNING) {
           battle.stepForward();
           battle.beforeMove();
           // 我方：ask 玩家输入或自动按顺序放技能（battle.performAction(...)，SP 不够自动普攻）
           // 敌方：P9-3 的技能选择 + P5-4 的目标选择
           battle.afterMove();
       }
       ```
    3. 每步 `battle.printBattle()`：HP + 韧性/`[BROKEN]`/DOT 标记 + 战技点（P8-4）+ `castUltra` 提示（P10-5）
- **验收**：一遍跑通：削韧 → 击破伤害 → 敌人跳回合 → DOT 扣血 → 我方满能量放大招 → 敌人死亡 → WIN

---

### P11-3 测试总盘点 + 基准 + 零警告

- **目标**：全部验收测试在，`gradlew test` 全绿，出 Benchmark 对比，零编译警告。
- **涉及文件**：`Benchmark.java`、各测试类
- **怎么做**：
    1. 对照【进度总览】表：每个 ☐ 变成 ✅ 时对应测试类已在
    2. `Benchmark.java`：`Random` 固定种子，10 万轮战斗计时（改造前数值存 one 个字段对比）
    3. `.\gradlew.bat build` 无 warning；`git add -A && git commit`
- **验收**：全绿 + Benchmark 输出前后耗时对比

---

## 13. 远期（只记规格，不排实现）

> 做完 P1–P11 再开。以下只保证"有规格锚点"，不承诺顺序。
> 原"召唤物/Boss 机制"已排入 P9-4 / P9-5，此处只留它们做不完的部分。

| 项目                 | 规格锚点                                                                                                                                                                                                   |
|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **C 忆灵（角色专属召唤）** | P9-4 只做通用怪物召唤；忆灵 = 独立血条/能量/可受击/可选中，伤害类型 `MEMORY`，面板快照本体，角色专属召唤物。`models/Memosprite` 新类                                                                     |
| **B4 Boss 专属机制库** | P9-5 已有换招/反击/免疫；此处留给只属于个别 Boss 的机制：召唤自带 buff、连锁技能、护盾阶段、死亡强制自爆等（每个 = 一个 `BossMechanic` 子类）                                                              |
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
7. **P8 角色为什么最后才来**：真实角色技能面很宽（治疗/辅助/控制/召唤），依赖 P6 的命中治疗护盾、P7 的额外回合；
   放前面必然满屏 TODO。地基本来就能用 `fromAttributes` 占位测，不亏。
8. **P9 怪物全机制紧随其后**：AI（P5）+ 命中（P6）+ 行动条（P7）三块地皮备好，"会打人"升级成"会打团"。唯一的外部
   风险是怪物技能数据源缺失——P9-1 用自建表隔离，找到源数据只换加载处。
9. **P10 机制补完**：全是已开口子的填充，原则一条——**没有新架构，只填数据**；任何任务做到一半发现需要新架构，
   说明它跑偏了，回退并拆小。
10. **角色机制为什么在 P8-0 先定架构**：93 个角色 × (天赋 + 行迹×3 + 星魂×6 + 秘技 + 追加攻击) 一个角色一个类是
    维护地狱。所以先定三分法（数据 / 引擎能力 / 逃生舱）与"引擎禁止判 cid"的红线，再补事件（P8-6）→
    触发器表（P8-7）→ 层数资源（P8-8）。**顺序上永远是机制先行、角色当验收**：
    P1–P7+P10 补引擎能力，每个能力顺手数据化 1–2 个代表角色，不做全量。

**每步保持：可编译 → `.\gradlew.bat test` → 提交。**
