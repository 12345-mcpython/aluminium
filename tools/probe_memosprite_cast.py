"""TWO-bit diagnostic for slot 23 (2026-10-02): does 1415.json get loaded, and does a memosprite cast announce CAST_SETUP?

The shipped ode (1403's `…_makes_his_damage_ignore_defence`) has the same `on: CAST_SETUP` + `when: [target == self, actor is_summon, from_skill_id == 15]` and its judge casts
with the same `SkillExecutor.execute(battle, ode, demiurge, List.of(tribbie))`. So the gate shape and the call are not the difference -- and three single-variable runs on
slot 23 already ruled out the filter, `target == self`, and the attribute. This puts TWO rules on the memosprite's own file:

  * `TURN_START` -> raise 昔涟's ATTACK (readable). If this fires, the file IS loaded and its rules run;
  * `CAST_SETUP` + `actor is_summon` -> raise 昔涟's ATTACK too. If the first fires and this one does not, then a memosprite's cast never announces CAST_SETUP.
"""
import io
import json
import sys

MEMOSPRITE = "src/main/resources/characters/1415.json"

doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
for rid in ("probe_file_is_loaded", "probe_cast_setup_fires"):
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)

rules.append({
    "id": "probe_file_is_loaded",
    "on": "TURN_START",
    "when": ["actor is_summon"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.5, "permanent": True,
            "target": "owner"}],
    "source": "DIAGNOSTIC 2026-10-02: if this fires, characters/1415.json is loaded and its rules run.",
    "note": "⚠ 临时探针，实验后必须回滚。",
})
rules.append({
    "id": "probe_cast_setup_fires",
    "on": "CAST_SETUP",
    "when": ["actor is_summon"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.25, "permanent": True,
            "target": "owner"}],
    "source": "DIAGNOSTIC 2026-10-02: if a memosprite's cast announces CAST_SETUP, this fires.",
    "note": "⚠ 临时探针，实验后必须回滚。",
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

/** DIAGNOSTIC (2026-10-02): two bits -- is 1415.json loaded, and does a memosprite cast announce CAST_SETUP? */
public class MemospriteCastProbeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRIBBIE = 1403;
    private static final int MONSTER = 1002011;

    @Test
    public void printTheTwoBits() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, tribbie),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        double afterSummon = cyrene.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.TURN_START,
                demiurge, battle.enemies.getFirst(), 1, 0);
        battle.processRequests();
        double afterTurnStart = cyrene.getAttribute(AttributeType.ATTACK).get();

        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        SkillExecutor.execute(battle, ode, demiurge, List.of(battle.characters.get(1)));
        battle.processRequests();
        double afterCast = cyrene.getAttribute(AttributeType.ATTACK).get();

        System.out.println("[probe] ATTACK after the summon = " + afterSummon
                + " ; after firing TURN_START with the memosprite as actor = " + afterTurnStart
                + " ; after the memosprite cast slot 23 = " + afterCast);
        Assertions.assertTrue(true, "diagnostic only");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/MemospriteCastProbeTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   probe written")
