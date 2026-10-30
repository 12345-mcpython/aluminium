package com.laosun.aluminium.test.compare;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.BreakDamageCalculator;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The break formula, anchored on the <b>one break the recorded fight actually contains</b>.
 *
 * <p>The capture's Break event (1415, level 80) lists exactly the three numbers the formula needs:
 * {@code raw_damage_before_modifiers = 10883.708128}, {@code break_damage_added_ratio = 0.15552000235766172} (the
 * attacker's break effect) and {@code break_damage_percentage = 2.5}. Solving the engine's own formula backwards gives
 * <b>25 toughness points consumed</b> - and at 25 the formula answers <b>10883.708573</b>, i.e. the capture to 4e-8
 * relative. That is what this case pins, so a change to the level base, the {@code /10} convention or the break-effect
 * factor reddens against a real fight instead of against another copy of the code.
 *
 * <p><b>Why 25 and not the skill's 30.</b> 「10 points of toughness left taking a 30-point skill means only 10 counts」 -
 * break damage is settled on what the bar actually gave, not on the nominal reduction. The capture's enemy had 25 left
 * when this broke, which is also why the replay's own breaks (at 30 and 15) do not match this number: the payload carries
 * no toughness bar, so the window's bar state cannot be reproduced from it.
 */
public class BreakBaseAgainstCaptureTest {

    /** `raw_damage_before_modifiers` of the capture's only Break event. */
    private static final double CAPTURE_BASE = 10883.708128133556;
    /** `break_damage_added_ratio` of that event. */
    private static final double CAPTURE_BREAK_EFFECT = 0.15552000235766172;

    private static Damage breakOf(Character attacker, double consumedToughness) {
        Enemy enemy = BattleFixture.monster();
        return BreakDamageCalculator.build(attacker, enemy, DamageElement.ICE, consumedToughness);
    }

    /**
     * The level base times the break effect times the CONSUMED toughness equals the captured number.
     */
    @Test
    public void theFormulaReproducesTheCapturedBreak() {
        Character attacker = CharacterFactory.create(1415, 80);
        attacker.getAttribute(AttributeType.BREAKING_EFFECT).addBase(CAPTURE_BREAK_EFFECT);

        double base = breakOf(attacker, 25).breakdown().get("base");

        Assertions.assertEquals(CAPTURE_BASE, base, Math.abs(CAPTURE_BASE) * 1e-6,
                "break base at 25 consumed toughness, level 80, break effect " + CAPTURE_BREAK_EFFECT);
    }

    /**
     * The same cast with the skill's NOMINAL toughness (30) is 20% high - the capture is the witness for "consumed, not
     * nominal", which is the difference between this test and a second copy of the code.
     */
    @Test
    public void theNominalToughnessWouldBeTooHigh() {
        Character attacker = CharacterFactory.create(1415, 80);
        attacker.getAttribute(AttributeType.BREAKING_EFFECT).addBase(CAPTURE_BREAK_EFFECT);

        double atConsumed = breakOf(attacker, 25).breakdown().get("base");
        double nominal = breakOf(attacker, 30).breakdown().get("base");

        Assertions.assertEquals(atConsumed * 30 / 25.0, nominal, Math.abs(nominal) * 1e-12,
                "the formula is linear in the toughness it is settled on");
        Assertions.assertTrue(nominal > CAPTURE_BASE * 1.19,
                "and settling on the nominal 30 instead of the consumed 25 reads 20% high - visible, not a rounding");
    }
}
