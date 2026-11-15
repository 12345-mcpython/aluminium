package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Skill.StanceList;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.TestSkillGroup1;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * acceptance: one skill activation expands into the right hits / targets.
 *
 * <p>Attacker ATK = 100 and defender DEFENCE = 0 (defence zone 1.0), with no boost or crit
 * attributes, so a hit is exactly {@code ATK  x  multiplier}.
 *
 * <p>Real data used here is cid 1001 (March th): slot 1 = {@code SingleAttack} x 0.5,
 * slot 2 = {@code Defence} (shield, non-damaging), slot 3 = {@code AoEAttack}  x 0.9,
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
        return new Battle(List.of(attacker), enemies, BattleFixture.seeded());
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

    /**
     * Fabricated data (this character has no Blast / Bounce).
     */
    private static Skill fakeSkill(SkillEffectType effect, List<Double> params) {
        // ? The Bounce path reads its numbers from the DESCRIPTION (round 10): the share is the `%`-referenced parameter
        // and the hit count is the ADDITIONAL count ("additionally deals N instances"), so the fixture states both. params[1] is therefore the
        // additional count, not the total - the call sites below say 2 to mean three hits.
        String description = params.size() > 1
                ? "Deals DMG equal to #1[i]% of ATK to one enemy and additionally deals #2[i] instances of DMG."
                : "Deals DMG equal to #1[i]% of ATK to one enemy.";
        SkillData data = new SkillData(1, "Fake", List.of(params), new StanceList(0, 0, 0),
                DamageElement.ICE, effect, null, 30.0, null, description);   // bp_add is null, so the policy keeps its default
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
    public void attackSkillsHitTheRightTargets() {
        // Slot 1 is SingleAttack x 0.5: only the explicitly targeted enemy is hit
        Character singleAttacker = attacker();
        Enemy singleFirst = enemy("e1");
        Enemy singleSecond = enemy("e2");
        Battle singleBattle = battle(singleAttacker, List.of(singleFirst, singleSecond));

        singleBattle.castImmediate(singleAttack(), singleAttacker, List.of(singleFirst));

        // Slot 3 is AoEAttack x 0.9: every alive enemy is hit, not only the selected one
        Character aoeAttacker = attacker();
        Enemy aoeFirst = enemy("e1");
        Enemy aoeSecond = enemy("e2");
        Enemy aoeThird = enemy("e3");
        Battle aoeBattle = battle(aoeAttacker, List.of(aoeFirst, aoeSecond, aoeThird));

        aoeBattle.castImmediate(aoeAttack(), aoeAttacker, List.of(aoeSecond));

        // TestSkill1 uses slot 3's data (AoE x 0.9) - after delegation it should behave the same as DefaultSkill
        Character groupAttacker = attacker();
        Enemy groupFirst = enemy("e1");
        Enemy groupSecond = enemy("e2");
        Battle groupBattle = battle(groupAttacker, List.of(groupFirst, groupSecond));

        groupBattle.castImmediate(new TestSkillGroup1.TestSkill1(1), groupAttacker, List.of(groupFirst));

        Assertions.assertAll("attack skills hit the right targets",
                () -> Assertions.assertEquals(50, damageTaken(singleFirst), EPS,
                        "single-target: the main target is hit"),
                () -> Assertions.assertEquals(0, damageTaken(singleSecond), EPS,
                        "single-target: the other enemy takes no damage"),
                () -> Assertions.assertEquals(90, damageTaken(aoeFirst), EPS,
                        "aoe: the first enemy is hit"),
                () -> Assertions.assertEquals(90, damageTaken(aoeSecond), EPS,
                        "aoe: the selected enemy is hit"),
                () -> Assertions.assertEquals(90, damageTaken(aoeThird), EPS,
                        "aoe: the third enemy is hit"),
                () -> Assertions.assertEquals(90, damageTaken(groupFirst), EPS,
                        "skill group: delegation hits the first enemy"),
                () -> Assertions.assertEquals(90, damageTaken(groupSecond), EPS,
                        "skill group: delegation hits the second enemy"));
    }

    @Test
    public void nonDamagingOrEmptyParameterSkillsDealNoDamage() {
        // Slot 2 is a shield skill (Defence): its param 0.38 is not a damage multiplier and MUST NOT enter
        // the damage pipeline
        Character shieldAttacker = attacker();
        Enemy shieldTarget = enemy("e1");
        Battle shieldBattle = battle(shieldAttacker, List.of(shieldTarget));

        shieldBattle.castImmediate(new DefaultSkill(1001, 2, 1), shieldAttacker, List.of(shieldTarget));

        // Slot 6 is MazeAttack but param_list = [[]]: empty params MUST NOT throw
        Character mazeAttacker = attacker();
        Enemy mazeTarget = enemy("e1");
        Battle mazeBattle = battle(mazeAttacker, List.of(mazeTarget));

        mazeBattle.castImmediate(new DefaultSkill(1001, 6, 1), mazeAttacker, List.of(mazeTarget));

        Assertions.assertAll("non-damaging effects deal no damage",
                () -> Assertions.assertEquals(0, damageTaken(shieldTarget), EPS,
                        "shield (Defence): 0.38 is not a damage multiplier"),
                () -> Assertions.assertEquals(0, damageTaken(mazeTarget), EPS,
                        "empty params (MazeAttack): no damage and no throw"));
    }

    @Test
    public void blastHitsTheMainTargetAndItsBattlefieldNeighbours() {
        Character centreAttacker = attacker();
        Enemy centreFirst = enemy("e1");
        Enemy centreSecond = enemy("e2");
        Enemy centreThird = enemy("e3");
        Battle centreBattle = battle(centreAttacker, List.of(centreFirst, centreSecond, centreThird));
        Skill centreBlast = fakeSkill(SkillEffectType.BLAST, List.of(0.5));

        centreBattle.castImmediate(centreBlast, centreAttacker, List.of(centreSecond));   // main target = the middle to all three are hit

        Character edgeAttacker = attacker();
        Enemy edgeFirst = enemy("e1");
        Enemy edgeSecond = enemy("e2");
        Enemy edgeThird = enemy("e3");
        Battle edgeBattle = battle(edgeAttacker, List.of(edgeFirst, edgeSecond, edgeThird));
        Skill edgeBlast = fakeSkill(SkillEffectType.BLAST, List.of(0.5));

        edgeBattle.castImmediate(edgeBlast, edgeAttacker, List.of(edgeFirst));       // main target = leftmost to only the two on the left

        Assertions.assertAll("blast hits the main target and its battlefield neighbours",
                () -> Assertions.assertEquals(50, damageTaken(centreFirst), EPS,
                        "centre blast: the left neighbour is hit"),
                () -> Assertions.assertEquals(50, damageTaken(centreSecond), EPS,
                        "centre blast: the main target is hit"),
                () -> Assertions.assertEquals(50, damageTaken(centreThird), EPS,
                        "centre blast: the right neighbour is hit"),
                () -> Assertions.assertEquals(50, damageTaken(edgeFirst), EPS,
                        "edge blast: the main target is hit"),
                () -> Assertions.assertEquals(50, damageTaken(edgeSecond), EPS,
                        "edge blast: the only existing neighbour is hit"),
                () -> Assertions.assertEquals(0, damageTaken(edgeThird), EPS,
                        "edge blast: the enemy beyond the edge is not hit"));
    }

    @Test
    public void bounceHitsTheConfiguredNumberOfTimes() {
        Character countedAttacker = attacker();
        Enemy countedFirst = enemy("e1");
        Enemy countedSecond = enemy("e2");
        Enemy countedThird = enemy("e3");
        Battle countedBattle = battle(countedAttacker, List.of(countedFirst, countedSecond, countedThird));
        Skill countedBounce = fakeSkill(SkillEffectType.BOUNCE, List.of(0.5, 2.0));   // 2 additional = 3 hits

        countedBattle.castImmediate(countedBounce, countedAttacker, List.of(countedFirst));

        Character onceAttacker = attacker();
        Enemy onceFirst = enemy("e1");
        Enemy onceSecond = enemy("e2");
        Battle onceBattle = battle(onceAttacker, List.of(onceFirst, onceSecond));
        Skill onceBounce = fakeSkill(SkillEffectType.BOUNCE, List.of(0.5));

        onceBattle.castImmediate(onceBounce, onceAttacker, List.of(onceFirst));

        Assertions.assertAll("bounce hits the configured number of times",
                () -> Assertions.assertEquals(150,
                        damageTaken(countedFirst) + damageTaken(countedSecond) + damageTaken(countedThird), EPS,
                        "bounce with 2 additional hits: 3 hits x 50"),
                () -> Assertions.assertEquals(50, damageTaken(onceFirst) + damageTaken(onceSecond), EPS,
                        "bounce without a hit count: a single hit"));
    }

    @Test
    public void bounceRetargetsLivingEnemiesAndStopsWhenAllAreDead() {
        Character retargetAttacker = attacker();
        Enemy fragile = enemy("e1", 100);        // 100 per hit to dies in one hit
        Enemy tough = enemy("e2");
        Battle retargetBattle = battle(retargetAttacker, List.of(fragile, tough));
        Skill retargetBounce = fakeSkill(SkillEffectType.BOUNCE, List.of(1.0, 2.0));   // 2 additional = 3 hits

        retargetBattle.castImmediate(retargetBounce, retargetAttacker, List.of(fragile));

        Character stopAttacker = attacker();
        Enemy lastOne = enemy("e1", 100);
        Battle stopBattle = battle(stopAttacker, List.of(lastOne));
        Skill stopBounce = fakeSkill(SkillEffectType.BOUNCE, List.of(1.0, 2.0));   // 2 additional = 3 hits

        stopBattle.castImmediate(stopBounce, stopAttacker, List.of(lastOne));

        Assertions.assertAll("bounce retargets living enemies and stops when every target is dead",
                () -> Assertions.assertEquals(0, fragile.getCurrentHp(), EPS,
                        "retarget: the fragile main target dies"),
                () -> Assertions.assertEquals(300, damageTaken(fragile) + damageTaken(tough), EPS,
                        "retarget: no hit is wasted, the remaining hits bounce onto a living target"),
                () -> Assertions.assertEquals(100, damageTaken(lastOne), EPS,
                        "all dead: the remaining hits are voided, no hitting a corpse"));
    }

    @Test
    public void deadMainTargetDoesNotBlockAoe() {
        Character attacker = attacker();
        Enemy dead = enemy("e1");
        Enemy alive = enemy("e2");
        dead.takeDamage(HP);
        double corpseHp = dead.getCurrentHp();
        Battle battle = battle(attacker, List.of(dead, alive));

        battle.castImmediate(aoeAttack(), attacker, List.of(dead));

        Assertions.assertAll("a dead main target does not block AoE",
                () -> Assertions.assertTrue(dead.isDeath(), "fixture: the main target is a corpse"),
                () -> Assertions.assertEquals(corpseHp, dead.getCurrentHp(), EPS,
                        "corpse: a dead target takes no further damage"),
                () -> Assertions.assertEquals(90, damageTaken(alive), EPS,
                        "aoe: the living enemy is still hit"));
    }

    @Test
    public void invulnerableTargetTakesNoDamageWhileOthersStillDo() {
        Character attacker = attacker();
        Enemy transitioning = enemy("boss");
        transitioning.setInvulnerable(true);      // phase-transition invulnerability / HP-lock scripted sequence
        Enemy other = enemy("e2");
        Battle battle = battle(attacker, List.of(transitioning, other));

        battle.castImmediate(aoeAttack(), attacker, List.of(other));

        Assertions.assertAll("invulnerable target takes no damage while others still do",
                () -> Assertions.assertEquals(0, damageTaken(transitioning), EPS,
                        "invulnerable: no HP loss while invulnerable"),
                () -> Assertions.assertEquals(90, damageTaken(other), EPS,
                        "other targets: hit as usual"));
    }
}
