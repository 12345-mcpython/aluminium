# 触发器（trigger）JSON 格式

> 本文写的是**规则文件怎么写**。引擎的架构与取舍在 `AGENT.md`，游戏侧的资料在 `HSR.md`。
>
> ⚠ **本文的每张表都是从代码里读出来的，不是凭印象写的** ✓ —— 字段名来自 `beans/TriggerSpec.java` 与 `beans/EffectSpec.java` 的
> `@SerializedName`；运算符、事件、操作、选择器、`scale` 来自引擎自己的闭集与**语料里的实际用法**（**94** 个角色文件；重新数：`ls src/main/resources/characters/*.json | wc -l`）。
> 语料目前用到 **50** 个 op、**211** 条不同的条件串 ✓。凡本文没列的名字，引擎都会在**加载期**报错并列出合法值 ✓，所以不会出现"写错了但静默无效"的情况 ✓。

---

## 1. 规则写在哪里

| 位置 | 内容 |
|---|---|
| `src/main/resources/characters/<cid>.json` | 一个角色（或光锥）的**全部规则** |
| 顶层 `rules` | 规则的数组；有些文件顶层直接就是数组 |

一条规则 = **一个事件** + **一组条件** + **一串效果**。

```json
{
  "on": "BATTLE_START",
  "id": "technique_incomplete_combustion",
  "when": ["self has_state 秘技"],
  "do": [
    { "op": "MODIFY_DAMAGE_TAKEN", "percent": 0.1, "scope_element": "Fire",
      "turns": 2, "target": "all_enemies" }
  ],
  "source": "1003 姬子 秘技 不完全燃烧 (100307, Maze/Impair): 「…原文…」 param = [1, 0.1, 2, 15]",
  "note": "「…逐句…」—— ① … ② …；⚠ **登记**：…"
}
```

### ⭐ `source` 与 `note` 不是装饰

- **`source`** 写**原文**与**参数表**，并标明 `#N` 对应参数表的第几个 ✓
- **`note`** 写**逐句映射**：句子的每个子句 → 哪个 op / 哪个字段 ✓；**写不了的部分必须登记** ✗

⚠ 这是本项目最硬的一条约定 ✓：**数据文件必须能回答"这个数字从哪来"** ✓
⚠ 而**过时的 `note` 比缺失的 `note` 更坏** ✗ —— 缺失让人去查 ✓，写错让人去**造** ✗（本项目真的发生过一次，见 `ROADMAP`）

---

## 2. 规则级字段（`TriggerSpec`）

| 字段 | 类型 | 作用 |
|---|---|---|
| **`on`** | string | **触发事件**（见 §4）。必填 |
| **`on_any`** | string[] | 多个事件共用一个 `do` ✓ —— 与 `on` 二选一 |
| **`when`** | string[] | **条件**（见 §3）。全部满足才触发；省略 = 无条件 |
| **`id`** | string | 规则名。⚠ **`MODIFY_RULE` / `RESET_TRIGGER_LIMIT` 靠它点名** ⇒ 被点名的规则必须有 `id` |
| **`do`** | object[] | **效果**（见 §5）。必填 |
| **`source`** / **`note`** | string | 见 §1 |

### 触发上限（"每回合只能触发 N 次"这类句子）

| 字段 | 含义 |
|---|---|
| **`per_turn`** | ⚠ **按【规则持有者自己的回合】计数** ✓ —— 不是"每个回合" ✗ |
| **`per_subject`** | 计数**按谁算**：`self`（默认）/ `actor` / `target` |
| **`cooldown`** | 触发后冷却 N 回合 |
| **`once_per_battle`** | 一场战斗只触发一次 |
| **`once_per_wave`** | 每波一次 |
| **`once_per_attack`** | 同一次攻击内只触发一次 |
| **`per_attack`** | 同一次攻击内最多 N 次 |
| **`chance`** | 触发概率（不是"效果命中"，是**这条规则本身**的掷骰） |
| **`chance_decay`** | 每次触发后概率的衰减 |
| **`chance_rolls`** | 一次开火**独立掷几次** `chance`（默认 1）—— 「额外增加 2 次…判定」= `2`。⚠ 不是效果的 `times`：那个重复的是**已判定的结算**（一次判定多个实例），这个重复的是**判定** |

