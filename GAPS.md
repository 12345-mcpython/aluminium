

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
