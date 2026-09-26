package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * L-12: every input of a {@code DotBuff} comes from data, so each one has to be rejected at
 * <b>construction</b> time instead of producing a wrong answer later.
 *
 * <p>Why this is not "defensive programming for its own sake" — each parameter has a concrete wrong
 * answer behind it:
 * <ul>
 *   <li>{@code turns = 0} is <b>one settlement, not zero</b>. {@code Battle.tickDots} settles
 *   everything in the buff list and only afterwards does {@code BuffManager.processBuffTick} expire
 *   it, so "0 turns" silently means "1 turn" — the class javadoc's own contract is "N turns settles
 *   exactly N times". The same holds for a negative value.</li>
 *   <li>A negative {@code baseDamage} neither errors nor heals: it is swallowed by
 *   {@code Battle.assemble}'s {@code Math.max(1, …)} floor, so the burn settles exactly <b>1 damage
 *   per turn</b> — a wrong number that nothing ever complains about. (The registry's original wording
 *   "silently no damage" was close but not exact.) NaN/infinity poisons the settlement the same way a
 *   NaN reaches the HP bar (see M-11).</li>
 *   <li>A {@code null} element is only caught much later by {@code Damage}'s
 *   {@code Objects.requireNonNull(element)} — at settlement time, in {@code tickDots}, far from the
 *   code that built the buff.</li>
 *   <li>A {@code null} source is not checked <b>anywhere</b> ({@code Damage} requires only element and
 *   type), so a DOT kill would be credited to nobody. {@code DotBuff}'s own javadoc already says the
 *   applier "MUST be recorded".</li>
 * </ul>
 *
 * <p>The production caller is {@code Battle.attachBreakDot}, whose element and turn count come from
 * {@code Constant.BREAK_EFFECTS} — i.e. from a table in the data pipeline. Validation is what turns a
 * typo there into a loud error instead of a burn that lasts one turn too few.
 */
public class DotBuffValidationTest {

    private static Character source() {
        return Character.fromAttributes("source", 10_000, 100, 100, 100);
    }

    @Test
    public void zeroTurnsIsRejectedBecauseItWouldStillSettleOnce() {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), DamageElement.FIRE, 500, 0));
        Assertions.assertTrue(error.getMessage().contains("turns"),
                "the message must name the offending parameter: " + error.getMessage());
    }

    @Test
    public void negativeTurnsIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), DamageElement.FIRE, 500, -1));
    }

    @Test
    public void negativeBaseDamageIsRejectedBecauseTheFloorWouldSwallowIt() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), DamageElement.FIRE, -1.0, 2));
    }

    @Test
    public void nonFiniteBaseDamageIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), DamageElement.FIRE, Double.NaN, 2), "NaN would poison every later zone");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), DamageElement.FIRE, Double.POSITIVE_INFINITY, 2));
    }

    @Test
    public void nullElementIsRejectedHereAndNotAtSettlementTime() {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(source(), null, 500, 2));
        Assertions.assertTrue(error.getMessage().contains("element"),
                "the message must name the offending parameter: " + error.getMessage());
    }

    @Test
    public void nullSourceIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DotBuff(null, DamageElement.FIRE, 500, 2),
                "the applier is who a DOT kill is credited to");
    }

    /**
     * The guard must not reject the legal shapes, and it must not rewrite them either.
     *
     * <p>{@code baseDamage == 0} stays legal on purpose: a DOT that lands for 0 is still a landed hit
     * (it fires {@code TAKING_HIT}, exactly like a hit a shield absorbed completely), which is a
     * different thing from a negative value that would add HP back.
     */
    @Test
    public void legalValuesAreAcceptedAndStoredUnchanged() {
        DotBuff dot = new DotBuff(source(), DamageElement.THUNDER, 0.0, 3);
        Assertions.assertEquals(3, dot.duration(), "the turn count is stored as given");
        Assertions.assertEquals(0.0, dot.getBaseDamage());
        Assertions.assertEquals(DamageElement.THUNDER, dot.getElement());
        Assertions.assertEquals(1, new DotBuff(source(), DamageElement.WIND, 500, 1).duration(),
                "one turn is the minimum, not an error");
    }
}
