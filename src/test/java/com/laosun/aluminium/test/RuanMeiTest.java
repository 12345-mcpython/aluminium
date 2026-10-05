package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1303 Ruan Mei, from her own file (2026-09-29, round 211): the SELECTOR clause (everyone but her) and the state she puts on herself.
 */
public class RuanMeiTest {
    private static final int RUANMEI = 1303;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 「除自身以外」: the TEAMMATE gains 10% speed and she does not — measured against a hand-built 20% reference. */
    @Test
    public void theTalentSpeedsTeammatesButNotHerself() {
        Character ruanmei = CharacterFactory.create(RUANMEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ruanmei, ally), List.of(enemy), fixed());
        double herBase = ruanmei.getAttribute(AttributeType.SPEED).baseValue();
        double allyBase = ally.getAttribute(AttributeType.SPEED).baseValue();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        double herBefore = ruanmei.getAttribute(AttributeType.SPEED).get();

        battle.startBattle();

        Assertions.assertEquals(allyBase * 0.1, ally.getAttribute(AttributeType.SPEED).get() - before, allyBase * 0.02,
                "「使除自身以外的队友速度提高10.00%」: ally base " + allyBase);
        Assertions.assertEquals(0.0, ruanmei.getAttribute(AttributeType.SPEED).get() - herBefore, 1e-9,
                "「除自身以外」 -- she is excluded, so her own speed must not move (base " + herBase + ")");
        Assertions.assertTrue(battle.getSkillPoints() >= 0, "the battle ran");
    }

    /** ⚠ The Skill puts 【弦外音】 on HER, for the document's three turns. */
    @Test
    public void theSkillPutsOvertoneOnHerself() {
        Character ruanmei = CharacterFactory.create(RUANMEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ruanmei, ally), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(ruanmei.getSkills().get(SkillType.SKILL), ruanmei, List.of(ally));

        Assertions.assertTrue(ruanmei.getBuffManager().hasState("弦外音"),
                "「施放战技后阮•梅获得【弦外音】」");
        Assertions.assertFalse(ally.getBuffManager().hasState("弦外音"),
                "the state is on HER, not on the ally she aimed at");
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
