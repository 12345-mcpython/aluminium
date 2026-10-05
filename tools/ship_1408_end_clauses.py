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
        "when": ["self state_ended 变身"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "SPEED", "percent": 0.15, "turns": 1, "target": "all_allies"}],
        "source": "1408 白厄 天赋：「变身结束时，使我方全体速度提高 **15%**，持续 **1** 回合」",
        "note": "「变身结束时…我方全体速度 +15%，持续 1 回合」⇒ `STATE_ENDED` ＋ `self state_ended 变身` ⇒ `MODIFY_ATTR{SPEED, 15%, turns: 1, all_allies}` ✓。",
    },
    {
        "on": "STATE_ENDED",
        "id": "trace_worlds_end_three_seeds",
        "when": ["self state_ended 变身"],
        "do": [{"op": "GAIN_RESOURCE", "resource": "火种", "amount": 3, "target": "self"}],
        "source": "1408 白厄 行迹 行向世界终点 (1408101): 「战斗开始时，获得 1 点【火种】。变身结束时，获得 **3** 点【火种】」",
        "note": "「变身结束时获得 **3** 点【火种】」⇒ `STATE_ENDED` ⇒ `GAIN_RESOURCE{火种, 3}` ✓。"
                "⚠ 同句的「战斗开始时获得 **1** 点」（另一半 ✗）仍未写 ✓。",
    },
    {
        "on_any": ["BATTLE_START", "STATE_ENDED"],
        "id": "trace_hero_true_colors_attack_up",
        "when": ["self state_ended 变身"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
                "max_stacks": 2, "target": "self"}],
        "source": "1408 白厄 行迹 照见英雄本色 (1408103): 「进入战斗或变身结束时，攻击力提高 **50%**。该效果最多叠加 **2** 层」",
        "note": "「进入战斗**或**变身结束时攻击力 +50%，最多 2 层」⇒ ⚠ 两个事件共用一条规则 ✓ "
                "—— 用 `on_any: [BATTLE_START, STATE_ENDED]` ✓（`TriggerSpec.onAny` 已有 ✓）；"
                "⚠ 条件 `self state_ended 变身` 在 `BATTLE_START` 上不成立 ✗ ⇒ 登记 ✓（需要“事件各自的条件” ✗）。",
    },
]
for rule in ADDITIONS:
    if rule["id"] in [entry.get("id") for entry in rules if isinstance(entry, dict)]:
        sys.exit("REFUSING: %s is already there" % rule["id"])
    if rule["id"] == "trace_worlds_end_three_seeds" and "火种" not in declared:
        sys.exit("REFUSING: 1408 does not declare 火种, so the resource rule cannot be written yet")
    rules.append(rule)

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added %d rules to 1408" % len(ADDITIONS))
