package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.DamageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P1-1 acceptance: the 12 damage types, the two game rules they carry, and lookup.
 */
public class DamageTypeTest {
    @Test
    public void valueCount() {
        Assertions.assertEquals(12, DamageType.values().length);
    }

    @Test
    public void critRules() {
        Assertions.assertTrue(DamageType.NORMAL.isCrittable());
        Assertions.assertTrue(DamageType.SKILL.isCrittable());
        Assertions.assertTrue(DamageType.ULTRA.isCrittable());
        Assertions.assertTrue(DamageType.ADDITIONAL.isCrittable());
        Assertions.assertTrue(DamageType.EXTRA.isCrittable());
        Assertions.assertTrue(DamageType.TECHNIQUE.isCrittable());
        Assertions.assertTrue(DamageType.MEMORY.isCrittable());
        Assertions.assertTrue(DamageType.ELATION.isCrittable(), "欢愉伤害吃双爆区（HSR.md §6.4）");

        Assertions.assertFalse(DamageType.BREAK.isCrittable(), "击破不吃双暴");
        Assertions.assertFalse(DamageType.SUPER_BREAK.isCrittable(), "超击破不吃双暴");
        Assertions.assertFalse(DamageType.DOT.isCrittable(), "持续伤害不吃双暴");
        Assertions.assertFalse(DamageType.TRUE.isCrittable(), "真实伤害不吃双暴");
    }

    @Test
    public void boostRules() {
        Assertions.assertTrue(DamageType.NORMAL.isBoostable());
        Assertions.assertTrue(DamageType.SKILL.isBoostable());
        Assertions.assertTrue(DamageType.DOT.isBoostable(), "持续伤害吃增伤");

        Assertions.assertFalse(DamageType.BREAK.isBoostable(), "击破不吃增伤");
        Assertions.assertFalse(DamageType.SUPER_BREAK.isBoostable(), "超击破不吃增伤");
        Assertions.assertFalse(DamageType.TRUE.isBoostable(), "真实伤害不吃增伤");
        Assertions.assertFalse(DamageType.ELATION.isBoostable(), "欢愉伤害不受伤害提高类效果影响（HSR.md §6.5）");
    }

    @Test
    public void fromStringIsCaseInsensitive() {
        Assertions.assertEquals(DamageType.BREAK, DamageType.fromString("break"));
        Assertions.assertEquals(DamageType.BREAK, DamageType.fromString("BREAK"));
        Assertions.assertEquals(DamageType.SUPER_BREAK, DamageType.fromString("Super_Break"));
    }

    @Test
    public void fromStringRejectsUnknown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> DamageType.fromString("nope"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> DamageType.fromString(null));
    }
}
