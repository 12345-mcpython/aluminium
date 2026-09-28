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
 * 1307 Black Swan, from her own file (2026-09-29, round 228): the Arcana stack with its 50 cap, and the Skill's defence shred.
 */
public class BlackSwanTest {
    private static final int BLACK_SWAN = 1307;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 One Arcana per Skill, stopped at the document's fifty, and exactly 20.80% of the enemy's own defence. */
    @Test
    public void theSkillStacksArcanaAndShredsDefence() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        double defenceBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(defenceBefore > 0, "the fixture must have defence to shred: " + defenceBefore);
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("\u5965\u8ff9"), "nothing before the Skill");

        battle.fireTriggers(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0);
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("\u5965\u8ff9"),
                "\u300c\u4f7f\u76ee\u6807\u2026\u9677\u51651\u5c42\u3010\u5965\u8ff9\u3011\u300d");
        Assertions.assertEquals(0.208, (defenceBefore - enemy.getAttribute(AttributeType.DEFENCE).get()) / defenceBefore, 1e-6,
                "\u300c\u9632\u5fa1\u529b\u964d\u4f4e20.80%\u300d of its own defence");

        for (int i = 0; i < 60; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0);
        }
        Assertions.assertEquals(50, enemy.getBuffManager().stacksOf("\u5965\u8ff9"),
                "\u300c\u3010\u5965\u8ff9\u3011\u6700\u591a\u53e0\u52a050\u5c42\u300d -- sixty-one casts must still read fifty");
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
