package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * acceptance: energy gain is really wired into battle - skill casts / taking hits / kills gain
 * energy automatically, and the ultimate needs full energy, then clears to zero before regaining 5.
 *
 * <p>Anchors (this project's energy convention): basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 / kill 5.
 */
public class EnergyBattleTest {
    private static final double EPS = 1e-6;

    /**
     * A skill cast grants the caster energy according to that caster's own energy rules: a damaging
     * basic attack grants 20, a non-damaging skill grants 30 even though its executor returns early,
     * the energy regeneration rate scales the gain, and the {@link EnergyProvider} hooks of the
     * caster / target replace the hardcoded constants.
     */
    @Test
    public void skillCastEnergyGainUsesTheCasterEnergyRules() {
        Assertions.assertAll("skill cast grants energy to the caster",
                () -> {
                    Character hero = character("hero", 120);
                    Enemy dummy = dummy(1_000_000);
                    Battle battle = newBattle(hero, dummy);

                    battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));   // basic attack (damaging skill)
                    Assertions.assertEquals(20, hero.getCurrentEnergy(), EPS, "basic attack grants 20");

                    // cid 1001 slot 2 is a shield (non-damaging skill): SkillExecutor returns early, but energy must still be granted
                    battle.castImmediate(new DefaultSkill(1001, 2, 1), hero, List.of(dummy));
                    Assertions.assertEquals(50, hero.getCurrentEnergy(), EPS, "20 + 30: a non-damaging skill also grants energy");
                },
                () -> {
                    Character hero = character("hero", 120);
                    hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
                    Battle battle = newBattle(hero, dummy(1_000_000));

                    battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, battle.enemies());

                    Assertions.assertEquals(30, hero.getCurrentEnergy(), EPS, "20 × (1 + 0.5)");
                },
                () -> {
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
        );
    }

    /**
     * The ultimate needs full energy: it cannot be cast at all below that, and once cast the order is
     * defined - clear the caster's energy to zero first, then regain 5 for itself.
     */
    @Test
    public void ultimateNeedsFullEnergyThenClearsAndRegainsFive() {
        Character hero = character("hero", 120);
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));
        Battle battle = newBattle(hero, dummy(1_000_000));

        Assertions.assertAll("ultimate needs full energy, then clears and regains 5",
                () -> Assertions.assertFalse(battle.castUltra(hero, battle.enemies()), "energy not full → cannot cast"),
                () -> Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "cannot cast → energy stays 0"),
                () -> {
                    hero.setCurrentEnergy(120);
                    Assertions.assertTrue(battle.castUltra(hero, battle.enemies()), "full energy → the ultimate is cast");
                },
                () -> Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS,
                        "order defined: clear to zero first, then regain 5 for itself")
        );
    }

    /**
     * The clear to zero must happen before the ultimate body is settled, otherwise the secondary
     * energy gains earned by the ultimate itself would be wiped out by the subsequent
     * {@code setCurrentEnergy(0)}. Two independent paths prove it: the kill energy gain given by the
     * enemy the ultimate kills (credited to {@code damage.getAttacker()}, i.e. the caster) must leave
     * 5 (kill) + 5 (ultimate) = 10, and the break energy gain given when the ultimate drains the
     * toughness must likewise leave 5 (break) + 5 (ultimate) = 10.
     */
    @Test
    public void ultimateKeepsTheSecondaryEnergyItEarned() {
        Assertions.assertAll("the clear to zero does not eat the energy the ultimate itself earned",
                () -> {
                    Character hero = character("hero", 120);
                    hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));   // AoE, multiplier 0.9 to 100  x  0.9 = 90
                    Enemy victim = dummy(50);
                    Battle battle = newBattle(hero, victim);
                    hero.setCurrentEnergy(120);

                    Assertions.assertTrue(battle.castUltra(hero, battle.enemies()), "full energy → the ultimate is cast");
                    Assertions.assertTrue(victim.isDeath(),
                            "this ultimate MUST kill on the spot, otherwise the kill energy gain cannot be measured");
                    Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS,
                            "kill 5 + ultimate's own 5, must not be eaten by the clear to zero");
                },
                () -> {
                    Character hero = character("hero", 120);
                    hero.setSkill(SkillType.ULTRA, new DefaultSkill(1003, 3, 1));   // Himeko's ultimate: Fire, AoE, all=60
                    Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);            // weak to Fire, toughness 60, high HP
                    Battle battle = newBattle(hero, iceEdge);
                    hero.setCurrentEnergy(120);

                    Assertions.assertTrue(battle.castUltra(hero, battle.enemies()), "full energy → the ultimate is cast");
                    Assertions.assertTrue(iceEdge.isBroken(), "60 points of toughness reduction drains 60 toughness → break");
                    Assertions.assertEquals(10, hero.getCurrentEnergy(), EPS,
                            "break 5 + ultimate's own 5, must not be eaten by the clear to zero");
                }
        );
    }

    /**
     * Energy settlement on damage application: taking a hit grants the target 10, merely hitting (not
     * killing) grants the attacker nothing, and any damage attributed to a character that kills
     * settles the kill energy gain of 5 for that attacker - including additional damage and true
     * damage, which are "not treated as one attack" and therefore grant the target no energy.
     */
    @Test
    public void damageSettlementGrantsHitEnergyAndKillEnergy() {
        Assertions.assertAll("taking-a-hit / kill / non-attack damage energy",
                () -> {
                    Character hero = character("hero", 120);
                    Enemy victim = dummy(500);
                    victim.setMaxEnergy(100);
                    Battle battle = newBattle(hero, victim);

                    battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 10));
                    Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS, "taking a hit grants 10 energy");
                    Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "merely hitting (not killing) grants the attacker no energy");

                    battle.applyDamage(victim, new Damage(hero, victim, DamageElement.ICE, DamageType.NORMAL, 100_000));
                    Assertions.assertTrue(victim.isDeath(), "the 100000 hit kills the victim");
                    Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "the kill energy gain is credited to damage.getAttacker()");
                },
                () -> {
                    Character hero = character("hero", 120);
                    Enemy victim = dummy(1_000_000);
                    victim.setMaxEnergy(100);
                    Battle battle = newBattle(hero, victim);

                    battle.applyAdditionalDamage(hero, victim, DamageElement.ICE, 100);
                    battle.applyTrueDamage(hero, victim, DamageElement.ICE, 100);

                    Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS,
                            "additional damage / true damage is 'not treated as having dealt 1 attack' → the target gains no energy");
                    Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "nobody was killed → no kill energy gain either");
                },
                () -> {
                    Character hero = character("hero", 120);

                    Enemy byAdditional = dummy(50);
                    byAdditional.setMaxEnergy(100);
                    Battle b1 = newBattle(hero, byAdditional);
                    b1.applyAdditionalDamage(hero, byAdditional, DamageElement.ICE, 100_000);
                    Assertions.assertTrue(byAdditional.isDeath(), "the additional damage kills the victim");
                    Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS,
                            "additional damage kill → the attacker gets the kill energy gain");

                    hero.setCurrentEnergy(0);
                    Enemy byTrue = dummy(50);
                    byTrue.setMaxEnergy(100);
                    Battle b2 = newBattle(hero, byTrue);
                    b2.applyTrueDamage(hero, byTrue, DamageElement.ICE, 100_000);
                    Assertions.assertTrue(byTrue.isDeath(), "the true damage kills the victim");
                    Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS,
                            "true damage kill → the attacker gets the kill energy gain");
                }
        );
    }

    /**
     * DOT energy rules: a DOT segment is not "one attack action", so it grants the target no
     * taking-a-hit energy; a DOT kill still grants the applier the kill energy gain. The kill case is
     * pinned down so that DOT is not later lumped into {@code notCountsAsAttack}.
     */
    @Test
    public void dotGrantsNoHitEnergyButItsKillCreditsTheSource() {
        Assertions.assertAll("DOT grants no hit energy, but a DOT kill credits its source",
                () -> {
                    Character hero = character("hero", 120);
                    Enemy victim = dummy(100);
                    victim.setMaxEnergy(100);
                    Battle battle = newBattle(hero, victim);
                    victim.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(hero, DamageElement.FIRE, 10_000, 1));

                    battle.tickDots(victim);

                    Assertions.assertTrue(victim.isDeath(), "the 10000 DOT damage kills the 100 HP victim");
                    Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "DOT kill → credited to the DOT's source");
                },
                () -> {
                    Character hero = character("hero", 120);
                    Enemy victim = dummy(100_000);
                    victim.setMaxEnergy(120);
                    Battle battle = newBattle(hero, victim);
                    victim.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(hero, DamageElement.FIRE, 50, 1));

                    battle.tickDots(victim);

                    Assertions.assertFalse(victim.isDeath(), "50 DOT damage does not kill the 100000 HP victim");
                    Assertions.assertEquals(0, victim.getCurrentEnergy(), EPS, "DOT is not one attack action → the target gains no energy");
                    Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "nobody was killed → no kill energy gain either");
                }
        );
    }

    /**
     * One attack action grants the target only one energy gain: when one hit breaks an enemy, the
     * skill segment grants the target energy, and the derived break segment / super break segment no
     * longer grants energy (they already have {@code notCountsAsAttack()} set).
     *
     * <p>Anchor: the dummy has a 120 energy cap (so the taking-a-hit energy baseline is 10). Himeko's
     * skill (Fire Blast, 60 center toughness reduction) is used on a Fire-weak dummy with 30
     * toughness: one hit produces both skill damage and break damage.
     */
    @Test
    public void oneAttackGrantsHitEnergyOnlyOnceEvenWhenItBreaks() {
        Assertions.assertAll("one attack → only one taking-a-hit energy gain",
                () -> {
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
                },
                () -> {
                    // one attack contains three damage types - skill + break + super break - and the target still gains energy only once
                    Enemy victim = dummy(1_000_000);
                    victim.setMaxEnergy(120);
                    victim.setStanceWeak(Set.of(DamageElement.FIRE));
                    victim.setStance(30);
                    victim.setMaxStance(30);
                    Character hero = character("hero", 120);
                    hero.getBuffManager().addBuff(new com.laosun.aluminium.buff.SuperBreakBuff(3));
                    Battle battle = newBattle(hero, victim);

                    battle.castImmediate(new DefaultSkill(1003, 2, 1), hero, List.of(victim));

                    Assertions.assertTrue(victim.isBroken(), "the hit drains 30 toughness → break");
                    Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS,
                            "three segments of damage — skill + break + super break — yet the taking-a-hit energy gain is still only one");
                }
        );
    }

    /**
     * An entity without an energy bar (the 140 Castorice kind) never gains energy - neither by
     * casting nor by taking a hit - and its ultimate can never be cast.
     */
    @Test
    public void entityWithoutEnergyBarNeverGainsAndCannotCastUltra() {
        Character hero = character("hero", 0);                     // no energy bar (the 140Castorice kind)
        hero.setSkill(SkillType.ULTRA, new DefaultSkill(1001, 3, 1));
        Enemy dummy = dummy(1_000_000);
        dummy.setMaxEnergy(0);
        Battle battle = newBattle(hero, dummy);

        battle.castImmediate(new DefaultSkill(1001, 1, 1), hero, List.of(dummy));
        battle.applyDamage(hero, new Damage(dummy, hero, DamageElement.ICE, DamageType.NORMAL, 10));

        Assertions.assertAll("no energy bar → no energy, no ultimate",
                () -> Assertions.assertEquals(0, hero.getCurrentEnergy(), EPS, "no energy bar → the caster gains no energy"),
                () -> Assertions.assertEquals(0, dummy.getCurrentEnergy(), EPS, "no energy bar → the target gains no energy"),
                () -> Assertions.assertFalse(hero.hasEnergyBar(), "the hero has no energy bar"),
                () -> Assertions.assertFalse(battle.castUltra(hero, List.of(dummy)), "with no energy bar the ultimate can never be cast")
        );
    }

    /**
     * Break energy goes to the breaker: the baseline gain is 5 and is cashed in on the breaker's
     * energy, no breaker returns 0 without blowing up, and the breaker's energy regeneration rate
     * scales the gain.
     */
    @Test
    public void breakEnergyGoesToTheBreakerAndScalesWithEnergyRegenerationRate() {
        Assertions.assertAll("break energy gain",
                () -> {
                    Character hero = character("hero", 120);
                    Enemy dummy = dummy(1_000_000);
                    Battle battle = newBattle(hero, dummy);

                    Assertions.assertEquals(5, battle.gainBreakEnergy(hero, dummy), EPS, "break energy gain baseline 5");
                    Assertions.assertEquals(5, hero.getCurrentEnergy(), EPS, "break energy gain baseline 5 (crediting it opens on break)");
                    Assertions.assertEquals(0, battle.gainBreakEnergy(null, dummy), EPS, "no breaker → 0, does not blow up");
                },
                () -> {
                    Character hero = character("hero", 120);
                    hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.5));
                    Battle battle = newBattle(hero, dummy(1_000_000));

                    Assertions.assertEquals(7.5, battle.gainBreakEnergy(hero, battle.enemies().getFirst()), EPS,
                            "5 × (1 + 0.5), scaled by the breaker's energy regeneration rate");
                }
        );
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
        return BattleFixture.battle(List.of(hero), List.of(enemy));
    }
}
