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

### 第三份整场捕获（`veritas_capture_2026-10-18T07-33-33-078Z.json`）：基数来源被点名了

这一份又补了一批字段，最重要的是**每条事件的攻方面板**与**链末附加乘区**：

| 新增字段 | 意义 |
|---|---|
| `attacker_attack` / `attacker_base_attack` / `attacker_defence` / `attacker_max_hp` / `attacker_base_hp` / `attacker_hp_added_ratio` | **基数层的数据来源逐条给出** —— 不必再从总量反解 |
| `target_defence` / `target_level` / `target_defence_added_ratio` | 目标侧同样逐条给出 |
| `extra_damage_ratio` / `extra_ratio_valid` / `extra_front_back_base` / `extra_luck_chance` / `extra_luck_damage` / `extra_all_damage_type_added_ratio[]` / `extra_attack_type_added_ratio{dot,elation,element_damage,insert,normal,skill,ultra}` / `extra_accumulated` | HSR.md §10.3 的**链末独立乘区**（前后排基准 × 幸运 × 攻击类型附加…） |
| `stage_final_damage_added_ratio` | **关卡侧**的最终伤害加成 |

**`AttackFormulaType` 到"用哪个面板数"的映射，现在是被数据验证过的**（HSR.md §10.4 的表）：

| 公式 | 基数 = | 本捕获里的证据 |
|---|---|---|
| 1 `ByAttack` | `attacker_attack` | 用它能精确复现的事件都能对上 ✓ |
| 3 `ByMaxHP` | `attacker_max_hp` | 例：忆灵 `attacker_max_hp = 3560.0287`（正是上一份捕获反解出的 3560.03 ✓✓） |

复算结果：**97 条事件里 64 条逐位复现**（含 48 条真实伤害）；剩下 **33 条**系统性偏低，偏差**每回合递增、同一回合内恒定**：

```
回合 1   miss-1 = 0.1368
回合 2   miss-1 = 0.1403
回合 3   miss-1 = 0.1473
回合 4   miss-1 = 0.1725
回合 5   miss-1 = 0.1761
回合 6   miss-1 = 0.1874   …
```

⚠ 同一回合里目标血量从 72% 掉到 31% 而偏差**不动** ⇒ 它不是"按目标状态"的因子，而是**随时间/回合累积的伤害加成**；而这份捕获里 `extra_accumulated` 与 `stage_final_damage_added_ratio` 在这些事件上都是 **0** ⇒ 它**仍未被命名**。这场是 `gluttony_level: 3`（`MCommon_Gluttony_LV3` / `MCommon_Gluttony_DirtyHp`、`dirty_hp_ratio` + `split_source: 上限压制`），最可能就是**贪饕/压制的战斗侧累积加成**。

### 第四份整场捕获（`veritas_capture_2026-10-18T07-41-13-305Z.json`，**无贪饕**）：剩下的东西不是贪饕

特意给的这一份把"贪饕"变量去掉了。结果：

| 项目 | 数值 |
|---|---|
| 事件数 | 159（`True` 78 / `Servant` 58 / `Additional` 13 / `Ultimate` 4 / `Basic` 4 / **`Break` 2**） |
| 逐位复现 | 94 条（含全部 78 条真实伤害） |
| 仍未对上 | 63 条，偏差仍是 `−14.7% ~ −21.4%` 这一档 |

⇒ **偏差与贪饕无关**：去掉贪饕后同一个缺口原封不动。而且四份捕获里 `extra_damage_ratio` **恒为 1**、`extra_ratio_valid` **从来没有 true** —— 也就是说那个字段是**占位值、并没有真的读到**。

#### 关键：缺的是"每条事件一个数"，而且它跟击破/普通伤害**共用**

把偏差按事件反解出来（`actual / 复算值`），同一时刻的**普通伤害与击破伤害拿到的是同一个数**：

```
#50  Break  (击破)  implied extra = 1.176149
附近普通伤害事件      implied extra = 1.1761  …
#139 Break  (击破)  implied extra = 1.272189
附近普通伤害事件      implied extra = 1.2722  …
```

