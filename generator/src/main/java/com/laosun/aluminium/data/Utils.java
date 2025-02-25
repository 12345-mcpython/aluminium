package com.laosun.aluminium.data;

import com.laosun.aluminium.data.origin.DoubleGetter;
import com.laosun.aluminium.data.origin.RawRelicMainAffixConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Utils {
    public static Map<String, Double> processRelicMainAttribute(RawRelicMainAffixConfig p) {
        return Map.ofEntries(Map.entry("base", Objects.requireNonNullElse(p.BaseValue(), new DoubleGetter(0.0)).Value()), Map.entry("bonus", Objects.requireNonNullElse(p.StepValue(), new DoubleGetter(0.0)).Value()));
    }
}
