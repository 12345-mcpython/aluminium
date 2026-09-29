package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 昔涟's aura: 「昔涟在场时，我方全体目标造成的伤害提高20.00%」.
 *
 * <p>Judged on a TEAMMATE (the point of 「我方全体」) and as a DIFFERENCE against the same teammate without her -- so the assertion is
 * about the scope reaching someone else, not about an absolute number a baseline could satisfy anyway.
 */
public class CyreneAuraTest {
    private static final int CYRENE = 1415;
    private static final int TEAMMATE = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAuraReachesATeammate() {
        double withHer = boost(true);
        double withoutHer = boost(false);
        Assertions.assertEquals(0.20, withHer - withoutHer, 1e-9,
                "the aura adds 20% to a teammate and nothing when she is absent: " + withoutHer + " -> " + withHer);
    }

    /** A teammate's ALL_DAMAGE_TYPE_BOOST with 昔涟 on the team or not. */
    private static double boost(boolean withCyrene) {
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        List<Character> team = withCyrene
                ? List.of(teammate, CharacterFactory.create(CYRENE, LEVEL))
                : List.of(teammate);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        return teammate.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
