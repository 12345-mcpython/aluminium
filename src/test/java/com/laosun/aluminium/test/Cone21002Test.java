package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * Light cone 21002: with it equipped, the WHOLE party carries +8% all-type resistance, so an incoming hit lands for less.
 *
 * <p>\u2b50 Read on the damage an ally actually takes from the same attack with and without the cone -- the resistance zone is the
 * victim\u2019s side, so the number that moves is the one the ally receives, and the enemy\u2019s own panel is checked to be untouched.
 */
public class Cone21002Test {
    private static final int CONE = 21002;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private double incoming(com.laosun.aluminium.models.CanHit victim) {
        return battle.applyDamage(victim, new Damage(enemy, victim, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void thePartyTakesLessAndTheEnemyIsUntouched() {
        build(false);
        double plainWearer = incoming(wearer);
        double plainAlly = incoming(ally);
        build(true);
        double withWearer = incoming(wearer);
        double withAlly = incoming(ally);
        System.out.println("[21002] resistance on the ally=" + ally.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get()
                + " on the wearer=" + wearer.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get()
                + " ; the ally takes " + plainAlly + " -> " + withAlly + " (x" + (withAlly / plainAlly) + ")"
                + " ; the wearer takes " + plainWearer + " -> " + withWearer + " (x" + (withWearer / plainWearer) + ")");
        Assertions.assertEquals(0.08, ally.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get(), 1e-9,
                "the whole party carries 8% (\u6211\u65b9\u5168\u4f53)");
        Assertions.assertTrue(withAlly < plainAlly, "so an incoming hit lands for less");
        Assertions.assertTrue(withWearer < plainWearer, "and the wearer is covered too");
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get(), 1e-9,
                "the enemy\u2019s own resistance is not touched by our side\u2019s buff (false case)");
    }

    @Test
    public void theReductionIsTheStatedShare() {
        build(false);
        double plain = incoming(ally);
        build(true);
        double reduced = incoming(ally);
        double ratio = reduced / plain;
        System.out.println("[21002] ratio=" + ratio + " (a resistance of 8% is meant to scale the hit by 0.92)");
        Assertions.assertEquals(0.92, ratio, 0.01, "8% resistance is 8% less damage");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        build(false);
        double before = ally.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get();
        System.out.println("[21002] without the cone: " + before);
        Assertions.assertEquals(0.0, before, 1e-9, "no cone, no resistance (false case)");
    }
}
