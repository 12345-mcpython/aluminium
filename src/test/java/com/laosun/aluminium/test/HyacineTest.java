package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
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
 * 1409 Hyacine, from her own file (2026-09-29, rounds 163-166).
 *
 * <p>The rules cover only what a skill's data does NOT do. Measured this round: the HEAL op is exact (`owner_max_hp` 10% + 100 healed
 * 219.52864 against a declared 219.52864000000002 when isolated on TURN_START), while a skill also heals from its own data — so a rule that
 * repeats the document's heal double-counts it. Her state and the party Max HP raise are therefore the shipped clauses.
 */
public class HyacineTest {
    private static final int HYACINE = 1409;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「风堇进入【雨过天晴】状态」 and 「我方全体目标生命上限提高30.00%」. */
    @Test
    public void herUltimateMarksHerAndRaisesThePartysMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyMaxBefore = ally.getMaxHp();
        double herMaxBefore = hyacine.getMaxHp();

        battle.castImmediate(hyacine.getSkills().get(SkillType.ULTRA), hyacine, List.of(ally));

        Assertions.assertTrue(hyacine.getBuffManager().hasState("\u96e8\u8fc7\u5929\u6674"),
                "\u300c\u98ce\u5807\u8fdb\u5165\u3010\u96e8\u8fc7\u5929\u6674\u3011\u72b6\u6001\u300d");
        Assertions.assertTrue(ally.getMaxHp() > allyMaxBefore, "\u300c\u6211\u65b9\u5168\u4f53\u76ee\u6807\u751f\u547d\u4e0a\u9650\u63d0\u9ad830.00%\u300d -- ally");
        Assertions.assertTrue(hyacine.getMaxHp() > herMaxBefore, "\u300c\u6211\u65b9\u5168\u4f53\u300d includes Hyacine herself");
    }

    /** Census: the ultimate's rule and the level convention; the heals belong to the skill data, not to rules. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(HYACINE);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "state and Max HP");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the skill's heal comes from the skill data (measured), so a rule would double-count it");
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