### 星魂 / 变体

| 字段 | 含义 |
|---|---|
| **`min_eidolon`** | 星魂 ≥ N 才生效 |
| **`max_eidolon`** | 星魂 ≤ N 才生效 |
| **`one_of`** | 整组里只取一条（同组规则按顺序取第一条可用） |
| **`one_of_no_repeat`** | 同上，但**不重复**取已取过的 |
| **`defer_until_wave`** | 延到下一波才结算 |

---

## 3. 条件（`when` / `target_when`）

### 3.1 语法

```
<主语> <谓词|运算符> [<值>]
```

⚠ **没有括号、没有 `and` / `or`** ✗ —— **数组里每一项都必须是真** ✓（就是 AND）✓

```
["actor == self", "self_stacks:白花之刻 >= 2"]      ← 两条都要成立
```

### 3.2 主语

| 主语 | 指的是 |
|---|---|
| **`self`** | **规则持有者**（写这条规则的角色） |
| **`actor`** | **本次事件的发起者**（可能是别人） |
| **`target`** | **本次事件的目标** |
| `from_skill` / `from_skill_id` / `from_category` | 不是主语，是**关于"这次施放"**的谓词（见 3.4） |

### 3.3 运算符与谓词

**比较**（对数值）：`==` `!=` `>` `>=` `<` `<=`

**状态**：

| 写法 | 含义 |
|---|---|
| `X has_state <名字>` | 是否持有某状态（`冻结` / `触电` / `结界` / `秘技` …） |
| `X has_shield` | 是否有护盾 |
| `X has_path <命途>` | 命途（`欢愉` / `智识` …） |
| `X has_skill <技能槽>` | 是否拥有某槽位的技能 |
| `X has_weakness` | 是否有弱点击破 |
| `X is_ally` / `X is_other_ally` | 是否我方 / 是否**除我以外**的我方 |
| `X is_summon` | 是否是召唤物 |
| `state_ended` | 状态结束 |

### 3.4 "这一次施放"的谓词（写在 `actor` 上最常用）

| 写法 | 含义 |
|---|---|
| `from_skill ULTRA` | 这次来自**哪个技能槽**（`COMMON` / `BPSKILL` / `ULTRA` / `TALENT`） |
| `from_skill_id <id>` | 这次来自**哪个具体技能**（按技能 id） |
| `from_category <类别>` | 按技能类别 |
| `damage_is_attack` | 这一发**算不算一次攻击** |
| `damage_is_additional` | 这一发是不是**追加伤害** |
| `damage_has_no_cast` | 这一发**没有施放来源** |
| `damage_element_is_self` | 这一发的属性 = 我自己的属性 |

⚠ **`from_skill` / `from_skill_id` 只在那几个"携带施放"的事件上为真** ✓（源码 `CAST_CARRYING_EVENTS` ✓）：`DEALING_DAMAGE` / `BREAK` / `KILL` / `ALLY_ATTACK` / `CAST_SETUP` / `DAMAGE_SETTLED` / `ATTACK_FINISHED` / **`CRIT_DEALT`** ✓。
挂到别的事件上会在**加载期**报错 ✓ —— 故意的 ✓：否则那条规则永不匹配，而且没有症状 ✗。
⚠ `CRIT_DEALT` 是最后加进来的一个 ✓（读者：1002 丹恒战技「战技造成的伤害触发暴击时」✓）：它的上下文带着**这一发的伤害实例** ✓，实例知道自己来自哪个技能 ✓，所以不必另造"这一发暴击了"的谓词 ✓。

### 3.5 数值变量（**闭集**，`TriggerConditionParser.NUMERIC_VARIABLES`）

