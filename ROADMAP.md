# aluminium 开发路线图

一个用 Java 写的类《崩坏：星穹铁道》回合制战斗引擎。数据来自
[turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata)，经 `E:\code\python\generate_data.py`
解析到 `src/main/resources/data/`。

**状态一律当场实测，本文件不记数字**（写下来的数字会过时，见下面的"怎么量"）。
引擎侧基础机制（伤害流水线、韧性击破、能量、战技点、行动条、关卡波次、角色真实面板、
敌方召唤物、我方忆灵）已具备； **"角色机制 = 数据"的通路已打通**： **事件 → 触发器表 → 解释器**，角色机制写在
`src/main/resources/characters/<cid>.json`，
引擎只解释、不认识具体角色。

**怎么量**：`git rev-list --count HEAD`（提交数）、
数 `build/test-results/test/*.xml`（测试数与结果）、
比 `character_data.json` 行数与 `characters/*.json` 个数（角色覆盖率）、
`gradlew run` / `--args=mechanics` / `--args=all` 的行数（demo 出口）。

> ⚠⚠ **凡是写在本文件里的数字都会过时** —— 本文件不保留任何"某时某刻的账"。
> 需要覆盖率／测试数／内容规模时，按上面三条命令 **当场实测**。

> **本文件只留三样东西**： **设计原则 + 待办 + 踩坑记录** —— 三者都不随时间失效。
> 已完成的逐条规格、带日期的进度快照、逐轮叙事 **一律不留**（它们会腐烂，而且会 **把待办埋掉**）。
> 需要查"当初为什么这么做"或"某时是什么状态"时去 git 历史里翻
> （`git log --diff-filter=D --name-only` 找删除提交，再 `git show <commit>:ROADMAP.md`）。

---

## 1. 设计原则（改代码前先读这一节）

### 1.1 三条红线

| # | 红线                                                                                     | 为什么                                                                           |
|---|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| 1 | **引擎不认角色**：`Battle` / `SkillExecutor` / `Damage` / `CanHit` 里**禁止 `cid` 判断** | 93 个角色 × (天赋 + 行迹×3 + 星魂×6 + 秘技 + 追加攻击) 一个角色一个类 = 维护地狱 |
| 2 | **能数据化就数据化**，写不成数据的才允许专用类，且必须在 §1.2 的表里登记                 | "机制先行、角色当验收"；顺序永远是**先补引擎能力，再数据化角色**                 |
| 3 | **一个任务自带测试，做完就绿**；改动前先想"这属于哪个抽象"                               | 没有测试的修复等于没修；往错的抽象里加代码比不加更贵                             |

### 1.2 三分法（P8-0 定的架构总纲）

问"**引擎有没有这个能力**"，而不是"这是哪个角色"：

| 机制长什么样                                                                                                                 | 归到哪                                | 例子                                                                   |
|------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|------------------------------------------------------------------------|
| 在某事件后，做一件**引擎已有能力**的事（加成 / 附加伤害 / 真伤 / 回能 / 上 buff / 额外回合 / 削韧 / 治疗 / 护盾 / 资源增减） | **数据**（触发器 + 条件 + 效果）      | 知更鸟「队友每次攻击后 +2 能量」、缇宝「我方每命中 1 目标 +1.5」       |
| 需要引擎**还不具备**的能力                                                                                                   | **引擎任务**（先补能力，再做数据）    | 「层数替代能量条」（黄泉/飞霄/白厄/昔涟）、「损血/治疗转资源」（遐蝶） |
| 需要跨系统状态机，数据表达不出来                                                                                             | **逃生舱**：一个 Java 类 + 在下表登记 | 刻律德菈【军功】↔【爵位】双轨 + 目标变更重置充能；万敌【血仇】阈值切换 |

**唯一允许出现 `cid` 的地方**：装配点（`CharacterFactory`）与 provider / 触发器注册表。

### 1.3 逃生舱登记表（每加一个专用类就往这里加一行）

| cid  | 角色     | 机制                                        | 登记于  |
|------|----------|---------------------------------------------|---------|
| 1412 | 刻律德菈 | 【军功】↔【爵位】双轨；目标变更充能重置为 0 | v2 P8-0 |

### 1.4 可扩展性的现有范式（新东西照这个来）

| 轴                     | 抽象                                     | 注入点                    | 为什么这样分                         |
|------------------------|------------------------------------------|---------------------------|--------------------------------------|
| **个人**资源（能量条） | `EnergyProvider`（5 个钩子）             | `CanHit.energyProvider`   | 能量条是**个人**的                   |
| **队伍**资源（战技点） | `SkillPointPolicy`                       | `Battle.skillPointPolicy` | 战技点是**全队共享**的               |
| 通用队伍级数值         | `Resource`（有界 + 显式溢出 + 原子消耗） | 由上面两者持有            | 战技点与 P8-8 层数资源共用           |
| 技能类型判定           | `SkillCategory` 枚举                     | `SkillData.getCategory()` | 消灭裸字符串 `switch` 的**静默失配** |

**验收标准**：新增一个角色机制时， **引擎目录下应该一行都不用改** —— 只加数据、
或只在装配点换一个实现。`SkillPointPolicyExtensibilityTest` 就是这条标准的样板
（不改引擎一行，只换策略就改掉了普攻增量 / 上限 / 开局）。

### 1.5 落地纪律

- **机制先行、角色当验收**：每补一个引擎能力，顺手数据化 1–2 个代表角色， **不做全量**。
- **写完必须变异验证**：把源码改回旧行为，确认新测试 **真的会红**。
  本项目已有多次"测试看着绿、其实空转"的教训（见 §4）。
- **文档承诺必须有测试兜着**：只写在 javadoc 里的承诺会腐烂（见 §4）。
- **警惕"假前置"**：`P8-3`（天赋 + 追加攻击）曾被一条 **假前置**卡住 —— "数据里没有 `is_follow_up`
  字段，所以判断不了哪个技能算追加攻击"。真相是 **不需要那个字段**：追加攻击的 payload 本来就在 **天赋槽自己的技能数据**
  里（元素/削韧/每级倍率齐全），唯一缺的是"**什么时候**放"，而那正是触发器表
  提供的。⇒ **动手前先确认"缺的那个东西"真的缺**，否则会为不存在的前置造能力。

---

## 2. 全局约定（写代码时永远遵守）

| 约定       | 内容                                                                                                                            |
|------------|---------------------------------------------------------------------------------------------------------------------------------|
| 常量       | 所有数值（回能、击破基数、clamp 上限、比率）一律进 `Constant`，代码里不写魔法数字                                               |
| 随机数     | 暴击 / 目标选择一律走注入的 `java.util.Random`（构造给 seed，可复现），**禁止 `Math.random()`**                                 |
| 测试       | JUnit 5；断言 `assertEquals(expected, actual, 1e-6)`；放 `src/test/java/com/laosun/aluminium/test/`，命名 `XxxTest`             |
| 数据       | 真实数据只从 `data/*.json` 经 Gson 读（`Constant` / `JSONReader`），不硬编码；测试/演示里允许手写常量并标 `// TODO data`        |
| 单段单目标 | `Damage` 永远是「单段·单目标」；一个技能 = N 个 Damage（AOE→每敌 1 个；BLAST→主目标+相邻；BOUNCE→循环 N 次）                    |
| 判别口诀   | **有伤害要结算 ⇒ 构造 `Damage`；不造 `Damage` ⇒ 无伤害**（治疗/护盾/回能/上 Buff 不构造 Damage）                                |
| 每段独立   | 每段伤害独立判定暴击、独立结算                                                                                                  |
| 判断失败   | **数据错误要 fail fast**（如伤害技能缺元素）；**数据不认识要降级**（如未知 `attack_type` → `UNKNOWN` 而不是抛异常）。两者别搞反 |
| 提交       | 任务编号进 commit message；`.\gradlew.bat test` 全绿才提                                                                        |

### 2.1 核心类与口子速查

