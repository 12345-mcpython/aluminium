package com.laosun.aluminium;

import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.models.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;
import com.laosun.aluminium.models.trigger.TriggerInterpreter;
import com.laosun.aluminium.models.trigger.TriggerPayload;
import com.laosun.aluminium.models.trigger.TriggerTable;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Consumer;


public class Battle {
    /**
     * Battle state machine.
     *
     * <pre>
     *  NOT_STARTED --startBattle--RUNNING --one side wiped out--WIN / LOSE
     *                                    |
     *                                   +---- (no rollback) ---+
     * </pre>
     *
     * <p>{@code WIN} / {@code LOSE} are terminal states: {@link #stepForward()} does not advance the action bar.
     */
    public enum Status {
        NOT_STARTED, RUNNING, WIN, LOSE
    }

    /**
     * Told what a damage instance actually settled, so a front end can show a number the engine stands behind.
     *
     * <p><b>Why this exists rather than a buff hook.</b> {@link com.laosun.aluminium.models.event.DamageEvent} is the
     * buff-facing surface and it fires <i>inside</i> the zone arithmetic, before the zones are multiplied -- so the
     * damage it carries is intermediate, and a screen reading it would print a figure the engine never applied. The
     * settled amount also cannot be worked out afterwards by assembling the hit again: assembly is additive
     * ({@code addBoost} appends), so a second pass would count DMG boost, vulnerability, reduction and weakness twice.
     *
     * <p>So this is an <b>observer</b>, not a mechanic: it is announced at the one place the settled figure exists, it
     * cannot change anything, and a battle with no observer registered behaves exactly as before.
     *
     * @see #addDamageObserver(DamageObserver)
     */
    @FunctionalInterface
    public interface DamageObserver {

        /**
         * One damage instance has been settled and applied.
         *
         * <p>Fired for every instance of the hit, so one skill that derives break damage and then super break damage
         * reports each separately.
         *
         * @param damage         the instance that landed
         * @param applied        what it had effect with: shield absorbed plus HP actually lost
         * @param hpLoss         the part that came out of HP
         * @param shieldAbsorbed the part a shield ate
         * @param died           whether this instance is what killed the target
         */
        void onDamageSettled(Damage damage, double applied, double hpLoss, double shieldAbsorbed, boolean died);
    }

    /**
     * Told how much a heal actually restored.
     *
     * <p>A sibling of {@link DamageObserver}, and an observer for the same reason: the amount restored is computed
     * inside {@code heal} and then forgotten, so a front end cannot recover it -- reading the target's HP before and
     * after would be wrong the moment anything else moves HP in between.
     *
     * @see #addHealObserver(HealObserver)
     */
    @FunctionalInterface
    public interface HealObserver {

        /**
         * A heal restored health.
         *
         * <p>Not fired for a heal that restored nothing (a heal on a corpse, or at full HP): that is not a heal event,
         * and reporting "0 healed" would put a row on screen for something that did not happen.
         *
         * @param healer the one who healed
         * @param target the one healed
         * @param healed the HP actually restored
         */
        void onHealSettled(CanHit healer, CanHit target, double healed);
    }

    /**
     * Told how much shield a grant actually applied.
     *
     * <p>Not the amount that was asked for: the provider's own shield boost is applied inside {@code grantShield}, so a
     * caller that reported the request would print a number the battle does not have.
     *
     * @see #addShieldObserver(ShieldObserver)
     */
    @FunctionalInterface
    public interface ShieldObserver {

        /**
         * A shield was granted and left standing.
         *
         * <p>Not fired when the grant clears a shield ({@code amount <= 0} is how this API spells "clear it") or when
         * the target is dead: neither puts a shield on the field, and "gained 0 shield" is not a fact worth a row.
         *
         * @param provider who granted it
         * @param target   who received it
         * @param granted  the shield value actually applied
         */
        void onShieldGranted(CanHit provider, CanHit target, double granted);
    }

    /**
     * Told that a weakness break just happened.
     *
     * <p>⚠ <b>Why this exists: a break was broadcast only to the buff hooks.</b> The engine fires {@code events.weaknessBreak}
     * so buffs can react, and a LISTENER -- the thing a screen attaches -- was never told, so seven breaks in one battle
     * produced no log line at all.
     *
     * @see #addBreakObserver(BreakObserver)
     */
    @FunctionalInterface
    public interface BreakObserver {

        /**
         * A weakness break happened.
         *
         * @param attacker who broke it
         * @param target   the unit that was broken
         * @param element  the element that broke it
         */
        void onBroken(CanHit attacker, CanHit target, DamageElement element);
    }

    /**
     * Told that a control state landed on a unit.
     *
     * <p>⚠ Landed, not attempted: the engine rolls the control through the resist pipeline, and a resisted application is a
     * different event. Reporting the attempt would claim a freeze that never happened.
     *
     * @see #addControlObserver(ControlObserver)
     */
    @FunctionalInterface
    public interface ControlObserver {

        /**
         * A control state was applied.
         *
         * @param applier who applied it
         * @param target  the unit it landed on
         * @param name    the state's own name (冻结)
         */
        void onControl(CanHit applier, CanHit target, String name);
    }

    /**
     * Told that a turn was skipped, and why.
     *
     * <p>⚠ <b>Why this exists: a skipped turn looked like an action that did nothing.</b> The log showed
     * `held -- cannot act` while the engine has TWO different reasons a unit may not act -- it is broken, or a control forbids
     * it -- and the reader had to guess which. Measured over one battle: seven breaks and twenty-three turns with a control
     * present, none of them distinguishable in the log.
     *
     * @see #addSkipObserver(SkipObserver)
     */
    @FunctionalInterface
    public interface SkipObserver {

        /**
         * A unit's turn was skipped.
         *
         * @param unit   whose turn it was
         * @param broken whether the cause was a break (as opposed to a control)
         */
        void onTurnSkipped(CanHit unit, boolean broken);
    }

    /**
     * Told that a data rule fired, and what it did.
     *
     * <p>⚠ <b>Why this exists: the engine announces EFFECTS, never "a rule fired".</b> Measured over twenty turns, 44 rule
     * effects were applied and only six of them -- the damage, shield and heal -- produced anything a screen could show. The
     * rest were silent, so a talent whose whole effect is "raise the skill level" or "gain a resource" was invisible: it
     * happened, and nothing anywhere said so.
     *
     * <p>It reports and cannot change anything, like the other observers.
     *
     * @see #addRuleObserver(RuleObserver)
     */
    @FunctionalInterface
    public interface RuleObserver {

        /**
         * One rule effect was applied.
         *
         * <p>⚠ A rule that does several things reports each of them separately, because they are applied separately -- a
         * talent that freezes a target and heals its owner is two effects, and collapsing them would hide one.
         *
         * @param owner the unit the rule belongs to
         * @param op    the operation it performed, as the data names it
         */
        void onRuleFired(CanHit owner, String op);
    }

    /**
     * Observer registration and announcement.
     */
    private final com.laosun.aluminium.models.battle.BattleObservers observers =
            new com.laosun.aluminium.models.battle.BattleObservers();
    private final com.laosun.aluminium.models.battle.UltimateService ultimates =
            new com.laosun.aluminium.models.battle.UltimateService(this);
    private final com.laosun.aluminium.models.battle.EnergyService energy =
            new com.laosun.aluminium.models.battle.EnergyService(this::onEnergyCredited);
    private final com.laosun.aluminium.models.battle.TriggerFacts triggerFacts =
            new com.laosun.aluminium.models.battle.TriggerFacts();
    private final com.laosun.aluminium.models.battle.DamageMarks damageMarks =
            new com.laosun.aluminium.models.battle.DamageMarks();
    private final com.laosun.aluminium.models.battle.WaveState waveState =
            new com.laosun.aluminium.models.battle.WaveState();
    private final com.laosun.aluminium.models.battle.TriggerBus triggerBus =
            new com.laosun.aluminium.models.battle.TriggerBus(this, triggerFacts);
    private final com.laosun.aluminium.models.battle.DamageService damageService =
            new com.laosun.aluminium.models.battle.DamageService(this, triggerBus, damageMarks, energy,
                    observers, this::broadcastHpLoss, this::broadcastKill);
    private final com.laosun.aluminium.models.battle.BreakService breakService =
            new com.laosun.aluminium.models.battle.BreakService(this, damageService, triggerBus,
                    this::broadcastBreak);
    private final com.laosun.aluminium.models.battle.DeathService deathService =
            new com.laosun.aluminium.models.battle.DeathService(this, triggerBus);
    private final com.laosun.aluminium.models.battle.SustainService sustain =
            new com.laosun.aluminium.models.battle.SustainService(this,
                    this::onHealSettled, this::onShieldGranted);
    private final com.laosun.aluminium.models.battle.TickService ticks =
            new com.laosun.aluminium.models.battle.TickService(damageService, sustain);
    private final com.laosun.aluminium.models.battle.Battlefield battlefield =
            new com.laosun.aluminium.models.battle.Battlefield(this);
    private final com.laosun.aluminium.models.battle.ChanceService chances =
            new com.laosun.aluminium.models.battle.ChanceService(this);
    private final com.laosun.aluminium.models.battle.DebuffService debuffs =
            new com.laosun.aluminium.models.battle.DebuffService(this, triggerBus);

    /** @param observer the damage observer */
    public void addDamageObserver(DamageObserver observer) {
        observers.addDamage(observer);
    }

    /** @param observer the damage observer */
    public void removeDamageObserver(DamageObserver observer) {
        observers.removeDamage(observer);
    }

    /** @param observer the break observer */
    public void addBreakObserver(BreakObserver observer) {
        observers.addBreak(observer);
    }

    /** @param observer the break observer */
    public void removeBreakObserver(BreakObserver observer) {
        observers.removeBreak(observer);
    }

    /**
     * Announces a weakness break.
     *
     * @param attacker who broke it
     * @param target   the unit that was broken
     * @param element  the element that broke it
     */
    public void announceBroken(CanHit attacker, CanHit target, DamageElement element) {
        observers.announceBroken(attacker, target, element);
    }

    /** @param observer the control observer */
    public void addControlObserver(ControlObserver observer) {
        observers.addControl(observer);
    }

    /** @param observer the control observer */
    public void removeControlObserver(ControlObserver observer) {
        observers.removeControl(observer);
    }

    /**
     * Announces that a control landed.
     *
     * @param applier who applied it
     * @param target  the unit it landed on
     * @param name    the state's name
     */
    public void announceControl(CanHit applier, CanHit target, String name) {
        observers.announceControl(applier, target, name);
    }

    /** @param observer the skip observer */
    public void addSkipObserver(SkipObserver observer) {
        observers.addSkip(observer);
    }

    /** @param observer the skip observer */
    public void removeSkipObserver(SkipObserver observer) {
        observers.removeSkip(observer);
    }

    /**
     * Announces that a turn was skipped.
     *
     * @param unit   whose turn it was
     * @param broken whether the cause was a break
     */
    public void announceTurnSkipped(CanHit unit, boolean broken) {
        observers.announceTurnSkipped(unit, broken);
    }

    /** @param observer the rule observer */
    public void addRuleObserver(RuleObserver observer) {
        observers.addRule(observer);
    }

    /** @param observer the rule observer */
    public void removeRuleObserver(RuleObserver observer) {
        observers.removeRule(observer);
    }

    /**
     * Announces that a rule effect was applied.
     *
     * @param owner the unit the rule belongs to
     * @param op    the operation performed
     */
    public void announceRuleFired(CanHit owner, String op) {
        observers.announceRuleFired(owner, op);
    }

    /** @param observer the heal observer */
    public void addHealObserver(HealObserver observer) {
        observers.addHeal(observer);
    }

    /** @param observer the heal observer */
    public void removeHealObserver(HealObserver observer) {
        observers.removeHeal(observer);
    }

    /** @param observer the shield observer */
    public void addShieldObserver(ShieldObserver observer) {
        observers.addShield(observer);
    }

    /** @param observer the shield observer */
    public void removeShieldObserver(ShieldObserver observer) {
        observers.removeShield(observer);
    }

    private Queue queue;

    /**
     * @return the action-order queue
     */
    public Queue queue() {
        return queue;
    }

    /**
     * The event-announcement surface: who is told about what (see {@link BattleEvents}).
     *
     * <p>Kept as one object rather than eight private methods so the routing rules live in a file whose
     * whole subject is routing. The delegates below keep every call site unchanged.
     */
    private final BattleEvents events = new BattleEvents(this);

    /**
     * The engine's roster of our characters. Read-only: admit a unit through
     * {@link #addCombatant(CanHit)} rather than writing to this list.
     */
    private final List<Character> characters;
    private final List<Character> characterRoster;

    /**
     * The <b>player camp's roster</b> - every combatant fighting for us, in battlefield order: our
     * characters plus any friendly {@link Summon} (a memosprite, Jing Yuan (景元)'s [神君], ...).
     *
     * <p><b>Why this exists next to {@link #characters} rather than replacing it</b> (the friendly half of
     * the camp split). The enemy side already has this split - {@link #enemies} is the camp and {@link #enemyUnits()}
     * is the monsters in it - so that things which genuinely only work on monsters say so. Our side had no
     * such split, and the consequence was harder than a missing convenience: <b>a player-side summon could
     * not be placed anywhere at all</b>, because the only roster we had was typed {@code List<Character>}.
     *
     * <p>The tempting fix was to widen {@code characters} and add a {@code characterUnits()} view (that is
     * the symmetric option, and it would have made the compiler find every stale call site). It was not
     * taken because {@code characters} is read in <b>19 places</b> - nearly all of them meaning "our
     * characters" (their skills, energy, relic rules) rather than "our camp" - and a rename of that size
     * costs a great deal of churn for no behaviour. So the camp got its own name instead, and the sites
     * that mean <em>the camp</em> were moved onto it one by one.
     *
     * <p>Note: <b>Which question a call site is asking, and the cost of getting it wrong.</b> "Who is on our
     * side" must read {@code allies}; "which of them are characters" must read {@code characters}. A camp
     * site left on {@code characters} does not fail loudly - it silently ignores friendly summons, so an
     * enemy AOE would miss one and a party-wide buff would skip it. The compiler cannot catch that here, so
     * it is pinned by {@code PlayerSideSummonTest}, which exercises each camp-level site with a summon on the
     * field. The sites that deliberately stay on {@code characters} are the ones that need something only a
     * {@code Character} has: {@code attachBattleSkills} (map skills), {@code fireTriggers} (trigger tables)
     * and the two skill-point broadcasts.
     *
     * <p>It starts as a copy of the constructor's character list, so it holds <b>the same instances</b> -
     * this is a second view of one roster, not a second roster. Dead members stay in it, like every other
     * roster in the engine ({@code enemies} / {@code characters}): "is it alive" is asked with
     * {@code isDeath()}.
     */
    private final List<CanHit> allies;
    private final List<CanHit> allyRoster;

    /**
     * The <b>enemy camp's roster</b> - every combatant fighting against us, in battlefield order.
     *
     * <p>Note: <b>Deliberately {@code CanHit}, not {@code Enemy}</b>. Typing it as
     * {@code List<Enemy>} would make an enemy-side {@link com.laosun.aluminium.models.Summon}
     * <b>structurally impossible to place</b>: the action bar accepts any {@code CanHit}
     * ({@code Queue.addCombatants} takes {@code List<? extends CanHit>}), and camp-agnostic code
     * ({@code TargetSelector}, {@code opposingCamp}) was already written against {@code CanHit},
     * but the roster it had to be registered in only admitted monsters. Widening it here is what
     * makes that possible at all.
     *
     * <p>Code that needs <b>monster</b> mechanics (toughness, weakness, per-element debuff
     * resistance, phase tables) must ask for them explicitly through {@link #enemyUnits()} rather
     * than assuming every entry is one - that is the whole point of the split, and it is why a
     * summon is not silently skipped anywhere: the places that genuinely only work on monsters now
     * say so.
     *
     * <p>Read-only: {@code WaveManager} appends each new wave's monsters through
     * {@link #addCombatant(CanHit)}.
     */
    private final List<CanHit> enemies;
    private final List<CanHit> enemyRoster;

    /**
     * @return the character roster
     */
    public List<Character> characters() {
        return characters;
    }

    /**
     * @return our camp's roster
     */
    public List<CanHit> allies() {
        return allies;
    }

    /**
     * @return the enemy camp's roster
     */
    public List<CanHit> enemies() {
        return enemies;
    }

    /**
     * One living unit from the other side, chosen with the battle's own seeded randomness (used by
     * 1505 Evanescia (绯英)'s ultimate, "deals 14% to a random enemy target that deals damage ...").
     *
     * <p>The roll lives here rather than in the interpreter for the same reason crit does: one seeded source means a judge
     * can pin determinism (same seed -> same pick) and still show the choice varies across seeds.
     *
     * @return the chosen unit, or {@code null} when the other side is already empty
     */
    public CanHit randomOpponent(CanHit owner) {
        return battlefield.randomOpponent(owner);
    }

    /**
     * The {@link Enemy} entries of {@link #enemies}, in the same order - the monsters, without any
     * summon that may be sharing the camp.
     *
     * <p>Exists so that "the enemy side" and "the monsters on the enemy side" are two named things
     * instead of one type that has to mean both. A fresh list: the caller may remove from it freely.
     *
     * @return a fresh list of the enemy camp's monsters (including dead ones - filter with
     * {@code !isDeath()} if that is what you mean)
     */
    public List<Enemy> enemyUnits() {
        return battlefield.enemyUnits();
    }

