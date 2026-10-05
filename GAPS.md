

- ✅ **已出货（2026-10-02，第 136 件）：长夜月战技的「**使我方全体忆灵的暴击伤害提高」**成句
  （`141302`「白昼悄然离去」）—— 它也是 `1141524` 第三句要“额外提高”的那个效果。
  ⭐ **新选择器 `all_summons`**（我方**全体**忆灵：遍历 `battle.characters` 各自的 `summonsOf`）。
  判据 `EvernightCritForSummonsTest`：`gained 0.07596 (her crit damage 0.633 x her skill's #1) ; without the skill 0.0`；
  变异（选择器只看主人）⇒ `0.0` ⇒ 红 ✓。
- ✅ **同时修掉一条真实的旧 bug**（本轮被这个判据撞出来）：
  `1413.json` 里的 `DELEGATE_DAMAGE{skill: "ULTRA"}` 挂在 `CAST_SETUP`（必须如此），但 `when` **只有** `actor == self`
  ⇒ 她施放**战技**时它也去交接终结技的挥击，引擎报
  *"DELEGATE_DAMAGE names ULTRA (slot 3) but the cast in progress is slot 2"*。
  ⭐ **意味着这条规则对她的战技从来没工作过**（一直抛异常）—— 因为**从没有判据铸过她的战技**。已补上 `from_skill_id == 3`。
- ⭐⭐ **两条引擎事实（第 91 轮）**：
  1. **`MODIFY_RULE` 只能挂 `BATTLE_START`**（它自己的报错原话：*"the op is validated onto BATTLE_START, which always has one"*）
     ⇒ **`1141524` 第三句还落不了**：诗是**战中**施放的，而修正案必须在开战前立案。⭐ 下一步：看 `amendRuleEffectPercent` 能不能安全地允许更晚立案。
  2. **`summon` = 主人自己的忆灵**（`Battle.summonOf`）⇒ 要说“**全体**”就必须自己遍历我方。

- ⛔ **第 92 轮：三句差一步，但那一步是引擎深处 ⇒ 回滚**（第 12 次）。两个新发现：
  1. ⭐⭐ **`MODIFY_RULE` 的事件守卫在 `TriggerInterpreter` 的 L881**，原文：
     `if (TriggerEvent.fromString(spec.getOn()) != TriggerEvent.BATTLE_START)`，报文是
     *"amends a rule for the whole battle, so it only makes sense on BATTLE_START (a bonus granted mid-battle would have to be taken back when whatever granted it ended, and nothing does
     that)"*。⭐ 而 **它自己给的理由对本句不成立**：诗是「整场生效」，没有要收回的东西。
     （我已把它放宽到也接受 `CAST_SETUP` 并跑通了校验一关，但因后面还有一关而整体回滚。）
  2. ⛔ **而真正的墙是 `TriggerTable.validateAmendments`**：报文
     *"MODIFY_RULE raises the value of rule \"her_skill_raises_our_memosprites_crit_damage\", but non…"*（截断）
     ⇒ **被修正的规则必须把它的份额写成字面 `percent`**，而我的目标规则用的是 `percent_from_skill_param`。
     ⭐ 意味着 `effect_percent` 目前**只能抬字面量**。
  - ⭐ **下一步的两个选择**：（a）让 `amendRuleEffectPercent` 也能抬一个**拼法份额**（比如给份额加一个常量）；
    （b）改成在施放那一刻就把**额外那份暴伤**直接加到忆灵身上（不动原规则）—— 但那会与原规则的修饰符同名叠代，需先量。
  - ⭐ 三句的其余两句已出货（第 82、89 轮），**只差这一句**。

- ✅ **已出货（2026-10-02，第 137 件）：`1141524` **第三句成句** ⇒ ⭐⭐ **该技能三句全部成句** ✓✓。
  ⭐ **新能力：修正案随拼法**（三处引擎改动）：
  ① `TriggerTable.requireAmendable` 接受**任何份额拼法**（不只 `percent`）；
  ② `TriggerInterpreter.amendedEffect` **不再对拼法位写 `percent`**；
  ③ `shareOf` 把修正案**加在拼法解出的份额上**（新助手 `amendmentDelta`）；④ 守卫放宽到也接受 `CAST_SETUP`。
  判据 `TimeOdeRaisesHerCritTest`：`gained 0.11394 with the ode ; 0.07596 without it (her CRIT DMG 0.633, her skill's #1 0.12)` ✓✓；
  变异（修正量改 0）⇒ 红 ✓。
  ⭐ 数学上正好：`0.11394 = 0.633 × 0.18`（`0.12 + 0.06`）、`0.07596 = 0.633 × 0.12`。
- ⭐⭐ **两条新事实（第 93 轮）**：
  1. **`amendedEffect` 的 `withPercent` 会覆盖拼法份额**：因为 `shareOf` **先读 `percent`** ⇒ 修正案若写成 `percent` 就会**抹掉规则自己的数**。
     ⭐ 所以修正案必须**随拼法走**。
  2. **`MODIFY_RULE` 的事件守卫在 `TriggerInterpreter` **L881****（不在 `TriggerTable`），而它自己给的理由
     只对“中途给了又要收回”的场合成立 ⇒ 「整场生效」的授予可以在施放时立案。
- ⚠ **流程教训**：本轮的庄园脚本在第 3 步退出 ⇒ 后面的**内容与判据都没写**（而引擎已改）。
  ⭐ **教训：脚本必须幂等**（已改成“已改过就跳过”后重跑成功）。

- ✅ **已出货（2026-10-02，第 138 件）：`1141521`（负世）**第一句成句**：
  「整场生效，对白厄施放后，**使白厄获得 `#8` 点【火种】**…」（`#8 = 6`）。
  ⭐ **白厄 = cid `1408`**（确定性地得到：`ExcelOutput/AvatarConfig.json` 里按**名字哈希**找，而不是靠能力文件的前后顺序猜）；
  `1408.json` 无损转 dict（15 → 17 条规则）并声明【火种】。
  判据 `WorldOdeFireSeedTest`：`after the ode 【火种】 is 6 with the ode ; 0 without it`；变异（门改成 20 号）⇒ 0 ⇒ 红 ✓。
- ⭐ **本轮同时把剩下两个技能的原话读到了**（需求 ① 完成）：
  - **`1141517`「献予「生死」之诗」**（`SkillCY05`，ParamList `[0, 0.0012, 2, 0, 0.0024, 2]`）：「整场生效，对**遐蝶**施放时，
    **【新蕊】可以溢出至 `#3%`**。**召唤死龙时会消耗所有溢出【新蕊】，每消耗 1% 溢出值，
    使本次召唤的死龙触发天赋【灼掠幽墟的晦翼】的技能效果时，造成的伤害倍率提高 `#2%`**，
    召唤时若场上敌方目标小于等于 `#6` 名，伤害倍率额外提高 `#5%`」（`#3 = 2`、`#2 = 0.0012`、`#6 = 2`、`#5 = 0.0024`）。
    ⭐ 它要的是：【新蕊】的**溢出量**（一个新资源）、以及**死龙**（遐蝶的召唤物）的天赋伤害倍率。
  - **`1141521`（负世）其余句**（ParamList `[0.08, 0.15, 5, 0.05, 12, 0.06, 0.36, 6, 4]`）：「…且在变身时获得【永续的燃烧】，
    变身时若【火种】大于 `#5` 点，每超出 1 点，卡厄斯兰那的暴击伤害提高 `#6%`，最多可提高 `#7%`…」（`#5 = 12`、`#6 = 0.06`、`#7 = 0.36`）
    ⭐ 它要的是：【永续的燃烧】（状态）、**变身**、**卡厄斯兰那**（白厄的变身形态）、【毁伤】、额外回合。
- ⭐ **一条方法论**：`AmazingBuff_<Name>` 与 cid 的配对**不能**靠“最近的名字”启发式（本轮试过，会串）；
  要用 `AvatarConfig.json` 的**名字哈希**。⭐ 已知：`_Phainon` = 1408（本轮确认）。

- ✅ **已出货（2026-10-02，第 139 件）：`1141521`（负世）**第二句成句**：
  「变身时若【火种】大于 `#5` 点，每超出 1 点，卡厄斯兰那的暴击伤害提高 `#6%`，最多可提高 `#7%`」
  （`#5 = 12`、`#6 = 0.06`、`#7 = 0.36`）。
  ⭐ **为何不需要新能力**：`#7 / #6 = 0.36 / 0.06 = 6` ⇒ “超出 12 点、每点 0.06、上限 0.36”**恰好是六步**
  ⇒ **六条阶梯规则**，各自由 `self_resource:火种 >= 13..18` 把门，上限天然成立。
  判据 `WorldOdeFireSeedCritTest`：`his own transformation grants +0.5 ; with seeds the total is +0.56 (12), +0.62 (13), +0.68 (15)`。
- ⭐⭐⭐ **本轮最有价值的一件：一次变异证明了判据看不见东西，然后修好并证明它看得见**：
  - 把第 1 步改成“永不触发”后，三次读数**同时平移一步**（0.5 / 0.56 / 0.62），而**只比差的判据依然绿**。
  - ⭐ 原因：**差值对“整体平移”盲目**，而“少一步”正是这个形状。
  - 修法：加一个 **`run(0)` 基线**（他自己变身的那份），每次读数改成 `读数 − 基线 == 步数 × 0.06`。
  - ⭐ **然后同一个变异打红了**：`at the threshold, nothing is over it ==> expected: <0.06> but was: <0.0>`。
