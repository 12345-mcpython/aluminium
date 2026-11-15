package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * P-3 acceptance: the win/loss state machine.
 *
 * <pre>
 *   NOT_STARTED --startBattle()--RUNNING --one side wiped out--WIN / LOSE (terminal, never reverted)
 * </pre>
 *
 * <p>Key points:
 * <ul>
 *   <li>After a terminal state {@code stepForward()} no longer advances the action bar;</li>
 *   <li>While {@code NOT_STARTED} no win/loss is judged (the battle has not begun, so there is no
 *   win or loss to speak of);</li>
 *   <li>An empty list on one side also counts as "wiped out" (cleared out).</li>
 * </ul>
 */
public class BattleResultTest {
    private static final double EPS = 1e-9;

    /**
     * One scenario for the status at the start of a battle: a fresh battle is {@code NOT_STARTED} and not
     * over, {@code startBattle()} moves it to {@code RUNNING}, and an ordinary move leaves it {@code RUNNING}.
     *
     * <p>One scenario because all three clauses read the same object of this class's one fixture - its
     * status and {@code isOver()} - at successive points of the same lifecycle; nothing but the status moves
     * between them.
     */
    @Test
    public void statusMovesFromNotStartedToRunningAndStaysRunning() {
        Battle battle = newBattle();
        Battle.Status beforeStart = battle.getStatus();
        boolean overBeforeStart = battle.isOver();

        battle.startBattle();
        Battle.Status afterStart = battle.getStatus();
        boolean overAfterStart = battle.isOver();

        battle.stepForward();
        battle.afterMove();
        Battle.Status midBattle = battle.getStatus();
        boolean overMidBattle = battle.isOver();

        Assertions.assertAll(
                () -> Assertions.assertEquals(Battle.Status.NOT_STARTED, beforeStart,
                        "before startBattle() the status is NOT_STARTED"),
                () -> Assertions.assertFalse(overBeforeStart, "it is not over before the battle has started"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, afterStart,
                        "startBattle() moves it to RUNNING"),
                () -> Assertions.assertFalse(overAfterStart, "a battle that has just started is not over"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, midBattle,
                        "it stays RUNNING until the outcome is decided"),
                () -> Assertions.assertFalse(overMidBattle,
                        "one ordinary move does not decide the outcome"));
    }

    /**
     * One scenario for the winning judgement: all enemies wiped out is a {@code WIN}, a terminal state after
     * which the action bar no longer advances - and killing our side afterwards does not re-judge it.
     *
     * <p>One scenario because both clauses stand on this class's one fixture and on the same premise (enemies
     * wiped out, one {@code processRequests()} judgement); the second clause only adds the party wipe to prove
     * the already-decided result is not re-judged.
     */
    @Test
    public void wipingOutTheEnemiesWinsAndTheWinIsTerminal() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.enemies().getFirst().takeDamage(999_999);
        battle.processRequests();                     // public entry point: triggers death cleanup + judgment
        Battle.Status afterWipe = battle.getStatus();
        boolean overAfterWipe = battle.isOver();

        double elapsedBefore = battle.queue().getElapsed();
        battle.stepForward();
        battle.stepForward();
        double elapsedAfter = battle.queue().getElapsed();
        var moveAfterTerminal = battle.currentMove();
        Battle.Status beforePartyWipe = battle.getStatus();

        for (Character c : battle.characters()) {
            c.takeDamage(999_999);
        }
        battle.processRequests();
        Battle.Status afterPartyWipe = battle.getStatus();

