"""Close the three rows with the measurements this goal asked for (round 1 of the new goal).

① 1217 「缩短【禳命】时长」: MEASURED, and it cannot be written. `BuffManager.extendBuffsFrom:296` is
       `if (source == null || turns <= 0 || (stateName == null && attribute == null)) return 0;`
   and `extendAllBuffs:319` is `if (turns <= 0) return 0;` -- negative turns are refused OUTRIGHT, so `EXTEND_BUFF` can only
   lengthen. The row's "缺什么" stops being a question and becomes "一个缩短具名状态时长的 op".

② 1415 忆灵技能 8: CONFIRMED that there is no third place. `MemospriteSpec` carries `name / source / note / panel / attack` and no skill
   list at all; the resource tree has no `servants/` data (the constant exists, the dir does not); `skills.json`'s nine 1415 rows are all
   her own (`141501`…`141519`); `skill_effects.json` carries `1409` (another memosprite master) but no 1415.

③ 1407: re-checked, unchanged -- `PLANNED = Set.of("REDUCE_TOUGHNESS")` and `Battle.performAction` only QUEUES (its own comment says the
   settlement is in afterMove/processRequests), which is what the row already records.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

REPLACEMENTS = [
    ("| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**",
     "| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**\uff08`1217` \u85ff\u85ff \u2713\uff09 "
     "| \u2757 **\u6d4b\u5b9a\uff1a\u5199\u4e0d\u51fa** \u2713 \u2014\u2014 \u5f15\u64ce**\u6ca1\u6709\u4efb\u4f55\u201c\u6539\u65f6\u957f\u201d\u7684 op** \u2717\uff0c"
     "\u800c\u4e14**\u8d1f\u503c\u88ab\u663e\u5f0f\u62d2\u7edd** \u2713\uff1a`BuffManager.extendBuffsFrom:296` \u662f "
     "`if (source == null || turns <= 0 || \u2026) return 0;` \u2713\uff0c`extendAllBuffs:319` \u662f `if (turns <= 0) return 0;` \u2713 "
     "\u21d2 `EXTEND_BUFF` **\u53ea\u80fd\u5ef6\u957f** \u2717\uff08`OPS_WITH_DURATION` \u7684\u4e94\u4e2a\u4e5f\u90fd\u662f\u201c\u6388\u4e88\u201d \u2713\uff09 "
     "| `1217`\uff081 \u4f4d\uff09 "
     "| \u4e00\u4e2a**\u7f29\u77ed\u5177\u540d\u72b6\u6001\u65f6\u957f**\u7684 op \u2717 |"),
    ("| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**",
     "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
     "| \u2757 **\u8be5\u6280\u80fd\u5728\u4e24\u5f20\u8868\u91cc\u90fd\u6ca1\u6709\u884c\uff0c\u800c\u4e14\u6ca1\u6709\u7b2c\u4e09\u5904** \u2713\uff08\u672c\u8f6e\u6d4b\u5b9a \u2713\uff09\uff1a"
     "\u2605 `MemospriteSpec` \u53ea\u6709 `name`\uff0f`source`\uff0f`note`\uff0f`panel`\uff0f`attack` \u2713 \u2014\u2014 **\u6ca1\u6709\u4efb\u4f55\u6280\u80fd\u8868** \u2717\uff1b"
     "\u2605 \u8d44\u6e90\u6811\u91cc**\u6ca1\u6709 `servants/` \u6570\u636e** \u2717\uff08`SERVANT_DIR` \u5e38\u91cf\u5728 \u2713\uff0c\u76ee\u5f55\u4e0d\u5728 \u2717\uff09\uff1b"
     "\u2605 `skills.json` \u7684\u4e5d\u884c**\u5168\u662f\u5979\u81ea\u5df1\u7684** \u2713\uff08`141501`\u2026`141519`\uff09\uff1b"
     "\u2605 `skill_effects.json` \u5e26 `1409` \u2713\uff08\u53e6\u4e00\u4f4d\u5fc6\u7075\u4e3b\u4eba \u2713\uff09\u800c**\u65e0 1415** \u2717\u3002"
     "\u21d2 \u5b83\u7684\u300c\u5bf9\u4e07\u654c\u65bd\u653e\u65f6\u300d**\u6ca1\u6709\u89e6\u53d1\u70b9** \u2717\u3002\u2b50 **\u4e0d\u9020**\uff1a\u8bfb\u8005 **1 \u4f4d** \u2717 \u21d2 \u767b\u8bb0 \u2713 "
     "| `1415`\uff081 \u4f4d\uff09 "
     "| \u7ed9\u8be5\u6280\u80fd\u4e00\u6761**\u884c**\uff08\u4f24\u5bb3\u7c7b\u8fdb `skills.json` \u2713\u3001\u975e\u4f24\u5bb3\u8fdb `skill_effects.json` \u2713\uff09\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |"),
]

for prefix, replacement in REPLACEMENTS:
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:24], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows are closed with the measurements")