- ⭐⭐ **三条新事实（第 95 轮）**：
  1. **同属性修饰符默认叠代** ⇒ 要让多条同时生效必须 `coexist: true`（出货先例：`trace_hero_true_colors_on_battle_start` 用 `coexist: true, max_stacks: 2`）；
  2. ⭐ **而 `coexist` 还要配 `max_stacks`**，否则层数被默认封顶（本轮先只加了 `coexist`，步数就卡住了）；
  3. **`stackScale` 读的是 buff 层数**（`self_stacks:` / `target_stacks:`），**不是资源**；而“以资源为条件”的 `self_resource:<NAME> >= N` 是已出货的。

- ✅ **已出货（2026-10-02，第 140 件）：`1141521`（负世）**第三句成句**：
  「…且在**变身时获得【永续的燃烧】**，…**持有【永续的燃烧】时，卡厄斯兰那的暴击率提高 `#1%`**」（`#1 = 0.08`）。
  ⭐ **一条规则两个效果、按原文顺序**：`ADD_STACK{永续的燃烧, permanent}` 然后 `MODIFY_ATTR{CRIT_CHANCE, 0.08, permanent}`。
  ⭐ 而“**持有它时**”对应的就是**从变身起**：两者都是 `permanent`，变身结束由倒计时管（已出货的 `last_countdown_turn_ends_the_transformation`）。
  判据 `WorldOdeEverburningTest`：`the state is 1.0 and CRIT RATE gained +0.08 ; before it the state is 0.0 and the gain is +0.0`；变异（改 0）⇒ 红 ✓。
  ⭐ 而且**一次绿**（本轮只用 3 次调用就到了提交门前）—— 因为它的形态与已出货的三条完全同类。

- ✅ **已出货（2026-10-02，第 141 件）：`1141521`（负世）**第四句成句**：
  「…**施放攻击后，造成 `#3` 次附加伤害，每次伤害对敌方随机单体造成等同于卡厄斯兰那 `#4%` 攻击力的火属性附加伤害**」
  （`#3 = 5`、`#4 = 0.05`）。
  ⭐ **为何用 `DAMAGE` 而不是 `ADD_DAMAGE`**（实测）：`ADD_DAMAGE` 自己**拒绝 `times`**，原话是
  *“「额外造成 #1 次附加伤害」counts INSTANCES, and this op adds ONE amount to the damage being settled”*；
  ⭐ 而 `DAMAGE` 的出货读者是 1003 姬子星魂 6：`times` + `target: random_enemy` + `element`。
  判据 `WorldOdeAddedFireHitsTest`：`one attack deals 1599.79 while holding the state ; 476.19 without it`。
- ⚠⚠ **本条的判据有一个已知缺口（必须记下来）**：
  - 变异（`times: 5` → `1`）**会移动读数**（1599.79 → 1394.25），但判据**依然绿**（它只说 `with > without`）。
  - ⭐ **为什么加不上一个可数的期望**：那个读数**混着他自己 kit 的附加伤害**（他的天赋同样挂在 `DAMAGE_SETTLED`），
    而**每一个用他 `ATTACK` 属性算的公式都与它不符**（实测：五次读到 1123.6，而 `5 × 5% × ATK × mitigation` 只给 103.95）。
  - ⭐ **下一步**：先**隔离**（关掉他 kit 的同事件规则，或改用一个只有这条规则的受控场景），再把“几次”变成可判。

- ⛔ **第 98 轮：回滚**（第 13 次）。目标是 `1141521` 的「在卡厄斯兰那的额外回合耗尽后**不会结束变身、
  刷新全部额外回合并获得 `#9`(4) 点【毁伤】**」。
- ⭐⭐⭐ **本轮量到的确切事实（有用）**：
  1. 引擎**自己公告 `COUNTDOWN_TURN` 的地方在 `Battle.java`（约 L1046）**，形式是
     `fireTriggersForAlly(TriggerEvent.COUNTDOWN_TURN, countdown, countdown, 0)` —— **是那个“发给我方”的广播器**，
     而不是 `fireTriggers`。⭐ 所以**手工发 `COUNTDOWN_TURN` 到不了白厄的表**（本轮两次探针：计数规则与刷新规则**都没发**）。
  2. **计数器会被声明的上限钳位**：`额外回合计数` 的 `max = 8`，所以手工加 8 再发一次**读到的还是 8**（没有 9）。
     ⭐ 正确做法：**从 7 开始**，让计数规则自己 +1 到 8，再过一回合才是“耗尽”。
  3. `BuffManager` 的**删除方法叫 `removeState`**（不是 `remove`）；⭐ 而注意 `beforeMove` 里也含子串 `remove`（`befo…remove` 笑话）—— 用属于属性名搜方法时会被骗。
  4. `变身` 是**有层数**的状态（读到 4）⇒ 判据要断言 `> 0`，不是 `== 1`。
  - ⭐ **下一步**：先把判据改成**走引擎的回合机制**（或直接调那个包住公告的方法），
    再把内容拿回来；内容本身已写过一遍、**加载通过**。

- ✅ **已出货（2026-10-02，第 142 件）：`1141521`（负世）**第五句成句**：
  「在卡厄斯兰那的额外回合耗尽后**不会结束变身、刷新全部额外回合并获得 `#9`(4) 点【毁伤】**」。
  ⭐ 三件事：① “不结束变身” = 给已出货的结束规则加**否定门** `!self has_state 永续的燃烧`；
  ② “刷新” = `SPEND_RESOURCE` + `spendAll` 清空计数器；③ “获得 4 点【毁伤】”。
  判据 `WorldOdeExtraTurnsRefreshTest`：`with: 变身 4.0, counter 0.0, 【毁伤】 4.0 ; without: 变身 0.0, counter 8.0, 【毁伤】 0.0` ✓✓；
  变异（去掉否定门）⇒ `变身 0.0` ⇒ 红 ✓。
- ⭐⭐⭐ **四条新事实（第 98–99 轮实测）**：
  1. **引擎公告 `COUNTDOWN_TURN` 的地方在 `Battle.beforeMove()`（约 L1046）**，用 `fireTriggersForAlly` 发给**倒计时主人的我方**；
     ⭐ **无主人的倒计时 ⇒ 谁都收不到**（本轮因此踩了两次：手工发不行，必须 `startCountdown(owner, …)`）。
  2. **出货判据的驱动原话**：`battle.currentMove = signal; battle.beforeMove();`（signal 来自 `battle.queue.snapshot()` 按 `getCanHit() == countdown` 过滤）。
  3. **计数器会被声明的上限钳位**：手工加 8 再发一次，**读到的还是 8** ⇒ 正确做法是从 7 开始。
  4. **`BuffManager` 的删除方法叫 `removeState`**；而 `beforeMove` 里含子串 `remove`（`befo…remove`）⇒ 按名字搜方法会被骗。
- ⭐⭐ **一处建模修正（被一条既有判据抓出来）**：【永续的燃烧】**由诗的落点授予**（原话里它是诗的一句），
  而第 96 轮把它挂在了**他自己的 `ULT_CAST`** 上 ⇒ 任何变身都会得到它，于是弄坏了已出货的
  `TransformationEndsOnLastCountdownTest`（它钉的是“**没见过诗**的角色：最后 1 个倒计时回合结束变身”）。
  ⭐ 改成两条：状态由 `CAST_SETUP` + `from_skill_id == 21` 授予；暴击率仍挂变身但**由状态把门** ⇒ 既有判据重新通过 ✓。

- ✅ **已出货（2026-10-02，第 143 件）：`1141521`（负世）**第六句成句** ⇒ ⭐⭐ **该技能六句全部成句** ✓✓：
  「…**额外回合开始时，卡厄斯兰那消耗等同于当前生命值 `#2%` 的生命值**」（`#2 = 0.15`）。
  ⭐ 实现：`CONSUME_HP{scale: "target_current_hp", percent: 0.15, target: "self"}`，门是 `COUNTDOWN_TURN` + `actor == countdown` + `self has_state 永续的燃烧`。
  判据 `WorldOdeExtraTurnCostsHpTest`：`with the state he lost 215.3844 of 1435.896 (15% would be 215.3844) ; without it he lost 0.0`；
  变异（`0.15` → `0.10`）⇒ `lost 143.59` ⇒ 红 ✓。
- ⭐⭐ **两条引擎事实（第 100 轮）**：
  1. ⚠ **`owner_current_hp` 通过校验但没有实现**（报文原话：*"Scale 'owner_current_hp' passed validation but has no implementation"*）
     ⇒ **校验集与实现集不一致** —— 一个真实的引擎瑕疵（已登记，未动它）；
  2. ⭐ **“消耗者自己的当前生命”的出货拼法就是 `target_current_hp`** —— 它的注释原话：
     *"⚠ `target` is the unit being resolved (for CONSUME_HP, the spender)"*。
- ⭐ **账：忆灵技能只剩 `17`（生死）一个**（它要【新蕊】溢出与死龙天赋倍率）。

