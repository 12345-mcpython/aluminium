"""Record the sky-ode stack shipment (2026-10-02, round 62)."""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD = ("- ⛔「德谬歌施放忆灵技时，使风堇获得 **2 层**【献予「天空」之诗】」：**跨表目标已不再是缺口**（2026-10-02 出货了 `ally_cid:<cid>`，判据两侧："
       "点名的那位拿 2 层、旁人 0 层）。剩下的缺口是**层数上限** —— `StackBuff` 是 `Math.max(1, maxStacks)`，不给上限时**夹到 1**（实测：点名那位读到 1），"
       "而该状态的上限**不在我能读到的表里**（实测：忆灵技能表无层数字段；`StatusConfig`（2,457 行）无该状态名）⇒ 不发明一个上限来凑数。"
       "⭐ **下一问**：这个状态的**层数上限**在哪 —— 已实测：`AvatarStatusConfig`（778 行）里它叫 `MServant_CyreneServant_00_AmazingBuff_Hyacine`"
       "（`StatusID 10014155`）而**没有**计数列；能力文件里的 5 个 `MaxLayer` 都属于别的修饰（两个 `99999`、两个动态、一个 `1`）。")
NEW = ("- ✅ 已出货（2026-10-02）：「德谬歌施放忆灵技时，使风堇获得 **2 层**【献予「天空」之诗】」 —— `on: CAST_SETUP` ＋ `actor is_summon` ⇒ "
       "`ADD_STACK{buff, amount: 2, max_stacks: 99999, permanent, target: \"ally_cid:1409\"}`。⭐ 判据 `SkyOdeStackTest` 两侧：点名的那位 2 层、同一场里另一个我方角色 0 层。\n"
       "- ⭐⭐ **上限为什么写 99999（⭐ 这是数据自己的惯例 ✓）**：能力数据把这句话写成 `AddModifier` ＋ `LayerAddWhenStack: 2`，前置是 "
       "`ByCompareCharacterID = 1409`（⭐ **游戏就是按 cid 点名** ✓）；提到该修饰的四个文件里它旁边**没有** `MaxLayer`（`AvatarStatusConfig` 的行甚至没有计数列），"
       "而同一个能力文件里"不限"写的就是 `MaxLayer: 99999`（两处）⇒ **缺省 ＝ 不限**。⚠ 不写它的话，引擎的 `StackBuff`（`Math.max(1, maxStacks)`）只会给 1 层。")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the slot-19 bullet occurs %d times" % text.count(OLD))
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW))
print("GAPS.md updated: slot 19's stack half is shipped")

BLOCK = """
> **2026-10-02 更新（第 62 轮：🎉 **第 124 件出货 ＝ `1141519` 第一句「使风堇获得 2 层」整句成句** ✓）**：
>
> * ✅ **一次批搜定案** ✓（⭐ 按我自己的教训 ✓）：⭐ 遍历整个 tbgd，⭐ 提到 ⭐ `AmazingBuff_Hyacine` ✗ 的文件共 **4 个** ✓，
>   ⭐ 而 ⭐ **每一个 "cap-ish" 键都是 `LayerAddWhenStack: 2`** ✗ ⇒ ⭐ **它旁边没有 `MaxLayer`** ✗（⭐ `AvatarStatusConfig` 的行甚至没有计数列 ✓）。
> * ⭐⭐ **于是上限照游戏自己的惯例写** ✗：⭐ 同一个能力文件里"⭐ 不限 ✗"写的是 ⭐ **`MaxLayer: 99999`** ✗（⭐ 两处 ✓）⇒ **缺省 ＝ 不限** ✓；
>   ⚠ 不写它 ⭐ 引擎的 `StackBuff`（⭐ `Math.max(1, maxStacks)` ✓）只会给 **1** 层 ✓（⭐ 实测过 ✓）。
> * ⭐⭐⭐ **而数据自己确认了 `ally_cid:`** ✓：（⭐ 上一轮 ✓）⭐ `"Predicate": { "ByCompareCharacterID" … "Value": 1409 }` ✓
>   ⇒ ⭐ **游戏按 cid 点名** ✗ ✓ ⭐ 与我们的选择器**同一件事** ✓。
> * ✅ **判据 ＋ 实测变异**：⭐ `SkyOdeStackTest` ⇒ ⭐ `[sky_stacks] the named character has 2 ; the other ally has 0` ✓✓；
>   ⭐ **变异**（⭐ `amount: 2 → 1` ✗）⇒ 红 ✓。
> * **实测（本轮）**：⭐ 全量 **0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
