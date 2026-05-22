package com.laosun.aluminium.models;


import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class Attribute {
    private final double baseValue;
    private double value;

    private final List<Modifier> percentModifiers = new ArrayList<>();
    private final List<Modifier> valueModifiers = new ArrayList<>();

    public Attribute(double baseValue) {
        this.baseValue = baseValue;
        compute();
    }

    @AllArgsConstructor
    public static class Modifier {
        private ModifierType modifierType;
        private double value;

        public enum ModifierType {
            PERCENT_VALUE,
            PURE_VALUE
        }

        public static Modifier percent(double percentValue) {
            return new Modifier(ModifierType.PERCENT_VALUE, percentValue / 100.0);
        }

        public static Modifier pure(double value) {
            return new Modifier(ModifierType.PURE_VALUE, value);
        }
    }

    public double get() {
        return value;
    }

    public void addModifier(Modifier modifier) {
        if (modifier.modifierType == Modifier.ModifierType.PERCENT_VALUE) {
            percentModifiers.add(modifier);
        } else {
            valueModifiers.add(modifier);
        }
        compute();

    }

    public boolean removeModifier(Modifier modifier) {
        boolean removed = percentModifiers.remove(modifier) || valueModifiers.remove(modifier);
        if (removed) compute();
        return removed;
    }

    public void clearModifiers() {
        percentModifiers.clear();
        valueModifiers.clear();
        compute();
    }

    private void compute() {
        double percentModifiersTotal = 1;
        double valueModifiersTotal = 0;
        for (Modifier percentModifier : percentModifiers) {
            percentModifiersTotal += percentModifier.value;
        }
        for (Modifier valueModifier : valueModifiers) {
            valueModifiersTotal += valueModifier.value;
        }

        value = baseValue * percentModifiersTotal + valueModifiersTotal;
    }
}
