"""Record round 62's shipment AND the regression I caused and then fixed (2026-10-02).

Honest record: the first commit of this content went out while the full suite was RED (22 failures). The cause was mine: the rule omitted `from_skill_id`, so EVERY memosprite cast
triggered it, and `ally_cid:1409` throws when the named character is not in the battle. The fix is the gate the data itself implies (the modifier sits in skill 19's own TaskList),
and the judge had to cast skill 19 to match.
"""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD_START = "- ⛔「德谬歌施放忆灵技时，使风堇获得"
line_start = text.find(OLD_START)
if line_start < 0:
    sys.exit("REFUSING: the slot-19 stack bullet was not found")
line_end = text.find("\n", line_start)
old_bullet = text[line_start:line_end]

NEW_BULLET = (
    "- ✅ 已出货（2026-10-02）：「德谬歌施放忆灵技时，使风堇获得 **2 层**【献予「天空」之诗】」 —— "
    "`on: CAST_SETUP` ＋ `when: [actor is_summon, from_skill_id == 19]` ⇒ "
    "`ADD_STACK{buff, amount: 2, max_stacks: 99999, permanent, target: \"ally_cid:1409\"}`。"
    "⭐ 判据 `SkyOdeStackTest`：点名的风堇 **2 层**、同一场里另一个我方角色 **0 层**。\n"
    "- ⭐⭐ **上限为什么写 99999（数据自己的惯例）**：能力数据把这句话写成 `AddModifier` ＋ `LayerAddWhenStack: 2`，前置 `ByCompareCharacterID = 1409`"
    "（**游戏就是按 cid 点名**）；提到该修饰的四个文件里它旁边**没有** `MaxLayer`（`AvatarStatusConfig` 的行甚至没有计数列），"
    "而同一个能力文件里「不限」写的就是 `MaxLayer: 99999`（两处）⇒ **缺省 ＝ 不限**。⚠ 不写它，引擎的 `StackBuff`（`Math.max(1, maxStacks)`）只给 1 层。\n"
    "- ⚠⚠ **这次修复的回归（我的错，已记录）**：第一版规则**漏了 `from_skill_id`**，于是**任何**忆灵的施放都会触发它，"
    "而队伍里没有风堇时 `ally_cid:1409` 会**抛异常** ⇒ **22 例红**（全是既有判据）。"
    "⭐ 修法有二：① 补上数据自己就有的那道门（该修饰写在 **19 号技能**的 `TaskList` 里）；② 判据要铸 **19 号**。\n"
    "- ⭐ **引擎侧仍有一处锋利的边（已登记）**：`ally_cid:<cid>` 在**点名的人不在场**时走的是 `require(...)` ⇒ **抛异常**，"
    "而引擎自己的惯例是**空列表、不是错误**（`resolveTargets` 里 `lowest_hp_ally` 旁边那句注释：「clause does nothing」）。"
    "⭐ **下一问**：把 `ally_cid:` 移进**复数**解析路径，让它在人不在场时静默不生效。"
)

text = text[:line_start] + NEW_BULLET + text[line_end:]
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text)
print("GAPS.md updated (slot 19's stack half + the regression + the sharp edge)")

BLOCK = """
> **2026-10-02 更新（第 62 轮：🎉 **第 124 件出货 ＝ `1141519` 第一句「使风堇获得 2 层」整句成句** ✓ —— ⚠ 但同一轮我先交付了一次**红的全量**，⭐ 已修并记录）**：
>
> * ✅ **一次批搜定案** ✓（⭐ 按我自己的教训 ✓）：⭐ 遍历整个 tbgd，⭐ 提到 `AmazingBuff_Hyacine` 的文件共 **4 个** ✓，
>   ⭐ 而 ⭐ **每一个 "cap-ish" 键都是 `LayerAddWhenStack: 2`** ✗ ⇒ ⭐ 它旁边**没有** `MaxLayer` ✗（⭐ 状态表甚至没有计数列 ✓）。
> * ⭐⭐ **上限照游戏自己的惯例写** ✗：⭐ 同一能力文件里"不限"写的就是 ⭐ **`MaxLayer: 99999`** ✗（两处 ✓）⇒ **缺省 ＝ 不限**；
>   ⚠ 不写它，⭐ `StackBuff`（`Math.max(1, maxStacks)`）只会给 **1** 层 ✓（实测 ✓）。
> * ⭐⭐⭐ **数据自己确认了 `ally_cid:`** ✓：⭐ `"ByCompareCharacterID" … "Value": 1409` ✓ ⇒ ⭐ **游戏按 cid 点名** ✓，⭐ 与我们的选择器同一件事 ✓。
> * ⚠⚠ **我交付过一次红的全量（⭐ 诚实记录 ✓）**：⭐ 第一版规则**漏了 `from_skill_id`** ✗ ⇒ ⭐ **任何**忆灵的施放都触发它 ⇒
>   ⭐ 队伍里没有风堇时 ⭐ `ally_cid:1409` ✗ **抛异常** ⇒ ⭐ **22 例红** ✗（⭐ 全是既有判据 ✓）。⭐ 修法：① 补数据本来就有的那道门
>   （⭐ 该修饰写在 **19 号技能**的 `TaskList` 里 ✓）；② 判据改铸 **19 号** ⇒ ⭐ 全量回绿（**2303 例** ✓）。
>   ⭐⭐ **教训：⭐ 一条 `when` 写得越宽，⭐ 它在新队伍里的失败面就越大 ✓ —— ⭐ 加规则前先问"⭐ 它在**别的**判据的战场上会不会触发 ✗" ✓。**
> * ⭐ **仍锋利的一处（已登记 ✓）**：⭐ `ally_cid:` ✗ ⭐ 在点名者不在场时**抛异常** ✗，⭐ 而引擎惯例是**空列表** ✗ ✓（⭐ `resolveTargets` 里 `lowest_hp_ally` 旁的原话 ✓）。
> """

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
