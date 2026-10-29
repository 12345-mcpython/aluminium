package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.SpeedBoostBuff;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * P10-4's acceptance case: <b>a speed change re-times the action bar, and a pull moves it</b>.
 *
 * <p>Both numbers are the ledger's own (§4.2): speed 100 slowed by 30% must act after <b>142.857</b>
 * ({@code 10000 / 70}), and a 50% pull must arrive <b>half a lap</b> earlier. They are written here because the
 * capability existed without a judge - {@code Battle.delayMovePercent} is public, {@code ADVANCE} goes through
 * {@link Queue#advanceActionByPercent}, and {@code SpeedBoostBuff} is what makes a speed change reach
 * {@code Queue.refreshSpeed} - so "does it really" was answerable only by reading the code.
 *
 * <p>⚠ Two traps this fixture exists to avoid. (1) <b>The first round is 1.5 laps</b> ({@code Queue.initialize}),
 * so every case measures a <b>cycle</b>: the unit acts and its bar is reset first; reading the interval off a fresh
 * queue would be 150, not 100. (2) <b>A bare {@code Queue} does not listen for speed changes</b> - the listener is
 * injected by {@code Battle} - so a queue built directly would show a slowed unit whose bar never moved, and the
 * case would "prove" the opposite of what it means to.
 */
public class SpeedBuffTest {

    private static final double EPS = 1e-9;

    /** The engine's cycle is {@code 10000 / speed} (see {@code Queue.ACTION_THRESHOLD}). */
    private static final double CYCLE = 10_000;

    private static final int SPEED = 100;

    /**
     * 30% slower means 70 speed, and a cycle at 70 is {@code 10000 / 70 = 142.857...} - counted from the action the
     * unit just took, because a just-reset cycle has all its progress ahead of it.
     */
    @Test
    public void aThirtyPercentSlowStretchesTheNextCycleToTheSlowedSpeed() {
        Scene scene = heroJustActed();
        Assertions.assertEquals(250, signalOf(scene.queue(), scene.hero()).getNextActionTime(), EPS,
                "precondition: after acting, the next cycle is 100 later (first round is 1.5 laps: 150 + 100)");

        scene.hero().getBuffManager().addBuff(new SpeedBoostBuff(2, -0.3));

        Assertions.assertEquals(70, scene.hero().getAttribute(AttributeType.SPEED).get(), EPS,
                "the sheet really is 70 (100 x (1 - 0.3))");
        Assertions.assertEquals(150 + CYCLE / 70, signalOf(scene.queue(), scene.hero()).getNextActionTime(), 1e-9,
                "the untouched cycle is re-timed to 10000 / 70 = 142.857, measured from the action it just took");
    }

    /**
     * A 50% pull moves a unit half a lap earlier when the whole cycle is still ahead of it.
     */
    @Test
    public void aFiftyPercentPullMovesTheActionHalfALapEarlier() {
        Scene scene = heroJustActed();
        double before = signalOf(scene.queue(), scene.hero()).getNextActionTime();

        Assertions.assertTrue(scene.queue().advanceActionByPercent(scene.hero(), 0.5), "the unit is on the bar");

        Assertions.assertEquals(before - SPEED / 2.0, signalOf(scene.queue(), scene.hero()).getNextActionTime(), EPS,
                "half of the 100 still to wait is skipped: half a lap");
    }

    /**
     * The pull is a fraction of the time <b>still to wait</b>, not of the whole cycle - the reading {@code ADVANCE}
     * documents, and the one that makes "advance 50%" mean different things at different points of a cycle.
     */
    @Test
    public void thePullIsAFractionOfWhatIsLeftNotOfTheWholeCycle() {
        Scene scene = heroJustActed();
        double remaining = signalOf(scene.queue(), scene.hero()).getNextActionTime() - scene.queue().getElapsed();

        scene.queue().advanceActionByPercent(scene.hero(), 0.5);
        double leftAfterOne = signalOf(scene.queue(), scene.hero()).getNextActionTime() - scene.queue().getElapsed();
        scene.queue().advanceActionByPercent(scene.hero(), 0.5);
        double leftAfterTwo = signalOf(scene.queue(), scene.hero()).getNextActionTime() - scene.queue().getElapsed();

        Assertions.assertEquals(remaining * 0.5, leftAfterOne, EPS, "the first pull halves the wait");
        Assertions.assertEquals(remaining * 0.25, leftAfterTwo, EPS,
                "and the second halves what was left - not the cycle, or it would have gone negative");
    }

    /**
     * A speed-100 hero that has just acted, so its next booking is a full cycle (see the class comment for why that
     * matters). The fixture's monster has 132 speed and therefore acts first.
     */
    private static Scene heroJustActed() {
        Character hero = character(SPEED);
        Battle battle = new Battle(List.of(hero), List.of(BattleFixture.monster()), BattleFixture.seeded());
        Queue queue = battle.queue();

        queue.move();                                   // the 132-speed monster acts first
        queue.setTopZero();
        queue.move();                                   // now the hero, at 150
        Assertions.assertSame(hero, queue.getCurrentActor().getCanHit(), "precondition: whose turn it is");
        queue.setTopZero();                             // the hero's next cycle starts from 150
        Assertions.assertEquals(250, signalOf(queue, hero).getNextActionTime(), EPS,
                "precondition: a full 100-point cycle is ahead of it");
        return new Scene(queue, hero);
    }

    private record Scene(Queue queue, Character hero) {
    }

    private static Character character(double speed) {
        Character unit = CharacterFactory.create(1001, 80);
        unit.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        return unit;
    }

    private static Signal signalOf(Queue queue, CanHit unit) {
        List<Signal> matches = queue.getHeap().stream()
                .filter(signal -> signal.getCanHit().equals(unit))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                unit.getName() + " should appear exactly once on the action bar, but appears " + matches.size());
        return matches.getFirst();
    }
}
