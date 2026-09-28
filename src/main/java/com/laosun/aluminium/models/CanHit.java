package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.event.*;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.models.skill.Skill;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final DoubleValue[] attributes;
    /**
     * Faction alignment.
     */
    private final Camp camp;

    /**
     * Combat level (P1-4): feeds the defence zone now, break base (P4) and enemy
     * stat scaling (P2-4) later. Defaults to 80 so existing code keeps working.
     */
    @Setter
    private int level = 80;

    /**
     * This unit's aggro weight, or {@code 0} for "not stated — use the regular tier" ({@link Battle#aggroOf}).
     *
     * <p>A {@link Character}'s own aggro comes from the character data (with the path as its fallback); this field
     * is what a unit with <b>no</b> character data can state instead. Its first user is a memosprite: the documents
     * give servants a line of their own — 「ServantID 11413 · 仇恨: 125」 — and before this {@code aggroOf} answered the
     * 100 fallback for every summon, so an enemy picked its target as if the memosprite were an ordinary character
     * while the game makes it 25% more attractive.
     */
    @Setter
    private int aggro;

    @Setter
    private EnumMap<SkillType, Skill> skills;
    /**
     * Current hit points.
     */
    private double currentHp;
    /**
     * Whether this entity has been defeated.
     */
    private boolean death = false;

    /**
     * Temporarily cannot take damage: boss phase transition / invulnerability window
     * (转阶段无敌、锁血演出).
     *
     * <p>Orthogonal to {@link #death}: an invulnerable target is still a legal target
     * (an AOE still "hits" it, for 0 damage) but {@code Battle.applyDamage} settles
     * nothing on it — this is what keeps a transitioning boss from being 鞭尸.
     */
    @Setter
    private boolean invulnerable = false;

    /**
     * Current energy (P3). Characters with no energy bar are always 0.
     */
    @Setter
    private double currentEnergy = 0;

    /**
     * Energy cap (P3). {@code 0} = no energy bar (1407 遐蝶 is like this): {@link #hasEnergyBar()} is
     * {@code false}, no energy gain is ever credited, and there is no such thing as "casting the ultimate
     * at full energy".
     *
     * <p>The real caps come from {@code max_energy} in {@code character_data.json} (of the 93 characters
     * only 遐蝶 is null). Many are way off the common tier: 飞霄/白厄 12, 黄泉 9, 昔涟 24,
     * 流萤/云璃/长夜月 240, 银枝/爻光 180, 阿格莱雅 350, 绯英 480 — see the P3-0 D table in ROADMAP.
     */
    @Setter
    private double maxEnergy = 0;

    /**
     * Energy gain rules (P3). Regular characters use {@link StandardEnergyProvider}; special characters
     * each implement this interface themselves.
     */
    @Setter
    private EnergyProvider energyProvider = new StandardEnergyProvider();

    private final BuffManager buffManager;

    /**
     * The stack resources this combatant owns (P8-8).
     *
     * <p>Never {@code null}, like {@code triggerTable}: a character with no stacks simply has an
     * empty manager, so call sites never null-check. This is what lets "stack instead of an energy
     * bar" characters (Acheron's 【残梦】, Feixiao's 【飞黄】, Cyrene's 【追忆】…) work without a class
     * of their own — the resource is data, the trigger table fills it, and the energy provider reads
     * it to decide whether the ultimate is available.
     */
    @Getter
    private final ResourceManager resources;

    /**
     * Per-rule firing limits ("cooldown" / "once per battle"), keyed by {@code CompiledRule.key()}.
     *
     * <p><b>Why the counters live on the combatant and not on the rule.</b> A trigger table is compiled
     * once and <b>cached per cid</b>, and a relic set's rules are merged into the same table instance for
     * every character wearing it — so a mutable field on a rule would be shared by every wearer, in every
     * battle, for the life of the JVM. That is the leak this project keeps refusing to ship (see the
     * static caches registered as N-1). A combatant owns its own counters, and
     * {@link #onBattleStart(Battle)} clears them.
     *
     * <p>Deliberately without a getter: the maps are mutable, and they are reachable only through the
     * four methods below, so nothing can tick or clear somebody else's limits by accident — the same
     * reasoning that made {@code Queue}'s exposed live heap a registered defect (L-5).
     */
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> triggerCooldowns = new HashMap<>();
    /**
     * How many times each per-turn-limited rule has fired <b>in the current turn</b> ({@code per_turn}).
     *
     * <p>Kept next to the cooldowns because it is the same kind of fact (battle state, per combatant, keyed by
     * the rule) and because both are cleared at the same moment: the start of this combatant's own turn. A
     * counter stored on the rule would be shared by every wearer of a relic set and by every battle in the JVM
     * — the reason the cooldowns live here in the first place.
     */
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> triggerTurnUses = new HashMap<>();
    /**
     * The rules that have used up a {@code once_per_battle} limit; they never fire again this battle.
     */
    @Getter(AccessLevel.NONE)
    private final Set<String> triggerSpentOnce = new HashSet<>();

    /**
     * Per-battle <b>amendments to another rule's own numbers</b>, keyed by that rule's {@code id}
     * (「天赋的反击效果每回合可触发的次数增加1次」 / 「冻结敌方目标的基础概率提高15%」).
     *
     * <p><b>Why the numbers live here and not on the rule.</b> Exactly like the firing limits above: a trigger table
     * is compiled once and cached per cid, and a relic set's rules are merged into the same instance for every wearer,
     * so writing a raised {@code per_turn} onto the rule would leak it into every battle in the JVM. The amendment is
     * a fact about <b>this combatant in this battle</b> ("I have 星魂 4 active"), so it is stored next to the counters
     * and reset with them.
     *
     * <p>⚠ Two fields, not one: the two sentences raise two different things, and a single "delta" would make it
     * impossible to say which. A rule can carry both (nothing in the corpus does).
     */
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> rulePerTurnBonus = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private final Map<String, Double> ruleBaseChanceBonus = new HashMap<>();
    /**
     * 「终结技的持续时间额外增加 1 回合」/「天赋的伤害提高效果额外提高 10%」: amendments to a named rule's own
     * <b>effect values</b> (2026-09-28).
     *
     * <p>⚠ Kept separate from {@link #rulePerTurnBonus} / {@link #ruleBaseChanceBonus} because those two change how
     * <i>often</i> a rule runs, while these change what it <i>does</i> — and unlike a second rule with a bigger number
     * (which would <b>replace</b> the first, since same-kind modifiers refresh rather than stack), these raise the one
     * that is already there.
     */
    private final Map<String, Double> ruleEffectPercentBonus = new HashMap<>();
    /** The duration half of the same idea: 「持续时间额外增加 N 回合」. */
    private final Map<String, Integer> ruleEffectTurnsBonus = new HashMap<>();

    /**
     * Per-battle <b>skill level raises</b> (M-32), keyed by slot: 「战技等级+1」「终结技等级+1」 (1001 星魂 3/5, and the
     * same sentence in most characters' kits).
     *
     * <p><b>Why the raise lives here and not on the {@code Skill}.</b> The same reasoning as the rule amendments
     * above: a skill instance belongs to a {@code Character} that a stage can put into more than one battle, so
     * writing a raised level onto it would stack once per battle and never come off. The raise is a fact about
     * <b>this combatant in this battle</b> ("my 星魂 3 is active"), so it sits next to the firing counters and is
     * cleared with them.
     *
     * <p>The <b>base</b> level is the other half, and it is character data, not battle state: it comes from the
     * file's optional {@code "skill_levels"} (see {@code CharacterFactory}), because the document's figure is quoted
     * at a particular level.
     */
    @Getter(AccessLevel.NONE)
    private final Map<SkillType, Integer> skillLevelBonus = new HashMap<>();

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
        this.attributes = attributes;
        this.currentHp = attributes[AttributeType.HEALTH.ordinal()].get();
        this.skills = new EnumMap<>(SkillType.class);
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
        this.skills = new EnumMap<>(other.skills);
        // ⚠ Deep-clone the attribute sheet (H-6). `other.attributes.clone()` clones the ARRAY and
        // nothing else, so every DoubleValue inside stayed the same object as the original's -- and
        // buffs mutate those objects in place (BoostDamageBuff.applyEffect calls addModifier on the
        // target's value), so a buff on one combatant showed up on the other's panel. DoubleValue.clone()
        // is a true deep copy; null slots (the percentage placeholders) stay null, exactly like the
        // original array.
        DoubleValue[] clonedAttributes = new DoubleValue[other.attributes.length];
        for (int i = 0; i < clonedAttributes.length; i++) {
            DoubleValue source = other.attributes[i];
            clonedAttributes[i] = source == null ? null : source.clone();
        }
        this.attributes = clonedAttributes;
        // don't need to clone
        this.currentHp = attributes[AttributeType.HEALTH.ordinal()].get();
        this.death = false;
        // Battle state, like `death` / `currentEnergy` / `resources`: a copy is a fresh participant, not
        // a snapshot of a fight in progress, so it starts vulnerable (invulnerable is what a boss turns on
        // to lock its HP bar mid-phase). Named explicitly because the old review read the omission as a
        // forgotten copy -- it is a decision, and this is where the decision lives.
        this.invulnerable = false;
        this.buffManager = new BuffManager(this);
        // Resources are per-battle state, like currentEnergy: the copy starts empty rather than
        // inheriting the original's stacks.
        this.resources = new ResourceManager(this);
        // Configuration-like fields must be copied along too; currentEnergy belongs to a new battle
        // instance and deliberately starts at 0
        this.maxEnergy = other.maxEnergy;
        this.currentEnergy = 0;
        this.energyProvider = other.energyProvider;
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
        return attributes[attributeType.ordinal()];
    }

    public void setSkill(SkillType skillType, Skill skill) {
        skills.put(skillType, skill);
    }

    /**
     * Replaces the {@link DoubleValue} at the given attribute index.
     *
     * <p>If what is replaced is {@code SPEED} and the value really changed, this notifies
     * {@link #notifySpeedChanged()} — this is the **only trigger point** for "speed change → reorder the
     * action bar" (P7 fix E2), so go through here to change speed (or call
     * {@code notifySpeedChanged()} after modifying the {@code DoubleValue}),
     * do **not** quietly change the speed attribute anywhere else.
     *
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        DoubleValue previous = attributes[attributeType.ordinal()];
        attributes[attributeType.ordinal()] = value;
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
        return attributes[AttributeType.HEALTH.ordinal()].get();
    }

    /**
     * Callback fired when the speed attribute changes (P7 fix E2).
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

    /**
     * Current shield value (P6-3). {@code 0} = no shield.
     *
     * <p>The shield is **drained before HP** ({@link #takeDamage(double)}), and it **does not stack**:
     * a new shield has {@code Battle.grantShield} overwrite the old value outright, with no addition.
     *
     * <p>⚠ Read it through {@link #getShield()}; write it through {@link #setShield(double)} (a raw write that
     * installs nothing) or {@link #installShield(double, AbstractBuff)} (a timed shield that takes itself off
     * again — see {@link com.laosun.aluminium.models.buff.ShieldBuff}).
     */
    private double shield = 0;

    /**
     * Which buff installed the shield currently up, or {@code null} when nobody owns it (a raw grant, or none).
     *
     * <p>A new shield overwrites the old one, and the old shield's buff expires later — at which moment
     * "clear the shield" would be wrong: the shield standing there is somebody else's. The value alone cannot
     * tell the two apart (two shields of the same size look identical), so the installer is remembered.
     *
     * @see #installShield(double, AbstractBuff)
     * @see #removeShieldFrom(AbstractBuff)
     */
    private AbstractBuff shieldInstaller;

    /**
     * Who provided the shield currently up, and <b>which rule</b> created it, or {@code null} / {@code ""}.
     *
     * <p><b>Why the shield has to remember this.</b> Two sentences ask about the shield's origin, not its existence:
     * 「在**战技提供的**护盾保护下的我方目标…」 (1001 三月七 星魂 6) and 「我方目标持有**装备者提供的**护盾时…」 (遗器 128's
     * 4-piece). Neither can be answered from the number — 三月七's Skill shield and her 星魂 2 shield are both hers — so
     * the pair is recorded when the shield is installed and read by the {@code has_shield from_rule …} condition.
     *
     * <p>⚠ A raw {@link #setShield(double)} says "somebody set the number directly", so it clears both: a shield with
     * no stated origin must not answer "yes" to a question about one.
     */
    private CanHit shieldProvider;
    private String shieldRuleId = "";

    /**
     * A raw shield write, which <b>clears the ownership</b>: "somebody set the number directly" is not a timed
     * shield, so no buff may take it off again.
     */
    public void setShield(double value) {
        this.shield = value;
        this.shieldInstaller = null;
        this.shieldProvider = null;
        this.shieldRuleId = "";
    }

    /**
     * Records a shield together with where it came from (the raw-grant path, whose shield no buff owns).
     *
     * @param value    the shield amount
     * @param provider who granted it ({@code null} = unknown)
     * @param ruleId   the id of the rule that granted it ({@code ""} = unnamed)
     */
    public void setShield(double value, CanHit provider, String ruleId) {
        this.shield = value;
        this.shieldInstaller = null;
        this.shieldProvider = provider;
        this.shieldRuleId = ruleId == null ? "" : ruleId;
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
        this.shield = value;
        this.shieldInstaller = installer;
        this.shieldProvider = installer == null ? null : installer.getSource();
        this.shieldRuleId = installer == null || installer.getRuleId() == null ? "" : installer.getRuleId();
    }

    /**
     * Who provided the shield currently up, or {@code null} (a raw number, no shield, or an anonymous giver).
     */
    public CanHit getShieldProvider() {
        return shieldProvider;
    }

    /**
     * The {@code id} of the rule that created the shield currently up ({@code ""} when it names none).
     */
    public String getShieldRuleId() {
        return shieldRuleId;
    }

    /**
     * Takes the shield off <b>only if</b> {@code installer} is the buff that put it up.
     *
     * <p>This is the whole reason the installer is remembered: when a second shield overwrites the first, the
     * first one's buff is removed <b>before</b> the new shield is installed, and it must not clear a shield it
     * never gave. A drained shield ({@code 0}) is not touched either — there is nothing to take off.
     *
     * @param installer the buff that is expiring
     * @return whether this call took the shield off
     */
    public boolean removeShieldFrom(AbstractBuff installer) {
        if (shieldInstaller != installer) {
            return false;
        }
        this.shield = 0;
        this.shieldInstaller = null;
        this.shieldProvider = null;
        this.shieldRuleId = "";
        return true;
    }

    /**
     * How much of the last {@link #takeDamage(double)} was blocked by the shield (P6-3).
     *
     * <p>Why it exists: the damage absorbed by the shield **is also damage dealt by this hit** — the return
     * value of {@code Battle.applyDamage} has to count "the part that went into the shield", otherwise
     * "hitting a shielded target" would show as dealing 0 damage (kill energy gain / the total damage of
     * the attack event would both be distorted). Every {@code takeDamage} overwrites it.
     */
    @Setter
    private double lastShieldAbsorbed = 0;

    /**
     * Applies damage to this entity, reducing current HP.
     * If HP drops to zero or below, the entity is marked dead.
     *
     * <p><b>The shield is drained first (P6-3)</b>: damage is absorbed by {@link #shield} first, and only
     * what is left after the shield is emptied is deducted from HP. So "you cannot die while shielded" holds
     * automatically; the absorbed amount is recorded in {@link #lastShieldAbsorbed}.
     *
     * @param damage the amount of damage to take
     * @return {@code true} if the entity died from this damage
     */
    public boolean takeDamage(double damage) {
        lastShieldAbsorbed = 0;                      // clear it at the start of every settlement so a stale value cannot be read
        if (death || damage <= 0) {
            return false;
        }
        if (shield > 0) {
            double absorbed = Math.min(shield, damage);
            shield -= absorbed;
            damage -= absorbed;
            // ⚠ This assignment MUST come **before any return**: when the shield eats all the damage the
            //   code below returns early, and missing this line would let the caller read a stale value
            //   from the previous hit (measured once: the returned damage came out doubled).
            lastShieldAbsorbed = absorbed;
            if (damage <= 0) {
                return false;                        // all eaten by the shield: HP untouched, and certainly not dead
            }
        }
        currentHp -= damage;
        if (currentHp <= 0) {
            currentHp = 0;
            death = true;
            return true;
        }
        return false;
    }

    /**
     * Marks this combatant as <b>defeated without being hurt</b> (P9-4), i.e. it leaves the fight while
     * keeping its HP.
     *
     * <p><b>What this is for.</b> A summon leaves with its master, and it did not take damage on the way
     * out. So {@link #currentHp} is deliberately left alone: "it left" and "it was beaten to 0 HP" are two
     * different facts, and content that later asks "how hurt is it" must not be told the second one. The
     * pinned consequence is that a summon which perishes keeps its HP, asserted by
     * {@code SummonTest.theMasterFallingTakesItsSummonWithIt}.
     *
     * <p>⚠ <b>No kill reward is paid, but not for the reason it first looks like.</b> This method fires no
     * events, and neither does {@link #takeDamage(double)} — the {@code HpLoss} / {@code Kill} events are
     * emitted by {@code Battle.applyDamage}, the pipeline's single settlement entry point. So the thing
     * that actually keeps a vanishing minion from paying out 「每消灭 1 敌 +5 能量」 is that the callers of
     * this method run <b>outside that entry point</b> (the orphan sweep in
     * {@code Battle.removeDeadCombatants}). A future caller that wants a death to pay out must go through
     * {@code Battle.applyDamage} and settle real damage; calling this instead is the way to say "it left,
     * nobody killed it".
     *
     * <p>What the callers must still do, because this method cannot: take the unit out of the roster
     * ({@code Battle.enemies}) and off the action bar. {@link #isDeath()} alone does not remove anybody —
     * see {@code Battle.removeDeadCombatants}, the one place both happen together.
     *
     * <p>Idempotent; a combatant that is already dead stays dead (and keeps the HP it had).
     */
    public void perish() {
        death = true;
    }

    /**
     * Restores HP to this entity, capped at max HP.
     * Has no effect on dead entities.
     *
     * @param amount the amount to heal
     */
    public void heal(double amount) {        if (death || amount <= 0) {
            return;
        }
        currentHp = Math.min(currentHp + amount, getMaxHp());
    }

    /**
     * Whether this entity has an energy bar ({@code maxEnergy > 0}).
     *
     * @return {@code true} if this entity has an energy bar
     */
    public boolean hasEnergyBar() {
        return maxEnergy > 0;
    }

    /**
     * Whether the energy bar is full (the ultimate can be cast). Always {@code false} for characters with
     * no energy bar.
     *
     * @return {@code true} if the energy bar is full
     */
    public boolean isEnergyFull() {
        return hasEnergyBar() && currentEnergy >= maxEnergy;
    }

    /**
     * Credit one energy gain (the only entry point for energy growth in P3).
     *
     * <p>Formula (HSR.md §3.3): {@code final energy gained = base energy gained × (1 + energy regeneration
     * rate%)}; when {@link EnergyGain#affectedByEfficiency()} is {@code false} the efficiency bonus does
     * not apply.
     *
     * @param gain the description of one energy gain
     * @return the **actually credited amount** (after being truncated by the cap, not the theoretical gain)
     */
    public double gainEnergy(EnergyGain gain) {
        if (gain == null || !hasEnergyBar()) {
            return 0;
        }
        // A non-positive base is not a gain. ⚠ This guard deliberately keeps its original `<= 0` shape: it
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
        double added = Math.min(Math.max(0, maxEnergy - currentEnergy), proposed);
        currentEnergy += added;
        return added;
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

    /**
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
    public boolean isTriggerReady(String key, int perTurn) {
        if (triggerSpentOnce.contains(key) || triggerCooldowns.getOrDefault(key, 0) > 0) {
            return false;
        }
        return perTurn <= 0 || triggerTurnUses.getOrDefault(key, 0) < perTurn;
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
        if (perTurn > 0) {
            triggerTurnUses.merge(key, 1, Integer::sum);
        }
        if (oncePerBattle) {
            triggerSpentOnce.add(key);
            return;
        }
        if (cooldownTurns > 0) {
            triggerCooldowns.put(key, cooldownTurns);
        }
    }

    /**
     * Counts one of <b>this combatant's</b> turns off every cooldown, and clears every per-turn counter.
     *
     * <p>Called by {@code Battle.beforeMove} for the unit whose turn is beginning, just before the
     * {@code TURN_START} rules fire — so {@code cooldown: 1} means "at most once per own turn", and a
     * rule that fires on that turn's own event is not immediately blocked again. ⚠ The per-turn counters are
     * cleared in the same breath, and for the same reason: 「每回合可触发 N 次」 counts <b>my</b> turns, so a rule
     * reacting to other people's actions gets its N back when my own turn comes round.
     */
    public void tickTriggerCooldowns() {
        triggerTurnUses.clear();
        if (triggerCooldowns.isEmpty()) {
            return;
        }
        triggerCooldowns.replaceAll((key, turns) -> turns - 1);
        triggerCooldowns.values().removeIf(turns -> turns <= 0);
    }

    /**
     * Clears every firing limit, so that a battle starts with all rules ready.
     */
    public void resetTriggerLimits() {
        triggerCooldowns.clear();
        triggerSpentOnce.clear();
        triggerTurnUses.clear();
        rulePerTurnBonus.clear();
        ruleBaseChanceBonus.clear();
        ruleEffectPercentBonus.clear();
        ruleEffectTurnsBonus.clear();
        skillLevelBonus.clear();
    }

    /**
     * Raises one of this combatant's skill slots by {@code delta} for this battle (M-32).
     *
     * <p>Called by the {@code RAISE_SKILL_LEVEL} op, whose load-time validation already checked that the slot is a
     * real {@code SkillType} and that the amount is a positive whole number.
     *
     * @param slot  which skill (the same spelling the other ops use for {@code "skill"})
     * @param delta how many levels (a positive whole number)
     */
    public void raiseSkillLevel(SkillType slot, int delta) {
        if (slot == null || delta == 0) {
            return;
        }
        skillLevelBonus.merge(slot, delta, Integer::sum);
    }

    /**
     * <b>The</b> level a skill is read at: its own level plus this battle's raises.
     *
     * <p>⚠ <b>One resolver on purpose.</b> Three call sites read a parameter row from a level ({@code SkillExecutor}
     * twice — damaging skills and generated effect tables — and {@code TriggerInterpreter.multiplierOf} for a
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
        // ⚠ Which slot this skill is cannot be read off the skill: `getSkillSlot()` is the data's **int** index and
        // `getData().getSkillType()` is a **String** (the data's spelling, e.g. "BPSkill"). The first version of this
        // line passed the int into `Map<SkillType, Integer>.getOrDefault` — which compiles (the int boxes to Object)
        // and always misses — so the raise was filed and never read, with every test still green on the map's own
        // getter. The character's own map is the honest answer: the raise is keyed the way the skills are stored.
        SkillType slot = null;
        if (this instanceof Character character) {
            for (var entry : character.getSkills().entrySet()) {
                if (entry.getValue() == skill) {
                    slot = entry.getKey();
                    break;
                }
            }
        }
        return skill.getLevel() + (slot == null ? 0 : skillLevelBonus.getOrDefault(slot, 0));
    }

    /**
     * How many levels this battle has raised the given slot by ({@code 0} = none).
     */
    public int skillLevelBonus(SkillType slot) {
        return slot == null ? 0 : skillLevelBonus.getOrDefault(slot, 0);
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
        if (ruleId == null || ruleId.isBlank()) {
            return;
        }
        rulePerTurnBonus.merge(ruleId.trim(), delta, Integer::sum);
    }

    /**
     * Raises the base chance of the rule named {@code ruleId} by {@code delta} for this battle.
     *
     * @param ruleId the target rule's {@code id}
     * @param delta  the extra probability, as a fraction of 1 ({@code 0.15} = 「提高15%」)
     */
    public void addRuleBaseChanceBonus(String ruleId, double delta) {
        if (ruleId == null || ruleId.isBlank()) {
            return;
        }
        ruleBaseChanceBonus.merge(ruleId.trim(), delta, Double::sum);
    }

    /**
     * How many extra firings per turn the rule named {@code ruleId} has this battle ({@code 0} = none).
     *
     * @param ruleId the rule's {@code id} ({@code ""} for an unnamed rule, which can never be amended)
     * @return the amendment, or {@code 0}
     */
    public int rulePerTurnBonus(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? 0 : rulePerTurnBonus.getOrDefault(ruleId.trim(), 0);
    }

    /**
     * How much extra base chance the rule named {@code ruleId} has this battle ({@code 0} = none).
     *
     * @param ruleId the rule's {@code id}
     * @return the amendment, or {@code 0}
     */
    /** Adds {@code delta} to every {@code percent} read of the named rule's effects. */
    public void amendRuleEffectPercent(String ruleId, double delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            ruleEffectPercentBonus.merge(ruleId.trim(), delta, Double::sum);
        }
    }

    /** Adds {@code delta} to every {@code turns} read of the named rule's effects. */
    public void amendRuleEffectTurns(String ruleId, int delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            ruleEffectTurnsBonus.merge(ruleId.trim(), delta, Integer::sum);
        }
    }

    /** The value amendment for a rule, or {@code null} when nothing raised it. */
    public Double ruleEffectPercentBonus(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? null : ruleEffectPercentBonus.get(ruleId.trim());
    }

    /** The duration amendment for a rule, or {@code null} when nothing raised it. */
    public Integer ruleEffectTurnsBonus(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? null : ruleEffectTurnsBonus.get(ruleId.trim());
    }

    public double ruleBaseChanceBonus(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? 0 : ruleBaseChanceBonus.getOrDefault(ruleId.trim(), 0.0);
    }

    /**
     * Damage-settlement hook (P1-7), fired for both sides before the zones are multiplied.
     *
     * <p>The default relays to {@link BuffManager#onDamage(Battle, Damage)}, so buffs can inject
     * vulnerability (易伤) / reduction (减伤) / weakness (虚弱). Subclasses that override it (character
     * talents, boss mechanics) <b>must call {@code super.onDamage(battle, damage)}</b>, otherwise their
     * own buffs stop working.
     */
    @Override
    public void onDamage(Battle battle, Damage damage) {
        buffManager.onDamage(battle, damage);
    }

    /**
     * Attack-level hook (P1-9), broadcast to every ally once an attack is fully settled.
     *
     * <p>The default relays to {@link BuffManager#afterAttack(Battle, CanHit, CanHit, List, double)},
     * so buffs like 知更鸟【协奏】/ 缇宝结界 can spawn additional damage (附加伤害) / true damage (真伤) off
     * someone else's attack. Subclasses that override it <b>must call {@code super}</b>.
     */
    @Override
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        buffManager.afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage);
    }

    // ==================================================================
    // P8-6 events: all of them forward through the same path as DamageEvent/AttackEvent
    // (BuffManager then iterates over the buffs). When overriding these methods you MUST call super,
    // otherwise the buffs on this entity will not receive the event.
    // ==================================================================

    /**
     * Skill cast (P8-6). **Fired for non-damaging skills too** — it is the trigger source for
     * healing / shielding / pure-buff skills.
     */
    @Override
    public void onSkillCast(Battle battle, CanHit user, Skill skill,
                            List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
        buffManager.onSkillCast(battle, user, skill, hitTargets, targets);
    }

    /**
     * Energy credited (P8-6). {@code actuallyAdded} is the **actual** credited value (after being truncated
     * by the cap).
     */
    @Override
    public void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
        buffManager.onEnergyGain(battle, target, actuallyAdded);
    }

    /**
     * HP loss (P8-6). {@code amount} is the **HP actually lost** (excluding what the shield absorbed).
     */
    @Override
    public void onHpLoss(Battle battle, CanHit target, double before, double after,
                         CanHit source, double amount) {
        buffManager.onHpLoss(battle, target, before, after, source, amount);
    }

    /**
     * Healing (P8-6). {@code actuallyHealed} is the **actual amount restored** (0 at full HP, in which case
     * the event is not fired).
     */
    @Override
    public void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
        buffManager.onHeal(battle, healer, target, actuallyHealed);
    }

    /**
     * Kill (P8-6). Same definition as kill energy gain: it does **not** look at {@code countsAsAttack}
     * (an additional-damage last hit counts too).
     */
    @Override
    public void onKill(Battle battle, CanHit attacker, CanHit victim) {
        buffManager.onKill(battle, attacker, victim);
    }

    /**
     * Weakness break (P8-6). Fired only once, at "the instant toughness is emptied".
     */
    @Override
    public void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
        buffManager.onBreak(battle, attacker, target, element);
    }

    /**
     * Skill point credited (P8-6). Only our own units receive it (skill points are our team's resource).
     */
    @Override
    public void onSkillPointGained(Battle battle, int amount) {
        buffManager.onSkillPointGained(battle, amount);
    }

    /**
     * Skill point spent (P8-6). ⚠ It is **not** fired when skill points are insufficient and the action
     * does not go through.
     */
    @Override
    public void onSkillPointSpent(Battle battle, int amount) {
        buffManager.onSkillPointSpent(battle, amount);
    }
}