⇒ 它是**战斗侧的一个全局乘数**（按回合/时间增长，同一时刻对所有命中相同），正是 HSR.md §10.3 链末那个**独立乘区** `_GetExtraDamageRatio`（前后排基准 × 幸运 × 攻击类型附加…）；这场是 `MMonster_W5_Vtuber_00_InField`（直播/热度）那套敌人，也可能是关卡自身的成长加成。

#### 顺带把 `AttackFormulaType = 7`（`ByBreakDamage`）钉住了

两条 `Break` 事件的基数可以**精确反解**为：

```
base = 3767.553 × break_damage_percentage       # 3767.553 = 等级 80 的击破基数
```

| 事件 | `break_damage_percentage` | base | 反解出的链末乘数 |
|---|---|---|---|
| #50 | 2.5 | 9418.8825 | 1.176149 |
| #139 | 5.5 | 20721.5415 | 1.272189 |

⚠ 而且**击破路径不吃元素/类型增伤**：若把 `all_damage_type_added_ratio`（1.65 / 2.13）也乘进去，反解出的链末乘数会变成 0.387 / 0.362 —— 不可能，所以击破伤害的乘区里**没有那一段**（这与 HSR.md §10.3 里"击破时改用 `BreakDamageAddedRatio`"的写法一致）。

### 第五份整场捕获（`veritas_capture_2026-10-18T08-00-26-209Z.json`）：基数层被"官方字段"证实了

这一份新加了 8 个字段，其中一个是决定性的：

| 字段 | 含义 |
|---|---|
| **`raw_damage_before_modifiers`** | **进乘区之前的原始伤害**（即基数层） |
| `damage_result` / `target_damage_hp` / `default_multiplier` | 最终伤害 / 分摊后伤害 / 默认倍率 |
| `attacker_fatigue_ratio` / `defender_minimum_fatigue_ratio` / `attacker_property_enum_boundary` / `sp_scaled_damage_value` | 疲劳与枚举边界（这些事件上都是 0） |

**验证**：对 116 条事件里**有面板数**的 115 条，逐条检查

```
raw_damage_before_modifiers  ==  stat(由 attack_formula_type 选) × damage_percentage + damage_value
```

**115 / 115 全部逐位相等，0 个不符** ✓
⇒ `AttackFormulaType → 用哪个面板数` 的映射（1→`attacker_attack`、3→`attacker_max_hp`、7→击破基数的特例…）
**不再是"我用总量反推出来的"，而是游戏自己给出的字段确认的** ✓✓

复算结果：**116 条里 73 条逐位复现**；剩下 42 条（`−19.8% ~ −21.4%`）—— 既然基数层已被证实与游戏一致，
缺口就**完全在基数之后**：仍然只有那个**链末全局乘数**（`extra_damage_ratio` 依旧是占位 1、`extra_ratio_valid` 依旧全 false，
`stage_final_damage_added_ratio` 全 0）✓

### 第六份整场捕获（`veritas_capture_2026-10-18T08-13-43-350Z.json`）：缺口不是链末乘区，是**减防**

捕获工具改成"从攻击方活属性重建链末乘区"之后，这一份里 `extra_ratio_source = 1`（确实走了重建路径），
但**重建出来的所有输入都是 0**：`extra_front_back_base 0`、五个 `extra_all_damage_type_added_ratio` 全 0、
七个攻击类型槽全 0、`extra_luck_damage 0` ⇒ `extra_damage_ratio = 0`、`extra_ratio_valid = false`。
而缺口**一点没变**（仍是 `−19.8% ~ −21.4%`）⇒ **链末乘区不是这个缺口**。

把每条非真实伤害事件的"有效 DEF"反解出来（用链条倒推），它落在**干净的 0.5% 台阶**上：

| 有效 DEF / 面板 DEF | 减防 | 事件数 |
|---|---|---|
| 1.00000 | −0.0% | 16 |
| 0.77500 | −22.5% | 2 |
| 0.77000 | −23.0% | 2 |
| 0.76000 | −24.0% | 1 |
| 0.72500 | −27.5% | 4 |
| 0.72000 | −28.0% | 2 |
| 0.71000 | −29.0% | 2 |
| 0.70500 | −29.5% | 5 |
| 0.70000 | −30.0% | 1 |
| 0.68500 | −31.5% | 19 |
| 0.66000 | −34.0% | 2 |
| 0.64500 | −35.5% | 2 |
| 0.64000 | −36.0% | 1 |
| 0.62000 | −38.0% | 2 |
| 0.61500 | −38.5% | 4 |
| 0.60000 | −40.0% | 4 |

