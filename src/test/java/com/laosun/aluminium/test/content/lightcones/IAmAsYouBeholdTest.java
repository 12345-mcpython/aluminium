package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Light cone 23062: two of its three clauses.
 *
 * <p>ATK +18..30% and energy regeneration +10..20%, both permanent. Entering battle or casting the ultimate grants the
 * state for 3 turns, and while it holds EVERY ally's crit damage is up 24..48%. The per-energy clause is REGISTERED.
 *
 * <p>Note: Two rules rather than one, because the condition DSL has no OR; and all three effects of a grant share the
 * state's own 3-turn life, so they live and die together (the engine has no "as long as the state lasts" spelling).
 */
public class IAmAsYouBeholdTest {
    private static final int CONE = 23062;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] ATK = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] REGEN = {0.10, 0.125, 0.15, 0.175, 0.20};
    private static final double[] CRITDMG = {0.24, 0.30, 0.36, 0.42, 0.48};
    private static final String KING = "王之娱乐";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        // 2026-10-19: the attack and energy-regen shares are the cone's own properties now (the stat rule was a duplicate);
        // the 30% 【王之娱乐】 window and the per-energy ultimate rider are pinned in ConeAuditBatchTwentyEightTest.
        for (int rank = 1; rank <= 5; rank++) {
            double plainAttack = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
            double plainRegen = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).get();
            var unit = wearer(rank);
            Assertions.assertTrue(unit.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get() > plainAttack,
                    "rank " + rank + ": the attack share reaches the panel");
            Assertions.assertTrue(unit.getAttribute(com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).get()
                    > plainRegen, "rank " + rank + ": the regen share reaches the panel");
        }
    }
}
