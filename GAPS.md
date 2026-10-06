

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

- ✅ **已出货（2026-10-02，新目标第 7 件）**：能力 ③ "**这次攻击是追加攻击**" ⇒ **槽位 15 的收紧完成**。
  ⭐ **新词汇 `damage_has_no_cast`**（裸关键词：*"the instance being settled names no cast"* —— 即**引擎自己产生的**那一下），
  三处：常量 ＋ 解析支 ＋ 条件类。
  ⭐ 内容：槽位 15 的规则移到**实例事件**，条件为
  `["actor == self", "self has_state 结界", "self has_state 献予「门径」之诗", "damage_is_additional", "damage_has_no_cast"]`，
  ⭐ 而**它加的那一下声明 `cast_category: ULTRA`** ⇒ 不满足上面的条件 ⇒ **不自触发**；目标用 `target`。
  ⭐ 判据 `PassageOdeExtraZoneHitTest`（**同一场战斗内的对照**：施放诗之后**撤掉门径的状态**）：
  `with 475.06583639547534 ; without 388.6902297781162` ⇒ 差 **86.3756 ≈ 一个 ADDITIONAL 实例**；
  ⭐ 变异（去掉 `damage_has_no_cast`）⇒ **RED**，报 *"Trigger recursion exceeded 8 levels while firing DEALING_DAMAGE"* ✓。
- ⭐⭐⭐ **五条新事实（新目标第 7 轮）**：
  1. ⭐⭐ **引擎自己产生的实例长这样**（`DEALING_DAMAGE` 处实测）：
     `type=NORMAL skillKey=3 castCategory=ULTRA`（终结技）、`type=NORMAL skillKey=1 castCategory=NORMAL`（普攻）、
     **`type=ADDITIONAL skillKey=0 castCategory=UNSPECIFIED`（结界那一下）**；
  2. ⭐⭐ **那些实例带的是枚举 `UNSPECIFIED`，不是 null** ⇒ 只判 null 的条件**恒假**（我第一版就这么错了）；
  3. ⭐ **`from_skill` 解析的是 `SkillType`** ⇒ `from_skill UNSPECIFIED` 会被拒（*"names the unknown skill slot"*）；
  4. ⭐⭐ **此前所有尝试里那 35.69 的"差值"全部是门径的 `DEFENCE_IGNORE`**：
     `405.575 / 369.885 = 1.0965` 与 `90.13 / 82.20 = 1.0965` **完全一致** ⇒ **③ 的规则从未触发过**；
  5. ⭐ **结界那一下的 `skillKey` 是 0** ⇒ 我当初的守卫 `damage_skill_key == 3` **恰好把要看的那一下排除了**。
- ⚠ **一条诚实的说明**：对本条而言 `damage_is_additional` 在 `damage_has_no_cast` 之下是**冗余的**
  （实测：去掉它读数不动）。它保留是因为**原话就是"附加伤害时"**，但**能咬的变异是本轮新增的那件**。


## 附：四件"缺词汇"的收官账（2026-10-02，新目标）

| # | 能力 | 结论 | 证据 |
|---|---|---|---|
| ① | **命途选择器** | ✅ **出货** | `allies_of_path:<命途>`（常量 ＋ 解析支 ＋ `pathNamed` 助手 ＋ 校验支 ＋ `Path` 的 import）<br>判据 `ReasonOdeEruditionAttackTest`：`那刻夏 0.84 ; 景元 0.84 ; 缇宝 0.0 ; 昔涟 0.0`<br>变异（「智识」→「同谐」）⇒ `缇宝 0.84 ; 两位智识 0.0` ⇒ **RED**<br>提交 `47118061` |
| ② | **持续地抬高某一类伤害** | ✅ **出货**，且**不需要新词汇** | 正确的形状是「状态承载存续 ＋ `from_skill SKILL` 限定实例 ＋ `BOOST_DAMAGE`」，⭐ 引擎只改了**一行**（`boostDamage` 改走 `shareOf`）<br>判据 `TrueKnowledgeSkillDamageTest`：战技比值 **1.3948**、普攻比值 **1.00111**<br>变异（去掉 `from_skill SKILL`）⇒ 普攻 **575.36 → 801.63** ⇒ **RED**<br>提交 `03f27600` |
| ③ | **"这次攻击是追加攻击"** | ✅ **出货** | ⭐ 那件词汇**本来就有**（`damage_is_additional`，而 `Battle` 自述 ADDITIONAL 就是引擎对追加攻击的唯一表示）；⭐ 真正缺的是 **`damage_has_no_cast`**（"这一下没有指名任何施放" = 引擎自己产生的），三处：常量 ＋ 解析支 ＋ 条件类<br>判据 `PassageOdeExtraZoneHitTest`（**同一场战斗内**撤掉门径状态作对照）：`475.06583639547534` vs `388.6902297781162` ⇒ 差 **86.3756 ≈ 一个 ADDITIONAL 实例**<br>变异（去掉 `damage_has_no_cast`）⇒ **RED**（*Trigger recursion exceeded 8 levels*）<br>提交 `34779c68` |
| ④ | **"这个召唤物是哪一个"** | ✅ **已消除**（**从来不需要**） | ⭐ 实测：`actor == summon` **本来就表示"规则主人自己的召唤物"** —— 把五条规则全部换回旧拼法，"别人的召唤物到达"那一侧**仍是精确 0.0**<br>⭐ 同时量到：`SummonFactory.servant(master, spec)` 与 `memosprite(master, spec)` 对同一 spec 造出**同一个单位**（同名、`memospriteOf` 都指向它）<br>提交 `fc9c47ce` |

⭐ **四点共同的方法论**：四件里**只有一件**真的需要新词汇（①），② 是**形状用错**、③ 是**词汇已在但判别器缺失**、④ 是**从一开始就不需要**
⇒ 而每一次的结论都来自**运行时探针**（`fireTriggers`／`apply`／`DAMAGE` 份额／条件字段），不是推断。


## 附：`1415` 忆灵技能的**终局审计**（2026-10-02，新目标第 1 轮）

三方对照：tbgd 的整句原话（`AvatarServantSkillConfig` 的等级行 ＋ TextMap）／我们的规则（`characters/*.json` 的 `source`）／已有判据（`src/test/**`）。
⭐ **立刻发现两处整技能缺失**（此前从未出货、也没有交付条目 ⇒ **不可施放**），以及一处从句缺失：

| 槽位 | 技能 | 我们的规则 | 已有判据 | 结论 |
|---|---|---|---|---|
| 13 | 献予「创世」之诗 `1141513` | 3（`8007.json`） | `OdeOfGenesisTest` | 已落 |
| 14 | 献予「浪漫」之诗 `1141514` | 6（`1402.json`） | `OdeOfRomanceTest` | 已落 |
| 15 | 献予「门径」之诗 `1141515` | 2（`1403.json`） | `PassageOdeExtraZoneHitTest` | 已落（第 7 轮收紧完成） |
| 16 | 献予「纷争」之诗 `1141516` | 5（`1404.json`） | `OdeToStrifeBloodfeudTest` 等 | 已落 |
| 17 | 献予「生死」之诗 `1141517` | 5（`1407.json`） | `LifeOde*`、`DragonHollowWingTest` 等 | 已落 |
| **18** | **献予「理性」之诗** `1141518` | 3（`1405.json`） | `ReasonOde*` | ⚠ **仍缺一句**：「使其**战技的伤害次数增加 `#1`(3) 次**，持续1回合」 |
| 19 | 献予「天空」之诗 `1141519` | 5（`1409.json`、`1415.json`） | `SkyOde*` | 已落 |
| 20 | 献予「诡计」之诗 `1141520` | 4（`1406.json`） | `TrickeryOdeDamageTest` 等 | 已落 |
| 21 | 献予「负世」之诗 `1141521` | 7（`1408.json`） | `WorldOde*` | 已落 |
| 22 | 献予「海洋」之诗 `1141522` | 18（`1410.json`） | `OceanOde*` | 已落 |
| 23 | 献予「律法」之诗 `1141523` | 2（`1412.json`） | `OdeToLawChargeTest`、`LawOdeCritDamageTest` | 已落 |
| 24 | 献予「岁月」之诗 `1141524` | 7（`1413.json`） | `TimeOde*`、`EvernightCritForSummonsTest` | 已落 |
| **25** | **献予「大地」之诗** `1141525` | **0 ⇒ 2**（`1414.json`） | **NONE ⇒ `EarthOdeMarksDanHengTest`** | ✅ **本轮首次出货** |
| **26** | **献予「真我」之诗** `1141526` | **0** | **NONE** | ⛔ **整技能仍未出货**（⭐ 交付条目也没有） |

- ✅ **已出货（新目标第 1 件）**：槽位 25 的**前两句**——「德谬歌施放忆灵技时，使丹恒•腾荒获得【献予「大地」之诗】」（形状同已出货的天空之诗：规则写在**被指名角色自己的文件**里，`when` 用 `target == self`）
  与「对丹恒•腾荒施放时，使龙灵行动提前 **100%**」（`ADVANCE{percent: 1.0}`）。
  ⭐ 判据 `EarthOdeMarksDanHengTest`：**向着他的那侧得状态、向着别人那侧不得**；变异（去掉 `target == self`）⇒ **RED** ✓。
  ⭐ **并补上了槽位 25 的交付条目** ⇒ `canDeliver` 为真（`11415` 现有 `3,5,13,14,15,16,17,18,19,20,21,22,23,25`）。
- ⛔ **仍登记**（槽位 25 内）：【龙灵】的下 `#3`(3) 次攻击、⭐ 【同袍】护盾量 `#4`(0.4)% 的附加伤害、⭐ 【同袍】伤害 `#1`(0.12)%、⭐ 终结技强化与护盾传递 `#5`(1.5)%。
  ⭐ 而「⭐ 使**龙灵**行动提前 ✗」的目标用了 `all_summons`（⭐ 它是"所有召唤物"、不是"名叫龙灵的那个" ✓）—— 也登记。
- ⭐ **下一件**：槽位 26（**献予「真我」之诗**）⭐ 整技能从未出货，⭐ 且 `11415` 里没有它的条目。

- ⛔ **新目标第 2 轮：槽位 26 的第三句回滚**（第 9 次），但**缺的那件能力已经定位到很窄**；⭐ 同时**查明了目标 ③ 的第一半**。
- ⭐⭐⭐ **查明：目标 ③ 的"基线 0.4184"就是 `ALL_DAMAGE_TYPE_BOOST` 的两层**
  （`1415.json` 的 `talent_party_damage`）：
  ```json
  { "op": "MODIFY_ATTR", "attribute": "ALL_DAMAGE_TYPE_BOOST", "percent": 0.2,
    "permanent": true, "target": "all_allies", "max_stacks": 2 }
  ```
  ⇒ ⭐ **0.2 × 2 层 ＝ 0.4**，而判据读到的 `getAttribute(...).get()` 是 **0.2**（**每层**值）⇒ 反解出的 **0.4184** 与 0.4 的差（0.0184）另有小来源，
  但**量级与来源都已指明**（⭐ 记录为"已知其主体为 `0.2 × max_stacks`，⭐ 残差 0.0184 未分到具体来源"）。
- ⛔ **而槽位 26 的第三句为什么落不下来**（实测）：
  1. ⭐ **加载器接受** `GAIN_RESOURCE{resource: 【故事】, target: "summon"}`（探针规则编译并构建成功）；
  2. ⚠ **运行期静默什么都不给**：召唤物的 【故事】 两次读数都是 **0**；
  3. ⇒ 原因是 **【故事】只能声明在角色文件里，而召唤物看不到**（第 104 轮那条事实）—— **而 `memosprites/*.json` 没有声明资源的字段**
     （已出货的 `memosprites/1407.json` 的键是 `aggro / attack / name / note / panel / servant_id / skills / source`）。
  ⇒ ⭐ **缺的那件能力是：给召唤物一个资源声明处**（⭐ 例如 `memosprites/<cid>.json` 增加 `resources`，⭐ 或规则层的声明），
  它一到位，槽位 26 的**第三句与第四句**就能一起落。
- ⭐ **因此本轮回滚**：`true_self_ode_gives_story`、`true_self_ode_gives_story_when_the_memosprite_arrives`、【故事】声明、以及**槽位 26 的交付条目**
  （⭐ 加条目会让它"可施放但不做事"，⭐ 与本项目的纪律相悖）。⭐ 而**审计表里槽位 26 的状态恢复为"⛔ 整技能未出货"**，⭐ 原因已写明。
- ⭐ **本轮同时确认的两件事**：⭐ 【花与箭的舞曲】= **`SkillID 1141501`／槽位 1**；⭐ `EXTRA_TURN` ＋ `CAST_SKILL{skill, skill_id}` 都已出货（`8007.json` 的 `skill_id: 1` 就是数据槽位）。

- ✅ **已出货（新目标第 2 件）：召唤物的资源声明处**。⭐ `MemospriteSpec` 新增一个 record 组件 `resources`（形状就是角色文件用的 `ResourceSpec`），
  ⭐ `SummonFactory` 新增 `declareResources(summon, spec)` 并在**两个**构造点调用；⭐ 读数是槽位 26 的【故事】。
  ⭐ 判据 `TrueSelfOdeGivesStoryTest`：`the memosprite's 【故事】 reads 1.0 with the ode and 0.0 without it`；
  ⭐ 变异（去掉 `declareResources` 的调用）⇒ **RED**（`reads 0.0 with the ode`）—— ⭐ 正是那件能力不存在时的**静默无效果**。
- ⭐⭐ **两条新事实**：
  1. ⭐ **加载器自己的规则**：规则里**用到**的资源，**必须**在**角色自己**的文件里声明
     （实测报错：*"Character 1415 has a rule that uses the resource 【故事】, which the character does not declare"*）
     ⇒ 所以【故事】在**两处**声明：`characters/1415.json` 满足加载器，`memosprites/1415.json` 才让召唤物**真的有**这个存储。
  2. ⭐ `GAIN_RESOURCE{target: "summon"}` 在**没有**声明处时**既不报错也不生效** ⇒ 这类"静默无效果"只能靠计数探针发现。
- ✅ **槽位 26 现有 4 条规则**（打标记 ＋ 终结技给【故事】 ＋ 被召唤时给【故事】）＋ **交付条目** ⇒
  `11415` 现有 `3,5,13,14,15,16,17,18,19,20,21,22,23,25,26`（15 个）。
- ⚠ **登记（槽位 26）**：⭐ 同句的另一个触发 **「或德谬歌被召唤时」在判据里没有推动计数器**（⭐ 而终结技那条推动了 ✓）⇒ 逐条登记，不冒充；
  ⭐ 第一句（**不同队友**的计数）、第二句（额外交付的冰伤，依赖第一句）、第四句（读**召唤物自己**的资源以在 3 点触发）仍登记。
- ⭐ **审计表的更正**：槽位 26 的状态从"⛔ 整技能未出货"改为"⚠ 第三句的一半已落 ＋ 交付条目已在"。

- ⭐⭐⭐ **查明（新目标第 4 轮）：槽位 26 的「或德谬歌被召唤时」为什么在判据里不动计数器** —— 而答案是**它其实会发**。
  实测链条：
  1. ⭐ 探针（`TriggerInterpreter.apply` 入口只印含【故事】的规则）在整个判据里**只看到一次应用**，⭐ 且那一次的 `actor` 是**昔涟自己**
     ⇒ 那是**终结技**那条；⭐ 说明到达那条**没发**。
  2. ⭐ 把门从 `actor is_summon` 换成身份形式 `actor == summon` ⇒ 到达那条**仍然**没发（0.0）。
     （⭐ `1413.json` 早有同一条记录：*"`actor is_summon` 在这里未命中"*。）
  3. ⭐ **去掉状态门** ⇒ 读数 `2.0 with the ode and 1.0 without it` ⇒ ⭐ **到达那条确实匹配**（⭐ `actor == summon` 有效 ✓），
     而 ⭐ **`self has_state 献予「真我」之诗` 在那一刻是假的**。
  4. ⭐ **剂量实验**（带门给 1、不带门给 100）⇒ `101.0 with the ode and 100.0 without it`
     ⇒ ⭐ 带门那条**在"有诗"那侧发过**（101 = 100 + 1）⇒ ⭐ **门是真的、规则也在发**。
- ⭐⭐ **而这与上一条并不矛盾，原因就是第 4 轮那条实测**：
  ⭐ `SummonFactory.servant(master, spec)` 与 `memosprite(master, spec)` 造出的是**同一个单位**
  ⇒ 判据里"**为了施放诗而召唤的德谬歌**"⭐ **就是**后面那次"**德谬歌被召唤**"的**同一次到达** ⇒ 那一刻诗还**没上** ⇒ 门当然是假的。
  ⇒ ⭐⭐ **因此这句触发只能在"诗已在、之后**再**发生一次召唤"时观察到**（⭐ 游戏里对应的就是昔涟终结技把德谬歌**再**召出来那一次）。
- ⛔ **本轮的处置**：⭐ 判据恢复为上一轮出货的形态（⭐ 那条判据量的是**能力本身** ✓，⭐ 它一直是绿的 ✓），
  ⭐ 而⭐ **不**把"到达"写成已出货 —— ⭐ **逐条登记**：它的门（`self has_state`）与"同一次到达"的时序关系需要**一个能制造第二次召唤的判据**才能量。
- ⭐ **一条可复用的事实**：⭐ `self has_state …` 在 `SUMMONED` 上读的是**规则主人**（⭐ 与文件注记一致 ✓）；⭐ 而⭐ **规则主人自己的那次到达**发生在它被标记**之前**。

- ✅ **已出货（新目标第 3 件）：`until: "next_turn_start"` —— 目标 ③ 的第二半同时被"查明并修好"**。
  ⭐⭐ **查明**：引擎的时长集合原本是 `LIFETIMES = {next_attack, next_skill, cast_end, next_ultimate, turn_end}`
  ⇒ ⭐ **「持续至下一个…回合开始时」根本没有拼法**；⭐ 而槽位 18 的【真知】原话正是
  「⭐ **持续至下一个那刻夏回合开始时**」⭐ 却写成 `until: next_attack` ✗ ⇒ ⭐ **而授予它的那次施放本身就是 attack**
  ⇒ ⭐⭐ **状态在它被创建的那一次施放上就被消耗掉了**（实测：变异回 `next_attack` ⇒ *"【真知】 is gone"* ✓）。
  ⭐ **修法**：`AbstractBuff.Lifetime` 增加 **`NEXT_TURN_START`**、拼写 **`"next_turn_start"`**、
  并在 **主人自己的 `TURN_START`** 处消费（`Battle` 的 `TURN_START` 触发点旁 ＋ `BuffManager.removeWithLifetime` ✓）。
  ⭐ 判据 `TrueKnowledgeLifetimeTest`：`after the cast that grants it, 【真知】 is still on him`；
  ⭐ **变异（换回 `next_attack`）⇒ RED**（`【真知】 is gone`）✓。
- ⭐ **一条提醒**：`AbstractBuff` 与 `EnemySkill` 的注记里都有 `next_attack` 的坑
  （⭐ `EnemySkill.java:161`：`target: summon` ＋ `until: next_attack` **"never consumed and simply stayed"** ✓）
  ⇒ ⭐ 所以这个时长**两边都会出错**，⭐ 用错时不会报错 ✓。
- ⭐ **审计表的状态**：槽位 18 的「持续至下一个那刻夏回合开始时」⭐ **现已按原话落地**（⭐ 而「战技伤害次数增加 3 次」仍登记 ✓）。

- ⛔ **新目标第 6 轮：回滚（第 10 次）—— 但槽位 26 第四句的阻碍已定位到一处，且很窄。**
- ⭐ **本轮做了什么**：为"⭐ 读**召唤物自己**的资源 ✗"加了词汇 **`actor_resource:` / `target_resource:`**（⭐ 复用 `Numeric` ✗ 已有的 `stacksOnActor`/`stacksOnTarget` ✗ 两个标志 ⇒ ⭐ **零新字段** ✓：
  `selfResourceOf` ✗ 接受三个前缀、两个构造点的标志放宽、变量白名单放行、`value()` ✗ 的资源读取改走**与计数相同的持有者解析** ✓）⇒ ⭐ **编译通过、加载器接受** ✓。
- ⚠ **但第四句仍然落不下来**，且阻碍已经量清：
  1. ⭐ 规则挂在 **`RESOURCE_CHANGED`** ✗ 上；⭐ 而那个事件是 `fireResourceChanged` ✗ 以**持有者**为 actor 广播的
     （`battle.fireTriggers(RESOURCE_CHANGED, holder, holder, 0, delta)` ✗）⇒ ⭐ **持有者是忆灵** ✗，⭐ **而忆灵没有规则表** ✓
     ⇒ ⭐ 规则**压根没发**（⭐ 实测：带去状态门、⭐ 去状态门，⭐ 两种写法的读数都是 **3.0**，⭐ 即从未消耗 ✓）。
  2. ⭐ 这与已登记的一条同源：`1413.json` ✗ 的注记说 **`DAMAGE_SETTLED` 只送到攻击者的表、主人表收不到** ✗
     ⇒ ⭐ **`RESOURCE_CHANGED`** ✗ ⭐ 很可能同样是"⭐ 只到 actor 的表 ✗" ✓。
  3. ⇒ ⭐ **正确的形状**应当是：⭐ 把 3 点检查**挂在她收得到的触发上**（⭐ `ULT_CAST` ✗ / ⭐ `SUMMONED` ✗ ✓）
     ⭐ 并读 **"⭐ 主人的召唤物 ✗"** 的资源 ⇒ ⭐ 那需要一个 **`summon_resource:<id>`** ✗ 形式（⭐ 在我这次的 `actor_/target_resource:` ✗ 之上再加一向 ✓）。
     ⚠ 而顺序也要照顾（⭐ 102 轮的教训：⭐ 计数的规则必须排在增益之后 ✓）。
- ⛔ **因此本轮回滚**：`true_self_ode_spends_three_story` 规则、`actor_resource:`/`target_resource:` 两个前缀、
  以及那条判据（⭐ 按项目纪律：**没有可归因效果的词汇不算出货** ✓）。
  ⭐ **而阻碍被写成两条可执行的话**：⭐ （a）⭐ `RESOURCE_CHANGED` ✗ 的投递范围要**探针证实**；⭐ （b）⭐ 需要一个 ⭐ `summon_resource:` ✗。

- ✅ **已出货（新目标第 4 件）：`summon_resource:<资源>` ＋ 槽位 26 第四句的**可表达部分**。
- ⭐⭐⭐ **查明（这才是第四句落不下来的真因）**：`fireResourceChanged` 的正文是
  ```java
  CanHit holder = ctx.owner();                                   // 规则主人，不是真正获得资源的那个单位
  battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, holder, holder, 0, delta);
  ```
  ⇒ ⭐ **`RESOURCE_CHANGED` 会发、也会送到她的表** ✓ ⭐ 而它的 **actor/target 都是【规则主人】昔涟** ✗，⭐ **delta 也在她的存储上量**（⭐ 所以第 6 轮探针读到 `delta=0` ✓）
  ⇒ ⭐⭐ 因此 `self_resource:` / `actor_resource:` **都看不到忆灵身上的【故事】** ✓ ⇒ ⭐ 正确的是 **`summon_resource:`**（用 `Battle.memospriteOf(owner)` 从主人**伸到它的召唤物** ✓）。
- ✅ **判据** `TrueSelfOdeSpendsThreeStoryTest`：`with 3 points the counter reads 0.0 ; with only 2 it reads 2.0`；
  ⭐ **变异（换成 `self_resource:`）⇒ RED**（`reads 3.0`）—— ⭐ **正是第 6 轮那个症状**，⭐ 诊断由此闭环 ✓。
- ⛔ **同句仍登记**：⭐ 「**立即获得 1 个额外回合**」⇒ `Battle.grantExtraTurn` 对**不在行动顺序**的单位返回 false，
  而本忆灵的速度 **据数据就是 0**（⭐ `memosprites/1415.json` 的 `source` 记录了游戏行：`SpeedBase: "0"`、`SpeedInherit: "0"` ✓）。
  ⭐ 而 `EXTRA_TURN` 现在会**响亮地拒绝**，⭐ 它的注记说明**同一情形早前已在创世「迷迷」上量到** ✗ ⇒ ⭐ 这是**已知的引擎分歧（忆灵不在行动顺序）** ✓ 登记。
