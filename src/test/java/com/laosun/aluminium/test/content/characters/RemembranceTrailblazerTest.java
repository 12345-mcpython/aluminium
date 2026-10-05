package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The Remembrance Trailblazer pair (2026-09-29, round 184): the first summon shipped since 1402/1413, because the document states its SPEED.
 *
 * <p>Round 163 measured that a memosprite file MUST state SPEED ("the action bar cannot schedule a unit with 0 speed"), which is why 1409's memosprite is
 * still unwritten. Here both numbers are in the text - 130 SPD and 80% of the Trailblazer's Max HP plus 640 - so the panel can be stated and asserted.
 */
public class RemembranceTrailblazerTest {
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The memorisprite arrives with the document's own panel, for both ids. */
    @Test
    public void theSummonArrivesWithTheDocumentedPanel() {
        for (int cid : new int[]{8007, 8008}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
            battle.startBattle();

            battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));

            var mem = battle.memospriteOf(tb);
            Assertions.assertNotNull(mem, "cid " + cid + ": 「召唤忆灵迷迷」");
            Assertions.assertEquals(130, mem.getAttribute(AttributeType.SPEED).get(), 1e-9,
                    "cid " + cid + ": 「忆灵迷迷初始拥有130点速度」");
            double expected = tb.getMaxHp() * 0.8 + 640;
            Assertions.assertEquals(expected, mem.getMaxHp(), expected * 0.02,
                    "cid " + cid + ": 「等同于开拓者80%生命上限+640的生命上限」: expected " + expected + ", got " + mem.getMaxHp());
        }
    }

    /** Note: Casting again does not create a second memosprite - the first is still the one on the field. */
    @Test
    public void castingAgainKeepsTheSameMemosprite() {
        Character tb = CharacterFactory.create(8007, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var first = battle.memospriteOf(tb);
        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var second = battle.memospriteOf(tb);

        Assertions.assertSame(first, second, "「若迷迷已在场」 -- the same memosprite, not a second one");
    }

    /** Census: the summon (Skill), the summon (Ultimate) and the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        for (int cid : new int[]{8007, 8008}) {
            var table = com.laosun.aluminium.data.TriggerTables.of(cid);
            Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.SKILL_CAST), "cid " + cid);
            Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST), "cid " + cid);
            Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START), "cid " + cid);
        }
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
