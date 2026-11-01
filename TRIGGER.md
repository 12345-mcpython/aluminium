# 触发器规则格式（trigger）

> 这是**规则语言的规范**，不是台账。引擎的架构、取舍和踩坑记录在 `AGENT.md`；游戏侧的公式在 `HSR.md`。
>
> **“现在是什么”的权威是代码**：字段名来自 `beans/TriggerSpec.java` / `beans/EffectSpec.java`，事件来自
> `enums/TriggerEvent`，op 来自 `trigger/TriggerOperator`，条件变量和前缀来自 `trigger/TriggerConditionParser`。
> 本文只回答“规则怎么写”，不复制每个 op 的参数表 —— 那张表在 `TriggerInterpreter` 的类注释里，复制一份就会和源表分叉。

---

## 0. 写一条新规则的顺序

1. **抄原文**进规则的 `source`，标出 `#N` 与参数表的对应。
2. **定事件**：看句子的时间点（§4，尤其 §4.1 的时序）。
3. **定条件**：句子的前提（§3）；注意 `self` / `actor` / `target` 的区别。
4. **拆效果**（§5）：只用语料里已有的 op；缺能力就**登记**，不要近似。
5. **写注解**：`_note` 写逐句映射、为什么选这个词、哪部分写不了；新文件用 `_` 前缀，旧文件继续用 `note`。
6. **加判据**，两半都要：不满足条件时**什么都不发生**；满足时**数字对**。
7. **跑全套**：`gradlew gate`。

⚠ 第 6 步的两半是硬要求：只有“有效果”的用例，会在“门根本没接上”时也通过。

---

## 1. 规则写在哪里

| 位置 | 内容 |
|---|---|
| `src/main/resources/game/characters/<cid>.json` | 一个角色的全部规则（光锥/遗器/忆灵也有各自的目录） |
| 顶层 `rules` | 规则数组；有些文件顶层直接就是数组 |
| 顶层 `resources` | 该角色声明的计数器（可选） |

一条规则 = **一个事件** + **一组条件** + **一串效果**。

```json
{
  "on": "SKILL_CAST",
  "id": "skill_shield",
  "when": [
    { "type": "entity", "entity": "actor", "json": { "same_as": "self" } }
  ],
  "do": [
    {
      "op": "SHIELD",
      "value": {
        "type": "add",
        "inputs": [
          { "type": "mul", "inputs": [
              { "type": "attr", "attribute": "DEFENCE", "whose": "owner" },
              { "type": "skill_param", "skill": "SKILL", "index": 0 } ] },
          { "type": "skill_param", "skill": "SKILL", "index": 3 } ]
      },
      "turns": 3,
      "target": "target"
    }
  ],
  "source": "1001 三月七 战技 可爱即是正义 (100102): 「为指定我方单体提供…等同于三月七#1[i]%防御力+#4[i]伤害的护盾，持续#2[i]回合。」",
  "_note": "逐句映射：…；⚠ 未写：…"
}
```

### 1.1 两种顶层形状

```json
[ { "on": "BATTLE_START", "do": [ … ] } ]
```

```json
{
  "schema": 1,
  "character": 1001,
  "resources": [ { "id": "充能", "max": 3 } ],
  "rules": [ { "on": "BREAK", "do": [ … ] } ]
}
```

- 裸数组仍然合法；需要文件头或 `resources` 时用对象。
- 对象形式**必须有** `rules`；写错键（`"trigger"`）会在装载期拒绝，而不是变成“没有规则”。
- `schema` 可选。省略 = 旧文件；写了必须是 **1**。别的值装载期报错 —— 新格式的文件不会被旧引擎静默读错。
- `character` 可选。写了必须等于文件名里的 cid；复制到别的 cid 下的规则集会装载期被拒。
- 出货的角色文件**都**写 schema: 1 + character: <cid>；光锥 / 遗器的同一套文件头见 §1.1a，三者由 ContentFileHeaderTest 逐文件把守。

### 1.1a 光锥 / 遗器的文件头（2026-10-31 定形）

光锥文件按**叠影档**分键（`"1"`..`"5"`），遗器文件按**件数**分键（`"2"` / `"4"`）。文件头就写在**这些键旁边**，不把行包进
`"ranks": {...}` —— 包一层只会把 229 个文件整体重排、不增加任何信息，而「元数据与行并列」在光锥的 `resources` 上已有先例。

```json
{ "schema": 1, "light_cone": 23055, "1": [ … ], … , "5": [ … ] }
{ "schema": 1, "relic_set": 101,   "2": [ … ], "4": [ … ] }
```

- 键名就是内容种类：角色 `character` / 光锥 `light_cone` / 遗器 `relic_set`。写**别的种类**的键 = 未知键，装载期拒绝 ——
  这样把一个角色文件复制成光锥文件不会“看起来能读”。
- 两个键在装载器里都**可选**（旧文件照读），但**出货内容必须都有**：`ContentFileHeaderTest` 扫三个目录里的每一个文件；
  漏一个、写错 id、写成别的种类，都会红。
- `schema` 写了必须是 **1**，`light_cone` / `relic_set` 必须是文件名里的 id。
- 顶层允许的键是：数字键（档位/件数）、该种类的元数据键、以及 §1.2 的注解。**其余一律拒绝** —— 在此之前光锥装载器
  对未知顶层键是**静默忽略**，遗器装载器则把它当件数解析（报一个看不懂的错）。
