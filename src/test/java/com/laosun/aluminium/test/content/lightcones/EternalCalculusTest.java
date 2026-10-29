package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 24004: two of its three clauses.
 *
 * <p>ATK +8..12%; after an attack that hits three or more enemies, SPD +8..16% for 1 turn. The "for each enemy hit"
 * clause is REGISTERED, not approximated: its multiplier is the number of enemies hit, and per_stack reads only the
 * target's debuff count, DoT count or a counter (measured).
 */
public class EternalCalculusTest {
    private static final int CONE = 24004;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] ATK = {0.08, 0.09, 0.10, 0.11, 0.12};
    private static final double[] SPD = {0.08, 0.10, 0.12, 0.14, 0.16};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        // 2026-10-19: the attack share is the cone's own `attack_percent` property now (the rule was a duplicate); what is left
        // is the wide-hit speed rule, and the per-hit stack is registered.
        for (int rank = 1; rank <= 5; rank++) {
            double plainAttack = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
            var unit = wearer(rank);
            Assertions.assertTrue(unit.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get() > plainAttack,
                    "rank " + rank + ": the attack share reaches the panel");
            var rules = unit.getTriggerTable().rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                    .filter(r -> r.id().equals("cone24004_speed_on_wide_hit")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the wide-hit speed rule");
            Assertions.assertEquals(List.of("actor == self", "hit_count >= 3.0"),
                    rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": three or more targets");
            Assertions.assertEquals(0.08 + (rank - 1) * 0.02, rules.getFirst().effects().getFirst().getPercent(), 1e-9,
                    "rank " + rank + ": speed");
        }
    }
}
