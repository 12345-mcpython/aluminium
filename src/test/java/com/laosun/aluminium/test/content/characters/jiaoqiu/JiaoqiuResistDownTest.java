package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1218 椒丘's talent: 「每层【烬煨】会使目标的全属性抗性降低 3%」.
 *
 * <p>Two properties: the penalty follows the CURRENT layer count, and it goes back to zero when the layers do - the case the
 * old snapshot spelling got wrong (it stayed at whatever it was when the last attack landed).
 */
public class JiaoqiuResistDownTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    @Test
    public void theResistPenaltyFollowsTheLayersAndReturnsToZero() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        double bare = enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        int layers = enemy.getBuffManager().stacksOf(ASHEN);
        Assertions.assertTrue(layers >= 1, "precondition: 「施加 1 层【烬煨】」");
        double withOne = enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1218] layers=" + layers + " res " + bare + " -> " + withOne);
        Assertions.assertEquals(0.03 * layers, withOne - bare, 1e-9, "「每层 3%」");

        enemy.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        Assertions.assertEquals(0.03 * (layers + 1), enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare,
                1e-9, "one more layer: the panel follows without another attack");

        // Clear every layer: the penalty has to disappear with them.
        for (int i = 0; i < 10; i++) {
            enemy.getBuffManager().removeState(ASHEN);
        }
        System.out.println("[1218] after clearing: " + (enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare));
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare, 1e-9,
                "层数归零后抗性惩罚必须回到 0（旧写法会停在最后一次攻击时的值）");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}