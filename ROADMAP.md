# aluminium 开发路线图

一个用 Java 写的类《崩坏：星穹铁道》回合制战斗引擎。数据来自
[turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata)，经 `E:\code\python\generate_data.py`
解析到 `src/main/resources/data/`。

**当前状态（2026-09-27）**：**87 个测试类 / 780 个用例全绿**；demo 跑通一场三对三战斗
（10 轮 / 43 次行动，我方胜利），`run --args="mechanics"` 三幕演示不变。
引擎侧基础机制（伤害流水线、韧性击破、能量、战技点、
行动条、关卡波次、角色真实面板、敌方召唤物）已完成；**A 层"角色机制 = 数据"的通路也已打通**
（事件 → 触发器表 → 解释器，`engine.md` §4.6/§4.7），剩下的全部是
"把已有口子填满""接数据"或"填内容"。**当前在做的就是"填口子"：`P10-3` 前半已落地**。

> **v3 为什么重写**：v2 是"执行清单"（2400 行，每个已完成任务都留着完整规格），
> 结果**真正待办的事被埋在 2000 行历史里**。v3 只留：**设计原则 + 待办 + 踩坑记录**。
> v2 全文（含所有已完成任务的规格与当时的实测数字）**已于 2026-09-27 删除** ——
> 需要查"当初为什么这么做"时去 git 历史里翻（`git log --diff-filter=D --name-only`）。

---

## 1. 设计原则（改代码前先读这一节）

### 1.1 三条红线

| # | 红线 | 为什么 |
|---|---|---|
| 1 | **引擎不认角色**：`Battle` / `SkillExecutor` / `Damage` / `CanHit` 里**禁止 `cid` 判断** | 93 个角色 × (天赋 + 行迹×3 + 星魂×6 + 秘技 + 追加攻击) 一个角色一个类 = 维护地狱 |
| 2 | **能数据化就数据化**，写不成数据的才允许专用类，且必须在 §1.2 的表里登记 | "机制先行、角色当验收"；顺序永远是**先补引擎能力，再数据化角色** |
| 3 | **一个任务自带测试，做完就绿**；改动前先想"这属于哪个抽象" | 没有测试的修复等于没修；往错的抽象里加代码比不加更贵 |

### 1.2 三分法（P8-0 定的架构总纲）

问"**引擎有没有这个能力**"，而不是"这是哪个角色"：

| 机制长什么样 | 归到哪 | 例子 |
|---|---|---|
| 在某事件后，做一件**引擎已有能力**的事（加成 / 附加伤害 / 真伤 / 回能 / 上 buff / 额外回合 / 削韧 / 治疗 / 护盾 / 资源增减） | **数据**（触发器 + 条件 + 效果） | 知更鸟「队友每次攻击后 +2 能量」、缇宝「我方每命中 1 目标 +1.5」 |
| 需要引擎**还不具备**的能力 | **引擎任务**（先补能力，再做数据） | 「层数替代能量条」（黄泉/飞霄/白厄/昔涟）、「损血/治疗转资源」（遐蝶） |
| 需要跨系统状态机，数据表达不出来 | **逃生舱**：一个 Java 类 + 在下表登记 | 刻律德菈【军功】↔【爵位】双轨 + 目标变更重置充能；万敌【血仇】阈值切换 |

**唯一允许出现 `cid` 的地方**：装配点（`CharacterFactory`）与 provider / 触发器注册表。

### 1.3 逃生舱登记表（每加一个专用类就往这里加一行）

| cid | 角色 | 机制 | 登记于 |
|---|---|---|---|
| 1412 | 刻律德菈 | 【军功】↔【爵位】双轨；目标变更充能重置为 0 | v2 P8-0 |

### 1.4 可扩展性的现有范式（新东西照这个来）

| 轴 | 抽象 | 注入点 | 为什么这样分 |
|---|---|---|---|
| **个人**资源（能量条） | `EnergyProvider`（5 个钩子） | `CanHit.energyProvider` | 能量条是**个人**的 |
| **队伍**资源（战技点） | `SkillPointPolicy` | `Battle.skillPointPolicy` | 战技点是**全队共享**的 |
| 通用队伍级数值 | `Resource`（有界 + 显式溢出 + 原子消耗） | 由上面两者持有 | 战技点与 P8-8 层数资源共用 |
| 技能类型判定 | `SkillCategory` 枚举 | `SkillData.getCategory()` | 消灭裸字符串 `switch` 的**静默失配** |

**验收标准**：新增一个角色机制时，**引擎目录下应该一行都不用改** —— 只加数据、
或只在装配点换一个实现。`SkillPointPolicyExtensibilityTest` 就是这条标准的样板
（不改引擎一行，只换策略就改掉了普攻增量 / 上限 / 开局）。

### 1.5 落地纪律

- **机制先行、角色当验收**：每补一个引擎能力，顺手数据化 1–2 个代表角色，**不做全量**。
- **写完必须变异验证**：把源码改回旧行为，确认新测试**真的会红**。
  本项目已有多次"测试看着绿、其实空转"的教训（见 §5）。
- **文档承诺必须有测试兜着**：只写在 javadoc 里的承诺会腐烂（见 §5 的教训 1）。

---

## 2. 全局约定（写代码时永远遵守）

| 约定 | 内容 |
|---|---|
| 常量 | 所有数值（回能、击破基数、clamp 上限、比率）一律进 `Constant`，代码里不写魔法数字 |
| 随机数 | 暴击 / 目标选择一律走注入的 `java.util.Random`（构造给 seed，可复现），**禁止 `Math.random()`** |
| 测试 | JUnit 5；断言 `assertEquals(expected, actual, 1e-6)`；放 `src/test/java/com/laosun/aluminium/test/`，命名 `XxxTest` |
| 数据 | 真实数据只从 `data/*.json` 经 Gson 读（`Constant` / `JSONReader`），不硬编码；测试/演示里允许手写常量并标 `// TODO data` |
| 单段单目标 | `Damage` 永远是「单段·单目标」；一个技能 = N 个 Damage（AOE→每敌 1 个；BLAST→主目标+相邻；BOUNCE→循环 N 次） |
| 判别口诀 | **有伤害要结算 ⇒ 构造 `Damage`；不造 `Damage` ⇒ 无伤害**（治疗/护盾/回能/上 Buff 不构造 Damage） |
| 每段独立 | 每段伤害独立判定暴击、独立结算 |
| 判断失败 | **数据错误要 fail fast**（如伤害技能缺元素）；**数据不认识要降级**（如未知 `attack_type` → `UNKNOWN` 而不是抛异常）。两者别搞反 |
| 提交 | 任务编号进 commit message；`.\gradlew.bat test` 全绿才提 |

### 2.1 核心类与口子速查

> 完整的机制说明在 **`engine.md`**（那是引擎说明书，与本文分工：**本文说"还要做什么"，engine.md 说"现在是什么"**）。

| 类 | 关键口子 |
|---|---|
| `Battle` | `applyDamage`（**唯一结算入口**）/ `performAction` / `useSkill` / `castUltra` / `startBattle` / `stepForward` / `beforeMove` / `afterMove` / `processRequests` / `grantExtraTurn` / `heal` / `grantShield` / `skillPointPolicy` |
| `models.Damage` | `toValue()` / `breakdown()` / 7 个乘区 accessor / `addBoost` 等装配口 / `crit` / `trueDamage` / `notCountsAsAttack` |
| `models.CanHit` | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / `getLevel` / `getCamp` / `getEnergyProvider` / 事件转发（`BattleEvent`/`MoveEvent`/`DamageEvent`/`AttackEvent`） |
| `models.Resource` | `gainClamped` / `gain`（显式溢出）/ `spend` / `spendExactly` / `setMaxOverflow` / `isFull` / `isCapped` |
| `models.skillpoint.*` | `SkillPointPolicy`（接口）/ `StandardSkillPointPolicy`（基础规则 + `gainForCast` 覆盖点） |
| `SkillData` | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）/ **`getCategory()`** / `getSpNeed()` |
| `Queue` | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `refreshSpeed` / `grantExtraTurn` |
| `utils.CharacterFactory` | `create(cid, level)`（真实角色）/ `usesSpecialResource(cid)` / `exists` |
| `models.EnemyFactory` | `create(monsterId, level, hardLevelGroup)` |
| `utils.StageFactory` | `load(stageId)` |
| `Constant` | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_TRACES` / `MONSTERS` / `SKILL_SLOT` / `stages()`（懒加载） |

### 2.2 数据文件速查

| 文件 | 内容 | 已确认锚点 |
|---|---|---|
| `monster_config.json` | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak, *modify_ratio, damage_resistance, debuff_resistance, summon_id}` | **1002011 冰锋**：弱 `[Fire, Thunder]`，抗 5 项 0.2，`STAT_CTRL_Frozen=1`；**1003010 银鬃尉官**：`summon_id = [1002040, 1002040]`（692/2649 只怪有非空召唤名单） |
| `monster_template_config.json` | 模板基础属性 | **1002011**：atk 18 / def 210 / hp 69.75 / spd 100 / stance 60 / 效果抵抗 0.2 |
| `hard_level_group.json` | `{组: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}` | 组1 Lv90 `{36.821384, 5.238095, 236.53471, 1.32, 1, 0.32, 0.1}` |
| `breaking_rate.json` | 等级 → 击破基数（**原始 10 倍值**） | Lv80 = 3767.5535 → 公式里 `/10` |
| `skills.json` | `cid → {槽位 → {attack_type, max_level, param_list, skill_effect, stance_list, element, sp_need, sp_base}}` | 槽位 1 普攻 / 2 战技 / 3 终结技 / 4 天赋 / 6 地图普攻 / 7 秘技；**敌人技能不在这里** |
| `character_data.json` | `cid → {name, attribute, mt, max_energy, aggro, ..., rarity}` | 景元 1204：thunder / 能量 130 / aggro 75 / hp 158.4 |
| `stage.json` | `stage_id → {type, hard_level_group, level, monster:[...]}`（9 MB，**懒加载**） | `103201`：level 29 |
| `monster_attack_modify_ratio.json` | **本项目补丁**（444 条 ≠1） | 见 §4 陷阱 2 |
| `enemy_skills.json` | **自建**（P5-3 起），只覆盖 5 只演示怪，倍率是猜的 | 见 §4 陷阱 7 |

### 2.3 机制文档（公式有疑义时以它为准）

| 文档 | 内容 |
|---|---|
| `E:\code\blog\hsr\HSR.md` | 乘区总览（§2）、行动/破韧/仇恨（§3）、治疗护盾（§4）、忆灵（§5）、欢愉（§6）、超击破（§7） |
| `E:\code\blog\hsr\GLOSSARY.md` / `GLOSSARY_EXTRA.md` | 游戏名词表（含官方描述原文） |
| `E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md` | **93 个角色文档**（用户明确许可读），含天赋/行迹/星魂/秘技原文 |
| `E:\turnbasedgamedata\aluminium_texts\{RELICS,WEAPONS,GLOSSARY,GLOSSARY_EXTRA}.md` | 遗器 / 光锥 / 名词总表 |

> ⚠ `HSR.md` **不是完整的规格**：它没有战技点这一节，能量学（§3.3）只有一行。
> 缺失部分靠游戏机制攻略 + 角色文档反推，**并且要在代码里注明"这是反推的"**。

### 2.4 已核对的公式结论（别再重新推导）