> ⚠ **完整的机制说明没有第二份文档了** —— `engine.md` 已删（见 §10 文档分工）。
> 分工改成： **本文说"还要做什么"，代码说"现在是什么"**。
> 查"现在是什么"的入口：`src/main/java`（引擎）＋ `src/main/resources/characters/<cid>.json`
> （角色机制，每条规则自带 `source`/`note` 标出处）。本文件里残留的"见 `engine.md` §X"一律按此处理。

| 类                       | 关键口子                                                                                                                                                                                                                        |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Battle`                 | `applyDamage`（**唯一结算入口**）/ `performAction` / `useSkill` / `castUltra` / `startBattle` / `stepForward` / `beforeMove` / `afterMove` / `processRequests` / `grantExtraTurn` / `heal` / `grantShield` / `skillPointPolicy` |
| `models.Damage`          | `toValue()` / `breakdown()` / 7 个乘区 accessor / `addBoost` 等装配口 / `crit` / `trueDamage` / `notCountsAsAttack`                                                                                                             |
| `models.CanHit`          | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / `getLevel` / `getCamp` / `getEnergyProvider` / 事件转发（`BattleEvent`/`MoveEvent`/`DamageEvent`/`AttackEvent`）                                                    |
| `models.Resource`        | `gainClamped` / `gain`（显式溢出）/ `spend` / `spendExactly` / `setMaxOverflow` / `isFull` / `isCapped`                                                                                                                         |
| `models.skillpoint.*`    | `SkillPointPolicy`（接口）/ `StandardSkillPointPolicy`（基础规则 + `gainForCast` 覆盖点）                                                                                                                                       |
| `SkillData`              | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）/ **`getCategory()`** / `getSpNeed()`                                                                                                               |
| `Queue`                  | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `refreshSpeed` / `grantExtraTurn`                                                                                                                            |
| `utils.CharacterFactory` | `create(cid, level)`（真实角色）/ `usesSpecialResource(cid)` / `exists`                                                                                                                                                         |
| `models.EnemyFactory`    | `create(monsterId, level, hardLevelGroup)`                                                                                                                                                                                      |
| `utils.StageFactory`     | `load(stageId)`                                                                                                                                                                                                                 |
| `Constant`               | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_TRACES` / `MONSTERS` / `SKILL_SLOT` / `stages()`（懒加载）                                                                                                                         |

> ** 拆分后新增的类**（`TriggerInterpreter` 6813 → 2310，`Battle` 3844 → 3621，
> `TriggerTable` 4049 → 1844）：
> 从解释器拆出五个 —— `TriggerVocabulary`（合法拼写的封闭集合）/ `TriggerValidator`
> （装载期校验）/ `TriggerTargets`（选择器 → 打到谁）/ `TriggerMagnitudes`（scale → 多少）/
> `TriggerEffects`（op → 做什么）；从 `Battle` 拆出 `BattleDamage`（伤害六乘区装配）；
> 从 `TriggerTable` 拆出 `TriggerConditionParser`（条件 DSL：19 方法 ＋ 26 个条件类 ＋ 38 常量）。
> ⚠ **`Battle` 的刀口被不变量钉住，拆之前先读 `REFACTOR.md` §一**：
> `DEALING_DAMAGE` 公告 **在装配中途**发射，`fireTriggers(…, Damage, …)` 与 `assemble`
> **必须保持 private**（`DamagePipelineTest` 数"公共方法里收 `Damage` 的恰好 1 个"）。
> ⚠ **`TriggerTable` 那一刀量了三次才成**（前两次都回退）：共享常量 **跟着 DSL 走**、
> 其他读者反过来指向新家；`PartyCondition`（接口没有 `static`）/ `HasShield` / `Numeric`
> **必须留下**。三次的测量过程与工具都在 **`REFACTOR.md`**。

### 2.2 数据文件速查

| 文件                               | 内容                                                                                                                                                     | 已确认锚点                                                                                                                                                       |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `monster_config.json`              | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak, *modify_ratio, damage_resistance, debuff_resistance, summon_id}` | **1002011 冰锋**：弱 `[Fire, Thunder]`，抗 5 项 0.2，`STAT_CTRL_Frozen=1`；**1003010 银鬃尉官**：`summon_id = [1002040, 1002040]`（692/2649 只怪有非空召唤名单） |
| `monster_template_config.json`     | 模板基础属性                                                                                                                                             | **1002011**：atk 18 / def 210 / hp 69.75 / spd 100 / stance 60 / 效果抵抗 0.2                                                                                    |
| `hard_level_group.json`            | `{组: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}`                                                             | 组1 Lv90 `{36.821384, 5.238095, 236.53471, 1.32, 1, 0.32, 0.1}`                                                                                                  |
| `breaking_rate.json`               | 等级 → 击破基数（**原始 10 倍值**）                                                                                                                      | Lv80 = 3767.5535 → 公式里 `/10`                                                                                                                                  |
| `skills.json`                      | `cid → {槽位 → {attack_type, max_level, param_list, skill_effect, stance_list, element, sp_need, sp_base}}`                                              | 槽位 1 普攻 / 2 战技 / 3 终结技 / 4 天赋 / 6 地图普攻 / 7 秘技；**敌人技能不在这里**                                                                             |
| `character_data.json`              | `cid → {name, attribute, mt, max_energy, aggro, ..., rarity}`                                                                                            | 景元 1204：thunder / 能量 130 / aggro 75 / hp 158.4                                                                                                              |
| `stage.json`                       | `stage_id → {type, hard_level_group, level, monster:[...]}`（9 MB，**懒加载**）                                                                          | `103201`：level 29                                                                                                                                               |
| `monster_attack_modify_ratio.json` | **本项目补丁**（444 条 ≠1）                                                                                                                              | 见 §4 陷阱 2                                                                                                                                                     |
| `enemy_skills.json`                | **自建**（P5-3 起），只覆盖 5 只演示怪，倍率是猜的                                                                                                       | 见 §4 陷阱 7                                                                                                                                                     |

### 2.3 机制文档（公式有疑义时以它为准）

| 文档                                                                               | 内容                                                                                       |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `E:\code\blog\hsr\HSR.md`                                                          | 乘区总览（§2）、行动/破韧/仇恨（§3）、治疗护盾（§4）、忆灵（§5）、欢愉（§6）、超击破（§7） |
| `E:\code\blog\hsr\GLOSSARY.md` / `GLOSSARY_EXTRA.md`                               | 游戏名词表（含官方描述原文）                                                               |
| `E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md`                                | **93 个角色文档**（用户明确许可读），含天赋/行迹/星魂/秘技原文                             |
| `E:\turnbasedgamedata\aluminium_texts\{RELICS,WEAPONS,GLOSSARY,GLOSSARY_EXTRA}.md` | 遗器 / 光锥 / 名词总表                                                                     |

> ⚠ `HSR.md` **不是完整的规格**：它没有战技点这一节，能量学（§3.3）只有一行。
> 缺失部分靠游戏机制攻略 + 角色文档反推， **并且要在代码里注明"这是反推的"**。

### 2.4 已核对的公式结论（别再重新推导）

- §2.2 伤害修饰区 = 增伤 `1+Σ` × 易伤 `1+Σ`（cap **3.5**）× 减伤 `Π(1-r)`（floor **0.01**）× 虚弱 `1-Σ`（floor **0.2**）
- §2.5 抗性区 = `1 - 抗性`，范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`； **负抗全效**（不是半效）
- §2.4 防御区 `(200+10L)/(def+200+10L)`，`def = 原始防御 × (1 - 减防% - 穿透%)`（ **加算**），上限 1、不为负
- §6.4 欢愉伤害 **吃双爆、不吃增伤** ⇒ `DamageType.ELATION(isCrittable=true, isBoostable=false)`
- §7.2 超击破不吃攻击力/常规增伤/双暴，吃等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤
- §7.1 **两套刻度不可混用**：80 级基础击破基数 3767（削韧单位 1）vs 超击破 376.7（削韧单位 10）
- §2.2 增伤/易伤区各自都是「伤害类型 + 攻击类型」 **加算进同一个区**（所以"战技/终结技增伤"灌 `BoostArea`，不新开区）
- §4 治疗 `= 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)`；护盾 `= 基础 × (1 + Σ护盾量提高)`

---

## 3. 踩坑记录（实测，别再踩）

### 4.1 怪物数据陷阱