- 光锥额外允许 `rules`（所有档共用一份，见装载器）与 `resources`；遗器两者都没有。
- 文件级 `_status` / `_gaps` **现在允许但还没要求**（`ContentFileHeaderTest` 只查 `schema` + 归属键）；要不要逐件标状态
  是下一步的政策决定，不是格式缺口。

⚠ 光锥/遗器的规则数组现在也走**与角色文件同一套**词表守卫（规则键 / 效果键 / 条件键 / 状态注解）：
`TriggerTables.requireRuleVocabulary`。以前这两个装载器直接把规则交给 Gson，拼错的键（`_notee`）在那里是**静默丢弃**的。

### 1.2 注解与注释

这些键可以出现在**文件 / 规则 / 效果**三层，装载器接受，但**不会进入任何 bean**：

```
_note  _source  _status  _gaps  _divergences  _changelog  _literal_reason  _comment
```

- 只认这八个确切名字；`_notee` 和别的拼错一样在装载期报错。
- 旧的 `source` / `note` 仍然可读；`source` 是运行期保留的 provenance 字段，`note` 只作为注解、**不进 bean**。新注解用下划线形式。
- **构建期剥离**：源文件保留注解；`processResources` 只给触发规则目录（`characters` / `light_cones` / `relic_sets`）的运行时副本剥掉注解，`source` 保留 ✓；`memosprites` 的 `note` 是运行期字段，不剥；资源声明的 `note` 也是 `ResourceSpec` 字段，不剥。
- **参数行对账**：参数行写在 `source` 对象里（`skill` / `row` / `params`），`ParamAnnotationReconciliationTest` 拿它跟 `game/data/skills.json` 的对应行逐项比较；独立的 `_params` 注解已移除。它不是运行时字段，构建期同样剥掉。
- **结构化状态**：`_status` 的闭集是 `implemented` / `partial` / `registered` / `unmodelled`；`_gaps` / `_divergences` 是字符串数组；非空 gap 必须配 `_status`，且 `implemented` 不能带非空 gap、其余状态必须带一条。未知状态装载期拒绝。状态可写在**规则**上，也可写在**对象形式的文件顶层**：全部 95 个角色文件都有文件级 `_status`（12 个 `implemented`，83 个 `partial` + `_gaps`）。光锥 / 遗器暂未要求文件级状态，见 §1.1a。
- `//` 与 `/* … */` 注释可以写；**尾逗号不行**（Gson 的 lenient 模式只放过注释）。

### 1.3 `source` 与注解不是装饰

- `source` 是**运行期保留**的 provenance：写原文与参数表，并标明 `#N` 对应参数表的第几个；装载失败时它会被引用。
  它可以是**字符串**，也可以是**对象**：
  ```json
  "source": { "text": "1001 三月七 战技…「…」", "skill": "SKILL", "row": 10,
              "params": { "#1": 0.57, "#2": 3, "#4": 760 } }
  ```
  引擎只保留 `text`（错误信息用）；结构化字段供读者和参数对账（`source.params`），装载期校验未知键 / 缺 `text` / 坏 `row` / 坏 `params`。1001 的 5 条参数规则已全部用 `source.params`。
- `_note`（旧拼法 `note`）写**逐句映射**：句子的每个子句 → 哪个 op / 哪个字段；**写不了的部分必须登记**。它是注解，不进运行时 bean。
  **全部主内容已迁到 `_note`**（角色 95 / 光锥 169 / 遗器 62，共 2309 条规则）；`resources[].note` 仍是 `note`（`ResourceSpec` 运行期字段）。
- 过时的注记比缺失的注记更坏：缺失让人去查，写错让人去**造**。
- 数字以参数表/规则字段为准；注记里手抄的数字不参与执行，也不能拿来当真相。

---

## 2. 规则级字段

### 2.1 基本字段

| 字段 | 类型 | 作用 |
|---|---|---|
| **`on`** | string | 触发事件（§4）。必填 |
| **`on_any`** | string[] | 多个事件共用一个 `do`；与 `on` 二选一 |
| **`when`** | array | 条件（§3）。每一项是 DSL 字符串**或**谓词对象；全部满足才触发；省略 = 无条件 |
| **`id`** | string | 规则名。`MODIFY_RULE` / `RESET_RULE_CHANCE` / `RESET_TRIGGER_LIMIT` 靠它点名 |
| **`do`** | object[] | 效果（§5）。必填 |
| **`source`** | string 或 object | 见 §1.3；运行期保留 `text`，结构化字段供读者 / 参数对账。注记用 `_note`（旧拼法 `note`），不进 bean |

### 2.2 触发上限（“每回合只能触发 N 次”）

| 字段 | 含义 |
|---|---|
| **`per_turn`** | 按【规则持有者自己的回合】计数，最多 N 次 |
| **`per_subject`** | 计数按谁算：`self`（默认）/ `actor` / `target` |
| **`cooldown`** | 触发后冷却 N 回合（按持有者的回合递减） |
| **`once_per_battle`** | 一场战斗只触发一次 |
| **`once_per_wave`** | 每波一次 |
| **`once_per_attack`** | 同一次攻击内只触发一次 |
| **`per_attack`** | 同一次攻击内最多 N 次 |
| **`chance`** | 这条规则本身的触发概率（不是效果命中） |
| **`chance_decay`** | 每次触发后概率的衰减因子 |
| **`chance_rolls`** | 一次开火独立掷几次 `chance`（默认 1） |

