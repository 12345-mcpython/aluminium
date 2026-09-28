package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8001 Trailblazer (Destruction), from his own file (2026-09-29, round 177): ATK that stacks on weakness breaks.
 *
 * <p>The case asserts the cap: two breaks give the full +40%, and a third changes nothing. Dropping `max_stacks` makes it red.
 */
public class TrailblazerDestructionTest {
    private static final int TB = 8001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「每次击破敌方目标的弱点后，攻击力提高20%…最多叠加2层」. */
    @Test
    public void hisTalentStacksAttackOnBreaksAndStopsAtTwo() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();
        double before = tb.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterOne = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterTwo = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterThree = tb.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(afterOne > before, "\u300c\u6bcf\u6b21\u51fb\u7834\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u540e\uff0c\u653b\u51fb\u529b\u63d0\u9ad820%\u300d: " + before + " -> " + afterOne);
        Assertions.assertTrue(afterTwo > afterOne, "the second layer must add again");
        Assertions.assertEquals(afterTwo, afterThree, 1e-9,
                "\u300c\u8be5\u6548\u679c\u6700\u591a\u53e0\u52a02\u5c42\u300d -- a third break must not add a third layer");
    }

    /** Census: the talent and the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(TB);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK), "the ATK stack");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START),
                "the technique heal (round 178) and the level convention");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
