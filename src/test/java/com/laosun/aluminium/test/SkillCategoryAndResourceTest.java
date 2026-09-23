package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Two generic abstractions introduced by the refactor (2026-09-23):
 * {@link SkillCategory} (enumification of the data's {@code attack_type}) and
 * {@link Resource} (a bounded party-level resource).
 *
 * <p>The reason both exist is **stability**:
 * <ul>
 *   <li>{@code SkillCategory} eliminates the class of bugs "bare string switch → a spelling change
 *       in the data silently fails to match" ({@code DOC_VS_CODE.md} §F, F-6);</li>
 *   <li>{@code Resource} gives "cap / overflow / atomic spend" a named boundary semantics, shared
 *       by skill points and the P8-8 stack resource ({@code DOC_VS_CODE.md} §F, F-1/F-7).
 * </ul>
 */
public class SkillCategoryAndResourceTest {

    // ==================================================================
    // SkillCategory: parsing must be robust (the data is an external artifact)
    // ==================================================================

    /** All 7 values that really exist in the data must parse correctly. */
    @Test
    public void knownValuesRoundTrip() {
        Assertions.assertEquals(SkillCategory.NORMAL, SkillCategory.fromString("Normal"));
        Assertions.assertEquals(SkillCategory.BPSKILL, SkillCategory.fromString("BPSkill"));
        Assertions.assertEquals(SkillCategory.ULTRA, SkillCategory.fromString("Ultra"));
        Assertions.assertEquals(SkillCategory.MAZE_NORMAL, SkillCategory.fromString("MazeNormal"));
        Assertions.assertEquals(SkillCategory.MAZE, SkillCategory.fromString("Maze"));
        Assertions.assertEquals(SkillCategory.ASSIST, SkillCategory.fromString("Assist"));
        Assertions.assertEquals(SkillCategory.ELATION_DAMAGE, SkillCategory.fromString("ElationDamage"));
    }

    /** {@code value()} must be able to look the original data value back up — otherwise writing back to the data later would not line up. */
    @Test
    public void valueMatchesTheDataString() {
        Assertions.assertEquals("Normal", SkillCategory.NORMAL.value());
        Assertions.assertEquals("BPSkill", SkillCategory.BPSKILL.value());
        Assertions.assertEquals("Ultra", SkillCategory.ULTRA.value());
        Assertions.assertEquals("MazeNormal", SkillCategory.MAZE_NORMAL.value());
        Assertions.assertEquals("Maze", SkillCategory.MAZE.value());
        Assertions.assertEquals("Assist", SkillCategory.ASSIST.value());
        Assertions.assertEquals("ElationDamage", SkillCategory.ELATION_DAMAGE.value());
    }

    /**
     * {@code null} / empty string → {@code UNSPECIFIED}, **not** {@code UNKNOWN}.
     *
     * <p>This distinction matters a lot: the {@code attack_type} of talents and follow-up attacks is
     * simply empty in the data (94 entries measured); that is a **legitimate empty**, not
     * "the data is broken".
     */
    @Test
    public void nullAndBlankAreUnspecifiedNotUnknown() {
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString(null));
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString(""));
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString("   "));
        Assertions.assertTrue(SkillCategory.UNSPECIFIED.isUnspecified());
        Assertions.assertFalse(SkillCategory.UNSPECIFIED.isKnownValue(), "a legitimate empty does not count as a 'recognized value'");
    }

    /**
     * ⚠ <b>Core stability assertion</b>: an unrecognized value **does not throw**, it degrades to
     * {@code UNKNOWN}.
     *
     * <p>The data is an external artifact — blowing up the engine because of one new type is a
     * stability problem. Here "safe degradation + observability" is chosen
     * ({@code isKnownValue()} lets the caller decide whether to speak up).
     */
    @Test
    public void unknownValueDegradesInsteadOfThrowing() {
        SkillCategory weird = SkillCategory.fromString("SomeFutureType");
        Assertions.assertEquals(SkillCategory.UNKNOWN, weird);
        Assertions.assertFalse(weird.isKnownValue());
        Assertions.assertFalse(weird.isUnspecified(), "unrecognized ≠ legitimate empty; the two must be distinguishable");

        // Extreme input must not blow up either
        Assertions.assertEquals(SkillCategory.UNKNOWN, SkillCategory.fromString("\u0000"));
        Assertions.assertEquals(SkillCategory.UNKNOWN, SkillCategory.fromString("Normal2"));
    }

    /** Case-insensitive + trimming — a non-uniform style on the data side should not fail to match. */
    @Test
    public void parsingIsCaseInsensitiveAndTrims() {
        Assertions.assertEquals(SkillCategory.NORMAL, SkillCategory.fromString("normal"));
        Assertions.assertEquals(SkillCategory.BPSKILL, SkillCategory.fromString("bpskill"));
        Assertions.assertEquals(SkillCategory.ULTRA, SkillCategory.fromString("  Ultra  "));
        Assertions.assertEquals(SkillCategory.ELATION_DAMAGE, SkillCategory.fromString("elationdamage"));
    }

    /** Only basic attack / skill / ultimate count as "actively acting in combat"; map skills and empty values do not. */
    @Test
    public void combatActionClassification() {
        Assertions.assertTrue(SkillCategory.NORMAL.isCombatAction());
        Assertions.assertTrue(SkillCategory.BPSKILL.isCombatAction());
        Assertions.assertTrue(SkillCategory.ULTRA.isCombatAction());

        Assertions.assertFalse(SkillCategory.MAZE_NORMAL.isCombatAction(), "a map basic attack is outside combat");
        Assertions.assertFalse(SkillCategory.MAZE.isCombatAction(), "a technique is outside combat");
        Assertions.assertFalse(SkillCategory.UNSPECIFIED.isCombatAction(), "a talent / follow-up attack is not actively acting");
        Assertions.assertFalse(SkillCategory.UNKNOWN.isCombatAction(), "an unrecognized value must not default to actively acting");
        Assertions.assertFalse(SkillCategory.ASSIST.isCombatAction());
        Assertions.assertFalse(SkillCategory.ELATION_DAMAGE.isCombatAction());
    }

    // ==================================================================
    // Resource: the three boundaries
    // ==================================================================

    /** The regular path does not cross the cap, and returns the **actual** amount credited. */
    @Test
    public void gainClampedStopsAtMax() {
        Resource r = new Resource("sp", 5, 3);

        Assertions.assertEquals(2, r.gainClamped(2), "3 + 2 = 5, credited in full");
        Assertions.assertEquals(5, r.getValue());
        Assertions.assertTrue(r.isFull());

        Assertions.assertEquals(0, r.gainClamped(1), "already full → 0 actually credited");
        Assertions.assertEquals(0, r.gainClamped(100), "adding 100 at once is 0 as well");
        Assertions.assertEquals(5, r.getValue());
    }

    /** Adding a non-positive number is a caller bug: ignored silently, and **must not** turn into a deduction. */
    @Test
    public void nonPositiveGainIsIgnored() {
        Resource r = new Resource("sp", 5, 3);

        Assertions.assertEquals(0, r.gainClamped(0));
        Assertions.assertEquals(0, r.gainClamped(-5));
        Assertions.assertEquals(0, r.gain(-5));
        Assertions.assertEquals(3, r.getValue(), "must not be deducted by 'adding a negative number'");
    }

    /** Overflow is **not allowed** by default: with no allowance configured, {@code gain} and {@code gainClamped} are equivalent. */
    @Test
    public void overflowIsOffByDefault() {
        Resource r = new Resource("sp", 5, 5);
        Assertions.assertEquals(0, r.getMaxOverflow());

        Assertions.assertEquals(0, r.gain(3), "no overflow allowance configured → cannot be added");
        Assertions.assertEquals(5, r.getValue());
    }

    /**
     * Only with an overflow allowance configured can it be stored above the cap, and it is
     * **capped at max + overflow**.
     *
     * <p>Corresponds to Sparkle's ultimate "restore 4/6 skill points; if skill points overflow when
     * restoring, record the number of overflowing skill points, up to 10" ({@code 1306_花火.md}) —
     * the engine side only provides the "can overflow and is capped" capability.
     */
    @Test
    public void overflowIsExplicitAndCapped() {
        Resource r = new Resource("sp", 5, 5);
        r.setMaxOverflow(10);

        Assertions.assertEquals(6, r.gain(6), "5 + 6 = 11 (≤ 15), credited in full");
        Assertions.assertEquals(11, r.getValue());
        Assertions.assertTrue(r.isFull(), "above the regular cap, so of course it counts as full");
        Assertions.assertFalse(r.isCapped(), "but it has not reached 5 + 10 = 15 yet");

        Assertions.assertEquals(4, r.gain(100), "15 - 11 = 4, capped at the absolute maximum");
        Assertions.assertEquals(15, r.getValue());
        Assertions.assertTrue(r.isCapped());
    }

    /**
     * Lowering the overflow allowance **clamps the out-of-range stored value**, keeping the
     * invariant {@code value ≤ max + overflow} true.
     *
     * <p>⚠ This is where my first version was wrong: at the time it only changed the allowance
     * without clamping the value, so it could produce an illegal state like
     * {@code max=5, overflow=0, value=15} — after which every {@code isCapped()} / {@code gain()}
     * judgement is off, and it **raises no error**.
     */
    @Test
    public void loweringOverflowReclampsToKeepTheInvariant() {
        Resource r = new Resource("sp", 5, 5);
        r.setMaxOverflow(10);
        r.gain(10);
        Assertions.assertEquals(15, r.getValue());
        Assertions.assertTrue(r.isCapped());

        r.setMaxOverflow(0);
        Assertions.assertEquals(5, r.getValue(), "the out-of-range stored value is clamped to the new absolute maximum");
        Assertions.assertTrue(r.isCapped(), "5 is the current absolute maximum");
        Assertions.assertTrue(r.isFull());
        Assertions.assertEquals(0, r.missingToMax(), "the invariant is not broken: value ≤ max");
        Assertions.assertEquals(0, r.gain(1), "it is full, cannot be added to");
    }

    /** Raising the allowance does not touch the stored value, it just allows adding more afterwards. */
    @Test
    public void raisingOverflowKeepsCurrentValue() {
        Resource r = new Resource("sp", 5, 3);
        r.setMaxOverflow(10);

        Assertions.assertEquals(3, r.getValue(), "the stored value is untouched");
        Assertions.assertEquals(8, r.gain(8), "5 + 10 = 15 is the new absolute maximum");
        Assertions.assertEquals(11, r.getValue());
    }

    /** A negative overflow allowance is treated as 0. */
    @Test
    public void negativeOverflowIsClampedToZero() {
        Resource r = new Resource("sp", 5, 0);
        r.setMaxOverflow(-3);
        Assertions.assertEquals(0, r.getMaxOverflow());
        Assertions.assertEquals(5, r.gain(5));
        Assertions.assertEquals(0, r.gain(1));
    }

    /** {@code spend} = "deduct as much as it can"; {@code spendExactly} = "if it is not enough, deduct nothing at all". */
    @Test
    public void spendVersusSpendExactly() {
        Resource r = new Resource("sp", 5, 3);

        // Deduct as much as it can (the semantics of taking DoT HP loss, say)
        Assertions.assertEquals(3, r.spend(10), "only 3 available, all of it deducted");
        Assertions.assertEquals(0, r.getValue());

        // Atomic semantics (the skill point kind: insufficient = nothing was spent this time)
        r.setValue(2);
        Assertions.assertTrue(r.spendExactly(2));
        Assertions.assertEquals(0, r.getValue());

        Assertions.assertFalse(r.spendExactly(1), "not enough → fails");
        Assertions.assertEquals(0, r.getValue(), "a failed spend must not deduct into the negative");
    }

    /** {@code setValue} is an unprotected raw write, but it **still clamps** (used for save restoration). */
    @Test
    public void setValueClampsButDoesNotFail() {
        Resource r = new Resource("sp", 5, 0);

        r.setValue(-10);
        Assertions.assertEquals(0, r.getValue());
        r.setValue(999);
        Assertions.assertEquals(5, r.getValue());

        r.setMaxOverflow(3);
        r.setValue(999);
        Assertions.assertEquals(8, r.getValue(), "clamped to max + overflow");
    }

    /** The initial value is clamped too — passing an out-of-range value to the constructor should neither blow up nor leave an illegal state. */
    @Test
    public void initialValueIsClamped() {
        Assertions.assertEquals(5, new Resource("sp", 5, 99).getValue());
        Assertions.assertEquals(0, new Resource("sp", 5, -99).getValue());
    }

    /** Illegal constructor arguments must **fail fast** (these are coding errors, not data errors, and must not be silent). */
    @Test
    public void invalidConstructionFailsFast() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource(null, 5, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource("  ", 5, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource("sp", -1, 0));

        // max = 0 is legal: a "read-only" resource
        Resource zero = new Resource("zero", 0, 0);
        Assertions.assertTrue(zero.isFull());
        Assertions.assertTrue(zero.isCapped());
        Assertions.assertEquals(0, zero.gain(5));
    }

    /** The boundaries of {@code missingToMax} and {@code isEmpty}. */
    @Test
    public void queryHelpers() {
        Resource r = new Resource("sp", 5, 0);
        Assertions.assertTrue(r.isEmpty());
        Assertions.assertEquals(5, r.missingToMax());

        r.gain(3);
        Assertions.assertFalse(r.isEmpty());
        Assertions.assertEquals(2, r.missingToMax());

        r.gain(2);
        Assertions.assertEquals(0, r.missingToMax());

        r.setMaxOverflow(5);
        r.gain(3);
        Assertions.assertEquals(0, r.missingToMax(), "when overflowing it is 0 too, not negative");
    }
}