- §2.2 伤害修饰区 = 增伤 `1+Σ` × 易伤 `1+Σ`（cap **3.5**）× 减伤 `Π(1-r)`（floor **0.01**）× 虚弱 `1-Σ`（floor **0.2**）
- §2.5 抗性区 = `1 - 抗性`，范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`；**负抗全效**（不是半效）
- §2.4 防御区 `(200+10L)/(def+200+10L)`，`def = 原始防御 × (1 - 减防% - 穿透%)`（**加算**），上限 1、不为负
- §6.4 欢愉伤害**吃双爆、不吃增伤** ⇒ `DamageType.ELATION(isCrittable=true, isBoostable=false)`
- §7.2 超击破不吃攻击力/常规增伤/双暴，吃等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤
- §7.1 **两套刻度不可混用**：80 级基础击破基数 3767（削韧单位 1）vs 超击破 376.7（削韧单位 10）
- §2.2 增伤/易伤区各自都是「伤害类型 + 攻击类型」**加算进同一个区**（所以"战技/终结技增伤"灌 `BoostArea`，不新开区）
- §4 治疗 `= 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)`；护盾 `= 基础 × (1 + Σ护盾量提高)`

---

## 3. 进度总览

### ✅ 已完成（v2 的 P1–P7、P8-0/1/2/4）

| 块 | 内容 |
|---|---|
| 伤害流水线 | 属性代数 + 完整乘区（增伤/易伤/减伤/虚弱/暴击/防御/抗性）+ 唯一结算入口 + 12 种伤害类型 |
| 技能 | 真实槽位映射（`Constant.SKILL_SLOT`）+ 等级取行 + 展开（单体/AOE/扩散/弹射）+ 每段独立结算 |
| 韧性击破 | 削韧 / 击破伤害 / 推条 / 跳回合 / DOT（4 系）/ 超击破 |
| 能量 | 字段 + 回能效率 + 5 个钩子 + 开大阈值（读 `sp_need`）+ 清零回能 + 6 个特殊资源角色 |
| **战技点** | **全队共享池 + 可替换策略 + 通用 `Resource`**（见 §5） |
| 行动条 | 绝对时间 + 堆 + E1–E4 四处修正（含同值平局裁决）+ 额外回合 + 速度变化立即重排 |
| 关卡 | `stage.json` 懒加载 + 波次逐波进怪 + `StageFactory.load` + 难度组 |
| 敌人 | 真实面板（模板 × 等级组 × 实例 × 精英组）+ 仇恨选目标 + 会普攻 |
| 角色 | `CharacterFactory.create(cid, level)` 真实面板（等级缩放/光锥/遗器/行迹/额外加成/元素/命途/仇恨/能量/真实技能） |
| 其他 | 效果命中与抵抗 / 治疗 / 护盾 / Buff 生命周期 / 附加伤害与真实伤害 / 胜负状态机 |
| **事件体系** | **11 个事件家族**（P8-6）：技能施放 / 能量 / 掉血 / 治疗 / 击杀 / 击破 / 战技点增减，见 `engine.md` §4 |
| **触发器表** | **角色机制 = 数据**（P8-7）：`resources/characters/<cid>.json`，引擎只解释；已跑通缇宝 1403 / 知更鸟 1309 / 克拉拉 1107 / 希儿 1102，见 `engine.md` §4.6。**知更鸟 1309 是第一个把行迹（额外能力）也数据化的**：天赋 + 2 条行迹共处一张表，**零引擎改动**（`RobinTraceTest`，含"做她时发现 M-24"）。规则可带**限额**：`cooldown`（自己的回合数）/ `once_per_battle`，计数在战斗单位上、开局清空（`TriggerLimitTest` 10 条）。**具名状态**也接线了：`APPLY_BUFF` 施加「进入【XX】状态」，`self/target has_state XX` 读它 —— 状态就是"带名字的普通 buff"（`TriggerStateTest` 11 条） |
| **层数资源** | **没有能量条也能开大**（P8-8）：`Resource` + `ResourceManager` + `EnergyProvider.canCastUltra` 闸门，见 `engine.md` §23 |
| **真实队伍** | **关卡用真角色**（P8-5）：`StageFactory.realTeam()` = 景元/希儿/克拉拉/娜塔莎（4 命途），各带本命途 5★ 光锥；占位队已删除 |
| **天赋与追加攻击** | **天赋 = 数据**（P8-3）：触发器表新增 `DAMAGE` op，倍率取**天赋槽**的 `damage_param`；克拉拉受击反击、希儿击杀再动，见 `engine.md` §4.7 |
| **攻击类型增伤** | **普攻/战技/终结技各自增伤**（2026-09-27）：三条新属性加算进同一个增伤区，按 `Damage.getCastCategory()` 选 —— `Damage` 现在显式携带施放类别，因为普攻与战技都是 `DamageType.NORMAL`，靠 type 分不开。同日接上 `REMOVE_STACK`（取回叠层）后，**遗器套装 131「星如我见的领航员」已整条建模**（`relic_sets/131.json`，从未建模登记里移出：28→27），见 `engine.md` §18.2 |
| **易伤 / 减伤** | **受到的伤害提高/降低 = 数据**（2026-09-27）：`MODIFY_DAMAGE_TAKEN`，**正负号决定哪个乘区**（正=易伤、负=减伤），两个 buff 类各自拒绝错号。此前 `MODIFY_ATTR` 够不着它们 —— 它们是乘区不是属性。遗器套装 106「戍卫风雪的铁卫」2 件套因此可建模（未建模 27→26） |
| **按血条缩放的治疗/护盾** | **「恢复等同于生命上限 X%」= 数据**（2026-09-27）：`HEAL` / `SHIELD` 支持 `scale` + `percent`，两种写法封闭 —— `target_max_hp`（**受治疗者**自己的血条）与 `owner_max_hp`（**规则持有者**的血条，即 `skill_effects.json` 里的 `healer_max_hp`）。量在**目标循环里逐人算**（我方全体回 8% 是每人各自的 8%，不是一个数）。遗器套装 106 的 4 件套因此可建模（未建模 26→25） |
| **负面效果** | **「解除 N 个负面效果」+「有 X% 固定概率」= 数据**（2026-09-27）：新增 `DISPEL`（最新的先走，取不到不算错）、条件变量 `target_debuff_count`、规则字段 `chance`（掷骰用战斗注入的 `Random`；**掷骰失败不消耗冷却**）。"什么算负面"由每个 buff 类回答（`AbstractBuff.isDebuff()`，默认 false 是安全方向），五个 debuff 类的答案在 `DebuffTest` 里列表钉住。克拉拉 1107 的 家人 行迹（「受到攻击时 35% 固定概率解除自身 1 个负面效果」）是第一个用户 |
| **星魂** | **星魂机制 = 数据 + 一道门槛**（2026-09-27）：`CharacterFactory.create(..., eidolonRank)` 由装配点决定生效等级（0–6，越界直接抛），规则字段 `min_eidolon: N` 让"这条属于星魂"写在**机制旁边**；引擎依旧不认识星魂，`eidolons.json` 只作溯源材料（规则 `source` 引用它）。第一个用户是**布洛妮娅星魂 1**（50% 概率 +1 战技点、1 回合冷却）—— 即 `F-4` 里挂了很久的那条，见 `engine.md` §9 |
| **敌方召唤物** | **召唤物能真的上场了**（2026-09-27，P9-4）：`monster_config.summon_id`（**692/2649 只怪有非空名单**）→ `Enemy.summonIds` → `SummonFactory`（复用 `EnemyFactory.resolve` 的缩放链）→ `Battle.summon(master, summonId, group)`；**主人倒下带走它**。⚠ 两处别记反：①退场**不付击杀奖励**，原因是这次清扫跑在 `Battle.applyDamage`（唯一结算入口）**之外** —— 不是"`takeDamage` 会发事件"（它一个都不发，这正是一条被变异测试纠正的错判，见 `engine.md` §6.2）；②`CanHit.perish()` 只负责"别谎报它受了伤"（**HP 不变**）。只做**敌方**阵营：我方主人被**响亮拒绝**（`battle.characters` 是 `List<Character>`，塞进 `enemies` 会让我方召唤物被自己人打、还按敌人算胜负）。`SummonTest` 18 条，见 `engine.md` §24 |
| **伤害实例条件** | **「对处于 X 状态的目标造成的伤害提高」= 数据**（2026-09-27）：新增结算**前**事件 `DEALING_DAMAGE`（唯一携带 `Damage` 的事件）+ `BOOST_DAMAGE`（只改这一次，不挂 buff、不漏到下一击）；`has_state` 同时认下**四种 DoT 状态名**（灼烧/触电/裂伤/风化 = `DotBuff(element)`，不是 `StateBuff`）。这一条家族是普查里最大的（约 60 个行迹节点），此前完全无法表达 |
| **DOT 并入 buff 体系** | **DOT 不再是第二套机制**（P10-0）：`DotBuff extends AbstractBuff`，删掉 `models/Dot` / `Enemy.dots` / `tickDots(Enemy)`；角色与敌人走同一条 DOT 路径，demo 数值逐位不变，见 `engine.md` §8.5 |
| **击破控制状态** | **控制 = 数据组合，不是新类**（P10-2）：`ControlEffect` 表 + `StunBuff`/`StatModifierBuff`/`delayMovePercent` 三个现成原语；冰=锁行动、量子/虚数=减速+推条；**冻结"+30% 受伤"被数据推翻**（原文是每回合冰伤），见 `engine.md` §8.6 |
| **推条不再被速度变化吃掉** | **L-26 已修**：`delayAction`/`advanceAction` 同步 `Signal.remaining`，`refreshSpeed` 去掉进度**上**钳（被推条的单位合法地超过一整轮）。之前量子/虚数击破的"减速+推条"里推条完全不可观测（28.409 加不加都一样）；现在 `QueueActionManipulationTest.aDelaySurvivesASpeedChange` 钉住，**两半各自都能让它变红**，见 `engine.md` §5.1/§5.2 |
| **敌方阵营不再只收怪** | **L-8 一半已修**：`Battle.enemies` = `List<CanHit>`（构造器拷贝，不再别名调用方 list），`enemyUnits()` 才是其中的 `Enemy`；`targetableEnemies()`/`aliveEnemies()` 一并放宽，胜负判定按**整阵营**。敌方召唤物从"类型上无处安放"变成"收得下"，`EnemyCampSummonTest` 4 条钉住，见 `engine.md` §7 |
| **机制演示** | **把新做出来的东西真打一遍**（P11-1 入口那半）：`gradlew run --args="mechanics"` 三幕 —— 击破三系控制状态（冰锁行动 / 量子·虚数减速+推条，打印速度与行动值变化）、**DOT 挂在我方角色身上**由引擎结算并到期、**敌方阵营里的召唤物**（可被 AOE 打到、且它站着就不算赢）。默认无参跑的原战斗 demo 输出逐字不变；`DemoSmokeTest` 4 条钉住"都还跑得起来"。顺带删掉 `StunBuff.removeBuff` 里那行 `IO.println`（引擎往 stdout 打印，会插进调用方输出中间） |
| **技能几率可读** | **描述文本自己说了下标**（P10-6）：`SkillData.debuffChance()` 按"紧挨着 基础概率/固定概率 的占位符"定位，5 个真实样本标定（0.6 / 0.5 是关键），28 条 `Impair` 全覆盖 —— 计划里"取 `param_list` 第 3 项"会读出 15（秒数）并夹成"必定命中" |

### 🚧 部分完成

| 项 | 现状 | 归属 |
|---|---|---|
| 技能回能数据化 | `sp_base` 已落库但**不驱动回能**（多段技能是"每段值"，需聚合 `SPHitRatio`） | P3-4 剩余 |
| 战技点**上限**可变（F-1） | **接口已就位**（换策略构造参数），**没有接线** | 见 §12.5 的 F-1 |
| 开局战技点可变（F-2） | ✅ **已接线**：过客 4 件套开局 **3 → 4**（套装 ability 走触发器表，有用例钉住） | — |
| 强化普攻的战技点 | 一刀切 +1：对青雀对、**对波提欧错** | F-3，数据补全 |
| 敌人技能不发事件 | `EnemySkill` 不走 `SkillExecutor`，故不发 `SkillCastEvent` | P9-2 对齐 |
| **非伤害技能分派** | ✅ **已修**（P10-3 引擎侧）：`resolveHits` 不再静默 return，`dispatchNonDamaging` 按 `skill_effects.json` 分派 `Restore`/`Defence`（26/40 条可用），`Main` 的手搓算术已收回引擎。`SUMMON` **能力侧已补**（P9-4：`Battle.summon`），仍缺"这次召唤召谁"的数据列；`BUFF`/`CONTROL` 与另外 14 条缺数据 | **P10-3 引擎侧完成** |

### ☐ 待办（见 §7–§9；`P10-3` 引擎侧已完成、`P10-6` 前半完成）

**A 层（角色数据化的基础设施）全部完成** ✅ —— P8-6 事件、P8-7 触发器表、P8-8 层数资源、
P8-5 真实队伍、**P8-3 天赋 + 追加攻击**。

> **P8-3 的教训（值得记）**：它被一个**假前置**卡了很久 ——
> "数据里没有 `is_follow_up` 字段，所以判断不了哪个技能算追加攻击"。
> 真相是**不需要那个字段**：追加攻击的 payload 本来就在**天赋槽自己的技能数据**里
> （元素/削韧/每级倍率齐全），唯一缺的是"**什么时候**放"—— 而那正是触发器表提供的。
> 结论与数据事实见 `engine.md` §4.7。

**当前状态：引擎侧的"洞"基本补完了，剩下的大多卡在数据源。**
2026-09-26 逐条核对 §8 的待办后发现**四条过时的状态描述**（P10-3 说"卡在参数表上"、P10-4 说"公开 API 还没上移"、
P10-1 说"缺三个控制系"、P10-6 说"几率取 `param_list` 第 3 项"），已全部按代码与数据更正。核对后的真实缺口：

1. **数据补全**（`generate_data.py`）：`skill_effects.json` 里 14/40 条 `params` 为空；
   `BUFF`/`CONTROL`/`SUMMON` 技能没有"施加什么"的表；`enemy_skills.json` 的倍率仍是猜的
   （这是 P9-1/P9-3 的阻塞点）。
   ⚠ **动它之前先确认版本**：脚本的版本控制在 **GitHub gist** 上（README 有链接），
   但 **gist 落后于本地副本** —— `skill_effects.json` 那段只在本地、任何地方都没有历史，
   改了没人知道。先推回 gist 再改。
2. **引擎侧仅剩的几处**：P10-4 的拉条 API 二选一、P10-5 的验收补齐、光锥数值被动接线、
   P11-1 的 `Main` 拆分（⚠ 更正：`Main` 现在已有 `main(String[] args)`，且原无参版在 JDK 25 上
   本来就能启动 —— 见 P11-1）。
3. **`Main` 的定位**：它现在是"demo 的 AI + 打印"，不该再承担引擎算不出来的东西 —— 这条已经做到了。

> A 层剩下的"93 个角色的天赋/行迹/星魂/秘技"是**内容活**，不再是引擎任务：
> 一次数据化 1–2 个角色（`source`/`note` 标出处），引擎缺能力时才回头补引擎。
> 目前 93 个里只有 4 个有触发器表。

---

## 4. 踩坑记录（实测，别再踩）

### 4.1 怪物数据陷阱

| # | 陷阱 | 事实 | 处理 |
|---|---|---|---|
| 1 | **`hp_modify_ratio` 是幽灵字段** | tbgd 只有 `HPModifyRatio`，**没有** `HealthModifyRatio`；解析器猜错键名后留了个恒为 1 的字段。真实血量系数在 `health_modify_ratio` | bean 不读幽灵字段 |
| 2 | **`attack_modify_ratio` 未导出** | tbgd 里 444/2649 个怪 ≠1，本数据整体缺这一列 | 补丁 `monster_attack_modify_ratio.json` + `Constant.normalizeMonsterConfigs` 合并。**正解在导出脚本**：`monster_config.json` 自带该列后删掉补丁与合并代码 |
| 3 | **效果命中/抵抗是加值不是系数** | 组1·Lv90 给 0.32 / 0.1，冰锋模板 0.2 → **0.2 + 0.1 = 0.3**；相乘会得 0.02 | bean 字段名去掉 `Ratio` 后缀 |
| 4 | **等级组的键名 ≠ bean 字段名** | JSON 是 `attack/defence/health/speed/stance`，bean 写成 `attackRatio` 之类 Gson **静默读成 0** | 一律 `@SerializedName` |
| 5 | **组号/等级来自关卡** | 怪的 `hard_level_group` 通常是 1；真正决定难度的是 `StageConfig` | 调用方显式传参 |
| 6 | **精英组有两张表** | 普通关卡 `EliteGroup`；无限波次用 `InfiniteEliteGroup`（绝境王虫 ×6.2）。用错血量差几倍；两者都来自**波组** | 本数据暂无 `elite_group.json`，缺省 1 |
| 7 | **敌人技能数据源缺失** | tbgd **没下发**怪物技能表（`skills.json` 只有角色） | `enemy_skills.json` 自建隔离；目前只覆盖 5 只演示怪、**倍率是猜的**（每条标 `guessed=true`） |
| 8 | **`stage.json` 9 MB / 2.9 万条** | 塞进静态块等于每次 `Constant` 初始化多付 ~35 MB 堆 + 72 ms | 懒加载 `Constant.stages()`，**缺失时返回空表而不抛异常** |
| 9 | **`summon_id` 里的 `0` 是"没有召唤物"，不是怪物 0** | 该列是列表（1957 只怪是 `[]`，692 只有内容），但 405301004 的列表恰好是 `[0]` | 装载期（`normalizeMonsterConfigs`）只保留正数；**不要**改成"跳过查不到的 id"——那会让数据错误与"没有召唤物"长得一样。`SummonTest` 钉住 |

> ⚠ `monster_attack_modify_ratio.json` 在 `.gitignore` 排除的 `data/` 里，但**已 `git add -f`**
> （否则新克隆缺文件、`MonsterDataTest` 直接红）。

### 4.2 Gson 静默失败（本项目历史重灾区）

新增 bean 字段一律加 `@SerializedName` 并**核对 JSON 键名**。`enemy_skills.json` 的
`hits` 字段语义是"目标数（0=全体）"、`monster_config` 的 `damage_resistance`
与 `debuff_resistance` 是**两个不同的 map**（元素 / `STAT_*`）—— 都踩过。

### 4.3 工具链（PowerShell / Gradle）

- `git` 的进度输出走 stderr，PowerShell 会报成 exit code 1 —— **那不是失败**。
- `git push` 后若 `git fetch` 超时（网络抖动），用 GitHub API 核对：
  `Invoke-RestMethod https://api.github.com/repos/.../commits/main`。
- **Gradle 测试会缓存**：只改源码不碰测试文件时，`gradlew test` 可能直接 `UP-TO-DATE`
  而不真跑。要确认结果必须加 `--rerun-tasks`。
- 变异验证时尤其注意上一条 —— 否则会误判"测试没抓到"。
- `pwsh` 渲染中文正常；Windows PowerShell 5.1 会糊成乱码，**一律用 `pwsh`**。
- PowerShell 的 `>` 重定向会写成 UTF-16（会把 Java 文件写坏）；用
  `[System.IO.File]::WriteAllText(..., UTF8Encoding($false))` 或 fs 工具。

### 4.4 数据探测（别用 `ConvertFrom-Json` 读 1.5 MB 的 `skills.json`）

它会因编码/体积失败并把整个文件吐到控制台。用正则直接数：

```powershell
$t = [System.IO.File]::ReadAllText('src/main/resources/data/skills.json')
([regex]::Matches($t, '"sp_need":\s*null')).Count
```

> ⚠ 正则里的 `\s*(?!null)` 这种**负向先行断言会因回溯而失效**（我先写错了一版，
> 数出"638 条全部非 null"）。要数"非 null"就直接匹配取值：
> `'"sp_need":\s*[1-9]'`。

---

## 5. 上一轮的教训（P8-4 复核 + 重构，2026-09-23）

这一节是**方法论**，比具体任务更值得记住。四件事里有三件是"引擎看着对、其实错"。

| # | 教训 | 具体经过 |
|---|---|---|
| 1 | **文档承诺必须有测试兜着** | `SkillCategory` 第一版只在 javadoc 写了"大小写不敏感"，实现里**建表存原值、查表转小写** → 永远查不到。被 `knownValuesRoundTrip` 抓到 |
| 2 | **不变式要么写死要么别写** | `Resource.setMaxOverflow` 第一版只改额度**不重新夹值** → 能造出 `max=5, overflow=0, value=15` 的**静默非法状态**，之后所有 `isCapped`/`gain` 判断失准且不报错 |
| 3 | **⚠ 最危险的是"在不该沉默的地方沉默"** | 我一度让 `applySkillPointCost(skill)` 从 `currentMove` **猜**出手者，"没有行动者"时猜出 `null`，而 `null != Camp.PLAYER` 让策略**静默变成空操作** —— 一个**不报错的错误答案**。已改成显式传参，误用直接编译不过 |
| 4 | **"空转的测试"比没测试更坏** | 拿敌人默认的 `EnemySkill` 测"敌方不影响战技点"——它的 `getData()` **恒为 null**，去不去掉阵营判断都会绿。靠变异测试才发现。已改成手工给敌人装真实角色普攻 |

另外两次**自我更正**（都已撤回，记在这里防止重犯）：

- 我曾说"技能等级没接进伤害"——**错**。`SkillExecutor` 一直用 `getLevel() - 1` 取行；
  我构造了 8 级技能却断言 `getSkills().getFirst()` 是 1.2，而 `getFirst()` **永远是第 1 档**。
  **把自己取错行当成了引擎没取行。**
