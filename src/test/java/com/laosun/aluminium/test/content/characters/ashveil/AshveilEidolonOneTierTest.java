package com.laosun.aluminium.test.content.characters.ashveil;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1504 不死途's Eidolon 1, the second tier:「当敌方目标当前生命值百分比小于等于 50% 时，其受到的伤害提高至 36%」.
 *
 * <p>⚠ Lowering the bar took four attempts, and the working one is a LOOP OF SMALL HITS: one big setup hit overkills the
 * target because the settled damage carries the zones ({@code CanHit} has no {@code setCurrentHp}, and {@code consumeHp}
 * leaves an enemy untouched).
 */
public class AshveilEidolonOneTierTest {

    private static final int OWNER = 1504;
    private static final int LEVEL = 80;

    @Test
    public void belowHalfTheEnemyTakesMore() {
        double above = hitAt(0.8);
        double below = hitAt(0.3);
        System.out.println("[1504] fixed 1000-hit: above half " + above + " ; below half " + below);
        Assertions.assertTrue(above > 0 && below > 0, "both hits must land");
        Assertions.assertEquals(1.36 / 1.24, below / above, 0.02, "「提高至 36%」 where it was 24%");
    }

    /** One fixed 1000-point hit on an enemy standing at the given HP fraction. */
    private static double hitAt(double fraction) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, 1);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(owner)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Small steps: a single hit sized to the gap is multiplied by the zones and kills the target.
        for (int step = 0; step < 200 && enemy.getCurrentHp() > enemy.getMaxHp() * fraction; step++) {
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                    com.laosun.aluminium.enums.DamageElement.THUNDER,
                    com.laosun.aluminium.enums.DamageType.NORMAL, enemy.getMaxHp() * 0.01));
            if (enemy.isDeath()) {
                break;
            }
        }
        Assertions.assertFalse(enemy.isDeath(), "the target must survive the setup");
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }
}