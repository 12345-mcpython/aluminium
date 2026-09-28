package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.models.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;


public class Battle {
    /**
     * Battle state machine (P7-3).
     *
     * <pre>
     *   NOT_STARTED ──startBattle()──▶ RUNNING ──one side wiped out──▶ WIN / LOSE
     *                                   ▲                      │
     *                                   └──── (no rollback) ───┘
     * </pre>
     *
     * <p>{@code WIN} / {@code LOSE} are **terminal states**: {@link #stepForward()} no longer advances the action bar.
     */
    public enum Status {
        NOT_STARTED, RUNNING, WIN, LOSE
    }

    public Queue queue;

    public List<Character> characters;

    /**
     * The <b>player camp's roster</b> — every combatant fighting for us, in battlefield order: our
     * characters plus any friendly {@link Summon} (a memosprite, 景元's 【神君】, …).
     *
     * <p><b>Why this exists next to {@link #characters} rather than replacing it</b> (the friendly half of
     * L-8). The enemy side already has this split — {@link #enemies} is the camp and {@link #enemyUnits()}
     * is the monsters in it — so that things which genuinely only work on monsters say so. Our side had no
     * such split, and the consequence was harder than a missing convenience: <b>a player-side summon could
     * not be placed anywhere at all</b>, because the only roster we had was typed {@code List<Character>}.
     *
     * <p>The tempting fix was to widen {@code characters} and add a {@code characterUnits()} view (that is
     * the symmetric option, and it would have made the compiler find every stale call site). It was not
     * taken because {@code characters} is read in <b>179 places</b> — nearly all of them meaning "our
     * characters" (their skills, energy, relic rules) rather than "our camp" — and a rename of that size
     * costs a great deal of churn for no behaviour. So the camp got its own name instead, and the sites
     * that mean <em>the camp</em> were moved onto it one by one.
     *
     * <p>⚠ <b>Which question a call site is asking, and the cost of getting it wrong.</b> "Who is on our
     * side" must read {@code allies}; "which of them are characters" must read {@code characters}. A camp
     * site left on {@code characters} does not fail loudly — it silently ignores friendly summons, so an
     * enemy AOE would miss one and a party-wide buff would skip it. The compiler cannot catch that here, so
     * it is pinned by {@code PlayerSideSummonTest}, which exercises each camp-level site with a summon on the
     * field. The sites that deliberately stay on {@code characters} are the ones that need something only a
     * {@code Character} has: {@code attachBattleSkills} (map skills), {@code fireTriggers} (trigger tables)
     * and the two skill-point broadcasts.
     *
     * <p>It starts as a copy of the constructor's character list, so it holds <b>the same instances</b> —
     * this is a second view of one roster, not a second roster. Dead members stay in it, like every other
     * roster in the engine ({@code enemies} / {@code characters}): "is it alive" is asked with
     * {@code isDeath()}.
     */
    public List<CanHit> allies;

    /**
     * The <b>enemy camp's roster</b> — every combatant fighting against us, in battlefield order.
     *
     * <p>⚠ <b>Deliberately {@code CanHit}, not {@code Enemy}</b> (L-8). It used to be
     * {@code List<Enemy>}, which made an enemy-side {@link com.laosun.aluminium.models.Summon}
     * <b>structurally impossible to place</b>: the action bar accepts any {@code CanHit}
     * ({@code Queue.addCombatants} takes {@code List<? extends CanHit>}), and camp-agnostic code
     * ({@code TargetSelector}, {@code opposingCamp}) was already written against {@code CanHit},
     * but the roster it had to be registered in only admitted monsters. Widening it here is what
     * makes P9-4 possible at all.
     *
     * <p>Code that needs <b>monster</b> mechanics (toughness, weakness, per-element debuff
     * resistance, phase tables) must ask for them explicitly through {@link #enemyUnits()} rather
     * than assuming every entry is one — that is the whole point of the split, and it is why a
     * summon is not silently skipped anywhere: the places that genuinely only work on monsters now
     * say so.
     *
     * <p>Mutable on purpose: {@code WaveManager} appends each new wave's monsters to it.
     */
    public List<CanHit> enemies;

    /**
     * The {@link Enemy} entries of {@link #enemies}, in the same order — the monsters, without any
     * summon that may be sharing the camp.
     *
     * <p>Exists so that "the enemy side" and "the monsters on the enemy side" are two named things
     * instead of one type that has to mean both. A fresh list: the caller may remove from it freely.
     *
     * @return a fresh list of the enemy camp's monsters (including dead ones — filter with
     * {@code !isDeath()} if that is what you mean)
     */
    public List<Enemy> enemyUnits() {
        List<Enemy> monsters = new ArrayList<>();
        for (CanHit unit : enemies) {
            if (unit instanceof Enemy enemy) {
                monsters.add(enemy);
            }
        }
        return monsters;
    }

    public Signal currentMove;

    /**
     * The current battle status (P7-3). It starts as {@link Status#NOT_STARTED}, and {@link #startBattle()}
     * turns it into {@link Status#RUNNING}.
     */
    @Getter
    private Status status = Status.NOT_STARTED;

    /**
     * Wave management (P7-4); {@code null} for a non-wave battle.
     *
     * <p>Its only reason to exist is to let {@link #checkResult()} know whether "the enemy team is empty"
     * means **won** or **this wave has not entered yet**.
     */
    @Getter
    @Setter
    private WaveManager waveManager;

    public ArrayList<CanHit> addRequestItems = new ArrayList<>();

    /**
     * The countdown units this battle has placed on the action order (M-49).
     *
     * <p>Kept in their own list, and ⚠ <b>not</b> in {@link #allies}: a countdown belongs to our camp (our rules react
     * to its turn) but it is not a party member — were it in the roster, 「我方全体」 would buff it, `lowest_hp_ally`
     * could pick it, and it would be a legal target for every ally-directed effect. See {@link Countdown}.
     */
    private final List<Countdown> countdowns = new ArrayList<>();

    /**
     * Summons placed since the last settle, so {@link TriggerEvent#SUMMONED} can be fired once they are in the
     * action bar (see {@link #fireSummoned}). Separate from {@link #addRequestItems} on purpose: that queue is
     * shared with wave entries, and a wave arriving is not a summon.
     */
    private final List<CanHit> justSummoned = new ArrayList<>();

    /**
     * The cast being resolved right now, or {@code null} outside a cast (P11-1, M-40) — what
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
     * {@link TriggerEvent#CAST_SETUP}): the alternative — a rule that describes the swing after the fact — cannot
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
         * The skill slot being cast (1 = 基本攻击, 2 = 战技, 3 = 终结技, …, the numbering {@code skills.json}
         * itself uses — see {@link Skill#getSkillSlot()}).
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
         * Whether this cast's own damage has been delegated — i.e. {@code SkillExecutor} must not expand it and
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
     * How many targets the <b>cast being delivered right now</b> has actually applied each named state to
     * (「终结技每冻结1个目标，为三月七恢复6点能量」).
     *
     * <p><b>Why the engine has to count it.</b> The number is not the number of targets <i>aimed at</i> — that one is
     * the event's hit count, which {@code per_target} already multiplies by. It is the number the <b>roll let
     * through</b>, and only {@code Battle.tryApplyDebuff} knows it: with a 50% base chance against three enemies the
     * answer can be 0, 1, 2 or 3, and a rule that guessed "3" would pay three times what the text says.
     *
     * <p><b>Why it lives on the battle and not on the cast token.</b> The cast window is <i>closed</i> before the cast
     * events are delivered ({@code SkillExecutor.execute} calls {@code endCast} in a {@code finally}, and only then
     * broadcasts {@code ULT_CAST}) — which is precisely when a rule gets to read this. So the record outlives the
     * token and is cleared at the end of the delivery instead.
     *
     * <p>⚠ <b>Cleared twice on purpose</b>: at {@link #beginCast} (so a new cast never sees the previous one's
     * numbers) and after the cast's own events have been delivered ({@link #endCastOutcome}). A rule firing outside
     * that window reads 0 — and content cannot get there by accident: the loader refuses
     * {@code "scale": "cast_applied:…"} on any event that is not a cast (see {@code TriggerInterpreter}).
     */
    private final Map<String, Integer> castApplied = new HashMap<>();