| 变量 | 读的是 |
|---|---|
| `hp_percent` | **我自己**的生命百分比 |
| `target_hp_percent` / `actor_hp_percent` | 目标 / 发起者的生命百分比 |
| **`target_hp_percent_before`** | ⭐ **掉血【之前】的百分比** —— 「**降到** 50% 或以下」是**跨越**，不是"正处于低位" ✓ |
| `self_energy_percent` / `self_max_energy` | 我的能量百分比 / 上限 |
| `hit_count` / `weakness_hit_count` / `target_weakness_count` | 命中数 / 弱点命中数 / 目标弱点数 |
| `target_debuff_count` / `target_dot_count` | 目标身上的负面数 / 持续伤害数 |
| `self_summon_count` / `target_summon_count` | 召唤物数量 |
| `enemy_count` / `ally_count` | 场上敌我数量 |
| `skill_point_max` | 战技点上限 |
| `eidolon` | 星魂等级 |
| `from_skill_id` / `damage_skill_key` | 技能 id / key |
| `counted_laughs` | 【笑点】计数 |

⚠ **`target_hp_percent_before` 有一个配套的坑** ✓：它只在**会掉血的事件**（如 `HP_LOST`）上才有意义 ✓，
因为它是 `(当前 + 本次损失) / 上限` 算出来的 ✓ —— 在别的事件上它是 `NaN` ✗。

**带参数的变量**（写法是 `前缀:参数`）：

| 前缀 | 例 |
|---|---|
| `self_attr:` | `self_attr:ATTACK >= 1000` |
| `self_stacks:` / `actor_stacks:` / `target_stacks:` | `self_stacks:白花之刻 >= 2` |
| `self_resource:` / `summon_resource:` | `self_resource:充能 >= 3` |
| `allies_with_path:` | 按命途数我方 |
| `enemy_state_count:<状态>` | 场上**活着**的敌人在该状态的有几个（「场上有敌方目标处于X」= `>= 1`） |

⚠ **拼错的名字会在加载期报错** ✓ —— 这条是**故意的** ✗：曾经的行为是"不认识的变量一律当假" ✓，
⇒ **一条永不触发的规则看起来和"条件不满足"一模一样** ✗（没有症状的错误 ✓）。

---

## 4. 事件（`on`，共 39 个）

| 分类 | 事件 |
|---|---|
| **战斗流程** | `BATTLE_READY` `BATTLE_START` `WAVE_START` `TURN_START` `TURN_END` `EXTRA_TURN_STARTED` `EXTRA_TURN_ENDED` `COUNTDOWN_TURN` |
| **施放** | `CAST_SETUP` `SKILL_CAST` `BASIC_ATTACK` `ULT_CAST` `INSERTED_CAST_END` `ATTACK_FINISHED` |
| **攻击与受击** | `ALLY_ATTACK` `ENEMY_ATTACK` `SUMMON_ATTACK` `TAKING_HIT` `DEALING_DAMAGE` `DAMAGE_SETTLED` `FOLLOW_UP` `CRIT_DEALT` |
| **伤害与生命** | `HP_LOST` `HP_CONSUMED` `LETHAL_DAMAGE` `HEALED` `SHIELD_GRANTED` `DEATH` `KILL` |
| **击破与控制** | `BREAK` `WEAKNESS_ADDED` `DEBUFF_APPLIED` `STATE_ENDED` |
| **资源** | `ENERGY_GAINED` `SKILL_POINT_GAINED` `SKILL_POINT_SPENT` `SKILL_POINT_OVERFLOWED` `RESOURCE_CHANGED` |
| **召唤** | `SUMMONED` |

⚠ **`BATTLE_START` 是最常用的一类** ✓ —— 秘技（`self has_state 秘技`）、星魂等级声明、开战加攻都属于它 ✓
⚠ 而它的效果**在任何回合开始之前结算** ✓ —— 前端要记得把这段写进日志 ✓（本项目为此专门修过一次 ✓）

---

## 5. 效果（`do` 里的一项）

### 5.1 引擎能执行的操作（**51 个**，`TriggerOperator` 里 `wired = true`）

