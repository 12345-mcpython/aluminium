# 触发器（trigger）JSON 格式

> 本文写的是**规则文件怎么写**。引擎的架构与取舍在 `AGENT.md`，游戏侧的资料在 `HSR.md`。
>
> ⚠ **本文的每张表都是从代码里读出来的，不是凭印象写的** ✓ —— 字段名来自 `beans/TriggerSpec.java` 与 `beans/EffectSpec.java` 的
> `@SerializedName`；运算符、事件、操作、选择器、`scale` 来自引擎自己的闭集与**语料里的实际用法**（**94** 个角色文件；重新数：`ls src/main/resources/game/characters/*.json | wc -l`）。
> 语料目前用到 **50** 个 op、**211** 条不同的条件串 ✓。凡本文没列的名字，引擎都会在**加载期**报错并列出合法值 ✓，所以不会出现"写错了但静默无效"的情况 ✓。

---

## 1. 规则写在哪里

| 位置 | 内容 |
|---|---|
| `src/main/resources/game/characters/<cid>.json` | 一个角色（或光锥）的**全部规则** |
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

⚠ **冷却可以被改** ✓：`MODIFY_RULE` + **`cooldown_delta`**（正 = 延长、负 = 缩短；见 §5.1 的 op 表 ✓）——
「天赋的冷却时间减少 1 回合」= `cooldown_delta: -1` ✓，被点名的规则**必须自己写着 `cooldown`** ✓，
否则加载期就拒绝 ✓。

### 星魂 / 变体

| 字段 | 含义 |
|---|---|
| **`min_eidolon`** | 星魂 ≥ N 才生效 |
| **`max_eidolon`** | 星魂 ≤ N 才生效 |
| **`one_of`** | 整组里只取一条（同组规则按顺序取第一条可用） |
| **`one_of_no_repeat`** | 同上，但**不重复**取已取过的 |
| **`defer_until_wave`** | 延到下一波才结算 |

---

### 3.0 谓词（`when_predicates`）—— OR、嵌套、具名复用

`when` 只能表达"**全部成立**" ✓。谓词把条件写成**对象**，补上 `when` 表达不了的三件事 ✓：**任一成立**（OR）、**任意嵌套**、**具名复用** ✓。

```json
"when": ["actor is_ally"],
"when_predicates": [
  { "type": "any_of", "terms": [
      { "type": "condition", "spell": "actor == self" },
      { "type": "ref", "ref": "ally_shielded_by_wearer" } ] },
  { "type": "not", "term": { "type": "condition", "spell": "target has_shield" } }
]
```

| `type` | 字段 | 语义 |
|---|---|---|
| `all_of` / `any_of` | `terms` | **全部** / **任一** 成立 ✓ |
| `not` | `term` | 取反 ✓ —— ⚠ 对 **party 条件**（self / actor / target）**"没有这一方"仍然判否** ✓，与 `!` 前缀同一条规则 ✓ |
| `entity` | `entity` + `json` | **实体谓词**：`entity` 是被问的那一方（`self` / `actor` / `target` ✓），`json` 是**子谓词 → 值**的映射 ✓（见下表 ✓）|
| `ref` | `ref` | `game/data/predicates.json` 里的**具名谓词** ✓ —— `{"name": {"type": ...}}` ✓ |

**实体子谓词**（`json` 的键；未列出的键、类型不对的值，**装载期就拒绝** ✓）：
⚠ **分段的两个边界**（2026-10-30，读 `skill_segments_fields_zh.md` 后核过）：① `segments` 是**同一批目标**的份额序列（`HitSplitRatio` = 乘性份额 ✓），所以"同一发打不同目标类别、各自吃满"（`[1.0, 1.0]`，如 11415/1、11413/8）**不能**写成 `segments`，要写 `damage_parts` ✓；② **`multi_target_rule`（每目标每段 = 行倍率 ÷ N）引擎还没有** —— 全表只有 8 条段实例用到，且**都不在复刻的四人身上**（见 `AGENT.md` P9-6 ✓）。

