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
  本项目已有多次"测试看着绿、其实空转"的教训（见 §3）。
- **文档承诺必须有测试兜着**：只写在 javadoc 里的承诺会腐烂（见 §3）。
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

> ⚠ **完整的机制说明没有第二份文档了** —— `engine.md` 已删（见 §9 文档分工）。
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
| `monster_attack_modify_ratio.json` | **本项目补丁**（444 条 ≠1）                                                                                                                              | 见 §3 陷阱 2                                                                                                                                                     |
| `enemy_skills.json`                | **自建**（P5-3 起），只覆盖 5 只演示怪，倍率是猜的                                                                                                       | 见 §3 陷阱 7                                                                                                                                                     |

### 2.3 机制文档（公式有疑义时以它为准）

| 文档                                                                               | 内容                                                                                       |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `E:\code\blog\hsr\HSR.md`                                                          | 乘区总览（§2）、行动/破韧/仇恨（§3）、治疗护盾（§4）、忆灵（§4）、欢愉（§5）、超击破（§6） |
| `E:\code\blog\hsr\GLOSSARY.md` / `GLOSSARY_EXTRA.md`                               | 游戏名词表（含官方描述原文）                                                               |
| `E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md`                                | **93 个角色文档**（用户明确许可读），含天赋/行迹/星魂/秘技原文                             |
| `E:\turnbasedgamedata\aluminium_texts\{RELICS,WEAPONS,GLOSSARY,GLOSSARY_EXTRA}.md` | 遗器 / 光锥 / 名词总表                                                                     |

> ⚠ `HSR.md` **不是完整的规格**：它没有战技点这一节，能量学（§3.3）只有一行。
> 缺失部分靠游戏机制攻略 + 角色文档反推， **并且要在代码里注明"这是反推的"**。

### 2.4 已核对的公式结论（别再重新推导）

