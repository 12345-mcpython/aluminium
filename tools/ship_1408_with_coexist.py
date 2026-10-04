"""Make the ask protect the buff that carries it, in BOTH directions, then ship 1408's three clauses.

First attempt only skipped the eviction when the NEW buff asked -- but 1408's trace is added at BATTLE_START and the
transformation's +80% arrives later, so the plain newcomer would still evict the protected one. The ask belongs to the buff
that carries it: `addBuff` must skip removing an existing buff that asks.

Then the three sentences ship:
  * talent 「变身结束时，使我方全体速度提高 15%，持续 1 回合」;
  * trace 行向世界终点 「变身结束时，获得 3 点【火种】」;
  * trace 照见英雄本色 「进入战斗或变身结束时，攻击力提高 50%，最多 2 层」 -- as TWO rules (measured: `on_any` is not a JSON key) and
    with `coexist` on it, because her transformation grants the same attribute.
"""
import io
import json
import sys

MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
text = io.open(MANAGER, encoding="utf-8").read()
OLD = """        for (int i = buffs.size() - 1; i >= 0 && !buff.keepsSiblings(); i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff)) {
                removeBuff(existed);
            }
        }"""
NEW = """        // \u2b50 An effect may ask NOT to evict (2026-10-02; reader: 1408's trace, whose +50% must live beside her transformation's
        // +80%). The ask protects the buff that CARRIES it, in both directions: the newcomer skips its own sweep, and an
        // already-attached buff that asked is not swept by a later plain one. Measured, eviction is load-bearing for three
        // cones and two other kits, so this is opt-in and the default is untouched.
        for (int i = buffs.size() - 1; i >= 0 && !buff.keepsSiblings(); i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff) && !existed.keepsSiblings()) {
                removeBuff(existed);
            }
        }"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(MANAGER, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the ask protects its carrier in both directions")

CHAR = "src/main/resources/characters/1408.json"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
declared = [entry.get("id") for entry in (doc.get("resources") or [])]
if "\u706b\u79cd" not in declared:
    sys.exit("REFUSING: 火种 is not declared")

SOURCE_TRACE1 = "1408 \u767d\u5384 \u884c\u8ff9 \u884c\u5411\u4e16\u754c\u7ec8\u70b9 (1408101)"
SOURCE_TRACE3 = ("1408 \u767d\u5384 \u884c\u8ff9 \u7167\u89c1\u82f1\u96c4\u672c\u8272 (1408103): "
                 "\u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u653b\u51fb\u529b\u63d0\u9ad8 **50%**\u3002\u8be5\u6548\u679c\u6700\u591a\u53e0\u52a0 **2** \u5c42\u300d")
ATTACK = {"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
          "max_stacks": 2, "target": "self", "coexist": True}

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
        "source": SOURCE_TRACE1 + "\uff1a\u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u3002\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u83b7\u5f97 **3** \u70b9\u3010\u706b\u79cd\u3011\u300d",
        "note": "\u300c\u53d8\u8eab\u7ed3\u675f\u65f6\u83b7\u5f97 **3** \u70b9\u3010\u706b\u79cd\u3011\u300d\u21d2 `STATE_ENDED` \u21d2 `GAIN_RESOURCE{\u706b\u79cd, 3}` \u2713\u3002",
    },
    {
        "on": "BATTLE_START",
        "id": "trace_hero_true_colors_on_battle_start",
        "do": [dict(ATTACK)],
        "source": SOURCE_TRACE3,
        "note": "\u26a0 \u4e24\u4e2a\u4e8b\u4ef6\u5199\u6210**\u4e24\u6761\u89c4\u5219** \u2713\uff08\u5b9e\u6d4b `on_any` **\u4e0d\u662f** JSON \u952e \u2717\uff1a\u88c5\u8f7d\u5668\u62a5 *\"Unknown trigger event 'null'\"* \u2713\uff09\u3002"
                "\u2b50 **`coexist: true`** \u2713\uff1a\u5979\u7684\u53d8\u8eab\u540c\u6837\u7ed9 `ATTACK` \u52a0\u6210 \u2717\uff0c\u800c\u8bed\u6599\u8bf4\u4e24\u8005**\u540c\u65f6\u751f\u6548** \u2713"
                "\uff08\u5408\u8ba1 +130% \u2713\uff09\uff1b\u26a0 \u201c\u4e0d\u540c\u89c4\u5219\u4e00\u5f8b\u5171\u5b58\u201d\u5df2\u5b9e\u6d4b**\u7834\u574f 7 \u4f8b** \u2717 \u21d2 \u6324\u51fa\u4ecd\u662f\u9ed8\u8ba4 \u2713\u3002",
    },
    {
        "on": "STATE_ENDED",
        "id": "trace_hero_true_colors_on_transformation_end",
        "when": ["self state_ended \u53d8\u8eab"],
        "do": [dict(ATTACK)],
        "source": SOURCE_TRACE3,
        "note": "\u540c\u4e0a\u7684\u53e6\u4e00\u534a\uff1a\u300c**\u53d8\u8eab\u7ed3\u675f\u65f6**\u2026\u653b\u51fb\u529b +50%\uff0c\u6700\u591a 2 \u5c42\u300d\u21d2 `STATE_ENDED` \uff0b `self state_ended \u53d8\u8eab` \u2713\uff08\u4e24\u6761\u5171\u7528 `max_stacks: 2` \u2713 \u4e0e `coexist` \u2713\uff09\u3002",
    },
]
for rule in ADDITIONS:
    if rule["id"] in [entry.get("id") for entry in rules if isinstance(entry, dict)]:
        sys.exit("REFUSING: %s is already there" % rule["id"])
    rules.append(rule)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1408's three clauses are in (%d rules total)" % len(rules))
