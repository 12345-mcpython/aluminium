# aluminium 开发路线图

一个用 Java 写的类《崩坏：星穹铁道》回合制战斗引擎。数据来自
[turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata)，经 `E:\code\python\generate_data.py`
解析到 `src/main/resources/data/`。

**当前状态（2026-09-23）**：46 个测试类 / 381 个用例全绿；demo 跑通一场三对三战斗
（10 轮 / 43 次行动，我方胜利）。引擎侧基础机制（伤害流水线、韧性击破、能量、战技点、
行动条、关卡波次、角色真实面板）已完成；**剩下的全部是"把已有口子填满"或"接数据"**。

> **v3 为什么重写**：v2 是"执行清单"（2400 行，每个已完成任务都留着完整规格），
> 结果**真正待办的事被埋在 2000 行历史里**。v3 只留：**设计原则 + 待办 + 踩坑记录**。
> v2 全文存档在 `docs/archive/ROADMAP-v2-历史.md`（含所有已完成任务的规格与当时的实测数字），
> 需要查"当初为什么这么做"时再去翻。

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
| `Constant` | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_POINTS` / `MONSTERS` / `SKILL_SLOT` / `stages()`（懒加载） |

### 2.2 数据文件速查

| 文件 | 内容 | 已确认锚点 |
|---|---|---|
| `monster_config.json` | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak, *modify_ratio, damage_resistance, debuff_resistance, summon_id}` | **1002011 冰锋**：弱 `[Fire, Thunder]`，抗 5 项 0.2，`STAT_CTRL_Frozen=1` |
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

### 🚧 部分完成

| 项 | 现状 | 归属 |
|---|---|---|
| 技能回能数据化 | `sp_base` 已落库但**不驱动回能**（多段技能是"每段值"，需聚合 `SPHitRatio`） | P3-4 剩余 |
| 战技点上限/开局可变 | **接口已就位**（换策略构造参数），**没有接线** | 见 `DOC_VS_CODE.md` §F 的 F-1/F-2 |
| 强化普攻的战技点 | 一刀切 +1：对青雀对、**对波提欧错** | F-3，数据补全 |

### ☐ 待办（共 20 项，见 §6–§9）

**下一项建议：`P8-6` 事件补齐** —— 它是 `P8-7`（触发器表）的唯一前置，而 P8-7 是
93 个角色机制的**总开关**。`P8-4` 刚做完，`P8-3` 建议并进 P8-7 一起做（见 §6 说明）。

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

## 6. 待办 A 层：角色数据化的基础设施（最高优先）

> **为什么这层最优先**：93 个角色的天赋/行迹/星魂/秘技/追加攻击目前**全是空的**。
> 引擎能给角色的只有普攻/战技/终结技/能量/战技点 —— 而这层的三项是
> "让角色机制变成数据"的**唯一通路**。做完这层，才有资格谈"像 HSR"。

**本层顺序**：`P8-6`（事件）→ `P8-7`（触发器表）→ `P8-8`（层数资源）。
`P8-3`（追加攻击）**建议并进 P8-7 做**（理由见该任务）。

---

### P8-6 事件补齐（触发器宿主）

- **目标**：把 12 类触发源都变成事件，让"角色机制"只订阅事件，不再往 `Battle` 里塞逻辑。
- **现状**：只有 4 个事件 —— `BattleEvent` / `MoveEvent` / `DamageEvent` / `AttackEvent`。
- **涉及文件**：新建 `models/event/{SkillCastEvent, EnergyEvent, HpLossEvent, HealEvent, KillEvent, BreakEvent, TurnStartEvent, SkillPointSpentEvent, SkillPointGainedEvent}.java`；
  `models/CanHit.java`（实现并转发给 `BuffManager`）；`Battle.java`、`models/SkillExecutor.java`（发事件）；新建 `test/EventBusTest.java`
