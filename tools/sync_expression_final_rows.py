"""Both remaining rows get their final form -- and neither is built, on the repo's own reader bar (round 29 of the goal).

1407: 「本次行动中所有受到致命攻击的我方角色」 is not a one-line selector. Measured, it needs five things at once: an ACTION boundary
(`Battle.performAction` only QUEUES -- the settlement is in afterMove/processRequests), a per-action set of lethal victims, a place to
announce `the action ended` (no such event exists; the nearest is ATTACK_FINISHED, and one action may hold several attacks), the selector
itself, and the cap's cooperation. Reader count: 1 (1407). Below the bar.

1415: the memosprite's skill 8 has NO ROW IN EITHER TABLE. `skills.json` is keyed by game skill id and all nine of 1415's rows are HER OWN
(`141501`…`141519`); `skill_effects.json` -- the non-damaging table, keyed by the MASTER's cid, which DOES carry `1409` (another memosprite
master) -- has no 1415 entry. `SummonFactory.servant` builds the memosprite from `Memosprites.of(master.getCid(), SERVANT_DIR)`. So the
clause's trigger point (「对万敌施放时」 = the skill being cast) does not exist. Reader count: 1 (1415). Below the bar.

The bar itself is the repo's: `light_cones/_unmodelled.json` records "Reader count: 1 (this cone only) -- below the bar, so registered
rather than built."
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

ROWS = {
    "| 仓库技「**获得该角色即生效，无需上场**」": (
        "| 仓库技「**获得该角色即生效，无需上场**」 "
        "| ✅ `1506` 全部出货 ✓。❗ `1407` 月茇之庇：只差「**一次行动**中受到致命攻击的全体」"
        "选择器 ✓，而它不是一行代码 ✗——本轮量清它需要**五位一体** ✓："
        "① 一条**行动边界** ✗（`Battle.performAction` **只入队** ✓，结算在 `afterMove`／`processRequests` ✓）；"
        "② 行动内的**受害者集合** ✗；③ 一个**行动结束的公告点** ✗"
        "（最近的是 `ATTACK_FINISHED` ✓，而一次**行动**可含多次攻击 ✗）；④ 选择器本身 ✗；⑤ 限额的配合 ✓。"
        "⚠ 另外两半已就位 ✓（`defers_death` ✓；`HEALED`／`SHIELD_GRANTED` ⇒ `REMOVE_STATE` ✓）。"
        "⭐ **不造**：读者 **1 位** ✗ ⇒ 按本项目自己的先例登记 ✓（`light_cones/_unmodelled.json`：“Reader count: 1 … below the bar, so registered rather than built” ✓） "
        "| `1407`（月茇之庇 ✓） **1 位**（`1506` 已出货 ✓） "
        "| 上述五件；❗**读者不足 2 位前不造** ✓ |"),
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**": (
        "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
        "| ❗ **该技能在**两张表**里都没有行** ✓（本轮测定 ✓）："
        "★ `skills.json` 的键是**游戏技能 id** ✓，而 `1415` 的九行**全是她自己的** ✓（`141501`…`141519`）；"
        "★ `skill_effects.json`（**非伤害**表 ✓，**按主人 cid** ✓，它**确实带** `1409` ✓ —— 另一位忆灵主人 ✓）**没有 1415** ✗；"
        "★ 而忆灵由 `SummonFactory.servant` 用 `Memosprites.of(master.getCid(), SERVANT_DIR)` 建 ✓。"
        "⇒ 所以它的「对万敌施放时」（＝该技能被施放 ✓）**没有触发点** ✗。"
        "⭐ **不造**：读者 **1 位** ✗ ⇒ 登记 ✓ "
        "| `1415`（1 位） "
        "| 给该技能一条**行**（伤害类进 `skills.json` ✓、非伤害进 `skill_effects.json` ✓）；❗**读者不足 2 位前不造** ✓ |"),
}

for prefix, replacement in ROWS.items():
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:20], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows now carry the five-part size, the reader count and the bar")
