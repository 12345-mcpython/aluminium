"""Replace the healing bullet by FINDING its line, not by matching its whole text (2026-10-02).

The last two attempts refused because the bullet's exact wording differs from what I typed into the anchor. Finding the line that contains the distinctive phrase, then replacing
that whole line, cannot drift.
"""
import io
import sys

GAPS = "GAPS.md"
lines = io.open(GAPS, encoding="utf-8").read().split("\n")

NEEDLE = "\u8ba1\u5165\u5c0f\u4f0a\u5361"
hits = [i for i, l in enumerate(lines) if NEEDLE in l]
print("lines mentioning the healing total: %s" % [i + 1 for i in hits])
if len(hits) != 1:
    sys.exit("REFUSING: %d lines mention it" % len(hits))

NEW = ("- \u26d4\u300c\u8ba1\u5165\u5c0f\u4f0a\u5361\u5fc6\u7075\u6280\u7684\u6cbb\u7597\u6570\u503c\u989d\u5916\u63d0\u9ad8\u2026\u7b49\u540c\u4e8e\u672c\u6b21\u6cbb\u7597\u6570\u503c\u7684 `#1%`\u300d\uff1a"
       "**\u8f7d\u4f53\u5df2\u7ecf\u627e\u5230\u4e86\uff0c\u5361\u5728\u522b\u5904**\uff082026-10-02 \u5b9e\u6d4b\uff09\uff1a\n"
       "  - \u2705 `HEALED` **\u5e26\u91cf**\uff08`fireTriggersForAlly(TriggerEvent.HEALED, healer, target, healed)`\uff09\uff0c"
       "\u4e14\u8be5\u4e8b\u4ef6\u7684 **actor \u662f\u6cbb\u7597\u8005** \u21d2\u300c\u98ce\u5807\u2026\u63d0\u4f9b\u6cbb\u7597\u300d\u53ef\u7528 `actor has_state`\uff1b\n"
       "  - \u2705 `GAIN_RESOURCE` \u5df2\u652f\u6301 `amount_from_event` \u00d7 `amount_percent`\uff08\u5b57\u9762\u91cf\uff09\uff1b\n"
       "  - \u26d4 **\u7f3a\u53e3\u4e00**\uff1a\u300c\u7d2f\u8ba1\u6cbb\u7597\u6570\u503c\u300d\u5c5e\u4e8e**\u98ce\u5807\u7684\u5c0f\u4f0a\u5361**\uff0c\u800c\u9009\u62e9\u5668\u53ea\u80fd\u70b9\u540d**\u89d2\u8272**"
       "\uff08`ally_cid:`\uff09\uff0c\u70b9\u4e0d\u4e86**\u522b\u4eba\u7684\u5fc6\u7075** \u21d2 \u9700\u8981\u300c\u67d0\u89d2\u8272\u7684\u5fc6\u7075\u300d\u8fd9\u6837\u7684\u9009\u62e9\u5668\uff1b\n"
       "  - \u26d4 **\u7f3a\u53e3\u4e8c**\uff1a`characters/1409.json` \u662f**\u5217\u8868**\uff0c\u88c5\u4e0d\u4e0b `resources` \u21d2 \u9700\u8981\u8d44\u6e90\u58f0\u660e\u6709\u522b\u7684\u843d\u70b9\uff1b\n"
       "  - \u26d4 **\u7f3a\u53e3\u4e09**\uff1a`amount_percent` \u53ea\u80fd\u662f\u5b57\u9762\u91cf\uff0c\u800c `#1` \u968f\u7b49\u7ea7\u53d8\uff080.36 \u2192 1.008\uff09"
       "\u21d2 \u9700\u8981 `amount_percent_from_skill_param`\uff08\u672c\u8f6e\u505a\u4e86\u53c8\u56de\u6eda\uff1a\u5224\u636e\u5728 `BATTLE_START` \u671f\u95f4\u7531 `HEAL` \u89e6\u53d1\u7684 "
       "`HEALED` \u4e0a\u8bfb\u6570\u4ecd\u4e3a 0\uff0c\u2b50 **\u4e0b\u4e00\u95ee**\uff1a\u90a3\u6b21 `GAIN_RESOURCE` \u5230\u5e95\u6709\u6ca1\u6709\u88ab\u8d70\u5230\uff09\u3002")

lines[hits[0]] = NEW
io.open(GAPS, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("GAPS.md bullet replaced (line %d)" % (hits[0] + 1))

BLOCK = io.open("tools/_block65.txt", encoding="utf-8").read()
with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