- **怎么做**：
    1. 照 `AttackEvent` 的现成模式：接口 + 全部 `default` 空实现 + `CanHit` 转发 `BuffManager` + `Battle` 广播给友方。
    2. 每个事件必须携带**足够还原事实**的字段：
        - `SkillCastEvent(battle, user, skill, hitTargets)`（**非伤害技能也要发**）
        - `EnergyEvent(battle, target, actuallyAdded)`（用 `gainEnergy` 返回的**实际入账值**）
        - `HpLossEvent(battle, target, before, after, source)`（`after - before` 就是损血量，遐蝶/万敌/刃要）
        - `HealEvent(battle, healer, target, amount)` / `KillEvent(battle, attacker, target)` /
          `BreakEvent(battle, attacker, target, element)` / `TurnStartEvent(battle, actor)`
        - **`SkillPointSpentEvent` / `SkillPointGainedEvent`**（P8-4 复核新增，见下）
    3. 发送点：`SkillExecutor.execute`（技能）、`Battle.applyEnergyGain`（能量）、
       `CanHit.takeDamage`/`heal`（损血/治疗，**口径与 `isCountsAsAttack()` 一致**）、
       `Battle.grantHitAndKillEnergy`（击杀）、`Battle.gainBreakEnergy`（击破）、
       `Battle.beforeMove`（回合开始）、`StandardSkillPointPolicy`（战技点，或由 `Battle` 在转发时发）
    4. ⚠ **不要顺手把效果实现也写了** —— 本任务只发事件。
- **为什么需要 `SkillPointSpentEvent`**：米沙「我方全体每消耗 1 个战技点 → 下次终结技 +1 段、米沙回 2 能量」
  和花火「我方消耗战技点时额外回 1 点能量」要监听的是"战技点**被消耗**"这件**事** ——
  这是 `EnergyProvider`/`SkillPointPolicy` 那种"按技能类型查表"的钩子**表达不了**的。
- **验收**：`EventBusTest`：挂一个测试 buff，断言收到的 `(事件, 角色, 数值)` 序列；
  DOT/附加伤害**不发** `KillEvent`；无能量条角色**不发** `EnergyEvent`；
  战技点不足时**不发** `SkillPointSpentEvent`（因为没花出去）。
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

---

### P8-8 层数资源 Resource（替代能量条）

> ✅ **前置已完成**：`models/Resource.java` **已经存在**（P8-4 重构时为战技点抽出来的），
> **不要**再新建同名类。已有：有界值 + `gainClamped`/`gain`（显式溢出）/`spend`/`spendExactly`、
> `setMaxOverflow`、`isFull`/`isCapped`/`missingToMax`、不变式 `value ∈ [0, max+overflow]`（有测试）。
> 战技点是它的第一个用户，**本任务是第二个**。

- **目标**：让"层数当能量 / 层数触发大招"的角色（飞霄【飞黄】/ 黄泉【残梦】/ 白厄【火种】/
  昔涟【追忆】/ 遐蝶【新蕊】）不写专用类。
- **涉及文件**：**扩展** `models/Resource.java`、新建 `models/ResourceManager.java`；
  `CanHit.java`（挂 manager）、`Constant.java`、扩展 `test/SkillCategoryAndResourceTest.java` 或新建 `test/ResourceTest.java`
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

---

### P8-3 天赋 + 追加攻击（建议并进 P8-7）

> ⚠ **建议不要单独做**：v2 的原始方案是在 `Battle` 加 `List<TalentTrigger>` +
> `CharacterFactory` 里 `switch (cid)` 注册 —— 那是**过渡实现**，P8-7 一落地就要返工，
> 而且正好踩在"引擎不认角色"的红线上。**正路**：P8-6 补 `AttackEvent` 的"攻击后"语义，
> P8-7 用触发器表表达"受击后追加一段"。

- **前置阻塞**：⚠ **数据里没有 `is_follow_up` 字段**，槽位 4 天赋的 `attack_type` 是 `null`，
  数据里强化普攻与普通普攻**都是 `"Normal"`**。所以"哪个技能算追加攻击"目前**无从判断**。
  开工前必须先定来源（翻 `Config/ConfigAbility`，或维护一张手工表）。
- **代表角色**：克拉拉 1107（受击反击）、希儿 1102（击杀额外回合）。
- **验收**：`TalentTest`：克拉拉被打 → 追加 1 段（`DamageType.ADDITIONAL` 且 `getCountsAsAttack() == false`，不回能不削韧）；
  希儿击杀 → 该角色行动条立即提前。

---

### P8-5 真实队伍装配（`StageFactory` 换真角色）

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

### P9-2 `EnemySkill` 全效果

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

### P9-3 敌方 AI 技能选择器

- **目标**：敌人回合**先选技能再选目标**（P5-4 已有目标选择，缺技能选择）。
- **涉及文件**：新建 `models/ai/SkillSelector.java`、`Main.java`、新建 `test/SkillSelectorTest.java`
- **怎么做**：纯静态 `next(enemy, battle, rng)`：过滤 `conditionOk` → 按 `aiWeight` 加权随机。
- **验收**：`SkillSelectorTest`：权重 1.0/1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%）；
  `condition "hp<0.5"` 满血时选不到。
