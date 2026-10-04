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
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d": (
        "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d "
        "| \u2705 `1506` \u5168\u90e8\u51fa\u8d27 \u2713\u3002\u2757 `1407` \u6708\u8307\u4e4b\u5e87\uff1a\u53ea\u5dee\u300c**\u4e00\u6b21\u884c\u52a8**\u4e2d\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u5168\u4f53\u300d"
        "\u9009\u62e9\u5668 \u2713\uff0c\u800c\u5b83\u4e0d\u662f\u4e00\u884c\u4ee3\u7801 \u2717\u2014\u2014\u672c\u8f6e\u91cf\u6e05\u5b83\u9700\u8981**\u4e94\u4f4d\u4e00\u4f53** \u2713\uff1a"
        "\u2460 \u4e00\u6761**\u884c\u52a8\u8fb9\u754c** \u2717\uff08`Battle.performAction` **\u53ea\u5165\u961f** \u2713\uff0c\u7ed3\u7b97\u5728 `afterMove`\uff0f`processRequests` \u2713\uff09\uff1b"
        "\u2461 \u884c\u52a8\u5185\u7684**\u53d7\u5bb3\u8005\u96c6\u5408** \u2717\uff1b\u2462 \u4e00\u4e2a**\u884c\u52a8\u7ed3\u675f\u7684\u516c\u544a\u70b9** \u2717"
        "\uff08\u6700\u8fd1\u7684\u662f `ATTACK_FINISHED` \u2713\uff0c\u800c\u4e00\u6b21**\u884c\u52a8**\u53ef\u542b\u591a\u6b21\u653b\u51fb \u2717\uff09\uff1b\u2463 \u9009\u62e9\u5668\u672c\u8eab \u2717\uff1b\u2464 \u9650\u989d\u7684\u914d\u5408 \u2713\u3002"
        "\u26a0 \u53e6\u5916\u4e24\u534a\u5df2\u5c31\u4f4d \u2713\uff08`defers_death` \u2713\uff1b`HEALED`\uff0f`SHIELD_GRANTED` \u21d2 `REMOVE_STATE` \u2713\uff09\u3002"
        "\u2b50 **\u4e0d\u9020**\uff1a\u8bfb\u8005 **1 \u4f4d** \u2717 \u21d2 \u6309\u672c\u9879\u76ee\u81ea\u5df1\u7684\u5148\u4f8b\u767b\u8bb0 \u2713\uff08`light_cones/_unmodelled.json`\uff1a\u201cReader count: 1 \u2026 below the bar, so registered rather than built\u201d \u2713\uff09 "
        "| `1407`\uff08\u6708\u8307\u4e4b\u5e87 \u2713\uff09 **1 \u4f4d**\uff08`1506` \u5df2\u51fa\u8d27 \u2713\uff09 "
        "| \u4e0a\u8ff0\u4e94\u4ef6\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |"),
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**": (
        "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
        "| \u2757 **\u8be5\u6280\u80fd\u5728**\u4e24\u5f20\u8868**\u91cc\u90fd\u6ca1\u6709\u884c** \u2713\uff08\u672c\u8f6e\u6d4b\u5b9a \u2713\uff09\uff1a"
        "\u2605 `skills.json` \u7684\u952e\u662f**\u6e38\u620f\u6280\u80fd id** \u2713\uff0c\u800c `1415` \u7684\u4e5d\u884c**\u5168\u662f\u5979\u81ea\u5df1\u7684** \u2713\uff08`141501`\u2026`141519`\uff09\uff1b"
        "\u2605 `skill_effects.json`\uff08**\u975e\u4f24\u5bb3**\u8868 \u2713\uff0c**\u6309\u4e3b\u4eba cid** \u2713\uff0c\u5b83**\u786e\u5b9e\u5e26** `1409` \u2713 \u2014\u2014 \u53e6\u4e00\u4f4d\u5fc6\u7075\u4e3b\u4eba \u2713\uff09**\u6ca1\u6709 1415** \u2717\uff1b"
        "\u2605 \u800c\u5fc6\u7075\u7531 `SummonFactory.servant` \u7528 `Memosprites.of(master.getCid(), SERVANT_DIR)` \u5efa \u2713\u3002"
        "\u21d2 \u6240\u4ee5\u5b83\u7684\u300c\u5bf9\u4e07\u654c\u65bd\u653e\u65f6\u300d\uff08\uff1d\u8be5\u6280\u80fd\u88ab\u65bd\u653e \u2713\uff09**\u6ca1\u6709\u89e6\u53d1\u70b9** \u2717\u3002"
        "\u2b50 **\u4e0d\u9020**\uff1a\u8bfb\u8005 **1 \u4f4d** \u2717 \u21d2 \u767b\u8bb0 \u2713 "
        "| `1415`\uff081 \u4f4d\uff09 "
        "| \u7ed9\u8be5\u6280\u80fd\u4e00\u6761**\u884c**\uff08\u4f24\u5bb3\u7c7b\u8fdb `skills.json` \u2713\u3001\u975e\u4f24\u5bb3\u8fdb `skill_effects.json` \u2713\uff09\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |"),
}

for prefix, replacement in ROWS.items():
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:20], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows now carry the five-part size, the reader count and the bar")
