package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The conditions of every shipped cone rule, asserted against the text (2026-09-30).
 *
 * <p>A fourth mutation layer (rewriting a rule's "when") stayed green for seventeen of these, because the cone
 * judges asserted effects but never conditions. Conditions decide WHEN a rule is in force, so a wrong one makes a rule
 * fire at the wrong time while every numeric assertion still passes.
 *
 * <p>Note: Measured: the parser LOWERS the attribute name, so the condition text the engine produces is
 * {@code self_attr:breaking_effect >= 1.5} even though the content writes it in upper case.
 */
public class ConeConditionCoverageTest {
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    private Character wearer(int cone) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL));
    }

    private void assertConditions(int cone, TriggerEvent event, String idPrefix, List<String> expected) {
        var rules = wearer(cone).getTriggerTable().rulesFor(event).stream()
                .filter(r -> r.id().startsWith(idPrefix)).toList();
        Assertions.assertEquals(1, rules.size(), "one rule " + idPrefix + " on " + event);
        var conditions = rules.getFirst().conditions().stream().map(c -> c.source()).toList();
        System.out.println("[conditions] " + idPrefix + " -> " + conditions);
        Assertions.assertEquals(expected, conditions, idPrefix);
    }

    @Test
    public void everyShippedConeRuleStatesWhatItWaitsFor() {
        assertConditions(21061, TriggerEvent.DEALING_DAMAGE, "cone21061_damage_up", List.of("actor == self"));
        assertConditions(21061, TriggerEvent.DEALING_DAMAGE, "cone21061_vulnerability", List.of("actor == self"));
        assertConditions(23019, TriggerEvent.CAST_SETUP, "cone23019_ult_damage",
                List.of("actor == self", "from_category ULTRA"));
        assertConditions(23019, TriggerEvent.CAST_SETUP, "cone23019_ult_skill_point",
                List.of("actor == self", "from_category ULTRA", "self_attr:breaking_effect >= 1.5"));
        assertConditions(23029, TriggerEvent.DEALING_DAMAGE, "cone23029_armor_break", List.of("actor == self"));
        assertConditions(23041, TriggerEvent.DEALING_DAMAGE, "cone23041_defence_down", List.of("actor == self"));
        assertConditions(23046, TriggerEvent.CAST_SETUP, "cone23046_skill_stacks",
                List.of("actor == self", "from_category BPSKILL"));
        assertConditions(23051, TriggerEvent.CAST_SETUP, "cone23051_ult_heal",
                List.of("actor == self", "from_category ULTRA"));
        assertConditions(23063, TriggerEvent.CAST_SETUP, "cone23063_ult_skill_point",
                List.of("actor == self", "from_category ULTRA"));
        assertConditions(24003, TriggerEvent.CAST_SETUP, "cone24003_ult_dot",
                List.of("actor == self", "from_category ULTRA"));
    }
}
