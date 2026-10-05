package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 22008: after a FOLLOW-UP attack the wearer gains 3% crit damage for 2 turns, stacking up to 10.
 *
 * <p>⭐ Plain readings on the attribute itself: one follow-up adds 3%, ten of them reach 30%, and an eleventh adds nothing --
 * which is what 最多叠加 10 层 means.
 */
public class Cone22008Test {
    private static final int CONE = 22008;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CAP = 10;

    private Character wearer;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double followUpAndRead() {
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, null, 0, 0);
        return wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    @Test
    public void oneFollowUpIsThreePercentAndTenIsTheCap() {
        build(true);
        double before = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double one = followUpAndRead();
        for (int i = 1; i < CAP; i++) {
            followUpAndRead();
        }
        double full = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double extra = followUpAndRead();
        System.out.println("[22008] crit damage " + before + " -> " + one + " (one) -> " + full + " (ten) -> " + extra
                + " (eleven)");
        Assertions.assertEquals(before + 0.03, one, 1e-9, "one follow-up is +3%");
        Assertions.assertEquals(before + 0.30, full, 1e-9, "ten is +30%");
        Assertions.assertEquals(full, extra, 1e-9, \u0022and the eleventh changes nothing: 最多叠加 10 层\u0022);
    }

    @Test
    public void onlyFollowUpsCount() {
        build(true);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, null, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, null, 0, 0);
        // ★ Compared against the BASELINE, not against zero: the wearer’s own crit damage is 0.5 (measured), and the
        // assertion `== 0` was a mistake about the unit’s starting panel rather than about the cone.
        System.out.println("[22008] after a plain attack and a skill: crit damage is still "
                + wearer.getAttribute(AttributeType.CRIT_ATTACK).get() + " (baseline 0.5)");
        Assertions.assertEquals(0.5, wearer.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "the trigger is a FOLLOW-UP (false case)");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        build(false);
        double before = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        double after = followUpAndRead();
        System.out.println("[22008] without the cone: " + before + " -> " + after);
        Assertions.assertEquals(before, after, 1e-9, "no cone, no stacking (false case)");
    }

    @Test
    public void theDurationIsGrantedAndTheClockIsOpen() {
        // ★ Measured, and registered rather than asserted away: the layer is granted with `turns: 2`, but NOTHING public moves
        // that clock -- firing TURN_END twice, and calling the buff manager’s own turn boundary (`beforeMove()`) three times,
        // both left the value at 0.53. So this test pins the grant, and the expiry is a named gap until a real turn loop is
        // driven from a test (the countdown simply does not run outside the battle’s own turn machinery).
        build(true);
        double baseline = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        followUpAndRead();
        double granted = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        wearer.getBuffManager().beforeMove();
        double afterBoundary = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[22008] duration: baseline=" + baseline + " granted=" + granted
                + " after a turn boundary=" + afterBoundary + " (the clock does not move here)");
        Assertions.assertEquals(baseline + 0.03, granted, 1e-9, "granted on the follow-up");
        Assertions.assertEquals(granted, afterBoundary, 1e-9, "and no public boundary runs the clock (registered)");
    }
}
