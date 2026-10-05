"""Split the third clause into the two rules its own sentence states (round 1689).

「进入战斗**或**变身结束时，攻击力提高 50%，最多叠加 2 层」 -- one effect, two events. `on_any` is not the JSON key (measured:
the loader reported "Unknown trigger event 'null'"), and even with the right key the `self state_ended 变身` condition cannot hold
on BATTLE_START. Two rules say it exactly, and the 2-layer cap is the stack cap either way.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1408.json"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]

kept = [rule for rule in rules
        if not (isinstance(rule, dict) and rule.get("id") == "trace_hero_true_colors_attack_up")]
if len(kept) != len(rules) - 1:
    sys.exit("REFUSING: the combined rule was not found")
doc["rules"] = kept

SOURCE = ("1408 白厄 行迹 照见英雄本色 (1408103): "
          "「进入战斗或变身结束时，攻击力提高 **50%**。该效果最多叠加 **2** 层」")
EFFECT = {"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
          "max_stacks": 2, "target": "self"}
doc["rules"].extend([
    {
        "on": "BATTLE_START",
        "id": "trace_hero_true_colors_on_battle_start",
        "do": [dict(EFFECT)],
        "source": SOURCE,
        "note": "「**进入战斗**…攻击力提高 50%，最多 2 层」⇒ `BATTLE_START` ⇒ "
                "`MODIFY_ATTR{ATTACK, 50%, permanent, max_stacks: 2}` ✓。"
                "⚠ 同句的另一半（**变身结束时** ✓）是**另一条规则** ✓ —— "
                "⚠ 实测：一条规则写两个事件的拼法（`on_any` ✗）**不成立** ✗"
                "（装载器：*\"Unknown trigger event 'null'\"* ✓），而且条件也不可能在两个事件上同时成立 ✗。",
    },
    {
        "on": "STATE_ENDED",
        "id": "trace_hero_true_colors_on_transformation_end",
        "when": ["self state_ended 变身"],
        "do": [dict(EFFECT)],
        "source": SOURCE,
        "note": "「**变身结束时**…攻击力提高 50%（最多 2 层）」⇒ `STATE_ENDED` ＋ "
                "`self state_ended 变身` ⇒ 同一个效果 ✓（两条规则共用 `max_stacks: 2` ✓）。",
    },
])

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1408 now has the clause as two rules: %d total" % len(doc["rules"]))
