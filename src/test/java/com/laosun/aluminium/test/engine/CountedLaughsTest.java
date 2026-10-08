package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code counted_laughs}: 「本次造成的欢愉伤害**固定计入 99 点**【好活当赏】」 - ONE instance states the count it is folded with.
 *
 * <p>⚠ Why the field exists at all: the Elation factor normally comes from the battle's snapshot of 【好活当赏】 (the party
 * pool frozen when the state arrived), and several sentences say the opposite - 「固定计入 N 点」. The shape has 12 hits across
 * 10 files (1506's technique box with 99, 1513's cast with 20, 8009/8010's triggered Elation skill with 20).
 *
 * <p>What this file pins, and the load-bearing one is the middle case:
 * <ul>
 *   <li>a declared count is the WHOLE factor - it is not added to the pool's snapshot;</li>
 *   <li>⭐ and it IGNORES the pool: the same declared count gives the same damage with the pool at 0 and at 300. Without
 *       this the field could quietly be "the pool value under a new name", which is exactly what the sentence denies;</li>
 *   <li>while an instance that states nothing keeps reading the snapshot (so the old route is untouched);</li>
 *   <li>and a negative count is refused loudly rather than folded as zero.</li>
 * </ul>
 */
public class CountedLaughsTest {
    private static final int MINE = 1506;
    private static final int ALLY = 1204;
    private static final String LAUGH = "笑点";
    private static final double EPS = 1e-9;

    /** A declared 99 is the whole factor: the ratio to the same instance without it is exactly 1 + 99*5/(99+240). */
    @Test
    public void aDeclaredCountIsTheWholeFactor() {
        double plain = damage(null, 0);
        double declared = damage(99.0, 0);
        double expected = 1 + 99 * 5.0 / (99 + 240);
        System.out.println("[counted_laughs] no field = " + plain + " ; counted 99 = " + declared
                + " ; ratio = " + (declared / plain) + " ; expected factor = " + expected);
        Assertions.assertEquals(expected, declared / plain, 1e-9,
                "「固定计入 99 点」 is the factor itself, and everything else in the instance cancels in the ratio");
    }

    /** ⭐ The pool is irrelevant to a declared count - the case that stops the field from being the snapshot by another name. */
    @Test
    public void aDeclaredCountIgnoresThePool() {
        double empty = damage(99.0, 0);
        double full = damage(99.0, 300);
        System.out.println("[counted_laughs] counted 99 with a 0 pool = " + empty + " ; with a 300 pool = " + full);
        Assertions.assertEquals(empty, full, EPS,
                "the sentence says 固定计入 - the pool's value must not reach this instance at all");
    }

    /** And an instance that states nothing keeps the snapshot route, so nothing shipped changes. */
    @Test
    public void withoutTheFieldTheSnapshotStillDrives() {
        double empty = damage(null, 0);
        double full = damage(null, 300);
        System.out.println("[counted_laughs] no field with a 0 pool = " + empty + " ; with a 300 pool = " + full);
        Assertions.assertTrue(full > empty + 1,
                "an instance with no declared count still follows the counted snapshot (" + full + " vs " + empty + ")");
    }

    /** A negative count is a rule error, not a zero. */
    @Test
    public void aNegativeCountIsRefused() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class, () -> damage(-1.0, 0));
        System.out.println("[counted_laughs] negative refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("cannot be negative"), "the message says what is wrong");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The settled damage of one rule-driven Elation instance.
     *
     * <p>Fixture shape copied from the shipped {@code ElationLaughMultiplierTest} rather than invented: a hand-made
     * {@code DAMAGE} effect on {@code ULT_CAST}, an {@code elation_base} scale, and the reading taken off the enemy's HP. The
     * target is a MILLION-HP dummy with no toughness bar, so the layer's 10% reduction and the enemy's remaining HP cannot
     * distort a ratio.
     *
     * @param counted    the declared laugh count, or {@code null} for "no field"
     * @param poolLaughs what the party's 笑点 pool holds when 【好活当赏】 is granted
     */
    private static double damage(Double counted, int poolLaughs) {
        Character mine = CharacterFactory.create(MINE, 80);
        Enemy enemy = Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
        Battle battle = new Battle(List.of(mine, CharacterFactory.create(ALLY, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.partyResource(LAUGH).gain(poolLaughs);
        battle.noteElationGift(mine, 2);        // freeze the pool into the snapshot, the way the state does

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        TriggerSpecs.set(instance, "damageType", "ELATION");
        if (counted != null) {
            TriggerSpecs.set(instance, "countedLaughs", counted);
        }
        mine.setTriggerTable(new TriggerTable(MINE, List.of(
                TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance))));

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, mine, enemy, 0, 0);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
