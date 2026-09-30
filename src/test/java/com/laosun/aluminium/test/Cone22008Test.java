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
 * <p>\u2b50 Plain readings on the attribute itself: one follow-up adds 3%, ten of them reach 30%, and an eleventh adds nothing --
 * which is what \u6700\u591a\u53e0\u52a0 10 \u5c42 means.
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
        Assertions.assertEquals(full, extra, 1e-9, \u0022and the eleventh changes nothing: \u6700\u591a\u53e0\u52a0 10 \u5c42\u0022);
    }

    @Test
    public void onlyFollowUpsCount() {
        build(true);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, null, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, null, 0, 0);
        // \u2605 Compared against the BASELINE, not against zero: the wearer\u2019s own crit damage is 0.5 (measured), and the
        // assertion `== 0` was a mistake about the unit\u2019s starting panel rather than about the cone.
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
}
