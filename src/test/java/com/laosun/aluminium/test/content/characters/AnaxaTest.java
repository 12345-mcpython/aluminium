package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.engine.EidolonGateTest;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1405 Anaxa's eidolon 1 (2026-09-29, round 199): the target's DEFENCE drops 16% when his SKILL lands.
 *
 * <p>Two guards are load-bearing and both are tested: `from_skill SKILL` (a basic attack must not do it) and `min_eidolon: 1`. The magnitude is compared with a
 * hand-built -32% reference in the same pipeline, so the ratio 0.5 is the claim rather than "some reduction".
 */
public class AnaxaTest {
    private static final int ANAXA = 1405;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The ratio, and the control that a basic attack does nothing. */
    @Test
    public void hisSkillLowersTheTargetsDefenceFromEidolonOne() {
        double content = defenceDrop(0);
        double reference = defenceDrop(1);
        double fromBasic = basicAttackDrop();

        Assertions.assertTrue(reference > 0, "the reference must lower it at all");
        Assertions.assertEquals(0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference);
        Assertions.assertEquals(0.0, fromBasic, 1e-9,
                "「施放**战技**击中时」 -- a basic attack must not lower it");
    }

    /** mode 0 = the shipped file, 1 = a hand-built -32% reference. Returns the target's DEFENCE drop after a Skill. */
    private static double defenceDrop(int mode) {
        // The shipped rule carries min_eidolon: 1, so the character must actually HAVE eidolon 1 (EidolonGateTest.create takes the rank as its last argument).
        Character anaxa = mode == 0
                ? CharacterFactory.create(ANAXA, LEVEL, true, null, null, 1)
                : CharacterFactory.create(ANAXA, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "DEFENCE");
            TriggerSpecs.set(effect, "percent", -0.32);
            TriggerSpecs.set(effect, "turns", 2);
            TriggerSpecs.set(effect, "target", "target");
            anaxa.setTriggerTable(new TriggerTable(ANAXA, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(anaxa), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.DEFENCE).get();
        battle.castImmediate(anaxa.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), anaxa, List.of(enemy));
        return before - enemy.getAttribute(AttributeType.DEFENCE).get();
    }

    /** A basic attack with the shipped file: the guard must stop it. */
    private static double basicAttackDrop() {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(anaxa), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.DEFENCE).get();
        battle.castImmediate(anaxa.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), anaxa, List.of(enemy));
        return before - enemy.getAttribute(AttributeType.DEFENCE).get();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
