package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 800："使指定我方单体行动提前 100% 并附上[迷迷的声援]，持续 3 回合".
 *
 * <p>FILE-DRIVEN. The cheer lands on the ally the skill was aimed at, which is also the unit the advance moves.
 */
public class MimiCheerTest {
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CHEER = "迷迷的声援";

    /** The skill lays the cheer on its target. */
    @Test
    public void theSkillLaysTheCheerOnItsTarget() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(ally.getBuffManager().hasState(CHEER), "precondition: no cheer yet");
        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: he has a skill");
        SkillExecutor.execute(battle, skill, owner, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ally.getBuffManager().hasState(CHEER),
                "「附上【迷迷的声援】」");
    }
}
