package com.laosun.aluminium.test;

import com.laosun.aluminium.utils.LevelPromotionCalc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LevelPromotionCalcTest {
    @Test
    public void characterRateBaseline() {
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1), 1e-9);
        Assertions.assertEquals(1.95, LevelPromotionCalc.calcCharacterRate(20, false), 1e-9);
        Assertions.assertEquals(7.35, LevelPromotionCalc.calcCharacterRate(80, false), 1e-6);
        Assertions.assertEquals(7.35, LevelPromotionCalc.calcCharacterRate(80, true), 1e-6);
    }

    @Test
    public void characterRateIsMonotonic() {
        double lv10 = LevelPromotionCalc.calcCharacterRate(10);
        double lv40 = LevelPromotionCalc.calcCharacterRate(40);
        double lv80 = LevelPromotionCalc.calcCharacterRate(80);

        Assertions.assertTrue(lv10 < lv40, "rate should grow with level");
        Assertions.assertTrue(lv40 < lv80, "rate should grow with level");
    }

    @Test
    public void weaponRateBaseline() {
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcWeaponRate(1), 1e-9);
        Assertions.assertEquals(3.85, LevelPromotionCalc.calcWeaponRate(20, false), 1e-9);
        Assertions.assertEquals(5.05, LevelPromotionCalc.calcWeaponRate(20, true), 1e-9);
    }

    @Test
    public void weaponRateIsMonotonic() {
        double lv1 = LevelPromotionCalc.calcWeaponRate(1);
        double lv40 = LevelPromotionCalc.calcWeaponRate(40);
        double lv80 = LevelPromotionCalc.calcWeaponRate(80);

        Assertions.assertTrue(lv1 < lv40, "rate should grow with level");
        Assertions.assertTrue(lv40 < lv80, "rate should grow with level");
    }
}