⚠ **`event` 谓词**（2026-10-29）：有些条件说的是**这次施放**而不是任何单位 —— `{"type": "event", "json": {"from_skill_id": 1}}`、`{"type": "event", "json": {"from_skill": "SKILL"}}`、`{"type": "event", "json": {"damage_is_attack": true}}`。它**不新开文法**：把事实渲染成条件文字后交给 `TriggerConditionParser` 编译 ✓ ⇒ 语义与拼法都只有一份 ✓（`source()` 就是那句话本身 ✓）。配合**开区间** `{"gt": 200}` / `{"lt": 200}`（「速度**大于** 200」没有闭区间写法 ✓），四人文件里的字符串 `when` 已经**清零** ✓。`n`n⚠ **`memosprite_name`**（2026-10-29）：`SUMMON` 可以指出**召唤哪一只**忆灵（1512 的三只「晴空乐手」✓）。忆灵文件可写**一个对象**（绝大多数角色）或**数组**（一角色多忆灵 ✓）；`Memosprites.of` 仍答第一个 ⇒ 老调用方不用改 ✓。

⚠ **`once_per_turn`**（2026-10-29）：效果级「本回合第一次」—— `{"op": "GAIN_RESOURCE", "amount": 1, "once_per_turn": "heal_or_shield"}`（1512 天赋 ✓）。窗口是**回合**、key 由作者给 ⇒ 两条规则可以用**同一个 key** 表示"同一件事"（治疗与护盾 ✓）；窗口在 **`TURN_START` 事件**上打开 ✓（不是"自己的回合"，句子说的是「任意目标回合」✓）。

⚠ **`blocks`**（2026-10-29）：`APPLY_BUFF` 的**抑制**字段 —— 「无法使其他友方目标获得行动提前效果」（1512 的【特邀嘉宾】✓）。抑制不是属性、是**动作**禁令 ⇒ 由状态承载（`Buff#blocks()` ✓）、由**做那个动作的 op**读（`ADVANCE` ✓）；只挡「对别人」的提前 ✓，且读的是**给予者**的状态 ✓。拼法是闭集，装载期拒绝别的词 ✓。

⚠ **`once_per_battle`**（2026-10-29）：`skill_effects.json` 的**历史**类可施放性 —— 「单场战斗中只能施放 1 次」（1415 终结技 ✓）。与 `castable_when`（资源类）分开：前者由 `Battle` 记账、施放提交时才消耗 ✓；被别的门拒掉的那次**不消耗** ✓。

⚠ **迁移进度**（2026-10-29）：内容里的 2947 条 `when` 条件中，**1280 条**可由本表表达（215 个文件各至少一条）；已迁 **1001 三月七**（8 条）、**1409 风堇 / 1413 长夜月 / 1415 昔涟 / 1512 晴空乐手**（37 / 14 / 25 / 11 条，即复刻那场战斗的四个 ✓），其余按角色逐批迁 —— 一次只迁一个文件，让该角色的判据兜住 ✓。**不能迁的**：事件级条件（`from_skill` / `from_category` / `from_skill_id` / `damage_is_attack`，共 167 条）留在 `when` ✓；以及尚无子谓词的那些（`is_other_ally`、`debuff_count`、`energy_percent`、`enemy_count`、`is_slowed`、`self_summon_count`）。⚠ 迁移**只改形状不改语义**：编译后的 `source()` 会**还原成原来的拼法**（整数阈值也按解析器的 `4.0` ✓），所以钉「这条规则的守卫」的判据照旧通过 ✓。⚠ **`same_as: "summon"` 是"归属"不是"在场"** ✓ —— `SUMMON_LEFT` 触发时忆灵已经离场（`summonOf` 返回 null ✗），所以它按"这是**我的**召唤物"判 ✓（实测：按在场判会让离场规则永不开火 ✓）。