- §2.2 伤害修饰区 = 增伤 `1+Σ` × 易伤 `1+Σ`（cap **3.5**）× 减伤 `Π(1-r)`（floor **0.01**）× 虚弱 `1-Σ`（floor **0.2**）
- §2.5 抗性区 = `1 - 抗性`，范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`； **负抗全效**（不是半效）
- §2.4 防御区 `(200+10L)/(def+200+10L)`，`def = 原始防御 × (1 - 减防% - 穿透%)`（ **加算**），上限 1、不为负
- §5.4 欢愉伤害 **吃双爆、不吃增伤** ⇒ `DamageType.ELATION(isCrittable=true, isBoostable=false)`
- §6.2 超击破不吃攻击力/常规增伤/双暴，吃等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤
- §6.1 **两套刻度不可混用**：80 级基础击破基数 3767（削韧单位 1）vs 超击破 376.7（削韧单位 10）
- §2.2 增伤/易伤区各自都是「伤害类型 + 攻击类型」 **加算进同一个区**（所以"战技/终结技增伤"灌 `BoostArea`，不新开区）
- §4 治疗 `= 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)`；护盾 `= 基础 × (1 + Σ护盾量提高)`

---

## 3. 踩坑记录（实测，别再踩）

### 3.1 怪物数据陷阱

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

### 3.2 Gson 静默失败（本项目历史重灾区）

新增 bean 字段一律加 `@SerializedName` 并 **核对 JSON 键名**。`enemy_skills.json` 的
`hits` 字段语义是"目标数（0=全体）"、`monster_config` 的 `damage_resistance`
与 `debuff_resistance` 是 **两个不同的 map**（元素 / `STAT_*`）—— 都踩过。

### 3.3 工具链（PowerShell / Gradle）

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

### 3.4 数据探测（别用 `ConvertFrom-Json` 读 1.5 MB 的 `skills.json`）

它会因编码/体积失败并把整个文件吐到控制台。用正则直接数：

```powershell
$t = [System.IO.File]::ReadAllText('src/main/resources/data/skills.json')
([regex]::Matches($t, '"sp_need":\s*null')).Count
```

> ⚠ 正则里的 `\s*(?!null)` 这种 **负向先行断言会因回溯而失效**（我先写错了一版，
> 数出"638 条全部非 null"）。要数"非 null"就直接匹配取值：
> `'"sp_need":\s*[1-9]'`。

### 3.5 大招在无头路径里是**免费的**（`BattleDriver` 绕过收费点）

- **现象（实测）**：`gradlew run --args=driven` 里姬子一场战斗放了 **4 次**终结技，
  而她的能量条在**每一次**施放前后**都是 120/120** —— 即**根本没扣**。
- **根因**：`Battle.castUltra` 是**唯一**会付账的入口（`lastUltEnergySpent = user.getCurrentEnergy();
  user.setCurrentEnergy(0)`，且**先清零再结算** —— 顺序是刻意的，反过来会把大招自己赚到的击杀/击破能量抹掉）。
  而 `useSkill` **完全不碰能量**。`BattleDriver.cast` 却把**所有**动作都走 `performAction` → `useSkill`，
  **包括终结技** ⇒ 无头路径上大招**不花钱**。
- **为什么一直没被发现**：**手写演示 `castUltra` 是自己调的** ⇒ 引擎"自己打"的路径以前**不存在**，
  这个 bug 就藏在"引擎接管选择"的那一刻。
- ⚠⚠ **修法（第一版是浅的，已改深）**：第一版只让 `BattleDriver` 在终结技时改走 `castUltra`。
  **那还留着同一类 bug** ✗ —— 代价住在**兄弟方法**里，**任何**前端只要走到 `useSkill` 就还是白拿。
  ⇒ 现在规矩**挪到施放发生的地方**：
  - `useSkill` **自己**判断「这条是不是终结技」，是就**必须 ready**，否则**拒绝**（拒绝是安全方向：
    忘了问的前端**拿不到施放**，而不是白拿一次）；
  - 并且由**同一段代码**（`settleUltimate`，`castUltra` 也调它）**扣掉能量**。
  ⇒ 前端**没有**办法绕过收费。
- **⚠ 连带的验证变化**：旧的反面对照（删掉 `BattleDriver` 的分支）**已经不能复现这个 bug 了** ——
  因为门在引擎里。⇒ 对照**改指向引擎那一段**（删掉 `useSkill` 的终结技分支）才变红。
  **这正说明修复变强了**：从"驱动记得收费"变成"**谁都不能不收费**"。
- **修复后连带改善（实测）**：演示从 "WIN / 32 行动 / 7 回合" 变成 **"WIN / 40 行动 / 9 回合"**，
  姬子**终结技 4 次 → 1 次**、**战技 1 次 → 3 次**，终局血量更健康（1790 / 889 / 1184 对 2228 / 301 / 9.7）。
  ⇒ **免费大招掩盖了策略的真实表现**。
- **验收**：`DrivenUltimateCostTest` **三条** ——
  ① 驱动路径施放大招后能量**确实下降**；② 放完**不再 ready**；
  ③ ⭐ **从外面钉住不变量**：经 `useSkill`（**曾经免费的那条路**）—— 空能量时**被拒绝**、满能量时**扣费**。
  **变异验证**：删掉 `useSkill` 的终结技分支 ⇒ 第 ③ 条变红。
- ⚠ **两个探针坑（同一轮踩的）**：
  ① `BattleDriver.step()`（无参）内部是 `step(-1)` ⇒ **不通知 listener**；只有 `run()` 会报回合号 ⇒
  用 `step()` 做探针会看到"什么都没发生"。
  ② 驱动演示用的是**手搓的 80 级姬子 + 三件攻击%遗器 + 光锥**（ATK **3807**），而

### 3.6 技能点在无头路径里是**收两次的**（同一形状，紧接着上一个）

- **现象（实测）**：驱动路径上，罗刹/三月七的**战技**从 3 → **1**，即**扣 2 点**；
  而 `useSkill` 单独调用是 3 → **2**（**扣 1 点**）。
- **根因**：`Battle.useSkill` 对**每一条**技能都通过 `skillPointPolicy.onSkillCast` 收费
  —— 这个收费是"**决定行动**"这一步的，**与哪条管线结算效果无关**。
  而 `BattleDriver.cast` **又**对非伤害技能手动收了 `applySkillPointCost`，
  理由是（它自己写的）"治疗/护盾技能由 `SkillExecutor` 执行、不走伤害管线，所以进来时没人收"。
  ⚠ **那条理由对演示 `Main` 成立，对驱动不成立** —— `Main` 走的是 `skill.execute(...)`（**不收费**），
  所以 `Main` 的手动收费是**唯一**一次（**正确**）；驱动走的是 `performAction` → `useSkill`（**已收费**），
  再手动收一次就是**第二次**。
- **⚠ 所以这个坑值得单独记：两种情况在 grep 里长得一模一样**（都是 `applySkillPointCost` 紧跟一个结算调用），
  **只有看结算入口才知道是不是重复**：
  - `Main`：`applySkillPointCost` + `skill.execute` ⇒ execute **不收费** ⇒ **一次，对**；
  - `Driver`：`applySkillPointCost` + `performAction` ⇒ 内部 `useSkill` **收费** ⇒ **两次，错**。
  验证方式：读 `DefaultSkill.execute` 确认它**没有任何技能点相关代码**。
- **修法**：**删掉驱动的第二次收费**（`categoryOf` / `SkillEffectTypeCategory` 随之成为死代码，一并删除）。
  规矩与 §3.5 同一条：**引擎收费，调用方不许收**。
- **修复后连带改善（实测）**：驱动演示从 "WIN / 40 行动 / 9 回合" 变成 **"WIN / 30 行动 / 7 回合"**;
  技能点**开始在 1↔2 之间正常来回**（战技 -1，普攻 +1），而不再是"战技 -2、普攻 +1"的**净亏损**。
- **验收**：`DrivenSkillPointCostTest` 两条 —— 驱动路径一次战技**恰好花 1 点**；
  且**与演示那种写法（`applySkillPointCost` + `skill.execute`）花的点数相同**。
- ⚠⚠ **这个测试我写错过两版，都是反面对照逼出来的** —— 两版**都是绿的**，但**什么都没测**：
  1. **「回合结束时的池子」不等于花费**：回合结束会**恢复 1 点**，所以用"行动前 − 回合结束"去量，
     **收费 1 次**和**完全没收费**（手动那次被 `useSkill` 因点数不足**拒绝**）**读数都是 2**
     ⇒ 量法必须**夹住施放本身**：行动被通知时的池子 − **同一回合结束**时的池子。
  2. ⚠ **拿驱动去和 `useSkill` 对比，等于什么都没比**：两者**都**走 `useSkill` ⇒ **bug 在时两边都是 2**
     ⇒ **恒真**。对照必须指向**不可能重复收费**的路径：演示那种 `skill.execute`（它**没有任何技能点代码**）。
  3. ⚠ **对照脚本本身也踩了一次**：变异如果**编译不过**，Gradle **一个测试都不跑**却报成功
     ⇒ 脚本会看到"绿"并得出结论。⇒ 对照**必须先 `compileJava`** ✓，且**从 JUnit XML 读用例数**
     （**全绿时 Gradle 根本不打印 "N tests completed"**）✓，`tests > 0` 才算通过。

### 3.7 破韧的敌人**照常行动**（同一形状的第三个，也是真正拉开差距的那个）

- **现象**：破韧（击破弱点的韧性条）本该让敌人**跳过回合** ✓ —— 而**无头路径上它照打不误** ✗。
- **根因**：`Battle.handleBrokenTurn` 的文档写的是「**由调用方来问**」 ✓，它做两件只有它会做的事：
  **① 递减破韧剩余回合**、**② 返回"这一回合跳过"** ✓。
  **手写演示就是那个调用方** ✓ —— `enemyTurn` **开头就调**它 ✓，返回 `true` 就直接 `return` ✓。
  而 **`BattleDriver` 从来没调过它** ✗ ⇒ 无头路径上：
  - 破韧的敌人**照常出手** ✗
  - 破韧状态**永远不会按回合结束** ✗（`brokenRemainTurns` 不动）
  ⇒ **把韧性条打空这件事的全部收益都不存在** ✗。
- ⚠ **这是本次同一个形状的第三个**（前两个：大招免费 §3.5、技能点双倍 §3.6）：
  > **只靠"调用方记得做"的规则，一定会有一个调用方不做。**
  ⇒ 所以这次把它放在**回合发生的地方**（`BattleDriver.step`），并且**在 `policy.choose` 之前** ✓
  —— 破韧的敌人**没有决定要下** ✓。
- ⚠⚠ **它也是"演示打不过 30000"的真正原因**（实测）：
  修掉之后同一套三人队在三只 30000 HP 的怪上 **0/8 胜 → 8/8 胜** ✓（见 P11-4）。
  ⇒ 之前把差距归给「策略（集火/破韧时机）」，**是错的** ✓ —— 不是策略不好，
  **是破韧这个机制在无头路径上根本没生效** ✓。
- **验收**：`DrivenBrokenTurnTest` 一条 ✓ —— 驱动路径上破韧的敌人**① 不造成伤害** ✓、**② 回合计数递减** ✓
  （两条刻意分开：只做其中一条仍然算坏 —— 计数不动意味着敌人**永远不会恢复** ✓）。
  **变异验证**：删掉驱动里那一段 ⇒ **变红** ✓。

### 3.8 同一形状的**系统排查**（结论：找到三个，**没有第四个**）

§3.5 / §3.6 / §3.7 **连着三次**都是同一个形状 —— 所以做了一次**系统性排查**，而不是继续等 bug 冒出来。

**形状**：引擎把一条规则**拆成"半个操作"**暴露出来，另一半**靠调用方记得**。

**排查方法（两条互补）**：
1. **让文档自证**：搜 `Battle` 里 javadoc 写「由调用方 / caller must / must be called」的地方；
2. ⭐ **机械可查的那条**：列出**手写演示调了、而 `BattleDriver` 没调**的 `Battle` 公开方法 ——
   这正是三个 bug 的共同特征。结果是 **13 个**，逐个判定：

| 方法 | 判定 |
|---|---|
| `handleBrokenTurn` | ✗ **就是 §3.7 那个 bug**（已修）|
| `applySkillPointCost` | ✓ **演示是对的** —— 它走 `skill.execute`（不收费），所以手动那次是唯一一次；驱动走 `useSkill`（已收费），所以驱动**不该**调它（§3.6）|
| `castImmediate` | ✓ 演示的便捷包装（`execute` + `processRequests` + `removeDeadCombatants`）；驱动走 `performAction` → `useSkill` → `afterMove`，**同一批善后** |
| `processRequests` | ✓ **驱动会经 `afterMove()` 调到**（`afterMove` 末尾就是它）—— 手动调用只服务 `castImmediate` / `castUltra` / `BATTLE_START` 那几处 |
| `reduceToughness` | ✓ **查询/显示用**；真正的破韧在 `applyDamage` 管线里，两条路都走 |
| `isUltraReady`, `getSkillPoints`, `getOpponents`, `getQueueSnapshot`, `aggroOf`, `enemyUnits`, `targetableEnemies`, `memospriteOf`, `printHp` | ✓ **全是查询**（`printHp` 是打印）—— 不做决定、不改状态 |

**另外查了一圈"配对调用"**：
- `beginCast` / `endCast` 的 javadoc 写着「**必须在 `finally` 里调用**」 ——
  但它的调用方是 **`SkillExecutor` 自己**（`SkillExecutor:77/99`，配 `finally`），**不是外部调用方** ⇒ 由引擎内部拥有 ✓；
- `markNextCastCountedLaughs` / `castCountedLaughs` 是一对，但两个调用方（`TriggerInterpreter` / 规则）**都成对使用** ✓；
- `StanceResult.breakDamage` 是**故意**用返回值带出来的（它说自己 `applyDamage` 调用方够不着）✓
  —— 这**不是**"忘了结算"，而是**已经结算了、把数字带出来给总伤害用** ✓。

**结论**：**没有找到第四个** ✓。三个 bug 的共同点是**规则被拆到了"决定"与"付款"两个方法里** ✓
—— 现在这三处的规矩是统一的：

> **引擎收费/引擎执行，调用方不许代劳。**

**⚠ 并且补上了另一半的测试**：`DrivenBrokenTurnTest` 现在**两条**：
① 破韧的敌人**跳过**（不造成伤害）✓；② **它会恢复**（计数走完、并且**再次出手**）✓。
第二条是刻意分开的 —— 只测"不造成伤害"的话，一个**永远不递减计数**的实现也会通过 ✓，
而那意味着敌人**永久无害** ✗ —— **比原 bug 更糟** ✓。

**⚠ 顺带记一条失败的方法论（别重犯）**：我一度想写一个"两条路径效果完全对等"的**差分测试** ✓，
但 `BattleDriver.step()` **自己**调 `stepForward()` ✓ ⇒ 想"把某个角色塞进驱动"是**做不到的** ✓
（它比较的其实是**不同的行动者** ✗）。⇒ **我把那个测试删了** ✓，而不是留一个建立在错误前提上的绿测试 ✓。
**能机械查的已经查完（上面那张表）**；剩下要靠"读日志"的部分，**没有捷径** ✓。

### 3.9 破韧的**恢复那一回合也被跳过了**（`handleBrokenTurn` 的返回值语义改了）

- **用户报的现象**："回合到怪时弱点击破恢复后仍然继续攻击吗？不跳过回合"。
- ⚠ **查下去发现两件事，一件是 bug，一件是设计**：
  1. **bug**：被破韧**且被控制**的怪，**回合计数永远不动** ⇒ **永远不恢复、整场不再行动** ✗
     （见 §3.7 的实现：驱动**先问 `canAct()` 再问 `isBroken()`** ⇒ 被控制的破韧怪**进不了**破韧分支 ⇒
     `handleBrokenTurn` **没被调用** ⇒ 计数不减）。**已修**：**先问破韧，再问能不能行动** ✓。
  2. **设计**：`handleBrokenTurn` **在计数归零那一回合也返回"跳过"** ✓ ⇒
     破韧实际吃掉 **`BROKEN_REMAIN_TURNS + 1`** 个回合 ✗ ⇒ **怪恢复的那一回合不出手** ✓ ——
     读起来就是"它都恢复了还是跳过" ✓。
- **本轮按用户要求改了机制（B）**：

| | 旧 | 新 |
|---|---|---|
| `handleBrokenTurn` 返回值的含义 | "**它还是破韧的**" | "**这一回合是否跳过**" |
| 计数归零的那一回合 | 返回 `true` ⇒ **跳过**（恢复但不出手）| 返回 `false` ⇒ **不跳过**，**恢复并出手** |
| 破韧实际代价 | `BROKEN_REMAIN_TURNS + 1` 个回合 | **恰好 `BROKEN_REMAIN_TURNS` 个回合** |
| **计数器本身** | 2（比自己写的"跳过 2 回合"**少跳 1**）| **1** ⇒ **一次跳过都不发生** ✓ |

### 3.10 ⚠ 计数器与自己写的回合数**差了一个**（`BROKEN_REMAIN_TURNS` 2 → 1）

- **用户指出**："当时当怪物行动时就应该解除弱点击破了……应该刚开始就是 1 turn left" ✓
- **量出来的差一**：`handleBrokenTurn` **花掉怪物的一个回合去递减计数器** ✓ ⇒
  **计数器与"实际丢掉的回合数"差 1** ✗：

| 计数器 | 实际丢掉的自己的回合 |
|---|---|
| 2 | **1** 个（第 2 个回合是**恢复并出手**的那一个）|
| 1 | **0** 个（**行动即解除**）|

⇒ **`Constant` 的 javadoc 原本写"skips this many of its own turns"（跳这么多回合）就是错的** ✓
（值 2 只跳 1 个）⇒ 已改为**准确描述** ✓
- **本轮按用户要求把值改成 1** ✓ ⇒ **实测**（冰锋，真实破韧）：

```
E2E broken: panel=1 turn(s) left  broken=true      ← 刚开始就是 1 ✓
E2E t1  actor=基层员工•外勤  | 冰锋: 1 left
E2E t2  actor=冰锋 <-- ITS TURN | 冰锋: not broken (stance=60)   ← 行动时解除、韧性回满、并出手 ✓
```

- ⚠ **代价必须说清楚（这是取舍，不是细节）**：值改成 1 之后 **"跳过回合"这件事完全不发生了** ✓ ⇒
  **破韧的收益只剩**：**25% 行动延迟**（`BREAK_DELAY_RATIO`）+ **伤害窗口** ✓
  ⇒ **想要回"跳过 1 个回合"就把常数调回 2** ✓ —— **那条断言就是拿来提醒这件事的** ✓
- **受影响的测试 2 条**（都已按新契约改写，而不是删掉）：
  - `BreakStateTest`：改为**显式钉住两个边界**（计数器 2 ⇒ 跳过；计数器 1 ⇒ 就是恢复回合）✓
    ⇒ ⚠ **不再依赖常数**，因为**要测的正是两者之间的那个边界** ✓
  - `DrivenBrokenTurnTest`：**显式写成 2** ✓ —— 它测的是**跳过行为**，
    而值 1 **根本不会跳过** ⇒ 用常数会让这条用例**无从断言** ✓
- ⚠ **我自己在这轮又错了一次**：改完常数后我写下"刚破韧的怪会丢掉一个回合"的断言 ✓
  ⇒ **实测把我否掉了** ⇒ **是断言错，不是代码错** ✓ —— 已按实测改写 ✓

- **实测对照**（冰锋，`BROKEN_REMAIN_TURNS = 2`）：
  - **改之前**：回合 2 跳过 → 回合 7 跳过**并**恢复 → 回合 13 才出手 ✓（**多丢一回合**）
  - **改之后**：回合 2 跳过 → **回合 7 恢复并出手** ✓ → 回合 13 出手 ✓
- **调用方同步改**：`BattleDriver` 现在直接用 `mayAct = !handleBrokenTurn(...)` ✓
  （两者在此**互为反义** ✓）；`Main` 演示的 `if (handleBrokenTurn(...)) return;` **无需改动** ✓ ——
  它本来就只读"是否跳过" ✓。⇒ **手写演示的口径自动跟着变** ✓。
- **受影响的测试**：`BreakStateTest.brokenEnemySkipsItsTurnsThenRecovers` **一条** ✓
  —— 它把**旧契约**写死了（"turn 2: skipped, and it recovers right on schedule"）✓
  ⇒ 改名并改为**新契约**：**turn 2 是恢复回合、不跳过、恢复并出手** ✓。
  全量 **769 suite / 2642 用例** ✓ 其余**零回归** ✓。
- **连带可见变化**：`battle` 演示输出 **302 → 304 行** ✓（`all` 347 → 349 ✓）——
  ⚠ **这 2 行正是恢复回合的那次攻击** ✓（怪恢复后现在打了一下）✓。
  ⚠ 顺带更正我自己的一个说法：这些行数**从来没有被测试钉住** ✓ ——
  `DemoSmokeTest` 只断言"演示不抛异常" ✓，行数是我会话笔记里的记录 ✓。
- **`--args=driven` 的连带结果**：**51 行动 / 10 回合 → 57 行动 / 12 回合** ✓
  —— 怪多打了几次，仗自然变长 ✓（仍然 WIN ✓）。
  `CharacterFactory.create(1003, 90)` 只有 **1051** ⇒ 用工厂角色做的探针**复现不出日志的数字**。
---

## 4. 待办 B 层：敌人（只有 5 只怪有技能，其余全是平 A）

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
    2. **数据源缺失是已知事实**（见 §3 陷阱 7）：先自建，每条标 `guessed=true`。
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
   `SkillCastEvent`（F-5 与"部分完成"那节都记着）。⚠ 但 **不能简单补一个发射**：
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
> （这条与"负数抵抗会被当成 0 而不是增伤"是同一处口径，见 `engine.md` §4。）

---

## 5. 待办 C 层：机制补完（全是"填口子"，无新架构）

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

### P10-8 罗刹的【结界】死时解除 ✅ 已落地（靠**新事件 `DEATH`**）

> 读者：1203 罗刹 天赋 生息的轮转 —— 「当罗刹**陷入无法战斗状态**时，结界**也会被解除**」。

**为什么这句曾经写不出来（结构性原因，不是缺某个 op）**：
`fireTriggers` 遍历队伍时会 **skip 掉已死单位** ✗（`if (ally.isDeath()) continue` ✓）——
而"收拾自己的结界"按定义**只有倒下的人自己能做** ✗ ⇒ **躺下的人永远没机会执行规则** ✓。

**⚠ 先试过、**已回退**的做法（这条值得留着）**：把"死者的自身锚定 buff"一并扫掉。
它**会破两条既有行为** ✗：
- `LethalSetTest` —— 「**all allies lethally hit this action**」是**真规则族** ✓，那条 buff **就是**落在
  **已经 `isDeath()`** 的单位身上的 ✓；
- `RelicAbilityBatchTest` —— 同类，一条在**死亡那一刻**生效的增益 ✓。

⇒ 结论：**不能靠"多扫一遍"** ✓，而必须让**内容自己说**"这条随我死而消失" ✓。

**落地形状**：
1. **新事件 `TriggerEvent.DEATH`** ✓ —— **只发给倒下的那一个** ✓，且**绕过** dead-unit skip ✓；
   发出点为 `Battle.applyDamage` 判定死亡处 ✓，**以倒下者同时作 `actor` 与 `target`** ✓
   ⇒ 规则写 `when: ["target == self"]` ✓（与 `TAKING_HIT`、反击规则**同一写法** ✓）。
   ⚠ 引擎**只开这一个例外** ✓：其它事件**仍然**是"死人什么都不响应" ✓。
   ⚠ 与 `KILL` **不是一回事** ✓：`KILL` 是**击杀者**的事实（发给我方全体）✓，`DEATH` 是**倒下者**的事实 ✓。
2. **内容规则** ✓：`on: DEATH` + `when: ["target == self"]` + `REMOVE_STATE 结界` ✓（见 1203.json
   `talent_zone_ends_when_he_falls` ✓）。

**验收**：`LuochaFieldTest.theFieldLeavesWhenHeIsKnockedDown` ✓ ——
⚠ 实现之前它**故意断言"仍在"**（把缺口钉住，不让它悄悄合上 ✓），实现后**按注释要求反转成"已解除"** ✓。
**变异验证**：把 `DEATH` 的那个例外改回去（死者再次被 skip）⇒ **该用例变红** ✓。

**⚠ 顺带说明域类效果**：本事件对**所有"施法者倒下则领域解除"的写法**都可用 ✓
（1218 椒丘的结界、1321 大丽花的结界等 —— 它们原先靠 `ticks_on: self` 由**清扫**兜住 ✓，
因为它们的 buff 挂在**活着的别人**身上 ✓；挂在**自己**身上的那种（本条）才是清扫够不到的 ✓）。
---

## 6. 待办 D 层：演示与收尾

### P11-2 真实内容演示（冰锋战）

- **目标**：冰锋（弱火/雷）+ 真角色演示完整循环：普攻→削韧→击破→推条→跳回合→DOT→回能→大招→WIN。
- **怎么做**：`CharacterFactory.create(1109, 80)`（虎克·火）+ `1204`（景元·雷）对
  `EnemyFactory.create(1002011, 29)`；循环 `stepForward → beforeMove → 出手 → afterMove`；
  每步 `printBattle()`：HP + 韧性/`[BROKEN]`/DOT 标记 + **战技点** + `castUltra` 提示。
- **验收**：一遍跑通全链路。

### P11-3 测试总盘点 + 基准 + 零警告

- **怎么做**：对照 §4–§6 的待办表确认每项测试类都在；`Benchmark.java`（固定种子，10 万轮计时）；
  `.\gradlew.bat build` **无 warning**。
- **验收**：全绿 + Benchmark 输出。

### P11-4 `AutoPolicy` 到底强不强 —— ⚠ **旧结论已被推翻，先说清楚为什么**

**⚠⚠ 这一节出过一个方法论错误，值得先读**：我曾把"演示打不过 30000"归因于**策略**
（集火残血 / 破韧时机 / 终结技时机），并据此把它写成待办。
**那个归因是错的。** 真正的原因是**三个"只靠调用方记得做"的规则在无头路径上失效**：

| # | 缺陷 | 后果 | 章节 |
|---|---|---|---|
| 1 | 大招**不扣能量** | 姬子一场放 4 次终结技，掩盖了真实资源节奏 | §3.5 |
| 2 | 技能点**收两次** | 每回合净亏 1 点，治疗/护盾双倍价 | §3.6 |
| 3 | 破韧的敌人**照常行动** | **韧性条打空毫无收益** —— 最大的一条 | §3.7 |

⇒ ⚠ **教训**：把"打不过"读成"策略不行"之前，**先确认机制真的生效了** ✓。
"策略问题"和"机制没接线"在**结果上长得一样** ✓，但**修法完全不同** ✓。

**修完三个之后的实测（8 种子，同一队：姬子 1003 / 三月七 1001 / 罗刹 1203 vs 三只 90 级怪）**：

| 姬子 | 每只怪 HP | 修之前 | **修之后** |
|---|---|---|---|
| 80 级（演示那套：3 件攻击%遗器 + 光锥）| **30000** | 0/8 | **8/8**（69 行动 / 14 回合，全队剩 4277 HP）|
| 80 级 | 20000 | 8/8 | 8/8（36 行动 / 8 回合）|
| 80 级 | 15000 | 8/8 | 8/8（26 行动 / 6 回合）|
| 90 级（工厂裸配）| 30000 | 0/8 | **3/8** |
| 90 级 | 15000 | 0/8 | 5/8 |

⇒ **演示（`--args=driven`）改回 30000** ✓（`DRIVEN_ENEMY_HP`）—— 那个刻度**本来就是数据写的** ✓，
当初调低**纯粹是因为三个 bug 让队伍看起来弱** ✓。现在它**赢得下来** ✓。

**⚠ "AutoPolicy 比手写脚本弱"这个说法也是错的（实测）**
把**手写演示的决策逻辑照抄成一个 policy** ✓，和 `AutoPolicy` 在**同样的内容、同样的种子**上对打 ——
**两边都赢，但 `AutoPolicy` 用的行动数明显更少** ✓：

**在演示自己的队伍上**（手搓 80 级姬子 + 三月七 + 罗刹，三只怪各 30000 HP，8 个种子，行动数取首种子）：

| 姬子 | `AutoPolicy` | 照抄的手写策略 |
|---|---|---|
| **80 级（演示那套）** | **8/8**，**47 行动** | 8/8，**66 行动** |
| 90 级（工厂裸配）| **8/8**，**71 行动** | 8/8，**79 行动** |

⇒ **两种队伍配置下，`AutoPolicy` 都更快** ✓（差约 10–40% 的行动数）✓。

⇒ **手写脚本不是更强的策略，它只是更慢** ✓。
⚠ **这里我先前写错过一次，更正如下**：我曾说"`battleDemo` 从不覆盖敌人 HP、面对的是工厂默认值，
所以旧的对比根本不成立" ✗ —— **那是错的** ✓：`battleDemo` **确实**把敌人 HP 设成 **30000** ✓
（`Main` 第 200 行）⇒ **内容本来就是同一套** ✓。
真正的差别在**队伍配置**：那次对比里照抄的策略用的是**工厂 90 级姬子**，
而 `battleDemo` 用的是**手搓的 80 级姬子（3 件攻击%遗器 + 光锥）** ✓。
⇒ 结论仍然成立（**手写脚本更慢** ✓），但**理由不是"内容不同"** ✓，而是
**`AutoPolicy` 在保护上更省技能点** ✓ —— 它只在**有人低于阈值且没盾**时才上盾 ✓，
而手写脚本**只要付得起就上盾** ✓ ⇒ 留给输出的点更少 ✓ ⇒ 打得更久 ✓。
**"更慢"不等于"更强"** ✓；也**不等于"脚本是更好的策略"** ✓。

**⚠ 那 `AutoPolicy` 现在是什么定位？**
- 它**仍然是刻意简单的**参考实现 ✓ —— 但这三个 bug 修完，它**已经能打赢真实内容** ✓。
- ⇒ **测试不再断言"它打不过 30000"** ✓；现在断言的是**它能在 30000 上赢** ✓
  （`DrivenRealBattleTest.theDrivenDemoWinsAtItsOwnScale`）。
- ⚠ 它的**失败依然不等于引擎缺陷** ✓；但**它现在的成功是有意义的** ✓
  —— 说明引擎能在**没有任何脚本**的情况下打完一场**数据本身刻度**的仗 ✓。
---

## 7. 远期（只记规格，不排实现）

| 项目                                   | 规格锚点                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **忆灵（角色专属召唤）**               | ✅ **已做**：面板**推导**（不是快照）§24.5 / 在场可问可指 §24.6 / 规则召上场 §24.7 / **能打且伤害按自己的属性缩放** §24.8。⚠ 与本节原规格的两处差异：面板读的是召唤者**已解析**的面板（**含**召唤时在场的临时增益，原规格写的是"快照本体、不含临时增益"，文档只写「等同于…的生命上限」，没有说要不要含增益 → 已登记）；伤害类型仍走普通元素而不是 `MEMORY`。仍缺：连携攻击（一次行动中的两次独立攻击）、忆灵技 2~5、削韧、仇恨、"指令忆灵行动" |
| **欢愉体系**                           | 伤害公式 `基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))`，**禁攻击力/属性增伤**；阿哈速度 = `80 + 最快/5 + 第二/10 + 第三/20 + 最慢/50`；`elation_basic_level_damage.json`（101 条）**从未被加载**                                                                                                                                                                                                                          |
| **模拟宇宙**                           | 祝福池化 = 3 选 1                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Boss 专属机制库**                    | P9-5 只做通用换招/反击/免疫；个别 Boss 的专属机制每个 = 一个 `BossMechanic` 子类（需登记逃生舱）                                                                                                                                                                                                                                                                                                                                                |
| **界面**                               | `Battle.getQueueSnapshot / printHp` 是现有 CLI 出口；UI 层接 `Battle` 事件流即可                                                                                                                                                                                                                                                                                                                                                                |
| **战斗拆分：组装 / 主战斗 / 引擎原语** | 见 §7.1，**已量、第一小刀已落地**（事件公告面 `BattleEvents`），其余未排                                                                                                                                                                                                                                                                                                                                                                                                       |

### 7.1 战斗拆分（组装 / 主战斗 / 引擎原语）—— 量过了，**第一小刀已落地**

> 目标（用户提出）：把**组装战斗（聚合所有机制 trigger）**与**主战斗（专门触发 trigger）**分开，
> 让**触发成为战斗的主要站点**。⚠ 下面是**实测**，不是设想。

**先量再动 —— 三个前提里有两条其实已经成立**：

| 设想 | 实测 |
|---|---|
| 「组装」散落在各处，要抽出来 | ⚠ **已经集中** ✓：`CharacterFactory.effectiveTriggerTable(cid, relicSuit)` 一处做**角色表 + 遗器套装**的合并 ✓，紧邻一行合并**光锥** ✓（`TriggerTable.plus`）。**没有第二个组装点** ✓ |
| 「主战斗」触发点散成一团 | **27 处**，但分布在 **18 个方法**里、其中 **13 个只发 1 个事件** ✓ —— 真正密集的只有 `applyDamage`（6 个）✓。⇒ **不是**"散成一团" ✓ |
| `Battle` 太大 | **3801 行**，129 个字段 ✓。⚠ 组装只占 **9 个方法 / 约 120 行** ✓ ⇒ **抽组装不会让 Battle 变小** ✓，只是职责更清楚 ✓ |

**⚠ 已落地的那一刀：事件公告面有了自己的家**（`BattleEvents`）
- 原先 `Battle` 里**八个一行的公告方法**（`broadcastHeal` / `broadcastKill` / `broadcastHpLoss` / `broadcastEnergyGain` /
  `broadcastBreak` / `broadcastSkillPointGained` / `broadcastSkillPointSpent` …）**各自带着一条不同的路由规则** ✓，
  埋在 3800 行的运行时里 ✓，**没有任何一处把"谁被通知"讲清楚** ✗。
- 现在它们是 `BattleEvents` 的**全部内容** ✓ —— 那个文件的**唯一主题**就是路由 ✓。`Battle` 保留一行委托 ✓，
  **所有调用点一字未改** ✓。
- ⚠ **拆出来的路由规则**（这正是先前没人写下来的东西）：
  1. **先通知"直接当事方"** ✓（`target` / `attacker` …）；⚠ **当事方可以是敌人** ✓
     —— 打出致命一击的怪**会被通知** ✓（Boss 机制/反击要能响应 ✓）；
  2. **再通知我方全体** ✓（"我方目标每次攻击后"是**每个辅助**都订阅的 ✓）；⚠ **只限我方** ✓
     —— 没卷入的敌人**没有规则表、没有会响应的 buff** ✓ ⇒ 通知它是**没人读的工作** ✓；
  3. **按对象去重** ✓ —— 已经在第 1 步收到过的单位**不再**在第 2 步收到 ✓
     （否则同一个 buff 被调两次 ⇒ **效果翻倍** ✗）。
- **验收**：`BattleEventRoutingTest` 三条 ✓（当事方+全队各一次 ✓ / 兼任两角色**只收一次** ✓ /
  **卷入的敌人收到、旁观敌人收不到** ✓）。⚠ 第三条我**一开始写反了**（以为敌人都收不到 ✗）—— **代码是对的** ✓。
  **零回归**：全量测试绿 ✓，三个演示的**行数逐字不变** ✓（302 / 45 / 347 ✓）。

**⚠ 没做的（留着，未排）**：
- `fireTriggers` **16 个重载** + `fireTriggersWithSubject` **3 个** ✓ 仍在 `Battle` ✓
  —— 那是**规则表总线** ✓，与上面这个**单位/ buff 公告**是**两个不同的听众** ✓，搬它会把整个组装一起拖走 ✓。
- `Battle` 的**129 个字段**与**运行时**仍是它自己的 ✓。若要做第二刀，**建议动的是**
  「把 `applyDamage` 里 6 个公告点提炼成有名字的步骤」 ✓（可读性收益明确、风险低 ✓），
  **而不是**搬 `fireTriggers` ✓。

**⚠ 关于"公式走规则"（原第三条硬约束，仍然成立）**：要求**公式本身**也走触发 ⇒ 得在 DSL 里表达乘区公式 ⇒
等于在 DSL 里重写 `Damage.java` ⇒ 需要**公式解释器**。**不建议** ✓。
---

## 8. 为什么这样排序

1. **A 层（P8-6/7/8 + P8-3）最优先** —— 它是"93 个角色机制"的 **总开关**。在此之前做任何角色内容
   都是往 `Battle` 里塞 `switch (cid)`，做完还要返工。 **已完成** ✅
2. **B 层（P9）紧随** —— 现在只有 5 只怪有技能、其余全平 A，没有难度曲线。
   `enemy_skills.json` 的自建表 **隔离了数据源缺失这个外部风险**（找到源数据只换加载处）。
3. **C 层（P10）是"填口子"** —— 全部依赖已完成的机制， **不该出现新架构**。
   若某任务需要新架构，回退拆小。
4. **D 层收尾** —— demo 与基准，最后做。

**每步保持：可编译 → `.\gradlew.bat test` 全绿 → 提交。**


### 3.11 伤害数字**看不到** ⇒ 引擎新增一个**只观察、不改变**的接口（`Battle.DamageObserver`）

- **用户要求**："日志加伤害数字" ✓ —— 这是日志**最后一块缺口**：
  **破韧和冻结都看得到了**（`broken -- skips its turn` / `held -- cannot act`）✓，**唯独伤害看不到** ✗
  ⇒ 一行只写 `used COMMON`，**3 点还是 3000 点全靠猜** ✗
- ⚠ **为什么不能用现成的 `DamageEvent.onDamage`**（两次踩到同一个坑）：
  - 它**在乘区计算【之内】触发** ⇒ **拿到的是中间值**，不是结算值 ✗
    （`Battle.applyDamage` 的注释已经写明：这就是**为什么吸收要放在 `DamageEvent` 之外**）✓
  - 结算值**事后也拼不出来** ⇒ **assembly 是累加的**（`addBoost` 是 append）✓
    ⇒ **再算一遍会把增伤/易伤/减伤/弱点全算两次** ✗
- **做法**：在**结算值唯一存在的那一处**（`applyDamage` 的 return 之前）**通知观察者** ✓

| 项目 | 内容 |
|---|---|
| 接口 | `Battle.DamageObserver`（`@FunctionalInterface`）|
| 参数 | `damage, applied, hpLoss, shieldAbsorbed, died` |
| 注册 | `Battle.addDamageObserver`（**copy-on-write**，注册少、结算多）|
| 转发 | `BattleDriver` 构造时注册一次，转发给**当时**的 `listener` ⇒ `withListener` 之后调用也生效 ✓ |
| 前端接口 | `BattleListener.onDamage(...)`（默认空实现）✓ |

- ⚠ **它是观察者而不是机制**，这一点**是量出来的，不是声明出来的**：
  **把通知整段删掉，全量 769 suite / 2642 用例结果【完全一致】** ✓
  ⇒ **没有任何东西能读回它** ✓ ⇒ 不会改变战斗 ✓
- **前端呈现**（tui）：伤害数字**拼在产生它的那一行动作后面** ✓：
  `used COMMON  - 315 to 基层员工•外勤` ✓
- ⚠ **顺带抓到一个【假归因】，而且是读日志读出来的**：
  日志里出现 `冰锋 used COMMON  - 132 to 冰锋` —— **看起来是怪打自己** ✗
  ⇒ **实测**：那是 **`type=DOT`**，**由我方角色（姬子/三月七）施加**，
  **在【敌人】回合开始时结算** ✓
  ⇒ **把它记在受害者的行动下，等于两次归错** ✓ ⇒ 现在**单独一行，并写明是谁施加的**：
  `Himeko  |  damage  | 132 to 冰锋` ✓
- **测试**：新增 2 条（`anActionReportsTheDamageItDealt` / `damageOverTimeNamesWhoAppliedIt`）✓，
  改写 2 条（一条**只允许两种 side** ⇒ 现在有第三种 `damage` ✓；一条**用 equals 比对动作文案** ⇒ 现在文案带数字 ✓）
  ⇒ **tui 41 用例全绿** ✓
- **反面对照 · 3 条全部咬人**：
  1. **删掉通知** ⇒ 全量结果**完全一致** ✓（证明**只是观察**）
  2. **DOT 重新记在行动者头上** ⇒ 2 条用例**变红** ✓
  3. **不拼伤害数字** ⇒ 1 条用例**变红** ✓
- ⚠ **我自己第一次写的对照是【废的】**：`--tests *` **一个用例都没匹配到** ⇒
  **0 个测试对比 0 个测试**，却报告"相同" ✗ ⇒ 改成**跑全量并用总数+失败集合比对** ✓


### 3.12 治疗与护盾也**公告结算值**（`HealObserver` / `ShieldObserver`）

- **用户要求**："把提供治疗、提供盾、dot 伤害都记录" ✓
- **三者各自都只有一处结算值存在** ✓ —— 公告就放在那里：

| 事实 | 结算值所在 | 观察者 |
|---|---|---|
| 伤害 | `applyDamage` 的返回值 | `DamageObserver`（§3.11）|
| 治疗 | `heal` 里算出的**实际回复量** | `HealObserver` ✓ |
| 护盾 | `grantShield` 里**加成之后**的值 | `ShieldObserver` ✓ |

- ⚠ **为什么不能拿"请求值"或"HP 差"代替** ✓：
  - **治疗**：`CanHit.heal` 会**按上限截断**、**对死者直接不生效** ✓ ⇒ **请求值 ≠ 实际回复量** ✗；
    读 HP 前后差也不行 —— **中间只要有别的东西动了 HP 就错** ✗
  - **护盾**：值要**先过提供者自己的护盾加成** ✓ ⇒ 报请求值就是**报一个战斗里不存在的数字** ✗
- **触发条件都收窄到"真的发生了"** ✓：
  **治疗 ≤ 0 不触发**（满血治疗不是治疗事件）✓；**护盾 ≤ 0 不触发**（`amount <= 0` 是这套 API 表达"清除护盾"的方式）✓
- ⚠ **前端又抓到一次【归因错误】，和 §3.11 的 DOT 是同一个错误换了张皮**：
  日志出现 `冰锋 | enemy turn | used COMMON - 474 to Himeko, heal +949 to Himeko` ——
  **读起来是怪在给我们治疗** ✗
  ⇒ **实测**：`turnActor=冰锋  healer=罗刹  target=Himeko  949` ✓
  ⇒ **罗刹的结界在【敌人】回合给我方回血** ✓
  ⇒ **第一个版本把效果记在"当前行动者"名下** ✗ ⇒ 现在**按【施加者】分组** ✓：
  行动者自己造成的效果**拼在它的行动行后面** ✓，**别人造成的单独一行并写明是谁** ✓
- **实测输出**：
  ```
  10  March 7th   our turn   used SKILL  - shield +504 to March 7th
  11  Luocha      our turn   used SKILL  - heal +378 to March 7th
   7  Himeko      damage     132 to 冰锋          <- DOT，单独一行，写明施加者
  ```
- **测试**：新增 2 条 ✓（`healsAndShieldsAreReportedWithTheirAmounts` /
  `ourHealsAreNeverFiledUnderAnEnemyTurn`）⇒ **tui 43 用例全绿** ✓
- **反面对照 · 3 条全部咬人**：不记治疗 ⇒ 红 ✓；不记护盾 ⇒ 红 ✓；
  **效果重新记在行动者名下 ⇒ 红** ✓


### 3.13 日志**可滚动**，天赋/追击的行**有标记**

- **用户要求**："天赋的释放也要写 log 并且 log 可以滚动" ✓
- **日志滚动** ✓：`PgUp` / `PgDn` 一次 4 行，`Home` 到最旧，`End` 回最新 ✓
  - **标题会说明**：`Log -- scrolled back 12 (End: newest)`，**边框变黄** ✓
    ⇒ ⚠ **必须让人看出来"没跟着最新"** ✓ —— 否则**看起来就是日志卡住了** ✓
  - 偏移量放在 `ChoiceFlow`（**视图每帧重建**，放视图里会忘掉）✓
  - ⚠ **用例抓到一个真 bug**：`scrollLog(Integer.MAX_VALUE)` **算术溢出成负数** ⇒
    **`Home` 跳到了【最新】** ✗ —— **正好反了** ✓ ⇒ 现在**先夹住步长再相加** ✓
- ⚠ **"天赋释放"这件事引擎【不公告】**，这一条是**量出来的**：
  **把 `CAST_SKILL` 插桩、打 25 回合 ⇒ 触发 0 次** ✓
  ⇒ **姬子的追击走的不是 `CAST_SKILL`** ✗ ⇒ **hook 它等于加一个死功能** ✓
  ⇒ **天赋的效果本来就已经在日志里** ✓（伤害/治疗/护盾，且**归因正确**）✓
- **所以按用户选择走"给天赋造成的行加标记"** ✓ —— 判据是引擎自己的
  **`DamageType.ADDITIONAL`**（离手额外攻击）✓：
  - **姬子的追击**：`cat=UNSPECIFIED type=ADDITIONAL applied=874` ✓
  - **敌回合里三月七的反击**：`turnActor=基层员工•外勤 attacker=March 7th type=ADDITIONAL applied=332` ✓
  - ⇒ **两者是同一种事实**：**不是它选的那个动作造成的伤害** ✓
- ⚠ **标记写 `[extra]` 而不是 `[talent]`** ✓：`ADDITIONAL` **同时涵盖天赋和追击** ✓
  ⇒ 叫 "talent" **等于替其中一种命名、把另一种悄悄叫错** ✗，而且**屏幕上分不出来** ✗
- **实测输出**：
  ```
  6  Himeko  our turn  used ULTRA  - 13.3k to 冰锋 [extra], 1949 to 基层员工•外勤 [extra], ...
  ```
  ⚠ 但 `used COMMON - 315 to 基层员工•外勤` **没有标记** ✓ —— 那次天赋只是**追加了普通伤害**，
  **引擎报的类型是 `NORMAL`** ✓ ⇒ **标记是诚实的：它只标引擎真的说是额外攻击的那些** ✓
- **测试**：**+2 条**（`theLogScrollsBackAndClampsAtTheOldestEntry` / `extraHitsAreMarked`）⇒ **tui 45 用例全绿** ✓
- **反面对照**：**去掉标记 ⇒ 红** ✓


### 3.14 行动轴（`Order` 列）与**开局状态**的语义

- **用户要求**："界面重排一下 加上行动轴 并且角色行动时 使用箭头标注行动轴"，
  随后明确："行动轴排左面"、"刚开始 length 都应该是 0 而不应该推进行动轴" ✓
- **数据全部取自引擎**，前端**不自己算速度** ✓：
  `Battle.getQueueSnapshot()`（**已按行动值排序**）+ `Queue.getTimeRemaining(signal)`（**就是行动值**）✓
  ⇒ ⚠ **自己算会是引擎规则的第二份实现**：破韧延迟 25%、多种提前/延后一改就会和引擎对不上 ✗
- **最终布局**：

```
header
[ Order(定宽18) | Our side | Enemies ]     ← 行动轴在左，定宽
log / choices / footer
```

  - ⚠ **宽度必须显式定死**：`row` 会在子元素间**平均分配** ⇒ 不定宽则**行动轴占掉三分之一屏宽**，
    把营地名字压扁 ✓ ⇒ 实测 `Constraint.length(18)` + 两个 `fill()` = **18+31+31 = 80** ✓
  - ⚠ **`Element.constraint()` 只是读方法** ⇒ **方法返回类型必须是 `Panel`** 才能链式调用 ✓
    （**这个是我用"写代码编译"试出来的**，先读字节码得到的结论是错的 ✗）
  - 每个单位**占两行**（名字 / 时间）⇒ **长中文名不会把左列撑宽** ✓
  - 只列 4 个单位，其余 `+N later` ✓

- ⚠ **开局语义（用户提了三次才说清，值得完整记下来）**：
  - **实测**：`turnsTaken=0`，但会话在 `reset()` 里**为了表头能写出"接下来是谁"而调了一次队列推进** ✓
    ⇒ **引擎的 `currentActor` 已指向第一个单位** ✓
  - ⇒ 屏幕上出现：**箭头指着它 + `now` + 表头"waiting for 它"** ✗
    ⇒ **一个键都没按，屏幕却在说"轮到它了"** ✗ —— 用户报的就是这个 ✓
  - **且引擎开局就已算好全部行动值**（`nextActionTime = 10000/速度`）：
    `0.00 / 17.33 / 40.61 / 52.21 / 52.21 / 59.95` ✓ ⇒ **屏幕把它们照实显示** ✗
    ⇒ 用户："**刚开始 length 都应该是 0**" ✓
  - **改法（显示层）**：
    | | 开局 | 按键之后 |
    |---|---|---|
    | 箭头 `v` | **不显示** | 显示 |
    | `now` | **不显示** | 显示在当前行动单位下 |
    | **所有长度** | **全部 `0`** | **真实距离** |
    | 顺序（谁在前）| 显示 | 显示 |
  - ⚠ **没有做**（并说明为什么）：**没有让引擎从 0 开始** ✗
    ⇒ `nextActionTime` 是**行动条的核心机制**，开局算好是它的设计 ✓；
    动 `startBattle/initialise` 会**波及全部消费方**（769 引擎用例 + 手写演示）✓
    ⇒ 收益只是"内部数字好看"，而**"屏幕上没推进"已经满足** ✓ —— **已向用户说明并请其定夺** ✓

- ⚠ **我自己在这轮犯的错，全部被用例/探针抓到**：
  1. **缩放基准错**：`冰锋 17` 画在第 0 列 —— 因为尺度用了"**已画过的单位**"，
     而当前行动者恒为 0 ⇒ **第一次比较就是拿 0 比** ✗
  2. **尺度随"最后一个装得下的单位"变** ⇒ **同一个间隙在两帧是两个不同距离** ✗
     ⇒ 改成**固定以一个完整回合（约 100 行动值）为尺度** ✓
  3. **竖排第一版把营地挤爆**：轴占 **30 行里的 10 行** ⇒ 营地只剩 **6 行** ⇒ **HP 读数被截** ✗
     原因：**每个不同行动值之间插空行**（4 行）+ **副标题太长导致每行折成两行**（翻倍）✗
     ⇒ 去掉空行、标题缩成 `Order`、只列 4 个 ⇒ **营地恢复 16 行** ✓
     ⚠ **这个是 `ourSideActuallyDamagesTheEnemies`（"HP 读数必须在屏幕上"）红了才发现的** ✓
  4. **归零时把 `now` 一起删掉了** ⇒ **按键后也没有标记** ✗ ⇒ 被 `theActionAxisNames…` 红出来 ✓

- **受影响的用例 4 条**（都按新契约改写，而不是删掉）：
  - `weaknessesOnScreenMatchTheEngine`：原来**全屏找怪名**再看下一行 ⇒
    **左列也有怪名** ⇒ 读到左列的行 ✗ ⇒ 改成**锚定 `Enemies` 面板** ✓
  - `theActionAxisNamesWhoActsNext…`：原来**全屏按顺序找名字** ⇒
    但**名字在表头也会出现**（`waiting for X`）⇒ 报告"顺序错"，**其实左列是对的** ✗ ⇒
    改成**只扫左列内容**（取每行第一、二个 `|` 之间）✓；并且**先推进一回合再读轴** ✓
    （**轴是快照**，推进前读它，断言的"第一名"就不是当前行动者 ✗）
  - 新增 `nothingIsMarkedAsActingBeforeTheFirstTurn` ✓
  - 新增 `extraHitsAreMarked` ✓（见 §3.13）
- **反面对照**：**去掉归零 ⇒ 开局用例变红** ✓；
  断言**逐行**检查"不能是非 0 数字"（不只第一行）—— 因为**"某一行漏了"正是用户报的现象** ✓
- ⚠ **教训（第三次同类）**：**用例用"全屏搜索"代替"明确作用域"** ⇒
  **界面一改就失效**，而且**失效方向是假红**（报错误的 bug）✗ ⇒ 现在都**锚定到具体面板** ✓


### 3.15 布局的高度契约：**营地不能被挤掉单位**

- **起因**：用户问"三个怪物都能看清，为什么要裁" —— ⚠ **他是对的**：我先前的探针**搜整个缓冲区**找怪名，
  而**左列（Order）也列敌名**，所以把"✓ 三个都在"当成了结论 ✗
- **改正后的实测**（**用敌方面板自己的边框定位，只数框内的内容**）：

```
终端高度   敌方面板内部   框内敌人数
  25        10 行         2/3
  27        12 行         2/3
  28        13 行         3/3   ← 地板
  30        15 行         3/3
  45        28 行         3/3
