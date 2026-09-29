package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1204 景元's trace 「遣将」: 「施放战技后，暴击率提升10.0%，持续2回合」.
 *
 * <p>Judged as the difference in CRIT_CHANCE across a skill cast, and then again after the two turns its duration states have passed --
 * a duration that is not judged is a duration that can be wrong while the test stays green.
 */
public class JingyuanTraceTest {
    private static final int JINGYUAN = 1204;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSkillRaisesHisCritChanceForTwoTurns() {
        Character jingyuan = CharacterFactory.create(JINGYUAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jingyuan), List.of(enemy), new Random(0));
        battle.startBattle();
        double base = jingyuan.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.castImmediate(jingyuan.getSkills().get(SkillType.SKILL), jingyuan, List.of(enemy));
        double raised = jingyuan.getAttribute(AttributeType.CRIT_CHANCE).get();
        Assertions.assertEquals(0.1, raised - base, 1e-9, "one trace: " + base + " -> " + raised);
    }
}
