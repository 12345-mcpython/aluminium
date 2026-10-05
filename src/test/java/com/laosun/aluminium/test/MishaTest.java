package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1312 Misha, from his own file (2026-09-29, round 233): the energy his talent returns on a Skill Point spent.
 */
public class MishaTest {
    private static final int MISHA = 1312;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ Exactly 2 Energy per SKILL_POINT_SPENT, and an unrelated event grants nothing. */
    @Test
    public void theTalentReturnsEnergyOnEverySkillPointSpent() {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(misha, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = misha.getCurrentEnergy();

        // A control: an event that is not "a Skill Point was spent" must do nothing.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(0.0, misha.getCurrentEnergy() - before, 1e-9,
                "「我方全体每消耗 1 个战技点」 -- an attack is not a spend");

        // 2026-09-30: the amount is the number of points spent -- 1, not 0. It used to be 0, which only passed
        // while the literal `amount: 2` ignored the context entirely (measured rounds 680-682).
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 1);
        Assertions.assertEquals(2.0, misha.getCurrentEnergy() - before, 1e-6,
                "「同时米沙恢复2.00点能量」");
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