- ⭐ **槽位 26 现状**：6 条规则（⭐ 打标记 ＋ 终结技给【故事】 ＋ 被召唤时给【故事】 ＋ 3 点消耗与自动施放 ✓）
  ⇒ ⭐ 第三、四句的**机械核心**已落；⭐ 第一句（不同队友计数）、第二句（额外冰伤）、⭐ 以及「额外回合」仍登记 ✓。

- ✅ **已出货（新目标第 5 件）：槽位 26 第四句**完整**落地**，且「额外回合」的**正确读法**被查明：
  ⭐⭐ 关键在 `Queue.addCombatant` 自己写下的决定：
  > *"A unit at zero speed has no action value at all (`cycleTime = 10000 / speed`), so there is nothing to schedule — **skipped rather than refused** (2026-10-02). **The game states this** for the two
  > memosprite whose panel says so — 「小伊卡的速度保持为0…并且不会出现在行动…」 — and **simply never takes a turn**."*
  ⭐ 而**德谬歌的速度据数据就是 0**（`memosprites/1415.json` 记录游戏行 `SpeedBase: "0"`）
  ⇒ ⭐⭐ **所以「额外回合」对它不可能是排程意义上的回合** ⇒ ⭐ **「立即获得」的语义是 `INSERT_ACTION`**（⭐ 该 op 的自述：*"makes X act now"* ✓）。
- ⭐ **落地内容**：`summon_resource:故事 >= 3` ＋ `SPEND_RESOURCE{spendAll}` ＋ **`INSERT_ACTION`** ＋ `CAST_SKILL{skill: SKILL, skill_id: 1}`（⭐ 花与箭的舞曲 = `SkillID 1141501`／忆灵槽位 1 ✓）。
- ⭐ **判据**：`with 3 points the counter reads 0.0 ; with only 2 it reads 2.0`；
  ⭐ **变异（换回 `EXTRA_TURN`）⇒ RED**，⭐ 报的正是那句拒绝：*"EXTRA_TURN gives 德谬歌 an extra turn, but it is not in the action order (a memosprite at Speed 0 is skipped…)"* ✓。
- ⛔ **本轮试过并撤回**：给 `Queue.grantExtraTurn` 加"缺席时先 `addCombatant`" —— ⭐ **它失败了**（`addCombatant` 对速度 0 直接 return ✓），
  而 ⭐ 失败本身**就是证据**：⭐ 那处跳过是**有意且与游戏一致**的 ✓ ⇒ ⭐ 撤回，⭐ 不留任何引擎改动 ✓。
- ⚠ **仍登记两条**：
  1. ⭐ **插入的那次行动是否真的执行了自动施放**，⭐ 判据目前只证到"消耗"（⭐ 独立断言需要行动条的可观测点 ✓）；
  2. ⭐ **`EXTRA_TURN` 的其它读者**（⭐ 创世的「迷迷」：它的 `EXTRA_TURN` 注记记录了同一测量 ✓）⭐ 若「迷迷」速度也是 0 ⇒ ⭐ 它需要同样的改读 ✓。
- ⭐ **槽位 26 现状**：6 条规则 ⇒ ⭐ **第三、四句已完整**；⭐ 第一句（不同队友计数）、第二句（额外冰伤）仍登记 ✓。

- ✅ **已出货（新目标第 6 件）：`CyreneTalentBoostReadingTest`** —— ⭐ 把天赋那句钉成实测值。
- ⭐⭐⭐ **目标 ③ 第一半的完整答案**：
  1. ⭐ 天赋 `talent_party_damage` ✗ 就是原话「⭐ **昔涟在场时，我方全体目标造成的伤害提高 20.00%** ✗」⭐ **实测读出 0.2** ✓
     （⭐ **不是** 0.4 ⇒ ⭐ `max_stacks: 2` ✗ 只是**允许**叠更多，⭐ 此战只落了 1 层 ✓）。
  2. ⭐⭐ 而反解的 **0.4184 ≈ 0.4** 说明当时**有第二个来源在场**：⭐ 她的痕迹 **`trace_party_damage_at_speed_180`** ✗
     （⭐ 也是 `ALL_DAMAGE_TYPE_BOOST 0.2`、`max_stacks: 2`，⭐ 在 `TURN_START` ✗ ＋ `self_attr:SPEED >= 180` ✗ 时触发 ✓）
     ⇒ ⭐ **0.2 ＋ 0.2 = 0.4** ⭐ 正好是那个 0.4 ✓。
  3. ⭐ 残差 **0.0184** 仍待分（⭐ 最可能是**痕迹的 8% 攻击**渗进反解 ✓）⇒ 登记。
- ⭐⭐ **本轮新发现的缺口（登记）**：⭐ 天赋 141504 的原话里还有**三句从未落地**——
  ⭐ 「⭐ 战斗开始时**或昔涟行动后**，我方任意状态的其他角色及其忆灵获得【未来】 ✗」；
  ⭐ 「⭐ 持有【未来】的我方目标行动时消耗【未来】使昔涟获得 1 点【追忆】 ✗」；
  ⭐ 「⭐ 【追忆】达 24 点（⭐ 【往昔的涟漪】下 12 点）可激活终结技并解除自身所有负面效果，上限后可溢出至 27 点 ✗」。
  ⭐ 而 `talent_party_damage` ✗ 只落了最后那句伤害提高 ✓。
- ✅ **已消解（登记项）**：⭐ `EXTRA_TURN` ✗ 那条 —— ⭐ 全库只剩 4 处、全部 `target: self` ✗（⭐ 持有者都是**角色** ✓），
  ⭐ 而两个速度 0 的忆灵（小伊卡、德谬歌）**都没有** `EXTRA_TURN` ✗ ✓ ⇒ ⭐ 无一处指向速度 0 的单位 ✓。
- ⭐ **登记（槽位 18）**：⭐ 「使其**战技的伤害次数增加 `#1`(3) 次**，持续1回合」⭐ **没有词汇**（⭐ `hits` ✗ 只在 `MemospriteSpec` ✗；⭐ `hit_count` ✗ 是**条件** ✓）
  ⇒ ⭐ 需要"⭐ 运行期改某技能的命中次数 ✗"，⭐ 而**模板就在旁边**：`CanHit.raiseSkillLevel` ✗ 的 `skillLevelBonus` ✗ ＋ ⭐ `RAISE_SKILL_LEVEL` ✗ op ✓。

- ✅ **已出货（新目标第 7 件）：天赋的【未来】授予**（⭐ 原话「⭐ 战斗开始时**或昔涟行动后**，我方任意状态的**其他**角色及其忆灵获得【未来】 ✗」）。
  ⭐ 两条规则：**`BATTLE_START`** ＋ ⭐ **`TURN_END`**（`actor == self` ✗ = 「昔涟行动后」✓），⭐ 都是
  `APPLY_BUFF{未来, target: "other_allies", permanent: true}` ✗ ✓。
  ⭐ 判据 `CyreneFutureTest`：`the ally has it = true ; 昔涟 herself = false`；
  ⭐ **变异（`other_allies` → `all_allies`）⇒ RED**（`昔涟 herself = true`）✓。
- ⭐⭐ **本轮量到的三条引擎事实**：
  1. ⭐ **选择器的准确拼写是 `other_allies`**（⭐ 不是 `all_other_allies`；⭐ 加载器会列出 `TARGET_SELECTORS` 的已知项 ✓）；
  2. ⭐ **`APPLY_BUFF` 拒绝 `max_stacks`**（⭐ 报错原文：*"only MODIFY_ATTR accumulates"* ✓）⇒ ⭐ **buff 天生就是刷新** ✓，⭐ 正合「获得【未来】」✓；
  3. ⭐ **`target_when` 不能在 `BATTLE_START` 上用**（⭐ *"carries no actor and no target"* ✓）；
     ⭐ 而 ⭐ **`REMOVE_STACK` 必须写 `amount`**，⭐ 并且**"先给全体、再后置回收"的写法无效**（⭐ 同一事件内的应用顺序**不是文件序** ✓）。
- ⛔ **仍登记（天赋 141504 的其余两句）**：⭐ 「⭐ 持有【未来】的我方目标行动时消耗【未来】使昔涟获得 1 点【追忆】 ✗」
  （⭐ 需要读**行动者自己**的计数器：⭐ `actor_stacks:未来` ✗ ＋ ⭐ `REMOVE_STACK{target: actor}` ✗ ＋ ⭐ `GAIN_RESOURCE{追忆, 1}` ✗）；
  ⭐ 以及 ⭐ 「⭐ 【追忆】达 24 点（⭐ 【往昔的涟漪】下 12 点）可激活终结技并解除自身所有负面效果，上限后可溢出至 27 点 ✗」✓。
- ⭐ **一条可复用的事实**：⭐ **同一事件内的多条规则，其应用顺序不按文件顺序** —— ⭐ 所以"⭐ 先给再撤 ✗"⭐ 这种写法不可靠 ✓（⭐ 102 轮那条"⭐ 计数要排在增益之后 ✗"的教训由此扩展 ✓）。

- ⛔ **新目标第 11 轮：回滚（第 11 次）—— 但【未来】消耗的阻碍缩到**一条**，且两条引擎事实被钉住。**
- ⭐⭐⭐ **实测（本轮三条，都来自同一条判据）**：
  1. ⭐ **派发没问题**：⭐ 挂在**她的表**上的规则，⭐ 对**队友的 `TURN_START`** ✗ **会发**
     （⭐ 证法：⭐ 去掉条件后读数 `【追忆】 0.0 -> 1.0` ✓）—— ⭐ 这与 `SUMMONED` 可达队友表同源 ✓。
  2. ⭐⭐ **`actor_stacks:未来` 是假的**（⭐ 而同一条规则**带**这个条件时**不发** ✓）
     ⇒ ⭐ **`*_stacks:` 读的是"计数器"，不是"状态"**；⭐ `APPLY_BUFF` ✗ 造出来的是**状态** ⇒ ⭐ 条件必须写 **`actor has_state 未来`** ✓
     （⭐ 换成它之后规则就发了，⭐ 读数同样 `0.0 -> 1.0` ✓）。
  3. ⚠ **`REMOVE_STACK{buff: 未来, amount: 1}` 没有把它拿掉**：⭐ 用 `target` ✗ 与 `attacker` ✗ **都试过**，
     ⭐ 而 `hasState(未来)` ✗ 仍是 true ✓ ⇒ ⭐ 嫌疑是**授予时写的 `permanent: true`**（⭐ 是否让它不可被 `REMOVE_STACK` 移除 ✓）—— ⭐ **这是下一轮的第一个探针** ✓。
- ⛔ **因此本轮回滚**：`talent_spends_future_for_recollection` 一条（⭐ 上一轮的【未来】**授予**保留 ✓）。
- ⭐ **一条可复用的事实（新）**：⭐ **状态与计数器是两个家族** —— ⭐ `self/actor_stacks:<名字>` ✗ 读计数器，
  ⭐ `self/actor has_state <名字>` ✗ 读状态；⭐ 用错时**不报错、只是恒假** ✓。

- ✅ **已出货（新目标第 8 件）：天赋的【未来】消耗**（⭐ 原话「⭐ 持有【未来】的我方目标行动时消耗【未来】使昔涟获得 1 点【追忆】 ✗」）。
  ⭐ 形状：`TURN_START` ✗ ＋ 条件 **`actor has_state 未来`** ✗ ⇒ ⭐ `REMOVE_STATE{buff: 未来, target: "attacker"}` ✗ ＋ ⭐ `GAIN_RESOURCE{追忆, 1}` ✗ ✓。
  ⭐ 判据 `CyreneFutureSpendTest`：`after the ally acts: 【未来】 still on it = false ; 昔涟's 【追忆】 0.0 -> 1.0` ✓；
  ⭐ **变异（门换成 `actor_stacks:未来 >= 1`）⇒ RED**（⭐ `still on it = true` ＋ `0.0 -> 0.0` ✓）。
- ⭐⭐⭐ **三条引擎事实（本轮与上一轮量到，全部有实测）**：
  1. ⭐ **状态与计数器是两个家族**：⭐ `self/actor_stacks:<名字>` ✗ 读**计数器**，⭐ 对 `APPLY_BUFF` ✗ 造的**状态恒假、且不报错**；
     ⭐ 状态要用 ⭐ `self/actor has_state <名字>` ✗ ✓（⭐ 变异即证明 ✓）。
  2. ⭐ **`REMOVE_STACK{buff, amount}` 拿不掉一个 buff** ✗ —— ⭐ 而 `BuffManager.removeNamedStacks` ✗ **根本不看 `permanent`** ✓
     ⇒ ⭐ 问题在 **op 的选择**：⭐ **`REMOVE_STATE`** ✗（⭐ 该 arm 自述 *"takes all of them off"* ✓）**可以** ✓。
  3. ⭐ **`target` 在"⭐ 不带目标的 `TURN_START` ✗"上不可用** ✗ —— ⭐ 实测：三个旧判据因此报
     *"Effect targets \"target\" but this event has no such party"* ✗ ⇒ ⭐ 改用 **`attacker`** ✗ ✓ ⇒ ⭐ 全量恢复 **0** ✓。
- ⛔ **仍登记**：⭐ 【追忆】达 24 点（⭐ 【往昔的涟漪】下 12 点）可激活终结技并解除自身所有负面效果、上限后可溢出至 27 点 ✓；
  ⭐ 槽位 18 的「战技伤害次数 +3」✓；⭐ 槽位 26 的第一句（⭐ **不同队友**的计数 ✓）与第二句 ✓。
- ⭐ **进展说明**：⭐ 槽位 26 第一句的前置（⭐ 「⭐ 使昔涟获得 1 点【追忆】 ✗」）**已经落地** ✓ ⇒ ⭐ 剩下的只是"⭐ **哪些队友**给过 ✗"⭐ 这一层去重 ✓。

- ✅ **已出货（新目标第 9 件）：天赋最后一句里可表达的两项**。
  ⭐ 形状：⭐ 【追忆】 声明 **`overflow: 3`** ✗（⭐ `max: 24` ⇒ ⭐ **可达 27** ✓，⭐ 正合「达到上限后还可最多溢出至27点」✓）；
  ⭐ 以及一条 `RESOURCE_CHANGED` ✗ 规则（⭐ 实测其 holder **就是规则主人** ✓）⭐ `when: ["self_resource:追忆 >= 24"]` ✗
  ⭐ `do: [DISPEL{kind: control, target: self}, DISPEL{kind: dot, target: self}]` ✗ ✓。
  ⭐ 判据 `CyreneRecollectionCapTest`：`max=24 overflow=3` ＋ `after reaching 24, her debuffs read 0` ✓；
  ⭐ **变异（阈值 24 → 25）⇒ RED**（`debuffs read 2`）✓。
- ⭐⭐⭐ **三条引擎事实（本轮量到）**：
  1. ⭐ **`DISPEL{kind: "all"}` 被拒**（⭐ 报错原文：*"names the class 'all', which the documents do not: known classes are control / dot"* ✓）
     ⇒ ⭐ 所以「**所有**负面效果」⭐ 要**按类别**写 ✓。
  2. ⭐⭐ **"重复句子守卫"的键是 `事件 ＋ when ＋ op ＋ target`** ✗ —— ⭐ 所以**只有 `kind` 不同的两条规则会被判为"同一句写了两遍"**
     ⇒ ⭐ 正确写法是**一条规则带两个效果** ✓（⭐ 语义上也确实是一句话 ✓）。
  3. ⭐ **`overflow` 是资源自身的字段** ✗ ⇒ ⭐ `max: 24` ＋ `overflow: 3` ＝ **27** ✓。
- ⚠ **一条已出货判据因此移动**：⭐ `CyreneTest.bothWritersFeedRecollectionUpToTwentyFour` ✗ 原本断言 **24**
  —— ⭐ 它在本条落地**之前是对的、现在错了**（⭐ 数据自己说可溢出至 27 ✓）⇒ ⭐ 已改名为 `…UpToItsStatedOverflow` ✗ 并把期望改为 **27** ✓ ✓。
- ⛔ **仍登记**：⭐ 「⭐ 处于【往昔的涟漪】状态时…**12 点** ✗」（⭐ 需要该状态，⭐ 套装尚未建模 ✓）⭐ 与 ⭐ 「⭐ **可激活终结技** ✗」本身 ✓。

- ⛔ **新目标第 14 轮：回滚（第 12 次）—— 但槽位 18 那句的**实现设计已全部查明**，只剩落内容与判据。**
- ⭐⭐ **已查明的三件事**（⭐ 直接决定写法 ✓）：
  1. ⭐ **命中次数在哪里**：`SkillExecutor` 的 `BOUNCE` 分支
     ```java
     Integer additional = data.bounceAdditionalHits(bounceShare);
     int hits = (additional == null ? 0 : additional) + 1;
     for (int i = 0; i < hits; i++) { ... }
     ```
     ⭐ **`user` 就在作用域里** ⇒ ⭐ 加一份加成只需**一行** ✓；
  2. ⭐ **键要用"数据槽位"**：`Skill` ✗ 只暴露 `getLevel()` / `getData()` / `getCid()` / **`getSkillSlot()`（int）**，
     ⭐ **没有** `SkillType` ✗ ⇒ ⭐ 所以加成表应当是 **`Map<Integer,Integer>`** ✗，⭐ 与 ⭐ `CAST_SKILL{skill_id}` ✗ 同一口径 ✓
     （⭐ 我第一版按 `SkillType` ✗ 做键，⭐ 编译器当场否掉 ✓）；
  3. ⭐ **op 的形状照 `RAISE_SKILL_LEVEL` ✗ 抄**（⭐ 它的正文是 `ctx.owner()` ＋ `SkillType.valueOf(effect.getSkill())` ＋ `(int) Math.round(effect.getAmount())` ✓）
     ⭐ 而本 op 读 ⭐ **`skill_id`** ✗（⭐ 数据槽位）＋ ⭐ `amount` ✗（⭐ 可为负 ⇒ ⭐ 「⭐ 持续 1 回合 ✗」⭐ 由读者在 `TURN_END` ✗ 用负值收回 ✓）。
- ⛔ **为什么本轮仍回滚**：⭐ 引擎侧改完并**编译通过**（⭐ 三个文件 ✓），⭐ 但**内容与判据**来不及 ⇒
  ⭐ 按项目纪律"**没有可归因效果的词汇不算出货**"⭐ **全部撤回** ✓（⭐ 不留半成品 ✓）。
- ⭐ **下一轮的落法（预计 3 步）**：⭐ ① `CanHit.skillHitBonus` ✗（`Map<Integer,Integer>` ✗ ＋ ⭐ 两个存取器）＋ ⭐ `RAISE_SKILL_HITS` ✗ op（⭐ 含它自己的 `skill_id`/`amount` 校验 ✓）＋ ⭐ `SkillExecutor` ✗ 的那一行；
  ⭐ ② 内容：⭐ `1405.json` ✗ 槽位 18 的规则加 `RAISE_SKILL_HITS{skill_id: 2, amount: 3}` ✗（⭐ 战技的数据槽位 ✓）＋ ⭐ 一条 `TURN_END` ✗ 收回；
  ⭐ ③ 判据：⭐ **必须避开"有诗／无诗"的混杂**（⭐ 诗本身会给【真知】 ✗）；
  ⭐ 最干净的是 ⭐ 比较**同一个技能在加成前后**的命中数（⭐ 用同种子、⭐ 只切换加成 ✓）。

- ✅ **已出货（新目标第 10 件）：槽位 18 的最后一句**「⭐ 使其**战技的伤害次数增加 `#1`(3) 次**，持续1回合 ✗」⇒ ⭐ **槽位 18 至此完整** ✓。
  ⭐ **引擎三处**：⭐ `CanHit.skillHitBonus` ✗（⭐ **按数据槽位**的 `Map<Integer,Integer>` ✗ ＋ ⭐ 两个存取器 ✓）；
  ⭐ op **`RAISE_SKILL_HITS{skill_id, amount}`** ✗（⭐ 照 `RAISE_SKILL_LEVEL` ✗ 的形状：⭐ `ctx.owner()` ✗ ＋ ⭐ 解析目标 ✓；⭐ **`amount` ✗ 可为负** ✓）；
  ⭐ `SkillExecutor` ✗ 的 `BOUNCE` ✗ 分支加**一行**（`hits += user.skillHitBonus(skill.getSkillSlot())` ✗ ✓）。
  ⭐ **内容**：⭐ `1405.json` ✗ 槽位 18 的规则加 `RAISE_SKILL_HITS{skill_id: 2, amount: 3}` ✗；
  ⭐ 一条 `TURN_END` ✗（`actor == self` ✗）规则用 **`-3`** ✗ 收回 ⇒ ⭐ 「持续1回合」**不需要新的时长机制** ✓。
  ⭐ **判据** `ReasonOdeExtraHitsTest`：`his skill deals 1721.6988481718581 with the three extra segments, 1052.1492961050244 without them` ✓；
  ⭐ **变异（`skill_id: 2` → `1`）⇒ RED**（⭐ 两侧读数**相等** ✓）。
- ⭐⭐ **两条新的工程事实（本轮量到）**：
  1. ⭐ **新 op 除了"被处理"之外，还必须登记进 `TriggerInterpreter` ✗ 的已知 op 清单** ✗ ——
     ⭐ 否则加载器报 *"Unknown trigger op 'RAISE_SKILL_HITS'"* ✗（⭐ 实测 ✓）。
  2. ⭐⭐ **判据的控制侧必须"读回真实值"** ✗：⭐ 我第一版硬编码 `-3` ✗，⭐ 结果当加成被**误导到别的槽位**时，
      ⭐ 控制侧把**真正该看的槽位减成负数** ✗ ⇒ ⭐ 两侧仍然"有多有少" ✗ ⇒ ⭐ **变异没咬** ✗ ✓。
     ⭐ 改成 ⭐ `-aimed.skillHitBonus(slot)` ✗ ✓ ⇒ ⭐ 变异立刻打红 ✓。
- ⭐ **槽位 18 现状**：⭐ 原话四句 —— ⭐ 「恢复战技点并立即行动」✓、「战技伤害次数 +3，持续1回合」✓、
  ⭐ 「获得【真知】：智识命途攻击力提高、战技伤害提高、**持续至下一个那刻夏回合开始时**」✓ ⇒ ⭐ **本槽已完整** ✓。

- ⛔ **新目标第 16 轮：回滚（第 13 次）—— 但本轮量到一条**关键的引擎事实**。**
- ⭐⭐⭐ **实测：同一事件内的规则按文件顺序执行** ✓ —— 探针：⭐ 规则 A（⭐ 在前）在 `TURN_START` ✗ 给昔涟打标记，
  ⭐ 规则 B（⭐ 在后，⭐ 同一事件同一主人 ✓）⭐ 只在**看到那个标记时**才给【追忆】24 点 ⇒ ⭐ **读数 24** ✗ ✓
  ⇒ ⭐ **B 看得见 A 刚打上的标记** ✓ ✓。
  ⭐ 这也解释了更早那次"⭐ 先给再撤 ✗"⭐ 的失败：⭐ 那次用的是 **`REMOVE_STACK`** ✗，⭐ 而它**拿不掉 buff** ✗ ✓（⭐ 后来已单独证明 ✓）⇒ ⭐ **不是顺序的问题** ✓。
- ⛔ **槽位 26 第一句本轮为什么仍回滚**：⭐ 计数规则（⭐ "⭐ 还没有标记 ✗" ⇒ ⭐ 给忆灵 +1 ✓；⭐ 随后打标记 ✓）⭐ 写好后，
  ⭐ 判据里忆灵的计数器读数是 **0** ✗ ⇒ ⭐ 规则没有触发 ✓ ⇒ ⭐ 触发条件还差一环 ✓（⭐ 候选：⭐ 取反条件 `!actor has_state …` ✗ 的实际语义、
  ⭐ 或 `target: "summon"` ✗ 在 `TURN_START` ✗ 上的解析 ✓）⇒ ⭐ **下一轮的第一个探针** ✓。
