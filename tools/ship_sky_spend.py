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
MARK = "\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7"

doc = json.load(io.open(HYACINE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])

for slot, label in ((2, "\u6218\u6280"), (3, "\u7ec8\u7ed3\u6280")):
    rid = "casting_%s_spends_one_layer_of_the_sky_ode" % ("a_skill" if slot == 2 else "the_ultimate")
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)
    rules.append({
        "id": rid,
        "on": "SKILL_CAST",
        "when": ["self has_state " + MARK, "from_skill_id == " + str(slot)],
        "do": [{"op": "REMOVE_STACK", "buff": MARK, "amount": 1, "target": "self"}],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff0cSkillID 1141519\uff09\uff1a"
                   "\u300c**\u98ce\u5807\u65bd\u653e" + label + "\u540e\uff0c\u6d88\u80171\u5c42\u3010" + MARK + "\u3011**\u3002\u300d"),
        "note": ("\u2b50 \u300c\u6218\u6280/\u7ec8\u7ed3\u6280\u300d\u2192 **\u4e24\u6761\u89c4\u5219**\uff08\u69fd\u4f4d 2 \u4e0e 3\uff09\uff1a`from_skill_id` \u6bd4\u7684\u662f "
                 "`ctx.skillId()`\uff0c\u800c\u5b83\u662f**\u5355\u503c**\uff08**\u5b9e\u6d4b**\uff1a\u51fa\u8d27\u8bfb\u8005 `1111.json` \u5199 `\"from_skill_id == 8\"`\uff09"
                 "\u2014\u2014 \u4e0d\u53d1\u660e\u4e00\u4e2a\u201c\u4efb\u4e00\u201d\u62fc\u6cd5\u3002"
                 "\u2b50 \u72b6\u6001\u5728**\u5979\u81ea\u5df1**\u8eab\u4e0a\uff08\u4e0a\u4e00\u8f6e\u51fa\u8d27\uff09\uff0c\u6240\u4ee5 `target: \"self\"`\u3002"),
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
    effects["11415"][str(SLOT)]["note"] = ("\u2b50 2026-10-02\uff1a**\u56db\u53e5\u91cc\u5df2\u6709\u4e09\u53e5**\u6210\u53e5\uff08\u5c42\u6570\u3001\u80fd\u91cf\u3001\u6d88\u8017\uff09\uff1b"
                                           "\u5269\u4e0b\u4e00\u53e5\u662f\u201c\u6cbb\u7597\u6570\u503c\u8ba1\u5165\u5c0f\u4f0a\u5361\u2026\u201d\u3002")
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
 * 1415's memosprite skill 19, THIRD sentence: \u300c\u98ce\u5807\u65bd\u653e\u6218\u6280/\u7ec8\u7ed3\u6280\u540e\uff0c\u6d88\u80171\u5c42\u3010\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 The reading discriminates the SLOT gate three ways in one battle: casting her SKILL (slot 2) spends a layer, casting her ULTIMATE (slot 3) spends another, and casting
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

        Assertions.assertEquals(3, afterBasic, "\\u300c\\u6218\\u6280/\u7ec8\\u7ed3\\u6280\\u300d-- a BASIC is neither, so it must not spend");
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
