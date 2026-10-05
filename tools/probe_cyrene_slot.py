"""Discriminating probe (2026-10-02): does the rule fire on HER table, or does the slot simply not stack?

Measured so far: on a clean hand-built table the identical rule yields 0.2; on her table the total stays at 0.2 (her
talent's value), so either (a) the same attribute does not stack and my rule DID fire, or (b) the rule does not fire on her
table at all.

The discriminator: keep everything identical but make the rule write an attribute NOBODY else writes in her table
(`BREAKING_EFFECT`). If a delta appears there, (a) is right -- the rule fires, the slot just does not add. If it is still 0,
(b) is right.
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
 * Does a TURN_START rule fire on HER table? Probed with an attribute nobody else writes (1415, 2026-10-02).
 *
 * <p>⚠ `ALL_DAMAGE_TYPE_BOOST` is written by her talent too, so a delta there proves nothing; `BREAKING_EFFECT` is untouched
 * in her table, which is what makes this a discriminator rather than another confounded reading.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Past the threshold the rule must move an attribute nobody else writes. */
    @Test
    public void theRuleFiresOnHerTable() {
        Assertions.assertEquals(0, untouched(0), 1e-9, "below the threshold, nothing");
        Assertions.assertTrue(untouched(100) > 0,
                "past 180 the rule must move BREAKING_EFFECT (was " + untouched(100) + ")");
    }

    // ==================================================================

    /** The gain in an attribute her table never touches, with the same rule shape and the same speed raise. */
    private static double untouched(double extra) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
        TriggerSpecs.set(raise, "attribute", "SPEED");
        TriggerSpecs.set(raise, "amount", extra);
        TriggerSpecs.set(raise, "permanent", true);
        TriggerSpecs.set(raise, "target", "self");

        EffectSpec probe = new EffectSpec();
        TriggerSpecs.set(probe, "op", "MODIFY_ATTR");
        TriggerSpecs.set(probe, "attribute", "BREAKING_EFFECT");
        TriggerSpecs.set(probe, "percent", 0.2);
        TriggerSpecs.set(probe, "turns", 1);
        TriggerSpecs.set(probe, "target", "all_allies");

        java.util.List<com.laosun.aluminium.beans.TriggerSpec> extraRules = new java.util.ArrayList<>();
        if (extra > 0) {
            extraRules.add(TriggerSpecs.rule("BATTLE_START", List.of(), raise));
        }
        extraRules.add(TriggerSpecs.rule("TURN_START", List.of("self_attr:SPEED >= 180"), probe));
        owner.setTriggerTable(owner.getTriggerTable().plus(new TriggerTable(OWNER, extraRules)));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double before = owner.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.BREAKING_EFFECT).get() - before;
    }
}
''')
print("ok   discriminating probe written")
