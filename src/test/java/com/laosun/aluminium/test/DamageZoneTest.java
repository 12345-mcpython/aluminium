package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.BUFF;

/**
 * P1-3 acceptance (and the P1-2 constructor checks): every zone asserts its own
 * multiplier against a base of 1000, plus the invariants of the zone container.
 *
 * <p>Pure algebra: {@code attacker}/{@code defender} take no part in zone settlement,
 * so they stay {@code null} here — that also keeps {@link com.laosun.aluminium.Constant}
 * from being initialised (no JSON is loaded for this test).
 */
public class DamageZoneTest {
    private static final double BASE = 1000.0;
    private static final double EPS = 1e-6;

    private Damage damage() {
        return new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, BASE);
    }

    @Test
    public void noZoneLeavesBaseUntouched() {
        Damage damage = damage();

        Assertions.assertTrue(damage.getDamageArea().isEmpty());
        Assertions.assertEquals(BASE, damage.toValue(), EPS);
    }

    @Test
    public void boostIsAdditive() {
        Damage damage = damage().addBoost(0.3).addBoost(0.2);

        Assertions.assertEquals(1.5, damage.boostArea().getRate(), EPS);
        Assertions.assertEquals(1500, damage.toValue(), EPS);
    }

    @Test
    public void vulnerableIsCapped() {
        Damage damage = damage().addVulnerable(2.0).addVulnerable(2.0);

        // The un-clamped raw value is still 5.0; it is only capped to 3.5 when the rate is read
        Assertions.assertEquals(5.0, damage.vulnerableArea().raw().get(), EPS);
        Assertions.assertEquals(3.5, damage.vulnerableArea().getRate(), EPS);
        Assertions.assertEquals(3500, damage.toValue(), EPS);
    }

    @Test
    public void reductionIsMultiplicativeFlooredAndInputClamped() {
        // Multiplication + floor: 0.1³ = 0.001 → 0.01
        Assertions.assertEquals(10,
                damage().addReduction(0.9).addReduction(0.9).addReduction(0.9).toValue(), EPS);

        // The input is clamped to [0,1] first: 1.5 → 1 (factor 0), -0.5 → 0 (factor 1) → product 0 → floor 0.01
        Assertions.assertEquals(10,
                damage().addReduction(1.5).addReduction(-0.5).toValue(), EPS);
    }

    @Test
    public void weaknessIsFloored() {
        Damage damage = damage().addWeakness(0.9).addWeakness(0.3);

        // 1 - 1.2 = -0.2 → floored to 0.2
        Assertions.assertEquals(200, damage.toValue(), EPS);
    }

    @Test
    public void sanityFloorVersusOfficialFloors() {
        // DMG boost / vulnerability: there is no negative DMG boost in the game; 0 is just PercentArea's
        // sanity floor (guarding against a negative factor flipping the damage sign);
        // the only official lower bounds are reduction 0.01 and weakness 0.2, already asserted by their
        // own cases, so they are not repeated here
        Assertions.assertEquals(0.0, damage().addBoost(-1.5).boostArea().getRate(), EPS);
        Assertions.assertEquals(0.0, damage().addVulnerable(-1.5).vulnerableArea().getRate(), EPS);
        // Computed zones have no lower bound: the defence formula is always positive itself, and at 0 defence it is exactly 1.0
        Assertions.assertEquals(1.0, new Damage.DefenceArea().set(80, 0, 0).getRate(), EPS);
    }

    @Test
    public void critIsBookkeepingOnly() {
        Assertions.assertEquals(2000, damage().crit(true, 1.0).toValue(), EPS);
        Assertions.assertEquals(1000, damage().crit(false, 1.0).toValue(), EPS);
    }

    @Test
    public void defenceZone() {
        Damage damage = damage().defence(80, 1150, 0);

        Assertions.assertEquals(1000.0 / 2150.0, damage.defenceArea().getRate(), EPS);
        Assertions.assertEquals(BASE * 1000.0 / 2150.0, damage.toValue(), EPS);
    }

    @Test
    public void defenceIgnoreShrinksEffectiveDefence() {
        Damage damage = damage().defence(80, 1150, 0.5);

        Assertions.assertEquals(1000.0 / 1575.0, damage.defenceArea().getRate(), EPS);
        Assertions.assertEquals(BASE * 1000.0 / 1575.0, damage.toValue(), EPS);
    }

    @Test
    public void defenceIgnoreIsClampedToUnitRange() {
        // 1.5 → clamped to 1 → effective defence 0 → defence zone rate 1.0
        Assertions.assertEquals(1.0, damage().defence(80, 1150, 1.5).defenceArea().getRate(), EPS);
        // -0.5 → clamped to 0 → equivalent to ignoring no defence at all
        Assertions.assertEquals(1000.0 / 2150.0, damage().defence(80, 1150, -0.5).defenceArea().getRate(), EPS);
    }

    @Test
    public void resistanceZone() {
        // 0.2 - 0.4 = -0.2 → negative resistance (currently implemented at full effect)
        Assertions.assertEquals(1200, damage().resist(0.2, 0.4).toValue(), EPS);
    }

    @Test
    public void resistanceIsClampedToMax() {
        // 1.2 → cap 0.9
        Assertions.assertEquals(100, damage().resist(1.2, 0).toValue(), EPS);
    }

    @Test
    public void resistanceIsClampedToMin() {
        // HSR.md §2.5: resistance ranges over -100% ~ 90% ⇒ resistance zone 0.1 ~ 2.0 (negative resistance at full effect)
        Assertions.assertEquals(2000, damage().resist(-1.5, 0).toValue(), EPS);
        Assertions.assertEquals(2.0, damage().resist(-1.5, 0).resistArea().getRate(), EPS);
    }

    @Test
    public void elationKeepsCritButSkipsBoost() {
        Damage damage = new Damage(null, null, DamageElement.FIRE, DamageType.ELATION, BASE)
                .addBoost(0.5)
                .crit(true, 1.0);

        // Elation damage: takes the crit zone, but is unaffected by damage-increase effects (HSR.md §6.4 / §6.5)
        Assertions.assertEquals(2000, damage.toValue(), EPS);
    }

    @Test
    public void trueDamageSkipsEveryZone() {
        Damage damage = damage()
                .addBoost(0.5)
                .addVulnerable(0.5)
                .addReduction(0.5)
                .addWeakness(0.5)
                .crit(true, 1.0)
                .defence(80, 1150, 0)
                .resist(0.2, 0)
                .trueDamage();

        Assertions.assertEquals(BASE, damage.toValue(), EPS);
        Assertions.assertTrue(damage.isTrueDamage());
        Assertions.assertTrue(damage.isCountsAsAttack());
        Assertions.assertFalse(damage.notCountsAsAttack().isCountsAsAttack());
    }

    @Test
    public void oneZoneInstancePerKind() {
        Damage damage = damage().addBoost(0.1).addBoost(0.2);

        Assertions.assertEquals(1, damage.getDamageArea().size());
        Assertions.assertEquals(1.3, damage.boostArea().getRate(), EPS);
    }

    @Test
    public void zoneContainerIsReadOnly() {
        Damage damage = damage().addBoost(0.1);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> damage.getDamageArea().clear());
        Assertions.assertEquals(1, damage.getDamageArea().size());
    }

    @Test
    public void zonesAreTestableWithoutDamage() {
        // A zone can be unit-tested without a Damage too: one additive zone + one computed zone
        // (the values of the other zones are already asserted by their own cases)
        Assertions.assertEquals(3.5, new Damage.VulnerableArea().add(2.0).add(2.0).getRate(), EPS);
        Assertions.assertEquals(1000.0 / 2150.0, new Damage.DefenceArea().set(80, 1150, 0).getRate(), EPS);
    }

    @Test
    public void zoneOrderDoesNotMatter() {
        double resistanceFirst = damage().resist(0.2, 0).addBoost(0.5).toValue();
        double boostFirst = damage().addBoost(0.5).resist(0.2, 0).toValue();

        Assertions.assertEquals(1200, resistanceFirst, EPS);
        Assertions.assertEquals(resistanceFirst, boostFirst, EPS);
    }

    @Test
    public void breakDamageSkipsBoostAndCritButKeepsDefence() {
        Damage damage = new Damage(null, null, DamageElement.FIRE, DamageType.BREAK, BASE)
                .addBoost(0.5)
                .crit(true, 1.0)
                .defence(80, 1150, 0);

        // DMG boost / crit are blocked by applies(), the defence zone still takes effect as usual
        Assertions.assertFalse(damage.boostArea().applies(DamageType.BREAK));
        Assertions.assertFalse(damage.critArea().applies(DamageType.BREAK));
        Assertions.assertEquals(BASE * 1000.0 / 2150.0, damage.toValue(), EPS);
    }

    @Test
    public void dotKeepsBoostButSkipsCrit() {
        Damage damage = new Damage(null, null, DamageElement.FIRE, DamageType.DOT, BASE)
                .addBoost(0.5)
                .crit(true, 1.0);

        Assertions.assertEquals(1500, damage.toValue(), EPS);
    }

    @Test
    public void modifiersCarrySourceAndCanBeRemovedBySource() {
        Damage damage = damage()
                .addVulnerable(0.5, BUFF, 7)
                .addBoost(0.2, ModifierSource.BUFF, 3)
                .addBoost(0.3, ModifierSource.RELIC, 4);

        // DMG boost 1+0.5, vulnerability 1+0.5 → 2250
        Assertions.assertEquals(2250, damage.toValue(), EPS);
        Assertions.assertEquals(1, damage.boostArea().raw().filterBySource(ModifierSource.BUFF).size());
        Assertions.assertEquals(1, damage.boostArea().raw().filterBySource(ModifierSource.RELIC).size());

        // Removal by source: a Buff expiring / being dispelled (P10-3) is exactly this opening
        damage.vulnerableArea().removeModifiersFrom(BUFF, 7);
        damage.boostArea().removeModifiersFrom(ModifierSource.BUFF, 3);

        Assertions.assertEquals(1300, damage.toValue(), EPS);
        Assertions.assertEquals(1.3, damage.boostArea().getRate(), EPS);
    }

    @Test
    public void breakdownListsEveryContributingZone() {
        Map<String, Double> breakdown = damage().addBoost(0.5).crit(false, 0).breakdown();

        Assertions.assertEquals(BASE, breakdown.get("base"), EPS);
        Assertions.assertEquals(1.5, breakdown.get("BoostArea"), EPS);
        Assertions.assertEquals(1.0, breakdown.get("CritArea"), EPS);
        Assertions.assertEquals(1500, breakdown.get("final"), EPS);
    }

    @Test
    public void constructorContract() {
        // 4-arg legacy constructor: damage type defaults to NORMAL
        Damage legacy = new Damage(null, null, DamageElement.FIRE, BASE);
        Assertions.assertEquals(DamageType.NORMAL, legacy.getType());
        Assertions.assertEquals(BASE, legacy.toValue(), EPS);

        // neither element nor type is allowed to be null
        Assertions.assertThrows(NullPointerException.class,
                () -> new Damage(null, null, null, DamageType.BREAK, BASE));
        Assertions.assertThrows(NullPointerException.class,
                () -> new Damage(null, null, DamageElement.FIRE, null, BASE));
    }
}
