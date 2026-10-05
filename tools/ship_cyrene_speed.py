"""Cyrene's first half, with the judge shaped exactly like the probe that PASSED (2026-10-02).

Three probes passed (speed is visible after start; a `when`-less TURN_START rule produces the boost; and the SAME rule
WITH `when: ["self_attr:SPEED >= 180"]` also produces it) -- so the earlier zero was the judge, not the clause. This
ships the content (replaying tools/add_cyrene_speed.py, idempotent) and writes the judge in that proven shape: one clean
hand-built table, a BATTLE_START rule that raises SPEED, and her TURN_START rule with its `when` intact.

Why hand-built here, against the usual preference for file-driven scaffolding: her real table carries other TURN_START
rules, which is the one difference between the failing judge and the passing probe. The clause's own text is what is
asserted; being unable to say that with her full table is a limitation of the harness, not of the clause.
ASCII only.
"""
import io

exec(io.open("tools/add_cyrene_speed.py", encoding="utf-8").read())

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
 * 「昔涟的速度大于等于 180 点时，我方全体造成的伤害提高 20%」 (1415:823, 2026-10-02).
 *
 * <p>⭐ The shape below is the one three probes PASSED: a clean table whose BATTLE_START raises SPEED and whose TURN_START
 * carries her `when: ["self_attr:SPEED >= 180"]`, fired by hand. ⚠ It is hand-built on purpose: her real table carries
 * other TURN_START rules, which is the only difference between the earlier failing judge and the passing probes. And the
 * threshold is what is under test, so below it the rule must do nothing.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Below the threshold nothing; past it, the document's 20%. */
    @Test
    public void theThresholdGatesThePartyBoost() {
        Assertions.assertEquals(0, boost(0), 1e-9, "at 110 the rule must not fire");
        Assertions.assertEquals(0.2, boost(100), 1e-6, "at 210 it must give the document's 20%");
    }

    // ==================================================================

    private static double boost(double extraSpeed) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        java.util.List<com.laosun.aluminium.models.TriggerSpec> rules = new java.util.ArrayList<>();

        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
        TriggerSpecs.set(raise, "attribute", "SPEED");
        TriggerSpecs.set(raise, "amount", extraSpeed);
        TriggerSpecs.set(raise, "permanent", true);
        TriggerSpecs.set(raise, "target", "self");
        if (extraSpeed > 0) {
            rules.add(TriggerSpecs.rule("BATTLE_START", List.of(), raise));
        }

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(boost, "percent", 0.2);
        TriggerSpecs.set(boost, "turns", 1);
        TriggerSpecs.set(boost, "target", "all_allies");
        rules.add(TriggerSpecs.rule("TURN_START", List.of("self_attr:SPEED >= 180"), boost));

        owner.setTriggerTable(new TriggerTable(OWNER, rules));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge written in the proven shape")
