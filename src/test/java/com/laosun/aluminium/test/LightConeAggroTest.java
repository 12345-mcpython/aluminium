package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "朗道的选择 / Landau's Choice": "使装备者受到攻击的概率提高，同时受到的伤害降低16/18/20/22/24%".
 *
 * <p>The prose states no number for the aggro half; `weapons.json` states the factor 2 (a doubling) and the reduction per rank.
 * The aggro half is judged numerically -- a ratio of `Battle.aggroOf`, the recipe `SoftAggroWeightTest` established -- and the
 * reduction by its presence, with its magnitude registered as needing a damage measurement.
 */
public class LightConeAggroTest {
    private static final int WEAPON_ID = 21009;
    private static final int WEARER = 1210;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAggroIsDoubled() {
        double without = aggro(false);
        double with = aggro(true);
        Assertions.assertEquals(2.0, with / without, 1e-9,
                "使装备者受到攻击的概率提高 -- and the data says the factor is 2");
    }

    @Test
    public void theDamageReductionIsOnTheWearer() {
        Fixture f = fixture(true);
        Assertions.assertEquals(1, f.wearer.getBuffManager().countBuffs(ReductionBuff.class),
                "the same sentence's second half is a reduction on the wearer");
        Fixture none = fixture(false);
        Assertions.assertEquals(0, none.wearer.getBuffManager().countBuffs(ReductionBuff.class),
                "and an unequipped character carries none");
    }

    private static double aggro(boolean equipped) {
        Fixture f = fixture(equipped);
        return f.battle.aggroOf(f.wearer);
    }

    private static Fixture fixture(boolean equipped) {
        Weapon weapon = equipped ? Weapon.build(WEAPON_ID, LEVEL) : null;
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, weapon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Fixture(wearer, battle);
    }

    private record Fixture(Character wearer, Battle battle) {
    }

    /**
     * The reduction's MAGNITUDE, judged across two ranks of the same light cone.
     *
     * <p>Rank 1 states 16% and rank 5 states 24%, and the weapon's stats do not depend on rank -- so the same enemy attack
     * must land at `(1-0.24)/(1-0.16)` = 0.90461... of the damage it does to the rank-1 wearer. A presence check could not
     * see a wrong percentage; this can, and mutating the rank-5 row moves it.
     *
     * <p>Note: The generator is pinned at 0.99 (no crit, and every base-chance roll lands), because a crit would swamp a 8%
     * difference -- the same instrument problem rounds 25 and 39 ran into.
     */
    @Test
    public void theReductionMagnitudeFollowsTheRank() {
        double rankOne = damageTaken(1);
        double rankFive = damageTaken(5);
        Assertions.assertTrue(rankOne > 0 && rankFive > 0, "precondition: the enemy's attack lands on both");
        Assertions.assertEquals((1 - 0.24) / (1 - 0.16), rankFive / rankOne, 1e-6,
                "rank 5 states 24% and rank 1 states 16%: " + rankFive + " vs " + rankOne);
    }

    /** The wearer's HP loss from one enemy attack, at the given rank of the light cone. */
    private static double damageTaken(int rank) {
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
        double before = wearer.getCurrentHp();
        // Note: The attacker is the ALLY, not the enemy: Enemy.activeSkill() is null unless a phase skill was set, while
        // castImmediate settles damage through the ordinary pipeline whoever the caster is -- and the reduction under
        // test sits on the WEARER, so who swings is irrelevant to it.
        battle.castImmediate(ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), ally, List.of(wearer));
        return before - wearer.getCurrentHp();
    }
}