- ⛔ **`1141517`（生死）—— 已登记，三块均未建模**（第 101 轮逐块量清）：
  原句（`SkillCY05`，ParamList `[0, 0.0012, 2, 0, 0.0024, 2]`）：「整场生效，对**遐蝶**施放时，
  **【新蕊】可以溢出至 `#3`(2)%**。**召唤死龙时会消耗所有溢出【新蕊】，每消耗 1% 溢出值，使本次召唤的死龙触发天赋
  【灼掠幽墟的晦翼】的技能效果时，造成的伤害倍率提高 `#2`(0.0012)**，召唤时若场上敌方目标小于等于 `#6`(2) 名，
  伤害倍率额外提高 `#5`(0.0024)」。
  - ⭐ **已量到的（可用）**：**遐蝶 = cid `1407`**；我们的 `1407.json` **已声明【新蕊】**
    （`max: 34000`、**`scope: PARTY`**、出处是 `Castorice_Passive_MaxCount` 的 `5.3125 × 队伍最高等级²` 公式）；
    而 `"overflow"` 是**已出货字段**（`1506` 隐藏分 `max: 60, overflow: 240`；`1408` 火种 `max: 12, overflow: 3`），**与 `max` 同单位**。
  - ⛔ **三块缺口**：① “**对遐蝶施放时**”才可溢出 —— `overflow` 是**声明**，没有**动态授予**的路；
    ② “**消耗所有溢出**” —— `SPEND_RESOURCE{spendAll}` 花的是**全部**，不是“只花溢出那一段”；
    ③ “**消耗量 → 某个天赋的伤害倍率**” —— 没有“把刚花掉的量当作一个加成”的词汇（`amount_from_previous` 只在**入账**路径上）。
  - ⭐ **而且第三块还要一个“**死龙**”与它的天赋【灼掠幽墟的晦翼】（本文件里并无它们）。
  - ⭐ **下一步（若要做）**：先在引擎里给 `overflow` 一个**动态**来源（一条能提高容量的效果），再给“只花溢出那一段”一个拼法，
    最后才是“消耗量 × 系数 → 天赋倍率”。三件都是**新能力**，不是内容能补的。

- ✅ **需求 ③ 补齐（2026-10-02，第 101 轮）**：发现 `data/skill_effects.json` 的 `11415` 条目里
  **一直缺着 `21`**（而 `21` 已经有**六句出货**）—— 而**没有条目就不可交付**（`SkillExecutor.canDeliver`）。已补上。
  - ⭐ 现在 `11415` 的条目：`3, 5, 13, 14, 15, 16, 18, 19, 20, **21**, 22, 23`。
  - ⭐ **`17` 故意不加**：它的句子尚未实现，加了反而会让它**可施放但什么都不做**—— 那正是本项目拒绝的失败模式。
- ⭐ **当前账（第 101 轮末）**：忆灵技能里 **能表达的句子全部出货**；
  整句成句的技能：`13`、`14`、`5`、`3`（第四句）、`22`（四句）、`19`（四句）、`24`（三句）、**`21`（六句）**；
  多句已落：`15`、`18`、`20`、`23`；**只剩 `17`（生死）**，而它的阻碍是**三个新能力**。

- ⛔ **第 102 轮：回滚**（第 14 次）。目标是 `17`（生死）**最基础的那一块**：给 `overflow` 一个**动态来源**。
- ⭐⭐ **已量到（可用的设计）**：`ResourceManager.register(id, max, initial, overflow)` **存在**，它做的就是
  `resource.setMaxOverflow(overflow)` —— `Resource` **一直有两层**，而溢出是**可写**的。
  ⇒ 新能力形状：**`RAISE_RESOURCE_CAP{resource, amount}`** ⇒ `resource.setMaxOverflow(原值 + amount)`（三处：闭集、校验、派发）。
  ⭐ 而内容侧已算出数字：【新蕊】在 `1407.json` 里是 `max: 34000` ⇒ `#3 = 2%` = **680**。
- ⛔ **两个卡点（都是我自己的手术事故）**：
  1. 校验的 `case` **必须放在** `case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {` **之前**；我把它插进了**那个块里**，于是“孤立的 case”。
  2. 然后两次**行手术**（插一行/删一行）都把花括号弄坏了 —— 第一次从一处变两处。
  - ⭐ **教训（本会话第三次）**：改 `case` 块**必须连整段一起重写**（像第 79 轮对份额检查、第 89 轮对 `gainResource` 梯子那样），
    **不能插一行**。而且改完**必须立即 `compileJava`**，不要等到跑判据才发现。
- ⭐ **下一步**：按上面的形状重做（三处改动全部用“读整段 → 整段重写”），并在插完后**先编译**。

- ✅ **已出货（2026-10-02，第 144 件）：`1141517`（生死）**第一句成句**：
  「整场生效，对遐蝶施放时，**【新蕊】可以溢出至 `#3`(2)%**」。
  ⭐ **新能力 `RAISE_RESOURCE_CAP{resource, amount}`**：把**溢出容量**抬高 amount（不动正常上限）。三处改动全部**整段重写** ⇒ **一次编译通过** ✓。
  内容：`1407.json`：`CAST_SETUP` + `target == self` + `from_skill_id == 17` ⇒ `RAISE_RESOURCE_CAP{新蕊, 680}`（2% of 34000）。
  判据 `LifeOdeNewBudOverflowTest`：`filled past the cap: with the ode 34680 ; without it 34000`；变异（680 → 340）⇒ `34340` ⇒ 红 ✓。
- ⭐⭐⭐ **三条新事实（第 103 轮）**：
  1. **`Resource` 一直有两层**（`max` 与 `overflow`），而 `setMaxOverflow` 是 public ⇒ “动态抬高溢出”只是小件；
  2. ⭐ **PARTY 资源要经队伍商店**：角色商店的 `get(id)` 返回 **null**，而**加值**也要 `battle.partyResource(id)` —— 第 77 轮那条事实的又一面；
  3. ⭐⭐ **`case` 块必须整段重写** —— 本轮照做，**一次编译通过**；而上一轮三次行手术全坏。
- ⭐ **`17` 的后两句仍登记**：① “消耗**所有溢出**”（`spendAll` 花的是全部）；
  ② “消耗量 → 死龙天赋【灼掠幹墟的晦翼】伤害倍率”（还需“死龙”与那条天赋）。

- ✅ **已出货（2026-10-02，第 145 件）：新能力 **`SPEND_RESOURCE{overflow_only: true}`**（只花**上限以上**那一段）
  **＋** `1141517`（生死）**第二句的消耗部分**：「召唤死龙时会消耗**所有溢出【新蕊】**」。
  判据 `OverflowOnlySpendTest`：`from 14 it leaves 10 ; from 12 it leaves 10`；变异（让新拼法失效）⇒ `leaves 14 / 12` ⇒ 红 ✓。
  ⭐ 内容：`1407.json`：`on: SUMMONED` + `actor == summon` ⇒ `SPEND_RESOURCE{新蕊, overflow_only: true}`。
- ⭐⭐⭐ **三条新事实（第 104 轮）**：
  1. ⭐ **内容里的键是 JSON 拼法** —— 我写了 Java 字段名 `overflowOnly`，而它的 `@SerializedName` 是 `overflow_only`
     ⇒ **加载器当场拒绝**（*"unknown key … known: […]"*），26 例红；
  2. `SPEND_RESOURCE` 的 `requireAmount` 在 `if (isCastParamScale(effect)) … else …` 里 ⇒ **一个自带尺寸的新拼法必须同样免检**（像 `spendAll`）；
  3. ⭐ `Resource` 用**逐字段 `@Getter`** ⇒ `getMax()` 与 `getMaxOverflow()` 都有，而 `ResourceManager` **没有** `max(String)` ⇒ 要 `get(id).getMax()`。
- ⭐ **仍登记**：① “召唤**死龙**时”的**触发**（我们的 `1407.json` 里并无死龙，规则暂挂在通用 `SUMMONED`）；
  ② “消耗量 → 死龙天赋【灼掠幹墟的晦翼】伤害倍率”。

- ⭐⭐⭐ **第 105 轮：`1141517` 最后半步的**路径完全钉死了**（全部实测）**：
  - ✅ **“死龙”已经建模**！`memosprites/1407.json` 写着 `name: 死龙•玻吕刻斯`、`servant_id: 11407`，
    并列了 8 个技能槽位（1,2,3,5,6,10,11,12）—— 那一块**已经在**。
  - ✅ **那条天赋**：TextMap 哈希 `11802310553938915223` = **灼掠幽墟的晦翼**，它就是死龙的
    **`Skill23`（`SkillID 1140712`）**，参数 `[0.2, 6, 0.03, 400]`（随等级）；而 `memosprites/1407.json` 的 `slot 12` ⇒ **等级 10**。
  - ✅ **“刚花掉的量”的机制也在**：`TriggerInterpreter` 的 `previousCredited` 就是**资源值的差**
    （L1005–1010：`creditedBefore/After` 读 `holder.getResources()`）⇒ **一次消耗的差是负值**，而 `amount_from_previous` 已经会读它。
