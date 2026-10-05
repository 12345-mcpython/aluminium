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
 * 1211: "when a teammate of Bailu (白露) takes a lethal attack ... Bailu immediately provides healing for it, restoring health equal to 18.00% of Bailu's Max HP + 480"
 * (2026-10-02).
 *
 * <p>ONE VARIABLE: whether the blow would have killed the teammate. Nothing else differs -- same party, same heal amount, same scene.
 */
public class LethalHealOnTeammateTest {
    private static final int BAILU = 1211;
    private static final int TEAMMATE = 1002;
    private static final int MONSTER = 1002011;

    /** A lethal blow: the teammate is saved, at Bailu (白露)'s 18% Max HP plus 480. */
    @Test
    public void aLethalBlowIsHealed() {
        double[] result = afterBlow(true);
        Assertions.assertFalse(result[0] < 0, "the teammate is alive (hp " + result[0] + ")");
        Assertions.assertTrue(result[0] > 0, "「will not fall into the unable-to-fight state」-- a teammate at 0 HP would have fallen");
        Assertions.assertEquals(result[1], result[0], 1.0,
                "「restores health equal to 18.00% of Bailu's Max HP + 480」");
    }

    /** Note: A survivable blow: the clause has not started, so the damage simply lands. */
    @Test
    public void aSurvivableBlowIsNotHealed() {
        double[] result = afterBlow(false);
        Assertions.assertTrue(result[0] < result[2], "「when taking a lethal attack」-- this one was not lethal");
    }

    // ==================================================================

    /** { hp after, the amount the sentence promises, hp before }. */
    private static double[] afterBlow(boolean lethal) {
        Character healer = CharacterFactory.create(BAILU, 80, false, null, null, 0);
        Character teammate = CharacterFactory.create(TEAMMATE, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(healer, teammate),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = teammate.getCurrentHp();
        double damage = lethal ? before * 2.0 : before * 0.1;
        battle.applyTrueDamage(battle.enemies.getFirst(), teammate, DamageElement.ICE, damage);
        battle.processRequests();
        return new double[]{teammate.getCurrentHp(), healer.getMaxHp() * 0.18 + 480, before};
    }
}
