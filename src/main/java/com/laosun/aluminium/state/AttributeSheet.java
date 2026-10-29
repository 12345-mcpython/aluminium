package com.laosun.aluminium.state;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.DoubleValue;

/**
 * The combat attribute array, indexed by {@link AttributeType#ordinal()}.
 *
 * <p><b>Why this is separate.</b> The sheet is configuration-like data with one deep-copy rule
 * (buffs mutate the {@link DoubleValue} objects in place), and max HP is derived from it. Keeping the
 * array behind one object makes the copy rule and the indexing one place instead of a bare array on
 * the combatant.
 */
public final class AttributeSheet {

    private final DoubleValue[] values;

    /**
     * @param values the pre-computed array
     */
    public AttributeSheet(DoubleValue[] values) {
        this.values = values;
    }

    /**
     * Deep-copies a sheet: every {@link DoubleValue} is cloned, null slots stay null.
     *
     * @param other the source
     * @return the copy
     */
    public static AttributeSheet copyOf(AttributeSheet other) {
        DoubleValue[] cloned = new DoubleValue[other.values.length];
        for (int i = 0; i < cloned.length; i++) {
            DoubleValue source = other.values[i];
            cloned[i] = source == null ? null : source.clone();
        }
        return new AttributeSheet(cloned);
    }

    /**
     * @param type the attribute
     * @return its value object, or {@code null}
     */
    public DoubleValue get(AttributeType type) {
        return values[type.ordinal()];
    }

    /**
     * @param type  the attribute
     * @param value the value object
     */
    public void set(AttributeType type, DoubleValue value) {
        values[type.ordinal()] = value;
    }

    /**
     * @return max HP
     */
    public double maxHp() {
        return values[AttributeType.HEALTH.ordinal()].get();
    }
}