- ⭐ **剩下三件（按依赖顺序）**：
  1. **死龙那条天赋的伤害规则**（内容可写）：从 `1140712` 的参数与它的 `SkillDesc` 来；
     ⭐ 而它必须是一条**可被 `MODIFY_RULE` 寻得到的规则**（否则“倍率提高”无处可加）；
  2. **消耗量 → 一个资源**（工程）：`GAIN_RESOURCE` + **带绝对值**的 `amount_from_previous`（现在对负值会取到负数）；
  3. **`MODIFY_RULE` 的 `effect_percent_from_resource`**（工程）：与已出货的 `percent_from_resource` 同形，
     让“每消耗 1% 溢出值”真正成为一个可算的倍率。
  - ⭐ 而第三句的另一半（“若敌方 ≤ `#6`(2) 名，倍率额外提高 `#5`(0.0024)”）只需一个 `enemy_count <= 2` 条件，
    而那个条件**已出货**（我们的 `talent_memosprite_damage_enemy_count_*` 就用它）。


## 附：待办表（2026-10-02 第 105 轮整理）

| 优先 | 待办 | 块 | 依赖 |
|---|---|---|---|
| 1 | 死龙天赋【灼掠幽墟的晦翼】的伤害规则（可被 `MODIFY_RULE` 寻得） | 内容 | `1140712` 参数 + `SkillDesc` |
| 2 | `amount_from_previous` 对负值取绝对值（消耗侧） | 引擎 | 已测：L1005–1010 |
| 3 | `MODIFY_RULE{effect_percent_from_resource}` | 引擎 | 同形已出货 `percent_from_resource` |
| 4 | `17` 第二句的倍率加成（上三件齐了就能落） | 内容 | 1–3 |
| 5 | `17`「召唤**死龙**时」的**触发**精度（现暂挂通用 `SUMMONED`） | 内容 | 需“哪个召唤物”的判断 |
| 6 | 收敛：`GAPS.md` 里其余“登记”项逐条过一遍，该销的销 | 文档 | — |

- ✅ **已出货（2026-10-02，第 146 件）：`1407` 亿灵死龙的天赋 **【灼掠幽墟的晦翼】成句**
  （`SkillID 1140712`，`Skill23`，等级 10）—— 它是 `1141517` 最后半步的**依赖 1**，而且是**可被 `MODIFY_RULE` 寻得**的那一条。
  ⭐ **拆成三条**（伤害、生命代价、治疗）：因为 `MODIFY_RULE{effect_percent}` 通过 `amendedEffect` 改**命名规则的每一个效果**，
  合并的话诗会连**生命代价**与**治疗**一起抬高。
  ⭐ 数值全部来自数据行：`#1 = 0.56`、`#2 = 6`、`#3 = 0.084`、`#4 = 1120`；口径 `owner_max_hp` = **遐蝶**的生命上限（规则主人是她）。
  判据 `DragonHollowWingTest`：`expected 6 x 56% x her max HP 1629.936 x mitigation 0.4358095341859407 = 2386.747940347329` 对上 `lost 2386.7479403473335`；
  变异（0.56 → 0.28）⇒ 红 ✓。⭐ **而 mitigation 是判据自己用一次 1000 伤害测出来的** —— 期望里没有一个磁数。
- ⭐⭐⭐ **三条新事实（第 106 轮）**：
  1. **遐蝶本身是 `Servant` `11407`**，而死龙是它的 **`Skill23` = `1140712`**（`AvatarServantConfig` / `AvatarServantSkillConfig`，80 行）；
  2. ⭐ **`MODIFY_RULE{effect_percent}` 改的是整条规则的每一个效果** ⇒ 一句里有多个数值时必须**拆成多条规则**；
  3. ⭐ **满血单位的治疗读数是 0**（第一版判据就是这么红的）⇒ 判据必须**先造伤**。
- ⭐ **待办表更新**：第 1 项（死龙天赋的伤害规则）**已完成** ✓；剩下 `amount_from_previous` 取绝对值、`effect_percent_from_resource`、
  然后是 `17` 第二句的倍率加成。

- ✅ **已出货（2026-10-02，第 147 件）：`amount_from_previous` **读出消耗的量值**（待办 2 完成）：
  ① `previousCredited` 现在取**绝对值**；② 那个“测量入账差”的块**原先只对 `GAIN_RESOURCE` 运行**，现已纳入 `SPEND_RESOURCE`。
  判据 `SpentAmountIsPositiveTest`：`after a spend of 7 the follower gained 7 ; after a gain of 7 it gained 7`；变异（保留符号）⇒ `gained 0` ⇒ 红 ✓。
- ⭐⭐ **两条新事实（第 107 轮）**：
  1. ⭐ **那个测量块只对 `GAIN_RESOURCE` 运行** ⇒ 消耗走 `else`、**从不设置 `previousCredited`**（第一版判据读到 0 就是这个）；
  2. ⭐ **满上限的资源入账为 0**（判据的对照侧因此读到 0）⇒ 造判据时资源要**有余额又有余地**。
- ⭐ **待办表**：第 2 项已完成 ✓；**剩最后一件引擎小件**（`MODIFY_RULE{effect_percent_from_resource}`）⇒ 然后 `17` 第二句的倍率加成就能落。

- ⛔ **第 108 轮：回滚**（第 15 次）。目标是待办 3（`MODIFY_RULE{effect_percent_from_resource}`）与它服务的句子。
- ⭐⭐ **能力已写完且编译通过（六处，下一轮可照此重做）**：
  1. `EffectSpec`：`@SerializedName("effect_percent_from_resource") private String effectPercentFromResource;` + copy；
  2. `modifyRule`：新增一支 `} else if (effect.getEffectPercentFromResource() != null) {`，内用
     `resourceAmount(ctx.battle(), owner, id) / 10000.0` 当增量；
  3. `requireOneAmendment`：候选数组加上它（否则它不算“一个修正”）；
  4. `MODIFY_RULE` 的**校验链**（`TriggerInterpreter`）：在 `effect_percent` 那一支后插入新支；
     ⚠ **而不能调 `requireResource`**（它查的是 `resource`，不是这个字段）；
  5. `TriggerTable.requireAmendable`（**在 `TriggerTable` 里，不是 `TriggerInterpreter`**）：同样加一支，
     要求被改规则**有 `percent`**；
  6. `MODIFY_RULE` 的**事件守卫**：放宽到也接受 `SUMMONED`（理由同「整场生效」）。
- ⛔ **而它服务的链条读数不动**：内容已写（`SUMMONED` + `actor == summon`，三个效果同一条规则：
  消耗（`overflow_only`）→ 捕获（`amount_from_previous` + `amount_percent: 12`）→ 用它给修正案定大小）。
  **而两侧读数完全相同**（`3099.8347865249507`）。下一步是**仪表化**：把捕获到的资源值与已立案的修正量打印出来。
- ⭐⭐ **一条新事实**：`previousCredited` 是**每条规则独立**的（源码自述 “this is per-rule”）⇒
  **消耗与捕获必须在同一条规则里**。
- ⭐ 而 `TriggerSpecs.rule(...)`（测试帮手）的重载里**没有给规则命 id 的那个**：一个被 `MODIFY_RULE`
  引用的规则必须在**同一张表**里有 id ⇒ 隔离判据要么手动构 `TriggerSpec`，要么改用内容文件。

- ✅ **已出货（2026-10-02，第 148 件）：`MODIFY_RULE{effect_percent_from_resource}`（待办 3 完成）**：
  修正案的**大小来自一个资源**（基点，除 10000）。⭐ **六处改动全部照上一轮的清单一次过** ⇒ **编译第一次就通过**。
  判据 `AmendmentFromResourceTest`：`the amended rule granted 710.892 with 8000 basis points ; 273.42 with none`（比值 **13/5**）；
  变异（不除 10000）⇒ 红 ✓。
- ⭐⭐ **两条新事实（第 109 轮）**：
  1. **`TriggerSpec` 是 POJO**（`@Getter` / `@NoArgsConstructor`，`private String id`，**无 setter**）⇒ 测试里给规则命 id 要用
     `TriggerSpecs.set(spec, "id", …)`（反射写入）；而 `TriggerSpecs.rule(…)` 的重载里没有带 id 的那个；
  2. ⭐ **判据要按属性百分比读**：`MODIFY_ATTR` 缩放的是**属性** ⇒ 两侧读数**之比**才是 `(0.5+0.8)/0.5`，
     而不是把 `0.5` / `1.3` 当绝对值（第一版判据就是这么红的）。
- ⭐ **待办表**：第 3 项完成 ✓；**只剩第 4 项**（`17` 第二句的倍率加成）—— 内容已写过一遍，差的是**仪表化**（打印捕获到的资源与已立案的修正量）；
  以及待办 5（触发精度）与 6（文档收敛）。

- ⛔ **第 110 轮：内容回滚（第 16 次），但两处引擎改进与一个新判据出货**。
- ⭐⭐⭐ **仪表化把真相说出来了**（判据打印三个数）：
  ```
  with the ode:  captured 0.0, 【新蕊】 filled 34680.0 -> left 34000.0
  without it:    captured 0.0, 【新蕊】 filled 34000.0 -> left 34000.0
  ```
  ⇒ **加宽工作** ✓（第一句）、**消耗也工作** ✓（34680 → 34000，正好花掉 680）—— 而 **捕获读 0**。
