package com.laosun.aluminium.gen.origin;


public record RawHardLevelGroup(int Level, int HardLevelGroup, DoubleGetter AttackRatio, DoubleGetter DefenceRatio,
                                DoubleGetter HPRatio, DoubleGetter SpeedRatio,
                                DoubleGetter StanceRatio, DoubleGetter StatusProbability,
                                DoubleGetter StatusResistance) {
}
