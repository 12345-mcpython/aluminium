package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
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
}
