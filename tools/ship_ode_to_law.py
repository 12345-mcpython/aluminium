"""The fifth reader: 1415's memosprite skill 15 pays 刻律德菈 a charge when the coup ends (2026-10-02, objective ①-b).

The sentence, verbatim: 「整场生效，对刻律德菈施放后，持有【军功】的角色暴击伤害提高 30%，**奇袭结束后，使刻律德菈获得 1 点充能**。」

Measured before writing (every piece checked in the tree, not assumed):
  * the moment already exists: `INSERTED_CAST_END`, and 1412 already subscribes to it;
  * `coup_de_main` is `CAST_SETUP -> CAST_SKILL{skill: SKILL, target: "attacker"}`, so the commanded cast is AIMED at the unit
    that triggered it -- the 【爵位】 holder, which the sentence calls 刻律德菈. Item 66 made the event's target slot carry
    exactly that unit;
  * the resource is `充能`, declared in 1412's file. The loader validates declarations per file, so 1415's file declares the
    same id with the same cap -- the one declaration the effect needs is present in the file that states the rule.

The note records the one thing this reading assumes: the coup belongs to whoever holds 【爵位】, which is 刻律德菈 when the
sentence fires.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1415.json"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
if not isinstance(doc, dict):
    sys.exit("REFUSING: 1415.json is not an object")

declared = [entry.get("id") for entry in doc.get("resources", [])]
if "\u5145\u80fd" not in declared:
    doc.setdefault("resources", []).append({
        "id": "\u5145\u80fd",
        "max": 8,
        "source": "1412 \u523b\u5f8b\u5fb7\u83c8 \u6218\u6280\uff1a\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u83b7\u5f97\u3010\u519b\u529f\u3011\u5e76\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd\u3002\u5145\u80fd\u4e0a\u9650 8 \u70b9\u3002\u300d"
                  "\uff08\u672c\u6587\u4ef6\u58f0\u660e\u5b83\uff0c\u662f\u56e0\u4e3a**\u89c4\u5219\u5728\u672c\u6587\u4ef6**\u800c\u8d44\u6e90\u5c5e\u4e8e 1412 \u2713\uff1b\u4e0a\u9650\u4e0e 1412 \u6587\u4ef6\u4e00\u81f4 \u2713\uff09",
    })
    print("ok   declared 充能 in 1415's file (mirroring 1412's declaration)")

rules = doc["rules"]
if any(isinstance(rule, dict) and rule.get("id") == "memosprite_ode_to_law_pays_charge" for rule in rules):
    sys.exit("REFUSING: the rule is already there")
rules.append({
    "on": "INSERTED_CAST_END",
    "id": "memosprite_ode_to_law_pays_charge",
    "when": ["actor has_state \u7235\u4f4d"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "\u5145\u80fd", "amount": 1, "target": "target"}],
    "source": "1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 15 \u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7 / Ode to Law (141505, effect 10000023): "
              "\u300c\u6574\u573a\u751f\u6548\uff0c\u5bf9\u523b\u5f8b\u5fb7\u83c8\u65bd\u653e\u540e\uff0c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 30%\uff0c"
              "**\u5947\u88ad\u7ed3\u675f\u540e**\uff0c\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 **1** \u70b9\u5145\u80fd\u3002\u300d",
    "note": "\u300c**\u5947\u88ad\u7ed3\u675f\u540e\uff0c\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd**\u300d\u21d2 `INSERTED_CAST_END` \u21d2 `GAIN_RESOURCE{\u5145\u80fd, 1, target: target}` \u2713\u3002"
            "\u26a0 **\u4e3a\u4f55 `target` \u5c31\u662f\u523b\u5f8b\u5fb7\u83c8** \u2713\uff1a\u6e38\u620f\u91cc\u300c\u5947\u88ad\u300d\u7531\u3010\u7235\u4f4d\u3011\u6301\u6709\u8005\u65bd\u653e\u6218\u6280\u65f6\u89e6\u53d1 \u2713\uff0c"
            "\u800c `1412` \u7684 `coup_de_main` \u5199\u7684\u662f `CAST_SKILL{skill: SKILL, **target: \"attacker\"**}` \u2713 \u2014\u2014 \u5373\u88ab\u547d\u4ee4\u7684\u90a3\u6b21\u65bd\u653e**\u7784\u51c6\u7684\u5c31\u662f\u89e6\u53d1\u5b83\u7684\u90a3\u4f4d** \u2713\uff0c"
            "\u800c\u672c\u4ef6\uff08\u7b2c 66 \u4ef6\uff09\u6b63\u597d\u8ba9\u8be5\u65f6\u523b\u7684 `target` \u643a\u5e26\u5b83 \u2713\u2713\u3002\u26a0 \u6761\u4ef6 `actor has_state \u7235\u4f4d` \u2713 \u4fdd\u8bc1\u53ea\u5728\u5947\u88ad\u771f\u6b63\u53d1\u751f\u65f6\u7ed3\u7b97 \u2713\u3002"
            "\u26a0 \u767b\u8bb0\uff1a\u540c\u53e5\u7684**\u66b4\u51fb\u4f24\u5bb3 +30%**\uff08\u9488\u5bf9\u6301\u6709\u3010\u519b\u529f\u3011\u8005 \u2717\uff09\u4e0e\u300c\u5bf9\u523b\u5f8b\u5fb7\u83c8\u65bd\u653e\u540e**\u6574\u573a\u751f\u6548**\u300d\u7684\u6301\u7eed\u6027 \u2717 \u5c1a\u672a\u5199 \u2713\u3002",
})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added memosprite_ode_to_law_pays_charge to 1415.json")
