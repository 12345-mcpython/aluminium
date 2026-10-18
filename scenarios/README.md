# scenarios —— 与原游戏逐乘区对数值

一个场景 = **一次施放**：谁、什么技能、几级、打谁、什么面板，以及**原游戏给出的每一个乘区的数**。
比较器把引擎的一次结算按乘区拆开（`Damage.breakdown()`），逐行对，差多少直接打出来。

```
gradlew run --args="compare scenarios/1002-skill.json"
gradlew run --args="compare"                     # 列出 scenarios/ 下的场景
```

退出码：**每一行都对上 = 0**，有差异 = **1**（所以它能当闸门用；`--args=compare` 不会进 `all`）。

---

## 1. 文件格式

```json
{
  "name": "一句话说明这个场景",
  "note": "这些数从哪来（录像 / 表格 / 手算）",

  "attacker": {
    "cid": 1002,
    "level": 80,
    "eidolon": 0,
    "skill": "SKILL",
    "skillLevel": 10,
    "attributes": { "ATTACK": 2000.0, "CRIT_CHANCE": 0.05 }
  },

  "target": {
    "monsterId": 1002011,
    "level": 90,
    "hardLevelGroup": 1,
    "attributes": { "HEALTH": 50000000.0 }
  },

  "crit": false,
  "instance": 0,
  "instances": 4,
  "tolerance": 1e-6,
  "expect": { "base": 629.13942, "final": 264.022858 }
}
```

| 字段 | 必填 | 含义 |
|---|---|---|
| `attacker.cid` / `level` | ✓ | 角色与等级（引擎按真实数据建面板） |
| `attacker.eidolon` | | 星魂 0–6（会真的装上星魂规则） |
| `attacker.skill` | ✓ | `COMMON` / `SKILL` / `ULTRA` / `TALENT` |
| `attacker.skillLevel` | | 施放等级；不写 = 该表的最大行（本库的 Lv10 口径） |
| `attacker.attributes` | | **绝对**属性覆盖，让两边用同一面板（见下） |
| `attacker.base` | | 直接给**基数层**（= 捕获里的 `damage_value`）。给了它，引擎的 `base` 就按 `ATK = base / 倍率` 反解，比较从游戏的起点开始 |
| `target.broken` | | `true` = 目标已处于击破状态。它影响减伤区：未击破的敌人会**再叠 0.9 的韧性减伤** |
| `target.buffs` | | 捕获里的伤害侧状态：`[{"kind":"vulnerable","value":0.64},{"kind":"reduction","value":0.75}]` |
| `target.monsterId` / `level` | ✓ | 敌人实例与等级（`monster_config.json` 的键） |
| `target.hardLevelGroup` | | 关卡难度组，默认 1 |
| `target.attributes` | | 同上（常用：`HEALTH` 调大，保证一击打不死） |
| `crit` | | `true` = 这一发必暴击、`false` = 必不暴击、不写 = 交给骰子。**游戏给的数已经含它自己的暴击结论，所以这里必须对齐** |
| `instance` | | 报哪一段（0 基）。1002 的战技分 **4 段** ⇒ 0–3 |
| `instances` | | 期望段数，不符会报一条差异 |
| `tolerance` | | 相对容差，默认 `1e-6` |
| `expect` | | zone 名 → 游戏的值。**留空则只打印引擎自己的数**（就是给你抄格式用的） |

### 一次真实捕获的复算（`scenarios/capture-858483.json`）

拿一份游戏伤害事件（`damage` + `zones`）做了端到端核对，结论：**公式逐项对上，12 个乘区全 0 误差**，
最终值差 `0.00016`（相对 `1.9e-10`，来自游戏侧 float32 与反解出来的 DEF）。

对齐关系（这就是本仓库的乘区命名与游戏字段的对应）：

