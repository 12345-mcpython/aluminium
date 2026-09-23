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
        Assertions.assertTrue(DamageType.ELATION.isCrittable(), "elation damage does get crit stats (HSR.md §6.4)");

        Assertions.assertFalse(DamageType.BREAK.isCrittable(), "break damage never crits");
        Assertions.assertFalse(DamageType.SUPER_BREAK.isCrittable(), "super break damage never crits");
        Assertions.assertFalse(DamageType.DOT.isCrittable(), "DOT never crits");
        Assertions.assertFalse(DamageType.TRUE.isCrittable(), "true damage never crits");
    }

    @Test
    public void boostRules() {
        Assertions.assertTrue(DamageType.NORMAL.isBoostable());
        Assertions.assertTrue(DamageType.SKILL.isBoostable());
        Assertions.assertTrue(DamageType.DOT.isBoostable(), "DOT is boosted by the damage-bonus zone");

        Assertions.assertFalse(DamageType.BREAK.isBoostable(), "break damage is not boosted");
        Assertions.assertFalse(DamageType.SUPER_BREAK.isBoostable(), "super break damage is not boosted");
        Assertions.assertFalse(DamageType.TRUE.isBoostable(), "true damage is not boosted");
        Assertions.assertFalse(DamageType.ELATION.isBoostable(), "elation damage is unaffected by damage-increasing effects (HSR.md §6.5)");
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