- 我曾说 `models/Buff.java` 是死代码并删了它 —— **错**。grep 过滤器 `Buff\b` 把
  `AbstractBuff` 里的 `Buff` 也滤掉了，而 **`AbstractBuff implements Buff`**。

**可复用的做法**：写完修复 → 把源码改回旧行为 → 确认测试**真的红** → 再改回来。
本项目对 P8-4 做过 6 处变异验证（null 保护 / 阵营判断 / Normal 不给点 / BPSkill 不扣点 /
大小写敏感 / 溢出夹取），**每一处都确认了护栏有效**。

---

## 6. A 层：角色数据化的基础设施 ✅（全部完成）

> **为什么这层曾是最优先**：93 个角色的天赋/行迹/星魂/秘技/追加攻击原本**全是空的**。
> 引擎能给角色的只有普攻/战技/终结技/能量/战技点 —— 而这一层是
> "让角色机制变成数据"的**唯一通路**。这层做完了，才有资格谈"像 HSR"。
>
> 现在通路是通的：`Battle` 发事件 → 角色自带的 JSON 表匹配条件 → 解释器执行引擎已有能力。
> **接下来缺的不是引擎，是内容**（93 个角色里只有 4 个填了表）。

**本层顺序**：`P8-6`（事件）→ `P8-7`（触发器表）→ `P8-8`（层数资源）→ `P8-3`（天赋 + 追加攻击）。
`P8-3` 原本"建议并进 P8-7 做"，实际做下来确认它对 `P8-7` 的依赖是**真的**
（只需要补一个 `DAMAGE` op），而它自己那个"数据前置"是**假的**（见该任务）。

---

### P8-6 事件补齐（触发器宿主）✅

- **目标**：把 12 类触发源都变成事件，让"角色机制"只订阅事件，不再往 `Battle` 里塞逻辑。
- **结果**：事件从 **4 个扩到 11 个**（2026-09-23 完成），新增
  `SkillCastEvent` / `EnergyEvent` / `HpLossEvent` / `HealEvent` / `KillEvent` / `BreakEvent` /
  `SkillPointEvent`（战技点增减：2026-09-26 由 `SkillPointGainedEvent` + `SkillPointSpentEvent`
  合并成一个接口，两个方法都是 `default`，粒度不变）。
  完整口径见 `engine.md` §4（含**广播口径**与**每个事件的口径**两张表）。
- **涉及文件**：新建 `models/event/` 下 8 个接口（战技点那两个后来合并成 `SkillPointEvent`，现为 7 个）；
  `models/CanHit.java`（实现并转发给 `BuffManager`）；
  `models/BuffManager.java`（逐个 `instanceof` 转发）；`Battle.java`（发事件 + 广播）；
  `models/SkillExecutor.java`（发 `SkillCastEvent`）；`models/skillpoint/StandardSkillPointPolicy.java`
  （上报实际增减）；新建 `test/EventBusTest.java`（20 条）

**实际落地与原计划的差异**：

1. **`TurnStartEvent` 没做，也没必要做**：回合开始/结束已经由 `MoveEvent.beforeMove/afterMove`
   表达（`CanHit` 已实现并转发）。再加一层是**重复的抽象** ——
   已用 `EventBusTest.turnBoundariesAreStillMoveEvent` 钉住，防止以后有人再加。
2. **战技点用"策略上报"而不是 `Battle` 比前后值**：让 `StandardSkillPointPolicy` 把
   **实际**增减上报给 `Battle`（`StandardSkillPointPolicy.Listener`）。理由：
   策略是唯一知道"这次到底涨没涨、涨了多少"的组件（满点时实际入账是 0，不该发事件），
   而 `Battle` 用"前后值相减"去**猜**会在满点/失败时静默判错。
   这也保住了 F-8 的结论：**`Battle` 依然不知道战技点的规则**。
3. **广播口径统一成三条规则**（相关方总是收到含敌人 / 我方额外全员 / 我方去重），
   避免下一批事件又出现第三种写法；`SkillPoint*` 两个事件是例外（只投我方）。
4. **`HpLossEvent` 的口径定义为"真的掉了多少血"**，不含被护盾吸走的量。
   这个选择让"损血转资源"（遐蝶/万敌/刃）与"受到伤害"两个口径分开 ——
   想要后者请用 `applyDamage` 的返回值。
5. **敌人技能暂不发 `SkillCastEvent`**：`EnemySkill` 有自己的 `execute`（不走
   `SkillExecutor`），等 P9-2 接进统一执行器时对齐。已记在 `engine.md` §4.4。
6. ⚠ **原计划的一条验收标准是错的，已更正**：计划写"DOT/附加伤害**不发** `KillEvent`"，
   但 `tickDots` 走的是 `Battle.applyDamage(..., KILL_ONLY)` —— 与普攻同一条路径，
   **会发**。而且"会发"才对：姬子「终结技每消灭 1 敌 +5 能量」需要知道
   DOT/附加伤害补刀也算消灭（`KILL_ONLY` 只影响回能种类，不影响"死亡"这个事实）。
   已按**实测行为**写测试，并把更正记进 `engine.md` §4.5。

**变异验证**（3 处，都确认护栏有效）：

| 变异 | 结果 |
|---|---|
| 只在打中目标时才发 `SkillCastEvent`（模拟"没打中就不发"） | `nonDamagingSkillStillFiresSkillCast` 红 ❌ |
| 去掉"真的掉血才发"的门槛 | `fullyShieldedHitFiresNoHpLoss` 红 ❌ |
| 战技点消耗失败也上报 | `failedSkillSpendFiresNoSkillPointSpent` 红 ❌ |

**验收**：`EventBusTest` **22 条**全绿；全套 47 套 / 403 例全绿；demo 行为不变（10 轮 / 43 次行动）。
- **依赖**：P1-7（事件模式已成型）

<!-- 以下为原始计划，保留作为对照 -->

- **原始方案**：新建 `models/event/{SkillCastEvent, EnergyEvent, HpLossEvent, HealEvent, KillEvent, BreakEvent, TurnStartEvent}.java`；
  照 `AttackEvent` 的模式：接口 + 全部 `default` 空实现 + `CanHit` 转发 `BuffManager` + `Battle` 广播给友方。
  每个事件必须携带**足够还原事实**的字段；`TurnStartEvent` 已被 `MoveEvent` 覆盖（见上）。

---

### P8-7 触发器表 + 效果词表（角色内容数据化）✅

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
       `GAIN_ENERGY` / `GAIN_RESOURCE` / `SPEND_RESOURCE` / **`GAIN_SKILL_POINT`** / `APPLY_BUFF` /
       `EXTRA_TURN` / `ADVANCE` / `REDUCE_TOUGHNESS` / `HEAL` / `SHIELD`；
       每个 op 一个 `record` 实现，`switch` 只在解释器里。
    3. 装载：`CharacterFactory` 造角色时把该 cid 的表挂成触发器列表；**未登记 = 空表（不是错误）**。
    4. **战技点的角色级修正从这里进**：覆盖 `StandardSkillPointPolicy.gainForCast`
       （布洛妮娅「战技 50% 概率 +1」、素裳「打击破目标 +1」、青雀「争番单场一次 +1」、
       花火「上限 +2」），或由效果表驱动 —— ⚠ **不要回头去改 `Battle`**。
    5. 数据源：角色文档（`E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md`）+ tbgd 技能文案；
       每条效果写清出处，数值缺失标 `TODO data`。
- **验收**：`TriggerTableTest`：**不写任何 Java 角色类**，纯数据跑通两个真实角色 ——
  缇宝 1403（我方每命中 1 目标 +1.5 能量）与知更鸟 1309（队友每次攻击 +2 能量）；
  再补一条"未登记角色 = 空表、战斗不炸"。
- **依赖**：P8-6、P3-1（能量入账口）

**实际落地（2026-09-23）**：机制说明见 `engine.md` §4.6。四个组件职责分开 ——
`beans.TriggerSpec`/`EffectSpec`（JSON 形状）、`models.TriggerTable`（编译+校验+匹配，**无副作用**）、
`models.TriggerInterpreter`（执行，唯一碰引擎状态处）、`data.TriggerTables`（按 cid 懒加载+缓存）。
与原计划的差异：

1. **`per_target` 开关是计划里没有的，但必须有**。计划把知更鸟的「+2」与缇宝的「+1.5」
   都写成 `GAIN_ENERGY amount`，但两者**口径不同**：知更鸟是**每次攻击平摊 2**，
   缇宝是**每命中 1 个目标 1.5**（打 3 个给 4.5）。差别就是一个命中数倍。
   所以效果带一个**显式**的 `per_target`（默认关）—— 不让解释器"看到攻击事件就自动按
   命中数放大"，那样知更鸟会被算成 `2 × 命中数`。
2. **`ALLY_ATTACK` 与 `SKILL_CAST` 在同一处派生**（都从 `SkillExecutor`）。角色文本会区分
   「施放战技后」与「我方目标攻击后」，而目前引擎的"施放"就是"攻击"，
   所以同时发两个事件，由数据的 `when` 去区分。
3. **条件 DSL 的变量是封闭集合**（`self` / `actor` / `hit_count`），写错变量名在**加载时**报错。
   这样"我的条件写错了"与"引擎压根不发这个事件"能分开诊断 —— 后者也被
   未接线事件在加载时拒绝并**点名归属阶段**。
4. **只对我方开火**：带主体的事件走 `fireTriggersForAlly` 做阵营判断 ——
   敌人挨打不该让我方角色被触发两次，且敌方内容属 P9。
5. **递归护栏**：触发器效果本身会再发事件（治疗 → 治疗类 buff → …）。引擎不判环，
   而是限制嵌套深度（`MAX_TRIGGER_DEPTH = 8`）并**响亮报错**，让跑飞的表被抓住而不是挂死战斗。
6. **六个 op 仍被拒绝**：`GAIN_RESOURCE`/`SPEND_RESOURCE`（缺 `ResourceManager`，属 P8-8）、
   `MODIFY_ATTR`（要 P10-3 持有并到期 modifier）、`APPLY_BUFF`、`ADD_DAMAGE`/`TRUE_DAMAGE`、
   `REDUCE_TOUGHNESS` —— 一律在加载时报错点名阶段。**宁可响亮拒绝，也不要静默无效**。
7. **没有文件 = 空表**（`TriggerTable.EMPTY`，`Character.triggerTable` **永不为 null**）；
   **文件存在但写错 = 加载即抛**。`TriggerTables.loadCount()` 可观测，测试据此断言缓存生效。

**验收**：`TriggerTableTest`（20 条）+ `TriggerDataBindingTest`（5 条）—— **不写任何 Java 角色类**，
纯数据跑通两个真实角色：缇宝 1403 与知更鸟 1309；含"未登记角色 = 空表、战斗不炸"。

**变异验证**（4 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| 忽略 `per_target`（一律当平摊值） | `tribbieGainsPerTargetHitWhenAnAllyAttacks` 红 ❌ |
| 条件恒为真（`actor != self` 失效） | 4 条红（两条"自己攻击不触发"、条件判别、`self` 简写）❌ |
| `CharacterFactory` 不挂表 | 5 条红 ❌ |
| JSON 键写成 `perTarget`（Gson 静默失配） | `perTargetSurvivesJsonBinding` 红 ❌ |

**全套**：49 套 / 428 例全绿；demo 行为不变（demo 队里没有触发器文件）。

---

### P8-8 层数资源 Resource（替代能量条）✅

> ✅ **前置已完成**：`models/Resource.java` **已经存在**（P8-4 重构时为战技点抽出来的），
> **不要**再新建同名类。已有：有界值 + `gainClamped`/`gain`（显式溢出）/`spend`/`spendExactly`、
> `setMaxOverflow`、`isFull`/`isCapped`/`missingToMax`、不变式 `value ∈ [0, max+overflow]`（有测试）。
> 战技点是它的第一个用户，**本任务是第二个**。
>
> ✅ **P8-7 预留、本项已解锁**：`GAIN_RESOURCE` / `SPEND_RESOURCE` 已从
> `TriggerInterpreter` 的 `PLANNED` 名单挪到 `WIRED` 并实现 —— 触发器的形状没动一行。

- **目标**：让"层数当能量 / 层数触发大招"的角色（飞霄【飞黄】/ 黄泉【残梦】/ 白厄【火种】/
  昔涟【追忆】/ 遐蝶【新蕊】）不写专用类。
- **涉及文件**：**扩展** `models/Resource.java`、新建 `models/ResourceManager.java`；
  `CanHit.java`（挂 manager）、`Constant.java`、`TriggerInterpreter.java`（解锁两个 op）、
  扩展 `test/SkillCategoryAndResourceTest.java` 或新建 `test/ResourceTest.java`
- **怎么做**：
    1. `Resource` 加 `scope`（`SELF` / `PARTY`）；`ResourceManager` 提供 `gain/spend/get/isFull`，
       满了**不溢出**并发"满"事件（`onFull` 效果仍归触发器表，别硬编码）。
    2. **资源不等于能量**：黄泉是"【残梦】满 9 才能放大招" —— 用 `EnergyProvider` 把资源接到
       `isEnergyFull()`/`castUltra` 上（P3 已把这条口子留成 provider），**不要**改 `CanHit` 的能量语义。
    3. 三个来源先做：事件触发获得（P8-7 `GAIN_RESOURCE`）、主动消耗（`SPEND_RESOURCE`）、
       转化（损血/治疗 → 资源，需要 P8-6 的 `HpLossEvent`/`HealEvent`）。
    4. ⚠ **昔涟 1415 特殊**：她是【追忆】**24 → 进入【往昔的涟漪】后 12**，
       池子 24 / 可溢出到 27（`engine.md` §9.5）。**溢出的口子已经有了**（`setMaxOverflow`）。
- **验收**：`ResourceTest`：加满触发"满"且不溢出；消耗到 0；`HpLossEvent` 下"每损失 1 点生命 +1 资源"正确；
  测试替身 provider 让"资源满 → `castUltra` 可用"。
- **依赖**：P8-6、P8-7

**实际落地（2026-09-23）**：机制说明见 `engine.md` **§23**。三个部件 ——
`Resource`（P8-4 已存在）+ `ResourceManager`（每个 `CanHit` 一个，**永不为 null**）
+ **`EnergyProvider.canCastUltra(user, cost)` 闸门**。与原计划的差异：

1. ⚠ **闸门必须挂在 `EnergyProvider` 上，而不是"把资源接到 `isEnergyFull()`"**。
   计划写的是接 `isEnergyFull()`/`castUltra`，但真正卡住层数角色的是
   `isUltraReady` 开头那句 `!user.hasEnergyBar() → return false` ——
   而他们的**能量恒为 0**（`NoConventionalEnergyProvider` 全返回 null），所以会被**永久锁死**。
   现在 `isUltraReady` 委托给 provider 的 `canCastUltra`，默认实现仍是老规则
   （有能量条 + 够阈值），常规角色行为**一字不变**。
   ⚠ 传进去的是**阈值**（`ultraEnergyCost`）不是 `maxEnergy` —— 5 个角色的阈值低于上限。
2. **"满了"信号是上升沿，不是电平**。计划说"满了不溢出并发满事件"，但昔涟的池子 24
   可以继续**溢出存到 27**（§9.5）：按电平，她到达 24 之后每次溢出入账都会再触发"满了"。
   现在只在"本次入账让它**从不满变成满**"且**真的入账了**（`gained > 0`）时触发一次。
3. **`PARTY` scope 被显式拒绝，而不是当成 `SELF`**。共享池需要一个比单个角色活得久的
   持有者（per-battle 注册表）；挂在每个角色身上会得到**四份各自独立的计数器**，
   而游戏里只有一个共享池 —— 这种错**运行时不报任何异常**，所以宁可注册时响亮拒绝。
   `SELF` 已接。
4. **`SPEND_RESOURCE` 不够时抛异常**，不是静默失败：战技点不够是"玩家按不动按钮"（合法状态），
   而触发规则的消耗是作者写了就认为层数会在那 —— 静默吃掉会让内容 bug 隐形。
5. **两处限制写成了测试，不是藏起来**：
   - 效果 amount 是**字面量、无算术**，所以"给 `lost` 层"表达不出来。测试拆成两半：
     `hpLossReachesTheOwnersTriggerTable`（接线）与
     `oneStackPerPointOfLossIsAManagerCall`（1:1 算术，属 manager 层）。
   - 条件 DSL **不能表达"只有我自己受伤"**：`self` 比的是 **actor**（谁造成事件），
     而 `HP_LOST` 是**全队事件**（§4.1），规则分不清"我被打"和"队友被打"。
     对需要全队损血的遐蝶是对的，对个人层数角色是错的 → 需要给 DSL 加 `target` 变量。
     由 `subjectFilterIsNotExpressibleYet` 钉住。