| 键 | 值 | 读什么 |
|---|---|---|
| `is_ally` / `is_enemy` | `true`/`false` | 阵营 ✓ |
| `is_dead` / `is_summon` / `has_shield` | `true`/`false` | 死亡 / 是忆灵 / 有护盾 ✓ |
| `shield_from` | `self`/`actor`/`target` | 盾**是谁给的** ✓ |
| `hp` / `max_hp` / `energy` / `shield` / `level` / `cid` / `speed` | 数字（= ≥）或 `{"min"/"max"}` | 绝对量 ✓（两端都是**闭区间** ✓）|
| `eidolon` | 同上 | **星魂数** ✓ |
| `skill_level` | `{"name": "SKILL", "min"?: n, "max"?: n}` | **某个槽位的等级** ✓（槽位名装载期校验 ✓）|
| `hp_pct` / `shield_pct` | 同上 | **百分比** `当前/上限` ✓（盾的份额 = 盾 / 生命上限 ✓）|
| `state_turns` | `{"name": "充能", "min"?: n, "max"?: n}` | **剩余回合** ✓（多个同名取最大 ✓，没有 = 0 ✓）|
| `camp` | `PLAYER` / `ENEMY` / `NEUTRAL`（或数组 = 任一）| **阵营** ✓（绝对）|
| `summon_count` | 数字或 `{"min"/"max"}` | 该单位**在场的召唤物数**（= `self_summon_count` ✓）|
| `enemy_count` | 同上 | 该单位的**敌人存活数**（= `enemy_count` ✓）|
| `resource` | `{"name": "追忆", "min"?: n, "max"?: n}` | 该单位的计数器（= `self_resource:` / `summon_resource:` ✓；**没声明会报错** ✓）|
| `energy_pct` | 同上 | 能量百分比（= `self_energy_percent` ✓）|
| `debuff_count` | 同上 | 负面效果数（= `target_debuff_count` ✓）|
| `same_as` | `self` / `actor` / `target` / `summon` | **同一性**：这一方就是那一方 ✓（`actor == self` 在内容里有 1238 处，是出现最多的条件 ✓）|
| `team` | `own` / `other` | **相对规则主人**的阵营 ✓（"我方 / 对方" ✓）|
| `attribute` | `{"name": "SPEED", "min"?: n, "max"?: n}` | 任意属性 ✓（名字在装载期校验 ✓）|
| `stacks` | `{"name": "充能", "min"?: n, "max"?: n}` | 层数 ✓（= `self_stacks:` ✓）|
| `state` / `path` / `element` | 字符串或字符串数组（数组 = **任一** ✓）| 状态 / 命途 / 属性 ✓ |

⚠ **缺主语就判否** ✓ —— 例如 `BATTLE_START` 没有 target，`{"entity": "target", ...}` 一律失败 ✓（与条件 DSL 同一条规则 ✓）；`not` 也**不会**把它翻成通过 ✓。
⚠ **装载期就拒绝**：未知 `type` ✓、实体谓词的空 `json` ✓、未知子谓词 ✓、值形状不对 ✓、未知属性名 ✓、未知阵营 / 未知 `team` / 未知槽位 ✓、`all_of`/`any_of` 空 `terms` ✓、`not` 缺 `term` ✓、`ref` 未声明 ✓、**具名谓词成环** ✓（报错会打出环的路径 ✓）。
⚠ 谓词是**判定**（是否执行），不是**数值**（用哪个数）✗ —— 后者是 `percent_from` / `amount_from` 的数值提供器 ✓；两者在一条规则里各管一半 ✓。
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
| `X is_slowed` | 是否处于**减速**（`SPEED` 上有负修饰 ✓）—— ⚠ **不是**"净速度低于基础值" ✗：带着 +20% 加速又中了 −12% 减速，仍然算减速 ✓ |
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
⚠ **`KILL` 的施放沿"伤害链"** ✓（2026-10-17 ✓）：实例自己的 `castCategory` 是"这一发是不是那个技能**自己**的伤害" ✓；而**引擎派生**出来的实例（击破 / 超击破）带的是 `causedByCast` ✓（产生它的那一发的施放 ✓）。`KILL` 用的是后者补前者 ✓，所以「施放终结技消灭敌方目标时」在**终结技击破、由那一发的击破伤害致**死时也成立 ✓（读者：1002 丹恒星魂 4 ✓）。
⚠ 而 `DAMAGE_SETTLED` **不**沿链 ✓：它问的是"这一发**本身**" ✓ —— 实测：让 `DAMAGE_SETTLED` 也沿链，1003 姬子星魂 6「原伤害的 40%」会对**击破实例**再算一次 ✓（并且它的 `random_enemy` 在那个上下文里解析不了 ✓）。同理，`ADD_DAMAGE` / `BOOST_DAMAGE` / 增伤区读的始终是**实例自己**的槽位 ✓ —— 否则终结技的倍率加成会加到击破伤害上 ✗。

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
| `skill_point` | **当前**战技点 —— 与上面那个**上限**是两个数（读者：1015 Archer 的行迹「若战技点大于等于 4 点」、战技的「战技点不足以再次施放战技则退出」） |
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

