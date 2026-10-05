package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Round 259: a damage_type scope is legal only on the ops that READ it.
 *
 * <p>The list is closed on purpose: the bug this closes is a scope the loader ACCEPTED and the runtime IGNORED
 * (BOOST_DAMAGE, fixed 2026-09-29 -- "follow-up attacks only" used to raise every hit). An op that cannot honour a scope
 * must refuse it at load time rather than silently drop it.
 */
public class DamageTypeScopeValidationTest {
    private static final int OWNER = 1205;

    private static TriggerSpec scoped(String op, String type) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", op);
        TriggerSpecs.set(effect, "percent", 0.24);
        TriggerSpecs.set(effect, "damageType", type);   // the JAVA field name, not the JSON one
        return TriggerSpecs.rule("DEALING_DAMAGE", null, effect);
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(OWNER, List.of(rule));
    }

    @Test
    public void anOpThatReadsTheScopeAcceptsIt() {
        Assertions.assertNotNull(table(scoped("BOOST_DAMAGE", "ADDITIONAL")),
                "BOOST_DAMAGE really does scope its boost");
        System.out.println("[259] BOOST_DAMAGE + damage_type accepted");
    }

    @Test
    public void anOpThatIgnoresItIsRefused() {
        for (String op : List.of("GAIN_ENERGY", "HEAL", "APPLY_BUFF", "ADD_STACK")) {
            IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                    () -> table(scoped(op, "ADDITIONAL")),
                    op + " does not read damage_type, so stating it must fail at load time");
            String message = String.valueOf(refused.getMessage());
            System.out.println("[259] " + op + " refused: " + message.substring(0, Math.min(90, message.length())));
            Assertions.assertTrue(message.contains("damage_type"), "the message names the offending field");
        }
    }
}
