package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `skill_param:<SKILLTYPE>:<index>`: a parameter of one of the RULE OWNER's own skills (2026-10-02).
 *
 * <p>Reader: 1403 缇宝's ultimate, whose zone rider deals 「等同于缇宝 #3% 生命上限」 damage on somebody else's attack. `cast_skill_param:` reads the skill that
 * PRODUCED the event -- the attack -- which is the wrong one, and a literal `percent` would freeze one level of a value that runs 0.06 -> 0.126.
 *
 * <p>⭐ The expected number is read the way the engine reads it: `skill.getData().getSkills()` at `attacker.skillLevel(skill)`, never row 1 by hand. And
 * the second assertion pins the INDEX: a neighbouring member of the same row is a different number, so a wrong index cannot pass.
 */
public class SkillParamTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int INDEX = 2;          // #3 of his ultimate

    @Test
    public void theOwnersUltimateParameterIsReadAtItsOwnLevel() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(tribbie, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();

        Skill ultra = tribbie.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ultra, "precondition: 1403 has an ULTRA skill");
        var used = ultra.getData().getSkills().get(tribbie.skillLevel(ultra) - 1);
        double expected = 2.0 * used.get(INDEX);
        double neighbour = 2.0 * used.get(INDEX - 1);

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "skill_param:ULTRA:" + INDEX);
        TriggerSpecs.set(effect, "percent", 2.0);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        tribbie.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), effect))));

        double before = tribbie.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START);
        battle.processRequests();
        double gained = tribbie.getAttribute(AttributeType.ATTACK).get() - before;

        System.out.println("[skill_param] his ULTRA row = " + used + " ; expected " + expected
                + " (the neighbour is " + neighbour + ") ; the gain = " + gained);

        Assertions.assertEquals(expected, gained, Math.abs(expected) * 1e-6,
                "\u300c\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650\u300d-- #3 is a parameter of HIS ultimate, at its own level");
        Assertions.assertNotEquals(neighbour, gained, Math.abs(expected) * 1e-6,
                "and the index is load-bearing: the neighbouring member is a different number");
    }
}