    private Signal currentMove;

    /**
     * @return the current action, or {@code null}
     */
    public Signal currentMove() {
        return currentMove;
    }

    /**
     * @param signal the current action
     */
    public void setCurrentMove(Signal signal) {
        currentMove = signal;
    }

    private final com.laosun.aluminium.models.battle.BattleResult battleResult =
            new com.laosun.aluminium.models.battle.BattleResult(this);

    /**
     * The current battle status.
     *
     * @return the status
     */
    public Status getStatus() {
        return battleResult.status();
    }

    /**
     * Wave management; {@code null} for a non-wave battle.
     *
     * <p>Its only reason to exist is to let {@link #checkResult()} know whether "the enemy team is empty"
     * means won or this wave has not entered yet.
     */
    @Getter
    @Setter
    private WaveManager waveManager;

    private final com.laosun.aluminium.models.battle.PendingCombatants pendingCombatants =
            new com.laosun.aluminium.models.battle.PendingCombatants(this);

    /**
     * The combatants queued to enter the action bar at the next settle, in entry order.
     *
     * @return an immutable copy of the pending entrants
     */
    public List<CanHit> pendingCombatants() {
        return pendingCombatants.view();
    }

    private final com.laosun.aluminium.models.battle.Countdowns countdowns =
            new com.laosun.aluminium.models.battle.Countdowns(this);

    /**
     * The read-only entry point for countdowns (1408's transformation countdown predicate, and the like predicates at
     * 150/1309).
     *
     * @return every countdown, in placement order
     */
    public List<Countdown> countdownUnits() {
        return countdowns.all();
    }

    /**
     * Summons placed since the last settle, so {@link TriggerEvent#SUMMONED} can be fired once they are in the
     * action bar (see {@link #fireSummoned}). Separate from {@link #addRequestItems} on purpose: that queue is
     * shared with wave entries, and a wave arriving is not a summon.
     */
    private final com.laosun.aluminium.models.battle.SummonedBuffer summoned =
            new com.laosun.aluminium.models.battle.SummonedBuffer();

    /**
     * The facts the caller declared before the battle started.
     */
    private final com.laosun.aluminium.models.battle.PreBattle preBattle =
            new com.laosun.aluminium.models.battle.PreBattle();

    /**
     * The techniques the battle was CREATED with, or {@code null} when the caller marks them later.
     *
     * <p>Kept beside {@link com.laosun.aluminium.models.battle.PreBattle#techniqueUsers()} rather than replacing it: the battle's own state is still
     * "which units hold 秘技", and this is only the construction-time answer to that question. Both routes
     * end in the same list, so nothing downstream can tell which one was used.
     *
     * <p>Not final only because the two technique-taking constructors delegate to the older ones and then
     * record their argument; there is no setter and nothing past construction writes it.
     */
    private Techniques techniques;

    /**
     * The cast being resolved right now, or {@code null} outside a cast - what
     * {@link TriggerEvent#CAST_SETUP} hands to the trigger tables.
     *
     * <p><b>Why the battle holds it rather than the trigger context.</b> "A cast is in progress" is a fact about
     * the battle, not about one event: the rules that read it fire <i>during</i> the cast and the decision has to
     * survive back to {@code SkillExecutor}, which is what skips the damage. Putting it here also keeps
     * {@code TriggerContext} (a record with a construction site per event) unchanged, and {@code ctx.battle()} is
     * already in every context.
     *
     * <p>Nesting is modelled with a link to the enclosing cast ({@link PendingCast#outer()}): an inner cast
     * restores the outer one when it ends, so a rule that somehow casts during a cast cannot corrupt it.
     */
    private PendingCast pendingCast;

    /**
     * One cast in flight: who is casting, which slot, and whether its damage has been handed to somebody else.
     *
     * <p>Mutable on purpose, and it is the <b>only</b> thing the rules may change about a cast today (see
     * {@link TriggerEvent#CAST_SETUP}): the alternative - a rule that describes the swing after the fact - cannot
     * undo a damage instance that has already been settled.
     */
    public static final class PendingCast {

        private final CanHit caster;
        private final int slot;
        private final PendingCast outer;
        private boolean damageDelegated;

        private PendingCast(CanHit caster, int slot, PendingCast outer) {
            this.caster = caster;
            this.slot = slot;
            this.outer = outer;
        }

        /**
         * Who is casting.
         */
        public CanHit caster() {
            return caster;
        }

        /**
         * The skill slot being cast (1 = basic attack, 2 = Skill, 3 = ultimate, ..., the numbering {@code skills.json}
         * itself uses - see {@link Skill#getSkillSlot()}).
         */
        public int slot() {
            return slot;
        }

        /**
         * The cast this one is nested inside, or {@code null}.
         */
        public PendingCast outer() {
            return outer;
        }

        /**
         * Whether this cast's own damage has been delegated - i.e. {@code SkillExecutor} must not expand it and
         * must not remove this skill's toughness either: both belong to whoever delivers the swing.
         */
        public boolean damageDelegated() {
            return damageDelegated;
        }

        /**
         * Hands this cast's damage over. Called by the {@code DELEGATE_DAMAGE} op, which is the only caller and
         * checks that the cast really is the rule owner's own and really is the slot the rule names.
         */
        public void delegateDamage() {
            this.damageDelegated = true;
        }
    }

    /**
     * Records one <b>landed</b> application of a named state during the current cast.
     *
     * <p>Called by the ops that roll for a state ({@code APPLY_CONTROL}, {@code APPLY_DOT}) and only when the roll
     * passed - a resisted application is not an application, which is the whole reason this counter exists.
     *
     * @param stateName the state's name as the documents spell it (Frozen / Burn / ...)
     */
    public void recordCastApplied(String stateName) {
        castOutcome.recordApplied(stateName);
    }

    /**
     * How many targets the current cast has applied {@code stateName} to ({@code 0} outside a cast's own events).
     *
     * @param stateName the state's name (Frozen / Burn / ...)
     * @return the landed count for this cast
     */
    public int castAppliedCount(String stateName) {
        return castOutcome.appliedCount(stateName);
    }

    /**
     * Forgets the current cast's landed applications, once its events have been delivered.
     *
     * <p>Called from {@code SkillExecutor.execute}'s {@code finally} so that nothing firing later - an ally's attack,
     * a hit taken next turn - can read a stale count.
     */
    public void endCastOutcome() {
        castOutcome.end();
        // "when casting ..." ends here: every event of this cast has been delivered, so a modifier stating
        // `"until": "cast_end"` has covered every heal the cast performed and nothing after it. Swept on both sides,
        // because either camp may carry one.
        for (CanHit unit : allies) {
            unit.getBuffManager().removeWithLifetime(AbstractBuff.Lifetime.CAST_END);
        }
        for (CanHit unit : enemies) {
            unit.getBuffManager().removeWithLifetime(AbstractBuff.Lifetime.CAST_END);
        }
    }

    /**
     * Starts a cast and returns its token; {@link #endCast(PendingCast)} must be called in a {@code finally}.
     *
     * @param skill  the skill being cast
     * @param caster who casts it
     * @return the token identifying this cast
     */
    public PendingCast beginCast(Skill skill, CanHit caster) {
        // A new cast's outcome starts empty, consuming the count a commanded cast claimed just before this call.
        castOutcome.begin();
        pendingCast = new PendingCast(caster, skill == null ? 0 : skill.getSkillSlot(), pendingCast);
        return pendingCast;
    }

    /**
     * Ends the cast started by {@link #beginCast}, restoring the enclosing one (if any).
     *
     * @param cast the token {@link #beginCast} returned
     */
    public void endCast(PendingCast cast) {
        if (pendingCast == cast) {
            pendingCast = cast == null ? null : cast.outer();
        }
    }

    /**
     * The cast being resolved right now, or {@code null} when nothing is being cast.
     */
    public PendingCast currentCast() {
        return pendingCast;
    }

    /**
     * Skill points policy: one pool shared by the whole team, not one track per character.
     *
     * <p>{@code Battle} itself does not know the skill point rules -- it only holds a
     * {@link SkillPointPolicy} and asks it once when "deciding to act" (see {@link #useSkill}).
     * Why split it this way: skill point rules keep growing character-level corrections (Bronya (布洛妮娅)
     * "skill has a 50% chance of +1", Sushang (素裳) "skill on a weakness-broken target +1",
     * Sparkle (花火) "max +2", ...). If all of those branches were added here,
     * {@code Battle} would fill up with "because of some character" conditionals, violating the three-way split.
     * See {@link com.laosun.aluminium.models.skillpoint.SkillPointPolicy}.
     *
     * <p>The default is {@link StandardSkillPointPolicy} (start 3 / max 5 / our basic attack +1 / skill -1 /
     * everything else neutral). To swap in another rule set for the team (e.g. Sparkle (花火) raising the max),
     * call {@link #setSkillPointPolicy}; it rewires the event reports, which a plain field assignment could not.
     */
    private final com.laosun.aluminium.models.battle.SkillPoints skillPoints =
            new com.laosun.aluminium.models.battle.SkillPoints(this,
                    this::broadcastSkillPointGained, this::onSkillPointSpent);

    /**
     * The policy that owns this battle's skill points.
     *
     * @return the current policy
     */
    public SkillPointPolicy getSkillPointPolicy() {
        return skillPoints.policy();
    }

    /**
     * Replaces the skill point policy and wires its reports into this battle's events.
     *
     * <p>The setter exists rather than a public field because the wiring is not optional: a policy that
     * is installed without {@link com.laosun.aluminium.models.battle.SkillPoints} still holds the points, but
     * {@code SKILL_POINT_GAINED} / {@code SKILL_POINT_SPENT} never fire, so every rule gated on them
     * silently stops working. The constructor wires the default; this method is the other way in.
     *
     * @param policy the new policy; must not be {@code null}
     * @throws IllegalArgumentException when {@code policy} is {@code null}
     */
    public void setSkillPointPolicy(SkillPointPolicy policy) {
        skillPoints.setPolicy(policy);
    }

    /**
     * Current skill points. Equivalent to {@code getSkillPointPolicy().getValue()}.
     */
    public int getSkillPoints() {
        return skillPoints.value();
    }

    /**
     * The regular skill point cap. Equivalent to {@code skillPointPolicy.getMax()}.
     */
    public int getSkillPointMax() {
        return skillPoints.max();
    }

    /**
     * Whether the pool holds at least {@code points} skill points.
     *
     * <p>Beside {@link #hasSkillPoint()}, which only ever asks "is one action affordable": a clause that
     * reads the count or the cap asks a different question, and the register's F-7 is that the narrower
     * one used to be the only one on offer.
     */
    public boolean hasSkillPoint(int points) {
        return skillPoints.hasAtLeast(points);
    }

    /**
     * Whether there are enough skill points to cast one skill.
     */
    public boolean hasSkillPoint() {
        return skillPoints.canAfford();
    }

    /**
     * Directly restore skill points, capped at the regular maximum.
     *
     * <p>For explicit sources other than "basic attack +1": techniques, relics (the 4-piece Passerby set),
     * character mechanics.
     *
     * <p>This is also the single place the {@code SKILL_POINT_GAINED} trigger fires from,
     * whichever way the points were added: the standard policy reports its own in-cast gains to the
     * listener, and this method reports through the same listener for direct calls. Firing in both
     * places independently would make a single gain trigger twice.
     *
     * @param n the number of points to add; does nothing when {@code <= 0}
     */
    public void gainSkillPoint(int n) {
        skillPoints.gain(n);
    }

    /**
     * Spend 1 skill point.
     *
     * @return whether the spend succeeded; {@code false} means the count was already 0 (the caller should block this action)
     */
    public boolean spendSkillPoint() {
        return skillPoints.spend();
    }

    /**
     * Settle skill points for a skill. The internal funnel point, shared by {@link #useSkill} and
     * the heal/shield branches in the demos -- the latter call {@code Battle.heal/grantShield} directly,
     * bypassing {@link #useSkill}, so this has to be called explicitly, otherwise "a healing skill costs no points".
     *
     * <p>All the rules live in the policy (including the camp check); this only forwards.
     *
     * @param skill the skill to settle
     * @param user  the actor -- must be passed explicitly. Earlier this guessed the actor from
     *              {@code currentMove}, and in scenarios with no actor (directly-called tests, the demo's
     *              healing branch) it guessed {@code null}, while {@code null != Camp.PLAYER} silently turned
     *              the policy into a no-op -- a wrong answer that reports no error. Passing it explicitly makes
     *              this kind of misuse surface as a compile error.
     * @return whether this action may continue (enough skill points); basic attack/ultimate/follow-up attack are always true
     */
    public boolean applySkillPointCost(Skill skill, CanHit user) {
        return skillPoints.onSkillCast(skill, user);
    }


    private final com.laosun.aluminium.models.battle.SkillRequests skillRequests =
            new com.laosun.aluminium.models.battle.SkillRequests(this);
    private final com.laosun.aluminium.models.battle.CastService cast =
            new com.laosun.aluminium.models.battle.CastService(this, skillPoints, ultimates, skillRequests);

    /**
     * Injected random source (crit rolls / target selection); a fixed seed makes a
     * whole battle reproducible.
     */
    @Getter
    private final Random rng;
    /**
     * Starts a wave: the counter every per-wave limit is compared against. Called by WaveManager before WAVE_START.
     */
    public void beginWave() {
        waveState.begin();
    }

    /**
     * The current wave's number.
     *
     * @return the sequence
     */
    public int waveSequence() {
        return waveState.sequence();
    }

    /**
     * Registers a unit whose rules are asked although it is not on the field.
     *
     * @param listener  the unit
     * @param warehouse the table to ask
     */
    public void registerWarehouseListener(Character listener, TriggerTable warehouse) {
        triggerBus.registerWarehouseListener(listener, warehouse);
    }

    /**
     * Registers a unit whose own table is asked although it is not on the field.
     *
     * @param listener the unit
     */
    public void registerWarehouseListener(Character listener) {
        triggerBus.registerWarehouseListener(listener);
    }


    /**
     * The per-battle home of PARTY-scoped resources (the shared [笑点] (laughs) counter). A resource like
     * that belongs to the party, not to whoever happened to declare it -- `ResourceManager` refuses to keep such a thing
     * per character, and this is the owner it asks for. Registered once at the start of the battle from the declarations.
     */
    private final com.laosun.aluminium.models.battle.PartyResources partyResources =
            new com.laosun.aluminium.models.battle.PartyResources();
    private final com.laosun.aluminium.models.battle.SummonService summons;
    private final com.laosun.aluminium.models.battle.CastOutcome castOutcome =
            new com.laosun.aluminium.models.battle.CastOutcome();


    public record SkillRequest(Skill skill, CanHit object, List<? extends CanHit> target) {
    }

