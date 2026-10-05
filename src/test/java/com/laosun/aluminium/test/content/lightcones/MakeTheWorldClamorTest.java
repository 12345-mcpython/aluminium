package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Light cone 21013: energy on entering battle, and a permanent ultimate-damage boost. */
public class MakeTheWorldClamorTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theConeGrantsEnergyAndRaisesUltimateDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21013, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double boostBefore = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
        double energyBefore = unit.getCurrentEnergy();
        battle.startBattle();
        double boost = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get() - boostBefore;
        double energy = unit.getCurrentEnergy() - energyBefore;
        System.out.println("[21013] ultBoost=" + boost + " energyGain=" + energy);
        Assertions.assertEquals(0.64, boost, 1e-6, "rank 5 states 64%");
        Assertions.assertEquals(32.0, energy, 1e-6, "rank 5 states 32 energy");
    }
}
