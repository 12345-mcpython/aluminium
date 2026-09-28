package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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
 * 1510 Himeko • Nova, from her own file (2026-09-29, round 219): the one clause of her kit that needs nothing the engine lacks.
 *
 * <p>Her Territory and Assist-Skill mechanics are registered, so the shipped rule is the marker itself — and the test also records that the marker does NOT carry the
 * party-wide 20%, because that clause is state-conditioned on HER and would otherwise outlive its own state.
 */
public class HimekoNovaTest {
    private static final int HIMEKO = 1510;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The Skill puts 【领航旗语】 on HER for the document's three turns, and hands the party no damage boost (that clause is registered). */
    @Test
    public void theSkillPutsSemaphoreOnHerselfAndNothingElse() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(himeko, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyBoostBefore = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, himeko, enemy, 0, 0);

        Assertions.assertTrue(himeko.getBuffManager().hasState("\u9886\u822a\u65d7\u8bed"),
                "\u300c\u59ec\u5b50\u2022\u542f\u884c\u83b7\u5f97\u3010\u9886\u822a\u65d7\u8bed\u3011\u300d");
        Assertions.assertFalse(ally.getBuffManager().hasState("\u9886\u822a\u65d7\u8bed"),
                "the state is on HER, not on the party");
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBoostBefore, 1e-9,
                "\u26a0 \u300c\u5f53\u59ec\u5b50\u2022\u542f\u884c\u62e5\u6709\u3010\u9886\u822a\u65d7\u8bed\u3011\u65f6\uff0c\u6211\u65b9\u5168\u4f53\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad820%\u300d is REGISTERED, not written: a permanent modifier would outlive the state");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
