package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 亚空异容所 (relic set 330), 2-piece: 「使装备者的速度提高 6%，当装备者的速度大于等于 145 时，进入战斗后使我方全体击破特攻提高
 * 18%，该效果无法叠加。」
 *
 * <p><b>What the set needed.</b> Nothing new: the speed half is the data's own {@code properties}
 * ({@code SpeedAddedRatio 0.06}), and the conditional half is {@code BATTLE_START} + the attribute gate
 * {@code self_attr:SPEED >= 145} + {@code MODIFY_ATTR BREAKING_EFFECT target: all_allies}. It was registered in
 * {@code _unmodelled.json} as "the vocabulary looks sufficient, just not written yet" - this is that writing.
 *
 * <p><b>Why the gate is worth a test rather than a comment.</b> 「当装备者的速度大于等于 145」 asks about the wearer's
 * <b>total</b> speed, and part of it comes from this very set - so the measurement has to show the property is already
 * folded in when {@code BATTLE_START} fires. The two boards differ by exactly the suit: 138.2 against 145.1, either side
 * of the stated 145.
 *
 * <p><b>How the flat 23.2 is arrived at.</b> 1102's level-80 speed is 115; the suit's 6% is +6.9, so the suited board
 * sits at 121.9 and needs 23.1 more to reach the threshold. Both boards get the same +23.2, so the only difference
 * between them is the suit: 138.2 against 145.1.
 */
public class Relic330SubspaceTest {

    private static final int LEVEL = 80;
    private static final int RELIC_STAR = 5;
    private static final int RELIC_LEVEL = 15;
    private static final int SET = 330;
    /** 1102 希儿: her level-80 speed is 138.1, which the suit's own 6% pushes to 145.0 - across the stated 145. */
    private static final int WEARER = 1102;
    /** Both boards carry the same flat speed, so the threshold crossing is decided by the suit alone. */
    private static final double MARGIN = 23.2;

    /** The two-piece's speed share is what carries the wearer across the clause's threshold. */
    @Test
    public void theSuitsSpeedShareIsWhatCrossesTheThreshold() {
        Run bare = new Run(false).withExtraSpeed(MARGIN);
        Run suited = new Run(true).withExtraSpeed(MARGIN);

        Assertions.assertTrue(bare.speed() < 145,
                "「当装备者的速度大于等于 145 时」 - without the suit: " + bare.speed());
        Assertions.assertTrue(suited.speed() >= 145,
                "with the suit's own speed share: " + suited.speed());
        // 6% of her 115 DATA speed = 6.9; the +23.1 that growth adds at level 80 is not part of the share (measured -
        // 6% of the grown 138.1 would have been 8.29). Both boards carry the same +2, so it cancels.
        Assertions.assertEquals(6.9, suited.speed() - bare.speed(), 1e-6,
                "「使装备者的速度提高 6%」: " + suited.speed() + " vs " + bare.speed());
    }

    /** 「进入战斗后使我方全体击破特攻提高 18%」 - every ally, not just the wearer. */
    @Test
    public void allAlliesGetBreakEffectWhenTheWearerIsFastEnough() {
        Run run = new Run(true).withExtraSpeed(MARGIN);
        Assertions.assertEquals(0.0, run.wearerBreakEffect(), 1e-9, "precondition: nothing before the battle starts");

        run.startBattle();

        Assertions.assertEquals(0.18, run.wearerBreakEffect(), 1e-9, "the wearer is an ally too");
        Assertions.assertEquals(0.18, run.allyBreakEffect(), 1e-9, "「我方全体」 - the other ally as well");
    }

    /** The gate itself: one speed point short of 145 grants nothing, even with the suit worn. */
    @Test
    public void belowTheThresholdNothingIsGranted() {
        Run run = new Run(true).withExtraSpeed(MARGIN);
        run.limitSpeedTo(144.0);
        Assertions.assertTrue(run.speed() < 145, "precondition: under 145, was " + run.speed());

        run.startBattle();

        Assertions.assertEquals(0.0, run.wearerBreakEffect(), 1e-9, "144 < 145 means no break effect");
        Assertions.assertEquals(0.0, run.allyBreakEffect(), 1e-9, "and none for the ally either");
    }

    /** The control: the same board without the suit stays at 138.2, so the 6% is what makes the clause apply. */
    @Test
    public void withoutTheSuitTheClauseNeverApplies() {
        Run run = new Run(false).withExtraSpeed(MARGIN);
        Assertions.assertTrue(run.speed() < 145, "precondition: under 145 without the suit, was " + run.speed());

        run.startBattle();

        Assertions.assertEquals(0.0, run.wearerBreakEffect(), 1e-9, "「速度大于等于 145 时」 did not hold");
        Assertions.assertEquals(0.0, run.allyBreakEffect(), 1e-9, "so the allies get nothing");
    }

    /** 「该效果无法叠加」: a second opening grants the same 18%, not 36%. */
    @Test
    public void theBoostDoesNotStack() {
        Run run = new Run(true).withExtraSpeed(MARGIN);
        run.startBattle();
        Assertions.assertEquals(0.18, run.allyBreakEffect(), 1e-9, "first opening");

        run.fireBattleStartAgain();

        Assertions.assertEquals(0.18, run.allyBreakEffect(), 1e-9,
                "「该效果无法叠加」 - same attribute on the same target replaces instead of adding");
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Run(boolean withSuit) {
            wearer = withSuit
                    ? CharacterFactory.create(WEARER, LEVEL, true, null,
                    RelicFactory.suit(SET, RELIC_STAR, RELIC_LEVEL))
                    : CharacterFactory.create(WEARER, LEVEL);
            ally = CharacterFactory.create(1001, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        }

        /** A flat speed modifier on the wearer: the lever both boards share, so the suit is what decides the crossing. */
        private Run withExtraSpeed(double extra) {
            wearer.getAttribute(AttributeType.SPEED).addModifier(DoubleValue.Modifier.pure(extra));
            return this;
        }

        /** Brings the wearer one point under the clause's threshold, keeping everything else as it was. */
        private void limitSpeedTo(double ceiling) {
            double over = speed() - ceiling;
            Assertions.assertTrue(over > 0, "precondition: above " + ceiling + ", was " + speed());
            withExtraSpeed(-over);
        }

        private void startBattle() {
            battle.startBattle();
            battle.processRequests();
        }

        /** A second BATTLE_START: 「无法叠加」 must show up as the same number, not twice it. */
        private void fireBattleStartAgain() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BATTLE_START));
            battle.processRequests();
        }

        private double speed() {
            return wearer.getAttribute(AttributeType.SPEED).get();
        }


        private double wearerBreakEffect() {
            return wearer.getAttribute(AttributeType.BREAKING_EFFECT).get();
        }

        private double allyBreakEffect() {
            return ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        }
    }
}
