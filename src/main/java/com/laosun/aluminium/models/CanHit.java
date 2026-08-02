package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.event.BattleEvent;
import com.laosun.aluminium.models.event.MoveEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Abstract base for all entities that can participate in combat.
 *
 * <p>Holds the entity's name, faction ({@link Camp}), and an array of
 * {@link DoubleValue} combat attributes indexed by {@link AttributeType#ordinal()}.
 * Subclasses include {@link Character}, {@link Enemy}, and {@link Summon}.
 *
 * <p>Runtime combat state: current HP, energy (HSR.md §3.3), aggro (HSR.md §3.4),
 * active {@link Buff}s and DoTs (HSR.md §3.5, §5.4).
 */
@Getter
@ToString
public abstract class CanHit implements BattleEvent, MoveEvent {
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
     * Character/enemy level, used by the defense region (HSR.md §2.4).
     */
    @Setter
    private int level = 80;

    /**
     * This entity's damage element.
     */
    @Setter
    private Element element = Element.PHYSICAL;

    /**
     * Base aggro value (存护 150, 毁灭 125, 其他 100, HSR.md §3.4).
     */
    @Setter
    private double aggro = 100;

    /**
     * Current energy. Consumed by ultimate (HSR.md §3.3).
     */
    private double energy = 0;
    /**
     * Maximum energy. Null-safe: 0 means this entity has no energy bar.
     */
    @Setter
    private double maxEnergy = 0;

    /**
     * Active buffs/debuffs (HSR.md §3.5).
     */
    private final List<Buff> buffs = new ArrayList<>();

    /**
     * Active damage-over-time effects, settled at the owner's turn start
     * in application order (HSR.md §5.4).
     */
    private final List<Buff.Dot> dots = new ArrayList<>();

    /**
     * The control state currently locking this entity (freeze etc.).
     */
    @Setter
    private Buff.ControlType controlState = null;

    /**
     * Current shield amount. Damage is absorbed by the shield first (HSR.md §4.2).
     */
    private double shield = 0;

    /**
     * Turns remaining for the current shield; 0 means the shield never expires.
     */
    @Setter
    private int shieldTurns = 0;

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
    }

    /**
     * Copy construction
     *
     * @param other what you want to copy
     */
    public CanHit(CanHit other) {
        this.name = other.name;
        this.camp = other.camp;
        this.skills = new EnumMap<>(other.skills);
        this.attributes = other.attributes.clone();
        this.currentHp = other.currentHp;
        this.death = other.death;
        this.level = other.level;
        this.element = other.element;
        this.aggro = other.aggro;
        this.energy = other.energy;
        this.maxEnergy = other.maxEnergy;
        this.controlState = other.controlState;
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
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        attributes[attributeType.ordinal()] = value;
    }

    /**
     * Returns the maximum hit points from the HEALTH attribute.
     */
    public double getMaxHp() {
        return attributes[AttributeType.HEALTH.ordinal()].get();
    }

    /**
     * Applies damage to this entity. Shield absorbs damage first (HSR.md §4.2);
     * the remainder reduces current HP. If HP drops to zero or below, the entity
     * is marked dead.
     *
     * @param damage the amount of damage to take
     * @return {@code true} if the entity died from this damage
     */
    public boolean takeDamage(double damage) {
        if (death || damage <= 0) {
            return false;
        }
        if (shield > 0) {
            double absorbed = Math.min(shield, damage);
            shield -= absorbed;
            damage -= absorbed;
        }
        if (damage <= 0) {
            return false;
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
     * Sets the shield value. HSR shields do not stack: the stronger one wins.
     *
     * @param amount the new shield amount
     */
    public void setShield(double amount) {
        shield = Math.max(shield, amount);
    }

    /**
     * Sets the shield value with a duration in the owner's turns.
     *
     * @param amount the new shield amount
     * @param turns  how many of the owner's turns the shield lasts
     */
    public void setShield(double amount, int turns) {
        shield = Math.max(shield, amount);
        shieldTurns = Math.max(shieldTurns, turns);
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
     * Revives this entity from defeat with the given HP (used by eidolon
     * death-prevention effects, e.g. Arlan 绝处反击).
     *
     * @param hp the HP to revive with
     */
    public void revive(double hp) {
        death = false;
        currentHp = Math.min(Math.max(0, hp), getMaxHp());
    }

    /**
     * The current HP ratio between 0 and 1.
     */
    public double getHpPercent() {
        return currentHp / getMaxHp();
    }

    // ─── Energy (HSR.md §3.3) ──────────────────────────────────────────

    /**
     * Gains energy, scaled by the ENERGY_REGENERATION_RATE attribute:
     * {@code final = base × (1 + energyRegenerationRate)}.
     *
     * @param base the raw energy gain
     * @return the amount of energy actually gained
     */
    public double gainEnergy(double base) {
        if (maxEnergy <= 0 || death) {
            return 0;
        }
        double rate = getAttribute(AttributeType.ENERGY_REGENERATION_RATE) != null
                ? getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get() : 0;
        double gained = base * (1 + rate);
        energy = Math.min(maxEnergy, energy + gained);
        return gained;
    }

    /**
     * Spends all energy (used when casting an ultimate).
     *
     * @return the amount of energy that was consumed
     */
    public double consumeEnergy() {
        double consumed = energy;
        energy = 0;
        return consumed;
    }

    // ─── Buffs & DoTs (HSR.md §3.5, §5.4) ──────────────────────────────

    /**
     * Applies a buff to this entity. Stat modifiers take effect immediately.
     */
    public void applyBuff(Buff buff) {
        buffs.removeIf(b -> b.equals(buff));
        buffs.add(buff);
        buff.apply();
    }

    /**
     * Applies a damage-over-time effect to this entity.
     */
    public void applyDot(Buff.Dot dot) {
        dots.add(dot);
    }

    /**
     * Ticks all buffs and DoTs at the start of this entity's turn.
     * Expired effects are removed (HSR.md §5.4: 先上先结算).
     *
     * @param battle the battle context
     */
    public void tickStatuses(Battle battle) {
        List<Buff> expired = new ArrayList<>();
        for (Buff buff : buffs) {
            if (buff.tick(battle)) {
                expired.add(buff);
            }
        }
        for (Buff buff : expired) {
            buff.onExpire(battle);
            buff.remove();
            buffs.remove(buff);
        }

        List<Buff.Dot> finished = new ArrayList<>();
        for (Buff.Dot dot : dots) {
            if (dot.tick(battle)) {
                finished.add(dot);
            }
        }
        dots.removeAll(finished);

        // Shield durations tick at the owner's turn start.
        if (shieldTurns > 0) {
            shieldTurns--;
            if (shieldTurns <= 0) {
                shield = 0;
            }
        }
    }

    /**
     * Removes a buff (and its stat modifiers) by name.
     */
    public void removeBuff(String name) {
        buffs.removeIf(buff -> {
            if (buff.getName().equals(name)) {
                buff.remove();
                return true;
            }
            return false;
        });
    }

    /**
     * Removes all buffs (and their stat modifiers).
     */
    public void clearBuffs() {
        for (Buff buff : buffs) {
            buff.remove();
        }
        buffs.clear();
    }

    /**
     * Whether an active buff/debuff with the given name exists.
     */
    public boolean hasBuffNamed(String name) {
        return buffs.stream().anyMatch(b -> b.getName().equals(name));
    }

    /**
     * Whether this entity suffers a DoT of the given element.
     */
    public boolean hasDotOfElement(Element element) {
        return dots.stream().anyMatch(d -> d.getElement() == element && d.getDuration() > 0);
    }

    /**
     * Whether this entity carries any debuff.
     */
    public boolean hasDebuff() {
        return buffs.stream().anyMatch(b -> b.getCategory() == Buff.Category.DEBUFF);
    }

    /**
     * Whether this entity is currently under a control effect that skips actions.
     */
    public boolean isControlled() {
        return controlState != null;
    }

    public boolean isDead() {
        return death;
    }

    public boolean isDeath() {
        return death;
    }
}
