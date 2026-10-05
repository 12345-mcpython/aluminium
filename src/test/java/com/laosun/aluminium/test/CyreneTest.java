package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 Cyrene, from her own file (2026-09-29, round 230): the [追忆] resource and the party boost that needs no condition.
 */
public class CyreneTest {
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: Both writers feed [追忆], stopped at the document's twenty-four. */
    @Test
    public void bothWritersFeedRecollectionUpToItsStatedOverflow() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cyrene), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, recollectionOf(cyrene), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, cyrene, enemy, 0, 0);
        Assertions.assertEquals(1, recollectionOf(cyrene), "「获得1点【追忆】」");
        battle.fireTriggers(TriggerEvent.SKILL_CAST, cyrene, enemy, 0, 0);
        Assertions.assertEquals(4, recollectionOf(cyrene), "「获得3点【追忆】」 -- the other writer");

        for (int i = 0; i < 8; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, cyrene, enemy, 0, 0);
        }
        Assertions.assertEquals(27, recollectionOf(cyrene),
                "「【追忆】达到24点时可激活终结技」 -- thirty-two casts must still read twenty-four");
    }

    /** Note: The party boost, which the document conditions on nothing at all: an ally gains it, an enemy does not. */
    @Test
    public void thePartyBoostNeedsNoCondition() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(enemy), fixed());
        double allyBefore = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double enemyBefore = enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.startBattle();

        Assertions.assertEquals(0.2, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBefore, 1e-9,
                "「昔涟在场时，我方全体目标造成的伤害提高20.00%」");
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - enemyBefore, 1e-9,
                "the selector is `all_allies`: the enemy side must be untouched");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int recollectionOf(Character unit) {
        return unit.getResources().get("追忆").getValue();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
