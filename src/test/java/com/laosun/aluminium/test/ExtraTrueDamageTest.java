package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.event.AttackEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P1-9 acceptance: additional damage (附加伤害) and true damage (真实伤害), reproduced from the
 * real kits of 1309 Robin and 1403 Tribbie (see {@code E:\code\blog\hsr\1309_知更鸟.md} /
 * {@code 1403_缇宝.md}).
 *
 * <p><b>Additional damage (附加伤害)</b> official definition: "makes the hit target take 1 extra
 * instance of damage; this damage is not considered to have dealt 1 attack"
 * → it goes through the full zones (its base is a panel value: ATK / MaxHP × multiplier) and is
 * flagged {@code notCountsAsAttack()}.
 *
 * <p><b>True damage (真伤)</b> (Tribbie E1): the base is derived from this attack's total damage
 * value, so it skips every zone.
 *
 * <p>Attackers are plain Lv80 characters with ATK 100 / MaxHP 1000 and no boost or crit panel
 * attributes; defenders below use DEFENCE 0 unless stated otherwise.
 */
public class ExtraTrueDamageTest {
    private static final double EPS = 1e-6;

    private static Character character() {
        return Character.fromAttributes("ally", 1000, 100, 100, 100);   // ATK 100 / MaxHP 1000
    }

    private static Enemy enemy(String name) {
        return enemy(name, 1_000_000, 0);
    }

    private static Enemy enemy(String name, double hp, double defence) {
        return Enemy.fromAttributes(name, hp, defence, 100, 100);
    }

    private static Battle battle(List<Character> allies, List<Enemy> enemies) {
        return new Battle(allies, enemies, new Random(0));
    }

    private static double damageTaken(Enemy enemy) {
        return enemy.getMaxHp() - enemy.getCurrentHp();
    }

    private static Skill singleAttack() {
        return new DefaultSkill(1001, 1, 1);       // real data: SingleAttack ×0.5
    }

    private static Skill aoeAttack() {
        return new DefaultSkill(1001, 3, 1);       // real data: AoEAttack ×0.9
    }

    @Test
    public void concertoStyleAdditionalDamageHitsTheMainTargetExactlyOnce() {
        Character robin = character();
        Character mainC = character();
        Enemy first = enemy("e1");
        Enemy main = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(List.of(robin, mainC), List.of(first, main, third));
        ConcertoBuff concerto = new ConcertoBuff(2, robin);
        robin.getBuffManager().addBuff(concerto);

        battle.castImmediate(aoeAttack(), mainC, List.of(main));    // main target = e2

        // mainC's AOE: 90 per enemy; additional damage triggers only 1 time and lands only on the main target
        // 120 (120% × Robin's ATK 100) × fixed crit 2.5 (100% crit rate / 150% crit DMG) = 300
        Assertions.assertEquals(90, damageTaken(first), EPS);
        Assertions.assertEquals(90 + 300, damageTaken(main), EPS);
        Assertions.assertEquals(90, damageTaken(third), EPS);
        Assertions.assertEquals(1, concerto.triggerCount, "triggers only once after each attack cast");
    }

    @Test
    public void fixedCritIsNotOverwrittenByThePanel() {
        Character robin = character();
        Character mainC = character();
        Enemy boss = enemy("boss");
        Battle battle = battle(List.of(robin, mainC), List.of(boss));
        robin.getBuffManager().addBuff(new ConcertoBuff(2, robin));

        // attacker's panel crit rate = 0: if assemble still rolled by the panel, additional damage would be only 120 instead of 300
        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        Assertions.assertEquals(50 + 300, damageTaken(boss), EPS);
    }

    @Test
    public void additionalDamageDoesNotRetriggerTheAttackEvent() {
        Character robin = character();
        Character mainC = character();
        Enemy boss = enemy("boss");
        Battle battle = battle(List.of(robin, mainC), List.of(boss));
        ConcertoBuff concerto = new ConcertoBuff(2, robin);
        robin.getBuffManager().addBuff(concerto);

        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        // the additional damage instance "is not considered to have dealt 1 attack" → no recursive trigger
        Assertions.assertEquals(1, concerto.triggerCount);
        Assertions.assertEquals(50 + 300, damageTaken(boss), EPS);
    }

