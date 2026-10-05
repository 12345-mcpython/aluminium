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
if "充能" not in declared:
    doc.setdefault("resources", []).append({
        "id": "充能",
        "max": 8,
        "source": "1412 刻律德菈 战技：「使指定我方单体角色获得【军功】并使刻律德菈获得 1 点充能。充能上限 8 点。」"
                  "（本文件声明它，是因为**规则在本文件**而资源属于 1412 ✓；上限与 1412 文件一致 ✓）",
    })
    print("ok   declared 充能 in 1415's file (mirroring 1412's declaration)")

rules = doc["rules"]
if any(isinstance(rule, dict) and rule.get("id") == "memosprite_ode_to_law_pays_charge" for rule in rules):
    sys.exit("REFUSING: the rule is already there")
rules.append({
    "on": "INSERTED_CAST_END",
    "id": "memosprite_ode_to_law_pays_charge",
    "when": ["actor has_state 爵位"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "充能", "amount": 1, "target": "target"}],
    "source": "1415 昔涟 忆灵技能 15 献予「律法」之诗 / Ode to Law (141505, effect 10000023): "
              "「整场生效，对刻律德菈施放后，持有【军功】的角色暴击伤害提高 30%，"
              "**奇袭结束后**，使刻律德菈获得 **1** 点充能。」",
    "note": "「**奇袭结束后，使刻律德菈获得 1 点充能**」⇒ `INSERTED_CAST_END` ⇒ `GAIN_RESOURCE{充能, 1, target: target}` ✓。"
            "⚠ **为何 `target` 就是刻律德菈** ✓：游戏里「奇袭」由【爵位】持有者施放战技时触发 ✓，"
            "而 `1412` 的 `coup_de_main` 写的是 `CAST_SKILL{skill: SKILL, **target: \"attacker\"**}` ✓ —— 即被命令的那次施放**瞄准的就是触发它的那位** ✓，"
            "而本件（第 66 件）正好让该时刻的 `target` 携带它 ✓✓。⚠ 条件 `actor has_state 爵位` ✓ 保证只在奇袭真正发生时结算 ✓。"
            "⚠ 登记：同句的**暴击伤害 +30%**（针对持有【军功】者 ✗）与「对刻律德菈施放后**整场生效**」的持续性 ✗ 尚未写 ✓。",
})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added memosprite_ode_to_law_pays_charge to 1415.json")
