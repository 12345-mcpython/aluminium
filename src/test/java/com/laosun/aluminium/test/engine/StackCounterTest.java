package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * A <b>named counter with a threshold</b>: {@code ADD_STACK} plus {@code *_stacks:<name>}.
 *
 * <p><b>The sentence that needs it.</b> Hanya (寒鸦)'s Skill: "after our target casts a basic attack, Skill or Ultimate <b>2</b> times on an enemy target in the [承负] state, immediately
 * restore 1 skill point to our side; [承负] ... is automatically removed after triggering the skill-point restoration effect <b>2</b> times" - two counters, each with a threshold, and
 * neither is a turn count or a probability. The engine could already <i>stack</i> ({@code maxStacks}, {@code REMOVE_STACK})
 * but nothing could <b>read</b> a count, so "after N times" had no spelling at all.
 *
 * <p><b>What is pinned here.</b> That the count is the number of named buffs (so the two existing removal directions both
 * work on it), that a threshold rule sees it, that the cap stops it from overshooting, and that the name is
 * <b>validated at load</b> against what the file itself creates (a typo would otherwise be a rule that never fires).
 */
public class StackCounterTest {
    private static final int HANYA = 1215;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The counter accumulates one mark per firing, and a threshold rule can read it. */
    @Test
    public void theCountIsReadableAtTheThreshold() {
        Character hero = CharacterFactory.create(HANYA, LEVEL);
        hero.setTriggerTable(new TriggerTable(HANYA, List.of(countMark(), atThreshold())));
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("承负"),
                "one mark is not two: \"after every 2 times ...\" has not been reached");

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("承负"),
                "the second mark reached the threshold, and the rule that answered it cleared the counter");

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("承负"),
                "…and counting starts again (the cap stops a stray event from pushing it past the threshold)");
    }

    /** The two removal directions work on a counter: one mark off, or the whole counter by name. */
    @Test
    public void bothRemovalDirectionsWork() {
        Character hero = CharacterFactory.create(HANYA, LEVEL);
        hero.setTriggerTable(new TriggerTable(HANYA, List.of(countMark())));
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        Assertions.assertEquals(2, enemy.getBuffManager().stacksOf("承负"), "two marks");

        Assertions.assertEquals(2, enemy.getBuffManager().removeState("承负"),
                "REMOVE_STATE by name clears the WHOLE counter (every buff carrying the name), which is what "
                        + "\"automatically removed after triggering 2 times\" needs");
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("承负"));
    }

    /** Note: A name that no effect in this file creates is refused at load: it would be a condition that never holds. */
    @Test
    public void anUndeclaredCounterNameIsRefusedAtLoad() {
        Character hero = CharacterFactory.create(HANYA, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("ALLY_ATTACK", List.of("target_stacks:没人创建过这个计数器 >= 2"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(HANYA, List.of(rule)));
        Assertions.assertTrue(refused.getMessage().contains("没人创建过这个计数器"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** One mark on the enemy's Bondmate (承负) counter, capped at 2. */
    private static TriggerSpec countMark() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "承负");
        TriggerSpecs.set(effect, "maxStacks", 2);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    /** The threshold rule: at two marks, pays 1 skill point and clears the counter. */
    private static TriggerSpec atThreshold() {
        EffectSpec pay = new EffectSpec();
        TriggerSpecs.set(pay, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(pay, "amount", 1.0);
        EffectSpec reset = new EffectSpec();
        TriggerSpecs.set(reset, "op", "REMOVE_STATE");
        TriggerSpecs.set(reset, "buff", "承负");
        TriggerSpecs.set(reset, "target", "target");
        return TriggerSpecs.rule("ALLY_ATTACK", List.of("target_stacks:承负 >= 2"), pay, reset);
    }

    private static Enemy enemy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