```
ABSORB_DAMAGE          ADD_DAMAGE             ADD_ELEMENTAL_WEAKNESS ADD_STACK
ADVANCE                APPLY_BUFF             APPLY_CONTROL          APPLY_DOT
APPLY_REGEN            BOOST_DAMAGE           BOOST_TOUGHNESS        CAST_SKILL
COMMAND_SUMMON         CONSUME_HP             DAMAGE                 DELEGATE_DAMAGE
DISPEL                 EXTEND_BUFF            EXTRA_TURN             GAIN_ENERGY
GAIN_RESOURCE          GAIN_SKILL_POINT       HEAL                   INSERT_ACTION
MODIFY_ATTR            MODIFY_DAMAGE_TAKEN    MODIFY_RULE            RAISE_ENERGY_TO
RAISE_RESOURCE_CAP     RAISE_SKILL_HITS       RAISE_SKILL_LEVEL      RAISE_SKILL_POINT_CAP
REGISTER_HEAL_BONUS    REMOVE_BUFF            REMOVE_RESOURCE        REMOVE_STACK
REMOVE_STATE           REPLACE_SKILL          RESET_RESOURCE_CAP     RESET_RULE_CHANCE
RESET_TRIGGER_LIMIT    RESIST_DEBUFF          SHIELD                 SPEND_RESOURCE
SPEND_STEPS            START_COUNTDOWN        SUMMON                 SUMMON_SERVANT
SUPER_BREAK            TAUNT                  TICK_DOT
```

⚠ **只有 `REDUCE_TOUGHNESS` 是"声明了但还没实现"** ✓ —— 写它会**在加载期**报错并说明原因 ✓

⚠ **常用操作的参数以源码的 op 表为准** ✓ —— 那张表在
`models/trigger/TriggerInterpreter` 的类注释里，**每个 op 一行，列出它的参数与状态** ✓。
本文不复制它 ✗：**复制一份就会和源表分叉** ✓（与 §7 同一条理由）。

### 5.2 目标（`target`）

| 分类 | 选择器 |
|---|---|
| **自己人** | `self` `target` `attacker` `actor` `all_allies` `party` `other_allies` `next_ally` `party_first` `lowest_hp_ally` `random_ally_below_half_energy` |
| **敌人** | `all_enemies` `random_enemy` `random_hit_enemy` `highest_hp_enemy` `target_else_random_enemy` |
| **召唤物** | `summon` `all_summons` `target_and_summon` |
| **按标记找人** | `holder_of:<状态>` |
| **按命途 / id** | `allies_of_path:<命途>` `allies_of_path_except:<命途>:<名单>` `ally_cid:<id>` |
| **按波次** | `wave_monster` `wave_monsters` |
| **特殊** | `all_allies_lethally_hit_this_action` `highest_hp_attack_hit` |

⚠ **选择器是闭集** ✓ —— 拼错的 `target` 会被拒绝 ✓
⇒ 这条是**故意的** ✗：曾经的行为是"不认识的 `target` 一律当成 `self`" ✓
⇒ **拼错的 `target` 和 `"self"` 完全一样** ✗ —— **一个没有症状的错误** ✓

### 5.3 数值来源（`scale` + `percent` / `amount`）

```
倍率 = percent × <scale 读出来的数> + amount
```

| `scale` | 读的是 |
|---|---|
| `self_attr:<属性>` | 持有者的属性（`self_attr:ATTACK` 最常见） |
| 裸属性名 | 等价于 `self_attr:<属性>`（`ATTACK` / `DEFENCE` 也合法） |
| `owner_attack` / `owner_def` / `owner_max_hp` | 持有者的攻击 / 防御 / 生命上限 |
| `target_max_hp` | **目标**的生命上限 |
| `actor_max_hp` / `actor_attr:<属性>` | 事件发起者的 |
| `self_stacks:<名字>` / `target_stacks:<名字>` | 层数 |
| `party_resource:<名字>` | **全队计数器** |
| `self_max_energy` / `owner_max_energy` | 能量上限 |
| `elation_base` | 欢愉体系的基础值 |
| `original_damage` | **触发这一发本身的伤害**（追加伤害常用） |
| `settled_damage_this_event` | **本次事件已结算的总伤害**（"本次总伤害的 20%"） |
| `cast_applied:<状态>` | **本次施放实际施加了该状态的次数** |
| `cast_skill_param:<n>` / `skill_param:<n>` | 技能参数表的第 n 项 |
| `self_attr_above:<阈值>` | 属性**超出阈值的那部分** |
| `steps:<n>` | 按步长分段（阶梯式加成） |

