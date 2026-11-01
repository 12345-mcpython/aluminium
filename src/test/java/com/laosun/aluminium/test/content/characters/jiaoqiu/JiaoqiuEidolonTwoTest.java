package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1218 椒丘's Eidolon 2: 「【烬煨】状态下的敌方目标受到的灼烧伤害倍率提高 300%」 - the talent's Burn scales x4.
 *
 * <p>Note: the fixture is the one `JiaoqiuTest` uses (narrow constructor, `alwaysRoll(0.0)`): an earlier probe with a wider
 * fixture and a 0.99 roll saw no Burn at all, which misread as an engine path problem rather than a scaffolding difference.
 */
public class JiaoqiuEidolonTwoTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;

    @Test
    public void theBurnPerTickQuadruples() {
        double e1 = burnTick(1);
        double e2 = burnTick(2);
        System.out.println("[1218] e1=" + e1 + " e2=" + e2 + " ratio=" + (e2 / e1));
        Assertions.assertTrue(e1 > 0, "precondition: the talent's Burn is on the enemy and ticks");
        Assertions.assertEquals(4.0, e2 / e1, 1e-6, "「倍率提高 300%」 = x4");
    }

    /**
     * @return the damage one Burn tick settles on the enemy
     */
    private static double burnTick(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        double before = enemy.getCurrentHp();
        battle.tickDots(enemy);
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}