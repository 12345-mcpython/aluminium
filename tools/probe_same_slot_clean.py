"""Clean-table probe: do TWO rules writing the same attribute add up? (2026-10-02)

Measured so far: `DoubleValue.compute()` SUMS same-attribute add-percent modifiers (`base * (1 + Σ)`), so two +20% rules
should read 0.4. Yet on her real table the reading stayed 0.2 while the rule demonstrably fired (proved by aiming the same
rule at an untouched attribute). So the question is now narrow: on a CLEAN table, with the talent's shape and the trace's
shape both present, what is the total?

0.4 -> her table has something else interfering (some rule expires or overrides); 0.2 -> the two shapes conflict even when
nothing else is present, and the mechanism is in `MODIFY_ATTR`'s own bookkeeping.
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
 * Two rules, one attribute, a clean table (1415, 2026-10-02): the talent's shape plus the trace's shape.
 *
 * <p>`DoubleValue.compute()` sums add-percent modifiers, so this asserts 0.4 -- a red saying "was 0.2" is the answer we
 * need, not a failure of the work.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Both +20% rules present: do they add? */
    @Test
    public void twoRulesOnOneAttributeAdd() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec talent = new EffectSpec();
        TriggerSpecs.set(talent, "op", "MODIFY_ATTR");
        TriggerSpecs.set(talent, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(talent, "percent", 0.2);
        TriggerSpecs.set(talent, "turns", 3);
        TriggerSpecs.set(talent, "target", "all_allies");

        EffectSpec trace = new EffectSpec();
        TriggerSpecs.set(trace, "op", "MODIFY_ATTR");
        TriggerSpecs.set(trace, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(trace, "percent", 0.2);
        TriggerSpecs.set(trace, "turns", 3);
        TriggerSpecs.set(trace, "target", "all_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), talent),
                TriggerSpecs.rule("TURN_START", List.of("self_attr:SPEED >= 180"), trace))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(0.2, owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-6,
                "the talent's shape alone");
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        Assertions.assertEquals(0.4, owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-6,
                "and the same attribute must SUM the second one, per compute()");
    }
}
''')
print("ok   clean-table probe written")
