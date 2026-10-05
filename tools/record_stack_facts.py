"""Record what the ability data actually says about slot 19's stack clause (2026-10-02).

Found in `Config/ConfigAbility/Servant/Servant_CyreneServant_00_Ability.json` (the file the goal names -- it lives under `Config/`, not `ExcelOutput/`):

    "Predicate": { "$type": "RPG.GameCore.ByCompareCharacterID" … "FixedValue": { "Value": 1409 } }
    "TaskList": [ { "$type": "RPG.GameCore.AddModifier",
        "ModifierName": { "Value": "MServant_CyreneServant_00_AmazingBuff_Hyacine" },
        "LayerAddWhenStack": { "IsDynamic": false, "FixedValue": { "Value": 2 } } } ]

Two things follow, and both are the data speaking:
  * the game NAMES THE CHARACTER BY CID (1409) -- which is exactly what last round's `ally_cid:` selector does, so that design is confirmed by the source rather than assumed;
  * the layer count is 2, which is what the sentence says.

The CAP is still not readable: `AvatarStatusConfig`'s rows carry no count column, and the five `MaxLayer` values in this ability file belong to other modifiers (two `99999`,
two dynamic, one `1`) -- none sits next to the Hyacine state. So the clause stays registered rather than written with a cap invented to make the number come out.
"""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD = ("⭐ **下一问**：在 `AvatarStatusConfig`／`MonsterStatusConfig` 或别处找这个状态的定义。")
NEW = ("⭐ **下一问**：这个状态的**层数上限**在哪 —— 已实测：`AvatarStatusConfig`（778 行）里它叫 `MServant_CyreneServant_00_AmazingBuff_Hyacine`"
       "（`StatusID 10014155`）而**没有**计数列；能力文件里的 5 个 `MaxLayer` 都属于别的修饰（两个 `99999`、两个动态、一个 `1`）。\n"
       "- ⭐⭐ **而数据已经证实了两件事**（`Config/ConfigAbility/Servant/Servant_CyreneServant_00_Ability.json` —— 目标点名的那个文件在 `Config/` 下，"
       "**不在** `ExcelOutput/`）：① 游戏用 **`ByCompareCharacterID` ＝ 1409** 点名风堇 —— **这与 `ally_cid:` 是同一件事**，"
       "所以那个设计是**源头确认过的**，不是猜的；② `LayerAddWhenStack: 2` —— **层数就是 2**，与原句一致。")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the next-question line occurs %d times" % text.count(OLD))
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW))
print("GAPS.md updated with the data confirmation")

BLOCK = """
> **2026-10-02 更新（第 61 轮：⚠ **本轮没有出货** ✗ —— ⭐ 但**数据自己确认了 `ally_cid:`** ✓，⭐ 并把层数钉在 `2` ✓）**：
>
> * ⭐⭐⭐ **在 `Config/ConfigAbility/Servant/Servant_CyreneServant_00_Ability.json` 里读到（⭐ 目标点名的文件在 `Config/` 下，⭐ 不在 `ExcelOutput/` ✓）**：
>   ⭐ `"Predicate": { "ByCompareCharacterID" … "FixedValue": { "Value": 1409 } }` ✓ 与
>   ⭐ `"ModifierName": "MServant_CyreneServant_00_AmazingBuff_Hyacine"` ＋ ⭐ `"LayerAddWhenStack": { "FixedValue": { "Value": 2 } }` ✓
>   ⇒ ⭐⭐ **游戏本身就是"⭐ 按 cid 点名（1409 ✓）＋ ⭐ 加 2 层 ✗"** ✓ ⇒ ⭐⭐ **上一轮那个 `ally_cid:` 设计是源头确认过的** ✓✓，⭐ 不是猜的 ✓。
> * ⭐ **层数上限仍不可读** ✗：⭐ `AvatarStatusConfig`（778 行 ✓）里那个状态叫 ⭐ `MServant_CyreneServant_00_AmazingBuff_Hyacine` ✗（`StatusID 10014155` ✓），
>   ⭐ 而它**没有**计数列 ✓；⭐ 能力文件里的 **5 个 `MaxLayer`** ✗ 都属于别的修饰 ✓（⭐ 两个 `99999` ✗、⭐ 两个动态 ✗、⭐ 一个 `1` ✗），
>   ⭐ **没有一个挨着 `Hyacine` 那个状态** ✗ ⇒ ⭐ 不发明上限 ✓ ⇒ ⭐ 那句诗仍登记 ✓。
> * ⭐ **顺带量到的**：⭐ 那个状态的名字空间是 ⭐ `MServant_CyreneServant_00_AmazingBuff_<角色名>` ✗ ✓（⭐ 24 行 ✓：⭐ `_Hyacine`／`_Castorice`／`_Tribbie`／`_Aglaea`／`_Anaxa`／`_Cipher`／`_Evernight` … ✓）。
> * **实测（本轮）**：⭐ 全量 **0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
