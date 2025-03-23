package com.laosun.aluminium.gen;

import com.laosun.aluminium.gen.origin.DoubleGetter;
import com.laosun.aluminium.gen.origin.RawRelicConfig.RawRelicMainAffixConfig;
import com.laosun.aluminium.gen.origin.RawRelicConfig.RawRelicSubAffixConfig;

import java.util.Map;
import java.util.Objects;

public class Utils {
    public static DoubleGetter getDoubleGetter(DoubleGetter getter) {
        return Objects.requireNonNullElse(getter, new DoubleGetter(0.0));
    }

    public static Map<String, Double> processRelicMainAttribute(RawRelicMainAffixConfig p) {
        return Map.ofEntries(Map.entry("base", getDoubleGetter(p.BaseValue()).Value()),
                Map.entry("bonus", getDoubleGetter(p.LevelAdd()).Value()));
    }

    public static Map<String, Double> processRelicSubAttribute(RawRelicSubAffixConfig p) {
        return Map.ofEntries(Map.entry("base", getDoubleGetter(p.BaseValue()).Value()),
                Map.entry("bonus", getDoubleGetter(p.StepValue()).Value()));
    }
}
