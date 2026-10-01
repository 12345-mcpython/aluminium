package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Six cones shipped together (2026-09-30): clause 1 of each, one permanent attribute, table-driven.
 *
 * <p>23055 was shipped with them and then rolled back: `data/weapons.json` (169 rows) has no row for it, so
 * Weapon.build throws and the card cannot exist in this engine. 23050-23060 misses only that one id.
 *
 * <p>\u26a0 Every value was read from the game data and every attribute name from ALREADY SHIPPED content.
 */
public class SixConeStatsTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1210;

    private static final Object[][] TABLE = {
        {23001, "CRIT_CHANCE", new double[]{0.18, 0.21, 0.24, 0.27, 0.3}},
        {23028, "CRIT_CHANCE", new double[]{0.16, 0.19, 0.22, 0.25, 0.28}},
        {23042, "SPEED", new double[]{0.18, 0.21, 0.24, 0.27, 0.3}},
        {23043, "SPEED", new double[]{0.18, 0.21, 0.24, 0.27, 0.3}},
        {23045, "CRIT_ATTACK", new double[]{0.36, 0.45, 0.54, 0.63, 0.72}},
        {23049, "HEALTH", new double[]{0.3, 0.375, 0.45, 0.525, 0.6}}
    };

    @Test
    public void everyCardStatesItsFirstClause() {
        for (Object[] row : TABLE) {
            int cone = (int) row[0];
            String attribute = (String) row[1];
            double[] values = (double[]) row[2];
            for (int rank = 1; rank <= 5; rank++) {
                var wearer = CharacterFactory.create(WEARER, LEVEL, true,
                        Weapon.build(cone, LEVEL, false, rank));
                var rules = wearer.getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).stream()
                        .filter(r -> r.id().equals("cone" + cone + "_stat")).toList();
                Assertions.assertEquals(1, rules.size(), cone + " rank " + rank + ": the stat rule");
                var e = rules.getFirst().effects().getFirst();
                Assertions.assertEquals("MODIFY_ATTR", e.getOp(), cone + " rank " + rank);
                Assertions.assertEquals(attribute, e.getAttribute(), cone + " rank " + rank);
                Assertions.assertEquals(values[rank - 1], e.getPercent(), 1e-9, cone + " rank " + rank);
                Assertions.assertEquals("self", e.getTarget(), cone + " rank " + rank);
            }
        }
        System.out.println("[six-stats] cards=" + TABLE.length);
    }
}
