package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `percent_from_cast_param`: a magnitude that is a skill parameter TIMES an attribute (2026-10-02).
 *
 * <p>Reader: 1415's memosprite skill 10 「献予「创世」之诗」 -- 「使开拓者•记忆的攻击力提高，提高数值等同于<b>德谬歌生命上限的 #1%</b>」. The magnitude
 * is a product: `#1` comes from the casting skill and runs with its level; the max HP comes from the field. One `scale` names one factor, so
 * without this the sentence could only be written by freezing a level of `#1`.
 *
 * <p>⚠ Three lessons from earlier rounds are baked in here, each of which cost a round:
 * <ul>
 *   <li>the expectation is read from the ROW OF THE CASTER'S CURRENT LEVEL (this skill sits at level 10, and `70` is in every row -- which is
 *       how a wrong row stayed invisible for a whole round);</li>
 *   <li>the test helper keys on the FIELD NAME (`percentFromCastParam`), not on the JSON spelling;</li>
 *   <li>the unboxed `getAmount()` this field first crashed on is fixed, and the reader below is what would notice if it came back.</li>
 * </ul>
 */
public class PercentFromCastParamScaleTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;

    /** The share is the param; the attribute is the memosprite's. The product is what lands. */
    @Test
    public void theShareIsTheCastParamAndTheSubjectIsTheMemosprite() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Skill ode = demiurge.skillAt(ODE_OF_ROMANCE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 14");

        var used = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        Assumptions.assumeTrue(used.get(0) > 0, "the shipped row must have a positive first parameter");

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "summon_attr:HEALTH");
        TriggerSpecs.set(effect, "percentFromCastParam", 0);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        cyrene.setTriggerTable(new TriggerTable(CYRENE, List.of(
                TriggerSpecs.rule("CAST_SETUP", List.of("actor is_summon"), effect))));

        double before = cyrene.getAttribute(AttributeType.ATTACK).get();
        SkillExecutor.execute(battle, ode, demiurge, List.of(ally));
        battle.processRequests();
        double gained = cyrene.getAttribute(AttributeType.ATTACK).get() - before;

        double expected = used.get(0) * demiurge.getMaxHp();
        System.out.println("[percent_param] the row used = " + used + " ; the memosprite Max HP = " + demiurge.getMaxHp()
                + " ; expected = " + expected + " ; the gain = " + gained);

        Assertions.assertEquals(expected, gained, Math.abs(expected) * 1e-6,
                "「提高数值等同于德谬歌生命上限的 #1%」-- the param TIMES the attribute");
    }

    /** Stating both shares is refused at load time rather than letting one silently win. */
    @Test
    public void bothSharesAreRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "summon_attr:HEALTH");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "percentFromCastParam", 0);
        TriggerSpecs.set(effect, "target", "self");

        boolean threw = false;
        try {
            Character owner = CharacterFactory.create(CYRENE, LEVEL);
            owner.setTriggerTable(new TriggerTable(CYRENE, List.of(TriggerSpecs.rule("WAVE_START", List.of(), effect))));
        } catch (IllegalArgumentException | IllegalStateException expected) {
            threw = true;
            String message = String.valueOf(expected.getMessage());
            System.out.println("[percent_param] both -> " + expected.getClass().getSimpleName() + ": "
                    + message.substring(0, Math.min(96, message.length())));
        }
        Assertions.assertTrue(threw, "a share from two places is not a number the engine will guess at");
    }
}
