package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-3 acceptance: the win/loss state machine.
 *
 * <pre>
 *   NOT_STARTED ──startBattle()──▶ RUNNING ──one side wiped out──▶ WIN / LOSE (terminal, never reverted)
 * </pre>
 *
 * <p>Key points:
 * <ul>
 *   <li>After a terminal state {@code stepForward()} **no longer advances** the action bar;</li>
 *   <li>While {@code NOT_STARTED} no win/loss is judged (the battle has not begun, so there is no
 *   win or loss to speak of);</li>
 *   <li>An **empty list** on one side also counts as "wiped out" (cleared out).</li>
 * </ul>
 */
public class BattleResultTest {
    private static final double EPS = 1e-9;

    @Test
    public void statusStartsAsNotStarted() {
        Battle battle = newBattle();

        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.getStatus());
        Assertions.assertFalse(battle.isOver(), "it is not over before the battle has started");
    }

    @Test
    public void startBattleMovesToRunning() {
        Battle battle = newBattle();

        battle.startBattle();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus());
        Assertions.assertFalse(battle.isOver());
    }

    /**
     * All enemies wiped out → WIN, and after the terminal state the action bar no longer advances.
     */
    @Test
    public void wipingOutTheEnemiesWinsAndStopsTheClock() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();                     // public entry point: triggers death cleanup + judgment

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
        Assertions.assertTrue(battle.isOver());

        double elapsedBefore = battle.queue.getElapsed();
        battle.stepForward();
        battle.stepForward();

        Assertions.assertEquals(elapsedBefore, battle.queue.getElapsed(), EPS,
                "after a terminal state stepForward() no longer advances the clock");
        Assertions.assertNull(battle.currentMove, "and nobody is at their action point");
    }

    /**
     * Our side wiped out → LOSE.
     */
    @Test
    public void wipingOutThePartyLoses() {
        Battle battle = newBattle();
        battle.startBattle();

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, battle.getStatus());
        Assertions.assertTrue(battle.isOver());
    }

    /**
     * Both sides wiped out at the same time → LOSE (loss is judged before win).
     *
     * <p>This rule was set because "at the same time" must have a definite outcome and must not
     * waver with the order of judgment.
     */
    @Test
    public void mutualDestructionIsALoss() {
        Battle battle = newBattle();
        battle.startBattle();

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, battle.getStatus());
    }

    /**
     * Before the battle starts ({@code NOT_STARTED}), not even a full wipe is judged a loss — the
     * battle has not begun.
     */
    @Test
    public void nothingIsJudgedBeforeTheBattleStarts() {
        Battle battle = newBattle();
        battle.enemies.getFirst().takeDamage(999_999);

        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.checkResult());
        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.getStatus());
    }

    /**
     * No reverting from a terminal state: killing someone after a win has already been judged does
     * not turn it into a LOSE.
     */
    @Test
    public void theResultIsFinal() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();
        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus(), "the result is decided, it is not re-judged");
    }

    /**
     * An empty side also counts as wiped out: a battle with no enemies is won at the start.
     */
    @Test
    public void anEmptySideCountsAsWipedOut() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(), new Random(0));

        battle.startBattle();

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
    }

    /**
     * After one battle finishes the status is terminal, and starting another is independent of it
     * (the status is not static).
     */
    @Test
    public void statusIsPerBattleInstance() {
        Battle first = newBattle();
        first.startBattle();
        first.enemies.getFirst().takeDamage(999_999);
        first.processRequests();
        Assertions.assertEquals(Battle.Status.WIN, first.getStatus());

        Battle second = newBattle();

        Assertions.assertEquals(Battle.Status.NOT_STARTED, second.getStatus(),
                "the new battle's status is unaffected by the previous one");
    }

    /**
     * Fighting a normal battle: the status is RUNNING in the middle and WIN at the end.
     */
    @Test
    public void statusIsRunningWhileTheBattleGoesOn() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.stepForward();
        battle.afterMove();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "it stays RUNNING until the outcome is decided");
        Assertions.assertFalse(battle.isOver());
    }

    // ==================================================================

    private static Battle newBattle() {
        return new Battle(List.of(character("hero", 100)),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }
}