⚠ `cooldown` 可被 `MODIFY_RULE` + `cooldown_delta` 修改；被点名的规则必须自己写着 `cooldown`，否则装载期拒绝。
⚠ `chance: 0` 和 `per_turn: 0` 这类“永远不会发生”的值会被拒绝；省略字段才是“不限”。
⚠ `per_turn` 与 `cooldown` 不能同时写；“每回合最多 2 次”和“每两回合一次”是两个意思。

### 2.3 星魂 / 变体

| 字段 | 含义 |
|---|---|
| **`min_eidolon`** | 星魂 ≥ N 才生效 |
| **`max_eidolon`** | 星魂 ≤ N 才生效 |
| **`one_of`** | 整组效果里随机取一条 |
| **`one_of_no_repeat`** | 同上，但不重复取上一次的；值是共享记忆的名字 |
| **`defer_until_wave`** | 触发时若敌方全灭，延到下一波再结算 |
| **`pick`** | 规则级**一次抽签**：写一个单选选择器（如 `random_enemy`），效果的 `target` 写 `pick` 就共用这一次抽签（读者：1111 卢卡秘技「随机单体」伤害 + 同一目标的裂伤）|

---

## 3. 条件

### 3.1 一个 `when`，两种项

`when` 的每一项可以是：

- **DSL 字符串**：`"actor == self"`、`"target has_shield"`；
- **谓词对象**：`{ "type": "entity", … }`、`{ "type": "any_of", … }`。

两种项编译成同一个 `Condition`，由同一套执行器判定。`when_predicates` 是**已移除的旧拼法**：主内容已于 2026-10-31 全部迁移到 `when`，装载器不再接受旧键；`ContentConditionSpellingTest` 防止它重新进入主内容。

### 3.2 DSL 语法

```
<主语> <谓词|运算符> [<值>]
```

数组里每一项都必须是真（AND）。没有括号、没有 `and` / `or`；要 OR/嵌套，用谓词对象（§3.6）。

```json
"when": ["actor == self", "self_stacks:白花之刻 >= 2"]
```

### 3.3 主语

| 主语 | 指的是 |
|---|---|
| **`self`** | 规则持有者（写这条规则的角色） |
| **`actor`** | 本次事件的发起者（可能是别人） |
| **`target`** | 本次事件的目标 |

`from_skill` / `from_skill_id` / `from_category` 不是主语，是关于“这次施放”的谓词，写在 `actor` 上最常用。

### 3.4 运算符与谓词

**比较**（对数值）：`==` `!=` `>` `>=` `<` `<=`

**状态 / 身份**：

| 写法 | 含义 |
|---|---|
| `X has_state <名字>` | 是否持有某状态（`冻结` / `触电` / `结界` / `秘技` …） |
| `X has_shield` | 是否有护盾（可加 `from_rule <id>` / `from_provider self\|actor\|target`） |
| `X is_slowed` | 是否处于减速（`SPEED` 上有负修饰，不是“净速度低于基础值”） |
| `X has_path <命途>` | 命途 |
| `X has_skill <技能槽>` | 是否拥有某槽位的技能 |
| `X has_weakness` | 是否有弱点击破 |
| `X is_ally` / `X is_other_ally` | 是否我方 / 是否除我以外的我方 |
| `X is_summon` | 是否是召唤物 |
| `state_ended` | 状态结束 |

**“这一次施放”的谓词**：

| 写法 | 含义 |
|---|---|
| `from_skill ULTRA` | 这次来自哪个技能槽（`COMMON` / `BPSKILL` / `ULTRA` / `TALENT`） |
| `from_skill_id <id>` | 这次来自哪个具体技能 |
| `from_category <类别>` | 按技能类别 |
| `damage_is_attack` | 这一发算不算一次攻击 |
| `damage_did_not_crit` | 这一发**没有暴击**（只在 `DAMAGE_SETTLED` 上，掷骰已发生）|
| `damage_is_additional` | 这一发是不是追加伤害 |
| `damage_has_no_cast` | 这一发没有施放来源 |
| `damage_element_is_self` | 这一发的属性 = 我自己的属性 |

⚠ `from_skill` / `from_skill_id` 只在那几个“携带施放”的事件上为真；挂到别的事件会在装载期报错。
⚠ `DAMAGE_SETTLED` 不沿伤害链；`KILL` 会沿链（击破/超击破致死的施放也算）。

### 3.5 数值变量

下表中的变量可以直接写在 DSL 的比较里（`hp_percent < 0.5`）。拼错的名字在装载期报错，不会“当假”。