```

- ⚠ **营地面板是唯一"拿剩余空间"的带**：`header 1` + `营地(剩余)` + `Log 固定` + `Orders 固定` + `footer 1`
  ⇒ **终端矮时，缺的行全从营地扣** ✓
- ⚠ **试过给营地加约束，两次都无效**（实测每个高度**完全一致**）：
  - `Constraint.min(CAMP_MIN_ROWS)` ✗
  - `Constraint.length(CAMP_MIN_ROWS)` ✗
  ⇒ **下面的带照样占满自然高度** ⇒ **营地仍是吸收缺口的那个** ✓
  ⇒ **结论：不要留一个"看起来有用"的约束** —— 已删除，改成**文档化的终端地板** ✓
- **有效的那一半：从下方两个过大面板回收行数**（实测营地曾需要 **32 行**终端，现在 **28 行**）：

| 面板 | 原来 | 改成 | 省 |
|---|---|---|---|
| **Orders** | 每个队员的大招**各占一行**（3 行多数字是 "no ultimate ready"）+ 空行 + "acting unit's action" 标题 | **大招合成一行**（按队伍位置编号），去掉空行和标题 | **4 行** |
| **Log** | `LOG_ROWS = 6`（实测只显示到 4）| `LOG_ROWS = 5`（显示 3）| **1 行** |

- ⚠ **被裁掉的是"最后一个敌人的最后几行"**：`次元扑满` 的**韧性行**先没了，再少一行**连名字都没了** ✓
- **受影响的用例**：
  - 新增 `everyEnemyStaysOnScreen` —— ⚠ **用面板自己的角字符 `╭` / `╰` 定位**，
    断言**每个敌人都在框内** ✓
    ⇒ **前两版都被骗过**：先按列切（**扫进了左列**），再按列切（**扫进了 Log 面板**，它也会打敌名）✗
  - `theChoiceScreenShowsWhatIsBeingDecided` —— 原来断言 `ULTRA ready` 字样，
    而**那个字样随大招行一起消失了**，改成**每个队员的按键和名字都要在** ✓
- **反面对照**：
  - **给 Orders 加 4 行 ⇒ 布局用例变红** ✓
  - **把地板写成 27（实际只有 2/3）⇒ 布局用例变红** ✓
- ⚠ **教训**：**"名字在屏幕上"不等于"在那个面板里"** ——
  同一个名字会出现在**左列**和**日志**里 ⇒ **任何"某内容是否显示"的断言都必须先锁定作用域** ✓
  （这已经是同类第三次：§3.14 记了两次）


### 3.16 罗刹结界的两处缺陷：**状态挂错阵营** + **跨阵营计时不到**

- **起因**：用户问"罗刹的天赋好像也没触发" ✓ —— **他是对的**：实测**结界从 t11 到 t25 一直在**
  （层数消耗、重开、时长都正常），**期间我方攻击了六次，一次治疗都没有** ✗
- **缺陷 ①：结界挂在了罗刹自己身上** ✗
  - 数据里是 `{"op": "APPLY_BUFF", "buff": "结界", "target": "self"}` ✓
  - ⚠ **但两条治疗规则要求 `target has_state 结界`，而 `ALLY_ATTACK` 传给规则的是「被攻击的敌人」**
    （`SkillExecutor`：「ALLY_ATTACK fires for OUR attacks, so the unit it was aimed at is always on the other side」）✓
    ⇒ **敌人永远不持有结界 ⇒ 治疗永不触发** ✗
  - **判定依据（行迹文本）**：「**处于结界中的任意敌方目标**受到我方攻击后，除攻击者外的我方目标也会回复……」✓
    ⇒ **「处于结界中的敌方目标」⇒ 结界标记的是敌人** ✓ ⇒ 改为 `all_enemies` ✓
- **缺陷 ②：跨阵营的计时永远不到** ✗ —— **这个是修 ① 之后才暴露的**
  - 结界挂到敌人身上后，**时长不再递减**：实测 **连续 15 回合 `dur=2`**，而 `tickOwner=Luocha` **设置正确** ✓
  - 根因：`Battle.tickForeignBuffs` **只遍历 `allies`** ✗
    ```java
    for (CanHit ally : allies) { ... }   // 敌人身上的 buff 永远到不了
    ```
    ⇒ **挂在敌人身上、时钟却是我们角色的状态，永远不会被推进** ✗
  - ⚠ **这个缺口在 ① 存在时是看不见的**：结界挂在罗刹自己身上时，**是「载体自己的 tick」在消耗它** ✓
    ⇒ **两个缺陷互相掩盖** ✓
  - 修法：改为遍历**两个阵营** ✓ ⇒ **逐 buff 的 `ticksOn(clockOwner)` 判断仍然决定一切** ✓
    ⇒ 放宽遍历范围**不会**消耗没有时钟的 buff ✓
- **修好后的实测**（同一场战斗）：

```
t11  结界展开（敌人各 2 回合）
t12  RULE HEAL ×2            ← 盟友攻击触发治疗
t18  结界 2 → 1               ← 罗刹自己的回合推进
t20  RULE HEAL ×2
t23  RULE HEAL ×2
t25  结界消失
LOG  heal +131 / +757 / +173 / +54 to March 7th   ← 日志里也能看到了
```

- **受影响的既有用例 1 条**：`LuochaFieldTest.fieldUp()` 原来读**罗刹自己**身上的结界 ✗
  ⇒ 改为读**敌人**（并注明 `ticks_on: "self"` 不变：时长按他的回合数、他倒下时解除，这两点对"状态在别人身上"同样成立）✓
- **反向覆盖**：**引擎 2642 用例全绿** ✓ —— 包括阮•梅、缇宝、银狼、三月七等其他 7 个用「结界」的角色 ✓
  ⇒ 说明放宽 `tickForeignBuffs` 没有破坏它们 ✓
- ⚠ **教训**：**一个"标记型状态"挂在哪一方，是由【读它的那条规则】决定的，不是由"谁展开它"决定的** ✓
  ⇒ 数据里那句 `note` 已经推出了"`has_state 结界` 必须在被攻击的单位上读得到" ✓
  **却没有回头改三行之上的 `APPLY_BUFF` 的 `target`** ✗ —— **同一个对象的两半在不同地方被想清楚了，但没有合上** ✗


### 3.17 观察脚本**不要**放在构建目录里

- ⚠ **实测**：`build\jd\` 里的分析脚本（`probe_*.py` / `dump_*.py` / `control_*.py`，本会话写了几十个）
  **被 `gradlew clean` 一起删掉了** ✗ —— 它们不在版本控制里，**没有任何备份** ✓
- ⇒ **脚本移到 `E:\code\aluminium-jd\`**（构建树之外，仍不在版本控制里）✓
- ⚠ **代价的边界要说清楚**：
  - **丢了的是「过程」**：探针与对照脚本、以及它们量出来的中间数据 ✗ ⇒ **重跑要重写** ✓
  - **没丢的是「结论」**：ROADMAP 的条目在**源码树**里、**已提交** ✓；
    代码改动在 `f81a348d` ✓ —— **我上一条说「§3.16 丢了」是错的** ✗（我自己核查后纠正）
- ⚠ **教训**：**「结论」写进受版本控制的文档，「过程」写在构建目录之外** ✓
  ⇒ 把过程放进 `build/` 等于**把它标记成可丢弃**，而 `clean` 会照做 ✓


### 3.18 `DamageType.TALENT`：**天赋槽位的伤害**，以及它**做不到**的那件事

- **起因**：用户在日志里看到 `[extra]`，问"天赋到底触发没有"。实测 `[extra]` **同时用于三种东西**：
  天赋追击、三月七的反击、姬子大招的追加命中 ✓ ⇒ **前端想把它区分开** ✗
- **前端推理失败 6 次**（每次都被实测推翻，且**每次弄坏别的行为**）：

| 做法 | 实测为什么不行 |
|---|---|
| 用「当前规则不是普通 DAMAGE」推断 | **把大招的 `[extra]` 弄没了** |
| 用 `SPEND_RESOURCE` 标记 | **天赋伤害在扣费【之前】结算** ⇒ 晚一步 |
| 用 `DAMAGE` 规则标记 + 去掉旧检查 | **反击标记坏了，2 个绿灯用例变红** |
| 加标记且保留旧路径 | **标记持续到回合末 ⇒ 敌人的普攻被标成天赋** |
| 改成"消费式" | **只标到 3 条里的 1 条**（另外两条没有自己的 DAMAGE 规则）|
| 改成"窗口式"（DAMAGE 开、SPEND 关）| **脚本锚点没匹配，改动没落地**（第 6 次未验证）|

- ⇒ **结论：日志层推不出来** ✓ ⇒ **改引擎**：新增 `DamageType.TALENT` ✓
  - 在 `TriggerInterpreter` 的 DAMAGE 分支：规则的 `skill` 是 `TALENT` 且**没自己写 `damage_type`** ⇒ 打上 `TALENT` ✓
  - `BattleDamage`：**`TALENT` 与 `ADDITIONAL` 一起收追击加成** ✓
    ⇒ ⚠ **不加这一条会静默降低所有天赋伤害** ⇒ **实测：全套引擎用例（含所有伤害数字断言）全绿** ✓
  - `DamageTypeTest` 的**取值计数 12 → 13**（那个计数是**故意的守卫**：新增类型必须是决定，不能是副作用）✓
    并**新增用例断言 `TALENT` 在暴击/加成/字符串表上与 `ADDITIONAL` 完全一致** ✓
- ⚠ **然后发现它做不到我想要的事**：**三月七的反击规则和姬子的天赋规则，`skill` 都是 `TALENT`** ✓

```json
// 反击
{"op": "DAMAGE", "skill": "TALENT", "target": "attacker"}
// 姬子天赋
{"op": "DAMAGE", "skill": "TALENT", "target": "all_enemies"}
```

  ⇒ **`TALENT` 的真实含义是「天赋槽位的伤害」，而反击本来就在天赋槽位** ✓ ⇒ **引擎没有标错，是我把「天赋伤害」和「天赋的追击」混为一谈** ✗
  ⇒ **类型这一层区分不了反击和追击** ✓ —— `Damage` 上**没有规则 id、也没有目标选择器** ✓
- **最终决定（两件事都保留，但都不越界）**：
  - **引擎保留 `TALENT`** ✓ —— 它让消费方能问一个**更真的问题**：「这是天赋槽位的伤害吗」，
    而 `ADDITIONAL` 表达不了 ✓
  - **日志保持 `[extra]` 一个标记** ✓ —— **把反击叫「天赋」、或把天赋叫「反击」，都是把猜测印成事实** ✗
  - **TUI 把 `TALENT` 也算作 off-turn extra hit** ✓ —— ⚠ 不计入会**把这些伤害整行丢掉**
    （实测：天赋三条实例和它引发的反击**一起从日志消失**）✓
- ⚠ **教训（这轮最贵的一条）**：**我在一个"锦上添花"的标记上连试 6 次** ✓
  ⇒ **第 1 次弄红时就该停下来问"引擎给的信息够不够"**，而不是换第 2、3……种推断方式 ✓
  ⇒ **最终答案不是"更聪明的推断"，而是"让引擎把事实说出来"** ✓ —— 而且**说出来之后仍然不够**，
  真正缺的是**规则身份**（`Damage` 上没有它）✓ ⇒ **这一条留作已知缺口，不猜** ✓

---

## 9. 文档分工（改文档前先读这一节）

| 文件                                                | 放什么                                                                                                                                                     | 不放什么                   |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| **`src/main/java`（唯一权威）**                     | **引擎当前行为**。`engine.md` 已删（见下），所以"现在是什么"要**读代码**，或读 `src/main/resources/characters/<cid>.json` 里每条规则自带的 `source`/`note` | 设计意图、进度、历史       |
| `ROADMAP.md`                                        | **本文件**：设计原则（§1 三条红线 ＋ 三分法）、待办（§4–§6）、踩坑记录（§3）                                                                               | 已完成的逐轮叙事、缺口清单 |
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
> * `GAPS.md` 的"未出货登记" → 本文件 **§4–§6 待办**。
> * `EXPRESSION.md` 曾由 `ExpressionChecklistTest` 机器校验； **那份测试已随文档一起删除**。
>
> **全文都在 git 历史里**：`git log --diff-filter=D --name-only` 找出删除提交，
> 再 `git show <commit>:<path>`。⚠ **不要用 `>` 重定向**（Windows PowerShell 下会写成 UTF-16）。

> ⚠⚠ **本文件刻意不留"带日期的快照"与"逐轮叙事"** —— 那些东西会腐烂，而且会 **把待办埋掉**。
> 想读被删掉的旧账（进度快照、已完成任务的完整规格、旧的缺陷登记）： **去 git 历史里翻**
> —— `git log --diff-filter=D --name-only` 找删除提交，再 `git show <commit>:ROADMAP.md`。
>
> ⚠ **别用 `>` 重定向去写它**（Windows PowerShell 下会写成 UTF-16，下一轮读出来是乱码）。

