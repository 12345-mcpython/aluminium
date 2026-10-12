package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * {@code split}: the stated magnitude is the <b>total</b>, divided evenly among the units the effect reaches.
 *
 * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「造成 90% 的虚数属性欢愉伤害，<b>由敌方全体均分</b>」. Every other magnitude in
 * this DSL is per victim, so the per-victim reading of that sentence multiplies it by the enemy count - and the count is
 * exactly what nobody can see in the JSON.
 *
 * <p>⚠ The judge is a comparison between two fixtures that differ ONLY in how many identical enemies there are: one
 * enemy takes the whole share, two take half each, and the TOTAL is the same. The per-victim reading fails it by a factor
 * of two, which is the whole reason the field exists.
 */
public class SplitDamageTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;

    /**
     * One enemy takes the stated share whole; two take half each; the total does not move with the enemy count.
     */
    @Test
    public void theStatedShareIsATotalSplitAmongTheVictims() {
        double alone = perEnemyLoss(1);
        double eachOfTwo = perEnemyLoss(2);
        double totalWithOne = totalLoss(1);
        double totalWithTwo = totalLoss(2);
        System.out.println("[split] one enemy: " + alone + " (total " + totalWithOne + "); two enemies: " + eachOfTwo
                + " each (total " + totalWithTwo + ")");
        Assertions.assertTrue(alone > 0, "the fixture must really settle damage");
        Assertions.assertEquals(alone, 2 * eachOfTwo, 1e-6,
                "the share is the TOTAL: N victims take 1/N of it each, so two take exactly half of one");
        Assertions.assertEquals(totalWithOne, totalWithTwo, 1e-6,
                "and the TOTAL is what the sentence fixes - it must not grow with the enemy count");
    }

    /**
     * Without the field, every victim takes the full share - which is what makes the field meaningful.
     */
    @Test
    public void withoutTheFieldEachVictimTakesTheWholeShare() {
        double split = perEnemyLoss(2);
        double unsplit = perEnemyLoss(2, false);
        System.out.println("[split] two enemies: split " + split + " each, unsplit " + unsplit + " each");
        Assertions.assertEquals(2 * split, unsplit, 1e-6,
                "a rule without `split` settles the stated magnitude on EACH victim, as it always did");
    }

    /**
     * A corpse does not absorb a share: the divisor counts only the victims the damage can really be dealt to.
     *
     * <p>⚠ This is the silent hole the divisor's own code guards: counting the resolved list would divide the total by
     * two while only one enemy takes anything, so half of the stated damage would vanish and nothing would report it.
     * The corpse's own share is not redistributed either - the living one takes the whole stated total.
     */
    @Test
    public void aCorpseDoesNotAbsorbAShare() {
        Battle battle = battle(2, true);
        var roster = battle.enemyUnits();
        roster.get(1).perish();                       // one of the two is already down
        double before = roster.get(0).getCurrentHp();
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
        battle.processRequests();
        double alive = before - roster.get(0).getCurrentHp();
        System.out.println("[split] with one of two enemies dead, the living one took " + alive
                + " (one enemy alone takes " + perEnemyLoss(1) + ")");
        Assertions.assertEquals(perEnemyLoss(1), alive, 1e-6,
                "the divisor is the LIVING victims: a corpse must not take a share that then never lands");
    }

    /**
     * The two combinations that would read two ways, or say nothing, are refused at load.
     */
    @Test
    public void theCombinationsThatSayNothingAreRefused() {
        EffectSpec both = box();
        TriggerSpecs.set(both, "perTarget", true);
        assertRefused(both, "per_target");

        EffectSpec singleTarget = box();
        TriggerSpecs.set(singleTarget, "target", "highest_hp_enemy");
        assertRefused(singleTarget, "all_enemies");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static void assertRefused(EffectSpec effect, String names) {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(WOLF, List.of(TriggerSpecs.rule("TURN_START", List.of(), effect))));
        System.out.println("[split] refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains(names),
                "the message must name what is wrong, and it says: " + error.getMessage());
    }

    private static double perEnemyLoss(int enemies) {
        return perEnemyLoss(enemies, true);
    }

    /**
     * One firing of the hand-built box against {@code enemies} identical enemies, read per enemy.
     */
    private static double perEnemyLoss(int enemies, boolean split) {
        Battle battle = battle(enemies, split);
        var roster = battle.enemyUnits();
        double before = roster.getFirst().getCurrentHp();
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
        battle.processRequests();
        return before - roster.getFirst().getCurrentHp();
    }

    private static double totalLoss(int enemies) {
        Battle battle = battle(enemies, true);
        double before = battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).sum();
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
        battle.processRequests();
        return before - battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).sum();
    }

    private static Battle battle(int enemies, boolean split) {
        List<Enemy> roster = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            roster.add(BattleFixture.monster());
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)), roster, new NoCrit());
        battle.startBattle();
        battle.processRequests();
        battle.characters.getFirst().setTriggerTable(new TriggerTable(WOLF, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), box(split)))));
        return battle;
    }

    /**
     * 「造成 90% 的虚数属性欢愉伤害，由敌方全体均分」: the box's own damage, split or not.
     */
    private static EffectSpec box() {
        return box(true);
    }

    private static EffectSpec box(boolean split) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.9);
        TriggerSpecs.set(effect, "element", "Imaginary");
        TriggerSpecs.set(effect, "damageType", "ELATION");
        TriggerSpecs.set(effect, "target", "all_enemies");
        if (split) {
            TriggerSpecs.set(effect, "split", true);
        }
        return effect;
    }

    /**
     * A source that never crits, so the two fixtures are compared on the same numbers.
     *
     * <p>⚠ Not cosmetic: a crit moves one victim's settled value by the crit multiplier, and the comparison above is
     * between different NUMBERS of instances - so a lucky roll here would be read as the split being wrong.
     */
    private static final class NoCrit extends Random {
        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
