package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.energy.EnergyGain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * M-11: crediting energy must never <b>take energy away</b>, and must never poison the bar with {@code NaN}.
 *
 * <p>Three ways the old {@code gainEnergy} did one or the other:
 * <ol>
 *   <li>the guard was {@code gain.amount() <= 0}, and {@code NaN <= 0} is <b>false</b>, so a NaN amount
 *       slipped through to {@code Math.min(…, NaN)} → {@code currentEnergy = NaN}. From then on
 *       {@code isEnergyFull()} ({@code NaN >= max} is false) could never be true again: the ultimate
 *       became unreachable for the rest of the battle, silently;</li>
 *   <li>a <b>negative efficiency</b> ({@code ENERGY_REGENERATION_RATE <= -1}) turned the "gain" into a
 *       subtraction;</li>
 *   <li>{@code maxEnergy - currentEnergy} is negative whenever the bar is already above its cap (the public
 *       {@code setCurrentEnergy} is a raw write), and {@code Math.min(negative, gain)} then <b>lowered</b>
 *       the energy by the over-cap amount.</li>
 * </ol>
 *
 * <p>The consequences differ, so they are separate tests: 1 and 2 are "the bar is now permanently wrong",
 * 3 is "a gain reduced the bar".
 */
public class EnergyGainSafetyTest {
    private static final double EPS = 1e-9;

    private static Character bar(double maxEnergy, double energy) {
        Character c = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        c.setMaxEnergy(maxEnergy);
        c.setCurrentEnergy(energy);
        return c;
    }

    /** A NaN amount must be refused, and the bar must still be usable afterwards. */
    @Test
    public void aNaNAmountDoesNotPoisonTheBar() {
        Character hero = bar(100, 95);

        Assertions.assertEquals(0, hero.gainEnergy(Double.NaN), EPS, "refused, so nothing is credited");
        Assertions.assertFalse(Double.isNaN(hero.getCurrentEnergy()), "the bar must not become NaN");

        // The point of the bug: after a poisoned bar the ultimate could never be cast again. So the test
        // asks the question the bug was really about -- can this bar still reach full?
        hero.gainEnergy(10);
        Assertions.assertTrue(hero.isEnergyFull(),
                "the bar still works: 95 + 10 reaches the cap, so the ultimate is still reachable");
    }

    /** The same poison through the efficiency attribute rather than the amount. */
    @Test
    public void aNaNEfficiencyDoesNotPoisonTheBar() {
        Character hero = bar(100, 95);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(Double.NaN));

        Assertions.assertEquals(0, hero.gainEnergy(10), EPS);
        Assertions.assertFalse(Double.isNaN(hero.getCurrentEnergy()));

        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0));
        hero.gainEnergy(10);
        Assertions.assertTrue(hero.isEnergyFull(), "and the bar recovers once the attribute is sane again");
    }

    /**
     * A negative amount must be refused <b>before</b> the efficiency multiplication, not after.
     *
     * <p>This case is why the amount guard cannot be dropped as "the product guard will catch it anyway":
     * a negative amount times a <b>negative efficiency is positive</b>, so {@code -10} with efficiency
     * {@code -2} would be credited as {@code +20}.
     *
     * <p>Found by mutating the amount guard away and noticing that the NaN case stayed green — the NaN
     * amount happens to be caught downstream by the product guard, but this combination is not. Without
     * this test the amount guard would have looked like dead code.
     */
    @Test
    public void aNegativeAmountIsRefusedEvenWhenTheEfficiencyIsNegative() {
        Character hero = bar(100, 50);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(-3));   // efficiency -2

        Assertions.assertEquals(0, hero.gainEnergy(-10), EPS,
                "-10 × -2 is +20, so refusing on the product alone would have CREDITED energy");
        Assertions.assertEquals(50, hero.getCurrentEnergy(), EPS);
    }

    /** {@code ENERGY_REGENERATION_RATE = -3} means efficiency -2; a gain must not become a subtraction. */
    @Test
    public void aGainNeverTakesEnergyAway() {
        Character hero = bar(100, 50);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(-3));

        Assertions.assertEquals(0, hero.gainEnergy(10), EPS,
                "a negative efficiency produces a negative gain, which is refused rather than applied");
        Assertions.assertEquals(50, hero.getCurrentEnergy(), EPS, "so the bar is exactly where it was");
    }

    /**
     * An over-cap bar is not "gained down" to the cap.
     *
     * <p>{@code Math.min(-50, 10)} is -50, so a gain used to drop the bar by the whole over-cap amount.
     * Capping an over-cap value is a separate operation and {@code setCurrentEnergy} is where that state
     * came from; a <i>gain</i> that reduces energy is indistinguishable from a bug, so it adds 0 here.
     */
    @Test
    public void aGainDoesNotReduceAnOverCapBar() {
        Character hero = bar(100, 150);

        Assertions.assertEquals(0, hero.gainEnergy(10), EPS);
        Assertions.assertEquals(150, hero.getCurrentEnergy(), EPS, "unchanged, not pulled down to the cap");
    }

    /** The ordinary paths must be untouched: proportional gain, the cap, and the efficiency bypass. */
    @Test
    public void normalGainsStillWork() {
        Character hero = bar(100, 50);
        Assertions.assertEquals(30, hero.gainEnergy(30), EPS);
        Assertions.assertEquals(80, hero.getCurrentEnergy(), EPS);

        Assertions.assertEquals(20, hero.gainEnergy(30), EPS, "truncated by the cap");
        Assertions.assertEquals(100, hero.getCurrentEnergy(), EPS);
        Assertions.assertTrue(hero.isEnergyFull());

        Assertions.assertEquals(0, hero.gainEnergy(30), EPS, "a full bar credits nothing");

        Character boosted = bar(100, 0);
        boosted.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
        Assertions.assertEquals(15, boosted.gainEnergy(10), EPS, "efficiency still multiplies (10 × 1.5)");
        Assertions.assertEquals(10, boosted.gainEnergy(EnergyGain.fixed(10)), EPS,
                "and a fixed grant still bypasses it");
    }
}
