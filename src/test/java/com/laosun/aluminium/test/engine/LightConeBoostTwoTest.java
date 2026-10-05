package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * Two more light cones whose non-property halves land on scoped boosts, judged per rank.
 *
 * <p>Note: A battle has to START before the value means anything: these rules hang on BATTLE_START, and round 49 measured a
 * fixture that skipped `startBattle()` reading 0.0 while the table visibly carried the rules.
 */
public class LightConeBoostTwoTest {
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theBygoneBloodBoostsFollowTheRank() {
        Assertions.assertEquals(0.24, raw(21058, AttributeType.SKILL_DAMAGE_BOOST, 1), 1e-9, "skill, rank 1");
        Assertions.assertEquals(0.40, raw(21058, AttributeType.SKILL_DAMAGE_BOOST, 5), 1e-9, "skill, rank 5");
        Assertions.assertEquals(0.24, raw(21058, AttributeType.ULTIMATE_DAMAGE_BOOST, 1), 1e-9, "ultimate, rank 1");
    }

    @Test
    public void theWheatDreamBoostsFollowTheRank() {
        Assertions.assertEquals(0.24, raw(21060, AttributeType.ULTIMATE_DAMAGE_BOOST, 1), 1e-9, "ultimate, rank 1");
        Assertions.assertEquals(0.40, raw(21060, AttributeType.FOLLOW_UP_DAMAGE_BOOST, 5), 1e-9, "follow-up, rank 5");
    }

    @Test
    public void theCritChanceComesFromTheDataOnce() {
        // The other half of both sentences is an ability_property; authored twice it would double, so this pins it at the
        // data's own value (0.12 at rank 1, on top of the inherent 0.05).
        double one = raw(21058, AttributeType.CRIT_CHANCE, 1);
        double five = raw(21058, AttributeType.CRIT_CHANCE, 5);
        Assertions.assertEquals(0.08, five - one, 1e-9,
                "crit chance is 20% at rank 5 and 12% at rank 1: " + one + " vs " + five);
    }

    private static double raw(int weaponId, AttributeType attribute, int rank) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true,
                Weapon.build(weaponId, LEVEL, false, rank));
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return wearer.getAttribute(attribute).get();
    }
}
