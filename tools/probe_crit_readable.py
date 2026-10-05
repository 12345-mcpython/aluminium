"""Two probes, one run: is CRIT_ATTACK readable at all, and does a DERIVED share land on ATTACK? (2026-10-02)

Five single-variable runs on slot 23 narrowed it to the share spelling, but they also revealed something I had never checked: every reading so far watched `CRIT_ATTACK`,
and the only reading that ever moved was on `ATTACK`. So this run asks two independent questions at once, on two different allies:

  * probe A on 1412: `MODIFY_ATTR{CRIT_ATTACK, percent: 0.25}` -- a LITERAL share on the crit-damage attribute. If this does not move, `CRIT_ATTACK` is not something
    `getAttribute` reports (and slot 23's judge has been watching the wrong number all along);
  * probe B on 1412: `MODIFY_ATTR{ATTACK, percent_from_cast_param: 0}` -- a DERIVED share on the attribute that DID move. If this does not move while the literal one does,
    a derived share is what fails.
"""
import io
import json
import sys

CERYDRA = "src/main/resources/characters/1412.json"
doc = json.load(io.open(CERYDRA, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
for rid in ("probe_crit_literal", "probe_attack_derived"):
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)

rules.append({
    "id": "probe_crit_literal",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == 23"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.25, "permanent": True, "target": "self"}],
    "source": "DIAGNOSTIC: a LITERAL share on CRIT_ATTACK.",
    "note": "\u26a0 \u4e34\u65f6\u63a2\u9488\u3002",
})
rules.append({
    "id": "probe_attack_derived",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == 23"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent_from_cast_param": 0, "permanent": True, "target": "self"}],
    "source": "DIAGNOSTIC: a DERIVED share on ATTACK.",
    "note": "\u26a0 \u4e34\u65f6\u63a2\u9488\u3002",
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CERYDRA, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1412 carries the two probes (%d rules)" % len(rules))

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** DIAGNOSTIC (2026-10-02): two bits -- is CRIT_ATTACK readable, and does a derived share land on ATTACK? */
public class CritReadableProbeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int MONSTER = 1002011;

    @Test
    public void printBothBits() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: slot 23");

        double critBefore = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get();
        double atkBefore = cerydra.getAttribute(AttributeType.ATTACK).get();
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();
        System.out.println("[crit_probe] literal CRIT_ATTACK gain = "
                + (cerydra.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore)
                + " ; derived ATTACK gain = " + (cerydra.getAttribute(AttributeType.ATTACK).get() - atkBefore));

        Assertions.assertTrue(true, "diagnostic only");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/CritReadableProbeTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   probe written")