- ⭐⭐ **新判据 `OverflowOnlyFeedsTheCaptureTest`（出货）**：同两个效果在一条手制规则里，
  而资源是 **SELF** 型：`overflow_only: left 10, captured 4 ; stated amount: left 10, captured 4` ⇒ **链条在 SELF 资源上是通的**。
- ⭐⭐⭐ **断链边界（下一步的入口）**：**`overflow_only` 消耗 ＋ PARTY 资源** ⇒ 捕获读 0。
  已排除：① `resourceAmount` 不认 PARTY（它写的是 `角色商店 + 队伍商店`）；② 守卫不认消耗（已改成同时匹配**原始 op**）。
  ⇒ 下一步是在**消耗的派发块**（`case "SPEND_RESOURCE" -> { … }`，它自己就用 `resourceAmount` 量了 `before`）里看它把值发到了哪里。
- ⭐ **本轮保留的两处引擎改进**（无害且方向正确，新判据绿灯）：
  ① 量入账差改用 `resourceAmount`（能读两个商店）；② 守卫同时匹配原始 op。

- ⛔ **第 111 轮：回滚（第 17 次）**（我自己引入了一个回归：第 107 轮出货的 `SpentAmountIsPositiveTest` 变红）。
  但**仪表化把整条链的真相全查清了**：
- ⭐⭐⭐ **“重复触发”不是 bug，是两次到达**（`Battle.java`)：
  `private final List<CanHit> justSummoned = new ArrayList<>();`（L196）而 `fireSummoned()`（L1359）**逐个元素触发一次**；
  `summonMemosprite`、`summonServant`、`summon` 各自 `add` 一次（L3244/3289/3308）。而遐蝶**同时拥有忆灵与仆从**
  ⇒ 她的 `SUMMONED` 规则**每次到达触发一次**。**这正是待办 5**（句子说的是“召唤**死龙**时”，不是“任何到达”）。
- ⭐⭐ **`resourceAmount` 把两个商店相加**（L3505：`角色商店 + 队伍商店`）：它对“总量”是对的，
  对“**差量**”则错 —— 一个 SELF 资源会被算两遍（实测：捕获 4 读成 **8**）。差量要**二者取一**。
- ⭐⭐ **链条本身是好的**：删掉重复的消耗规则后，`[SPEND{overflow_only}, GAIN{amountFromPrevious}]`
  在 **PARTY** 资源上读到 `captured 1360` = **2 × 680**（一次到达一份）；而 `【新蕊】 34680 -> 34000`。
- ⭐ **回归教训**：把 `resourceAmount` 用到差量上会让 **第 107 轮的判据读到 `14`（7+7）** ⇒ “总量”与“差量”需要**不同的读法**。
- ⭐⭐⭐ **下一步的确切顺序**：
  1. **删掉 `life_ode_summon_spends_the_overflow`**（第 104 轮出货）—— 它会**抢先花掉**溢出，
     而 `previousCredited` 是**每条规则独立**的，于是链条自己那一步读到 0；
  2. 把差量改成**二者取一**（新增一个专用读法，**不碰 `resourceAmount`**）；
  3. 再把三个效果写成一条规则（消耗 → 捕获 → 定大小）。

- ✅ **已出货（2026-10-02，第 149 件）：`1141517`（生死）**第二句成句** ⇒ **`17` 全部成句**，
  而且忆灵技能的**非伤害部分全部收口**。句子：「召唤死龙时会消耗所有溢出【新蕊】，每消耗 1% 溢出值，
  使本次召唤的死龙触发天赋【灼掠幽墟的晦翼】的技能效果时，造成的伤害倍率提高 #2%」。
  判据 `LifeOdeRaisesTheDragonTalentTest`：`with the ode 7616.736904032734 ; without it 3099.8347865249507` ⇒ 比值 **2.4571428571428564**，
  而句子自己的算术说 **2.457142857142857**（十五位）；变异（`amount_percent` 12 → 6）⇒ 比值 1.7285714285714282 ⇒ 红 ✓。
- ⭐⭐ **三步落地（照第 111 轮的顺序）**：① 删掉抢先消耗的重复规则 `life_ode_summon_spends_the_overflow`；
  ② 差量改用**二者取一**的专用读法 `measureForTheDelta`（**不碰 `resourceAmount`**）；③ 三个效果写成一条规则。
- ⭐⭐ **两条新事实（第 112 轮）**：
  1. ⭐ **“总量”与“差量”需要不同的读法**：`resourceAmount` 相加两个商店（对总量对、对差量错）⇒
     新增 `measureForTheDelta`。⭐ 而**全量 0 证明没有回归**（第 107 轮的判据仍绿）；
  2. ⭐ **抢先消耗的重复规则会让链条读 0**（`previousCredited` 每条规则独立）。
- ⭐ **待办表**：第 4 项完成 ✓ ⇒ 只剩 **第 5 项**（“召唤**死龙**时”的触发精度：现在每次**到达**都会触发）与 **第 6 项**（文档收敛）。

- ✅ **已出货（2026-10-02，第 150 件）：`1141517`（生死）**第三句**也成句 ⇒ 三句齐；
  并且给 `11415` 补上了 **`17` 的交付条目** ⇒ `11415` 的 **13 个槽位全部有条目**（需求 ③ 对忆灵技能全部满足）。
  句子：「…召唤时**若场上敌方目标小于等于 #6(2) 名，伤害倍率额外提高 #5(0.0024)**」。
  判据 `LifeOdeRaisesTheDragonTalentTest`（两个方法）：`ratio 2.450924608819344`（句子 **2.4509246088193453**）与
  `ratio 1.0017441860465117`（句子 **1.0017441860465115**）——**两半都吻合到 15 位**；变异（`<= 2` → `>= 4`）⇒ 2 例红 ✓。
  ⭐ 而且连带更新了 `DragonHollowWingTest` 的期望（该场景只有 1 名敌人 ⇒ 多 0.0024）：`2396.976860091675`。
- ⭐⭐ **两条新事实（第 113 轮）**：
  1. ⭐ `enemy_count` 后面**必须带一个比较**：`1413.json` 里那条叫 `…_enemy_count_4` 的写的是 `>= 4`
     ⇒ **把他人的条件整条搬过来会把意思弄反**（本轮就那么错了一次）；
  2. ⭐ 同一事件上的多条修正案**按文件顺序立案**；而当一侧有某个加成、另一侧没有时，
     期望要**两侧都带上不变的那一项**（本轮的分母是 `#1 + #5` 而不是 `#1`）。
- ⭐ **待办表**：第 5 项（触发精度）与第 6 项（文档收敛）。
  ⭐ 而 `15`/↉`18`/↉`20`/↉`23` 这四个多句技能里仍有**已登记的未实现从句**。

- ⛔ **第 114 轮：回滚**（第 18 次）。目标是槽位 20（**诡计**）的首句：
  「整场生效，对赛飞儿施放时，使赛飞儿造成的伤害提高 `#1`(18)%」。
- ⭐⭐⭐ **一个重要发现（可能修正了一个旧误解）**：我按已出货的天空之诗写了
  `target: "ally_cid:1406"`（赛飞儿 = cid **1406**，从 `AvatarConfig` 的名字哈希查出，不是猜的）。
  读数：名指那位 `0.2 -> 0.504`（涨 **0.304**），另一位始终 `0.2`。
  ⛔ **但把我那条规则的 `percent` 从 0.18 改成 0.36，读数一动不动** ⇒
  **那个提升不是我这条规则造成的**，判据把功劳记在了错的规则上。
  ⇒ 按纪律回滚（“变异要咬”是硬标准）。
- ⭐ **下一步的入口**：`0.2` 是**每个角色都有**的基础值（两位都是）；而 0.304 到底是谁给的、
  为什么是 0.304 而不是 0.18，需要先**把我那条规则彻底关掉**（或换一个独立可观测的属性）再量一次。
  ⭐ **而当前这个属性（`ALL_DAMAGE_TYPE_BOOST`）不适合做这种判据**：它有别的写入者。
- ⭐ 而本轮另两个句子的阻碍也已量清：槽位 20 的后两句是“降低防御力”（引擎**没有这个词汇**）；
  槽位 15 的首句是“无视 `#2`(6)% 防御力”（同样缺词汇）；槽位 18 的“恢复 `#4`(1) 个战技点”与
  “立即行动”可能可写（`GAIN_SKILL_POINT` 与 `ADVANCE` 都已出货）—— 那是下一轮更好的入口。

- ⛔ **第 115 轮：回滚**（第 19 次）。目标是槽位 18（**理性**）的头两句：「为我方恢复 `#4`(1) 个战技点并使那刻夏立即行动」。
- ⭐⭐⭐ **而它已经出货过了**！全量跑出 **`ReasonOdePointAndActionTest`**：
  `「为我方恢复 #4 个战技点」-- and #4 is 1 at every level ==> expected: <4> but was: <5>`。
  ⇒ 我又写了一条同样的规则，多发了一点。**已回滚**。
- ⭐⭐ **教训（本轮最值钱的东西）**：**开工前要先查已有的判据**（`src/test/**/*Ode*`），
  而不是只看 `GAPS.md` 的登记清单—— **已出货的从句不在“待办”里**，而在**已有的绿灯判据**里。
