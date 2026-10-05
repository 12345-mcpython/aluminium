package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 130Black Swan, from her own file (2026-09-29, round 228): the Arcana stack with its 50 cap, and the Skill's defence shred.
 */
public class BlackSwanTest {
    private static final int BLACK_SWAN = 1307;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: One Arcana per Skill, stopped at the document's fifty, and exactly 20.80% of the enemy's own defence. */
    @Test
    public void theSkillStacksArcanaAndShredsDefence() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        double defenceBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(defenceBefore > 0, "the fixture must have defence to shred: " + defenceBefore);
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("奥迹"), "nothing before the Skill");

        battle.fireTriggers(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0);
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("奥迹"),
                "「使目标…陷入1层【奥迹】」");
        Assertions.assertEquals(0.208, (defenceBefore - enemy.getAttribute(AttributeType.DEFENCE).get()) / defenceBefore, 1e-6,
                "「防御力降低20.80%」 of its own defence");

        for (int i = 0; i < 60; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0);
        }
        Assertions.assertEquals(50, enemy.getBuffManager().stacksOf("奥迹"),
                "「【奥迹】最多叠加50层」 -- sixty-one casts must still read fifty");
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