**变异验证**（3 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| `isUltraReady` 改回只看能量 | `resourceFullMakesTheUltimateAvailable` 红 ❌ |
| "满了"信号改成电平触发 | `stayingAtTheCapDoesNotRefire` 红 ❌ |
| 放行 `PARTY` scope | `partyScopedResourceIsRefusedUntilItHasARealOwner` 红 ❌ |

**验收**：`ResourceTest` 19 条全绿（含"资源满 → `castUltra` 可用"）；
全套 **50 套 / 447 例**全绿；demo 行为不变。

---

### P8-3 天赋 + 追加攻击 ✅

> ✅ **前置已就位（P8-6/P8-7）**：不需要再往 `Battle` 加 `List<TalentTrigger>`，也不需要在
> `CharacterFactory` 里 `switch (cid)` —— 那张表已经能表达"受击后追加一段"。

**计划时的两条"前置"，一条是真的、一条是假的：**

| 原计划 | 实际 |
|---|---|
| ⚠ **数据阻塞**：数据里没有 `is_follow_up` 字段，"哪个技能算追加攻击"无从判断 | ❌ **假前置**。不需要那个字段 —— 追加攻击的 payload 就在**天赋槽自己的技能数据**里，缺的只有"什么时候放" |
| **引擎缺口**：触发器表的 `ADD_DAMAGE` / `TRUE_DAMAGE` / `REDUCE_TOUGHNESS` 在加载时被拒绝 | ✅ **真缺口**。改成一个 `DAMAGE` op 就够（`ADD_DAMAGE`/`TRUE_DAMAGE` 这两个名字是我凭空想的，实际不需要拆成两个） |

- **实际新增的引擎能力**：触发器表 `DAMAGE` op（`skill` 指定槽位、`damage_param` 指定倍率索引、
  `target` 打谁、可选 `per_target` / `as_attack`），走 `Battle.applyAdditionalDamage`。
  `REDUCE_TOUGHNESS` **仍然没接**（它要的是"定元素"，与追加攻击无关，留给需要它的角色）。
- **代表角色（都是纯 JSON，零 Java 角色类）**：
    - 克拉拉 1107 `因为我们是家人`：`on: HP_LOST` + `when: ["target == self"]`
      → `DAMAGE`(`TALENT`, `damage_param: 1`, `target: "attacker"`)。
    - 希儿 1102 `再现`：`on: KILL` + `when: ["actor == self"]` → `EXTRA_TURN`。
      她的天赋槽是 `Enhance`/stance 0（**不带攻击**），所以不是"打一段"而是"再动一次"。

**变异验证**（3 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| JSON 的 `damage_param` 1 → 0 | `theShippedRuleDeclaresTheRightParameterIndex` 红 ❌ |
| `when` 里 `target == self` → `actor == self` | `claraCountersTheEnemyThatHitHer`、`theShippedRuleRequiresClaraToBeTheVictim`、`theShippedRuleDeclaresTheRightParameterIndex` 红 ❌ |
| 删掉希儿的 `when`（规则变成无条件） | `seeleDoesNotGetATurnFromATeammatesKill` 红 ❌ |

**验收**：`TalentTest` 11 条全绿（反击打回攻击者 / 倍率取自天赋 `damage_param` /
`target == self` 与 `actor == self` 不可混用 / 别人挨打不反击 / 击杀再动 / 队友击杀不给回合）；
全套 **52 套 / 469 例**全绿；demo 行为不变。

> **范围说明**：93 个角色里 21 个的槽位 4 带伤害效果（候选承载者，但**不全是**追加攻击）。
> 本项只做上面两个代表 —— 剩下的按"一次一个 + `source`/`note` 标出处"推进，
> 因为 `damage_param` 索引**因技能而异**（姬子 0 / 克拉拉 1 / 貊泽 2 / 大丽花 2），批量猜必错。
> 完整说明见 `engine.md` §4.7。

---

### P8-5 真实队伍装配（`StageFactory` 换真角色）✅

- **目标**：P7-5 的 `fromAttributes` 临时队换成 4 人真队；光锥/遗器沿用 `Builder` 已有管线。
- **涉及文件**：`utils/StageFactory.java`、新建 `test/RealTeamTest.java`
- **怎么做**：
    1. `StageFactory.load(stageId)` 里把 `temporaryTeam()` 换成
       `CharacterFactory.create(1204, 80)`（景元）/ `1102`（希儿）/ `1107`（克拉拉）/ `1105`（娜塔莎）。
    2. 每个角色再 `.weapon(...)` / `.relicSuit(...)`：先从 `Constant.WEAPONS` 挑同命途光锥
       （数值被动 P10-3 再接，**先只吃面板**）。
- **验收**：`RealTeamTest`：`load(103201)` → `team.size() == 4`、每个 `getElement()` 非 null、
  `Battle` 能完整跑一轮不炸。
- **依赖**：P8-1、P7-5

**实际落地（2026-09-23）**：

1. ⚠ **光锥必须挂在 `Character.Builder` 上，不能建完再 `setWeapon`**。
   这是我实现时踩到的：`Calculator` 在 `build()` 里**把它当输入消费**，
   所以对已建好的角色 `setWeapon(...)` 只改字段、**面板不会重算**（锥装上去了但一点属性都没加，
   而且运行时不报任何异常）。为此给 `CharacterFactory` 加了带光锥的 `create` 重载
   （装配点，P8-0 允许），并在 `Character.weapon` 的字段注释里写明这个坑
   （`setWeapon` 全项目**零调用者**，就是个陷阱）。
2. **选锥规则：先看稀有度，再按 id 最小**。原本只按 id 最小 → 选中 3★ 新手锥
   （锋镝/天倾/一场术后对话），对一支 80 级队伍很怪。为此给 `WeaponData` 补了 **`rarity`**
   字段 —— `weapons.json` 里**一直有**这个字段，只是 bean 没声明，
   正是本项目反复踩的 **Gson 静默失配**（`ROADMAP` §4.2）。补上后每个角色拿到的是自己的
   5★ 签名锥：景元「银河铁道之夜」/ 希儿「于夜色中」/ 克拉拉「无可取代的东西」/ 娜塔莎「棺的回响」。
3. **遗器当时没有**（**已解决**，见下）：当时没有可用的遗器实例数据（`relic_sets.json` 未装载），
   所以队伍裸装上场。`Builder` 的遗器管线早就存在，由 `CharacterFactoryTest` 覆盖。
   > **后续**：`relic_sets.json` 已装载、2/4 件套效果已生效、`RelicFactory` 提供
   > **确定性**遗器（旧的 `createRandomLevelZero` 用 `ThreadLocalRandom`，两次运行面板不同），
   > `StageFactory.realTeam()` 已穿上「4 件套 + 2 件位面」。见 `RelicSetTest`。
4. **删掉了 `temporaryTeam()` 与 `temporaryCharacter()`**（连同 `TEMPORARY_MAX_ENERGY`）——
   并加了一条反射测试断言 `StageFactory` **不再暴露**任何 temporary 方法，
   免得以后有人把占位队加回来。
5. 顺带修了 `StageFactoryTest` 里那条"3 个占位角色、速度各不相同"的旧断言
   （真队是 4 人，速度也各不相同，所以那条**关于行动条的意图**保住了）。

**变异验证**（2 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| 选锥时忽略命途 | `everyMemberCarriesAWeaponOfItsOwnPath` 红 ❌ |
| 建完再 `setWeapon`（面板不重算） | `theWeaponContributesToTheStatSheet` 红 ❌ |

**验收**：`RealTeamTest` 11 条全绿（4 人 / 4 命途 / 元素非 null / 锥的 type 对上命途 /
锥真的进面板 / 每次调用给新实例 / 默认 `load` 也走真队 / 占位入口已消失）；
全套 **51 套 / 458 例**全绿；demo 行为不变。

---

## 7. 待办 B 层：敌人（只有 5 只怪有技能，其余全是平 A）

> 没有敌人技能就没有难度曲线。`enemy_skills.json` 目前是自建的、倍率是猜的，
> 找到源数据后只换加载处。

### P9-1 敌人技能数据（`enemy_skills.json` 扩充）

- **目标**：敌人技能表从"5 只演示怪"扩到可用规模；找到源数据后只替换 `Constant` 加载处。
- **涉及文件**：`src/main/resources/data/enemy_skills.json`、`beans/EnemySkillBean.java`、`Constant.java`、`test/EnemySkillDataTest.java`
- **怎么做**：
    1. bean 字段：`name / attackType / effect / element / multiplier / stance / hits / aiWeight / condition / summonId / debuffKey / debuffChance`
       （`hits` = **目标数**，0=全体；`condition` = `null` / `"hp<0.5"` / `"firstTurn"`）
    2. **数据源缺失是已知事实**（见 §4 陷阱 7）：先自建，每条标 `guessed=true`。
    3. `Constant.ENEMY_SKILLS` 的 key 用 monster **template_id**，`EnemyFactory` 按 `cfg.templateId()` 查。
- **验收**：`EnemySkillDataTest`：条目数、倍率、削韧值与表一致；无条目的怪走兜底普攻。

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

**实际落地（形状分派，2026-09-24）**：

数据侧新增 `effect` 字段（`SingleAttack` / `AoEAttack` / `Blast`，省略 = `SingleAttack`），
`EnemySkill` 按形状决定**打到谁**：`AoEAttack` 打我方全体、`Blast` 打主目标及左右相邻
（**到边界就截断，不回绕**），段数落在**每一个**被打到的目标上。5 条现有条目显式标 `SingleAttack`，
所以行为与之前逐位相同。`EnemySkillFullTest` 6 条覆盖：默认单目标 / AOE 打全体 / Blast 打相邻 /
**边界不回绕** / 段数落在每个目标上 / 死者不再挨打。

**变异验证**：把 Blast 的相邻改成取模回绕 → `blastOnTheEdgeCharacterDoesNotWrapAround` 红 ❌。

> ⚠ **一处"两个来源用同一字段名"的陷阱（已写进数据文件的注释）**：
> `enemy_skills.json` 的 `hits` 是**段数**（8013010「连续踏击」= 同一目标 2 段），
> 而 P9-1 的计划里同名字段是**目标数（0=全体）**。两者**不可**合并 ——
> 真表落地时若把 `hits` 改指目标数，现有每只怪的行为都会变而**没有任何测试会红**。
> 所以目标数改用 `effect` 表达（加法式、默认值等于旧行为），段数语义由
> `EnemySkillFullTest` 从出货侧钉住。

**仍未做**：
1. **敌人技能仍不发事件**。`EnemySkill.execute` 走的是 `Battle.applyDamage`，不发
   `SkillCastEvent`（F-5 与 §3 的"🚧 部分完成"都记着）。⚠ 但**不能简单补一个发射**：
   条件 DSL 只有 `actor ==/!= self`、**没有阵营变量**，所以一旦让敌人的施放也发
   `SKILL_CAST`，「当我方施放技能时」这类规则会连带在敌人身上触发 —— 需要先给 DSL 一个
   阵营条件，或改用"以我为承受者"的事件（`TAKING_HIT` 已经在做这件事）。
2. `EnemySkill` **仍不持有 bean**、`getData()` 仍为 `null`（F-5）；目标是复用
   `SkillExecutor` 的伤害分派而不是自成一摊，等上面那条一起做。
3. 敌人技能**不削韧**：角色没有韧性条，这一条对"敌人打我们"本来就不适用；
   真正的削韧方向是反过来（我们打敌人的韧性，已经在用）。

### P9-3 敌方 AI 技能选择器

- **目标**：敌人回合**先选技能再选目标**（P5-4 已有目标选择，缺技能选择）。
- **涉及文件**：新建 `models/ai/SkillSelector.java`、`Main.java`、新建 `test/SkillSelectorTest.java`
- **怎么做**：纯静态 `next(enemy, battle, rng)`：过滤 `conditionOk` → 按 `aiWeight` 加权随机。
- **验收**：`SkillSelectorTest`：权重 1.0/1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%）；
  `condition "hp<0.5"` 满血时选不到。
- **依赖**：P9-2、P5-5

### P9-4 召唤物（`summon_id` 机制）✅ 敌方阵营已落地（2026-09-27）

> **⚠ 结构障碍已清除（2026-09-26，L-8）**：`Battle.enemies` 曾是 `List<Enemy>`，敌方召唤物**无处安放**。
> 现在是 `List<CanHit>`，`enemyUnits()` 才是"其中的怪"；引擎的目标表与胜负判定都按**整阵营**走。
> 能力回归：`EnemyCampSummonTest` 4 条（含"打死所有怪但召唤物还站着不算赢"）。

**落地（2026-09-27）**：机制说明见 `engine.md` **§24**。数据侧 `summon_id` 这一列终于被解析
（此前 `MonsterConfig` 里根本没有这个字段）。四处与原计划的差异：

1. **新增了 `CanHit.perish()`**，计划里没有。计划写"本体重伤 → 召唤物同判移除"，但**没有"移除"
   这个操作**：`death` 只在 `takeDamage` 里置位。直接调 `takeDamage` 会发 `HpLoss`/`Kill` ——
   于是每一个「消灭敌人」天赋都会为一个**没人杀死的**单位付钱。`perish()` 只翻标志位，
   所以 `SummonTest.aSummonLeavingWithItsMasterIsNotAKill` 与"真杀死会付钱"成对存在。
2. **`SummonFactory` 放在 `models/enemy/` 而不是 `utils/`**（计划写的是 `utils`）：它要用
   `EnemyFactory` 的包私有 `resolve` / `statSheet` / `enemySkillFor`，搬到 `utils` 就得抄一份 ——
   那正是两份缩放规则开始分叉的方式。为此把 `EnemyFactory.create` 的"查配置/查模板/查等级组/缩放"
   抽成了 `EnemyFactory.resolve`。
3. **`[0]` 这个数据陷阱**（计划里没有）：405301004 的 `summon_id` 就是 `[0]`，而 0 不是怪物 id。
   约定在**装载期**一次性执行（只留正数），理由见 §4.1 陷阱 9。
4. **我方主人的召唤被响亮拒绝**，而不是"先塞进 enemies 凑合"。计划里"我方召唤物入场"被列在
   "剩下的都是内容活"里，但它其实是**类型问题**：`battle.characters` 是 `List<Character>`，
   放不下 `Summon`（L-8 只做了敌方那一半）。塞进 `enemies` 会让我方召唤物**被我们自己的攻击打中**
   且在胜负判定里**算成敌人** —— 一个不报错的错误答案。

**还不做的**（都记在 `engine.md` §24.3）：
- **忆灵**（我方、独立单位、面板快照、连携攻击）—— 要先放宽 `battle.characters`，属远期。
- **技能侧 `SkillEffectType.SUMMON` 分派** —— 数据里没有"这次施放召谁"那一列，缺的是**内容**不是能力
  （能力就是 `Battle.summon`）。
- **谁来触发召唤** —— 名单只回答"能召谁"，不回答"什么时候召"；调用方（将来的敌方技能 / 阶段表 /
  敌方 AI）负责。这条与原计划一致。

- ~~**目标**：`monster_config.summon_id` 生效：敌人技能召唤实体入战，实体可受击、会死亡移除。~~
- ~~**涉及文件**：`models/Summon.java`、新建 `utils/SummonFactory.java`、`Battle.java`、`models/Enemy.java`、新建 `test/SummonTest.java`~~
- ~~**怎么做**：`SummonFactory.create(summonId, level)`；`Battle.summon(boss, summonId)`；本体重伤 → 召唤物同判移除~~
- ~~**验收**：`SummonTest`：触发后 `enemies.size()` 增加；召唤物被打死 → 数量回落；本体死 → 全清~~
  —— **实际验收 18 条**（口径更正：死者**留在 `enemies` 里**是引擎既有约定，"数量回落"看的是
  活人数而不是列表长度，见 `engine.md` §6.2）
- **依赖**：P9-2、P5-5、P7-4 ✅

### P9-5 Boss 机制（phase 换招 / 受击反击 / 控制免疫）

> **⚠ 第 3 条（控制免疫）是一条假缺口** —— 它**已经实现并且已经有测试**，不需要再做。
> 见下面的"核对结果"。本条只保留**真正还没做**的两项。

