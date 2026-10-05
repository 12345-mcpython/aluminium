"""Record the `ally_cid:` shipment and move slot 19's blocker from "no cross-table target" to the stack cap (2026-10-02)."""
import io
import sys

GAPS = "GAPS.md"
LOG = "GAPS_LOG.md"

text = io.open(GAPS, encoding="utf-8").read()

# --- update the slot-19 entry: the cross-table target is no longer the blocker ---
OLD = "- ⛔「德谬歌施放忆灵技时，使风堇获得 2 层【献予「天空」之诗】」：层数在**她**身上、而触发者是忆灵 ⇒ **跨表目标**。"
NEW = ("- ⛔「德谬歌施放忆灵技时，使风堇获得 **2 层**【献予「天空」之诗】」：**跨表目标已不再是缺口**（2026-10-02 出货了 `ally_cid:<cid>`，判据两侧："
       "点名的那位拿 2 层、旁人 0 层）。剩下的缺口是**层数上限** —— `StackBuff` 是 `Math.max(1, maxStacks)`，不给上限时**夹到 1**（实测：点名那位读到 1），"
       "而该状态的上限**不在我能读到的表里**（实测：忆灵技能表无层数字段；`StatusConfig`（2,457 行）无该状态名）⇒ 不发明一个上限来凑数。"
       "⭐ **下一问**：在 `AvatarStatusConfig`／`MonsterStatusConfig` 或别处找这个状态的定义。")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the slot-19 line occurs %d times" % text.count(OLD))
text = text.replace(OLD, NEW)

# --- and add this arc-round's shipment to section 四's closing list ---
ANCHOR = "### 本弧新增的三条纪律"
ADD = """### ⭐ 本弧新增的可复用能力（2026-10-02）

- **`ally_cid:<cid>`** —— 一个**按 cid 点名角色**的选择器，是本引擎第一个**前缀式选择器族**（闭集仍在：前缀是显式开的一扇门，不是回退）。
  读者：`1141519` 的「使**风堇**获得 2 层…」。判据 `AllyCidSelectorTest` 两侧：点名的那位拿到 2 层、同一场里另一个我方角色拿到 0 层；
  ⭐ 变异（让它回退到主人）⇒ `0 ; 0` ⇒ 红。
  配套：`Character.getCid()`。

"""
if text.count(ANCHOR) != 1:
    sys.exit("REFUSING: the lessons heading occurs %d times" % text.count(ANCHOR))
text = text.replace(ANCHOR, ADD + ANCHOR)
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text)
print("GAPS.md updated")

# --- the log keeps a block, as its own design asks ---
BLOCK = """
> **2026-10-02 更新（新目标（续）第 1 轮：🎉 **第 123 件出货 ＝ 选择器 `ally_cid:<cid>`** ✓）**：
>
> * ✅ **出货**：⭐ 按 **cid 点名一个角色** ✗ ✓ —— ⭐ 选择器集合里原本没有这一类 ✓（⭐ `party_first`／`next_ally` 是**位置** ✗、
>   ⭐ `lowest_hp_ally`／`random_ally_below_half_energy` 是**谓词** ✗）；⭐ 这是本引擎**第一个前缀式选择器族** ✓
>   （⭐ 闭集仍在 ✓ —— ⭐ 前缀是**显式开的一扇门** ✗，⭐ 不是"⭐ 认不出来就回退 ✗" ✓）。⭐ 配套 ⭐ `Character.getCid()` ✓。
> * ✅ **判据 ＋ 实测变异**：⭐ `AllyCidSelectorTest` ⇒ ⭐ `[ally_cid] the NAMED character has 2 ; a different ally has 0` ✓✓；
>   ⭐ **变异**（⭐ 让它回退到主人 ✗）⇒ ⭐ `0 ; 0` ⇒ 红 ✓。
> * ⛔ **slot 19「使风堇获得 2 层」仍登记** ✗ —— ⭐ 而缺口从"⭐ 跨表目标 ✗"变成了"⭐ **层数上限** ✗"：
>   ⭐ `StackBuff` ✗ ⭐ 是 ⭐ `Math.max(1, maxStacks)` ✗ ⇒ ⭐ 不给上限**夹到 1** ✓（⭐ 实测 ✓）；⭐ 而 ⭐ 该状态的上限
>   ⭐ **不在我能读到的表里** ✗（⭐ 忆灵技能表无层数字段 ✓；⭐ `StatusConfig`（2457 行 ✓）无该状态名 ✓）⇒ ⭐ 不发明上限 ✓。
> * ⚠ **我自己的**：⭐ 判据里又踩了 ⭐ `EffectSpec` 的字段名 ✗（⭐ `amount` ✗ 是 `Double` ✓；⭐ `maxStacks` ✗ **不是** `max_stacks` ✗ ✓）
>   —— ⭐ 这是**第二次**犯同一条 ✓ ⇒ ⭐⭐ **写测试内的 EffectSpec 时，⭐ 字段名照 Java 字段抄 ✓。**
"""

with io.open(LOG, "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
