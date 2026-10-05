"""1141519's third sentence: casting a skill spends one layer of the sky ode (2026-10-02, round 64).

Verbatim (hash 3195157591843010775, and again on its own in hash 5943790019529126162): 「…**风堇施放战技/终结技后，消耗1层【献予「天空」之诗】。**」

Why it could not be written until now: the state itself is new (shipped last round), and the sentence CONSUMES it.

Measured shapes:
  * `REMOVE_STACK` reader 1111.json: `{"op": "REMOVE_STACK", "buff": 斗志, "amount": 2, "target": "self"}`;
  * `from_skill_id` compares against `ctx.skillId()`, which is the SLOT (reader 1111.json: `"from_skill_id == 8"`), and it is a single value --
    so 「战技/终结技」 is TWO rules, one per slot (skill = 2, ultimate = 3), rather than one rule with an invented "either" spelling.

The rule lives on 风堇's own file: she is the one casting, and the layers are hers.
"""
import io
import json
import sys

HYACINE = "src/main/resources/characters/1409.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 19
MARK = "献予「天空」之诗"

doc = json.load(io.open(HYACINE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])

for slot, label in ((2, "战技"), (3, "终结技")):
    rid = "casting_%s_spends_one_layer_of_the_sky_ode" % ("a_skill" if slot == 2 else "the_ultimate")
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)
    rules.append({
        "id": rid,
        "on": "SKILL_CAST",
        "when": ["self has_state " + MARK, "from_skill_id == " + str(slot)],
        "do": [{"op": "REMOVE_STACK", "buff": MARK, "amount": 1, "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 19 「献予「天空」之诗」（数据槽位 19，SkillID 1141519）："
                   "「**风堇施放" + label + "后，消耗1层【" + MARK + "】**。」"),
        "note": ("⭐ 「战技/终结技」→ **两条规则**（槽位 2 与 3）：`from_skill_id` 比的是 "
                 "`ctx.skillId()`，而它是**单值**（**实测**：出货读者 `1111.json` 写 `\"from_skill_id == 8\"`）"
                 "—— 不发明一个“任一”拼法。"
                 "⭐ 状态在**她自己**身上（上一轮出货），所以 `target: \"self\"`。"),
    })
    print("ok   rule for %s (slot %d)" % (label, slot))

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(HYACINE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1409 now carries %d rules" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    effects["11415"][str(SLOT)]["note"] = ("⭐ 2026-10-02：**四句里已有三句**成句（层数、能量、消耗）；"
                                           "剩下一句是“治疗数值计入小伊卡…”。")
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json 11415/%d note updated" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, THIRD sentence: 「风堇施放战技/终结技后，消耗1层【献予「天空」之诗】」 (2026-10-02).
 *
 * <p>⭐ The reading discriminates the SLOT gate three ways in one battle: casting her SKILL (slot 2) spends a layer, casting her ULTIMATE (slot 3) spends another, and casting
 * her BASIC (slot 1) spends none. A rule that fired on any cast -- or on a wrong slot -- cannot pass all three.
 */
public class SkyOdeSpendTest {
    private static final int LEVEL = 80;
    private static final int HYACINE = 1409;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u732e\\u4e88\\u300c\\u5929\\u7a7a\\u300d\\u4e4b\\u8bd7";

    @Test
    public void aSkillAndAnUltimateSpendOneLayerEachAndABasicSpendsNone() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle battle = new Battle(List.of(hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        hyacine = battle.characters.getFirst();

        // three layers to start with, put there by a rule of her own table's -- no content is replaced
        EffectSpec layers = new EffectSpec();
        TriggerSpecs.set(layers, "op", "ADD_STACK");
        TriggerSpecs.set(layers, "buff", MARK);
        TriggerSpecs.set(layers, "amount", 3.0);
        TriggerSpecs.set(layers, "maxStacks", 99999);
        TriggerSpecs.set(layers, "permanent", Boolean.TRUE);
        TriggerSpecs.set(layers, "target", "self");
        hyacine.setTriggerTable(new TriggerTable(HYACINE, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), layers))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);
        Assertions.assertEquals(3, hyacine.getBuffManager().stacksOf(MARK), "precondition: three layers");

        int afterBasic = cast(battle, hyacine, 1);
        int afterSkill = cast(battle, hyacine, 2);
        int afterUlt = cast(battle, hyacine, 3);
        System.out.println("[sky_spend] layers after basic = " + afterBasic + " ; after skill = " + afterSkill
                + " ; after ultimate = " + afterUlt);

        Assertions.assertEquals(3, afterBasic, "\\u300c\\u6218\\u6280/终\\u7ed3\\u6280\\u300d-- a BASIC is neither, so it must not spend");
        Assertions.assertEquals(2, afterSkill, "\\u6218\\u6280 (slot 2) spends one");
        Assertions.assertEquals(1, afterUlt, "\\u7ec8\\u7ed3\\u6280 (slot 3) spends another");
    }

    private static int cast(Battle battle, Character who, int slot) {
        var skill = who.skillAt(slot);
        Assertions.assertNotNull(skill, "precondition: slot " + slot + " exists");
        SkillExecutor.execute(battle, skill, who, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return who.getBuffManager().stacksOf(MARK);
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkyOdeSpendTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