    /**
     * Records one <b>landed</b> application of a named state during the current cast.
     *
     * <p>Called by the ops that roll for a state ({@code APPLY_CONTROL}, {@code APPLY_DOT}) and only when the roll
     * passed — a resisted application is not an application, which is the whole reason this counter exists.
     *
     * @param stateName the state's name as the documents spell it (冻结 / 灼烧 / …)
     */
    public void recordCastApplied(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return;
        }
        castApplied.merge(stateName, 1, Integer::sum);
    }

    /**
     * How many targets the current cast has applied {@code stateName} to ({@code 0} outside a cast's own events).
     *
     * @param stateName the state's name (冻结 / 灼烧 / …)
     * @return the landed count for this cast
     */
    public int castAppliedCount(String stateName) {
        return stateName == null ? 0 : castApplied.getOrDefault(stateName, 0);
    }

    /**
     * Forgets the current cast's landed applications, once its events have been delivered.
     *
     * <p>Called from {@code SkillExecutor.execute}'s {@code finally} so that nothing firing later — an ally's attack,
     * a hit taken next turn — can read a stale count.
     */
    public void endCastOutcome() {
        castApplied.clear();
    }

    /**
     * Starts a cast and returns its token; {@link #endCast(PendingCast)} must be called in a {@code finally}.
     *
     * @param skill the skill being cast
     * @param caster who casts it
     * @return the token identifying this cast
     */
    public PendingCast beginCast(Skill skill, CanHit caster) {
        // A new cast's outcome starts empty: see `castApplied`.
        castApplied.clear();
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
     * Skill points (战技点) policy (P8-4): **one pool shared by the whole team**, not one track per character.
     *
     * <p>{@code Battle} itself **does not know the skill point rules** -- it only holds a
     * {@link SkillPointPolicy} and asks it once when "deciding to act" (see {@link #useSkill}).
     * Why split it this way: skill point rules keep growing character-level corrections (Bronya (布洛妮娅)
     * "skill has a 50% chance of +1", Sushang (素裳) "skill on a weakness-broken target +1",
     * Sparkle (花火) "max +2", ...). If all of those branches were added here,
     * {@code Battle} would fill up with "because of some character" conditionals, violating the P8-0 three-way split.
     * See <b>F-8</b> in §F of {@code DOC_VS_CODE.md}.
     *
     * <p>The default is {@link StandardSkillPointPolicy} (start 3 / max 5 / our basic attack +1 / skill -1 /
     * everything else neutral). To swap in another rule set for the team (e.g. Sparkle (花火) raising the max),
     * replace this field -- it is a controllable injection point and a **stable API** to callers
     * (the three methods read/add/spend do not change).
     */
    public SkillPointPolicy skillPointPolicy = new StandardSkillPointPolicy();

    /**
     * Current skill points (P8-4). Equivalent to {@code skillPointPolicy.getValue()}.
     */
    public int getSkillPoints() {
        return skillPointPolicy.getValue();
    }

    /**
     * The regular skill point cap (P8-4). Equivalent to {@code skillPointPolicy.getMax()}.
     */
    public int getSkillPointMax() {
        return skillPointPolicy.getMax();
    }

    /**
     * Whether there are enough skill points to cast one skill (P8-4).
     */
    public boolean hasSkillPoint() {
        return skillPointPolicy.canAfford();
    }

    /**
     * Directly restore skill points (P8-4), capped at the regular maximum.
     *
     * <p>For explicit sources other than "basic attack +1": techniques, relics (the 4-piece 过客 set),
     * character mechanics.
     *
     * <p>This is also the **single place** the {@code SKILL_POINT_GAINED} trigger fires from,
     * whichever way the points were added: the standard policy reports its own in-cast gains to the
     * listener, and this method reports through the same listener for direct calls. Firing in both
     * places independently would make a single gain trigger twice.
     *
     * @param n the number of points to add; does nothing when {@code <= 0}
     */
    public void gainSkillPoint(int n) {
        int before = skillPointPolicy.getValue();
        skillPointPolicy.gain(n);
        int gained = skillPointPolicy.getValue() - before;
        if (gained > 0) {
            fireTriggers(TriggerEvent.SKILL_POINT_GAINED, null, null, 0, gained);
        }
    }

    /**
     * Spend 1 skill point (P8-4).
     *
     * @return whether the spend succeeded; {@code false} means the count was already 0 (the caller should block this action)
     */
    public boolean spendSkillPoint() {
        return skillPointPolicy.spend();
    }

    /**
     * Settle skill points for a skill (P8-4). **The internal funnel point**, shared by {@link #useSkill} and
     * the heal/shield branches in the demos -- the latter call {@code Battle.heal/grantShield} directly,
     * bypassing {@link #useSkill}, so this has to be called explicitly, otherwise "a healing skill costs no points".
     *
     * <p>All the rules live in the policy (including the camp check); this only forwards.
     *
     * @param skill the skill to settle
     * @param user  the actor -- **must be passed explicitly**. Earlier this guessed the actor from
     *              {@code currentMove}, and in scenarios with no actor (directly-called tests, the demo's
     *              healing branch) it guessed {@code null}, while {@code null != Camp.PLAYER} silently turned
     *              the policy into a no-op -- a wrong answer that reports no error. Passing it explicitly makes
     *              this kind of misuse surface as a compile error.
     * @return whether this action **may continue** (enough skill points); basic attack/ultimate/follow-up attack are always true
     */
    public boolean applySkillPointCost(Skill skill, CanHit user) {
        return skillPointPolicy.onSkillCast(user, skill);
    }

    public ArrayList<AdvanceRequest> advanceRequests = new ArrayList<>();

    private final ArrayList<SkillRequest> skillRequests = new ArrayList<>();

    /**
     * Injected random source (crit rolls / target selection); a fixed seed makes a
     * whole battle reproducible.
     */
    @Getter
    private final Random rng;

    public record AdvanceRequest(CanHit object, double rate) {
    }

    public record SkillRequest(Skill skill, CanHit object, List<? extends CanHit> target) {
    }

    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue) {
        this(characterQueue, enemyQueue, new Random());
    }

    /**
     * @param characterQueue our side (characters; a player-side summon is added to the action bar directly)
     * @param enemyQueue     the enemy camp — monsters, and any summon fighting alongside them. Taken as
     *                       {@code ? extends CanHit} so that a caller's {@code List<Enemy>} still fits
     *                       (L-8). Copied, so the battle owns its roster and a later
     *                       {@code WaveManager} append does not write through to the caller's list.
     */
    public Battle(List<Character> characterQueue, List<? extends CanHit> enemyQueue, Random rng) {
        characters = characterQueue;
        // The camp view of our side: the same instances, one more list. A friendly summon is appended here
        // (and only here) by summon(...), so `characters` stays exactly "our characters".
        allies = new ArrayList<>(characterQueue);
        enemies = new ArrayList<>(enemyQueue);
        this.rng = rng;
        queue = new Queue();
        queue.addCombatants(characterQueue);
        queue.addCombatants(enemyQueue);
        queue.initialize();
        // P7 fix E2: wire up "speed change → re-sort the action bar". Without this, a speed buff/debuff does
        // not take effect immediately (the speed cached in Signal is only refreshed at
        // initialize/setTopZero/resetSignal).
        for (Character c : characterQueue) {
            c.setSpeedChangeListener(this::onSpeedChanged);
        }
        for (CanHit e : enemyQueue) {
            e.setSpeedChangeListener(this::onSpeedChanged);
        }
        listenToSkillPointChanges();
    }

    /**
     * Wire the skill point policy's change reports into event broadcasting (P8-6).
     *
     * <p>The policy reports after it "really credited / really spent", and {@code Battle} only broadcasts --
     * that way {@code Battle} does not need to know the skill point rules (nor to **guess** what just happened
     * by "subtracting the before and after values", an inference that silently goes wrong at the cap).
     *
     * <p>Only {@link StandardSkillPointPolicy} is wired: other implementations that also want to emit events
     * can hook up {@code setListener} themselves at the assembly point -- the engine makes no special case for
     * any one implementation.
     */
    private void listenToSkillPointChanges() {
        if (skillPointPolicy instanceof StandardSkillPointPolicy standard) {
            standard.setListener(new StandardSkillPointPolicy.Listener() {
                @Override
                public void onGained(int amount) {
                    broadcastSkillPointGained(amount);
                }

                @Override
                public void onSpent(int amount) {
                    broadcastSkillPointSpent(amount);
                    fireTriggers(TriggerEvent.SKILL_POINT_SPENT, null, null, 0, amount);
                }
            });
        }
    }

    /**
     * A unit's speed changed → re-sort its action time from the "action progress already accumulated"
     * (P7 fix E2).
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

    // After releasing ultra skill must call processRequests()!
    // NO BEFAN YOY DID IT

    /**
     * Whether this unit can cast its ultimate right now (P3-4 follow-up): **reaching the "ult threshold" is
     * enough, it does not have to be filled to the maximum**.
     *
     * <p>The threshold comes from the skill data's {@code spNeed} (tbgd {@code AvatarSkillConfig.SPNeed},
     * see {@link SkillData#getSpNeed()}).
     * 5 of the 93 characters have a threshold **below** the maximum -- Yunli (云璃) 120/240,
     * Argenti (银枝) 90/180, 绯英 240/480, Feixiao (飞霄) 6/12, Cyrene (昔涟) 12/24. When the data is missing
     * it falls back to "fill {@code maxEnergy}" (the old behaviour).
     *
     * <p>After it is cast it is **zeroed** (see {@link #castUltra}): for the majority of characters whose
     * threshold == max this is equivalent to before; for the exceptions above it is equivalent to
     * "one cast consumes the threshold part". The game docs say
     * "Energy required to cast 120 (max 240)" -- "required" is a **gate**.
     *
     * <p>⚠ This is the only place on `Battle` that reads {@code spNeed}, so if the distinction between
     * "consume the threshold" and "zero out" is ever needed, changing this place and {@link #castUltra} is enough.
     */
    public boolean isUltraReady(CanHit user) {
        if (user == null) {
            return false;
        }
        // P8-8: the gate is the provider's to decide, not "energy is full". Characters who build a
        // stack resource instead of energy (Acheron 【残梦】/ Feixiao 【飞黄】/ Cyrene 【追忆】…) become
        // ready when their resource fills, and their energy stays at 0 by design -- so the old
        // "must have an energy bar" test would lock them out forever.
        //
        // `hasEnergyBar()` is therefore NOT checked here: the default implementation inside
        // EnergyProvider still applies exactly that rule (plus the threshold), so conventional
        // characters behave as before, while a stack provider may ignore energy entirely.
        return user.getEnergyProvider().canCastUltra(user, ultraEnergyCost(user));
    }

    /**
     * The energy this unit needs to cast its ultimate: prefers the skill data's {@code spNeed},
     * otherwise falls back to {@code maxEnergy}.
     */
    public double ultraEnergyCost(CanHit user) {
        if (user == null) {
            return Double.MAX_VALUE;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        Double spNeed = ultra == null || ultra.getData() == null ? null : ultra.getData().getSpNeed();
        if (spNeed != null && spNeed > 0) {
            return spNeed;
        }
        return user.getMaxEnergy();
    }

    public boolean castUltra(CanHit user, List<? extends CanHit> targets) {
        if (user == null || user.isDeath() || !isUltraReady(user)) {
            return false;                       // not enough accumulated, cannot cast (characters without an energy bar can never cast)
        }
        // P7-2: during an extra turn, inserting **someone else's** ultimate is forbidden.
        // Rule in HSR.md §3.1; without this block, an "extra turn" could be extended forever by ultimates.
        CanHit extraTurnActor = queue.getExtraTurnActor();
        if (extraTurnActor != null && extraTurnActor != user) {
            return false;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        if (ultra == null) {
            return false;
        }
        if (!requestSkill(ultra, user, targets)) {
            return false;
        }
        // H-5: **zero it first** (the order in ROADMAP P3-2), then let the ultimate body settle.
        // Reversing the order eats the energy the ultimate itself earns: the kill energy / break energy
        // inside processRequests() are both credited to damage.getAttacker() (= the one casting the ultimate),
        // so settling first and zeroing afterwards wipes those entries out.
        user.setCurrentEnergy(0);
        processRequests();                      // ultimate body settles: the Ultra slot gains no energy in onSkillCast, so no double credit
        EnergyGain ultraGain = user.getEnergyProvider().onUltCast(user, ultra);
        if (ultraGain != null) {
            applyEnergyGain(user, ultraGain);   // then the 5 points of its own (× energy gain rate)
        }
        return true;
    }

    public void startBattle() {
        status = Status.RUNNING;
        attachBattleSkills();
        for (Signal signal : queue.snapshot()) {
            signal.getCanHit().onBattleStart(this);
        }
        // P8-7: after the opening hooks, let every combatant's trigger table see BATTLE_START.
        // Deliberately after `onBattleStart` so an opening buff is already in place when a trigger
        // reads its own state (e.g. "restore 30 energy at the start of battle").
        fireTriggers(TriggerEvent.BATTLE_START);
        processRequests();
        checkResult();
    }

    /**
     * Attach the **map skills** at the start of a battle (P8-2): map basic attack (slot 6) and
     * technique (slot 7).
     *
     * <p>Why here and not in {@code CharacterFactory}: these two slots are **things on the map**,
     * not a character's permanent skills -- they only make sense at the moment of "entering battle".
     * In the data the map basic attack's attack type is {@code MazeNormal} and the technique's is {@code Maze},
     * while the in-battle basic attack is the {@code Normal} of slot 1; the two are not the same thing.
     *
     * <p>The technique's **effect** (e.g. Jing Yuan (景元) "at the start of the next battle 【神君】+3 hits")
     * has to wait for the P8-6 events plus the trigger table; this method only attaches the skill itself
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
                character.setSkill(type, new DefaultSkill(character.getCid(), slot, 1));
            }
        }
    }

    public void stepForward() {
        if (isOver()) {
            return;                                  // terminal state: the action bar is no longer advanced (P7-3)
        }
        queue.move();
        currentMove = queue.getCurrentActor();
    }

    /**
     * Whether the battle is already over (won or lost).
     */
    public boolean isOver() {
        return status == Status.WIN || status == Status.LOSE;
    }

    /**
     * Decide the outcome and set the status (P7-3). **Idempotent**: once terminal it does nothing
     * (terminal states never roll back).
     *
     * <p>The rules:
     * <ul>
     *   <li>A side being **wiped out** means that side loses -- this uses {@code allMatch(isDeath)}, so
     *       **an empty list also counts as wiped out** (an empty side has simply been cleared).</li>
     *   <li>At {@link Status#NOT_STARTED} nothing is decided: the battle has not started, so there is no
     *       outcome to speak of. So this judgement only takes effect after {@link #startBattle()}.</li>
     *   <li>Both sides wiped out at the same time → {@code LOSE} (loss is checked before win, and it will
     *       **not** keep judging from a terminal state).</li>
     * </ul>
     *
     * @return the current status after the judgement
     */
    public Status checkResult() {
        if (status != Status.RUNNING) {
            return status;
        }
        // P7-4: there are waves not yet entered → an empty enemy team only means "this wave has not entered", so no win.
        boolean pendingWaves = waveManager != null && waveManager.hasPendingWaves();
        // Both sides are judged by their CAMP, not by their "real units" list (L-8 on the enemy side, its
        // friendly half here). Today the two readings coincide for our side anyway: a summon perishes with
        // its master, so "every character is down" and "the whole camp is down" are the same state.
        if (allies.stream().allMatch(CanHit::isDeath)) {
            status = Status.LOSE;                    // our side being wiped out is a real loss, with or without pending waves
        } else if (enemies.stream().allMatch(CanHit::isDeath) && !pendingWaves) {
            status = Status.WIN;
        }
        return status;
    }

    /**
     * Give {@code actor} an **extra turn** (P7-2): the next {@link #stepForward()} is taken by it,
     * and it **costs no action value** (the clock does not move → the round does not change, see {@link #getRound()}).
     *
     * <p>The typical use is a kill-type talent (Seele (希儿) and the like, ROADMAP P5-9): call it inside
     * {@code afterMove()} -- that is, after {@code queue.setTopZero()} -- so that its **normal** turn schedule
     * stays untouched and the extra turn is a free one.
     *
     * <p>During an extra turn, **someone else's ultimate must not be inserted** (see {@link #castUltra}) --
     * that is a rule requirement; inserting another ultimate inside an extra turn turns "extra" into
     * "infinite chain".
     *
     * @param actor the unit that gets the extra turn
     * @return {@code true} = scheduled; {@code false} if the target is dead / not in the queue
     */
    public boolean grantExtraTurn(CanHit actor) {
        return queue.grantExtraTurn(actor);
    }

    /**
     * The currently scheduled extra-turn actor (P7-2); {@code null} if there is none.
     */
    public CanHit getExtraTurnActor() {
        return queue.getExtraTurnActor();
    }

    /**
     * The current round (P7-1): derived from the action bar's accumulated action value; the first round = 1.
     *
     * <p>One round = 100 action value, the first round = 150 (see {@code Queue.initialize()}).
     * This is only a query point; the real round driving (outcome decision, stage round limit) is in P7-3.
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
        // 「【协奏】状态结束前不会进入自己的回合且无法行动」 (M-49 的一环): a suspended unit's turn passes *without it* -- no DOT
        // tick, no TURN_START, no own hooks. ⚠ Deliberately not a control: a control still lets the turn arrive (and
        // the DOTs tick), which is a different sentence; see AbstractBuff#suspendsTurns.
        if (actor.getBuffManager().suspendsTurns()) {
            return;
        }
        // P4-5: damage over time is settled at the start of the turn -- of **any** unit, not only an
        // enemy. The `instanceof Enemy` guard that used to stand here was the visible edge of the old
        // design (a DOT could only exist on an Enemy); now that a DOT is an ordinary buff there is
        // nothing enemy-shaped left to test for. The guard is also what kept "the boss burns us"
        // inexpressible, so removing it is the point of the migration, not a side effect.
        tickDots(actor);
        // ⚠ Immediately after the DOT pass and before the early duration tick (2026-09-28): a regeneration settles N
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
        actor.getBuffManager().beforeMove();
        // M-42 ④: and the buffs on the rest of the field whose clock is this unit's (「星期日自身每回合开始时
        // 【蒙福者】状态持续回合减1」). Swept here rather than inside the manager, because "whose turns count it"
        // is a fact about the battle's turn boundary, not about the unit that happens to carry the buff.
        tickForeignBuffs(actor, true);
        if (actor.isDeath()) {
            return;
        }
        // P8-7: "the turn began", delivered to the data-driven tables. Deliberately here and not as a
        // new buff interface: turn boundaries stay `MoveEvent.beforeMove/afterMove` for buffs (see
        // EventBusTest.turnBoundariesAreStillMoveEvent). What is added is only the *data* subscription
        // to the same moment -- a JSON rule cannot implement a Java interface, so without this event
        // "at the beginning of the turn, ..." would have no trigger source at all.
        //
        // Position matters twice over:
        //   * after `getBuffManager().beforeMove()`, so buffs that expire at this turn boundary are
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
        // most once per own turn". It is **this actor's** counters, not the event actor's, because a
        // limit belongs to the rule's owner -- a rule of mine that fires on somebody else's attack still
        // comes back on MY turn (TriggerLimitTest.otherPeoplesTurnsDoNotCountTheCooldownDown).
        actor.tickTriggerCooldowns();
        fireTriggers(TriggerEvent.TURN_START, actor, actor, 0, 0);
        // P12 (M-49): a countdown exists to HAVE a turn -- this is the moment its reader waits for
        // (「倒计时回合开始时知更鸟退出【协奏】状态并立即行动」). The countdown has no table of its own, and our
        // characters' tables are what subscribe, so the ordinary ally broadcaster is the right one.
        if (actor instanceof Countdown countdown) {
            fireTriggersForAlly(TriggerEvent.COUNTDOWN_TURN, countdown, countdown, 0);
        }
        actor.beforeMove(this);
    }

    /**
     * Settle the damage over time on one unit (P4-5): **first applied, first settled** (in application order).
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
     * state is currently dealing (2026-09-28).
     *
     * <p>「使其当前承受的裂伤状态<b>立即产生 1 次</b>相当于原伤害 85% 的伤害」 (1111 卢卡 天赋). It mirrors
     * {@link #tickDots(CanHit)} — same source, element and {@link DamageType#DOT}, same {@code EnergyGrant.KILL_ONLY} — so
     * this is "the state ticked once more", not a new kind of damage. ⚠ The duration is <b>not</b> touched: the sentence
     * asks for one extra instance of damage, not for the state to age.
     *
     * <p>⚠ The layer ceiling is honoured while summing (the same per-state grouping {@code tickDots} uses): a capped DOT
     * ticks its <b>capped</b> total, because 「当前承受的…伤害」 is what it is dealing now.
     *
     * @param target  the unit carrying the state
     * @param element the element whose state is to tick (an element IS a state in this engine)
     * @param percent the share of that state's current damage (0.85 = 「85%」)
     * @return the damage actually settled
     */
    public double tickDotStateNow(CanHit target, DamageElement element, double percent) {
        if (target == null || target.isDeath() || element == null) {
            return 0;
        }
        double total = 0;
        Map<DamageElement, Integer> paidPerState = new HashMap<>();
        for (DotBuff dot : target.getBuffManager().allBuffsOf(DotBuff.class)) {
            int paid = paidPerState.merge(dot.getElement(), 1, Integer::sum);
            if (dot.getElement() != element) {
                continue;                                        // only the named state ticks
            }
            if (dot.getMaxStacks() > 0 && paid > dot.getMaxStacks()) {
                continue;                                        // 「最多叠加 N 层」: this layer adds no damage
            }
            Damage damage = new Damage(dot.getSource(), target, dot.getElement(), DamageType.DOT,
                    dot.getBaseDamage() * percent);
            total += applyDamage(target, damage, EnergyGrant.KILL_ONLY);
        }
        return total;
    }

    public double tickDots(CanHit target) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double total = 0;
        // A snapshot, so a DOT kill that removes or attaches buffs mid-loop cannot disturb the
        // iteration. Attaching one here also does not settle it this turn -- it is not in the
        // snapshot -- which is the old "attached this turn, burns from the next one" behaviour.
        // ⚠ The layer ceiling is applied per DOCUMENT STATE (an element IS a state here), and the total is
        // order-independent: the first `cap` layers of a state pay, the rest are inert.
        java.util.Map<DamageElement, Integer> paidPerState = new java.util.HashMap<>();
        for (DotBuff dot : target.getBuffManager().allBuffsOf(DotBuff.class)) {
            int paidLayers = paidPerState.merge(dot.getElement(), 1, Integer::sum);
            if (dot.getMaxStacks() > 0 && paidLayers > dot.getMaxStacks()) {
                continue;                                        // 「最多叠加 N 层」: this layer adds no damage
            }
            if (target.isDeath()) {
                break;                                           // killed by a DOT → the rest is not settled
            }
            Damage damage = new Damage(dot.getSource(), target, dot.getElement(),
                    DamageType.DOT, dot.getBaseDamage());
            // KILL_ONLY: a DOT is not "one attack action", so the victim gains no energy; but a DOT kill is still credited to the applier
            total += applyDamage(target, damage, EnergyGrant.KILL_ONLY);
        }
        return total;
    }

    /**
     * Settle every <b>heal over time</b> on one unit (2026-09-28): the twin of {@link #tickDots(CanHit)}, and placed
     * right beside it so the two cannot drift apart.
     *
     * <p>「目标每回合开始时为其回复等同于娜塔莎 7.20% 生命上限 + 192 的生命值，持续 2 回合」 needs this rather than a
     * {@code TURN_START} rule: buffs are counted down by the early tick, which happens <b>after</b> this pass and
     * <b>before</b> {@code TURN_START}, so a {@code turns: 2} regeneration settles twice here while a {@code turns: 2}
     * state read from {@code TURN_START} would heal once.
     *
     * <p>⚠ The amount was derived when the rule fired (「等同于娜塔莎生命上限的…」 is a share of the applier's panel) and
     * is frozen here; the applier is still carried, because {@code Battle.heal} reads their
     * {@code OUTGOING_HEALING_BOOST}.
     *
     * @param target the unit carrying the regenerations
     * @return the total restored this time
     */
    public double tickRegens(CanHit target) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double total = 0;
        // A snapshot, for the same reason the DOT pass takes one: a heal that triggers something cannot disturb it.
        for (com.laosun.aluminium.models.buff.RegenBuff regen
                : target.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.RegenBuff.class)) {
            if (target.isDeath()) {
                break;
            }
            total += heal(regen.getSource(), target, regen.getBaseHeal());
        }
        return total;
    }

    /**
     * Weakness break's attached damage over time (P4-5): only Fire/Lightning/Physical/Wind have it.
     * Ice = Frozen, Quantum = Entanglement, Imaginary = Imprisonment — those three carry <b>no effect at
     * all</b> today, they are not merely "a different DOT".
     *
     * <p>The per-element numbers come from {@code Constant.BREAK_EFFECTS} (the P10-1 table: dot ratio,
     * dot turns, control type), which replaced the old "one ratio and one duration for all four
     * elements" pair of scalars. That table also names the control type of the other three elements,
     * but naming it is where it stops: {@code P10-2}'s control state machine does not exist yet, so
     * {@code hasDot()} is false for them and this method returns above. The honest state is "the four
     * DOT elements are data now; the three control elements are labelled but inert".
     *
     * <p>The DOT is attached as an ordinary {@code DotBuff}, so nothing here decides how it is stored,
     * counted down or removed — that is the buff system's job, and it is what lets the same burn land
     * on a character as on an enemy.
     *
     * @param attacker the breaker (the DOT's source, and the damage's attacker)
     * @param enemy    the target that was broken
     * @param element  the break element
     */
    private void attachBreakDot(CanHit attacker, Enemy enemy, DamageElement element) {
        Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
        if (effect == null || !effect.hasDot()) {
            return;
        }
        enemy.getBuffManager().addBuff(new DotBuff(attacker, element,
                BreakDamageCalculator.breakBaseOf(attacker) * effect.dotRatio(), effect.dotTurns()));
    }

    /**
     * The control part of a weakness break (P10-2): the element's own extra action delay, plus the control
     * state itself.
     *
     * <p><b>What the three non-damaging elements do, and how that was decided.</b> The plan said "冻结期受
     * 伤害 +30% 施加控制" — a guess. The encyclopedia text was probed instead, and it says something else,
     * consistently across every source that describes the states:
     * <ul>
     *   <li>冻结 — {@code "冻结状态下，敌方目标不能行动同时每回合开始时受到等同于<施法者>#4%攻击力的冰属性伤害"}
     *       (深寒徘徊者 / 永冬灾影 / 三月七 / 杰帕德 / 镜流, six independent entries). The damage is <b>ice
     *       damage over time</b>, not a "taken +30%" multiplier — which is why it is <b>not</b> implemented
     *       here: it is a DOT whose ratio is not in the data, and {@code BreakEffect.dotRatio} is that field.
     *       See the TODO in P10-2 rather than a number invented here.</li>
     *   <li>禁锢 — {@code "禁锢状态下，敌方目标行动延后#2%，速度降低#4%"} (瓦尔特).</li>
     *   <li>纠缠 — {@code "「纠缠」会使敌人行动延后，并在敌人下次行动时对其造成额外的量子属性伤害"}
     *       (explicitly about 弱点击破 with Quantum). The delayed damage is again a DOT, left to the same
     *       TODO.</li>
     * </ul>
     * So all three are <b>行动延后</b>, and the difference is 冻结 = cannot act versus 禁锢/纠缠 = acts but slower
     * (a {@code SPEED} debuff). Both are existing primitives, and since 2026-09-27 they are the two parts of
     * {@code ControlBuff} — which is what gives a control its <b>name</b>, so 「冻结状态」 can be asked about no
     * matter which path applied it.
     *
     * <p><b>No effect-hit roll, and no {@code resistKey} lookup, on this path.</b> A break is not a resisted
     * debuff: it happens because the toughness bar emptied. {@code ControlEffect.resistKey} is for the
     * <i>skill</i>-applied case, which goes through {@link #tryApplyDebuff}, and letting a monster's
     * {@code STAT_CTRL_*} resistance cancel a break would make a weakness break silently do nothing.
     *
     * @param enemy   the enemy that was just broken
     * @param element the break element
     */
    private void attachBreakControl(Enemy enemy, DamageElement element) {
        Constant.BreakEffect breakEffect = Constant.BREAK_EFFECTS.get(element);
        if (breakEffect == null) {
            return;
        }
        // Order: the state first, the one-off push last.
        //
        // ⚠ This order used to be load-bearing and no longer is -- recorded because the comment here
        // claimed the opposite and would otherwise outlive its reason. Before the L-26 fix, a push applied
        // *before* the slow was recomputed away by the reschedule, so the element's extra delay became
        // unobservable (measured: 28.409 with and without it). L-26 fixed the loss at its source -- the
        // ledgers are synchronised and the progress is no longer capped from above -- so a push now
        // survives a speed change either way. The order is kept because "apply the state, then the
        // one-off push" is the order the data describes it in, not because correctness depends on it.
        Constant.ControlEffect control = breakEffect.controlEffect();
        if (control != null) {
            // One buff, three parts (2026-09-27): before this, the composition was written out here as "a
            // StunBuff, plus a SPEED debuff if the element slows" -- which left the state with no name, so
            // nothing could ask 「冻结状态」 about a break-frozen unit, and an ability-applied control (which
            // needs exactly the same composition plus a resistance roll) had no shared place to live.
            enemy.getBuffManager().addBuff(new ControlBuff(control, control.turns()));
        }
        if (breakEffect.delayPercent() > 0) {
            // An instant push, not a buff: 禁锢/纠缠's "行动延后" is a one-off on the action bar, so it must
            // not expire with the state (a state that lasts 1 turn leaving a delay that outlives it is the
            // data's own behaviour). Kept outside the control block so that an element may carry extra
            // delay without a state, and so the four DOT elements (delayPercent = 0) are untouched.
            delayMovePercent(enemy, breakEffect.delayPercent());
        }
    }

    public boolean performAction(Skill skill, List<? extends CanHit> targets) {
        if (currentMove == null || skill == null || targets == null) {
            return false;
        }
        CanHit actor = currentMove.getCanHit();
        if (actor.isDeath()) {
            return false;
        }
        if (!actor.getBuffManager().canAct()) {
            return false;
        }
        return useSkill(skill, targets);
    }

    public boolean useSkill(Skill skill, List<? extends CanHit> target) {
        if (currentMove == null || skill == null || target == null) {
            return false;
        }
        CanHit user = currentMove.getCanHit();
        if (user.isDeath()) {
            return false;
        }
        // Skill points (P8-4): all the rules are in skillPointPolicy (including the our-side/enemy-side camp
        // check); Battle only asks once "does this action hold up" -- it knows no character, see F-8 in §F.
        if (!skillPointPolicy.onSkillCast(user, skill)) {
            return false;
        }
        skillRequest(skill, user, target);
        return true;
    }

    public void afterMove() {
        if (currentMove == null) {
            return;
        }

        CanHit actor = currentMove.getCanHit();

        if (actor.isDeath()) {
            actor.getBuffManager().clearAll();
            queue.removeCombatant(actor);
        } else {
            queue.setTopZero();
        }
        currentMove = null;
        removeDeadCombatants();
        if (!actor.isDeath()) {
            actor.afterMove(this);
            actor.getBuffManager().afterMove();
            tickForeignBuffs(actor, false);
            // 「回合结束时」 (2026-09-28): the turn is over HERE -- after the actor's own hook and the late tick, so a rule
            // sees the state the turn ended in.
            fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);
        }
        processRequests();
    }

    /**
     * Spends the duration of every buff on the field whose clock belongs to {@code clockOwner} (M-42 ④).
     *
     * <p>Iterates the <b>camp</b>, so a buff anchored to one of our characters is found wherever it sits — the
     * point of the feature is that it sits on somebody else.
     *
     * @param clockOwner the unit whose turn boundary this is
     * @param early      {@code true} = before the move, {@code false} = after it
     */
    private void tickForeignBuffs(CanHit clockOwner, boolean early) {
        for (CanHit ally : allies) {
            if (ally != null && ally != clockOwner) {
                ally.getBuffManager().tickForeign(clockOwner, early);
            }
        }
    }

    public void processRequests() {
        processSkillRequests();
        processAddRequests();
        // The newly placed summons are on the roster AND in the action bar by now, which is what a rule
        // answering 「被召唤时」 needs before it can touch their action value -- see TriggerEvent.SUMMONED.
        fireSummoned();
        processAdvanceRequests();
        removeDeadCombatants();
    }

    /**
     * Fires {@link TriggerEvent#SUMMONED} for everything summoned since the last settle, once each.
     *
     * <p>The list is drained <b>before</b> the events are fired: a rule that answers 「被召唤时」 by summoning
     * something else must not make this loop chase its own tail (that recursion is bounded by
     * {@code MAX_TRIGGER_DEPTH}, but there is no reason to build it), and the new arrival is picked up by the
     * next settle like any other.
     */
    private void fireSummoned() {
        if (justSummoned.isEmpty()) {
            return;
        }
        List<CanHit> arrived = List.copyOf(justSummoned);
        justSummoned.clear();
        for (CanHit summon : arrived) {
            fireTriggers(TriggerEvent.SUMMONED, summon, null, 0, 0);
        }
    }

    private void processSkillRequests() {
        if (skillRequests.isEmpty()) {
            return;
        }
        List<SkillRequest> requests = new ArrayList<>(skillRequests);
        skillRequests.clear();

        for (SkillRequest req : requests) {
            if (req.object.isDeath()) {
                continue;
            }
            req.skill().execute(this, req.object(), req.target());
        }
    }

    private void skillRequest(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return;
        }
        skillRequests.add(new SkillRequest(skill, user, target));
    }


    /**
     * Assembles every damage zone on the given hit, settles it and applies it to the
     * target, returning the damage actually settled.
     *
     * <p>This is the <b>only</b> public settlement entry point. To learn how much a hit
     * deals, use the returned value — do not assemble it a second time: assembly is
     * additive ({@code addBoost} appends a modifier to the boost zone), so assembling the
     * same {@link Damage} twice would count DMG boost/vulnerability/reduction/weakness twice.
     *
     * @param target the combatant taking the hit
     * @param damage the hit (convention: one damage instance = one Damage object)
     * @return the settled damage, or {@code 0} if the target was already dead
     */
    public double applyDamage(CanHit target, Damage damage) {
        return applyDamage(target, damage, EnergyGrant.ALL);
    }

    /**
     * The internal settlement entry point: one parameter more than the public version, "which kind of energy
     * gain this instance is allowed to settle".
     *
     * <p>Why it is needed (the 2026-09-19 rule): **one attack action grants the victim energy only once**.
     * One attack can derive several damage types (skill damage → break damage → super break damage); if every
     * instance granted the victim energy, the victim would gain more energy for "being hit harder" -- which
     * is wrong. So only the **main instance** of a hit carries {@link EnergyGrant#ALL}; derived instances are
     * always {@link EnergyGrant#KILL_ONLY}.
     *
     * @param grant which kinds of energy gain this instance may settle
     */
    private double applyDamage(CanHit target, Damage damage, EnergyGrant grant) {
        if (target.isDeath() || target.isInvulnerable()) {
            return 0;                  // corpse / phase-transition invulnerability: not settled (so no hitting a corpse either)
        }
        double settled = assemble(damage);                       // damage after the zones (this is "how much was dealt")
        double hpBefore = target.getCurrentHp();
        boolean died = target.takeDamage(settled);
        double hpLoss = hpBefore - target.getCurrentHp();
        double shieldAbsorbed = target.getLastShieldAbsorbed();
        // P8-7: "I was hit" -- a *different fact* from "I lost HP", and the difference is the whole
        // point (see TriggerEvent.TAKING_HIT):
        //   * it fires whenever an incoming damage instance lands, even when a shield absorbs all of
        //     it (hpLoss == 0) -- which is what "after the wearer is hit / attacked" means in the
        //     relic and talent texts, and what would otherwise make e.g. set 105 never accumulate
        //     behind a shielder;
        //   * it does NOT fire for a target that was already dead or invulnerable, because
        //     applyDamage returns above in exactly those cases (nothing landed).
        // The HP_LOST trigger below keeps its own stricter gate (`hpLoss > 0`), so the two events can
        // never be mistaken for one another.
        fireTriggersForAlly(TriggerEvent.TAKING_HIT, damage.getAttacker(), target, settled);
        // P8-6: HP loss and being killed are two **facts**, both emitted here. Placed before the energy
        // settlement -- the event means "an HP change happened", independent of the energy rule (who gains how
        // much, whether it counts as an attack).
        if (hpLoss > 0) {
            broadcastHpLoss(target, hpBefore, target.getCurrentHp(), damage.getAttacker(), hpLoss);
            // `actor` = whoever dealt the damage, `target` = the one who lost HP (i.e. the victim).
            // A counter rule keys off `target == self` -- see TriggerTable's DSL notes.
            fireTriggersForAlly(TriggerEvent.HP_LOST, damage.getAttacker(), target, hpLoss);
        }
        if (died) {
            broadcastKill(damage.getAttacker(), target);
            // The causing instance's cast category rides along: 「每消灭1个敌方目标」-style rules have to be able to
            // ask WHICH skill produced the kill (see the `from_skill` condition), and the instance already knows.
            fireTriggersWithSubject(TriggerEvent.KILL, damage.getAttacker(), target, 0, damage.getCastCategory());
        }
        grantHitAndKillEnergy(target, damage, died, grant);     // P3-2: hit energy gain / kill energy gain
        // The return value = the damage this hit **actually had effect** with = shield-absorbed + HP really lost.
        // The goal is that "hitting a shielded target must not display as 0", while **not** adding settled to
        // the shield absorption (settled is "the amount dealt", and the part the shield absorbed is already in
        // it; adding them would exactly double it).
        // Identity: settled == shieldAbsorbed + hpLoss (the shield is eaten first, HP is deducted only after it
        // runs out); computing them separately is just so that both parts stay observable.
        return shieldAbsorbed + hpLoss;
    }

    /**
     * The **only** energy gain entry point inside battle (P3-2): the rules are decided by {@code target}'s own
     * {@link com.laosun.aluminium.models.energy.EnergyProvider}; team charging (Tingyun (停云)/Huohuo (藿藿)/
     * Sunday (星期日)) will in the future also grant energy to other targets from here.
     *
     * @param target the one gaining energy
     * @param gain   one energy gain description (base value + whether it takes the energy gain rate)
     * @return the value actually credited (after truncation by the cap); 0 if nothing can be credited
     */
    public double applyEnergyGain(CanHit target, EnergyGain gain) {
        if (target == null) {
            return 0;
        }
        double added = target.gainEnergy(gain);
        // P8-6: only emit when the amount actually credited > 0 -- "blocked by the cap" should not count as gaining energy
        if (added > 0) {
            broadcastEnergyGain(target, added);
            // P8-7: let the data-driven tables see it too (e.g. "when I gain energy, ...").
            fireTriggersForAlly(TriggerEvent.ENERGY_GAINED, target, target, added);
        }
        return added;
    }

    /**
     * Convenience entry point: grant someone energy by base value (goes through the energy gain rate).
     *
     * @param target the one gaining energy
     * @param amount the base energy gain value
     * @return the value actually credited
     */
    public double grantEnergy(CanHit target, double amount) {
        return applyEnergyGain(target, EnergyGain.normal(amount));
    }

    /**
     * Skill energy gain hook point (P3-2): called by {@link SkillExecutor} where the skill is executed -- only
     * there does it know the **actual hit set** (AOE hits everyone, BLAST hits three slots, BOUNCE switches
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
        if (user == null) {
            return 0;
        }
        EnergyGain gain = user.getEnergyProvider().onSkillCast(user, skill, hitTargets);
        return gain == null ? 0 : applyEnergyGain(user, gain);
    }

    /**
     * Toughness reduction + weakness break trigger (P4-2): **the only toughness reduction entry point in
     * battle**, called by {@link SkillExecutor} after each damage instance settles.
     *
     * <p>The rules (HSR.md §3.2 / §7.1):
     * <ul>
     *   <li><b>Only hitting a weakness reduces toughness</b> -- a non-weakness element reduces none of it
     *       ("toughness reduction ignoring weakness" is a character trait of Rappa (乱破)/Himeko (姬子)·启行
     *       and the like; wait for P8-7 to put it in data, do not open a default hole here)</li>
     *   <li><b>But super break is not restricted by weakness</b>: while the enemy **is already in the broken
     *       state**, the whole nominal toughness reduction counts as "the excess part", regardless of whether
     *       the element hits a weakness (the official wording's only condition is "the enemy is in the
     *       weakness-broken state"). A non-weakness attack on an already broken enemy still produces a super
     *       break instance</li>
     *   <li>No toughness bar (the data really does contain enemies with {@code stance = 0}) / already broken /
     *       already dead → no reduction</li>
     *   <li>Toughness reaching zero → the break is triggered from here ({@code Enemy.reduceStance} does no
     *       judgement itself)</li>
     * </ul>
     *
     * <p>The order of the break chain (later tasks add things here): broken state → break damage (P4-3) →
     * action delay (P4-4) → attach DOT (P4-5) → break energy gain (P3-3).
     *
     * <p><b>The return value also gives the "excess part" (P4-6)</b>: let the skill's nominal toughness
     * reduction be {@code S} and the remaining toughness {@code T}; this one {@code S} is **split between two
     * chains** -- break damage uses {@code min(S,T)} ({@link StanceResult#consumed()}), super break damage uses
     * {@code max(0, S-T)} ({@link StanceResult#overkill()}), and the two always sum to {@code S}.
     * So the caller must not keep only one number, otherwise the instance that breaks the toughness would drop
     * the super break part.
     *
     * @param attacker     the attacker (break damage and break energy gain are both credited to it)
     * @param enemy        the target being hit
     * @param element      the element of this damage instance (decides the weakness, and is also the break
     *                     element)
     * @param stanceDamage the toughness reduction points (the skill's {@code stance_list} value, in units of
     *                     "points"; **not** a fixed per-instance value -- for bounces {@link SkillExecutor}
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
     * <p>It matters for one sentence family: 「施放战技…造成弱点击破时」 (1003 姬子 星魂 4) asks not "was something
     * broken" but "did <b>my Skill</b> break it", and the cast category is the only thing that answers it. A break left
     * by a talent's follow-up attack carries {@code UNSPECIFIED} (it is not an active cast) and is therefore correctly
     * <b>not</b> a 「施放战技」 break.
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
        if (attacker == null || enemy == null || stanceDamage <= 0) {
            return StanceResult.NONE;
        }
        // The weakness check must come **before** the "already broken" branch: super break is "converting the
        // toughness reduction that cannot enter the toughness bar", and its premise is still "this instance
        // could reduce toughness in the first place" (only a weakness reduces). If broken were checked first
        // and the whole instance returned as excess, a non-weakness attack on an already broken enemy would
        // conjure a super break out of nothing -- which is wrong.
        if (enemy.isDeath()) {
            return StanceResult.NONE;
        }
        if (enemy.isBroken() || !enemy.hasToughnessBar()) {
            // The toughness bar is empty (already broken / an enemy with stance = 0 in the data) ⇒ the whole
            // nominal toughness reduction cannot enter the toughness bar, so all of it counts as "the excess
            // part" -- that is the input to super break (P4-6).
            //
            // **The weakness check is deliberately skipped here**: the official wording is "after attacking an
            // enemy in the weakness-broken state, this attack's toughness reduction value is converted into 1
            // super break damage" -- the only condition is "the enemy is already in the broken state", with no
            // element restriction. So a non-weakness element hitting an already broken enemy **still** produces
            // super break (using the whole nominal toughness reduction value).
            // Compare the regular toughness reduction below: that step is still strictly "only a weakness
            // reduces".
            return new StanceResult(0, stanceDamage, 0, false);
        }
        if (!enemy.isWeakTo(element)) {
            return StanceResult.NONE;        // while not broken: a non-weakness reduces nothing at all, so there is no excess part
        }
        // H-4: break damage is computed from **the value this instance actually reduced**, not the skill's nominal toughness reduction
        double consumed = enemy.reduceStance(stanceDamage);
        double overkill = stanceDamage - consumed;                 // P4-6: the excess part = the input to super break
        if (enemy.getStance() > 0) {
            return new StanceResult(consumed, 0, 0, false);        // not emptied: no excess part available
        }
        enemy.breakEnemy(element);
        enemy.setBrokenRemainTurns(Constant.BROKEN_REMAIN_TURNS);
        // P8-6: the break **fact** is emitted here (the only entry point -- an already broken enemy never
        // reaches this line a second time).
        // Placed before damage/delay/DOT/energy: listeners want the moment of "just got broken".
        broadcastBreak(attacker, enemy, element);
        fireTriggersWithSubject(TriggerEvent.BREAK, attacker, enemy, 0, fromCast);
        // The break damage is settled right here, so the settled value must be carried out -- it belongs to
        // **this attack**, and dropping it would make AttackEvent.totalDamage miss a whole break chain.
        // KILL_ONLY: the break is extra damage derived from the main instance, so the victim gains no energy
        // (one attack grants energy only once); but if the main instance did not kill and the break finishes it
        // off, the kill energy gain is still credited to the attacker.
        double breakDamage = applyDamage(enemy,
                BreakDamageCalculator.build(attacker, enemy, element, consumed), EnergyGrant.KILL_ONLY); // P4-3
        delayMovePercent(enemy, Constant.BREAK_DELAY_RATIO);                                       // P4-4 action delay
        attachBreakDot(attacker, enemy, element);                                                  // P4-5 DOT
        attachBreakControl(enemy, element);                                                        // P10-2 control state
        gainBreakEnergy(attacker, enemy);            // P3-3
        return new StanceResult(consumed, overkill, breakDamage, true);
    }

    /**
     * The result of one toughness reduction (P4-6).
     *
     * <p>The two toughness reduction numbers **always sum to this instance's nominal toughness reduction**,
     * with nothing double-counted and nothing dropped.
     *
     * @param consumed    the points actually deducted from the toughness bar (break damage uses this)
     * @param overkill    the points beyond the remaining toughness (super break damage uses this; 0 when the
     *                    bar was not emptied)
     * @param breakDamage the **settled break damage** triggered this time (0 = no break triggered).
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
     * Delay the action by a percentage of the target's action period (P4-4): {@code delay = period × percent},
     * period = {@code 10000 / speed} (consistent with {@code Queue}'s {@code ACTION_THRESHOLD}).
     *
     * @param target  the target being delayed
     * @param percent the delay ratio (0 ~ 1, HSR weakness break = 0.25)
     * @return {@code true} = the target is in the action bar and was really delayed
     */
    public boolean delayMovePercent(CanHit target, double percent) {
        if (target == null || percent <= 0) {
            return false;
        }
        double speed = target.getAttribute(AttributeType.SPEED).get();
        if (speed <= 0) {
            return false;
        }
        return queue.delayAction(target, 10000.0 / speed * percent);
    }

    /**
     * Called when a broken enemy's turn comes up (P4-4): decrements the broken turn count, restores toughness
     * at 0, and returns "this turn is skipped".
     *
     * <p>When the caller (today the {@code Main} demo, in P5-5 the enemy turn execution) gets {@code true},
     * it must not let it act and goes straight to {@link #afterMove()}.
     *
     * @param enemy the enemy whose turn it is
     * @return {@code true} = it is still broken, so it does not act this turn
     */
    public boolean handleBrokenTurn(Enemy enemy) {
        if (enemy == null || !enemy.isBroken()) {
            return false;
        }
        enemy.setBrokenRemainTurns(enemy.getBrokenRemainTurns() - 1);
        if (enemy.getBrokenRemainTurns() <= 0) {
            enemy.recoverFromBroken();
        }
        return true;
    }

    /**
     * A unit's current aggro value (P5-1/P5-2): it decides the probability of the enemy selecting it.
     *
     * <p>Priority: the {@code aggro} in the character data (it is the game multiplier itself: Preservation
     * 150 / Destruction 125 / others 100 / Hunt·Erudition 75) → when there is no data, fall back to the path's
     * default tier → non-characters (enemies/summons) get 100.
     *
     * <p>**Taunt is not here**: taunt is the hard constraint of "can only be selected", handled by
     * {@link TargetSelector}. Squeezing it into the aggro value by multiplication can only raise the
     * probability and can never achieve "can only be selected".
     *
     * @param entity the unit to query
     * @return the aggro value (&gt; 0)
     */
    public double aggroOf(CanHit entity) {
        if (entity instanceof Character character) {
            if (character.getAggro() > 0) {
                return character.getAggro();
            }
            return character.getPath().getAggro();
        }
        // A unit with no character data can still state its own weight (CanHit.aggro): every servant document
        // gives a 仇恨 line (11413 / 11402 are both 125), and without this every summon sat at the 100 fallback
        // -- a target the enemy treated as an ordinary character while the game makes it 25% more attractive.
        if (entity.getAggro() > 0) {
            return entity.getAggro();
        }
        return 100;                                  // enemies / summons with nothing stated: the regular tier
    }

    /**
     * The aggro table: {@code unit → hit probability} (P5-2). The probabilities sum to 1.
     *
     * @param allies the candidate units (the caller is responsible for filtering out dead targets first)
     * @return an ordered unit → probability mapping; an empty list returns an empty table
     */
    public Map<CanHit, Double> getAggroTable(List<? extends CanHit> allies) {
        Map<CanHit, Double> table = new LinkedHashMap<>();
        double total = 0;
        for (CanHit ally : allies) {
            total += aggroOf(ally);
        }
        if (total <= 0) {
            return table;
        }
        for (CanHit ally : allies) {
            table.put(ally, aggroOf(ally) / total);
        }
        return table;
    }

    /**
     * The application chance of a debuff (P6-1):
     *
     * <pre>
     * chance = base chance × (1 + caster's effect hit rate%) × (1 - victim's effect resistance%) × (1 - specific debuff resistance%)
     * </pre>
     *
     * <p>The result is clamped to {@code [0, 1]}:
     * <ul>
     *   <li>Effect hit rate is a **damage zone**, so with 32% hit rate an 80% base chance → {@code 0.8 × 1.32 = 1.056 → 1.0}
     *       (it never exceeds 100%, but there is also no "excess hit converted into some other benefit");</li>
     *   <li>Effect resistance multiplies the same way: with 30% resistance a 100% base chance → {@code 0.7};</li>
     *   <li>{@code specificResistKey} is a {@code STAT_*} string from the data (see {@code Enemy.debuffResist}):
     *       冰锋 {@code {"STAT_CTRL_Frozen": 1}} → the key factor {@code (1 - 1) = 0} → **completely immune**.
     *       Only {@link Enemy} has this table; characters do not.</li>
     * </ul>
     *
     * <p>**Only the probability is computed, no dice are rolled**: the roll is in {@link #rollDebuff} (using the
     * injected rng), so an AI can "look only at the expectation" without consuming random numbers.
     *
     * @param caster            the applier (reads {@code EFFECT_HIT_RATE})
     * @param target            the victim (reads {@code EFFECT_RESISTANCE} and possibly a specific resistance)
     * @param baseChance        the base chance on the skill panel (0.8 = 80%)
     * @param specificResistKey the specific resistance key; {@code null} or the target is not an enemy → not looked up
     * @return the application chance, falling in {@code [0, 1]}
     */
    public double hitChance(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        if (caster == null || target == null) {
            return 0;
        }
        double hit = caster.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double resist = target.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double specific = 0;
        if (target instanceof Enemy enemy && specificResistKey != null) {
            specific = enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0);
        }
        return Math.clamp(baseChance * (1 + hit) * (1 - resist) * (1 - specific), 0, 1);
    }

    /**
     * After the chance check, whether this debuff is actually applied (P6-1).
     *
     * <p>Rolls with the **injected {@link #rng}**: the same seed → the same battle is reproducible.
     *
     * @param caster            the applier (reads its effect hit rate)
     * @param target            the victim (reads its effect resistance / specific resistance)
     * @param baseChance        the base chance on the skill panel (0.8 = 80%)
     * @param specificResistKey the key of the specific debuff resistance (a {@code STAT_*} string from the data), {@code null} = not looked up
     * @return {@code true} = it hit, so the buff may be applied
     */
    public boolean rollDebuff(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        return rng.nextDouble() < hitChance(caster, target, baseChance, specificResistKey);
    }

    /**
     * Apply a debuff: the hit check comes first, and only on a hit is {@code addBuff} called (P6-1).
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
        if (caster == null || target == null || buff == null || target.isDeath()) {
            return false;
        }
        // Class resistance (「抵抗控制类负面状态的概率提高35%」 / 「免疫控制类负面状态」): the family the state belongs to
        // is a property of the state itself (`AbstractBuff.debuffClass`), so a control written tomorrow is covered by
        // a resistance written today. ⚠ Multiplied rather than folded into `specific`: 「概率提高35%」 means 35% of the
        // chances that would have landed do not, and 1.0 is immunity either way.
        double classResist = target.getBuffManager().debuffResistOf(buff.debuffClass());
        double chance = hitChance(caster, target, baseChance, specificResistKey) * (1 - classResist);
        if (!(rng.nextDouble() < Math.clamp(chance, 0, 1))) {
            return false;
        }
        target.getBuffManager().addBuff(buff);
        return true;
    }

    /**
     * Healing amount (P6-2): it **does not touch {@code Damage}**; it is an independent set of damage zones.
     *
     * <pre>
     * healing = base amount × (1 + outgoing healing boost) × (1 + healing taken boost)
     * </pre>
     *
     * <p>Both factors multiply, and they come from **different people**:
     * {@code OUTGOING_HEALING_BOOST} is read from the one applying the heal (the healer's traces/light cone),
     * {@code HEAL_TAKEN_RATIO} is read from the one being healed (the healing taken bonus; **a negative value
     * is healing reduction** -- the game has no separate "healing reduction" attribute, see
     * {@code AttributeType}).
     *
     * <p>It only computes the number and **does not change HP**: the execution is in
     * {@link #heal(CanHit, CanHit, double)}.
     *
     * @param healer     the one applying the heal ({@code null} → only the healing-taken side is computed)
     * @param target     the one being healed
     * @param baseAmount the base healing amount (skill multiplier × attribute + flat value, computed by the caller)
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
        return baseAmount * (1 + outgoing) * (1 + taken);
    }

    /**
     * Perform one heal (P6-2): compute the healing amount first, then apply it to the target's HP
     * ({@code CanHit.heal} caps it itself and does nothing for the dead).
     *
     * @return the **HP actually restored** (after truncation by the cap; 0 when the target is dead or the
     * healing amount ≤ 0)
     */
    public double heal(CanHit healer, CanHit target, double baseAmount) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double amount = calculateHeal(healer, target, baseAmount);
        if (amount <= 0) {
            return 0;
        }
        double before = target.getCurrentHp();
        target.heal(amount);
        double healed = target.getCurrentHp() - before;
        // P8-6: only emit when HP was really restored (healing at full HP is 0 and is not a heal event)
        if (healed > 0) {
            broadcastHeal(healer, target, healed);
            fireTriggersForAlly(TriggerEvent.HEALED, healer, target, healed);
        }
        return healed;
    }

    /**
     * Gain a shield (P6-3).
     *
     * <p><b>No stacking</b>: it directly overwrites the current shield value (shields in this game normally do
     * not stack; a refresh from the same source is treated as an overwrite).
     * Because {@link CanHit#takeDamage} is "deduct shield first, then HP", "not dying before the shield breaks"
     * holds automatically.
     *
     * <p>⚠ This overload states <b>no provider</b>, so no shield boost applies: it is the raw entry point the fixtures
     * and demos use, and the units that call it are stating "this shield came from nowhere in particular". Content
     * goes through {@link #grantShield(CanHit, CanHit, double)} — which is what makes 「装备者提供的护盾量提高 X%」 mean
     * the <i>giver's</i> shield and not everybody's ({@link #boostedShield}).
     *
     * @param target the one gaining the shield (no effect if already dead)
     * @param amount the shield amount (≤ 0 is treated as clearing the shield)
     * @return the shield value actually set
     */
    /**
     * Places a <b>countdown</b> on the action order (M-49): a unit that only exists to have a turn at a fixed speed.
     *
     * <p>Called by the {@code START_COUNTDOWN} op. The unit is scheduled by the same queue as everybody else, so every
     * mechanic that already moves the action order (advance, delay, weakness break) moves it too — for free and by
     * construction, which is exactly why 「直到倒计时回合」 cannot be spelled as a `turns` count.
     *
     * @param name  what it is called in logs
     * @param speed its fixed speed (90 for 知更鸟's 【协奏】)
     * @return the countdown, for tests and logs
     */
    public Countdown startCountdown(String name, double speed) {
        return startCountdown(null, name, speed);
    }

    /**
     * The same, recording <b>who placed it</b> — the handle {@code actor == countdown} resolves through.
     *
     * @param owner who placed it ({@code null} = unattributed, and then no rule's {@code countdown} term matches it)
     */
    public Countdown startCountdown(CanHit owner, String name, double speed) {
        Countdown countdown = new Countdown(owner, name, speed);
        countdowns.add(countdown);
        queue.addCombatant(countdown);
        return countdown;
    }

    /** The countdown units placed so far (M-49), in the order they were started. */
    public List<Countdown> countdowns() {
        return java.util.Collections.unmodifiableList(countdowns);
    }

    /** The countdowns a given unit placed — the list {@code actor == countdown} reads (mirrors {@code summonsOf}). */
    public List<Countdown> countdownsOf(CanHit owner) {
        List<Countdown> mine = new ArrayList<>();
        for (Countdown countdown : countdowns) {
            if (countdown.getOwner() == owner) {
                mine.add(countdown);
            }
        }
        return mine;
    }

    public double grantShield(CanHit target, double amount) {
        return grantShield(null, target, amount);
    }

    /**
     * Gain a shield, <b>as created by {@code provider}</b>.
     *
     * <p>The provider is not decoration: {@code provider}'s {@link AttributeType#SHIELD_BOOST} multiplies the amount,
     * which is the engine's answer to 「使装备者提供的护盾量提高 20%」 (遗器 103 / 128, 一件光锥). A {@code null} provider
     * means "unknown giver" and applies no boost — the safe direction, since a boost that cannot be attributed would
     * silently strengthen every shield in the fight.
     *
     * @param provider who is granting it ({@code null} = unknown, so unboosted)
     * @param target   the one gaining the shield (no effect if already dead)
     * @param amount   the shield amount before the provider's boost (≤ 0 is treated as clearing the shield)
     * @return the shield value actually set
     */
    public double grantShield(CanHit provider, CanHit target, double amount) {
        return grantShield(provider, target, amount, "");
    }

    /**
     * The same, also recording <b>which rule</b> created the shield.
     *
     * <p>The rule id is what tells two shields from one giver apart: 「战技提供的护盾」 (1001 三月七 星魂 6) must not
     * answer for the shield her 星魂 2 gives at battle start. A grant that names no rule states {@code ""}, and a
     * condition asking for a named rule then correctly answers "no".
     *
     * @param provider who is granting it ({@code null} = unknown, so unboosted)
     * @param target   the one gaining the shield (no effect if already dead)
     * @param amount   the shield amount before the provider's boost (≤ 0 is treated as clearing the shield)
     * @param ruleId   the id of the rule granting it ({@code ""} = unnamed)
     * @return the shield value actually set
     */
    public double grantShield(CanHit provider, CanHit target, double amount, String ruleId) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double value = boostedShield(provider, amount);
        target.setShield(value, provider, ruleId);
        // P12 (M-43): "a shield was granted" is its own fact, so 「受到队友提供的…护盾时」 can subscribe to it.
        // ⚠ Only a grant that leaves a shield standing (a grant of ≤ 0 is how this API spells "clear it"), and the
        // actor is the provider -- null for the raw overload, which then correctly fails `actor is_ally`.
        // ⚠ The TIMED path (`SHIELD` with `turns`) does not come through here: its shield is installed by a
        // ShieldBuff, which has no Battle handle, so the interpreter announces that one itself.
        if (value > 0) {
            fireTriggersForAlly(TriggerEvent.SHIELD_GRANTED, provider, target, value);
        }
        return value;
    }

    /**
     * The shield amount a given provider's shield is actually worth: {@code amount × (1 + 提供的护盾量提高)}.
     *
     * <p><b>One formula, two paths.</b> A {@code SHIELD} effect reaches the field either as a raw grant (no
     * {@code turns}) or through a {@link com.laosun.aluminium.models.buff.ShieldBuff} (timed), and 「提供的护盾量」 has to
     * mean the same number in both — otherwise a shield would be worth 120 for three turns and 100 forever after the
     * duration came off, which is exactly the class of silent discrepancy this project keeps hunting. The buff
     * snapshots this value when it is constructed; the raw path calls it here.
     *
     * <p>⚠ The boost is read <b>once, at grant time</b>, and frozen into the shield (the same convention
     * {@code MODIFY_ATTR}'s derived values use): a shield already standing does not grow when its giver is
     * strengthened later.
     *
     * @param provider whose boost applies ({@code null} = none)
     * @param amount   the requested amount
     * @return the amount after the boost, never negative
     */
    public static double boostedShield(CanHit provider, double amount) {
        double base = Math.max(0, amount);
        if (provider == null) {
            return base;
        }
        return base * (1 + provider.getAttribute(AttributeType.SHIELD_BOOST).get());
    }

    /**
     * The members of a unit's opposing camp (P5-5): our side → enemies; enemies → our side.
     *
     * <p>**It does not filter out the dead** (the caller filters as needed): it only answers "whose camp is
     * this", not "who can be hit". If a third camp is introduced in the future
     * ({@link com.laosun.aluminium.enums.Camp#NEUTRAL}), the semantics of this method need to be redefined.
     *
     * @param self the querier
     * @return the list of the opposing camp (it is {@code allies} / {@code enemies} itself, not a copy)
     */
    public List<? extends CanHit> getOpponents(CanHit self) {
        if (self == null || self.getCamp() == null) {
            return List.of();
        }
        return self.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER ? enemies : allies;
    }

    /**
     * Break energy gain (P3-3): P4-4 calls this **one** funnel point at the moment of the break; the rules
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
    // P8-6 event broadcasting
    //
    // Broadcast rules (**deliberately unified**, so that the next batch of events does not invent a third
    // way of writing it):
    //
    //   * Directly involved parties **always** receive it (even if it is an enemy). Reason: an event
    //     describes a **fact**, unrelated to camp; and elite/Boss mechanics such as counters and immunity
    //     also need to subscribe to the things happening to themselves.
    //   * Our side **additionally, all of them** receive it -- `AttackEvent` already established this
    //     convention (Robin (知更鸟) 【协奏】/ Tribbie (缇宝) field hangs on the support itself, and "after each
    //     time one of our targets casts an attack" is subscribed by each of them).
    //   * Our members are **de-duplicated**: if a `CanHit` already received it as an involved party, it does
    //     not receive it a second time (otherwise the same buff is called twice and the effect doubles).
    //
    // `TurnStartEvent` was not created separately: turn start/end is already expressed by
    // `MoveEvent.beforeMove/afterMove` (`CanHit` implements and forwards it), so adding another layer would
    // be a duplicate abstraction.
    // ==================================================================

    /**
     * Deliver an event to "the directly involved parties + our entire side (de-duplicated)".
     *
     * @param targets  the directly involved parties (may contain {@code null}, which is skipped)
     * @param consumer the delivery action
     */
    private void dispatch(Consumer<CanHit> consumer, CanHit... targets) {
        Set<CanHit> notified = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CanHit target : targets) {
            if (target != null && notified.add(target)) {
                consumer.accept(target);
            }
        }
        for (CanHit ally : allies) {
            if (notified.add(ally)) {
                consumer.accept(ally);
            }
        }
    }

    /**
     * Energy credited (P8-6).
     */
    private void broadcastEnergyGain(CanHit target, double added) {
        dispatch(t -> t.onEnergyGain(this, target, added), target);
    }

    /**
     * HP loss (P8-6). {@code source} is this hit's attacker and may be {@code null}.
     */
    private void broadcastHpLoss(CanHit target, double before, double after, CanHit source, double amount) {
        dispatch(t -> t.onHpLoss(this, target, before, after, source, amount), target, source);
    }

    /**
     * Kill (P8-6).
     */
    private void broadcastKill(CanHit attacker, CanHit victim) {
        dispatch(t -> t.onKill(this, attacker, victim), victim, attacker);
    }

    /**
     * Heal (P8-6).
     */
    private void broadcastHeal(CanHit healer, CanHit target, double healed) {
        dispatch(t -> t.onHeal(this, healer, target, healed), target, healer);
    }

    /**
     * Weakness break (P8-6).
     */
    private void broadcastBreak(CanHit attacker, CanHit target, DamageElement element) {
        dispatch(t -> t.onBreak(this, attacker, target, element), target, attacker);
    }

    /**
     * Skill points credited (P8-6). Only delivered to our side -- skill points are a **resource of our team**
     * and the enemy has no share.
     *
     * <p>"Our team" is the camp ({@code allies}), but only a {@link Character} has anything to do with skill
     * points, so this is one of the few places that could equally read {@code characters}: a friendly summon
     * has no trigger table and no skill point hooks of its own. It reads the camp for consistency with the
     * event policy -- and because a future summon that does care should not have to be remembered here.
     */
    private void broadcastSkillPointGained(int amount) {
        for (CanHit ally : allies) {
            ally.onSkillPointGained(this, amount);
        }
    }

    /**
     * Skill points spent (P8-6). Only delivered to our side, same as {@link #broadcastSkillPointGained}.
     */
    private void broadcastSkillPointSpent(int amount) {
        for (CanHit ally : allies) {
            ally.onSkillPointSpent(this, amount);
        }
    }

    // ==================================================================
    // P8-7 trigger tables
    //
    // The engine fires a named event and each character's data-driven table decides whether to
    // react. Nothing here knows which character it is looking at -- that is the whole point of the
    // trigger table (P8-0 three-way split).
    // ==================================================================

    /**
     * Depth of nested trigger firing, used only for diagnostics.
     *
     * <p>The recursion guard matters because a trigger may itself produce an event that triggers
     * more tables (heal -> heal buff -> ...). The engine does **not** try to be clever about
     * cycles; it caps the nesting and reports loudly, so a runaway table is caught rather than
     * hanging the battle.
     */
    private int triggerDepth;

    /**
     * Nesting depth of buff-path reactions; see {@link #runCounter}. Per battle, never static.
     */
    private int counterDepth;

    /**
     * How deep nested trigger firing may go before the engine gives up (see {@link #triggerDepth}).
     */
    private static final int MAX_TRIGGER_DEPTH = 8;

    /**
     * Rolls a rule-level probability (「有 35% 的固定概率…」) against the battle's own random source.
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
        if (chance >= 1) {
            return true;
        }
        if (!(chance > 0)) {
            return false;
        }
        return rng.nextDouble() < chance;
    }

    /**
     * Fires a trigger event with neither actor nor subject (BATTLE_START and similar).
     *
     * @param event the event
     * @return how many rules fired in total
     */
    public int fireTriggers(TriggerEvent event) {
        return fireTriggers(event, null, null, 0, 0);
    }

    /**
     * Fires a trigger event to the tables of our characters.
     *
     * <p>Every character on our side evaluates the event with itself as {@code self}. Two separate
     * facts are handed over because characters need both and they are not the same thing:
     * <ul>
     *   <li>{@code actor} — who <b>caused</b> the event. "After an ally attacks" is
     *       {@code actor != self}.</li>
     *   <li>{@code target} — what it <b>happened to</b>. "After I am hit" is {@code target == self};
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
        // ⚠ Both nulls spelled out: with a 6-arg (..., Damage) and a 6-arg (..., SkillCategory) overload, a single bare
        // null is ambiguous (measured: the compiler refused it).
        return fireTriggers(event, actor, target, hitCount, amount, null, null);
    }

    /**
     * The same, stating <b>which cast</b> produced the event — the form {@code ALLY_ATTACK} uses.
     *
     * <p>「每当我方目标…施放 <b>2 次普攻、战技、终结技</b>后」（1215 寒鸦）has to tell the three slots apart, and an attack
     * event is the only place that count can be taken <b>once per cast</b>: counting on {@code DEALING_DAMAGE} would
     * count <i>hits</i> instead (a multi-hit skill would mark several times for one cast — a wrong number with no
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
     * exactly one method taking a {@code Damage} — {@code applyDamage} — so that "the same hit assembled twice"
     * stays structurally impossible. This is the internal carrier for that context, not a second way in.
     *
     * @param damage the instance being settled, or {@code null} for every other event (which is what the
     *               five-argument overload passes)
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
     * still change it), while {@code fromCast} is the {@link SkillCategory} of the cast that built it — which a kill and
     * a weakness break can also name, and which is what 「施放战技…造成弱点击破时」 asks about.
     */
    private int fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                             Damage damage, SkillCategory fromCast) {
        if (triggerDepth >= MAX_TRIGGER_DEPTH) {
            throw new IllegalStateException(
                    "Trigger recursion exceeded " + MAX_TRIGGER_DEPTH + " levels while firing "
                            + event.value() + "; a trigger table is probably reacting to its own effect");
        }
        triggerDepth++;
        try {
            int fired = 0;
            for (Character ally : characters) {
                if (ally == null || ally.isDeath()) {
                    continue;
                }
                TriggerTable table = ally.getTriggerTable();
                if (table == null || table.isEmpty()) {
                    continue;
                }
                fired += TriggerInterpreter.fire(this, table, event,
                        new TriggerTable.TriggerContext(ally, actor, target, hitCount, amount, damage, this,
                                fromCast));
            }
            return fired;
        } finally {
            triggerDepth--;
        }
    }

    /**
     * Fires an event whose subject is one of our characters (the one who lost HP, was healed, ...).
     *
     * <p>The side check is deliberate: an enemy taking damage should not make our characters react,
     * and enemy events are not ours to data-ise yet (P9 owns monsters).
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
     * <p>Only some events can name one — a kill and a weakness break happen while an instance is being settled, and the
     * instance knows the {@link com.laosun.aluminium.enums.SkillCategory} of the cast that built it. That is what lets
     * a rule say 「**施放战技**对敌方目标造成弱点击破时」 instead of "somebody broke something".
     *
     * <p>⚠ It carries the <b>category</b> rather than the instance: the settlement entry point is the only public API
     * that takes a {@code Damage} ({@code DamagePipelineTest.settlementHasExactlyOnePublicEntryPoint}), and an event
     * does not need the instance's numbers — only which slot produced it.
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
     * Tells our side that one <b>attack</b> has finished — {@link com.laosun.aluminium.models.event.AttackEvent},
     * fired <b>once per attack</b>, after every instance of it has been settled.
     *
     * <p><b>Which attacks fire it.</b> A character's skill activation ({@code SkillExecutor}) and, since
     * P9-4 忆灵, a summon's own attack ({@code EnemySkill.execute}) — both are attacks the engine drives from
     * beginning to end. Derived hits still never fire it: additional damage, true damage, DOT ticks and break
     * damage go straight through {@link #applyDamage} and are <em>part of</em> somebody else's attack. That
     * exclusion is not an oversight, it is what makes the notification safe to hand to arbitrary listeners:
     * listeners are allowed to answer an attack by dealing damage (that is how a third-party kit lands), so a
     * derived hit that fired the event again would recurse until {@code MAX_TRIGGER_DEPTH} threw. A follow-up
     * attack therefore does <b>not</b> consume an {@code "until": "next_attack"} buff — see M-27, which records
     * that accepted under-consumption rather than hiding it.
     *
     * <p><b>Why it is broadcast to {@link #allies} whatever the attacker's camp.</b> The listeners are buffs on
     * our characters, and every one of them answers with its own question ("is this <em>my</em> attack?" —
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
        if (attacker == null || hitTargets == null || hitTargets.isEmpty()) {
            return;                                  // not a single hit landed → it does not count as an attack
        }
        List<CanHit> targets = List.copyOf(hitTargets);
        for (CanHit ally : allies) {
            ally.afterAttack(this, attacker, mainTarget, targets, totalDamage);
        }
    }

    /**
     * Hit energy gain + kill energy gain (P3-2).
     *
     * <p><b>The two rules differ, do not gate them with the same switch:</b>
     * <ul>
     *   <li><b>Hit energy gain</b>: requires this instance to "count as an attack" ({@code countsAsAttack}).
     *       Additional damage / true damage, by the official definition, "does not count as dealing 1 attack"
     *       → the one being hit gains no energy.</li>
     *   <li><b>Kill energy gain</b>: it only looks at "did this instance kill the target", **regardless of
     *       whether that damage counts as an attack**. Any damage attributed to the attacker (basic attack,
     *       skill, break, super break, DOT, additional damage, true damage, ...), as long as it killed the
     *       monster, settles kill energy gain for {@code damage.getAttacker()}.</li>
     * </ul>
     *
     * <p>Why they are separate: additional damage/true damage can kill, but the "kill" itself still happened
     * -- gating them together with {@code countsAsAttack} would leave additional damage's finishing blow
     * without kill energy gain.
     *
     * @param target the one being hit
     * @param damage this hit's damage
     * @param died   whether this instance killed {@code target}
     */
    private void grantHitAndKillEnergy(CanHit target, Damage damage, boolean died, EnergyGrant grant) {
        if (!died) {
            grantHitEnergy(target, damage, grant);
            return;
        }
        grantKillEnergy(target, damage, grant);
    }

    /**
     * Hit energy gain: only the **main instance of this attack** grants energy to the side being hit.
     *
     * <p>Two gates: ① this instance must "count as an attack" (additional damage / true damage do not count
     * as attacks); ② {@code grant} must allow hit energy gain (derived instances such as break / super break /
     * DOT do not, otherwise one attack would give the victim more energy for "dealing more damage types").
     */
    private void grantHitEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant != EnergyGrant.ALL || !damage.isCountsAsAttack()) {
            return;
        }
        EnergyGain hitGain = target.getEnergyProvider().onTakingHit(target, damage);
        if (hitGain != null) {
            applyEnergyGain(target, hitGain);
        }
    }

    /**
     * Kill energy gain: credited to {@code damage.getAttacker()}, **not looking at** {@code countsAsAttack}
     * (any damage attributed to a character that kills a monster should give energy, the 2026-09-19 rule).
     *
     * <p>But it **does look at {@code grant}**: a kill is settled only once. So the main instance carries
     * {@link EnergyGrant#ALL} and derived instances (break / super break / DOT / additional damage / true
     * damage) carry {@link EnergyGrant#KILL_ONLY} -- this way "the main instance did not kill, a derived
     * instance finishes it off" does not miss the kill energy gain, while "the main instance already killed"
     * does not give it twice from a derived instance (which would not settle anyway, the target being dead).
     */
    private void grantKillEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant == EnergyGrant.NONE) {
            return;
        }
        CanHit attacker = damage.getAttacker();
        if (attacker == null) {
            return;
        }
        EnergyGain killGain = attacker.getEnergyProvider().onKill(attacker, target);
        if (killGain != null) {
            applyEnergyGain(attacker, killGain);
        }
    }

    /**
     * Which energy gains this damage instance is allowed to settle (see the internal overload of
     * {@code Battle.applyDamage}).
     */
    private enum EnergyGrant {
        /**
         * Main instance: both hit energy gain and kill energy gain are settled (the damage instance of a skill a character casts).
         */
        ALL,
        /**
         * Derived instance: only kill energy gain is settled (break / super break / DOT / additional damage / true damage).
         */
        KILL_ONLY,
        /**
         * Nothing is settled (reserved).
         */
        NONE
    }

    /**
     * Runs a <b>buff-path reaction</b> (today: a counter-attack) that must not nest.
     *
     * <p>⚠ Why this exists and why it is not {@code MAX_TRIGGER_DEPTH}: a counter is triggered by HP loss,
     * and a counter's own damage causes HP loss. Two units wearing a counter would therefore hit each
     * other forever. The trigger-table version of a counter is stopped loudly by
     * {@link #fireTriggers}'s depth guard, because it is fired from there — but a buff reacts through
     * {@code BuffManager.onHpLoss}, which is <b>not</b> on that path, so it needs its own guard.
     *
     * <p><b>Verified load-bearing</b> by `BossMechanicTest.aCounterAnsweringACounterIsRefused`, which
     * watches the wearer's victim: removing the check lets the answering counter land and turns it red.
     * (An earlier test watched the other side and could not tell the difference — see that test's Javadoc.)
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
        if (counterDepth > 0) {
            return false;
        }
        counterDepth++;
        try {
            reaction.run();
            return true;
        } finally {
            counterDepth--;
        }
    }

    /**
     * Additional damage: a panel-type base (ATK / max HP × multiplier) that **goes through the full damage
     * zones** (it takes DMG boost/defence/resistance/vulnerability).
     *
     * <p>The official definition: "makes the victim take 1 extra instance of damage; this damage does not count
     * as dealing 1 attack" -- so {@code notCountsAsAttack()} is set (**the victim** gains no energy, no
     * toughness is reduced, no attack-level event is triggered).
     * But it **is attributed to the attacker**, so on a kill the attacker still settles kill energy gain
     * (see {@link #grantKillEnergy}).
     *
     * @param base the already-computed base value (e.g. Robin (知更鸟) 120% ATK / Tribbie (缇宝) 12% max HP)
     * @return the settled value of this instance (0 = no damage dealt)
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        return applyAdditionalDamage(attacker, target, element, base, null, null);
    }

    /**
     * The same, for an instance whose crit is <b>stated rather than rolled</b> (「该伤害暴击率固定为100%，暴击伤害固定为150%」).
     *
     * <p>⚠ The numbers ride into the {@link Damage} here instead of being applied by the caller: this is the engine's
     * one additional-damage entry point (`DamagePipelineTest` pins the single-settlement invariant), so an extra
     * overload is the honest place for them — a second public way to build and settle an instance is exactly what
     * that invariant exists to prevent.
     *
     * @param fixedCritRate   {@code 1.0} for "always crits, no roll" ({@code null} = roll normally)
     * @param fixedCritDamage the crit damage to use ({@code 1.5} = 150%; {@code null} with a rate is refused at load)
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage) {
        Damage extra = new Damage(attacker, target, element, DamageType.ADDITIONAL, base);
        if (fixedCritRate != null) {
            extra.fixedCrit(true, fixedCritDamage);
        }
        // KILL_ONLY: additional damage is extra damage derived from some attack, so the victim gains no energy; a kill is still credited to the attacker
        double settled = applyDamage(target, extra.notCountsAsAttack(), EnergyGrant.KILL_ONLY);
        // P10-3 tail: this is the engine's one and only notion of a follow-up attack, so the
        // data-facing FOLLOW_UP event is emitted here rather than from a second attack path.
        // Gated on the instance having really connected: an additional-damage sweep swallowed by
        // invulnerability did not land, and "used a Follow-Up ATK" must not be credited for it.
        // P10-3 tail: this is the engine's one and only notion of a follow-up attack, so the
        // data-facing FOLLOW_UP event is emitted here rather than from a second attack path.
        //
        // Emitted unconditionally, not gated on the instance having dealt damage: the texts that
        // subscribe read "when the wearer uses a Follow-Up ATK", which is the attack being *used*, and
        // an instance absorbed entirely by a shield or an invulnerable target was still used. An
        // earlier version gated this on `settled > 0`; it was dropped because the semantic was
        // questionable and, more decisively, `settled == 0` could not be produced reliably in a test,
        // so the branch would have shipped unverified.
        fireTriggersWithSubject(TriggerEvent.FOLLOW_UP, attacker, target, settled);
        return settled;
    }

    /**
     * True damage: a fixed amount, or a derived value such as "this attack's total damage × %" -- it **skips
     * every damage zone** and does not count as an attack.
     *
     * <p>{@code notCountsAsAttack()} is likewise set: the victim gains no energy and no toughness is reduced;
     * but it is attributed to the attacker, so on a kill the attacker still settles kill energy gain
     * (see {@link #grantKillEnergy}).
     *
     * @param base the true damage amount (no longer affected by defence/resistance/DMG boost/crit/vulnerability)
     * @return the settled value of this instance (0 = no damage dealt)
     */
    public double applyTrueDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage trueDamage = new Damage(attacker, target, element, DamageType.TRUE, base);
        // KILL_ONLY: true damage is likewise derived damage, so the victim gains no energy; a kill is still credited to the attacker
        return applyDamage(target, trueDamage.trueDamage().notCountsAsAttack(), EnergyGrant.KILL_ONLY);
    }

    /**
     * The enemy camp's units that may be selected as attack targets (= alive), in battlefield order.
     *
     * <p>Single source of truth for "who can be hit": {@link SkillExecutor} uses it today,
     * the target selector (P5-4) and wave handling (P7-4) must use the same judgement so
     * that no caller ever picks a corpse (that is where corpse-hitting comes from).
     *
     * <p>Returns {@code CanHit}, not {@code Enemy} (L-8): an enemy-side summon is a legitimate
     * target, and narrowing here would have made the roster widening pointless — the target list is
     * where the widening has to be visible.
     *
     * @return a fresh list of the camp's alive units
     */
    public List<CanHit> targetableEnemies() {
        List<CanHit> targets = new ArrayList<>();
        for (CanHit enemy : enemies) {
            if (!enemy.isDeath()) {
                targets.add(enemy);
            }
        }
        return targets;
    }

    /**
     * Zone assembly + settlement: DMG boost → crit → defence → resistance → {@link Damage#toValue()}.
     *
     * <p>Private on purpose: the only way in is {@link #applyDamage(CanHit, Damage)}, which
     * makes "the same hit assembled twice" structurally impossible.
     *
     * @param damage the hit to assemble and settle
     * @return the final damage of this hit, floored at 1
     */
    private double assemble(Damage damage) {
        CanHit attacker = damage.getAttacker();
        CanHit defender = damage.getDefender();

        // 0) The base layer lives in Damage.toValue(): the skill multiplier plus any absolute addend
        //    («提高数值等同于三月七防御力的30%», ROADMAP M-55), added BEFORE every zone below so it crits and is
        //    boosted exactly like the multiplier. ⚠ Deliberately not a percentage in the boost zone: that would only
        //    equal the sentence when the base happened to equal the attribute. See Damage#addFlat.

        // 1) DMG boost zone: element boost + all-type boost (break/super break/true damage are skipped
        //    automatically by BoostArea.applies())
        AttributeType elementBoost = AttributeType.getBoostByElement(damage.getElement());
        if (elementBoost != null) {
            damage.addBoost(attacker.getAttribute(elementBoost).get());
        }
        damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        // ...plus the follow-up-only boost, which exists because set 115's 2-piece must raise
        // follow-up damage without touching any other attack. Gated on the damage type, not on a flag:
        // additional damage is the engine's one representation of a follow-up attack.
        if (damage.getType() == DamageType.ADDITIONAL) {
            damage.addBoost(attacker.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get());
        }
        // ...and the same shape for damage over time (322's 「使装备者造成的持续伤害额外提高 X%」): gated on the
        // damage TYPE, because that is what the sentence names. ⚠ DoT is NOT one of the types the boost zone skips
        // (break / super break / true damage are), so this reaches the tick -- checked before writing it, since an
        // attribute nothing reads would look exactly like a working rule.
        if (damage.getType() == DamageType.DOT) {
            damage.addBoost(attacker.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get());
        }

        // Scoped boosts (P10-4): 「普攻 / 战技 / 终结技造成的伤害提高 X%」. These cannot be gated on the damage
        // *type* the way the follow-up boost above is -- a basic attack and a skill are both
        // DamageType.NORMAL -- so they are gated on the category of the cast that produced this instance,
        // which SkillExecutor threads through (`Damage.getCastCategory()`).
        //
        // Deliberately conservative about *which* instance carries the category: only the cast's own damage
        // does. Break / super break / DOT / additional instances stay UNSPECIFIED, so "the Skill's DMG" does
        // not silently grow to mean "the break damage that Skill caused" -- if content ever needs that, it is
        // a decision to take on purpose, not a side effect of this wiring. (A follow-up therefore collects
        // FOLLOW_UP_DAMAGE_BOOST and never one of these, which is what the texts mean by 追加攻击.)
        AttributeType scopeBoost = damage.getCastCategory().damageBoost();
        if (scopeBoost != null) {
            damage.addBoost(attacker.getAttribute(scopeBoost).get());
        }

        // Damage-instance conditions (ROADMAP §3「伤害实例条件」): 「对处于 X 状态的目标造成的伤害提高 Y%」. Fired *before* the zones
        // are read, because afterwards the number is final and all a rule could do is describe it. `target` is
        // the one about to take the damage; a rule that changes this instance uses BOOST_DAMAGE, which mutates
        // the instance itself -- the instance is the state, so there is no buff to attach, nothing to clean up,
        // and nothing that can leak into the next hit.
        //
        // It fires for every instance the engine settles, DOT ticks and break damage included: those are damage
        // too, and a rule that means "attacks only" says so with its own conditions.
        fireTriggers(TriggerEvent.DEALING_DAMAGE, attacker, defender, 0, damage.getSkillBaseValue(), damage);

        // 2) Crit zone: only crittable types roll; an effect that already fixed the crit (fixedCrit) is not
        //    overwritten
        if (damage.getType().isCrittable() && !damage.isCritFixed()) {
            double critRate = attacker.getAttribute(AttributeType.CRIT_CHANCE).get();
            boolean isCrit = critRate > 0 && rng.nextDouble() < critRate;
            damage.crit(isCrit, attacker.getAttribute(AttributeType.CRIT_ATTACK).get());
        }

        // 3) Defence zone: attacker level / victim defence / attacker defence ignore
        damage.defence(attacker.getLevel(),
                defender.getAttribute(AttributeType.DEFENCE).get(),
                attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get());

        // 4) Resistance zone: victim resistance - attacker penetration, then clamp (HSR.md §2.5, negative
        //    resistance is fully effective)
        //    Note: weakness break does not change resistance (§2.5; P4 re-check)
        double rawResist = defender instanceof Enemy enemy
                ? enemy.getDamageResist().getOrDefault(damage.getElement(), 0.0)
                : 0.0;
        damage.resist(rawResist, attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get());

        // 5) Hook: entity-level DamageEvent (HSR.md §2.2: weakness = attacker's debuff, vulnerability = victim's
        //    debuff, reduction = victim's buff)
        //    Both sides are notified; the default implementation forwards to each one's BuffManager, and
        //    subclasses can override it for talents/Boss mechanics
        attacker.onDamage(this, damage);
        defender.onDamage(this, damage);

        return Math.max(1, damage.toValue());
    }

    private void processAddRequests() {
        if (addRequestItems.isEmpty()) {
            return;
        }
        for (CanHit canHit : addRequestItems) {
            queue.addCombatant(canHit);
        }
        addRequestItems.clear();
    }

    public void advanceRequest(CanHit canHit, double rate) {
        if (canHit != null && rate >= 0 && rate <= 1) {
            advanceRequests.add(new AdvanceRequest(canHit, rate));
        }
    }

    private void processAdvanceRequests() {
        if (advanceRequests.isEmpty()) {
            return;
        }
        for (AdvanceRequest advanceRequest : advanceRequests) {
            queue.advanceActionByPercent(advanceRequest.object, advanceRequest.rate);
        }
        advanceRequests.clear();
    }

    private void removeDeadCombatants() {
        perishOrphanedSummons();                     // P9-4: a summon goes when its master does
        for (Signal signal : queue.snapshot()) {
            if (signal.getCanHit().isDeath()) {
                queue.removeCombatant(signal.getCanHit());
            }
        }
        releaseBuffsAnchoredToTheDead();             // M-42 ③: a buff spent by MY turns has no clock left
        checkResult();                               // P7-3: decide the outcome right after clearing the corpses
    }

    /**
     * Takes off every buff whose duration was being spent by a unit that is now dead (M-42 ③).
     *
     * <p><b>Why this has to exist.</b> A buff can state that its clock is somebody else's turns
     * ({@code AbstractBuff.ticksOn}, 「星期日自身每回合开始时【蒙福者】状态持续回合减1」). If that somebody dies, the
     * clock never comes again — the buff would sit on its carrier for the rest of the battle, which is not "a long
     * duration" but a different mechanic. 星期日's sentence says it outright (「当星期日陷入无法战斗状态时，
     * 【蒙福者】效果也会被解除」); this is the generic version of it.
     *
     * <p>Swept from here rather than from the dying unit's own table because {@code fireTriggers} <b>skips dead
     * units</b>: the unit that needs to react is gone before it could, and the buffs live on other units anyway.
     */
    private void releaseBuffsAnchoredToTheDead() {
        for (CanHit ally : allies) {
            if (ally == null || ally.isDeath()) {
                continue;
            }
            for (CanHit other : allies) {
                if (other != null && other != ally && other.isDeath()) {
                    ally.getBuffManager().removeBuffsAnchoredTo(other);
                }
            }
        }
    }

    /**
     * Defeats every summon whose master is already down (P9-4).
     *
     * <p>This runs at the top of {@link #removeDeadCombatants}, so the sweep right after it takes the
     * orphan off the action bar in the same pass — the summon stops acting, stops being targetable, and
     * stops counting as a survivor for {@link #checkResult}, all through the ordinary
     * {@link CanHit#isDeath()} channel rather than a second notion of "no longer here".
     *
     * <p><b>Why {@code perish} and not a {@code takeDamage} call.</b> The minion left, it was not beaten
     * down: {@code perish} keeps its HP, so nothing downstream is told "it was hurt". Where the
     * reward-free property actually comes from is worth stating precisely, because it is easy to get
     * backwards — {@code CanHit.takeDamage} fires <b>no</b> events either; {@code HpLoss}/{@code Kill} are
     * emitted by {@link #applyDamage}, the pipeline's single settlement entry point. So what keeps a
     * vanishing minion from paying out every on-kill talent in the game is that this sweep runs
     * <b>outside</b> that entry point. See {@link CanHit#perish()}.
     *
     * <p>The orphan is deliberately <b>left in its camp roster</b> ({@code enemies} or {@code allies}), like
     * any other corpse — the roster records who was in the fight, and "is it alive" is asked through
     * {@code isDeath()} ({@code targetableEnemies} / {@code aliveEnemies} / {@code checkResult} all do
     * exactly that). A master who is merely absent from that roster does <b>not</b> orphan anything: death is
     * the signal, because it is the one thing every removal path in the engine sets.
     *
     * <p>⚠ <b>Both camps are swept.</b> A summon can now be ours as well as theirs (the friendly half of
     * L-8), and a sweep that only walked {@code enemies} would leave a player-side minion standing after its
     * master fell — still acting, still targetable, and still counted as a survivor by {@link #checkResult}.
     */
    private void perishOrphanedSummons() {
        for (CanHit unit : allies) {
            perishIfOrphaned(unit);
        }
        for (CanHit unit : enemies) {
            perishIfOrphaned(unit);
        }
    }

    private static void perishIfOrphaned(CanHit unit) {
        if (unit instanceof Summon summon && !summon.isDeath()
                && summon.getMaster() != null && summon.getMaster().isDeath()) {
            summon.perish();
        }
    }

    /**
     * Brings a summon onto the field (P9-4).
     *
     * <p>The summon comes from real monster data ({@code SummonFactory}, keyed by {@code monster_config.json}
     * — so {@code monster_config.json}'s {@code summon_id} roster on the master tells you the candidates and
     * this call picks one). It joins the master's camp and enters the action bar through the same door a
     * wave does, so it acts from the <b>current</b> action value rather than restarting a round.
     *
     * <p><b>What is deliberately explicit.</b> The level group is a parameter, not something inferred: a
     * monster's own {@code hard_level_group} is almost always 1 and the <em>stage</em> is what decides
     * difficulty, so there is nothing here to guess it from. Guessing is how the project once got a
     * silently-empty skill point policy (see ROADMAP §5 lesson 3) — a wrong answer with nothing to see is
     * worse than a required argument.
     *
     * <p><b>Both camps can summon</b> (the friendly half of L-8 landed on 2026-09-27). The summon joins the
     * master's own camp: an enemy's minion goes into {@link #enemies}, ours into {@link #allies}, so our side
     * is targetable by the enemy as a whole and our own summon is <b>not</b> a legal target for our attacks
     * (our attacks look at {@code enemies}). The camp is taken from the master rather than passed in — a
     * summon that fights for the other side than its summoner is not a thing the text ever asks for.
     *
     * <p>⚠ {@code Camp.NEUTRAL} is refused rather than guessed at: it has no roster of its own, and picking
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
        if (master == null) {
            throw new IllegalArgumentException("A summon (" + summonId + ") needs a master to belong to");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead master cannot summon (" + master.getName() + " -> " + summonId + "): the summon "
                            + "would enter already orphaned, and the very next removeDeadCombatants would "
                            + "take it straight back out, so the call could never do anything");
        }
        if (master.getCamp() != Camp.ENEMY && master.getCamp() != Camp.PLAYER) {
            throw new IllegalArgumentException(
                    "A summon must join a camp that has a roster (master " + master.getName() + " is "
                            + master.getCamp() + ", summon " + summonId + "): CAMP.NEUTRAL has none, and "
                            + "putting it in either list would silently decide who it fights for");
        }
        List<CanHit> camp = master.getCamp() == Camp.PLAYER ? allies : enemies;
        Summon summon = SummonFactory.create(summonId, master.getLevel(), hardLevelGroup, master.getCamp());
        summon.setMaster(master);
        camp.add(summon);
        addRequestItems.add(summon);                 // processRequests pushes it into the action bar
        justSummoned.add(summon);                    // ...and then fires SUMMONED, with it already scheduled
        // The constructor wires the speed listener for the opening roster only; a unit admitted later
        // needs it too, otherwise a speed buff on it would never reorder the action bar.
        summon.setSpeedChangeListener(this::onSpeedChanged);
        return summon;
    }

    /**
     * Brings a character's <b>memosprite</b> (忆灵) onto the field, with a panel derived from that character.
     *
     * <p>This is the character-side sibling of {@link #summon(CanHit, int, int)}: the monster path takes an
     * id from a roster, this one takes nothing but the summoner and reads
     * {@code resources/memosprites/<cid>.json} for how the panel is inherited. It always joins
     * {@link #allies}, because a memosprite fights for whoever summoned it and its summoner is one of ours.
     *
     * <p><b>Idempotent per summoner.</b> If that character already has a living memosprite on the field,
     * nothing happens and the existing one is returned. The documents actually say "if it is already present,
     * restore it to full HP" (阿格莱雅's 「若衣匠已在场，则使其生命值回复至上限」), and that refresh is <b>not</b>
     * modelled here: doing nothing is at least never a wrong <em>state</em>, whereas a second copy of the same
     * memosprite would be one — two units, one of which the player cannot see. The refresh is registered as
     * the next step rather than approximated.
     *
     * @param master the summoning character
     * @return the memosprite (newly placed, or the one already standing), already in the camp roster and
     *         queued to enter the action bar
     * @throws IllegalArgumentException if the master is null, already down, or has no memosprite spec
     */
    public Summon summonMemosprite(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead character cannot summon a memosprite (" + master.getName() + "): it would enter "
                            + "already orphaned, and the very next removeDeadCombatants would take it "
                            + "straight back out");
        }
        Summon existing = memospriteOf(master);
        if (existing != null) {
            return existing;
        }
        Summon memosprite = SummonFactory.memosprite(master);
        memosprite.setMaster(master);
        allies.add(memosprite);
        addRequestItems.add(memosprite);
        justSummoned.add(memosprite);                // see the note in summon(...): SUMMONED fires after scheduling
        memosprite.setSpeedChangeListener(this::onSpeedChanged);
        return memosprite;
    }

    /**
     * The master's living memosprite on the field, or {@code null}.
     *
     * <p>Found by walking {@link #allies} for a summon whose master is this character — the master link is
     * the only place that relation lives, so this is the one query that can answer it. Dead memsprites stay
     * in the roster like every other corpse, hence the {@code isDeath()} filter.
     */
    public Summon memospriteOf(CanHit master) {
        for (CanHit unit : allies) {
            if (unit instanceof Summon summon && summon.getMaster() == master && !summon.isDeath()) {
                return summon;
            }
        }
        return null;
    }

    /**
     * Every living summon this unit owns, looking in <b>its own camp</b>, in roster order.
     *
     * <p>The one place the predicate "is this unit mine" is written: {@link #summonOf} takes the first,
     * {@link #summonCountOf} counts them, and the {@code actor == summon} / {@code target == summon} conditions
     * ask whether an event's subject is among them. Three loops with the same test is how three answers to
     * "which units are mine" start to differ — and a unit with several summons (知更鸟·晴歌's 晴空乐手 is a
     * trio) is exactly where a stop-at-the-first version would silently cap the answer at one.
     *
     * @param master the summoner (may be {@code null}, which owns nothing)
     * @return the living summons whose master is {@code master} (a fresh list, never {@code null})
     */
    public List<Summon> summonsOf(CanHit master) {
        if (master == null) {
            return List.of();
        }
        List<Summon> owned = new ArrayList<>();
        for (CanHit unit : campOf(master)) {
            if (unit instanceof Summon summon && summon.getMaster() == master && !summon.isDeath()) {
                owned.add(summon);
            }
        }
        return List.copyOf(owned);
    }

    /**
     * The first living summon this unit owns, looking in <b>its own camp</b>, or {@code null}.
     *
     * <p>What {@code TriggerInterpreter}'s {@code "summon"} target selector resolves to. The difference from
     * {@link #memospriteOf(CanHit)} is the point: that one is about memsprites on <em>our</em> side (what
     * {@link #summonMemosprite(Character)} manages), while this one answers "which unit did <b>this</b> unit
     * summon" for either camp — an enemy boss's minion is a summon too, and 「装备者及其忆灵」 and a monster's
     * own text are the same shape.
     *
     * @param master the summoner (may be {@code null}, which owns nothing)
     * @return the first living summon whose master is {@code master}, in roster order, or {@code null}
     */
    public Summon summonOf(CanHit master) {
        List<Summon> owned = summonsOf(master);
        return owned.isEmpty() ? null : owned.getFirst();
    }

    /**
     * How many living summons this unit owns — what {@code self_summon_count} evaluates.
     *
     * <p>Counted over the whole roster rather than stopping at the first match, so a unit with several
     * summons is counted correctly: nothing has more than one yet, but 知更鸟·晴歌's 晴空乐手 is a trio, and a
     * query that stopped looking would silently cap it at one.
     *
     * @param master the summoner (may be {@code null}, which owns none)
     * @return the number of living summons whose master is {@code master}
     */
    public int summonCountOf(CanHit master) {
        return summonsOf(master).size();
    }

    /**
     * The roster a unit belongs to, by camp.
     *
     * <p>An unknown camp falls back to the enemy roster, which is where anything on the battlefield that is
     * not one of ours lives — {@code Camp.NEUTRAL} has no roster of its own, which is exactly why
     * {@link #summon} refuses to create such a unit rather than picking one for it.
     */
    private List<CanHit> campOf(CanHit unit) {
        return unit.getCamp() == Camp.PLAYER ? allies : enemies;
    }

    public List<Signal> getQueueSnapshot() {
        return queue.snapshot();
    }

    public void printBattle() {
        printHp();
        queue.printActionQueue();
    }

    public void printHp() {
        System.out.println("=== HP Status ===");
        for (CanHit c : allies) {
            System.out.printf("%s: %.0f / %.0f%n", c.getName(), c.getCurrentHp(), c.getMaxHp());
        }
        for (CanHit e : enemies) {
            System.out.printf("%s: %.0f / %.0f%n", e.getName(), e.getCurrentHp(), e.getMaxHp());
        }
        System.out.println("================");
    }
}