- **涉及文件**：`models/Enemy.java`、新建 `models/buffs/CounterMechanic.java`、`Main.java`、新建 `test/BossMechanicTest.java`
- **怎么做**：
    1. ~~`Enemy.phase`：HP 阈值切技能列表~~ —— ✅ **已落地**（`Enemy.setPhaseSkill` / `activeSkill`，
       `EnemyPhaseTest` 5 条）。**注意实现方式**：`activeSkill()` **每次读当前血量**，
       所以**不需要任何状态机** —— 没有要翻的标志位、没有要调度的转换。
       这正是为了绕开第 4 条那个陷阱（"把血条锁在 1"）。⚠ 它**只覆盖阈值换招**；
       **多血条**仍需要锁血 + 显式重置，那一项**未做**，单独登记。
    2. 受击反击：用 `DamageEvent` → 追加一段 `DamageType.ADDITIONAL` + `notCountsAsAttack()`，
       **反击目标 = 该段的 `damage.getAttacker()`**（"施放技能的个体"，不一定等于角色本人）。
       > ⚠ **不是一行代码**：`DamageEvent.onDamage` 是在结算**之前**往这一击里塞乘区用的，
       > 反击要的是"这一击结算**之后**"的钩子 —— 那是 `HpLossEvent.onHpLoss(..., source, amount)`。
       > 并且**必须自带递归护栏**：反击本身造成掉血 → 若对方也带反击就会乒乓，
       > 而这条路径**不经过** `Battle.fireTriggers`，所以 `MAX_TRIGGER_DEPTH` **拦不住它**
       > （触发器版的反击能拦住，是因为它走 `applyDamage` → `fireTriggers`）。
    3. ~~控制免疫~~ —— ✅ **已实现，见下。**
    4. ⚠ **阶段推进绝不能用"0 血不死"实现**：`takeDamage` 在 HP≤0 时立刻 `death = true`。
       要锁血就用 `setInvulnerable(true)` 再**显式重置 HP**；否则要么阶段被跳过，要么**鞭尸**。
       多血条同样按"打空一段 → invulnerable → 重置 HP"。
- **验收**：`BossMechanicTest`：HP 降到 50% 以下换招；受击反击段 `getCountsAsAttack() == false`。
- **依赖**：P9-2、P1-7、P6-1

**核对结果（2026-09-24）：控制免疫已经是 P6-1 的一部分，不需要新代码。**

`Battle.hitChance(caster, target, baseChance, specificResistKey)` 里已经有这段：

```
chance = base × (1 + 效果命中) × (1 - 效果抵抗) × (1 - 具体 debuff 抵抗)
```

`specificResistKey` 查的就是 `Enemy.debuffResist`，所以冰锋的 `{"STAT_CTRL_Frozen": 1}`
让 `(1 - 1) = 0` → **完全免疫**，与原文描述逐字一致。**并且已经被测试钉住**
（`HitResistTest`）：`冰锋.getDebuffResist().get("STAT_CTRL_Frozen") == 1.0`、
`hitChance(..., "STAT_CTRL_Frozen") == 0`、别的 key 不免疫、
`tryApplyDebuff(..., "STAT_CTRL_Frozen")` **端到端返回 false**。

> 所以计划里那句"`Enemy.isImmuneTo(resistKey)`"是**不需要的方法名** —— 免疫不需要一个新方法，
> 它就是"具体抵抗 = 1"这一种情形，走同一条概率公式即可。**加一个 `isImmuneTo` 只会是第二套判据。**
> （这条与"负数抵抗会被当成 0 而不是增伤"是同一处口径，见 `engine.md` §6。）

---

## 8. 待办 C 层：机制补完（全是"填口子"，无新架构）

> 原则一条：**任何任务做到一半发现需要新架构，说明它跑偏了，回退并拆小。**

### P10-0 DOT 并入 buff 体系 ✅（2026-09-26 完成）

> **起点是一个提问**："DOT 不应该是 buff 吗？" —— **是**。核对代码后确认它当时是一套**并行机制**：
> `models/Dot` + `Enemy.dots` + `Battle.tickDots(Enemy)`，与 buff 逐行对应（存储 / 时长 / 倒计时 / 到期），
> 正是 §1.2 三分法要消灭的"第二套机制"。

- **尖锐的缺陷（不只是风格）**：`tickDots` 收 `Enemy`、DOT 列表挂在 `Enemy` 上，
  所以"**boss 给我们挂燃烧**"在类型上**无法表达** —— 而这是 HSR 的常态。
  同时丢掉了驱散、`hasBuff` 查询、统一的刷新/叠层规则、以及 buff 机制对它的可见性。
- **怎么做**：`models/buffs/DotBuff extends AbstractBuff`（early buff，`super(turns, true)`），
  只持有**生命周期**；伤害仍由 `Battle.tickDots(CanHit)` 结算（`tickEffect` 拿不到 `Battle`，
  为一个 buff 加宽签名会把 `Battle` 泄漏给所有 buff）。
  新增 `BuffManager.allBuffsOf(Class)`（**快照**，按挂载顺序）供结算查询；
  删掉 `models/Dot`、`Enemy.dots/addDot/removeDot`、`tickDots` 的 `Enemy` 形参。
- **顺序是契约**：`beforeMove` 里**先结算、后倒计时**（`tickDots(actor)` → `buffManager.beforeMove()`），
  所以 N 回合的 DOT 恰好结算 N 次，与旧 `Dot.tick()` 的次数一致。
- **验收**：`DotTest` 9 条（原 6 条 + 新增 3 条）；全套 **63 suites / 601 tests / 0 failures**；
  demo `Main` 输出与迁移前**逐行对比零差异**（唯一 6 行差异是 `Set` 打印顺序，实测**同一份代码跑两次也会变**，
  是 `Main` 既有的不确定性，与本次改动无关）。
- **变异验证（4 处，均确认护栏有效）**：
  1. 恢复 `beforeMove` 里的 `instanceof Enemy` 守卫 → `aCharacterCanCarryADot` 红（角色 DOT 变哑）；
  2. 把 `beforeMove` 两行写反 → `aOneTurnDotStillSettlesBeforeItExpires` 红（1 回合 DOT 一个伤害都打不出）；
  3. `DotBuff.isSameKind` 改回默认（同类即顶替）→ `theSameElementStacksInsteadOfRefreshing` + `dotsSettleInApplicationOrder` 红；
  4. `allBuffsOf` 改成倒序 → `dotsSettleInApplicationOrder` 红。
- **顺带发现（未修，属演示层）**：`Main` 打印 `Weakness [...]` / `Resist {...}` 时直接 `toString` 了
  `Set`/`Map`，顺序取决于 identity hash，**跨进程不稳定** —— 所以 demo 输出目前**不能用来做逐行回归**。
  这正是上面第 4 条验收要绕道"同代码跑两次"来证明的原因。要做逐行回归得先让它排序（归 P11-1）。

### P10-1 七系击破异常全量 ✅（表 + 七系数值；三个控制系的机制在 P10-2）

- **现状**：`Constant.BREAK_EFFECTS`（`record BreakEffect(dotRatio, dotTurns, delayPercent, control)`）
  描述全部七系；四个 DOT 系（火/雷/物理/风）走它且**逐位不变**，`Constant.DOT_ELEMENTS` 由它**派生**
  （不再手写，所以"哪些系有 DOT"不可能与表不一致），挂 DOT 的代码**查表**。
  三个控制系也进了表：额外推条 + 一个 `CONTROL_EFFECTS` 的键。
- **怎么做**：见上；击破推条统一 = 固定 25% + `delayPercent`（`Battle.attachBreakControl`）。
- **验收**：`BreakEffectTableTest` 5 条：每个元素都有条目；四个 DOT 系 = 有 DOT + 无控制 + 无额外推条；
  三个控制系 = 有控制 + 有额外推条（**当前无 DOT**，见下）；控制键必须能解析且只有冻结锁行动；
  `DOT_ELEMENTS` 与表一致。回归 P4-5 由 `DotTest` + `ControlTest.aPhysicalBreakStillOnlyBurns` 覆盖。
- ⚠ **原验收里"量子 → DOT"这一条没有实现，是有意的。** 词条确实说纠缠会在敌人下次行动时造成量子伤害
  （即一个 DOT），但**没有给"击破施加"的比例**；填一个示例值会让它看起来像数据。留 `dotRatio = 0`
  并把 TODO 写在 P10-2；真要填时 `attachBreakDot` 一行都不用改。
- **依赖**：P4-5、P4-4、P10-2 ✅

### P10-2 控制异常状态机 ✅（2026-09-26 完成，一个数字仍是 TODO）

> **计划里两句话被数据推翻了，动手前改了。** 原计划写"`ControlBuff extends AbstractBuff` +
> 冻结期受伤害 +30%（示例值）"。去查数据时发现：
> 1. **`StunBuff` 早就是控制 buff**（`canAct() == false`，有 `BuffManagerTest` /
>    `SkillPointGameParityTest` 钉着），而且 `tryApplyDebuff` + `hitChance(caster, target, base,
>    "STAT_CTRL_Frozen")` **已经端到端可用**（`HitResistTest`）—— 也就是说"建一个控制类"和
>    "施加方走命中/抵抗"这两条**本来就不需要做**，写在这里只会让人以为还要做一遍（同 P9-5 的
>    `isImmuneTo` 假缺口）。
> 2. **"+30% 受伤害"没有任何数据支持。** 词条原文相反且一致（六条独立来源）：冻结是
>    `"冻结状态下，敌方目标不能行动同时每回合开始时受到等同于<施法者>#4%攻击力的冰属性伤害"`
>    （深寒徘徊者 / 永冬灾影 / 三月七 / 杰帕德 / 镜流）；禁锢是
>    `"禁锢状态下，敌方目标行动延后#2%，速度降低#4%"`（瓦尔特）；纠缠是
>    `"「纠缠」会使敌人行动延后，并在敌人下次行动时对其造成额外的量子属性伤害"`
>    （原文就写的是「弱点击破」量子）。**冻结的伤害是"每回合冰伤"，不是"受伤加成"。**

- **为什么没有新建 `ControlBuff`**：三个状态只差几个数字和一个布尔，而引擎已有的原语正好一一对应，
  所以控制是**组合**出来的（P8-0 的规矩，同 `StatModifierBuff` 之于所有属性 buff）：
  `blocksAct` → `StunBuff`（已存在）；`slowPercent` → `StatModifierBuff.percentDebuff(SPEED, …)`（已存在）；
  推条 → `delayMovePercent`（已存在，是瞬时推、不是 buff）；`resistKey` → `hitChance` 的第 4 个参数
  （只给"技能施加"那条路用）。
- **落地**：`Constant.ControlEffect(resistKey, turns, blocksAct, slowPercent)` + `CONTROL_EFFECTS`
  （`FROZEN` / `ENTANGLED` / `IMPRISONED`）；`BreakEffect` 加 `delayPercent` 与 `control`
  （四个 DOT 系填 `0` / `null`，所以它们**逐位不变**）；`Battle.attachBreakControl` 在击破链里接线。
- ⚠ **击破不走抵抗判定**：破韧是"韧性条空了"，不是被抵抗的 debuff —— 让 `STAT_CTRL_*` 取消一次击破，
  会让弱点击破**静默什么都不发生**。`ControlTest.anIceBreakFreezesEvenAMonsterThatIsImmuneToFreezeSkills`
  钉住这条（冰锋本身就带 `STAT_CTRL_Frozen = 1`）。
- ⚠ **顺序（先加状态、最后推条）曾经是契约，现在不是了。** 当时实测：推条写在减速之前时，
  减速触发重排会把那 20% 额外推条**重算掉** —— 加不加它行动值变化都是 28.409，完全不可观测
  （变异 3 就是靠这条变红的）。**根因已由 L-26 修掉**（账本同步 + 去掉进度上钳），
  所以现在推条能活过速度变化、两种顺序都对；顺序保留只是因为"先状态、后一次性推条"读起来顺。
  > 顺带：L-26 修好后 `ControlTest` 里"禁锢/纠缠推条"的基线断言**反而抓不到东西了**
  > （重排不再被截断，无额外推条时也超过基线），所以两条都撤了 —— 见该测试的实测表。
- **验收**：`ControlTest` 5 条（冻结=锁行动+25%+50%推条且不减速；量子/纠缠与虚数/禁锢=减速 20%+
  推条且**仍能行动**；物理回归=只挂 DOT、不减速、推条恰为固定 25%；冰锋带 100% 冻结抵抗仍被击破冻结）；
  `BreakEffectTableTest` 5 条（含"控制键必须能解析"与"只有冻结锁行动"）。全套 **64 suites / 608 tests / 0 failures**。
  demo `Main`：唯一变化是 `[Break IMAGINARY]` 的次元扑满在行动条上被推后并减速（23 行行动条），
  **伤害/HP/击杀/结局逐行不变**（仍 `victory (10 rounds / 43 actions)`）。
- **变异验证（4 处，均确认护栏有效）**：
  1. 摘掉 `attachBreakControl` 调用 → 4 条控制用例红、物理回归**仍绿**（区分度正确）；
  2. 无视 `hasControl()` 让每个元素都吃冻结 → `aPhysicalBreakStillOnlyBurns` 红；
  3. 把推条挪到加状态**之前** → 量子/禁锢两条红，实测值 `28.409` vs 下限 `42.61`
     （正好等于"没有额外推条"的那个数，即顺序论证本身被验证）；
  4. 让击破也吃 `debuffResist` → 两条冻结用例红。
- 🚧 **仍是 TODO data（示例值）**：`CONTROL_EFFECTS` 的三个 `turns`（都取 1）、三个额外推条
  （`FREEZE_EXTRA_DELAY = 0.5` / `ENTANGLE_EXTRA_DELAY = 0.2` / `IMPRISON_EXTRA_DELAY = 0.2`）、
  以及两个减速比例（0.2）。数据里**没有**击破控制表（`breaking_rate.json` 是"等级→击破基数"），
  词条正文只给机制、不给击破用的数值。
- 🚧 **有意留下的口子：冻结/纠缠的伤害段没做。** 词条说冻结每回合受冰伤、纠缠下次行动受量子伤 ——
  两个都是 DOT，而 `BreakEffect.dotRatio` 正是它的字段；但**没有任何来源给出"击破施加"的那个比例**，
  所以宁可留 `0` 并在这里记着，也不填一个看起来像数据的数
  （`BreakEffectTableTest` 会因为有人乱填而变红）。要做的话就是给冰/量子填 `dotRatio`/`dotTurns`，
  `attachBreakDot` **不需要改一行**。
- **依赖**：P6-1、P4-4、P10-1

### P10-2 旧计划原文（保留，供对照）

- ~~**怎么做**：`ControlBuff extends AbstractBuff`；`BuffManager.hasControl()`；冻结期受伤害 +30%（示例值）~~
  —— 前两条**不需要**（已存在/无调用者，照项目纪律不造没有调用者的 API），第三条**与数据相反**。
- ~~**验收**：普通怪 → 跳回合后恢复；禁锢 → 延迟 30%~~ —— "跳回合后恢复"由 `StunBuff` 已有的用例覆盖。

### P10-3 Buff 体系完善（属性类 + 刷新规则）✅ 引擎侧已完成（2026-09-26 核对）

> ⚠ **本节下面那段"还没做（后半）—— 卡在一张参数表上"已经过时**，保留它是因为它记录的
> 侦察过程仍然准确（"倍率取 `param_list[0][0]` 是错的"确实是当时的真相）。核对后的实际情况：
> **那张表已经有了**（`data/skill_effects.json`，40 条），`resolveHits` **不再静默 return**，
> `dispatchNonDamaging` 真的在分派，`Main` 的两条手搓路径**只剩"选谁"**
> （`healTurn`/`shieldTurn` 现在只选目标 + 打印，算术与回能都走 `skill.execute` → 引擎）。

**当前真实状态（逐条核对过）**：

| 项 | 状态 |
|---|---|
| 表存在且被读取 | ✅ `SkillEffects.forSkill(skill)` → `dispatchNonDamaging` |
| `Restore`（治疗）分派 | ✅ 17/26 条可用（其余 9 条表里 `params` 为空，被 `isAmbiguous` 拒绝并记诊断） |
| `Defence`（护盾）分派 | ✅ 9/14 条可用（其余 5 条 `params` 为空） |
| `Main` 手搓算术 | ✅ 已收回引擎；`Main` 只剩"选谁治疗/套盾"（这确实属于调用方） |
| `BUFF` / `CONTROL` / `SUMMON` 技能分派 | ☐ **没做**，且**缺数据**：表里只有 `Restore`/`Defence` 两类 |
| 光锥 / 遗器数值被动 | ☐ 没做（`StatModifierBuff` 已有，缺接线） |

