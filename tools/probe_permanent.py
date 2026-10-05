"""Field diff found the suspect: her talent is `permanent: true` with no `turns` (2026-10-02).

Dumped from her file:
  {"op":"MODIFY_ATTR","attribute":"ALL_DAMAGE_TYPE_BOOST","percent":0.2,"permanent":true,"target":"all_allies"}
while the clean-table probe that DID reach 0.4 used `turns: 3` and no `permanent`. That is the one field that differs, so
this probe pairs a permanent modifier with a timed one, both stackable, and asks whether the permanent one is what blocks
the sum.

0.4 -> the blocker is something else; 0.2 -> a permanent modifier is not stackable, and no amount of `max_stacks` on the
other rule will make the pair add.
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/CyreneSpeedThresholdTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A PERMANENT stackable modifier plus a TIMED stackable one, same attribute (1415, 2026-10-02).
 *
 * <p>The only difference from the probe that reached 0.4: rule A is `permanent: true` and has no `turns`, exactly as her
 * talent is written.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Permanent plus timed, both stackable: do they add? */
    @Test
    public void permanentAndTimedAdd() {
        Assertions.assertEquals(0.4, total(), 1e-6,
                "a permanent and a timed stackable modifier on one attribute must sum to 0.4");
    }

    // ==================================================================

    private static double total() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec permanentOne = new EffectSpec();          // exactly her talent's shape
        TriggerSpecs.set(permanentOne, "op", "MODIFY_ATTR");
        TriggerSpecs.set(permanentOne, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(permanentOne, "percent", 0.2);
        TriggerSpecs.set(permanentOne, "permanent", true);
        TriggerSpecs.set(permanentOne, "maxStacks", 1);
        TriggerSpecs.set(permanentOne, "target", "all_allies");

        EffectSpec timedOne = new EffectSpec();
        TriggerSpecs.set(timedOne, "op", "MODIFY_ATTR");
        TriggerSpecs.set(timedOne, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(timedOne, "percent", 0.2);
        TriggerSpecs.set(timedOne, "turns", 3);
        TriggerSpecs.set(timedOne, "maxStacks", 1);
        TriggerSpecs.set(timedOne, "target", "all_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), permanentOne),
                TriggerSpecs.rule("TURN_START", List.of(), timedOne))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
''')
print("ok   permanent-vs-timed probe written")
