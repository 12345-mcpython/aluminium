package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P3-2 acceptance: energy gain is really wired into battle — skill casts / taking hits / kills gain
 * energy automatically, and the ultimate needs full energy, then clears to zero before regaining 5.
 *
 * <p>Anchors (ROADMAP P3-0 convention 2): basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 / kill 5.
 */
public class EnergyBattleTest {
    private static final double EPS = 1e-6;

    @Test
    public void normalAttackAndSkillGrantEnergyToTheCaster() {
        Character hero = character("hero", 120);
        Enemy dummy = dummy(1_000_000);
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));   // basic attack (damaging skill)
        Assertions.assertEquals(20, hero.getCurrentEnergy(), EPS);

        // cid 1001 slot 2 is a shield (non-damaging skill): SkillExecutor returns early, but energy must still be granted
        battle.castImmediate(new DefaultSkill(1001, 2, 1), hero, List.of(dummy));
        Assertions.assertEquals(50, hero.getCurrentEnergy(), EPS, "20 + 30: a non-damaging skill also grants energy");
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

        Assertions.assertFalse(battle.castUltra(hero, battle.enemies), "energy not full → cannot cast");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS);

        hero.setCurrentEnergy(120);
        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "order defined: clear to zero first, then regain 5 for itself");
    }

    /**
     * H-5: the clear to zero must happen **before the ultimate body is settled**.
     *
     * <p>Otherwise the kill energy gain given by the enemy the ultimate kills (credited to
     * {@code damage.getAttacker()}, i.e. the one casting the ultimate) would be wiped out by the
     * subsequent {@code setCurrentEnergy(0)} — it should be 5 (kill) + 5 (ultimate) = 10, but only 5
     * would remain.
     */
    @Test
    public void ultraKeepsTheKillEnergyItEarned() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));   // AoE, multiplier 0.9 → 100 × 0.9 = 90
        Enemy victim = dummy(50);
        Battle battle = newBattle(hero, victim);
        hero.setCurrentEnergy(120);

        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertTrue(victim.isDeath(), "this ultimate MUST kill on the spot, otherwise the kill energy gain cannot be measured");
        Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS, "kill 5 + ultimate's own 5, must not be eaten by the clear to zero");
    }

    /**
     * H-5's break branch: the break energy gain given when the ultimate drains the toughness must
     * likewise not be wiped out by the clear to zero.
     */
    @Test
    public void ultraKeepsTheBreakEnergyItEarned() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1003, 3, 1));   // Himeko's ultimate: Fire, AoE, all=60
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);            // weak to Fire, toughness 60, high HP
        Battle battle = newBattle(hero, iceEdge);
        hero.setCurrentEnergy(120);

        Assertions.assertTrue(battle.castUltra(hero, battle.enemies));
        Assertions.assertTrue(iceEdge.isBroken(), "60 points of toughness reduction drains 60 toughness → break");
        Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS, "break 5 + ultimate's own 5, must not be eaten by the clear to zero");
    }

    @Test
    public void takingHitGrantsEnergyAndKillGrantsItToTheAttacker() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(500);
        victim.setMaxEnergy(100);
        Battle battle = newBattle(hero, victim);

        battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 10));
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS, "taking a hit grants 10 energy");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "merely hitting (not killing) grants the attacker no energy");

        battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 100_000));
        Assertions.assertTrue(victim.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "the kill energy gain is credited to damage.getAttacker()");
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
                "additional damage / true damage is 'not treated as having dealt 1 attack' → the target gains no energy");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "nobody was killed → no kill energy gain either");
    }

    /**
     * **Kill energy gain is independent of damage type** (2026-09-19 convention): any damage
     * attributed to a character, as long as it kills a monster, settles kill energy gain for the
     * attacker — including additional damage and true damage, which are "not treated as one attack".
     *
     * <p>Difference from the previous test: there the target did not die, so the target gained no
     * energy and the attacker gained none either; here the target is killed, so the attacker gets
     * the kill energy gain (the target is already dead and no longer gains energy).
     */
    @Test
    public void anyDamageTypeGrantsKillEnergyWhenItKills() {
        Character hero = character("hero", 120);

        Enemy byAdditional = dummy(50);
        byAdditional.setMaxEnergy(100);
        Battle b1 = newBattle(hero, byAdditional);
        b1.applyAdditionalDamage(hero, byAdditional, DamageElement.ICE, 100_000);
        Assertions.assertTrue(byAdditional.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "additional damage kill → the attacker gets the kill energy gain");

        hero.setCurrentEnergy(0);
        Enemy byTrue = dummy(50);
        byTrue.setMaxEnergy(100);
        Battle b2 = newBattle(hero, byTrue);
        b2.applyTrueDamage(hero, byTrue, DamageElement.ICE, 100_000);
        Assertions.assertTrue(byTrue.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "true damage kill → the attacker gets the kill energy gain");
    }

    /**
     * A DOT kill also gains energy (attributed to the DOT's applier). The DOT segment itself
     * "counts as one attack", so this already worked; it is pinned down here as well so that DOT is
     * not later lumped into {@code notCountsAsAttack}.
     */
    @Test
    public void dotKillGrantsEnergyToItsSource() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(100);
        victim.setMaxEnergy(100);
        Battle battle = newBattle(hero, victim);
        victim.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(hero, DamageElement.FIRE, 10_000, 1));

        battle.tickDots(victim);

        Assertions.assertTrue(victim.isDeath());
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "DOT kill → credited to the DOT's source");
    }

    /**
     * **One attack action grants the target only one energy gain** (2026-09-19 convention): when one
     * hit breaks an enemy, the skill segment grants the target energy, and the derived break segment /
     * super break segment **no longer** grants energy (they already have
     * {@code notCountsAsAttack()} set).
     *
     * <p>Anchor: the dummy has a 120 energy cap (so the taking-a-hit energy baseline is 10). Himeko's
     * skill (Fire Blast, 60 center toughness reduction) is used on a Fire-weak dummy with 30
     * toughness: one hit produces both skill damage and break damage.
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

        battle.castImmediate(new DefaultSkill(1003, 2, 1), hero, List.of(victim));   // Blast/Fire, 60 center toughness reduction

        Assertions.assertTrue(victim.isBroken(), "60 points of toughness reduction drains 30 toughness");
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS,
                "only one taking-a-hit energy gain counts (the break segment is derived and no longer grants energy)");
    }

    /**
     * Same as above, plus a super break segment: one attack contains three damage types — skill +
     * break + super break — and the target **still gains energy only once**.
     */
    @Test
    public void superBreakSegmentAlsoDoesNotGrantExtraHitEnergy() {
        Enemy victim = dummy(1_000_000);
        victim.setMaxEnergy(120);
        victim.setStanceWeak(Set.of(DamageElement.FIRE));
        victim.setStance(30);
        victim.setMaxStance(30);
        Character hero = character("hero", 120);
        hero.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.SuperBreakBuff(3));
        Battle battle = newBattle(hero, victim);

        battle.castImmediate(new DefaultSkill(1003, 2, 1), hero, List.of(victim));

        Assertions.assertTrue(victim.isBroken());
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS,
                "three segments of damage — skill + break + super break — yet the taking-a-hit energy gain is still only one");
    }

    /**
     * DOT does not grant the target energy (it is not "one attack action"); but a DOT kill still
     * grants the applier energy.
     */
    @Test
    public void dotDoesNotGrantHitEnergyButItsKillStillCreditsTheSource() {
        Character hero = character("hero", 120);
        Enemy victim = dummy(100_000);
        victim.setMaxEnergy(120);
        Battle battle = newBattle(hero, victim);
        victim.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(hero, DamageElement.FIRE, 50, 1));

        battle.tickDots(victim);

        Assertions.assertFalse(victim.isDeath());
        Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS, "DOT is not one attack action → the target gains no energy");
        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "nobody was killed → no kill energy gain either");
    }

    @Test
    public void entityWithoutEnergyBarNeverGainsAndCannotCastUltra() {
        Character hero = character("hero", 0);                     // no energy bar (the 1407 Castorice kind)
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));
        Enemy dummy = dummy(1_000_000);
        dummy.setMaxEnergy(0);
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));
        battle.applyDamage(hero, new Damage(dummy, hero, DamageElement.ICE, DamageType.NORMAL, 10));

        Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS);
        Assertions.assertEquals(0, dummy.getCurrentEnergy(), EPS);
        Assertions.assertFalse(hero.hasEnergyBar());
        Assertions.assertFalse(battle.castUltra(hero, List.of(dummy)), "with no energy bar the ultimate can never be cast");
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

        Assertions.assertEquals(7, hero.getCurrentEnergy(), EPS, "the skill energy gain reads the caster's own provider");
        Assertions.assertEquals(99, dummy.getCurrentEnergy(), EPS, "the taking-a-hit energy gain reads the target's own provider");
    }

    @Test
    public void breakEnergyGoesToTheBreaker() {
        Character hero = character("hero", 120);
        Enemy dummy = dummy(1_000_000);
        Battle battle = newBattle(hero, dummy);

        Assertions.assertEquals(5, battle.gainBreakEnergy(hero, dummy), EPS);
        Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "break energy gain baseline 5 (P4-4 calls this opening on break)");
        Assertions.assertEquals(0, battle.gainBreakEnergy(null, dummy), EPS, "no breaker → 0, does not blow up");
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