- ⭐⭐ **槽位 26 第二句（额外交付的冰伤）需要两处新引擎件**（⭐ 本轮查清 ✓）：
  1. ⭐ **`times_from` ✗ 只收 `event_amount` ✗ / `hit_count` ✗**（`TriggerInterpreter` L1326-1337 ✓）
     ⇒ ⭐ 「⭐ 计数器有几点就多打几次 ✗」⭐ **没有拼法**（⭐ 需要 ⭐ `times_from: "resource:<名字>"` ✗）；
  2. ⭐ **`owner_max_hp` ✗ 读的是规则主人**（⭐ `attacker = ctx.owner()` ✗ ⇒ ⭐ 昔涟 ✓），
     ⭐ 而原话说「⭐ 等同于**德谬歌** `#1`(0.3)% 生命上限 ✗」⇒ ⭐ **需要"⭐ 施放者／召唤物的生命上限 ✗"这一档** ✓。
- ⭐ **两处加载器要求（再次确认）**：⭐ 规则用到的资源**必须在角色自己的文件里也声明**（⭐ 否则 `CharacterException` ✓），
  ⭐ 而**真正的存储**在 `memosprites/1415.json` ✗ 的 `resources` ✗ ✓ —— ⭐ 两处都要写 ✓。

- ✅ **已出货（新目标第 11 件）：槽位 26 第一句**「⭐ 昔涟每从 1 个除德谬歌以外**不同的队友**处获得【追忆】后 ✗」
  ⇒ ⭐ **"⭐ 不同 ✗"这一层做出来了** ✓。
  ⭐ **形状**（⭐ 两条，⭐ 都插在文件的**最前面** ✓）：
  1. ⭐ `true_self_counts_a_new_allies_recollection_first` ✗ —— `TURN_START` ✗ ＋
     `["self_summon_count >= 1", "actor has_state 未来", "!actor has_state 从这位队友处得到过【追忆】"]` ✗
     ⇒ ⭐ `GAIN_RESOURCE{忆灵技的额外一击, 1, target: "summon"}` ✗（⭐ 计数器在**忆灵**身上 ✓）；
  2. ⭐ `true_self_marks_the_allies_that_gave_one` ✗ —— `TURN_START` ✗ ＋ `["actor has_state 未来"]` ✗
     ⇒ ⭐ `APPLY_BUFF{从这位队友处得到过【追忆】, permanent, target: "attacker"}` ✗ ✓。
  ⭐ **判据** `TrueSelfOdeDistinctAlliesTest`：`after ally1: 1 ; after ally2: 2 ; after ally1 acts again: 2` ✓；
  ⭐ **变异（去掉"还没有标记"那半）⇒ RED**（⭐ 第三次读数变成 **3** ✓）。
- ⭐⭐⭐ **本轮的决定性发现**：⭐ **【未来】的消耗规则也在同一个 `TURN_START` 上、而且排在文件前面** ✗ ⇒
  ⭐ 我的两条规则排在后面时，⭐ 【未来】**已经被消耗掉** ⇒ ⭐ 两个条件**全假** ✓
  （⭐ 诊断实测：⭐ `mark=false counter=0`，⭐ 而她的【追忆】已经是 **1** ✓ —— ⭐ 消耗那条确实生效了 ✓）
  ⇒ ⭐ **所以这两条必须插到最前面** ✓ ✓。⭐ 这也是"⭐ 文件顺序 ✗"⭐ 那条事实的**第一次实际应用** ✓。
- ⭐⭐ **两条被全量测试抓住的陷阱**（⭐ 都值得记住 ✓）：
  1. ⭐ `target: "summon"` ✗ 需要 ⭐ **`self_summon_count >= 1`** ✗ —— ⭐ **引擎自己的报错就点名了这个修法** ✓
     （⭐ 实测：⭐ 四个旧判据因此挂掉 ✓）；
  2. ⭐ **"⭐ 重复句子守卫 ✗"⭐ 抓到的是我留在文件里的**诊断探针** ✗ —— ⭐ 出货脚本只过滤了新的 id 前缀 ✓
     ⇒ ⭐ **出货前必须把 `probe_*` ✗ 清干净** ✓。
- ⛔ **仍登记（槽位 26 第二句）**：⭐ 「⭐ 额外对敌方随机单体造成 1 次等同于**德谬歌** `#1`(0.3)% 生命上限的冰属性伤害 ✗」
  —— ⭐ 需要两处新引擎件：⭐ **`times_from: "resource:<名字>"`** ✗（⭐ 现在只收 `event_amount`/`hit_count` ✓）
  ⭐ 与 ⭐ **"⭐ 施放者／召唤物的生命上限 ✗"** ⭐ 这一档 scale（⭐ `owner_max_hp` ✗ 读的是规则主人 ✓）。

- ⛔ **新目标第 18 轮：回滚（第 14 次）—— 但槽位 26 第二句的**引擎侧已跑通**，卡点缩到一个数据细节。**
- ⭐ **本轮做了什么**：为第二句加了两处引擎件（⭐ 都编译通过 ✓）：
  1. ⭐ **`times_from: "resource:<名字>"`** ✗ —— ⭐ 重复次数读**事件的 actor** ✗ 的该资源（⭐ "⭐ 计数器有几点就多打几次 ✗" ✓）；
  2. ⭐ **`actor_max_hp`** ✗ —— ⭐ "⭐ **施放者**的生命上限 ✗"（⭐ 而 `owner_max_hp` ✗ 读的是规则主人 ✓）。
- ⭐⭐ **一条新事实（有意思的坑）**：⭐ 一个新 scale **要在三处同时登记**才会被接受 ——
  ⭐ ① 共享的已知 scale 集合 ✓；⭐ ② ⭐ `TriggerInterpreter` L494 ✗ 的 `maxHpShare` ✗（⭐ DAMAGE ✗ 自己的白名单 ✓）；⭐ ③ ⭐ `literalBase` ✗ 的 `case` ✗ ✓
  —— ⭐ 只加前两处时，⭐ 加载器仍报 *"Op DAMAGE has \"scale\": \"actor_max_hp\", which is not a spelling this op knows"* ✗ ✓。
- ⚠ **卡点（一个数据细节）**：⭐ 判据里 `dragon.skillAt(1)` ✗ **返回 null** ✗
  ⇒ ⭐ 而【花与箭的舞曲】⭐ 就是**忆灵槽位 1** ✗ ✓ ⇒ ⭐ 所以这句的**触发读不到** ✓。
  ⭐ `memosprites/1415.json` ✗ 里**明明有** `{"slot": 1, "level": 10}` ✗ ✓，⭐ 而 `SummonFactory` L324-331 ✗ 也逐个 `setSkillAt(row.slot(), …)` ✗ ✓
  ⇒ ⭐ 最可能的是 ⭐ **槽位 1 就是 `COMMON` ✗ 槽**、⭐ 所以要用 ⭐ `getSkills().get(SkillType.COMMON)` ✗ ⭐ 而不是 `skillAt(1)` ✗ ✓ ——
  ⭐ **下一轮的第一行**（⭐ 另外：⭐ 改判据时**锚点不要带转义中文**，⭐ 本轮两次因此空转 ✓）。
- ⛔ **因此本轮全部回滚**（⭐ 两处引擎件 ＋ ⭐ 那条内容规则 ＋ ⭐ 判据 ✓）：⭐ 按纪律"**没有可归因效果的词汇不算出货**" ✓。
  ⭐ 但**登记已经把落法写到最后一行**：⭐ 引擎件（⭐ 三处 ✓）＋ ⭐ 内容（⭐ `CAST_SETUP` ✗ ＋ ⭐ `actor is_summon` ✗ ＋ ⭐ `from_skill_id == 1` ✗ ⇒ ⭐ `DAMAGE{scale: actor_max_hp, percent: 0.003, element: Ice, times_from: "resource:…", target: random_enemy}` ✗）
  ⭐ ＋ ⭐ 判据（⭐ 读 `COMMON` ✗ 槽 ✓）。

- ✅ **已出货（新目标第 12 件）：槽位 26 第二句**「⭐ 德谬歌施放【花与箭的舞曲】时额外对敌方随机单体造成 1 次等同于**德谬歌** `#1`(0.3)% 生命上限的冰属性伤害 ✗」
  ⇒ ⭐ **槽位 26 现在只剩第三句的「被召唤时」时序问题** ✓。
  ⭐ **两处新引擎件**：
  1. ⭐ **`times_from: "resource:<名字>"`** ✗ —— ⭐ 重复次数读**事件的 actor** ✗ 的该资源 ✓；
  2. ⭐ **`actor_max_hp`** ✗ —— ⭐ "⭐ **施放者**的生命上限 ✗"（⭐ 而 `owner_max_hp` ✗ 读的是规则主人 ✓），
     ⭐ **必须在三处同时登记**：⭐ 已知 scale 集合 ✓ ＋ ⭐ `TriggerInterpreter` L494 ✗ 的 `maxHpShare` ✗ ＋ ⭐ `literalBase` ✗ 的 `case` ✗ ✓。
  ⭐ **内容**：⭐ `CAST_SETUP` ✗ ＋ `["actor is_summon", "from_skill_id == 1"]` ✗ ⇒
  ⭐ `DAMAGE{scale: "actor_max_hp", percent: 0.003, element: "Ice", times_from: "resource:忆灵技的额外一击", target: "random_enemy"}` ✗ ✓。
  ⭐ **判据** `TrueSelfOdeExtraIceTest`：`the dance costs the enemy 469.421555197594 at zero points and 558.6753578962525 at forty` ✓
  （⭐ 40 点 ≈ 40 × 0.3% × 生命上限 × 减免 ≈ 89 ✓ ⭐ 数字自洽 ✓）；
  ⭐ **变异（去掉那条规则）⇒ RED**（⭐ 两侧读数**相等** ✓）。
- ⭐⭐⭐ **一条重要的引擎缺口（本轮量到，已登记）**：⭐
  **`summonMemosprite` ✗ 走的是 `memospriteWith` ✗，⭐ 而那条路径**不装** spec 的 `skills` ✗** ——
  ⭐ 实测：⭐ 这样造出来的忆灵 ⭐ `skillsByDataSlot()` ✗ **是空的** ✗ ✓（⭐ 而 ⭐ `summonServant` ✗ 走的 `servantWith` ✗ **装了全部 18 个槽位** ✓，
  ⭐ 且第 4 轮已证两者造的是**同一个单位** ✓）⇒ ⭐ **要读到忆灵自己的技能，必须走 servant 路径** ✓；
  ⭐ 而 ⭐ `memospriteWith` ✗ 应当与它**对称** ✓ ⇒ ⭐ **登记为引擎缺口** ✓。
- ⭐ **另一条事实**：⭐ 忆灵自己的技能**只在 `skillsByDataSlot` ✗ 里**，⭐ **不进** `SkillType.COMMON` ✗ ——
  ⭐ 实测：⭐ `SkillType.COMMON present = false` ✗ ✓（⭐ 所以 `getSkills().get(COMMON)` ✗ 对它恒为 null ✓）。

- ✅ **已出货（新目标第 13 件）：两条召唤路径对 spec 的 `skills` 对称**。
  ⭐ **形状**：⭐ 把原先内联在 `servantWith` ✗ 里的循环收成 **`SummonFactory.installSpecSkills(summon, spec, master)`** ✗，
  ⭐ 由 **`memospriteWith` ✗ 与 `servantWith` ✗ 各调用一次** ✓（⭐ 重复代码也一并消失 ✓）。
  ⭐ **判据** `MemospriteSkillsSymmetryTest`：`via summonMemosprite: [1, 2, 3, 5, 13, …, 26] ; via summonServant: [1, 2, 3, 5, 13, …, 26]` ✓；
  ⭐ **变异（去掉忆灵路径那次调用）⇒ RED**（⭐ `via summonMemosprite: []` ✗ —— ⭐ **正是上一轮量到的那个缺口** ✓）。
- ⭐ **效果**：⭐ 现在 `summonMemosprite` ✗ 造出来的忆灵**自带全部 18 个槽位** ✓ ⇒ ⭐ 判据不必再绕道 servant 路径 ✓
  （⭐ 上一轮的额外冰伤判据仍用 `summonServant` ✗，⭐ 两条路现在等价 ✓）。
- ⚠ **一条流程教训（第三次踩到，本轮记下）**：⭐ **锚点里不要带源码的中文注释** ✗ ——
  ⭐ 脚本里用转义中文写锚点时**永远匹配不上** ✓，⭐ 本轮与上一轮共因此空转 **4 次** ✓
  ⇒ ⭐ **一律用纯 ASCII 锚点**（⭐ 本轮改用 `if (spec.skills() != null && !spec.skills().isEmpty()) {` ✗ 后一次通过 ✓）。
- ⭐ **另一处小修**：⭐ 第一次替换把调用**粘到了 `return summon;` ✗ 同一行**（⭐ 功能正确、⭐ 只是格式 ✓）⇒ ⭐ 当场整理 ✓。


## 附：`1415` 忆灵技能的审计表（**2026-10-02 刷新**）

⭐ 三方对照：tbgd 的整句原话（`AvatarServantSkillConfig` 的等级行 ＋ TextMap）／我们的规则（`characters/*.json`）／我们的判据（`src/test/**`）。
⭐ 规则按**可靠信号**匹配（`from_skill_id == N`、SkillID、或原话里的「槽位 N」「忆灵技能 N」）。

| 槽位 | 技能 | 规则数 | 判据数 | 状态 |
|---|---|---|---|---|
| 13 | 献予「创世」之诗 `1141513` | 3 | 2 | ✅ 已落 |
| 14 | 献予「浪漫」之诗 `1141514` | 6 | 5 | ✅ 已落 |
| 15 | 献予「门径」之诗 `1141515` | 2 | 2 | ✅ 已落（⭐ 本次会话收紧完成） |
| 16 | 献予「纷争」之诗 `1141516` | 5 | 2 | ✅ 已落 |
| 17 | 献予「生死」之诗 `1141517` | 5+ | 6 | ✅ 已落 |
| 18 | 献予「理性」之诗 `1141518` | 6 | 6 | ✅ **本次会话收尾**（⭐ 命中次数 ＋ 【真知】时长 ✓） |
| 19 | 献予「天空」之诗 `1141519` | 5 | 3 | ✅ 已落 |
| 20 | 献予「诡计」之诗 `1141520` | 3+ | 8 | ✅ 已落 |
| 21 | 献予「负世」之诗 `1141521` | 7 | 5 | ✅ 已落 |
| 22 | 献予「海洋」之诗 `1141522` | 4+ | 3 | ✅ 已落 |
| 23 | 献予「律法」之诗 `1141523` | 2 | 3 | ✅ 已落 |
| 24 | 献予「岁月」之诗 `1141524` | 3 | 4 | ✅ 已落 |
| **25** | 献予「大地」之诗 `1141525` | **2** | **1** | ⚠ **四句里两句已落**：⭐ 「获得【献予「大地」之诗】」✓、「使龙灵行动提前 100%」✓；⛔ 仍登记：⭐ 【龙灵】下 `#3`(3) 次攻击、⭐ 【同袍】护盾量 `#4`(0.4)% 的附加伤害、⭐ 【同袍】伤害 `#1`(0.12)%、⭐ 终结技强化与护盾传递 `#5`(1.5)% |
| **26** | 献予「真我」之诗 `1141526` | **5** | **3** | ⚠ **四句里三句已落**：⭐ 第三句（⭐ 【故事】＋ 3 点消耗 ＋ 立即行动 ＋ 自动施放 ✓）、⭐ 第一句的**不同队友计数** ✓、⭐ 第二句的**额外冰伤** ✓；⛔ 仍登记：⭐ 第三句的「**或德谬歌被召唤时**」⭐ 那一半（⭐ 引擎的重复召唤是 `memospriteOf` ✗ 的 no-op，⭐ 与游戏不一致 ✓）、⭐ 「⭐ 可激活终结技 ✗」⭐ 的 12 点档 |

⭐ **判据覆盖**：⭐ **14 个槽位全都有判据** ✓（⭐ 无 `NONE` ✓）。
⭐ **规则覆盖**：⭐ 13 个槽位已完整；⭐ **25 与 26 各有已登记的剩余从句**（⭐ 上表末两行 ✓）。

- ✅ **已出货（新目标第 14 件）：槽位 25 的第三句**「⭐ 当丹恒•腾荒持有【献予「大地」之诗】时，【同袍】造成的伤害提高 `#1`(0.12)% ✗」。
  ⭐ **形状**：⭐ 同一条 `CAST_SETUP` ✗（`target == self` ✗ ＋ `actor is_summon` ✗ ＋ `from_skill_id == 25` ✗）⇒
  ⭐ `MODIFY_ATTR{ALL_DAMAGE_TYPE_BOOST, percent: 0.0012, permanent: true, **coexist: true**, max_stacks: 1, target: "holder_of:同袍"}` ✗ ✓。
  ⭐ **为什么 `holder_of:同袍`**：⭐ 【同袍】是**他自己的技能**给的 3 回合状态（`skill_bondmate_and_shield` ✗：`APPLY_BUFF{buff: 同袍, turns: 3, target: target}` ✗），
  ⭐ 而 `1414.json` ✗ 自己已用过 `holder_of:同袍` ✗ ⇒ ⭐ "⭐ 【同袍】造成的伤害 ✗"⭐ 就是**持有者的伤害** ✓。
  ⭐ **判据** `EarthOdeRaisesTheBondmatesDamageTest`：`the bondmate's damage boost reads 0.2 -> 0.20120000000000002` ✓（⭐ 正好 +0.12% ✓）；
  ⭐ **变异（去掉 `coexist`）⇒ RED**（⭐ `0.2 -> 0.0012` ✓）。
- ⭐⭐ **一条带数字的引擎事实（本轮实测）**：⭐ **同属性的 `MODIFY_ATTR` 默认是"替换"而不是"叠加"** ✗ ——
  ⭐ 第一版没写 `coexist` ✗ 时，⭐ 队友的 `ALL_DAMAGE_TYPE_BOOST` 从天赋给的 **0.2 直接变成 0.0012** ✗ ✓（⭐ 实测读数 `0.2 -> 0.0012` ✓）
  ⇒ ⭐ **凡是要"加在别人可能已经加过的属性上"，必须写 `coexist: true` ＋ `max_stacks`** ✓ ✓。
- ⚠ **仍登记（槽位 25）**：⭐ 原话说「⭐ **当…持有…时** ✗」⭐ 是**期间性**的，⭐ 而本条写成了**永久** ⇒ ⭐ 要严格期间化需要一个"⭐ 状态在则加成在 ✗"的写法 ✓；
  ⭐ 以及 ⭐ 【龙灵】下 `#3`(3) 次攻击、⭐ 【同袍】护盾量 `#4`(0.4)% 的附加伤害、⭐ 终结技强化与护盾传递 `#5`(1.5)% ✓。

- ⛔ **新目标第 23 轮：回滚（第 15 次）—— 但"⭐ 状态在则加成在 ✗"⭐ 这件通用词汇的**边界被量清了**。**
- ⭐⭐⭐ **加载器直接给出的答案**（⭐ 本轮探针触发 ✓）：
  > *"Op MODIFY_ATTR requires **`turns`** (how long the buff lasts), **`permanent: true`** (until the battle ends) or **`until`** (until its own…)"*
  ⇒ ⭐⭐ **所以 `MODIFY_ATTR` ✗ 必须自报时长** ✓，⭐ 而 ⭐ **`buff: <名字>` ✗ 只是"⭐ 筛选 ✗"、⭐ 不是生命周期绑定** ✗ ✓
  ⇒ ⭐ **「⭐ 当…持有…时，…提高 X% ✗」⭐ 目前没有精确拼法** ✓（⭐ 本会话那一处（⭐ 槽位 25 第三句 ✓）因此写成 `permanent` ✓，⭐ 已登记 ✓）。
- ⭐⭐ **正确的落法已经清楚**（⭐ 下一轮可直接做 ✓）：⭐ 把"⭐ 收回 ✗"⭐ 做出来 ——
  ⭐ ① `STATE_ENDED` ✗ **已经是事件**（`Battle` L2353：`fireTriggers(STATE_ENDED, carrier, carrier, 0, magnitude)` ✗ ✓）；
  ⭐ ② ⭐ 缺的是 ⭐ **一个能把属性修改撤掉的 op** ✗（⭐ `MODIFY_ATTR` ✗ 只能加 ✓）⇒ ⭐ 例如 ⭐ `MODIFY_ATTR{percent: -0.0012, …}` ✗ ⭐ 是否被接受 ✓ ⚠ —— ⭐ **这就是下一轮的第一个探针** ✓。
- ⭐ **另有两条读数（本轮探针顺带量到）**：
  1. ⭐ **`coexist: true` ＋ `max_stacks: 1` ✗ 仍然允许同一条规则再次加成** ✗（⭐ 实测：⭐ 第二个 `TURN_START` ✗ 上 boost 从 **0.7 涨到 1.2** ✓，⭐ 即又加了 0.5 ✓）
     ⇒ ⭐ 所以 `max_stacks` ✗ 的计数口径**不是"⭐ 同一属性最多几份 ✗"** ✓，⭐ 用时要复核 ✓；
  2. ⭐ 一回合的 buff **不会**在同一次 `beforeMove/afterMove` ✗ 里过期（⭐ 实测 `mark=true` ✓）⇒ ⭐ 判据里"⭐ 等它过期 ✗"⭐ 需要真正的回合边界 ✓。
- ⛔ **回滚内容**：⭐ 探针规则（`1415.json` ✗）＋ ⭐ 探针判据 ✓ —— ⭐ 结论已落档 ✓。
- ⭐ **槽位 25 剩余三句的落法（本轮查清 ✓）**：⭐ 【龙灵】那半句需要 ⭐ **按选择器读护盾**（⭐ `CanHit.getShield()` ✗ 可读 ✓，⭐ 但 `DAMAGE` ✗ 的 scale ✗ 是闭集 ✓）
  ＋ ⭐ **"⭐ 下 N 次攻击 ✗"** ⭐ 的计数 ✓；⭐ 而 `holder_of:同袍` ✗ 这个选择器**已经存在** ✓。

- ✅ **已出货（新目标第 15 件）：槽位 26 的 12 点档 ＋ 她终结技里三件可表达的**。
  ⭐⭐ **审计发现**：⭐ **她的终结技（`141503`）在我们这边只有 `{"op": "SUMMON"}` 一条**（`ult_summons_demiurge` ✗），
  ⭐ 而 TextMap 的原话是：⭐
  > 「⭐ 召唤忆灵德谬歌，使其立即获得 1 个额外回合并**激活全体队友的终结技**，随后**进入【往昔的涟漪】状态**，获得强化普攻。
  > 昔涟和德谬歌的**暴击率提高**，展开战技的结界并使其没有持续时间 ✗」
  ⭐ 本轮落了其中**三件**（⭐ 而第一件正是 12 点档的前置 ✓）：
  1. ⭐ `ult_enters_the_ripple_and_sharpens` ✗ —— `ULT_CAST` ✗ ＋ `actor == self` ✗ ⇒
     ⭐ `APPLY_BUFF{往昔的涟漪, permanent}` ✗ ＋ ⭐ `MODIFY_ATTR{CRIT_CHANCE, percent: 0.5, permanent, coexist: true, max_stacks: 1}` ✗ ✓；
  2. ⭐ `talent_cleanses_in_the_ripple_at_twelve` ✗ —— `RESOURCE_CHANGED` ✗ ＋
     `["self has_state 往昔的涟漪", "self_resource:追忆 >= 12"]` ✗ ⇒ ⭐ 按类别两条 `DISPEL` ✗ ✓。
  ⭐ **判据** `CyreneRippleTierTest`：`debuffs left inside the ripple = 0 ; outside it = 2` ✓（⭐ **双向** ✓）；
  ⭐ **变异（去掉状态门）⇒ RED**（⭐ `outside it = 0` ✓）。
- ⭐⭐ **数据确认（两档阈值在同一行）**：⭐ `141503` ✗ 的 **Lv10** 参数是 `[1, **24**, **0.5**, **12**]` ✗
  ⇒ ⭐ `#2` = 24（⭐ 满点档 ✓）、⭐ `#3` = **0.5（暴击率 50%）** ✓、⭐ `#4` = **12（涟漪档）** ✓ ✓。
- ⭐ **一条拼写事实**：⭐ 暴击率属性的拼写是 ⭐ **`crit_chance`（`CRIT_CHANCE`）** ✗，⭐ **不是** `CRIT_RATE` ✗（⭐ 实测被加载器拒绝 ✓）。
- ⛔ **仍登记（她终结技里剩下三件）**：⭐ 「使德谬歌**立即获得 1 个额外回合**」、⭐ 「**激活全体队友的终结技**」
  （⭐ 引擎有 `Battle.isUltraReady` ✗（`Battle:688` ✓），⭐ 但**没有把终结技置位的 op** ✗）、⭐ 「使结界**没有持续时间**」✓。

