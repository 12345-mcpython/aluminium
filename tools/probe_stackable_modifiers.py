"""Verify the stacking path: two same-attribute modifiers WITH `max_stacks` must add (2026-10-02).

`BuffManager.addBuff` replaces a same-kind buff by design (two tests pin it), and the javadoc says the stacking question
is answered by a different predicate: `isStackable` / `stackGroupKey`. So the content-side fix for "two sources must both
count" is to make the modifier STACKABLE -- which for `MODIFY_ATTR` means stating `max_stacks` (1407's talent does).

The probe states `max_stacks` on both rules and asserts 0.4. If it reads 0.2, then `isStackable` is not what `max_stacks`
sets, and the next question is which field does.
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
 * Two +20% rules on one attribute, both STACKABLE (`max_stacks`), on a clean table (1415, 2026-10-02).
 *
 * <p>\u26a0 Measured: without `max_stacks` the second replaces the first (0.2) -- by design, per `BuffManager.addBuff`'s javadoc
 * ("Stacking is a different question", answered by `isStackable`). This asserts 0.4: a red saying "was 0.2" tells us
 * `max_stacks` is not the switch.
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
        TriggerSpecs.set(talent, "max_stacks", 3);
        TriggerSpecs.set(talent, "target", "all_allies");

        EffectSpec trace = new EffectSpec();
        TriggerSpecs.set(trace, "op", "MODIFY_ATTR");
        TriggerSpecs.set(trace, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(trace, "percent", 0.2);
        TriggerSpecs.set(trace, "turns", 3);
        TriggerSpecs.set(trace, "max_stacks", 3);
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
print("ok   stackable probe written")