- **还差的（都是数据活，不是引擎活）**：
  1. 14/40 条 `params` 为空 → 要 `generate_data.py` 从描述里补出参数（属数据补全）；
  2. `BUFF`/`CONTROL`/`SUMMON` 技能要一张"技能施加了什么 buff"的表（同上）；
  3. 光锥数值被动的接线（引擎侧只差把 `weapons.json` 的数值喂给 `StatModifierBuff`）。
- ⚠ `isAmbiguous` 是**临时护栏**（拒绝"一条参数里混了立即量与每回合量"的条目，如 Natasha 战技的
  直接治疗 + 持续治疗），不是设计 —— 真正的修法是在生成器里按从句切开，只导出**立即**项。
  拒绝是诚实的：**被拒的治疗看得见，错误的治疗看不见**。

**已落地（2026-09-23，前半）**：

1. **`StatModifierBuff`** —— 一个通用类覆盖所有"属性 X 变成 X ⊕ 值，持续 N 回合"，
   不必每个 buff 一个 Java 类。身份是 `(属性, modifier 类型, BUFF/DEBUFF)`，
   **不是**"类相同"：否则一个 +攻击% 会把已有的 +防御% 顶掉（这正是它要防的坑）。
2. **触发器 op `MODIFY_ATTR`** 接线（`attribute` / `percent` / `turns`），
   正负号决定 buff / debuff，所以两者能共存并各自移除。
3. **`target` 选择器补上 `all_allies`**，并把它变成**封闭集合** ——
   原来写错 `target` 会静默回退成 `self`（`"atacker"` 与 `"self"` 行为相同、什么都不报）。
4. **`AttributeType.fromString` 修成真的大小写不敏感**（Javadoc 一直这么写，代码没有），
   并新增 `isPercentVariant()` 供"这四个是 builder 输入键、不是运行时属性"的判断。

**变异验证**（3 处，均确认护栏有效）：

| 变异 | 结果 |
|---|---|
| `StatModifierBuff.isSameKind` 退回"只比类" | `differentAttributesDoNotEvictEachOther` 等 **4 条**红 ❌ |
| 去掉 `target` 选择器的加载期校验 | `unknownTargetSelectorIsRejected` 红 ❌ |
| `all_allies` 回退成单目标 | `modifyAttrOpCanReachTheWholeParty`、`allAlliesWithoutABattleFailsLoudly` 红 ❌ |

> 顺带发现一条**测试写弱了**：`allAlliesWithoutABattleFailsLoudly` 原本断言消息里含
> `"all_allies"`，但"未知选择器"的报错也含这个词，所以它在变异下**照样通过**。
> 改成断言含 `"no battle"` 之后才真的抓到。

**验收（前半）**：`BuffRuleTest` 14 条 + `TriggerTableTest` 新增 11 条全绿；
全套 **53 套 / 494 例**全绿。

**还没做（后半）—— 卡在一张参数表上**：
> ⚠ **这一整段已经过时（2026-09-26 核对）：表有了、静默 return 没了、`Main` 的手搓路径也收了。**
> 保留它是因为**侦察过程仍然准确**（"倍率取 `param_list[0][0]` 是错的"确实是当时的真相，
> 也正是它引出了下面那张"每个技能参数布局都不一样"的表）。不要照这段去做事 —— 看本节开头的状态表。

`resolveHits` 的静默 return 还在，`Main` 的两条手搓路径也还在。原因是实测发现
**每个技能的效果参数布局都不一样**，且**缩放属性只写在技能描述文字里**：

| 技能 | `param_list[0]` | 文字里说的 |
|---|---|---|
| Natasha 1105 战技 | `[0.07, 0.048, 2, 70, 48]` | 生命上限 **7%** + **70**（flat 在 index 3，因为中间夹着持续治疗） |
| Natasha 1105 终结技 | `[0.092, 92]` | 生命上限 9.2% + 92 |
| Luocha 1203 战技 | `[0.4, 200, 0.5, 2]` | **攻击力** 40% + 200 |
| Bailu 1211 战技 | `[0.078, 78, 0.15, 2]` | 生命上限 7.8% + 78 |
| 三月七 1224 战技 | `[0.06, 0.1, 1]` | 速度 +6%（**百分比**，而 Asta 1009 是速度 **+36 固定值**） |

也就是说"倍率取 `param_list[0][0]`"这个假设是**错的**，而 demo 现在用的
`ATK × param[0]` 对 Natasha 就是**算错的**（她的治疗按生命上限，且 flat 项被整个丢掉）。

**好消息**：参数索引其实在数据里**可机器读出** —— `skill_introduction` 里的
`#1[f1]%` / `#4[i]` 占位符就是 `param_list` 的下标。所以正解是让
`generate_data.py` 顺手导出一张"技能效果参数表"（属**数据补全**，不是引擎逻辑），
引擎只读表。**建议下一步先做这张表**，再回来收 `Main` 的两条路径。

- **依赖**：P6-2（`Battle.heal`）、P6-3（`grantShield`）、P8-6（事件）、P8-7（表与 op 词表）
- **风险**：低（当前是"没实现"，不产生错误数值；但 `Main` 的治疗**算错**是真 bug）

**剩下的原计划项（本次没做）**：

- **光锥 / 遗器数值被动**（P8-5 遗留）用 `StatModifierBuff` 表达 —— 通用类已经有了，缺的是接线。
- **遗器套装效果**：✅ **已落地**（`relic_sets.json` 装载 + 2/4 件套生效 + `RelicFactory` 确定性遗器
  + `realTeam()` 实装）。⚠ 但**92 条套装效果里有 35 条是"具名 ability"**，不是纯数值 ——
  这 35 条由 `Effect.hasAbility()` 暴露、并由测试钉住 57/35 这个切分，不允许它悄悄变大。
  > **后续（已落地）**：execute 通道**已建**，而且**不是新建机制** —— ability 的文本形状
  > 恰好就是触发器表（"当 <事件>，做 <引擎已有的事>"），所以套装规则与角色规则同一份 JSON 形状，
  > 只是多一层件数阈值分组（`resources/relic_sets/<setId>.json`）。`ULT_CAST` 已接线。
  > **35 条里精确表达了 4 条、登记了 31 条**（`_unmodelled.json` + `F-10`，各自写明缺哪种能力）。
  > **F-2 因此解锁**：「过客 4 件套开局 +1 战技点」实测 **3 → 4**（两人穿 5，三件或无则不变）。
  > F-1 / F-7 仍**未**解锁，它们的套装效果落在那 31 条里（改"战技点上限"需要策略侧的口子）。
- **"同 class 不同来源取绝对值大者"**：⚠ **本次刻意没做**。那是 ROADMAP 自己标的
  `TODO data` 近似，而"再上一遍同类 buff"该刷新、叠加还是取大，属于**数据问题**；
  在拿到实测依据之前发明一条规则，会静默改掉现有行为。当前口径是
  **同类替换（后写的生效）**，由 `theSameBuffAgainRefreshesInsteadOfStacking` 钉住，
  将来要改必须是有意的。

- **验收**：`BuffRuleTest` 14 条（攻 +50% → 属性 ×1.5；同类再上只刷新不叠加；到期精确还原；
  buff 与 debuff 同属性共存；只有 SPEED 通知行动条）。
- **依赖**：P6-2、P6-3、P1-7

### P10-4 速度与行动条操纵 🚧 只差"拉条"那一半

- **现状（已核对，计划那句"公开 API 还没上移"过时了）**：`Battle.delayMovePercent` **已经是 public**
  （`Battle.java:1019`），而且 P4-4 的击破推条**已经在调它**（`:971`）—— 没有重复代码要删。
  真正缺的只有"拉条"：`Battle` 上没有 `advanceMovePercent`，现有入口是
  `Battle.advanceRequest(canHit, rate)` → `Queue.advanceActionByPercent`（形状不同：它是
  "登记一个请求、在 `processRequests` 统一结算"）。
- **怎么做**：加 `advanceMovePercent`，或在文档里明确"拉条走 `advanceRequest`"—— 二选一，别两个都留。
- **验收**：`SpeedBuffTest`：speed 100 减速 30% → 下次行动间隔 ≈ 142.857；拉条 50% → 提前半圈。
  ⚠ 减速那一半**已经被 `ControlTest` 侧面覆盖**（禁锢/纠缠减速后的行动值变化）。
- **依赖**：P10-3 ✅、P7-1

### P10-5 终结技插入

- **怎么做**：确认规则（不消耗行动条、不占回合、先清零再回自身 5）；`castUltra` 后**不触发** `afterMove`、
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
  一起带上 —— 数据里有些数字**只写在散文里**，生成器本来就靠这个：`skill_effects.json` 的
  `source: "SkillDesc 占位符"`）。
- **标定（5 个样本，全部来自真实数据）**：

  | 技能 | 原文 | 下标 | 值 |
  |---|---|---|---|
  | 1003/7 姬子 不完全燃烧 | `有#1%的<u>基础概率</u>` | 0 | 1.0 |
  | 1004/7 瓦尔特 画地为牢 | `有#1%的<u>基础概率</u>` | 0 | 1.0 |
  | 1108/7 桑博 你最闪亮 | `有#2%<u>固定概率</u>`（**没有"的"**） | **1** | 1.0 |
  | 1006/4 银狼 等待程序响应… | `有#4%的<u>基础概率</u>` | **3** | **0.6** |
  | 1307/4 黑天鹅 无端命运的机杼 | `有#2%的<u>基础概率</u>` | **1** | **0.5** |

  0.6 与 0.5 是关键：返回常数、或读错下标都**不可能**得到它们。
- **不变量（不止 5 个样本）**：全部 **28 条 `Impair`** 技能要么读得出一个 `(0,1]` 的几率，要么
  描述里**根本没有概率字样**（14 条，如波提欧 1315/2 的【绝命对峙】—— 那是"无条件生效"，
  返回 `null` 是**正确答案**，不是失败）。
- **顺带发现两条**：
  1. **基础概率 ≠ 固定概率**：前者吃施法者效果命中、被目标抵抗削减（就是 `hitChance` 算的东西），
     后者原样生效。表现在两者都返回（都叫"技能写的几率"），**但把固定概率喂进 `hitChance` 会放大它** ——
     留给真正做 Impair 分派的人处理。
  2. **禁锢的真实数值**：瓦尔特 1004/7 写 `行动延后#2%，速度降低#3%`，`param_list = [1, 0.2, 0.1, 15, 0.5]`
     → **延后 20% / 减速 10%**。P10-2 的 `IMPRISON_EXTRA_DELAY = 0.2` 蒙对了，
     `IMPRISONED.slowPercent` 从猜的 0.2 **更正为 0.1**（`ENTANGLED` 的 0.2 仍无出处）。
- **验收**：`DebuffChanceDataTest` 4 条（5 个标定样本 / 计划那个 index 3 的数值确实不是几率 /
  文本没写概率时必须返回 `null` 而不是编造 1.0 / 28 条 `Impair` 的不变量）。
  全套 **65 suites / 612 tests / 0 failures**。
- **变异验证（2 处）**：
  1. 改成固定读 index 0（计划那个 bug 的近亲）→ 标定用例在桑博处红，且**不变量用例点名 4 个技能**
     （1108/7、1305/7、1225/7、1410/7 会读出 10~20 的"几率"）；
  2. 找不到时 `return 1.0`（编造）→ `aSkillWhoseTextStatesNoChanceReportsNoChance` 红。
- 🚧 **仍缺（决定"还能不能往下做"的那一块）**：`SkillExecutor` 的 `IMPAIR` 分支**没做**，
  因为"这个技能施加的是**哪个** debuff、数值多少"在数据里没有位置 ——
  `skill_effects.json` 只有 `Restore` / `Defence` 两类（40 条）。
  要做就得让 `generate_data.py` 也导出 Impair 的 `(属性, 数值, 持续)`，属数据补全。
- **依赖**：P6-1、P8-2 ✅（P10-2 ✅）

---

## 9. 待办 D 层：演示与收尾

### P11-1 `Main` 修复 + demo 包拆分 🚧 入口那一半已完成

- ✅ **入口已修（2026-09-26）**：`Main` 现在是 `public static void main(String[] args)`，按参数选 demo
  （`battle`（默认）/ `mechanics` / `all`）。**默认无参跑的还是原来那个战斗 demo，输出逐字不变** ——
  所以 `gradlew run` 的既有行为、以及"demo 是回归锚点"这件事都没被打破。
- ⚠ **更正一条不准的旧注记**：本节原来写"当前 `main()` 无参数 → `gradle run` 进不去"。
  **实测不是**：`Main` 之前确实只有无参 `public static void main()`，而 `gradlew run` 一直正常跑出战斗 demo ——
  JDK 25 的 *flexible main method* 认无参 `main()`，`application.mainClass = 'com.laosun.Main'` 照样启动。
  所以"加 `String[] args`"的动机是**为了能选 demo**，不是修一个跑不起来的东西。
- ☐ **仍未做**：逻辑搬进 `demo/` 包（`demo/BattleDemo` / `demo/MechanicsDemo` / `demo/CharacterDemo`），
  `Main` 只剩入口调用。`Main` 现在约 640 行、含两个 demo + 一套回合驱动，确实该拆。
- **验收**：`.\gradlew.bat run` 能跑（✅ 已有 `DemoSmokeTest` 4 条钉住"两个 demo + 默认路径 + 未知参数都不抛"）；
  `Main` 只剩入口调用（☐）。
- **注**：`DemoSmokeTest` 只钉"还跑得起来"，**不**钉 demo 打印的任何数字 —— 那些是各单元测试的活，
  用抓 stdout 再断言一遍会是同一批结论的弱副本。
- **验收**：`.\gradlew.bat run` 能跑；`Main` 只剩入口调用。
- ~~**注**：当前 `main()` **无参数**（`Main.java` 用的是无参 `public static void main()`）。~~
  ⚠ **这条注记已被实测推翻，见本节开头**：无参 `main()` 在 JDK 25 上是能启动的，`gradlew run` 一直好使。

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

## 10. 远期（只记规格，不排实现）

| 项目 | 规格锚点 |
|---|---|
| **忆灵（角色专属召唤）** | P9-4 只做通用怪物召唤；忆灵 = 独立血条/能量/可受击/可选中，伤害类型 `MEMORY`，面板快照本体（**不含临时增益**），连携攻击按一次行动中的两次独立攻击判定 |
| **欢愉体系** | 伤害公式 `基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))`，**禁攻击力/属性增伤**；阿哈速度 = `80 + 最快/5 + 第二/10 + 第三/20 + 最慢/50`；`elation_basic_level_damage.json`（101 条）**从未被加载** |
| **模拟宇宙** | 祝福池化 = 3 选 1 |
| **Boss 专属机制库** | P9-5 只做通用换招/反击/免疫；个别 Boss 的专属机制每个 = 一个 `BossMechanic` 子类（需登记逃生舱） |
| **界面** | `Battle.getQueueSnapshot / printHp` 是现有 CLI 出口；UI 层接 `Battle` 事件流即可 |

---

## 11. 为什么这样排序

1. **A 层（P8-6/7/8 + P8-3）最优先** —— 它是"93 个角色机制"的**总开关**。在此之前做任何角色内容
   都是往 `Battle` 里塞 `switch (cid)`，做完还要返工。**已完成** ✅
2. **B 层（P9）紧随** —— 现在只有 5 只怪有技能、其余全平 A，没有难度曲线。
   `enemy_skills.json` 的自建表**隔离了数据源缺失这个外部风险**（找到源数据只换加载处）。
3. **C 层（P10）是"填口子"** —— 全部依赖已完成的机制，**不该出现新架构**。
   若某任务需要新架构，回退拆小。
4. **D 层收尾** —— demo 与基准，最后做。

**每步保持：可编译 → `.\gradlew.bat test` 全绿 → 提交。**

---

## 12. 遗留缺陷登记（原 `CODE_REVIEW.md` + `DOC_VS_CODE.md` 并入，2026-09-26）

