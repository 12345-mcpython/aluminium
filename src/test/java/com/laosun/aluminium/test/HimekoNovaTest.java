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

    /** ⚠ The Skill puts 【领航旗语】 on HER for the document's three turns, and hands the party no damage boost (that clause is registered). */
    @Test
    public void theSkillPutsSemaphoreOnHerselfAndNothingElse() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(himeko, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyBoostBefore = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, himeko, enemy, 0, 0);

        Assertions.assertTrue(himeko.getBuffManager().hasState("领航旗语"),
                "「姬子•启行获得【领航旗语】」");
        Assertions.assertFalse(ally.getBuffManager().hasState("领航旗语"),
                "the state is on HER, not on the party");
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBoostBefore, 1e-9,
                "⚠ 「当姬子•启行拥有【领航旗语】时，我方全体造成的伤害提高20%」 is REGISTERED, not written: a permanent modifier would outlive the state");
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
