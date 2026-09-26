package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * P10-1's break-effect table: the structure exists and nothing can fall out of it unnoticed.
 *
 * <p>⚠ <b>The numbers are still placeholders and this class does not pretend otherwise.</b> The four
 * damaging elements reuse the old {@code DOT_RATIO} / {@code DOT_TURNS}, and the three control elements
 * carry no DOT at all — their real parameters belong with P10-2's control state machine. What is pinned
 * here is the <b>structure</b>: every element is accounted for, and the DOT set is derived from the table
 * rather than listed a second time.
 */
public class BreakEffectTableTest {

    /**
     * Every element has an entry, so adding an element to {@code DamageElement} cannot leave it with a
     * silently undefined break effect.
     */
    @Test
    public void everyElementHasABreakEffect() {
        for (DamageElement element : DamageElement.values()) {
            Assertions.assertNotNull(Constant.BREAK_EFFECTS.get(element),
                    element + " has no break effect; add it to Constant.BREAK_EFFECTS rather than leaving it "
                            + "implicit -- 'not listed' and 'nothing happens' must not look the same");
        }
    }

    /**
     * The four damaging elements attach a DOT and the three control elements do not.
     *
     * <p>The second half is the point: it is what makes "Ice / Quantum / Imaginary do nothing" a stated
     * fact instead of an oversight, and it is what would go red if someone "filled in" a control element
     * with a made-up ratio before P10-2 exists.
     */
    @Test
    public void damagingElementsHaveADotAndControlElementsAreNamedButHaveNone() {
        for (DamageElement element : Arrays.asList(DamageElement.FIRE, DamageElement.THUNDER,
                DamageElement.PHYSICAL, DamageElement.WIND)) {
            Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
            Assertions.assertTrue(effect.hasDot(), element + " should carry a DOT");
            Assertions.assertNull(effect.control(), element + " is not a control-type break");
        }

        for (DamageElement element : Arrays.asList(DamageElement.ICE,
                DamageElement.QUANTUM, DamageElement.IMAGINARY)) {
            Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
            Assertions.assertFalse(effect.hasDot(),
                    element + " is a control-type break: it must not carry a DOT until P10-2 defines one");
            Assertions.assertNotNull(effect.control(),
                    element + " must at least name its control effect, so 'unimplemented' is visible");
        }
    }

    /**
     * {@code DOT_ELEMENTS} and the table cannot disagree, because the set is derived from the table.
     *
     * <p>This is the assertion that would catch the old shape coming back: a second hand-written list of
     * "which elements have a DOT" is precisely how Ice came to look like a different kind of DOT.
     */
    @Test
    public void theDotSetAgreesWithTheTable() {
        long expected = Constant.BREAK_EFFECTS.values().stream().filter(Constant.BreakEffect::hasDot).count();
        Assertions.assertEquals(expected, Constant.DOT_ELEMENTS.size());
        for (DamageElement element : Constant.DOT_ELEMENTS) {
            Assertions.assertTrue(Constant.BREAK_EFFECTS.get(element).hasDot(),
                    element + " is in DOT_ELEMENTS but has no DOT in the table");
        }
    }
}
