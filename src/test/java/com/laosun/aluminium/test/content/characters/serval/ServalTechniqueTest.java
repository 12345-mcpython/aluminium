package com.laosun.aluminium.test.content.characters.serval;

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
 * 1103 希露瓦's technique:「进入战斗后对敌方随机单体造成 50% 攻击力的雷属性伤害，同时有 100% 的基础概率使敌方每个单体目标陷入触电状态，持续 3 回合」.
 */
public class ServalTechniqueTest {

    @Test
    public void theTechniqueShocksEveryoneAndDamagesOne() {
        Character her = CharacterFactory.create(1103, 80);
        her.getBuffManager().addBuff(new StateBuff("秘技", 1));
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy(), BattleFixture.dummy())),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int shocked = 0;
        double lost = 0;
        for (var enemy : battle.enemyUnits()) {
            if (enemy.getBuffManager().hasState("触电")) {
                shocked++;
            }
            lost += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        System.out.println("[1103] shocked=" + shocked + " totalLost=" + lost);
        Assertions.assertEquals(2, shocked, "「使敌方每个单体目标陷入触电状态」");
        Assertions.assertTrue(lost > 0, "「对敌方随机单体造成…雷属性伤害」");
    }

    @Test
    public void withoutTheTechniqueNothingHappens() {
        Character her = CharacterFactory.create(1103, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertFalse(battle.enemyUnits().getFirst().getBuffManager().hasState("触电"),
                "the control: no technique, no shock");
    }
}