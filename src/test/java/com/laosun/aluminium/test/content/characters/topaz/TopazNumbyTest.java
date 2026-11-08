package com.laosun.aluminium.test.content.characters.topaz;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1112 托帕&账账: 账账 (Numby) is a <b>panel-less</b> summon with its own action - 「战斗开始时召唤账账…初始拥有 80 点速度，行动时…造成等同于托帕 150% 攻击力的火属性伤害」.
 *
 * <p>It has no skill row in the data, so the share is the sentence's own number; the action point is its turn, exactly as 1204's 神君.
 */
public class TopazNumbyTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;

    @Test
    public void numbyEntersAtEightySpeed() {
        Battle battle = scene();
        Summon numby = battle.summonsOf(battle.characters().getFirst()).getFirst();
        Assertions.assertEquals("账账", numby.getName());
        Assertions.assertEquals(80, numby.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「账账初始拥有 80 点速度」");
    }

    @Test
    public void numbyStrikesOnItsOwnTurn() {
        Battle battle = scene();
        Summon numby = battle.summonsOf(battle.characters().getFirst()).getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
        battle.processRequests();
        Assertions.assertTrue(enemy.getCurrentHp() < before, "「行动时发动追加攻击…造成火属性伤害」");
    }

    private static Battle scene() {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