| 变量 | 读什么 |
|---|---|
| `hp_percent` | 自己当前生命百分比 |
| `target_hp_percent` / `actor_hp_percent` | 目标 / 事件发起者的生命百分比 |
| `target_hp_percent_before` | 掉血【之前】的目标生命百分比；只在会掉血的事件上有意义 |
| `self_energy_percent` / `self_max_energy` | 自己的能量百分比 / 能量上限 |
| `hit_count` / `weakness_hit_count` / `target_weakness_count` | 命中数 / 弱点命中数 / 目标弱点数 |
| `target_debuff_count` / `target_dot_count` | 目标身上的负面数 / 持续伤害数 |
| `target_dots_from_self` | 目标身上由规则拥有者施加的持续伤害数 |
| `event_amount` | 触发事件自带的数值（如 `HP_CONSUMED` 的实际消耗） |
| `self_summon_count` / `target_summon_count` | 召唤物数量 |
| `enemy_count` / `ally_count` | 场上敌我数量 |
| `map_attacker_count` | 除装备者外、本场「开战」的队友数（地图攻击的发起者）|
| `last_elation_skill_id` | 自己上一次**施放完成的欢愉技**的槽位（0 = 还没施放过）；与 `from_skill_id` 比较可写「与上一次不同」|
| `self_cid` | 自己的角色 id（光锥里写「某角色装备时」的门）|
| `skill_point_max` / `skill_point` | 战技点上限 / 当前战技点 |
| `eidolon` | 星魂等级 |
| `from_skill_id` / `damage_skill_key` | 技能 id / key |
| `counted_laughs` | 【笑点】计数 |

**带参数的变量**（写法是 `前缀:参数`）：

| 前缀 | 例 |
|---|---|
| `self_attr:` | `self_attr:ATTACK >= 1000` |
| `self_stacks:` / `actor_stacks:` / `target_stacks:` | `self_stacks:白花之刻 >= 2` |
| `self_resource:` / `summon_resource:` | `self_resource:充能 >= 3` |
| `allies_with_path:` | 按命途数我方 |
| `enemy_state_count:` | `enemy_state_count:冻结 >= 1` |

⚠ `target_hp_percent_before` 只在会掉血的事件上有效；在别的事件上是 `NaN`。
⚠ `has_shield` 的 `from_provider` 问的是盾是谁给的，`from_rule` 问的是哪条规则造的。

### 3.6 谓词对象：OR、嵌套、具名复用

```json
"when": [
  "actor is_ally",
  { "type": "any_of", "terms": [
      { "type": "entity", "entity": "actor", "json": { "same_as": "self" } },
      { "type": "ref", "ref": "ally_shielded_by_wearer" } ] },
  { "type": "not", "term": { "type": "entity", "entity": "target", "json": { "has_shield": true } } }
]
```

| `type` | 字段 | 语义 |
|---|---|---|
| `all_of` / `any_of` | `terms` | 全部 / 任一成立 |
| `not` | `term` | 取反；对 party 条件，“没有这一方”仍然判否 |
| `entity` | `entity` + `json` | 实体谓词：`entity` 是 `self` / `actor` / `target`，`json` 是子谓词 → 值 |
| `event` | `json` | 这次施放的事实（`from_skill` / `from_skill_id` / `from_category` / `damage_is_attack`） |
| `ref` | `ref` | `game/data/predicates.json` 里的具名谓词 |

**实体子谓词**（`json` 的键）：

| 键 | 值 | 读什么 |
|---|---|---|
| `is_ally` / `is_enemy` | `true`/`false` | 阵营 |
| `is_dead` / `is_summon` / `has_shield` | `true`/`false` | 死亡 / 是忆灵 / 有护盾 |
| `shield_from` | `self`/`actor`/`target` | 盾是谁给的 |
| `hp` / `max_hp` / `energy` / `shield` / `level` / `cid` / `speed` | 数字（= ≥）或 `{"min"/"max"}` | 绝对量；两端都是闭区间 |
| `eidolon` | 同上 | 星魂数 |
| `skill_level` | `{"name": "SKILL", "min"?: n, "max"?: n}` | 某个槽位的等级 |
| `hp_pct` / `shield_pct` | 同上 | 百分比 |
| `state_turns` | `{"name": "充能", "min"?: n, "max"?: n}` | 剩余回合 |
| `camp` | `PLAYER` / `ENEMY` / `NEUTRAL`（或数组） | 阵营 |
| `summon_count` / `enemy_count` | 数字或 `{"min"/"max"}` | 该单位在场召唤物数 / 敌人存活数 |
| `resource` | `{"name": "追忆", "min"?: n, "max"?: n}` | 该单位的计数器 |
| `energy_pct` | 同上 | 能量百分比 |
| `debuff_count` | 同上 | 负面效果数 |
| `same_as` | `self` / `actor` / `target` / `summon` | 同一性 |
| `team` | `own` / `other` | 相对规则主人的阵营 |
| `attribute` | `{"name": "SPEED", "min"?: n, "max"?: n}` | 任意属性 |
| `stacks` | `{"name": "充能", "min"?: n, "max"?: n}` | 层数 |
| `state` / `path` / `element` | 字符串或字符串数组（数组 = 任一） | 状态 / 命途 / 属性 |

⚠ 缺主语就判否：`BATTLE_START` 没有 target，`{"entity":"target", …}` 一律失败；`not` 不会把它翻成通过。
⚠ 装载期拒绝：未知 `type`、空 `json`、未知子谓词、值形状不对、未知属性/阵营/槽位、空 `terms`、缺 `term`、未声明的 `ref`、具名谓词成环。
⚠ 谓词是**判定**（是否执行），不是**数值**（用哪个数）；后者用 §5.3 的数值来源。

### 3.7 `target_when`

