package com.laosun.aluminium.models;


import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class DoubleValue implements Cloneable {
    private final double baseValue;
    private double value;

    private List<Modifier> multiplyPercentModifiers = new ArrayList<>();
    private List<Modifier> addPercentModifiers = new ArrayList<>();
    private List<Modifier> valueModifiers = new ArrayList<>();

    public DoubleValue(double baseValue) {
        this.baseValue = baseValue;
        compute();
    }

    public static DoubleValue zero() {
        return new DoubleValue(0.0);
    }

    public double get() {
        return value;
    }

    public DoubleValue addModifier(Modifier modifier) {
        if (modifier.modifierType == Modifier.ModifierType.ADD_PERCENT) {
            addPercentModifiers.add(modifier);
        } else if (modifier.modifierType == Modifier.ModifierType.PURE_VALUE) {
            valueModifiers.add(modifier);
        } else {
            multiplyPercentModifiers.add(modifier);
        }
        compute();
        return this;
    }

    public DoubleValue removeModifier(Modifier modifier) {
        boolean removed = addPercentModifiers.remove(modifier);
        removed |= valueModifiers.remove(modifier);
        removed |= multiplyPercentModifiers.remove(modifier);
        if (removed) compute();
        return this;
    }

    public DoubleValue clearModifiers() {
        addPercentModifiers.clear();
        multiplyPercentModifiers.clear();
        valueModifiers.clear();
        compute();
        return this;
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
        return String.format("<DoubleValue: %f>", value);
    }

    @AllArgsConstructor
    public static class Modifier implements Cloneable {
        private ModifierType modifierType;
        private double value;

        public static Modifier addPercentNumber(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue / 100.0);
        }

        public static Modifier multiplyPercentNumber(double percentValue) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue / 100.0);
        }

        public static Modifier addPercent(double percentValue) {
            return new Modifier(ModifierType.ADD_PERCENT, percentValue);
        }

        public static Modifier multiplyPercent(double percentValue) {
            return new Modifier(ModifierType.MULTIPLY_PERCENT, percentValue);
        }

        public static Modifier pure(double value) {
            return new Modifier(ModifierType.PURE_VALUE, value);
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
    }
}