> **那两个文件已删除**（`CODE_REVIEW.md` 402 行 / `DOC_VS_CODE.md` 927 行）。理由：它们是一次性分析快照，
> 且腐烂得厉害 —— 同一轮里我就修正了其中 **4 条状态描述、2 处行号**，还发现一条注记与实测相反。
> **未修条目全部迁到这里；已修条目丢掉**（全文在删除前的提交里：`git log --diff-filter=D --name-only`）。
> 取舍标准：**"已修清单"会腐烂，"未修清单"是待办** —— 所以只留开着的。
>
> ⚠ 编号沿用原文件的 `H-*` / `M-*` / `L-*` / `N-*` / `F-*`，旧文档里的交叉引用仍然对得上。
>
> 🔴 **本表只做过抽查，不是逐条核过代码的。** 迁移时我照抄了原文件自己的 ✅ 标记，**没有对代码**，
> 结果第一次抽查就抓到 **3 条早就修好的**（`M-1` `setTopZero` 认 `currentActor`、`M-2`
> `advanceActionByPercent` 有 clamp —— P7 的 E1/E3 修的；`L-6` `removeCombatant` 清 `currentActor`）。
> **动手前先看代码，别信这张表。**（2026-09-26 核实为**仍开着**的：M-5、M-6、M-11、M-12、L-3、L-4、
> L-7、L-12、H-1、N-3；同日已修：M-5、M-12。）

### 12.1 High（未修）

| # | 位置 | 问题 |
|---|---|---|
| H-1 | `Constant.java` | ✅ **已修（2026-09-27）**：加载期用递归助手 `frozen(...)` 把**容器**冻结（`Collections.unmodifiableMap/List` + `LinkedHashMap`/`ArrayList` 保序，**不用 `Map.copyOf` 因为它不保序**）。原文成立：`public static final` 只锁引用，Gson 交回的是可变 `LinkedHashMap`，**嵌套层也一样**（`SKILLS.get(cid)` 是 map、`SKILL_TRACES.get(cid)` 是 list）→ 任何调用方一次 `clear()`/`put()` 就静默污染同 JVM 后续所有消费者。**先核过：全仓无人写这些表**（顶层与嵌套都没有），所以改完不破坏任何调用点。<br>**包了 8 个**：`WEAPONS`/`CHARACTERS`/`SKILL_TRACES`/`SKILLS`/`MONSTER_TEMPLATES`/`HARD_LEVEL_GROUPS`/`BREAKING_RATE`/`ENEMY_SKILLS`；**3 个本来就不变**（`RELIC_SETS` 走 `RelicSets.index` 的 `Map.copyOf`、`MONSTER_CONFIGS`、`ENEMY_SKILLS`）—— 其中 `RELIC_SETS` 我先包了一次，**被 `RelicSetTest` 的 identity 断言（"读一次并缓存"）抓住**，退回不包。<br>⚠ **范围是容器、不是 bean**：bean 里的字段/内部 map 仍可变 —— `RelicMainAttribute.getAttributeByStar` 那两处仍是 **N-11**。<br>**验收**：`ConstantImmutabilityTest` 3 条（顶层 7 张表 + 嵌套 map/list + 读取仍正常）；变异（摘掉一张表的包装 / 助手不递归）**各红一条、零连带**；全套 69 suites / 630 tests 绿；demo 不变；套件耗时仍 3s（加载期一次拷贝，看不出来）。<br>⚠ **踩坑记录**：这条测试的**第一版在失败时是有破坏性的** —— 它用 `clear()`/`put()` 去试，守卫一旦缺失就**真的把 `SKILL_TRACES` 清空**，连带毒死 9 个无关用例（10 红里 9 个是连带）。已改成"成功时也是 no-op"的写法（map 用 `remove(不存在的键)`、list 用 `set(越界下标, …)`），变异才变成干净的各红一条 |
| H-2 | `Character.java:280-295` | ✅ **早就修了**（P8-1）：`Character.Builder.build` 现在读 `character_data.json` 的 `max_energy`（`Character.java` 里那行带 P8-1 注释，并写明 **null 必须保持 0 = 没有能量条**，因为 1407 遐蝶的 null 是**设计**而不是缺数据 —— 兜底成 100 会凭空给她造一根能量条）。⚠ 我迁移这张表时照抄了旧状态，见本节开头的警告 |
| H-6 | `CanHit.java` | ✅ **已修（2026-09-27）**：拷贝构造器现在**逐个 `DoubleValue.clone()`** 深拷属性表（`DoubleValue.clone()` 本来就是真深拷贝，之前只 clone 了数组）。原文的后果成立：buff 会**就地**改 `DoubleValue`（`BoostDamageBuff.applyEffect` 调 `addModifier`），所以挂在副本上的 buff 会出现在**原体**面板上 —— 实测复现。`Character(Character)` 走 `super(other)`，因此一并修好。⚠ **原文说"没拷 `invulnerable`"是误读**：那一块是显式的"不该拷"清单（`death`/`currentEnergy`/`resources` 都归零，因为副本是新战斗的参与者而非战况快照），`invulnerable` 属同一类战斗状态 —— 已**显式赋值 + 写明理由**，不是补拷。回归 `CombatantCopyTest` 3 条（行为 + 对象身份 + 新战斗状态约定）；变异回浅拷贝 → 前两条红、约定那条仍绿。见 §12 顶部的"只做过抽查"警告
| H-9 | `Battle.startBattle()` | 从未被任何测试调用（曾 23 个测试类 0 次）→ **开场事件链零覆盖**，而"战斗开始回能"是一整类角色机制。建议补 `BattleStartTest` |

（H-3 削韧按段翻倍 / H-4 击破用标称值 / H-5 大招清零顺序 / H-7 `hasBuff` / H-8 同速无裁决 —— **均已修**，不再列。）

### 12.2 Medium（未修）

| # | 位置 | 问题 |
|---|---|---|
| M-1 | `Queue.java:226` | ✅ **早就修了**（P7 的 E1）：`setTopZero()` 现在重置 `currentActor`（`Signal acting = currentActor`），不是堆顶。⚠ 迁移这张表时照抄了旧状态，见本节开头的警告 |
| M-2 | `Queue.java:328` | ✅ **早就修了**（P7 的 E3）：`advanceActionByPercent` 现在有 `Math.max(elapsed, …)`，与 `advanceAction` 一致。原文说实测 102961 组里 8814 组结果小于 `elapsed` —— 那是修之前的事。⚠ 我迁移这张表时照抄了旧状态，见本节开头的警告 |
| M-3 | `AttributeBuilder.java:124` | `build()` **直接交出内部 `DoubleValue`**（未 clone），且 `getOrCreate` 用 `computeIfAbsent` 缓存 → 同一 builder 两次 `build()` 拿到同一批对象（与 H-6 同族，builder 侧） |
| M-4 | `EnemyFactory.java:59` | 只灌 5 个属性，**漏了 `EFFECT_HIT_RATE`**（`EnemyScaler` 已算出）→ 敌人命中率恒 0，那是个"看着被用了其实没有"的死输出 |
| M-5 | `BuffManager.java` | ✅ **已修（2026-09-26）**：`clearAll()` 现在也清 `blocked`。原文：不重置 `blocked` → 控制 buff 在 tick 里到期置位后，清空列表 `canAct()` 仍 false 直到下次 `beforeMove()`，**驱散也救不回来**。回归 `BuffManagerTest.clearAllAlsoClearsTheBlockedFlag`（原 `clearAllRemovesEverything` 抓不到，因为它那个眩晕从没被 tick 过） |
| M-6 | `BuffManager.java:54` | `blocked` **只对 early buff 生效**（`beforeMove` 先清标志再 tick）→ 后置控制 buff 的"最后一回合"什么都挡不住。"晕眩最后一回合是否还挡"取决于 tick 时序而非语义 |
| M-7 | `Character.java:84,91` | 两个 `fromAttributes` 工厂各自残留 null：`(Translate,…)` 版 `skillLevel` 为 null、`(String,…)` 版 `relicSuit`/`weapon` 为 null → 相应 getter/setter 直接 NPE |
| M-8 | `Character.java:235` | `build()` 把可变 `relicSuit`/`weapon`/`skillLevel` **直接交给角色**（无防御拷贝）→ 同一 builder 造的两个角色共享一套遗器与一个 map |
| M-9 | `Weapon.java:71` | **光锥永远取叠影 1 的被动**（`weaponSkillData.getFirst()`），而表按叠影档位索引 → 23042 永远 +18% 速度，拿不到 +21%…+30%。另外 `getFirst()` 对空列表抛 `NoSuchElementException` |
| M-10 | `RelicSuit.java:55` | `addToSuit` 覆盖槽位字段但 **`total.add(relic)` 不移除旧的** → 两件身甲一起算进面板（白送一件）；`clone()` 与原件因此**可以不一致** |
| M-11 | `CanHit.java` | ✅ **已修（2026-09-27）**：`gainEnergy` 在**乘积**上加守卫 `!(proposed > 0)`，并给 `maxEnergy - currentEnergy` 加**下界 0**。原文三条全部实测复现：① `NaN` 金额绕过 `amount() <= 0`（`NaN <= 0` 是 false）→ `Math.min(…, NaN)` → 能量永久 NaN、`isEnergyFull()` 恒 false、**大招再也放不出且无人报错**；② 负效率（`ENERGY_REGENERATION_RATE <= -1`）把"获得"变成扣除；③ 能量已超上限时 `Math.min(负数, gain)` 会把能量**降下来**。<br>⚠ **`amount()` 那行守卫没动 —— 我第一版改错了**：写成 `!(amount > 0)` 后**变异显示没有任何测试结果变化**（NaN 被下游乘积守卫兜住），于是退回原来的 `<= 0`（最小改动），注释里写明"**这里不是拒绝 NaN 的地方**"。同一轮还发现该守卫**另有其用**：`负金额 × 负效率 = 正数`（`-10 × -2 = +20`），所以"负的获得"会**加**能量 —— 这条现在有测试（去掉守卫即红）。<br>**范围**：`setCurrentEnergy` 仍是**裸写**（同 `Resource.setValue`），超上限状态仍可能存在；变的是"获得"不再顺手把它降下来（降上限是另一个操作）。<br>**验收**：`EnergyGainSafetyTest` 6 条；修前 **4 红 1 绿**（正常路径本来就绿，所以修复不能动它）；全套 70 suites / 636 tests 绿；demo 不变。**变异**：去掉下界 → 只红"超上限不被降低"；金额守卫改严格形式 → **零红**（故未采纳）；完全去掉金额守卫 → "负金额 × 负效率"红 |
| M-12 | `BuffManager.java` | ✅ **已修（2026-09-26）**：**每一次遍历 `buffs` 都走 `List.copyOf`**（16 处），`processBuffTick` 从 `removeIf` 改成显式快照循环 + 显式移除。原文：`onDamage`/`afterAttack` 遍历活列表，而回调恰恰是"buff 再挂 buff"的地方 → **CME 或静默跳过**。**实测复现**：一个受击时给自己挂 buff 的反应，`applyDamage` 直接抛 `ConcurrentModificationException`（在伤害结算内部，最难查的位置）。回归两条：`aBuffMayAttachAnotherBuffWhileReactingToDamage` / `WhileTicking`（后者走 `removeIf` 那条路）。代价是每次遍历一次小拷贝（一个单位几个 buff），换来"没有例外可被后人改回去"——规则写在 `BuffManager` 的类 javadoc 里。变异：两处各自改回活列表，**各红一条** |
| M-13 | `AbstractBuff.java:52` 等 6 处 | 主代码留着 `IO.println`，其中 `decreaseDuration` 那条**每次 buff tick 都打**。`slf4j`+`log4j` 已配好却**主代码 0 处使用**。~~`StunBuff.removeBuff` 那行已删（2026-09-26，它会插进调用方输出）~~ |
| M-14 | `SkillData.java:41,82` | **未知 `cid`/`skillId` 静默返回"假的非伤害技能"**（`EMPTY` = PHYSICAL + ENHANCE + 空参数）→ 打错/未实现的 id 与被动无法区分：0 伤害、无报错、照样回能。建议 fail fast |
| M-15 | `SkillData.java:20,56` | javadoc 称 immutable，但 `skills` 及其内层 list 是 Gson 造的**可变** `ArrayList`；而 `DefaultSkill.DATA_CACHE` 把同一实例共享给所有实体/战斗 → 一次改动污染全进程 |
| M-16 | `Character.java:104,243` | 六个技能槽**全解析成槽位 1（普攻）** → 演示里的伤害数值全是错的（P8-2 已修槽位映射；本条的**剩余风险**是 `SkillData.EMPTY` 的静默零伤害会把将来的 bug 伪装成"设计如此"） |
| M-17 | `JSONReader.java` | ✅ **已修（2026-09-27）**。原文是两半，状态不同：① "缺失 → 裸 `requireNonNull` NPE → `ExceptionInInitializerError`，此后该 JVM 每次访问 `Constant` 都失败" —— **这半早就不成立了**：`fromJSON` 现在对缺失文件直接抛 `IllegalStateException`，消息带文件路径 + "按 README 生成数据"的指引（javadoc 也已同步）。② 剩下那半是真的、也正是这次补的：`GSON.fromJson` 对**空文件或字面 `null`** 返回 `null`，原样交回去就是 `Constant.WEAPONS = frozen(null)` → 在很远的某行 NPE，或表现得像"这张表本来就是空的"（后者更坏，因为沉默）。现在同一处抛 `IllegalStateException`；**空表仍然合法**（`{ }` 解成空 map，不是 `null`，`stages()` 那种容忍缺文件的行为不受影响）。<br>**顺带**：`Benchmark` 的 `data == null` 分支确实永远走不到（原文指的就是它），已删。<br>**验收**：`JSONReaderTest` 4 条（缺文件 / 字面 `null` / 空文件 / 对照：真数据仍解析 + `{ }` 仍合法）；夹具在 `src/test/resources/data/{null_literal,empty_table,empty_table_but_valid}.json`（主数据目录被 gitignore，测试不能共用）。修前 **2 红 2 绿**（红的正是 null 两条），修后全绿；全套 72 suites / 647 tests |
| M-18 | `MonsterDataTest.java:56` | `everyModifyRatioIsPresentAfterNormalisation` **不可能失败**：`normalizeMonsterConfigs` 总会用 `orOne` 补 1.0，它断言的是归一化自己的后置条件 |
| M-19 | `AttributeBuilder.java:95` | `addPercentPoint` javadoc 说"不应用于 percent 类型"，实现却走 `addPure`（会把 percent 重定向成平值）→ 加 0.3 点血而不是 30%。今天安全**只因为** 4 个调用点各自抄了一遍守卫。建议该方法直接拒绝 percent |
| M-20 | `MapUtils.java:48` | `getRandomList` 用 `distinct().limit(n)` 拒绝采样：`sampleSize` 接近 `list.size()` 时期望工作量 `n·H(n)` 且尾部无界；`sampleSize < 0` 静默返回空表；null 给的是裸 NPE 而非 javadoc 承诺的 `IllegalArgumentException` |
| M-21 | `EliteGroup.java:17` | record **没有 `@SerializedName`**，而它自己的 javadoc 就在警告这个坑。用 tbgd 的 `{"HPRatio":…}` 会让除一个字段外全读成 0.0、血量乘 0 且无校验。当前只被 `EnemyScaler` 用到，所以不炸 |
| M-22 | `SkillData.java:47` | `maxLevel` **从未被读取**，而 `SkillExecutor` 在 `level-1` 越界时**静默 return** → 0 级或超上限技能零伤害无报错。存在的校验字段是死的 |
| M-23 | `TestSkillGroup1.java:15` | 测试脚手架放在 `src/main`：静态初始化硬编码 cid/槽位、绕过缓存、仅仅加载该类就强制全量数据加载；注释描述的技能与实际槽位（AoEAttack）不符 |
| M-24 | `SkillExecutor.java` | ✅ **已修（2026-09-27）**：`SKILL_CAST` 原来表示"**除终结技以外的一切施放**"（发出端只有两路），所以**普攻也触发它** —— 遗器套装 109「施放战技时攻击力提高 20%」（`relic_sets/109.json`，注释里还写着"发出端已经分开了"，实际没有）与知更鸟的 `模进乐段`（+5 能量）都在普攻上白给一次。**发现方式**：把知更鸟做完整时，"她自己的普攻不该给她这 5 点"这条断言直接红了（`RobinTraceTest.herOwnBasicAttackIsNotASkillCast`）—— 不是读代码读出来的。<br>**修法**：发出端**三路分流**。新增 `TriggerEvent.BASIC_ATTACK`（数据 `Normal`，**含强化普攻**，因为数据里两者都写 `Normal`）；`SKILL_CAST` 收窄为 `BPSkill`；秘技 / 地图普攻 / 助战 / 欢愉伤害 / 天赋（`attack_type` 为空）**一个都不发** —— 它们不是"战斗内施放"，需要就得自己申请事件，在那之前是**加载期响亮报错**而不是静默不触发。判定仍在发出端、读**解析出的 `SkillCategory`**，因为条件 DSL 没有"这次施放是什么"这个变量（和当年分 `ULT_CAST`/`SKILL_CAST` 同一个理由）。`ALLY_ATTACK` 不动：命中即算，终结技也算。数据为空的技能（`EnemySkill`、测试手搓的占位）按 `UNSPECIFIED` 处理、三个都不发，保住了原 `isUltimate` 助手"没数据就不能作证"的规矩。<br>**验收**：`UltCastTriggerTest` +3 条（普攻只发 `BASIC_ATTACK`；普攻仍发 `ALLY_ATTACK`；战技不发 `BASIC_ATTACK`）+1 条（地图普攻与天赋一个都不发）；`RobinTraceTest` 端到端 1 条。**变异**（把 `NORMAL` 改回走 `SKILL_CAST`）→ **4 条各自独立变红**（含既有的 `TriggerTableTest.robinDoesNotTriggerOnHerOwnAttack`），说明覆盖不靠同一条断言。两条既有测试因为"描述的是 Robin 只有天赋时的表"而必须更新（`TriggerDataBindingTest` / `TriggerTableTest`），现在都按**事件**断言条数。<br>⚠ **顺带学到的坑**：这类测试的"战技点指纹"必须让**总和不超过上限 5**（`Constant.SKILL_POINT_MAX`），否则饱和后分不清"触发 1 条"与"触发 2 条"—— 我第一版用 8+4，报了 5 |