## 4. 事件（`on`，共 40 个）

| 分类 | 事件 |
|---|---|
| **战斗流程** | `BATTLE_READY` `BATTLE_START` `WAVE_START` `TURN_START` `TURN_END` `EXTRA_TURN_STARTED` `EXTRA_TURN_ENDED` `COUNTDOWN_TURN` |
| **施放** | `CAST_SETUP` `SKILL_CAST` `BASIC_ATTACK` `ULT_CAST` `INSERTED_CAST_END` `ATTACK_FINISHED` |
| **攻击与受击** | `ALLY_ATTACK` `ENEMY_ATTACK` `SUMMON_ATTACK` `TAKING_HIT` `DEALING_DAMAGE` `DAMAGE_SETTLED` `FOLLOW_UP` `CRIT_DEALT` |
| **伤害与生命** | `HP_LOST` `HP_CONSUMED` `LETHAL_DAMAGE` `HEALED` `SHIELD_GRANTED` `DEATH` `KILL` |
| **击破与控制** | `BREAK` `WEAKNESS_ADDED` `DEBUFF_APPLIED` `STATE_ENDED` |
| **资源** | `ENERGY_GAINED` `SKILL_POINT_GAINED` `SKILL_POINT_SPENT` `SKILL_POINT_OVERFLOWED` `RESOURCE_CHANGED` |
| **召唤** | `SUMMONED` `SUMMON_LEFT` |

⚠ **`SUMMON_LEFT` 是「忆灵离场」，不等于「死亡」** ✓ —— 忆灵可以没被打死就消失（`CanHit.perish()`），
那种消失 `DEATH` 收不到 ✓、只有它收得到 ✓。收到它的是**还活着的规则持有者**（离场的那位自己没有表可以动 ✓），
所以条件写 `actor is_summon` ✓；`target == summon` 在这里会失效 ✗（该选择器拒绝已经不在场的单位 ✓）。

⚠ **`BATTLE_START` 是最常用的一类** ✓ —— 秘技（`self has_state 秘技`）、星魂等级声明、开战加攻都属于它 ✓
⚠ 而它的效果**在任何回合开始之前结算** ✓ —— 前端要记得把这段写进日志 ✓（本项目为此专门修过一次 ✓）

---

## 5. 效果（`do` 里的一项）

### 5.1 引擎能执行的操作（**52 个**，`TriggerOperator` 里 `wired = true`）

```
ABSORB_DAMAGE          ADD_DAMAGE             ADD_ELEMENTAL_WEAKNESS ADD_STACK
ADVANCE                APPLY_BUFF             APPLY_CONTROL          APPLY_DOT
APPLY_REGEN            BOOST_DAMAGE           BOOST_TOUGHNESS        CAST_SKILL
COMMAND_SUMMON         CONSUME_HP             DAMAGE                 DELEGATE_DAMAGE
DISMISS_SUMMON         DISPEL                 EXTEND_BUFF            EXTRA_TURN
GAIN_ENERGY            GAIN_RESOURCE          GAIN_SKILL_POINT       HEAL
INSERT_ACTION          MODIFY_ATTR            MODIFY_DAMAGE_TAKEN    MODIFY_RULE
RAISE_ENERGY_TO        RAISE_RESOURCE_CAP     RAISE_SKILL_HITS       RAISE_SKILL_LEVEL
RAISE_SKILL_POINT_CAP  REGISTER_HEAL_BONUS    REMOVE_BUFF            REMOVE_RESOURCE
REMOVE_STACK           REMOVE_STATE           REPLACE_SKILL          RESET_RESOURCE_CAP
RESET_RULE_CHANCE      RESET_TRIGGER_LIMIT    RESIST_DEBUFF          SHIELD
SPEND_RESOURCE         SPEND_STEPS            START_COUNTDOWN        SUMMON
SUMMON_SERVANT         SUPER_BREAK            TAUNT                  TICK_DOT
```