⚠ **`self_stacks:` / `target_stacks:` 读的是【层数】，而 `MODIFY_ATTR` 的 `percent` 是每层的增量** ✓ ——
所以"每层 +5%"写 `percent: 0.05` + `scale: "target_stacks:烬煨"` ✓（对照 §8 的例子）

### 5.4 持续时间与生命周期

| 字段 | 含义 |
|---|---|
| `turns` | 持续 N 回合 |
| `permanent` | `true` = **整场战斗**，且**不被回合边界递减** ✓ |
| `until` | 由**事件**结束（`next_attack` 等）✓ 与 `turns` 二选一 |
| **`ticks_on`** | ⚠ **时长按【谁的回合】递减** ✓ —— `self` = 按持有者（"罗刹倒下则解除"就靠它 ✓） |

⚠ **`turns` / `permanent` / `until` 三选一** ✓ —— `until` 时**不能同时写 `turns`** ✗（会报错 ✓）

### 5.5 层数与叠加

| 字段 | 含义 |
|---|---|
| `max_stacks` / `cap_amount` | 层数上限 / 数值上限 |
| `stackable` / `coexist` | 是否可叠加 / 是否可共存 |
| `instance` | 每次施加算**独立实例**（而不是叠加） |
| `stacks` | 一次施加几层 |
| `per_stack` / `per_stack_live` | 每层的增量 / 是否**实时**跟随层数 |

### 5.6 施加判定（"有 X% 的基础概率"）

| 字段 | 含义 |
|---|---|
| **`base_chance`** | ⚠ **基础概率** —— 会被 `(1 − 效果抵抗)` 削减 ✓ 所以 **100% 不是保证** ✗ |
| `target_when` | **筛选受术者**的条件（"生命值 ≥50% 的那些"）✓ |

⚠ **不写 `base_chance` = 直接附着、不判定** ✓ —— 这就是"100% 且不可抵抗" ✓
⚠ **引擎无法表达"100% 基础概率【且】仍可抵抗"** ✗ —— 写了会被抵抗、不写完全不可抵抗 ✓（本项目登记过这个取舍 ✓）

### 5.7 其余常用参数

`attribute`（`MODIFY_ATTR` 改哪个属性）、`buff`（状态的**名字**，`REMOVE_STATE` 靠它点名 ✓）、
`resource`（资源名）、`control`（控制名，如 `冻结`）、`element`（**这一发**的属性）、
`damage_type`（这是**哪一种**伤害：`DOT` / `BREAK` / `ADDITIONAL` / `ULTRA` …）、
`skill`（哪个技能槽）、`damage_param`（技能参数表的第几项）、
`crit_rate` / `crit_damage`（固定暴击）、`times`（打几次）、`rule`（`MODIFY_RULE` 点名的规则 id）。

⚠ **`element` 与 `damage_type` 是两个不同的问题** ✓：前者是"火/冰/雷"，后者是"DoT/击破/追加" ✓
写规则时**两者可以同时出现** ✓

⚠ **`scope_element` 只被 `MODIFY_DAMAGE_TAKEN` 读** ✓ —— 见 §8 的例子 ✓
它是**另一个字段**、不是复用 `element` ✗：`element` 是 `DAMAGE` / `APPLY_DOT` / `APPLY_CONTROL` 用来表示
**它们产生的东西**的属性 ✓，把"易伤的作用域"塞进同一个字段会让那三个的语义被改写 ✗

---

## 6. 校验：写错了会怎样

| 时机 | 行为 |
|---|---|
| **加载期** | 不认识的事件 / op / 条件变量 / `target` / `scale` / 属性 ⇒ **抛异常并列出合法值** ✓ |
| 加载期 | `REDUCE_TOUGHNESS` ⇒ 报错并说明"前置能力未落地" ✓ |
| 加载期 | 条件变量**拼错** ⇒ 报错（不再是"当假"）✓ |

⚠ **引擎的设计目标是"没有症状的错误一个都不留"** ✓ ——
**拒绝一条规则**远好于**接受它并静默地算出错误的数** ✓

---

## 7. 一个完整的例子（姬子 秘技）

