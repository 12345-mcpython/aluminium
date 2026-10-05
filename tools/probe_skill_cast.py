"""The separating probe for slot 03's fourth sentence (2026-10-02, final round).

Two rules on the memosprite's own file, SAME gate, DIFFERENT attribute so neither replaces the other:

  * probe A: `on: SKILL_CAST`   + `when: [actor is_summon]` -> MODIFY_ATTR{ATTACK, 0.25, self}
  * probe B: `on: CAST_SETUP`   + `when: [actor is_summon]` -> MODIFY_ATTR{DEFENCE, 0.5, self}

Round 52 already proved a memosprite's cast announces CAST_SETUP. So:
  * B rises and A rises -> `SKILL_CAST` fires too, and the shortening rule simply did not shorten (the earlier reading's fault);
  * B rises and A does not -> `SKILL_CAST` is NOT fired for a memosprite's cast, and the rule needs a different event.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"
doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
for rid in ("probe_skill_cast_fires", "probe_cast_setup_again"):
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)

rules.append({
    "id": "probe_skill_cast_fires",
    "on": "SKILL_CAST",
    "when": ["actor is_summon"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.25, "permanent": True, "target": "self"}],
    "source": "DIAGNOSTIC: does a memosprite's cast fire SKILL_CAST?",
    "note": "⚠ 临时探针。",
})
rules.append({
    "id": "probe_cast_setup_again",
    "on": "CAST_SETUP",
    "when": ["actor is_summon"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "DEFENCE", "percent": 0.5, "permanent": True, "target": "self"}],
    "source": "DIAGNOSTIC: the control -- round 52 already proved this one fires.",
    "note": "⚠ 临时探针。",
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 carries two probes (%d rules)" % len(rules))

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

/** DIAGNOSTIC (2026-10-02): does a memosprite's cast fire SKILL_CAST, with CAST_SETUP as the control? */
public class SkillCastProbeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void printBothBits() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        double atkBefore = cyrene.getAttribute(AttributeType.ATTACK).get();
        double defBefore = cyrene.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        System.out.println("[cast_probe] its cast raised the master's ATTACK by "
                + (cyrene.getAttribute(AttributeType.ATTACK).get() - atkBefore)
                + " (SKILL_CAST) and her DEFENCE by "
                + (cyrene.getAttribute(AttributeType.DEFENCE).get() - defBefore) + " (CAST_SETUP)");
        Assertions.assertTrue(true, "diagnostic only");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkillCastProbeTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   probe written")