| #  | 陷阱                                                       | 事实                                                                                                                                                                                                                 | 处理                                                                                                                                                                                                            |
|----|------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | **`hp_modify_ratio` 是幽灵字段**                           | tbgd 只有 `HPModifyRatio`，**没有** `HealthModifyRatio`；解析器猜错键名后留了个恒为 1 的字段。真实血量系数在 `health_modify_ratio`                                                                                   | bean 不读幽灵字段                                                                                                                                                                                               |
| 2  | **`attack_modify_ratio` 未导出**                           | tbgd 里 444/2649 个怪 ≠1，本数据整体缺这一列                                                                                                                                                                         | 补丁 `monster_attack_modify_ratio.json` + `Constant.normalizeMonsterConfigs` 合并。**正解在导出脚本**：`monster_config.json` 自带该列后删掉补丁与合并代码                                                       |
| 3  | **效果命中/抵抗是加值不是系数**                            | 组1·Lv90 给 0.32 / 0.1，冰锋模板 0.2 → **0.2 + 0.1 = 0.3**；相乘会得 0.02                                                                                                                                            | bean 字段名去掉 `Ratio` 后缀                                                                                                                                                                                    |
| 4  | **等级组的键名 ≠ bean 字段名**                             | JSON 是 `attack/defence/health/speed/stance`，bean 写成 `attackRatio` 之类 Gson **静默读成 0**                                                                                                                       | 一律 `@SerializedName`                                                                                                                                                                                          |
| 5  | **组号/等级来自关卡**                                      | 怪的 `hard_level_group` 通常是 1；真正决定难度的是 `StageConfig`                                                                                                                                                     | 调用方显式传参                                                                                                                                                                                                  |
| 6  | **精英组有两张表**                                         | 普通关卡 `EliteGroup`；无限波次用 `InfiniteEliteGroup`（绝境王虫 ×6.2）。用错血量差几倍；两者都来自**波组**                                                                                                          | 本数据暂无 `elite_group.json`，缺省 1                                                                                                                                                                           |
| 7  | **敌人技能数据源缺失**                                     | tbgd **没下发**怪物技能表（`skills.json` 只有角色）                                                                                                                                                                  | `enemy_skills.json` 自建隔离；目前只覆盖 5 只演示怪、**倍率是猜的**（每条标 `guessed=true`）                                                                                                                    |
| 8  | **`stage.json` 9 MB / 2.9 万条**                           | 塞进静态块等于每次 `Constant` 初始化多付 ~35 MB 堆 + 72 ms                                                                                                                                                           | 懒加载 `Constant.stages()`，**缺失时返回空表而不抛异常**                                                                                                                                                        |
| 9  | **`summon_id` 里的 `0` 是"没有召唤物"，不是怪物 0**        | 该列是列表（1957 只怪是 `[]`，692 只有内容），但 405301004 的列表恰好是 `[0]`                                                                                                                                        | 装载期（`normalizeMonsterConfigs`）只保留正数；**不要**改成"跳过查不到的 id"——那会让数据错误与"没有召唤物"长得一样。`SummonTest` 钉住                                                                           |
| 10 | **`damage_param` 是 0 基下标，文档占位符 `#N[i]` 是 1 基** | 141303 的 `param = [2, 2, 0.6, 0.3]`、行文说「`#1[i]`% 生命上限」，所以列号是 **0**。写成 `1` 会读到 `#2[i]` —— 那是【至暗之谜】的**充能数，每一级都是 2** —— 而它在 **Lv10 恰好等于 2.0**，于是伤害数看起来完全正确 | 读参数表时先数下标；这类错误只有"换个等级再看一次"才暴露（`SummonCommandTest.theMultiplierComesFromTheStatedRow` 就是靠三个等级的行号比值抓到的）。⚠ 同理：**占位符 `#N` 也是 1 基**，`param_list[N-1]` 才是它 |

> ⚠ `monster_attack_modify_ratio.json` 在 `.gitignore` 排除的 `data/` 里，但 **已 `git add -f`**
> （否则新克隆缺文件、`MonsterDataTest` 直接红）。

### 4.2 Gson 静默失败（本项目历史重灾区）

新增 bean 字段一律加 `@SerializedName` 并 **核对 JSON 键名**。`enemy_skills.json` 的
`hits` 字段语义是"目标数（0=全体）"、`monster_config` 的 `damage_resistance`
与 `debuff_resistance` 是 **两个不同的 map**（元素 / `STAT_*`）—— 都踩过。

### 4.3 工具链（PowerShell / Gradle）

- `git` 的进度输出走 stderr，PowerShell 会报成 exit code 1 —— **那不是失败**。
- `git push` 后若 `git fetch` 超时（网络抖动），用 GitHub API 核对：
  `Invoke-RestMethod https://api.github.com/repos/.../commits/main`。
- **Gradle 测试会缓存**：只改源码不碰测试文件时，`gradlew test` 可能直接 `UP-TO-DATE`
  而不真跑。要确认结果必须加 `--rerun-tasks`。
- 变异验证时尤其注意上一条 —— 否则会误判"测试没抓到"。
- ⚠ **比对 demo 要比全文，不能只 `Select-String "Battle over"`**：
  那句只证明 **胜负与轮数**没变。我这轮给遗器套装 301 加了规则文件，而 demo 队穿的 **正是 301** ——
  过滤成一行就"看起来没变"，实际伤害数值可能已经变了。正确做法是取工作树里改动前的提交
  （`git worktree add <临时目录> <commit>` + 把 `data/` 内容拷进去）跑两遍全文再 `Compare-Object`。
  实测：281 行里只有 3 行 `Resist {...}` 的顺序不同，而那是 `Main` 打印 `Set` 的 **既有**不稳定行为
  （同一份代码跑两次也会变，见 P10-0 的注），数值/HP/击杀逐行一致。
- **文件编码**：`Get-Content | Set-Content` 会把 UTF-8 文档按 GBK 往返一次， **整份糊成乱码**
  。中文文档一律走编辑器/fs 工具，不要用 shell 重写；
  真写坏了就 `git checkout -- <file>` 还原后逐条重打。
- ⚠ **同一条坑又踩了一次，而且这次是 Java 文件**：用
  `(Get-Content f -Raw) -replace 'A','B' | Set-Content f -NoNewline` 做一次全局改名，结果这份 UTF-8 的
  `RelicTriggerTableTest.java` 被 **按 GBK 写出**（`Set-Content` 在 Windows PowerShell 5.1 的默认编码不是 UTF-8），
  文件里带中文的地方整段变成非法字节序 —— 测试当场编译不过。 **恢复只要一步**（改动还没提交）：
  `git checkout -- <file>`，然后用 **fs 的 `edit` 工具**（`replace_all`）重做那次改名。
- ⚠ **"验证动作本身会悄悄失效"是本项目第三次同类教训**：
  ① 变异脚本按"必须恰好一处匹配"替换，源码里有两处时它 **拒绝改动而我把输出丢了** —— 于是"0 红"看起来像"变异存活"
  （实际是变异没生效）；② 用例的实例基数取了 100，而 `+X` 与 `+X%` 在基数 = 100 时 **数学上相同** —— 那个"抓住错误实现"的
  用例 **永远不可能失败**；③ 改了 `characters/1003.json` 的 note（引号未转义 → JSON 坏） **没跑校验就 commit+push**，
  把主分支弄红了一个提交。 **合并成一条可执行的规矩**： **任何改动 → 先在同一个命令里做机器校验（`json.load` / 编译 / 跑测试）→
  再 commit**； **演示闸门只在提交之后跑**（闸门里那句 `git checkout HEAD -- src/main/...` 会冲掉未提交的改动，
  因此丢掉过一次内容规则）；
  最后 **提交前再跑一次全套**确认仓库绿。三条都是"验证没发生，却长得像验证过了"。
  规矩： **任何文本改写都用 fs 工具**；shell 只用来跑命令、读输出、查状态。顺带确认了这台机器上
  `pwsh` 其实是 **Windows PowerShell 5.1**（`$PSVersionTable.PSVersion` = 5.1.26100），所以下面那条
  "一律用 `pwsh`"的告诫在 **改写文件**这件事上并不成立。
