"""Add the two summons rules the documents state (round 1673).

1409's skill 「彩虹尽头的…」 says 「召唤忆灵 小伊卡，为除小伊卡以外的我方全体目标回复…」 and 1415's ultimate says 「召唤忆灵 德谬歌…」
-- so each needs a SUMMON rule for the panel to be reached. Handles both file shapes (bare list and {resources, rules}).
"""
import io
import json
import sys

RULES = {
    "1409": {
        "on": "SKILL_CAST",
        "id": "skill_summons_ica",
        "when": ["actor == self"],
        "do": [{"op": "SUMMON"}],
        "source": "1409 \u98ce\u5807 \u6218\u6280 \u5f69\u8679\u5c3d\u5934\u7684\u2026 (140902): \u300c\u53ec\u5524\u5fc6\u7075 \u5c0f\u4f0a\u5361\uff0c\u4e3a\u9664\u5c0f\u4f0a\u5361\u4ee5\u5916\u7684\u6211\u65b9\u5168\u4f53\u76ee\u6807\u56de\u590d\u7b49\u540c\u4e8e\u98ce\u5807 8.00% \u751f\u547d\u4e0a\u9650+ 160 \u7684\u751f\u547d\u503c\u3002\u300d",
        "note": "\u300c**\u53ec\u5524\u5fc6\u7075 \u5c0f\u4f0a\u5361**\u300d\u21d2 `SKILL_CAST` \u21d2 **`SUMMON`** \u2713\uff08\u65e0\u53c2\u6570 \u2713\uff1a\u5fc6\u7075\u5c5e\u4e8e\u53ec\u5524\u8005 \u2713\uff09\uff1b"
                "\u9762\u677f\u5728 `resources/memosprites/1409.json` \u2713\u3002\u26a0 \u540c\u53e5\u7684**\u6cbb\u7597\u90e8\u5206**\uff08\u6211\u65b9\u5168\u4f53 8% \u751f\u547d + 160 \u2717\u3001\u5c0f\u4f0a\u5361\u81ea\u5df1 10% + 200 \u2717\uff09"
                "\u5c1a\u672a\u5199 \u2717 \u21d2 \u767b\u8bb0 \u2713\uff08\u5b83\u9700\u8981\u201c\u9664\u5c0f\u4f0a\u5361\u4ee5\u5916\u201d\u7684\u76ee\u6807\u8bcd\u6c47 \u2717\uff09\u3002",
    },
    "1415": {
        "on": "ULT_CAST",
        "id": "ult_summons_demiurge",
        "when": ["actor == self"],
        "do": [{"op": "SUMMON"}],
        "source": "1415 \u6614\u6d9f \u7ec8\u7ed3\u6280 \u8bd7\u7684\u300c\u25e6\u300d\u8a93\u7ea6\u7684\u300c\u221e\u300d (141503/141504): \u300c\u53ec\u5524\u5fc6\u7075 \u5fb7\u8c2c\u6b4c\u2026\u5355\u573a\u6218\u6597\u4e2d\u53ea\u80fd\u65bd\u653e 1 \u6b21\u3002\u300d",
        "note": "\u300c**\u53ec\u5524\u5fc6\u7075 \u5fb7\u8c2c\u6b4c**\u300d\u21d2 `ULT_CAST` \u21d2 **`SUMMON`** \u2713\uff1b\u9762\u677f\u5728 `resources/memosprites/1415.json` \u2713\u3002"
                "\u26a0 \u540c\u53e5\u8fd8\u8bf4\u300c\u4f7f\u5176\u7acb\u5373\u83b7\u5f97 1 \u4e2a**\u989d\u5916\u56de\u5408**\u2717\u3001\u6fc0\u6d3b\u5168\u4f53\u961f\u53cb\u7684\u7ec8\u7ed3\u6280 \u2717\u3001\u8fdb\u5165\u3010\u5f80\u6614\u7684\u6d9f\u6f2a\u3011\u2717\u3001"
                "\u5c55\u5f00\u6218\u6280\u7ed3\u754c \u2717\u3001\u53cc\u65b9\u66b4\u51fb\u7387 +50% \u2717\u3001\u5355\u573a\u4e00\u6b21 \u2713\uff08`once_per_battle` \u2713 \u53ef\u5199 \u2713\uff09\u300d\u21d2 \u9664\u53ec\u5524\u5916**\u9010\u6761\u767b\u8bb0** \u2713\u3002",
    },
}

for cid, rule in RULES.items():
    path = "src/main/resources/characters/%s.json" % cid
    with io.open(path, encoding="utf-8") as handle:
        doc = json.load(handle)
    rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
    if any(isinstance(existing, dict) and existing.get("id") == rule["id"] for existing in rules):
        sys.exit("REFUSING: %s already has %s" % (cid, rule["id"]))
    rules.append(rule)
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("ok   added %s to %s" % (rule["id"], path))
