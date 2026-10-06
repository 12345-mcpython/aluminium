package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408："解除自身所有负面效果，随后造成…".
 *
 * <p>SAME SCENE, ONE VARIABLE, and the DOT is applied AFTER the transformation: item 41 made the transformed form immune to
 * CONTROLS, so a control could not be used here even though it is a debuff -- a Thunder DOT is used instead, and the immunity
 * does not touch it.
 */
public class TransformationDispelsTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final String DOT = "触电";

    /** Transformed: her own cast strips the debuff. */
    @Test
    public void theTransformedFormStripsItsDebuffs() {
        Assertions.assertFalse(debuffedAfterCast(true),
                "「解除自身所有负面效果」");
    }

    /** Note: Untransformed: the same cast leaves it there. */
    @Test
    public void withoutTheTransformationNothingIsStripped() {
        Assertions.assertTrue(debuffedAfterCast(false),
                "「卡厄斯兰那…解除」-- the dispel belongs to the transformation");
    }

    // ==================================================================

    private static boolean debuffedAfterCast(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), dot()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }

        // The debuff lands NOW -- after the transformation (see the class comment), and before her own cast.
        Skill theirs = applier.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: the applier has a skill");
        SkillExecutor.execute(battle, theirs, applier, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(DOT),
                "precondition: the DOT landed on her (state name " + DOT + ")");

        Skill hers = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(hers, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, hers, owner, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return owner.getBuffManager().hasState(DOT);
    }

    /** A Thunder DOT on the other ally. */
    private static EffectSpec dot() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_DOT");
        TriggerSpecs.set(e, "element", "Thunder");
        // Note: `baseChance`, the JAVA field name: `TriggerSpecs.set` uses reflection, so the JSON key (`base_chance`) is not what it
        // wants -- it failed loudly with "cannot set base_chance on class EffectSpec".
        TriggerSpecs.set(e, "baseChance", 1.0);
        TriggerSpecs.set(e, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(e, "percent", 2.9);
        TriggerSpecs.set(e, "turns", 2);
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }
}
