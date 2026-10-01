package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The conditions of the arc\u2019s characters, written out LITERALLY (2026-09-30).
 *
 * <p>\u26a0 Why literal: {@code CharacterConditionCoverageTest} compares the file against the compiled table, so a
 * mutation that changes both sides together stays green by definition. To catch \u300cthe content itself was changed\u300d the
 * expectation has to live in the judge. The literals below are the parser\u2019s spelling: attribute names lowered and
 * integral thresholds given a decimal point.
 */
public class CharacterConditionLiteralsTest {
    private static final int LEVEL = 80;

    private void assertRule(int cid, String event, String id, List<String> expected) {
        Character c = CharacterFactory.create(cid, LEVEL);
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.valueOf(event)).stream()
                .filter(r -> r.id().equals(id)).toList();
        Assertions.assertEquals(1, rules.size(), cid + " " + id + " on " + event);
        var actual = rules.getFirst().conditions().stream().map(x -> x.source()).toList();
        Assertions.assertEquals(expected, actual, cid + " " + id);
    }

    @Test
    public void everyRuleWaitsForExactlyWhatItSays() {
        assertRule(1505, "BATTLE_START", "p1505_elation_value", List.of());
        assertRule(1505, "BATTLE_START", "p1505_technique_gift", List.of());
        assertRule(1505, "ENERGY_GAINED", "p1505_energy_sync", List.of());
        assertRule(1505, "DEALING_DAMAGE", "p1505_skill_elation_rider", List.of("actor == self", "from_category BPSKILL", "self_resource:好活当赏 >= 1.0"));
        assertRule(1505, "CAST_SETUP", "p1505_ult_elation_rider", List.of("actor == self", "from_category ULTRA", "self_resource:好活当赏 >= 1.0"));
        assertRule(1505, "CAST_SETUP", "p1505_ult_random_rider", List.of("actor == self", "from_category ULTRA", "self_resource:好活当赏 >= 1.0"));
        assertRule(1505, "ENERGY_GAINED", "p1505_energy_accumulator", List.of());
        assertRule(1505, "BATTLE_START", "p1505_technique_damage", List.of());
        assertRule(1505, "CAST_SETUP", "p1505_skill_laughs", List.of("actor == self", "from_category BPSKILL"));
        assertRule(1502, "CAST_SETUP", "p1502_ult_penetration", List.of("actor == self", "from_category ULTRA"));
        assertRule(1506, "CAST_SETUP", "p1506_ult_advance", List.of("actor == self", "from_category ULTRA"));
        assertRule(1506, "RESOURCE_CHANGED", "p1506_laughs_to_hidden", List.of("resource_changed:笑点"));
    }
}
