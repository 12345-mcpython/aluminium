package com.laosun.aluminium.data.origin;


public record RawHardLevelGroup(DoubleGetter AttackRatio, DoubleGetter DefenceRatio, DoubleGetter HPRatio, DoubleGetter SpeedRatio,
                                DoubleGetter StanceRatio, DoubleGetter StatusProbability, DoubleGetter StatusResistance) {
}
