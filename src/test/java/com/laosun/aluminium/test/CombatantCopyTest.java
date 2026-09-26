package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.BoostDamageBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * H-6: copying a combatant must not leave the copy <b>sharing its attribute sheet</b> with the original.
 *
 * <p>The bug this pins: {@code CanHit(CanHit)} did {@code this.attributes = other.attributes.clone()},
 * which clones the <b>array</b> and nothing else — every {@code DoubleValue} inside stayed the same
 * object. Buffs mutate those objects in place ({@code BoostDamageBuff.applyEffect} calls
 * {@code addModifier} on the target's value), so buffing one combatant changed the other's panel, and
 * {@code removeModifiersFrom} would strip the other's modifiers as well.
 *
 * <p>Nothing called the copy constructor when this was written, which is exactly why it is worth a test:
 * "no caller" is not "no bug", it is a landmine waiting for the first caller (P7-4's waves are the
 * obvious one — "spawn the same monster again").
 *
 * <p>The second test is the other half of the contract, and it is deliberately <b>not</b> a bug report:
 * a copy starts with a <b>fresh battle state</b> (full HP, no energy, no stacks, alive, vulnerable).
 * That is the convention the constructor already states for {@code death} / {@code currentEnergy} /
 * {@code resources}; it simply did not name {@code invulnerable}, so the test names it.
 */
public class CombatantCopyTest {
    private static final double EPS = 1e-9;

    @Test
    public void aCopyDoesNotShareItsAttributeSheetWithTheOriginal() {
        Character original = Character.fromAttributes("hero", 10_000, 100, 100, 100);

        Character copy = new Character(original);
        copy.getBuffManager().addBuff(new BoostDamageBuff(3, 0.5));

        Assertions.assertEquals(0.0, original.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), EPS,
                "a buff on the copy must not reach the original: cloning the array is not enough, the "
                        + "DoubleValue objects inside must be cloned too");
        Assertions.assertEquals(0.5, copy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), EPS,
                "and the buff really is on the copy");
    }

    /**
     * The mechanism, asserted directly: the copy's attribute objects are <b>different objects</b>.
     *
     * <p>Kept alongside the behavioural test rather than instead of it, because the two fail for
     * different reasons: the behavioural one says "a buff leaked", this one says "the sheet is shared" —
     * and a fix that made buffs not leak for some other reason while still sharing the objects would
     * leave the next mutation bug (any in-place change) in place.
     *
     * <p>⚠ An earlier version of this test cleared the copy's buffs and asserted the original kept its
     * own. That could never fail: a copy's {@code BuffManager} starts empty, so {@code clearAll()} has
     * nothing to remove and never touches the shared object.
     */
    @Test
    public void theCopyHasItsOwnAttributeObjects() {
        Character original = Character.fromAttributes("hero", 10_000, 100, 100, 100);

        Character copy = new Character(original);

        Assertions.assertNotSame(original.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST),
                copy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST),
                "each attribute must be cloned, not aliased");
        Assertions.assertNotSame(original.getAttribute(AttributeType.SPEED),
                copy.getAttribute(AttributeType.SPEED),
                "and every slot, not just the one a test happened to look at");
    }

    /** A copy is a fresh participant in a new battle, not a snapshot of the current one. */
    @Test
    public void aCopyStartsWithAFreshBattleState() {
        Character original = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        original.setInvulnerable(true);
        original.setCurrentEnergy(50);
        original.getBuffManager().addBuff(new BoostDamageBuff(3, 0.5));

        Character copy = new Character(original);

        Assertions.assertEquals(copy.getMaxHp(), copy.getCurrentHp(), EPS, "starts at full HP");
        Assertions.assertTrue(copy.getBuffManager().isEmpty(), "starts with no buffs");
        Assertions.assertEquals(0, copy.getCurrentEnergy(), EPS, "starts with no energy");
        Assertions.assertFalse(copy.isInvulnerable(),
                "invulnerable is battle state (a boss locking its HP bar mid-fight), so a copy starts "
                        + "vulnerable -- the same treatment as death and currentEnergy");
        Assertions.assertFalse(copy.isDeath(), "and alive");
    }
}
