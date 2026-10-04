# 「完全表达」判定清单 (EXPRESSION.md)

> **目的（目标 ⑥）**：文档里每一类句式，要么**能写出来**（有出货内容 ＋ 判据 ＋ 实测变异），
> 要么**登记**（缺什么／读者是谁／前置是什么）。**不允许含糊，也不允许近似。**
>
> ⚠ 本文件由 `src/test/java/com/laosun/aluminium/test/ExpressionChecklistTest.java` 守着：
> 它逐个检查本文件引用的**每个文件都真实存在**、§2 的每一行都点名了**存在的证据文件**、
> §3 的每一行的**三列都非空**。⚠ 所以这份清单不能靠印象写：改它就要能通过那道闸。

## §1 判定程序（遇到新句式，按这四步走）

1. **找到句子** —— 原文逐字抄下来；数值先查数据（`E:\turnbasedgamedata` 与 `src/main/resources/data/*.json`）。
   「文档没给数值」**≠**「数据里没有」。
2. **拆成从句** —— 一句常含多个从句。**只写能完整表达的那些**，其余逐条登记；⚠ 不写"差不多"的半句。
3. **试写** —— ⚠ 先**在树里找现成拼法**（下"这是新能力"的判断前，先打开同族文件看一眼；
   本项目已四次把"树里早就有的"当成新发现）。写出来就必须同时有**判据**与**实测变异**。
4. **写不出就登记** —— 三件事必须写清：**缺什么**、**读者是谁**（具名 ＋ 个数）、**前置是什么**。

## §2 可写的句族（每一行的证据文件都必须真实存在）

