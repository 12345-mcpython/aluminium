package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;

import java.util.Objects;

/**
 * Plays one battle turn, in the one order the engine requires.
 *
 * <p><b>Why this exists.</b> {@link Battle} exposes the four turn primitives
 * ({@code stepForward}, {@code beforeMove}, {@code afterMove}, {@code processRequests}) because a
 * front end may need to inspect the battle between them. Those primitives do not say when a turn
 * <i>starts</i>, when the actor may act, or when queued effects are settled; that order was written
 * out by hand in every caller. Three callers already existed ({@code BattleDriver}, the demo, and
 * the TUI), and the driven one had shipped three defects that were really "the caller called the
 * primitives in a different order" (a free ultimate, a skill point charged twice, a broken enemy
 * acting anyway). This class is that order, once:
 *
 * <pre>
 *   stepForward          -- advance the action bar, exposing the current actor
 *   onTurnStart
 *   beforeMove           -- damage over time, buff clocks, TURN_START
 *   action.act           -- the caller's decision and cast
 *   afterMove            -- re-time the bar, clear the dead, settle queued effects
 *   onTurnEnd
 * </pre>
 *
 * <p><b>What it does not own.</b> The decision itself. A caller supplies an {@link Action}; the
 * canonical policy-driven action is {@link #policyDriven}. Keeping the action pluggable is what lets
 * a test stop after one turn, a demo keep its own narration, and a UI play one side.
 *
 * <p>The loop never calls {@code processRequests} itself: {@code afterMove} does, which is the same
 * settlement point every existing turn used. A caller that settles early (the demo does, to print
 * numbers) still gets an empty {@code processRequests} at the end rather than a second settlement.
 */
public final class TurnLoop {

    /**
     * What happens between the turn's start and its end.
     *
     * <p>An action must cast through {@link Battle}'s public entry points (for example
     * {@code performAction} or {@code castUltra}) and must not call {@code stepForward},
     * {@code beforeMove} or {@code afterMove}: those boundaries belong to this loop.
     */
    @FunctionalInterface
    public interface Action {

        /**
         * Decide and perform the current actor's turn.
         *
         * <p>Called after {@code beforeMove}, so damage over time has already settled and the actor
         * may already be dead. An action that needs the actor alive must check
         * {@link CanHit#isDeath()} itself; the loop still closes the turn either way.
         *
         * @param battle the battle in progress
         * @param actor  the unit whose turn it is
         * @param turn   the 1-based turn number, or a non-positive value when none is reported
         */
        void act(Battle battle, CanHit actor, int turn);
    }

    /**
     * Told when a turn opens and when it closes.
     *
     * <p>Both methods have no-op defaults. The start fires after the action bar has advanced and
     * before {@code beforeMove}; the end fires after {@code afterMove}, so a listener pairing the two
     * always sees a match.
     */
    public interface Listener {

        /**
         * A turn is about to begin.
         *
         * @param actor the unit whose turn it is
         * @param turn  the 1-based turn number
         */
        default void onTurnStart(CanHit actor, int turn) {
        }

        /**
         * The turn has ended.
         *
         * @param actor the unit whose turn it was
         * @param turn  the same 1-based turn number given to the start
         */
        default void onTurnEnd(CanHit actor, int turn) {
        }
    }

    private final Battle battle;
    private final Action action;
    private final Listener listener;

    /**
     * A loop with no listener.
     *
     * @param battle the battle to play
     * @param action what to do inside each turn
     */
    public TurnLoop(Battle battle, Action action) {
        this(battle, action, new Listener() {
        });
    }

