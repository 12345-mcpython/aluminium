package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
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
     * The rules that have used up a {@code once_per_battle} limit; they never fire again this battle.
     */
    @Getter(AccessLevel.NONE)
    private final Set<String> triggerSpentOnce = new HashSet<>();

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
     */
    @Setter
    private double shield = 0;

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
     * Restores HP to this entity, capped at max HP.
     * Has no effect on dead entities.
     *
     * @param amount the amount to heal
     */
    public void heal(double amount) {
        if (death || amount <= 0) {
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
     * @param key the rule's stable key ({@code CompiledRule.key()})
     * @return {@code false} while the rule is on cooldown or has spent a once-per-battle limit
     */
    public boolean isTriggerReady(String key) {
        return !triggerSpentOnce.contains(key) && triggerCooldowns.getOrDefault(key, 0) <= 0;
    }

    /**
     * Records that a rule fired, which starts its limit.
     *
     * @param key           the rule's stable key
     * @param cooldownTurns the owner's turns before it may fire again ({@code 0} = no cooldown)
     * @param oncePerBattle {@code true} = it may never fire again in this battle
     */
    public void startTriggerCooldown(String key, int cooldownTurns, boolean oncePerBattle) {
        if (oncePerBattle) {
            triggerSpentOnce.add(key);
            return;
        }
        if (cooldownTurns > 0) {
            triggerCooldowns.put(key, cooldownTurns);
        }
    }

    /**
     * Counts one of <b>this combatant's</b> turns off every cooldown.
     *
     * <p>Called by {@code Battle.beforeMove} for the unit whose turn is beginning, just before the
     * {@code TURN_START} rules fire — so {@code cooldown: 1} means "at most once per own turn", and a
     * rule that fires on that turn's own event is not immediately blocked again.
     */
    public void tickTriggerCooldowns() {
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