    @Test
    public void zoneStyleZoneHitsTheHighestHpTargetOncePerHitTarget() {
        Character tribbie = character();
        Character mainC = character();
        Enemy high = enemy("e1", 300_000, 0);
        Enemy mid = enemy("e2", 200_000, 0);
        Enemy low = enemy("e3", 100_000, 0);
        Battle battle = battle(List.of(tribbie, mainC), List.of(high, mid, low));
        tribbie.getBuffManager().addBuff(new TribbieZoneBuff(2, tribbie));

        battle.castImmediate(aoeAttack(), mainC, List.of(mid));

        // AOE ×0.9 → 90 each; the zone fires "for each target that is attacked" → 3 times × (12% × MaxHP 1000 = 120)
        // each time it picks the "highest current HP among the hit targets" → all of them land on e1
        Assertions.assertEquals(90 + 360, damageTaken(high), EPS);
        Assertions.assertEquals(90, damageTaken(mid), EPS);
        Assertions.assertEquals(90, damageTaken(low), EPS);
    }

    @Test
    public void zoneE1StyleTrueDamageUsesTheAttackTotalAndIgnoresEveryZone() {
        Character tribbie = character();
        Character mainC = character();
        Enemy boss = enemy("boss", 1_000_000, 10_000);                    // DEFENCE 10000
        boss.setDamageResist(Map.of(DamageElement.QUANTUM, 0.9));         // quantum RES 0.9
        Battle battle = battle(List.of(tribbie, mainC), List.of(boss));
        tribbie.getBuffManager().addBuff(new TribbieE1Buff(2, tribbie));

        // mainC's basic attack (ice, ×0.5): 100 × 0.5 = 50 → defence zone 1000 / (10000 + 1000)
        double mainDamage = 50.0 * 1000.0 / 11_000.0;

        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        // true damage = this attack's total damage × 24% (D2 undecided: no overflow occurs here, so both readings agree)
        Assertions.assertEquals(mainDamage * 1.24, damageTaken(boss), 1e-9);
    }

    @Test
    public void additionalDamageStopsWhenTheMainDamageAlreadyKilledTheTarget() {
        Character robin = character();
        Character mainC = character();
        Enemy fragile = enemy("boss", 50, 0);        // mainC's basic attack is exactly 50 → killed on the spot
        Battle battle = battle(List.of(robin, mainC), List.of(fragile));
        ConcertoBuff concerto = new ConcertoBuff(2, robin);
        robin.getBuffManager().addBuff(concerto);

        battle.castImmediate(singleAttack(), mainC, List.of(fragile));

        // the event fires as usual (an attack did happen), but the main target is already dead → no more additional damage
        Assertions.assertEquals(1, concerto.triggerCount);
        Assertions.assertEquals(50, damageTaken(fragile), EPS);
    }

    @Test
    public void zoneStyleAdditionalDamageSkipsKilledTargetsAndFallsBackToSurvivors() {
        Character tribbie = character();
        Character mainC = character();
        Enemy fragile = enemy("e1", 50, 0);          // the highest current-HP target, but it will be killed by the main damage
        Enemy survivor = enemy("e2", 200_000, 0);
        Enemy low = enemy("e3", 100_000, 0);
        Battle battle = battle(List.of(tribbie, mainC), List.of(fragile, survivor, low));
        tribbie.getBuffManager().addBuff(new TribbieZoneBuff(2, tribbie));

        battle.castImmediate(aoeAttack(), mainC, List.of(survivor));

        // AOE 90 per enemy: e1 is killed → all 3 additional damage instances land on e2, the "currently alive + highest HP" one
        Assertions.assertEquals(50, damageTaken(fragile), EPS);          // a corpse takes no more damage
        Assertions.assertEquals(90 + 360, damageTaken(survivor), EPS);
        Assertions.assertEquals(90, damageTaken(low), EPS);
    }

    @Test
    public void zoneStyleAdditionalDamageDoesNotTriggerWhenEveryHitTargetDies() {
        Character tribbie = character();
        Character mainC = character();
        Enemy fragile = enemy("boss", 50, 0);
        Battle battle = battle(List.of(tribbie, mainC), List.of(fragile));
        tribbie.getBuffManager().addBuff(new TribbieZoneBuff(2, tribbie));

        battle.castImmediate(aoeAttack(), mainC, List.of(fragile));

        // every hit target dies → none of the 3 additional damage instances is produced (no retargeting to targets that were not attacked)
        Assertions.assertEquals(50, damageTaken(fragile), EPS);
    }