- ✅ **已出货（新目标第 16 件）：她终结技的「⭐ 激活全体队友的终结技 ✗」**。
  ⭐ **引擎**：⭐ `Battle.ULTIMATE_ACTIVATED_STATE` ✗（`终结技已激活` ✓）＋ ⭐ `isUltraReady` ✗ ⭐ **认这个状态**（⭐ 带它的单位**当场可放** ✓，⭐ 三行 ✓）。
  ⭐ **内容**：⭐ 她的终结技把这状态给 `other_allies` ✗ ✓。
  ⭐ **判据** `UltimateActivationTest`：`the ally's ultimate readiness: false -> true` ✓（⭐ 双向 ✓）；
  ⭐ **变异（去掉引擎那段）⇒ RED**（⭐ `false -> false` ✓）。
- ⭐⭐⭐ **引擎自己的注释解释了这件事的边界**（`isUltraReady` L692-699 ✓）：
  > *"the gate is the provider's to decide, not 'energy is full'. Characters who build a stack resource instead of energy
  > (**Acheron … / Feixiao … / **Cyrene** …**) become ready when their resource fills, and their energy stays at 0 by design"*
  ⇒ ⭐ **昔涟自己那一档早就是"⭐ 资源满即可激活 ✗"** ✓ ⇒ ⭐ 缺的只是"⭐ 让**别人**当场可放 ✗" ✓ ✓。
- ⭐ **一条命名说明**：⭐ `终结技已激活` ✗ **不是数据里的名字** —— ⭐ 文档描述的是**效果**而不是状态 ✓
  ⇒ ⭐ 所以它按引擎侧标记的惯例在 `Battle` ✗ 里**命名一次** ✓（⭐ 常量注释里写明了这一点 ✓）。

- ✅ **已出货（新目标第 17 件）：她终结技的「⭐ 获得强化普攻 ⋯ 普攻强化为【向着爱与明天♪】且仅能使用该普攻 ✗」**。
  ⭐ **数据测量**：⭐ 强化普攻的名字在 TextMap 的三条哈希都指向 ⭐ **`SkillTriggerKey Skill11`** ✗ ⇒ ⭐ **`SkillID 141508`** ✗
  ⇒ ⭐ 按本库惯例是**数据槽位 8** ✓。
  ⭐ **内容**：⭐ 她的终结技那条加 ⭐ `REPLACE_SKILL{skill: "COMMON", skill_id: 8, permanent: true, target: "self"}` ✗ ✓。
  ⭐ **判据** `RippleReinforcedBasicTest`：`her basic slot went 1 -> 8` ✓（⭐ 双向 ✓）；
  ⭐ **变异（`skill_id: 2`）⇒ RED**（⭐ `1 -> 2` ✓）。
- ⭐ **一条校验器事实**：⭐ **`REPLACE_SKILL` ✗ 同样必须自报时长** ✗（`turns` ✗ / `permanent` ✗ / `until` ✗）
  —— ⭐ 与 ⭐ `MODIFY_ATTR` ✗ 一致 ✓ ⇒ ⭐ **凡"⭐ 会持续的东西 ✗"⭐ 都要说清楚多久** ✓ ✓。
- ⭐⭐ **她终结技的进度（七件里五件已落 ✓）**：
  ✅ 召唤忆灵德谬歌（`ult_summons_demiurge` ✗）／✅ **激活全体队友的终结技**／✅ **进入【往昔的涟漪】**／✅ **获得强化普攻**（`REPLACE_SKILL` ✗）／✅ **暴击率提高 50%**；
  ⛔ 仍登记：⭐ 「使**德谬歌**立即获得 1 个**额外回合**」⭐ 与 ⭐ 「使**结界没有持续时间**」✓。

- ✅ **已出货（新目标第 18 件）：她终结技的「⭐ 展开战技的结界并使其没有持续时间 ✗」**。
  ⭐ **依据是引擎自己的措辞**：⭐ `AbstractBuff` ✗ 写得很清楚 —— ⭐ `permanent` ✗ = **"the buff has no turn limit and is never ticked"** ✗
  ⇒ ⭐ **「没有持续时间」就是 `permanent`** ✓ ✓。⭐ 而这一条**同时**是原话里的「**展开**战技的结界」（⭐ 没有结界时把它展开 ✓）。
  ⭐ **内容**：⭐ 她的终结技那条加 ⭐ `APPLY_BUFF{结界, permanent: true, ticks_on: self, target: self}` ✗ ✓。
  ⭐ **判据** `RippleEndlessWardTest`：`buffs a lengthening can touch: from the skill alone = 1 ; through her ultimate = 0` ✓；
  ⭐ **变异（把它写回 `turns: 2`）⇒ RED**（⭐ 两侧都变 1 ✓）。
- ⭐⭐ **一条可复用的读法（本轮发现）**：⭐ `BuffManager` ✗ **没有按名字取 buff 对象的方法** ✗，⭐ 但 ⭐ **`extendAllBuffs(turns)` ✗ 会跳过永久 buff 并返回被延长的个数** ✗
  ⇒ ⭐ **"⭐ 这个状态是不是永久 ✗"⭐ 可以由它读出** ✓ ✓ —— ⭐ 于是"⭐ 没有持续时间 ✗"⭐ 这件事**可判** ✓。
- ⭐⭐ **她终结技的进度：七件里六件已落 ✓**
  ✅ 召唤忆灵德谬歌／✅ 激活全体队友的终结技／✅ 进入【往昔的涟漪】／✅ 获得强化普攻／✅ 暴击率提高 50%／✅ **展开结界且无时长**；
  ⛔ **只剩** ⭐ 「使**德谬歌**立即获得 1 个**额外回合**」✓ —— ⭐ 它与槽位 26 的「被召唤时」**同源**：⭐ 忆灵速度据数据为 0 ⇒ ⭐ 不在行动序列 ✓。

- ✅ **已出货（新目标第 19 件）：天赋的「⭐ 可激活终结技 ✗」两条档位**（⭐ 24 点，⭐ 以及【往昔的涟漪】下的 12 点 ✓）。
  ⭐ **形状**：⭐ 两条 `RESOURCE_CHANGED` ✗ 规则 ⇒ ⭐ `APPLY_BUFF{终结技已激活, permanent, target: self}` ✗ ✓
  （⭐ 24 点一条；⭐ 12 点那条加 `self has_state 往昔的涟漪` ✗ ✓）。
  ⭐ **判据** `RecollectionUltimateReadinessTest`：
  `bare 0 = false ; reaching 12 = false ; reaching 12 in the ripple = true ; reaching 24 = true` ✓（⭐ **四面都对** ✓）；
  ⭐ **变异（去掉 12 点那条的涟漪门）⇒ RED**（⭐ `reaching 12 = true` ✓）。
- ⭐⭐⭐ **一条结构性发现（本轮量到）**：⭐ **`isUltraReady` ✗ 对她曾经在四种情形下**全是 false** ✗** ——
  ⭐ `CharacterFactory` ✗ 给她的是 ⭐ `NoConventionalEnergyProvider` ✗，⭐ 而**那个类覆盖了 `hasEnergyBar` ✗、⭐ 却没有覆盖 `canCastUltra` ✗**，
  ⭐ 于是走**默认实现**（⭐ 要求"⭐ 有能量条且能量够 ✗" ✓）⭐ 而**她没有能量条** ✗ ⇒ ⭐ **她永远不可放终结技** ✗ ✓。
  ⭐⭐ **而修好它**不需要新引擎件** ✗**：⭐ 两轮前为"⭐ 激活队友 ✗"⭐ 加的 `ULTIMATE_ACTIVATED_STATE` ✗ ⭐ 正好被 `isUltraReady` ✗ 认可 ✓
  ⇒ ⭐ 这两句只是**授予那个状态** ✓ ✓。
- ⭐ **一条再学一次的事实**：⭐ **直接 `Resources.gain` ✗ 不会触发 `RESOURCE_CHANGED` ✗** ——
  ⭐ 判据必须**经由一个 op**（⭐ 她的普攻 +1 ✓）把计数推过阈值 ✓，⭐ 否则规则什么都看不到 ✓（⭐ 本轮第一版就是这么空转的 ✓）。
- ✅ **登记项收掉**：⭐ 「⭐ 可激活终结技 ✗」（⭐ 24 点 ＋ ⭐ 12 点两档 ✓）⭐ **已由内容承担** ⇒ ⭐ 从登记里移除 ✓。

## 附：`1415` 忆灵技能的**收尾审计表**（2026-10-02 最后一次重刷）

⭐ 三方对照：tbgd 的整句原话（`AvatarServantSkillConfig` 的等级行 ＋ TextMap）／我们的规则（`characters/*.json`）／我们的判据（`src/test/**`）。
⭐ 规则按**可靠信号**匹配（`from_skill_id == N`、SkillID、或原话里的「槽位 N」「忆灵技能 N」）。

| 槽位 | 技能 | SkillID | 规则 | 判据 | 状态 |
|---|---|---|---|---|---|
| 13 | 献予「创世」之诗 | `1141513` | 3 | 2 | ✅ 已落 |
| 14 | 献予「浪漫」之诗 | `1141514` | 7 | 5 | ✅ 已落 |
| 15 | 献予「门径」之诗 | `1141515` | 3 | 2 | ✅ 本次会话收紧完成 |
| 16 | 献予「纷争」之诗 | `1141516` | 7 | 2 | ✅ 已落 |
| 17 | 献予「生死」之诗 | `1141517` | 4 | 6 | ✅ 已落 |
| 18 | 献予「理性」之诗 | `1141518` | 4 | 6 | ✅ **本次会话收尾**（命中次数 +3＋【真知】时长） |
| 19 | 献予「天空」之诗 | `1141519` | 6 | 3 | ✅ 已落 |
| 20 | 献予「诡计」之诗 | `1141520` | 5 | 8 | ✅ 已落 |
| 21 | 献予「负世」之诗 | `1141521` | 12 | 5 | ✅ 已落 |
| 22 | 献予「海洋」之诗 | `1141522` | 18 | 3 | ✅ 已落 |
| 23 | 献予「律法」之诗 | `1141523` | 2 | 3 | ✅ 已落 |
| 24 | 献予「岁月」之诗 | `1141524` | 6 | 4 | ✅ 已落 |
| 25 | 献予「大地」之诗 | `1141525` | 3 | 2 | ⚠ 四句里三句已落；⛔ 仍缺【龙灵】的下 `#3`(3) 次攻击（需“按选择器读护盾”＋“下 N 次攻击”）与护盾传递 `#5`(1.5)% |
| 26 | 献予「真我」之诗 | `1141526` | 6 | 3 | ⚠ 四句里三句已落；⛔ 第三句的「或德谬歌被召唤时」那一半（引擎的重复召唤是 `memospriteOf` 的 no-op，与游戏不一致） |

⭐ **判据覆盖：14/14**（无 `NONE`）；⭐ **规则覆盖：除 25、26 各有一小截外，全部已落** ✓

### 剩下三处（**为何未落 ＋ 落法**）

1. ⭐ **「使德谬歌立即获得 1 个额外回合」**（她终结技）**与** ⭐ **「或德谬歌被召唤时」**（槽位 26）—— **同源**：
   ⭐ 忆灵的速度**据数据就是 0**（`memosprites/1415.json` 记录了游戏行 `SpeedBase: "0"`），
   而 `Queue.addCombatant` 自述这种单位“**不会出现在行动条上、也不会轮到它**”（“skipped rather than refused”）；
   ⭐ 而重复召唤在引擎里是 `memospriteOf` 的 **no-op**（`Battle` L3281-3283）。
   ⭐ **落法**：① 把「额外回合」写成 `INSERT_ACTION`（已在槽位 26 第四句用过，实测可用）；
   ② 若要让「被召唤时」真正可观，需让 `SUMMON` 在已有忆灵时**仍然发一次到达事件**（与游戏一致），而不是直接返回已有单位。

2. ⭐ **槽位 25 的「使【龙灵】的下 `#3`(3) 次攻击造成等同于【同袍】护盾量 `#4`(0.4)% 的相应属性附加伤害」**：
   ⭐ 护盾值**可读**（`CanHit.getShield()`），但 `DAMAGE` 的 `scale` 是**闭集**，且需要“**按选择器**读某个单位的护盾”；
   ⭐ 「下 N 次攻击」也没有拼法（现有 `RAISE_SKILL_HITS` 是按槽位加段数，不是“前 N 次”）。
   ⭐ **落法**：加一个 `scale: "holder_of:<状态>的护盾"`（比照 `actor_max_hp` 的三处登记）＋ 一个“计数器消耗在攻击上”的 op。

3. ⭐ **槽位 25 的「使龙灵下一次行动时获得终结技强化、护盾量为原护盾量的 `#5`(1.5)%、不消耗强化次数」**：
   ⭐ 需要“**预约下一次行动**”（`REPLACE_SKILL` 只能改技能，不能预约行为）＋ “**护盾倍率**”。

- ⛔ **新目标第 30 轮：回滚（第 16 次）—— 而这次撞上的是一条**有意且被判据锁定**的引擎决定。**
- ⭐⭐⭐ **实测**：⭐ 我让"⭐ 重复召唤也发一次到达 ✗"⭐ 之后，⭐ **`SummonEntryTest.summoningAgainWhileOneIsOutAnnouncesNothing` ✗ 立刻红了** ✗，
  ⭐ 而它自己的失败信息就写着理由：⭐
  > *"⭐ 「**若已在场，则…**」 clauses **depend on this: no arrival, no event** ✗"*
  ⇒ ⭐⭐ **"⭐ 已在场就不发到达 ✗"⭐ 是文档里那句话的**前提** ✓，⭐ 不是疏忽 ✓** ⇒ ⭐ 我的改动**与它冲突** ✗ ✓（⭐ 与第 7 轮 `grantExtraTurn` 同类 ✓）。
  ⭐ 另有两条本会话的判据也随之中断（⭐ `TrueSelfOdeGivesStoryTest` ✗ ⭐ 读数 `1.0 -> 0.0` ✓、⭐ `TrueSelfOdeSpendsThreeStoryTest` ✗ ⭐ `0.0 -> 1.0` ✓）
  ⇒ ⭐ **一次改动同时推翻三处已出货行为** ✓ ⇒ ⭐ **回滚** ✓。
- ⭐⭐ **而回滚之后有一条更有用的结论**：⭐ 在**我们的模型**里，⭐ 「⭐ 或德谬歌被召唤时 ✗」⭐ 与「⭐ 昔涟施放终结技后 ✗」**本来就同时发生** ——
  ⭐ 因为 `ult_summons_demiurge` ✗（`SUMMON` ✗）⭐ 与 ⭐ `true_self_ode_gives_story_on_her_ultimate` ✗ ⭐ 都在 `ULT_CAST` ✗ 上 ✓
  ⇒ ⭐ **这一半从句在行为上已被另一半覆盖** ✓ ✓ —— ⭐ 所以它应当**登记为"⭐ 与另一半同址、且被「若已在场」的语义挡住 ✗"** ✓，⭐ 而不是当成缺口 ✓。
- ⛔ **回滚内容**：⭐ `Battle.summonMemosprite` ✗ 里那行 `justSummoned.add(existing)` ✗ ＋ ⭐ 对应判据 ✓。

- ⛔ **新目标第 31 轮：回滚（第 17 次）—— 但槽位 25 那句的**真正阻碍**浮出来了，⭐ 而且量到一条重要的登记规则。**
- ⭐⭐⭐ **决定性发现**：⭐ 那条规则跑起来后，⭐ 引擎报：
  > *"Character Dan Heng • Permansor Terrae (1414) has **no memosprite spec**: add `resources/memosprites/1414.json` describing its name and how its panel derives from…"*
  ⇒ ⭐⭐ **【龙灵】在我们这边根本不是忆灵** ✗ —— ⭐ `1414.json` ✗ 里既没有 `memosprites/1414.json` ✗，
  ⭐ 而现有规则只把它当作 `holder_of:同袍` ✗ 与 `cast_target` ✗ 那样的**目标**用 ✓
  ⇒ ⭐ **所以「使【龙灵】的下 3 次攻击…」⭐ 这句的第一步是"⭐ 先把【龙灵】建成忆灵 ✗"** ✓ ✓，
  ⭐ 而**不是**先做护盾档 ✓ —— ⭐ 这比我第 31 轮开头的判断更靠前 ✓。
- ⭐⭐ **一条"新 scale 要登记几处"的完整答案（本轮量到 ✓）**：⭐ **四处** ——
  ⭐ ① `literalBase` ✗ 的分派 `switch` ✗；⭐ ② 共享的已知 scale 集合 ✗；⭐ ③ `DAMAGE` ✗ 自己的白名单（⭐ `maxHpShare` ✗ 那一带 ✓）；
  ⭐ ④ ⭐ **`scaleAttribute` ✗ 的提前返回**（⭐ L2906-2915 ✓：⭐ 字面 scale **不是** AttributeType，⭐ 必须在那里说"⭐ 返回 null ✗" ✓）。
  ⭐ 缺 ④ 时的报错正是 *"Op DAMAGE has \"scale\": \"self_shield\", which is not a spelling this op knows"* ✗ ✓ —— ⭐ **而 scale 本身已经实现好了** ✓ ✓。
- ⛔ **回滚内容**：⭐ `self_shield` ✗ scale（⭐ 四处 ✓）＋ ⭐ `1414.json` ✗ 的两条内容规则（⭐ 计数器与伤害 ✓）＋ ⭐ 判据 ✓ ——
  ⭐ 按纪律"**没有读者的词汇不算出货**" ✓。⭐ 但**四处登记的清单已入档** ✓，⭐ 下次做护盾档时可直接照做 ✓。
- ⭐ **另一条本轮确认的引擎行为**：⭐ `target: "summon"` ✗ 的**加载期**校验要求规则自己带 `self_summon_count >= 1` ✗
  （⭐ 报错原文就点名了这个修法 ✓）—— ⭐ 与本会话第 17 轮、⭐ 第 20 轮遇到的同一条 ✓。

- ⭐⭐⭐ **新目标第 32 轮：槽位 25 那两句的**真正阻碍**查清了 —— 而它是一条**建模问题**，不是缺词汇。**
  ⭐ **数据事实（两处互相印证 ✓）**：
  1. ⭐ `AvatarServantConfig` ✗ 一共七行 —— 衣匠 `11402`／迷迷 `18007`／死龙•玻吕刻斯 `11407`／小伊卡 `11409`／**德谬歌 `11415`**／长夜 `11413`／晴空乐手 `11512` —— ⭐ **没有龙灵** ✗ ✓；
  2. ⭐ 而 TextMap 把它描述成**依附型**：⭐
     > 「⭐ 我方角色成为【同袍】时，丹恒•腾荒会**为其召唤【龙灵】**。**【龙灵】行动时**，解除我方全体 1 个负面效果并提供**可叠加护盾量**的护盾 ✗」
     > 「⭐ **拥有龙灵的队友攻击力提高** ✗」⭐ ／ ⭐ 「⭐ 单场战斗中，**【龙灵】依附在有召唤物的角色身**上累计行动 `#1` 次 ✗」
  ⇒ ⭐⭐ **所以它不是 `memosprites/1414.json` ✗ 能描述的那种 servant** —— ⭐ 它属于**【同袍】的持有者**，⭐ 并按那个角色的节奏行动 ✓
  ⇒ ⭐ **给它写 spec 会是在发明数据里没有的单位** ✗ ✓ ⇒ ⭐ 因此槽位 25 剩下的两句**登记在建模问题上** ✓。
- ⭐ **这也修正了上一轮的判断**：⭐ 上一轮说"⭐ 第一步是给 1414 建忆灵 ✗"⭐ —— ⭐ **不对** ✓；⭐ 正确的第一步是**决定【龙灵】在引擎里怎么表示**
  （⭐ 依附在【同袍】持有者上的**次要召唤** ✓），⭐ 而 `1414.json` ✗ 现有的 `cast_target: "holder_of:同袍"` ✗ ⭐ 正是这个方向上的近似 ✓。
- ⭐ **审计表的状态更正**：⭐ 槽位 25 的第 2、4 句 ⭐ 应记作"⛔ **阻碍：`【龙灵】`尚未在引擎里建模（⭐ 数据里它不是 servant，⭐ 而是依附型召唤 ✓）**" ✓，
  ⭐ 而**不是**"⭐ 缺 `self_shield` ✗"⭐（⭐ 那只是它需要的一部分 ✓）。

## Unicode 转义 → 字面字符（2026-10-02）

⭐ **一次性规范化**：⭐ 把被跟踪树里**所有字面** `\uXXXX` 序列转成对应字符 ✓。
⭐ **实测**：⭐ 被跟踪文件 1764 个，⭐ 其中 **746** 个含字面转义 ⇒ ⭐ 共 **53,970** 处 ✓；⭐ 实际解码 **53,915** 处／**743** 个文件 ✓；
⭐ 二次扫描可解码数为 **0**（⭐ 幂等 ✓）；⭐ **53970 − 53915 = 55** ⭐ 正是**被守卫保留**的那些 ✓ ✓。
⭐ **验证**：⭐ `compileJava` ✓、⭐ 全量 **rc 0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓。

⭐ **保留不解码的三类（判据都在脚本里，⭐ 且有实测依据 ✓）**：
1. ⭐ **控制字符**（`< U+0020`）：⭐ `light_cones/23061.json` ✗（50 处）⭐ 与 `characters/8001.json` ✗（1 处）的 `\u0007` ✗
   —— ⭐ **JSON 字符串里不允许裸控制字符** ✓，⭐ 解码会让文件失效 ✓；
2. ⭐ **引号／反斜杠**（`U+0022`／`U+0027`／`U+005C`）：⭐ `RaceToTheHorizonTest.java` ✗ L64 的两处 `\u0022` ✗
   —— ⭐ 它们在**一个 Java 字符串字面量内部** ✓，⭐ 直接解码会**截断字面量** ✓；
3. ⭐ **U+FEFF**（BOM）⭐ 与**孤立代理项**（⭐ 不成对的 `\uD800`–`\uDFFF` ✓）—— ⭐ 解码会产生非法 UTF-8 ✓；
   ⭐ 成对的代理项**已经合并**成对应字符（⭐ 星形符号等 BMP 之外的字符 ✓）。
⭐ 另有**两处 `\u0000` ✗ 保留**（`SkillCategoryAndResourceTest.java` ✗ 与 `ContentDuplicateGuardTest.java` ✗）—— ⭐ 它们是**判据自己的输入**（⭐ NUL 字符／键分隔符 ✓）✓。

⛔ **未处理（⭐ 与本轮目标无关但顺带发现 ✓）**：⭐ `RaceToTheHorizonTest.java` ✗ L64 的断言消息文本是**中英混排**的（⭐ `and the eleventh changes nothing: … 10 …` ✓），
⭐ 不影响判据 ✓；⭐ 若你要统一判据消息的语言，⭐ 这是唯一一处 ✓。

## F-3 已解决：战技点的 +1 改成由数据给出（2026-10-02）

⭐ **登记原文**（`ROADMAP.md` §12.5）：⭐「⭐ **「普攻 +1」是一刀切，强化普攻有例外** ✗：⭐ 对青雀对、⭐ **对波提欧错** ✓ —— ⭐ 登记表里**唯一一条让引擎算错数**的项，⭐ 属**数据补全** ✓」。

### ⭐ 先在 tbgd 里找权威

⭐ `AvatarSkillConfig` ✗ 里**没有** `SPBase` ✗ 这种"⭐ 技能点增减 ✗"⭐ 列（⭐ `SPMultipleRatio` ✗ 对所有技能都是 0.5 ⇒ ⭐ 不是判别字段 ✓），⭐ **但有 `BPAdd` ✗** ✓：

| 技能 | `BPAdd` ✗ |
|---|---|
| `131501` ✗ 波提欧 普攻 `蹄铁裂颅` ✗ | **1** ✓ |
| `131508` ✗ 波提欧 **强化普攻** `击锤连弩` ✗ | **`null`** ✗ |
| `120101` ✗ 青雀 普攻 `门前清` ✗ | **1** ✓ |
| `120108` ✗ 青雀 **强化普攻** `杠上开花！` ✗ | **`null`** ✗ |

⭐ 而波提欧强化普攻的**原文**自己写着：⭐「⭐ 强化普攻**无法恢复战技点** ✗，且仅能以处于【绝命对峙】的敌方目标为目标 ✓」。

