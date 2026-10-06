package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** F-7: "at least N skill points" is a different question from "can the next action be paid for". */
public class SkillPointCountTest {

    @Test
    public void theCountQuestionIsAnsweredAtTheRightBoundary() {
        Battle battle = new Battle(List.of(CharacterFactory.create(1204, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        int start = battle.getSkillPoints();
        System.out.println("[f7] pool = " + start
                + " ; hasAtLeast(start) = " + battle.hasSkillPoint(start)
                + " ; hasAtLeast(start + 1) = " + battle.hasSkillPoint(start + 1)
                + " ; hasAtLeast(0) = " + battle.hasSkillPoint(0));

        Assertions.assertEquals(3, start, "the conventional start");
        Assertions.assertTrue(battle.hasSkillPoint(start), "the pool holds exactly this many");
        Assertions.assertFalse(battle.hasSkillPoint(start + 1), "and not one more");
        Assertions.assertTrue(battle.hasSkillPoint(0), "asking for none is trivially true");

        Assertions.assertTrue(battle.spendSkillPoint(), "spend one");
        Assertions.assertEquals(start - 1, battle.getSkillPoints());
        Assertions.assertTrue(battle.hasSkillPoint(start - 1), "the boundary moved with the pool");
        Assertions.assertFalse(battle.hasSkillPoint(start), "and the old boundary no longer holds");
        Assertions.assertTrue(battle.hasSkillPoint(), "the old question still answers as before");
    }
}
