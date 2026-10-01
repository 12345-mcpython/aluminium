package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
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
 * Elation slice 1b (2026-09-30): {@code ELATION_DAMAGE_BOOST} is folded into the base of an Elation instance, because that damage
 * type is deliberately not boostable (so `addBoost` would be silently ignored by the zone\u2019s own `applies(type)` gate).
 *
 * <p>\u2b50 Judged on a SHIPPED character\u2019s REAL Elation skill (1501, slot 20): the enemy\u2019s health is what moves, and the boost
 * arrives through CONTENT (a hand-added modifier does not stick on this attribute -- measured twice).
 */
public class ElationBoostTest {
    private static final int WEARER = 1501;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(double boost) {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        if (boost != 0) {
            EffectSpec spec = new EffectSpec();
            TriggerSpecs.set(spec, "op", "MODIFY_ATTR");
            TriggerSpecs.set(spec, "attribute", "ELATION_DAMAGE_BOOST");
            TriggerSpecs.set(spec, "percent", boost);
            TriggerSpecs.set(spec, "permanent", true);
            TriggerSpecs.set(spec, "target", "self");
            wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                    TriggerSpecs.rule("BATTLE_START", List.of(), spec))));
            battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0);
        }
    }

    /** Casts a real skill of the kit and reports the health the enemy lost. */
    private double castAndMeasure(SkillType type) {
        double before = enemy.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(type), wearer, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    @Test
    public void theElationBoostLiftsTheElationSkillOnly() {
        // \u2605 The character carries an Elation boost of its OWN in the data (measured: 0.28), and the fold puts BOTH into the
        // base -- so the expected ratio is (1 + own + added) / (1 + own), not (1 + added). Reading `own` here keeps the judge
        // honest about that: with own = 0.28 the measured 1.390625 is exactly 1.78 / 1.28.
        build(0);
        double own = wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get();
        double elationPlain = castAndMeasure(SkillType.ELATION_SKILL);
        double normalPlain = castAndMeasure(SkillType.COMMON);
        build(0.5);
        double withBoost = wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get();
        double elationBoosted = castAndMeasure(SkillType.ELATION_SKILL);
        double normalBoosted = castAndMeasure(SkillType.COMMON);
        System.out.println("[boost] own=" + own + " with the content rule=" + withBoost
                + " ; the Elation skill took " + elationPlain + " -> " + elationBoosted + " (x"
                + (elationBoosted / elationPlain) + ", expected " + ((1 + withBoost) / (1 + own)) + ") ; the basic attack took "
                + normalPlain + " -> " + normalBoosted + " (x" + (normalBoosted / normalPlain) + ")");
        Assertions.assertTrue(elationPlain > 0, "the real Elation skill settles damage at all");
        Assertions.assertEquals((1 + withBoost) / (1 + own), elationBoosted / elationPlain, 0.02,
                "its own boost is folded into the base");
        Assertions.assertEquals(1.0, normalBoosted / normalPlain, 0.02, "and an ordinary skill is untouched (false case)");
    }
}