效果级的 `target_when` 筛选受术者（“生命值 ≥50% 的那些”），是本效果的局部条件，不改变整条规则的门。

---

## 4. 事件（共 41 个）

| 分类 | 事件 |
|---|---|
| 战斗流程 | `BATTLE_READY` `BATTLE_START` `WAVE_START` `TURN_START` `TURN_END` `EXTRA_TURN_STARTED` `EXTRA_TURN_ENDED` `COUNTDOWN_TURN` |
| 施放 | `CAST_SETUP` `SKILL_CAST` `BASIC_ATTACK` `ULT_CAST` `INSERTED_CAST_END` `ATTACK_FINISHED` |
| 攻击与受击 | `ALLY_ATTACK` `ENEMY_ATTACK` `SUMMON_ATTACK` `TAKING_HIT` `DEALING_DAMAGE` `DAMAGE_SETTLED` `FOLLOW_UP` `CRIT_DEALT` |
| 伤害与生命 | `HP_LOST` `HP_CONSUMED` `LETHAL_DAMAGE` `HEALED` `SHIELD_GRANTED` `DEATH` `KILL` |
| 击破与控制 | `BREAK` `WEAKNESS_ADDED` `DEBUFF_APPLIED` `STATE_ENDED` |
| 资源 | `ENERGY_GAINED` `SKILL_POINT_GAINED` `SKILL_POINT_SPENT` `SKILL_POINT_OVERFLOWED` `RESOURCE_CHANGED` |
| 召唤 | `SUMMONED` `SUMMON_LEFT` |
| 入场 | `ENEMY_ENTERED`（敌方单位**开战后**加入名册时；`actor` = 新来的；开战前摆好的名册不算）|

⚠ `SUMMON_LEFT` 是“忆灵离场”，不等于“死亡”；那种消失 `DEATH` 收不到，只有它收得到。收到它的是还活着的规则持有者，所以条件写 `actor is_summon`，不要用 `target == summon`。

### 4.1 一次施放的时序（选错事件常常没有报错）

| 顺序 | 事件 | 相对什么 | 关键事实 |
|---|---|---|---|
| 1 | `CAST_SETUP` | 命中结算之前 | 带 `from_skill_id`；要“施放时给这一发加伤”，挂这里 |
| 2 | `DEALING_DAMAGE` | 在 assemble 内、各 zone 之前 | 可以改写这一发 |
| 3 | `TAKING_HIT` | HP 已经扣完之后 | 受害者是 `target`，出手方是 `actor` |
| 4 | `HP_LOST` | 同上，仅当实际掉血 > 0 | 实际掉血量在事件的 amount |
| 5 | `DAMAGE_SETTLED` | 结算完毕 | 追加伤害/真伤骑手读它 |
| 6 | `DEATH` / `KILL` | 目标阵亡时 | `KILL` 带击杀者当 `actor` |
| 7 | `ULT_CAST` / `SKILL_CAST` / `BASIC_ATTACK` | 所有命中都结算完之后 | 带 `hits` 列表与命中数 |

⚠ `AFTERMATH` 一类（`ATTACK_FINISHED` / `ALLY_ATTACK`）在全部命中之后，适合“这一发打完了再付”。
⚠ `BATTLE_START` 的效果在任何回合开始之前结算；前端要单独处理这一段。
⚠ `HP_CONSUMED` 由 `CONSUME_HP` 自己发；它目前不是可靠入口，写之前先做探针。

---

## 5. 效果

### 5.1 引擎能执行的 op（wired 闭集）

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

- `REDUCE_TOUGHNESS` 是**声明了但还没实现**的 op；写它会装载期报错并说明原因。
- `REPLACE_SKILL` 有两个方向：**装上**（`skill` + `skill_id` + 一个寿命）与 **`"remove": true`**（把该槽位的替换取下、恢复原来的行；不带 `skill_id`、不带寿命）。重复装上**同一**替换是 no-op（否则第二个会把强化行记成「原始」，卸下时反而装回去）。读者：1111 卢卡「≥2 层时强化 / <2 层时恢复」
- **每个 op 的参数以 `TriggerInterpreter` 的类注释为准**，本文不复制。

### 5.2 目标（`target`）

| 分类 | 选择器 |
|---|---|
| 自己人 | `self` `target` `attacker` `actor` `all_allies` `party` `other_allies` `next_ally` `party_first` `lowest_hp_ally` `random_ally_below_half_energy` `other_map_attackers` `highest_break_effect_ally` `allies_with_summons` |
| 敌人 | `all_enemies` `random_enemy` `random_hit_enemy` `highest_hp_enemy` `target_else_random_enemy` |
| 召唤物 | `summon` `all_summons` `target_and_summon` |
| 按标记找人 | `holder_of:<状态>` |
| 按命途 / id | `allies_of_path:<命途>` `allies_of_path_except:<命途>:<名单>` `ally_cid:<id>` |
| 按波次 | `wave_monster` `wave_monsters` |
| 特殊 | `all_allies_lethally_hit_this_action` `highest_hp_attack_hit` |
| 规则抽签 | `pick`（引用本规则 `pick` 字段解析出的那一个单位）|

⚠ `other_map_attackers` 是**除装备者外**、本场之前用**地图攻击**开战的队友（`Battle.declareMapAttack` 的记录）；没有就是空集。`highest_break_effect_ally` 是**除装备者外**击破特攻最高的存活队友（并列取名册顺序）；没有别的队友就是空集（不报错）。`allies_with_summons` 是**拥有至少一个忆灵**的存活队友（读者：23051）。