- ⚠ **第四次同族失误，这次的形态是"守卫本身没被守卫"**：我把"校验通过才提交"写成
  `python 校验 | Select-String …` 之后 `if ($LASTEXITCODE -eq 0) { commit }` —— 而 `$LASTEXITCODE`
  在 **管道之后反映的是管道最后一段**（这里是 `gradle`/`Select-String`）， **不是**那个校验脚本的；
  于是校验明明失败（`1313.json` 被写出非法 JSON），提交照样发生，主分支又红了一个提交。 **规矩**：校验结果要 **单独取值**（
  `$ok = python …`，或干脆在 python 里 `sys.exit(1)` 并与提交命令用 `;`/`if` 串联，
  不走管道），并且 **提交前那次全套测试的退出码也要单独判断**。同一天里 `1313.json` 被我连续写坏两次
  （切片替换切进转义序列、引号未转义），两次都靠 `git checkout HEAD~1 -- <file>` + 单独提交修复回来 ——
  第三次改用 **fs `edit` 工具（先 `read` 再改、引号只用「」）** 一次成功。这就是"工具选错，验证再快也白搭"。
- ⚠ **同一条坑的第五次：变异循环也会冲掉未提交的改动**：变异脚本每次跑完都用
  `git checkout -- src/main` 还原，而那一轮 **引擎改动还没提交** —— 于是第一轮变异跑完，`TriggerEvent`、
  `Battle.grantShield`、解释器的两处改动与 `1321.json` 的规则 **全部没了**（只剩一个未跟踪的测试文件）。 **规矩（与"
  闸门只在提交之后跑"合并成一条）**： **任何会 `git checkout` 的脚本只能在提交之后跑** ——
  闸门、变异循环、以及任何"改文件再还原"的验证脚本都算。动手顺序固定为： **改 → 校验（json/编译/全套）→ 提交 → 变异 → 闸门
  →（如有文档）文档提交 → push**。
  附带的两个操作细节（同轮实测）：
  ① 源码文件在工作树里是 **CRLF**，脚本锚点必须先归一化行尾（第一次重放脚本报了"0 匹配"就是这个原因，不是内容不对）；
  ② 重放这种"被冲掉的多处改动"时， **把补丁写成 `.py` 文件再跑**（`write` 工具落盘），不要用 shell 里的多行字符串 ——
  引号与转义在这个会话里已经三次把文件或脚本写坏。
- ⚠ **往里插东西时，"第一个 `{`"不是锚点**：角色文件有两种形状， **对象形式**（`{resources, rules}`）
  里 `resources` 的声明对象与 `rules` 的元素 **缩进完全一样**，所以用一个按缩进找"第一个规则对象"的探针会先命中
  **resources** ——
  那条 `RAISE_SKILL_LEVEL` 规则被塞进了资源数组，而 **JSON 依然合法**（两个对象都合法），于是解析器读到 `resources` 有 2 项、
  `rules` 里没有那条规则， **332 条测试红**。 **规矩**：定位"某个数组的第一个元素"时，锚点必须包含 **数组名**（`"rules": [`），
  插完要 **核对数量**（`resources=1 / rules=6`）而不只是"JSON 能解析"。
- ⚠ **同一个"裸引号"坑第三次**：脚本里写 `\"…\"` 想得到转义引号，但 Python 会把 `\"` 变成裸 `"`，
  于是插进 JSON 的英文句子把自己的字符串提前结束。 **规矩**： **插入的任何文本（中英文都算）里的引号一律用 `「」`**，
  并在写完的同一个命令里 `json.load` 验证 —— 这一条已经写在上面，但这次仍然踩了，所以补一句 **操作**层面的做法：
  补丁脚本里 **不要出现 `\"`**，需要引号就用 `chr(0x300c)/chr(0x300d)`。- ⚠ **裸引号第四次**：脚本里写 `\"…\"`
  想得到转义引号，Python 把 `\"` 变成裸 `"`，
  于是插进 JSON 的中文句子把自己的字符串提前结束。这一次 **不再手写修复清单**（前三次都是），改成 **机械化**做法：
  读文件 → 找到每个 `"note": "` 到该行最后一个引号之间的值 → 把其中所有 `"` 交替换成 `「`/`」` → 写回 → `json.load` 验证。
  **规矩**：插入文本里 **不出现 ASCII 双引号**；真出事了就用这个机械化修复，而不是再列一遍短语。
- ⚠ **给某个角色写内容文件，会让"没有规则文件的角色"这套测试夹具一起变红**：`TriggerTableTest`
  与 `RelicTriggerTableTest` 都把 `1003` 当"未注册角色"这个 **对照**用，而 1003 姬子当天有了自己的文件 →
  四条与本轮改动 **毫无关系**的断言红了（"没有文件 → 空表"、`BATTLE_START` 计数 1→2…）。
  修法 **不是**随手换一个 cid（下次再写一个角色还会踩），而是把"**某个**没有文件的真实角色"变成一处查询
  （`TestCharacters.withoutTriggerFile()`：取 id 最小、且不是特殊供能角色的那个），并在需要
  "会打全体敌人的队友"这种 **技能形状**要求的地方单独用命名常量。教训： **夹具若把内容的偶然状态写进常量，
  内容一动它就断**；能让它自己回答的问题就不要用常量回答。
- `pwsh` 渲染中文正常；Windows PowerShell 5.1 会糊成乱码， **一律用 `pwsh`**。
- PowerShell 的 `>` 重定向会写成 UTF-16（会把 Java 文件写坏）；用
  `[System.IO.File]::WriteAllText(..., UTF8Encoding($false))` 或 fs 工具。
- ⚠ **演示 diff 闸门的"还原"步骤会抹掉未提交的改动 —— 这条同一轮里发生了两次，所以它必须是硬顺序，不是记性**
  ： **跑闸门之前先 `git status` 确认工作区干净**（或者先 `git stash`），否则 `git checkout HEAD~1 -- src/main/...`
  取旧版本、`git checkout HEAD -- src/main/...` 还原时会把 **未提交的 src/main 改动一并丢掉**（未跟踪的新文件能幸存，
  这正是两次都能靠重放补丁找回的原因）。⚠ 更隐蔽的一种：工作区与 HEAD 相同时跑闸门 **比的是自己**，看着像通过 ——
  真正的对照必须让"before"来自 `HEAD~1`。
- ⚠ **`git checkout <旧提交> -- <路径>` 只会"更新"，不会"删除"**：
  这一轮新增了 `ShieldBuff.java`，取 `HEAD~1` 的 `src/main/java` 之后它 **留在原地**，于是"before"那次编译
  直接失败（新的 buff 调 `CanHit.installShield`，而那个方法来自本轮）—— 输出的前几行成了编译错误， **闸门比的是什么都没测**
  （而且很容易被误读成"after 侧报错"，因为 `Compare-Object` 的 `<=` 是 reference/`=>`
  是 difference）。规矩：跑闸门之前，把 **旧版本没有、而工作区有**的文件也删掉 ——
  `git ls-tree -r --name-only HEAD~1 -- <路径>` 与 `git ls-files <路径>` 取差集，逐个 `Remove-Item`，
  跑完再 `git checkout HEAD -- <路径>`（这些文件在 HEAD 里是 **已跟踪**的，所以一次 checkout 就能连同内容一起还原）。
  ⚠ 顺带一条同族的： **"闸门跑起来了"不等于"闸门比对了"** —— 先看两边输出的大小/首行，确认没有编译错误，
  再看 diff；这次两侧文件大小完全相同（17556 / 3233）且只有 Set 顺序行不同，才是真的"输出不变"。
