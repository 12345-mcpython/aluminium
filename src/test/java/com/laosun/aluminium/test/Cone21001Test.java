package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21001: "敌方目标每承受 1 个负面效果，装备者对其造成的伤害提高 #1%，最多叠加 #2 层" at superimposition 5 (24% x 3).
 *
 * <p>The expectation is built from the count the ENGINE reports, because that is what the clause counts -- and a fixture cannot assume how many debuffs a given
 * buff class contributes (measured: a `ControlBuff` contributes two).
 */
public class Cone21001Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.24;
    private static final int CAP = 3;

    @Test
    public void theConeScalesPerDebuffAndCapsAtThree() {
        double clean = settled(0);
        for (int fixture = 1; fixture <= 4; fixture++) {
            double[] pair = settledWithCount(fixture);
            double ratio = pair[0] / clean;
            int count = (int) pair[1];
            double expected = 1 + Math.min(count, CAP) * SHARE;
            System.out.println("[21001] fixture=" + fixture + " engineCount=" + count
                    + " ratio=" + ratio + " expected=" + expected);
            Assertions.assertEquals(expected, ratio, 3e-2,
                    "count " + count + " should scale by min(count, 3) x 0.24");
        }
        Assertions.assertEquals(1 + CAP * SHARE, settledWithCount(3)[0] / clean, 3e-2, "the cap is reached");
    }

    private static double settled(int debuffs) {
        return settledWithCount(debuffs)[0];
    }

    private static double[] settledWithCount(int debuffs) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21001, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (debuffs >= 1) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        if (debuffs >= 2) {
            enemy.getBuffManager().addBuff(new ControlBuff(Constant.CONTROL_EFFECTS.get("IMPRISONED"), 3));
        }
        if (debuffs >= 3) {
            enemy.getBuffManager().addBuff(new ControlBuff(Constant.CONTROL_EFFECTS.get("FROZEN"), 3));
        }
        int count = enemy.getBuffManager().debuffCount();
        double settled = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        return new double[] {settled, count};
    }
}
