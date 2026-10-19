package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Light cone 23054: three of its four clauses -- and the FIRST content reader of WAVE_START.
 *
 * <p>SPD +18..30% permanently; entering battle grants the charm for 3 turns, and while it holds EVERY ally's crit rate
 * is up 10..14% and crit damage 30..60%, with the wearer's own energy regeneration up 12..20%; and at every wave start
 * the wearer recovers a flat 15 energy.
 *
 * <p>Note: The other trigger of the grant -- "or casting the ultimate ON AN ALLY" -- is REGISTERED: the engine would
 * have to judge that the ultimate landed on our side.
 */
public class WhenSheDecidedToSeeTest {
    private static final int CONE = 23054;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] SPD = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] CRIT = {0.10, 0.11, 0.12, 0.13, 0.14};
    private static final double[] CDMG = {0.30, 0.375, 0.45, 0.525, 0.60};
    private static final double[] REGEN = {0.12, 0.14, 0.16, 0.18, 0.20};
    private static final String CHARM = "上上签";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        // 2026-10-19: the speed share is the cone's own `speed_percent` property now (the rule was a duplicate).
        for (int rank = 1; rank <= 5; rank++) {
            double plain = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
            Assertions.assertTrue(wearer(rank).getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get() > plain,
                    "rank " + rank + ": the speed share reaches the panel");
        }
    }
}
