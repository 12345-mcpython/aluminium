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

## §3 登记中的句族（三列都必须写清）

| 句族 | 缺什么 | 读者 | 前置 |
|---|---|---|---|
| 仓库技「获得该角色即生效，无需上场」 | 一个与队伍无关的被动装载路径（引擎只在角色**在队**时读它的表） | `1407`（1 位） | 被动装载点 |
| 「使【禳命】的持续回合数 −1」 | 「缩短时长」的拼写（`EXTEND_BUFF` 只收正数） | `1217`（1 位） | 一个减时长的 op |
| 「自动施放【弑神登神】」 | 数据 id 11 对应的技能槽（`SkillType` 没有它） | `1408`（1 位） | 槽位映射 |
| 「按数量／计数缩放」 | 一个"按数量缩放"的修饰 | `1305`／`23000`（2 位） | 引擎的数量维度 |
| 「施放瞬间作用域」 | 一个"只在本次施放内有效"的作用域 | `20001`／`23004`（2 位） | 实例级作用域 |
| 「欢愉度」属性 | `AttributeType` 里没有对应成员 | `1502`／`1513`（2 位） | 属性枚举成员 |
| 「按列位／段数结算」 | 按段位分次结算 | `1505`／`8009`／`8010`／`1502`／`1506`（5 位） | 段落维度 |
