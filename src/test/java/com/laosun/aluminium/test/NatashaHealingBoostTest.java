package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * trace (行迹) "医者": "娜塔莎<b>提供的</b>治疗量提高10%" - the provider's side, read from the healer.
 *
 * <p>The reader is {@code OUTGOING_HEALING_BOOST}, which {@code Battle.heal} takes from the unit applying the heal, so the
 * number has to show up as a ratio between two healers healing the same kind of target by the same base amount. The
 * upstream row (AvatarSkillTreeConfig, PointID 1105102) states {@code [0.1]}, which is where the 10% comes from.
 */
public class NatashaHealingBoostTest {
    private static final int NATASHA = 1105;
    /** A healer with no such rule, used as the control. */
    private static final int PLAIN = 1002;
    private static final int TARGET_ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BASE_HEAL = 500;

    @Test
    public void herTraceRaisesTheHealingSheProvidesByTenPercent() {
        double boosted = computedHeal(NATASHA);
        double plain = computedHeal(PLAIN);

        Assertions.assertTrue(plain > 0, "the control must compute something, or the ratio below means nothing");
        Assertions.assertEquals(1.1, boosted / plain, 1e-6,
                "「娜塔莎提供的治疗量提高10%」 -- "
                        + "boosted " + boosted + " vs plain " + plain);
    }

    /**
     * The healing the given healer computes for a fixed base amount.
     *
     * <p>Note: {@code Battle.heal} returns the HP <b>actually restored</b> and caps it at what is missing, so a first
     * version of this test damaged the target by half its HP and then compared two heals that were BOTH clipped to the
     * same 315 HP -- reading 1.0 and looking exactly like a boost that does not work. {@code calculateHeal} is the same
     * formula ({@code base  x  (1 + outgoing)  x  (1 + taken)}) without that cap, which is the part the trace changes.
     */
    private static double computedHeal(int healerCid) {
        Character healer = CharacterFactory.create(healerCid, LEVEL);
        Character target = CharacterFactory.create(TARGET_ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(healer, target), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle.calculateHeal(healer, target, BASE_HEAL);
    }
}