### 12.3 Low / Nit（未修）

| # | 位置 | 问题 |
|---|---|---|
| L-1 | `AttributeType.java:115` | ~~`fromString` 大小写不敏感~~ ✅ **已修（P10-3 前半）**，本条只留两个未使用 import |
| L-2 | `Signal.java:67`/`Queue.java:50`/`Battle.java:388` | `10000` 行动周期常量三处硬编码（`Queue` 有自己的 `ACTION_THRESHOLD`），违反"数值一律进 `Constant`" |
| L-3 | `Signal.java:59` | `refreshSpeed()` 跳过构造器有的 `speed > 0` 校验 → 0/负速 debuff 让该单位**永远不再行动**而不是 fail fast；javadoc 大喊要调用它却没人调 |
| L-4 | `Signal.java:23,81` | `Cloneable` + `clone()` 无人使用，克隆会产生**同一 `CanHit` 的第二个 Signal** → 潜在"一个单位一回合动两次" |
| L-5 | `Queue.java:47` | 类级 `@Getter` 暴露 **`getHeap()`（活的内部 `PriorityQueue`）**，调用方可破坏排序不变量；`resetSignal`/`getCombatant`/`rebuildHeap` 无调用者 |
| L-6 | `Queue.java:175` | ✅ **早就修了**：`removeCombatant` 现在会清 `currentActor`。⚠ 迁移这张表时照抄了旧状态 |
| L-7 | `Battle.java`（`tickDots`/`beforeMove`） | 无敌目标的 DOT **照样消耗结算次数**却零伤害 → 转阶段无敌的 Boss 白吃 DOT。⚠ P10-0 后修法变了：现在是"跳过 `beforeMove` 里那步倒计时" |
| L-9 | `DoubleValue.java:310-335` | 六个 `Modifier.*PercentNumber` 工厂 + `Modifier.pure(double)` **零调用者**，而它们除以 100、旁边的 `addPercent` 不除 → 典型 100× 陷阱。建议删或改名 `fromWholePercent` |
| L-10 | `BreakDamageCalculator.java:41` | `/10.0`、`/100.0` 写死；`/10` 这个数据约定在类 javadoc、`Constant.BREAKING_RATE` javadoc、两个测试里各复述一遍 |
| L-11 | `AttributeBuilder.java:43` | `setBase`/`addBase` **不做** `PERCENT_TO_BASE` 重定向，而 `build()` 又把 percent 槽位置 null → `setBase(HEALTH_PERCENT, …)` 静默消失 |
| L-12 | `DotBuff.java` | ✅ **已修（2026-09-27）**：构造器校验四项输入（`source`/`element` 非 null、`turns >= 1`、`baseDamage` 有限且 `>= 0`），消息带上违规值与后果。原文两条实测更正：① `turns <= 0` 仍结算**一次** —— 成立（`tickDots` 先结算、`processBuffTick` 之后才到期），所以 0 回合 DOT 会打一次；② "负 `baseDamage` 静默无伤害" —— **不准确**：它被 `Battle.assemble` 末尾的 `Math.max(1, …)` 下限吞掉，实际是**每回合静默 1 点**（数字是错的，且无人报错）。`baseDamage == 0` 仍合法：0 伤害仍是一次"命中"（发 `TAKING_HIT`，与护盾全吸同构），与"负数会被吞成 1"是两件事。<br>**刻意没做**：不硬编码"只有火/雷/物理/风"的元素白名单 —— 哪种元素有 DOT 是**数据**的事（`BreakEffects.hasDot()`，`attachBreakDot` 已据此判断），把表抄进模型类就是第二份真相。`source` 值得校验的原因：`Damage` 只 `requireNonNull(element/type)`，**attacker 从不检查**，`source == null` 会让 DOT 击杀记在没有人身上。<br>**验收**：`DotBuffValidationTest` 7 条（0/负回合、负/NaN/∞ 伤害、null 元素、null 来源 + 对照：合法值原样存下）；修前 **6 红 1 绿**（绿的正是对照），修后全绿；全套 72 suites / 647 tests |
| L-13 | `EnemyScaler.java:52` | `scale` 对 template/config/group 无 null 检查，缺 id 直接 NPE 且不说是哪个 id |
| L-14 | `AbstractBuff.java:47` | `isSameKind` 用 `getClass()` 比较 → **任何子类/匿名子类永远不与父类同类** → 同类替换失效、无限叠加。建议改成 buff 自报的 `kind()` 键 |
| L-15 | `Relic.java:174-183` | `checkLegal` 校验用的主词条等级与实际造词条用的 `level` **不是同一个** → JSON 里的主词条等级是死数据；子词条上界报错消息把违规值插进 "between 0 and N"；接受负 `attributeLevel` |
| L-16 | `Relic.java:92,124,329` | `getSubAttributes()` 的**可变性随工厂而变**（别名调用方 list / `ArrayList` / `Stream.toList()`）→ 同一 API 有时 `add` 成功、有时抛 `UnsupportedOperationException` |
| L-17 | `Relic.java:329` | `Relic.Builder` 不执行 `createRandomLevelZero` 的任何不变量：允许重复副词条、副词条等于主词条、不校验上界与 `star` |
| L-18 | `models/skill/SkillTrace.java:32` | 静态缓存持有**公共可变字段 + 公共可变 `children`** 的节点，同 cid 共享；javadoc 写 "per character" 其实 "per cid, process-wide"。`Character` 还无条件套用**整棵**行迹树，没有解锁门槛 |
| L-19 | `ExtraBasicPromote.java:29` | 平属性用 `addPure`（百分比**之后**），javadoc 却说"加到基础属性"，而光锥用 `addBase`（**之前**）→ 同样 +500 HP，行迹的不会随 HP% 放大 |
| L-20 | `ExtraBasicPromote.java:14` | 分量顺序 `(health, defence, attack, speed, …)` 与其它模型 `(health, attack, defence)` 不同 → 位置构造会**静默交换 ATK/DEF** |
| L-21 | `SkillAttackType.java:6` | 枚举 `SINGLE/THREE/ALL` **全仓库零引用**，且值与 `skills.json` 的 `attack_type` 完全对不上 |
| L-22 | `Relic.java:114`、`MapUtils.java:28,53` | `star <= 2` 把**合法的 2★** 拒了；随机数走 `ThreadLocalRandom`（不可播种）**违反"随机数一律走注入 `Random`"** |
| L-23 | `Camp`/`Summon` | `getCamp()` 曾全仓库无调用者、`Camp.NEUTRAL` 未用。（L-8 之后召唤物已可入场，P9-4 之后**内容侧真会创建它了** —— `Battle.summon` + `SummonFactory`；`Battle.summon` 的阵营判断就靠 `master.getCamp()`。`Camp.NEUTRAL` **仍未用**） |
| L-24 | `beans/Skill.java:11,15` | `skillID` 解析了但从不读取（槽位 id 来自外层 map 键，无人校验一致）；`StanceList` 无 `@SerializedName`，改名会静默读 0 → 削韧恒 0 |
| L-25 | `SkillEffectType.java:88,97` | `getCategory()` 无调用者；每个常量上的 `@SerializedName` **从不生效**；`BY_STRING` 是可变的 static `HashMap` |

（L-8 敌方阵型 / L-26 推条账本 —— **已修**，不再列。）

### 12.4 Nit（未修）

| # | 位置 | 问题 |
|---|---|---|
| N-1 | `DefaultSkill.java:16,10` | `static ConcurrentMap` 把可变对象当共享单例；`SkillData.init` 会在 `computeIfAbsent` **内部抛异常** → 一个 getter 在战斗时抛错且每次重抛；`:10` 的 TODO 说该删这个类，而它是唯一的 `Skill` 生产实现 |
| N-2 | `Benchmark.java:38` | 读的 `dump_data.json` 仓库里不存在；兜底分支走不到；`main()` 包私有；结果被丢弃 |
| N-3 | `CanHit.java:99-104` | 同时有 `Runnable` **字段**与**同名方法**（`beforeMove`/`afterMove`/`onBattleStart`）→ 直接调 `MoveEvent.beforeMove(battle)` 会**静默跳过 buff tick**。建议 `setBeforeMoveHook(Runnable)` |
| N-4 | `Enemy`/`Summon` | 无拷贝构造器：`new Enemy(...)` 走 `CanHit(CanHit)` 会丢掉 `damageResist`/`stanceWeak`/`stance`/`broken` 全部子类字段（`Character` 就正确重写了）。P9-4 之后丢的还多一个 `summonIds`（召唤名单），`Summon` 则会丢 `master` —— 后者尤其危险：**丢了 master 的召唤物再也不会随主人退场** |
| N-5 | `RelicSuit.java:153` | `appendAttribute` 是死方法，逐行相同的代码内联在 `appendTo`；"按属性类型分发"逻辑在 `RelicSuit`/`Weapon`/`SkillTrace` **三处复制**。建议下沉 `AttributeBuilder.add(type, value, source)`（顺带消掉 M-19 那 4 处重复守卫） |
| N-6 | `models/Buff.java:3` | 接口上留着裸 `// TODO`；`Buff.setSource/getSource` 全仓库无人调用，`AbstractBuff.source` 恒 null → **接口契约实际未实现**（`DotBuff` 用了 `setSource`，所以只剩 getter 侧无人读） |
| N-7 | `BreakDamageCalculator.java:21` | javadoc 例子"30 点普攻 × 2.5 击破加成 = 112.5"在仓库里无法复现，会误导读者以为调用方要预乘 |
| N-8 | `Queue.java:269,299,324` | 在 for-each 遍历 `heap` 的同时改键再 `rebuildHeap()` 清空同一个 heap —— **只因为每个分支立刻 `return` 才安全** |
| N-9 | `QueueTest.java:17,42` | 对没有 `equals` 的 `Character` 用 `assertEquals` 其实是身份断言；`:18,29` 的 `50` 从私有常量推出 |
| N-10 | `SkillData.java:4,97` | 在声明了 `models.Skill` 的包里 import `beans.Skill` 易混；`@AllArgsConstructor` 里两个 `List` 参数调换顺序能静默编译并错绑 |
| N-11 | `RelicMainAttribute`/`RelicSubAttribute` | 同一个"非法 star"错误两处抛不同异常类型；`getAttributeByStar` 返回可变 map（与 H-1 同类） |
| N-12 | `LevelPromotionCalc.java:23,44` | 边界行为无文档：`level == 80` 的 clamp 让突破/未突破相等；光锥倍率从 20 级 3.85 **降到** 21 级突破后 3.60（反直觉到该写注释）。未发现 off-by-one |

### 12.5 引擎能力缺口（原 `DOC_VS_CODE` §F，只列还开着的）

| # | 缺口 | 说明 |
|---|---|---|
| F-1 | **战技点上限不是恒定值，且引擎没有"改队伍级资源上限"的口子** | 接口已就位（换策略构造参数），**没有接线**；受影响的套装效果落在 `_unmodelled.json` 里 |
| F-3 | ⚠ **「普攻 +1」是一刀切，强化普攻有例外** | 对青雀对、**对波提欧错** —— 这是登记表里**唯一一条让引擎算错数**的项，属**数据补全** |
| F-4 | 角色 / 光锥 / 遗器级的**供点机制全部未接** | 与 F-1 同族 |
| F-5 | `EnemySkill.getData()` 恒为 `null` | 敌方行动绕过 `SkillExecutor`，不发 `SkillCastEvent`。⚠ 另外：条件 DSL **没有"阵营"变量**，所以"敌人施放技能"目前**无法表达**（这是设计口子，不是补一行） |
| F-7 | `hasSkillPoint()` 语义过窄 | 只看"够不够这一次" |
| F-9 | 技能效果参数表 | ✅ 表已存在（`skill_effects.json`，40 条 / 26 条可用）；**仍缺**：14 条 `params` 为空、`BUFF`/`CONTROL`/`SUMMON` 技能没有"施加什么"的表、`IMPAIR` 分支缺"哪个 debuff" |
| F-10 | 套装具名 ability 只有一部分能表达 | 92 条效果里 35 条是纯 ability：**精确表达了 4 条、登记了 31 条**（`_unmodelled.json`，每条写明缺哪种能力），由 `RelicTriggerTableTest` 钉住 35 = 4 + 31 |

（F-2 开局战技点 / F-6 `applySkillPointCost` 裸字符串 / F-8 战技点策略与机制挤在 `useBattle` —— **均已解决**。）

### 12.6 待核实项（原 `CODE_REVIEW` §4，只列还没结论的）

| # | 事项 |
|---|---|
| V-1b | `params.get(1)` 在**小数段数**技能上的语义（瓦尔特的 0.65、桑博的 0.28、景元的 0.33…）：当前 `(int)(double)` 会算出 **0 段**（技能完全不出手）。今天无害（`DefaultSkill` 只解析槽位 1，这些技能都在别的槽位），**但接真实槽位前必须搞清这个字段的语义** |
| V-2 | `StandardEnergyProvider` 读的 `attack_type` 是否真能拿到值：现有测试**手写 `SkillData`**，走不到真实字段绑定 → 测试绿不能证明真实路径正确。建议补"从 `Constant.SKILLS` 取真实普攻/战技 → 断言 20/30" |
| V-6 | 击破 DOT 缺 `(1 + 击破特攻)`：`attachBreakDot` 用 `breakBaseOf × BreakEffect.dotRatio()`，不含击破特攻。若 BE 应参与，`DotTest` 的锁死值也要改 |
| V-9 | `Character.Builder.build` **无条件套用整棵行迹树**（无解锁门槛）是否是有意的 L80 全解锁简化 |
| V-10 | `speedRatio`/`stanceRatio` 在全部 2649 条怪里**恒为 1.0** → `EnemyScaler` 这两条乘法路径永远没有真实数据覆盖（可能是导出问题） |
| V-12 | `getActionLength` 混用"当前 speed"与"旧 speed 算出的 `nextActionTime`"，中周期变速时语义未定义（该值目前只用于显示） |

---

## 附：文档分工

| 文档 | 说什么 |
|---|---|
| **本文 `ROADMAP.md`** | **还要做什么**（设计原则 + 待办 + 踩坑）+ **§12 遗留缺陷登记**（原 `CODE_REVIEW` / `DOC_VS_CODE` §F 并入） |
| **`engine.md`** | **现在是什么**（引擎说明书，逐机制给出实现位置） |
| `README.md` | 怎么跑起来 |

> **2026-09-26/27 删掉了三份文档**：`CODE_REVIEW.md` 与 `DOC_VS_CODE.md`（一次性分析快照，且已多处腐烂 ——
> 同一轮里修正了 4 条状态 + 2 处行号 + 1 条与实测相反的注记），以及 v2 存档
> `docs/archive/ROADMAP-v2-历史.md`（2446 行纯历史）。
> 前两者的**未修条目已全部并入本文 §12**，已修条目不保留 —— 已修清单会腐烂，未修清单才是待办。
> 三份全文都在 git 历史里（`git log --diff-filter=D --name-only`）。
