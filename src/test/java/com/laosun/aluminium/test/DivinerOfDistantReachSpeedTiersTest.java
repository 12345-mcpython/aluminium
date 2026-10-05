package com.laosun.aluminium.test;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Relic 130, 4-piece (2026-09-30): "SPD >= 120/160 so CRIT Rate +10%/18%".
 *
 * <p>Note: The lower tier MUST carry the upper bound. Without it a wearer at SPD >= 160 satisfies both rules and takes
 * +28% instead of the 18% the text says -- a rule that is perfectly legal and would pass a naive shape check. That is
 * why this judge asserts the upper bound, and why the sweep deletes it.
 *
 * <p>Also note the fixture hazard this set taught us: a same-id file under src/test/resources SHADOWS shipped content
 * (the fixture had to move to 126 before this set could be reclaimed at all).
 */
public class DivinerOfDistantReachSpeedTiersTest {
    private static List<TriggerTable.CompiledRule> rulesFor(TriggerEvent event) {
        return RelicTriggerTables.of(130).at(4).rulesFor(event);
    }

    @Test
    public void theTwoTiersAreExclusiveAndCarryTheRightValues() {
        var rules = rulesFor(TriggerEvent.BATTLE_START);
        Assertions.assertEquals(2, rules.size(), "two tiers");
        var low = rules.stream().filter(r -> r.id().equals("relic130_spd120_crit")).findFirst().orElseThrow();
        Assertions.assertEquals(List.of("self_attr:speed >= 120.0", "self_attr:speed < 160.0"),
                low.conditions().stream().map(c -> c.source()).toList(),
                "the lower tier must be bounded above, or SPD >= 160 double-counts");
        Assertions.assertEquals("CRIT_CHANCE", low.effects().getFirst().getAttribute());
        Assertions.assertEquals(0.10, low.effects().getFirst().getPercent(), 1e-9);
        var high = rules.stream().filter(r -> r.id().equals("relic130_spd160_crit")).findFirst().orElseThrow();
        Assertions.assertEquals(List.of("self_attr:speed >= 160.0"),
                high.conditions().stream().map(c -> c.source()).toList());
        Assertions.assertEquals(0.18, high.effects().getFirst().getPercent(), 1e-9);
        Assertions.assertEquals("self", high.effects().getFirst().getTarget());
        System.out.println("[130] shape ok: two exclusive tiers");
    }
}