### ⚠ 一个我纠正过的过度推论（⭐ 值得记 ✓）

⭐ 第一版我把 `BPAdd = null` ✗ 一律当 **0** ✗，⭐ 给全部 638 行都写了 `bp_add` ✗ —— ⭐ **过头了** ✗：⭐ 青雀的强化普攻也是 `null` ✗，⭐ 而我们自己的语料 `1201_青雀.md` ✗ 写着"⭐ 施放强化普攻后**恢复 1 点战技点** ✗" ✓
⇒ ⭐ **`BPAdd = null` ✗ 不等于 0** ✓，⭐ 它只说明"⭐ 这列不表态 ✗" ✓。⭐ 收紧后：⭐ **105 行**（⭐ 全部普攻 ✓）记 1 ✓、⭐ **只有 1 行**记 0 ✓（⭐ 波提欧强化普攻 ✓，⭐ 散文＋数据双证 ✓）、⭐ 其余**不写** ⇒ ⭐ 策略退回历史默认 ✓。

### ⭐ 落法（⭐ 四处，⭐ 每处都锚在唯一文本上 ✓）

1. ⭐ **数据**：⭐ `skills.json` ✗ 的行加 `bp_add` ✗（⭐ 值取自 tbgd `AvatarSkillConfig.BPAdd` ✗ ✓）；
2. ⭐ **bean**：⭐ `beans/Skill` ✗ 加 `@SerializedName("bp_add") Double bpAdd` ✗ ✓；
3. ⭐ **`SkillData`** ✗ 加字段（⭐ Lombok `@Getter` ✗ 自动出 `getBpAdd()` ✗ ✓）＋ ⭐ 同步两个构造点（⭐ `EMPTY` ✗ 与 `init` ✗ ✓）；
4. ⭐ **策略**：⭐ `StandardSkillPointPolicy.gainForCast` ✗ —— ⭐ 它自己的注释早就写着这里是"⭐ '⭐ 强化普攻不恢复点数 ✗'⭐ 该落的地方 ✗" ✓ ⇒ ⭐ 改成"⭐ 行里说了就用行里的 ✓，⭐ 没数据才用历史 +1 ✗" ✓。

### ⭐ 内容侧：波提欧的强化普攻此前**根本没建模** ✗

⭐ `characters/1315.json` ✗ 原来 6 条规则、⭐ **没有 `REPLACE_SKILL`** ✗ ⇒ ⭐ 那条一刀切 +1 **碰不到它** ✗ ✓。
⭐ 现按他自己的原文补上：⭐ `skill_standoff_on_both` ✗（⭐ 施放战技进入【绝命对峙】✓）⭐ 同时 `REPLACE_SKILL{skill: COMMON, skill_id: 8, turns: 2}` ✗（⭐ slot 8 = `131508` ✗ ✓）。

### ⭐ 判据与变异

⭐ `BoothillEnhancedBasicTest` ✗（⭐ 走 `Battle.useSkill` ✗，⭐ 因为策略只在那条路径上被咨询 ✓）：
`[boothill_sp] skill points after an ordinary basic = 4 ; after the enhanced basic inside Standoff = 2` ✓
⭐ **变异**（⭐ 把 `131508` ✗ 的 `bp_add` ✗ 改回 1 ✓）⇒ **RED**（⭐ `= 3` ✗ ✓）。

### ⛔ 仍登记（⭐ F-3 的另一半 ✓）

⭐ tbgd 的 `BPAdd = null` ✗ **不等于 0** ✗ ⇒ ⭐ "⭐ 某个角色的强化普攻到底给不给点 ✗"⭐ 要**逐条按散文**补 ✓（⭐ 青雀就是一个反例 ✓）。
⭐ 因此 `SkillPointGameParityTest` ✗ 的注解已改写为"⭐ **只记有证据的行** ✗"，⭐ 测试名也从 `normalAttackIsBlanketPlusOneSoEnhancedNormalsAlsoGain` ✗ 改为 `anOrdinaryBasicRestoresThePointItsRowStates` ✗ ✓。

### F-3 另一半（2026-10-02 同日收口）

⭐ 登记里那半条是"⭐ `BPAdd = null` ✗ 不等于 0 ⇒ 逐条按散文补 ✗" ✓。⭐ 我先量了"⭐ 到底哪些角色**真的会施放强化普攻** ✗" ✓ —— ⭐ 只有**四个** ✓：

| 角色 | slot 8 | tbgd `BPAdd` ✗ | 散文 | 结论 |
|---|---|---|---|---|
| 卢卡 `1111` ✗ | `111108` ✗ Sky-Shatter Fist ✗ | **1** ✓ | ⭐ 未提战技点 ✓ | ⭐ 给 +1 ✓（⭐ 收紧时已在 105 行内 ✓） |
| 加拉赫 `1301` ✗ | `130108` ✗ Nectar Blitz ✗ | **1** ✓ | ⭐ 未提 ✓ | ⭐ 给 +1 ✓（⭐ 同上 ✓） |
| 波提欧 `1315` ✗ | `131508` ✗ | `null` ✗ | ⭐「⭐ 强化普攻无法恢复战技点 ✗」✓ | ⭐ **记 0** ✓ |
| 昔涟 `1415` ✗ | `141508` ✗ | `null` ✗ | ⭐「⭐【向着爱与明天♪】**无法恢复战技点** ✗」✓ | ⭐ **记 0** ✓ |

⇒ ⭐ **四个全部钉住** ✓，⭐ 而"⭐ 未建模强化普攻的角色 ✗"⭐ **尚无可填之处**（⭐ 计算不会出错 ✓）✓。

### ⚠ 一条值得记的判据教训（⭐ 本轮自己踩的 ✓）

⭐ 赛希那条判据第一版是"⭐ 不施放（4 ✓）vs 终结技后强化普攻（3 ✓）✗" ✗ —— ⭐ 但**终结技不花战技点** ✗ ⇒ ⭐ `3` ✗ 与"⭐ 终结技花 1 ＋ 强化普攻给 1 ✗"**完全同值** ✗ ⇒ ⭐ **它区分不了任何东西** ✗ ✓。
⭐ 改成**两侧都先施战技（3 → 2 ✓）** ⇒ ⭐ 唯一差别就是普攻自己的 +1 ✓：
`[reinforced_sp] after her Skill and then an ordinary basic = 3 ; after her Skill, her ultimate and the reinforced basic = 2` ✓
⭐ **变异**（⭐ `141508` ✗ 的 `bp_add` ✗ 改回 1 ✓）⇒ ⭐ 右侧变 3 ✗ ⇒ **RED** ✓✓。

## F-1 已解决：战技点上限可以由内容上调（2026-10-02）

⭐ **登记原文**：⭐「⭐ **战技点上限不是恒定值，且引擎没有"⭐ 改队伍级资源上限 ✗"⭐ 的口子** ✗：⭐ 接口已就位（⭐ 换策略构造参数 ✓），⭐ **没有接线** ✓」。

### ⭐ 先看引擎里已有什么（⭐ 差点误判 ✓）

⭐ 引擎**已经有** `RAISE_RESOURCE_CAP` ✗ ✓ —— ⭐ 但它放宽的是**声明式资源的溢出上限** ✗（`Resource.setMaxOverflow` ✗ ✓），⭐ 与战技点池的 **cap** ✗ 是**两个数** ✓ ⇒ ⭐ 不能复用 ✓，⭐ 需要新 op ✗ ✓。

### ⭐ tbgd 的权威

⭐ 花火（⭐ `AvatarID 1306` ✗ ✓）⭐ 天赋 `130604` ✗ 原文：⭐「⭐ **花火在场时，战技点上限额外增加 `#3[i]` 点** ✗」✓，⭐ 而 `ParamList` ✗ 的 `#3` ✗ **每级都是 2** ✗ ✓ ⇒ ⭐ **+2** ✓（⭐ 与登记旧注一致 ✓）。

### ⭐ 落法

1. ⭐ **`Resource`** ✗：⭐ 上限改成 **基础值 + 加成** ✗ ✓（`max` ✗ 仍是 `final` ✓，⭐ 新增 `maxBonus` ✗ ＋ `getMax()` ✗ ＋ `raiseMax(int)` ✗ ✓，⭐ 加成下限 0 ✓）；
2. ⭐ **`SkillPointPolicy` / `StandardSkillPointPolicy`** ✗：⭐ 新增 `raiseMax(int)` ✗ ✓（⭐ 池子的主人是策略 ✓，⭐ 所以必须经它 ✓）；
3. ⭐ **op `RAISE_SKILL_POINT_CAP`** ✗：⭐ 校验（⭐ `amount` ✗ 非零、⭐ 可为负 ✓）＋ ⭐ 分派（⭐ 直接走到策略 ✓）＋ ⭐ 文档表 ＋ ⭐ `WIRED` ✗ ✓；
4. ⭐ **内容**：⭐ `characters/1306.json` ✗ 加 `talent_raises_skill_point_cap` ✗（`BATTLE_START` ✗，⭐ `amount: 2` ✗ ✓）—— ⭐ 她天赋的**另一半**（⭐ 消耗战技点加伤害 ✓）⭐ 早就在文件里了 ✓。

### ⚠ 一条我自己写错、⭐ 被判据当场抓到的断言（⭐ 值得记 ✓）

⭐ 我第一版在 `Resource.getMax()` ✗ 的 javadoc ✗ 里写"⭐ **每处**"⭐ 是否满／⭐ 还能装多少的问题都读它 ✗" ✗ —— ⭐ **当次运行就被 `SparkleTest` ✗ 推翻** ✗：⭐ 她的终结技 +4 ✗ 被夹在**基础 5** ✗、⭐ 而上限报的是 **7** ✗ ⇒ ⭐ 因为**四处仍读裸 `max`** ✗（`missingToMax` ✗／`isFull` ✗／`gainClamped` ✗／`cap` ✗ ✓）。
⭐ 改掉那四处之后，⭐ 那句话才成立 ✓ —— ⭐ **先写断言、⭐ 后补证据 ✗**⭐ 是顺序错了 ✓。

### ⭐ 判据与变异

⭐ `SparkleTest.herTalentRaisesTheTeamCap` ✗：`[sparkle_cap] the team cap with her = 7 ; without her = 5` ✗ ✓
⭐ **变异**（⭐ 删掉她那条规则 ✓）⇒ ⭐ 上限回落 5 ✗ ⇒ **RED** ✓。

## F-4 实测（2026-10-02）：角色侧已齐，光锥侧只剩 1 条，而**守卫有盲区**

### ⭐ 角色侧：tbgd 里"⭐ 提到战技点 ✗"⭐ 的 12 个 cid ✗，⭐ 逐条判过

⭐ 其中 **10 个是"⭐ 强化普攻**无法**恢复战技点 ✗"⭐ 的否定句** ✗ ✓（⭐ 属 F-3 家族 ✓）；⭐ 而这些角色的强化普攻我们**未建模** ✓ ⇒ ⭐ 无需动作 ✓。
⭐ 另外 `1130…` ✗ 系列**不在 `AvatarConfig` ✗ 里** ✓（⭐ 未出货 ✓）；⭐ 花火的秘技／终结技／天赋**三条供点与上限全部在库** ✓。

### ⭐ 光锥侧：权威对差只剩 2 个

| 光锥 | 实测 |
|---|---|
| **`23034`** ✗ 回到大地的飞行 | ✅ ⭐ **本轮补上**：⭐ 它自己的文本有**两半** ✓（⭐ 能量／圣咏 ＋ ⭐ "⭐ 每 2 次对我方单体施放战技或终结技恢复 1 战技点 ✗" ✓），⭐ 用 `23003` ✗／`23064` ✗ 现成的"⭐ 计数 ＋ 兑付 ✗"⭐ 形状 ✓，⭐ 5 档全写 ✓ |
| **`23021`** ✗ 游戏尘寰 | ✅ ⭐ **早就写好**（⭐ `flame_per_point` ✗／`flame_per_overflow` ✗ … ✓）⇒ ⭐ 我原先的登记**多余**、⭐ 且"⭐ 溢出无法表达 ✗"⭐ 的说法**错误** ⇒ ⭐ 已撤 ✓ |
| **`23037`** ✗ 向着不可追问处 | ⛔ ⭐ 文件里有"⭐ 技能／终结技伤害 ✗"✓，⭐ **缺**"⭐ 施放终结技后若本次耗能 ≥ #3 则恢复 1 战技点 ✗" ✓ —— ⭐ 而 `sp_need` ✗ **在条件 DSL 里读不到** ✓ |

### ⚠ 守卫的盲区（⭐ 本轮量到 ✓）

⭐ `LightConeRegistryTest` ✗ 的判据是"⭐ **登记的光锥必须没有规则文件** ✗" ✓ ⇒ ⭐ 它**无法表达**"⭐ 光锥存在、⭐ 但其中一条从句缺失 ✗" ✓ ⇒ ⭐ `23037` ✗ 属此类 ✓，⭐ 因此**不能登记** ✓，⭐ 只能记在本条与 §12.5 ✓。
⭐ 建议（⭐ 未做，⭐ 留待你定 ✓）：⭐ 守卫改成按 **(⭐ 光锥, 从句 ✗)** 登记 ✓，⭐ 或允许"⭐ 部分文件 ✗"⭐ 的条目带 `partial: true` ✗ ✓。

### ⚠ 我这一轮自己的两个失误（⭐ 都记下 ✓）

1. ⭐ 判据**没给角色装备光锥** ✗ ⇒ ⭐ 规则根本没加载、⭐ 测了个寂寞 ✓（⭐ 已修：⭐ `Weapon.build(23034, …)` ✗ ✓）。
2. ⭐ 我把 `23034` ✗ 的文本**截断在 260 字** ✗ 就下结论"⭐ 没有战技点从句 ✗" ✗ ⇒ ⭐ 差点漏掉 ✓（⭐ 是**文件自己的 source ✗** ⭐ 与**完整文本** ✗ 纠正了我 ✓）。

## F-7 实测（2026-10-02）：宽的那一半做了，⭐ 真正需要读数值的只有 **1 个光锥**

### ⭐ 先量"⭐ 谁需要读战技点的数量 ✗"

| 范围 | 结果 |
|---|---|
| ⭐ 出货的 **93 个角色** | **0** 条 ✓ |
| ⭐ 出货的 **59 套遗器** | **0** 条 ✓ |
| ⭐ 出货的 **166 个光锥** | ⭐ **1** 条：`23046` ✗ ✓ |

⭐ （⭐ 全库 122 条"⭐ 数量门槛 ✗"⭐ 命中里，⭐ 绝大多数在 `AvatarSkillConfigLD` ✗ —— ⭐ 一张**我们没建模的联动表** ✓，⭐ 不计 ✓。）

### ⭐ 做掉的那一半（⭐ 登记原文的字面所指 ✓）

⭐ `SkillPointPolicy.canAfford()` ✗ 只会回答"⭐ 下一次行动付不付得起 ✗"（⭐ 战技＝1 点 ✓）；⭐ 现新增 `hasAtLeast(int)` ✗ 与 `Battle.hasSkillPoint(int)` ✗ ✓（⭐ 旧方法**保留** ✓，⭐ 因为那个问题依然有用 ✓）。

### ⛔ 仍登记的那一半，⭐ 带**实测消费者**

⭐ 光锥 **`23046`** ✗「⭐ 进入战斗时，⭐ 若我方的战技点**上限**大于等于 **6** 点 ✗，⭐ 使装备者的攻击力提高 #3% ✗」（⭐ `Ability23046` ✗ 的 `#2 = 6` ✗ 每档一致 ✓、⭐ `#3` ✗ 随档 0.4→0.8 ✓）✓。
⭐ 它读的是**上限** ✗（⭐ 不是当前值 ✓），⭐ 而条件 DSL **没有任何拼法**能读战技点的数量或上限 ✗ ✓ ⇒ ⭐ 要新增一个数值变量 ✓，⭐ 涉及 `TriggerContext` ✗ 访问器 ＋ ⭐ `TriggerTable` ✗ 的解析与校验 ✓ ⇒ ⭐ **本轮未做** ✓（⭐ 本轮已在同一片区域做过两次改动，⭐ 再叠一次风险不划算 ✓）。

### ⚠ 代码注释里那两处"⭐ F-7 未解锁 ✗"⭐ 的说法也已过时 ✓

⭐ 它们写在 `ROADMAP` ✗ 的 M-25 段里（⭐ "⭐ 改'战技点上限'需要策略侧的口子 ✗" ✓）—— ⭐ 那个口子**F-1 ✗ 已经做了** ✓。

## F-9 实测（2026-10-02）：三条登记里**两条其实是内容侧、且已完成**；真缺口只有**火萤天赋的两句**

### ⭐ ① "⭐ 14 条 `params` ✗ 为空" ⇒ ⭐ 那是**无效的第二条路** ✓

⭐ `skill_effects.json` ✗ 的 `params` ✗ 列**只被 `dispatchNonDamaging` ✗ 读** ✓，⭐ 而分派器只处理 `Restore` ✗／`Defence` ✗ ✓。
⭐ 逐条查那 14 行的**内容侧**：

| 行 | 内容侧是否已实现 |
|---|---|
| `1104/4` ✗ 杰帕德天赋 | ✅ `trace_survives_a_lethal_hit_once` ✗ ＋ ⭐ 文件里有 `HEAL` ✗ ✓ |
| `8004/2` ✗／`8003/2` ✗ 开拓者战技 | ✅ `skill_reduction_and_taunt` ✗（`TAUNT` ✗ ＋ `MODIFY_DAMAGE_TAKEN` ✗ ✓） |
| `1304/7` ✗ 沙金秘技 | ✅ `trace_hot_hand_opening_shield` ✗（`SHIELD` ✗ ✓） |
| `1402/9` ✗／`8007/9` ✗／`8008/9` ✗／`1301/2` ✗ | ✅ ⭐ 文件里有 `HEAL` ✗ ＋ 相应规则 ✓ |
| ⭐ 其余（⭐ 秘技给状态 ✓） | ✅ ⭐ 状态名在角色文件里（⭐ 实测 `MISSING=[]` ✗ ✓） |

### ⭐ ② "⭐ `BUFF`／`CONTROL`／`SUMMON` ✗ 缺'⭐ 施加什么 ✗'表" ⇒ ⭐ **已经在内容里** ✓

⭐ 实测：⭐ 那些技能具名的状态（⭐ 穷观阵 ✗／生息 ✗／灼热意志 ✗／酩酊 ✗ ✓）**全部**出现在角色自己的 JSON ✗ 里 ✓ ⇒ ⭐ 不缺这张表 ✓。

### ⭐ ③ "⭐ `IMPAIR` ✗ 缺'⭐ 哪个 debuff ✗'" ⇒ ⭐ 引擎**不分派 `Impair`** ✗ ✓

⭐ 28 个 `Impair` ✗ 技能 ✓、⭐ **无一行表** ✓；⭐ 而 `dispatchNonDamaging` ✗ 只处理 `Restore` ✗／`Defence` ✗ ⇒ ⭐ 它们的 debuff 由**内容规则**施加 ✓。

### ⛔ 真缺口：⭐ 火萤 `1310` ✗ 天赋 `131004` ✗ 的三句里我们只有一句

⭐ 原文（⭐ tbgd ✓）：
1. ⭐「⭐ 生命值越低受到的伤害越低，⭐ 生命值 ≤ #3% 时减伤达到最大值，⭐ 最多降低 **#1%** ✗」⇒ ⚠ **未建模** ✓（⭐ 1310 的 ops ✗ 里没有 `MODIFY_DAMAGE_TAKEN` ✗ ✓）
2. ⭐「⭐ 战斗开始时若能量不足 **#2%** ✗ 则使其恢复至 #2% ✗」⇒ ⚠ **未建模** ✓（⭐ 这是"⭐ 能量**下限** ✗"，⭐ 不是 `GAIN_ENERGY` ✗ 的加量语义 ✓）
3. ⭐「⭐ 能量恢复至上限时解除自身所有负面效果 ✗」⇒ ✅ **已建模**（`talent_dispel_when_energy_full` ✗ ✓）

⭐ 参数实测：`131004` ✗ 的 `#1` ✗ ＝ 0.2→0.28 ✗（⭐ 逐级 ✓）、`#2` ✗ ＝ 0.5 ✗（⭐ 恒定 ✓）、`#3` ✗ ＝ 0.2 ✗ ✓。
⚠ ⭐ 第 2 句需要一个"⭐ 把能量抬到至少 X% ✗"⭐ 的口子 ✓（⭐ 现有 `GAIN_ENERGY` ✗ 是加量 ✓）⇒ ⭐ 与 F-7 ✗ 的条件、⭐ F-5 ✗ 的事件同属"⭐ 能力 ✗"类 ✓。

### F-9 真缺口的一半已出货（2026-10-02 同日）

⭐ **火萤 `1310` ✗ 天赋 `131004` ✗ 第 2 句**：「⭐ 战斗开始时若能量不足 **#2%** ✗ 则使其恢复至 #2% ✗」✓（⭐ `#2 = 0.5` ✗ 每级一致 ✓）。

⭐ **为什么必须新 op**：`GAIN_ENERGY` ✗ 是**加量**语义 ✓（⭐ 加固定值或 max ✗ 的百分比 ✓）⇒ ⭐ 从 40% ✗ 能量加 50% ✗ 会**冲过头** ✗，⭐ 而原文要的是**下限** ✗ ✓。
⭐ **落法**：⭐ op **`RAISE_ENERGY_TO{percent} ✗`** ✓（⭐ 校验要求**朴素 percent** ✗ ✓，⭐ 拒绝 `percent_from_*` ✗ —— ⭐ 因为处理器直接读它 ✓）＋ ⭐ `canHit.setCurrentEnergy(max × percent)` ✗ ⭐ 仅在低于下限时 ✓ ＋ ⭐ `WIRED` ✗ 与文档表 ✓。

⭐ **判据**（⭐ 测 op ✗ 自身的语义 ✓，⭐ 不依赖她开局能量 ✓）：
`[firefly_floor] max = 240.0 ; from 10% -> 120.0 ; from 80% -> 192.0` ✓
⭐ **变异**（⭐ 下限 50% ⇒ 40% ✗）⇒ **RED**（`from 10% -> 96.0` ✗ ✓）。

### ⛔ 第 1 句仍登记

⭐「⭐ 生命值越低受到的伤害越低，⭐ HP ≤ #3% 时减伤最大，⭐ 最多降低 **#1%** ✗」（⭐ `#1` ✗ ＝ 0.2→0.28 ✗ 逐级 ✓、⭐ `#3` ✗ ＝ 0.2 ✗ ✓）。
⭐ 需要**随当前 HP 反向变化**⭐ 的 scale ✗：⭐ 库里 82 处 HP ✗ 相关 scale ✗ **全是** `owner_max_hp` ✗／`target_max_hp` ✗（⭐ 最大值 ✓），⭐ 没有"⭐ 当前 HP 越低、⭐ 效果越强 ✗"⭐ 的形状 ✓。
⚠ 还有半句：「⭐「完全燃烧」状态下减伤维持最大值 ✗」✓ —— ⭐ 那半是**条件**（⭐ 状态存在 ✓ ⇒ `has_state` ✗ 有 ✓），⭐ 但主体仍卡在上面那条 ✓。

### F-7 第二轮（2026-10-02）：⭐ 变量做成了，⭐ 但光锥的门槛**证明不了**，⭐ 所以没出货

#### ✅ 已出货的能力

⭐ **条件变量 `skill_point_max`** —— ⭐ 名字住在**四处** ✓（⭐ 这类名字一律如此 ✓）：⭐ `TriggerTable` ✗ 的 `NUMERIC_VARIABLES` ✗ 闭合集 ✓、⭐ 取值 switch ✗（⭐ `ctx.battle().getSkillPointMax()` ✗，⭐ 无战场时 `NaN` ✗ ✓，⭐ 与 `self_max_energy` ✗ 同规 ✓）、⭐ `UnitDisciplineTest` ✗ 的 pin ✓、⭐ 文档 ✓。
⭐ 先例是 `self_max_energy` ✗：⭐ switch ✗ 里的注释说能量是**字段 ✗**不是属性 ✓，⭐ 所以要有自己的名字 ✓；⭐ 而战技点上限**更进一步** ✗ —— ⭐ 它**不属于单位** ✗，⭐ 属于战斗的策略 ✓，⭐ 所以这个变量读**战场** ✗ ✓。

