package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * Character 1506 (Silver Wolf LV.999): her talent's conversion - 「每点【隐藏分】使暴击率提高 <b>0.4%</b>。暴击率达到 <b>100%</b> 后，
 * 其余每点【隐藏分】改为使暴击伤害提高 <b>0.8%</b>。」 (her page, line 184).
 *
 * <p>⭐⭐ <b>Neither half needed a new engine word, and that is the finding worth keeping.</b> The first is
 * {@code self_attr_above:隐藏分:0} x 0.004; the second is the OVERFLOW OF CRIT RATE itself -
 * {@code self_attr_above:CRIT_CHANCE:1.0} x 2 - because "每溢出 1% 暴击率提高 2% 暴击伤害" is the same statement
 * (0.004 CRIT per point x 2 = 0.008 CRIT DMG per point, which is the page's 0.8%). So the shape is expressible today, and
 * its second reader is 1313 Sunday's talent (「当目标的暴击率超过 100%，每溢出 1% 暴击率提高 2% 暴击伤害」) - found by
 * scanning the corpus for the shape before building anything for it.
 *
 * <p>⚠ What this file therefore pins, and why each part is load-bearing:
 * <ul>
 *   <li>the per-point rate is +0.4% of CRIT RATE and NOTHING to CRIT DMG while she is below full;</li>
 *   <li>the switch sits at CRIT RATE = 1.0 <b>exactly</b> - measured against her own base rate, so a hard-coded 250 points
 *       (1.0 / 0.004) would fail here even though it looks right on paper;</li>
 *   <li>and past it, the excess pays at 2x (in CRIT-RATE units), which is the same 0.8% per point.</li>
 * </ul>
 */
public class SilverWolfLV999TalentCritTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "隐藏分";

    /** Below full: +0.4% CRIT Rate per hidden point, and the crit-damage half stays out of it. */
    @Test
    public void eachHiddenPointRaisesCritRateByPointFourPercent() {
        Reading plain = read(0);
        Reading atHundred = read(100);
        System.out.println("[1506-talent] CRIT_CHANCE " + plain.crit() + " -> " + atHundred.crit()
                + " ; CRIT_ATTACK " + plain.critDamage() + " -> " + atHundred.critDamage()
                + " ; 隐藏分 " + atHundred.hidden());
        Assertions.assertEquals(100, atHundred.hidden(), "precondition: 100 counted laughs became 100 隐藏分");
        Assertions.assertEquals(plain.crit() + 100 * 0.004, atHundred.crit(), 1e-9,
                "「每点【隐藏分】使暴击率提高 0.4%」");
        Assertions.assertEquals(plain.critDamage(), atHundred.critDamage(), 1e-9,
                "and while she is below full, NOTHING switches to crit damage yet");
    }

    /**
     * The switch is at CRIT RATE 1.0, not at a round number of points.
     *
     * <p>⚠ Measured against her own base rate: she starts with some CRIT RATE from her own panel, so the point where the
     * talent fills it up is {@code (1 - base) / 0.004} - and that is why the obvious hard-coded 250 would be a silently
     * wrong boundary. The two readings below sit either side of it.
     */
    @Test
    public void theSwitchSitsExactlyAtFullCritRate() {
        Reading plain = read(0);
        int justBelow = (int) Math.floor((1 - plain.crit()) / 0.004) - 1;
        Reading below = read(justBelow);
        Reading above = read(justBelow + 3);
        double excess = above.crit() - 1.0;
        System.out.println("[1506-talent] base CRIT_CHANCE " + plain.crit() + " ; at " + below.hidden() + " 隐藏分 crit = "
                + below.crit() + " (crit dmg " + below.critDamage() + ") ; at " + above.hidden() + " crit = "
                + above.crit() + " (crit dmg " + above.critDamage() + ", excess " + excess + ")");
        Assertions.assertTrue(below.crit() < 1.0, "precondition: the lower reading is still under full");
        Assertions.assertTrue(above.crit() > 1.0, "precondition: the upper reading is over it");
        Assertions.assertEquals(plain.critDamage(), below.critDamage(), 1e-9,
                "under full there is no crit-damage half at all");
        Assertions.assertEquals(plain.critDamage() + 2 * excess, above.critDamage(), 1e-9,
                "and over it the excess pays 2x - 「每溢出 1% 暴击率提高 2% 暴击伤害」");
    }

    /** Past full, every point pays 0.8% CRIT DMG - read as the MARGINAL rate, since the first ~190 points pay none. */
    @Test
    public void theOverflowPaysPointEightPercentPerPoint() {
        Reading plain = read(0);
        Reading full = read(300);
        Reading lower = read(250);
        double excess = full.crit() - 1.0;
        System.out.println("[1506-talent] at the 300-point ceiling: CRIT_CHANCE " + full.crit() + ", CRIT_ATTACK "
                + full.critDamage() + " (excess " + excess + " -> 2 x excess = " + (2 * excess) + ")"
                + " ; marginal per point past the switch = " + ((full.critDamage() - lower.critDamage()) / 50));
        Assertions.assertEquals(300, full.hidden(), "60 plus the 240 overflow is the ceiling");
        Assertions.assertEquals(plain.crit() + 300 * 0.004, full.crit(), 1e-9,
                "the crit-rate half keeps its rate - it is not clamped, because its overflow is what the other half reads");
        Assertions.assertEquals(plain.critDamage() + 2 * excess, full.critDamage(), 1e-9,
                "and 2 x excess is exactly 0.008 per hidden point past full");
        // ⚠ The MARGINAL rate, not the average over all 300: the points below full pay no crit damage at all, so
        // (critDmg(300) - base) / 300 is 0.0029 and asserting 0.008 there would be asserting the switch point by accident.
        Assertions.assertEquals(0.008, (full.critDamage() - lower.critDamage()) / 50, 1e-9,
                "past the switch each hidden point pays the page's 0.8% CRIT DMG");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Her CRIT RATE, CRIT DMG and 隐藏分 after the party gains {@code laughs} 笑点 (which her trace turns into 隐藏分). */
    private record Reading(double crit, double critDamage, int hidden) {
    }

    /**
     * One reading. The 隐藏分 is moved through an OP, because that is the only route that raises the RESOURCE_CHANGED the
     * talent listens for - `fireResourceChanged` is called from the op, not by the resource manager, so granting the
     * counter by hand leaves the talent deaf (measured: 隐藏分 100 and CRIT_CHANCE unchanged at 0.236999998).
     *
     * <p>⚠⚠ <b>ONE firing per reading, deliberately.</b> A version of this helper fired the grant once per point and
     * measured {@code 20.436999998} for 100 points instead of {@code 0.636999998}: the modifiers STACK across firings, so
     * the total was {@code 0.004 x (1+2+...+100) = 20.2}. That is registered in GAPS as an open question rather than
     * papered over here - it means {@code coexist: true} does NOT mean "replace my own previous contribution", and the
     * shipped enhanced-basic ramp (which fires on every DEALING_DAMAGE) has the same shape and is very likely
     * over-counting on every hit after the first.
     */
    private static Reading read(int hiddenPoints) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters.getFirst();
        if (hiddenPoints > 0) {
            TriggerTable withGrant = wolf.getTriggerTable().plus(new TriggerTable(WOLF,
                    List.of(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                            TriggerSpecs.gainResource(HIDDEN, hiddenPoints)))));
            wolf.setTriggerTable(withGrant);
            battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
            battle.processRequests();
        }
        return new Reading(wolf.getAttribute(AttributeType.CRIT_CHANCE).get(),
                wolf.getAttribute(AttributeType.CRIT_ATTACK).get(), wolf.getResources().value(HIDDEN));
    }
}
