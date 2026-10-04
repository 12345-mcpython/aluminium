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

SOURCE = ("1408 \u767d\u5384 \u884c\u8ff9 \u7167\u89c1\u82f1\u96c4\u672c\u8272 (1408103): "
          "\u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u653b\u51fb\u529b\u63d0\u9ad8 **50%**\u3002\u8be5\u6548\u679c\u6700\u591a\u53e0\u52a0 **2** \u5c42\u300d")
EFFECT = {"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
          "max_stacks": 2, "target": "self"}
doc["rules"].extend([
    {
        "on": "BATTLE_START",
        "id": "trace_hero_true_colors_on_battle_start",
        "do": [dict(EFFECT)],
        "source": SOURCE,
        "note": "\u300c**\u8fdb\u5165\u6218\u6597**\u2026\u653b\u51fb\u529b\u63d0\u9ad8 50%\uff0c\u6700\u591a 2 \u5c42\u300d\u21d2 `BATTLE_START` \u21d2 "
                "`MODIFY_ATTR{ATTACK, 50%, permanent, max_stacks: 2}` \u2713\u3002"
                "\u26a0 \u540c\u53e5\u7684\u53e6\u4e00\u534a\uff08**\u53d8\u8eab\u7ed3\u675f\u65f6** \u2713\uff09\u662f**\u53e6\u4e00\u6761\u89c4\u5219** \u2713 \u2014\u2014 "
                "\u26a0 \u5b9e\u6d4b\uff1a\u4e00\u6761\u89c4\u5219\u5199\u4e24\u4e2a\u4e8b\u4ef6\u7684\u62fc\u6cd5\uff08`on_any` \u2717\uff09**\u4e0d\u6210\u7acb** \u2717"
                "\uff08\u88c5\u8f7d\u5668\uff1a*\"Unknown trigger event 'null'\"* \u2713\uff09\uff0c\u800c\u4e14\u6761\u4ef6\u4e5f\u4e0d\u53ef\u80fd\u5728\u4e24\u4e2a\u4e8b\u4ef6\u4e0a\u540c\u65f6\u6210\u7acb \u2717\u3002",
    },
    {
        "on": "STATE_ENDED",
        "id": "trace_hero_true_colors_on_transformation_end",
        "when": ["self state_ended \u53d8\u8eab"],
        "do": [dict(EFFECT)],
        "source": SOURCE,
        "note": "\u300c**\u53d8\u8eab\u7ed3\u675f\u65f6**\u2026\u653b\u51fb\u529b\u63d0\u9ad8 50%\uff08\u6700\u591a 2 \u5c42\uff09\u300d\u21d2 `STATE_ENDED` \uff0b "
                "`self state_ended \u53d8\u8eab` \u21d2 \u540c\u4e00\u4e2a\u6548\u679c \u2713\uff08\u4e24\u6761\u89c4\u5219\u5171\u7528 `max_stacks: 2` \u2713\uff09\u3002",
    },
])

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1408 now has the clause as two rules: %d total" % len(doc["rules"]))