| 句族 | 拼法（引擎词汇） | 出货例／证据文件 | 判据 |
|---|---|---|---|
| 某个具名状态结束（解除或到期） | `STATE_ENDED`，名字**随事件走**（不能读持有者） | `src/main/resources/characters/1408.json` | `TransformationEndsOnLastCountdownTest` |
| 致命一击改为回血存活 | `LETHAL_DAMAGE` ＋ `HEAL` | `src/main/resources/characters/1217.json` | `TalismanSavesAnAllyTest` |
| 单场战斗 N 次 | `ADD_STACK` ＋ 条件 `self_stacks:<名> < N` | `src/main/resources/characters/1217.json` | `TalismanSavesAnAllyTest` |
| 触发 N 次后**或**持续 M 回合后解除 | 有 `turns` 的状态 ＋ `has_state` 当门 ＋ `REMOVE_STATE` | `src/main/resources/characters/1008.json` | `ArlanEidolonFourTest` |
| 暂时延后陷入无法战斗状态 | `defers_death: true`（引擎在该单位 `TURN_END` 提交死亡） | `src/main/resources/characters/1407.json` | `MooncocoonTest` |
| 最多叠 N 层（属性修饰） | `MODIFY_ATTR` ＋ `max_stacks` | `src/main/resources/relic_sets/313.json` | `SigoniaCritTest` |
| 层数／计数当条件 | `ADD_STACK` ＋ `*_stacks:<名>` | `src/main/resources/relic_sets/126.json` | `Relic126HelpTest` |
| 每超过阈值 1 点就加 X% | `scale: "self_attr_above:<属性>:<阈值>"`（**超额**，不是属性本身） | `src/test/java/com/laosun/aluminium/test/AboveThresholdTest.java` | `AboveThresholdTest` |
| 结界／领域（一个状态 ＋ 其上的修饰） | `APPLY_BUFF` ＋ `has_state` ＋ 带 `buff:` 的修饰 | `src/main/resources/characters/1415.json` | `ElysiumZoneTest` |
| 真实伤害（跳过全部区间） | `damage_type: "TRUE"` | `src/main/resources/characters/1415.json` | `TrueDamageJudgeTest` |
| 目标选择器族 | `random_enemy`／`lowest_hp_ally`／`party_first`／`next_ally`／`target_else_random_enemy` … | `src/main/resources/light_cones/21025.json` | `Cone21025NextAllyTest` |
| 召唤物／忆灵攻击与面板 | `SUMMON` ＋ `SUMMON_ATTACK` ＋ 面板派生 | `src/main/resources/characters/8007.json` | `MemospriteResourcePanelTest` |
| 由规则命令一次真实施放 | `CAST_SKILL{skill: <槽>}` | `src/main/resources/characters/1404.json` | `CastSkillTest` |
| 延长既有 buff（含 `kind: all`） | `EXTEND_BUFF` | `src/main/resources/characters/1209.json` | `ExtendAllBuffsTest` |
| 驱散／移除 | `DISPEL{amount}`／`REMOVE_STATE{buff}` | `src/main/resources/characters/1412.json` | `PeerageDispelsControlTest` |
| 插入的施放结束 | `INSERTED_CAST_END` | `src/main/resources/characters/1412.json` | `CoupDeMainTest` |
| 定时弱点（会过期） | `ADD_ELEMENTAL_WEAKNESS` ＋ `turns` | `src/main/resources/characters/1310.json` | `TimedWeaknessTest` |
| 事件词汇表本身 | `TriggerEvent`（未接的事件在装载期就报错） | `src/main/java/com/laosun/aluminium/enums/TriggerEvent.java` | `TriggerEventWiringTest` |
| 「获得 N 个**笑点**」（队伍级、无上限的共享计数） | 无上限资源的**既有拼法**：`max: 2147483647` ＋ `scope: "PARTY"`（⚠ 共享 ⇒ 任何在队角色都可加） | `src/main/resources/characters/1513.json` | `Character1513LaughterTest` |
| **「某个状态的持续回合数在**它自己**的回合开始时减 1」** | `APPLY_BUFF` ＋ `turns` ＋ **`ticks_on: "self"`**（谁的回合花掉时长 ✓） | `src/main/resources/characters/1217.json` | `HuohuoTalismanDurationTest` |
| **「消耗等同于…**当前**生命值 X% 的生命值」** | `CONSUME_HP` ＋ **`scale: "target_current_hp"`**（新增：**当前**生命值的份额 ✓；旧词汇只有 `owner_max_hp`／`target_max_hp`／`target_lost_hp` ✗） | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1404.json` | `MydeiBloodfeudSkillsTest` |
| **「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」** | `TURN_START` ＋ `self has_state 血仇` ＋ **`REPLACE_SKILL{skill: SKILL, skill_id: 9, turns: 1}`** ＋ `CONSUME_HP{scale: target_current_hp, percent: 0.35}` ＋ `CAST_SKILL{skill: SKILL}` | `src/main/resources/characters/1404.json` | `MydeiBloodfeudSkillsTest` |
| **「【血仇】状态期间充能达到 **150** 点时，立即获得 1 个额外回合并自动施放【弑神登神】」** | `SPEND_RESOURCE{150}` ＋ `EXTRA_TURN` ＋ **`REPLACE_SKILL{skill: SKILL, skill_id: 11, turns: 1}`** ＋ `CAST_SKILL{skill: SKILL, target: self}` | `src/main/resources/characters/1404.json` | `MydeiBloodfeudSkillsTest` |
| **「**未**处于【X】状态时…」**（否定一个状态条件） | **`!self has_state X`**（`!` 前缀 ✓，只允许否定 `PartyCondition` ✓，而 `has_state` 读的就是单位的增益 ✓） | `src/main/resources/characters/1404.json` | `MydeiBloodfeudSkillsTest` |
| **「施加的是**控制类**／**持续伤害类**负面状态」** | **`debuff_class:control`**／**`debuff_class:dot`**（刚落地的负面状态的**族** ✓；事实在咽喉点记录 ✓，而那里本来就读它算抗性 ✓） | `src/main/java/com/laosun/aluminium/Battle.java`、`src/main/java/com/laosun/aluminium/models/TriggerTable.java` | `DebuffClassConditionTest` |
| **「每个波次开始时…」** | `on: "WAVE_START"`（事件本就存在 ✓；⚠ 它不携带 actor／target ✗ ⇒ 条件只能问 `self`／资源 ✓） | `src/main/resources/characters/1309.json` | `RobinWaveEnergyTest` |
| **「获得该角色即生效，无需上场」的**装载点** | **`battle.registerWarehouseListener(character)`**（只被问、**不进队列、不被选中** ✓；为什么不能塞进 `characters`：那份名单就是队列的来源 ✓） | `src/main/java/com/laosun/aluminium/Battle.java` | `WarehouseListenerTest` |
| **队友的状态结束时取一部分**（「其中的 50% 转化为自身的…」） | `STATE_ENDED` ＋ **事件携带的量**（被结束状态的**实例数**）＋ `amountFromEvent` × `amountPercent` | `src/main/resources/characters/1505.json`、`src/main/resources/characters/1513.json` | `GiftCarriesTheLaughsTest` |
| **「变身结束时…」的三句**（白厄：全队速度 +15% ✓、获得 3 点【火种】✓、「进入战斗**或**变身结束时攻击力 +50%」） | `STATE_ENDED` ＋ `self state_ended 变身`；第三句还用 **`coexist: true`**（同一属性上的两条规则共存 ✓） | `src/main/resources/characters/1408.json`、`src/main/java/com/laosun/aluminium/models/buff/BuffManager.java` | `TransformationStatsTest` |
| **层数＝队级计数**（「将本次…计入该状态」） | `scale: "party_resource:<NAME>"`（战斗级计数当数值读） | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java` | `PartyResourceScaleTest` |
| **一次施加 N 个可叠加状态**（「层数＝某个数」的另一半） | `APPLY_BUFF` ＋ `stackable: true` ＋ **`max_stacks`** ＋ `amount`／`scale` | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1513.json` | `StackTimesTest` |
| **按层数实时缩放（平坦属性）**（「每拥有 1 层…提高 X%」的持续光环） | `MODIFY_ATTR` 上 `per_stack: self_stacks:<NAME>` ＋ **`per_stack_live: true`**（份额 = `percent × 当前层数`，**每次读取重算**） | `src/main/java/com/laosun/aluminium/models/DoubleValue.java`、`src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java` | `PerStackLiveTest` |
| **按层数实时缩放（比率／绝对值）**（同一族的派生写法） | `MODIFY_ATTR` 上 `scale: self_stacks:<NAME>` ＋ **`per_stack_live: true`**（绝对值 = `percent × 当前层数 + amount`） | `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1314.json` | `JadeLiveGoodsTest` |
| **带计数的状态**（“将 N 计入该状态”） | `APPLY_BUFF` 上的 `"stackable": true` ＋ `max_stacks`（同名实例累加，且仍会公告自己的结束） | `src/main/java/com/laosun/aluminium/models/buff/StackableStateBuff.java` | `AttachedBuffProbeTest` |
| 「**此次攻击／本次施放**」内的数值（施放瞬间作用域） | `"until": "cast_end"`（`Lifetime` 的既有取值 ✓，同族还有 `NEXT_ATTACK`／`NEXT_SKILL`／`NEXT_ULTIMATE` ✓） | `src/main/resources/light_cones/23004.json` | `Cone23004CastScopeTest` |

## §3 登记中的句族（三列都必须写清）

> ⚠ **读者数不得用关键词匹配生成**（本项目已有登记 ✗）。本轮实测到一个现成的反例：「仓库技」这个词在 **104 份文档里命中 99 份**
> —— 因为每页页尾都有「XX 有仓库技吗？」的问答模板 ⇒ ⭐ **命中数不是读者数** ✗。所以本节的每一个数字都必须来自**逐条读到**的句子或源码；
> 读不到的，就写"未逐条读 ⇒ 不计入门槛" ✓。

| 句族 | 缺什么 | 读者 | 前置 |
|---|---|---|---|
| 仓库技「**获得该角色即生效，无需上场**」 | ✅ `1506` 全部出货 ✓。❗ `1407` 月茇之庇：⭐ **订正——“延迟倒下”并不缺** ✓（`DeferredDeathBuff` ✓ ＋ `defers_death` ✓ ＋ `Battle:1305` 在回合重查 ✓）；结算那一半也写得出 ✓（`HEALED`／`SHIELD_GRANTED` ＋ `self has_state 月茇` ⇒ `REMOVE_STATE` ✓）。❗ **真正缺的是**「本次**行动**中**所有**受到致命攻击的我方角色」✗（最近的是 `ctx.attackHitTargets()` ✓ ＝“这一**次攻击**命中的目标池” ✗；而一次**行动**可含多次攻击 ✗） | `1407`（月茇之庇 ✓） **1 位**（`1506` 已出货 ✓） | 一个“**一次行动**中受到致命攻击的全体”选择器 ✓ |

| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） | ⭐⭐ **本轮测定：该技能根本无法被施放** ✗ —— ❗ 不是缺槽位 ✗，而是它**什么都不交付** ✗：技能 8 是「**辅助**」（非伤害 ✓），而 `skill_effects.json`（21 个 cid ✓）**里没有 1415** ✗，而 `CAST_SKILL` 的护栏**拒绝的恰恰就是**“什么都不做的被命令施放” ✓ ⇒ ⭐ **一个只触发规则、自身无交付的技能在当前模型里没有位置** ✗，因此它的「对万敌施放时」句子**没有触发点** ✗ | `1415`（1 位） | 一个**只触发规则**的技能能被承认（或给它一条 `skill_effects.json` 条目 ✓） |