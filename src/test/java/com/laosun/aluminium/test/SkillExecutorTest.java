package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Skill.StanceList;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.models.tests.TestSkillGroup1;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P1-8 acceptance: one skill activation expands into the right hits / targets.
 *
 * <p>Attacker ATK = 100 and defender DEFENCE = 0 (defence zone 1.0), with no boost or crit
 * attributes, so a hit is exactly {@code ATK × multiplier}.
 *
 * <p>Real data used here is cid 1001 (March 7th): slot 1 = {@code SingleAttack} ×0.5,
 * slot 2 = {@code Defence} (shield, non-damaging), slot 3 = {@code AoEAttack} ×0.9,
 * slot 6 = {@code MazeAttack} with empty params. Blast / Bounce do not exist for this
 * character, so those two use a hand-made {@link SkillData} (// TODO data).
 */
public class SkillExecutorTest {
    private static final double EPS = 1e-6;
    private static final double HP = 100000.0;

    private static Character attacker() {
        return Character.fromAttributes("attacker", 1000, 100, 100, 100);
    }

    private static Enemy enemy(String name) {
        return enemy(name, HP);
    }

    private static Enemy enemy(String name, double hp) {
        return Enemy.fromAttributes(name, hp, 0, 100, 100);
    }

    private static Battle battle(Character attacker, List<Enemy> enemies) {
        return new Battle(List.of(attacker), enemies, new Random(0));
    }

    private static double damageTaken(Enemy enemy) {
        return enemy.getMaxHp() - enemy.getCurrentHp();   // use each one's own max HP, do not assume they are all the HP constant
    }

    private static Skill singleAttack() {
        return new DefaultSkill(1001, 1, 1);            // real data: multiplier 0.5
    }

    private static Skill aoeAttack() {
        return new DefaultSkill(1001, 3, 1);            // real data: multiplier 0.9
    }

    /** Fabricated data (this character has no Blast / Bounce). */
    private static Skill fakeSkill(SkillEffectType effect, List<Double> params) {
        SkillData data = new SkillData(1, "Fake", List.of(params), new StanceList(0, 0, 0),
                DamageElement.ICE, effect, null, 30.0, null);
        return new Skill() {
            @Override
            public int getLevel() {
                return 1;
            }

            @Override
            public SkillData getData() {
                return data;
            }

            @Override
            public void execute(Battle battle, CanHit user, List<? extends CanHit> targets) {
                SkillExecutor.execute(battle, this, user, targets);
            }
        };
    }

    @Test
    public void singleAttackHitsOnlyTheMainTarget() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Battle battle = battle(attacker, List.of(first, second));

        battle.castImmediate(singleAttack(), attacker, List.of(first));

        Assertions.assertEquals(50, damageTaken(first), EPS);
        Assertions.assertEquals(0, damageTaken(second), EPS);
    }

    @Test
    public void aoeHitsEveryAliveEnemy() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(attacker, List.of(first, second, third));

        battle.castImmediate(aoeAttack(), attacker, List.of(second));

        Assertions.assertEquals(90, damageTaken(first), EPS);
        Assertions.assertEquals(90, damageTaken(second), EPS);
        Assertions.assertEquals(90, damageTaken(third), EPS);
    }

    @Test
    public void nonDamagingSkillDealsNoDamage() {
        Character attacker = attacker();
        Enemy target = enemy("e1");
        Battle battle = battle(attacker, List.of(target));

        // Slot 2 is a shield skill (Defence): its param 0.38 is not a damage multiplier and MUST NOT enter
        // the damage pipeline
        battle.castImmediate(new DefaultSkill(1001, 2, 1), attacker, List.of(target));

        Assertions.assertEquals(0, damageTaken(target), EPS);
    }

    @Test
    public void emptyParametersDealNoDamage() {
        Character attacker = attacker();
        Enemy target = enemy("e1");
        Battle battle = battle(attacker, List.of(target));

        // Slot 6 is MazeAttack but param_list = [[]]: empty params MUST NOT throw
        battle.castImmediate(new DefaultSkill(1001, 6, 1), attacker, List.of(target));

        Assertions.assertEquals(0, damageTaken(target), EPS);
    }

    @Test
    public void blastHitsTheMainTargetAndItsBattlefieldNeighbours() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(attacker, List.of(first, second, third));
        Skill blast = fakeSkill(SkillEffectType.BLAST, List.of(0.5));

        battle.castImmediate(blast, attacker, List.of(second));      // main target = the middle → all three are hit

        Assertions.assertEquals(50, damageTaken(first), EPS);
        Assertions.assertEquals(50, damageTaken(second), EPS);
        Assertions.assertEquals(50, damageTaken(third), EPS);
    }

    @Test
    public void blastFromAnEdgeHitsOnlyTwoNeighbours() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(attacker, List.of(first, second, third));
        Skill blast = fakeSkill(SkillEffectType.BLAST, List.of(0.5));

        battle.castImmediate(blast, attacker, List.of(first));       // main target = leftmost → only the two on the left

        Assertions.assertEquals(50, damageTaken(first), EPS);
        Assertions.assertEquals(50, damageTaken(second), EPS);
        Assertions.assertEquals(0, damageTaken(third), EPS);
    }

    @Test
    public void bounceHitsTheParamCountTimes() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(attacker, List.of(first, second, third));
        Skill bounce = fakeSkill(SkillEffectType.BOUNCE, List.of(0.5, 3.0));

        battle.castImmediate(bounce, attacker, List.of(first));

        double total = damageTaken(first) + damageTaken(second) + damageTaken(third);
        Assertions.assertEquals(150, total, EPS);                    // 3 hits × 50
    }

    @Test
    public void bounceWithoutHitCountHitsOnce() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Battle battle = battle(attacker, List.of(first, second));
        Skill bounce = fakeSkill(SkillEffectType.BOUNCE, List.of(0.5));

        battle.castImmediate(bounce, attacker, List.of(first));

        Assertions.assertEquals(50, damageTaken(first) + damageTaken(second), EPS);
    }

    @Test
    public void deadMainTargetDoesNotBlockAoe() {
        Character attacker = attacker();
        Enemy dead = enemy("e1");
        Enemy alive = enemy("e2");
        dead.takeDamage(HP);
        Assertions.assertTrue(dead.isDeath());
        double corpseHp = dead.getCurrentHp();
        Battle battle = battle(attacker, List.of(dead, alive));

        battle.castImmediate(aoeAttack(), attacker, List.of(dead));

        Assertions.assertEquals(corpseHp, dead.getCurrentHp(), EPS);  // a corpse takes no further damage
        Assertions.assertEquals(90, damageTaken(alive), EPS);         // the living one is still hit
    }

    @Test
    public void testSkillGroupDelegatesToTheExecutor() {
        Character attacker = attacker();
        Enemy first = enemy("e1");
        Enemy second = enemy("e2");
        Battle battle = battle(attacker, List.of(first, second));

        // TestSkill1 uses slot 3's data (AoE ×0.9) — after delegation it should behave the same as DefaultSkill
        battle.castImmediate(new TestSkillGroup1.TestSkill1(1), attacker, List.of(first));

        Assertions.assertEquals(90, damageTaken(first), EPS);
        Assertions.assertEquals(90, damageTaken(second), EPS);
    }

    @Test
    public void bounceRetargetsLivingEnemiesInsteadOfWastingHits() {
        Character attacker = attacker();
        Enemy fragile = enemy("e1", 100);        // 100 per hit → dies in one hit
        Enemy tough = enemy("e2");
        Battle battle = battle(attacker, List.of(fragile, tough));
        Skill bounce = fakeSkill(SkillEffectType.BOUNCE, List.of(1.0, 3.0));

        battle.castImmediate(bounce, attacker, List.of(fragile));

        Assertions.assertEquals(0, fragile.getCurrentHp(), EPS);
        // Not one of the 3 hits is wasted: after a mid-way kill it must bounce onto a living target again
        Assertions.assertEquals(300, damageTaken(fragile) + damageTaken(tough), EPS);
    }

    @Test
    public void bounceStopsWhenEveryTargetIsDead() {
        Character attacker = attacker();
        Enemy fragile = enemy("e1", 100);
        Battle battle = battle(attacker, List.of(fragile));
        Skill bounce = fakeSkill(SkillEffectType.BOUNCE, List.of(1.0, 3.0));

        battle.castImmediate(bounce, attacker, List.of(fragile));

        // No living target left → the remaining hits are voided (no hitting a corpse, and no wasted cast)
        Assertions.assertEquals(100, damageTaken(fragile), EPS);
    }

    @Test
    public void invulnerableTargetTakesNoDamageWhileOthersStillDo() {
        Character attacker = attacker();
        Enemy transitioning = enemy("boss");
        transitioning.setInvulnerable(true);      // phase-transition invulnerability / HP-lock scripted sequence (转阶段无敌、锁血演出)
        Enemy other = enemy("e2");
        Battle battle = battle(attacker, List.of(transitioning, other));

        battle.castImmediate(aoeAttack(), attacker, List.of(other));

        Assertions.assertEquals(0, damageTaken(transitioning), EPS);   // no HP loss while invulnerable
        Assertions.assertEquals(90, damageTaken(other), EPS);          // the other targets are hit as usual
    }
}
