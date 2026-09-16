package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * P3-1 acceptance: 能量字段 + 唯一入账口 {@code gainEnergy} + 常规 provider 的数值映射。
 *
 * <p>数值口径见 ROADMAP 的 P3-0：普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5 / 击破 5；
 * 终结技先清零再回 5（清零在 P3-2 的 {@code castUltra} 里做）。
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

        Assertions.assertEquals(20, added, EPS, "定值回能不吃回能效率（按上限百分比回能那种）");
        Assertions.assertEquals(20, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void gainIsCappedByEnergyLimitAndReturnsActuallyAddedAmount() {
        Character c = withEnergyBar(100);
        c.setCurrentEnergy(95);

        Assertions.assertEquals(5, c.gainEnergy(20), EPS, "离满只差 5 → 实际入账 5");
        Assertions.assertEquals(100, c.getCurrentEnergy(), EPS);
        Assertions.assertTrue(c.isEnergyFull());
        Assertions.assertEquals(0, c.gainEnergy(20), EPS, "满了之后再加 = 0");
        Assertions.assertEquals(100, c.getCurrentEnergy(), EPS);
    }

    @Test
    public void entityWithoutEnergyBarNeverGains() {
        Character c = Character.fromAttributes("no-energy-bar", 1000, 100, 100, 100);   // maxEnergy 默认 0

        Assertions.assertFalse(c.hasEnergyBar());
        Assertions.assertFalse(c.isEnergyFull(), "没有能量条就没有「满能量」");
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

        EnergyGain normal = provider.onSkillCast(user, new DefaultSkill(1001, 1, 1), Set.of());   // 普攻
        EnergyGain skill = provider.onSkillCast(user, new DefaultSkill(1001, 2, 1), Set.of());    // 战技
        EnergyGain ultra = provider.onSkillCast(user, new DefaultSkill(1001, 3, 1), Set.of());    // 终结技
        EnergyGain followUp = provider.onSkillCast(user, new DefaultSkill(1001, 4, 1), Set.of()); // 追加攻击槽

        Assertions.assertEquals(20, normal.amount(), EPS);
        Assertions.assertEquals(30, skill.amount(), EPS);
        Assertions.assertNull(ultra, "终结技不在 onSkillCast 结算（先清零再回 5，见 onUltCast）");
        Assertions.assertNull(followUp, "追加攻击本阶段不回能（每段值要先落数据，P3-4/P8-3）");
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

        Assertions.assertInstanceOf(StandardEnergyProvider.class, c.getEnergyProvider(), "默认就是常规档");

        c.setEnergyProvider(new EnergyProvider() {
            @Override
            public EnergyGain onSkillCast(CanHit user, com.laosun.aluminium.models.Skill skill,
                                          Set<? extends CanHit> hitTargets) {
                return EnergyGain.normal(7);
            }
        });

        Assertions.assertEquals(7, c.getEnergyProvider()
                .onSkillCast(c, new DefaultSkill(1001, 1, 1), Set.of()).amount(), EPS,
                "入账口读的是实体自己的 provider，不是写死的常量");
    }

    private static Character withEnergyBar(double maxEnergy) {
        Character c = Character.fromAttributes("tester", 1000, 100, 100, 100);
        c.setMaxEnergy(maxEnergy);
        return c;
    }
}