    /**
     * The data pack this battle reads skills and summons from.
     */
    private final GameData data;

    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue) {
        this(characterQueue, enemyQueue, new Random());
    }

    /**
     * @param characterQueue our side. Copied, so the battle owns its roster and a caller that mutates
     *                       its own list afterwards cannot desynchronise it from the action bar
     * @param enemyQueue     the enemy camp - monsters, and any summon fighting alongside them. Taken as
     *                       {@code ? extends CanHit} so that a caller's {@code List<Enemy>} still fits.
     *                       Copied, so the battle owns its roster and a later
     *                       {@code WaveManager} append does not write through to the caller's list.
     */
    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue, Random rng) {
        this(characterQueue, enemyQueue, rng, GameData.DEFAULT);
    }

    /**
     * @param characterQueue our side
     * @param enemyQueue     the enemy camp
     * @param rng            the random source
     * @param data           the data pack skills and summons are read from
     */
    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue, Random rng, GameData data) {
        this.data = java.util.Objects.requireNonNull(data, "data");
        this.summons = new com.laosun.aluminium.models.battle.SummonService(this, data, summoned::add);
        characterRoster = new ArrayList<>(characterQueue);
        // The camp view of our side: the same instances, one more list. A friendly summon is appended here
        // (and only here) by summon(...), so `characters` stays exactly "our characters".
        allyRoster = new ArrayList<>(characterQueue);
        enemyRoster = new ArrayList<>(enemyQueue);
        characters = java.util.Collections.unmodifiableList(characterRoster);
        allies = java.util.Collections.unmodifiableList(allyRoster);
        enemies = java.util.Collections.unmodifiableList(enemyRoster);
        this.rng = rng;
        queue = new Queue();
        queue.addCombatants(characterQueue);
        queue.addCombatants(enemyQueue);
        queue.initialize();
        // Pfix E2: wire up "speed change to re-sort the action bar". Without this, a speed buff/debuff does
        // not take effect immediately (the speed cached in Signal is only refreshed at
        // initialize/setTopZero/resetSignal).
        for (Character c : characterQueue) {
            c.setSpeedChangeListener(this::onSpeedChanged);
        }
        for (CanHit e : enemyQueue) {
            e.setSpeedChangeListener(this::onSpeedChanged);
        }
        // The units' buff managers hold the battle so a state that leaves a unit can be announced
        // (the STATE_ENDED event, whose reader is 1211's "when [生息] ends ...").
        for (Character c : characterQueue) {
            c.getBuffManager().setBattle(this);
        }
        for (CanHit e : enemyQueue) {
            e.getBuffManager().setBattle(this);
        }
    }

    /**
     * Which characters released their technique before the battle, stated at construction.
     *
     * <p><b>Why this exists as a constructor argument.</b> A technique happens on the map, before the battle
     * exists, so the engine cannot observe it -- it has to be told. {@link #markTechniqueUsed(CanHit)} is the
     * other way to tell it, and it is the right one for a caller that assembles a battle and decides
     * afterwards. But the decision is really part of "what battle are we fighting": a player chose which
     * characters used a technique while setting the team up, and passing it here keeps that fact with the
     * roster instead of as a sequence of calls whose order the engine has to tolerate.
     *
     * <p><b>What is not modelled, and why.</b> Not "released or not" as a per-character toggle the engine
     * could default. A technique consumes a limited map resource and the map decides; the engine's business
     * is only the battle-start consequence, which is the {@code 秘技} state.
     *
     * @param released       whether each character released its technique, keyed by character id. A character
     *                       absent from the map did not. {@code null} means none did.
     * @param archetypeHolder the unit holding the technique archetype mark (「美学底本」), or {@code null}
     *                       for none. Read by {@link #markArchetypeHolder} as well; only Pearl (真珠) reads it.
     */
    public record Techniques(Map<Integer, Boolean> released, CanHit archetypeHolder) {

        /**
         * The characters that released a technique, with no archetype holder.
         *
         * @param cids the character ids that used their technique
         * @return the flags
         */
        public static Techniques released(Integer... cids) {
            Map<Integer, Boolean> map = new LinkedHashMap<>();
            for (Integer cid : cids) {
                if (cid != null) {
                    map.put(cid, Boolean.TRUE);
                }
            }
            return new Techniques(map, null);
        }
    }

    /**
     * A battle whose characters' technique releases are stated up front.
     *
     * <p>An overload rather than a changed signature: the two existing constructors are called from more
     * than a thousand places, and none of them is about techniques.
     *
     * @param characterQueue our side
     * @param enemyQueue     the enemy camp
     * @param techniques     which characters released a technique; {@code null} means none did
     */
    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue, Techniques techniques) {
        this(characterQueue, enemyQueue);
        this.techniques = techniques;
        applyStatedTechniques();
    }

    /**
     * A battle whose technique releases and random source are both stated up front.
     *
     * @param characterQueue our side
     * @param enemyQueue     the enemy camp
     * @param rng            the random source, so a run is reproducible
     * @param techniques     which characters released a technique; {@code null} means none did
     */
    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue, Random rng,
                  Techniques techniques) {
        this(characterQueue, enemyQueue, rng);
        this.techniques = techniques;
        applyStatedTechniques();
    }

    /**
     * Turns the construction-time technique flags into the units that will be given the {@code 秘技} state.
     *
     * <p>Resolution is by character id against {@link #characters}, and the enemy camp is deliberately not
     * searched: a monster has no technique. A cid that matches nobody is ignored rather than refused -- the
     * flags describe a team the caller assembled, and a battle may legitimately be built from a subset of
     * it (a wave, a test fixture), which is not an error.
     */
    private void applyStatedTechniques() {
        if (techniques == null) {
            return;
        }
        for (Character member : characters) {
            if (member == null) {
                continue;
            }
            if (Boolean.TRUE.equals(techniques.released().get(member.getCid()))) {
                markTechniqueUsed(member);
            }
        }
        if (techniques.archetypeHolder() != null) {
            markArchetypeHolder(techniques.archetypeHolder());
        }
    }

    /**
     * Announces a policy-reported spend.
     *
     * @param spender who spent it
     * @param amount  how many were spent
     */
    private void onSkillPointSpent(CanHit spender, int amount) {
        broadcastSkillPointSpent(amount);
        fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, spender, null, 0, amount));
    }

    /**
     * A unit's speed changed to re-sort its action time from the "action progress already accumulated"
     * (Pfix E2).
     *
     * <p>The caller is {@link CanHit#notifySpeedChanged()}; it compares against the old value first and only
     * notifies on a real change, so no duplicate check is needed here.
     *
     * @param target the unit whose speed changed
     */
    private void onSpeedChanged(CanHit target) {
        if (target == null || target.isDeath()) {
            return;
        }
        queue.refreshSpeed(target);
        currentMove = queue.getCurrentActor();       // the re-sort may have changed who is next
    }

    public void castImmediate(Skill skill, CanHit user, List<? extends CanHit> targets) {
        if (skill == null || user == null || targets == null) return;
        if (user.isDeath()) return;

        skill.execute(this, user, targets);

        processRequests();

        removeDeadCombatants();
    }

    public boolean requestSkill(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return false;
        }
        if (user.isDeath()) {
            return false;
        }
        skillRequest(skill, user, target);
        return true;
    }

    // After releasing ultra skill must call processRequests!
    // NO BEFAN YOY DID IT

    /**
     * The state that means "the ultimate is activated": a clause grants it and {@link #isUltraReady} honours it, which is the engine's one spelling of "activating the ultimate".
     * Not from the data -- the documents name the effect, not a state -- so it is named here once, like every other engine-side marker.
     */
    public static final String ULTIMATE_ACTIVATED_STATE = "终结技已激活";

    /**
     * Whether this unit can cast its ultimate right now: reaching the "ult threshold" is
     * enough, it does not have to be filled to the maximum.
     *
     * <p>The threshold comes from the skill data's {@code spNeed} (tbgd {@code AvatarSkillConfig.SPNeed},
     * see {@link SkillData#getSpNeed()}).
     * 5 of the 93 characters have a threshold below the maximum -- Yunli (云璃) 120/240,
     * Argenti (银枝) 90/180, Evanescia (绯英) 240/480, Feixiao (飞霄) 6/12, Cyrene (昔涟) 12/24. When the data is missing
     * it falls back to "fill {@code maxEnergy}" (the old behaviour).
     *
     * <p>After it is cast it is zeroed (see {@link #castUltra}): for the majority of characters whose
     * threshold == max this is equivalent to before; for the exceptions above it is equivalent to
     * "one cast consumes the threshold part". The game docs say
     * "Energy required to cast 120 (max 240)" -- "required" is a gate.
     *
     * <p>Note: This is the only place on `Battle` that reads {@code spNeed}, so if the distinction between
     * "consume the threshold" and "zero out" is ever needed, changing this place and {@link #castUltra} is enough.
     */
    public boolean isUltraReady(CanHit user) {
        return ultimates.isReady(user);
    }

    /**
     * The energy this unit needs to cast its ultimate: prefers the skill data's {@code spNeed},
     * otherwise falls back to {@code maxEnergy}.
     */
    public double ultraEnergyCost(CanHit user) {
        return ultimates.energyCost(user);
    }

    public boolean castUltra(CanHit user, List<? extends CanHit> targets) {
        return ultimates.cast(user, targets);
    }

    /**
     * The energy the ultimate now settling consumed ({@code 0} when none is in flight).
     */
    public double getLastUltEnergySpent() {
        return ultimates.lastEnergySpent();
    }

    private void registerPartyResources() {
        partyResources.registerFrom(allies);
    }

    /*
     * The party's own counter, or {@code null} when this battle has none by that name.
     *
     * <p>Asked by both sides of the DSL: a `self_resource:NAME` read and a `GAIN_RESOURCE`/`SPEND_RESOURCE` write. They try the
     * unit first, exactly as before, and fall back here -- so nothing changes for a resource a character owns itself.
     */
    /* The party counter's current value, or 0 when this battle has no such counter. */
    /**
     * The laugh-point factor of Elation damage: {@code 1 + 笑点×5/(笑点+240)}.
     *
     * <p>From the spec
     * ({@code 基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))}, attack power banned). The curve lives
     * here so the three settlement sites cannot drift apart, the same reason {@code ELATION_DAMAGE_BOOST} is folded in
     * one step.
     *
     * <p>⚠⚠ <b>Which laugh points</b>, and this is the correction the encyclopedia entry forced. It reads
     * {@link #countedLaughPoints()} - the <b>merged snapshot</b> taken when each holder received 【好活当赏】 - and NOT
     * the party's live pool, which is what it used to read. The entry says the state takes 「本次阿哈时刻的笑点」 and that
     * 「技能效果和欢愉伤害以【好活当赏】计入的笑点数计算」, with several holders 「合并计算」.
     *
     * <p>Two consequences, both intended: damage is computed from the count that was frozen when the state arrived (so
     * the pool growing afterwards does not move it), and with NO holder the factor is 1.0 - a battle that never had an
     * Aha Moment has no laugh points folded into anything.
     */
    public double elationLaughMultiplier() {
        // The cast's own count wins while that cast is being resolved: 「固定计入 20 笑点」 is a fact about the CAST, and
        // its damage is assembled from the skill's row, so nothing else could carry it. Otherwise the merged snapshot.
        Integer claimed = castOutcome.countedLaughs();
        double laughs = claimed != null ? claimed : countedLaughPoints();
        return 1 + laughs * 5.0 / (laughs + 240);
    }

    /**
     * Claims the laugh count for the NEXT cast to begin: 「使其立即施放 1 次固定计入 20 笑点的欢愉技」.
     *
     * <p>Called by {@code CAST_SKILL} right before it executes the cast it commands - see
     * {@link com.laosun.aluminium.models.battle.CastOutcome} for why the claim and the cast cannot be the same moment.
     *
     * @param laughs the count that cast's Elation damage is folded with (0 is legal: "counting nothing")
     */
    public void markNextCastCountedLaughs(int laughs) {
        castOutcome.markNextCountedLaughs(laughs);
    }

    /**
     * The laugh count the cast being resolved is settled with, or {@code null} when that cast stated none.
     *
     * <p>Read by the fold ({@link #elationLaughMultiplier()}) and by the judge that pins the window: a cast's count must
     * not outlive its cast, and "the window is empty again" is the fact the behavioural readings are compared against.
     */
    public Integer castCountedLaughs() {
        return castOutcome.countedLaughs();
    }

    /**
     * The laugh points a settlement of Elation damage will use, once the count is modelled as the document states it.
     *
     * <p><b>Not wired into the factor yet, on purpose.</b> The encyclopedia says the count is 「本次阿哈时刻的笑点」
     * frozen when the state is granted, merged across holders, each timed on its own turns - and the elapsed part of
     * that model is what this and {@link #noteElationGift} implement. The factor still reads the live party pool, so
     * that building this changes no number anywhere; switching the factor over is the second step, and it will move
     * every Elation expectation in the suite at once.
     */
    public int countedLaughPoints() {
        return partyResources.countedLaughPoints();
    }

    /**
     * A unit received 【好活当赏】: freeze the party's 笑点 as ITS count, to run down over {@code turns} of its own turns.
     *
     * <p>Called wherever the state is granted, whatever spelling the rule used, so 「将本次阿哈时刻的笑点计入该状态」 has one
     * implementation rather than one per rule.
     *
     * @param turns how many of the holder's turns it lasts (the document states 2)
     */
    public void noteElationGift(CanHit holder, int turns) {
        partyResources.noteElationGift(holder, turns);
    }

    /**
     * One holder's frozen count, 0 when it holds none. For judges, and for the demo's output.
     */
    public int laughSnapshotOf(CanHit holder) {
        return partyResources.laughSnapshotOf(holder);
    }

    /**
     * How many holders are counted right now - the "how many【好活当赏】" a judge can read.
     */
    public int laughSnapshotHolders() {
        return partyResources.laughSnapshotHolders();
    }

    /**
     * Ends {@code unit}'s turn: the document's own boundary for anything timed in a unit's turns.
     *
     * <p>Note: Exposed because a per-turn duration has to be drivable from a judge as ONE action. {@link #afterMove}
     * is the engine's own path and it does more than this (the actor's hook, the buff tick, the extra-turn pairing), so
     * a case that wants to say "two of its turns have passed" should not have to reproduce that sequence - the first
     * version of the snapshot judge fired {@code TURN_END} directly, which is a different event from the boundary and
     * left the snapshot unticked.
     */
    public void endTurnOf(CanHit unit) {
        if (unit == null) {
            return;
        }
        partyResources.tick(unit);
        fire(TriggerPayload.Simple.of(TriggerEvent.TURN_END, unit, unit, 0, 0));
    }

    public int partyResourceValue(String id) {
        return partyResources.value(id);
    }

    public com.laosun.aluminium.models.Resource partyResource(String id) {
        return partyResources.resource(id);
    }

    /* Notes which resource a change is about, then fires. */
    /**
     * Records the class of the debuff that just landed (see {@link #lastAppliedDebuffClass()}).
     */
    public void noteAppliedDebuffClass(com.laosun.aluminium.enums.DebuffClass debuffClass) {
        triggerFacts.appliedDebuffClass(debuffClass);
    }

    /**
     * The class of the debuff that just landed, or {@code null} for a state belonging to neither family.
     */
    public com.laosun.aluminium.enums.DebuffClass lastAppliedDebuffClass() {
        return triggerFacts.appliedDebuffClass();
    }

    public void noteChangedResource(String resource) {
        triggerFacts.changedResource(resource);
    }

    /**
     * The resource the change being evaluated is about, or null.
     */
    public String lastChangedResource() {
        return triggerFacts.changedResource();
    }

    public void startBattle() {
        if (getStatus() != Status.NOT_STARTED) {
            throw new IllegalStateException(
                    "startBattle() may be called once per battle; this one is already " + getStatus()
                            + ". A second call would re-run every opening hook (BATTLE_START, party "
                            + "resources, technique states) and double their effects.");
        }
        battleResult.start();
        // [WAREHOUSE] "after the character is obtained, or while the character is in the party": a member carrying a `warehouse/<cid>.json` is heard through it, with her own
        // battle table untouched. Registered before every hook below, so a warehouse clause can answer the opening too.
        for (Character member : characters) {
            if (member == null) {
                continue;
            }
            TriggerTable warehouse =
                    com.laosun.aluminium.data.TriggerTables.warehouse(member.getCid());
            if (warehouse != null && !warehouse.isEmpty()) {
                registerWarehouseListener(member, warehouse);
            }
        }
        registerPartyResources();   // before any hook, so a BATTLE_START rule can already gain a shared counter
        attachBattleSkills();
        // Before the technique states and every BATTLE_START rule: the map hit happened first, so the bar a rule
        // reads is the bar the battle opens with.
        applyDeclaredMapAttacks();
        applyTechniqueStates();   // before every BATTLE_START rule, so `self has_state 秘技` already answers
        for (Signal signal : queue.snapshot()) {
            signal.getCanHit().onBattleStart(this);
        }
        // Note: after the opening hooks, let every combatant's trigger table see BATTLE_START.
        // Deliberately after `onBattleStart` so an opening buff is already in place when a trigger
        // reads its own state (e.g. "restore 30 energy at the start of battle").
        fire(TriggerPayload.Simple.of(TriggerEvent.BATTLE_START));
        processRequests();
        // Note: the second phase. Some clauses read a fact that another BATTLE_START rule produces -- the team's
        // skill point cap, for instance, which a talent raises -- and BATTLE_START rules run in team order, so
        // such a clause would answer differently depending on where its raiser stood. Let the first phase settle,
        // then give those clauses their moment.
        fire(TriggerPayload.Simple.of(TriggerEvent.BATTLE_READY));
        processRequests();
        checkResult();
    }

    /**
     * Attach the map skills at the start of a battle: map basic attack (slot 6) and
     * technique (slot 7).
     *
     * <p>Why here and not in {@code CharacterFactory}: these two slots are things on the map,
     * not a character's permanent skills -- they only make sense at the moment of "entering battle".
     * In the data the map basic attack's attack type is {@code MazeNormal} and the technique's is {@code Maze},
     * while the in-battle basic attack is the {@code Normal} of slot 1; the two are not the same thing.
     *
     * <p>The technique's effect (e.g. Jing Yuan (景元)'s "at the start of the next battle [神君]+3 hits")
     * has to wait for the events plus the trigger table; this method only attaches the skill itself
     * (readable and executable from the data).
     *
     * <p>Only our characters get them: monsters have no map skills ({@code EnemyFactory} installs their own
     * basic attack).
     */
    private void attachBattleSkills() {
        for (Character character : characters) {
            if (character.isDeath()) {
                continue;
            }
            for (SkillType type : List.of(SkillType.MAZE, SkillType.TECHNIQUE)) {
                if (character.getSkills().containsKey(type)) {
                    continue;                        // already explicitly installed (test or custom): do not overwrite
                }
                Integer slot = Constant.SKILL_SLOT.get(type);
                if (slot == null) {
                    continue;
                }
                character.setSkill(type, new DefaultSkill(character.getCid(), slot, 1, data.skills()));
            }
        }
    }

    /**
     * Applies the map basic attacks declared before the battle - 「攻击敌人，进入战斗后削弱敌方目标对应属性韧性」.
     *
     * <p>The numbers come from the attacker's <b>map</b> skill (slot 6), never from the in-battle basic attack: the
     * data files them under different slots, and the sentence names the map one (attack type {@code MazeNormal},
     * effect {@code MazeAttack}). 1002 Dan Heng's slot 6 says 30 toughness and Wind - the two values this reads.
     *
     * <p>Through {@link #reduceToughness}, so the engine's one weakness rule applies: only an enemy weak to that
     * element loses toughness at all, and an emptied bar settles the break right here - the state the enemy then
     * carries into the first turn. A map skill whose data states no element or no stance reduces nothing rather
     * than being guessed at (the data really does contain such skills).
     */
    private void applyDeclaredMapAttacks() {
        for (com.laosun.aluminium.models.battle.PreBattle.MapAttack hit : preBattle.mapAttacks()) {
            // The cast is safe: `declareMapAttack` refuses a target that is not an enemy.
            Enemy enemy = (Enemy) hit.target();
            if (enemy.isDeath()) {
                continue;                                  // a map hit can only land on a living monster
            }
            Skill mapSkill = hit.attacker() instanceof Character character
                    ? character.getSkills().get(SkillType.MAZE) : null;
            if (mapSkill == null || mapSkill.getData() == null || !mapSkill.getData().isLoaded()) {
                // Not skipped: the caller stated that this unit's map hit landed, so the number to read must exist -
                // a silent no-op would look exactly like "the map attack does nothing".
                throw new IllegalStateException(
                        "The declared map attack names " + hit.attacker().getName()
                                + ", but that unit has no loaded map skill (slot 6), so its toughness reduction "
                                + "cannot be read; declare a hit only for a unit whose map attack really happened");
            }
            double stance = mapSkill.getData().stanceFor(true);
            DamageElement element = mapSkill.getData().getElement();
            if (stance <= 0 || element == null) {
                continue;                                  // the data gives this map hit nothing to reduce
            }
            reduceToughness(hit.attacker(), enemy, element, stance, SkillCategory.MAZE_NORMAL);
        }
    }

    public void stepForward() {
        if (isOver()) {
            return;                                  // terminal state: the action bar does not advance
        }
        // EXTRA_TURN_STARTED, and why the QUEUE is asked rather than tracked here.
        // `Queue.moveExtraTurn()` consumes the pending flag as it hands the turn over, so from the turn's own
        // TURN_START onward nothing outside the queue can tell an extra turn from an ordinary one. The queue holds
        // that fact at exactly one moment - just before this move - so it is read here.
        // Firing BEFORE `move()` is deliberate: "at the start of this extra turn" has to be in place before the unit
        // acts (1503 grants a resource the archetype is about to spend), and after `move()` it is too late.
        // The unit is remembered in `ExtraTurnState` because the END cannot be announced here - the turn has not
        // been acted out yet. See `afterMove()`.
        // A grant that never becomes a turn (the holder dies or leaves the queue) never reaches this branch, so it
        // announces neither boundary: the honest reading is that no extra turn happened.
        // An INSERTED action (TurnInsertAction) is a FREE turn - 「不消耗回合数」 - so it is announced and treated exactly
        // like an extra one, or its unit's timed effects would be counted down by a turn that costs nothing.
        boolean startingExtraTurn = queue.hasPendingExtraTurn() || queue.hasPendingInsertedAction();
        queue.move();
        currentMove = queue.getCurrentActor();
        if (startingExtraTurn && currentMove != null) {
            extraTurns.begin(currentMove.getCanHit());
            fire(TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, extraTurns.actor(),
                    extraTurns.actor(), 0, 0));
        }
    }

    private final com.laosun.aluminium.models.battle.ExtraTurnState extraTurns =
            new com.laosun.aluminium.models.battle.ExtraTurnState();

    /**
     * Whether the battle is already over (won or lost).
     */
    public boolean isOver() {
        return battleResult.isFinished();
    }

    /**
     * Decide the outcome and set the status. Idempotent: once terminal it does nothing
     * (terminal states never roll back).
     *
     * <p>The rules:
     * <ul>
     *  <li>A side being wiped out means that side loses -- this uses {@code allMatch(isDeath)}, so
     *       an empty list also counts as wiped out (an empty side has simply been cleared).</li>
     *  <li>At {@link Status#NOT_STARTED} nothing is decided: the battle has not started, so there is no
     *  outcome to speak of. So this judgement only takes effect after {@link #startBattle()}.</li>
     *  <li>Both sides wiped out at the same time to {@code LOSE} (loss is checked before win, and it will
     *       not keep judging from a terminal state).</li>
     * </ul>
     *
     * @return the current status after the judgement
     */
    public Status checkResult() {
        return battleResult.check();
    }

    /*
     * Give {@code actor} an extra turn: the next {@link #stepForward()} is taken by it,
     * and it costs no action value (the clock does not move, so the round does not change, see {@link #getRound()}).
     *
     * <p>The typical use is a kill-type talent (Seele (希儿) and the like): call it inside
     * {@code afterMove()} -- that is, after {@code queue.setTopZero()} -- so that its normal turn schedule
     * stays untouched and the extra turn is a free one.
     *
     * <p>During an extra turn, someone else's ultimate must not be inserted (see {@link #castUltra}) --
     * that is a rule requirement; inserting another ultimate inside an extra turn turns "extra" into
     * "infinite chain".
     *
     * @param actor the unit that gets the extra turn
     * @return {@code true} = scheduled; {@code false} if the target is dead / not in the queue
     */
    /**
     * Makes a unit act at the next step, outside the action order (the game's {@code TurnInsertAction}).
     */
    public boolean insertAction(CanHit actor) {
        return queue.insertAction(actor);
    }

    public boolean grantExtraTurn(CanHit actor) {
        return queue.grantExtraTurn(actor);
    }

    /**
     * The currently scheduled extra-turn actor; {@code null} if there is none.
     */
    public CanHit getExtraTurnActor() {
        return queue.getExtraTurnActor();
    }

    /**
     * The current round: derived from the action bar's accumulated action value; the first round = 1.
     *
     * <p>One round = 100 action value, the first round = 150 (see {@code Queue.initialize()}).
     * This is only a query point; the real round driving (outcome decision, stage round limit) happens elsewhere.
     *
     * @return the round, starting from 1
     */
    public int getRound() {
        return queue.getRound();
    }

    public void beforeMove() {
        if (currentMove == null) {
            return;
        }
        CanHit actor = currentMove.getCanHit();
        // "before the [协奏] state ends it will not enter its own turn and cannot act" (one link of a suspended turn): a suspended unit's turn passes *without it* -- no DOT
        // tick, no TURN_START, no own hooks. Note: Deliberately not a control: a control still lets the turn arrive (and
        // the DOTs tick), which is a different sentence; see AbstractBuff#suspendsTurns.
        if (actor.getBuffManager().suspendsTurns()) {
            return;
        }
        // Damage over time is settled at the start of the turn -- of any unit, not only an
        // enemy. An `instanceof Enemy` guard here would be the visible edge of the old
        // design (a DOT could only exist on an Enemy); now that a DOT is an ordinary buff there is
        // nothing enemy-shaped left to test for. That guard is also what kept "the boss burns us"
        // inexpressible, so removing it is the point of the migration, not a side effect.
        tickDots(actor);
        // Note: Immediately after the DOT pass and before the early duration tick: a regeneration settles N
        // times for a `turns: N` buff for exactly the reason the comment below gives for a DOT -- and a `TURN_START`
        // rule could not do it, because that event fires after the tick that removes the buff.
        tickRegens(actor);
        if (actor.isDeath()) {
            return;
        }
        // Order matters: settlement above runs *before* this tick, so a DOT created with N turns
        // settles N times -- on the turn it is attached through its Nth turn -- and only then does the
        // early tick count it down and, at zero, remove it. Swapping the two gives N-1 settlements, and
        // for a 1-turn DOT it burns for nothing. `DotBuff` is an early buff precisely so this line is
        // what counts it down.
        // 「不消耗回合数」: a free (extra / inserted) turn spends no duration.
        boolean freeTurn = extraTurns.isTakenBy(actor);
        if (!freeTurn) {
            actor.getBuffManager().beforeMove();
        }
        // (d): and the buffs on the rest of the field whose clock is this unit's ("at the start of Sunday (星期日)'s own every turn the
        // [蒙福者] state's remaining turns are reduced by 1"). Swept here rather than inside the manager, because "whose turns count it"
        // is a fact about the battle's turn boundary, not about the unit that happens to carry the buff.
        if (!freeTurn) {
            tickForeignBuffs(actor, true);
        }
        if (actor.isDeath()) {
            return;
        }
        // Note: "the turn began", delivered to the data-driven tables. Deliberately here and not as a
        // new buff interface: turn boundaries stay `MoveEvent.beforeMove/afterMove` for buffs (see
        // EventBusTest.turnBoundariesAreStillMoveEvent). What is added is only the *data* subscription
        // to the same moment -- a JSON rule cannot implement a Java interface, so without this event
        // "at the beginning of the turn, ..." would have no trigger source at all.
        //
        // Position matters twice over:
        //  * after `getBuffManager.beforeMove`, so buffs that expire at this turn boundary are
        //     already gone and a rule cannot read a dead buff's state;
        //   * before `actor.beforeMove(this)`, i.e. before the actor's own move hook, so a rule that
        //     heals or buffs has taken effect by the time the character actually moves.
        // `actor` = the character whose turn it is; `target` is that same character, because for
        // "the beginning of the wearer's turn" the wearer is both who caused it and who it happens to.
        // Passing both is what keeps the two natural spellings -- `actor == self` and `target == self`
        // -- equivalent here, so a rule cannot be silently dead because the author picked the other
        // one. Every other character still evaluates the event with itself as `self`, so only the
        // actor's own table can match.
        //
        // It fires once per turn, extra turns included -- an extra turn really is one.
        //
        // Firing limits ("cooldown" / "once per battle") are counted down here, for the unit whose turn
        // is beginning and *before* the rules below get their chance: `cooldown: 1` therefore means "at
        // most once per own turn". It is this actor's counters, not the event actor's, because a
        // limit belongs to the rule's owner -- a rule of mine that fires on somebody else's attack still
        // comes back on MY turn (TriggerLimitTest.otherPeoplesTurnsDoNotCountTheCooldownDown).
        actor.tickTriggerCooldowns();
        // "within one action" begins here: the action boundary already existed (this event brackets
        // `performAction` and its settlement), so the lethal set is cleared at it.
        damageMarks.clear();
        fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, actor, actor, 0, 0));
        // "lasting until the start of the next ... turn" ends HERE: the owner's own turn is beginning.
        actor.getBuffManager().removeWithLifetime(AbstractBuff.Lifetime.NEXT_TURN_START);
        // A countdown exists to HAVE a turn -- this is the moment its reader waits for
        // ("at the start of the countdown turn, Robin (知更鸟) exits the [协奏] state and acts immediately"). The countdown has no table of its own, and our
        // characters' tables are what subscribe, so the ordinary ally broadcaster is the right one.
        if (actor instanceof Countdown countdown) {
            fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.COUNTDOWN_TURN, countdown, countdown, 0));
        }
        actor.beforeMove(this);
    }

    /*
     * Settle the damage over time on one unit: first applied, first settled (in application order).
     *
     * <p>DOT goes through the full damage zones (it takes DMG boost and defence/resistance; vulnerability/reduction
     * are injected by the {@code onDamage} hook), but it cannot crit -- expressed by {@link DamageType#DOT}'s
     * {@code crittable=false}.
     *
     * <p><b>It settles; it does not consume a turn.</b> The DOT is an ordinary {@code DotBuff} now, so its
     * duration is counted down by the buff manager's early tick -- the very next statement in
     * {@link #beforeMove()}. Doing both here would burn two turns per settlement. The reason the damage
     * cannot simply move into the buff is the reverse: {@code AbstractBuff.tickEffect} gets no
     * {@code Battle}, and settling damage needs the zone set.
     *
     * @param target the unit carrying the DOTs (may be a character -- a boss burning us is the same path)
     * @return the total damage settled this time (the sum of all DOTs)
     */
    /**
     * Settles <b>one extra instance</b> of a damage-over-time state on a unit, right now, at {@code percent} of what that
     * state is currently dealing.
     *
     * <p>"make the laceration state it is currently under <b>immediately produce 1 instance</b> of damage equal to 85% of the original damage" (1111 Luka (卢卡) talent). It mirrors
     * {@link #tickDots(CanHit)} - same source, element and {@link DamageType#DOT}, same {@code EnergyGrant.KILL_ONLY} - so
     * this is "the state ticked once more", not a new kind of damage. Note: The duration is <b>not</b> touched: the sentence
     * asks for one extra instance of damage, not for the state to age.
     *
     * <p>Note: The layer ceiling is honoured while summing (the same per-state grouping {@code tickDots} uses): a capped DOT
     * ticks its <b>capped</b> total, because "the damage it is currently under" is what it is dealing now.
     *
     * @param target  the unit carrying the state
     * @param element the element whose state is to tick (an element IS a state in this engine)
     * @param percent the share of that state's current damage (0.85 = "85%")
     * @return the damage actually settled
     */
    public double tickDotStateNow(CanHit target, DamageElement element, double percent) {
        return ticks.dotStateNow(target, element, percent);
    }

    public double tickDots(CanHit target) {
        return ticks.dots(target);
    }

    /**
     * Settle every <b>heal over time</b> on one unit: the twin of {@link #tickDots(CanHit)}, and placed
     * right beside it so the two cannot drift apart.
     *
     * <p>"at the start of each of the target's turns, restore to it health equal to 20% of Natasha (娜塔莎)'s Max HP + 192, lasting 2 turns" needs this rather than a
     * {@code TURN_START} rule: buffs are counted down by the early tick, which happens <b>after</b> this pass and
     * <b>before</b> {@code TURN_START}, so a {@code turns: 2} regeneration settles twice here while a {@code turns: 2}
     * state read from {@code TURN_START} would heal once.
     *
     * <p>Note: The amount was derived when the rule fired ("equal to a share of Natasha's Max HP" is a share of the applier's panel) and
     * is frozen here; the applier is still carried, because {@code Battle.heal} reads their
     * {@code OUTGOING_HEALING_BOOST}.
     *
     * @param target the unit carrying the regenerations
     * @return the total restored this time
     */
    public double tickRegens(CanHit target) {
        return ticks.regens(target);
    }


    public boolean performAction(Skill skill, List<? extends CanHit> targets) {
        return cast.performAction(skill, targets);
    }

    public boolean useSkill(Skill skill, List<? extends CanHit> target) {
        return cast.useSkill(skill, target);
    }

    public void afterMove() {
        if (currentMove == null) {
            return;
        }

        CanHit actor = currentMove.getCanHit();

        if (actor.isDeath()) {
            actor.getBuffManager().clearAll();
            queue.removeCombatant(actor);
        } else if (queue.isInActionOrder(actor)) {
            // Note: Only an actor that HAS a place in the order has a cycle to re-time. An inserted action carries a signal that was never
            // scheduled, and `setTopZero` would re-time the top of the heap -- i.e. somebody else.
            queue.setTopZero();
        }
        currentMove = null;
        removeDeadCombatants();
        if (!actor.isDeath()) {
            actor.afterMove(this);
            if (!extraTurns.isTakenBy(actor)) {
                actor.getBuffManager().afterMove();
                tickForeignBuffs(actor, false);
            }
            // "at the end of the turn": the turn is over HERE -- after the actor's own hook and the late tick, so a rule
            // sees the state the turn ended in.
            // A timed weakness is counted in the TARGET's own turns, so it runs down when that unit's turn ends.
            if (actor instanceof com.laosun.aluminium.models.enemy.Enemy ticking) {
                ticking.tickTimedWeaknesses();
            }
            // 「各个【好活当赏】的持续时间各自独立计算」: a laugh snapshot is counted in ITS HOLDER's own turns, exactly like
            // the timed weakness above is counted in the target's. Same boundary, same reason.
            endTurnOf(actor);
            // "(if not restored) otherwise it will immediately fall into the unable-to-fight state" (140's Moon Cocoon's Ward (月茧之庇)): a death that a deferring state held
            // is committed HERE -- the carrier's turn is over (so it really did "act normally"), and the next turn has not begun
            // (so a heal or a shield up to this point still saves it). Nothing removed the state, so it falls now.
            if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {
                actor.perish();
            }
        }
        // EXTRA_TURN_ENDED, and why it belongs HERE rather than beside the start.
        // "At the end of this extra turn" is exactly this boundary: the unit has acted, its own hook has run
        // (`afterMove`) and the late tick is done, so what the start granted has been spent. Announcing it at the move
        // that STARTED the turn - which the first version of this change did - would make "at the start" and "at the
        // end" the same instant, and a pair of events that cannot be told apart is not a pair.
        // Note: Fired before `processRequests()` for the same reason TURN_END is: the rules may still produce effects.
        if (extraTurns.isTakenBy(actor)) {
            extraTurns.clear();
            fire(TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, actor, actor, 0, 0));
        }
        processRequests();
    }

    /**
     * Spends the duration of every buff on the field whose clock belongs to {@code clockOwner}.
     *
     * <p>Iterates the <b>camp</b>, so a buff anchored to one of our characters is found wherever it sits - the
     * point of the feature is that it sits on somebody else.
     *
     * @param clockOwner the unit whose turn boundary this is
     * @param early      {@code true} = before the move, {@code false} = after it
     */
    private void tickForeignBuffs(CanHit clockOwner, boolean early) {
        // ⚠ BOTH camps. This walked `allies` only, so a state on an ENEMY whose clock is one of our characters was never
        // spent: measured, 1203 Luocha's zone -- applied to all enemies, `ticksOn: "self"` -- held `duration() == 2` for fifteen
        // consecutive turns with `tickOwner` correctly set to him. Nothing was wrong with the anchor; the sweep never reached it.
        //
        // The gap was invisible while that state sat on Luocha himself, because then it was the CARRIER's own tick that spent it.
        for (CanHit unit : combatants()) {
            if (unit != null && unit != clockOwner) {
                unit.getBuffManager().tickForeign(clockOwner, early);
            }
        }
    }

    /**
     * Every combatant on both sides, for the sweeps that must reach either camp.
     *
     * <p>⚠ Not `allies` plus `enemies` field access: the roster is fixed after construction but a summon joins mid-battle,
     * and a sweep that read a stale list would miss it -- the same reason {@code Battle.enemyUnits()} exists.
     *
     * @return the combatants
     */
    private java.util.List<CanHit> combatants() {
        java.util.List<CanHit> all = new java.util.ArrayList<>(allies);
        all.addAll(enemies);
        return all;
    }

    public void processRequests() {
        skillRequests.settle();
        processAddRequests();
        // The newly placed summons are on the roster AND in the action bar by now, which is what a rule
        // answering "when summoned" needs before it can touch their action value -- see TriggerEvent.SUMMONED.
        fireSummoned();
        removeDeadCombatants();
    }

    /**
     * Fires {@link TriggerEvent#SUMMONED} for everything summoned since the last settle, once each.
     *
     * <p>The list is drained <b>before</b> the events are fired: a rule that answers "when summoned" by summoning
     * something else must not make this loop chase its own tail (that recursion is bounded by
     * {@link com.laosun.aluminium.models.battle.TriggerFiring#MAX_DEPTH}, but there is no reason to build it), and the new arrival is picked up by the
     * next settle like any other.
     */
    private void fireSummoned() {
        if (summoned.isEmpty()) {
            return;
        }
        for (CanHit summon : summoned.drain()) {
            fire(TriggerPayload.Simple.of(TriggerEvent.SUMMONED, summon, null, 0, 0));
        }
    }

    private void skillRequest(Skill skill, CanHit user, List<? extends CanHit> target) {
        skillRequests.add(skill, user, target);
    }


    /**
     * Assembles every damage zone on the given hit, settles it and applies it to the
     * target, returning the damage actually settled.
     *
     * <p>This is the <b>only</b> public settlement entry point. To learn how much a hit
     * deals, use the returned value - do not assemble it a second time: assembly is
     * additive ({@code addBoost} appends a modifier to the boost zone), so assembling the
     * same {@link Damage} twice would count DMG boost/vulnerability/reduction/weakness twice.
     *
     * @param target the combatant taking the hit
     * @param damage the hit (convention: one damage instance = one Damage object)
     * @return the settled damage, or {@code 0} if the target was already dead
     */
    public double applyDamage(CanHit target, Damage damage) {
        return damageService.applyDamage(target, damage);
    }

    /**
     * The assembly entry point, kept private so {@code applyDamage} stays the only public settlement path.
     *
     * @param damage the hit
     * @return the settled damage
     */
    private double assemble(Damage damage) {
        return damageService.assemble(damage);
    }

    /**
     * The internal settlement entry point with an explicit energy grant.
     *
     * @param target the combatant taking the hit
     * @param damage the hit
     * @param grant  which energy gains this instance may settle
     * @return the settled damage
     */
    private double applyDamage(CanHit target, Damage damage,
                               com.laosun.aluminium.models.battle.DamageService.EnergyGrant grant) {
        return damageService.applyDamage(target, damage, grant);
    }


    /**
     * The only energy gain entry point inside battle: the rules are decided by {@code target}'s own
     * {@link com.laosun.aluminium.models.energy.EnergyProvider}; team charging (Tingyun (停云)/Huohuo (藿藿)/
     * Sunday (星期日)) will in the future also grant energy to other targets from here.
     *
     * @param target the one gaining energy
     * @param gain   one energy gain description (base value + whether it takes the energy gain rate)
     * @return the value actually credited (after truncation by the cap); 0 if nothing can be credited
     */
    public double applyEnergyGain(CanHit target, EnergyGain gain) {
        return energy.apply(target, gain);
    }

    /**
     * Announces energy actually credited: the typed hook and the data-driven tables.
     *
     * @param target the one whose bar moved
     * @param added  how much was credited
     */
    private void onEnergyCredited(CanHit target, double added) {
        broadcastEnergyGain(target, added);
        fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.ENERGY_GAINED, target, target, added));
    }

    /**
     * Convenience entry point: grant someone energy by base value (goes through the energy gain rate).
     *
     * @param target the one gaining energy
     * @param amount the base energy gain value
     * @return the value actually credited
     */
    public double grantEnergy(CanHit target, double amount) {
        return energy.grant(target, amount);
    }

    /**
     * Skill energy gain hook point: called by {@link SkillExecutor} where the skill is executed -- only
     * there does it know the actual hit set (AOE hits everyone, BLAST hits three slots, BOUNCE switches
     * target every instance).
     *
     * <p>Note the distinction: this is the energy gain for "casting a skill" (basic attack 20 / skill 30);
     * the ultimate does not go through {@code onSkillCast} (it zeroes first and then gains 5 in
     * {@link #castUltra}).
     *
     * @param user       the caster
     * @param skill      the skill cast
     * @param hitTargets the actual hit set (empty for buff/healing skills)
     * @return the value actually credited
     */
    public double grantSkillEnergy(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        return energy.grantSkill(user, skill, hitTargets);
    }

    /*
     * Toughness reduction + weakness break trigger: the only toughness reduction entry point in
     * battle, called by {@link SkillExecutor} after each damage instance settles.
     *
     * <p>The rules (HSR.md §3.2 / §3.1):
     * <ul>
     *   <li><b>Only hitting a weakness reduces toughness</b> -- a non-weakness element reduces none of it
     *       ("toughness reduction ignoring weakness" is a character trait of Rappa (乱破)/Himeko (姬子) - Nova
     *  and the like; wait for to put it in data, do not open a default hole here)</li>
     *   <li><b>But super break is not restricted by weakness</b>: while the enemy is already in the broken
     *       state, the whole nominal toughness reduction counts as "the excess part", regardless of whether
     *       the element hits a weakness (the official wording's only condition is "the enemy is in the
     *       weakness-broken state"). A non-weakness attack on an already broken enemy still produces a super
     *       break instance</li>
     *  <li>No toughness bar (the data really does contain enemies with {@code stance = 0}) / already broken /
     *       already dead to no reduction</li>
     *  <li>Toughness reaching zero to the break is triggered from here ({@code Enemy.reduceStance} does no
     *       judgement itself)</li>
     * </ul>
     *
     * <p>The order of the break chain (later tasks add things here): broken state to break damage to 
     * action delay to attach DOT to break energy gain.
     *
     * <p><b>The return value also gives the "excess part"</b>: let the skill's nominal toughness
     * reduction be {@code S} and the remaining toughness {@code T}; this one {@code S} is split between two
     * chains -- break damage uses {@code min(S,T)} ({@link StanceResult#consumed()}), super break damage uses
     * {@code max(0, S-T)} ({@link StanceResult#overkill()}), and the two always sum to {@code S}.
     * So the caller must not keep only one number, otherwise the instance that breaks the toughness would drop
     * the super break part.
     *
     * @param attacker     the attacker (break damage and break energy gain are both credited to it)
     * @param enemy        the target being hit
     * @param element      the element of this damage instance (decides the weakness, and is also the break
     *                     element)
     * @param stanceDamage the toughness reduction points (the skill's {@code stance_list} value, in units of
     *  "points"; not a fixed per-instance value -- for bounces {@link SkillExecutor}
     *                     spreads the total evenly over each instance)
     * @return the toughness reduction result of this instance (how much was actually reduced / how much
     * exceeded / whether a break was triggered)
     */


    public StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
        // No causing cast known (a test, a demo, an enemy skill that builds its damage inline): the break still
        // happens, it just cannot say which skill produced it -- so `from_skill` conditions do not match it.
        return reduceToughness(attacker, enemy, element, stanceDamage, null);
    }

    /**
     * The same, stating the <b>cast</b> whose instance this toughness reduction belongs to.
     *
     * <p>It matters for one sentence family: "when casting the Skill ... causing a weakness break" (1003 Himeko (姬子) eidolon 4) asks not "was something
     * broken" but "did <b>my Skill</b> break it", and the cast category is the only thing that answers it. A break left
     * by a talent's follow-up attack carries {@code UNSPECIFIED} (it is not an active cast) and is therefore correctly
     * <b>not</b> a "cast the Skill" break.
     *
     * @param attacker     the attacker
     * @param enemy        the target being hit
     * @param element      the element of this instance
     * @param stanceDamage the toughness reduction points
     * @param fromCast     the category of the cast this reduction belongs to, or {@code null}
     * @return the toughness reduction result
     */
    public StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage,
                                        SkillCategory fromCast) {
        return breakService.reduce(attacker, enemy, element, stanceDamage, fromCast);
    }

    /**
     * The result of one toughness reduction.
     *
     * <p>The two toughness reduction numbers always sum to this instance's nominal toughness reduction,
     * with nothing double-counted and nothing dropped.
     *
     * @param consumed    the points actually deducted from the toughness bar (break damage uses this)
     * @param overkill    the points beyond the remaining toughness (super break damage uses this; 0 when the
     *                    bar was not emptied)
     * @param breakDamage the settled break damage triggered this time (0 = no break triggered).
     *                    It is settled inside this method via {@link #applyDamage}, which the caller cannot
     *                    reach, so it must be carried out through the return value -- otherwise an attack's
     *                    "total damage" would miss the break chain
     *                    (see {@code AttackEvent#totalDamage})
     * @param broke       whether this instance emptied the toughness and triggered a break
     */
    public record StanceResult(double consumed, double overkill, double breakDamage, boolean broke) {

        /**
         * This instance reduced nothing (no toughness reduction / non-weakness / target already dead):
         * neither chain is produced.
         */
        public static final StanceResult NONE = new StanceResult(0, 0, 0, false);

        /**
         * The toughness reduction input for super break: an equivalent form of {@code max(0, S - T)}
         * (see {@code Battle.reduceToughness}).
         */
        public double superBreakStance() {
            return overkill;
        }
    }

    /**
     * Delay the action by a percentage of the target's action period: {@code delay = period  x  percent},
     * period = {@code 10000 / speed} (consistent with {@code Queue}'s {@code ACTION_THRESHOLD}).
     *
     * @param target  the target being delayed
     * @param percent the delay ratio (0 ~ 1, HSR weakness break = 0.25)
     * @return {@code true} = the target is in the action bar and was really delayed
     */
    public boolean delayMovePercent(CanHit target, double percent) {
        return breakService.delayMovePercent(target, percent);
    }

    /**
     * Called when a broken enemy's turn comes up: decrements the broken turn count and, at zero, restores the
     * toughness bar.
     *
     * <p>⚠ <b>The return value means "this turn is skipped", and the turn on which the unit RECOVERS is not
     * skipped.</b> That is the change from the earlier contract, which answered {@code true} whenever the unit was
     * broken on entry -- including the turn it came back -- so recovery itself cost an action. A weakened enemy
     * therefore lost {@code BROKEN_REMAIN_TURNS + 1} actions and returned one turn later than its own countdown said,
     * which read as "it recovered and then skipped anyway".
     *
     * <p>The caller reads it as "may this unit act": a broken enemy answers {@code true} and spends the turn skipping
     * it, while the turn that ends the break answers {@code false} and the unit takes the field again immediately.
     *
     * @param enemy the enemy whose turn it is
     * @return {@code true} = still broken after this turn's count, so this turn is skipped
     */
    public boolean handleBrokenTurn(Enemy enemy) {
        return breakService.handleBrokenTurn(enemy);
    }

    /**
     * A unit's current aggro value: it decides the probability of the enemy selecting it.
     *
     * <p>This is the stated weight of {@link com.laosun.aluminium.models.battle.AggroTable} with the unit's {@link AttributeType#AGGRO_ADDED_RATIO}
     * applied - "the probability of being attacked is greatly increased" is a <b>soft</b> modifier on the same weighted draw, which is why it is an
     * attribute and not a {@code TauntBuff} (a taunt is the hard "can only be selected", and
     * {@link TargetSelector} is where that lives).
     *
     * @param entity the unit to query
     * @return the aggro value (&gt; 0)
     */
    public double aggroOf(CanHit entity) {
        return com.laosun.aluminium.models.battle.AggroTable.aggroOf(entity);
    }

    /**
     * The aggro table: {@code unit to hit probability}. The probabilities sum to 1.
     *
     * @param allies the candidate units (the caller is responsible for filtering out dead targets first)
     * @return an ordered unit to probability mapping; an empty list returns an empty table
     */
    public Map<CanHit, Double> getAggroTable(List<? extends CanHit> allies) {
        return com.laosun.aluminium.models.battle.AggroTable.table(allies);
    }

    /**
     * The application chance of a debuff:
     *
     * <pre>
     * chance = base chance  x  (1 + caster's effect hit rate%)  x  (1 - victim's effect resistance%)  x  (1 - specific debuff resistance%)
     * </pre>
     *
     * <p>The result is clamped to {@code [0, 1]}:
     * <ul>
     *  <li>Effect hit rate is a damage zone, so with 32% hit rate an 80% base chance to {@code 0.8  x  1.32 = 1.056 to 1.0}
     *       (it never exceeds 100%, but there is also no "excess hit converted into some other benefit");</li>
     *  <li>Effect resistance multiplies the same way: with 30% resistance a 100% base chance to {@code 0.};</li>
     *  <li>{@code specificResistKey} is a {@code STAT_*} string from the data (see {@code Enemy.debuffResist}):
     *  Ice Edge {@code {"STAT_CTRL_Frozen": 1}} makes the key factor {@code (1 - 1) = 0}, i.e. completely immune.
     *  Only {@link Enemy} has this table; characters do not.</li>
     * </ul>
     *
     * <p>Only the probability is computed, no dice are rolled: the roll is in {@link #rollDebuff} (using the
     * injected rng), so an AI can "look only at the expectation" without consuming random numbers.
     *
     * @param caster            the applier (reads {@code EFFECT_HIT_RATE})
     * @param target            the victim (reads {@code EFFECT_RESISTANCE} and possibly a specific resistance)
     * @param baseChance        the base chance on the skill panel (0.8 = 80%)
     * @param specificResistKey the specific resistance key; {@code null} or the target is not an enemy to not looked up
     * @return the application chance, falling in {@code [0, 1]}
     */
    public double hitChance(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        return chances.hitChance(caster, target, baseChance, specificResistKey);
    }

    /**
     * After the chance check, whether this debuff is actually applied.
     *
     * <p>Rolls with the injected {@link #rng}: the same seed to the same battle is reproducible.
     *
     * @param caster            the applier (reads its effect hit rate)
     * @param target            the victim (reads its effect resistance / specific resistance)
     * @param baseChance        the base chance on the skill panel (0.8 = 80%)
     * @param specificResistKey the key of the specific debuff resistance (a {@code STAT_*} string from the data), {@code null} = not looked up
     * @return {@code true} = it hit, so the buff may be applied
     */
    public boolean rollDebuff(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        return chances.rollDebuff(caster, target, baseChance, specificResistKey);
    }

    /**
     * Apply a debuff: the hit check comes first, and only on a hit is {@code addBuff} called.
     *
     * <p>This is the unified entry point for "a skill applying a debuff" -- do not call {@code addBuff}
     * directly inside a skill, otherwise effect hit rate and resistance are bypassed.
     *
     * @param caster            the applier
     * @param target            the target
     * @param buff              the buff to apply
     * @param baseChance        the base chance
     * @param specificResistKey the specific resistance key (may be {@code null})
     * @return {@code true} = it was applied
     */
    public boolean tryApplyDebuff(CanHit caster, CanHit target, AbstractBuff buff,
                                  double baseChance, String specificResistKey) {
        return debuffs.tryApply(caster, target, buff, baseChance, specificResistKey);
    }

    /**
     * Healing amount: it does not touch {@code Damage}; it is an independent set of damage zones.
     *
     * <pre>
     * healing = base amount  x  (1 + outgoing healing boost)  x  (1 + healing taken boost)
     * </pre>
     *
     * <p>Both factors multiply, and they come from different people:
     * {@code OUTGOING_HEALING_BOOST} is read from the one applying the heal (the healer's traces/light cone),
     * {@code HEAL_TAKEN_RATIO} is read from the one being healed (the healing taken bonus; a negative value
     * is healing reduction -- the game has no separate "healing reduction" attribute, see
     * {@code AttributeType}).
     *
     * <p>It only computes the number and does not change HP: the execution is in
     * {@link #heal(CanHit, CanHit, double)}.
     *
     * @param healer     the one applying the heal ({@code null} to only the healing-taken side is computed)
     * @param target     the one being healed
     * @param baseAmount the base healing amount (skill multiplier  x  attribute + flat value, computed by the caller)
     * @return the final healing amount (may be negative -- when healing reduction > 100%; a caller treating it
     * as 0 will be blocked by heal)
     */
    public double calculateHeal(CanHit healer, CanHit target, double baseAmount) {
        if (target == null) {
            return 0;
        }
        double outgoing = healer == null ? 0
                : healer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double taken = target.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get();
        return baseAmount * (1 + outgoing) * conditionalHealBonus(healer, target) * (1 + taken);
    }

    /**
     * The heal bonus the healer declared for a heal <b>on this particular recipient</b>, as a multiplier.
     *
     * <p><b>Why it is evaluated here and not as an attribute.</b> 1105's talent raises her healing by 50% only when
     * the target is at 30% HP or lower. A stat cannot say that -- {@code OUTGOING_HEALING_BOOST} is a fact about the
     * healer alone, so reading it would raise every heal equally, including one on a full-HP ally. And no rule can say
     * it either: there is no pre-heal event ({@code HEALED} fires from {@link #heal} <i>after</i> the amount is
     * computed), so no rule has a moment in which to raise her healing before it applies. The one place that holds the
     * healer, the recipient and the battle together is here.
     *
     * <p>The conditions were parsed at load time and handed over by {@code REGISTER_HEAL_BONUS}; each is asked with
     * the healer as {@code owner} and {@code actor} and the recipient as {@code target}, which is what makes
     * {@code target_hp_percent} read the person being healed.
     *
     * <p>Bonuses multiply together, like every other zone in this engine, and an unconditional bonus cannot be
     * registered at all (the validator refuses it).
     *
     * @param healer the one performing the heal; {@code null} yields no bonus
     * @param target the one being healed
     * @return {@code 1 + Σ} of the bonuses whose conditions hold for this heal
     */
    private double conditionalHealBonus(CanHit healer, CanHit target) {
        if (healer == null || healer.conditionalHealBonuses().isEmpty()) {
            return 1.0;
        }
        // The recipient's HP as it stands now, i.e. BEFORE this heal lands -- which is what the sentence means by
        // "a target whose current HP percentage is 30% or lower".
        com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext ctx =
                new com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext(
                        healer, healer, target, 0, 0, null, this);
        double bonus = 0;
        for (CanHit.ConditionalHealBonus entry : healer.conditionalHealBonuses()) {
            if (entry.condition().test(ctx)) {
                bonus += entry.percent();
            }
        }
        return 1 + bonus;
    }

    /**
     * Perform one heal: compute the healing amount first, then apply it to the target's HP
     * ({@code CanHit.heal} caps it itself and does nothing for the dead).
     *
     * @return the HP actually restored (after truncation by the cap; 0 when the target is dead or the
     * healing amount {@code <= 0})
     */
    public double heal(CanHit healer, CanHit target, double baseAmount) {
        return sustain.heal(healer, target, baseAmount);
    }

    /**
     * Announces a heal that actually restored HP.
     *
     * @param healer who healed
     * @param target who was healed
     * @param healed how much was restored
     */
    private void onHealSettled(CanHit healer, CanHit target, double healed) {
        broadcastHeal(healer, target, healed);
        fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.HEALED, healer, target, healed));
        for (HealObserver observer : observers.healObservers()) {
            observer.onHealSettled(healer, target, healed);
        }
    }

    /*
     * Gain a shield.
     *
     * <p><b>No stacking</b>: it directly overwrites the current shield value (shields in this game normally do
     * not stack; a refresh from the same source is treated as an overwrite).
     * Because {@link CanHit#takeDamage} is "deduct shield first, then HP", "not dying before the shield breaks"
     * holds automatically.
     *
     * <p>Note: This overload states <b>no provider</b>, so no shield boost applies: it is the raw entry point the fixtures
     * and demos use, and the units that call it are stating "this shield came from nowhere in particular". Content
     * goes through {@link #grantShield(CanHit, CanHit, double)} - which is what makes "the shield amount provided by the wearer is increased by X%" mean
     * the <i>giver's</i> shield and not everybody's ({@link #boostedShield}).
     *
     * @param target the one gaining the shield (no effect if already dead)
     * @param amount the shield amount (<= 0 is treated as clearing the shield)
     * @return the shield value actually set
     */
    /**
     * Places a <b>countdown</b> on the action order: a unit that only exists to have a turn at a fixed speed.
     *
     * <p>Called by the {@code START_COUNTDOWN} op. The unit is scheduled by the same queue as everybody else, so every
     * mechanic that already moves the action order (advance, delay, weakness break) moves it too - for free and by
     * construction, which is exactly why "until the countdown turn" cannot be spelled as a `turns` count.
     *
     * @param name  what it is called in logs
     * @param speed its fixed speed (90 for Robin (知更鸟)'s [协奏])
     * @return the countdown, for tests and logs
     */
    public Countdown startCountdown(String name, double speed) {
        return countdowns.start(name, speed);
    }

    /**
     * The same, recording <b>who placed it</b> - the handle {@code actor == countdown} resolves through.
     *
     * @param owner who placed it ({@code null} = unattributed, and then no rule's {@code countdown} term matches it)
     */
    public Countdown startCountdown(CanHit owner, String name, double speed) {
        return countdowns.start(owner, name, speed);
    }

    /**
     * The countdown units placed so far, in the order they were started.
     */
    public List<Countdown> countdowns() {
        return countdowns.all();
    }

    /**
     * The countdowns a given unit placed - the list {@code actor == countdown} reads (mirrors {@code summonsOf}).
     */
    public List<Countdown> countdownsOf(CanHit owner) {
        return countdowns.ownedBy(owner);
    }

    public double grantShield(CanHit target, double amount) {
        return sustain.grantShield(null, target, amount, "");
    }

    /**
     * Gain a shield, <b>as created by {@code provider}</b>.
     *
     * <p>The provider is not decoration: {@code provider}'s {@link AttributeType#SHIELD_BOOST} multiplies the amount,
     * which is the engine's answer to "the shield amount provided by the wearer is increased by 20%" (relic 103 / 128, one light cone). A {@code null} provider
     * means "unknown giver" and applies no boost - the safe direction, since a boost that cannot be attributed would
     * silently strengthen every shield in the fight.
     *
     * @param provider who is granting it ({@code null} = unknown, so unboosted)
     * @param target   the one gaining the shield (no effect if already dead)
     * @param amount   the shield amount before the provider's boost ({@code <= 0} is treated as clearing the shield)
     * @return the shield value actually set
     */
    public double grantShield(CanHit provider, CanHit target, double amount) {
        return sustain.grantShield(provider, target, amount, "");
    }

    /*
     * The same, also recording <b>which rule</b> created the shield.
     *
     * <p>The rule id is what tells two shields from one giver apart: "the shield provided by the Skill" (1001 March 7th (三月七) eidolon 6) must not
     * answer for the shield her eidolon 2 gives at battle start. A grant that names no rule states {@code ""}, and a
     * condition asking for a named rule then correctly answers "no".
     *
     * @param provider who is granting it ({@code null} = unknown, so unboosted)
     * @param target   the one gaining the shield (no effect if already dead)
     * @param amount   the shield amount before the provider's boost ({@code <= 0} is treated as clearing the shield)
     * @param ruleId the id of the rule granting it ({@code ""} = unnamed)
     * @return the shield value actually set
     */
    /**
     * The shield amount after the provider's own boost ("the shield granted is increased by X%").
     *
     * <p>A one-line delegate: the body lives in {@link com.laosun.aluminium.models.BattleDamage}
     * with the rest of the settlement arithmetic, but this name is public API that tests read
     * (measured as load-bearing by {@code JourneyForeverPeacefulTest}: it still returned the bare
     * 1000 until a battle had started), so it keeps its home and its signature.
     */
    public static double boostedShield(CanHit provider, double amount) {
        return BattleDamage.boostedShield(provider, amount);
    }

    public double grantShield(CanHit provider, CanHit target, double amount, String ruleId) {
        return sustain.grantShield(provider, target, amount, ruleId);
    }

    /**
     * Announces a shield that was actually granted.
     *
     * @param provider who granted it
     * @param target   who received it
     * @param value    the value actually set
     */
    private void onShieldGranted(CanHit provider, CanHit target, double value) {
        fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.SHIELD_GRANTED, provider, target, value));
        for (ShieldObserver observer : observers.shieldObservers()) {
            observer.onShieldGranted(provider, target, value);
        }
    }


    /**
     * The members of a unit's opposing camp: our side to enemies; enemies to our side.
     *
     * <p>It does not filter out the dead (the caller filters as needed): it only answers "whose camp is
     * this", not "who can be hit". If a third camp is introduced in the future
     * ({@link com.laosun.aluminium.enums.Camp#NEUTRAL}), the semantics of this method need to be redefined.
     *
     * @param self the querier
     * @return the list of the opposing camp (it is {@code allies} / {@code enemies} itself, not a copy)
     */
    public List<? extends CanHit> getOpponents(CanHit self) {
        return battlefield.opponents(self);
    }

    /**
     * The members of a unit's <b>own</b> camp - the counterpart of {@link #getOpponents(CanHit)}.
     *
     * <p>It exists because an effect can speak about "our side" without being aimed at it: the state-holder selector
     * ({@code holder_of:同袍}) (the bondmate state) asks which of <i>my</i> units carries a state, and an enemy carrying it would be a
     * different sentence entirely. Like its counterpart it does <b>not</b> filter the dead (the caller decides), and
     * it answers an empty list for a unit with no camp rather than guessing one.
     *
     * @param self the querier
     * @return the list of the querier's own camp (it is {@code allies} / {@code enemies} itself, not a copy)
     */
    public List<? extends CanHit> getSideOf(CanHit self) {
        return battlefield.side(self);
    }

    /**
     * Break energy gain: calls this one funnel point at the moment of the break; the rules
     * still belong to the breaker's own provider (the standard implementation gives 5; Rappa (乱破) +10,
     * Harmony Trailblazer (同谐开拓者) +10, Fugue (忘归人) +3 and the like get their own implementations when the
     * characters are really built).
     *
     * @param attacker the one who caused the break
     * @param target   the target that was broken
     * @return the value actually credited
     */
    public double gainBreakEnergy(CanHit attacker, CanHit target) {
        if (attacker == null) {
            return 0;
        }
        EnergyGain gain = attacker.getEnergyProvider().onBreak(attacker, target);
        return gain == null ? 0 : applyEnergyGain(attacker, gain);
    }

    // ==================================================================
    // event broadcasting
    //
    // Broadcast rules (deliberately unified, so that the next batch of events does not invent a third
    // way of writing it):
    //
    //   * Directly involved parties always receive it (even if it is an enemy). Reason: an event
    //     describes a fact, unrelated to camp; and elite/Boss mechanics such as counters and immunity
    //     also need to subscribe to the things happening to themselves.
    //   * Our side additionally, all of them receive it -- `AttackEvent` already established this
    //     convention (Robin (知更鸟) [协奏]/ Tribbie (缇宝) field hangs on the support itself, and "after each
    //     time one of our targets casts an attack" is subscribed by each of them).
    //   * Our members are de-duplicated: if a `CanHit` already received it as an involved party, it does
    //     not receive it a second time (otherwise the same buff is called twice and the effect doubles).
    //
    // `TurnStartEvent` was not created separately: turn start/end is already expressed by
    // `MoveEvent.beforeMove/afterMove` (`CanHit` implements and forwards it), so adding another layer would
    // be a duplicate abstraction.
    // ==================================================================

    /**
     * Energy credited.
     */
    private void broadcastEnergyGain(CanHit target, double added) {
        events.energyGain(target, added);
    }

    /**
     * HP loss. {@code source} is this hit's attacker and may be {@code null}.
     */
    private void broadcastHpLoss(CanHit target, double before, double after, CanHit source, double amount) {
        events.hpLoss(target, before, after, source, amount);
    }

    /**
     * Kill.
     */
    private void broadcastKill(CanHit attacker, CanHit victim) {
        events.kill(attacker, victim);
    }

    /**
     * Heal.
     */
    private void broadcastHeal(CanHit healer, CanHit target, double healed) {
        events.heal(healer, target, healed);
    }

    /**
     * Weakness break.
     */
    private void broadcastBreak(CanHit attacker, CanHit target, DamageElement element) {
        events.weaknessBreak(attacker, target, element);
    }

    /**
     * Skill points credited. Only delivered to our side -- skill points are a resource of our team
     * and the enemy has no share.
     *
     * <p>"Our team" is the camp ({@code allies}), but only a {@link Character} has anything to do with skill
     * points, so this is one of the few places that could equally read {@code characters}: a friendly summon
     * has no trigger table and no skill point hooks of its own. It reads the camp for consistency with the
     * event policy -- and because a future summon that does care should not have to be remembered here.
     */
    private void broadcastSkillPointGained(int amount) {
        events.skillPointGained(amount);
    }

    /**
     * Skill points spent. Only delivered to our side, same as {@link #broadcastSkillPointGained}.
     */
    private void broadcastSkillPointSpent(int amount) {
        events.skillPointSpent(amount);
    }

    // ==================================================================
    // trigger tables
    //
    // The engine fires a named event and each character's data-driven table decides whether to
    // react. Nothing here knows which character it is looking at -- that is the whole point of the
    // trigger table three-way split).
    // ==================================================================

    private final com.laosun.aluminium.models.battle.AttackSequence attackSequence =
            new com.laosun.aluminium.models.battle.AttackSequence();
    private final com.laosun.aluminium.models.battle.CounterNest counterNest =
            new com.laosun.aluminium.models.battle.CounterNest();

    /**
     * How many attacks have finished -- the boundary a rule-level {@code once_per_attack} reads.
     *
     * @return the sequence number
     */
    public int attackSequence() {
        return attackSequence.value();
    }

    /**
     * The settled damage of the firing in progress. Zero outside a firing, and zero at the start of one until something
     * settles.
     *
     * @return the total of the outermost firing
     */
    public double settledDamageThisEvent() {
        return triggerBus.settledDamage();
    }

    /**
     * Rolls a rule-level probability ("there is a 35% fixed probability ...") against the battle's own random source.
     *
     * <p>Deliberately not a fresh {@link Random}: every draw in this engine goes through the injected generator,
     * so a battle built from a seed is reproducible and a test can hand in its own generator to make a coin flip
     * deterministic.
     *
     * @param chance a fraction of 1 (at or above 1 always passes, <b>without</b> consuming a draw; 0 or less
     *               never passes)
     * @return whether the roll passed
     */
    public boolean rollChance(double chance) {
        return chances.roll(chance);
    }

    /*
     * Fires a trigger event with neither actor nor subject (BATTLE_START and similar).
     *
     * @param event the event
     * @return how many rules fired in total
     */
    /**
     * The state named by the {@link TriggerEvent#STATE_ENDED} currently being fired, or {@code null}.
     */
    public String getLastStateEndedName() {
        return triggerFacts.stateEndedName();
    }

    /**
     * Fires {@link TriggerEvent#STATE_ENDED} for a state that has just left {@code carrier}.
     *
     * <p>Note: Save/restore, not a plain clear: a rule handling this event may itself remove another state, and the
     * outer name must survive that. This is the guard {@code lastUltEnergySpent}'s comment warns about.
     */
    public void fireStateEnded(CanHit carrier, String stateName) {
        fireStateEnded(carrier, stateName, 0);
    }

    /**
     * The same moment, carrying <b>how many instances of that state</b> the carrier held. Note: The number goes in the AMOUNT
     * slot, not the hit count: measured, `fireTriggers(event, actor, target, int hitCount, double amount)`, and putting it one
     * place early left `ctx.amount` at zero -- which is what "50% of it" reads.
     */
    public void fireStateEnded(CanHit carrier, String stateName, int magnitude) {
        fire(TriggerPayload.StateEnded.of(TriggerEvent.STATE_ENDED, carrier, stateName, magnitude));
    }

    /**
     * Fires one typed trigger payload.
     *
     * <p>The typed entry exists so a cast-family call site cannot put a skill slot where a hit count
     * belongs; the old positional overloads stay as adapters for the events that have not moved yet.
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.Cast payload) {
        return triggerBus.fire(payload);
    }

    /**
     * Fires a damage-bearing payload. Private because {@code Damage} already has exactly one public
     * settlement entry point ({@link #applyDamage}); this is an announcement, not a second way in.
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    private int fire(TriggerPayload.DamagePayload payload) {
        return triggerBus.fire(payload);
    }

    /**
     * Fires a subject-only payload ({@code KILL}, {@code BREAK}, {@code CRIT_DEALT}).
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    private int fire(TriggerPayload.Subject payload) {
        return triggerBus.fire(payload);
    }

    /**
     * Fires an ally-only subject payload; an enemy subject is deliberately not announced to our tables.
     *
     * @param payload the firing data
     * @return how many rules fired, or {@code 0} for a non-ally subject
     */
    public int fireForAlly(TriggerPayload.AllySubject payload) {
        return triggerBus.fireForAlly(payload);
    }

    /**
     * Fires a plain event payload.
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.Simple payload) {
        return triggerBus.fire(payload);
    }

    /**
     * Fires a state-ended payload, scoping the state's name to this firing.
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    private int fire(TriggerPayload.StateEnded payload) {
        return triggerBus.fire(payload);
    }

    /**
     * Fires a resource-changed payload, scoping the resource's name to this firing.
     *
     * <p>The save/restore lives here rather than in every caller: a nested firing may move another
     * resource, and the rules of the outer firing must still see the outer resource's name.
     *
     * @param payload the firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.ResourceChanged payload) {
        return triggerBus.fire(payload);
    }

    public int fireTriggers(TriggerEvent event) {
        return fireTriggers(event, null, null, 0, 0);
    }

    /**
     * Fires a trigger event to the tables of our characters.
     *
     * <p>Every character on our side evaluates the event with itself as {@code self}. Two separate
     * facts are handed over because characters need both and they are not the same thing:
     * <ul>
     *  <li>{@code actor} - who <b>caused</b> the event. "After an ally attacks" is
     *  {@code actor != self}.</li>
     *  <li>{@code target} - what it <b>happened to</b>. "After I am hit" is {@code target == self};
     *       note the actor there is the attacker, not me.</li>
     * </ul>
     * Enemies have no tables, so nothing is fired for them.
     *
     * @param event    the event that happened
     * @param actor    who caused it, or {@code null}
     * @param target   the event's subject, or {@code null}
     * @param hitCount how many targets an attack connected with (0 when not applicable)
     * @param amount   the event's magnitude where it has one
     * @return how many rules fired in total
     */
    public int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount) {
        // Note: Both nulls spelled out: with a 6-arg (..., Damage) and a 6-arg (..., SkillCategory) overload, a single bare
        // null is ambiguous (measured: the compiler refused it).
        return fireTriggers(event, actor, target, hitCount, amount, null, null);
    }

    /**
     * The same, stating <b>which cast</b> produced the event - the form {@code ALLY_ATTACK} uses.
     *
     * <p>"each time one of our targets ... casts <b>2 basic attacks, Skills, ultimates</b>" (1215 Hanya (寒鸦)) has to tell the three slots apart, and an attack
     * event is the only place that count can be taken <b>once per cast</b>: counting on {@code DEALING_DAMAGE} would
     * count <i>hits</i> instead (a multi-hit skill would mark several times for one cast - a wrong number with no
     * symptom).
     *
     * @param fromCast the category of the cast that produced this attack ({@code null} = nothing can name one)
     */
    public int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                            SkillCategory fromCast) {
        return fireTriggers(event, actor, target, hitCount, amount, null, fromCast);
    }

    /**
     * The same, for the one event that carries a <b>pending damage instance</b>
     * ({@link TriggerEvent#DEALING_DAMAGE}), so a rule can still change it.
     *
     * <p><b>Private on purpose.</b> The pipeline's guardrail
     * ({@code DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint}) says the public API may contain
     * exactly one method taking a {@code Damage} - {@code applyDamage} - so that "the same hit assembled twice"
     * stays structurally impossible. This is the internal carrier for that context, not a second way in.
     *
     * @param damage   the instance being settled, or {@code null} for every other event (which is what the
     *                 five-argument overload passes)
     * @param fromCast the cast category that produced the event's instance, or {@code null} when nothing can name one
     */
    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage) {
        return fireTriggers(event, actor, target, hitCount, amount, damage, null);
    }

    /**
     * The full form: the same, plus <b>which cast</b> produced the event's instance.
     *
     * <p>Kept separate from {@code damage} because the two answer different questions and are populated by different
     * events: {@code damage} is the instance itself (only {@code DEALING_DAMAGE} has one, because only there can a rule
     * still change it), while {@code fromCast} is the {@link SkillCategory} of the cast that built it - which a kill and
     * a weakness break can also name, and which is what "when casting the Skill ... causing a weakness break" asks about.
     */
    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage, SkillCategory fromCast) {
        return fireTriggers(event, actor, target, hitCount, amount, damage, fromCast, 0);
    }

    /**
     * The same, naming the <b>data row</b> of the skill that produced the event.
     *
     * <p>Note: The row, not the slot: an enhanced attack is a row of its own, so "an enhanced basic attack hits ..." and "an enhanced basic attack consumes 2 layers" can be
     * told apart from the ordinary basic attack they replace. Events that no skill produced (a DOT tick, a break) leave it 0.
     */
    public int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                            SkillCategory fromCast, int skillId) {
        return fireTriggers(event, actor, target, hitCount, amount, null, fromCast, skillId, 0);
    }

    /**
     * The same, plus how many of the hit targets carry the attack's own element weakness (cone
     * 21040's "if no fewer than 2 of the attacked enemy targets have the corresponding attribute weakness").
     *
     * <p>A count over a predicate cannot be assembled from the parts: a multi-target attack fires the damage event once
     * per target, so "how many of them were weak" only exists where the whole set is in hand -- the caster's side.
     */
    public int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                            SkillCategory fromCast, int skillId, int weakHitCount) {
        return fireTriggers(event, actor, target, hitCount, amount, null, fromCast, skillId, weakHitCount);
    }

    /**
     * The same, plus the targets the whole attack hit (cone 21029).
     *
     * <p>Only the side holding the whole set can pass it: a per-hit event fires once per target, so "a random
     * one of the targets that WAS HIT" cannot be assembled from the parts (Note: the argument {@code weakHitCount} makes).
     */
    public int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                            SkillCategory fromCast, int skillId, int weakHitCount,
                            List<CanHit> attackHitTargets) {
        return fireTriggers(event, actor, target, hitCount, amount, null, fromCast, skillId, weakHitCount,
                attackHitTargets);
    }

    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage, SkillCategory fromCast, int skillId) {
        return fireTriggers(event, actor, target, hitCount, amount, damage, fromCast, skillId, 0);
    }

    /**
     * Note: Kept beside the ten-parameter terminal on purpose: the other overloads and their call sites still
     * forward nine arguments, and REPLACING a signature moves every one of those call sites' type matching with it
     * (measured: the compiler then reports type mismatches that look like bad arguments but are a missing overload).
     */
    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage, SkillCategory fromCast, int skillId, int weakHitCount) {
        return fireTriggers(event, actor, target, hitCount, amount, damage, fromCast, skillId, weakHitCount,
                List.of());
    }

    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage, SkillCategory fromCast, int skillId, int weakHitCount,
                             List<CanHit> attackHitTargets) {
        return triggerBus.fire(event, actor, target, hitCount, amount, damage, fromCast, skillId, weakHitCount,
                attackHitTargets);
    }

    /**
     * Holds a rule's effects until a wave brings enemies in; called by the interpreter in place of applying them.
     *
     * @param rule    the rule that triggered
     * @param context the context it triggered with (its owner and actor are part of what is being held)
     */
    public void deferUntilWave(TriggerTable.CompiledRule rule, TriggerTable.TriggerContext context) {
        triggerBus.deferUntilWave(rule, context);
    }

    /**
     * Fires an event whose subject is one of our characters (the one who lost HP, was healed, ...).
     *
     * <p>The side check is deliberate: an enemy taking damage should not make our characters react,
     * and enemy events are not ours to data-ise yet (the enemy side owns monsters).
     *
     * @param event  the event
     * @param actor  who caused it (may be {@code null})
     * @param target the subject; the call is skipped entirely when this is not one of ours
     * @return how many rules fired in total
     */
    public int fireTriggersForAlly(TriggerEvent event, CanHit actor, CanHit target, double amount) {
        if (target == null || target.getCamp() != Camp.PLAYER) {
            return 0;
        }
        return fireTriggers(event, actor, target, 0, amount);
    }

    /**
     * Fires a trigger event whose subject may be on either side (used where the subject itself is the
     * point, e.g. an enemy being broken).
     *
     * @param event   the event
     * @param actor   who caused it
     * @param subject what it happened to
     * @param amount  the event's magnitude, if any
     */
    public int fireTriggersWithSubject(TriggerEvent event, CanHit actor, CanHit subject, double amount) {
        return fireTriggers(event, actor, subject, 0, amount);
    }

    /**
     * The same, stating the <b>cast that produced the event's instance</b>.
     *
     * <p>Only some events can name one - a kill and a weakness break happen while an instance is being settled, and the
     * instance knows the {@link com.laosun.aluminium.enums.SkillCategory} of the cast that built it. That is what lets
     * a rule say "when casting the Skill on an enemy target causing a weakness break" instead of "somebody broke something".
     *
     * <p>Note: It carries the <b>category</b> rather than the instance: the settlement entry point is the only public API
     * that takes a {@code Damage} ({@code DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint}), and an event
     * does not need the instance's numbers - only which slot produced it.
     *
     * @param event    the event
     * @param actor    who caused it
     * @param subject  what it happened to
     * @param amount   the event's magnitude, if any
     * @param fromCast the cast category that produced it, or {@code null}
     */
    private int fireTriggersWithSubject(TriggerEvent event, CanHit actor, CanHit subject, double amount,
                                        SkillCategory fromCast) {
        return fireTriggers(event, actor, subject, 0, amount, null, fromCast);
    }

    /**
     * The same, stating the <b>instance</b> the event is about as well.
     *
     * <p>{@code DAMAGE_SETTLED} is a fact about one damage instance - its amount AND its identity - so the rules that
     * read it must be able to ask the instance's own questions: {@code damage_is_attack} is the guard that keeps a
     * rider from re-triggering itself ("additionally deals 2 instances of damage" is additional damage, which is not an attack), and it
     * reads exactly this field.
     */
    private int fireTriggersWithSubject(TriggerEvent event, CanHit actor, CanHit subject, double amount,
                                        Damage damage, SkillCategory fromCast) {
        return fireTriggers(event, actor, subject, 0, amount, damage, fromCast);
    }

    /**
     * Tells our side that one <b>attack</b> has finished - {@link com.laosun.aluminium.models.event.AttackEvent},
     * fired <b>once per attack</b>, after every instance of it has been settled.
     *
     * <p><b>Which attacks fire it.</b> A character's skill activation ({@code SkillExecutor}) and, since
     * memosprites, a summon's own attack ({@code EnemySkill.execute}) - both are attacks the engine drives from
     * beginning to end. Derived hits still never fire it: additional damage, true damage, DOT ticks and break
     * damage go straight through {@link #applyDamage} and are <em>part of</em> somebody else's attack. That
     * exclusion is not an oversight, it is what makes the notification safe to hand to arbitrary listeners:
     * listeners are allowed to answer an attack by dealing damage (that is how a third-party kit lands), so a
     * derived hit that fired the event again would recurse until {@link com.laosun.aluminium.models.battle.TriggerFiring#MAX_DEPTH} threw. A follow-up
     * attack therefore does <b>not</b> consume an {@code "until": "next_attack"} buff: that accepted
     * under-consumption is recorded rather than hidden.
     *
     * <p><b>Why it is broadcast to {@link #allies} whatever the attacker's camp.</b> The listeners are buffs on
     * our characters, and every one of them answers with its own question ("is this <em>my</em> attack?" -
     * {@code AbstractBuff.afterAttack} compares {@code attacker == owner}). So an enemy's attack can be
     * delivered here without any of them acting on it, and a future listener that <em>wants</em> to react to
     * being attacked has somewhere to do it. Deciding "who cares" is the listener's job; deciding "an attack
     * happened" is this method's.
     *
     * @param attacker    who attacked (may be either camp, or a summon)
     * @param mainTarget  the target the attacker selected (with an AOE it may not be the first hit)
     * @param hitTargets  the targets it actually connected with, in hit order (deduped by the caller)
     * @param totalDamage the sum of the settled values of its instances
     */
    public void fireAfterAttack(CanHit attacker, CanHit mainTarget,
                                Collection<? extends CanHit> hitTargets, double totalDamage) {
        fireAfterAttack(attacker, mainTarget, hitTargets, totalDamage, 0);
    }

    /**
     * The same, naming the <b>SLOT of the skill that made the attack</b> so a rule can ask "after <em>that</em> attack".
     *
     * <p>Why it was missing: every other cast-level event ({@code SKILL_CAST} / {@code CAST_SETUP} / {@code DAMAGE_SETTLED})
     * carries the slot, so content gates on {@code from_skill_id} freely; {@code ATTACK_FINISHED} announced the attack but
     * nothing about <em>which</em> attack it was, and it is the only event carrying the settled total. Reader: 1408
     * Phainon (白厄)'s Eidolon 6 - 「<b>施放【支柱•死星天裁】的攻击后</b>，对生命值最高的敌方目标额外造成等同于本次攻击总伤害值
     * #1% 的真实伤害」 - where every other transformed attack must NOT pay the rider.
     *
     * <p>Note: {@code 0} is "unknown", not a slot: the four-argument overload above and {@code EnemySkill} (an enemy or a
     * memosprite skill is not addressed by a character slot) both pass it, and no rule can say {@code from_skill_id == 0}
     * by accident because a slot is never 0.
     *
     * @param skillSlot the data slot of the skill whose attack just finished, or 0 when the caller has none
     */
    public void fireAfterAttack(CanHit attacker, CanHit mainTarget,
                                Collection<? extends CanHit> hitTargets, double totalDamage, int skillSlot) {
        if (attacker == null || hitTargets == null || hitTargets.isEmpty()) {
            return;                                  // not a single hit landed to it does not count as an attack
        }
        // The attack is over, so the NEXT instance belongs to a new one. This is the boundary a rule-level
        // `once_per_attack` reads (see CanHit.isAttackLimitReady): every instance of ONE attack sees the same number.
        attackSequence.next();
        List<CanHit> targets = List.copyOf(hitTargets);
        // The attack is over and its hit set is frozen, so content may now ask "a random one of the targets
        // that WAS HIT" -- the fact only this side holds (cone 21029). Note: Same early return
        // as above: an attack that hit nothing does not announce itself.
        fire(TriggerPayload.Cast.of(TriggerEvent.ATTACK_FINISHED, attacker, mainTarget, targets.size(),
                totalDamage, null, skillSlot, 0, targets));
        for (CanHit ally : allies) {
            ally.afterAttack(this, attacker, mainTarget, targets, totalDamage);
        }
    }


    /**
     * Runs a <b>buff-path reaction</b> (today: a counter-attack) that must not nest.
     *
     * <p>Note: Why this exists and why it is not {@link com.laosun.aluminium.models.battle.TriggerFiring#MAX_DEPTH}: a counter is triggered by HP loss,
     * and a counter's own damage causes HP loss. Two units wearing a counter would therefore hit each
     * other forever. The trigger-table version of a counter is stopped loudly by
     * {@link #fireTriggers}'s depth guard, because it is fired from there - but a buff reacts through
     * {@code BuffManager.onHpLoss}, which is <b>not</b> on that path, so it needs its own guard.
     *
     * <p><b>Verified load-bearing</b> by `BossMechanicTest.aCounterAnsweringACounterIsRefused`, which
     * watches the wearer's victim: removing the check lets the answering counter land and turns it red.
     * (An earlier test watched the other side and could not tell the difference - see that test's Javadoc.)
     *
     * <p>Per-battle state, not a static: two battles in the same test must not share a depth counter.
     * The reaction is <b>skipped</b> rather than reported when it would nest, because "the counter's own
     * damage does not itself trigger a counter" is the intended rule, not an error.
     *
     * @param reaction the reaction to run; must not be {@code null}
     * @return {@code true} when it ran, {@code false} when it was refused because a reaction was already
     * in progress
     */
    public boolean runCounter(Runnable reaction) {
        return counterNest.run(reaction);
    }

    /**
     * Additional damage: a panel-type base (ATK / max HP  x  multiplier) that goes through the full damage
     * zones (it takes DMG boost/defence/resistance/vulnerability).
     *
     * <p>The official definition: "makes the victim take 1 extra instance of damage; this damage does not count
     * as dealing 1 attack" -- so {@code notCountsAsAttack()} is set (the victim gains no energy, no
     * toughness is reduced, no attack-level event is triggered).
     * But it is attributed to the attacker, so on a kill the attacker still settles kill energy gain
     * (see {@code DamageService#grantKillEnergy}).
     *
     * @param base the already-computed base value (e.g. Robin (知更鸟) 120% ATK / Tribbie (缇宝) 12% max HP)
     * @return the settled value of this instance (0 = no damage dealt)
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        return applyAdditionalDamage(attacker, target, element, base, null, null);
    }

    /**
     * The same, for an instance whose crit is <b>stated rather than rolled</b> ("this damage's crit rate is fixed at 100%, and its crit damage is fixed at 150%").
     *
     * <p>Note: The numbers ride into the {@link Damage} here instead of being applied by the caller: this is the engine's
     * one additional-damage entry point (`DamagePipelineTest` pins the single-settlement invariant), so an extra
     * overload is the honest place for them - a second public way to build and settle an instance is exactly what
     * that invariant exists to prevent.
     *
     * @param fixedCritRate   {@code 1.0} for "always crits, no roll" ({@code null} = roll normally)
     * @param fixedCritDamage the crit damage to use ({@code 1.5} = 150%; {@code null} with a rate is refused at load)
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage) {
        return applyAdditionalDamage(attacker, target, element, base, fixedCritRate, fixedCritDamage, null);
    }

    /**
     * The same, with the instance's DAMAGE TYPE stated (1505 Evanescia (绯英)'s three elation riders, whose
     * text says "dealing ... physical-attribute <b>elation damage</b>"). A rule-driven rider could only ever be {@code ADDITIONAL} before,
     * so the one thing its sentence names -- the damage type -- had no spelling. {@code null} keeps the old meaning, which is why
     * the six-argument overload above still answers exactly as it did.
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage, DamageType type) {
        return applyAdditionalDamage(attacker, target, element, base, fixedCritRate, fixedCritDamage, type, null);
    }

    /**
     * The same, with the instance's CAST CATEGORY stated (1415's ode of passage, whose clause is about a "follow-up attack"). Note: The
     * body stays here and the six/seven-argument forms delegate to it -- a second way to build and settle an instance is exactly what the single-settlement
     * invariant exists to prevent, as the note above says.
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage, DamageType type,
                                        com.laosun.aluminium.enums.SkillCategory castCategory) {
        Damage extra = new Damage(attacker, target, element, type == null ? DamageType.ADDITIONAL : type, base, castCategory);
        if (fixedCritRate != null) {
            // Note: A rate of 0.0 means "never crits" and must not REPORT a crit either: the flag is what
            // CRIT_DEALT and `isCrit` read, so `fixedCrit(true, 0)` would multiply by 1.0 (numerically the same
            // damage) while telling every listener that a crit happened -- a wrong fact with no symptom.
            // Note: Measured the same day: this flag does NOT move the number (a rider at `crit_rate: 0.0` settled the
            // same 520.45168 either way), so the choice here is about the REPORT, not about the damage.
            extra.fixedCrit(fixedCritRate != 0.0, fixedCritDamage);
        }
        // KILL_ONLY: additional damage is extra damage derived from some attack, so the victim gains no energy; a kill is still credited to the attacker
        double settled = applyDamage(target, extra.notCountsAsAttack(), com.laosun.aluminium.models.battle.DamageService.EnergyGrant.KILL_ONLY);
        // This is the engine's one and only notion of a follow-up attack, so the
        // data-facing FOLLOW_UP event is emitted here rather than from a second attack path.
        // Gated on the instance having really connected: an additional-damage sweep swallowed by
        // invulnerability did not land, and "used a Follow-Up ATK" must not be credited for it.
        //
        // Emitted unconditionally, not gated on the instance having dealt damage: the texts that
        // subscribe read "when the wearer uses a Follow-Up ATK", which is the attack being *used*, and
        // an instance absorbed entirely by a shield or an invulnerable target was still used. An
        // earlier version gated this on `settled > 0`; it was dropped because the semantic was
        // questionable and, more decisively, `settled == 0` could not be produced reliably in a test,
        // so the branch would have shipped unverified.
        fire(TriggerPayload.Subject.of(TriggerEvent.FOLLOW_UP, attacker, target, settled, null));
        return settled;
    }

    /**
     * True damage: a fixed amount, or a derived value such as "this attack's total damage  x  %" -- it skips
     * every damage zone and does not count as an attack.
     *
     * <p>{@code notCountsAsAttack()} is likewise set: the victim gains no energy and no toughness is reduced;
     * but it is attributed to the attacker, so on a kill the attacker still settles kill energy gain
     * (see {@code DamageService#grantKillEnergy}).
     *
     * @param base the true damage amount (not affected by defence/resistance/DMG boost/crit/vulnerability)
     * @return the settled value of this instance (0 = no damage dealt)
     */
    public double applyTrueDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage trueDamage = new Damage(attacker, target, element, DamageType.TRUE, base);
        // KILL_ONLY: true damage is likewise derived damage, so the victim gains no energy; a kill is still credited to the attacker
        return applyDamage(target, trueDamage.trueDamage().notCountsAsAttack(), com.laosun.aluminium.models.battle.DamageService.EnergyGrant.KILL_ONLY);
    }

    /**
     * The enemy camp's units that may be selected as attack targets (= alive), in battlefield order.
     *
     * <p>Single source of truth for "who can be hit": {@link SkillExecutor} uses it today,
     * the target selector and wave handling must use the same judgement so
     * that no caller ever picks a corpse (that is where corpse-hitting comes from).
     *
     * <p>Returns {@code CanHit}, not {@code Enemy}: an enemy-side summon is a legitimate
     * target, and narrowing here would have made the roster widening pointless - the target list is
     * where the widening has to be visible.
     *
     * @return a fresh list of the camp's alive units
     */
    public List<CanHit> targetableEnemies() {
        return battlefield.targetableEnemies();
    }


    /**
     * Forgets the previous wave's monsters. Called by {@code WaveManager.nextWave} BEFORE the new spawn, because the order measured there
     * is `waveIndex++` -> `spawnWave` -> `beginWave` -> `WAVE_START`: clearing any later would erase the wave that just arrived.
     */
    public void forgetWaveMonsters() {
        waveState.forgetMonsters();
    }

    /**
     * Records an enemy that has just entered with a wave ({@code WaveManager.spawnWave}).
     *
     * @param enemy the newcomer
     */
    public void noteWaveMonster(CanHit enemy) {
        waveState.noteMonster(enemy);
    }

    /**
     * The enemies that entered with the current wave.
     *
     * @return the wave's monsters
     */
    public List<CanHit> waveMonsters() {
        return waveState.monsters();
    }

    /**
     * The allies a lethal blow has landed on since this action began, in the order it happened.
     *
     * @return the marks
     */
    public List<CanHit> lethallyHitThisAction() {
        return damageMarks.lethalThisAction();
    }

    /**
     * The allies who <b>lost HP</b> since this action began, in the order it happened.
     *
     * @return the marks
     */
    public List<CanHit> lostHpThisAction() {
        return damageMarks.lostHpThisAction();
    }

    /**
     * Queues a combatant to join its camp and enter the action bar at the next settle.
     *
     * <p><b>Why this is the entry point.</b> A unit that joins after construction used to be put in
     * place by writing to {@code allies} / {@code enemies} and to the pending-entry list separately.
     * Those are two facts ("on the roster", "in the action bar") and a caller could do one without the
     * other, producing a unit that is targetable but never acts -- or acts but is not on a roster.
     * This method does both, once, and is idempotent for a unit already present.
     *
     * <p>It does not announce {@code SUMMONED}: a wave arriving is not a summon. The summon methods
     * record the newcomer in the {@code SummonedBuffer} themselves, which is where that event comes from.
     *
     * @param unit the combatant to add; must be alive and on a camp that has a roster
     * @throws IllegalArgumentException when {@code unit} is {@code null}, dead, or neutral
     */
    public void addCombatant(CanHit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("a battle cannot admit a null combatant");
        }
        if (unit.isDeath()) {
            throw new IllegalArgumentException(
                    "a dead combatant cannot join (" + unit.getName() + "): it would be removed by the "
                            + "next removeDeadCombatants, so the call could never do anything");
        }
        if (unit.getCamp() != Camp.PLAYER && unit.getCamp() != Camp.ENEMY) {
            throw new IllegalArgumentException(
                    "a combatant must join a camp that has a roster (" + unit.getName() + " is "
                            + unit.getCamp() + "); Camp.NEUTRAL has none, and picking one would silently "
                            + "decide who it fights for");
        }
        List<CanHit> camp = unit.getCamp() == Camp.PLAYER ? allyRoster : enemyRoster;
        if (!camp.contains(unit)) {
            camp.add(unit);
        }
        if (unit instanceof Character character && !characterRoster.contains(character)) {
            characterRoster.add(character);
        }
        pendingCombatants.add(unit);
        // The constructor wires the speed listener for the opening roster only; a unit admitted later
        // needs it too, otherwise a speed buff on it would never reorder the action bar.
        unit.setSpeedChangeListener(this::onSpeedChanged);
        unit.getBuffManager().setBattle(this);
    }

    private void processAddRequests() {
        pendingCombatants.settle();
    }


    private void removeDeadCombatants() {
        deathService.removeDeadCombatants();
    }

    /**
     * Brings a summon onto the field.
     *
     * <p>The summon comes from real monster data ({@code SummonFactory}, keyed by {@code monster_config.json}
     * - so {@code monster_config.json}'s {@code summon_id} roster on the master tells you the candidates and
     * this call picks one). It joins the master's camp and enters the action bar through the same door a
     * wave does, so it acts from the <b>current</b> action value rather than restarting a round.
     *
     * <p><b>What is deliberately explicit.</b> The level group is a parameter, not something inferred: a
     * monster's own {@code hard_level_group} is almost always 1 and the <em>stage</em> is what decides
     * difficulty, so there is nothing here to guess it from. Guessing is how the project once got a
     * silently-empty skill point policy - a wrong answer with nothing to see is
     * worse than a required argument.
     *
     * <p><b>Both camps can summon</b> (the friendly half of the camp split). The summon joins the
     * master's own camp: an enemy's minion goes into {@link #enemies}, ours into {@link #allies}, so our side
     * is targetable by the enemy as a whole and our own summon is <b>not</b> a legal target for our attacks
     * (our attacks look at {@code enemies}). The camp is taken from the master rather than passed in - a
     * summon that fights for the other side than its summoner is not a thing the text ever asks for.
     *
     * <p>Note: {@code Camp.NEUTRAL} is refused rather than guessed at: it has no roster of its own, and picking
     * one for it would silently make a neutral unit either our ally or our enemy.
     *
     * @param master         the unit calling the summon; supplies the camp, and the level
     * @param summonId       the summon's own monster id (a key of {@code monster_config.json})
     * @param hardLevelGroup the stage's hard level group
     * @return the summon, already in its camp's roster and queued to enter the action bar
     * @throws IllegalArgumentException if the master is null, already down, or on a camp with no roster, or
     *                                  if the summon id / level group is unknown
     */
    public Summon summon(CanHit master, int summonId, int hardLevelGroup) {
        return summons.summon(master, summonId, hardLevelGroup);
    }

    /**
     * Brings a character's <b>memosprite</b> onto the field, with a panel derived from that character.
     *
     * <p>This is the character-side sibling of {@link #summon(CanHit, int, int)}: the monster path takes an
     * id from a roster, this one takes nothing but the summoner and reads
     * {@code resources/game/memosprites/<cid>.json} for how the panel is inherited. It always joins
     * {@link #allies}, because a memosprite fights for whoever summoned it and its summoner is one of ours.
     *
     * <p><b>Idempotent per summoner.</b> If that character already has a living memosprite on the field,
     * nothing happens and the existing one is returned. The documents actually say "if it is already present,
     * restore it to full HP" (Aglaea (阿格莱雅)'s "if the 衣匠 is already present, restore its health to the maximum"), and that refresh is <b>not</b>
     * modelled here: doing nothing is at least never a wrong <em>state</em>, whereas a second copy of the same
     * memosprite would be one - two units, one of which the player cannot see. The refresh is registered as
     * the next step rather than approximated.
     *
     * @param master the summoning character
     * @return the memosprite (newly placed, or the one already standing), already in the camp roster and
     * queued to enter the action bar
     * @throws IllegalArgumentException if the master is null, already down, or has no memosprite spec
     */
    public Summon summonMemosprite(Character master) {
        return summons.memosprite(master);
    }

    public Summon summonServant(Character master) {
        return summons.servant(master);
    }

    /**
     * The master's living memosprite on the field, or {@code null}.
     *
     * <p>Found by walking {@link #allies} for a summon whose master is this character - the master link is
     * the only place that relation lives, so this is the one query that can answer it. Dead memsprites stay
     * in the roster like every other corpse, hence the {@code isDeath()} filter.
     */
    public Summon memospriteOf(CanHit master) {
        return summons.memospriteOf(master);
    }

    /**
     * Every living summon this unit owns, looking in <b>its own camp</b>, in roster order.
     *
     * <p>The one place the predicate "is this unit mine" is written: {@link #summonOf} takes the first,
     * {@link #summonCountOf} counts them, and the {@code actor == summon} / {@code target == summon} conditions
     * ask whether an event's subject is among them. Three loops with the same test is how three answers to
     * "which units are mine" start to differ - and a unit with several summons (Robin - Summeretto (知更鸟-晴歌)'s Sky Musician (晴空乐手) is a
     * trio) is exactly where a stop-at-the-first version would silently cap the answer at one.
     *
     * @param master the summoner (may be {@code null}, which owns nothing)
     * @return the living summons whose master is {@code master} (a fresh list, never {@code null})
     */
    public List<Summon> summonsOf(CanHit master) {
        return summons.summonsOf(master);
    }

    /**
     * The first living summon this unit owns, looking in <b>its own camp</b>, or {@code null}.
     *
     * <p>What {@code TriggerInterpreter}'s {@code "summon"} target selector resolves to. The difference from
     * {@link #memospriteOf(CanHit)} is the point: that one is about memsprites on <em>our</em> side (what
     * {@link #summonMemosprite(Character)} manages), while this one answers "which unit did <b>this</b> unit
     * summon" for either camp - an enemy boss's minion is a summon too, and "the wearer and their memosprite" and a monster's
     * own text are the same shape.
     *
     * @param master the summoner (may be {@code null}, which owns nothing)
     * @return the first living summon whose master is {@code master}, in roster order, or {@code null}
     */
    public Summon summonOf(CanHit master) {
        return summons.summonOf(master);
    }

    /**
     * How many living summons this unit owns - what {@code self_summon_count} evaluates.
     *
     * <p>Counted over the whole roster rather than stopping at the first match, so a unit with several
     * summons is counted correctly: nothing has more than one yet, but Robin - Summeretto (知更鸟-晴歌)'s Sky Musician (晴空乐手) is a trio, and a
     * query that stopped looking would silently cap it at one.
     *
     * @param master the summoner (may be {@code null}, which owns none)
     * @return the number of living summons whose master is {@code master}
     */
    public int summonCountOf(CanHit master) {
        return summons.summonCountOf(master);
    }

    public List<Signal> getQueueSnapshot() {
        return queue.snapshot();
    }

    /**
     * Records that this unit used its technique before the battle. Call before {@link #startBattle()}.
     */
    public void markTechniqueUsed(CanHit unit) {
        preBattle.markTechniqueUsed(unit);
    }

    /**
     * Declares that this unit used its <b>map basic attack</b> on an enemy before the battle. Call before
     * {@link #startBattle()}.
     *
     * <p><b>Why the battle has to be told.</b> The hit happens on the overworld, which this engine does not model -
     * the same reason {@link #markTechniqueUsed} exists. The battle's half is the sentence's in-battle consequence,
     * 「攻击敌人，进入战斗后削弱敌方目标对应属性韧性」: the struck enemy opens the battle with that much of its
     * <b>matching</b> toughness already gone. The amount and the element are read from the character's slot-6 skill
     * ({@link #applyDeclaredMapAttacks}), so they stay data rather than constants here.
     *
     * <p>Note: Calling it twice is two hits - the caller said so, and nothing here imposes a cap.
     *
     * @param attacker the character whose map attack landed
     * @param target   the enemy it hit
     */
    public void declareMapAttack(CanHit attacker, CanHit target) {
        preBattle.declareMapAttack(attacker, target);
    }

    /**
     * Records the unit that 【美学底本】 sat on when the battle began - the technique's own half.
     *
     * <p>Reader: 1503 Pearl (真珠)'s technique - 「使用秘技后获得【美学底本】，切换行动角色时【美学底本】转移至当前行动角色。下一次
     * 战斗开始时，真珠获得 20 点【好活当赏】，并【深度学习】持有【美学底本】的角色」.
     *
     * <p>⚠ <b>Where the transfer lives.</b> 「切换行动角色」 is a MAP action, and the map is a pre-battle CONFIGURATION
     * layer: the player switches characters, the mark follows, and by the time combat starts exactly one ally holds it.
     * So the battle does not need to observe the switching at all - it needs to be TOLD who holds the mark, which is
     * what this method is for. A caller that models the map (or a judge standing in for one) calls it before
     * {@link #startBattle()}; the engine's half is to apply the mark and let the rules read it.
     *
     * <p>There is always an archetype in the game ("the character who used the technique holds it, and it moves when the
     * active character changes"), so "nobody" is spelled by not calling this at all - not by a flag. Whatever is passed
     * is the unit holding the mark, and {@code null} leaves the battle with none.
     */
    public void markArchetypeHolder(CanHit unit) {
        preBattle.markArchetypeHolder(unit);
    }

    /**
     * The unit 【美学底本】 sat on when the battle began, or {@code null} when the caller named none.
     */
    public CanHit archetypeHolder() {
        return preBattle.archetypeHolder();
    }

    /**
     * Gives every unit whose technique was used a permanent "秘技" state, before anything reads BATTLE_START.
     */
    private void applyTechniqueStates() {
        for (CanHit unit : preBattle.techniqueUsers()) {
            if (unit == null || unit.isDeath()) {
                continue;
            }
            unit.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("秘技", 1, true));
        }
        // The technique's own mark, on the unit the caller named - 「使用秘技后获得【美学底本】」. Applied HERE rather than
        // by a rule because it exists BEFORE the battle does; the rules then read it (`self has_state 美学底本`) to find
        // who to apply 深度学习 to.
        // Note: No further condition. The MAP decides who holds the mark and the caller states it, so a named unit IS the
        // answer - "nobody" is spelled by not naming one. The first version additionally required that someone had used a
        // technique, which was my own invention: the map's answer already accounts for that (a holder is named only when
        // Pearl's technique put the mark somewhere), and the judge's "no archetype named" case is what showed the extra
        // condition was doing nothing but adding a way to disagree with the caller.
        CanHit archetype = preBattle.archetypeHolder();
        if (archetype != null && !archetype.isDeath()) {
            archetype.getBuffManager()
                    .addBuff(new com.laosun.aluminium.models.buff.StateBuff("美学底本", 1, true));
        }
    }

}



