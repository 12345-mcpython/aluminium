package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;

import java.util.ArrayList;
import java.util.Collections;
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

    public List<Enemy> enemies;

    public Signal currentMove;

    /**
     * The current battle status (P7-3). It starts as {@link Status#NOT_STARTED}, and {@link #startBattle()}
     * turns it into {@link Status#RUNNING}.
     */
    private Status status = Status.NOT_STARTED;

    /**
     * Wave management (P7-4); {@code null} for a non-wave battle.
     *
     * <p>Its only reason to exist is to let {@link #checkResult()} know whether "the enemy team is empty"
     * means **won** or **this wave has not entered yet**.
     */
    private WaveManager waveManager;

    public ArrayList<CanHit> addRequestItems = new ArrayList<>();

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

    /** Current skill points (P8-4). Equivalent to {@code skillPointPolicy.getValue()}. */
    public int getSkillPoints() {
        return skillPointPolicy.getValue();
    }

    /** The regular skill point cap (P8-4). Equivalent to {@code skillPointPolicy.getMax()}. */
    public int getSkillPointMax() {
        return skillPointPolicy.getMax();
    }

    /** Whether there are enough skill points to cast one skill (P8-4). */
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
    private final Random rng;

    public record AdvanceRequest(CanHit object, double rate) {
    }

    public record SkillRequest(Skill skill, CanHit object, List<? extends CanHit> target) {
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue) {
        this(characterQueue, enemyQueue, new Random());
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue, Random rng) {
        characters = characterQueue;
        enemies = enemyQueue;
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
        for (Enemy e : enemyQueue) {
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

    public Random getRng() {
        return rng;
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
     * see {@link com.laosun.aluminium.models.SkillData#getSpNeed()}).
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
     * The current battle status (P7-3).
     */
    public Status getStatus() {
        return status;
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
        if (characters.stream().allMatch(CanHit::isDeath)) {
            status = Status.LOSE;                    // our side being wiped out is a real loss, with or without pending waves
        } else if (enemies.stream().allMatch(CanHit::isDeath) && !pendingWaves) {
            status = Status.WIN;
        }
        return status;
    }

    /**
     * Register the wave manager (P7-4). Called by the {@link WaveManager} constructor; business code does not
     * call it by hand.
     */
    public void setWaveManager(WaveManager waveManager) {
        this.waveManager = waveManager;
    }

    /**
     * The current wave manager (P7-4); {@code null} for a non-wave battle.
     */
    public WaveManager getWaveManager() {
        return waveManager;
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
        if (actor instanceof Enemy enemy) {
            tickDots(enemy);                         // P4-5: settle damage over time first when the enemy's turn starts
            if (enemy.isDeath()) {
                return;
            }
        }
        actor.getBuffManager().beforeMove();
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
        fireTriggers(TriggerEvent.TURN_START, actor, actor, 0, 0);
        actor.beforeMove(this);
    }

    /**
     * Settle the damage over time on one enemy (P4-5): **first applied, first settled** (in application order).
     *
     * <p>DOT goes through the full damage zones (it takes DMG boost and defence/resistance; vulnerability/reduction
     * are injected by the {@code onDamage} hook), but it cannot crit -- expressed by {@link DamageType#DOT}'s
     * {@code crittable=false}.
     *
     * @param enemy the target
     * @return the total damage settled this time (the sum of all DOTs)
     */
    public double tickDots(Enemy enemy) {
        if (enemy == null || enemy.isDeath()) {
            return 0;
        }
        double total = 0;
        for (Dot dot : new ArrayList<>(enemy.getDots())) {       // snapshot iteration: settlement may remove entries
            if (enemy.isDeath()) {
                break;                                           // killed by a DOT → the rest is not settled
            }
            Damage damage = new Damage(dot.getSource(), enemy, dot.getElement(),
                    DamageType.DOT, dot.getBaseDamage());
            // KILL_ONLY: a DOT is not "one attack action", so the victim gains no energy; but a DOT kill is still credited to the applier
            total += applyDamage(enemy, damage, EnergyGrant.KILL_ONLY);
            if (dot.tick()) {
                enemy.removeDot(dot);
            }
        }
        return total;
    }

    /**
     * Weakness break's attached damage over time (P4-5): only Fire/Lightning/Physical/Wind have it
     * (Ice = Frozen, Quantum = Entanglement, Imaginary = Imprisonment; P10-1 unified it into a table).
     *
     * @param attacker the breaker (the DOT's source, and the damage's attacker)
     * @param enemy    the target that was broken
     * @param element  the break element
     */
    private void attachBreakDot(CanHit attacker, Enemy enemy, DamageElement element) {
        if (!Constant.DOT_ELEMENTS.contains(element)) {
            return;
        }
        enemy.addDot(new Dot(attacker, element,
                BreakDamageCalculator.breakBaseOf(attacker) * Constant.DOT_RATIO, Constant.DOT_TURNS));
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
        }
        processRequests();
    }

    public void processRequests() {
        processSkillRequests();
        processAddRequests();
        processAdvanceRequests();
        removeDeadCombatants();
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
            fireTriggersWithSubject(TriggerEvent.KILL, damage.getAttacker(), target, 0);
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
     *         exceeded / whether a break was triggered)
     */
    public StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
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
        fireTriggersWithSubject(TriggerEvent.BREAK, attacker, enemy, 0);
        // The break damage is settled right here, so the settled value must be carried out -- it belongs to
        // **this attack**, and dropping it would make AttackEvent.totalDamage miss a whole break chain.
        // KILL_ONLY: the break is extra damage derived from the main instance, so the victim gains no energy
        // (one attack grants energy only once); but if the main instance did not kill and the break finishes it
        // off, the kill energy gain is still credited to the attacker.
        double breakDamage = applyDamage(enemy,
                BreakDamageCalculator.build(attacker, enemy, element, consumed), EnergyGrant.KILL_ONLY); // P4-3
        delayMovePercent(enemy, Constant.BREAK_DELAY_RATIO);                                       // P4-4 action delay
        attachBreakDot(attacker, enemy, element);                                                  // P4-5 DOT
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
        return 100;                                  // enemies / summons: no path, so give the regular tier
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
     * @param caster           the applier (reads its effect hit rate)
     * @param target           the victim (reads its effect resistance / specific resistance)
     * @param baseChance       the base chance on the skill panel (0.8 = 80%)
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
     * @param caster           the applier
     * @param target           the target
     * @param buff             the buff to apply
     * @param baseChance       the base chance
     * @param specificResistKey the specific resistance key (may be {@code null})
     * @return {@code true} = it was applied
     */
    public boolean tryApplyDebuff(CanHit caster, CanHit target, com.laosun.aluminium.models.AbstractBuff buff,
                                  double baseChance, String specificResistKey) {
        if (caster == null || target == null || buff == null || target.isDeath()) {
            return false;
        }
        if (!rollDebuff(caster, target, baseChance, specificResistKey)) {
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
     *         as 0 will be blocked by heal)
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
     *         healing amount ≤ 0)
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
     * <p>🚧 The shield-boost entries (shield amount boost / shield gained boost) **have no corresponding
     * attribute yet** (there is none in {@code AttributeType}), so for now the shield amount is just the value
     * passed in -- add them when a real effect references them.
     *
     * @param target the one gaining the shield (no effect if already dead)
     * @param amount the shield amount (≤ 0 is treated as clearing the shield)
     * @return the shield value actually set
     */
    public double grantShield(CanHit target, double amount) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double value = Math.max(0, amount);
        target.setShield(value);
        return value;
    }

    /**
     * The members of a unit's opposing camp (P5-5): our side → enemies; enemies → our side.
     *
     * <p>**It does not filter out the dead** (the caller filters as needed): it only answers "whose camp is
     * this", not "who can be hit". If a third camp is introduced in the future
     * ({@link com.laosun.aluminium.enums.Camp#NEUTRAL}), the semantics of this method need to be redefined.
     *
     * @param self the querier
     * @return the list of the opposing camp (it is {@code characters} / {@code enemies} itself, not a copy)
     */
    public List<? extends CanHit> getOpponents(CanHit self) {
        if (self == null || self.getCamp() == null) {
            return List.of();
        }
        return self.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER ? enemies : characters;
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
        for (Character ally : characters) {
            if (notified.add(ally)) {
                consumer.accept(ally);
            }
        }
    }

    /** Energy credited (P8-6). */
    private void broadcastEnergyGain(CanHit target, double added) {
        dispatch(t -> t.onEnergyGain(this, target, added), target);
    }

    /** HP loss (P8-6). {@code source} is this hit's attacker and may be {@code null}. */
    private void broadcastHpLoss(CanHit target, double before, double after, CanHit source, double amount) {
        dispatch(t -> t.onHpLoss(this, target, before, after, source, amount), target, source);
    }

    /** Kill (P8-6). */
    private void broadcastKill(CanHit attacker, CanHit victim) {
        dispatch(t -> t.onKill(this, attacker, victim), victim, attacker);
    }

    /** Heal (P8-6). */
    private void broadcastHeal(CanHit healer, CanHit target, double healed) {
        dispatch(t -> t.onHeal(this, healer, target, healed), target, healer);
    }

    /** Weakness break (P8-6). */
    private void broadcastBreak(CanHit attacker, CanHit target, DamageElement element) {
        dispatch(t -> t.onBreak(this, attacker, target, element), target, attacker);
    }

    /**
     * Skill points credited (P8-6). Only delivered to our side -- skill points are a **resource of our team**
     * and the enemy has no share.
     */
    private void broadcastSkillPointGained(int amount) {
        for (Character ally : characters) {
            ally.onSkillPointGained(this, amount);
        }
    }

    /** Skill points spent (P8-6). Only delivered to our side, same as {@link #broadcastSkillPointGained}. */
    private void broadcastSkillPointSpent(int amount) {
        for (Character ally : characters) {
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

    /** How deep nested trigger firing may go before the engine gives up (see {@link #triggerDepth}). */
    private static final int MAX_TRIGGER_DEPTH = 8;

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
                        new TriggerTable.TriggerContext(ally, actor, target, hitCount, amount));
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
        /** Main instance: both hit energy gain and kill energy gain are settled (the damage instance of a skill a character casts). */
        ALL,
        /** Derived instance: only kill energy gain is settled (break / super break / DOT / additional damage / true damage). */
        KILL_ONLY,
        /** Nothing is settled (reserved). */
        NONE
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
        Damage extra = new Damage(attacker, target, element, DamageType.ADDITIONAL, base);
        // KILL_ONLY: additional damage is extra damage derived from some attack, so the victim gains no energy; a kill is still credited to the attacker
        return applyDamage(target, extra.notCountsAsAttack(), EnergyGrant.KILL_ONLY);
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
     * Enemies that may be selected as attack targets (= alive), in battlefield order.
     *
     * <p>Single source of truth for "who can be hit": {@link SkillExecutor} uses it today,
     * the target selector (P5-4) and wave handling (P7-4) must use the same judgement so
     * that no caller ever picks a corpse (that is where corpse-hitting comes from).
     *
     * @return a fresh list of alive enemies
     */
    public List<Enemy> targetableEnemies() {
        List<Enemy> targets = new ArrayList<>();
        for (Enemy enemy : enemies) {
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

        // 1) DMG boost zone: element boost + all-type boost (break/super break/true damage are skipped
        //    automatically by BoostArea.applies())
        AttributeType elementBoost = AttributeType.getBoostByElement(damage.getElement());
        if (elementBoost != null) {
            damage.addBoost(attacker.getAttribute(elementBoost).get());
        }
        damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());

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
        for (Signal signal : queue.snapshot()) {
            if (signal.getCanHit().isDeath()) {
                queue.removeCombatant(signal.getCanHit());
            }
        }
        checkResult();                               // P7-3: decide the outcome right after clearing the corpses
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
        for (Character c : characters) {
            System.out.printf("%s: %.0f / %.0f%n", c.getName(), c.getCurrentHp(), c.getMaxHp());
        }
        for (Enemy e : enemies) {
            System.out.printf("%s: %.0f / %.0f%n", e.getName(), e.getCurrentHp(), e.getMaxHp());
        }
        System.out.println("================");
    }
}
