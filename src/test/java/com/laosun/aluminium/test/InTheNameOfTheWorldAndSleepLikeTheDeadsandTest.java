package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Cones 23004 (boost against a debuffed target) and 23012 (a constant crit-damage clause). */
public class InTheNameOfTheWorldAndSleepLikeTheDeadsandTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone23004BoostsOnlyAgainstADebuffedTarget() {
        double clean = settled(false);
        double debuffed = settled(true);
        System.out.println("[23004] clean=" + clean + " debuffed=" + debuffed + " ratio=" + (debuffed / clean));
        Assertions.assertEquals(1.4, debuffed / clean, 2e-2, "rank 5 states 40% against a debuffed target");
    }

    @Test
    public void cone23012RaisesCritDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23012, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.startBattle();
        double gain = unit.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
        System.out.println("[23012] critDamageGain=" + gain);
        Assertions.assertEquals(0.5, gain, 1e-6, "rank 5 states 50% crit damage");
    }

    private static double settled(boolean debuffed) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23004, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (debuffed) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }
}
