package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「结界持续期间，敌方目标受到的伤害提高30.00%」 — a ratio, not a presence check, so the number is what is pinned.
 *
 * <p>The zone's clock is her own (the text says 「结界持续2回合，自身每回合开始时结界持续回合数减1」), which is exactly
 * {@code ticks_on: "self"}; the effect itself reaches enemies through the {@code all_enemies} selector.
 */
public class TrinnonZoneTest {
    private static final int TRINNON = 1403;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    @Test
    public void herZoneRaisesTheDamageEnemiesTakeByThirtyPercent() {
        double without = damageTaken(false);
        double with = damageTaken(true);

        Assertions.assertTrue(without > 0, "the control must land, or the ratio below means nothing");
        Assertions.assertEquals(1.3, with / without, EPS,
                "\u300c\u7ed3\u754c\u6301\u7eed\u671f\u95f4\uff0c\u654c\u65b9\u76ee\u6807\u53d7\u5230\u7684\u4f24\u5bb3\u63d0\u9ad830.00%\u300d -- "
                        + "with " + with + " vs without " + without);
    }

    /** Applies one identical hit to a fresh enemy, optionally after her Ultimate has opened the zone. */
    private static double damageTaken(boolean withZone) {
        Character trinnon = CharacterFactory.create(TRINNON, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(trinnon, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        if (withZone) {
            battle.castImmediate(trinnon.getSkills().get(SkillType.ULTRA), trinnon, List.of(enemy));
        }
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }
}
