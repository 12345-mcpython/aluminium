"""Two corrections from the measurements this goal asked for (round 1 of the new goal).

① 1217: the 「持续回合数减 1」 family is NOT a shortening mechanic. Eighteen documents state it, and reading any of them shows why they
   matched: 「施放战技后藿藿获得【禳命】，持续 2 回合，**藿藿每回合开始时**持续回合数减1」 -- that is the normal TICK of a timed buff, which
   `APPLY_BUFF`'s `turns` already expresses. What is actually missing is 星魂 2's EXTRA reduction (「…使【禳命】的持续回合数减 1」, on top of
   the tick), and that has ONE reader. The hard evidence for "cannot be written": `BuffManager.extendBuffsFrom:296` and `extendAllBuffs:319`
   both refuse `turns <= 0`.

② 1415: the memosprite DOES have its own data home -- `ServantID 11415` -- and its skills are numbered 1…18. 忆灵技能 8 is
   「献予「纷争」之诗 / Ode to Strife」, 效果 `辅助`, 元素 无, with 效果ID `[10000001, 10000011]`. Our two tables have no `11415` key at all, so the
   gap is an IMPORT gap, not the engine's inability to hold a rules-only skill (which is what the row used to claim).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

REPLACEMENTS = [
    ("| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**",
     "| **\u300c\u7f29\u77ed\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed**\u65f6\u957f**\u300d**\uff08`1217` \u85ff\u85ff \u661f\u9b42 2 \u2713\uff09 "
     "| \u2b50 **\u8ba2\u6b63\uff08\u672c\u8f6e\u6d4b\u5b9a \u2713\uff09**\uff1a\u300c\u6301\u7eed\u56de\u5408\u6570\u51cf 1\u300d\u8fd9\u4e00\u65cf\uff08**18 \u7bc7** \u2713\uff09"
     "**\u4e0d\u662f\u7f29\u77ed\u673a\u5236** \u2717 \u2014\u2014 \u8bfb\u4efb\u4e00\u7bc7\u5c31\u77e5\u9053\uff1a\u300c\u65bd\u653e\u6218\u6280\u540e\u85ff\u85ff\u83b7\u5f97\u3010\u7a79\u547d\u3011\uff0c**\u6301\u7eed 2 \u56de\u5408**\uff0c"
     "**\u85ff\u85ff\u6bcf\u56de\u5408\u5f00\u59cb\u65f6**\u6301\u7eed\u56de\u5408\u6570\u51cf1\u300d\u2713 \u2014\u2014 \u90a3\u662f**\u5b9a\u65f6 buff \u7684\u8df3\u6570** \u2713\uff0c`APPLY_BUFF` \u7684 `turns` \u672c\u6765\u5c31\u8868\u8fbe \u2713\u3002"
     "\u2757 \u771f\u6b63\u7f3a\u7684\u662f**\u661f\u9b42 2 \u7684\u201c\u989d\u5916\u518d\u51cf 1\u201d** \u2717\uff08\u300c\u2026\u4f7f\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed\u56de\u5408\u6570\u51cf 1\u300d\uff0c\u8df3\u6570\u4e4b\u5916 \u2713\uff09\uff0c"
     "**\u8bfb\u8005 1 \u4f4d** \u2717\u3002\ud83d\udee1 \u201c\u5199\u4e0d\u51fa\u201d\u7684\u786c\u8bc1\u636e\uff1a"
     "`BuffManager.extendBuffsFrom:296` \u4e0e `extendAllBuffs:319` **\u90fd\u62d2\u7edd** `turns <= 0` \u2713 "
     "| `1217`\uff081 \u4f4d\uff09 "
     "| \u4e00\u4e2a**\u7f29\u77ed\u5177\u540d\u72b6\u6001\u65f6\u957f**\u7684 op \u2717\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |"),
    ("| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**",
     "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
     "| \u2b50 **\u8ba2\u6b63\uff08\u672c\u8f6e\u6d4b\u5b9a \u2713\uff09**\uff1a\u5fc6\u7075**\u6709\u81ea\u5df1\u7684\u6570\u636e\u4f4d\u7f6e** \u2713 \u2014\u2014 `ServantID` **11415** \u2713\uff0c"
     "\u6280\u80fd\u7f16\u53f7 **1\u202618** \u2713\u3002\u8be5\u6280\u80fd\uff1d**\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7 / Ode to Strife** \u2713\uff0c"
     "\u6548\u679c `\u8f85\u52a9`\u3001\u5143\u7d20 `\u65e0`\u3001\u6700\u9ad8\u7b49\u7ea7 10\u3001**\u6548\u679cID `[10000001, 10000011]`** \u2713\u3002"
     "\u800c\u6211\u4eec\u4e24\u5f20\u8868\u91cc**\u6ca1\u6709 `11415` \u952e** \u2717 \u21d2 \u2757 \u7f3a\u7684\u662f**\u5bfc\u5165** \u2717\uff0c"
     "\u800c\u4e0d\u662f\u201c\u5f15\u64ce\u653e\u4e0d\u4e0b\u4e00\u4e2a\u53ea\u89e6\u53d1\u89c4\u5219\u7684\u6280\u80fd\u201d \u2717\uff08\u540e\u8005\u662f\u672c\u884c\u539f\u5148\u7684\u8bf4\u6cd5 \u2717\uff09\u2014\u2014 "
     "\u5f15\u64ce\u7684\u673a\u5236\u662f\u201c**\u4e3b\u4eba cid \uff0b \u69fd\u4f4d**\u201d \u2713\uff08`1409` \u5c31\u662f\u4f8b\u5b50 \u2713\uff09\uff0c\u5b83\u80fd\u627f\u8f7d \u2713\u3002"
     "\u2b50 **\u4e0d\u9020**\uff1a\u8bfb\u8005 **1 \u4f4d** \u2717 \u21d2 \u767b\u8bb0 \u2713 "
     "| `1415`\uff081 \u4f4d\uff09 "
     "| \u628a\u5fc6\u7075\uff08`ServantID 11415` \u2713\uff09\u7684\u6280\u80fd\u884c\u5bfc\u5165\u6211\u4eec\u7684\u6570\u636e\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |"),
]

for prefix, replacement in REPLACEMENTS:
    hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(hits) != 1:
        sys.exit("REFUSING %s: %d rows" % (prefix[:24], len(hits)))
    lines[hits[0]] = replacement

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   both rows carry the corrections")
