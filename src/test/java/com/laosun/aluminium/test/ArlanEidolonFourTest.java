package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1008：「受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: the eidolon rank. Same scene, same lethal blow.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;

    /** ⭐ At E4 he stands at a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeStandsAtAQuarter() {
        double[] result = afterLethalBlow(4);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」 (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "「回复至自身生命上限的 25%」");
    }

    /** ⚠ Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "星魂 4 才有这一条");
    }

    // ==================================================================

    /** { his HP after the blow, his Max HP }. */
    private static double[] afterLethalBlow(int eidolon) {
        Character him = CharacterFactory.create(ARLAN, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        return new double[]{him.isDeath() ? 0 : him.getCurrentHp(), him.getMaxHp()};
    }
}
