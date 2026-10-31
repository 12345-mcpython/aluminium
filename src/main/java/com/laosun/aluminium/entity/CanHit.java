package com.laosun.aluminium.entity;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.BuffManager;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.event.*;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.state.*;
import com.laosun.aluminium.trigger.TriggerTable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base for all entities that can participate in combat.
 *
 * <p>Holds the entity's name, faction ({@link Camp}), and an array of
 * {@link DoubleValue} combat attributes indexed by {@link AttributeType#ordinal()}.
 * Subclasses include {@link Character}, {@link Enemy}, and {@link Summon}.
 */
@Getter
@ToString
public abstract class CanHit implements BattleEvent, MoveEvent, DamageEvent, AttackEvent,
        SkillCastEvent, EnergyEvent, HpLossEvent, HealEvent, KillEvent, BreakEvent,
        SkillPointEvent {
    /**
     * The display name of this entity.
     */
    private final String name;
    /**
     * Combat attributes indexed by {@link AttributeType#ordinal()}.
     */
    private final AttributeSheet attributes;
    /**
     * Faction alignment.
     */
    private final Camp camp;

    /**
     * Combat level: feeds the defence zone now, break base and enemy
     * stat scaling later. Defaults to 80 so existing code keeps working.
     */
    @Setter
    private int level = 80;

    /**
     * This unit's aggro weight, or {@code 0} for "not stated - use the regular tier" ({@link Battle#aggroOf}).
     *
     * <p>A {@link Character}'s own aggro comes from the character data (with the path as its fallback); this field
     * is what a unit with <b>no</b> character data can state instead. Its first user is a memosprite: the documents
     * give servants a line of their own - "ServantID 11413 - 仇恨: 125" - and {@code aggroOf} answers the
     * 100 fallback for every summon unless the field is set, so an enemy would pick its target as if the memosprite
     * were an ordinary character while the game makes it 25% more attractive.
     */
    @Setter
    private int aggro;

    private final SkillSet skills = new SkillSet();

    /**
     * @return the slot-to-skill map
     */
    public Map<SkillType, Skill> getSkills() {
        return skills.all();
    }

    /**
     * @param skills the new slot map
     */
    public void setSkills(Map<SkillType, Skill> skills) {
        this.skills.copyFrom(skills);
    }

    private boolean untargetable;

    private final HealthState health = new HealthState();

    /**
     * @return current HP
     */
    public double getCurrentHp() {
        return health.hp();
    }

    /**
     * Whether this unit is present but cannot be <b>selected</b> as a target right now.
     *
     * <p>Distinct from invulnerability: an invulnerable unit still absorbs what reaches it, while an untargetable one is
     * skipped when a skill picks its victims. Reader: a recorded fight states, per cast, which enemies the skill could reach
     * ({@code field.on_stage}) - measured: cast 65 of the 2026-10-30 fight hit ONE enemy twice where the replay hit three.
     *
     * @return {@code true} when a skill's target selection must skip this unit
     */
    public boolean isUntargetable() {
        return untargetable;
    }

    /**
     * @param untargetable whether skills must skip this unit when selecting targets
     */
    public void setUntargetable(boolean untargetable) {
        this.untargetable = untargetable;
    }

    /**
     * @return whether the unit is dead
     */
    public boolean isDeath() {
        return health.isDead();
    }

    /**
     * @return whether the unit is invulnerable
     */
    public boolean isInvulnerable() {
        return health.isInvulnerable();
    }

    /**
     * @param invulnerable the new flag
     */
    public void setInvulnerable(boolean invulnerable) {
        health.setInvulnerable(invulnerable);
    }

    private final EnergyState energy = new EnergyState();

    /**
     * @return current energy
     */
    public double getCurrentEnergy() {
        return energy.current();
    }

    /**
     * @param currentEnergy the new value
     */
    public void setCurrentEnergy(double currentEnergy) {
        energy.setCurrent(currentEnergy);
    }

    /**
     * @return the energy cap
     */
    public double getMaxEnergy() {
        return energy.max();
    }

    /**
     * @param maxEnergy the cap
     */
    public void setMaxEnergy(double maxEnergy) {
        energy.setMax(maxEnergy);
    }

    /**
     * @return the energy provider
     */
    public EnergyProvider getEnergyProvider() {
        return energy.provider();
    }

    /**
     * @param energyProvider the provider
     */
    public void setEnergyProvider(EnergyProvider energyProvider) {
        energy.setProvider(energyProvider);
    }

    private final BuffManager buffManager;

    /**
     * The stack resources this combatant owns.
     *
     * <p>Never {@code null}, like {@code triggerTable}: a character with no stacks simply has an
     * empty manager, so call sites never null-check. This is what lets "stack instead of an energy
     * bar" characters (Acheron's [残梦], Feixiao's [飞黄], Cyrene's [追忆]...) work without a class
     * of their own - the resource is data, the trigger table fills it, and the energy provider reads
     * it to decide whether the ultimate is available.
     */
    @Getter
    private final ResourceManager resources;

    /**
     * Per-rule firing limits ("cooldown" / "once per battle"), keyed by {@code CompiledRule.key()}.
     *
     * <p><b>Why the counters live on the combatant and not on the rule.</b> A trigger table is compiled
     * once and <b>cached per cid</b>, and a relic set's rules are merged into the same table instance for
     * every character wearing it - so a mutable field on a rule would be shared by every wearer, in every
     * battle, for the life of the JVM. That is the leak this project keeps refusing to ship (see the
     * static caches registered as N-1). A combatant owns its own counters, and
     * {@link #onBattleStart(Battle)} clears them.
     *
     * <p>Deliberately without a getter: the maps are mutable, and they are reachable only through the
     * four methods below, so nothing can tick or clear somebody else's limits by accident - the same
     * reasoning that made {@code Queue}'s exposed live heap a registered defect.
     */
    private final TriggerCooldowns triggerCooldowns = new TriggerCooldowns();

    private final RuleLimits ruleLimits = new RuleLimits();

    /**
     * The same question as {@link #isAttackLimitReady}, for a WAVE: the sequence is {@code Battle.waveSequence()}, and because a new
     * wave is a new number, nothing has to be cleared when one starts.
     */
    public boolean isWaveLimitReady(String key, int waveSequence) {
        return ruleLimits.isWaveReady(key, waveSequence);
    }

    /**
     * Records that the rule has had its wave allowance in {@code waveSequence}.
     */
    public void recordWaveUse(String key, int waveSequence) {
        ruleLimits.recordWave(key, waveSequence);
    }



    /**
     * Per-battle <b>amendments to another rule's own numbers</b>, keyed by that rule's {@code id}
     * ("天赋的反击效果每回合可触发的次数增加1次" / "冻结敌方目标的基础概率提高15%").
     *
     * <p><b>Why the numbers live here and not on the rule.</b> Exactly like the firing limits above: a trigger table
     * is compiled once and cached per cid, and a relic set's rules are merged into the same instance for every wearer,
     * so writing a raised {@code per_turn} onto the rule would leak it into every battle in the JVM. The amendment is
     * a fact about <b>this combatant in this battle</b> ("I have Eidolon (星魂) 4 active"), so it is stored next to the counters
     * and reset with them.
     *
     * <p>Note: Two fields, not one: the two sentences raise two different things, and a single "delta" would make it
     * impossible to say which. A rule can carry both (nothing in the corpus does).
     */
    private final RuleAmendments ruleAmendments = new RuleAmendments();

    /**
     * Heal bonuses that are conditional on <b>who is being healed</b>, held by the healer.
     *
     * <p><b>Why this cannot be an attribute.</b> 「为当前生命值百分比小于等于 30% 的我方目标提供治疗时，
     * 娜塔莎的治疗量提高 50%」 (1105's talent) is not a fact about the healer: a stat such as
     * {@code OUTGOING_HEALING_BOOST} has no idea who the recipient is, so the same +50% would apply to a heal
     * on a full-HP ally. It is also not expressible as a rule: there is no pre-heal event, so no rule can raise
     * her outgoing healing just before the heal it is about. The one place with the healer and the recipient
     * together is {@code Battle.calculateHeal}, and this is what it reads there.
     *
     * <p>A list rather than a running total, because each entry keeps its own condition and they are evaluated
     * per heal -- the answer legitimately differs between recipients in the same turn.
     *
     * <p>Registered by {@code REGISTER_HEAL_BONUS} at {@code BATTLE_START}, so it is in place before any heal.
     *
     * @param condition the question asked about this heal, evaluated with the healer as {@code owner} and
     *                  {@code actor} and the recipient as {@code target}
     * @param percent   the bonus as a fraction: {@code 0.5} for 「提高 50%」
     */
    public record ConditionalHealBonus(TriggerTable.Condition condition, double percent) {
    }

    /**
     * The conditional heal bonuses registered on this unit, in registration order.
     */
    private final ConditionalHealBonuses conditionalHealBonuses = new ConditionalHealBonuses();

    /**
     * Files a heal bonus whose applicability is decided per heal, not per unit.
     *
     * @param condition the question asked about each heal, or {@code null} to skip registering
     * @param percent   the bonus as a fraction
     */
    public void addConditionalHealBonus(TriggerTable.Condition condition, double percent) {
        if (condition != null) {
            conditionalHealBonuses.add(condition, percent);
        }
    }

    /**
     * The conditional heal bonuses registered on this unit.
     *
     * @return the list, never {@code null}; empty when the unit has none
     */
    public List<ConditionalHealBonus> conditionalHealBonuses() {
        return conditionalHealBonuses.all();
    }

    /**
     * Per-battle <b>current fixed probabilities</b> of rules that decay, keyed by that rule's {@code id}.
     *
     * <p>"触发该效果的初始固定概率为 100%，成功触发后下一次触发的固定概率降低至当前概率的 20%": the rule's own {@code chance} is the
     * INITIAL value, and this map holds where it has decayed to. Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】.
     *
     * <p><b>Why the number lives here and not on the rule.</b> Exactly like the amendments above: a trigger table is
     * compiled once and cached per cid, so writing a decayed probability onto the rule would leak it into every battle
     * in the JVM and into every later firing of the cached table. The probability is a fact about <b>this combatant in
     * this battle</b>, so it sits next to the firing counters and is cleared with them. An absent entry means "still at
     * the initial value", which is why {@link #resetRuleChance} is a removal rather than a write.
     */
    @Getter(AccessLevel.NONE)
    private final RuleChanceState ruleChances = new RuleChanceState();

    /**
     * The slot of the last <b>Elation-damage skill</b> this combatant finished casting ({@code 0} = none yet).
     *
     * <p>「若施放的欢愉技与上一次施放的不同」 (light cone 23064) needs the previous cast of that category, while
     * {@code from_skill_id} / {@code damage_skill_key} describe only the <b>current</b> one. Written by
     * {@code SkillExecutor} after the cast has completed, so a rule firing on {@code CAST_SETUP} still reads the
     * previous value; read by the condition variable {@code last_elation_skill_id}.
     */
    private int lastElationSkillId;

    /**
     * Per-battle <b>skill level raises</b>, keyed by slot: "战技等级+1""终结技等级+1" (1001 Eidolon (星魂) 3/5, and the
     * same sentence in most characters' kits).
     *
     * <p><b>Why the raise lives here and not on the {@code Skill}.</b> The same reasoning as the rule amendments
     * above: a skill instance belongs to a {@code Character} that a stage can put into more than one battle, so
     * writing a raised level onto it would stack once per battle and never come off. The raise is a fact about
     * <b>this combatant in this battle</b> ("my Eidolon (星魂) 3 is active"), so it sits next to the firing counters and is
     * cleared with them.
     *
     * <p>The <b>base</b> level is the other half, and it is character data, not battle state: it comes from the
     * file's optional {@code "skill_levels"} (see {@code CharacterFactory}), because the document's figure is quoted
     * at a particular level.
     */
    private final SkillBonuses skillBonuses = new SkillBonuses();

    // test event behavior
    public Runnable beforeMove = () -> {
    };
    public Runnable afterMove = () -> {
    };
    public Runnable onBattleStart = () -> {
    };

    /**
     * Constructs a combat entity.
     *
     * @param name       display name
     * @param camp       faction alignment
     * @param attributes pre-computed attribute array
     */
    public CanHit(String name, Camp camp, DoubleValue[] attributes) {
        this.name = name;
        this.camp = camp;
        this.attributes = new AttributeSheet(attributes);
        health.hp(this.attributes.maxHp());

        this.buffManager = new BuffManager(this);
        this.resources = new ResourceManager(this);
    }

    /**
     * Copy construction
     *
     * @param other what you want to copy
     */
    public CanHit(CanHit other) {
        // need to clone
        this.name = other.name;
        this.camp = other.camp;
        this.level = other.level;
        skills.copyFrom(other.getSkills());
        this.attributes = AttributeSheet.copyOf(other.attributes);
        health.hp(attributes.maxHp());
        this.buffManager = new BuffManager(this);
        // Resources are per-battle state, like currentEnergy: the copy starts empty rather than
        // inheriting the original's stacks.
        this.resources = new ResourceManager(this);
        // Configuration-like fields must be copied along too; currentEnergy belongs to a new battle
        // instance and deliberately starts at 0
        energy.setMax(other.getMaxEnergy());
        energy.setCurrent(0);
        energy.setProvider(other.getEnergyProvider());
        this.beforeMove = () -> {
        };
        this.afterMove = () -> {
        };
        this.onBattleStart = () -> {
        };
    }

    /**
     * Returns the {@link DoubleValue} for the given attribute type.
     *
     * @param attributeType the attribute to look up
     * @return the corresponding value object, or {@code null} for percentage-type attributes
     */
    public DoubleValue getAttribute(AttributeType attributeType) {
        return attributes.get(attributeType);
    }

    /**
     * The number a damage multiplier applies to: {@code attribute} read off this unit.
     *
     * <p>Overridden where the game's answer differs from "this unit's own sheet" - see
     * {@link Summon#damageBase(AttributeType)}: a servant's 「%生命上限」 row reads its <b>summoner's</b> Max HP.
     * Two readings of the same number would drift, so every damage base goes through here.
     *
     * @param attribute the attribute the row names ({@code ATK} by default, {@code HEALTH} for 「%生命上限」)
     * @return the value the multiplier applies to
     */
    public double damageBase(AttributeType attribute) {
        return getAttribute(attribute).get();
    }

    public void setSkill(SkillType skillType, Skill skill) {
        skills.set(skillType, skill);
    }

    /**
     * Replaces the {@link DoubleValue} at the given attribute index.
     *
     * <p>If what is replaced is {@code SPEED} and the value really changed, this notifies
     * {@link #notifySpeedChanged()} - this is the only trigger point for "speed change to reorder the
     * action bar" (Pfix E2), so go through here to change speed (or call
     * {@code notifySpeedChanged()} after modifying the {@code DoubleValue}),
     * do not quietly change the speed attribute anywhere else.
     *
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        DoubleValue previous = attributes.get(attributeType);
        attributes.set(attributeType, value);
        if (attributeType == AttributeType.SPEED) {
            double oldSpeed = previous == null ? 0 : previous.get();
            double newSpeed = value == null ? 0 : value.get();
            if (oldSpeed != newSpeed) {
                notifySpeedChanged();
            }
        }
    }

    /**
     * Returns the maximum hit points from the HEALTH attribute.
     */
    public double getMaxHp() {
        return attributes.maxHp();
    }

    /**
     * Callback fired when the speed attribute changes (Pfix E2).
     *
     * <p>{@code Battle} points this at "reschedule this unit's action time" ({@code Queue.refreshSpeed})
     * when it is constructed. Before that, the {@code speed} cached by {@code Signal} was only refreshed at
     * a few "reset the period" moments, so a speed buff/debuff would not be reflected on the action bar
     * immediately.
     */
    private transient Consumer<CanHit> speedChangeListener;

    /**
     * Notify that "this unit's speed changed". Called by {@code Battle.onSpeedChanged};
     * do not call it directly anywhere else (otherwise the action-bar reordering rules would scatter).
     */
    public void notifySpeedChanged() {
        if (speedChangeListener != null) {
            speedChangeListener.accept(this);
        }
    }

    /**
     * Injected by {@code Battle}: how to reorder the action bar when speed changes.
     *
     * @param listener the callback; {@code null} = do not notify (e.g. a unit test with no queue)
     */
    public void setSpeedChangeListener(Consumer<CanHit> listener) {
        this.speedChangeListener = listener;
    }

    private final ShieldState shieldState = new ShieldState();

    /**
     * @return the current shield value
     */
    public double getShield() {
        return shieldState.value();
    }

    /**
     * A raw shield write, which <b>clears the ownership</b>: "somebody set the number directly" is not a timed
     * shield, so no buff may take it off again.
     */
    public void setShield(double value) {
        shieldState.set(value);
    }

    /**
     * Records a shield together with where it came from (the raw-grant path, whose shield no buff owns).
     *
     * @param value    the shield amount
     * @param provider who granted it ({@code null} = unknown)
     * @param ruleId   the id of the rule that granted it ({@code ""} = unnamed)
     */
    public void setShield(double value, CanHit provider, String ruleId) {
        shieldState.set(value, provider, ruleId);
    }

    /**
     * Installs a shield and records the buff that must take it off again.
     *
     * <p>It also records the buff's own origin ({@code getSource()} / {@code getRuleId()}), so a timed shield answers
     * the same question a raw grant does.
     *
     * @param value     the shield amount
     * @param installer the timed shield that installed it
     */
    public void installShield(double value, AbstractBuff installer) {
        shieldState.install(value, installer);
    }

    /**
     * Who provided the shield currently up, or {@code null} (a raw number, no shield, or an anonymous giver).
     */
    public CanHit getShieldProvider() {
        return shieldState.provider();
    }

    /**
     * The {@code id} of the rule that created the shield currently up ({@code ""} when it names none).
     */
    public String getShieldRuleId() {
        return shieldState.ruleId();
    }

    /**
     * @return how much of the last hit the shield absorbed
     */
    public double getLastShieldAbsorbed() {
        return shieldState.lastAbsorbed();
    }

    /**
     * Takes the shield off <b>only if</b> {@code installer} is the buff that put it up.
     *
     * <p>This is the whole reason the installer is remembered: when a second shield overwrites the first, the
     * first one's buff is removed <b>before</b> the new shield is installed, and it must not clear a shield it
     * never gave. A drained shield ({@code 0}) is not touched either - there is nothing to take off.
     *
     * @param installer the buff that is expiring
     * @return whether this call took the shield off
     */
    public boolean removeShieldFrom(AbstractBuff installer) {
        return shieldState.removeFrom(installer);
    }

    /**
     * Applies damage to this entity, reducing current HP.
     * If HP drops to zero or below, the entity is marked dead.
     *
     * <p><b>The shield is drained first</b>: damage is absorbed by {@link ShieldState} first, and only
     * what is left after the shield is emptied is deducted from HP. So "you cannot die while shielded" holds
     * automatically; the absorbed amount is recorded by {@link ShieldState}.
     *
     * @param damage the amount of damage to take
     * @return {@code true} if the entity died from this damage
     */
    public boolean takeDamage(double damage) {
        return takeDamage(damage, true);
    }

    /**
     * The same, with the death decision left to the caller ({@code mayDie = false}).
     *
     * <p><b>Why it exists.</b> "受到致命攻击时不会陷入无法战斗状态,而是回复..." needs the target to reach 0 HP and be asked about it
     * BEFORE it is dead -- a unit at full HP cannot be healed, so announcing the moment before the damage would make the answer a
     * no-op (the wrong attempt at this feature). With this overload {@code Battle.applyDamage} can drop the target to
     * 0, fire {@link com.laosun.aluminium.enums.TriggerEvent#LETHAL_DAMAGE}, and then call {@link #perish()} only if nothing
     * answered -- which is exactly "不会陷入无法战斗状态" (it does not enter the unable-to-fight state).
     */
    public boolean takeDamage(double damage, boolean mayDie) {
        return health.takeDamage(damage, mayDie, shieldState);
    }

    /**
     * Marks this combatant as <b>defeated without being hurt</b>, i.e. it leaves the fight while
     * keeping its HP.
     *
     * <p><b>What this is for.</b> A summon leaves with its master, and it did not take damage on the way
     * out. So {@link HealthState} deliberately leaves HP alone: "it left" and "it was beaten to 0 HP" are two
     * different facts, and content that later asks "how hurt is it" must not be told the second one. The
     * pinned consequence is that a summon which perishes keeps its HP, asserted by
     * {@code SummonTest.theMasterFallingTakesItsSummonWithIt}.
     *
     * <p>Note: <b>No kill reward is paid, but not for the reason it first looks like.</b> This method fires no
     * events, and neither does {@link #takeDamage(double)} - the {@code HpLoss} / {@code Kill} events are
     * emitted by {@code Battle.applyDamage}, the pipeline's single settlement entry point. So the thing
     * that actually keeps a vanishing minion from paying out "每消灭 1 敌 +5 能量" (+5 energy per enemy killed) is that the callers of
     * this method run <b>outside that entry point</b> (the orphan sweep in
     * {@code Battle.removeDeadCombatants}). A future caller that wants a death to pay out must go through
     * {@code Battle.applyDamage} and settle real damage; calling this instead is the way to say "it left,
     * nobody killed it".
     *
     * <p>What the callers must still do, because this method cannot: take the unit out of the roster
     * ({@code Battle.enemies}) and off the action bar. {@link #isDeath()} alone does not remove anybody -
     * see {@code Battle.removeDeadCombatants}, the one place both happen together.
     *
     * <p>Idempotent; a combatant that is already dead stays dead (and keeps the HP it had).
     */
    public void perish() {
        health.perish();
    }

    /**
     * Spends HP as a COST: "消耗等同于刃生命上限 30% 的生命值" (spends HP equal to 30% of Mortenax Blade (千冶-刃)'s Max HP).
     *
     * <p>Note: Deliberately NOT {@code takeDamage}: a price, not an attack -- no shield absorbs it, no {@code HP_LOST} fires
     * (the texts list them side by side: "受到伤害<b>或</b>消耗生命值"), and it never kills.
     * The floor is the documents' own: "若当前生命值不足...当前生命值降低至 1 点" (if HP is insufficient, HP is lowered to 1).
     *
     * @return the HP actually spent
     */
    public double consumeHp(double amount) {
        return health.consume(amount);
    }

    /**
     * Restores HP to this entity, capped at max HP.
     * Has no effect on dead entities.
     *
     * @param amount the amount to heal
     */
    public void heal(double amount) {
        health.heal(amount, getMaxHp());
    }

    /**
     * Whether this entity has an energy bar ({@code maxEnergy > 0}).
     *
     * @return {@code true} if this entity has an energy bar
     */
    public boolean hasEnergyBar() {
        return energy.hasBar();
    }

    /**
     * Whether the energy bar is full (the ultimate can be cast). Always {@code false} for characters with
     * no energy bar.
     *
     * @return {@code true} if the energy bar is full
     */
    public boolean isEnergyFull() {
        return energy.isFull();
    }

    /**
     * Credit one energy gain (the only entry point for energy growth).
     *
     * <p>Formula (HSR.md §3.3): {@code final energy gained = base energy gained  x  (1 + energy regeneration
     * rate%)}; when {@link EnergyGain#affectedByEfficiency()} is {@code false} the efficiency bonus does
     * not apply.
     *
     * @param gain the description of one energy gain
     * @return the actually credited amount (after being truncated by the cap, not the theoretical gain)
     */
    public double gainEnergy(EnergyGain gain) {
        if (gain == null || !hasEnergyBar()) {
            return 0;
        }
        // A non-positive base is not a gain. Note: This guard deliberately keeps its original `<= 0` shape: it
        // is NOT where NaN is refused -- `NaN <= 0` is false, so a NaN amount walks past it. That is caught
        // one step down at the product, which is written the way it is for exactly that reason. (Measured
        // while mutating: rewriting this line as `!(amount > 0)` changes no test's outcome, so it would have
        // been a redundant condition carried for appearances.)
        if (gain.amount() <= 0) {
            return 0;
        }
        double efficiency = gain.affectedByEfficiency()
                ? 1 + getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()
                : 1;
        double proposed = gain.amount() * efficiency;
        // Same reasoning for the efficiency attribute (a NaN there poisons the product), plus a NEGATIVE
        // efficiency -- `ENERGY_REGENERATION_RATE <= -1` gives efficiency <= 0 -- which turned this "gain"
        // into a subtraction. A gain that takes energy away is indistinguishable from a bug, so it is
        // refused rather than clamped to something plausible.
        if (!(proposed > 0)) {
            return 0;
        }
        // `maxEnergy - currentEnergy` is negative when the bar already sits above its cap (the public
        // setCurrentEnergy is a raw write, the same "unprotected" style as Resource.setValue), and
        // `Math.min(negative, gain)` then LOWERED the energy by the whole over-cap amount. Take 0 instead:
        // capping an over-cap value is a separate operation, not something a gain should do on the way past.
        return energy.add(proposed);
    }

    /**
     * Convenience entry point: credit by base value (goes through energy regeneration efficiency).
     *
     * @param amount base energy
     * @return the actually credited amount
     */
    public double gainEnergy(double amount) {
        return gainEnergy(EnergyGain.normal(amount));
    }

    @Override
    public void beforeMove(Battle battle) {
        beforeMove.run();
    }

    @Override
    public void afterMove(Battle battle) {
        afterMove.run();
    }

    @Override
    public void onBattleStart(Battle battle) {
        // Firing limits are battle state, like `invulnerable`: a second battle must not inherit the first
        // one's cooldowns. Without this, a `once_per_battle` rule would stay dead for the rest of the
        // JVM's life the moment one battle ended -- a mechanic that silently disappears.
        resetTriggerLimits();
        onBattleStart.run();
    }

    /*
     * Whether a limited rule may fire right now.
     *
     * <p>Unlimited rules are never recorded, so they always answer {@code true}: adding this vocabulary
     * cannot change any rule that does not use it.
     *
     * @param key     the rule's stable key ({@code CompiledRule.key()})
     * @param perTurn how many times the rule may fire in one of <b>this combatant's</b> turns
     *                ({@code 0} = no per-turn cap)
     * @return {@code false} while the rule is on cooldown, has spent a once-per-battle limit, or has already
     *         fired {@code perTurn} times this turn
     */
    /**
     * Whether a rule limited to one firing per ATTACK may run now.
     *
     * <p>The sequence is {@code Battle.attackSequence()}: it counts finished attacks, so every instance of the attack in
     * progress shares one value and the next attack gets a new one. Kept beside the cooldown/per-turn state because
     * it is the same kind of fact (how often a rule has run) and is cleared with it.
     */
    public boolean isAttackLimitReady(String key, int attackSequence, int cap) {
        return ruleLimits.isAttackReady(key, attackSequence, cap);
    }

    /**
     * Records one firing of a rule inside the attack identified by {@code attackSequence}.
     */
    public void recordAttackUse(String key, int attackSequence) {
        ruleLimits.recordAttack(key, attackSequence);
    }

    public boolean isTriggerReady(String key, int perTurn) {
        return triggerCooldowns.isReady(key, perTurn, ruleLimits.hasSpentOnce(key));
    }

    /**
     * Records that a rule fired, which starts its limit.
     *
     * @param key           the rule's stable key
     * @param cooldownTurns the owner's turns before it may fire again ({@code 0} = no cooldown)
     * @param oncePerBattle {@code true} = it may never fire again in this battle
     * @param perTurn       how many times it may fire in one of the owner's turns ({@code 0} = no cap); the
     *                      use is counted here, which is what makes {@code per_turn} work at all
     */
    public void startTriggerCooldown(String key, int cooldownTurns, boolean oncePerBattle, int perTurn) {
        triggerCooldowns.record(key, cooldownTurns, perTurn, oncePerBattle);
        if (oncePerBattle) {
            ruleLimits.spendOnce(key);
        }
    }

    /*
     * Counts one of <b>this combatant's</b> turns off every cooldown, and clears every per-turn counter.
     *
     * <p>Called by {@code Battle.beforeMove} for the unit whose turn is beginning, just before the
     * {@code TURN_START} rules fire - so {@code cooldown: 1} means "at most once per own turn", and a
     * rule that fires on that turn's own event is not immediately blocked again. Note: The per-turn counters are
     * cleared in the same breath, and for the same reason: "每回合可触发 N 次" counts <b>my</b> turns, so a rule
     * reacting to other people's actions gets its N back when my own turn comes round.
     */
    /**
     * Clears the firing limits of ONE rule ("施放终结技后重置该效果触发次数";
     * readers 1305, 120, 1403). Note: Deliberately not {@link #resetTriggerLimits()}: that clears EVERY rule of
     * this combatant, and ULT_CAST carries unrelated rules too.
     */
    public void resetTriggerLimit(String key) {
        triggerCooldowns.reset(key);
        ruleLimits.reset(key);
    }

    public void tickTriggerCooldowns() {
        triggerCooldowns.tick();
    }

    /**
     * Clears every firing limit, so that a battle starts with all rules ready.
     */
    public void resetTriggerLimits() {
        triggerCooldowns.clear();
        ruleLimits.clear();
        ruleAmendments.clearForNewBattle();
        ruleChances.clear();
        skillBonuses.clearLevels();
    }

    /**
     * The slot of the last Elation-damage skill this combatant finished casting, or {@code 0} before the first one.
     */
    public int getLastElationSkillId() {
        return lastElationSkillId;
    }

    /**
     * Records the Elation-damage skill that has just finished, for the next cast to compare against.
     *
     * @param slot the skill slot (the same number {@code from_skill_id} reports)
     */
    public void setLastElationSkillId(int slot) {
        lastElationSkillId = slot;
    }

    /**
     * The effect index the pick filed under {@code memory} landed on last time, or {@code -1} when nothing is filed yet
     * (the first pick of the battle, which is uniform).
     *
     * @param memory the name the rule states in {@code one_of_no_repeat}
     */
    public int lastPick(String memory) {
        return ruleChances.lastPick(memory);
    }

    /**
     * Files this pick, so the next one under the same name cannot repeat it.
     *
     * <p>Called by the interpreter after the roll, and only for a rule that states a memory. Note: It is written even
     * when the pick was <b>forced</b> (a pool of two after the exclusion), because the next firing must still differ
     * from it.
     *
     * @param memory      the name the rule states in {@code one_of_no_repeat}
     * @param effectIndex the effect this pick landed on
     */
    public void rememberPick(String memory, int effectIndex) {
        ruleChances.rememberPick(memory, effectIndex);
    }

    /**
     * The probability the rule named {@code ruleId} has <b>right now</b>: the rule's own initial chance, or what its
     * decays have left it at (see {@link RuleChanceState}).
     *
     * <p>Read on every firing instead of once, which is the whole point: a rule that never states
     * {@code chance_decay} has no entry here and answers its initial {@code chance} forever - measured, adding this
     * reader changed no shipped rule's behaviour.
     *
     * @param ruleId  the rule's {@code id}
     * @param initial the rule's own {@code chance} ({@code 1.0} = always)
     */
    public double currentRuleChance(String ruleId, double initial) {
        return ruleChances.currentChance(ruleId, initial);
    }

    /**
     * Files the decay of a rule that has just triggered: its probability becomes {@code decay x} what it was.
     *
     * <p>Called by the interpreter <b>after</b> the rule has really run, which is what "成功触发后" means - a roll that
     * failed, or a firing a limit or a condition turned away, leaves the probability where it was.
     *
     * @param ruleId  the rule's {@code id}
     * @param initial the rule's own {@code chance} (the value the first decay starts from)
     * @param decay   the factor stated by {@code chance_decay}
     */
    public void decayRuleChance(String ruleId, double initial, double decay) {
        ruleChances.decay(ruleId, initial, decay);
    }

    /**
     * Puts a decaying probability back to the rule's initial value: "将触发…的固定概率重置为初始值".
     *
     * <p>Note: A removal, not a write of the initial number - an absent entry IS "the initial value", and storing it
     * would make a rule whose {@code chance} is later amended disagree with itself.
     *
     * @param ruleId the rule's {@code id}
     */
    public void resetRuleChance(String ruleId) {
        ruleChances.resetChance(ruleId);
    }

    /*
     * Raises one of this combatant's skill slots by {@code delta} for this battle.
     *
     * <p>Called by the {@code RAISE_SKILL_LEVEL} op, whose load-time validation already checked that the slot is a
     * real {@code SkillType} and that the amount is a positive whole number.
     *
     * @param slot  which skill (the same spelling the other ops use for {@code "skill"})
     * @param delta how many levels (a positive whole number)
     */
    /**
     * Adds {@code delta} damage segments to a data slot for the rest of the battle. A NEGATIVE delta takes them back, which is how a rule states a duration.
     */
    public void raiseSkillHits(int slot, int delta) {
        skillBonuses.raiseHits(slot, delta);
    }

    /**
     * The extra segments a data slot has been granted, or {@code 0}.
     */
    public int skillHitBonus(int slot) {
        return skillBonuses.hitBonus(slot);
    }

    public void raiseSkillLevel(SkillType slot, int delta) {
        skillBonuses.raiseLevel(slot, delta);
    }

    /**
     * Raises one of a <b>memosprite</b>'s own skills by {@code delta}, addressed by its <b>data slot</b>.
     *
     * <p>The memosprite half of {@link #raiseSkillLevel}: 「忆灵技等级+1」 names a data slot (1 / 3 / 5 …), not a
     * {@link SkillType}, so it needs the int-keyed map. Called through {@code RAISE_SKILL_LEVEL} with
     * {@code memosprite: true}, and read back by {@link #skillLevel} - the same one resolver, so a raised memosprite
     * slot moves its damage and every {@code cast_skill_param:} / {@code summon_param:} read together.
     *
     * @param dataSlot the memosprite skill's data slot ({@code > 0}); ignored otherwise
     * @param delta    how many levels to add (negative takes them away)
     */
    public void raiseMemospriteSkillLevel(int dataSlot, int delta) {
        skillBonuses.raiseMemospriteLevel(dataSlot, delta);
    }

    /**
     * How many levels this unit's <b>memosprite</b> skills were raised by, for one data slot.
     *
     * <p>Reader: {@link #skillLevel}, when it resolves a skill that belongs to a {@code Summon} - the raise is filed on
     * the <b>summoner</b> (「忆灵技等级+1」 is the character's Eidolon (星魂)/trace, and the memosprite may not be on the field
     * when a {@code BATTLE_START} rule runs), so a summon has to ask its master. A unit with no raises answers 0.
     */
    public int memospriteSkillLevelBonus(int dataSlot) {
        return skillBonuses.memospriteLevel(dataSlot);
    }

    /**
     * <b>The</b> level a skill is read at: its own level plus this battle's raises.
     *
     * <p>Note: <b>One resolver on purpose.</b> Three call sites read a parameter row from a level ({@code SkillExecutor}
     * twice - damaging skills and generated effect tables - and {@code TriggerInterpreter.multiplierOf} for a
     * rule-driven {@code DAMAGE}), and they must never disagree: a skill whose damage came from level 11 while its
     * generated effect read level 10 would be a number nobody could explain from the file.
     *
     * @param skill the skill instance (its {@code getLevel()} is the base from the character file)
     * @return the level to index the parameter table with
     */
    public int skillLevel(Skill skill) {
        if (skill == null) {
            return 1;
        }
        // Note: Which slot this skill is cannot be read off the skill: `getSkillSlot()` is the data's int index and
        // `getData().getSkillType()` is a String (the data's spelling, e.g. "BPSkill"). The first version of this
        // line passed the int into `Map<SkillType, Integer>.getOrDefault` - which compiles (the int boxes to Object)
        // and always misses - so the raise was filed and never read, with every test still green on the map's own
        // getter. The character's own map is the honest answer: the raise is keyed the way the skills are stored.
        //
        // Note: A MEMOSPRITE's skills are stored by data slot (`Summon.skillsByDataSlot`), so its raises are keyed that
        // way too (`raiseMemospriteSkillLevel`). Before that map existed, a summon always fell into the "no bonus" arm
        // below and its level was whatever the spec declared - which is why 「忆灵技等级+1」 had no spelling at all.
        int level;
        if (this instanceof Character character) {
            SkillType slot = null;
            for (var entry : character.getSkills().entrySet()) {
                if (entry.getValue() == skill) {
                    slot = entry.getKey();
                    break;
                }
            }
            level = skill.getLevel() + skillBonuses.level(slot);
        } else {
            Integer dataSlot = dataSlotOf(skill);
            int bonus = dataSlot == null ? 0 : skillBonuses.memospriteLevel(dataSlot);
            // The raise is filed on the SUMMONER (see `raiseMemospriteSkillLevel`): a memosprite's Eidolon (星魂)/trace raise
            // is a fact about its master's kit, and the memosprite does not exist yet when a BATTLE_START rule runs.
            if (dataSlot != null && this instanceof Summon summon
                    && summon.getMaster() != null) {
                bonus += summon.getMaster().memospriteSkillLevelBonus(dataSlot);
            }
            level = skill.getLevel() + bonus;
        }
        return Math.min(level, tableRows(skill));
    }

    /**
     * The data slot one of <b>this</b> unit's own skills is stored under, or {@code null} when that question has no
     * answer (a character's skills are keyed by {@link SkillType}, a monster's by nothing).
     */
    private Integer dataSlotOf(Skill skill) {
        if (this instanceof Summon summon) {
            for (var entry : summon.skillsByDataSlot().entrySet()) {
                if (entry.getValue() == skill) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * How many parameter rows this skill's data actually has - i.e. its <b>maximum level</b>.
     *
     * <p>Note: Why the resolver clamps. Raising a slot past its own table is not exotic: the data caps a basic attack at
     * level 10 while an Eidolon grants "普攻等级+1", so a file whose level convention already sits the slot at 10 and
     * then applies that Eidolon asks for row 11 of a 10-row table. Both readers used to answer that by <b>returning
     * silently</b> - `SkillExecutor`'s damaging path and its generated-effect path - so the skill dealt no damage at
     * all with no error anywhere (measured: 1503's enhanced basic is a flat 0 at Eidolon 6, and the same shape of
     * raise ships on 1103 / 1104 / 1109 / 1207 and more). The table's row count IS the documented cap ("最多不超过
     * 10级" is the row count, not a separate field), so the honest reading is "the level cannot exceed what the table
     * has", and every caller then finds a row.
     */
    private static int tableRows(Skill skill) {
        if (skill.getData() == null || skill.getData().getSkills() == null
                || skill.getData().getSkills().isEmpty()) {
            return Integer.MAX_VALUE;      // no table to clamp against (fixtures, enemies with bare skills)
        }
        return skill.getData().getSkills().size();
    }

    /**
     * How many levels this battle has raised the given slot by ({@code 0} = none).
     */
    public int skillLevelBonus(SkillType slot) {
        return skillBonuses.level(slot);
    }

    /**
     * Raises the per-turn limit of the rule named {@code ruleId} by {@code delta} for this battle.
     *
     * <p>Called by the {@code MODIFY_RULE} op, whose validation already checked that the named rule exists in the same
     * file and really states a {@code per_turn}.
     *
     * @param ruleId the target rule's {@code id}
     * @param delta  how many extra firings per turn (a positive whole number)
     */
    public void addRulePerTurnBonus(String ruleId, int delta) {
        ruleAmendments.addPerTurn(ruleId, delta);
    }

    /**
     * Raises the base chance of the rule named {@code ruleId} by {@code delta} for this battle.
     *
     * @param ruleId the target rule's {@code id}
     * @param delta  the extra probability, as a fraction of 1 ({@code 0.15} = "提高15%")
     */
    public void addRuleBaseChanceBonus(String ruleId, double delta) {
        ruleAmendments.addBaseChance(ruleId, delta);
    }

    /**
     * How many extra firings per turn the rule named {@code ruleId} has this battle ({@code 0} = none).
     *
     * @param ruleId the rule's {@code id} ({@code ""} for an unnamed rule, which can never be amended)
     * @return the amendment, or {@code 0}
     */
    public int rulePerTurnBonus(String ruleId) {
        return ruleAmendments.perTurn(ruleId);
    }

    /*
     * How much extra base chance the rule named {@code ruleId} has this battle ({@code 0} = none).
     *
     * @param ruleId the rule's {@code id}
     * @return the amendment, or {@code 0}
     */
    /**
     * Adds {@code delta} to every {@code percent} read of the named rule's effects.
     */
    public void amendRuleEffectPercent(String ruleId, double delta) {
        ruleAmendments.addEffectPercent(ruleId, delta);
    }

    /**
     * Adds {@code delta} to the cooldown of the rule named {@code ruleId} for this battle.
     *
     * <p>Called by the {@code MODIFY_RULE} op, whose load-time check already made sure the named rule states a
     * {@code cooldown} and that the result does not go below zero.
     *
     * @param ruleId the target rule's {@code id}
     * @param delta  how many of the owner's turns to add (negative = "comes back sooner")
     */
    public void addRuleCooldownDelta(String ruleId, int delta) {
        ruleAmendments.addCooldownDelta(ruleId, delta);
    }

    /**
     * How much the cooldown of the rule named {@code ruleId} is amended this battle ({@code 0} = none).
     *
     * <p>Read where the cooldown is recorded after a firing, so the amendment is applied to the number the rule
     * states rather than replacing it.
     *
     * @param ruleId the rule's own {@code id} ({@code ""} for an unnamed rule, which can never be amended)
     * @return the amendment, or {@code 0}
     */
    public int ruleCooldownDelta(String ruleId) {
        return ruleAmendments.cooldownDelta(ruleId);
    }

    /**
     * Adds {@code delta} to every {@code turns} read of the named rule's effects.
     */
    public void amendRuleEffectTurns(String ruleId, int delta) {
        ruleAmendments.addEffectTurns(ruleId, delta);
    }

    /**
     * Adds {@code delta} layers to the stack cap of every {@code max_stacks} read of the named rule's effects.
     */
    public void amendRuleEffectMaxStacks(String ruleId, int delta) {
        ruleAmendments.addEffectMaxStacks(ruleId, delta);
    }

    /**
     * The cap amendment for a rule, or {@code null} when nothing raised it.
     */
    public Integer ruleEffectMaxStacksBonus(String ruleId) {
        return ruleAmendments.effectMaxStacks(ruleId);
    }

    /**
     * The value amendment for a rule, or {@code null} when nothing raised it.
     */
    public Double ruleEffectPercentBonus(String ruleId) {
        return ruleAmendments.effectPercent(ruleId);
    }

    /**
     * The duration amendment for a rule, or {@code null} when nothing raised it.
     */
    public Integer ruleEffectTurnsBonus(String ruleId) {
        return ruleAmendments.effectTurns(ruleId);
    }

    public double ruleBaseChanceBonus(String ruleId) {
        return ruleAmendments.baseChance(ruleId);
    }

    /**
     * Damage-settlement hook, fired for both sides before the zones are multiplied.
     *
     * <p>The default relays to {@link BuffManager#onDamage(Battle, Damage)}, so buffs can inject
     * vulnerability / reduction / weakness. Subclasses that override it (character
     * talents, boss mechanics) <b>must call {@code super.onDamage(battle, damage)}</b>, otherwise their
     * own buffs stop working.
     */
    @Override
    public void onDamage(Battle battle, Damage damage) {
        buffManager.onDamage(battle, damage);
    }

    /**
     * Attack-level hook, broadcast to every ally once an attack is fully settled.
     *
     * <p>The default relays to {@link BuffManager#afterAttack(Battle, CanHit, CanHit, List, double)},
     * so buffs like Robin (知更鸟)'s [协奏] / Tribbie (缇宝)'s zone can spawn additional damage / true damage off
     * someone else's attack. Subclasses that override it <b>must call {@code super}</b>.
     */
    @Override
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        buffManager.afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage);
    }

    // ==================================================================
    // events: all of them forward through the same path as DamageEvent/AttackEvent
    // (BuffManager then iterates over the buffs). When overriding these methods you MUST call super,
    // otherwise the buffs on this entity will not receive the event.
    // ==================================================================

    /**
     * Skill cast. Fired for non-damaging skills too - it is the trigger source for
     * healing / shielding / pure-buff skills.
     */
    @Override
    public void onSkillCast(Battle battle, CanHit user, Skill skill,
                            List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
        buffManager.onSkillCast(battle, user, skill, hitTargets, targets);
    }

    /**
     * Energy credited. {@code actuallyAdded} is the actual credited value (after being truncated
     * by the cap).
     */
    @Override
    public void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
        buffManager.onEnergyGain(battle, target, actuallyAdded);
    }

    /**
     * HP loss. {@code amount} is the HP actually lost (excluding what the shield absorbed).
     */
    @Override
    public void onHpLoss(Battle battle, CanHit target, double before, double after,
                         CanHit source, double amount) {
        buffManager.onHpLoss(battle, target, before, after, source, amount);
    }

    /**
     * Healing. {@code actuallyHealed} is the actual amount restored (0 at full HP, in which case
     * the event is not fired).
     */
    @Override
    public void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
        buffManager.onHeal(battle, healer, target, actuallyHealed);
    }

    /**
     * Kill. Same definition as kill energy gain: it does not look at {@code countsAsAttack}
     * (an additional-damage last hit counts too).
     */
    @Override
    public void onKill(Battle battle, CanHit attacker, CanHit victim) {
        buffManager.onKill(battle, attacker, victim);
    }

    /**
     * Weakness break. Fired only once, at "the instant toughness is emptied".
     */
    @Override
    public void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
        buffManager.onBreak(battle, attacker, target, element);
    }

    /**
     * Skill point credited. Only our own units receive it (skill points are our team's resource).
     */
    @Override
    public void onSkillPointGained(Battle battle, int amount) {
        buffManager.onSkillPointGained(battle, amount);
    }

    /**
     * Skill point spent. Note: It is not fired when skill points are insufficient and the action
     * does not go through.
     */
    @Override
    public void onSkillPointSpent(Battle battle, int amount) {
        buffManager.onSkillPointSpent(battle, amount);
    }
}