#### ⛔ 为什么 `23046` 没出货（⭐ 两次实测 ✓）

⭐ 判据是"⭐ 有花火 ⇒ 门槛生效、⭐ 无花火 ⇒ 不生效 ✗" ✓，⭐ 读的是**穿戴者**的 ATK ✗ ✓（⚠ 第一版我读的是 `characters.getFirst()` ✗ —— ⭐ 那是**站第一位的人** ✗，⭐ 不是穿戴者 ✓，⭐ 这个错自己先修掉了 ✓）。

| 尝试 | 结果 |
|---|---|
| ⭐ 门槛挂 `BATTLE_START` ✗ | ⚠ ⭐ **受队伍顺序影响** ✗：⭐ 花火站**首位** ⇒ 生效 ✓（⭐ 数值确实变了 ✓）；⭐ 站**第二位** ⇒ 不生效 ✗ ✓（⭐ 两条规则同为 `BATTLE_START` ✗，⭐ 按队伍顺序跑 ✓）⇒ ⭐ 这正是"⭐ 合法但错 ✗"⭐ 的陷阱 ✓ |
| ⭐ 改挂 `WAVE_START` ✗ | ⛔ ⭐ **单波战斗里完全不触发** ✗（⭐ 两侧数值完全相同 ✓） |

⇒ ⭐ 结论：⭐ 变量是真的 ✓，⭐ 但"⭐ 光锥的开局门槛**该在何时**求值 ✗"⭐ 还没答案 ✓ ⇒ ⭐ 按纪律：⭐ **不写无法证明的内容** ✗ ⇒ ⭐ 光锥文件回滚到 HEAD ✗、⭐ 判据删除 ✓，⭐ 并把两次实测**逐条登记** ✓。

### F-7 第三轮（2026-10-02）：⭐ 去 tbgd 查"⭐ 它怎么判定 ✗"⭐ —— ⭐ 结论是**它不判定** ✓

#### ⭐ tbgd 里那句门槛的形态

| 那一半 | 形态 |
|---|---|
| ⭐ 暴击率 #1% ✗ | ✅ ⭐ **结构化属性** ✗：`AbilityProperty = [{"PropertyType": "CriticalChanceBase", "Value": …}]` ✗（⭐ 逐档 0.16 / 0.2 / 0.24 / 0.28 / 0.32 ✗ ✓） |
| ⭐ "⭐ 进入战斗时若上限 ≥ 6 ⇒ 攻击力 +#3 ✗" ✗ | ⛔ ⭐ **不在属性表、⭐ 不在条件表** ✗ —— ⭐ 只在 `SkillDesc` ✗ 的散文里 ✓ |
| ⭐ 任何提到战技点条件的表 | ⛔ ⭐ **一张都没有** ✗（⭐ 全库扫过 ✓） |

⇒ ⭐ **tbgd 不给时序** ✗ ✓：⭐ "⭐ 进入战斗时 ✗"⭐ 是**散文** ✓，⭐ 判定权在游戏引擎 ✓，⭐ 数据里没有可依据的字段 ✓ ⇒ ⭐ 我们不能"⭐ 照数据办 ✗" ✗，⭐ 只能按**语义**设计 ✓，⭐ 而语义要求"⭐ 全部开局设置**之后** ✗" ✓。

#### ⚠ 我上一轮的假设错了，⭐ 而引擎自己的注释纠正了我

⭐ 我上次让门槛挂 `WAVE_START` ✗ 并实测"⭐ 不触发 ✗" ✓，⭐ 当时只是记录现象 ✓。⭐ 这轮查到**原因** ✓：

> ⭐ `ContentWeaknessClausesTest` ✗ 的注释：⭐ **`WAVE_START` ✗ is fired by `WaveManager.nextWave()` ✗ (it is not inside `startBattle()` ✗)** ✓

⇒ ⭐ `WAVE_START` ✗ **是"⭐ 新一波 ✗"⭐ 事件** ✓，⭐ **不是开局事件** ✗ ⇒ ⭐ 单波战斗永远不发生 ✓。⭐ 顺带说明：⭐ 库里既有的 `WAVE_START` ✗ 内容（⭐ 如光锥 `23064` ✗ 的波次回点 ✓）⭐ **也只在多波关卡生效** ✗ ✓，⭐ 各判据都是**手动 `fireTriggers` ✗** ✓。

#### ⛔ 因此剩下的问题（⭐ 登记 ✓，⭐ 不猜 ✓）

⭐ "⭐ 进入战斗时 ✗"⭐ 需要一个 **`startBattle()` ✗ 内部的阶段边界** ✗：⭐ 先跑完所有开局声明／设置 ✓，⭐ 再求值依赖这些设置的从句 ✓。
⭐ 不能选的替代：⭐ ① 挂在别的开局事件上 ✗（⭐ 没有语义对得上的 ✓）；⭐ ② 改成"⭐ 上限一变化就发 ✗" ✗ —— ⭐ 那会把语义从"⭐ 开局一次 ✗"⭐ 改成"⭐ 常驻 ✗" ✓，⭐ 而**数据没有依据**支持这个改动 ✓ ⇒ ⭐ 属于发明行为 ✓，⭐ 本库拒绝 ✓。

### F-7 第四轮（2026-10-02）：⭐ 开局**第二阶段**做出来了，⭐ `23046` 出货

#### ⭐ 先量"⭐ 谁需要这个时刻 ✗"

| 范围 | 结果 |
|---|---|
| ⭐ `BATTLE_START` ✗ 上带 `when` ✗ 的规则 | **69** 条 ✓（⭐ 绝大多数是 `self has_state 秘技` ✗ ✓） |
| ⭐ 其中读**战斗级**变量的 | ⭐ **只有 `21003`** ✗（`enemy_count <= 2` ✗ ✓）—— ⭐ 而敌人数开局前就在 ✓ ⇒ ⭐ 不受顺序影响 ✓ |
| ⭐ 因而**真正需要**"⭐ 设置完成之后 ✗"⭐ 的 | ⭐ **只有 `23046`** ✗ ✓ |

⭐ tbgd 依然**没有时序概念** ✗（⭐ `EquipmentSkillConfig` ✗ 的唯一类阶段列是 `AbilityProperty` ✗ ✓、⭐ 全库无战技点条件表 ✓）⇒ ⭐ 名字由引擎定 ✓。

#### ⭐ 落法：⭐ `TriggerEvent.BATTLE_READY` ✗

`startBattle()` ✗ 的次序**本来就在处理同类担忧** ✓（⭐ 注释：⭐ "⭐ party resources **before any hook** ✗"、⭐ "⭐ technique states **before every BATTLE_START rule** ✗" ✓）⇒ ⭐ 我沿用它 ✓：

```
fireTriggers(BATTLE_START);
processRequests();          ← 第一阶段结算完
fireTriggers(BATTLE_READY); ← 新的时刻：可以读别的开局规则产生的事实
processRequests();
checkResult();
```

⭐ 用**新事件**而非**再发一次 `BATTLE_START` ✗** ✓ —— ⭐ 后者会让所有既有规则跑两遍 ✓。

#### ⭐ 判据与变异

⭐ `Cone23046CapGateTest` ✗ —— ⭐ 花火**故意站第二位** ✗ ✓（⭐ 那正是旧的 `BATTLE_START` ✗ 方案静默失效的顺序 ✓）：
`[cone23046_cap] wearer ATK with the base cap = 1639.24992 ; with the cap raised = 2663.78112` ✓
⭐ **变异**（⭐ 门槛 `>= 6` ✗ ⇒ `>= 8` ✗，⭐ 超出可达上限 ✓）⇒ **RED** ✓。

### ⚠ 我这一轮的一个错误推论（⭐ 判据当场纠正 ✓）

⭐ 我先前看到 `23046.json` ✗ 只有 **1 档**、⭐ 而它的 `skill_stacks` ✗ 用 `0.2` ✗（⭐ ＝ `#4` ✗ 的**满档**值 ✓）⇒ ⭐ 我怀疑"⭐ 单档＝满档数值 ✗" ✓，⭐ 随后用 `23064` ✗ 的逐档数值**否掉了这个怀疑** ✗ ✓，⭐ 于是把文件**补齐成 5 档** ✗ ✓ —— ⭐ 结果 `TheHellWhereIdealsBurnTest` ✗ **立刻打红** ✓（⭐ 它钉住 `0.2` ✗ ✓）。
⇒ ⭐ **该文件的约定就是"⭐ 单档装满档数值 ✗"** ✓，⭐ 而且**有判据钉着** ✓ —— ⭐ 我当时**没去找判据** ✗，⭐ 而是从**别的文件**推断约定 ✓ ⇒ ⭐ 教训：⭐ **先找钉约定的判据，⭐ 再推约定** ✓。

### F-9 最后一句（2026-10-02）：⭐ 曲线**两处都查过了**，⭐ 确实不可建模

⭐ 原文：⭐「⭐ 生命值越低受到的伤害越低，⭐ 生命值小于等于 **#3 = 20%** ✗ 时减伤效果达到最大值，⭐ 最多降低 **#1 = 0.2→0.28** ✗。「完全燃烧」状态下减伤效果维持最大值 ✗」（⭐ `131004` ✗ ✓）。

#### ⭐ 查证过程（⭐ 两处负结果 ✓）

| 来源 | 结果 |
|---|---|
| ⭐ **tbgd** ✗ | ⭐ `AvatarSkillConfig` ✗ 的 `131004` ✗（⭐ 含星魂变体 `1131004` ✗）⭐ 只有这段原文 ✓；⭐ **行迹／星魂／等阶表都没有重述** ✗；⭐ 属性表也没有公式列 ✓ |
| ⭐ **规格 `HSR.md` ✗** | ⭐ 275 行 ✓，⭐ 伤害章讲的是**抗性／防御的夹取** ✓（`1 - x` ✗ 夹在 0.01/0.99 ✗、⭐ `1 - x` ✗ 夹在 0.2 ✗）⭐ **没有**由拥有者 HP ✗ 驱动的曲线 ✓ |

⭐ 仓库里另外两处提到"⭐ 生命值越低 ✗"⭐ 的文件就是 `GAPS.md` ✗ 与 `ROADMAP.md` ✗ ✓ —— ⭐ **是本项目自己的登记** ✓，⭐ 不是独立来源 ✓。

#### ⛔ 为什么不能近似

