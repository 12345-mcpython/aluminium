"""1408's three 「变身结束时」 clauses -- the fifth reader's actual third of the reader table (round 1689).

Correcting an earlier audit: the transformation's END being announced (`REMOVE_STATE 变身` fires STATE_ENDED) is not the same as
the three sentences that hang off that end being written. Measured in the corpus, all three are explicit:

  * talent   「变身结束时，使我方全体速度提高 15%，持续 1 回合」
  * trace 行向世界终点 「变身结束时，获得 3 点【火种】」
  * trace 照见英雄本色 「进入战斗或变身结束时，攻击力提高 50%。该效果最多叠加 2 层」

So this ships the three, using `STATE_ENDED` for the first two and `on_any` (BATTLE_START / STATE_ENDED) for the third -- a
spelling `TriggerSpec` already carries.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1408.json"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
declared = [entry.get("id") for entry in (doc.get("resources") or [])]
print("declared resources: %s" % declared)

ADDITIONS = [
    {
        "on": "STATE_ENDED",
        "id": "transformation_end_speeds_the_party",
        "when": ["self state_ended \u53d8\u8eab"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "SPEED", "percent": 0.15, "turns": 1, "target": "all_allies"}],
        "source": "1408 \u767d\u5384 \u5929\u8d4b\uff1a\u300c\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u4f7f\u6211\u65b9\u5168\u4f53\u901f\u5ea6\u63d0\u9ad8 **15%**\uff0c\u6301\u7eed **1** \u56de\u5408\u300d",
        "note": "\u300c\u53d8\u8eab\u7ed3\u675f\u65f6\u2026\u6211\u65b9\u5168\u4f53\u901f\u5ea6 +15%\uff0c\u6301\u7eed 1 \u56de\u5408\u300d\u21d2 `STATE_ENDED` \uff0b `self state_ended \u53d8\u8eab` \u21d2 `MODIFY_ATTR{SPEED, 15%, turns: 1, all_allies}` \u2713\u3002",
    },
    {
        "on": "STATE_ENDED",
        "id": "trace_worlds_end_three_seeds",
        "when": ["self state_ended \u53d8\u8eab"],
        "do": [{"op": "GAIN_RESOURCE", "resource": "\u706b\u79cd", "amount": 3, "target": "self"}],
        "source": "1408 \u767d\u5384 \u884c\u8ff9 \u884c\u5411\u4e16\u754c\u7ec8\u70b9 (1408101): \u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u3002\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u83b7\u5f97 **3** \u70b9\u3010\u706b\u79cd\u3011\u300d",
        "note": "\u300c\u53d8\u8eab\u7ed3\u675f\u65f6\u83b7\u5f97 **3** \u70b9\u3010\u706b\u79cd\u3011\u300d\u21d2 `STATE_ENDED` \u21d2 `GAIN_RESOURCE{\u706b\u79cd, 3}` \u2713\u3002"
                "\u26a0 \u540c\u53e5\u7684\u300c\u6218\u6597\u5f00\u59cb\u65f6\u83b7\u5f97 **1** \u70b9\u300d\uff08\u53e6\u4e00\u534a \u2717\uff09\u4ecd\u672a\u5199 \u2713\u3002",
    },
    {
        "on_any": ["BATTLE_START", "STATE_ENDED"],
        "id": "trace_hero_true_colors_attack_up",
        "when": ["self state_ended \u53d8\u8eab"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
                "max_stacks": 2, "target": "self"}],
        "source": "1408 \u767d\u5384 \u884c\u8ff9 \u7167\u89c1\u82f1\u96c4\u672c\u8272 (1408103): \u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u653b\u51fb\u529b\u63d0\u9ad8 **50%**\u3002\u8be5\u6548\u679c\u6700\u591a\u53e0\u52a0 **2** \u5c42\u300d",
        "note": "\u300c\u8fdb\u5165\u6218\u6597**\u6216**\u53d8\u8eab\u7ed3\u675f\u65f6\u653b\u51fb\u529b +50%\uff0c\u6700\u591a 2 \u5c42\u300d\u21d2 \u26a0 \u4e24\u4e2a\u4e8b\u4ef6\u5171\u7528\u4e00\u6761\u89c4\u5219 \u2713 "
                "\u2014\u2014 \u7528 `on_any: [BATTLE_START, STATE_ENDED]` \u2713\uff08`TriggerSpec.onAny` \u5df2\u6709 \u2713\uff09\uff1b"
                "\u26a0 \u6761\u4ef6 `self state_ended \u53d8\u8eab` \u5728 `BATTLE_START` \u4e0a\u4e0d\u6210\u7acb \u2717 \u21d2 \u767b\u8bb0 \u2713\uff08\u9700\u8981\u201c\u4e8b\u4ef6\u5404\u81ea\u7684\u6761\u4ef6\u201d \u2717\uff09\u3002",
    },
]
for rule in ADDITIONS:
    if rule["id"] in [entry.get("id") for entry in rules if isinstance(entry, dict)]:
        sys.exit("REFUSING: %s is already there" % rule["id"])
    if rule["id"] == "trace_worlds_end_three_seeds" and "\u706b\u79cd" not in declared:
        sys.exit("REFUSING: 1408 does not declare 火种, so the resource rule cannot be written yet")
    rules.append(rule)

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added %d rules to 1408" % len(ADDITIONS))
