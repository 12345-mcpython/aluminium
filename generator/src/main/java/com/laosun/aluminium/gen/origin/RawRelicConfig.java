package com.laosun.aluminium.gen.origin;

/**
 * Record of raw relic config
 */
public class RawRelicConfig {
    public record RawRelicMainAffixConfig(int GroupID, int AffixID, String Property, DoubleGetter BaseValue,
                                          DoubleGetter LevelAdd, int StepNum) {
    }
    public record RawRelicSubAffixConfig(int GroupID, int AffixID, String Property, DoubleGetter BaseValue,
                                         DoubleGetter StepValue, int StepNum) {
    }
}