- **依赖**：P9-2、P5-5

### P9-4 召唤物（`summon_id` 机制）

- **目标**：`monster_config.summon_id` 生效：敌人技能召唤实体入战，实体可受击、会死亡移除。
- **涉及文件**：`models/Summon.java`（现有骨架，**全项目没有 `new Summon(...)`**）、新建 `utils/SummonFactory.java`、
  `Battle.java`、`models/Enemy.java`、新建 `test/SummonTest.java`
- **怎么做**：
    1. `SummonFactory.create(summonId, level)`：召唤物同样有 `monster_config` entry →
       `EnemyFactory.create` + `setSummon(true)`。
    2. `Battle.summon(boss, summonId)`：进 `enemies` + 走 `addRequestItems` 进场（复用 P7-4 的路，那条**是活的**）。
    3. 本体重伤 → 召唤物同判移除（`removeDeadCombatants` 清理）。
- **验收**：`SummonTest`：触发后 `enemies.size()` 增加；召唤物被打死 → 数量回落；本体死 → 全清。
- **依赖**：P9-2、P5-5、P7-4
- **注**：**忆灵**（角色专属召唤、面板快照、`DamageType.MEMORY`）是远期，本任务只做通用怪物召唤。

### P9-5 Boss 机制（phase 换招 / 受击反击 / 控制免疫）

- **涉及文件**：`models/Enemy.java`、新建 `models/buffs/CounterMechanic.java`、`Main.java`、新建 `test/BossMechanicTest.java`
- **怎么做**：
    1. `Enemy.phase`：HP 阈值切技能列表（用 `condition` 表达）。
    2. 受击反击：用 `DamageEvent` → 追加一段 `DamageType.ADDITIONAL` + `notCountsAsAttack()`，
       **反击目标 = 该段的 `damage.getAttacker()`**（"施放技能的个体"，不一定等于角色本人）。
    3. 控制免疫：`Enemy.isImmuneTo(resistKey)` 查 `debuffResist`；奥钦 `STAT_CTRL: 0.5` → P6-1 的
       `hitChance` 传 `"STAT_CTRL"` 自然半减。⚠ **key 要精确匹配**（冰锋是 `STAT_CTRL_Frozen=1`）。
    4. ⚠ **阶段推进绝不能用"0 血不死"实现**：`takeDamage` 在 HP≤0 时立刻 `death = true`。
       要锁血就用 `setInvulnerable(true)` 再**显式重置 HP**；否则要么阶段被跳过，要么**鞭尸**。
       多血条同样按"打空一段 → invulnerable → 重置 HP"。
- **验收**：`BossMechanicTest`：HP 降到 50% 以下换招；受击反击段 `getCountsAsAttack() == false`；
  冰锋对冻结免疫。
- **依赖**：P9-2、P1-7、P6-1

---

## 8. 待办 C 层：机制补完（全是"填口子"，无新架构）

> 原则一条：**任何任务做到一半发现需要新架构，说明它跑偏了，回退并拆小。**

### P10-1 七系击破异常全量

- **现状**：只做了 4 系 DOT（火/雷/物理/风）。缺冰（冻结）、量子（纠缠）、虚数（禁锢）。
- **怎么做**：`Constant` 加 `record BreakEffect(dotRatio, dotTurns, delayPercent, control)` +
  `Map<DamageElement, BreakEffect> BREAK_EFFECTS`；P4-5 的挂 DOT 代码改为**查表**；
  击破推条统一 = 固定 25% + `delayPercent`。比例先示例值 + `TODO data`。
- **验收**：`BreakEffectAllTest`：冰击破 → 无 DOT、推条 25%+50%；量子 → DOT + 推条 25%+20%；物理 → 仅 DOT（回归 P4-5）。
- **依赖**：P4-5、P4-4

### P10-2 控制异常状态机

- **怎么做**：`ControlBuff extends AbstractBuff`（冻结/眩晕 → `canAct() == false`；禁锢/纠缠靠推条）；
  `BuffManager.hasControl()`；冻结期受伤害 +30%（示例值）；施加方走 P6-1 的 `hitChance` +
  **先查 `isImmuneTo`**（P9-5）。
- **验收**：`ControlTest`：冰锋被冻结 → `hitChance == 0`；普通怪 → 跳回合后恢复；禁锢 → 延迟 30%。
- **依赖**：P6-1、P9-5、P4-4

### P10-3 Buff 体系完善（属性类 + 刷新规则）