```json
{
  "on": "BATTLE_START",
  "id": "technique_incomplete_combustion",
  "when": ["self has_state 秘技"],
  "do": [
    {
      "op": "MODIFY_DAMAGE_TAKEN",
      "percent": 0.1,
      "scope_element": "Fire",
      "turns": 2,
      "target": "all_enemies"
    }
  ],
  "source": "1003 姬子 秘技 不完全燃烧 (100307, Maze/Impair): 「…原文…」 param = [1, 0.1, 2, 15] → #1 = 1（100% 基础概率）、#2 = 0.1（受到的火属性伤害 +10%）、#3 = 2（持续回合）、#4 = 15（领域持续秒数）",
  "note": "「…逐句…」—— ① 「使用秘技后/进入战斗后」 = on: BATTLE_START + when: self has_state 秘技；② 「火属性」 = scope_element: Fire；③ 「100% 基础概率」 = 故意不写 base_chance（写了反而被抵抗 30%）；④ 「持续 2 回合」 = turns: 2；⑤ target: all_enemies。⚠ 登记：#4 = 15 秒没写 —— 那是地图层的事实。"
}
```

**逐条对应**：

| 句子 | 写法 |
|---|---|
| 「使用秘技后…进入战斗后」 | `on: BATTLE_START` + `when: ["self has_state 秘技"]` |
| 「100% 基础概率」 | **不写** `base_chance` ⚠（见 5.6 的取舍） |
| 「受到的**火属性**伤害提高」 | `op: MODIFY_DAMAGE_TAKEN` + **`scope_element: "Fire"`** |
| 「提高 10%」 | `percent: 0.1` |
| 「持续 2 回合」 | `turns: 2` |
| 「敌方目标」 | `target: "all_enemies"` |
| 「领域持续 15 秒」 | ✗ **写不了** ⇒ 登记（地图层） |

---

## 8. 写一条新规则的步骤

1. **抄原文**进 `source`，标出 `#N` 与参数表的对应 ✓
2. **定事件**：看句子的时间点（§4）✓
3. **定条件**：句子里的前提（§3）✓ —— ⚠ **注意 `self` / `actor` / `target` 的区别** ✓
4. **逐句拆效果**（§5）✓ —— ⚠ **只用语料里已有的 op** ✓；缺能力就**登记**，**不要近似** ✗
5. **写 `note`**：每句 → 哪个字段 ✓；**写不了的部分必须写清楚为什么** ✓
6. **加用例**，并且**两半都要**：
   - **门**：不满足条件时**什么都不应该发生** ✓
   - **效果**：满足时**数字对** ✓
7. **跑全套**：`gradlew test` ✓

⚠ **第 6 步的两半是这个项目的硬要求** ✓ ——
**只有"有效果"的用例，会在"门根本没接上"时也通过** ✗（本项目为此踩过多次 ✓）

---

## 9. 常见错误

| 症状 | 原因 |
|---|---|
| 规则**永不触发** | 条件变量**拼错**（旧行为）✗ / `self` 与 `target` 用反 ✓ |
| 触发了但**数字不对** | `scale` 读错了对象（`self_` / `owner_` / `target_` / `actor_`）✓ |
| 规则**只在某些时候**生效 | `base_chance` 被**效果抵抗**削了 ✓（§5.6）✓ |
| 「降到 50%」**每次都触发** | 用了 `target_hp_percent` ✗ ⇒ 要用 **`target_hp_percent_before`** ✓ |
| 「降低伤害」变成了「提高」 | `MODIFY_DAMAGE_TAKEN` 的**符号就是方向**：正=易伤（负面）✓、负=减伤（正面）✓ |
| 状态**一直不消失** | `ticks_on` 指向了**不会被推进的回合** ✓（本项目修过一次：挂在敌人身上、时钟却是我方 ✓）|
| 效果**从不出现在日志** | 事件发生在**第一个回合开始之前**（`BATTLE_START`）✓ —— 前端需要单独处理这一段 ✓ |

---

## 10. 相关文档

| 文件 | 内容 |
|---|---|
| `AGENT.md` | 引擎的架构与红线、分阶段设计与**每条决策的理由**、**已知缺口**、踩坑记录 |
| `HSR.md` | 游戏侧的资料（伤害公式、属性表等） |
| `README.md` | 项目入口 |
