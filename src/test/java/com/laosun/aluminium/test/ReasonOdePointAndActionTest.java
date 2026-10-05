package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 18 「献予「理性」之诗」: 「为我方恢复 #4 个战技点并使那刻夏立即行动」 (2026-10-02).
 *
 * <p>⭐ Two readings, one run: the team's skill points rise by #4 = 1, and his action value DROPS (which is what 「立即行动」 means). A rule that only granted
 * the point, or only advanced him, cannot pass both. Nothing is replaced.
 */
public class ReasonOdePointAndActionTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;

    @Test
    public void itGivesAPointAndMovesHimUp() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, anaxa),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        anaxa = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(18);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 18");

        double avBefore = timeRemaining(battle, anaxa);
        int pointsBefore = battle.getSkillPoints();
        SkillExecutor.execute(battle, ode, demiurge, List.of(anaxa));
        battle.processRequests();
        double avAfter = timeRemaining(battle, anaxa);
        int pointsAfter = battle.getSkillPoints();
        System.out.println("[reason] skill points " + pointsBefore + " -> " + pointsAfter
                + " ; his action value " + avBefore + " -> " + avAfter);

        Assertions.assertEquals(pointsBefore + 1, pointsAfter,
                "「为我方恢复 #4 个战技点」-- and #4 is 1 at every level");
        Assertions.assertTrue(avAfter < avBefore,
                "「使那刻夏立即行动」-- his action value must come DOWN (" + avBefore + " -> " + avAfter + ")");
    }

    /** How much action value the unit still has -- zero means "acts now" (mirrors AglaeaMemospriteTest). */
    private static double timeRemaining(Battle battle, com.laosun.aluminium.models.CanHit target) {
        for (var signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }
}