    /**
     * @param battle   the battle to play
     * @param action   what to do inside each turn
     * @param listener told when each turn opens and closes
     */
    public TurnLoop(Battle battle, Action action, Listener listener) {
        this.battle = Objects.requireNonNull(battle, "battle");
        this.action = Objects.requireNonNull(action, "action");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * The canonical policy-driven loop: the action asks a {@link SkillPolicy} and casts its decision.
     *
     * <p>This is the loop {@link BattleDriver} uses. It is also the reference behaviour a new front
     * end should start from: an ultimate goes through {@link Battle#castUltra}, everything else
     * through {@link Battle#performAction}, and a broken or controlled unit spends the turn without
     * acting.
     *
     * @param battle   the battle to play
     * @param policy   what decides each unit's action
     * @param listener the listener that receives the per-turn callbacks
     * @return a loop that plays the battle with {@code policy}
     */
    public static TurnLoop policyDriven(Battle battle, SkillPolicy policy, BattleListener listener) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(listener, "listener");
        return new TurnLoop(battle,
                (b, actor, turn) -> actOnPolicy(b, actor, turn, policy, listener),
                new Listener() {
                    @Override
                    public void onTurnStart(CanHit actor, int turn) {
                        listener.onTurnStart(actor, turn);
                    }

                    @Override
                    public void onTurnEnd(CanHit actor, int turn) {
                        listener.onTurnEnd(actor, turn);
                    }
                });
    }

    /**
     * Plays exactly one turn.
     *
     * @param turn the 1-based turn number to report, or a non-positive value to report none
     * @return {@code false} when no unit could act, so the caller stops
     */
    public boolean step(int turn) {
        battle.stepForward();
        Signal current = battle.queue.getCurrentActor();
        if (current == null) {
            return false;
        }
        CanHit actor = current.getCanHit();
        if (actor == null) {
            return false;
        }

        if (turn > 0) {
            listener.onTurnStart(actor, turn);
        }
        battle.beforeMove();
        action.act(battle, actor, turn);
        battle.afterMove();
        if (turn > 0) {
            listener.onTurnEnd(actor, turn);
        }
        return true;
    }

    /**
     * The policy-driven action: skip when the unit may not act, otherwise ask the policy and cast.
     *
     * <p>Kept as a static method rather than hidden in a lambda so the skip rules are readable in
     * one place and can be compared with {@link Battle#handleBrokenTurn}.
     */
    private static void actOnPolicy(Battle battle, CanHit actor, int turn, SkillPolicy policy,
                                    BattleListener listener) {
        // The turn still has to end even when the actor cannot use it: damage over time may have killed
        // it, and a control may forbid acting. `afterMove` is what re-times the bar and removes the dead,
        // so skipping it would leave the queue inconsistent rather than merely skipping an action.
        boolean mayAct = !actor.isDeath();

        // The BROKEN check comes FIRST, before `canAct()`, and the order is load-bearing.
        //
        // `handleBrokenTurn` is what ticks a broken unit's countdown and, at zero, recovers it -- and it must run
        // whether or not the unit could otherwise act. Evaluating `canAct()` first meant that a unit which was broken
        // AND blocked by a control never reached the branch: its countdown never ticked, so it stayed broken and silent
        // for the rest of the battle. Measured, one unit frozen at its initial count for seventeen consecutive turns
        // while the fight went on around it.
        boolean wasBroken = !actor.isDeath() && actor instanceof Enemy foe && foe.isBroken();
        if (!actor.isDeath() && actor instanceof Enemy broken && broken.isBroken()) {
            // The answer is "is this turn skipped", and that is exactly the complement of "may it act".
            // The turn that ENDS a break answers false and the unit acts again on it.
            boolean skippedByTheBreak = battle.handleBrokenTurn(broken);
            mayAct = !skippedByTheBreak;
            if (skippedByTheBreak) {
                battle.announceTurnSkipped(actor, true);
            } else {
                // the countdown reached zero: this is the turn the unit recovers and acts again
                listener.onBreakRecovered(actor);
            }
        } else if (!actor.isDeath()) {
            mayAct = actor.getBuffManager().canAct();
        }
        if (!mayAct && !wasBroken) {
            // not broken, so the reason is a control
            battle.announceTurnSkipped(actor, false);
        }
        if (mayAct) {
            SkillPolicy.Decision decision = policy.choose(battle, actor);
            if (decision != null) {
                if (turn > 0) {
                    listener.onAction(actor, decision);
                }
                cast(battle, actor, decision);
            }
        }
    }

    /**
     * Casts one decision.
     *
     * <p>An ultimate must go through {@link Battle#castUltra}, not through the ordinary action path:
     * that is what pays its energy and owns its pre-cast checks. The engine also enforces both rules
     * inside {@code useSkill}, but routing the ultimate through its own entry keeps the intent
     * explicit.
     */
    private static void cast(Battle battle, CanHit actor, SkillPolicy.Decision decision) {
        if (decision.targets().isEmpty()) {
            return;
        }
        Skill ultra = actor.getSkills().get(SkillType.ULTRA);
        if (decision.skill() == ultra) {
            battle.castUltra(actor, decision.targets());
            return;
        }
        battle.performAction(decision.skill(), decision.targets());
    }
}