⭐ 两处**只有端点** ✓：⭐ (⭐ HP ≤ 20% ⇒ 减伤 = #1 ✗ ✓) ⭐ 与"⭐ 越低越多 ✗"⭐ 的单调性 ✓。
⭐ 若在 20% ✗ 处做**阶梯** ✗ ⇒ ⭐ 每一个高于 20% ✗ 的 HP ✗ 都会被**低估** ✓（⭐ 而原文说那里**有**减伤 ✓，⭐ 只是更少 ✓）；⭐ 若做**线性** ✗ ⇒ ⭐ 是**凭空造数** ✗ ✓。
⇒ ⭐ 两者都属"⭐ 合法但错 ✗"⇒ ⭐ **登记** ✗ ✓，⭐ 并写明"⭐ 需要一条由当前 HP ✗ 反向驱动的 scale ✗" ✓（⭐ 库里的 82 处 HP scale ✗ 全是**最大值** ✗ ✓）。

## F-5 尝试与回滚（2026-10-02）：⭐ 量清了落点，⭐ 但判据三试未编译 ⇒ ⭐ 不发货

### ⭐ 量到的三件事（⭐ 都是登记原文的确认或修正 ✓）

1. ⭐ **`EnemySkill.getData()` ✗ 返回 `null` ✗ 是有意设计** ✓ —— ⭐ 它自己的 javadoc ✗ 写明：⭐ 它不走 `SkillData` ✗ 的乘区模型 ✓（⭐ 而是"⭐ 攻击 × 倍率 × 段数 ✗" ✓），⭐ "⭐ **所以 `getData()` ✗ 返回 `null`、⭐ `execute` ✗ 完全自定义 —— ⭐ 也正因如此 `SkillExecutor` ✗ 不能复用它 ✗**" ✓。
2. ⭐ **`SKILL_CAST` ✗ 的两个发出点**：⭐ `SkillExecutor:197` ✗（⭐ `BPSKILL` ✗ ✓）⭐ 与 `TriggerInterpreter:3428` ✗（⭐ **被命令的施放** ✓，⭐ 注释：⭐ "⭐ a commanded cast IS a real cast ✗" ✓）。⭐ 而 `EnemySkill.execute` ✗ **只发 `SUMMON_ATTACK` ✗** ✓。
3. ⭐ **阵营词是单边的** ✓：⭐ `TriggerTable:1026` ✗ 有 `IS_ALLY` ✗、⭐ `1046` ✗ 有 `IS_OTHER_ALLY` ✗ ✓（⭐ 条件类 `IsAlly` ✗ 在 `2633` ✗，⭐ 实现 `Condition, PartyCondition` ✗ ✓），⭐ **没有 `is_enemy`** ✗ ✓。

### ⭐ 下一步的落点（⭐ 已量好，⭐ 可直接照做 ✓）

| 件 | 落点 |
|---|---|
| ⭐ 敌方出手的事件 | ⭐ `EnemySkill.execute` ✗ 里 `SUMMON_ATTACK` ✗ 那一行的旁边 ✓，⭐ 仅当 `user instanceof Enemy` ✗ ✓；⭐ 目标建议传 **victim** ✗（⭐ 好表达"⭐ 敌人攻击了我 ✗" ✓，⭐ 而 `SUMMON_ATTACK` ✗ 传的是 `null` ✗ ✓） |
| ⭐ `is_enemy` ✗ | ⭐ 四处：⭐ `IS_ENEMY` ✗ 模式（⭐ 照 `IS_ALLY` ✗ ✓）、⭐ 解析分支（⭐ 照 `1403` ✗ 那段 ✓）、⭐ 条件类（⭐ 照 `IsAlly` ✗ ✓）、⭐ `requireCarriedParty` ✗ 链 ✓ |

### ⚠ 为什么回滚

⭐ 我写了判据三次 ✓，⭐ 三次都**编译不过** ✗（⭐ 依次：⭐ 用了不存在的 `DamageType.SINGLE` ✗ ✓、⭐ 以及**写好的实现脚本被我删掉却没运行** ✗ ⇒ ⭐ `TriggerEvent.ENEMY_ATTACK` ✗ 当时根本不存在 ✓）。
⭐ 而**没判据的引擎改动不予发货** ✓ ⇒ ⭐ enum ✗ 与 `EnemySkill` ✗ 两处改动**回滚到 HEAD** ✗ ✓、⭐ 判据删除 ✓，⭐ 并把上面那份落点清单**登记** ✓，⭐ 让下一轮**一次做成** ✓。

### ⚠ 本会话我第三次踩同一个坑

⭐ "⭐ **写了脚本但没运行** ✗"⭐ —— 前两次是 `%` ✗ 格式化崩溃 ✓，⭐ 这次是**删除与运行写在同一行、⭐ 顺序颠倒** ✗ ✓。⭐ 已记入台账 ✓。

## F-5 已出货（2026-10-02 同日，第二次尝试）：⭐ 敌方出手现在会宣告

⭐ 上一轮三试未编译 ⇒ ⭐ 回滚 ✓；⭐ 这一轮**先把 API ✗ 抄准**（⭐ 读一个能跑通的同类判据 `ShieldGrantedEventTest` ✗ ✓）⭐ 再一次做成 ✓。

### ⭐ 落法

| 件 | 内容 |
|---|---|
| ⭐ 事件 | ⭐ `TriggerEvent.ENEMY_ATTACK` ✗，⭐ 声明在 `SUMMON_ATTACK` ✗ 旁边 ✓，⭐ javadoc ✗ 写明"⭐ 为什么需要它 ✗" ＋ ⭐ "⭐ 为什么只对 `Enemy` ✗ 发 ✗" ✓ |
| ⭐ 发出点 | ⭐ `EnemySkill.execute` ✗ 的 `SUMMON_ATTACK` ✗ 那行之后 ✓，⭐ **仅当 `user instanceof Enemy`** ✗（⭐ 忆灵走同一个类 ✓、⭐ 已有 `SUMMON_ATTACK` ✗ ✓），⭐ 且**真的打中了人**才发 ✓ |
| ⭐ 目标 | ⭐ **victim** ✗（⭐ 而 `SUMMON_ATTACK` ✗ 传 `null` ✗ ✓）⇒ ⭐ "⭐ 敌人攻击了**我** ✗"⭐ 就是 `target == self` ✗ ✓ |
| ⭐ 判据 | ⭐ `EnemyAttackEventTest` ✗：⭐ 两个角色装**同一条**规则（⭐ `target == self` ✗ ⇒ ⭐ 攻击力 +50% ✗ ✓），⭐ 敌人只打其中一个 ✓ |
| ⭐ 变异 | ⭐ 删掉宣告块 ⇒ ⭐ **RED** ✗（⭐ 被打者也不涨 ✓） |

⭐ 读数：`[enemy_attack] hit 894.13632 -> 1243.40832 ; spared 543.312 -> 543.312` ✓

### ⚠ 判据第一版我写错了断言（⭐ 值得记 ✓）

⭐ v1 ✗ 我断言"⭐ 敌人出手**不该**触发 `SUMMON_ATTACK` ✗"✗ —— ⭐ 实测 `CRIT_ATTACK 0.5 → 1.0` ✗ ⇒ ⭐ **它确实会触发** ✗ ✓。
⭐ 而 `EnemySkill` ✗ 的**注释原文**早就写着：⭐ **"⭐ Fired for a summon of either camp ✗"** ✓ ⇒ ⭐ **是我断言反了** ✗，⭐ 不是引擎错 ✓。
⇒ ⭐ v2 ✗ 改成真正双向的检查：⭐ 用 `target == self` ✗ ＋ ⭐ 两个角色 ✓ ⇒ ⭐ **只有被打的那个该涨** ✗ ✓ —— ⭐ 这才测到了我这次的设计选择（⭐ 目标传 victim ✗ ✓）。

### ⚠ 一个被修正的期望

⭐ 登记行原先暗示 F-5 ✗ 做完能顺带解开 `23037` ✗ ✓ —— ⭐ **不会** ✗：⭐ 那条要的是读 **`sp_need`** ✗ ✓，⭐ 与敌方事件无关 ✓ ⇒ ⭐ 已在新行里改正 ✓。

### ⛔ 仍登记两件

1. ⭐ **`is_enemy`** ✗ —— ⭐ 阵营词只有 `is_ally` ✗／`is_other_ally` ✗ ✓；⭐ 落点四处已量（⭐ 模式／⭐ 解析分支／⭐ 条件类照 `IsAlly` ✗／⭐ `requireCarriedParty` ✗ 链 ✓）✓
2. ⭐ **"⭐ 敌人施放技能 ✗" ✗** 本身不存在 ✓ —— ⭐ 敌人的动作是一次**攻击** ✓ ⇒ ⭐ 该从句应读作"⭐ 敌人出手 ✗" ✓

### F-5 收口（2026-10-02 同日，第三次尝试）：⭐ `is_enemy` ✗ 补齐，⭐ 阵营词两侧齐全 ✓

⭐ 上一轮出货了**敌方出手的事件** ✓，⭐ 这一轮补**阵营词** ✓。⭐ 做法照旧：⭐ **先把要镜像的代码读全** ✓（⭐ `IsAlly` ✗ 的 `partyOf` ✗／`test` ✗／`source` ✗ 三方法 ✓ ＋ ⭐ 它依赖的 `requireCarriedParty` ✗／`requireStateSubject` ✗ ✓）⇒ ⭐ 一次编译过 ✓。

| 处 | 内容 |
|---|---|
| ⭐ 模式 | ⭐ `IS_ENEMY` ✗（⭐ 照 `IS_ALLY` ✗ ✓），⭐ javadoc ✗ 记"⭐ 阵营词原本是单边的 ✗" |
| ⭐ 解析分支 | ⭐ 照 `is_ally` ✗ 那段 ✓（⭐ 同样的"⭐ 后面不许再写东西 ✗"⭐ 校验 ✓，⭐ 提示语给 `actor is_enemy` ✗／⭐ `!actor is_enemy` ✗ ✓） |
| ⭐ 条件类 | ⭐ `IsEnemy implements Condition, PartyCondition` ✗ ✓，⭐ `partyOf` ✗ 与 `IsAlly` ✗ 逐字相同 ✓，⭐ `test` ✗ 答 **`ctx.battle().enemies.contains(who)`** ✗ ✓ |

⭐ **判据**（⭐ 用刚出货的 `ENEMY_ATTACK` ✗，⭐ 两条规则靠**不同属性**区分 ✓）：
`[is_enemy] ATTACK 894.13632 -> 1243.40832 ; CRIT_ATTACK 0.5 -> 0.5` ✓
⭐ **变异**（⭐ `enemies.contains` ✗ ⇒ `allies.contains` ✗）⇒ ⭐ **RED** ✗（⭐ 两侧都不命中 ✓）✓。

⭐ ⚠ 两次变异都做了 ✓：⭐ 事件那侧删宣告 ⇒ RED ✓；⭐ 阵营这侧换边 ⇒ RED ✓ ⇒ ⭐ **两半各自承重** ✓。

### F-9 补充（2026-10-02）：⭐ 查同族从句，⭐ 顺手查出一条**静默遗漏** ✗

⭐ 上次结论是"⭐ 火萤那条第 1 句不可建模 ✗"✓。⭐ 这次我先去找**同族从句** ✓（⭐ 库里的登记一向引用语料文件名 ✓，⭐ 所以语料是权威入口 ✓）：

| 同族从句 | 状态 |
|---|---|
| ⭐ `1217` ✗ 藿藿：「⭐ 生命值越低治疗量越高，⭐ 最多提高 **80%** ✗」 | ⛔ ⭐ **既未建模、⭐ 也未登记** ✗ ✓ —— ⭐ 而我自己的扫描**只在 `1310` ✗ 上找过** ✗ ✓ ⇒ ⭐ 这次补登记到她自己的文件里 ✓ |
| ⭐ `1002` ✗／`1102` ✗／`1206` ✗ | ✅ ⭐ 已建模，⭐ 而做法是 **`hp_percent <= 0.5` ✗ 与 `hp_percent > 0.5` ✗ 两支** ✗ ✓ ⇒ ⭐ 即**分段阈值** ✓，⭐ 不是曲线 ✓ |

⇒ ⭐ **库里的"⭐ 随 HP 变化 ✗"⭐ 做法已经明确** ✓：⭐ 在文本**给了阈值**的地方分两支 ✓；⭐ 而火萤与藿藿这两句**只给端点、⭐ 不给阈值** ✗ ⇒ ⭐ 没有可分之处 ✓，⭐ 单阶梯会低估另一侧 ✓、⭐ 曲线则是凭空造数 ✓ ⇒ ⭐ **登记** ✓。

⭐ ⚠ 附带教训：⭐ 我上次只搜了**出问题的那个角色** ✗，⭐ 没搜**同族的其他角色** ✗ ⇒ ⭐ 差点漏掉藿藿这条 ✓。⭐ 已记入台账 ✓。

### ⚠ F-9 补充的更正（2026-10-02 同日）：⭐ 登记**不能**写成一条空规则

⭐ 我先把藿藿那条登记写进了 `1217.json` ✗ 的规则列表 ✓（⭐ 一条 `do: []` ✗ 的 `BATTLE_START` ✗ 规则 ✓）⇒ ⭐ **全量立刻 22 红** ✗ ✓ —— ⭐ 加载守卫拒绝**没有效果的规则** ✓（⭐ 这本身是好事 ✓：⭐ 它挡住了"⭐ 占位规则 ✗" ✓）。

⭐ 而且 ⭐ `src/main/resources/characters/` ✗ 下**没有 `_unmodelled.json` ✗**（⭐ 我确认过 ✓；⭐ 遗器与光锥才有 ✓）⇒ ⭐ 角色侧的登记要么写在**某条真规则的 `note` ✗** 里 ✓（⭐ `1208` ✗／`1308` ✗ 就是这么做的 ✓），⭐ 要么写在 `ROADMAP` ✗／`GAPS` ✗ ✓。
⇒ ⭐ 文件**已回滚 HEAD** ✗ ✓，⭐ 登记保留在 `ROADMAP §12.5` ✗ 与本节 ✓。

### F-9 深入（2026-10-02）：⭐ 火萤天赋有**两处硬阻塞** ✗，⭐ 而不只是"⭐ 缺曲线 ✗"

⭐ 上次的结论"⭐ 缺插值 ✗"✓ 是对的 ✓，⭐ 但不完整 ✓。⭐ 这轮把参数与引擎都量到底 ✓：

#### ⭐ ① 数据侧：⭐ 确实没有插值（⭐ 这次是**反证** ✓）

| 行 | `ParamList` ✗（⭐ L1 ✗ ⇒ ⭐ L15 ✗ ✓） |
|---|---|
| ⭐ `131004` ✗（⭐ 本体 ✓） | `[0.2, 0.5, 0.2, 0.1]` ✗ ⇒ `[0.5, 0.5, 0.2, 0.4]` ✗ |
| ⭐ `1131004` ✗（⭐ 星魂变体 ✓） | ⭐ **逐字相同** ✗ ✓ |

⇒ ⭐ 参数只有四个 ✓：⭐ `#1` ✗ 最大减伤（⭐ 0.2→0.5 ✗ ✓）、⭐ `#2` ✗ 能量下限 0.5 ✗、⭐ `#3` ✗ HP ✗ 阈值 0.2 ✗、⭐ `#4` ✗ 效果抵抗（⭐ 0.1→0.4 ✗ ✓）⭐ —— **没有斜率** ✗ ✓。

#### ⭐ ② 引擎侧：⭐ "⭐ 已损失生命 ✗"⭐ 有 ✓，⭐ 但是**绝对量** ✗、⭐ 且只在 `target` ✗ 侧 ✓

| 现有 | 形态 |
|---|---|
| ⭐ `target_lost_hp` ✗ | ⭐ `Math.max(0, target.getMaxHp() - target.getCurrentHp()) * share + flat` ✗ ✓（⭐ 供治疗／护盾 ✓） |
| ⭐ `target_current_hp` ✗ | ⭐ `target.getCurrentHp() * share + flat` ✗ ✓ |
| ⭐ 封顶机制 ✗ | ✅ ⭐ `capped = Math.min(capped, resolveScale(cap_scale, cap_percent, ctx))` ✗ ✓ ⇒ ⭐ 阈值饱和**可以复用** ✓ |

⇒ ⭐ 缺的是"⭐ **分数形式的、⭐ owner ✗ 侧的**已损失生命 ✗" ✗ ✓ —— ⭐ 因为要驱动的是**百分比减伤** ✗ ✓，⭐ 而现有 scale ✗ 全是"⭐ 基数 × share ✗"⭐ 的**量 ✗** ✓。

#### ⭐ ③ 同一句里的"⭐ 效果抵抗 + #4% ✗"⭐ 也缺 ✗

⭐ `characters/1310.json` ✗ 里**没有** ⭐ `EFFECT_RES` ✗／抵抗 ✓（⭐ 实测 ✓）；⭐ 而 `AttributeType` ✗ 里**没有任何名字含 `RES`** ✗ ✓ ⇒ ⭐ "⭐ 效果抵抗 ✗"⭐ 这个**属性本身不存在** ✓ ⇒ ⭐ 要加它属于**新增属性** ✗（⭐ 影响面比前两项更大 ✓）。

#### ⭐ 结论

⭐ 火萤天赋四句：⭐ ① 满能驱散 ✅（⭐ 原有 ✓）、⭐ ② 能量下限 ✅（⭐ 上轮出货 ✓）、⭐ ③ 随 HP ✗ 反向减伤 ⛔（⭐ 缺分数型 owner ✗ scale ✗ ＋ ⭐ 数据无插值 ✓）、⭐ ④ 效果抵抗 ⛔（⭐ 缺属性 ✓）⇒ ⭐ **③④ 都登记** ✓，⭐ 同族的藿藿一并 ✓。

## 开始做角色（2026-10-02）：⭐ `1503` 真珠的第一步，以及⭐ **一个角色是两份文件** 的发现

### ⭐ 积压清单（量过）

| 类 | 内容 |
|---|---|
| ⭐ **完全没有文件** | ⭐ **`1503` = Pearl（真珠）** ✓ —— 而语料里**有** `1503_真珠.md`（ 401 行）✓ |
| ⭐ 最薄 | `1502`（1 条）、`1204`/`1210`/`1213`/`1312`/`1506`/`1510`（2 条） |

⚠ 一个反面发现：⭐ 90/93 个角色文件里都有“登记/未建模”字样，**但大多是笔记里的历史叙述**
（“WRITTEN on …”、“registered as a data gap”）✓ —— 所以那不能当积压清单用，得看“谁根本没文件”与“谁最薄”。

### ⭐ 已写：`characters/1503.json`（ 3 条规则 + 2 个计数）

| 件 | 依据 |
|---|---|
| ⭐ 计数【好活当赏】（`max 50`） | 天赋：“持续时间无限，且持有上限为 50 点”✓ |
| ⭐ 计数笑点（`scope: PARTY`） | 终结技的额外回合：“获得 60 点笑点”✓（与 `1502` 共用同一个队伍级名字） |
| ⭐ 战技 | “获得 15 点【好活当赏】…为我方全体回复 12% 防御力 + 240…**额外**为**最低**者再回一次”✓ |
| ⭐ 秘技 | “下一次战斗开始时真珠获得 20 点”✓ |
| ⭐ 星魂 1 免死 | “若我方目标受到致命伤害…回复其生命上限 50%…单场可触发 2 次”✓（逐字镜像 `1217.json` 的 E2 ✓） |

### ⭐ 未写（登记在文件的 `note` 里）

⭐ 天赋的抵御值（每点 = 200 抵御值，代价抵挡 60% 伤害）；
⭐ 天赋的“我方目标当前生命≤ 50% 时受到的伤害降低 30%”；
⭐ 终结技与行迹里“队伍中欢榆命途人数 = 1/2/3/4”的分档；
⭐ 【美学底本】【深度学习】与额外回合；⭐ 星魂 1/2 的欢榆度成长；⭐ 等级惯例（星魂 3/5）。

### ⭐⭐ 本轮最重要的发现：**一个角色是两份文件**

⭐ 我给 `1503` 写了规则文件后，全量 **rc 0** ✓（说明格式合法），但判据一跑就报：

> `CharacterException$CharacterNotFoundException: Character '1503' not found`
> 　at `Character$Builder.validateAndGet(Character.java:439)`

⇒ ⭐ 除了 `characters/<cid>.json`（规则）之外，还需要**面板数据**（HP / 攻击 / 防御 / 速度 / 能量 / 命途 / 元素），
建构器从那里读。⭐ 而语料里**数字全有** ✓：攻击 **63.36**、生命 **163.68**、防御 **99**、速度 **99**、仇恨 **100**、
暴击 **0.05**、暴伤 **0.5**、能量 **180**、冰 / 欢榆（已从 `AvatarConfig` 交叉验过：`Elation` + `Ice` + `SPNeed 180` ✓）。
⇒ ⭐ 下一轮就是**算数而不是调研**：把面板落库，判据就能跑。

⚠ 因此本轮**删掉了那条判据**（它在面板就位前不可能通过），只保留规则文件✓ ——
它已由全量用例验证“加载合法”✓，但**规则本身尚无判据**，这一点写在这里不模糊。

### ⭐⭐ 做角色的完整配方（ 2026-10-02 量测）：**四份文件 + 一处计数**

⭐ 我给 `1503` 写完规则后逐步补，三个守卫判据把清单逐条说了出来：

| 缺的 | 守卫的报错（原文） |
|---|---|
| ⭐ 面板数据 | `CharacterException$CharacterNotFoundException: Character '1503' not found`（`Character$Builder.validateAndGet`） |
| ⭐ `data/skills.json` 的技能行 | `cid=1503's COMMON parameter table is empty (data not fetched?)` 与 `cid=1503's MAZE parameter table is empty` |
| ⭐ 名册计数（5★） | `number of 5★ ==> expected: <70> but was: <71>` |

⇒ ⭐ 一个角色：① `characters/<cid>.json`（规则）、② `data/character_data.json`（面板）、
③ `data/character_id_mappings.json`（cid → 双语名）、④ `data/skills.json`（每槽的技能行），
加上⑤守卫里的名册计数（`CharacterFactoryTest`）。

#### ⭐ 面板字段（照 `1502` 逐字比过）

`id` / `attribute`（小写元素）/ `short_name` / `max_energy` / `name{chinese,english}` / `mt`（命途）/
`rarity` / `health` / `attack` / `defence` / `speed` / `aggro` / `crit_chance` / `crit_attack`。
⭐ 真珠的值已从语料量好（并与 `AvatarConfig` 交叉验过 `Elation`+`Ice`+`SPNeed 180`）：
`ice` / `pearl` / 180 / 真珠 / Pearl / `elation` / 5 / **163.68** / **63.36** / **99** / **99** / **100** / 0.05 / 0.5。

#### ⭐ 技能行的字段集（照 `1502` 量的，共 12 个）

`attack_type` / `element` / `max_level` / `name{chinese,english}` / `param_list`（**全等级**）/ `skill_effect` /
`skill_id` / `skill_introduction{chinese,english}` / `sp_base` / `sp_need` / `stance_list{single,all,spread}`，
基础攻击另带 `bp_add`。

#### ⭐ 槽位映射（已从 tbgd 量出真珠的每一行）

| 槽 | tbgd 的 `SkillTriggerKey` | 技能 ID | 形态 |
|---|---|---|---|
| 1 | `Skill01` | 150301 | `Normal` / `SingleAttack` / 10 级 |
| 2 | `Skill02` | 150302 | `BPSkill` / `Defence` / 15 级 |
| 3 | `Skill03` | 150303 | `Ultra` / `Support` / 15 级 |
| 4 | （天赋，待确认键） | 150304 | 天赋 |
| 6 | （空键） | 150306 | `MazeNormal` / `MazeAttack` / 1 级 |
| 7 | `SkillMaze` | 150307 | `Maze` 秘技 |
| 8 | `Skill11` | 150308 | `Normal` / `AoEAttack` / 强化普攻一 |
| （另一强化普攻） | （待确认） | （「行笔，绘制末浪」） | 强化普攻二 |
| 20 | `Skill04` | 150320 | `ElationDamage` / `Support` / 15 级 |

⭐ 每行的 `AttackType` / `SkillEffect` / `MaxLevel` / `ShowStanceList` / `StanceDamageType` / `ParamList`（全等级）/
`SkillName` / `SkillDesc` 在 `AvatarSkillConfig` 里**完整**，且另外 93 个角色就是这么生成的，
所以这一步是**机械活**。

⚠ 本轮为什么回滚：⭐ 我已有三份文件中的**两份**（面板与映射）写对了、
第三份（技能行）尚未生成，而三个守卫因此红着。⭐ 在一个已经很长的会话尾部，
“留一棵红树 + 半成品”比“回滚 + 完整配方”差得多，所以选后者。
⭐ `characters/1503.json`（规则）**保留**：它由全量用例验证“加载合法”✓，
但**规则本身仍无判据**（判据在面板就位后才能跑）—— 这一点不模糊。

### ✅ 修正：真珠已在同日**完整落地**，不是“已撤回”

⭐ 上一节写的“回滚 + 配方”很快就被推翻了，因为四份文件里的技能行**是机械活**：
⭐ 我从 tbgd 的 `AvatarSkillConfig` 直接生成了**八个槽**（1 / 2 / 3 / 4 / 6 / 7 / 8 / 20），
字段集逐字照 `1502` 的样子（`attack_type` / `element` / `max_level` / `name` / `param_list`（全等级）/
`skill_effect` / `skill_id` / `skill_introduction` / `sp_base` / `sp_need` / `stance_list`，基础攻击另带 `bp_add`）。

⭐ **最终四件套**：① `characters/1503.json`（规则）、② `data/character_data.json`（面板）、
③ `data/character_id_mappings.json`（双语名）、④ `data/skills.json`（技能行），
加⑤ `CharacterFactoryTest` 的 5★ 计数 **70 → 71**。

⭐ **判据**：`PearlSkillHealTest` ✓ 双向——
`[pearl] pearl gained 327.318 ; the lowest ally gained 654.636 ; counter 0 -> 15`（最低者恰好**两倍**）；
变异（删掉第二段治疗）⇒ **RED**（两者都是 327.318）。

⭐ 经验：⭐ 守卫（槽位、名册）**强制四件套原子落地** —— ⭐ 只提交规则文件会立刻 3 红，
这是个好守卫；⭐ 而“生成技能行”这一步虽然看着吓人，实际上全是查表。

### 真珠补从句（2026-10-02）：行迹「感知容差」的第二半已写

⭐ 原文：「我方目标回合开始时，真珠获得 5 点【好活当赏】，最多 50 点」。
⭐ **照现成样板**：`1201.json`（青雀）的 `talent_draw_on_ally_turn` 是**同一形状**
（`TURN_START` + 阵营条件 + `GAIN_RESOURCE`），所以零引擎改动。
⭐ 用 `actor is_ally` 而非 `is_other_ally`：原文说“我方目标”，而 `is_ally` 的文档明记
“counts the owner herself”。⭐ 上限由声明里的 `max: 50` 兑现，**不重复写**。

⭐ **判据**：`PearlTraceCounterTest` ——
`[pearl_trace] counter 0 -> 5 after one ally turn ; capped at 50`（一次加 5、上限确实在 50）；
⭐ **变异**（`amount` 5 → 7）⇒ **RED**。

### ⭐ 同日量到的四个“能不能写”结论（供后续复用）

| 问 | 答 |
|---|---|
| ⭐ 减伤的符号约定 | ✅ **负值就是减伤**：`MODIFY_DAMAGE_TAKEN` 已有 `percent: -0.5` 出货 3 次 |
| ⭐ 抗性穿透的拼法 | ✅ `RESISTANCE_REDUCTION`（1006 / 1203 / 1218） |
| ⭐ “队友回合开始 + 资源” | ✅ `TURN_START` + 阵营条件 + `GAIN_RESOURCE`（青雀） |
| ⭐ 效果抵抗属性 | ❌ **不存在**：`AttributeType` 里没有任何名字命名它 |

### ⛔ 真珠仍登记的从句

⭐ 天赋的**抵御值**（每点 = 200 抵御值、代价抵挡 60% 伤害）；
⭐ 天赋的“生命≤ 50% 时受到的伤害降低 30%”（需要**持续性**的减伤渠道，
而 `MODIFY_DAMAGE_TAKEN` 是时限 buff）；
⭐ 终结技/行迹里“队伭中欢榆命途人数 = 1/2/3/4”的分档；
⭐ 【美学底本】【深度学习】与额外回合；⭐ 星魂 1/2/4/6 的成长；
⭐ 强化普攻的治疗与额外冰欢榆伤害（它们还**未被装配**：没有 `REPLACE_SKILL`）。

## 「命途人数」通用变量（2026-10-02）：⭐ 设计已枚举到每一处，但**本轮未动手**

### ⭐ 它要解决什么

| 消费者 | 原文 |
|---|---|
| 真珠 终结技 | 「队伍中「欢榆」命途角色数量等于 1/2/3 或以上时，使【美学底本】行动提前 10%/15%/30%；等于 4 或以上时获得 1 个额外回合」 |
| 真珠 星魂 1 | 「队伍中「欢榆」命途角色数量等于 2/3/4 或以上时，使我方全体欢榆度提高 10%/20%/60%」 |
| 真珠 欢榆技 | 「队伍中「欢榆」命途角色数量等于 1/2/3/4 或以上时…追加 10%/15%/20%/40% 对应属性欢榆伤害」 |

⭐ 也就是说：**它一次解掉真珠三处分档**，而且这类分档在欢榆命途角色里很常见（她们都看“队伍里有几个同命途”）。

### ⭐ 形状：参数化的数值变量

⭐ 现有两个参数化先例：`self_attr:<ATTRIBUTE>`（`TriggerTable.SELF_ATTR_PREFIX`）与
`self_resource:<NAME>`（`SELF_RESOURCE_PREFIX`）。新变量取同样形状，例如 `allies_with_path:elation >= 2`。

### ⭐ 要动的九处（已逐一定位）

| # | 位置 | 内容 |
|---|---|---|
| 1 | `TriggerTable.NUMERIC_VARIABLES` | 新前缀不进闭合集（参数化的不进），但需在注释里点名 |
| 2 | `TriggerTable` 新前缀常量 | 照 `SELF_ATTR_PREFIX`（`static final String`，带 javadoc） |
| 3 | `Numeric` 类 | 加第 14 个字段（`path`）与构造器参数；它已有 13 个字段与**两变量**那一支，所以参数也要同步加到右侧 |
| 4 | `new Numeric(` 的每个调用点 | 单变量一处 + 两变量一处（各传两份） |
| 5 | 新校验器 `pathOf(...)` | 照 `selfAttributeOf`（空参数报错、命途名在**九名闭合集**内） |
| 6 | `isNumericVariable(...)` | 接受新前缀 |
| 7 | `Numeric.test(ctx)` | 取值：数 `ctx.battle().allies` 中命途等于该值的人数；**无战场时 NaN**（与其它变量同规） |
| 8 | `UnitDisciplineTest.KNOWN` | 第五处 pin（它自己的注释写明了这一点） |
| 9 | `TriggerTable` 的条件语法文档 | 同步写上新前缀 |

### ⚠ 为什么本轮不动手

⭐ 它与之前加的 `skill_point_max` **形状不同**：那个是**素名**（闭合集 加 取值 switch 两处），
而这个要动 `Numeric` 的**字段与构造器**，连带两变量那一支。
⭐ 在一个已经很长的会话**尾部**落九处改动，正是上几轮出现半成品的方式。
⭐ 所以把**每一处都写成上面的清单**，下一轮可以一次做完并配上双向判据。

## 装真珠的强化普攻（2026-10-02）：⭐ 第二形态的数据行已补，⭐ 两条治疗等待装配

### ⭐ 已交付：`Skill12` 的技能行（槽 9）

⭐ 上一轮生成 1503 的八个槽时，`Skill12` = **150310**（「行笔，绘制末浪」）**漏了**（当时的映射只有 `Skill11`）。
⭐ 现已按同一套字段补上槽 **9**，并由全量用例验证加载合法。

### ⭐ 量到的两件（供后续复用）

| 问 | 答 |
|---|---|
| ⭐ 基础攻击发什么事件 | ✅ `BASIC_ATTACK`，由 `SkillExecutor`（`case NORMAL`）发出，**带技能槽** |
| ⭐ `from_skill_id` 怎么写 | ⚠ 它是**数值变量**，**不是阵萢简写** —— 必须写 `from_skill_id == 150308`（写成 `from_skill_id 150308` 会报 “not a known shorthand”） |

### ⛔ 为什么两条治疗被撤回

⭐ 两种强化形态都写着同一句：「为我方全体回复 8% 防御 + 160，**额外**为**最低者**再回一次」。
⭐ 但它们**无法被判据** —— 因为要真的施放一次强化普攻才能观察，而强化普攻还**没装配**。
⭐ 而装配它的 `REPLACE_SKILL` 的**窗口**绑在【深度学习】/【美学底本】上，
那两个状态**尚未建模**（终结技里的“额外回合开始时获得 30 点…回合结束时移除”就是它的边界）。
⭐ 按纪律：**不写无法判据的内容** —— 所以两条规则已撤，代码本身被证明合法（全量绿）但**不作为成果保留**。

### ⭐ 下一步的确切顺序

1. ⭐ 建【美学底本】与【深度学习】两个状态（终结技施加、额外回合结束时移除）；
2. ⭐ 行 `REPLACE_SKILL`（`skill: "COMMON"` → 槽 8 或 9，照 `1315` / `1415` 的写法）；
3. ⭐ 然后那两条治疗就可以连同判据一起落地（“最低者恰好两倍”是现成的双向断言）。

## 真珠的三处命途分档（2026-10-02）：终结技已落，另两处登记

### ✅ 已落：终结技的四条

⭐ 原文：「队伍中「欢榆」命途角色数量等于 1/2/3 或以上时，使【美学底本】行动提前 10%/15%/30%；
等于 4 或以上时，使【美学底本】获得 1 个额外回合」。
⭐ 写法：**三条递增阈值**（`allies_with_path:欢愉 >= 1/2/3` → `ADVANCE` 0.10/0.15/0.30，作用对象 `target`）
加**一条额外回合**（`>= 4` → `EXTRA_TURN`）。两个 op 都有现成用例（1101 推队友、1309/1404/1501 额外回合）。
⭐ 作用对象是 `target`：终结技选中的那个队友就是【美学底本】，所以这一半不需要状态。

### ⛔ 仍登记的两处

| 位置 | 卡在哪 |
|---|---|
| 星魂 1 的“队伍中欢榆人数 = 2/3/4 → 我方全体**欢榆度** 10%/20%/60%” | ❌ **没有对应属性**：`AttributeType` 里没有任何名字命名欢榆伤害，内容里也没有一处拼写它 |
| 欢榆技的“队伍中欢榆人数 = 1/2/3/4 → 下一次攻击后追加 10%/15%/20%/40% 对应属性欢榆伤害” | ❌ 同上（需要欢榆伤害类型），另需“下一次攻击”的触发口 |

### ⚠⚠ 一个值得记的错字（本轮拓了一次）

⭐ 我把命途名写成了 **`欢榆`**（榆）而正确的是 **`欢愉`**（愉）——
`\u6986` 是榆、`\u6109` 才是愉，两个字形近、码位远。
⭐ 后果：四条规则全部读不出来，**7 个判据同时红**，报文直指 `requirePath`。
⭐ 修正：**11 处**（四个条件 加 引文）一次改完，全量立刷绿。
⭐ 教训：目前的命途名是**中文**（`丰饶 / 同谐 / 存护 / 巡猎 / 智识 / 欢愉 / 毁灭 / 虚无 / 记忆`），
而我的脚本里写码位时没核对字形 —— 下次宁可从报文里直接拷贝那九个名字。

## 欢榆度属性（2026-10-02）：⭐⭐ **它早就存在** —— 我先前的结论是错的

### ⭐ 实测：全链路都在

| 位置 | 内容 |
|---|---|
| `AttributeType:105` | `@SerializedName("elation_damage_boost") ELATION_DAMAGE_BOOST("elation_damage_boost")` |
| `AttributeType:288` | 数据列映射：`Map.entry("ElationDamageAddedRatioBase", ELATION_DAMAGE_BOOST)` |
| `TriggerInterpreter:5022` | `base * (1 + attacker.getAttribute(ELATION_DAMAGE_BOOST).get())` |
| `SkillExecutor:642` | 同一个应用点，且注释里**带实测**（“with `ELATION_DAMAGE_BOOST` at 0.8 …”） |

### ⚠ 我为什么误判了它缺失

⭐ 我扫 `AttributeType` 时用的正则是 `^\s*([A-Z][A-Z0-9_]*)\(`，而该枚举的条目**以 `@SerializedName(…)`开头**，
类名在行尾——所以整个枚举扫出 `count=0`。⭐ 我当时**注意到了这个不合理的 0**，却只回头查了一半，
并把“没有 ELATION 属性”当成结论告诉了用户。⭐ 教训：**扫描结果为 0 时，先质疑扫描本身**。

### ✅ 因此落地的只是内容：真珠星魂 1 的三档

⭐ 三条递增阈值（同结算技的写法）：
`allies_with_path:欢愉 >= 2/3/4` → `MODIFY_ATTR{ELATION_DAMAGE_BOOST}` 0.10/0.20/0.60，`target: all_allies`，`permanent: true`，`min_eidolon: 1`。

### ⚠ 仍缺一个判据（本轮撤回了草稿）

⭐ 草稿用 `pearl.setEidolon(1)`，而真名是 **`Character.Builder.eidolonRank(int)`**（`Character:373`），
且测试里已有 `EidolonGateTest` 的辅助可借用。⭐ 下一步：照 `EidolonGateTest` 的写法把判据补上（“1 个欢榆时为 0、2 个时 0.10、4 个时 0.60”是现成的双向断言）。

## 【美学底本】/【深度学习】两个状态（2026-10-02）：⭐ 语义已读全，⭐ 而且找到了两种强化形态的分支

### ⭐ 语料原文（`1503_真珠.md`）

| 行 | 内容 |
|---|---|
| 106（终结技） | 「获得 20 点【好活当赏】。【深度学习】**除自身以外**的指定我方单体角色，使目标成为【美学底本】…**【深度学习】时，普攻【行笔，临摹断水】强化为【行笔，绘制末浪】。若【美学底本】为「欢榆」命途，则强化为【…】**」 |
| 193（秘技） | 「使用秘技后获得【美学底本】，**切换行动角色时【美学底本】转移至当前行动角色**。下一次战斗开始时，真珠获得 20 点【好活当赏】，并【深度学习】持有【美学底本】的角色，**该【深度学习】具有 2 点充能**。真珠只能【深度学习】除自身以外的角色」 |
| 304（行迹） | 「进入战斗和施放终结技后，若【美学底本】为「欢榆」命途角色，使其**下一次施放终结技时**，为真珠固定恢复 90 点能量。该效果**无法叠加**」 |
| 357（星魂 6） | 「【深度学习】时，我方全体全属性抗性穿透提高 20%。真珠的强化普攻额外造成 240% 冰属性欢榆伤害，该次欢榆伤害**使用【美学底本】的属性值**计算」 |
| 327（星魂 2） | 「施放终结技时，使**除真珠和【美学底本】外**的其他我方「欢榆」命途角色也触发行动提前效果」 |

### ⭐⭐ 本轮的发现：两种强化形态是**分支**，不是两个独立技能

| 条件 | 强化为 | 槽 |
|---|---|---|
| 【深度学习】时（默认） | 【行笔，绘制末浪】「Brushstroke: Render the Great Wave」 | **9**（`150310`） |
| 【深度学习】**且【美学底本】为「欢榆」命途** | 【行笔，幻造星月】「Brushstroke: Imagenate the Starry Night」 | **8**（`150308`） |

⭐ 而分支条件用**现成词汇**就能写：`target has_path 欢愉`（`has_path` 已有，命途名用中文）。
⭐ 而且只有槽 8 那一个带“持有【好活当赏】时额外 15% 冰欢榆伤害”。

### ⭐ 两个状态的正确形状（已读出）

| 状态 | 挂在谁 | 形式 |
|---|---|---|
| 【美学底本】 | 终结技选中的那个**队友**（秘技版则是“当前行动角色”，会转移） | 具名状态，归属可能会变化（会转移） |
| 【深度学习】 | **真珠自己**（她只能学习**除自身以外**的角色） | ⭐ **充能制**：“该【深度学习】具有 **2 点充能**” ⇒ 它是**计数器**，不是带回合的 buff |

### ⚠ 为何本轮只登记而不建

⭐ 入口都已读出，但有**一个未知量是硬的**：⭐ 【深度学习】的 **2 点充能消耗在什么上**（每次强化普攻？）、
以及【美学底本】**持续多久**（终结技版）——语料两处都没说。
⭐ 而安装强化普攻的 `REPLACE_SKILL` 必须有一个**期限**（开斯的是 `turns`、昔涩的是 `permanent`）。
⭐ 按纪律：不猜期限。下一步先把这两个量量清（语料其他小节、或 tbgd 的 buff/ability 表），再一次性建状态、装强化普攻、落两条治疗并配判据。

## 第 1 步量测（2026-10-02）：⭐⭐ 真珠的**权威状态表**已找到 —— 它同时纠正了我上一轮的登记

⭐ 位置：`tbgd/ExcelOutput/AvatarStatusConfig.json`，按 `ModifierName` 含 `Pearl` 筛出 **14 条**，
每条都带 `StatusName` / `StatusDesc` / `StatusType` / `ReadParamList`。⭐ 这是真珠剩下部分的**权威依据**。

| StatusID | ModifierName | 名字 | 说明（原文） |
|---|---|---|---|
| `100150311` | `MAvatar_Pearl_00_Passive_DamageReduce` | 吞纳沙砾，重塑生机 | 受到伤害降低#1[i]%。 |
| `10015036` | `MAvatar_Pearl_00_PointB1_Effect` | 洞察万物 | 欢愉度提高#1[i]%。 |
| `100150314` | `MAvatar_Pearl_00_PointB1_HealEffect` | 洞察万物 | 治疗量加成提高#1[i]%。 |
| `100150313` | `MAvatar_Pearl_00_PointB2_Resistance` | 感知容差 | 效果抵抗提高#1[i]%。 |
| `10015033` | `MAvatar_Pearl_00_PointB3_EnergyRefund` | 艺术壁垒 | 下一次施放终结技时，为真珠固定恢复#1[i]点能量。 |
| `10015038` | `MAvatar_Pearl_00_Rank01_ElationUp` | 珍珠，藏在海的留白处 | 欢愉度提高#1[i]%。 |
| `100150312` | `MAvatar_Pearl_00_Rank01_Revive` | 珍珠，藏在海的留白处 | 若我方目标受到致命伤害，不会陷入无法战斗状态，并立即回复等同于其生命上限#1[i]%的生命值。剩余可触发次数为#2[i]。 |
| `100150310` | `MAvatar_Pearl_00_Rank02_DamageUp` | 截取，顷刻破晓的光斑 | 欢愉伤害增笑#1[i]%。 |
| `10015039` | `MAvatar_Pearl_00_Rank06_AllDamageTypePenetrate` | 用一副躯壳，演算生命 | 全属性抗性穿透提高#1[i]%。 |
| `10015034` | `MAvatar_Pearl_00_Skill03_Caster_BaseType` | - | - |
| `10015037` | `MAvatar_Pearl_00_Skill03_Caster_BuffA` | 深度学习 | 造成伤害时，额外造成#1[i]%冰属性欢愉伤害，该次欢愉伤害使用【美学底本】的属性值来计算。普攻【行笔，临摹断水】强化为【行笔，幻造星月】。 |
| `10015035` | `MAvatar_Pearl_00_Skill03_Caster_BuffB` | 深度学习 | 普攻【行笔，临摹断水】强化为【行笔，绘制末浪】。 |
| `10015032` | `MAvatar_Pearl_00_Skill03_Target` | 美学底本 | 正在被真珠深度学习。 |
| `10015031` | `MAvatar_Pearl_00_Skill04_Buff` | 渲染理性，解构欢愉 | 下一次攻击后，对攻击目标额外造成#1[i]%的对应属性欢愉伤害。 |

### ⭐⭐ 纠正：两种强化形态的**分支方向是反的**

⭐ `深度学习`（10015037）的原文：「造成伤害时，**额外造成 #1[i]% 冰属性欢榆伤害**，
该次欢榆伤害**使用【美学底本】的属性值来计算**。普攻【行笔，临摹断水】强化为
【**行笔，幻造星月**】」。

⇒ ⭐ **默认强化就是槽 8（幻造星月）**，不是我上一轮登记的槽 9；
而语料第 106 行“**若【美学底本】为「欢榆」命途，则强化为【…】**”指的是**另一个**（即槽 9）。
⭐ 而且“额外造成 X% 冰属性欢榆伤害”**就在这个状态里**（而不是写在技能条目里）。

### ⭐ 同一张表里顺手印证的几件

| 我已写的 | 印证 |
|---|---|
| 星魂 1 免死计数 `救主计数 < 2` | ✅ `100150312` 原文：「剩余可触发次数为 #2[i]」 |
| 星魂 1 欢榆度三档 | ✅ `10015038`：「欢榆度提高 #1[i]%」 |
| “属性已存在” | ✅ `100150310`（星魂 2）写的是「欢榆伤害**增笑** #1[i]%」 |

### ⭐ 真珠其余未写部分，现在都有了权威文本

| 部件 | StatusID | 原文要点 |
|---|---|---|
| 天赋（≤ 50% 减伤） | `100150311` | 「受到伤害降低 #1[i]%」 —— **是一个状态**，所以可以用 `APPLY_BUFF` + `MODIFY_DAMAGE_TAKEN` 写 |
| 行迹 洞察万物 | `10015036` / `100150314` | 「欢榆度提高 #1%」与「**治疗量加成**提高 #1%」（后者对应 `OUTGOING_HEALING_BOOST`） |
| 行迹 感知容差 | `100150313` | 「**效果抵抗**提高 #1%」 —— ⭐ 它是一个**状态**，不一定需要新 `AttributeType` |
| 行迹 艺术壁垒 | `10015033` | 「下一次施放终结技时，为真珠**固定恢复 #1[i] 点能量**」 |
| 星魂 6 | `10015039` | 「全属性抗性穿透提高 #1%」 |
| 【美学底本】 | `10015032` | `Other` 类，`..._Skill03_Target` |
| 【深度学习】 | `10015037` | `Buff` 类，`..._Skill03_Caster_BuffA`，`ReadParamList=[MDF_PropertyValue]` |

⚠ 本轮输出被截断在第 14 条（共 14 条已列出），但该表里**可能还有**对应技能、额外回合、
秘技版的条目 —— 下一步把 14 条全部打印并逐条对照。

## 第 1 步成果（2026-10-02）：真珠全部数值，以及一处**早先记错的数**

⭐ 全部取自 `tbgd/ExcelOutput/AvatarSkillConfig.json` 的 `ParamList`（行级 1 → 满级）。
⭐ 状态行只读 `MDF_PropertyValue`，数值就在**施加它的那行技能**里。

| 件 | 技能 ID | 级 1 | 满级 | 含义 |
|---|---|---|---|---|
| 天赋 | `150304` | `[0.5, 0.6, 50, 0.15, 200]` | `[0.5, 0.6, 50, 0.375, 200]` | HP ≤ **50%**；抵挡 **60%**；上限 **50**；受伤降低 **15% → 37.5%**；每点 = **200** 抵御值 |
| 战技 | `150302` | `[15, 0.06, 120]` | `[15, 0.15, 300]` | 15 点【好活当赏】；治疗 6%+120 → 15%+300 |
| 终结技 | `150303` | `[3, 0.3, 20, 0.1, 0.15, 0.3, 60, 30]` | `[3, 0.75, 20, 0.1, 0.15, 0.3, 60, 30]` | 档数 3；20 点；行动提前 **10/15/30%**；**60** 笑点；**30** 点【好活当赏】（额外回合奖励） |
| 秘技 | `150307` | `[2, 20]` | 同 | **2 点充能**；20 点【好活当赏】 |
| 行笔，幻造星月（槽 8） | `150308` | `[0.5, 0.04, 1, 80, 0.1]` | `[1.4, 0.112, 1, 224, 0.2]` | 治疗 4%+80 → 11.2%+224；额外冰欢榆伤害 **10% → 20%** |
| 行笔，绘制末浪（槽 9） | `150310` | `[0.5, 0.04, 1, 80]` | `[1.4, 0.112, 1, 224]` | 同治疗，但 **没有第五个参数**（佐证它就是 `BuffB`） |
| 欢榆技 | `150320` | `[0.05, 0.075, 0.1, 0.2]` | `[0.125, 0.1875, 0.25, 0.5]` | 四档：**5/7.5/10/20%** → 12.5/18.75/25/50% |

### ⚠ 纠正：天赋的减伤是 **15%**，不是我早先记的 30%

⭐ 我在开始做真珠时把这条写成“生命 ≤ 50% 时受到的伤害降低 **30%**”，
而数据是 `#4 = 0.15`（满级 0.375）。⭐ 数据权威，登记修正。

### ⭐ 抵御值的落点也查清了

⭐ `抵御值` 在 TextMap 里只有 **4 条**，而全部 ExcelOutput 表里只有 `AvatarStatusConfig` **提到 Pearl**。
⇒ ⭐ 抵御值**不是独立表**，它是**天赋状态**的职责；状态表里 `100150311`
（`Passive_DamageReduce`，“受到伤害降低 #1%”）是≤ 50% 那一半，而 `10015034`（`Skill03_Caster_BaseType`，**无名无描述**）是另一半的候选。

### ⭐ 顺手发现：已出货的一条规则数值可能取自语料的非级 1 值

⭐ 我写的战技治疗是 `percent: 0.12, amount: 240`（语料原文），而行数据是 **级 1 = 6%+120**、**满级 = 15%+300**。
⭐ 语料的 12%/240 应是**某一等级**的值（大约级 10）。⭐ 好在我的判据只断言“最低者恰好两倍”，**不断言绝对值**，
所以它不受影响 —— 但这个取值口径本身需要一个统一答案（后续内容一律用**满级**还是**某等级**）。

## ⭐ 新缺口（2026-10-02）：一笔规则产生的伤害**无法取攻击者的属性**

### 场景

真珠（1503）的欢榆技写着：「使我方全体目标**下一次攻击后**，对攻击目标额外造成 10%/15%/20%/40% 的
**对应属性**欢榆伤害」—— “对应属性”是**每个队友各自的属性**（冰/物理/雷…），
因为额外那一笔是**由该队友打出的**。

### 为什么现在写不出来（两处代码为证）

| 位置 | 内容 |
|---|---|
| `TriggerInterpreter:5071-5084` | `elementOf(effect, skill)`：**规则自己的 `element`**，否则**技能的**—— **没有第三个来源** |
| `TriggerInterpreter:497-499` | `DAMAGE` 的校验明说：字面倍率（`scale` + `percent`）**必须带 `element`**，“because no skill lends one” |

⇒ ⭐ 这类实例的属性在**写规则时就被固定**。

### 为什么以前没碰到

⭐ `1505` 与 `1513` 的欢榆 rider 都写一个**固定属性**（`physical` / `Quantum`），
而那与它们的形状相符：那些 rider 属于**单一单位**，属性当然固定。
真珠这一条是**第一个必须随攻击者而变**的。

### ⭐ 候选写法（供后续实现，本轮**未实现**）

⭐ 给 `DAMAGE` / `ADD_DAMAGE` 的 `element` 增一个**保留词**（如 `"attacker"` 或 `"self"`），
表示“取**产生这次伤害的单位**的元素”，在 `elementOf` 里读 `attacker.getElement()` 之类。
⭐ 它与 `damage_type` 是两件事：后者决定“是不是欢榆伤害”（以及是否把
`ELATION_DAMAGE_BOOST` 折进 base），前者决定元素对应的抵抗与加成。

### 影响

⭐ 真珠欢榆技的**兼现端**只能登记。⭐ 同一句话在**其它欢榆角色**里也会出现（参耍编号这一族特征），
所以这不是真珠专属的小事。

### ⭐ 实测的落点清单（2026-10-02，供下一轮直接执行）

| # | 位置 | 要做什么 |
|---|---|---|
| 1 | `TriggerInterpreter:5265 requireElement` | 它用 `DamageElement.fromString` 校验，所以保留词会**在加载期被拒**。需要一个只在**伤害类 op**上生效的许可（不能放宽 `APPLY_DOT` / `APPLY_CONTROL`，它们也调它） |
| 2 | `TriggerInterpreter:5079 elementOf(effect, skill)` | 签名加 `CanHit attacker`，并在**规则自己的 `element`**之前先识别保留词 |
| 3 | `elementOf` 的 **3 个调用点**：`5032` / `5042` / `5048` | 传入 attacker（它们所在的方法里 `attacker` 就在作用域内） |
| 4 | `5031` / `5041` / `5047` 的**内联**取值 | 这三处直接写了 `DamageElement.fromString(effect.getElement().trim())`，与 `elementOf` 重复；一并改成调 `elementOf` |
| 5 | 单位取属性的 API | 已有先例：`battle.characters.getFirst().getElement()`（1208 行）—— `Character.getElement()` 存在 |

⭐ 预期语法：`{"op": "DAMAGE", "scale": "self_attr:ATTACK", "percent": 0.1,`
`"element": "attacker", "damage_type": "ELATION", "target": "target"}`。

⭐ 为什么值得做：真珠欢榆技的兼现端（四档 10/15/20/40%）就卡在这里；
而“对应属性欢榆伤害”是**欢榆命途一族**的常见句式，所以它不是真珠专属。
⭐ 与它**同族但不同向**的还有一个：真珠形态 A 的 60% 与星魂 6 的 240% 要“使用
**【美学底本】的属性值**来计算”——那是“取**另一个单位**的属性值”，
与本缺口是同一个设计问题的两个方向（一个取元素、一个取面板值），
值得一起设计一个“**取谁的属性**”的词。

### ⚠ 修正上一条的范围（2026-10-02）：缺口比我写的更大

⭐ `ROADMAP.md:1262`（远期规格表，**我自己的台账**）早就写着完整口径：

> 伤害公式 `基础值 × 欢榆倍率 × (1+欢榆度) × (1+增笑) × (1+笑点×5/(笑点+240))`，**禁攻击力/属性增伤**；
> 阿哈速度 = `80 + 最快/5 + 第二/10 + 第三/20 + 最慢/50`；
> `elation_basic_level_damage.json`（101 条）**从未被加载**

⇒ 三个后果：

1. ⭐ **增笑是独立乘区** `(1+增笑)`，与 `(1+欢榆度)` 并列 —— 所以不把星魂 2 的 15%
   写成 `ELATION_DAMAGE_BOOST` 是对的，而且现在理由是**公式**而不是术语猜测。
2. ⭐ **欢榆伤害禁攻击力**：我从 `1513`/`1505` 抄来的 `scale: "self_attr:ATTACK"` 形状
   **与本规格不符**。那是既有内容的事，不是本轮要改的，但它**不是安全的模板**。
3. ⭐ **有一张 101 行的表从未被加载**：`elation_basic_level_damage.json`。
   它很可能就是 `基础值 × 欢榆倍率` 的来源 —— 这正是“额外一笔欢榆伤害该写什么 `scale`”
   在属性词汇里找不到答案的原因：那个数**根本不是面板属性**。

⇒ ⭐ 我上一条登记（“规则产生的伤害取不到攻击者的元素”）**是真的，但只是一个角**；
真正的工作量是**整条欢榆伤害管线**（至少包括那张未加载的表 + 四个乘区）。

⭐ 而且这是本会话**第三次**“答案早已写在已有文档里”：
语料页第 37 行（能力脚本未转储）、状态表（两个步得的权威文本）、现在是 `ROADMAP` 的规格行。
⇒ 新规矩：**做任何机制前，先读语料页、状态表、`GAPS.md` 与 `ROADMAP.md` 的相关段**。

### ⭐ 任务收窄（2026-10-02）：**类型是通的，缺的是基础值表**

⭐ `DamageType.java:48-49` 写着 `ELATION` “0 references … not even `elation_basic_level_damage.json`
has been loaded yet”，第 60 行定义 `ELATION("elation", true, false)`（**可暴击、不可被增伤**）。

⭐ 但“0 references”那半**已经过时**：`1505` 的 rider 已经在写
`damage_type: "ELATION"`，而引擎在 `TriggerInterpreter:5022` 与 `SkillExecutor:642` 两处把加成
**折进 base** —— 正因为 ELATION 不可被增伤。⇒ **类型是接好的**。

⭐ 真正缺的是**基础值**，而表就在仓库里：
`src/main/resources/data/elation_basic_level_damage.json`，101 条的 `{等级 → 一个数}`，
实测开头为 `{"1": 108, "2": 116, "3": 124, …}`。

⭐ 按 `ROADMAP.md:1262` 的公式 `基础值 × 欢榆倍率 × (1+欢榆度) × (1+增笑) × (1+笑点×5/(笑点+240))`（**禁攻击力**）：

| 乘区 | 现状 |
|---|---|
| `基础值 × 欢榆倍率` | ❌ 就是那张 101 行的表，**未加载** |
| `(1+欢榆度)` | ✅ `ELATION_DAMAGE_BOOST`（已加载，已应用于两处） |
| `(1+增笑)` | ❌ 无对应属性（真珠星魂 2 的 15%） |
| `(1+笑点×5/(笑点+240))` | ❌ 笑点已声明为 PARTY 资源，**曲线未实现** |

⇒ ⭐ 这就是真正的任务清单；做完一次解开真珠的**欢榆技兼现端、形态 A 的 60%、星魂 6 的 240%**，
以及**其它欢榆角色**。与它不同的是本襯先前登记的“取不到攻击者元素”——
那一条仍然成立，但是**最后一步**而不是第一步。

### ❓ 疑问（2026-10-02，五个实验为证）：`from_category ElationDamage` 到底匹配什么

⭐ 真珠的四条欢榆技档位规则写着
`["actor == self", "from_category ElationDamage", "allies_with_path:欢愉 >= N"]`，动作是
`APPLY_BUFF{渲染理性，解构欢榆, 目标全体}`。⭐ 五个实验全部读到**零个承载者**：

| 实验 | 做法 | 读数 |
|---|---|---|
| 1 | 直接 `fireTriggers(CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE)` | 空 |
| 2 | 寿命 `until: "next_attack"` → `turns: 2` | 空 |
| 3 | 去掉 `target_when` | 空 |
| 4 | 目标由 `all_allies` → `self` | 空 |
| 5 | **真实施放**：`castImmediate(getSkills().get(SkillType.ELATION_SKILL), ...)` | 空 |
| （附） | 寿命 `permanent: true` | 空 |

⭐ 排除掉的：寿命写法、`target_when`、目标选择器、施放路径。
⭐ 已单独证实可用的条件：`actor == self`（多处绿色判据）与
`allies_with_path:欢愉 >= N`（`AlliesWithPathTest` 绿色，且建议队伍下会触发）。
⇒ ⭐ **唯一没被证实能匹配的条件就是 `from_category ElationDamage`**。

⚠ 但这只是**怀疑**，不是证据：我没找到任何**确实匹配成功**的例子。
⭐ 而同一个词还装在：四张出货光锥（21064 / 21066 / 23058 / 23064）与 `1513` 的
`elation_spend_all_fervor` 上 —— 若它不匹配，那些也一律不会触发。

⭐ 最小的下一步：读 `from_category` 的**解析与比较代码**（它把 DSL 的 `ElationDamage`
拿去和什么比：`SkillCategory` 枚举本身、它的 `name` 字段、还是技能数据的
`attack_type`），并用一个**已知能匹配**的类别（如 `BPSkill` → `BPSKILL`）做对照实验。
那一步能同时判定：这四条规则、四张光锥、以及 1513 那条，到底是不是死的。

### ⭐ 收窄（2026-10-02）：为什么 `allies_with_path:欢愉` 在 CAST_SETUP 上为假，在 TURN_START 上为真

⭐ **决定性实验**：把真珠四条欢榆技档位规则的 `allies_with_path:欢愉 >= N`
**临时删掉**（其余不动），再用
`fireTriggers(CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE)` 发一次，
读数是 **`carriers=3`** —— 全队都拿到了载体，规则全部触发。
（实验后已用 `git checkout` 恢复，三道门都在，全量绿。）

⇒ ⭐ 所以失败的只有路径门。而且已排除的有：

| 假设 | 结果 |
|---|---|
| 寿命（`until` / `turns` / `permanent`） | ❌ 不是原因（三种写法都空） |
| `target_when` 防重复 | ❌ 不是 |
| 目标选择器（`all_allies` / `self`） | ❌ 不是 |
| 施放路径（真实 `castImmediate`） | ❌ 不是 |
| `ctx.battle()` 为空 | ❌ **推翻**：`Battle:2535/2551` 造上下文时传了 `this` |

⭐ 而 `AlliesWithPathTest`（绿色）证明同一个变量在 `TURN_START` 上能算对。
⇒ ⭐ 下一步只差一个诊断：在 `FromCategory`/`pathCount` 那一步打印
`ctx.battle()`、`ctx.actor()`、`ctx.owner()`、以及 `ctx.battle().allies` 的命途列表，
看 `CAST_SETUP` 与 `TURN_START` 两者差在哪一个。⭐ 一行输出就能定案。

⭐ 影响面：真珠的四条欢榆技档位（现在全部不触发）；其它把
`allies_with_path` 放在 `CAST_SETUP` 上的内容会有同样问题（目前只有真珠）。

### ✅ 撤回（2026-10-02）：`from_category` 没有问题，是我的实验污染了读数

⭐ 前面两节登记的“从哪来的 `carriers=0`”与“怀疑 `from_category ElationDamage` 不匹配”
**都是假警报**：那些读数是在**我自己的实验还没恢复**的文件上取的。

⭐ 恢复后的同一轮读数：`[same] gate-probe delta=1.0 carriers=2`；
而正式判据 `PearlElationSkillCarrierTest` 立刻通过：

> `[pearl_carrier] armed by the Elation skill: [1503, 1502, 1204] ; armed by BPSKILL: []`

⇒ ⭐ 四条欢榆技档位规则**一直是工作的**；变异（摘掉 `from_category`）打红。

⭐ **教训（本会话第五条方法论）**：做实验性修改时，
**必须在同一次调用里备份并恢复**，而不是依赖后续手动 `git checkout`——
我因为忘了恢复，在改过的文件上读了四次，并两次把错误结论写进台账。
