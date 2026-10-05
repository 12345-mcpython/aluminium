package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20004: "战斗开始时，使装备者的效果命中提高#1%，持续#2回合" -- the first cone authored from `weapons.json`'s per-rank table.
 *
 * <p>The wearer's EFFECT_HIT_RATE is a RATIO attribute, so `percent` lands as an absolute addend (round 9): the expectation is the rank's own number.
 * Both ranks 1 and 5 are asserted, because a per-rank file whose ranks all held the same value would otherwise pass unnoticed.
 */
public class Cone20004Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theConeRaisesTheWearersEffectHitRatePerRank() {
        Assertions.assertEquals(0.20, gain(1), 1e-6, "rank 1");
        Assertions.assertEquals(0.40, gain(5), 1e-6, "rank 5");
    }

    private static double gain(int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20004, LEVEL, false, rank));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        double before = wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        battle.startBattle();
        return wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get() - before;
    }
}
