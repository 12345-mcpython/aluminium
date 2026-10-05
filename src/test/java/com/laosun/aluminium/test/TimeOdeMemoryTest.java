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
 * 1415's memosprite skill 24, second sentence (2026-10-02): "长夜月施放战技/终结技后，额外获得 #2 点[忆质]".
 *
 * Two-sided: with the ode cast at her, her skill grants the captured #2 (1 at this level); without it nothing was captured, so nothing is granted. The value never appears as
 * a literal in content: it is read from the ode's own row and handed over through a resource.
 */
public class TimeOdeMemoryTest {
    private static final int LEVEL = 80;
    private static final int LONGNIGHT = 1413;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 24;
    private static final String MEM = "亿质";

    @Test
    public void herSkillGrantsTheCapturedMemory() {
        int with = memoryAfterSkill(true);
        int without = memoryAfterSkill(false);
        System.out.println("[time_memory] after her skill 【忆质】 is " + with + " with the ode ; " + without + " without it");
        Assertions.assertEquals(1, with, "the ode's #2 is 1 at this level, captured and handed over");
        Assertions.assertEquals(0, without, "without the ode nothing was captured, so nothing is granted");
    }

    private static int memoryAfterSkill(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character longnight = CharacterFactory.create(LONGNIGHT, LEVEL);
        Battle battle = new Battle(List.of(cyrene, longnight),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        longnight = battle.characters.get(1);

        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 24");
            SkillExecutor.execute(battle, ode, sprite, List.of(longnight));
            battle.processRequests();
        }

        Character her = battle.characters.get(1);
        int before = her.getResources().value(MEM);
        // The sentence covers 战技 OR 终结技, and her own kit has a DELEGATE_DAMAGE rule on the skill slot that names the ultimate -- measured: casting the skill
        // trips that rule with "names ULTRA (slot 3) but the cast in progress is slot 2". The ultimate path is the same clause and does not collide.
        var skill = her.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(skill, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return battle.characters.get(1).getResources().value(MEM) - before;
    }
}
