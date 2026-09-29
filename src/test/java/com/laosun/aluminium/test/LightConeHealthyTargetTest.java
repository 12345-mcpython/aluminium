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
}
