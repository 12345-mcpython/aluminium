package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.CanHit;

/**
 * Observes a driven battle, one turn at a time, without being able to change it.
 *
 * <p><b>Why this exists rather than printing in the driver.</b> {@link BattleDriver} owns the turn
 * cycle, so a front end can no longer run its own loop around it -- but a front end still has to
 * narrate: which round this is, what was chosen, how much damage landed. Giving the driver a listener
 * keeps both halves honest: the driver decides nothing about presentation, and the front end cannot
 * skip a turn or re-time the action bar. Every method is a notification; none returns a value the
 * driver acts on.
 *
 * <p>All methods are default no-ops, so an implementation overrides only what it needs. A run with no
 * listener produces no output at all, which is what makes a battle assertable in a test.
 */
public interface BattleListener {

    /**
     * The battle has started and its opening hooks have run.
     *
     * @param driver the driver, for reading the battle
     */
    default void onBattleStart(BattleDriver driver) {
    }

    /**
     * A turn is about to begin: the action bar has advanced and the turn's start has been processed.
     *
     * <p>Called even for a turn that will not be acted out -- the actor may have died to damage over
     * time, or be under a control that forbids acting. A listener that reports "X acts" here would
     * sometimes be wrong; {@link #onAction} is where acting is reported.
     *
     * @param actor the unit whose turn this is
     * @param turn  1-based count of actor-turns taken so far, including this one
     */
    default void onTurnStart(CanHit actor, int turn) {
    }

    /**
     * The policy produced a decision and it is about to be cast.
     *
     * <p>Not called when the policy declined, or when the actor was unable to act.
     *
     * @param actor    the acting unit
     * @param decision what it chose
     */
    default void onAction(CanHit actor, SkillPolicy.Decision decision) {
    }

    /**
     * The turn has ended.
     *
     * <p>Called for every turn that reached {@code beforeMove}, including turns where nothing was cast,
     * so a listener pairing work with {@link #onTurnStart} sees a matching end.
     *
     * @param actor the unit whose turn it was
     * @param turn  the same 1-based count given to {@link #onTurnStart}
     */
    default void onTurnEnd(CanHit actor, int turn) {
    }

    /**
     * The battle reached a terminal state or the turn ceiling.
     *
     * @param result how it ended
     */
    default void onBattleEnd(BattleDriver.Result result) {
    }
}
