package com.laosun.aluminium.models;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A numeric attribute value supporting compound modifier stacking.
 *
 * <p>The final value is computed as:
 * <pre>{@code value = baseValue * (1 + sum(addPcts)) * product(1 + multiplyPcts) + sum(pureValues)}</pre>
 *
 * <p>Three modifier types are supported:
 * <ul>
 *   <li>{@link Modifier.ModifierType#ADD_PERCENT} — additive percentage: all add-percent
 *   values are summed before multiplying against the base.</li>
 *   <li>{@link Modifier.ModifierType#MULTIPLY_PERCENT} — multiplicative bonus: each
 *   multiply-percent is independently multiplied as {@code (1 + pct)}.</li>
 *   <li>{@link Modifier.ModifierType#PURE_VALUE} — flat value added after all
 *   multiplicative operations.</li>
 * </ul>
 *
 * <p>Modifiers can be filtered by their {@link Modifier.ModifierSource} for selective
 * removal or inspection. The class also supports no-compute bulk-add operations used
 * internally by {@link com.laosun.aluminium.utils.AttributeBuilder} for performance.
 */
public final class DoubleValue implements Cloneable {
    private double baseValue;
    private double value;

    private List<Modifier> multiplyPercentModifiers = new ArrayList<>();
    private List<Modifier> addPercentModifiers = new ArrayList<>();
    private List<Modifier> valueModifiers = new ArrayList<>();

    /**
     * Creates a new value with the given base and computes the result immediately.
     *
     * @param baseValue the initial base value
     */
    public DoubleValue(double baseValue) {
        this.baseValue = baseValue;
        compute();
    }

    /**
     * Replaces the base value and recomputes the final value.
     *
     * @param base the new base value
     * @return this instance for chaining
     */
    public DoubleValue base(double base) {
        baseValue = base;
        compute();
        return this;
    }

    /**
     * Adds to the base value and recomputes the final value.
     *
     * @param value the amount to add
     * @return this instance for chaining
     */
    public DoubleValue addBase(double value) {
        baseValue = baseValue + value;
        compute();
        return this;
    }

    /**
     * Replaces the base value without triggering recomputation.
     * Used internally by {@link com.laosun.aluminium.utils.AttributeBuilder}
     * for batch operations; call {@link #commit()} after all additions.
     *
     * @param base the new base value
     */
    public void baseNoCompute(double base) {
        baseValue = base;
    }

    /**
     * Adds to the base value without triggering recomputation.
     * Used internally for batch operations; call {@link #commit()} after all additions.
     *
     * @param value the amount to add
     */
    public void addBaseNoCompute(double value) {
        baseValue = baseValue + value;
    }

    /**
     * Adds a modifier without triggering recomputation.
     * Used internally for batch operations; call {@link #commit()} after all additions.
     *
     * @param modifier the modifier to add
     */
    public void addModifierNoCompute(Modifier modifier) {
        switch (modifier.modifierType) {
            case ADD_PERCENT -> addPercentModifiers.add(modifier);
            case MULTIPLY_PERCENT -> multiplyPercentModifiers.add(modifier);
            case PURE_VALUE -> valueModifiers.add(modifier);
        }
    }


    /**
     * Creates a zero-valued instance with no modifiers.
     *
     * @return a new {@code DoubleValue} with base 0
     */
    public static DoubleValue zero() {
        return new DoubleValue(0.0);
    }

    /**
     * Returns the current computed value.
     *
     * @return the final numeric value
     */
    public double get() {
        return value;
    }

    /**
     * Adds a modifier and recomputes the final value immediately.
     *
     * @param modifier the modifier to add
     */
    public void addModifier(Modifier modifier) {
        switch (modifier.modifierType) {
            case ADD_PERCENT -> addPercentModifiers.add(modifier);
            case MULTIPLY_PERCENT -> multiplyPercentModifiers.add(modifier);
            case PURE_VALUE -> valueModifiers.add(modifier);
        }
        compute();
    }

    /**
     * Removes a modifier from the appropriate list and recomputes if removed.
     *
     * @param modifier the modifier to remove
     */
    public void removeModifier(Modifier modifier) {
        boolean removed = addPercentModifiers.remove(modifier);
        removed |= valueModifiers.remove(modifier);
        removed |= multiplyPercentModifiers.remove(modifier);
        if (removed) compute();
    }

    /**
     * Returns all modifiers from the given source category.
     *
     * @param type the source type to filter by
     * @return a new list containing matching modifiers
     */
    public List<Modifier> filterBySource(Modifier.ModifierSource type) {
        List<Modifier> result = new ArrayList<>();
        for (Modifier m : multiplyPercentModifiers) {
            if (m.source == type) result.add(m);
        }
        for (Modifier m : addPercentModifiers) {
            if (m.source == type) result.add(m);
        }
        for (Modifier m : valueModifiers) {
            if (m.source == type) result.add(m);
        }
        return result;
    }

    /**
     * Removes all modifiers and recomputes the value (returns to base only).
     */
    public void clearModifiers() {
        addPercentModifiers.clear();
        multiplyPercentModifiers.clear();
        valueModifiers.clear();
        compute();
    }

    /**
     * Commits pending no-compute additions by running the computation formula.
     * Called once after batch additions from {@link com.laosun.aluminium.utils.AttributeBuilder}.
     */
    public void commit() {
        compute();
    }

    /**
     * Computes the final value from base and all modifiers.
     *
     * <p>Formula: {@code value = baseValue * (1 + sum(addPcts)) * product(1 + multiplyPcts) + sum(pureValues)}.
     */
    private void compute() {
        double percentModifiersTotal = 1;
        double multiplyPercentTotal = 1;
        double valueModifiersTotal = 0;
        for (Modifier percentModifier : addPercentModifiers) {
            percentModifiersTotal += percentModifier.value;
        }
        for (Modifier valueModifier : valueModifiers) {
            valueModifiersTotal += valueModifier.value;
        }
        for (Modifier multiplyPercentModifier : multiplyPercentModifiers) {
            multiplyPercentTotal *= (1 + multiplyPercentModifier.value);
        }

        value = baseValue * percentModifiersTotal * multiplyPercentTotal + valueModifiersTotal;
    }

    /**
     * Creates a deep copy including all modifiers.
     *
     * @return a fully independent clone
     */
    @SneakyThrows
    @Override
    public DoubleValue clone() {
        DoubleValue copy = (DoubleValue) super.clone();
        copy.addPercentModifiers = new ArrayList<>();
        copy.multiplyPercentModifiers = new ArrayList<>();
        copy.valueModifiers = new ArrayList<>();
        addPercentModifiers.forEach(m -> copy.addPercentModifiers.add(m.clone()));
        multiplyPercentModifiers.forEach(m -> copy.multiplyPercentModifiers.add(m.clone()));
        valueModifiers.forEach(m -> copy.valueModifiers.add(m.clone()));
        copy.compute();
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<DoubleValue: ");
        sb.append(String.format("%.8f", value));
        sb.append(" formula: ").append(String.format("%.5f", baseValue));
        if (!addPercentModifiers.isEmpty()) {
            sb.append(" * (1");
            for (Modifier modifier : addPercentModifiers) {
                sb.append(" + ");
                sb.append(String.format("%.5f", modifier.value));
            }
            sb.append(")");
        }
        if (!multiplyPercentModifiers.isEmpty()) {
            for (Modifier modifier : multiplyPercentModifiers) {
                sb.append(" * ");
                sb.append(String.format("%.5f", 1 + modifier.value));
            }
        }
        if (!valueModifiers.isEmpty()) {
            for (Modifier modifier : valueModifiers) {
                sb.append(" + ");
                sb.append(String.format("%.5f", modifier.value));
            }
        }
        sb.append(">");
        return sb.toString();
    }

    /**
     * An attribute modifier with a type, value, and source metadata.
     *
     * <p>Provides static factory methods for creating modifiers with either
     * raw decimal values (e.g. 0.18) or integer-percent values (e.g. 18).
     */
    @AllArgsConstructor
    @Getter
    public static final class Modifier implements Cloneable {
        /** How this modifier affects the final value. */
        private ModifierType modifierType;
        /** The modifier's numeric magnitude. */
        private double value;
        /** Which system produced this modifier. */
        private ModifierSource source;
        /** Optional role/source identifier for precise tracking. */
        private int sourceRoleId;

        // these need to call with number for example 18 represent to 18% boost

        /**
         * Creates an add-percent modifier from an integer percentage (e.g. 18 means 18%).
         */
        public static Modifier addPercentNumber(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue / 100.0, ModifierSource.UNKNOWN, 0);
        }

        /**
         * Creates a multiply-percent modifier from an integer percentage.
         */
        public static Modifier multiplyPercentNumber(double percentValue) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue / 100.0, ModifierSource.UNKNOWN, 0);
        }

        public static Modifier addPercentNumber(double percentValue, ModifierSource source) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue / 100.0, source, 0);
        }

        public static Modifier multiplyPercentNumber(double percentValue, ModifierSource source) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue / 100.0, source, 0);
        }

        public static Modifier addPercentNumber(double percentValue, ModifierSource source, int sourceRoleId) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue / 100.0, source, sourceRoleId);
        }

        public static Modifier multiplyPercentNumber(double percentValue, ModifierSource source, int sourceRoleId) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue / 100.0, source, sourceRoleId);
        }

        // these need to call with number for example 0.18 represent to 18% boost

        /**
         * Creates an add-percent modifier from a decimal value (e.g. 0.18 means 18%).
         */
        public static Modifier addPercent(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue, ModifierSource.UNKNOWN, 0);
        }

        /**
         * Creates a multiply-percent modifier from a decimal value.
         */
        public static Modifier multiplyPercent(double percentValue) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue, ModifierSource.UNKNOWN, 0);
        }

        /**
         * Creates a pure (flat) value modifier.
         */
        public static Modifier pure(double value) {
            return new Modifier(ModifierType.PURE_VALUE, value, ModifierSource.UNKNOWN, 0);
        }


        public static Modifier addPercent(double percentValue, ModifierSource source) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue, source, 0);
        }

        public static Modifier multiplyPercent(double percentValue, ModifierSource source) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue, source, 0);
        }

        public static Modifier pure(double value, ModifierSource source) {
            return new Modifier(ModifierType.PURE_VALUE, value, source, 0);
        }


        public static Modifier addPercent(double percentValue, ModifierSource source, int sourceRoleId) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue, source, sourceRoleId);
        }

        public static Modifier multiplyPercent(double percentValue, ModifierSource source, int sourceRoleId) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue, source, sourceRoleId);
        }

        public static Modifier pure(double value, ModifierSource source, int sourceRoleId) {
            return new Modifier(ModifierType.PURE_VALUE, value, source, sourceRoleId);
        }

        @SneakyThrows
        @Override
        public Modifier clone() {
            return (Modifier) super.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Modifier that)) return false;
            return Double.compare(that.value, value) == 0 &&
                    sourceRoleId == that.sourceRoleId &&
                    modifierType == that.modifierType &&
                    source == that.source;
        }

        @Override
        public int hashCode() {
            return Objects.hash(modifierType, value, source, sourceRoleId);
        }

        /** The mathematical operation a modifier performs. */
        public enum ModifierType {
            /** Additive percentage: all values are summed. */
            ADD_PERCENT,
            /** Multiplicative bonus: each applied independently as (1 + pct). */
            MULTIPLY_PERCENT,
            /** Flat value added after all percentage operations. */
            PURE_VALUE
        }

        /** Which game system produced this modifier. */
        public enum ModifierSource {
            UNKNOWN,
            /** Character base stats. */
            BASE,
            /** Equipped relic. */
            RELIC,
            /** Equipped weapon (light cone). */
            WEAPON,
            /** Skill point (trace) bonus. */
            SKILL_POINT,
            /** Extra basic promotion bonus. */
            EXTRA,
            /** Relic set bonus. */
            RELIC_SET,
            /** Active buff. */
            BUFF,
            /** Active debuff. */
            DEBUFF,
            /** Test/dummy source. */
            TEST
        }
    }
}
