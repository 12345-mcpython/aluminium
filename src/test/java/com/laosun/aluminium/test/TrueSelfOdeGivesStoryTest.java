package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * A summon can DECLARE a resource, so a rule can grant one to it (2026-10-02).
 *
 * Slot 26's "昔涟施放终结技后…使德谬歌获得1点[故事]" is the reader. Before this capability the loader accepted `GAIN_RESOURCE{target: "summon"}` and the runtime silently granted nothing,
 * because a resource has to be declared where the unit can see it and a summon had no declaration site (`MemospriteSpec` now carries `resources`; `SummonFactory` registers them).
 *
 * Note: The same sentence's other trigger, "…或德谬歌被召唤时", did NOT move the counter in this judge -- registered, not claimed.
 */
public class TrueSelfOdeGivesStoryTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 26;
    private static final String STORY = "故事";

    @Test
    public void aRuleCanPutAResourceOnTheMemosprite() {
        double with = story(true);
        double without = story(false);
        System.out.println("[true_self] the memosprite's 【故事】 reads " + with + " with the ode and " + without
                + " without it");
        Assertions.assertEquals(1.0, with, 1e-9, "her ultimate gives the memosprite one point");
        Assertions.assertEquals(0.0, without, 1e-9, "and without the ode there is no counter at all");
    }

    private static double story(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(cyrene),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 26");
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(cyrene));
            battle.processRequests();
            cyrene = battle.characters.getFirst();
        }
        var dragon = battle.summonMemosprite(cyrene);
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        var ult = cyrene.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: her ultimate");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ult, cyrene,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return dragon.getResources().has(STORY) ? dragon.getResources().value(STORY) : 0;
    }
}