| 游戏字段 | 引擎 | 数值（该捕获） |
|---|---|---|
| `damage_value` | `base` | 91155.27047840762 |
| `all_damage_type_added_ratio`（+元素/全属性/DoT） | `BoostArea` | 1 + 3.49 = **4.49** |
| `base_damage_multi_ratio` | `BaseMultiArea` | **1.5**（引擎存**增量** 0.5） |
| `critical_damage`（`is_critical`） | `CritArea` | 1 + 5.0913 = **6.0913** |
| `all_damage_type_taken_ratio` | `VulnerableArea`（**上限 3.5**） | 1 + 0.64 = **1.64** |
| `all_damage_reduce` | `ReductionArea`（**下限 0.01**，含韧性 0.9） | 1 − 0.75 = **0.25** |
| `resistance`（元素 0.2）− `all_damage_type_penetrate`（0.2）= `net_resistance` 0 | `ResistArea` | **1.0** |
| 未出现在 zones 里 | `DefenceArea` | 反解 **0.559910** ⇒ **DEF = 786.000056** |
| `absorb.<元素>`（全 0） | `absorb`（**减法**，链末） | 0 |

⚠ 捕获里**唯一没给的输入是目标的 DEF**，它可以从等级 80 的防御式反解：`1 − DEF/(DEF+200+10×80)` ⇒
`DEF = 786.000056`。

目标已用属性 dump 确认是 **堕神之血•亚婆离**（`Level 100`、`MaxStance 1080`、四系抗性 0.2 —— 与
`zones.resistance` 逐项一致；`BaseHP 6,389,558.46` 被 **×2** 成 `MaxHP 12,779,116.92`）。dump 里还有
**`mod:StanceBreakState`** —— 那一发打在**已击破**目标上，所以 `all_damage_reduce 0.75` 就是总减伤（没有额外的
0.9 韧性减伤），场景里的 `broken: true` 是它证明的。同一份 dump 也把 0.64 易伤指到了
`MEquip_23042_AllDamageTypeTakenRatio` + `MServant_RobinSServant_00_DamageTakenAura`，把 0.75 减伤指到了
`MonsterAllDamageReduce`。

### 第二份捕获（`scenarios/capture-876170.json`）：三点各自验证一个乘区

| 与第一份的差别 | 它证明了什么 |
|---|---|
| `attack_formula_type: 3`（**ByMaxHP**）＋ `damage_percentage 3.6` | 基数层是**生命上限 × 伤害百分比**（6576.99 × 3.6 = 23677.17），引擎侧由 `attacker.base` 承载 ✓ |
| `all_damage_type_added_ratio 2.13` **与** `added_ratio.ice 0.3888` 分开给 | 两者进**同一个加性桶** ⇒ 增伤区 3.5188 ✓（引擎的 `ALL_DAMAGE_TYPE_BOOST` ＋ `ICE_DAMAGE_BOOST` ✓） |
| `all_damage_reduce 0.1` 而目标 **CurrentStance 480/480（未击破）** | 那个 0.1 就是**韧性 10% 减伤** ⇒ 引擎自己的 `TOUGHNESS_DAMAGE_REDUCTION` 0.9 已经复现 ✓（不必再加减伤 buff）—— 与第一份「已击破 ⇒ 0.75 全来自 `MonsterAllDamageReduce`」互为印证 ✓ |
| **目标 DEF 由游戏给出 = 1150** | 这是**唯一**一份 DEF 是游戏自己说出来的捕获 ⇒ 防御区 0.4651163 与引擎逐位吻合 ✓（也反过来说明第一份反解出的 786 是**关卡值**，不是表值） |

两份的结果：**12 个乘区全部 0 误差**，最终值差 2.5e-4（相对 3e-10，float32 噪声）。

⚠ 与第一份相同的两点仍成立：易伤 1.64 来自 `MServant_RobinSServant_00_DamageTakenAura` ＋ 光锥 23042；`penetrate 0.2` 抵消元素抗 0.2 ⇒ `net_resistance 0` ⇒ 抗性区 1.0 ✓。

⚠ **DEF 仍待补**：dump 只列了 modifier 的**名字**、没有 `Defence` 数值，而本仓库的 `monster_config.json` 里没有这位
boss（Lv100 + 模板 210 会给出 1200，与反解的 786 不符 ⇒ 存在 DEF 侧修正，或它的模板 DEF 更低）。所以它是
`target.attributes.DEFENCE` 的手工输入，等拿到 DEF 面板再换成数据。

