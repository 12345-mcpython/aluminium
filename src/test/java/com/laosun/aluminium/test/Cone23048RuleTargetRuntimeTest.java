package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * RULE-level runtime judge for cone 23048 clause 3 (2026-09-30) -- and the name says what it does NOT cover.
 *
 * <p>Measured: with the event's target set to an ally, the rule raises THAT ally's skill-damage boost (0.54 at rank 1)
 * and leaves the wearer at 0. So it proves the rule's target binding.
 *
 * <p>Note: It does NOT prove that SkillExecutor hands the aimed unit to CAST_SETUP: this test passes the ally to
 * fireTriggers ITSELF, so it never executes that engine line. Measured proof: disarming the engine (aimed -> null)
 * leaves this test GREEN. An end-to-end judge must go through
 * SkillExecutor.execute(battle, &lt;a BPSKILL skill&gt;, wearer, List.of(ally)), and only a mutation OF THE ENGINE LINE can
 * show that it really does. Recorded rather than papered over.
 */
public class Cone23048RuleTargetRuntimeTest {
    private static final int LEVEL = 80;

    @Test
    public void theRuleBuffsTheEventTargetAndNotTheWearer() {
        Character wearer = CharacterFactory.create(1210, LEVEL, true, Weapon.build(23048, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0.0, wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "nothing yet");
        int fired = battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, ally, 0, 0, SkillCategory.BPSKILL);
        Assertions.assertEquals(1, fired, "the wearer's CAST_SETUP rule fires");
        Assertions.assertEquals(0.54, ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "the event target gets the buff -- rank 1's #4");
        Assertions.assertEquals(0.0, wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get(), 1e-9,
                "and not the wearer: that would be the wrong sentence");
        System.out.println("[23048-rule-runtime] fired=" + fired + " ally="
                + ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get());
    }
}
