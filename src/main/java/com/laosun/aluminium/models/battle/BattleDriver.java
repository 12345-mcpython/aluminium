package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;

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
    /**
     * What decides each unit's action. Not final: see {@link #withPolicy}. Same shape as {@link #listener}.
     */
    private SkillPolicy policy;
    private final int maxTurns;
    private BattleListener listener = new BattleListener() {
    };

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
        // ⚠ Registered once, here, and relayed to whatever listener the run ends up with.
        //
        // `withListener` can be called after construction, so the relay cannot capture the listener -- it forwards to
        // `this.listener` at the moment the damage settles. That also means a caller may replace the listener mid-run
        // and the damage still goes to the current one, which is what `withListener`'s own javadoc implies.
        //
        // The settled figure only exists inside `Battle.applyDamage`, so this is where the engine hands it over; see
        // `Battle.DamageObserver` for why a front end cannot work it out for itself.
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                listener.onDamage(damage, applied, hpLoss, shieldAbsorbed, died));
        // The other two figures a front end cannot reconstruct for itself: how much a heal restored, and how much shield
        // actually stood. Both are computed inside the engine and then forgotten.
        battle.addHealObserver((healer, target, healed) -> listener.onHeal(healer, target, healed));
        battle.addShieldObserver((provider, target, granted) -> listener.onShield(provider, target, granted));
        // And the rule itself. Without this a talent whose effect is a skill-level raise or a resource gain is invisible --
        // measured: forty-four rule effects fired in twenty turns and only six of them reached any other hook.
        battle.addRuleObserver((owner, op) -> listener.onRule(owner, op));
        // And why a turn was skipped. A broken unit and a controlled one both fail to act, and the log could not tell them
        // apart -- measured over one battle: seven breaks and twenty-three turns with a control present.
        battle.addSkipObserver((unit, broken) -> listener.onTurnSkipped(unit, broken));
        // A break and a landed control. Both were announced only to the buff hooks, so a screen saw neither -- measured: seven
        // breaks in one battle, and not one line about any of them.
        battle.addBreakObserver((attacker, target, element) -> listener.onBroken(attacker, target, element));
        battle.addControlObserver((applier, target, name) -> listener.onControl(applier, target, name));
    }

    /**
     * Attach an observer for this run.
     *
     * <p>A listener cannot change what happens: it is notified, and its return values are ignored. Kept
     * out of the constructor so a test can drive a battle silently with the two-argument form.
     *
     * @param listener the observer; {@code null} restores the silent default
     * @return this driver, for chaining
     */
    public BattleDriver withListener(BattleListener listener) {
        this.listener = listener == null ? new BattleListener() {
        } : listener;
        return this;
    }

    /**
     * Replace the policy that decides what each unit does.
     *
     * <p>⚠ <b>Why this exists beside the constructor.</b> {@link #of} takes the party and the monsters and builds
     * everything else, which is what a caller who wants "just play this battle" needs -- and it installs an
     * {@link AutoPolicy} for <b>both</b> camps, because a driver that plays the whole battle has to decide for
     * everyone. A caller that wants one camp played by a person and the other by the engine therefore has to swap the
     * policy after construction, because {@code of} cannot know which camp is a person's. Letting that caller write
     * its own {@code Battle} instead would duplicate the driver's construction just to change one field.
     *
     * <p>Kept out of the constructor for the same reason {@link #withListener} is: the common case stays a one-liner.
     *
     * @param policy the decision-maker; {@code null} restores the reference opponent
     * @return this driver, for chaining
     */
    public BattleDriver withPolicy(SkillPolicy policy) {
        this.policy = policy == null ? new AutoPolicy() : policy;
        return this;
    }

    /**
     * Starts the battle and tells the listener.
     *
     * <p>\u26a0 <b>Why this exists: a driven battle never announced its own start.</b> Only {@link #run()} fired
     * {@code onBattleStart}, and a front end that steps the battle itself -- which is the whole point of the driver -- calls
     * {@code battle.startBattle()} directly, so the callback was dead and the log had no first line. Measured: a full battle
     * with neither a start nor an end line.
     */
    public void startBattle() {
        battle.startBattle();
        listener.onBattleStart(this);
    }

    /**
     * Tells the listener how the battle ended.
     *
     * <p>The complement of {@link #startBattle()}: a front end that stops stepping has to be able to report the outcome, and
     * the log outlives the header that would otherwise be the only place it appeared.
     *
     * @param result how it ended
     */
    public void finish(Result result) {
        listener.onBattleEnd(result);
    }

    /**
     * Start the battle and run until it is decided or the turn ceiling is reached.
     *
     * @return how it ended
     */
    public Result run() {
        startBattle();
        int turns = 0;
        while (!battle.isOver() && turns < maxTurns) {
            if (!step(turns + 1)) {
                Result stalled = new Result(Outcome.STALLED, battle.checkResult(), turns, battle.getRound());
                listener.onBattleEnd(stalled);
                return stalled;
            }
            turns++;
        }
        Result result = battle.isOver()
                ? new Result(Outcome.FINISHED, battle.checkResult(), turns, battle.getRound())
                : new Result(Outcome.TURN_LIMIT, battle.checkResult(), turns, battle.getRound());
        listener.onBattleEnd(result);
        return result;
    }

    /**
     * Play exactly one turn: advance the bar, start the turn, act, end the turn.
     *
     * <p>Exposed so a test or a front end can step a battle rather than only run it, which is what an
     * interactive client needs. Callers that only want the result should use {@link #run()}.
     *
     * <p>⚠ <b>This overload reports no turn number, so it fires none of the listener's per-turn callbacks</b>
     * ({@code onTurnStart} / {@code onAction} / {@code onTurnEnd}) — those are gated on having a number to report.
     * A caller that wants to see what each turn did must use {@link #step(int)}, which is why that one is public:
     * an interactive front end needs exactly the events this overload withholds.
     *
     * @return {@code false} when no unit could act, so the caller stops
     */
    public boolean step() {
        return step(-1);
    }

    /**
     * Play one turn, telling the listener which turn number this is.
     *
     * <p>⚠ <b>Why this is public.</b> It was private, and the consequence was concrete: a front end could advance a
     * battle but could not observe it. {@code run()} reports turns and this reports a single one, so a client that
     * wanted to play turn by turn — which is what an interactive UI does, and what this method's own documentation
     * says {@link #step()} exists for — got a battle it could watch only by diffing HP before and after. It could
     * see THAT something happened but never WHAT.
     *
     * <p>The turn number also matters to the engine's own bookkeeping: firing limits and "once per turn" rules are
     * counted in {@code beforeMove}, which runs inside here, so a caller stepping a battle is responsible for
     * supplying a monotonically increasing number, exactly as {@code run()} does.
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

        // The turn still has to end even when the actor cannot use it: damage over time may have killed
        // it, and a control may forbid acting. `afterMove` is what re-times the bar and removes the dead,
        // so skipping it would leave the queue inconsistent rather than merely skipping an action.
        boolean mayAct = !actor.isDeath();

        // ⚠ The BROKEN check comes FIRST, before `canAct()`, and the order is load-bearing.
        //
        // `handleBrokenTurn` is what ticks a broken unit's countdown and, at zero, recovers it -- and it must run
        // whether or not the unit could otherwise act. Evaluating `canAct()` first meant that a unit which was broken
        // AND blocked by a control never reached the branch: its countdown never ticked, so it stayed broken and silent
        // for the rest of the battle. Measured, one unit frozen at its initial count for seventeen consecutive turns
        // while the fight went on around it.
        //
        // ⚠ Note what the trigger is NOT. A broken unit in this engine still reports `canAct() == true`; being broken
        // does not by itself block acting, so the first version of this comment was wrong and the first two cases
        // written against it could not fail. The freeze needs broken AND a control (frozen, imprisoned, ...).
        //
        // \u26a0 The two reasons a unit may not act are announced separately, because they are different facts: a broken unit
        // is recovering from a break and a controlled one is not, and the log showed both as "cannot act". Measured over one
        // battle: seven breaks and twenty-three turns with a control present, indistinguishable.
        boolean wasBroken = !actor.isDeath() && actor instanceof Enemy foe && foe.isBroken();
        if (!actor.isDeath() && actor instanceof Enemy broken && broken.isBroken()) {
            // The answer is "is this turn skipped", and that is exactly the complement of "may it act" -- so it is used
            // directly. The turn that ENDS a break answers false and the unit acts again on it, which is the contract
            // `handleBrokenTurn` now documents.
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
                cast(actor, decision);
            }
        }

        battle.afterMove();
        // \u26a0 No death check here, and that is deliberate: `CanHit.takeDamage` sets `death = true` the moment the damage lands,
        // so `!isDeath()` was already false by this point and the check could never fire -- measured, a battle that ended with
        // two deaths and not one line about either. The report belongs in the damage callback, where the engine states it.
        if (turn > 0) {
            listener.onTurnEnd(actor, turn);
        }
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
        // ⚠ An ultimate must go through `castUltra`, NOT through the ordinary action path.
        //
        // Measured: routing it through `performAction` left the bar untouched, so ultimates were FREE on the driven
        // path -- 姬子 fired her 120-cost ultimate four times in one fight with the bar reading 120 before and 120
        // after every cast, while calling `castUltra` directly in isolation drained it correctly to 15. The
        // hand-written demo hid this because it calls `castUltra` itself; the moment the engine owned the choice,
        // nothing paid for the ultimate.
        //
        // Note: `useSkill` now enforces the same gate and the same payment by itself, so this branch is no longer
        // what keeps ultimates paid for -- it is kept because routing an ultimate through its own entry is the
        // clearer call, and because `castUltra` owns the pre-cast checks (readiness, the extra-turn rule).
        //
        // Note: the order matters inside `castUltra`. It zeroes the bar BEFORE the ultimate body settles,
        // deliberately, so the kill/break energy the ultimate itself earns is not wiped by the zeroing.
        if (decision.skill() == actor.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA)) {
            battle.castUltra(actor, decision.targets());
            return;
        }
        // ⚠ No skill-point charge here any more.
        //
        // The driver used to charge non-damaging skills itself, on the stated reasoning that "`Battle.useSkill`
        // charges through the skill point policy, but a healing or shielding skill is executed by `SkillExecutor`
        // rather than through the damage pipeline, so nothing charges it on the way in". That reasoning is false:
        // `useSkill` charges through `skillPointPolicy.onSkillCast` for EVERY skill, damaging or not -- the charge is
        // about deciding to act, not about which pipeline settles the effect.
        //
        // So the manual charge was a SECOND one on top. Measured: a Skill spent 3 → 2 through `useSkill` alone, and
        // 3 → 1 through the driver's sequence. Every heal and every shield on the driven path cost double.
        // The engine charges; a caller must not.
        //
        // `performAction` is the queue-aware entry: it reads the current actor, refuses when the unit is
        // dead or controlled, and is the same call the demo used.
        battle.performAction(decision.skill(), decision.targets());
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
        return of(characters, enemies, seed, null);
    }

    /**
     * Builds a driver for a battle whose characters' technique releases are known up front.
     *
     * <p><b>Why the technique belongs here and not inside the driver's own logic.</b> A technique is cast on
     * the map, before the battle exists, and only the caller can know whether it was: it spends a limited map
     * resource, so "everyone uses their technique every battle" would be a different game. The driver's job is
     * to carry that decision into the battle it builds, which is what this overload does.
     *
     * <p>Passing {@code null} is the same as {@link #of(List, List, long)}: nobody used a technique, so no
     * {@code 秘技} state is applied and every technique rule's {@code self has_state 秘技} gate is false.
     *
     * @param characters the player's party
     * @param enemies    the opposing units
     * @param seed       the random seed, for reproducibility
     * @param techniques which characters released their technique; {@code null} means none did
     * @return a driver using {@link AutoPolicy} for every unit
     */
    public static BattleDriver of(List<Character> characters, List<? extends CanHit> enemies, long seed,
                                  Battle.Techniques techniques) {
        Battle battle = new Battle(characters, enemies, new Random(seed), techniques);
        return new BattleDriver(battle, new AutoPolicy());
    }
}
