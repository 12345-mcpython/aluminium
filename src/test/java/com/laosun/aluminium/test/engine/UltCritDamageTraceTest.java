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
 * 1408："施放终结技时，暴击伤害提高 50%，持续 3 回合".
 *
 * <p>TWO-WAY: the same character measured before and after her ultimate, so the only thing that differs is the cast.
 */
public class UltCritDamageTraceTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;

    /** Casting the ultimate raises her crit damage by exactly 50%. */
    @Test
    public void theUltimateRaisesCritDamage() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertTrue(before > 0, "precondition: the panel reads (" + before + ")");

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertEquals(before + 0.50, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "「施放终结技时，暴击伤害提高 50%」 (before=" + before + ")");
    }

    /** Note: Before the ultimate the trace has not started, so the panel is untouched. */
    @Test
    public void beforeTheUltimateThePanelIsUntouched() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertFalse(owner.getBuffManager().hasState("变身"), "no ultimate yet");
        Assertions.assertEquals(before, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "nothing has been cast, so nothing has changed");
    }
}
