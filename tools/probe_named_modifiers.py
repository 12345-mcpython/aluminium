"""Verify the fix: two same-attribute modifiers with DISTINCT buff names must add (2026-10-02).

Mechanism found in `modifyAttr`: an attribute modifier with a duration is a BUFF, and an UNNAMED one shares its identity
with any other unnamed modifier for the same attribute, so the second replaces the first (measured: 0.2, never 0.4). The
code's own comment says naming is what lets the state loop address a modifier, and content already names them elsewhere
(1306's `"buff": "\u53d9\u8ff0\u6027\u8be1\u8ba1"`). So this probe gives each rule its own name and asserts 0.4.

One number changes between the two readings: the buff names. That is the mutation this capability has to survive.
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
 * Two +20% rules on one attribute, each with its OWN buff name (1415, 2026-10-02).
 *
 * <p>\u26a0 Measured before this: with both unnamed the total is 0.2 -- an unnamed modifier shares its identity with any other
 * unnamed modifier on the same attribute, so the second replaces the first. Naming them is what makes them two.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** \u2b50 Named modifiers add; the names are the only difference from the failing reading. */
    @Test
    public void namedModifiersAdd() {
        Assertions.assertEquals(0.4, total(true), 1e-6,
                "two named +20% modifiers on one attribute must sum to 0.4");
    }

    /** \u26a0 And without names they do not -- the behaviour this capability exists to avoid. */
    @Test
    public void unnamedModifiersReplace() {
        Assertions.assertEquals(0.2, total(false), 1e-6,
                "unnamed ones share an identity, so the second replaces the first");
    }

    // ==================================================================

    private static double total(boolean named) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec talent = new EffectSpec();
        TriggerSpecs.set(talent, "op", "MODIFY_ATTR");
        TriggerSpecs.set(talent, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(talent, "percent", 0.2);
        TriggerSpecs.set(talent, "turns", 3);
        TriggerSpecs.set(talent, "target", "all_allies");
        if (named) {
            TriggerSpecs.set(talent, "buff", "cyrene talent");
        }

        EffectSpec trace = new EffectSpec();
        TriggerSpecs.set(trace, "op", "MODIFY_ATTR");
        TriggerSpecs.set(trace, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(trace, "percent", 0.2);
        TriggerSpecs.set(trace, "turns", 3);
        TriggerSpecs.set(trace, "target", "all_allies");
        if (named) {
            TriggerSpecs.set(trace, "buff", "cyrene trace");
        }

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
print("ok   probe written (named vs unnamed)")
