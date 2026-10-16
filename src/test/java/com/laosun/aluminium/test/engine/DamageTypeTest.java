package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.DamageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * acceptance: the 12 damage types, the two game rules they carry, and lookup.
 */
public class DamageTypeTest {
    @Test
    public void valueCount() {
        // \u26a0 13 since TALENT: a Talent's follow-up had been indistinguishable from a counter and from an ultimate's extra hits,
        // and the type is what names it. Raising this number is the point of the guard.
        Assertions.assertEquals(13, DamageType.values().length);
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

    /**
     * \u2b50 TALENT behaves as ADDITIONAL does, in every mechanical way.
     *
     * <p>It exists to NAME a Talent's follow-up, not to change one: a Talent's damage IS additional damage, so it crits and is
     * boostable exactly as `ADDITIONAL` is, and it collects the follow-up boost. A difference in any of those would silently move
     * damage numbers, which is why it is asserted rather than assumed.
     */
    @Test
    public void talentFollowsAdditionalRules() {
        Assertions.assertEquals(DamageType.ADDITIONAL.isCrittable(), DamageType.TALENT.isCrittable(),
                "a Talent's follow-up crits exactly as additional damage does");
        Assertions.assertEquals(DamageType.ADDITIONAL.isBoostable(), DamageType.TALENT.isBoostable(),
                "and the damage-bonus zone applies to it in exactly the same way");
        Assertions.assertEquals(DamageType.TALENT, DamageType.fromString("talent"),
                "the string table resolves it");
        Assertions.assertEquals(DamageType.TALENT, DamageType.fromString("TALENT"),
                "case-insensitively, like every other entry");
    }
}
