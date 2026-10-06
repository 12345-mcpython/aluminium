package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1412："持有[军功]的角色造成伤害时无视目标 16% 的防御力".
 *
 * <p>FILE-DRIVEN: her skill is what grants [军功], so after it the ALLY should carry the 16% defence ignore -- while she
 * herself should not, which is the "false side" this judge also asserts.
 */
public class MilitaryMeritDefenceIgnoreTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    /** The merit holder ignores 16% DEF; the caster does not. */
    @Test
    public void theMeritHolderIgnoresSixteenPercent() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, owner, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(0.16, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "\"a character holding Military Merit (【军功】) ignores 16% DEF\" (持有【军功】的角色无视 16% 防御)");
        Assertions.assertEquals(0.0, owner.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and the caster keeps none of it (the false side)");
    }
}
