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
        "source": "1409 风堇 战技 彩虹尽头的… (140902): 「召唤忆灵 小伊卡，为除小伊卡以外的我方全体目标回复等同于风堇 8.00% 生命上限+ 160 的生命值。」",
        "note": "「**召唤忆灵 小伊卡**」⇒ `SKILL_CAST` ⇒ **`SUMMON`** ✓（无参数 ✓：忆灵属于召唤者 ✓）；"
                "面板在 `resources/memosprites/1409.json` ✓。⚠ 同句的**治疗部分**（我方全体 8% 生命 + 160 ✗、小伊卡自己 10% + 200 ✗）"
                "尚未写 ✗ ⇒ 登记 ✓（它需要“除小伊卡以外”的目标词汇 ✗）。",
    },
    "1415": {
        "on": "ULT_CAST",
        "id": "ult_summons_demiurge",
        "when": ["actor == self"],
        "do": [{"op": "SUMMON"}],
        "source": "1415 昔涟 终结技 诗的「◦」誓约的「∞」 (141503/141504): 「召唤忆灵 德谬歌…单场战斗中只能施放 1 次。」",
        "note": "「**召唤忆灵 德谬歌**」⇒ `ULT_CAST` ⇒ **`SUMMON`** ✓；面板在 `resources/memosprites/1415.json` ✓。"
                "⚠ 同句还说「使其立即获得 1 个**额外回合**✗、激活全体队友的终结技 ✗、进入【往昔的涟漪】✗、"
                "展开战技结界 ✗、双方暴击率 +50% ✗、单场一次 ✓（`once_per_battle` ✓ 可写 ✓）」⇒ 除召唤外**逐条登记** ✓。",
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