⚠ 选择器是闭集；拼错的 `target` 装载期拒绝（曾经会静默当成 `self`，那是没有症状的错误）。

### 5.3 数值

#### 5.3.1 旧三件套：`scale` + `percent` / `amount`

```
数值 = percent × <scale 读出来的数> + amount
```

| `scale` | 读的是 |
|---|---|
| `self_attr:<属性>` | 持有者的属性（`self_attr:ATTACK` 最常见） |
| 裸属性名 | 等价于 `self_attr:<属性>` |
| `owner_attack` / `owner_def` / `owner_max_hp` | 持有者的攻击 / 防御 / 生命上限 |
| `target_max_hp` / `target_current_hp` / `target_lost_hp` | 目标的 |
| `actor_max_hp` / `actor_attr:<属性>` | 事件发起者的 |
| `self_stacks:<名字>` / `target_stacks:<名字>` | 层数 |
| `party_resource:<名字>` | 全队计数器 |
| `self_resource:<名字>` | 持有者自己的计数器 |
| `self_max_energy` / `owner_max_energy` | 能量上限 |
| `elation_base` | 欢愉体系的基础值 |
| `original_damage` | 触发这一发本身的伤害 |
| `settled_damage_this_event` | 本次事件已结算的总伤害 |
| `cast_applied:<状态>` | 本次施放实际施加了该状态的次数 |
| `cast_skill_param:<n>` / `skill_param:<n>` | 技能参数表的第 n 项 |
| `self_attr_above:<阈值>` | 属性超出阈值的那部分 |
| `steps:<n>` | 按步长分段 |

⚠ **量纲**：`HEAL` 的 `percent: 0.04` 是 4%；`MODIFY_ATTR` 改 ratio 属性时优先写 **`ratio`**（`ratio: 5` = +500%），`percent` 是旧拼法。
`ratio` 只允许 `MODIFY_ATTR` + ratio 属性（`CRIT_CHANCE` / `DAMAGE_BOOST` / `AGGRO_ADDED_RATIO` …），与 `percent` / `value` / `amount` 互斥；平属性写 `ratio` 会在装载期拒绝。
新内容也可以统一用 §5.3.2 的 `value`。

#### 5.3.2 一个表达式：`value`（优先）

`value` 把整条数值写成一个**数值提供器**，求出来的数**已经是属性自己的单位**：

```json
"value": {
  "type": "add",
  "inputs": [
    { "type": "mul", "inputs": [
        { "type": "attr", "attribute": "DEFENCE", "whose": "owner" },
        { "type": "skill_param", "skill": "SKILL", "index": 0 } ] },
    { "type": "skill_param", "skill": "SKILL", "index": 3 } ]
}
```

（1001 的护盾：DEFENCE × 战技 #1 + 战技 #4。）

- `value` 与 `scale` / `percent` / `amount` / `percent_from` / `amount_from` / 所有 `*_from_*` / `skill` 行字段 / `spendAll` / `overflowOnly` **互斥**；同时写装载期报错。
- **量纲按 op 自己的单位**：magnitude 类 op 的 `value` 是绝对值（属性自己的单位）；share 类 op 的 `value` 是**份额本身**（`0.1` = 10%）。
- 支持的范围：
  - **magnitude**：`DAMAGE` / `ADD_DAMAGE` / `HEAL` / `SHIELD` / `MODIFY_ATTR` / `APPLY_DOT` / `APPLY_CONTROL`（每回合伤害）/ `GAIN_ENERGY` / `GAIN_RESOURCE` / `SPEND_RESOURCE` / `REMOVE_RESOURCE` / `CONSUME_HP`；
  - **share**：`MODIFY_DAMAGE_TAKEN` / `BOOST_DAMAGE` / `ADVANCE` / `BOOST_TOUGHNESS` / `SUPER_BREAK` / `TICK_DOT` / `RAISE_ENERGY_TO` / `ABSORB_DAMAGE` / `RESIST_DEBUFF`（字面 `value` 必须在 `(0, 1]`，否则装载期拒绝）；
  - **count**：`GAIN_SKILL_POINT` / `ADD_STACK` / `REMOVE_STACK` / `REMOVE_BUFF` / `DISPEL` / `RAISE_RESOURCE_CAP` / `RAISE_SKILL_HITS` / `RAISE_SKILL_POINT_CAP`（求值后四舍五入成整数）。
- `GAIN_ENERGY` 的 `value` 仍受 `fixed` 控制：写 `fixed: true` 就是固定恢复，不写就走能量恢复效率。
- 不读数值的 op（`MODIFY_RULE` 等）拒绝 `value`，不会静默忽略。
- ratio 属性写 `{"type":"const","value":5}` 就是 +500%；速度写 `50` 就是 +50 点。

#### 5.3.3 数值提供器（`value` 和 `*_from` 的表）

`percent_from` / `amount_from` 以及 `value` 共用这张表：