### 整场捕获的审计（`veritas_capture_2026-10-18T07-00-18-079Z.json`）

一份整场战斗的捕获（4 名角色 + 11 只敌人 + **260 条伤害事件**）被逐条拿去对公式。结论：

| 发现 | 数值 |
|---|---|
| **真实伤害**（`type: "True"`）完全不进乘区 | 130 条事件里 `damage == damage_value` **全部逐位相等** ✓（引擎的 `DamageType.TRUE` 同语义 ✓） |
| 事件按伤害类型 | `Servant` / `True` / `Additional` / `Ultimate` / `Basic` / `Elation` 六类都在，说明这份捕获覆盖了多段、追加、欢愉 |
| 事件按公式来源 | `ByAttack`(1) / `ByMaxHP`(3) / `ByElationDamage`(9) 三种 |
| 事件按元素 | ice / physical / imaginary |
| 有完整基数的事件（`damage_value` 已给且 `damage_percentage=0`，非真实伤害） | 51 条；**用链条反解出的 DEF 是 1150 的整齐比例**：`690`(×0.6) / `718.75`(×0.625) / `759`(×0.66) / `805`(×0.7) / `816.5`(×0.71) / `839.5`(×0.73) / `1150` |
| ⇒ 这说明 | 骨架公式（增伤→暴击→防御→抗性→减伤→易伤）是**对的**；同一目标在不同时刻的"有效 DEF"不同，是因为**减防 debuff** 在生效/过期（捕获里没有记录这项状态） |
| 只有 4 条事件的隐含 DEF 正好 **1150**（没有减防） | 只有这几条能**只凭捕获就精确复算** ⇒ 其中第 13 条（下标 12）已做成 `scenarios/capture-174678.json`，逐区 0 误差 ✓ |

⚠ **要让整场 260 条都能精确复算，捕获还缺三样**：① **每条事件的减防/有效 DEF**（或那份 debuff 列表）；
② 忆灵/随从的**面板**（`ByMaxHP` 用的是**忆灵自己**的生命上限，不是角色的 `MaxHP`，捕获的 `allies` 里没有它）；
③ 64 条事件的目标不在 `enemies` 表里（`target_max_hp` 对不上任何一只）⇒ 目标面板缺失。

### 第二份整场捕获（`veritas_capture_2026-10-18T07-20-25-672Z.json`）：它补上了缺的两样

同一场战斗的更早一份捕获缺"每条事件的有效 DEF"与"忆灵面板"，这一份**都补上了**：

| 新增 | 内容 |
|---|---|
| `zones.target_defence` / `target_base_defence` / `target_defence_added_ratio` / `target_level` | **逐条事件的目标防御与等级**（不必再反解） |
| `allies` 的 `kind: "Avatar"` / `kind: "Servant"` | **忆灵也有自己的面板**（`Attack` / `MaxHP` / `CurrentHP` / `Defence`），并与主人共享 `Defence`，可据此配对 |

用它复核的结果：

| 项目 | 结果 |
|---|---|
| 真实伤害（`type: "True"`） | 76 条，`damage == damage_value` **全部逐位相等** ✓ |
| 基数已完整给出的事件（`damage_value` 且 `damage_percentage` 缺省、非真实伤害） | 27 条 |
| 其中**重建到 6e-10** 的 | 4 条（战斗开局、目标满血、`all_damage_type_taken_ratio 0.18`、DEF 1150）✓ |
| 其余 23 条 | 重建值**系统性偏低**，偏差在**每组 4 条内恒定、组间单调增长**：1.1725 → 1.1874 → 1.2263 → 1.2385 → 1.2426 → 1.2509 → 1.2679 → 1.2722 |

⚠ 那 23 条的偏差**在 `zones` 里找不到对应字段**（同组事件的 `vuln` / `crit` / `baseMul` / 增伤 / 抗性 / DEF 全相同，组内目标血量 0%–72% 变化也不影响偏差）⇒ 缺的是一个**随时间增长的伤害加成**，最可能是**贪饕/污染**的战斗侧增益（这场是 `gluttony_level: 3`，敌人带 `MCommon_Gluttony_LV3` / `MCommon_Gluttony_DirtyHp`，且 `dirty_hp_ratio 0.75` + `split_source: 上限压制` —— 正是 HSR.md §9 的那套机制）。

