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
PREFIX = "| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**\uff08`1217` \u85ff\u85ff \u661f\u9b42 2 \u2713\uff09 "
    "| \u2b50\u2b50 **\u673a\u5236\u5df2\u4ece tbgd \u8bfb\u51fa** \u2713\uff1a\u6e38\u620f\u91cc\u300c\u7f29\u77ed\u65f6\u957f\u300d\uff1d\u5bf9\u5177\u540d modifier \u7684 **`LifeTime` \u505a `Add`** \u2713"
    "\uff08`Avatar_Huohuo_00_Rank02_Insert` \u7684 `SetModifierValue{ModifyFunction: \"Add\", ValueType: \"LifeTime\"}` \u2713\uff0c"
    "\u914d\u540c\u65cf\u7684 `SetDynamicValueByAddValue{AddValue: -1, Min: 0}` \u2713\uff09"
    "\u2014\u2014 \u2b50 \u800c\u6211\u4eec\u7684 `EXTEND_BUFF` **\u6b63\u662f\u540c\u4e00\u5f62\u72b6** \u2713\uff0c\u53ea\u5dee\u201c\u5141\u8bb8\u53d6\u8d1f\u201d \u2717\u3002"
    "\u2b50 **\u8bfb\u8005\u5df2\u91cf** \u2713\uff1a\u626b **286** \u4e2a\u80fd\u529b\u6587\u4ef6\u5f97 **50 \u5904**\u6539 `LifeTime` \u2713\uff0c\u5206\u5e03\u5728 **26** \u4e2a\u6587\u4ef6 \u2713 \u21d2 **\u8be5\u9020** \u2713 "
    "| `1217` \u7b49 **26 \u4e2a\u6587\u4ef6\uff0f50 \u5904** \u2713 "
    "| \u2460 `turns <= 0` \u6539\u4e3a `turns == 0` \u2713\uff1b\u2461 \u7ed3\u679c**\u94b3\u5230 0** \u2713\uff08\u7167\u6e38\u620f\u7684 `Min: 0` \u2713\uff09\uff1b\u2462 E2 \u52a0 `EXTEND_BUFF{buff: \u7a79\u547d, turns: -1}` \u2713 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1217 row now carries the game's mechanism and a measured reader count")