⚠ 这一节改过两次，把两次都留着（错误本身是证据）：

1. **第一版**：反解出的有效 DEF 落在干净台阶上 ⇒ 断言"减防 debuff"。
2. **第二版**：发现同一个乘数**同时覆盖多个目标**（`x1.1368` 打在 2,269,526 与 2,496,478 两只身上；
   `x1.1874` 打在三只身上）⇒ 认为"一只敌人的减防不可能出现在另一只身上"，于是**撤回了第一版**，
   改判为"全局乘数"。✗ **这次撤回本身过头了**：**AoE 减防**（对敌方全体减防）完全可以让多只目标同时拥有同一个有效 DEF。
3. **现在的判断**（第三次，基于单条事件的精确算术）：

用户给的这条事件（目标 `27,234,309`、`damage_value 47177.63774068`、全部 zone 都正常）逐项乘下来：

```
4.65 × 1.5 × 5.9263024 × 1.64 × 0.9 × 1.2 × 1000/2150  =  34.0449…
47177.63774068 × 34.0449…                              =  1,606,546.781552
游戏值                                                  =  1,901,500.431630
比值                                                    =  1.1835948094
```

把这 1.1836 反解成防御：**游戏用的防御区是 0.5505092392 ⇒ DEF = 816.4999 = 1150 × 0.710000**（刚好 **−29.0%**）。
而 0.71000 正是前面那张表里的一档（`1150 × 0.71`）⇒ **数值上"减防"的解释比"全局乘数"干净得多**：
16 档残差全是 0.5% 整数台阶，而"全局乘数"会给出任意小数。

⇒ 当前最可信的结论：**缺的输入是目标的有效 DEF**（含 AoE 减防），不是链末乘区，也不是公式错误；
判定它到底是减防还是全局加成的唯一实验：**dump 目标在那一下时的活 `Defence`**（或减防 debuff 列表）——
若它是 816.5，就是减防；若仍是 1150，那就是全局加成。

### 第七份整场捕获（`veritas_capture_2026-10-18T08-37-47-677Z.json`）：新字段 `target_defence_effective` 读错了方向

工具加了 `target_defence_effective` / `target_defence_lp_base` / `_lp_override` / `_lp_is_override` 与
`attacker_attack_lp` / `attacker_hp_lp`（`SetupDamageData_PropertyPart` 的 0x5B0 / 0x630 / 0x338）。
但这些值**和攻击方自己的防御一模一样**：

| 事件 | `target_defence_effective` | `attacker_defence` | `target_defence_lp_base` |
|---|---|---|---|
| #0 | 1201.0836764348396 | 1201.0836764348205 | 979.0200001229532（= 攻击方 `BaseDefence`） |
| #52 | 1375.416964498682 | 1375.4169644985814 | 1159.830000066897（= 攻击方 `BaseDefence`） |

⇒ 0x5B0 读到的是**攻击方**的 `Defence`（`LinearPropOverride`），不是**受击方**的。用它复算当然对不上
（偏差 −22.8% ~ −23.5%）。

**独立反解"该用的 DEF"**（把每条事件的防御区倒推出来）：34 条非真实伤害事件**全部**落在

```
DEF = 1150 × { 1.00000, 0.77500, 0.77000, 0.76000, 0.72500, 0.71000, 0.70500, 0.68500 }
```

即 **16 条 = 1150（精确复现 <1e-9）**、18 条 = 干净台阶（−22.5% ~ −31.5%）—— 与上一份捕获的 16 档台阶**同一套值**（同一场战斗）。

⇒ 结论进一步收紧：**除 DEF 之外没有任何缺口**（34/34 事件都由 DEF 一个量解释），需要的是
受击方那一路的 LP（同偏移、换 defende 侧），或者直接给 `target_defence_effective` 填**受击方**的值。

### 第八份整场捕获（`veritas_capture_2026-10-18T08-54-18-537Z.json`）：**82 / 82 条事件逐位复现**

修好"读受击方 DEF"之后，这份捕获第一次做到**全部事件逐位相等**：