- ⭐ **而本轮的探针仍有价值**（两个可复用的事实）：
  1. ⭐ 槽位 18 的规则**每次施放只触发一次**（用计数资源量过，不是推的）；
  2. ⭐ **`ADVANCE{percent: 1.0}` 就是“立即行动”**：实测 `154.63917525773195 -> 0.0`，**恰好归零**。
- ⭐ 而开工前应该先列一下**已有的忆灵技能判据清单**，看看哪些从句已经被判据盖住了。

- ⭐⭐⭐ **第 116 轮：真实覆盖图（从已有判据反推，而不是从“待办”正推）**：
  上一轮的教训落实了——把 `1415` 忆灵技能的**已有判据**列了一遍，得到真正的账：
  | 槽位 | 判据（已绿灯） |
  |---|---|
  | 13 创世 | `OdeOfGenesisTest`、`ActorAttrScaleTest`、`CastSkillParamScaleTest`、`PercentFromCastParamScaleTest`、`SummonAttrScaleTest` |
  | 14 浪漫 | `OdeOfRomanceTest`、`OdeOfRomanceEnergyTest`、`OdeOfRomanceStanceTest2` |
  | 15 门径 | `OdeOfPassageTest` |
  | 16 战争 | `OdeToStrifeBloodfeudTest`、`OdeToStrifeAdvanceTest`、`OdeToStrifeCritTest`、`OdeWaveRestrikeTest`、`DispelByClassTest` |
  | 17 生死 | `LifeOdeNewBudOverflowTest`、`LifeOdeRaisesTheDragonTalentTest`、`DragonHollowWingTest`、`OverflowOnlySpendTest`、`SpentAmountIsPositiveTest`、`AmendmentFromResourceTest` |
  | 18 理性 | `ReasonOdePointAndActionTest` |
  | 19 天空 | `SkyOdeStackTest`、`SkyOdeSpendTest`、`SkyOdeEnergyTest`、`SkyOdeHealingTotalTest` |
  | 20 诡计 | **`TrickeryOdeDamageTest`**（首句已出货！） |
  | 21 负世 | `WorldOdeFireSeedTest`、`WorldOdeFireSeedCritTest`、`WorldOdeEverburningTest`、`WorldOdeAddedFireHitsTest`、`WorldOdeExtraTurnsRefreshTest`、`WorldOdeExtraTurnCostsHpTest` |
  | 22 海洋 | `OceanOdeDamageTest`、`OceanOdeEnergyTest`、`OceanOdeTickDotTest` |
  | 23 律法 | `OdeToLawChargeTest`、`LawOdeCritDamageTest` |
  | 24 岁月 | `TimeOdeBoostTest`、`TimeOdeMemoryTest`、`TimeOdeRaisesHerCritTest`、`EvernightCritForSummonsTest` |
  - ⭐ 而**这就解释了第 114 轮的“变异不咬”**：`TrickeryOdeDamageTest` **已经在那里**，读数是它驱动的。
- ⭐⭐⭐ **而“对某角色”的正确惯用法也找到了**：`OdeOfPassageTest` 的头注说得明白——
  **规则写在那个角色自己的文件里**（带宝 = `1403.json`，赛飞儿 = `1406.json`，都是**规则数组**），并且 `when` 用 `target == self`。
  ⭐ 效果：第 114 轮我把规则写在 `1415.json` 里、用 `target: "ally_cid:1406"` 指名—— **那不是已出货的惯例**。
- ⭐⭐ **而“防御力”的词汇也在**（`AttributeType`）：`DEFENCE_PERCENT`（`defence_percent`）、`DEFENCE`（落自 `DefenceDelta`）、
  `DEFENCE_IGNORE`（`defence_ignore`）、`RESISTANCE_REDUCTION` ⇒ **槽位 20 的后两句“防御力降低 10%/6%”是表达得出来的**
  （`MODIFY_ATTR{attribute: DEFENCE_PERCENT, percent: 负值}`）；而否定条件（`!target has_state 老主顾`）也早已出货。
  ⭐ **下一步**：在 `1406.json` 里写两条（老主顾 −10%、其余 −6%），并用敌人的 `DEFENCE_PERCENT` 做判据。

- ✅ **已出货（2026-10-02，第 151 件）：槽位 20（**诡计**）的**后两句**成句 ⇒ **诡计三句齐**：
  「…并使**【老主顾】的防御力降低 #2(10)%**，**【老主顾】以外的敌方目标的防御力降低 #3(6)%**」。
  ⭐ 内容：**两条规则写在 `1406.json`（赛飞儿自己的文件）**，而“只对带【老主顾】的目标”用 **`target_when`**（脚本抄自 `1106.json`）。
  判据 `TrickeryOdeDefenceTest`：`the marked one's defence fell 0.1 ; the unmarked one's 0.0600000000000001`；
  变异（B 的 -6% → -12%）⇒ `unmarked 0.12` ⇒ **红** ✓（A 的 -10% → -20% 同样红）。
- ⭐⭐ **一条重要新事实（第 117 轮）**：⭐ **拿掉 `target_when` 不会改变读数**（变异没咬）——
  因为**同属性修正互相替换**（后落的赢），两条规则都命中时只剩一个值 ⇒
  **判据能测“数值”，而测不到“条件本身”**；要测条件，必须让两条规则命中的**属性不同**。
- ⭐⭐ **两条惯用法确认**：
  1. 「对某角色」⇒ 规则写在**该角色自己的文件**里，`when: ["target == self", "actor is_summon", "from_skill_id == N"]`；
  2. **逐目标条件** = `target_when: ["target has_state …"]`（脚本在 `1106.json`）。
- ⭐ 而 `Character` 取技能是 **`getSkills().get(SkillType.X)`**（**不是** `skillAt`，那是 `Summon` 的）。

- ✅ **已出货（2026-10-02，第 152 件）：槽位 15（**门径**）的**剩下半句**成句 ⇒ **门径两句齐**：
  「**缬宝施放追加攻击触发缬宝的结界的附加伤害时，会额外造成 #1(1) 次附加伤害**」。
  ⭐ 内容：`1403.json`（缬宝自己的文件）`on: ATTACK_FINISHED` + `self has_state 结界` + `self has_state 献予「门径」之诗`
  ⇒ **与 `ult_zone_additional_damage` 同形**（同事件、同口径 `owner_max_hp`、同参数引用 `ULTRA:2`）。
  判据 `PassageOdeExtraZoneHitTest`：`with 549.7804009943957 ; without 369.88504432678747`；一次结界伤害原值 138.311712 × 1.3 = 179.805
  对上差值 **179.8953566676082**（容差 1%）；变异（去掉门径的状态条件）⇒ **RED** ✓。
- ⭐⭐⭐ **三条新事实（第 118 轮）**：
  1. ⭐ **普攻的技能槽是 `SkillType.COMMON`**（**不是** `BASIC_ATTACK`）—— 成员是
     `COMMON, ELATION_EXTRA, ELATION_SKILL, MAZE, SKILL, SUMMON_SKILL, TALENT, TECHNIQUE, ULTRA`；
  2. ⭐ **`ATTACK_FINISHED`** 的注释写着 “settlement complete, **hit set frozen**” ⇒ 它是“被攻击目标”的正确挂点；
  3. ⭐ **【结界】自带 +30% 受伤**（`ult_zone_enemy_vulnerability`）⇒ 期望必须带上它，否则差值差 30%（实测比值 1.3006）。
- ⭐ **而“追加攻击”这一层没收窄**：`ATTACK_FINISHED` 上没有“这次攻击是追加攻击”的词汇 ⇒ **登记**。
  ⭐ 而判据的容差是 **1%**（实测残差 0.05%，来自等级行的取整）—— 1% 足以捕到“多一次/少一次”（它们移动 100%）。

- ✅ **已出货（2026-10-02，第 153 件）：槽位 18 的**【真知】从句**：「那刻夏在下一次施放普攻、战技时获得【真知】」。
  ⭐ 内容：`1405.json`（那刻夏自己的文件）`on: CAST_SETUP` + `target == self` + `actor is_summon` + `from_skill_id == 18`
  ⇒ `APPLY_BUFF{真知, until: next_attack}`。
  判据 `ReasonOdeTrueKnowledgeTest`：`cast at him: … the state is 1.0 ; cast elsewhere: … the state is 0.0`；变异（去掉 `target == self`）⇒ 别处读 **1.0** ⇒ 红 ✓。
- ⭐⭐⭐ **三条新事实（第 119 轮）**：
  1. ⭐ **`MODIFY_ATTR{damage_type}` 必须配 `instance: true`**（加载器原话：*“states \"damage_type\" without \"instance\": true；
     only an instance-scoped m…”*）⇒ 它只能做**逐次**修正（像 `1406.json` 的 `trace_followup_crit_damage`），
     **不能**做“持续抬高某一类伤害”；
  2. ⭐ **`until` 在内容里的取值是 `cast_end` 与 `next_attack`** ⇒ 「下一次施放普攻、战技时」= `next_attack`；
  3. ⭐ **`Path.ERUDITION` 已在**（`Map.entry("智识", ERUDITION)`）但条件词汇里**没有命途选择器** ⇒ 「所有智识命途角色」**仍不可表达**。
- ⭐ **而这个从句的两个数值各缺一件词汇**：智识命途攻击力 30% 需**命途选择器**；
  战技伤害 20% 需**“持续地抬某一类伤害”**（`damage_type` 只能逐次）—— 两者都已登记。


