package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「乐圮 / Shattered Home」: the boost applies only against a target whose HP share is above the threshold, and its size is
 * per rank.
 *
 * <p>⚠ Two ranks of the SAME light cone, because that is what makes the comparison clean: rank does not change the weapon's
 * stats, so the only difference is the boost the content states (0.20 versus 0.40). A ratio of 1.4/1.2 is the clause firing
 * with the right numbers; 1.0 would mean it never fires -- which is also how the ambiguous UNIT of `target_hp_percent` gets
 * settled, by measurement rather than by the validator accepting the spelling.
 *
 * <p>The generator is pinned at no-crit so a crit cannot swamp a 20% difference.
 */
public class LightConeHealthyTargetTest {
    private static final int WEAPON_ID = 20009;
    private static final int WEARER = 1210;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002064;

    @Test
    public void theBoostAgainstAHealthyTargetFollowsTheRank() {
        double rankOne = damage(1);
        double rankFive = damage(5);
        Assertions.assertTrue(rankOne > 0, "precondition: the attack lands: " + rankOne);
        double ratio = rankFive / rankOne;
        // \u26a0 Measured, not derived: the observable ratio is 1.1404494, because BOOST_DAMAGE adds into a zone that already
        // holds other contributions, so the naive 1.4/1.2 = 1.1667 compresses. The band is tight enough that a wrong boost
        // falls outside it (making both ranks equal gives 1.0).
        Assertions.assertTrue(ratio > 1.13 && ratio < 1.15,
                "rank 5 boosts 40% against rank 1's 20%, which is observable as ~1.1404: " + rankOne + " vs " + rankFive
                        + " -> " + ratio);
    }

    private static double damage(int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), noCrit);
        battle.startBattle();
        Assertions.assertEquals(1.0, enemy.getCurrentHp() / enemy.getMaxHp(), 1e-9,
                "precondition: the target is at full HP, which the clause requires for the boost to apply");
        double before = enemy.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    /**
     * The clause's negative half: below the threshold, the rank must not matter at all.
     *
     * <p>⚠ The fixture finds a monster it can WOUND without KILLING, by walking candidate ids and checking after each ally
     * attack that the target is still alive and now under half health. Without that self-check the test could pass by
     * measuring a corpse, or by never satisfying the condition it is about.
     */
    @Test
    // ⚠ This case PASSES but is NOT mutation-sensitive: changing the threshold from `> 0.5` to `> 0.0`
    // leaves it green, so it currently shows only that the two ranks agree on a wounded target -- which would
    // also hold if the condition never evaluated to true. See round 53 in GAPS.md; the probe that settles it is
    // named there (print `ctx.target()` and `hpPercent` inside TriggerTable's `target_hp_percent` case).
    public void belowTheThresholdTheRankStopsMattering() {
        double rankOne = damageAgainstWoundedTarget(1);
        double rankFive = damageAgainstWoundedTarget(5);
        Assertions.assertTrue(rankOne > 0, "precondition: the attack still lands on a wounded target: " + rankOne);
        Assertions.assertEquals(1.0, rankFive / rankOne, 1e-9,
                "below the threshold the boost is off for BOTH ranks, so the damage is identical: "
                        + rankOne + " vs " + rankFive);
    }

    /**
     * The wearer's skill damage against a target brought below the threshold -- and NOT killed by it.
     *
     * <p>⚠ The precondition is the point: an earlier version picked a target small enough to overkill, so the "damage" it
     * measured was the target's remaining HP and both ranks agreed no matter what the boost said (the round-34/39 fault).
     * Here the wearer's hit must leave the target alive with at least 60% of its pre-hit HP intact, or the case refuses to
     * judge at all.
     */
    private static double damageAgainstWoundedTarget(int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        int monster = woundableMonster(ally, wearer, noCrit);
        Enemy enemy = EnemyFactory.create(monster, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), noCrit);
        battle.startBattle();
        while (enemy.getCurrentHp() / enemy.getMaxHp() >= 0.5) {
            battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(enemy));
            Assertions.assertFalse(enemy.isDeath(), "the wounding must not kill the target (monster " + monster + ")");
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(),
                "the judged hit must not kill the target either (monster " + monster + ")");
        Assertions.assertTrue(dealt < 0.4 * before,
                "and it must be a real measurement, not the whole remaining bar: dealt " + dealt + " of " + before);
        return dealt;
    }

    /**
     * A monster an ally can wound below the threshold, that then survives the WEARER's skill with room to spare -- both
     * conditions are checked here, because only together do they make the negative judgement mean anything.
     */
    private static int woundableMonster(Character ally, Character wearer, Random noCrit) {
        for (int id = 1002010; id < 1002200; id++) {
            try {
                Enemy probe = EnemyFactory.create(id, 90, 1);
                Battle battle = new Battle(List.of(ally, wearer), List.of(probe), noCrit);
                battle.startBattle();
                for (int hit = 0; hit < 60 && !probe.isDeath() && probe.getCurrentHp() / probe.getMaxHp() >= 0.5; hit++) {
                    battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(probe));
                }
                if (probe.isDeath() || probe.getCurrentHp() / probe.getMaxHp() >= 0.5) {
                    continue;
                }
                double before = probe.getCurrentHp();
                battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(probe));
                if (!probe.isDeath() && (before - probe.getCurrentHp()) < 0.4 * before) {
                    return id;
                }
            } catch (RuntimeException ignored) {
                // not in the data: keep walking
            }
        }
        throw new IllegalStateException("no monster could be wounded without dying in the probe range");
    }
}