- ⚠ **提交信息里的数字要"跑完再读"，不能凭记忆写**：全量测试条数只有
  `gradlew test` 的输出说了算，而提交信息往往和跑测试写在 **同一条命令**里 —— 于是数字成了猜测
  （一次写 1019/实际 1017，一次写 1017/实际 1018）。规矩： **先跑、读到 "tests=" 那行、再写信息**，
  或者干脆不写数字、只写"全量绿"。"
  闸门要回到旧版本跑对照，用的是 `git checkout <旧提交> -- src/main/java src/main/resources`，
  跑完再用 `git checkout HEAD -- 同样的路径` 还原 —— 如果改动 **还没提交**，第二次 checkout 恢复的是 **旧版本**
  ，本轮工作当场消失（这次丢了 5 个文件的改动）。补救：Java 侧用锚点脚本重放（每个锚点
  断言 `count == 1` 才动笔），然后 **拿丢失前的 SHA-256 对齐**（`TriggerInterpreter`/`SkillExecutor`/
  `Battle` 三个文件逐字节吻合 = 之前那轮变体验证依然有效）。规矩： **先 commit，再跑闸门**。

### 4.4 数据探测（别用 `ConvertFrom-Json` 读 1.5 MB 的 `skills.json`）

它会因编码/体积失败并把整个文件吐到控制台。用正则直接数：

```powershell
$t = [System.IO.File]::ReadAllText('src/main/resources/data/skills.json')
([regex]::Matches($t, '"sp_need":\s*null')).Count
```

> ⚠ 正则里的 `\s*(?!null)` 这种 **负向先行断言会因回溯而失效**（我先写错了一版，
> 数出"638 条全部非 null"）。要数"非 null"就直接匹配取值：
> `'"sp_need":\s*[1-9]'`。

---

## 4. A 层：角色数据化的基础设施 ✅（全部完成）

> **为什么这层曾是最优先**：93 个角色的天赋/行迹/星魂/秘技/追加攻击原本 **全是空的**。
> 引擎能给角色的只有普攻/战技/终结技/能量/战技点 —— 而这一层是
> "让角色机制变成数据"的 **唯一通路**。这层做完了，才有资格谈"像 HSR"。
>
> 现在通路是通的：`Battle` 发事件 → 角色自带的 JSON 表匹配条件 → 解释器执行引擎已有能力。
> **接下来缺的不是引擎，是内容**（93 个角色里只有 4 个填了表）。

**本层顺序**：`P8-6`（事件）→ `P8-7`（触发器表）→ `P8-8`（层数资源）→ `P8-3`（天赋 + 追加攻击）。
`P8-3` 原本"建议并进 P8-7 做"，实际做下来确认它对 `P8-7` 的依赖是 **真的**
（只需要补一个 `DAMAGE` op），而它自己那个"数据前置"是 **假的**（见该任务）。

---

## 5. 待办 B 层：敌人（只有 5 只怪有技能，其余全是平 A）

> 没有敌人技能就没有难度曲线。`enemy_skills.json` 目前是自建的、倍率是猜的，
> 找到源数据后只换加载处。

### P9-1 敌人技能数据（`enemy_skills.json` 扩充）

- **目标**：敌人技能表从"5 只演示怪"扩到可用规模；找到源数据后只替换 `Constant` 加载处。
- **涉及文件**：`src/main/resources/data/enemy_skills.json`、`beans/EnemySkillData.java`、`Constant.java`、
  `test/content/enemies/EnemySkillTest.java`
- **怎么做**：
    1. bean 字段：
       `name / attackType / effect / element / multiplier / stance / hits / aiWeight / condition / summonId / debuffKey / debuffChance`
       （`hits` = **目标数**，0=全体；`condition` = `null` / `"hp<0.5"` / `"firstTurn"`）
    2. **数据源缺失是已知事实**（见 §4 陷阱 7）：先自建，每条标 `guessed=true`。
    3. `Constant.ENEMY_SKILLS` 的 key 用 monster **template_id**，`EnemyFactory` 按 `cfg.templateId()` 查。
- **验收**：`EnemySkillTest`：条目数、倍率、削韧值与表一致；无条目的怪走兜底普攻。

### P9-2 `EnemySkill` 全效果 🚧 形状分派已落地

- **目标**：`EnemySkill` 从 bean 构造，按 effect 复用伤害分派（AOE/BLAST/SINGLE），不再硬编码 ×1.0 单目标。
- **涉及文件**：`models/EnemySkill.java`、`utils/EnemyFactory.java`、新建 `test/EnemySkillFullTest.java`
- **怎么做**：
    1. `EnemySkill` 持有 bean，透传 `getElement()/getEffect()/getStance()`。
    2. 给 `SkillExecutor` 加"**无 SkillData 入口**"（敌人技能不占用 `skills.json`）：
       `executeSimple(battle, element, effect, stance, multiplier, user, targets)`，
       内部复用现有 switch；削韧挂点原样生效。
    3. `EnemyFactory.create` 挂上 `Constant.ENEMY_SKILLS.get(templateId)` 的全部条目；无表 → 兜底普攻（P5-3 保留）。
- **验收**：`EnemySkillFullTest`：冰锋战技打 3 人 → 每敌 `attack × 倍率 × 防区`；按 bean 削韧。
- **依赖**：P9-1、P5-3、P1-8

**实际落地（形状分派，）**：

数据侧新增 `effect` 字段（`SingleAttack` / `AoEAttack` / `Blast`，省略 = `SingleAttack`），
`EnemySkill` 按形状决定 **打到谁**：`AoEAttack` 打我方全体、`Blast` 打主目标及左右相邻
（ **到边界就截断，不回绕**），段数落在 **每一个**被打到的目标上。5 条现有条目显式标 `SingleAttack`，
所以行为与之前逐位相同。`EnemySkillFullTest` 6 条覆盖：默认单目标 / AOE 打全体 / Blast 打相邻 / **边界不回绕** /
段数落在每个目标上 / 死者不再挨打。

**变异验证**：把 Blast 的相邻改成取模回绕 → `blastOnTheEdgeCharacterDoesNotWrapAround` 红 ❌。

> ⚠ **一处"两个来源用同一字段名"的陷阱（已写进数据文件的注释）**：
> `enemy_skills.json` 的 `hits` 是 **段数**（8013010「连续踏击」= 同一目标 2 段），
> 而 P9-1 的计划里同名字段是 **目标数（0=全体）**。两者 **不可**合并 ——
> 真表落地时若把 `hits` 改指目标数，现有每只怪的行为都会变而 **没有任何测试会红**。
> 所以目标数改用 `effect` 表达（加法式、默认值等于旧行为），段数语义由
> `EnemySkillFullTest` 从出货侧钉住。

**仍未做**：

1. **敌人技能仍不发事件**。`EnemySkill.execute` 走的是 `Battle.applyDamage`，不发
   `SkillCastEvent`（F-5 与 §3 的"🚧 部分完成"都记着）。⚠ 但 **不能简单补一个发射**：
   条件 DSL 只有 `actor ==/!= self`、 **没有阵营变量**，所以一旦让敌人的施放也发
   `SKILL_CAST`，「当我方施放技能时」这类规则会连带在敌人身上触发 —— 需要先给 DSL 一个
   阵营条件，或改用"以我为承受者"的事件（`TAKING_HIT` 已经在做这件事）。
2. `EnemySkill` **仍不持有 bean**、`getData()` 仍为 `null`（F-5）；目标是复用
   `SkillExecutor` 的伤害分派而不是自成一摊，等上面那条一起做。
3. 敌人技能 **不削韧**：角色没有韧性条，这一条对"敌人打我们"本来就不适用；
   真正的削韧方向是反过来（我们打敌人的韧性，已经在用）。

### P9-3 敌方 AI 技能选择器

- **目标**：敌人回合 **先选技能再选目标**（P5-4 已有目标选择，缺技能选择）。
- **涉及文件**：新建 `models/ai/SkillSelector.java`、`Main.java`、新建 `test/SkillSelectorTest.java`
- **怎么做**：纯静态 `next(enemy, battle, rng)`：过滤 `conditionOk` → 按 `aiWeight` 加权随机。
- **验收**：`SkillSelectorTest`：权重 1.0/1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%）；
  `condition "hp<0.5"` 满血时选不到。
- **依赖**：P9-2、P5-5

### P9-5 Boss 机制（phase 换招 / 受击反击 / 控制免疫）

> **⚠ 第 3 条（控制免疫）是一条假缺口** —— 它 **已经实现并且已经有测试**，不需要再做。
> 见下面的"核对结果"。本条只保留 **真正还没做**的两项。