## 附：手术式清单——缺什么词汇，就解锁哪几句（2026-10-02 第 120 轮，收官）

本轮把“待办”与**已有判据反推出的真实覆盖图**对齐了（见第 116 轮）。以下是**真正还没落**的从句，以及它们各自等的那一件词汇：

| 缺的词汇 | 它会解锁 | 依据 |
|---|---|---|
| **命途选择器**（条件或目标） | 槽位 18「所有「智识」命途角色攻击力提高 30%」 | `Path.ERUDITION` **已在**（`Map.entry("智识", ERUDITION)`），只差一个能读它的条件名 |
| **“持续地抬高某一类伤害”** | 槽位 18「造成的**战技伤害**提高 20%」 | `damage_type` 只能配 `instance: true`（**逐次**），做不了持续 |
| **“这次攻击是追加攻击”** | 槽位 15的“缬宝施放**追加攻击**时”这一层收紧 | 现挂 `ATTACK_FINISHED`（任何攻击）；而 `FOLLOW_UP` 是事件、不是条件 |
| **“这个召唤物是哪一个”** | 槽位 17的“召唤**死龙**时”这一层收紧 | 现在**每次到达**都会触发（遐蝶同时有忆灵与仆从；`Battle.justSummoned` 是 `List`、`fireSummoned()` 逐个触发） |

⭐ 而下面这些**不是缺词汇，而是已经出货了**（当初的“待办”里误列了它们，以至第 114/115 轮重复实现过）：
69 槽位 20 首句（`TrickeryOdeDamageTest`）、槽位 15 首句（`OdeOfPassageTest`）、槽位 23 两句（`OdeToLawChargeTest`、`LawOdeCritDamageTest`）、
槽位 18 前两句（`ReasonOdePointAndActionTest`）。

⚠ ⭐ **而一条判据能测什么、测不到什么，也已量清**（第 117 轮）：
**同属性修正互相替换** ⇒ 拿掉 `target_when` 不改变读数；**要测条件，必须让两条规则命中不同属性**。

- ✅ **已出货（2026-10-02，新目标第 1 件）**：新能力 **`allies_of_path:<命途>`**（选择器族）支付了槽位 18 的
  「**所有「智识」命途角色攻击力提高 #3(30)%**」。
  ⭐ 五处改动：常量 `ALLIES_OF_PATH_PREFIX`；解析器新支；助手 `pathNamed`（**中文名与枚举名都接受**）；
  校验新支；以及 `Path` 的 import。
  判据 `ReasonOdeEruditionAttackTest`：`gains: 那刻夏 0.84 ; 景元 0.84 ; 缇宝 0.0 ; 昔涟 0.0`；
  变异（把「智识」换成「同谐」）⇒ `缇宝 0.84 ; 两位智识 0.0` ⇒ **RED** ✓。
- ⭐⭐⭐ **五条新事实（新目标第 1 轮）**：
  1. ⭐ `Path` 有 **`fromNameOrNull`**（含九个中文名，「智识」⇒ `ERUDITION`）与 `fromMt`；`Character.getPath()` 存在；
  2. ⭐⭐ **解析器里有两个 `ally_cid:` 分支**（**单数**走 `require(allyWithCid(...))`、**复数**走 `List.of(named)`）
     ⇒ 新族必须插进**复数**那一个（用 `List.of(named)` 辨认）；
  3. ⭐⭐ **按偏移插入必须在所有字符串替换之后定位**：我先改了更靠前的常量（文件变长）再用旧偏移插入，
     结果错位约 600 字符、编译死在一个孤立的反引号上；正确做法是**先换字符串、最后按行号插入**；
  4. ⭐ **`MODIFY_ATTR{ATTACK, percent}` 对不同角色移动相同的绝对量**（实测两位都是 +0.84）——
     它缩放的是**原始基数**，不是界面上那个经过等级/光锥叠加后的数值 ⇒ 判据应比「同一个绝对增量」，**而不是同一个百分比**；
  5. ⭐ 角色与命途：那刻夏 **1405**、景元 **1204**（智识）、缇宝 **1403**（同谐）、昔涟 **1415**（记忆）。
- ⭐ **而新目标剩下三件**：① 「持续地抬高某一类伤害」（解锁槽位 18 的战技伤害 20%）；
  ② 「这次攻击是追加攻击」（收紧槽位 15）；③ 「这个召唤物是哪一个」（收紧槽位 17）。

- ✅ **已出货（2026-10-02，新目标第 2 件）**：能力 ② "**持续地抬高某一类伤害**"，解锁槽位 18 的
  「…造成的**战技伤害提高 #2%**」。
  ⭐⭐ **而最重要的发现是：这件能力不需要新词汇** —— 当初登记的阻碍是「`damage_type` 只能逐次」，
  但正确的形状根本不用它：**用状态承载存续 ＋ 用 `from_skill SKILL` 把实例限定到战技 ＋ 用 `BOOST_DAMAGE` 改那一次**。
  ⭐ 引擎只改了**一行**：`boostDamage` 现在通过 `shareOf` 读份额（它本来就调 `requirePercent` —— 而那个校验**自第 83 轮起就接受 `percent_from_resource`** ——
  却在应用时只读 `getPercent()`，于是 `NullPointerException`：**校验认的词汇它不认**）。
  ⭐ 内容：按 `1413.json` 的捕获模式，在诗的施放那刻把 `#2` 存成基点
  （`GAIN_RESOURCE{resource, scale: "cast_skill_param:1", percent: 10000}`），再在 `DEALING_DAMAGE` 上读回。
  判据 `TrueKnowledgeSkillDamageTest`：战技比值 **1.3948**、普攻比值 **1.00111**；
  变异（去掉 `from_skill SKILL`）⇒ 普攻从 **575.36** 涨到 **801.63** ⇒ **RED** ✓。
- ⭐⭐⭐ **六条新事实（新目标第 2 轮）**：
  1. ⭐⭐ **`BOOST_DAMAGE` 校验用 `requirePercent`（认 `percent_from_resource`），应用却只读 `getPercent()`** ⇒
     "校验认的词汇它不认" ⇒ 读回时 `NullPointerException` ⇒ 已改为走 `shareOf`；
  2. ⭐ **忆灵技能的 `#2` 在等级行上是 0.56**（不是 1 级行的 0.20，捕获读数 5600 基点）—— 「文档没给数值」≠「数据里没有」；
  3. ⭐ **该加成是与既有加成"相加"的**：引擎报告的 `ALL_DAMAGE_TYPE_BOOST` 是 0.2，而反解出的基线是 **0.4184**
     ⇒ **它进的是哪个乘区仍未查明** ⇒ 如实登记，不写进期望；
  4. ⭐ **【真知】被下一次攻击消耗**（`until: next_attack`）⇒ 判据必须**每场只打一下**，否则第一次会把第二次的答案藏起来；
  5. ⭐ **拿"施放诗"对"不施放"作对照会引入 RNG 漂移**（施放会抽随机数）⇒ 对照必须**消耗同样的随机数**；
  6. ⭐ 而普攻那 0.111% 的移动**不是漂移也不是门漏**，而是**同一句的另一半**（智识 +30% 攻击力 ⇒ 实测 +0.84 / 757 = +0.111%）。
- ⭐ **新目标剩下两件**：③ "这次攻击是追加攻击"（收紧槽位 15）；④ "这个召唤物是哪一个"（收紧槽位 17）。

- ⛔ **新目标第 3 件（"这次攻击是追加攻击"）：已回滚，但边界已查清**（第 3 次回滚）。
- ⭐⭐⭐ **最重要的一条：那件词汇早就存在** —— `TriggerTable` 里就有
  **`DAMAGE_IS_ADDITIONAL = "damage_is_additional"`**（"the instance being settled is ADDITIONAL damage"），
  而 `Battle.java:2913-2915` 自述：*"**additional damage is the engine's one representation of a follow-up attack**"*
  ⇒ ⭐ 所以「追加攻击」在本引擎里的读法就是 **`DamageType.ADDITIONAL`**，登记时以为缺的那件词汇**不缺**。
- ⭐⭐ **而在实例事件上收紧时，量到了四件事**：
  1. ⭐ **目标必须是 `target`**：`highest_hp_attack_hit` 是**攻击的命中集**，实例事件上没有它
     （实测报错：*"Effect targets \"highest_hp_attack_hit\" but this event has no such party"*）；
  2. ⚠ **规则"看到附加伤害就再加一次伤害"会自触发**：*"Trigger recursion exceeded 8 levels while firing DEALING_DAMAGE"*；
  3. ⚠ **`once_per_attack` 挡不住它**（实测仍然递归）；
  4. ✅ **`damage_skill_key == 3` 挡得住**（结界那一下由终结技产生 ⇒ 键 = 3；本规则加的额外那一下不是 ⇒ 不会重入）。
     ⭐ 但用上它之后，额外那一下的**量级是 35.69**，而结界那一下是 **179.81**（比值 0.1985）——
     **它读的是哪一行参数没有查明**，所以**不写成出货**。
