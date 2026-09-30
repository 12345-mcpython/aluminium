package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23057: the wearer's ELATION damage ignores 20% of the target's defence (its speed clause is the row's, and
 * its \u7b11\u70b9 clause is registered).
 *
 * <p>\u2b50 The instance route is what makes "permanently, but only for elation" expressible: it mutates the instance being
 * settled and is filtered by `damage_type`, so nothing can leak into ordinary damage. The expectation is derived from
 * the defence-zone formula, and the control is a cone-less wearer rather than a later moment in time.
 */
public class Cone23057Test {
    private static final int CONE = 23057;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double IGNORE = 0.2;

    private Enemy enemy;

    private Battle battle(boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230570));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double elationHit(Battle battle) {
        Character wearer = battle.allies.getFirst() instanceof Character c ? c : null;
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.ELATION, 1000));
    }

    @Test
    public void elationDamageIgnoresTwentyPercentOfTheDefence() {
        Battle armed = battle(true);
        double withCone = elationHit(armed);
        double defence = enemy.getAttribute(AttributeType.DEFENCE).get();
        double k = 200 + 10.0 * LEVEL;
        double expectedRatio = (defence + k) / (defence * (1 - IGNORE) + k);
        Battle plain = battle(false);
        double withoutCone = elationHit(plain);
        System.out.println("[23057] withCone=" + withCone + " withoutCone=" + withoutCone
                + " ratio=" + (withCone / withoutCone) + " derived=" + expectedRatio + " defence=" + defence);
        Assertions.assertEquals(expectedRatio, withCone / withoutCone, 1e-6,
                "ignoring 20% of the defence multiplies the settled value by (def + K)/(def*0.8 + K)");
    }

    @Test
    public void ordinaryDamageIsUntouched() {
        Battle armed = battle(true);
        Character wearer = (Character) armed.allies.getFirst();
        double elation = armed.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.ELATION, 1000));
        double normal = armed.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        Battle plain = battle(false);
        Character plainWearer = (Character) plain.allies.getFirst();
        double plainElation = plain.applyDamage(enemy, new Damage(plainWearer, enemy, DamageElement.FIRE, DamageType.ELATION, 1000));
        double plainNormal = plain.applyDamage(enemy, new Damage(plainWearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        System.out.println("[23057] elation ratio=" + (elation / plainElation)
                + " ordinary ratio=" + (normal / plainNormal));
        Assertions.assertTrue(elation / plainElation > 1.0, "elation damage is raised");
        Assertions.assertEquals(1.0, normal / plainNormal, 1e-9,
                "ordinary damage is NOT -- that is what `damage_type: ELATION` buys");
    }
}
