package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.energy.StandardEnergyProvider;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * acceptance: the energy field + the single credit entry point {@code gainEnergy} + the
 * standard provider's numeric mapping.
 *
 * <p>For the numbers see the standard tier in {@code AGENT.md}: basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 /
 * kill 5 / break 5;
 * the ultimate clears to zero first and then regains 5 (the clear is done in {@code castUltra}).
 */
public class EnergyTest {
    private static final double EPS = 1e-6;

    /**
     * The energy regeneration rate multiplies a normal gain but leaves a fixed gain alone. Both clauses
     * are observed on the same fixture (120 cap, 0.5 rate): one character takes the normal gain, the
     * identical second character takes the fixed one, so the two entries can be compared side by side.
     */
    @Test
    public void energyRegenerationRateScalesNormalGainButNotFixedGain() {
        Character scaled = withEnergyBar(120);
        scaled.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
        Character fixed = withEnergyBar(120);
        fixed.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));

        double normalAdded = scaled.gainEnergy(20);
        double fixedAdded = fixed.gainEnergy(EnergyGain.fixed(20));

        Assertions.assertAll(
                () -> Assertions.assertEquals(30, normalAdded, EPS, "normal gain: 20 × (1 + 0.5) = 30"),
                () -> Assertions.assertEquals(30, scaled.getCurrentEnergy(), EPS, "normal gain: the bar holds the scaled 30"),
                () -> Assertions.assertEquals(20, fixedAdded, EPS, "fixed gain: a fixed energy gain does not take energy regeneration rate (the kind that restores a percentage of the cap)"),
                () -> Assertions.assertEquals(20, fixed.getCurrentEnergy(), EPS, "fixed gain: the bar holds the unscaled 20"));
    }

    /**
     * The cap truncates the gain and {@code gainEnergy} reports the amount actually credited, not the
     * theoretical one: 5 short of full credits 5, and the same call after full credits 0.
     */
    @Test
    public void gainIsCappedByEnergyLimitAndReturnsActuallyAddedAmount() {
        Character c = withEnergyBar(100);
        c.setCurrentEnergy(95);

        double firstAdded = c.gainEnergy(20);
        double afterFullAdded = c.gainEnergy(20);

        Assertions.assertAll(
                () -> Assertions.assertEquals(5, firstAdded, EPS, "capped: only 5 short of full → 5 actually credited"),
                () -> Assertions.assertEquals(100, c.getCurrentEnergy(), EPS, "capped: the bar stops at the 100 cap"),
                () -> Assertions.assertTrue(c.isEnergyFull(), "capped: 100 / 100 is full energy"),
                () -> Assertions.assertEquals(0, afterFullAdded, EPS, "capped: adding after full = 0"),
                () -> Assertions.assertEquals(100, c.getCurrentEnergy(), EPS, "capped: the refused gain left the bar at 100"));
    }

    /**
     * A character with no energy bar ({@code maxEnergy} defaults to 0) never gains and is never full.
     */
    @Test
    public void entityWithoutEnergyBarNeverGains() {
        Character c = Character.fromAttributes("no-energy-bar", 1000, 100, 100, 100);   // maxEnergy defaults to 0

        Assertions.assertAll(
                () -> Assertions.assertFalse(c.hasEnergyBar(), "no bar: maxEnergy 0 means there is no energy bar"),
                () -> Assertions.assertFalse(c.isEnergyFull(), "no bar: with no energy bar there is no 'full energy'"),
                () -> Assertions.assertEquals(0, c.gainEnergy(20), EPS, "no bar: a no-bar entity credits nothing"),
                () -> Assertions.assertEquals(0, c.getCurrentEnergy(), EPS, "no bar: the energy stays at 0"));
    }

    /**
     * The credit entry point refuses non-positive input: a zero base, a negative base and a null gain
     * are all no-ops that leave the bar untouched.
     */
    @Test
    public void nonPositiveGainIsIgnored() {
        Character c = withEnergyBar(120);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, c.gainEnergy(0), EPS, "non-positive: a zero base is not a gain"),
                () -> Assertions.assertEquals(0, c.gainEnergy(-5), EPS, "non-positive: a negative base is not a gain"),
                () -> Assertions.assertEquals(0, c.gainEnergy((EnergyGain) null), EPS, "non-positive: a null gain credits nothing"),
                () -> Assertions.assertEquals(0, c.getCurrentEnergy(), EPS, "non-positive: every refused gain left the bar at 0"));
    }

    /**
     * The standard tier's whole numeric mapping, read off one provider and one fixture: the skill slots
     * (basic attack 20 / skill 30 / ultimate not settled here / follow-up none, then 5 on the ultimate
     * cast) and the damage events (taking a hit 10 / kill 5 / break 5).
     */
    @Test
    public void standardProviderMapsEveryEventToBaseEnergy() {
        Character target = withEnergyBar(120);
        Character attacker = Character.fromAttributes("attacker", 1000, 100, 100, 100);
        Damage hit = new Damage(attacker, target, DamageElement.PHYSICAL, DamageType.NORMAL, 100);
        StandardEnergyProvider provider = new StandardEnergyProvider();

        EnergyGain normal = provider.onSkillCast(target, new DefaultSkill(1001, 1, 1), Set.of());   // basic attack
        EnergyGain skill = provider.onSkillCast(target, new DefaultSkill(1001, 2, 1), Set.of());    // skill
        EnergyGain ultra = provider.onSkillCast(target, new DefaultSkill(1001, 3, 1), Set.of());    // ultimate
        EnergyGain followUp = provider.onSkillCast(target, new DefaultSkill(1001, 4, 1), Set.of()); // follow-up attack slot
        EnergyGain ultRegain = provider.onUltCast(target, new DefaultSkill(1001, 3, 1));
        EnergyGain takenHit = provider.onTakingHit(target, hit);
        EnergyGain kill = provider.onKill(attacker, target);
        EnergyGain breakGain = provider.onBreak(attacker, target);

        Assertions.assertAll(
                () -> Assertions.assertEquals(20, normal.amount(), EPS, "skill slot: basic attack = 20"),
                () -> Assertions.assertEquals(30, skill.amount(), EPS, "skill slot: skill = 30"),
                () -> Assertions.assertNull(ultra, "the ultimate is not settled in onSkillCast (clear first, then regain 5, see onUltCast)"),
                () -> Assertions.assertNull(followUp, "follow-up attacks grant no energy at this stage (the per-hit value must land in the data first)"),
                () -> Assertions.assertEquals(5, ultRegain.amount(), EPS, "ultimate cast: 5 regained after the clear"),
                () -> Assertions.assertEquals(10, takenHit.amount(), EPS, "damage event: taking a hit = 10"),
                () -> Assertions.assertEquals(5, kill.amount(), EPS, "damage event: kill = 5"),
                () -> Assertions.assertEquals(5, breakGain.amount(), EPS, "damage event: break = 5"));
    }

    /**
     * The provider is per entity and replaceable: the default is the regular tier, and the credit entry
     * point reads the entity's own provider rather than a hardcoded constant.
     */
    @Test
    public void providerIsPerEntityAndReplaceable() {
        Character c = withEnergyBar(120);
        EnergyProvider defaultProvider = c.getEnergyProvider();

        c.setEnergyProvider(new EnergyProvider() {
            @Override
            public EnergyGain onSkillCast(CanHit user, Skill skill,
                                          Set<? extends CanHit> hitTargets) {
                return EnergyGain.normal(7);
            }
        });

        Assertions.assertAll(
                () -> Assertions.assertInstanceOf(StandardEnergyProvider.class, defaultProvider, "the default is the regular tier"),
                () -> Assertions.assertEquals(7, c.getEnergyProvider()
                                .onSkillCast(c, new DefaultSkill(1001, 1, 1), Set.of()).amount(), EPS,
                        "the credit entry point reads the entity's own provider, not a hardcoded constant"));
    }

    private static Character withEnergyBar(double maxEnergy) {
        Character c = Character.fromAttributes("tester", 1000, 100, 100, 100);
        c.setMaxEnergy(maxEnergy);
        return c;
    }
}
