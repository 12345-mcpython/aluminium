package com.laosun.aluminium.utils;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.DoubleValue;

import java.util.EnumMap;
import java.util.Map;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

/**
 * A fluent builder for accumulating combat attributes and computing final values.
 *
 * <p>The builder collects base values and modifiers (both additive and multiplicative
 * percentage bonuses, and pure flat bonuses) across multiple sources (base stats,
 * relics, weapons, skill points, extra promotions).
 *
 * <p>After all contributions are added, {@link #build()} produces an array of
 * {@link DoubleValue} indexed by {@link AttributeType#ordinal()}. During the build
 * phase, percentage-type attributes (HEALTH_PERCENT etc.) are redirected to their
 * corresponding base-type attribute's modifier list.
 *
 * <p><b>Usage pattern:</b>
 * <pre>{@code
 * AttributeBuilder atb = new AttributeBuilder();
 * atb.setBase(HEALTH, 1000)
 *    .addPercent(HEALTH_PERCENT, 0.2, ModifierSource.RELIC)
 *    .addPure(HEALTH, 500, ModifierSource.EXTRA);
 * DoubleValue[] result = atb.build();
 * double finalHealth = result[HEALTH.ordinal()].get();
 * }</pre>
 */
public class AttributeBuilder {
    private final Map<AttributeType, DoubleValue> attributeMap = new EnumMap<>(AttributeType.class);

    /**
     * Sets the base value for an attribute, replacing any previously set base.
     *
     * @param type  the target attribute
     * @param value the base value
     * @return this builder for chaining
     */
    public AttributeBuilder setBase(AttributeType type, double value) {
        getOrCreate(type).baseNoCompute(value);
        return this;
    }

    /**
     * Adds to the existing base value for an attribute.
     *
     * @param type  the target attribute
     * @param value the value to add to the current base
     * @return this builder for chaining
     */
    public AttributeBuilder addBase(AttributeType type, double value) {
        getOrCreate(type).addBaseNoCompute(value);
        return this;
    }

    /**
     * Adds a flat (pure) value modifier to the target attribute.
     *
     * <p>Percentage-type attributes (HEALTH_PERCENT etc.) are automatically
     * redirected to their corresponding base-type attribute.
     *
     * @param type   the attribute to modify
     * @param value  the flat value to add
     * @param source the source of this modifier (RELIC, WEAPON, etc.)
     * @return this builder for chaining
     */
    public AttributeBuilder addPure(AttributeType type, double value, DoubleValue.Modifier.ModifierSource source) {
        AttributeType targetType = PERCENT_TO_BASE.getOrDefault(type, type);
        getOrCreate(targetType).addModifierNoCompute(DoubleValue.Modifier.pure(value, source));
        return this;
    }

    /**
     * Adds an additive percentage modifier to the target attribute.
     *
     * <p>The percentage is added to the sum of all additive percentages, which
     * is then multiplied against the base value: {@code base * (1 + sum(pct))}.
     * Percentage-type attributes are redirected to their base-type counterparts.
     *
     * @param type    the attribute to modify
     * @param percent the percentage as a decimal (e.g. 0.18 = 18%)
     * @param source  the source of this modifier
     * @return this builder for chaining
     */
    public AttributeBuilder addPercent(AttributeType type, double percent, DoubleValue.Modifier.ModifierSource source) {
        AttributeType targetType = PERCENT_TO_BASE.getOrDefault(type, type);
        getOrCreate(targetType).addModifierNoCompute(DoubleValue.Modifier.addPercent(percent, source));
        return this;
    }

    /**
     * Adds a percentage-point modifier. Equivalent to {@link #addPure} and kept
     * for semantic clarity. Should NOT be used for HEALTH_PERCENT, ATTACK_PERCENT,
     * DEFENCE_PERCENT, or SPEED_PERCENT — those should use {@link #addPercent}.
     *
     * @param type    the attribute to modify
     * @param percent the value as a decimal
     * @param source  the source of this modifier
     * @return this builder for chaining
     */
    public AttributeBuilder addPercentPoint(AttributeType type, double percent, DoubleValue.Modifier.ModifierSource source) {
        return addPure(type, percent, source);
    }

    /**
     * Commits all pending modifier additions and produces the final attribute array.
     *
     * <p>Percentage-type attributes (HEALTH_PERCENT etc.) are set to {@code null}
     * in the result array since their values are folded into the base attributes.
     *
     * @return an array of computed {@link DoubleValue}, indexed by attribute ordinal
     */
    public DoubleValue[] build() {
        AttributeType[] types = AttributeType.values();
        DoubleValue[] result = new DoubleValue[types.length];
        for (AttributeType type : types) {
            if (PERCENT_TO_BASE.containsKey(type)) {
                result[type.ordinal()] = null;
            } else {
                DoubleValue dv = attributeMap.get(type);
                if (dv != null) {
                    dv.commit();
                    result[type.ordinal()] = dv;
                } else {
                    result[type.ordinal()] = DoubleValue.zero();
                }
            }
        }
        return result;
    }

    private DoubleValue getOrCreate(AttributeType type) {
        return attributeMap.computeIfAbsent(type, k -> DoubleValue.zero());
    }
}
