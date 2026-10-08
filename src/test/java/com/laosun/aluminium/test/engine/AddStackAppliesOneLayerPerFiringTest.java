package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * One firing of {@code ADD_STACK} adds exactly ONE layer.
 *
 * <p>⚠⚠ <b>Written because a refactor broke exactly this and nothing caught it.</b> The cleanup of
 * {@code TriggerInterpreter.addStack} rewrote the stack-construction chain into a nested call and added its own
 * {@code target.getBuffManager().addBuff(stack)} without removing the original line, so every firing applied two layers.
 * The full suite went red with 68 failures - "expected 1 got 2", "expected 2 got 4", and 1 → 3 where a layer already
 * existed - across Ashveil, Jade, Jingliu, BlackSwan, Boothill, Hanya, Herta and Jiaoqiu.
 *
 * <p>What made it quiet is worth stating: every one of those 68 was a CONTENT judge, each reading one character's stack
 * count, and the engine had no case of its own for "a single firing adds a single layer". The content sweep
 * ({@code RecomputeNotAccumulateTest}) guards a different property - a re-firing permanent modifier must take itself off
 * first - so this file fills the gap at the level where the bug actually lived.
 *
 * <p>It reads the count through {@code BuffManager.stacksOf}, i.e. the same accessor the content judges use, so it cannot
 * pass by measuring something else.
 */
public class AddStackAppliesOneLayerPerFiringTest {
    private static final int HOLDER = 1506;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String LAYER = "测试层";

    /** Firing once is ONE layer, firing twice is two, and the declared cap is what stops it - not luck. */
    @Test
    public void oneFiringIsOneLayerAndTheCapIsTheOnlyCeiling() {
        Battle battle = battle(3);
        Character holder = battle.characters.getFirst();
        Assertions.assertEquals(0, holder.getBuffManager().stacksOf(LAYER), "precondition: no layers yet");

        fire(battle, holder);
        int one = holder.getBuffManager().stacksOf(LAYER);
        fire(battle, holder);
        int two = holder.getBuffManager().stacksOf(LAYER);
        fire(battle, holder);
        int three = holder.getBuffManager().stacksOf(LAYER);
        fire(battle, holder);
        int capped = holder.getBuffManager().stacksOf(LAYER);
        System.out.println("[add_stack] one firing -> " + one + " ; two -> " + two + " ; three -> " + three
                + " ; a fourth against cap 3 -> " + capped);

        Assertions.assertEquals(1, one,
                "ONE firing adds ONE layer - the duplicate addBuff this guards against reads 2 here, which is how 68 "
                        + "content judges went red at once");
        Assertions.assertEquals(2, two, "and a second firing makes two");
        Assertions.assertEquals(3, three, "a third makes three");
        Assertions.assertEquals(3, capped, "while the declared max_stacks is the ceiling (a fourth changes nothing)");
    }

    /** A batch really is a batch: {@code amount: 3} is three layers in one firing, so the guard is not "always one". */
    @Test
    public void aStatedAmountIsStillTheBatchItSays() {
        Battle battle = battle(10, 3);
        Character holder = battle.characters.getFirst();
        fire(battle, holder);
        int after = holder.getBuffManager().stacksOf(LAYER);
        System.out.println("[add_stack] amount=3 in one firing -> " + after);
        Assertions.assertEquals(3, after, "one firing of amount: 3 is three layers - the fix removed a duplicate call, "
                + "not the stated batch");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static void fire(Battle battle, Character holder) {
        battle.fireTriggers(TriggerEvent.TURN_START, holder, holder, 0, 0);
        battle.processRequests();
    }

    private static Battle battle(int cap) {
        return battle(cap, 1);
    }

    private static Battle battle(int cap, int amount) {
        Character holder = CharacterFactory.create(HOLDER, 80);
        EffectSpec stack = new EffectSpec();
        TriggerSpecs.set(stack, "op", "ADD_STACK");
        TriggerSpecs.set(stack, "buff", LAYER);
        TriggerSpecs.set(stack, "amount", (double) amount);
        TriggerSpecs.set(stack, "maxStacks", (double) cap);
        TriggerSpecs.set(stack, "permanent", true);
        TriggerSpecs.set(stack, "target", "self");
        holder.setTriggerTable(new TriggerTable(HOLDER, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), stack))));
        Battle battle = new Battle(List.of(holder, CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
