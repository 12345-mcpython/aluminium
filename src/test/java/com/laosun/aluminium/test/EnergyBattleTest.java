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
import com.laosun.aluminium.models.EnemyFactory;
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

    /**
     * H-5：清零必须发生在**大招本体结算之前**。
     *
     * <p>否则大招打死的那个敌人给出的击杀回能（记给 {@code damage.getAttacker()}，也就是放大招的人）
     * 会被随后的 {@code setCurrentEnergy(0)} 抹掉——本该 5（击杀）+ 5（终结技）= 10，只剩 5。
     */
    @Test
    public void ultraKeepsTheKillEnergyItEarned() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));   // AoE，倍率 0.9 → 100 × 0.9 = 90
        Enemy victim = dummy(50);
        Battle battle = newBattle(hero, victim);
        hero.setCurrentEnergy(120);

        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertTrue(victim.isDeath(), "这一发大招必须当场击杀，否则测不到击杀回能");
        Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS, "击杀 5 + 终结技自身 5，不能被清零吃掉");
    }

    /**
     * H-5 的击破分支：大招打空韧性时给出的击破回能同样不能被清零抹掉。
     */
    @Test
    public void ultraKeepsTheBreakEnergyItEarned() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1003, 3, 1));   // 姬子终结技：Fire、AoE、all=60
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);            // 弱火、韧性 60、血厚
        Battle battle = newBattle(hero, iceEdge);
        hero.setCurrentEnergy(120);

        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertTrue(iceEdge.isBroken(), "60 点削韧打空 60 韧性 → 击破");
        Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS, "击破 5 + 终结技自身 5，不能被清零吃掉");
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

        Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS,
                "附加伤害/真伤「不视为造成了 1 次攻击」→ 受击方不回能");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "没打死人 → 也没有击杀回能");
    }

    /**
     * **击杀回能与伤害类型无关**（2026-09-19 口径）：任何归属到角色的伤害，只要打死了怪，
     * 就给攻击者结算击杀回能 —— 包括「不视为一次攻击」的附加伤害与真实伤害。
     *
     * <p>与上一条的差别：那里目标没死，所以受击方不回能、攻击者也不回能；这里目标被打死，
     * 攻击者拿击杀回能（受击方已死，不再涨能量）。
     */
    @Test
    public void anyDamageTypeGrantsKillEnergyWhenItKills() {
        Character hero = character("hero", 120);

        Enemy byAdditional = dummy(50);
        byAdditional.setMaxEnergy(100);
        Battle b1 = newBattle(hero, byAdditional);
        b1.applyAdditionalDamage(hero, byAdditional, DamageElement.ICE, 100_000);
        Assertions.assertTrue(byAdditional.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "附加伤害击杀 → 攻击者拿击杀回能");

        hero.setCurrentEnergy(0);
        Enemy byTrue = dummy(50);
        byTrue.setMaxEnergy(100);
        Battle b2 = newBattle(hero, byTrue);
        b2.applyTrueDamage(hero, byTrue, DamageElement.ICE, 100_000);
        Assertions.assertTrue(byTrue.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "真实伤害击杀 → 攻击者拿击杀回能");
    }

    /**
     * DOT 击杀也回能（归属 DOT 的施加者）。DOT 段本身「算一次攻击」，这条本来就通，
     * 一并钉住以免将来把 DOT 也算进 {@code notCountsAsAttack}。
     */
    @Test
    public void dotKillGrantsEnergyToItsSource() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(100);
        victim.setMaxEnergy(100);
        Battle battle = newBattle(hero, victim);
        victim.addDot(new com.laosun.aluminium.models.Dot(hero, DamageElement.FIRE, 10_000, 1));

        battle.tickDots(victim);

        Assertions.assertTrue(victim.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "DOT 击杀 → 记给 DOT 的来源");
    }

    /**
     * **一次攻击行为只给受击方回一次能**（2026-09-19 口径）：一发把敌人打破时，
     * 技能段给受击方回能，派生出来的击破段 / 超击破段**不再**回能
     * （它们已置 {@code notCountsAsAttack()}）。
     *
     * <p>锚点：靶子 120 能量上限（受击回能基准 10）。用姬子战技（Fire Blast 中心削韧 60）
     * 打一个 30 韧性的弱火靶：一发既出技能伤害、又出击破伤害。
     */
    @Test
    public void oneAttackGrantsHitEnergyOnlyOnceEvenWhenItAlsoBreaks() {
        Enemy victim = dummy(1_000_000);
        victim.setMaxEnergy(120);
        victim.setStanceWeak(Set.of(DamageElement.FIRE));
        victim.setStance(30);
        victim.setMaxStance(30);
        Character hero = character("hero", 120);
        Battle battle = newBattle(hero, victim);

        battle.castImmediate(new DefaultSkill(1003, 2, 1), hero, List.of(victim));   // Blast/Fire，中心削韧 60

        Assertions.assertTrue(victim.isBroken(), "60 点削韧打空 30 点韧性");
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS,
                "只算一次受击回能（击破段派生，不再回能）");
    }

    /**
     * 同上，外加超击破段：一次攻击里有技能 + 击破 + 超击破三种伤害类型，
     * 受击方**仍然只回一次能**。
     */
    @Test
    public void superBreakSegmentAlsoDoesNotGrantExtraHitEnergy() {
        Enemy victim = dummy(1_000_000);
        victim.setMaxEnergy(120);
        victim.setStanceWeak(Set.of(DamageElement.FIRE));
        victim.setStance(30);
        victim.setMaxStance(30);
        Character hero = character("hero", 120);
        hero.getBuffManager().addBuff(new com.laosun.aluminium.models.buffs.SuperBreakBuff(3));
        Battle battle = newBattle(hero, victim);

        battle.castImmediate(new DefaultSkill(1003, 2, 1), hero, List.of(victim));

        Assertions.assertTrue(victim.isBroken());
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS,
                "技能 + 击破 + 超击破三段伤害，受击回能仍只有一次");
    }

    /**
     * DOT 不给受击方回能（它不是"一次攻击行为"）；但 DOT 击杀仍给施加者回能。
     */
    @Test
    public void dotDoesNotGrantHitEnergyButItsKillStillCreditsTheSource() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(100_000);
        victim.setMaxEnergy(120);
        Battle battle = newBattle(hero, victim);
        victim.addDot(new com.laosun.aluminium.models.Dot(hero, DamageElement.FIRE, 50, 1));

        battle.tickDots(victim);

        Assertions.assertFalse(victim.isDeath());
        Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS, "DOT 不是一次攻击行为 → 受击方不回能");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "没打死人 → 也没有击杀回能");
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