- **涉及文件**：`models/Enemy.java`、新建 `models/buffs/CounterMechanic.java`、`Main.java`、新建
  `test/BossMechanicTest.java`
- **怎么做**：
    1. ~~`Enemy.phase`：HP 阈值切技能列表~~ —— ✅ **已落地**（`Enemy.setPhaseSkill` / `activeSkill`，
       `EnemyPhaseTest` 5 条）。 **注意实现方式**：`activeSkill()` **每次读当前血量**，
       所以 **不需要任何状态机** —— 没有要翻的标志位、没有要调度的转换。
       这正是为了绕开第 4 条那个陷阱（"把血条锁在 1"）。⚠ 它 **只覆盖阈值换招**； **多血条**仍需要锁血 + 显式重置，那一项
       **未做**，单独登记。
    2. 受击反击：用 `DamageEvent` → 追加一段 `DamageType.ADDITIONAL` + `notCountsAsAttack()`， **反击目标 = 该段的
       `damage.getAttacker()`**（"施放技能的个体"，不一定等于角色本人）。
       > ⚠ **不是一行代码**：`DamageEvent.onDamage` 是在结算 **之前**往这一击里塞乘区用的，
       > 反击要的是"这一击结算 **之后**"的钩子 —— 那是 `HpLossEvent.onHpLoss(..., source, amount)`。
       > 并且 **必须自带递归护栏**：反击本身造成掉血 → 若对方也带反击就会乒乓，
       > 而这条路径 **不经过** `Battle.fireTriggers`，所以 `MAX_TRIGGER_DEPTH` **拦不住它**
       > （触发器版的反击能拦住，是因为它走 `applyDamage` → `fireTriggers`）。
    3. ~~控制免疫~~ —— ✅ **已实现，见下。**
    4. ⚠ **阶段推进绝不能用"0 血不死"实现**：`takeDamage` 在 HP≤0 时立刻 `death = true`。
       要锁血就用 `setInvulnerable(true)` 再 **显式重置 HP**；否则要么阶段被跳过，要么 **鞭尸**。
       多血条同样按"打空一段 → invulnerable → 重置 HP"。
- **验收**：`BossMechanicTest`：HP 降到 50% 以下换招；受击反击段 `getCountsAsAttack() == false`。
- **依赖**：P9-2、P1-7、P6-1

**核对结果：控制免疫已经是 P6-1 的一部分，不需要新代码。**

`Battle.hitChance(caster, target, baseChance, specificResistKey)` 里已经有这段：

```
chance = base × (1 + 效果命中) × (1 - 效果抵抗) × (1 - 具体 debuff 抵抗)
```

`specificResistKey` 查的就是 `Enemy.debuffResist`，所以冰锋的 `{"STAT_CTRL_Frozen": 1}`
让 `(1 - 1) = 0` → **完全免疫**，与原文描述逐字一致。 **并且已经被测试钉住**
（`HitResistTest`）：`冰锋.getDebuffResist().get("STAT_CTRL_Frozen") == 1.0`、
`hitChance(..., "STAT_CTRL_Frozen") == 0`、别的 key 不免疫、
`tryApplyDebuff(..., "STAT_CTRL_Frozen")` **端到端返回 false**。

> 所以计划里那句"`Enemy.isImmuneTo(resistKey)`"是 **不需要的方法名** —— 免疫不需要一个新方法，
> 它就是"具体抵抗 = 1"这一种情形，走同一条概率公式即可。 **加一个 `isImmuneTo` 只会是第二套判据。**
> （这条与"负数抵抗会被当成 0 而不是增伤"是同一处口径，见 `engine.md` §5。）

---

## 6. 待办 C 层：机制补完（全是"填口子"，无新架构）

> 原则一条： **任何任务做到一半发现需要新架构，说明它跑偏了，回退并拆小。**

### P10-4 速度与行动条操纵 🚧 只差"拉条"那一半

- **现状（已核对，计划那句"公开 API 还没上移"过时了）**：`Battle.delayMovePercent` **已经是 public**
  （`Battle.java:1019`），而且 P4-4 的击破推条 **已经在调它**（`:971`）—— 没有重复代码要删。
  真正缺的只有"拉条"：`Battle` 上没有 `advanceMovePercent`，现有入口是
  `Battle.advanceRequest(canHit, rate)` → `Queue.advanceActionByPercent`（形状不同：它是
  "登记一个请求、在 `processRequests` 统一结算"）。
- **怎么做**：加 `advanceMovePercent`，或在文档里明确"拉条走 `advanceRequest`"—— 二选一，别两个都留。
- **验收**：`SpeedBuffTest`：speed 100 减速 30% → 下次行动间隔 ≈ 142.857；拉条 50% → 提前半圈。
  ⚠ 减速那一半 **已经被 `ControlTest` 侧面覆盖**（禁锢/纠缠减速后的行动值变化）。
- **依赖**：P10-3 ✅、P7-1

### P10-5 终结技插入

- **怎么做**：确认规则（不消耗行动条、不占回合、先清零再回自身 5）；`castUltra` 后 **不触发** `afterMove`、
  `processRequests` **不产生新回合**；demo 加"回合开始前可放大招"的输入位。
- **验收**：`UltraInsertTest`：满能量任意时点 `castUltra` true；行动条前后一致；能量 = 5 × (1+回能率)。
- **依赖**：P3-2、P7-2

### P10-6 Debuff 基础概率数据化 ✅ 前半（读得出几率；"哪个 debuff"仍缺数据）

> ⚠ **计划里的"`param_list` 第 3 项为几率"是错的，四个样本全错**，而且错得很危险：
> 姬子 1003/7 的 `param_list = [1, 0.1, 2, 15]`，index 3 是 **15**（秒数）——
> 读成几率就是 1500%，被 `hitChance` 的 `clamp(0,1)` 夹成 1.0，看起来"必定命中"，
> **一个不会报错的错误答案**。

- **正解：描述文本自己说了是哪个下标。** 紧挨着 **基础概率 / 固定概率** 的那个 `#N[...]` 占位符就是它，
  `param_list[N-1]`。落地为 `SkillData.debuffChance()`（`SkillData` 现在把 `skill_introduction.chinese`
  一起带上 —— 数据里有些数字 **只写在散文里**，生成器本来就靠这个：`skill_effects.json` 的
  `source: "SkillDesc 占位符"`）。
- **标定（5 个样本，全部来自真实数据）**：

  | 技能                         | 原文                                   | 下标  | 值      |
      |------------------------------|----------------------------------------|-------|---------|
  | 1003/7 姬子 不完全燃烧       | `有#1%的<u>基础概率</u>`               | 0     | 1.0     |
  | 1004/7 瓦尔特 画地为牢       | `有#1%的<u>基础概率</u>`               | 0     | 1.0     |
  | 1108/7 桑博 你最闪亮         | `有#2%<u>固定概率</u>`（**没有"的"**） | **1** | 1.0     |
  | 1006/4 银狼 等待程序响应…    | `有#4%的<u>基础概率</u>`               | **3** | **0.6** |
  | 1307/4 黑天鹅 无端命运的机杼 | `有#2%的<u>基础概率</u>`               | **1** | **0.5** |

  0.6 与 0.5 是关键：返回常数、或读错下标都 **不可能**得到它们。
- **不变量（不止 5 个样本）**：全部 **28 条 `Impair`** 技能要么读得出一个 `(0,1]` 的几率，要么
  描述里 **根本没有概率字样**（14 条，如波提欧 1315/2 的【绝命对峙】—— 那是"无条件生效"，
  返回 `null` 是 **正确答案**，不是失败）。
- **顺带发现两条**：
    1. **基础概率 ≠ 固定概率**：前者吃施法者效果命中、被目标抵抗削减（就是 `hitChance` 算的东西），
       后者原样生效。表现在两者都返回（都叫"技能写的几率"）， **但把固定概率喂进 `hitChance` 会放大它** ——
       留给真正做 Impair 分派的人处理。
    2. **禁锢的真实数值**：瓦尔特 1004/7 写 `行动延后#2%，速度降低#3%`，`param_list = [1, 0.2, 0.1, 15, 0.5]`
       → **延后 20% / 减速 10%**。P10-2 的 `IMPRISON_EXTRA_DELAY = 0.2` 蒙对了，
       `IMPRISONED.slowPercent` 从猜的 0.2 **更正为 0.1**（`ENTANGLED` 的 0.2 仍无出处）。
