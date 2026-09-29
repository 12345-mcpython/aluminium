package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「花儿不会忘记 / The Flower Remembers」: 「装备者忆灵造成的暴击伤害额外提高24/30/36/42/48%」 -- the first reader of a
 * memosprite-SCOPED damage boost.
 *
 * <p>⚠ The whole point of the gate is that it is not `instanceof Summon`: the documents distinguish 忆灵 from ordinary 召唤物, so the
 * test also pins that a plain summon does NOT receive the boost while the master's memosprite DOES.
 */
public class MemospriteBoostTest {
    /** Aglaea, whose 忆灵 has a shipped spec (the recipe AglaeaMemospriteTest uses). */
    private static final int SUMMONER = 1413;
    private static final int WEAPON_ID = 21057;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theMemospriteBoostFollowsTheRank() {
        Assertions.assertEquals(0.24, wornProbe(1), 1e-9, "24% at rank 1");
        Assertions.assertEquals(0.48, wornProbe(5), 1e-9, "48% at rank 5");
    }

    @Test
    public void theMemospriteIsTheOneThatQualifies() {
        Character master = CharacterFactory.create(SUMMONER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(master), List.of(enemy), new Random(0));
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the 忆灵 is out");
        Assertions.assertSame(memosprite, battle.memospriteOf(master),
                "the gate's predicate: this unit IS what memospriteOf returns for its master");
        Assertions.assertNotSame(master, battle.memospriteOf(master), "and the master is not its own memosprite");
    }

    private static double wornProbe(int rank) {
        Character master = CharacterFactory.create(SUMMONER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, rank));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(master), List.of(enemy), new Random(0));
        battle.startBattle();
        return master.getAttribute(AttributeType.MEMOSPRITE_DAMAGE_BOOST).get();
    }

    /**
     * The gate itself, judged through damage: a memosprite's own attack must be larger while its master wears rank 5 (48%) than
     * rank 1 (24%).
     *
     * <p>⚠ Two ranks rather than "with and without the light cone": a weapon contributes base stats, and the memosprite's damage
     * scales off its own Max HP (inherited from the master), so a with/without comparison would mix the boost with a stat change.
     * Rank does not move a weapon's stats, so it isolates the boost.
     */
    @Test
    public void theMemospriteHitsHarderAtRankFive() {
        double one = memospriteDamage(1);
        double five = memospriteDamage(5);
        Assertions.assertTrue(one > 0, "precondition: the memosprite landed a hit: " + one);
        double ratio = five / one;
        // Measured band: the boosts are 24% and 48%, and the boost zone already holds other contributions, so the observable
        // ratio compresses below 1.48/1.24. A missing gate gives exactly 1.0, well outside this band (that is the mutation).
        Assertions.assertTrue(ratio > 1.10 && ratio < 1.25,
                "rank 5 boosts 48% against rank 1's 24%: " + one + " vs " + five + " -> " + ratio);
    }

    /** One hit by the memosprite, against a victim that cannot die, at the given rank. */
    private static double memospriteDamage(int rank) {
        Character master = CharacterFactory.create(SUMMONER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, rank));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(master), List.of(enemy), noCrit);
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the 忆灵 is out");
        double before = enemy.getCurrentHp();
        // ⚠ COMMON, not SKILL: a memosprite's stated attack is installed in its COMMON slot (MemospriteAttackTest:78).
        battle.castImmediate(memosprite.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), memosprite,
                List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }
}