        Assertions.assertAll(
                () -> Assertions.assertEquals(Battle.Status.WIN, afterWipe,
                        "all enemies wiped out means WIN"),
                () -> Assertions.assertTrue(overAfterWipe, "a WIN is a terminal state, so it is over"),
                () -> Assertions.assertEquals(Battle.Status.WIN, beforePartyWipe,
                        "the WIN already stands when our side falls"),
                () -> Assertions.assertEquals(elapsedBefore, elapsedAfter, EPS,
                        "after a terminal state stepForward() no longer advances the clock"),
                () -> Assertions.assertNull(moveAfterTerminal, "and nobody is at their action point"),
                () -> Assertions.assertEquals(Battle.Status.WIN, afterPartyWipe,
                        "the result is decided, it is not re-judged"));
    }

    /**
     * One scenario for the losing judgement: our side wiped out is a {@code LOSE}, and both sides wiped out at
     * the same instant is a {@code LOSE} too - loss is judged before win.
     *
     * <p>One scenario because both clauses use this class's one fixture and read the same object, the status
     * after {@code processRequests()}; the second clause only adds the enemy wipe to the same premise, and the
     * rule exists because "at the same time" must have a definite outcome that does not waver with the order
     * of judgement.
     */
    @Test
    public void wipingOutThePartyLosesEvenWhenTheEnemiesFallToo() {
        Battle partyWiped = newBattle();
        partyWiped.startBattle();
        for (Character c : partyWiped.characters()) {
            c.takeDamage(999_999);
        }
        partyWiped.processRequests();
        Battle.Status partyWipedStatus = partyWiped.getStatus();
        boolean partyWipedOver = partyWiped.isOver();

        Battle bothWiped = newBattle();
        bothWiped.startBattle();
        for (Character c : bothWiped.characters()) {
            c.takeDamage(999_999);
        }
        bothWiped.enemies().getFirst().takeDamage(999_999);
        bothWiped.processRequests();
        Battle.Status bothWipedStatus = bothWiped.getStatus();

        Assertions.assertAll(
                () -> Assertions.assertEquals(Battle.Status.LOSE, partyWipedStatus,
                        "our side wiped out means LOSE"),
                () -> Assertions.assertTrue(partyWipedOver, "a LOSE is a terminal state, so it is over"),
                () -> Assertions.assertEquals(Battle.Status.LOSE, bothWipedStatus,
                        "both sides wiped out at the same time is a LOSE: loss is judged before win"));
    }

    /**
     * Before the battle starts ({@code NOT_STARTED}), not even a full wipe is judged a loss - the battle has
     * not begun, so there is no outcome to speak of.
     */
    @Test
    public void nothingIsJudgedBeforeTheBattleStarts() {
        Battle battle = newBattle();
        battle.enemies().getFirst().takeDamage(999_999);
        Battle.Status judged = battle.checkResult();
        Battle.Status status = battle.getStatus();

        Assertions.assertAll(
                () -> Assertions.assertEquals(Battle.Status.NOT_STARTED, judged,
                        "checkResult() judges nothing while the battle is NOT_STARTED"),
                () -> Assertions.assertEquals(Battle.Status.NOT_STARTED, status,
                        "and the status is left at NOT_STARTED"));
    }

    /**
     * The status is per battle instance, not static: a battle that has been won does not leak its terminal
     * state into the next one.
     */
    @Test
    public void statusIsPerBattleInstance() {
        Battle first = newBattle();
        first.startBattle();
        first.enemies().getFirst().takeDamage(999_999);
        first.processRequests();
        Battle.Status firstStatus = first.getStatus();

        Battle second = newBattle();
        Battle.Status secondStatus = second.getStatus();

        Assertions.assertAll(
                () -> Assertions.assertEquals(Battle.Status.WIN, firstStatus,
                        "the first battle is won"),
                () -> Assertions.assertEquals(Battle.Status.NOT_STARTED, secondStatus,
                        "the new battle's status is unaffected by the previous one"));
    }

    /**
     * An empty side also counts as wiped out: a battle with no enemies is won at the start.
     */
    @Test
    public void anEmptySideCountsAsWipedOut() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(), BattleFixture.seeded());

        battle.startBattle();
        Battle.Status status = battle.getStatus();

        Assertions.assertEquals(Battle.Status.WIN, status,
                "an empty enemy side counts as wiped out, so the battle is won at the start");
    }

    // ==================================================================

    private static Battle newBattle() {
        return BattleFixture.battle(List.of(character("hero", 100)), List.of(EnemyFactory.create(1002011, 90, 1)));
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }
}
