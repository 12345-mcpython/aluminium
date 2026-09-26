package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.models.skill.Skill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * P3-1 acceptance: the energy field + the single credit entry point {@code gainEnergy} + the
 * standard provider's numeric mapping.
 *
 * <p>For the numbers see ROADMAP's P3-0: basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 /
 * kill 5 / break 5;
 * the ultimate clears to zero first and then regains 5 (the clear is done in P3-2's {@code castUltra}).
 */
public class EnergyTest {
    private static final double EPS = 1e-6;

    @Test
    public void normalGainScalesWithEnergyRegenerationRate() {
        Character c = withEnergyBar(120);
        c.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));

        double added = c.gainEnergy(20);

        Assertions.assertEquals(30, added, EPS, "20 × (1 + 0.5) = 30");
        Assertions.assertEquals(30, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void fixedGainIgnoresEnergyRegenerationRate() {
        Character c = withEnergyBar(120);
        c.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));

        double added = c.gainEnergy(EnergyGain.fixed(20));

        Assertions.assertEquals(20, added, EPS, "a fixed energy gain does not take energy regeneration rate (the kind that restores a percentage of the cap)");
        Assertions.assertEquals(20, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void gainIsCappedByEnergyLimitAndReturnsActuallyAddedAmount() {
        Character c = withEnergyBar(100);
        c.setCurrentEnergy(95);

        Assertions.assertEquals(5, c.gainEnergy(20), EPS, "only 5 short of full → 5 actually credited");
        Assertions.assertEquals(100, c.getCurrentEnergy(), EPS);
        Assertions.assertTrue(c.isEnergyFull());
        Assertions.assertEquals(0, c.gainEnergy(20), EPS, "adding after full = 0");
        Assertions.assertEquals(100, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void entityWithoutEnergyBarNeverGains() {
        Character c = Character.fromAttributes("no-energy-bar", 1000, 100, 100, 100);   // maxEnergy defaults to 0

        Assertions.assertFalse(c.hasEnergyBar());
        Assertions.assertFalse(c.isEnergyFull(), "with no energy bar there is no 'full energy'");
        Assertions.assertEquals(0, c.gainEnergy(20), EPS);
        Assertions.assertEquals(0, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void nonPositiveGainIsIgnored() {
        Character c = withEnergyBar(120);

        Assertions.assertEquals(0, c.gainEnergy(0), EPS);
        Assertions.assertEquals(0, c.gainEnergy(-5), EPS);
        Assertions.assertEquals(0, c.gainEnergy((EnergyGain) null), EPS);
        Assertions.assertEquals(0, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void standardProviderMapsSkillSlotsToBaseEnergy() {
        CanHit user = withEnergyBar(120);
        StandardEnergyProvider provider = new StandardEnergyProvider();

        EnergyGain normal = provider.onSkillCast(user, new DefaultSkill(1001, 1, 1), Set.of());   // basic attack
        EnergyGain skill = provider.onSkillCast(user, new DefaultSkill(1001, 2, 1), Set.of());    // skill
        EnergyGain ultra = provider.onSkillCast(user, new DefaultSkill(1001, 3, 1), Set.of());    // ultimate
        EnergyGain followUp = provider.onSkillCast(user, new DefaultSkill(1001, 4, 1), Set.of()); // follow-up attack slot

        Assertions.assertEquals(20, normal.amount(), EPS);
        Assertions.assertEquals(30, skill.amount(), EPS);
        Assertions.assertNull(ultra, "the ultimate is not settled in onSkillCast (clear first, then regain 5, see onUltCast)");
        Assertions.assertNull(followUp, "follow-up attacks grant no energy at this stage (the per-hit value must land in the data first, P3-4/P8-3)");
        Assertions.assertEquals(5, provider.onUltCast(user, new DefaultSkill(1001, 3, 1)).amount(), EPS);
    }

    @Test
    public void standardProviderGivesHitKillAndBreakEnergy() {
        Character attacker = Character.fromAttributes("attacker", 1000, 100, 100, 100);
        Character target = withEnergyBar(120);
        Damage hit = new Damage(attacker, target, DamageElement.PHYSICAL, DamageType.NORMAL, 100);
        StandardEnergyProvider provider = new StandardEnergyProvider();

        Assertions.assertEquals(10, provider.onTakingHit(target, hit).amount(), EPS);
        Assertions.assertEquals(5, provider.onKill(attacker, target).amount(), EPS);
        Assertions.assertEquals(5, provider.onBreak(attacker, target).amount(), EPS);
    }

    @Test
    public void providerIsPerEntityAndReplaceable() {
        Character c = withEnergyBar(120);

        Assertions.assertInstanceOf(StandardEnergyProvider.class, c.getEnergyProvider(), "the default is the regular tier");

        c.setEnergyProvider(new EnergyProvider() {
            @Override
            public EnergyGain onSkillCast(CanHit user, Skill skill,
                                          Set<? extends CanHit> hitTargets) {
                return EnergyGain.normal(7);
            }
        });

        Assertions.assertEquals(7, c.getEnergyProvider()
                .onSkillCast(c, new DefaultSkill(1001, 1, 1), Set.of()).amount(), EPS,
                "the credit entry point reads the entity's own provider, not a hardcoded constant");
    }

    private static Character withEnergyBar(double maxEnergy) {
        Character c = Character.fromAttributes("tester", 1000, 100, 100, 100);
        c.setMaxEnergy(maxEnergy);
        return c;
    }
}
