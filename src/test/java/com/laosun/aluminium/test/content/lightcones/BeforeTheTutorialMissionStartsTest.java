package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Light cone 22000: hit a target whose DEFENCE was lowered and 4 energy comes back. */
public class BeforeTheTutorialMissionStartsTest {
    private static final int CONE = 22000;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int MODIFIER_ID = 990002;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private void hit() {
        battle.applyDamage(enemy, new com.laosun.aluminium.models.Damage(wearer, enemy,
                com.laosun.aluminium.enums.DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.NORMAL, 100));
    }

    private void lowerDefence() {
        enemy.getAttribute(AttributeType.DEFENCE)
                .addModifier(DoubleValue.Modifier.addPercent(-0.3, DoubleValue.Modifier.ModifierSource.DEBUFF, MODIFIER_ID));
    }

    @Test
    public void fourEnergyOnlyAgainstALoweredTarget() {
        build(true);
        double before = wearer.getCurrentEnergy();
        hit();
        double plain = wearer.getCurrentEnergy() - before;
        lowerDefence();
        hit();
        double lowered = wearer.getCurrentEnergy() - before - plain;
        System.out.println("[22000] energy after a plain hit = " + plain + " ; after hitting a lowered target = " + lowered);
        Assertions.assertEquals(0.0, plain, 1e-9, "a target that was not lowered pays nothing");
        Assertions.assertEquals(4.0, lowered, 1e-9, "4 energy at rank 1");
    }

    @Test
    public void withoutTheConeNothingIsPaid() {
        build(false);
        lowerDefence();
        double before = wearer.getCurrentEnergy();
        hit();
        System.out.println("[22000] without the cone: energy +" + (wearer.getCurrentEnergy() - before));
        Assertions.assertEquals(0.0, wearer.getCurrentEnergy() - before, 1e-9, "no cone, no energy (false case)");
    }
}
