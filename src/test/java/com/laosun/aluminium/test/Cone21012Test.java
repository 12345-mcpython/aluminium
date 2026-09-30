package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21012: damage is 20% higher against an enemy whose current HP share is at least the WEARER\u2019S own.
 *
 * <p>\u2b50 The comparison is between two variables, which the numeric DSL now reads directly. The judge moves the wearer\u2019s own HP
 * share instead of the enemy\u2019s, so the enemy stays at full health and the two states differ only in the condition.
 */
public class Cone21012Test {
    private static final int CONE = 21012;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private double hit() {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    private void hurtTheEnemy() {
        // \u2605 The enemy is the one to move: lowering ITS share makes `target_hp_percent >= hp_percent` false, while hurting the
        // wearer would do the opposite (and, measured, also woke that character\u2019s own low-health kit: 666 -> 1000).
        battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL,
                enemy.getMaxHp() * 0.5));
        System.out.println("[21012] the enemy is now at " + enemy.getCurrentHp() + " of " + enemy.getMaxHp());
    }

    @Test
    public void aHealthierTargetTakesMore() {
        build(false);
        double plain = hit();
        build(true);
        double boosted = hit();
        System.out.println("[21012] both at full health: " + plain + " -> " + boosted + " (x" + (boosted / plain) + ")");
        // \u2605 1.4, not 1.2: the cone\u2019s own `properties` already carry +20% ALL_DAMAGE_TYPE_BOOST, and the clause\u2019s instance
        // boost joins that SAME attribute additively (measured: 476.19 -> 666.67), it does not multiply on top of it.
        Assertions.assertEquals(1.4, boosted / plain, 0.02, "the props\u2019 20% and the clause\u2019s 20% add up");
    }

    @Test
    public void ahurtEnemyLosesTheBonus() {
        build(false);
        double plain = hit();
        build(true);
        double before = hit();
        hurtTheEnemy();
        double after = hit();
        System.out.println("[21012] before hurting the enemy: " + before + " ; after: " + after
                + " ; the props alone would be " + (plain * 1.2));
        Assertions.assertEquals(plain * 1.2, after, plain * 0.05,
                "with the enemy below the wearer only the props\u2019 20% is left");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        build(false);
        double first = hit();
        build(false);
        double second = hit();
        System.out.println("[21012] without the cone: " + first + " then " + second);
        Assertions.assertEquals(first, second, 1e-9, "no cone, no boost (false case)");
    }
}
