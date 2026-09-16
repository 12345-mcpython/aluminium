package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P3-2 acceptance: 回能真的接进战斗了——技能释放 / 受击 / 击杀自动回能，
 * 终结技要满能量、先清零再回 5。
 *
 * <p>锚点（ROADMAP P3-0 口径 2）：普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5。
 */
public class EnergyBattleTest {
    private static final double EPS = 1e-6;

    @Test
    public void normalAttackAndSkillGrantEnergyToTheCaster() {
        Character hero = character("hero", 120);
        Enemy dummy = dummy(1_000_000);
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));   // 普攻（伤害技）
        Assertions.assertEquals(20, hero.getCurrentEnergy(), EPS);

        // cid 1001 槽位 2 是护盾（非伤害技）：SkillExecutor 会提前 return，但能量照样要给
        battle.castImmediate(new DefaultSkill(1001, 2, 1), hero, List.of(dummy));
        Assertions.assertEquals(50, hero.getCurrentEnergy(), EPS, "20 + 30：非伤害技能也要回能");
    }

    @Test
    public void energyRegenerationRateScalesTheGain() {
        Character hero = character("hero", 120);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
        Battle battle = newBattle(hero, dummy(1_000_000));

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, battle.enemies);

        Assertions.assertEquals(30, hero.getCurrentEnergy(), EPS, "20 × (1 + 0.5)");
    }

    @Test
    public void ultraNeedsFullEnergyThenClearsAndRegainsFive() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));
        Battle battle = newBattle(hero, dummy(1_000_000));

        Assertions.assertFalse(battle.castUltra(hero, battle.enemies), "能量不满 → 放不了");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS);

        hero.setCurrentEnergy(120);
        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "顺序定义：先清零，再回自身 5");
    }

    @Test
    public void takingHitGrantsEnergyAndKillGrantsItToTheAttacker() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(500);
        victim.setMaxEnergy(100);
        Battle battle = newBattle(hero, victim);

        battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 10));
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS, "受击回能 10");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "只是打人（没杀人）不给攻击者回能");

        battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 100_000));
        Assertions.assertTrue(victim.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "击杀回能记给 damage.getAttacker()");
    }

    @Test
    public void additionalAndTrueDamageGrantNoHitEnergy() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(1_000_000);
        victim.setMaxEnergy(100);
        Battle battle = newBattle(hero, victim);

        battle.applyAdditionalDamage(hero, victim, DamageElement.ICE, 100);
        battle.applyTrueDamage(hero, victim, DamageElement.ICE, 100);

        Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS, "附加伤害/真伤「不视为造成了 1 次攻击」");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS);
    }

    @Test
    public void entityWithoutEnergyBarNeverGainsAndCannotCastUltra() {
        Character hero = character("hero", 0);                     // 无能量条（1407 遐蝶那种）
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));
        Enemy dummy = dummy(1_000_000);
        dummy.setMaxEnergy(0);
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));
        battle.applyDamage(hero, new Damage(dummy, hero, DamageElement.ICE, DamageType.NORMAL, 10));

        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS);
        Assertions.assertEquals(0, dummy.getCurrentEnergy(), EPS);
        Assertions.assertFalse(hero.hasEnergyBar());
        Assertions.assertFalse(battle.castUltra(hero, List.of(dummy)), "没有能量条就永远放不了终结技");
    }

    @Test
    public void battleHooksUseTheEntityProviderNotHardcodedConstants() {
        Character hero = character("hero", 120);
        hero.setEnergyProvider(new EnergyProvider() {
            @Override
            public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
                return EnergyGain.normal(7);
            }
        });
        Enemy dummy = dummy(1_000_000);
        dummy.setMaxEnergy(100);
        dummy.setEnergyProvider(new EnergyProvider() {
            @Override
            public EnergyGain onTakingHit(CanHit target, Damage damage) {
                return EnergyGain.normal(99);
            }
        });
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));

        Assertions.assertEquals(7, hero.getCurrentEnergy(), EPS, "技能回能读的是施放者自己的 provider");
        Assertions.assertEquals(99, dummy.getCurrentEnergy(), EPS, "受击回能读的是被打者自己的 provider");
    }

    @Test
    public void breakEnergyGoesToTheBreaker() {
        Character hero = character("hero", 120);
        Enemy dummy = dummy(1_000_000);
        Battle battle = newBattle(hero, dummy);

        Assertions.assertEquals(5, battle.gainBreakEnergy(hero, dummy), EPS);
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "击破回能基准 5（P4-4 击破时调这个口子）");
        Assertions.assertEquals(0, battle.gainBreakEnergy(null, dummy), EPS, "没有击破者 → 0，不炸");
    }

    @Test
    public void breakEnergyScalesWithEnergyRegenerationRate() {
        Character hero = character("hero", 120);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
        Battle battle = newBattle(hero, dummy(1_000_000));

        Assertions.assertEquals(7.5, battle.gainBreakEnergy(hero, battle.enemies.getFirst()), EPS);
    }

    private static Character character(String name, double maxEnergy) {
        Character c = Character.fromAttributes(name, 10_000, 100, 100, 100);
        c.setMaxEnergy(maxEnergy);
        return c;
    }

    private static Enemy dummy(double hp) {
        return Enemy.fromAttributes("dummy", hp, 100, 100, 100);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