| `type` | 字段 | 读什么 |
|---|---|---|
| `const` | `value` | 字面量 |
| `skill_param` | `skill` + `index` | 规则主人自己某个槽位、当前等级那一行的第 index 项 |
| `cast_param` | `index` | 产生这次事件的技能当前等级那一行 |
| `summon_param` | `slot` + `index` | 规则主人忆灵某个数据槽位 |
| `resource` | `resource`（+ `basis_points`） | 规则主人的资源 |
| `add` / `mul` / `min` / `max` / `avg` | `inputs` | 逐项求值后归约（`add` 空表=0、`mul` 空表=1；`min`/`max`/`avg` 空表拒绝） |
| `sub` / `div` | `left` + `right` | 二元运算 |
| `negate` / `abs` | `input` | 相反数 / 绝对值 |
| `attr` | `attribute`（+ `whose` / `above`） | 单位属性；`whose: target` 读事件目标，`above` 只算超出部分 |
| `stacks` | `buff`（+ `whose`） | 层数 |
| `original_damage` | — | 触发这一发本身的伤害 |
| `settled_damage_this_event` | — | 本次触发已结算的总伤害 |
| `elation_base` | — | 欢愉体系的基础值 |
| `cast_applied` | `state` | 本次施放实际施加了该状态的次数 |
| `party_resource` | `resource` | 战利品级的队伍计数器 |
| `energy` | `whose`（+ `relation`） | 能量条：`current`（默认）/ `max` |
| `skill_point` | — | 战技点 |
| `steps` | `counter` + `step` | 阶梯计数 `floor(值 / 步长)` |
| `uniform` | `min` + `max` | 区间内均匀随机 |
| `weighted_list` | `distribution: [{weight, value}]` | 按权重随机 |

⚠ 装载期拒绝：未知 `type`、缺必填字段、`uniform` 缺边界、`weighted_list` 没有正权重、`steps` 缺 counter/正 step、`energy` 的 `relation` 不是 `current`/`max`，以及与对应的旧拼法同时写。
⚠ `MODIFY_RULE` 改不了 provider 份额：`effect_percent` 只能加在字面 `percent` 上；provider 规则没有“被改大”的语义。

#### 5.3.4 技能等级与槽位

- `RAISE_SKILL_LEVEL` 的 `skill` 一次只改一个槽位；同额多槽位用 `skills`：
  ```json
  { "op": "RAISE_SKILL_LEVEL", "skills": ["COMMON", "SKILL", "ULTRA", "TALENT"], "amount": 9 }
  ```
  `skill` 与 `skills` 互斥；空表、重复槽位、未知槽位装载期拒绝。忆灵用 `memosprite: true` + `skill_id`。
- 文件级等级约定写成一条 `BATTLE_START` 规则，放在会读技能行的规则**之前**（同事件按文件顺序执行）。
- 一个数只有一种来源：字面量与 `*_from_*` 同时写会装载期拒绝。

### 5.4 持续时间与生命周期

| 字段 | 含义 |
|---|---|
| `turns` | 持续 N 回合 |
| `permanent` | 整场战斗，且不被回合边界递减 |
| `until` | 由事件结束（如 `next_attack`）；与 `turns` 二选一 |
| `ticks_on` | 时长按谁的回合递减：`self` = 持有者 |

⚠ `turns` / `permanent` / `until` 三选一；`until` 时不能写 `turns`。
⚠ 状态“一直不消失”常常是 `ticks_on` 指向了不会被推进的回合。

### 5.5 层数、概率与筛选

| 字段 | 含义 |
|---|---|
| `max_stacks` / `cap_amount` | 层数上限 / 数值上限 |
| `stackable` / `coexist` | 是否可叠加 / 是否可共存 |
| `instance` | 每次施加算独立实例 |
| `stacks` | 一次施加几层 |
| `per_stack` / `per_stack_live` | 每层的增量 / 是否实时跟随层数 |
| **`base_chance`** | 基础概率，会被效果抵抗削减；100% 不是保证 |
| `target_when` | 筛选受术者的条件 |

⚠ 不写 `base_chance` = 直接附着、不判定；写了就一定会被抵抗削减。引擎无法表达“100% 基础概率且仍可抵抗”。
⚠ `per_stack` 在 `MODIFY_ATTR` 上是每层增量，`self_stacks:` / `target_stacks:` 读的是层数。

### 5.6 常用字段

`attribute`（`MODIFY_ATTR` 改哪个属性）、`buff`（状态名，`REMOVE_STATE` 靠它点名）、`resource`（资源名）、
`control`（控制名，如 `冻结`）、`element`（这一发的属性）、`damage_type`（`DOT` / `BREAK` / `ADDITIONAL` / `ULTRA` …）、
`skill`（技能槽）、`damage_param`（参数表第几项）、`crit_rate` / `crit_damage`（固定暴击）、`times`（这个效果执行几次）、
`rule`（`MODIFY_RULE` 点名的规则 id）。

`per_steps` 是「按步长分段」的修饰量：`"<计数器>-<阈值>:<步长>"`（如 `"SPEED-100:10"`、`"max_energy-120:10"`），`per_steps_cap` 封顶段数；计数器可以是属性名、`max_energy`、自身/队伍计数器或战技点。N 段会折成一条 `percent × N` 的修饰（同属性同身份会互相替换，所以**不能**写成两条无名修饰再加）。⚠ `times` / `times_from` 重复的是**整个效果**，不只是伤害：`DAMAGE` 在自己的循环里逐次重抽目标，
其余 op 由解释器的效果循环重复（`EffectTimesTest`）。所以「每击中 1 个敌方目标获得 1 层」现在可以写成
`ADD_STACK` / `MODIFY_ATTR` + `times_from: "hit_count"`。`times: 0` 在装载期拒绝。