    @Test
    public void additionalDamageGoesThroughZonesWhileTrueDamageSkipsThem() {
        Character attacker = character();
        Enemy armoured = enemy("boss", 1_000_000, 1150);                  // DEFENCE 1150
        Battle battle = battle(List.of(attacker), List.of(armoured));

        double additional = battle.applyAdditionalDamage(attacker, armoured, DamageElement.PHYSICAL, 1000);
        double trueDamage = battle.applyTrueDamage(attacker, armoured, DamageElement.PHYSICAL, 1000);

        // additional damage's base is a panel value → goes through the defence zone; true damage → as-is
        Assertions.assertEquals(1000.0 * 1000.0 / 2150.0, additional, EPS);
        Assertions.assertEquals(1000, trueDamage, EPS);
    }

    // ==================================================================
    // "Character effects" embedded in the test: the real implementation is left to P8-3;
    // here they are reproduced with the documented values
    // ==================================================================

    /** Skeleton for a damage-reacting buff with no actual effect (applyEffect / removeBuff left empty; only the duration decreases). */
    private abstract static class DamageReactor extends AbstractBuff {
        protected final CanHit owner;

        private DamageReactor(int duration, CanHit owner) {
            super(duration, false);
            this.owner = owner;
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        /** The "highest current HP" survivor among the hit targets; null if all are dead. */
        protected static CanHit highestHpAlive(List<? extends CanHit> candidates) {
            CanHit best = null;
            for (CanHit candidate : candidates) {
                if (candidate.isDeath()) {
                    continue;
                }
                if (best == null || candidate.getCurrentHp() > best.getCurrentHp()) {
                    best = candidate;
                }
            }
            return best;
        }
    }

    /**
     * 1309 Robin 【协奏】: after an ally target casts an attack, deal 1 instance of physical
     * additional damage equal to 120% of her own ATK, with a fixed 100% crit rate / 150% crit DMG.
     * The target is the **main target** (D1(i)); if the main target is already dead, this instance is
     * skipped.
     */
    private static class ConcertoBuff extends DamageReactor implements AttackEvent {
        private int triggerCount;

        private ConcertoBuff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            triggerCount++;
            if (mainTarget == null || mainTarget.isDeath()) {
                return;                                   // D1(i): main target already dead → no damage this instance
            }
            double base = owner.getAttribute(AttributeType.ATTACK).get() * 1.2;
            Damage extra = new Damage(owner, mainTarget, DamageElement.PHYSICAL, DamageType.ADDITIONAL, base);
            extra.fixedCrit(true, 1.5).notCountsAsAttack();   // fixed 100% / 150%
            battle.applyDamage(mainTarget, extra);
        }
    }

    /**
     * 1403 Tribbie zone (结界): after an ally attacks, "for each target that is attacked", deal
     * 1 instance of quantum additional damage equal to 12% of Tribbie's MaxHP to the hit target with
     * the highest current HP.
     */
    private static class TribbieZoneBuff extends DamageReactor implements AttackEvent {
        private TribbieZoneBuff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            double base = owner.getMaxHp() * 0.12;
            for (int i = 0; i < hitTargets.size(); i++) {
                CanHit target = highestHpAlive(hitTargets);
                if (target == null) {
                    return;                               // all dead → the remaining instances are void
                }
                battle.applyAdditionalDamage(owner, target, DamageElement.QUANTUM, base);
            }
        }
    }

    /**
     * 1403 Tribbie E1: to the target (of the additional damage) deal extra true damage equal to 24%
     * of this attack's total damage value.
     * Here the main target is used as an approximation of "the target the additional damage was
     * dealt to" (TODO P8-3: wire it up through E1's full chain).
     */
    private static class TribbieE1Buff extends DamageReactor implements AttackEvent {
        private TribbieE1Buff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            if (mainTarget == null || mainTarget.isDeath()) {
                return;
            }
            battle.applyTrueDamage(owner, mainTarget, DamageElement.QUANTUM, totalDamage * 0.24);
        }
    }
}