⇒ 要让这 23 条也精确复算，还需要：**每条事件的战斗侧加成**（贪饕/压制/其它随时间变化的增益），或至少把那个加成作为 `zones` 的一行给出来。

### 为什么需要 `attributes` 覆盖

引擎的面板 = `character_data` + 晋阶 + 行迹，**没有光锥/遗器**。原游戏的数来自某个具体面板，所以比较时必须把那个面板的
`ATTACK` / `CRIT_CHANCE` / `CRIT_ATTACK` / `<元素>_DAMAGE_BOOST` 等**绝对**写进场景，两边才是同一道题。属性名用
`AttributeType` 的枚举名（`ATTACK`、`CRIT_CHANCE`、`WIND_DAMAGE_BOOST`…）。

---

## 2. 乘区对照表（游戏术语 → 引擎 zone）

引擎按这个顺序结算，`breakdown()` 也按这个顺序打印：

| 引擎 zone | 游戏里的那一项 | 说明 |
|---|---|---|
| `base` | 技能倍率 × 攻击力（含欢愉/超击破的折叠） | 进任何乘区之前的值 |
| `BoostArea` | 增伤区 `1 + Σ增伤` | 元素增伤 / 全属性增伤 / 普攻·战技·终结技增伤 / 击破易伤… |
| `FinalBoostArea` | `1 + Level_FinalDamageAddedRatio` | 链末独立因子之一 |
| `ComboArea` | `1 + ComboDamageAddedRatio` | 同上 |
| `BaseMultiArea` | `BaseDamageMultiRatio` | 同上 |
| `MonsterTakenArea` | `1 + Monster_DamageTakenRatio` | 怪物自身承伤 |
| `VulnerableArea` | 易伤区 `1 + Σ易伤`（**上限 3.5**） | |
| `CritArea` | 暴击区 `1 + 暴伤`（暴击时） | 不暴击 = 1.0 |
| `DefenceArea` | 防御区 `攻/(攻+防×(1−穿透))` 一类 | 等级差进这里 |
| `ReductionArea` | 减伤区 `Π(1−r)`（**下限 0.01**） | 韧性减伤 0.9 也在这里 |
| `WeaknessArea` | 虚弱区 `1 − Σ`（**下限 0.2**） | |
| `ResistArea` | 抗性区 `1 − (抗性 − 穿透)` | |
| `absorb` | 元素吸收（**减法**，结算后） | 只有 >0 时出现 |
| `final` | 最终伤害 | |

⚠ `crit` 在引擎里排在 `DefenceArea` **之前**——两者是相乘的，顺序不影响值，但表格的行序就是结算序。

---

## 3. 怎么用

1. 复制一个场景文件，改 `name` / `attacker` / `target`。
2. 先**不写** `expect` 跑一次 ⇒ 打印引擎的全部 zone（格式现成）。
3. 把原游戏的数填进 `expect`（能填几项填几项；只填 `final` 也行）。
4. 再跑一次 ⇒ 逐行 `game / engine / diff / relative`，对不上的行标 `<-- differs`。

⚠ **`skillLevel` 是基础等级**：角色文件自己的 `level_convention` 会在 `BATTLE_START` 把它抬高（1002 是 `+9`）。
所以想比"Lv10 那一行"要写 `skillLevel: 1`，而 `inputs:` 那行打印的 `Lv…` 才是**实际施放等级**（它也是反解 `base` 用的行）。

⚠ **精度**：`expect` 请填原游戏的**原始精度**。如果游戏只给了 4–6 位小数，`tolerance` 要跟着放到同一量级（默认 `1e-6`，是按“两边都是全精度”设的），否则会看到 `0.0001%` 这种假差异。

**对不上时先看的几件事**：`inputs:` 那行是不是你想要的题（ATK / 倍率 / 敌人 DEF / 暴击结论）；
`settled:` 的段数对不对（多段技能逐段结算，`instance` 选的是其中一段）；
以及 `expect` 的 key 是否拼错（引擎没有的 zone 会直接报 `MISSING` 并列出它所有的 zone 名）。
