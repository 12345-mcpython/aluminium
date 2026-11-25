package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue.Modifier.ModifierSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.laosun.aluminium.DoubleValue.Modifier.ModifierSource.BUFF;
import com.laosun.aluminium.DoubleValue;

/**
 * acceptance (plus the constructor checks): every zone asserts its own
 * multiplier against a base of 1000, plus the invariants of the zone container.
 *
 * <p>Pure algebra: {@code attacker}/{@code defender} take no part in zone settlement,
 * so they stay {@code null} here - that also keeps {@link com.laosun.aluminium.Constant}
 * from being initialised (no JSON is loaded for this test).
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the eight scenarios below, one
 * {@code assertAll} per area so a failing clause still names itself. The two exception
 * paths - the read-only container and the constructor's NPEs - stay separate tests: an
 * {@code assertThrows} is not a positive clause and must not hide behind one.
 */
public class DamageZoneTest {
    private static final double BASE = 1000.0;
    private static final double EPS = 1e-6;

    private Damage damage() {
        return new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, BASE);
    }

    @Test
    public void containerInvariants() {
        Damage empty = damage();
        Damage onePerKind = damage().addBoost(0.1).addBoost(0.2);
        double resistanceFirst = damage().resist(0.2, 0).addBoost(0.5).toValue();
        double boostFirst = damage().addBoost(0.5).resist(0.2, 0).toValue();

        Assertions.assertAll(
                () -> Assertions.assertTrue(empty.getDamageArea().isEmpty(),
                        "no zone leaves the container empty"),
                () -> Assertions.assertEquals(BASE, empty.toValue(), EPS,
                        "no zone leaves the base untouched"),
                () -> Assertions.assertEquals(1, onePerKind.getDamageArea().size(),
                        "one instance per kind"),
                () -> Assertions.assertEquals(1.3, onePerKind.boostArea().getRate(), EPS,
                        "the two boosts are one additive instance"),
                () -> Assertions.assertEquals(1200, resistanceFirst, EPS,
                        "resistance then boost"),
                () -> Assertions.assertEquals(resistanceFirst, boostFirst, EPS,
                        "zone order does not matter"));
    }

    @Test
    public void boostAndVulnerability() {
        Damage boosted = damage().addBoost(0.3).addBoost(0.2);
        Damage vulnerable = damage().addVulnerable(2.0).addVulnerable(2.0);
        Damage negativeBoost = damage().addBoost(-1.5);
        Damage negativeVulnerable = damage().addVulnerable(-1.5);
        Damage.DefenceArea zeroDefence = new Damage.DefenceArea().set(80, 0, 0);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1.5, boosted.boostArea().getRate(), EPS,
                        "damage boost is additive"),
                () -> Assertions.assertEquals(1500, boosted.toValue(), EPS,
                        "the additive boost settles"),
                () -> Assertions.assertEquals(5.0, vulnerable.vulnerableArea().raw().get(), EPS,
                        "the raw vulnerability stays unclamped at 5.0"),
                () -> Assertions.assertEquals(3.5, vulnerable.vulnerableArea().getRate(), EPS,
                        "the read rate is capped at 3.5"),
                () -> Assertions.assertEquals(3500, vulnerable.toValue(), EPS,
                        "the capped vulnerability settles"),
                () -> Assertions.assertEquals(0.0, negativeBoost.boostArea().getRate(), EPS,
                        "damage boost has a sanity floor at 0"),
                () -> Assertions.assertEquals(0.0, negativeVulnerable.vulnerableArea().getRate(), EPS,
                        "vulnerability has a sanity floor at 0"),
                () -> Assertions.assertEquals(1.0, zeroDefence.getRate(), EPS,
                        "at 0 defence the computed zone is exactly 1.0"));
    }

    @Test
    public void reductionAndWeakness() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(10,
                        damage().addReduction(0.9).addReduction(0.9).addReduction(0.9).toValue(), EPS,
                        "reduction multiplies and floors: 0.1^3 -> 0.001 -> 0.01"),
                () -> Assertions.assertEquals(10,
                        damage().addReduction(1.5).addReduction(-0.5).toValue(), EPS,
                        "reduction inputs clamp to [0,1]: 1.5 -> 1, -0.5 -> 0, then floor"),
                () -> Assertions.assertEquals(200,
                        damage().addWeakness(0.9).addWeakness(0.3).toValue(), EPS,
                        "weakness floors at 0.2: 1 - 1.2 -> 0.2"));
    }

    @Test
    public void defenceAndIgnore() {
        Damage plain = damage().defence(80, 1150, 0);
        Damage ignored = damage().defence(80, 1150, 0.5);
        Damage overIgnored = damage().defence(80, 1150, 1.5);
        Damage negativeIgnored = damage().defence(80, 1150, -0.5);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1000.0 / 2150.0, plain.defenceArea().getRate(), EPS,
                        "defence zone rate for 1150 DEF at level 80"),
                () -> Assertions.assertEquals(BASE * 1000.0 / 2150.0, plain.toValue(), EPS,
                        "the defence zone settles"),
                () -> Assertions.assertEquals(1000.0 / 1575.0, ignored.defenceArea().getRate(), EPS,
                        "50% defence ignore shrinks the effective defence"),
                () -> Assertions.assertEquals(BASE * 1000.0 / 1575.0, ignored.toValue(), EPS,
                        "the ignored defence settles"),
                () -> Assertions.assertEquals(1.0, overIgnored.defenceArea().getRate(), EPS,
                        "a 1.5 ignore clamps to 1 and leaves no defence"),
                () -> Assertions.assertEquals(1000.0 / 2150.0, negativeIgnored.defenceArea().getRate(), EPS,
                        "a -0.5 ignore clamps to 0 and leaves the defence untouched"));
    }

    @Test
    public void resistanceAndClamps() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(1200, damage().resist(0.2, 0.4).toValue(), EPS,
                        "negative resistance applies at full effect"),
                () -> Assertions.assertEquals(100, damage().resist(1.2, 0).toValue(), EPS,
                        "resistance caps at 0.9"),
                () -> Assertions.assertEquals(2000, damage().resist(-1.5, 0).toValue(), EPS,
                        "resistance has a -100% lower clamp"),
                () -> Assertions.assertEquals(2.0, damage().resist(-1.5, 0).resistArea().getRate(), EPS,
                        "the lower clamp reads as a 2.0 zone"));
    }

    @Test
    public void damageTypeExceptions() {
        Damage elation = new Damage(null, null, DamageElement.FIRE, DamageType.ELATION, BASE)
                .addBoost(0.5)
                .crit(true, 1.0);
        Damage trueDamage = damage()
                .addBoost(0.5)
                .addVulnerable(0.5)
                .addReduction(0.5)
                .addWeakness(0.5)
                .crit(true, 1.0)
                .defence(80, 1150, 0)
                .resist(0.2, 0)
                .trueDamage();
        Damage breakDamage = new Damage(null, null, DamageElement.FIRE, DamageType.BREAK, BASE)
                .addBoost(0.5)
                .crit(true, 1.0)
                .defence(80, 1150, 0);
        Damage dot = new Damage(null, null, DamageElement.FIRE, DamageType.DOT, BASE)
                .addBoost(0.5)
                .crit(true, 1.0);

        Assertions.assertAll(
                () -> Assertions.assertEquals(2000, elation.toValue(), EPS,
                        "欢愉 keeps crit but skips damage boost"),
                () -> Assertions.assertEquals(BASE, trueDamage.toValue(), EPS,
                        "true damage skips every zone"),
                () -> Assertions.assertTrue(trueDamage.isTrueDamage(), "the true-damage flag is set"),
                () -> Assertions.assertTrue(trueDamage.isCountsAsAttack(),
                        "true damage still counts as an attack"),
                () -> Assertions.assertFalse(trueDamage.notCountsAsAttack().isCountsAsAttack(),
                        "the not-counts-as-attack copy is off"),
                () -> Assertions.assertFalse(breakDamage.boostArea().applies(DamageType.BREAK),
                        "break damage skips the boost zone"),
                () -> Assertions.assertFalse(breakDamage.critArea().applies(DamageType.BREAK),
                        "break damage skips the crit zone"),
                () -> Assertions.assertEquals(BASE * 1000.0 / 2150.0, breakDamage.toValue(), EPS,
                        "break damage keeps the defence zone"),
                () -> Assertions.assertEquals(1500, dot.toValue(), EPS,
                        "dot keeps boost but skips crit"));
    }

    @Test
    public void critAndBreakdown() {
        Map<String, Double> breakdown = damage().addBoost(0.5).crit(false, 0).breakdown();

        Assertions.assertAll(
                () -> Assertions.assertEquals(2000, damage().crit(true, 1.0).toValue(), EPS,
                        "crit doubles a normal hit"),
                () -> Assertions.assertEquals(1000, damage().crit(false, 1.0).toValue(), EPS,
                        "a non-crit keeps the base"),
                () -> Assertions.assertEquals(3.5, new Damage.VulnerableArea().add(2.0).add(2.0).getRate(), EPS,
                        "a zone can be tested without a Damage"),
                () -> Assertions.assertEquals(1000.0 / 2150.0,
                        new Damage.DefenceArea().set(80, 1150, 0).getRate(), EPS,
                        "the computed defence zone can be tested without a Damage"),
                () -> Assertions.assertEquals(BASE, breakdown.get("base"), EPS,
                        "the breakdown names the base"),
                () -> Assertions.assertEquals(1.5, breakdown.get("BoostArea"), EPS,
                        "the breakdown names the boost zone"),
                () -> Assertions.assertEquals(1.0, breakdown.get("CritArea"), EPS,
                        "the breakdown names the crit zone"),
                () -> Assertions.assertEquals(1500, breakdown.get("final"), EPS,
                        "the breakdown names the final value"));
    }

    @Test
    public void modifiersCarrySourceAndCanBeRemovedBySource() {
        Damage damage = damage()
                .addVulnerable(0.5, BUFF, 7)
                .addBoost(0.2, ModifierSource.BUFF, 3)
                .addBoost(0.3, ModifierSource.RELIC, 4);

        Assertions.assertAll(
                () -> Assertions.assertEquals(2250, damage.toValue(), EPS,
                        "boost 1+0.5 and vulnerability 1+0.5 settle to 2250"),
                () -> Assertions.assertEquals(1, damage.boostArea().raw().filterBySource(ModifierSource.BUFF).size(),
                        "one BUFF modifier is on the boost zone"),
                () -> Assertions.assertEquals(1, damage.boostArea().raw().filterBySource(ModifierSource.RELIC).size(),
                        "one RELIC modifier is on the boost zone"));

        damage.vulnerableArea().removeModifiersFrom(BUFF, 7);
        damage.boostArea().removeModifiersFrom(ModifierSource.BUFF, 3);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1300, damage.toValue(), EPS,
                        "removing both BUFF modifiers leaves 1300"),
                () -> Assertions.assertEquals(1.3, damage.boostArea().getRate(), EPS,
                        "only the RELIC boost remains"));
    }

    /**
     * The read-only container is an exception path, so it stays its own test rather than
     * hiding inside {@link #containerInvariants()}.
     */
    @Test
    public void zoneContainerIsReadOnly() {
        Damage damage = damage().addBoost(0.1);

        Assertions.assertAll(
                () -> Assertions.assertThrows(UnsupportedOperationException.class,
                        () -> damage.getDamageArea().clear()),
                () -> Assertions.assertEquals(1, damage.getDamageArea().size()));
    }

    /**
     * The constructor's NPEs are an exception path too; the two positive constructor facts
     * stay here because they are about the same object.
     */
    @Test
    public void constructorContract() {
        Damage legacy = new Damage(null, null, DamageElement.FIRE, BASE);

        Assertions.assertAll(
                () -> Assertions.assertEquals(DamageType.NORMAL, legacy.getType(),
                        "the 4-arg constructor defaults to NORMAL"),
                () -> Assertions.assertEquals(BASE, legacy.toValue(), EPS,
                        "the 4-arg constructor keeps the base"),
                () -> Assertions.assertThrows(NullPointerException.class,
                        () -> new Damage(null, null, null, DamageType.BREAK, BASE)),
                () -> Assertions.assertThrows(NullPointerException.class,
                        () -> new Damage(null, null, DamageElement.FIRE, null, BASE)));
    }
}
