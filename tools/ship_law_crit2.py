"""Slot 23's real rule, written the way the probe proved works, plus its two-sided judge (2026-10-02).

The probe measured, on THIS table with THIS gate: `getSkillSlot() = 23`, `getCid() = 11415`, and the aimed ally's ATTACK rose by 155.232. So the gate is fine and the
sentence can be written. The only difference from the probe is what the sentence actually says:
  * the share is #1 of the CAST skill (it runs 0.15 -> 0.42), not a literal;
  * the recipients are 「持有【军功】的角色」 -- `all_allies` filtered by `target_when`.

The judge prints the gain for a marked ally AND an unmarked one, so a filter that swallowed both (or a rule that boosted everybody) is visible in one run.
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 23
MARK = "\u519b\u529f"

doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
# drop the probe, add the real thing
rules = [r for r in rules if r.get("id") != "probe_law_gate"]
RULE_ID = "memosprite_ode_of_law_raises_the_meritorious"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "CRIT_ATTACK",
        "percent_from_cast_param": 0,
        "permanent": True,
        "target": "all_allies",
        "target_when": ["target has_state " + MARK],
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 17 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 23\uff0cSkillID 1141523\uff09\uff1a"
               "\u300c\u6574\u573a\u751f\u6548\uff0c\u5bf9\u523b\u5f8b\u5fb7\u83c8\u65bd\u653e\u540e\uff0c**\u6301\u6709\u3010" + MARK + "\u3011\u7684\u89d2\u8272\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 #1%**\u3002\u300d"),
    "note": ("\u2b50 \u5f62\u72b6\u7ecf**\u63a2\u9488\u5b9e\u9a8c\u9a8c\u8bc1**\uff08\u540c\u4e00\u5f20\u8868\u3001\u540c\u4e00\u9053\u95e8\uff1a`getSkillSlot() = 23`\u3001\u76ee\u6807\u53d7\u76ca +155.232\uff09\u3002"
             "\u2b50 \u4e0e\u63a2\u9488\u7684\u5dee\u522b\u53ea\u6709\u4e24\u5904\uff1a\u5360\u6bd4\u53d6\u81ea**\u65bd\u653e\u6280\u80fd\u7684\u7b2c 0 \u53c2\u6570**\uff08\u968f\u7b49\u7ea7\u53d8\uff09\uff0c"
             "\u53d7\u76ca\u8005\u662f**\u6301\u6709\u3010" + MARK + "\u3011\u7684\u4eba**\uff08`all_allies` + `target_when`\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 carries the real rule (%d rules)" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 17 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 23\uff09\uff1a\u5de5\u4f5c\u5728\u89c4\u5219\u4fa7\u3002",
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1415's memosprite skill 23 \u300c\u732e\u4e88\u300c\u5f8b\u6cd5\u300d\u4e4b\u8bd7\u300d: \u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 #1%\u300d (2026-10-02).
 *
 * <p>\u2b50 The reading is TWO-SIDED because the sentence names a SUBSET: the ally carrying the mark gains it, the ally without it does not. A rule that boosted
 * everybody would pass the first half and be wrong about the sentence.
 */
public class LawOdeCritDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int PLAIN = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u519b\\u529f";

    @Test
    public void onlyTheMarkedAllyGainsTheCritDamage() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character plain = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra, plain),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);
        plain = battle.characters.get(2);

        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", MARK);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        cerydra.setTriggerTable(new TriggerTable(CERYDRA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), mark))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);
        Assertions.assertTrue(cerydra.getBuffManager().hasState(MARK), "precondition: the mark landed on her");
        Assertions.assertFalse(plain.getBuffManager().hasState(MARK), "precondition: the other one is unmarked");

        double markedBefore = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get();
        double plainBefore = plain.getAttribute(AttributeType.CRIT_ATTACK).get();

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        var row = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();

        double markedGain = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get() - markedBefore;
        double plainGain = plain.getAttribute(AttributeType.CRIT_ATTACK).get() - plainBefore;
        System.out.println("[law] marked CRIT_ATTACK gain = " + markedGain + " (=" + row.get(0) + ")"
                + " ; unmarked gain = " + plainGain);

        Assertions.assertEquals(row.get(0), markedGain, Math.abs(row.get(0)) * 1e-6,
                "\\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u66b4\\u51fb\\u4f24\\u5bb3\\u63d0\\u9ad8 #1%\\u300d-- and #1 runs with level");
        Assertions.assertEquals(0.0, plainGain, 1e-9,
                "and an ally WITHOUT it gains nothing -- \\u300c\\u6301\\u6709\\u3010\\u519b\\u529f\\u3011\\u7684\\u89d2\\u8272\\u300d names a subset");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/LawOdeCritDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
