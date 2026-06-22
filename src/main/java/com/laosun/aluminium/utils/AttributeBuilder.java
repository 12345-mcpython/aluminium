package com.laosun.aluminium.utils;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.DoubleValue;

import java.util.EnumMap;
import java.util.Map;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;


public class AttributeBuilder {
    private final Map<AttributeType, DoubleValue> attributeMap = new EnumMap<>(AttributeType.class);

    public AttributeBuilder setBase(AttributeType type, double value) {
        getOrCreate(type).baseNoCompute(value);
        return this;
    }

    public AttributeBuilder addBase(AttributeType type, double value) {
        getOrCreate(type).addBaseNoCompute(value);
        return this;
    }

    // HEALTH ATTACK PERCENT SPEED should call these
    public AttributeBuilder addPure(AttributeType type, double value, DoubleValue.Modifier.ModifierSource source) {
        AttributeType targetType = PERCENT_TO_BASE.getOrDefault(type, type);
        getOrCreate(targetType).addModifierNoCompute(DoubleValue.Modifier.pure(value, source));
        return this;
    }

    public AttributeBuilder addPercent(AttributeType type, double percent, DoubleValue.Modifier.ModifierSource source) {
        AttributeType targetType = PERCENT_TO_BASE.getOrDefault(type, type);
        getOrCreate(targetType).addModifierNoCompute(DoubleValue.Modifier.addPercent(percent, source));
        return this;
    }
    // end call

    // HEALTH ATTACK PERCENT SPEED shouldn't call this!
    public AttributeBuilder addPercentPoint(AttributeType type, double percent, DoubleValue.Modifier.ModifierSource source) {
        return addPure(type, percent, source);
    }

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