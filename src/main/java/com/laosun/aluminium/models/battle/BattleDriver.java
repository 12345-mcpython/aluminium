package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Drives a battle to its conclusion with no human and no output.
 *
 * <p><b>What was missing.</b> There was no way to run a battle except through the demo's entry point:
 * the turn cycle ({@code stepForward → beforeMove → cast → afterMove}) was written inline there,
 * interleaved with printing and hand-picked skills, so "play a battle to the end" existed only as a
 * side effect of producing demo output. This class is that cycle on its own, which is what makes a
 * whole battle assertable in a test.
 *
 * <p><b>The cycle, and why each part is here rather than in the engine.</b> {@link Battle} owns turn
 * <em>mechanics</em>: which unit acts, what a skill does, when buffs tick. It does not own the
 * <em>loop</em>, because the loop is where a policy lives and the engine must not know about policies.
 * So the driver does exactly four things per turn and nothing else:
 * <ol>
 *  <li>{@link Battle#stepForward()} -- advance the action bar to the next actor;</li>
 *  <li>{@link Battle#beforeMove()} -- let the turn start (damage over time settles here, so the actor
 *      may already be dead, and a suspended unit's turn passes without acting);</li>
 *  <li>ask the {@link SkillPolicy} for a decision and cast it;</li>
 *  <li>{@link Battle#afterMove()} -- let the turn end. That call also settles queued requests, so the
 *      driver does not call {@code processRequests} itself; doing it twice would resolve the same
 *      pending effect twice.</li>
 * </ol>
 *
 * <p><b>Termination.</b> A battle ends when one side is wiped out, which is {@link Battle#isOver()}.
 * That is a state the engine reaches on its own, but a driver that merely loops "until over" can spin
 * forever if neither side can finish the other -- a battle of two unkillable units is not a win, it is
 * a hang. So the loop is also bounded by {@link #maxTurns}, and hitting that bound is reported as
 * {@link Outcome#TURN_LIMIT} rather than being hidden. A test asserting "the battle finishes" is then
 * asserting something real.
 */
public final class BattleDriver {

    /**
     * Default ceiling on turns, counted as actor-turns rather than rounds.
     *
     * <p>Generous on purpose: it exists to turn a hang into a reportable result, not to cut normal
     * battles short. A normal battle here runs a few dozen turns.
     */
    public static final int DEFAULT_MAX_TURNS = 2000;

    /**
     * How a driven battle ended.
     */
    public enum Outcome {
        /**
         * The battle reached a terminal state the engine decided.
         */
        FINISHED,
        /**
         * The turn ceiling was reached with the battle still running.
         */
        TURN_LIMIT,
        /**
         * No unit could act -- the action bar produced no actor while nobody had won.
         */
        STALLED
    }

    /**
     * The result of driving one battle.
     *
     * @param outcome the reason the loop stopped
     * @param status  the engine's status when it stopped
     * @param turns   actor-turns actually taken
     * @param rounds  completed rounds, as the action bar counts them
     */
    public record Result(Outcome outcome, Battle.Status status, int turns, int rounds) {

        /**
         * Whether the battle reached a terminal state rather than being cut off.
         *
         * @return {@code true} when the engine decided the outcome itself
         */
        public boolean isDecided() {
            return outcome == Outcome.FINISHED;
        }
    }

    private final Battle battle;
    private final SkillPolicy policy;
    private final int maxTurns;

    /**
     * Drive {@code battle}, letting one policy act for every unit.
     *
     * @param battle the battle to drive, already constructed but not started
     * @param policy the policy used for every unit
     */
    public BattleDriver(Battle battle, SkillPolicy policy) {
        this(battle, policy, DEFAULT_MAX_TURNS);
    }

    /**
     * @param battle   the battle to drive
     * @param policy   the policy used for every unit
     * @param maxTurns actor-turns after which to stop and report {@link Outcome#TURN_LIMIT}
     */
    public BattleDriver(Battle battle, SkillPolicy policy, int maxTurns) {
        this.battle = Objects.requireNonNull(battle, "battle");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.maxTurns = maxTurns > 0 ? maxTurns : DEFAULT_MAX_TURNS;
    }

    /**
     * Start the battle and run until it is decided or the turn ceiling is reached.
     *
     * @return how it ended
     */
    public Result run() {
        battle.startBattle();
        int turns = 0;
        while (!battle.isOver() && turns < maxTurns) {
            if (!step()) {
                return new Result(Outcome.STALLED, battle.checkResult(), turns, battle.getRound());
            }
            turns++;
        }
        if (battle.isOver()) {
            return new Result(Outcome.FINISHED, battle.checkResult(), turns, battle.getRound());
        }
        return new Result(Outcome.TURN_LIMIT, battle.checkResult(), turns, battle.getRound());
    }

    /**
     * Play exactly one turn: advance the bar, start the turn, act, end the turn.
     *
     * <p>Exposed so a test or a front end can step a battle rather than only run it, which is what an
     * interactive client needs. Callers that only want the result should use {@link #run()}.
     *
     * @return {@code false} when no unit could act, so the caller stops
     */
    public boolean step() {
        battle.stepForward();
        Signal current = battle.queue.getCurrentActor();
        if (current == null) {
            return false;
        }
        CanHit actor = current.getCanHit();
        if (actor == null) {
            return false;
        }

        battle.beforeMove();

        // The turn still has to end even when the actor cannot use it: damage over time may have killed
        // it, and a control may forbid acting. `afterMove` is what re-times the bar and removes the dead,
        // so skipping it would leave the queue inconsistent rather than merely skipping an action.
        boolean mayAct = !actor.isDeath() && actor.getBuffManager().canAct();
        if (mayAct) {
            SkillPolicy.Decision decision = policy.choose(battle, actor);
            if (decision != null) {
                cast(actor, decision);
            }
        }

        battle.afterMove();
        return true;
    }

    /**
     * Cast a decided action, paying a non-damaging skill's point cost the way a damaging one is paid.
     *
     * <p>Why the cost is charged here: {@code Battle.useSkill} charges through the skill point policy,
     * but a healing or shielding skill is executed by {@code SkillExecutor} rather than through the
     * damage pipeline, so nothing charges it on the way in. The demo charged it by hand at exactly this
     * point; keeping that here means a policy cannot forget to pay.
     *
     * @param actor    the acting unit
     * @param decision what it chose
     */
    private void cast(CanHit actor, SkillPolicy.Decision decision) {
        if (decision.targets().isEmpty()) {
            return;
        }
        SkillEffectTypeCategory category = categoryOf(decision);
        if (category == SkillEffectTypeCategory.NON_DAMAGING) {
            battle.applySkillPointCost(decision.skill(), actor);
        }
        // `performAction` is the queue-aware entry: it reads the current actor, refuses when the unit is
        // dead or controlled, and is the same call the demo used.
        battle.performAction(decision.skill(), decision.targets());
    }

    /**
     * Whether the chosen skill needs its cost charged by hand.
     */
    private enum SkillEffectTypeCategory {
        /**
         * Goes through the damage pipeline, which charges the cost itself.
         */
        DAMAGING,
        /**
         * Runs as a side effect, so the caller charges the cost.
         */
        NON_DAMAGING
    }

    /**
     * Classify the decision's skill for cost purposes.
     *
     * @param decision the decision
     * @return whether the cast charges its own cost
     */
    private SkillEffectTypeCategory categoryOf(SkillPolicy.Decision decision) {
        var data = decision.skill().getData();
        if (data == null || data.getEffect() == null || data.getEffect().isDamaging()) {
            return SkillEffectTypeCategory.DAMAGING;
        }
        return SkillEffectTypeCategory.NON_DAMAGING;
    }

    /**
     * The battle being driven.
     *
     * @return the battle
     */
    public Battle battle() {
        return battle;
    }

    /**
     * Builds a driver for a hand-made battle, for callers that already have characters and enemies.
     *
     * @param characters the player's party
     * @param enemies    the opposing units
     * @param seed       the random seed, for reproducibility
     * @return a driver using {@link AutoPolicy} for every unit
     */
    public static BattleDriver of(List<Character> characters, List<? extends CanHit> enemies, long seed) {
        Battle battle = new Battle(characters, enemies, new Random(seed));
        return new BattleDriver(battle, new AutoPolicy());
    }
}
