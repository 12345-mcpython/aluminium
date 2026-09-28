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
 * 1306 Sparkle, from her own file (2026-09-29, round 210): the CRIT DMG share the Skill hands over, and the party-wide boost her talent accrues on every Skill Point spent.
 */
public class SparkleTest {
    private static final int SPARKLE = 1306;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「等同于花火24.00%暴击伤害+45.00%」 -- a derived share PLUS a flat amount, both read from the engine. */
    @Test
    public void theSkillHandsOverHerCritDamageShare() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        double expected = sparkle.getAttribute(AttributeType.CRIT_ATTACK).get() * 0.24 + 0.45;

        battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(ally));

        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before, expected * 0.02,
                "24% of her CRIT_ATTACK plus a flat 45%: expected " + expected);
    }

    /** \u26a0 The talent's party-wide boost on a Skill Point spent, capped at the document's three stacks. */
    @Test
    public void everySpentSkillPointRaisesThePartysDamage() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0);
        double afterOne = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0);
        }
        double afterFive = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        Assertions.assertEquals(0.06, afterOne - before, 1e-6,
                "\u300c\u6bcf\u6d88\u8017 1 \u70b9\u6218\u6280\u70b9\u2026\u4f24\u5bb3\u63d0\u9ad8 6.00%\u300d");
        Assertions.assertEquals(0.18, afterFive - before, 1e-6,
                "\u300c\u6700\u591a\u53ef\u53e0\u52a0 3 \u5c42\u300d -- five firings must still read three stacks of 6%");
    }

    /** \u26a0 The Ultimate: four Skill Points and the 【谜诡】 state on every ally. */
    @Test
    public void theUltimateGrantsSkillPointsAndCipher() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        Assertions.assertTrue(battle.spendSkillPoint(), "the fixture must have a point to spend");
        int before = battle.getSkillPoints();

        battle.fireTriggers(TriggerEvent.ULT_CAST, sparkle, enemy, 0, 0);

        Assertions.assertEquals(Math.min(before + 4, battle.getSkillPointMax()), battle.getSkillPoints(),
                "\u300c\u4e3a\u6211\u65b9\u6062\u590d 4 \u4e2a\u6218\u6280\u70b9\u300d (clamped by the pool's ceiling)");
        Assertions.assertTrue(ally.getBuffManager().hasState("\u8c1c\u8be1"),
                "\u300c\u5e76\u4f7f\u6211\u65b9\u5168\u4f53\u83b7\u5f97\u3010\u8c1c\u8be1\u3011\u300d");
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