⚠ **`DISMISS_SUMMON`** 是「忆灵**离场**，不是死亡」的那一半 ✓（读者：11413 忆灵技2「迷梦，流失，如露」的「并使「长夜」消失」✓）。
它走 `perish()`，随后 `SUMMON_LEFT` 照常公告 ✓ —— 不另开一条移除路径 ✓；目标不是召唤物时**报错**而不是静默 ✓。

⚠ **只有 `REDUCE_TOUGHNESS` 是"声明了但还没实现"** ✓ —— 写它会**在加载期**报错并说明原因 ✓

⚠ **常用操作的参数以源码的 op 表为准** ✓ —— 那张表在
`trigger/TriggerInterpreter` 的类注释里，**每个 op 一行，列出它的参数与状态** ✓。
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

⚠ **`percent` 不是唯一的"份额"写法** ✓ —— 份额也可以从**技能参数表**里读（五条拼法、各自的读者跑过代码核对）✓：

⭐ **数值提供器**（`percent_from` / `amount_from`）：把"这个数字从哪来"写成一个对象 ✓，`amount_from` 同时也是**常数项**的来源 ✓。
它**取代**下表五条字段拼法（两条拼法**互斥** ✓，同时写会在装载期被拒 ✗）：

```json
"percent_from": { "type": "skill_param", "skill": "SKILL", "index": 1 },
"amount_from":  { "type": "add", "inputs": [
                    { "type": "cast_param", "index": 1 },
                    { "type": "const", "value": 2 } ] }
```

| `type` | 字段 | 读什么 |
|---|---|---|
| `const` | `value` | 字面量 |
| `skill_param` | `skill` + `index` | 规则主人自己某个槽位、当前等级那一行的第 index 项（= `percent_from_skill_param` ✓）|
| `cast_param` | `index` | 产生这次事件的技能当前等级那一行（= `percent_from_cast_param` ✓）|
| `summon_param` | `slot` + `index` | 规则主人忆灵某个数据槽位（= `percent_from_summon_param` ✓）|
| `resource` | `resource`（+ `basis_points`）| 规则主人的资源（= `percent_from_resource` ✓；`basis_points: true` 时 ÷10000 ✓）|
| `add` / `mul` / `min` / `max` / `avg` | `inputs` | 逐项求值后归约（`add` 空表=0 ✓、`mul` 空表=1 ✓；`min`/`max`/`avg` 空表在装载期被拒 ✗）|
| `sub` / `div` | `left` + `right` | 二元运算（`div` 除零在**结算期**报错 ✓）|
| `negate` / `abs` | `input` | 相反数 / 绝对值 |
| `attr` | `attribute`（+ `whose` / `above`）| 单位的属性（= `self_attr:` ✓；`whose: target` 读事件目标 ✓；`above` 只算超出部分，= `self_attr_above:` ✓）|
| `stacks` | `buff`（+ `whose`）| 层数（= `self_stacks:` / `target_stacks:` ✓）|
| `original_damage` | — | **触发这一发本身**的伤害（= `scale: original_damage` ✓，真伤不做系数折算 ✓）|
| `settled_damage_this_event` | — | **本次触发已结算的总伤害**（= `scale: settled_damage_this_event` ✓）|
| `elation_base` | — | 欢愉体系的基础值（= `scale: elation_base` ✓）|
| `cast_applied` | `state` | **本次施放实际施加了该状态的次数**（= `scale: cast_applied:<状态>` ✓）|
| `party_resource` | `resource` | **战利品级的队伍计数器**（= `scale: party_resource:<名>` ✓；没声明会报错 ✓，不是 0）|
| `energy` | `whose`（+ `relation`）| 能量条：`relation: current`（默认）/ `max` ✓ |
| `skill_point` | — | 战技点（队伍共享的一份 ✓）|
| `steps` | `counter` + `step` | 阶梯计数 `floor(值 / 步长)`（= `per_steps` 的读数 ✓；`counter` 可以是属性 / 自己的资源 / `战技点` ✓）|
| `uniform` | `min` + `max` | 区间内均匀随机（**用战斗自己的 RNG** ✓ ⇒ 同种子可复现 ✓，`max` 开区间）|
| `weighted_list` | `distribution: [{weight, value}]` | 按权重随机（权重是**相对**的 ✓、`0` 权重的项**永远不中** ✓ —— 用作"兜底"）|

