package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Abstract base for all entities that can participate in combat.
 *
 * <p>Holds the entity's name, faction ({@link Camp}), and an array of
 * {@link DoubleValue} combat attributes indexed by {@link AttributeType#ordinal()}.
 * Subclasses include {@link Character}, {@link Enemy}, and {@link Summon}.
 */
@Getter
@ToString
public abstract class CanHit {
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
     * Whether this entity has been defeated.
     */
    private final boolean death = false;

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

    /**
     * Replaces the {@link DoubleValue} at the given attribute index.
     *
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        attributes[attributeType.ordinal()] = value;
    }
}
