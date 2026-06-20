package com.laosun.aluminium.models;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class DoubleValue implements Cloneable {
    private double baseValue;
    private double value;

    private List<Modifier> multiplyPercentModifiers = new ArrayList<>();
    private List<Modifier> addPercentModifiers = new ArrayList<>();
    private List<Modifier> valueModifiers = new ArrayList<>();

    public DoubleValue(double baseValue) {
        this.baseValue = baseValue;
        compute();
    }

    // These can't undo!
    public DoubleValue base(double base) {
        baseValue = base;
        compute();
        return this;
    }


    public void addBase(double value) {
        baseValue = baseValue + value;
        compute();
    }
    // end undo


    public static DoubleValue zero() {
        return new DoubleValue(0.0);
    }

    public double get() {
        return value;
    }

    public void addModifier(Modifier modifier) {
        if (modifier.modifierType == Modifier.ModifierType.ADD_PERCENT) {
            addPercentModifiers.add(modifier);
        } else if (modifier.modifierType == Modifier.ModifierType.PURE_VALUE) {
            valueModifiers.add(modifier);
        } else if (modifier.modifierType == Modifier.ModifierType.MULTIPLY_PERCENT) {
            multiplyPercentModifiers.add(modifier);
        } else {
            throw new IllegalArgumentException("Unknown modifier type: " + modifier.modifierType);
        }
        compute();
    }

    public void removeModifier(Modifier modifier) {
        boolean removed = addPercentModifiers.remove(modifier);
        removed |= valueModifiers.remove(modifier);
        removed |= multiplyPercentModifiers.remove(modifier);
        if (removed) compute();
    }

    public List<Modifier> filterBySource(Modifier.ModifierSource type) {
        List<Modifier> result = new ArrayList<>();
        result.addAll(multiplyPercentModifiers.stream().filter(value -> value.source == type).toList());
        result.addAll(addPercentModifiers.stream().filter(value -> value.source == type).toList());
        result.addAll(valueModifiers.stream().filter(value -> value.source == type).toList());
        return result;
    }

    public void clearModifiers() {
        addPercentModifiers.clear();
        multiplyPercentModifiers.clear();
        valueModifiers.clear();
        compute();
    }

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

    @Override
    public DoubleValue clone() {
        try {
            DoubleValue copy = (DoubleValue) super.clone();
            copy.addPercentModifiers = new ArrayList<>();
            copy.multiplyPercentModifiers = new ArrayList<>();
            copy.valueModifiers = new ArrayList<>();
            addPercentModifiers.forEach(m -> copy.addPercentModifiers.add(m.clone()));
            multiplyPercentModifiers.forEach(m -> copy.multiplyPercentModifiers.add(m.clone()));
            valueModifiers.forEach(m -> copy.valueModifiers.add(m.clone()));
            copy.compute();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
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
        return sb.toString();
    }

    @AllArgsConstructor
    @Getter
    public static class Modifier implements Cloneable {
        private ModifierType modifierType;
        private double value;
        private ModifierSource source;
        private int sourceRoleId;

        // these need to call with number for example 18 represent to 18% boost
        public static Modifier addPercentNumber(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue / 100.0, ModifierSource.UNKNOWN, 0);
        }

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
        public static Modifier addPercent(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue, ModifierSource.UNKNOWN, 0);
        }

        public static Modifier multiplyPercent(double percentValue) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue, ModifierSource.UNKNOWN, 0);
        }

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

        @Override
        public Modifier clone() {
            try {
                return (Modifier) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public enum ModifierType {
            ADD_PERCENT,
            MULTIPLY_PERCENT,
            PURE_VALUE
        }

        public enum ModifierSource {
            UNKNOWN,
            BASE,
            RELIC,
            WEAPON,
            SKILL_POINT,
            EXTRA,
            RELIC_SET,
            BUFF,
            DEBUFF,
            TEST
        }
    }
}
