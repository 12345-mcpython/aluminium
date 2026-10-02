"""Finish the stacking probe with the JAVA field name (2026-10-02).

Last attempt failed before running: `TriggerSpecs.set` takes Java field names, and `max_stacks` is the JSON name, so the
call threw. This writes the same probe with `maxStacks`, and asks the one question: with both rules declared stackable,
do the two +20% modifiers sum to 0.4, or is 0.2 all the engine gives?
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
 * Two +20% rules on one attribute, both declared stackable (1415, 2026-10-02).
 *
 * <p>\u26a0 `maxStacks` here is the JAVA field; the JSON content writes `max_stacks`. \u26a0 Measured earlier: without it the second
 * modifier replaces the first (0.2), which `BuffManager.addBuff`'s javadoc calls deliberate. This asserts 0.4 -- a red
 * saying "was 0.2" would mean `maxStacks` is not the switch that makes same-attribute modifiers accumulate.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 Two stackable +20% modifiers on one attribute sum to 0.4. */
    @Test
    public void stackableModifiersAdd() {
        Assertions.assertEquals(0.4, total(), 1e-6,
                "two stackable +20% modifiers on one attribute must sum to 0.4");
    }

    // ==================================================================

    private static double total() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec talent = new EffectSpec();
        TriggerSpecs.set(talent, "op", "MODIFY_ATTR");
        TriggerSpecs.set(talent, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(talent, "percent", 0.2);
        TriggerSpecs.set(talent, "turns", 3);
        TriggerSpecs.set(talent, "maxStacks", 3);
        TriggerSpecs.set(talent, "target", "all_allies");

        EffectSpec trace = new EffectSpec();
        TriggerSpecs.set(trace, "op", "MODIFY_ATTR");
        TriggerSpecs.set(trace, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(trace, "percent", 0.2);
        TriggerSpecs.set(trace, "turns", 3);
        TriggerSpecs.set(trace, "maxStacks", 3);
        TriggerSpecs.set(trace, "target", "all_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), talent),
                TriggerSpecs.rule("TURN_START", List.of(), trace))));

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
print("ok   stackable probe written with the Java field name")