⚠ **装载期就拒绝**：`type` 不在上表 ✓、`const` 缺 `value` ✓、`skill_param` 缺 `skill` ✓、`*_param` 缺 `index` ✓、`summon_param` 缺 `slot` ✓、`add` 族缺 `inputs` ✓、`sub`/`div` 缺操作数 ✓、`attr` 缺 `attribute` ✓、`stacks` 缺 `buff` ✓、`uniform` 缺 `min`/`max` ✓、`weighted_list` 没有正权重 ✓、`cast_applied` 缺 `state` ✓、`party_resource` 缺 `resource` ✓、`steps` 缺 `counter`/正 `step` ✓、`energy` 的 `relation` 不是 `current`/`max` ✓，以及与对应字段拼法同时写 ✓。
⚠ **`MODIFY_RULE` 改不了 provider 份额** ✗ —— `effect_percent` 只能加在**字面 `percent`** 上（`amendmentDelta` 只进 `percent_from_skill_param` 那一支 ✓）；`percent_from` 的规则**没有"被改大"的语义** ✗。实测（2026-10-29 迁移那一批）：1413 里被 `MODIFY_RULE` 改的那条规则改成 provider 后，装载期报「none of that rule's effects states a "percent"」✗ ⇒ 那条规则**保留旧拼法** ✓。⚠ **provider 不做分支** ✗ —— `conditional` 一类**故意没有** ✓：要不要执行是**条件**（`when`）的事 ✓，一个数字里再分一套执行模型是第二条路 ✓。

| 字段 | 读的是 | 哪些 op 读 |
|---|---|---|
| `percent_from_skill_param: "<SKILLTYPE>:<index>"` | **持有者自己**某个槽位、**当前等级**那一行的第 index 项 | 伤害类 + `MODIFY_ATTR` + `HEAL` / `SHIELD`（+ `APPLY_DOT` / `APPLY_CONTROL` 的每回合伤害）✓ |
| `percent_from_cast_param: <index>` | **产生这次事件的那个技能**当前等级的第 index 项（施放者是**角色或忆灵**都行 ✓：槽位就是 `Skill.getSkillSlot()` ✓） | 同上 |
| `amount_from_skill_param: "<SKILLTYPE>:<index>"` | 同第一行，但读的是**常数项**（`percent × scale + amount` 的那个 `amount`） | **所有读平值的 op** ✓：`HEAL` / `SHIELD` / `GAIN_ENERGY` / `GAIN_SKILL_POINT` / `GAIN_RESOURCE` / `CONSUME_HP` / `APPLY_DOT` / `APPLY_CONTROL` 的每回合伤害 ✓（同一 reader：`TriggerMagnitudes.constantTerm` ✓） |
| `percent_from_summon_param: "<DATA SLOT>:<index>"` / `amount_from_summon_param: "<DATA SLOT>:<index>"` | **规则主人的忆灵**某个**数据槽位**（1 / 3 / 5 …）在**当前等级**那一行的第 index 项 | 与上面两支相同 ✓；⚠ 忆灵的技能**不按 `SkillType` 存** ✗ ⇒ 只能用这个写法 ✓，而且它属于"别人的事件上也读得到"那一类 ✓ |
| `amount_from_cast_param: <index>` | 同 `percent_from_cast_param`，但读**常数项** | 同上 ✓（读者：忆灵施放且常数也随等级变的那种句子 ✓） |

⚠ **`RAISE_SKILL_LEVEL` 的忆灵拼法** ✓：`{"op": "RAISE_SKILL_LEVEL", "memosprite": true, "skill_id": 1, "amount": 1}` ✓ ——
`skill_id` 是忆灵的**数据槽位** ✓（角色槽位仍写 `skill`，两者互斥 ✓）。⚠ 加成记在**召唤者**身上 ✓
（忆灵在 `BATTLE_START` 时**还不存在** ✓，它出场更晚、而且可能重复出场 ✓）。

⚠ **两个半都随等级才算"随技能等级浮动"** ✓：像「#1% 防御力 + #4」这种句子，**份额与常数在表里都逐行变化** ✓，
只让一个半跟着走会得到**任何一行都不存在**的数字 ✓（§6 的例子）。
⚠ **一个 `amount` 只有一种来源** ✓ —— 字面量与 `amount_from_skill_param` 同时写，加载期就拒绝 ✓。

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