- **验收**：`DebuffChanceDataTest` 4 条（5 个标定样本 / 计划那个 index 3 的数值确实不是几率 /
  文本没写概率时必须返回 `null` 而不是编造 1.0 / 28 条 `Impair` 的不变量）。
  全套 **65 suites / 612 tests / 0 failures**。
- **变异验证（2 处）**：
    1. 改成固定读 index 0（计划那个 bug 的近亲）→ 标定用例在桑博处红，且 **不变量用例点名 4 个技能**
       （1108/7、1305/7、1225/7、1410/7 会读出 10~20 的"几率"）；
    2. 找不到时 `return 1.0`（编造）→ `aSkillWhoseTextStatesNoChanceReportsNoChance` 红。
- 🚧 **仍缺（决定"还能不能往下做"的那一块）**：`SkillExecutor` 的 `IMPAIR` 分支 **没做**，
  因为"这个技能施加的是 **哪个** debuff、数值多少"在数据里没有位置 ——
  `skill_effects.json` 只有 `Restore` / `Defence` 两类（40 条）。
  要做就得让 `generate_data.py` 也导出 Impair 的 `(属性, 数值, 持续)`，属数据补全。
- **依赖**：P6-1、P8-2 ✅（P10-2 ✅）

### P10-7 「按被治疗者的状态，加治疗者的治疗量」= 数据 ✅ 已落地

> 读者：**1105 娜塔莎 天赋 生机焕发** (110504, Enhance) ——
> 「为当前生命值百分比小于等于 30% 的我方目标提供治疗时，娜塔莎的治疗量提高 50%，
> **该效果对持续治疗效果也会生效**」，`param` Lv10 = `[0.3, 0.5]`。

**为什么不能写成属性或普通规则（三条实测结论，正是这条 op 存在的理由）**：

1. **它不是治疗者的属性**：`Battle.calculateHeal` 读的是治疗者自己的 `OUTGOING_HEALING_BOOST`，
   **它看不见被治疗的是谁** ⇒ 写成属性会把 +50% 平等加到「治疗满血队友」上，**是错数字**。
2. **`target_when` 不能改自己的属性**：它决定一条修正**落到哪些单位身上**
   （`TriggerEffects.modifyAttr` 改的是**被作用单位**的 `DoubleValue`），
   没法把「我在治疗谁」当作抬高**自己**属性的条件。
3. ⚠ **没有「治疗前」事件**：`TriggerEvent` 里与治疗有关的只有 `HEALED`，
   而它由 `Battle.heal` 在**算完之后**发出 ⇒ 规则**没有时机**在这笔治疗落地前抬治疗量。

⇒ 唯一同时握着治疗者、被治疗者与 `Battle` 的地方是 `calculateHeal`。

**落地形状（`REGISTER_HEAL_BONUS`）**：

- `TriggerOperator.REGISTER_HEAL_BONUS`（wired）＋ `TriggerInterpreter.registerHealBonus`：
  在 `BATTLE_START` 把条件登记到**规则主人**身上。
- ⚠ **条件复用 `target_when`，引擎侧一行解析都没加**：`EffectSpec` **根本没有** effect 级的 `when`，
  而 `target_when` 在 `TriggerTable.compile` 里**已经**被解析成 `Condition` 并交给解释器
  （`ctx.targetFilter()`），含义也一致（「这条效果是关于哪些单位的」）—— 只是这里的单位是
  **将来的被治疗者**，且**求值时机推迟到治疗那一刻**。
  （写这条待办时我以为要给 `CompiledRule` 加一份 effect 条件，**那是错的**：没有 effect 级 `when` 可加。）
- `CanHit.ConditionalHealBonus(condition, percent)` 列表 ＋ `addConditionalHealBonus`。
- `Battle.calculateHeal` 求出乘区：用**治疗者**当 `owner`/`actor`、**被治疗者**当 `target` 造
  `TriggerContext`，逐条 `test`，把过的加起来乘 `(1 + Σ)`。所以 `target_hp_percent` 读的正是被治疗者。
- ⚠ **校验器拒绝**没有 `target_when` 的 `REGISTER_HEAL_BONUS`：无条件版本会抬高每一笔治疗。
- ✅ 「**对持续治疗也生效**」**不需要额外写**：续疗走 `Battle.tickRegens` → `heal(regen.getSource(), …)`，
  **带的是原始施加者** ⇒ 同一条加成天然对每一跳生效（有用例钉住，不是假设）。

**⚠ 真正的坑在测试里，不在引擎里**：`Battle.heal` 返回的是**实际落地**的量，
而它被 `CanHit.heal` **按受疗者缺失的血量钳住**。80% 血的队友只缺 ~316，
于是「+50% 的 500」与「825」**都会落地成 316**，**测不出来** ⇒
`calculateHeal` 才是这条天赋真正缩放的那个量，用例必须断言它。
（第一版用例断言的正是落地量，因此**连"无条件加成"这个错误实现都能通过**。）

**变异验证（3 种错法各红一片，用例真的能区分）**：
`percent: 0` ⇒ 3 条全红；条件改**恒真**（= 无条件加成）⇒ **健康那一半**红；
条件改**恒假** ⇒ 低血那半与续疗红。

**⚠ 复核时发现的坑（写给下一个改 1105 的人）**：
`"target_hp_percent <= 0.3"` 在 `1105.json` 里**出现两次** —— 她的**星魂 2**（终结技对 ≤30% 血队友
附加持续治疗）用的是**同一条件**。任何按**首次匹配**改文件的脚本都会改到那条，
于是「变异验证」会在**没改到天赋**的情况下"通过"。锚点必须带 `"op": "REGISTER_HEAL_BONUS"`。

**验收**：`NatashaTalentTest` 3 条（阈值内/外对比、30% 含等号、续疗也生效）。
---

## 7. 待办 D 层：演示与收尾

### P11-2 真实内容演示（冰锋战）

- **目标**：冰锋（弱火/雷）+ 真角色演示完整循环：普攻→削韧→击破→推条→跳回合→DOT→回能→大招→WIN。
- **怎么做**：`CharacterFactory.create(1109, 80)`（虎克·火）+ `1204`（景元·雷）对
  `EnemyFactory.create(1002011, 29)`；循环 `stepForward → beforeMove → 出手 → afterMove`；
  每步 `printBattle()`：HP + 韧性/`[BROKEN]`/DOT 标记 + **战技点** + `castUltra` 提示。
- **验收**：一遍跑通全链路。

### P11-3 测试总盘点 + 基准 + 零警告

- **怎么做**：对照 §3 的待办表确认每项测试类都在；`Benchmark.java`（固定种子，10 万轮计时）；
  `.\gradlew.bat build` **无 warning**。
- **验收**：全绿 + Benchmark 输出。

---

## 8. 远期（只记规格，不排实现）

| 项目                                   | 规格锚点                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **忆灵（角色专属召唤）**               | ✅ **已做**：面板**推导**（不是快照）§24.5 / 在场可问可指 §24.6 / 规则召上场 §24.7 / **能打且伤害按自己的属性缩放** §24.8。⚠ 与本节原规格的两处差异：面板读的是召唤者**已解析**的面板（**含**召唤时在场的临时增益，原规格写的是"快照本体、不含临时增益"，文档只写「等同于…的生命上限」，没有说要不要含增益 → 已登记）；伤害类型仍走普通元素而不是 `MEMORY`。仍缺：连携攻击（一次行动中的两次独立攻击）、忆灵技 2~5、削韧、仇恨、"指令忆灵行动" |
| **欢愉体系**                           | 伤害公式 `基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))`，**禁攻击力/属性增伤**；阿哈速度 = `80 + 最快/5 + 第二/10 + 第三/20 + 最慢/50`；`elation_basic_level_damage.json`（101 条）**从未被加载**                                                                                                                                                                                                                          |
| **模拟宇宙**                           | 祝福池化 = 3 选 1                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Boss 专属机制库**                    | P9-5 只做通用换招/反击/免疫；个别 Boss 的专属机制每个 = 一个 `BossMechanic` 子类（需登记逃生舱）                                                                                                                                                                                                                                                                                                                                                |
| **界面**                               | `Battle.getQueueSnapshot / printHp` 是现有 CLI 出口；UI 层接 `Battle` 事件流即可                                                                                                                                                                                                                                                                                                                                                                |
| **战斗拆分：组装 / 主战斗 / 引擎原语** | 见 §9.1，**只有方向与实测基线，未排实现**                                                                                                                                                                                                                                                                                                                                                                                                       |

