package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 26 clause 4 (2026-10-02): 「当【故事】达到3点时，德谬歌消耗全部【故事】…」. Two-sided: at three the counter is spent, at two nothing happens.
 */
public class TrueSelfOdeSpendsThreeStoryTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 26;
    private static final String STORY = "\u6545\u4e8b";

    @Test
    public void threePointsAreSpentForTheExtraTurn() {
        double three = storyAfterUltimate(2);
        double two = storyAfterUltimate(1);
        System.out.println("[story_three] with 3 points the counter reads " + three + " ; with only 2 it reads " + two);
        Assertions.assertEquals(0.0, three, 1e-9, "at three the whole counter is spent");
        Assertions.assertEquals(2.0, two, 1e-9, "at two nothing happens");
    }

    /** Seeds {@code seeded} points into the memosprite, then lets her ultimate add the one that raises the event. */
    private static double storyAfterUltimate(int seeded) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(cyrene),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        var sprite = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 26");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(cyrene));
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        var dragon = battle.summonMemosprite(cyrene);
        battle.processRequests();
        dragon.getResources().gain(STORY, seeded);
        cyrene = battle.characters.getFirst();
        var ult = cyrene.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: her ultimate");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ult, cyrene,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return dragon.getResources().has(STORY) ? dragon.getResources().value(STORY) : 0;
    }
}
