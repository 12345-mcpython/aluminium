package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * P10-1 / P10-2's break-effect tables: the structure exists, every element is accounted for, and nothing
 * can fall out of the tables unnoticed.
 *
 * <p>⚠ <b>The numbers are still placeholders and this class does not pretend otherwise.</b> The four
 * damaging elements reuse the old {@code DOT_RATIO} / {@code DOT_TURNS}; the three control elements carry
 * a delay and a control state whose numbers are example values, and carry <b>no</b> DOT because the damage
 * component of 冻结/纠缠 is a ratio the data does not contain (see the TODO in ROADMAP P10-2). What is
 * pinned here is the <b>structure</b>: every element is accounted for, the DOT set is derived from the
 * table rather than listed a second time, and every control key resolves.
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
     * The four damaging elements are exactly "a DOT and nothing else": no control state, and no extra delay
     * — which is what makes the table reproduce their pre-table behaviour bit for bit.
     */
    @Test
    public void damagingElementsCarryADotAndNoControl() {
        for (DamageElement element : Arrays.asList(DamageElement.FIRE, DamageElement.THUNDER,
                DamageElement.PHYSICAL, DamageElement.WIND)) {
            Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
            Assertions.assertTrue(effect.hasDot(), element + " should carry a DOT");
            Assertions.assertFalse(effect.hasControl(), element + " is not a control-type break");
            Assertions.assertEquals(0, effect.delayPercent(), 1e-9,
                    element + " must not add delay beyond the fixed break delay");
        }
    }

    /**
     * The three control elements name a control state and an extra delay, and still carry no DOT.
     *
     * <p>The "no DOT" half is not tidiness, it is an open question recorded as one: the encyclopedia text
     * says a 冻结 victim takes ice damage every turn and that 纠缠 hits on its next action — both of which
     * are DOTs — but it gives no break-applied ratio, and {@code BreakEffect.dotRatio} is exactly that
     * field. This assertion makes "we have not decided that number yet" a red test if someone fills in a
     * made-up ratio, instead of a value that quietly looks like data.
     */
    @Test
    public void controlElementsNameAStateAndAnExtraDelayAndNoDotYet() {
        for (DamageElement element : Arrays.asList(DamageElement.ICE,
                DamageElement.QUANTUM, DamageElement.IMAGINARY)) {
            Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
            Assertions.assertFalse(effect.hasDot(),
                    element + " must not carry a DOT until the damage ratio is decided (ROADMAP P10-2)");
            Assertions.assertTrue(effect.hasControl(),
                    element + " must name its control effect, so 'unimplemented' is visible");
            Assertions.assertTrue(effect.delayPercent() > 0,
                    element + " is documented as 行动延后, so it must carry an extra delay");
        }
    }

    /**
     * Every control key in the break table resolves, and the states stay distinguishable.
     *
     * <p>Two separate guards: an unresolvable key is a typo that must throw rather than degrade into "no
     * control at all" ({@code controlEffect()} does that loudly), and the three states must not collapse
     * into one — 冻结 is the one that stops the victim acting, the other two only slow it down.
     */
    @Test
    public void everyControlKeyResolvesAndFreezeIsTheOnlyActLock() {
        for (Constant.BreakEffect effect : Constant.BREAK_EFFECTS.values()) {
            if (!effect.hasControl()) {
                continue;
            }
            Constant.ControlEffect control = effect.controlEffect();
            Assertions.assertNotNull(control, effect.control() + " does not resolve");
            Assertions.assertEquals(control.blocksAct(), "FROZEN".equals(effect.control()),
                    effect.control() + ": only 冻结 keeps the victim from acting -- 禁锢/纠缠 act, "
                            + "just later and slower");
            Assertions.assertNotNull(control.resistKey(),
                    effect.control() + " needs a resist key for the skill-applied path");
            Assertions.assertTrue(control.resistKey().startsWith("STAT_"),
                    effect.control() + "'s resist key must be the data's own vocabulary (STAT_*), got "
                            + control.resistKey());
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
