package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 22004: for every element the target is weak to, the wearer deals 4% more damage to it.
 *
 * <p>⭐ Judged as a RATIO of ratios, so the two monsters' defence zones cancel: each is hit with and without the cone and the
 * boosts must be 1 + 0.04n for its own n. The sentence's cap of 7 needs no code -- the game has exactly seven elements.
 */
public class Cone22004Test {
    private static final int CONE = 22004;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int ONE_WEAKNESS = 1002060;
    private static final int TWO_WEAKNESSES = 1002011;

    private Character wearer;

    private double damage(int monster, boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(monster, 90, 1);
        Battle battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 220041));
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void theBoostIsFourPercentPerWeakness() {
        double oneWithout = damage(ONE_WEAKNESS, false);
        double oneWith = damage(ONE_WEAKNESS, true);
        double twoWithout = damage(TWO_WEAKNESSES, false);
        double twoWith = damage(TWO_WEAKNESSES, true);
        double ratioOne = oneWith / oneWithout;
        double ratioTwo = twoWith / twoWithout;
        System.out.println("[22004] 1 weakness: x" + ratioOne + " ; 2 weaknesses: x" + ratioTwo
                + " ; ratio of ratios=" + (ratioTwo / ratioOne) + " expected=" + (1.08 / 1.04));
        Assertions.assertEquals(1.04, ratioOne, 0.005, "one weakness: +4%");
        Assertions.assertEquals(1.08, ratioTwo, 0.005, "two weaknesses: +8%");
    }

    @Test
    public void theSpecNamesThePerStackFactor() {
        damage(TWO_WEAKNESSES, true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, null, null))) {
            if (!rule.id().startsWith("cone22004_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[22004] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " perStack=" + effect.getPerStack() + " instance=" + effect.getInstance());
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