### 10.1 战斗拆分（组装 / 主战斗 / 引擎原语）—— 方向已记，未排实现

> 目标（用户提出）：把 **组装战斗（聚合所有机制 trigger） **与**主战斗（专门触发 trigger） **分开，
> 让**触发成为战斗的主要站点**。⚠ 下面是 **实测基线 + 分层想法**， **没有动任何代码**。

**实测基线（2026-10，先量再谈）：**

| 项             | 实测                                                                                                                                                                                               |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Battle.java`  | **3766 行**，172 个方法                                                                                                                                                                            |
| 其中**组装**   | **只有 9 个方法**：`startBattle` / `registerPartyResources` / `attachBattleSkills` / `applyTechniqueStates` / `registerWarehouseListener` / `beginWave` / `noteWaveMonster` / `forgetWaveMonsters` |
| 其中**运行时** | 20 个（`beforeMove` / `afterMove` / `applyDamage` / `reduceToughness` / `heal` / `grantShield` / …）                                                                                               |
| **触发点**     | `Battle` 内 **26 处**，发出 **25 个事件**；另有 8 个事件由 `SkillExecutor` / `EnemySkill` / `WaveManager` / `TriggerInterpreter` 发出 ⇒ **33 个事件**                                              |
| 事件总线入口   | `fireTriggers` **8 个重载**；**10 个类**在调用它                                                                                                                                                   |

**⚠ 关键结论一：`Battle` 的大小不在"组装"里。** 9 个组装方法，占 3766 行的一小部分。
把组装抽出来 **不会让 Battle 变小**，只是让职责更清楚。

**⚠ 关键结论二：触发现在是"播报"，不是"决策"。**
`applyDamage` 内层 **83 行**：自己算完伤害、自己施加， **然后**才发
`DAMAGE_SETTLED` / `HP_LOST` / `TAKING_HIT` / `KILL` / `LETHAL_DAMAGE`。
`reduceToughness` 与 `grantShield` **一个事件都不发**（`BREAK` 由更内层发）。
⇒ 规则 **能改结果**，但只能通过 `Damage.addBoost / addVulnerable / addReduction / addFlat / …`
（`Damage.java` 900 行） **注入修正值**， **不能接管流程**。

**⚠ 关键结论三（硬约束）："主战斗只触发"做不到全量。**
若要求 **公式本身**也走触发，就要在规则 DSL 里表达乘区公式 ——
那等于在 DSL 里重写 `Damage.java`，需要一个 **公式解释器**。 **不建议**。

**建议的三层（待定，未实现）：**

| 层              | 职责                                                                                   | 归属                           |
|-----------------|----------------------------------------------------------------------------------------|--------------------------------|
| **A. 组装**     | 花名册、面板推导、**聚合各来源的触发规则**（角色表 + 光锥 + 遗器 + 仓库 → 一张有效表） | 从 `Battle` 抽出               |
| **B. 主战斗**   | 回合循环 + **事件总线**：每个时刻**先问规则、再定数字**                                | `Battle` 瘦身                  |
| **C. 引擎原语** | 乘区 / 削韧 / 护盾的**算术**                                                           | 留在 `Damage` / `BattleDamage` |

**两个待定的轴（用户需先定）：**
（a）"聚合"指 **抽出组装流程**，还是 **在组装期把各来源规则合成一张有效表**，还是两者；
（b）"主战斗专门触发"到 **把 26 个触发点集中到一个 `fire(event, ctx)` 总线**为止，
还是 **再让每个原语在生效前经过总线**（做法要给乘区补事件）。

**⚠ 落地纪律**：`Battle` 3766 行、 **2607 条测试**依赖它。 **不要一次全做** ——
先选一个可验证的小切口（例如"抽出组装 + 集中触发点"），跑完全量测试证明零回归，再谈下一步。

---

## 9. 为什么这样排序

1. **A 层（P8-6/7/8 + P8-3）最优先** —— 它是"93 个角色机制"的 **总开关**。在此之前做任何角色内容
   都是往 `Battle` 里塞 `switch (cid)`，做完还要返工。 **已完成** ✅
2. **B 层（P9）紧随** —— 现在只有 5 只怪有技能、其余全平 A，没有难度曲线。
   `enemy_skills.json` 的自建表 **隔离了数据源缺失这个外部风险**（找到源数据只换加载处）。
3. **C 层（P10）是"填口子"** —— 全部依赖已完成的机制， **不该出现新架构**。
   若某任务需要新架构，回退拆小。
4. **D 层收尾** —— demo 与基准，最后做。

**每步保持：可编译 → `.\gradlew.bat test` 全绿 → 提交。**

---

## 10. 文档分工（改文档前先读这一节）

| 文件                                                | 放什么                                                                                                                                                     | 不放什么                   |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| **`src/main/java`（唯一权威）**                     | **引擎当前行为**。`engine.md` 已删（见下），所以"现在是什么"要**读代码**，或读 `src/main/resources/characters/<cid>.json` 里每条规则自带的 `source`/`note` | 设计意图、进度、历史       |
| `ROADMAP.md`                                        | **本文件**：设计原则（§1 三条红线 ＋ 三分法）、待办（§6–§8）、踩坑记录（§4）                                                                               | 已完成的逐轮叙事、缺口清单 |
| `HSR.md`                                            | 游戏机制的**权威公式**（正典副本在仓库外）                                                                                                                 | 本实现的差异               |
| `E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md` | **角色反推的原始依据**（天赋/行迹/星魂/秘技原文）                                                                                                          | 本实现的差异               |
| `E:\turnbasedgamedata\ExcelOutput\*.json`           | **数值的原始依据**（`AvatarSkillTreeConfig` 行迹节点、`AvatarRankConfig` 星魂等）                                                                          | 语义解释                   |

> ⚠⚠ ** 又删掉五个文档文件**，本节表格此前把它们当作存在的东西介绍，是 **坏指针**：
> `engine.md` / `GAPS.md` / `HANDOFF.md` / `REFACTOR.md` / `EXPRESSION.md`（连同 `tools/` 整个目录）。
> 现在全库只剩 `ROADMAP.md`、`HSR.md`、`README.md` 三份文档。
>
> **它们的内容去哪了**：
> * `engine.md` 的"引擎当前行为" → **代码本身**。⚠ 本文件里还有 **54 处**称"见 `engine.md` §X"，
>   那些 **章节号已无处可查**；遇到时 **按名字去代码里找**（例如"事件体系"查 `enums/TriggerEvent.java`，
>   "触发器表"查 `data/TriggerTables.java` + `models/trigger/`）。
> * `GAPS.md` 的"未出货登记" → 本文件 **§6–§8 待办**。
> * `EXPRESSION.md` 曾由 `ExpressionChecklistTest` 机器校验； **那份测试已随文档一起删除**。
>
> **全文都在 git 历史里**：`git log --diff-filter=D --name-only` 找出删除提交，
> 再 `git show <commit>:<path>`。⚠ **不要用 `>` 重定向**（Windows PowerShell 下会写成 UTF-16）。

> ⚠⚠ **本文件刻意不留"带日期的快照"与"逐轮叙事"** —— 那些东西会腐烂，而且会 **把待办埋掉**。
> 想读被删掉的旧账（进度快照、已完成任务的完整规格、旧的缺陷登记）： **去 git 历史里翻**
> —— `git log --diff-filter=D --name-only` 找删除提交，再 `git show <commit>:ROADMAP.md`。
>
> ⚠ **别用 `>` 重定向去写它**（Windows PowerShell 下会写成 UTF-16，下一轮读出来是乱码）。

