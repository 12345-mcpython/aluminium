package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-2 acceptance: extra turns ({@code Battle.grantExtraTurn}).
 *
 * <pre>
 * An extra turn = one action handed out for free: the next stepForward() has him act,
 *                 **the clock does not move** ⇒ no action value is consumed, the round does not change,
 *                 and his normal turn schedule is left completely untouched
 * </pre>
 *
 * <p>The difference from "action advance" (拉条), which is very easy to
 * confuse, and is also the most central assertion of this case:
 * action advance pulls his **normal** turn earlier (consuming it); an extra turn is an additional one,
 * and his normal turn is still waiting for him in its original position.
 */
public class ExtraTurnTest {
    private static final double EPS = 1e-9;

    /**
     * An extra turn makes the target act immediately, and the clock does not move → the round does not change.
     */
    @Test
    public void extraTurnActsImmediatelyWithoutAdvancingTheClock() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();                                        // hero's first round 150
        q.setTopZero();                                  // hero → 250，elapsed = 150
        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS);
        int roundBefore = q.getRound();

        Assertions.assertTrue(q.grantExtraTurn(hero));
        Assertions.assertEquals(hero, q.getExtraTurnActor());

        Assertions.assertEquals(0, q.move(), EPS, "an extra turn does not advance the clock");

        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        Assertions.assertEquals(150, q.getElapsed(), EPS, "the clock is still 150");
        Assertions.assertEquals(roundBefore, q.getRound(), "the round does not change");
    }

    /**
     * The core assertion: an extra turn does **not consume** the target's normal turn schedule — after he has
     * acted, his next action point is still the original 250.
     *
     * <p>If it were implemented as "action advance to elapsed" (the wrong way), this would yield a value other
     * than 150 + 100 = 250, or it would simply eat his normal turn (the next action point pushed back).
     */
    @Test
    public void extraTurnDoesNotConsumeTheNormalTurn() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        battle.stepForward();                            // the 132-speed enemy acts first (113.64)
        battle.afterMove();
        battle.stepForward();                            // hero's first round 150
        Assertions.assertEquals(hero, battle.currentMove.getCanHit());
        battle.afterMove();                              // hero → 250

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS);

        Assertions.assertTrue(battle.grantExtraTurn(hero));
        battle.stepForward();                            // extra turn: hero, the clock does not move
        Assertions.assertEquals(hero, battle.currentMove.getCanHit());
        Assertions.assertEquals(150, q.getElapsed(), EPS, "the clock is still 150");
        battle.afterMove();                              // hero's normal schedule is pushed back to 250

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS,
                "the extra turn did not eat the normal turn: his next action point is still 250");
    }

    /**
     * Core assertion (separating "the correct implementation" from "the extra turn conveniently consumes the
     * normal turn too"): after the extra turn, **his normal turn is still in its original position** and must
     * not be displaced by this extra turn.
     *
     * <p>Setup: actor has speed 140 (first round {@code 10000/140 × 1.5 ≈ 107.14}),
     * fast has speed 200 (first round 75). When the extra turn is granted elapsed = 0, so his original
     * schedule is 107.14.
     *
     * <pre>
     *   t=0     actor takes the extra turn and acts immediately (the clock does not move)
     *   correct: actor's normal schedule is still 107.14 → fast(75) → actor(107.14)
     *   wrong (no restore): setTopZero schedules him at 0 + 71.43 = 71.43 → he would cut in ahead of fast
     * </pre>
     *
     * <p>Why this case is mandatory: in a single-character scenario "restore to the original value" and
     * "reschedule to elapsed + period" happen to be equal, so the difference cannot be observed — there must
     * be another unit whose "normal turn is earlier" to separate the two.
     */
    @Test
    public void extraTurnKeepsTheNormalTurnInItsOriginalPlace() {
        Character actor = character("actor", 140);       // first round 107.14, period 71.43
        Character fast = character("fast", 200);         // first round 75
        Queue q = new Queue(List.of(actor, fast));

        double originalSchedule = 10000.0 / 140 * 1.5;
        Assertions.assertEquals(originalSchedule, signalOf(q, actor).getNextActionTime(), 1e-9);

        Assertions.assertTrue(q.grantExtraTurn(actor));
        Assertions.assertEquals(0, q.move(), EPS, "the extra turn happens immediately at t=0");
        Assertions.assertEquals(actor, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // the period is rescheduled to 0 + 71.43

        // His original schedule (107.14) is only restored at the beginning of the next move() —
        // that is what makes it an "extra turn" rather than "his normal turn pulled earlier"
        Assertions.assertEquals(fast, nextActor(q), "fast's normal turn is at 75 and he must not be jumped by actor");
        Assertions.assertEquals(75, q.getElapsed(), EPS);

        Assertions.assertEquals(actor, nextActor(q), "actor's normal turn is still at 107.14");
        Assertions.assertEquals(originalSchedule, q.getElapsed(), 1e-9);
    }

    /**
     * There is only one extra turn: once used, normal advancement resumes.
     */
    @Test
    public void extraTurnHappensOnce() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();
        q.setTopZero();
        q.grantExtraTurn(hero);

        Assertions.assertEquals(hero, nextActor(q));
        Assertions.assertNull(q.getExtraTurnActor(), "the extra turn has been consumed");
        Assertions.assertEquals(150, q.getElapsed(), EPS);

        // Back to normal advancement: the next one has to wait a full period
        Assertions.assertEquals(100, q.move(), EPS, "normal turn: 150 → 250");
    }

    /**
     * An extra turn can let someone who is "still a long way off" act first — this is exactly the
     * queue-jumping semantics.
     */
    @Test
    public void extraTurnJumpsAheadOfTheQueue() {
        Character soon = character("soon", 200);         // first round 75, acts first
        Character late = character("late", 100);         // first round 150
        Queue q = new Queue(List.of(soon, late));

        Assertions.assertEquals(soon, nextActor(q));      // soon acts at 75

        // soon's next is at 125; late's first round is at 150. Give late an extra turn → he cuts in before 125
        Assertions.assertTrue(q.grantExtraTurn(late));
        Assertions.assertEquals(late, nextActor(q), "late jumps the queue and acts first");
        Assertions.assertEquals(75, q.getElapsed(), EPS, "the clock is still parked at 75");

        // soon's normal turn is unaffected, still at 125
        Assertions.assertEquals(soon, nextActor(q));
        Assertions.assertEquals(125, q.getElapsed(), EPS);
    }

    /**
     * Granting extra turns to the same person repeatedly is equivalent to one (they do not accumulate).
     */
    @Test
    public void repeatedGrantsStillOnlyGiveOneExtraTurn() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();
        q.setTopZero();                                  // hero → 250

        Assertions.assertTrue(q.grantExtraTurn(hero));
        Assertions.assertTrue(q.grantExtraTurn(hero), "granting it twice still returns true (he is a legal target)");

        Assertions.assertEquals(hero, nextActor(q));      // extra turn
        Assertions.assertNull(q.getExtraTurnActor());

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS,
                "the normal turn is still at 250 and was not displaced by the extra turn");
        Assertions.assertEquals(100, q.move(), EPS, "back to normal advancement: 150 → 250");
    }

    /**
     * A dead target cannot get an extra turn.
     */
    @Test
    public void deadTargetCannotGetAnExtraTurn() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        hero.takeDamage(999_999);

        Assertions.assertFalse(q.grantExtraTurn(hero));
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * A target not in the queue (not on the field) cannot get an extra turn.
     */
    @Test
    public void targetOutsideTheQueueCannotGetAnExtraTurn() {
        Character inQueue = character("inQueue", 100);
        Queue q = new Queue(List.of(inQueue));
        Character outsider = character("outsider", 100);

        Assertions.assertFalse(q.grantExtraTurn(outsider));
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * Dying after receiving an extra turn: this extra turn is voided and normal advancement continues
     * (a dead man must not jam the action bar).
     */
    @Test
    public void extraTurnIsDroppedIfTheActorDiesBeforeUsingIt() {
        Character hero = character("hero", 100);
        Character other = character("other", 100);
        Queue q = new Queue(List.of(hero, other));

        q.move();                                        // hero acts
        q.setTopZero();
        q.grantExtraTurn(hero);
        hero.takeDamage(999_999);
        q.removeCombatant(hero);                          // death cleanup

        Assertions.assertEquals(other, nextActor(q), "the dead man's extra turn is voided and it is someone else's turn");
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * Inserting **someone else's** ultimate during an extra turn is forbidden; but the one whose extra turn it
     * is may cast theirs.
     */
    @Test
    public void ultimateCannotBeInsertedDuringSomeoneElsesExtraTurn() {
        Character hero = character("hero", 100);
        Character ally = character("ally", 100);
        hero.setMaxEnergy(100);                          // fromAttributes defaults maxEnergy to 0: no energy bar means no ultimate
        ally.setMaxEnergy(100);
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(hero, ally), List.of(enemy), new Random(0));

        battle.stepForward();
        battle.afterMove();
        battle.stepForward();                            // hero or ally acts
        CanHit actor = battle.currentMove.getCanHit();
        battle.afterMove();

        battle.grantExtraTurn(actor);

        CanHit bystander = actor == hero ? ally : hero;
        bystander.gainEnergy(bystander.getMaxEnergy());
        Assertions.assertTrue(bystander.isEnergyFull(), "the bystander's energy is full (otherwise this case would test nothing)");

        Assertions.assertFalse(battle.castUltra(bystander, List.of(enemy)),
                "someone else's ultimate cannot be inserted during an extra turn");

        actor.gainEnergy(actor.getMaxEnergy());
        Assertions.assertTrue(actor.isEnergyFull());
        Assertions.assertTrue(battle.castUltra(actor, List.of(enemy)),
                "the one whose extra turn it is may cast their ultimate");
    }

    // ==================================================================

    /** {@code move()} + {@code setTopZero()}: consume one round and return the actor. */
    private static CanHit nextActor(Queue q) {
        q.move();
        CanHit actor = q.getCurrentActor().getCanHit();
        q.setTopZero();
        return actor;
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }

    private static Signal signalOf(Queue q, CanHit target) {
        List<Signal> matches = q.getHeap().stream()
                .filter(s -> s.getCanHit().equals(target))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                target.getName() + " should appear exactly once in the action bar, actually " + matches.size() + " times");
        return matches.getFirst();
    }
}
