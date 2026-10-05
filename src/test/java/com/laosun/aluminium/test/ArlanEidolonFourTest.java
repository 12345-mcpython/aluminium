package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1008："进入战斗后，受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%。
 * 该效果在触发 1 次后或持续 2 回合后自动解除" (2026-10-02).
 *
 * <p>THREE READINGS, ONE VARIABLE EACH: the eidolon rank, the NUMBER of lethal blows, and the NUMBER of his own turns that
 * elapse before the blow. The trace is a state, so it can be asked about directly -- which is what makes "自动解除" testable.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;
    private static final String STATE = "绝处反击";

    /** At E4 the first lethal blow restores him to a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeSurvivesAtAQuarter() {
        double[] result = afterLethalBlows(4, 1, 0);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "「回复至自身生命上限的 25%」");
    }

    /** Note: Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1, 0)[0] <= 0, "星魂 4 才有这一条");
    }

    /** "该效果在触发 1 次后…自动解除": the second blow in the same battle is NOT answered. */
    @Test
    public void theFirstBlowConsumesTheTrace() {
        double[] one = afterLethalBlows(4, 1, 0);
        double[] two = afterLethalBlows(4, 2, 0);
        Assertions.assertTrue(one[0] > 0, "第一次被救");
        Assertions.assertTrue(two[0] <= 0, "第二次**不再**被救（「触发 1 次后自动解除」）");
    }

    /** "或持续 2 回合后自动解除": after two of HIS turns the trace is gone, even though it never triggered. */
    @Test
    public void twoTurnsEndTheTraceUntriggered() {
        double[] fresh = afterLethalBlows(4, 1, 0);
        double[] late = afterLethalBlows(4, 1, 2);
        Assertions.assertTrue(fresh[0] > 0, "没过回合时仍然被救");
        Assertions.assertTrue(late[0] <= 0, "过了两个回合后**不再**被救（「持续 2 回合后自动解除」）");
    }

    // ==================================================================

    /** { his HP after the blows, his Max HP }. {@code turns} of his own turns elapse before the blows. */
    private static double[] afterLethalBlows(int eidolon, int blows, int turns) {
        Character him = CharacterFactory.create(ARLAN, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (eidolon >= 4) {
            Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: the trace is on him");
        }

        // Note: A timed buff ticks on ITS WEARER's turns, in TWO halves: `beforeMove()` is the early tick and `afterMove()` the late
        // one (BuffManager.processBuffTick(true/false)), and a state's expiry is announced on the LATE half. Driving only
        // `beforeMove()` is therefore half a turn, which is what the first version of this judge measured by mistake.
        for (int turn = 0; turn < turns; turn++) {
            Signal signal = battle.queue.snapshot().stream()
                    .filter(candidate -> candidate.getCanHit() == him).findFirst()
                    .orElseThrow(() -> new AssertionError("precondition: he is in the queue"));
            battle.currentMove = signal;
            battle.beforeMove();
            battle.afterMove();
            battle.processRequests();
        }

        for (int blow = 0; blow < blows; blow++) {
            if (him.isDeath()) {
                break;
            }
            battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
            battle.processRequests();
        }
        return new double[]{him.isDeath() ? 0 : him.getCurrentHp(), him.getMaxHp()};
    }
}
