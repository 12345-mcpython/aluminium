package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A {@code MODIFY_RULE} that SCALES another rule's values must name a rule that states a share.
 *
 * <p>Note: this branch was missing - a scale amendment fell through to the base-chance check, which passed only when the named
 * rule happened to state a {@code base_chance}. 1218's burn multiplier exposed it; 1108's trace loaded by luck.
 */
public class ScaleAmendmentValidationTest {

    private static final int OWNER = 1002;

    private static EffectSpec scale(String rule) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_RULE");
        TriggerSpecs.set(effect, "rule", rule);
        TriggerSpecs.set(effect, "effectPercentScale", 1.15);
        return effect;
    }

    private static com.laosun.aluminium.beans.TriggerSpec stackRule(String id, boolean withPercent) {
        EffectSpec apply = new EffectSpec();
        TriggerSpecs.set(apply, "op", "ADD_STACK");
        TriggerSpecs.set(apply, "buff", "标记");
        TriggerSpecs.set(apply, "amount", 1.0d);
        TriggerSpecs.set(apply, "maxStacks", 5);
        TriggerSpecs.set(apply, "permanent", true);
        TriggerSpecs.set(apply, "target", "target");
        if (withPercent) {
            TriggerSpecs.set(apply, "permanent", null);
            TriggerSpecs.set(apply, "maxStacks", null);
            TriggerSpecs.set(apply, "op", "APPLY_DOT");
            TriggerSpecs.set(apply, "element", "Fire");
            TriggerSpecs.set(apply, "baseChance", 1.0);
            TriggerSpecs.set(apply, "turns", 2);
            TriggerSpecs.set(apply, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(apply, "percent", 1.8);
        }
        var rule = TriggerSpecs.rule("BATTLE_START", List.of(), apply);
        TriggerSpecs.set(rule, "id", id);
        return rule;
    }

    @Test
    public void aRuleWithAShareIsAmendable() {
        TriggerTable table = new TriggerTable(OWNER, List.of(
                stackRule("talent_burn", true),
                TriggerSpecs.rule("BATTLE_START", List.of(), scale("talent_burn"))));
        Assertions.assertNotNull(table);
    }

    @Test
    public void aRuleWithoutAShareIsRefused() {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        stackRule("talent_burn", false),
                        TriggerSpecs.rule("BATTLE_START", List.of(), scale("talent_burn")))));
        Assertions.assertTrue(error.getMessage().contains("scales the values of rule"), error.getMessage());
    }
}



