package com.laosun.aluminium.test.content.characters.argenti;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1302 银枝's technique:「进入战斗时，对敌方全体造成等同于银枝 80% 攻击力的物理属性伤害，并使银枝恢复 15 点能量」.
 */
public class ArgentiTechniqueTest {

    @Test
    public void theTechniqueHitsEveryoneAndPaysEnergy() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[1302] with technique: lost=" + with[0] + " energy=" + with[1]
                + " ; without: lost=" + without[0] + " energy=" + without[1]);
        Assertions.assertTrue(with[0] > 0, "「对敌方全体造成…物理属性伤害」");
        Assertions.assertEquals(15.0, with[1] - without[1], 1e-9, "「恢复 15 点能量」");
    }

    /** {total HP the enemies lost, her energy}. */
    private static double[] run(boolean technique) {
        Character him = CharacterFactory.create(1302, 80);
        if (technique) {
            him.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy(), BattleFixture.dummy())),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double lost = 0;
        for (var enemy : battle.enemyUnits()) {
            lost += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        return new double[]{lost, him.getCurrentEnergy()};
    }
}