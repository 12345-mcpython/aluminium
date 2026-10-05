

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
