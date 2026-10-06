package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.models.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A light cone's superimposition rank selects its skill row.
 *
 * <p>`weapons.json` carries five rows per light cone, and the engine used to read the first one -- modelling rank 1 without
 * saying so. Weapon 20003 is the fixture because its rows carry real differing properties: `defence_percent` is 0.16 at rank
 * 1 and 0.32 at rank 5.
 *
 * <p>Note: The row's effect is read through `Weapon`'s own `@ToString`, not through `WeaponAttribute`'s accessors: this test does
 * not know that type's shape, and the printer carries the numbers all the same. Coarse on purpose, and stated rather than
 * pretended.
 */
public class WeaponRankTest {

    @Test
    public void theRankSelectsTheSkillRow() {
        Weapon rankOne = Weapon.build(20003, 80, false, 1);
        Weapon lastRank = Weapon.build(20003, 80, false, 5);
        Assertions.assertEquals(1, rankOne.getRank(), "rank 1 is what was asked for");
        Assertions.assertEquals(5, lastRank.getRank(), "and so is the last rank");

        String one = rankOne.toString();
        String last = lastRank.toString();
        Assertions.assertTrue(one.contains("0.16"), "rank 1 states its row's value: " + one);
        Assertions.assertTrue(last.contains("0.32"), "the last rank states its row's value: " + last);
        Assertions.assertFalse(one.contains("0.32"), "and rank 1 does not state the last row's value");
    }

    @Test
    public void theDefaultIsRankOne() {
        Assertions.assertEquals(1, Weapon.build(20003, 80).getRank(),
                "⚠ before the rank existed the engine always read the first row, so rank 1 is the default");
    }
}
