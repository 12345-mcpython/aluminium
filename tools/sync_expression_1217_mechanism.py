"""The mechanism the tbgd files express, and the reader count that decides to BUILD it (round 1).

Game mechanism, read out of the original config (this is what the round was for):
  * 【禳命】's remaining turns live as a count on the modifier `MAvatar_Huohuo_Passive_HealMark`.
  * E2 is the ability `Avatar_Huohuo_00_Rank02_Insert`, and it does TWO same-family things:
        SetDynamicValueByAddValue { Key: Huohuo_Passive_HotCount, AddValue: -1, Min: 0, Max: 2 }
        SetModifierValue { ModifierName: MAvatar_Huohuo_Passive_HealMark, ModifyFunction: "Add", ValueType: "LifeTime" }
  * so 「缩短时长」 IS, in the game's own words, `Add` on a named modifier's `LifeTime` -- a SIGNED delta, clamped at Min 0.

Reader count, measured over the original ability configs:
  * 286 files scanned under ConfigAbility/Avatar and ConfigAbility/Servant
  * 50 abilities touch a modifier's LifeTime, spread over 26 files
  => far above this project's two-reader bar, so the capability is to be BUILT, in that same shape (one op, signed delta).

Next: relax `turns <= 0` to `turns == 0` in BuffManager's two extend entry points, clamp at 0, add the E2 rule to 1217.json,
judge it, and mutate the sign.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **「缩短【穹命】的持续**时长**」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| **「缩短【穹命】的持续**时长**」**（`1217` 藿藿 星魂 2 ✓） "
    "| ⭐⭐ **机制已从 tbgd 读出** ✓：游戏里「缩短时长」＝对具名 modifier 的 **`LifeTime` 做 `Add`** ✓"
    "（`Avatar_Huohuo_00_Rank02_Insert` 的 `SetModifierValue{ModifyFunction: \"Add\", ValueType: \"LifeTime\"}` ✓，"
    "配同族的 `SetDynamicValueByAddValue{AddValue: -1, Min: 0}` ✓）"
    "—— ⭐ 而我们的 `EXTEND_BUFF` **正是同一形状** ✓，只差“允许取负” ✗。"
    "⭐ **读者已量** ✓：扫 **286** 个能力文件得 **50 处**改 `LifeTime` ✓，分布在 **26** 个文件 ✓ ⇒ **该造** ✓ "
    "| `1217` 等 **26 个文件／50 处** ✓ "
    "| ① `turns <= 0` 改为 `turns == 0` ✓；② 结果**钳到 0** ✓（照游戏的 `Min: 0` ✓）；③ E2 加 `EXTEND_BUFF{buff: 穹命, turns: -1}` ✓ |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1217 row now carries the game's mechanism and a measured reader count")