⚠ `element` 与 `damage_type` 是两个问题：前者是火/冰/雷，后者是 DoT/击破/追加；可以同时出现。
⚠ `scope_element` 只被 `MODIFY_DAMAGE_TAKEN` 读，是另一个字段，不是复用 `element`。
⚠ 实例路径的 `MODIFY_DAMAGE_TAKEN` 也收**负** `percent`：负 = 这一发**减伤**（进 Reduction 区），正 = 易伤（读者：光锥 24002）。
⚠ 易伤区（正 `percent`、非实例）可加 **`from_self: true`**：只对**规则拥有者造成**的伤害生效（读者：光锥 23025）；它与 `damage_type` / `scope_element` 叠加收窄。负份额不接受它（减伤区没有来源过滤，装载期拒绝）。

---

## 6. 校验：写错了会怎样

| 时机 | 行为 |
|---|---|
| 装载期 | 不认识的事件 / op / 条件变量 / `target` / `scale` / 属性 ⇒ 抛异常并列出合法值 |
| 装载期 | `schema` 不是 1 / `character` 与 cid 不符 / 未知注解键 / `when` 项既不是字符串也不是对象 ⇒ 报错 |
| 装载期 | `value` 与旧字段同时写、`value` 用在只读 share 的 op、provider 形状不对 ⇒ 报错 |
| 装载期 | `REDUCE_TOUGHNESS` ⇒ 报错并说明“前置能力未落地” |
| 装载期 | 条件变量拼错 ⇒ 报错（不再是“当假”） |

⚠ 设计目标是“没有症状的错误一个都不留”：**拒绝一条规则**远好于**接受它并静默算出错误的数**。

---

## 7. 完整例子（1001 三月七·战技护盾）

```json
{
  "schema": 1,
  "character": 1001,
  "rules": [
    {
      "on": "SKILL_CAST",
      "id": "skill_shield",
      "when": [
        { "type": "entity", "entity": "actor", "json": { "same_as": "self" } }
      ],
      "do": [
        {
          "op": "SHIELD",
          "value": {
            "type": "add",
            "inputs": [
              { "type": "mul", "inputs": [
                  { "type": "attr", "attribute": "DEFENCE", "whose": "owner" },
                  { "type": "skill_param", "skill": "SKILL", "index": 0 } ] },
              { "type": "skill_param", "skill": "SKILL", "index": 3 } ]
          },
          "turns": 3,
          "target": "target"
        }
      ],
      "source": "1001 三月七 战技 可爱即是正义 (100102): 「为指定我方单体提供能够抵消等同于三月七#1[i]%防御力+#4[i]伤害的护盾，持续#2[i]回合。」 param Lv10 = [0.57, 3, 0.3, 760, 5]",
      "_note": "① 「指定我方单体」= target: target；② 「#1% 防御力 + #4」= value 的 DEFENCE × SKILL:0 + SKILL:3；③ 「持续 #2 回合」= turns: 3。⚠ 未写：该目标生命 ≥30% 时的仇恨提高（见 skill_soft_aggro）。"
    }
  ]
}
```

| 句子 | 写法 |
|---|---|
| 「施放战技时」 | `on: SKILL_CAST` |
| 「指定我方单体」 | `target: "target"` |
| 「#1% 防御力 + #4」 | `value` 的 `attr DEFENCE × skill_param SKILL:0 + skill_param SKILL:3` |
| 「持续 #2 回合」 | `turns: 3` |
| 「由三月七施放」 | `when` 的 `actor same_as self` |
| 「生命 ≥30% 时仇恨提高」 | 另一条规则 `skill_soft_aggro`（同事件、同门） |

---

## 8. 常见错误

| 症状 | 原因 |
|---|---|
| 规则永不触发 | 条件变量拼错 / 事件选错 / `from_skill` 挂在不能携带施放的事件上 |
| 触发了但数字不对 | `scale` / `value` 读错了对象（`owner_` / `target_` / `actor_`） |
| 护盾或治疗不随等级动 | 只读了字面量，没让份额与常数都读技能行 |
| 「降到 50%」每次都触发 | 用了 `target_hp_percent`，要用 `target_hp_percent_before` |
| 规则偶尔不生效 | `base_chance` 被效果抵抗削了 |
| 状态一直不消失 | `ticks_on` 指向了不会被推进的回合 |
| 施放时加伤没赶上这一发 | 挂在 `SKILL_CAST`，应挂 `CAST_SETUP` 或 `DEALING_DAMAGE` |
| `value` 写了却没生效 | 该 op 只读 share，装载期应已拒绝；检查 op 是否在支持列表 |
| 效果从不出现在日志 | 事件发生在第一个回合开始之前（`BATTLE_START`），前端要单独处理 |

---

## 9. 相关文档

| 文件 | 内容 |
|---|---|
| `AGENT.md` | 引擎架构、红线、决策理由、已知缺口、踩坑记录 |
| `HSR.md` | 游戏侧公式与属性表 |
| `CONTENT-STATUS.md` | 逐角色/光锥/遗器的实现状态 |
| `README.md` | 项目入口 |



