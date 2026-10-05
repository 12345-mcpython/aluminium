

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
