package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Relic set 325, 2-piece (2026-09-30): the Elation thresholds, hung on every event that can raise it.
 *
 * <p>Measured coverage: the four events below are ALL the writers of ELATION_DAMAGE_BOOST in the shipped content
 * (enumerated), and there is no character-side writer. The "first time" limit is a one-way marker: the rule only fires
 * while the marker is absent, and applying the marker is what makes it first-time-only.
 *
 * <p>Note: Read with rulesFor, not matching: matching evaluates the conditions, and a fresh context has no Elation.
 */
public class PunklordeStageZeroTest {
    private static final int SET = 325;
    private static final String MARK_A = "325_30";
    private static final String MARK_B = "325_60";

    private static List<TriggerTable.CompiledRule> rulesFor(TriggerEvent event) {
        return RelicTriggerTables.of(SET).at(2).rulesFor(event);
    }

    @Test
    public void everyElationSourceCarriesBothThresholds() {
        for (TriggerEvent event : List.of(TriggerEvent.CAST_SETUP, TriggerEvent.SKILL_CAST,
                TriggerEvent.SKILL_POINT_SPENT, TriggerEvent.ULT_CAST)) {
            var rules = rulesFor(event);
            Assertions.assertEquals(2, rules.size(), event + ": the two thresholds");
            var low = rules.get(0);
            Assertions.assertEquals(List.of("self_attr:elation_damage_boost >= 0.3",
                            "self_attr:elation_damage_boost < 0.6", "!self has_state " + MARK_A),
                    low.conditions().stream().map(c -> c.source()).toList(),
                    event + ": the low tier, with the upper bound that makes the tiers exclusive");
            Assertions.assertEquals("APPLY_BUFF", low.effects().get(0).getOp());
            Assertions.assertEquals(MARK_A, low.effects().get(0).getBuff(), event + ": the one-way marker");
            Assertions.assertEquals("CRIT_ATTACK", low.effects().get(1).getAttribute());
            Assertions.assertEquals(0.15, low.effects().get(1).getPercent(), 1e-9, event + ": +15%");
            Assertions.assertEquals("self", low.effects().get(1).getTarget());

            var high = rules.get(1);
            Assertions.assertEquals(List.of("self_attr:elation_damage_boost >= 0.6",
                            "!self has_state " + MARK_B),
                    high.conditions().stream().map(c -> c.source()).toList(), event + ": the high tier");
            Assertions.assertEquals(MARK_B, high.effects().get(0).getBuff());
            Assertions.assertEquals(0.30, high.effects().get(1).getPercent(), 1e-9, event + ": +30%");
            System.out.println("[relic325] " + event + " ok");
        }
    }
}