- **怎么做**：
    1. `StatModifierBuff`：`applyEffect` → `addPercent`，`removeBuff` → 减去同等值。
    2. `BuffManager.addBuff` 刷新规则：**同 class + 同 target → 覆盖**（重置 duration）；不同 class → 叠加；
       同 class 不同来源 → 取绝对值大者（HSR 近似，`TODO data`）。
    3. `SkillExecutor` 非伤害分支**落地**（现在只静默 return）：`RESTORE` → `Battle.heal`（P6-2 已实现但**无自动分派**）；
       `SUPPORT/DEFENCE` → `StatModifierBuff` / `grantShield`（P6-3）；`ENHANCE` → 纯被动不执行。
    4. 光锥/遗器数值被动（P8-5 遗留）用本 buff 表达。
    5. **遗器套装效果**（`relic_sets.json` **连装载都没装载**）也归这里。
- **验收**：`BuffRuleTest`：攻 +50% 2 回合 → 伤害 ×1.5；再上同 buff → duration 刷新不叠加；到期回 ×1.0。
- **依赖**：P6-2、P6-3、P1-7

### P10-4 速度与行动条操纵

- **现状**：E1–E4 已修（速度变化立刻重排、同值平局裁决），但**公开 API 还没上移**。
- **怎么做**：`Battle` 公开 `delayMovePercent` / `advanceMovePercent`，P4-4 的击破推条改调这里（删重复代码）。
- **验收**：`SpeedBuffTest`：speed 100 减速 30% → 下次行动间隔 ≈ 142.857；拉条 50% → 提前半圈。
- **依赖**：P10-3、P7-1

### P10-5 终结技插入

- **怎么做**：确认规则（不消耗行动条、不占回合、先清零再回自身 5）；`castUltra` 后**不触发** `afterMove`、
  `processRequests` **不产生新回合**；demo 加"回合开始前可放大招"的输入位。
- **验收**：`UltraInsertTest`：满能量任意时点 `castUltra` true；行动条前后一致；能量 = 5 × (1+回能率)。
- **依赖**：P3-2、P7-2

### P10-6 Debuff 基础概率数据化

- **怎么做**：`SkillData` 加 `debuffChance()`（`param_list` 第 3 项为几率，**先校准 2 个技能**，
  失败再调 index，注释写清约定来源）；`SkillExecutor` 的 `IMPAIR` 分支接 P6-1 的 `applyDebuffChance`。
- **验收**：`DebuffChanceDataTest`：读出的概率 ≠ 0 且与手写 baseChance 一致；沿用 P6-1 三例回归。
- **依赖**：P10-2、P6-1、P8-2

---

## 9. 待办 D 层：演示与收尾

### P11-1 `Main` 修复 + demo 包拆分

- **目标**：`Main` 变成 `public static void main(String[] args)` 入口，逻辑拆进 `demo/`。
- **怎么做**：属性预览搬 `demo/CharacterDemo`；战斗搬 `demo/BattleDemo`；`Main.main` 只留调用。
- **验收**：`.\gradlew.bat run` 能跑；`Main` 只剩入口调用。
- **注**：当前 `main()` **无参数**（`Main.java` 用的是无参 `public static void main()`）。

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

1. **A 层（P8-6/7/8）最优先** —— 它是"93 个角色机制"的**总开关**。在此之前做任何角色内容
   都是往 `Battle` 里塞 `switch (cid)`，做完还要返工。
2. **B 层（P9）紧随** —— 现在只有 5 只怪有技能、其余全平 A，没有难度曲线。
   `enemy_skills.json` 的自建表**隔离了数据源缺失这个外部风险**（找到源数据只换加载处）。
3. **C 层（P10）是"填口子"** —— 全部依赖已完成的机制，**不该出现新架构**。
   若某任务需要新架构，回退拆小。
4. **D 层收尾** —— demo 与基准，最后做。

**每步保持：可编译 → `.\gradlew.bat test` 全绿 → 提交。**

---

## 附：文档分工

| 文档 | 说什么 |
|---|---|
| **本文 `ROADMAP.md`** | **还要做什么**（设计原则 + 待办 + 踩坑） |
| **`engine.md`** | **现在是什么**（引擎说明书，逐机制给出实现位置） |
| `DOC_VS_CODE.md` | **文档与代码不一致** + §F 引擎能力缺口登记（F-1…F-8） |
| `CODE_REVIEW.md` | 历史上的代码审查问题与修复记录 |
| `README.md` | 怎么跑起来 |
| `docs/archive/ROADMAP-v2-历史.md` | v2 全文：已完成任务的完整规格与当时的实测数字 |
