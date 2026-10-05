package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * Advances a battle until a named unit has <b>had one turn</b> - begun and finished.
 *
 * <p><b>Why this is a shared helper.</b> Three suites need the same moment: a buff's duration is ticked and a
 * per-turn firing limit is cleared at the start of its carrier's turn ({@code Battle.beforeMove} to 
 * {@code BuffManager} tick / {@code CanHit.tickTriggerCooldowns}), so "how many of its own turns has this
 * lasted" and "does the counter come back next turn" are both measured by driving real turns. A second copy of
 * this loop is how the two measurements drift apart.
 *
 * <p>Note: <b>A turn has to be finished for the next one to begin.</b> {@code stepForward()} only moves the clock
 * and hands over the current actor; it is {@code Queue.setTopZero()} inside {@code Battle.afterMove()} that sends
 * the finished unit to the back of the bar. Stepping twice without finishing a turn keeps returning the same
 * actor - which is exactly how the first version of this helper failed with "no turn for the requested unit".
 */
final class TestTurns {

    private TestTurns() {
    }

    /**
     * Runs turns until {@code who} is the acting unit, settles that turn's start and finishes it.
     *
     * @param battle the battle to advance
     * @param who    the unit whose turn must happen
     * @throws AssertionError when the battle ends or 40 steps pass without that unit acting - a fixture problem
     *                        (a dead unit, or a speed order the test did not expect), reported as a failure
     *                        rather than as a silent no-op
     */
    static void take(Battle battle, CanHit who) {
        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue.getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
            }
            battle.afterMove();
            if (mine) {
                return;
            }
        }
        throw new AssertionError("no turn for the requested unit within 40 steps");
    }
}
