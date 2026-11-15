package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * P-2 acceptance: extra turns ({@code Battle.grantExtraTurn}).
 *
 * <pre>
 * An extra turn = one action handed out for free: the next stepForward() has him act,
 *                 the clock does not move so no action value is consumed, the round does not change,
 *                 and his normal turn schedule is left completely untouched
 * </pre>
 *
 * <p>The difference from "action advance" (拉条, action advance), which is very easy to
 * confuse, and is also the most central assertion of this case:
 * action advance pulls his normal turn earlier (consuming it); an extra turn is an additional one,
 * and his normal turn is still waiting for him in its original position.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the four scenarios below, one {@code assertAll}
 * per scenario so a failing clause still names itself: the solo actor's free / one-shot / accumulating
 * extra turn, the two-unit queue where the extra turn cuts in without displacing the turn already
 * scheduled, the Battle-level "the normal turn is not eaten", and the ultimate that cannot be inserted
 * during someone else's extra turn. The two refusals (a dead target, a target outside the queue) and the
 * voided-turn path when the holder dies before using it stay their own tests: a rejection path is not a
 * positive clause and must not hide behind one.
 */
public class ExtraTurnTest {
    private static final double EPS = 1e-9;

    /**
     * The solo actor's extra turn, on one fixture: it is handed out at once and costs no action value (the
     * clock does not move, so the round does not change either); exactly one is owed per grant, so a single
     * turn is used up and normal advancement resumes; and two grants are two extra turns, both free, with
     * the normal turn still waiting at 250.
     *
     * <p>Why one scenario: the three clauses stand on the same fixture ({@code character(name, 100)} in a
     * one-unit queue, already past their first round) and the same observables ({@code q.getElapsed()},
     * {@code q.getRound()}, {@code q.getExtraTurnActor()} and the actor's {@code nextActionTime}). How many
     * grants are given is the clause, not the fixture, so each clause is driven on its own instance and
     * every clause names itself in the {@code assertAll}.
     *
     * <p>Note: the accumulate clause said the opposite until 1506 Silver Wolf LV.999's Eidolon 2 was
     * written: 「每使【隐藏分】提高 120 点…获得 1 个额外回合」 counts one per milestone, and one move can
     * cross two, so a second grant that overwrote the first would pay one where the document counts two.
     * What must NOT change is that clause's other half - both are FREE turns, and the normal turn is still
     * waiting at 250.
     */
    @Test
    public void extraTurnOnTheSoloActorIsFreeOneShotAndAccumulates() {
        // One grant: the extra turn acts immediately, and neither the clock nor the round moves.
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));
        q.move();                                        // hero's first round 150
        q.setTopZero();                                  // hero to 250，elapsed = 150
        int roundBefore = q.getRound();
        double scheduleBeforeGrant = signalOf(q, hero).getNextActionTime();
        boolean granted = q.grantExtraTurn(hero);
        CanHit pendingActor = q.getExtraTurnActor();
        double clockDuringExtraTurn = q.move();
        CanHit actingNow = q.getCurrentActor().getCanHit();
        double clockAfterExtraTurn = q.getElapsed();
        int roundAfterExtraTurn = q.getRound();

        // One grant, played out to the end of the free action: it has been used up, and back-to-normal
        // advancement resumes.
        Character oneShotHero = character("hero", 100);
        Queue oneShotQueue = new Queue(List.of(oneShotHero));
        oneShotQueue.move();
        oneShotQueue.setTopZero();
        oneShotQueue.grantExtraTurn(oneShotHero);
        CanHit oneShotActor = nextActor(oneShotQueue);
        CanHit owedAfterOneShot = oneShotQueue.getExtraTurnActor();
        double oneShotClock = oneShotQueue.getElapsed();
        double oneShotAdvance = oneShotQueue.move();

        // Two grants to the same unit: they are TWO extra turns, and neither of them eats the normal turn.
        Character twiceHero = character("hero", 100);
        Queue twiceQueue = new Queue(List.of(twiceHero));
        twiceQueue.move();
        twiceQueue.setTopZero();
        boolean firstGrant = twiceQueue.grantExtraTurn(twiceHero);
        boolean secondGrant = twiceQueue.grantExtraTurn(twiceHero);
        CanHit firstExtraTurnActor = nextActor(twiceQueue);
        CanHit owedAfterFirst = twiceQueue.getExtraTurnActor();
        double clockAfterFirst = twiceQueue.getElapsed();
        CanHit secondExtraTurnActor = nextActor(twiceQueue);
        CanHit owedAfterSecond = twiceQueue.getExtraTurnActor();
        double clockAfterSecond = twiceQueue.getElapsed();
        double normalTurnTime = signalOf(twiceQueue, twiceHero).getNextActionTime();
        double advanceAfterTwoExtraTurns = twiceQueue.move();

        Assertions.assertAll(
                () -> Assertions.assertEquals(250, scheduleBeforeGrant, EPS,
                        "one round in, the solo actor's normal turn is at 250"),
                () -> Assertions.assertTrue(granted, "the grant is accepted"),
                () -> Assertions.assertEquals(hero, pendingActor, "hero owes the extra turn"),
                () -> Assertions.assertEquals(0, clockDuringExtraTurn, EPS,
                        "an extra turn does not advance the clock"),
                () -> Assertions.assertEquals(hero, actingNow, "the extra turn acts at once: hero"),
                () -> Assertions.assertEquals(150, clockAfterExtraTurn, EPS, "the clock is still 150"),
                () -> Assertions.assertEquals(roundBefore, roundAfterExtraTurn, "the round does not change"),
                () -> Assertions.assertEquals(oneShotHero, oneShotActor, "the extra turn is taken by hero"),
                () -> Assertions.assertNull(owedAfterOneShot, "the extra turn has been consumed"),
                () -> Assertions.assertEquals(150, oneShotClock, EPS,
                        "one free turn leaves the clock at 150"),
                () -> Assertions.assertEquals(100, oneShotAdvance, EPS, "normal turn: 150 → 250"),
                () -> Assertions.assertTrue(firstGrant, "the first grant is accepted"),
                () -> Assertions.assertTrue(secondGrant,
                        "granting it twice still returns true (he is a legal target)"),
                () -> Assertions.assertEquals(twiceHero, firstExtraTurnActor,
                        "the first extra turn is taken by hero"),
                () -> Assertions.assertEquals(twiceHero, owedAfterFirst,
                        "the second is still owed - the second grant did not replace the first"),
                () -> Assertions.assertEquals(150, clockAfterFirst, EPS,
                        "and neither of them moved the clock"),
                () -> Assertions.assertEquals(twiceHero, secondExtraTurnActor,
                        "the second extra turn is taken by hero"),
                () -> Assertions.assertNull(owedAfterSecond, "now there is none left"),
                () -> Assertions.assertEquals(150, clockAfterSecond, EPS, "still no clock movement"),
                () -> Assertions.assertEquals(250, normalTurnTime, EPS,
                        "the normal turn is still at 250 and was not displaced by either extra turn"),
                () -> Assertions.assertEquals(100, advanceAfterTwoExtraTurns, EPS,
                        "back to normal advancement: 150 → 250"));
    }

    /**
     * An extra turn cuts in without displacing a turn that is already scheduled - and it does not "consume"
     * the actor's own normal turn either: once the free turn is over, his normal turn is still waiting at
     * its original point.
     *
     * <p>Why one scenario: both clauses stand on the same fixture, a two-unit queue of {@code character}
     * pairs with one unit already scheduled ahead of the other, and the same observables - who acts next and
     * {@code q.getElapsed()} at each acting point. The speed pair and the moment of the grant are the
     * clause, not the fixture, so each clause is driven on its own instance and every clause names itself in
     * the {@code assertAll}.
     *
     * <p>Setup of the first clause: actor has speed 140 (first round {@code 10000/140 x 1.5 ~= 10.14}),
     * fast has speed 200 (first round 5). When the extra turn is granted elapsed = 0, so his original
     * schedule is 10.14.
     *
     * <pre>
     *   t=0     actor takes the extra turn and acts immediately (the clock does not move)
     *   correct: actor's normal schedule is still 10.14 to fast(5) to actor(10.14)
     *   wrong (no restore): setTopZero schedules him at 0 + 1.43 = 1.43 to he would cut in ahead of fast
     * </pre>
     *
     * <p>Why this case is mandatory: in a single-character scenario "restore to the original value" and
     * "reschedule to elapsed + period" happen to be equal, so the difference cannot be observed - there must
     * be another unit whose "normal turn is earlier" to separate the two.
     *
     * <p>Second clause: soon's next turn is at 125 and late's first round is at 150, so an extra turn for
     * late cuts in before 125 while soon's 125 stays exactly where it was.
     */
    @Test
    public void extraTurnCutsInWithoutDisplacingTheScheduledTurn() {
        // Granted at t=0 to the 140-speed unit, with the 200-speed unit ahead: it acts immediately, and
        // after the free turn both the fast unit's 75 and the actor's own 107.14 are still where they were.
        Character actor = character("actor", 140);       // first round 10.14, period 1.43
        Character fast = character("fast", 200);         // first round 5
        Queue q = new Queue(List.of(actor, fast));

        double originalSchedule = 10000.0 / 140 * 1.5;
        double scheduleBeforeGrant = signalOf(q, actor).getNextActionTime();
        boolean granted = q.grantExtraTurn(actor);
        double cutInClock = q.move();
        CanHit actingNow = q.getCurrentActor().getCanHit();
        q.setTopZero();                                  // the period is rescheduled to 0 + 1.43
        // His original schedule (10.14) is only restored at the beginning of the next move() - that is what
        // makes it an "extra turn" rather than "his normal turn pulled earlier".
        CanHit afterRestore = nextActor(q);
        double fastClock = q.getElapsed();
        CanHit actorNormalTurn = nextActor(q);
        double restoredClock = q.getElapsed();

        // Granted mid-flight to the 100-speed unit behind a unit that is already scheduled: it cuts in at 75,
        // and the unit it jumped past still acts at 125.
        Character soon = character("soon", 200);         // first round 5, acts first
        Character late = character("late", 100);         // first round 150
        Queue lateQueue = new Queue(List.of(soon, late));

        CanHit firstActor = nextActor(lateQueue);         // soon acts at 5
        boolean lateGranted = lateQueue.grantExtraTurn(late);
        CanHit jumped = nextActor(lateQueue);
        double jumpClock = lateQueue.getElapsed();
        CanHit soonAgain = nextActor(lateQueue);
        double soonClock = lateQueue.getElapsed();

        Assertions.assertAll(
                () -> Assertions.assertEquals(originalSchedule, scheduleBeforeGrant, 1e-9,
                        "before the grant, actor's normal turn is 107.14"),
                () -> Assertions.assertTrue(granted, "the extra turn is accepted"),
                () -> Assertions.assertEquals(0, cutInClock, EPS,
                        "the extra turn happens immediately at t=0"),
                () -> Assertions.assertEquals(actor, actingNow, "the extra turn acts as actor"),
                () -> Assertions.assertEquals(fast, afterRestore,
                        "fast's normal turn is at 75 and he must not be jumped by actor"),
                () -> Assertions.assertEquals(75, fastClock, EPS, "the clock is 75 at fast's turn"),
                () -> Assertions.assertEquals(actor, actorNormalTurn,
                        "actor's normal turn is still at 107.14"),
                () -> Assertions.assertEquals(originalSchedule, restoredClock, 1e-9,
                        "the clock is back on actor's original 107.14, not the 0 + 1.43 reschedule"),
                () -> Assertions.assertEquals(soon, firstActor, "soon's first-round turn comes first"),
                () -> Assertions.assertTrue(lateGranted, "late's extra turn is accepted"),
                () -> Assertions.assertEquals(late, jumped, "late jumps the queue and acts first"),
                () -> Assertions.assertEquals(75, jumpClock, EPS, "the clock is still parked at 75"),
                () -> Assertions.assertEquals(soon, soonAgain, "soon's normal turn is unaffected"),
                () -> Assertions.assertEquals(125, soonClock, EPS, "soon's normal turn is still at 125"));
    }

    /**
     * The Battle-level core assertion: an extra turn does not consume the target's normal turn schedule -
     * after he has acted, his next action point is still the original 250.
     *
     * <p>If it were implemented as "action advance to elapsed" (the wrong way), this would yield a value
     * other than 150 + 100 = 250, or it would simply eat his normal turn (the next action point pushed back).
     */
    @Test
    public void extraTurnInBattleDoesNotConsumeTheNormalTurn() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), BattleFixture.seeded());
        Queue q = battle.queue();

        battle.stepForward();                            // the 132-speed enemy acts first (113.64)
        battle.afterMove();
        battle.stepForward();                            // hero's first round 150
        CanHit firstHeroAction = battle.currentMove().getCanHit();
        battle.afterMove();                              // hero to 250
        double scheduleAfterNormalTurn = signalOf(q, hero).getNextActionTime();
        boolean granted = battle.grantExtraTurn(hero);
        battle.stepForward();                            // extra turn: hero, the clock does not move
        CanHit extraTurnActor = battle.currentMove().getCanHit();
        double clockDuringExtraTurn = q.getElapsed();
        battle.afterMove();                              // hero's normal schedule is pushed back to 250
        double scheduleAfterExtraTurn = signalOf(q, hero).getNextActionTime();

        Assertions.assertAll(
                () -> Assertions.assertEquals(hero, firstHeroAction, "hero is the one acting at 150"),
                () -> Assertions.assertEquals(250, scheduleAfterNormalTurn, EPS,
                        "hero's normal turn sits at 250"),
                () -> Assertions.assertTrue(granted, "the extra turn is accepted"),
                () -> Assertions.assertEquals(hero, extraTurnActor, "the extra turn acts as hero"),
                () -> Assertions.assertEquals(150, clockDuringExtraTurn, EPS, "the clock is still 150"),
                () -> Assertions.assertEquals(250, scheduleAfterExtraTurn, EPS,
                        "the extra turn did not eat the normal turn: his next action point is still 250"));
    }

    /**
     * Inserting someone else's ultimate during an extra turn is forbidden; but the one whose extra turn it
     * is may cast theirs.
     */
    @Test
    public void ultimateCannotBeInsertedDuringSomeoneElsesExtraTurn() {
        Character hero = character("hero", 100);
        Character ally = character("ally", 100);
        hero.setMaxEnergy(100);                          // fromAttributes defaults maxEnergy to 0: no energy bar means no ultimate
        ally.setMaxEnergy(100);
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(hero, ally), List.of(enemy), BattleFixture.seeded());

        battle.stepForward();
        battle.afterMove();
        battle.stepForward();                            // hero or ally acts
        CanHit actor = battle.currentMove().getCanHit();
        battle.afterMove();

        battle.grantExtraTurn(actor);

        CanHit bystander = actor == hero ? ally : hero;
        bystander.gainEnergy(bystander.getMaxEnergy());
        boolean bystanderFull = bystander.isEnergyFull();
        boolean bystanderUltra = battle.castUltra(bystander, List.of(enemy));
        actor.gainEnergy(actor.getMaxEnergy());
        boolean actorFull = actor.isEnergyFull();
        boolean actorUltra = battle.castUltra(actor, List.of(enemy));

        Assertions.assertAll(
                () -> Assertions.assertTrue(bystanderFull,
                        "the bystander's energy is full (otherwise this case would test nothing)"),
                () -> Assertions.assertFalse(bystanderUltra,
                        "someone else's ultimate cannot be inserted during an extra turn"),
                () -> Assertions.assertTrue(actorFull, "the extra-turn owner's energy is full"),
                () -> Assertions.assertTrue(actorUltra,
                        "the one whose extra turn it is may cast their ultimate"));
    }

    /**
     * A dead target cannot get an extra turn.
     */
    @Test
    public void deadTargetCannotGetAnExtraTurn() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        hero.takeDamage(999_999);

        boolean granted = q.grantExtraTurn(hero);
        CanHit pendingActor = q.getExtraTurnActor();

        Assertions.assertAll(
                () -> Assertions.assertFalse(granted, "a dead target is refused an extra turn"),
                () -> Assertions.assertNull(pendingActor, "and no extra turn is left pending"));
    }

    /**
     * A target not in the queue (not on the field) cannot get an extra turn.
     */
    @Test
    public void targetOutsideTheQueueCannotGetAnExtraTurn() {
        Character inQueue = character("inQueue", 100);
        Queue q = new Queue(List.of(inQueue));
        Character outsider = character("outsider", 100);

        boolean granted = q.grantExtraTurn(outsider);
        CanHit pendingActor = q.getExtraTurnActor();

        Assertions.assertAll(
                () -> Assertions.assertFalse(granted, "a target outside the queue is refused an extra turn"),
                () -> Assertions.assertNull(pendingActor, "and no extra turn is left pending"));
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

        CanHit next = nextActor(q);
        CanHit pendingActor = q.getExtraTurnActor();

        Assertions.assertAll(
                () -> Assertions.assertEquals(other, next,
                        "the dead man's extra turn is voided and it is someone else's turn"),
                () -> Assertions.assertNull(pendingActor, "the voided extra turn is no longer pending"));
    }

    // ==================================================================

    /**
     * {@code move()} + {@code setTopZero()}: consume one round and return the actor.
     */
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
