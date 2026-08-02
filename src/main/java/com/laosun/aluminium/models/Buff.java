package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A timed status effect applied to a combatant.
 *
 * <p>A buff/debuff can:
 * <ul>
 *   <li>Modify attributes while active: each {@link ModifierEntry} is pushed
 *   onto the owner's {@link DoubleValue} on apply and popped on expiry.</li>
 *   <li>Deal damage over time (DoT) at the start of the owner's turns
 *   (HSR.md §5.4: 持续伤害遵循"先上先结算").</li>
 *   <li>Control the owner ({@link ControlType}), e.g. freeze skips actions.</li>
 *   <li>Advance/delay the owner's action while active.</li>
 * </ul>
 *
 * <p>Durations count down at the start of the owner's turn (HSR.md §3.3).
 */
@Getter
@Setter
public class Buff {
    /** Whether this status is beneficial or harmful. */
    public enum Category { BUFF, DEBUFF }

    /** Control states that restrict the owner's actions (HSR.md §3.2). */
    public enum ControlType {
        /** Freeze: skip the next action, bonus damage on recovery. */
        FROZEN,
        /** Imprisonment / entanglement: cannot act, action delayed on recovery. */
        IMPRISONED,
        /** Domination: acts against its own camp. */
        DOMINATED
    }

    /** A stat modifier entry: pushes a modifier onto an attribute while active. */
    public record ModifierEntry(AttributeType attribute, DoubleValue.Modifier modifier) {
    }

    private final String name;
    private final Category category;
    private final CanHit source;
    private final CanHit owner;

    /** Remaining duration in the owner's turns. -1 means permanent (no expiry). */
    private int duration;

    /** Stat modifiers pushed onto the owner while this buff is active. */
    private final List<ModifierEntry> modifiers = new ArrayList<>();

    /** DoT damage applied per tick; 0 means no DoT. */
    private double dotDamage = 0;
    /** DoT element (decides whether the DoT is boosted by element damage). */
    private Element dotElement = Element.PHYSICAL;
    /** DoT lasts this many ticks. */
    private int dotDuration = 0;

    /** HoT: HP restored to the owner each turn while active. */
    private double healPerTurn = 0;

    /** Control state applied by this buff. */
    private ControlType control = null;

    /** Action delay ratio applied on expiration (e.g. imprisonment 0.3). */
    private double delayOnExpire = 0;
    /** Action advance ratio applied on expiration (e.g. freeze recovery). */
    private double advanceOnExpire = 0;
    /** Extra damage dealt on expiration (freeze/entanglement bonus damage). */
    private double damageOnExpire = 0;
    /** Effect hit rate / resistance that was used to decide this debuff's application. */
    private boolean applied = false;

    public Buff(String name, Category category, CanHit source, CanHit owner, int duration) {
        this.name = name;
        this.category = category;
        this.source = source;
        this.owner = owner;
        this.duration = duration;
    }

    public Buff stat(AttributeType attribute, DoubleValue.Modifier modifier) {
        modifiers.add(new ModifierEntry(attribute, modifier));
        return this;
    }

    public Buff dot(double damage, Element element, int duration) {
        this.dotDamage = damage;
        this.dotElement = element;
        this.dotDuration = duration;
        return this;
    }

    /**
     * Sets a heal-over-time: restores {@code perTurn} HP at the owner's turn start.
     */
    public Buff heal(double perTurn) {
        this.healPerTurn = perTurn;
        return this;
    }

    public Buff control(ControlType control) {
        this.control = control;
        return this;
    }

    public Buff delayOnExpire(double ratio) {
        this.delayOnExpire = ratio;
        return this;
    }

    public Buff advanceOnExpire(double ratio) {
        this.advanceOnExpire = ratio;
        return this;
    }

    public Buff damageOnExpire(double damage) {
        this.damageOnExpire = damage;
        return this;
    }

    /**
     * Pushes all stat modifiers onto the owner's attributes.
     */
    public void apply() {
        for (ModifierEntry entry : modifiers) {
            DoubleValue value = owner.getAttribute(entry.attribute());
            if (value != null) {
                value.addModifier(entry.modifier());
            }
        }
        applied = true;
    }

    /**
     * Removes all stat modifiers from the owner's attributes.
     */
    public void remove() {
        for (ModifierEntry entry : modifiers) {
            DoubleValue value = owner.getAttribute(entry.attribute());
            if (value != null) {
                value.removeModifier(entry.modifier());
            }
        }
    }

    /**
     * Ticks at the start of the owner's turn (HSR.md §5.4: DoTs settle in order).
     *
     * @param battle the battle context (for applying damage)
     * @return {@code true} if this buff expired during the tick and must be removed
     */
    public boolean tick(Battle battle) {
        if (dotDamage > 0 && dotDuration > 0) {
            battle.applyDotDamage(owner, this);
            dotDuration--;
        }
        if (healPerTurn > 0) {
            owner.heal(healPerTurn);
        }
        if (duration > 0) {
            duration--;
        }
        return duration == 0;
    }

    /**
     * Handles the control-state behavior when this buff expires while the
     * owner is under control (e.g. freeze bonus damage, imprisonment delay).
     */
    public void onExpire(Battle battle) {
        if (damageOnExpire > 0 && !owner.isDeath()) {
            battle.applyTrueDamage(owner, damageOnExpire, name + " expire");
        }
        if (delayOnExpire > 0 && !owner.isDeath()) {
            battle.delayByPercent(owner, delayOnExpire);
        }
        if (advanceOnExpire > 0 && !owner.isDeath()) {
            battle.advanceByPercent(owner, advanceOnExpire);
        }
    }

    /**
     * A DoT instance attached to the owner, ticking independently.
     */
    @Getter
    public static class Dot {
        private final String name;
        private final CanHit source;
        private final CanHit owner;
        private final double damage;
        private final Element element;
        private int duration;

        public Dot(String name, CanHit source, CanHit owner, double damage, Element element, int duration) {
            this.name = name;
            this.source = source;
            this.owner = owner;
            this.damage = damage;
            this.element = element;
            this.duration = duration;
        }

        public boolean tick(Battle battle) {
            battle.applyDotDamage(owner, this);
            duration--;
            return duration <= 0;
        }

        /**
         * Extends or shortens the remaining duration.
         */
        public void setDuration(int duration) {
            this.duration = duration;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Buff buff)) return false;
        return Objects.equals(name, buff.name) && category == buff.category && Objects.equals(source, buff.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, category, source);
    }

    @Override
    public String toString() {
        return name + "(" + duration + "t)";
    }
}
