"""Two-question probe for Cyrene's threshold rule (2026-10-02), after candidate (1) was eliminated.

(1) "the appended speed rule never applied" is already ruled OUT by a passing probe.
So the failure at 200 speed is either (2) the hand-fired TURN_START not reaching her rule, or (3) the `when` condition
reading something other than the raised speed at that moment. This probe asks both, in order, so the failing assertion
names which:

  A. with +100 appended, her speed AFTER battle start must be >= 180   (if not, the raise is not visible post-start)
  B. the SAME rule, hand-built WITHOUT the `when`, must produce the boost on a fired TURN_START
     -- if B lands, the condition is what stops it (3); if B is also 0, the event/context is (2).
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/CyreneProbeTest.java",
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

public class CyreneProbeTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** A: after start, with +100 appended, is she past the threshold? */
    @Test
    public void a_raisedSpeedIsVisibleAfterStart() {
        Character owner = raised();
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double speed = owner.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(speed >= 180, "raised speed after start must be >= 180 (was " + speed + ")");
    }

    /** B: the same rule WITHOUT `when`, hand-built -- does a fired TURN_START produce the boost? */
    @Test
    public void b_theSameRuleWithoutWhenDoesFire() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
        TriggerSpecs.set(raise, "attribute", "SPEED");
        TriggerSpecs.set(raise, "amount", 100.0);
        TriggerSpecs.set(raise, "permanent", true);
        TriggerSpecs.set(raise, "target", "self");

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(boost, "percent", 0.2);
        TriggerSpecs.set(boost, "turns", 1);
        TriggerSpecs.set(boost, "target", "all_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), raise),
                TriggerSpecs.rule("TURN_START", List.of("self_attr:SPEED >= 180"), boost))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        double after = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Assertions.assertEquals(0.2, after - before, 1e-6,
                "a `when`-less TURN_START rule must produce 0.2 (" + before + " -> " + after + ")");
    }

    // ==================================================================

    private static Character raised() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
        TriggerSpecs.set(raise, "attribute", "SPEED");
        TriggerSpecs.set(raise, "amount", 100.0);
        TriggerSpecs.set(raise, "permanent", true);
        TriggerSpecs.set(raise, "target", "self");
        owner.setTriggerTable(owner.getTriggerTable()
                .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)))));
        return owner;
    }
}
''')
print("ok   probe written (A then B)")