- ⭐ **因此这一处仍按旧形态保留**（`ATTACK_FINISHED` + 两个状态），
  ⭐ 而**下一步的入口**是：查清"规则在实例事件上产生的伤害，其 `percent_from_skill_param` 解析到哪一行"
  （⭐ 结界那一下是终结技产生的，而触发时**进行中的攻击是缇宝的普攻** —— 这很可能就是 0.1985 的来源）。

- ⛔ **新目标第 4 件（"这个召唤物是哪一个"）：已回滚 —— 而回滚的理由是它"不需要"**（第 4 次回滚）。
- ⭐⭐⭐ **实测把一件登记项**消除了**：`SummonFactory` 的两条路 `memosprite(master, spec)` 与 `servant(master, spec)` 对遐蝶**造出同一个单位**：
  ```
  path=memosprite created=死龙•玻吕刻斯 summons=1 memospriteOf=死龙•玻吕刻斯 createdIsMemospriteOf=true
  path=servant    created=死龙•玻吕刻斯 summons=1 memospriteOf=死龙•玻吕刻斯 createdIsMemospriteOf=true
  ```
  ⇒ 名字相同、场上只有一个召唤物、而 `Battle.memospriteOf(她)` **两次都指向它**。
- ⭐⭐ **因此**：「召唤**死龙**时」**不需要新词汇** —— 遐蝶只有一种到达（她的忆灵就是死龙），没有"别的召唤物"要排除。
- ⛔ **而第 111 轮那条解释被推翻**：那次"规则被触发两次、第二次的消耗差为 0"**不是两次到达**
  （实测 `summons=1`）。⇒ **新问题**：**一条 `SUMMONED` 规则为什么会对**一次**到达触发两次**？
  ⭐ 已有的线索：`Battle.justSummoned` 是 `List`、`fireSummoned()` 逐个触发、触发后清空
  ⇒ 所以重复**不在**那里；下一步要查的是"一次 `summonMemosprite` 会 `fireSummoned()` 几次"
  以及"规则本身是否按目标/效果重入"。
- ⭐ **本轮新增的条件 `actor_is_memosprite` 也已回滚**：它能编译、也能求值，但**量不出可归因的差别**
  （两条路都一样）⇒ 按纪律"没有可归因效果的词汇不算出货"。
- ⭐ **一条可复用的实测**：`Battle.memospriteOf(CanHit)` 的语义是"**这个主人的忆灵**"，
  而 `SummonFactory.servant(...)` 对同一个 spec 造出来的**也是它** —— 所以该方法是"主人的忆灵"，不是"某种到达"。

- ⛔ **新目标第 5 件：再次回滚（第 5 次）—— 而这次的结论是**那件能力从来不需要**，且我新加的条件是冗余的**。
- ⭐⭐⭐ **实测（决定性）**：把遐蝶的五条 `SUMMONED` 规则**全部**换回旧的 `actor == summon`，
  判据里"**别人**的召唤物到达"那一侧**仍然是精确的 0.0**：
  ```
  MUTANT (all five back to `actor == summon`) -> green
  [dragon_arrival] the enemy lost 0.0 when 昔涟's sprite arrived, and 7630.021910260697 when her own dragon did
  ```
  ⇒ ⭐⭐ **`actor == summon` 本来就表示"规则主人**自己**的召唤物"**，不是"任何一个召唤物"。
  ⇒ 所以「召唤**死龙**时」**从一开始就不需要收紧**，而我本轮加的 `actor_is_my_summon`（常量 ＋ 解析支 ＋ 条件类）**与旧拼法不可区分** ⇒ 按纪律回滚。
- ⭐⭐ **两次探针（同一轮内）把链条量清了**：
  ```
  [probe-summoned] fired for actor=死龙•玻吕刻斯 target=null amount=0.0            ← fireTriggers(SUMMONED) 只调用一次
  [probe-apply]    a 新蕊 rule is being applied: owner=Castorice actor=死龙 target=null effects=3   ← 链条规则只应用一次
  ```
  ⇒ ⭐ **第 111 轮登记的"对一次到达触发两次"在当前代码上不可复现** ⇒ 它很可能是**那一轮我自己的守卫实验**造成的假象
  （那轮我反复切换 `moved > 0` 守卫）⇒ 该项**重新归类**为"已消除"，而不是"待查"。
- ⭐ **保留的两条可复用事实**：`Battle.summonsOf(CanHit)` 的签名是 `public List<Summon> summonsOf(CanHit master)`；
  而 `SummonFactory.servant(master, spec)` 与 `memosprite(master, spec)` 对同一 spec 造出**同一个单位**（同名、`memospriteOf` 都指向它）。
- ⭐ **于是目标里四件的最终状态**：① ✅ 出货；② ✅ 出货；③ ⏳ **词汇已在**（`damage_is_additional`），卡在自触发守卫与量级来源；
  ④ ✅ **已消除**（不需要）。

- ⛔ **新目标第 6 件（能力 ③）：已回滚**（第 6 次）。而这一轮把它的边界量到了**很窄的一条**。
- ⭐⭐⭐ **计数探针证明：基础值本来就一样**。在 `DAMAGE` 的份额分支里打印：
  ```
  [probe-dmg] attacker=Tribbie maxHp=1152.5976 share=0.12 flat=0.0 victim=冰锋 outcome=138.311712
  [probe-dmg] attacker=Tribbie maxHp=1152.5976 share=0.12 flat=0.0 victim=冰锋 outcome=138.311712
  ```
  ⇒ 结界那一下与本规则加的那一下，**基础值完全相同**（138.311712）⇒ **量级差不在基础值**。
- ⭐⭐ **也不是血量钳位**：把敌人换成 **25377 血**（`create(MONSTER, 100, 1)`）后，
  判据里的差值**仍是 35.62**（而结界那一下是 179.81）。
- ⭐⭐ **`DAMAGE` 的结算分两支**（`TriggerInterpreter` L4971）：**`ordinary: true`** ⇒ `applyDamage(..., NORMAL)`（⭐ 正戏那一支 ✓）；
  **缺省** ⇒ `applyAdditionalDamage`。而结界那条规则**没有** `ordinary` ⇒ 两支规则**走同一条**。
  ⇒ 所以 35.62 的差额发生在 **`applyAdditionalDamage` 内部**，而且 ⭐ **35.62 / 138.31 = 0.2575**，与已知的减伤 **0.4358** 又不是一个数。
- ✅ **守卫有效**：`damage_skill_key == 3` **确实挡住了自触发**（否则 Trigger recursion exceeded 8 levels）。
- ⭐ **因此槽位 15 的规则仍保留旧形态**（`ATTACK_FINISHED` ＋ 两个状态，绿灯），
  ⭐ 而**下一步的入口**已经只剩一层：读 `applyAdditionalDamage` 内部，看**规则驱动**的 ADDITIONAL 实例与**技能驱动**的
  （`ult_zone_additional_damage` 由 `ATTACK_FINISHED` 触发）在哪个乘区上分叉。

- ⛔ **新目标第 7 件（能力 ③）：再次回滚**（第 7 次）。而这一轮**量出了两个硬数字**，并且**修正了一条已出货判据的故事**。
- ⭐⭐⭐ **实测（`applyAdditionalDamage` 的返回处）**：
  ```
  [probe-settled] base=138.311712 -> settled=90.12793458924534  attacker=Tribbie type=ADDITIONAL castCategory=UNSPECIFIED countsAsAttack=false
  [probe-settled] base=138.311712 -> settled=82.19667651706368  attacker=Tribbie type=ADDITIONAL castCategory=UNSPECIFIED countsAsAttack=false
  ```
  ⇒ ⭐ 一个**新造**的 ADDITIONAL 实例，从 138.311712 的基础结算出来只有 **90.13 / 82.20**（≈ **0.59–0.65 × base**），
  **不是** `base × 1.3`。
- ⭐⭐ **因此第 118 轮那条判据（`PassageOdeExtraZoneHitTest`）的期望讲错了故事**：它把
  `138.311712 × 1.3 = 179.805` 叫作"一次结界伤害"，而**它自己观测到的差值 179.895 其实是 ≈ 两个实例**
  （2 × 90 ≈ 180 ✓）。⚠ 那条判据**至今是绿灯而且变异会咬**（去掉门径的状态条件后普攻从 575 涨到 801），
  所以它的**结论**没错，但**它给那个数字配的解释**是错的 ⇒ 已在本行更正。
- ⭐⭐ **`applyAdditionalDamage` 的注释还揭示了一件重要的事**：⭐ *"with the instance's CAST CATEGORY stated
  (2026-10-02; **reader: 1415's ode of passage, whose clause is about a 「追加攻击」**)"*
  ⇒ ⭐ **那个重载就是为这首诗加的** ⇒ 「追加攻击」的表达路径**早已具备**，缺的只是把它用对。
- ⭐ **而 ③ 的形态下观测到的差值是 35.69**，既不是 90（一个实例）也不是 179.8（两个）⇒ **额外那一下只落了一部分**
  ⇒ 该量级关系**仍未查明** ⇒ **登记的入口已缩到**：`applyDamage` 的乘区里，为什么同一个 base 会结算出 90.13 与 82.20
  **两个不同的值**（这说明它**依赖上下文**，而两个上下文的差别就是 ③ 的答案）。
- ⭐ **槽位 15 的规则仍保留旧形态**（`ATTACK_FINISHED` ＋ 两个状态，绿灯）。
