package com.laosun.aluminium.data.origin;

public record RawRelicMainAffixConfig(int GroupID, int AffixID, String Property, DoubleGetter BaseValue,
                                      DoubleGetter StepValue, int StepNum) {
}