| 项目 | 结果 |
|---|---|
| 事件数 | 82（`True` 40 / `Servant` 30 / `Additional` 7 / `Ultimate` 3 / `Break` 1 / `Basic` 1） |
| 公式 | 1（`ByAttack`）/ 3（`ByMaxHP`）/ 7（`ByBreakDamage`） |
| 复算 | **82 条全部 <1e-6 相对误差，0 条不符**（含 40 条真实伤害、1 条击破） |

`target_defence_effective` 现在报的是**受击方**的有效 DEF（16 条 = 1150 原值，其余落在
`1150 × {0.65, 0.665, 0.675, 0.68, 0.705, 0.71, 0.725, 0.76, 0.77, 0.775}` 这些整齐台阶上），
`target_defence_lp_base` 也变成了 1150（不再是攻击方的 `BaseDefence`）。

⇒ 整条链（基数 → 增伤 → 暴击 → 防御 → 抗性 → 减伤 → 易伤 → 链末）在**一场真实战斗的 82 次命中**上验证完毕；
缺口从头到尾只有"有效 DEF"这一个输入。

#### 两条新的守卫（`DEF-ignore` 的一对）

`capture-726168.json` / `capture-464823.json` 取自这份捕获的同一目标、同一场战斗，减防分别是 **29%** 与 **27.5%**
（`target_defence_effective` = 1150 × 0.710 / 0.725），用 `attacker.attributes.DEFENCE_IGNORE` 承载
—— 检验的是引擎的减防路径（`DefenceArea` 里 `effDef = def × (1 − ignore)`），而不是把 DEF 改小来凑数。
两条方向相反，一侧改错就会一条过一条不过。

⚠ 夹具上的两处说明（不是游戏数值）：1002 的普攻是**两段**（`skill_effects.json` 的 `0.45 / 0.55`），所以基数与最终值都乘 0.45；
`WIND_DAMAGE_BOOST` 要显式置 0（抵掉角色自带的 0.224），否则增伤区会高 0.224。

### `compare-capture <capture.json>`：整场捕获直接回放

```bash
gradlew run --args="compare-capture C:\path\to\veritas_capture_*.json"      # 全部事件
gradlew run --args="compare-capture ...json --limit 5"                        # 只看前几条
```

它把捕获里的每一条伤害事件**逐条**变成一个场景，交给同一套测量路径（`DamageComparator.measure`），
比对 `base` 与 `final`，逐行打印 `# / type / element / game / engine / relative`，末尾给 `reproduced: n/m`，
退出码 0/1。

事件到场景的换算（全部取自捕获，不来自引擎）：

| 输入 | 取自 |
|---|---|
| 元素 | `damage_type` 位掩码（游戏自己的 `DamageType`：1 物理 / 2 火 / 4 冰 / 8 雷 / 16 风 / 32 量子 / 64 虚数） |
| 基数 | `raw_damage_before_modifiers` |
| 增伤 | `added_ratio[元素]` + `all_damage_type_added_ratio` + `all_damage_added_ratio` + `dot_*` |
| 乘算倍率 / 暴击 / 易伤 / 减伤 | `base_damage_multi_ratio` / `critical_damage` / `all_damage_type_taken_ratio` / `all_damage_reduce` |
| **无视防御** | `1 − target_defence_effective / target_defence` |
| 穿透 | 解成 `resistance[元素] − net_resistance`，这样引擎的 `1 − (res − pen)` 就是捕获的 `1 − net` |
| 韧性减伤 | 捕获把韧性 10% 折进了 `all_damage_reduce`；回放把目标标为已击破，再把该值作为减伤 buff 给进去，乘积与游戏一致 |

**结果**：`src/test/resources/captures/veritas-085418.json`（82 条事件，裁剪到只留驱动读的字段）
→ **81 / 81 可回放事件逐位复现，1 条击破事件按原因跳过**，已作为 `CaptureComparatorTest` 进判据。

⚠ 跳过的原因：击破伤害的乘区**不吃**增伤桶，而回放装的是一发普通命中（会吃）—— 击破那条另行验证
（`3767.553 × 击破倍率 × (1 + break_damage_added_ratio)`，见上文的整场审计）。

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
