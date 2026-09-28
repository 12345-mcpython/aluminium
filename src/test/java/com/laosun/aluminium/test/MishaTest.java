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

    /** \u26a0 Exactly 2 Energy per SKILL_POINT_SPENT, and an unrelated event grants nothing. */
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
                "\u300c\u6211\u65b9\u5168\u4f53\u6bcf\u6d88\u8017 1 \u4e2a\u6218\u6280\u70b9\u300d -- an attack is not a spend");

        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0);
        Assertions.assertEquals(2.0, misha.getCurrentEnergy() - before, 1e-6,
                "\u300c\u540c\u65f6\u7c73\u6c99\u6062\u590d2.00\u70b9\u80fd\u91cf\u300d");
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
