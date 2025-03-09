package com.laosun.aluminium.gen;

import com.laosun.aluminium.gen.origin.DoubleGetter;
import com.laosun.aluminium.gen.origin.RawRelicMainAffixConfig;

import java.util.Map;
import java.util.Objects;

public class Utils {
    public static Map<String, Double> processRelicMainAttribute(RawRelicMainAffixConfig p) {
        return Map.ofEntries(Map.entry("base", Objects.requireNonNullElse(p.BaseValue(), new DoubleGetter(0.0)).Value()), Map.entry("bonus", Objects.requireNonNullElse(p.LevelAdd(), new DoubleGetter(0.0)).Value()));
    }
}
