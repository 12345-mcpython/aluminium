package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 2302: break damage ignores 18% of the target's defence.
 *
 * <p>Read through the real break path, and scoped by {@code damage_type: BREAK} -- so the judge also checks that an ordinary
 * hit is NOT given the same ignore (the same settlement, a normal damage instance, no boost from this cone).
 */
public class SailingTowardsASecondLifeTest {
    private static final int CONE = 23027;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double breakDamage() {
        return battle.reduceToughness(wearer, enemy, DamageElement.FIRE, enemy.getMaxStance()).breakDamage();
    }

    private double normalHit() {
        return battle.applyDamage(enemy, new com.laosun.aluminium.models.Damage(wearer, enemy, DamageElement.FIRE,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
    }

    @Test
    public void breakDamageIgnoresDefenceButOrdinaryDamageDoesNot() {
        build(false);
        double plainBreak = breakDamage();
        build(false);
        double plainHit = normalHit();

        build(true);
        double withConeBreak = breakDamage();
        build(true);
        double withConeHit = normalHit();
        System.out.println("[23027] break " + plainBreak + " -> " + withConeBreak
                + " (x" + (withConeBreak / plainBreak) + ") ; normal " + plainHit + " -> " + withConeHit
                + " (x" + (withConeHit / plainHit) + ")");
        Assertions.assertTrue(withConeBreak > plainBreak, "break damage really ignores part of the defence");
        Assertions.assertEquals(plainHit, withConeHit, 1e-9, "and an ordinary hit is untouched (damage_type: BREAK)");
    }

    @Test
    public void theSpecPinsTheTypeScope() {
        build(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone23027_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[23027] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " damageType=" + effect.getDamageType() + " instance=" + effect.getInstance());
                Assertions.assertEquals("DEFENCE_IGNORE", effect.getAttribute(), "it ignores defence");
                Assertions.assertEquals(0.2, effect.getPercent(), 1e-9, "20% at rank 1");
                Assertions.assertEquals("BREAK", effect.getDamageType(), "and only for break damage");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
