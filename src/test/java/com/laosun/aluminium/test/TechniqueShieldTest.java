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
 * The technique gate's second harvest (2026-09-29, round 180): Gepard's opening shield, declared only when the technique was used.
 *
 * <p>Pair test again, and the declared side asserts the DOCUMENT'S arithmetic — 24% of his DEF plus 150.
 */
public class TechniqueShieldTest {
    private static final int GEPARD = 1104;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 「为我方全体提供…等同于杰帕德24%防御力+150伤害的护盾，持续2回合」. */
    @Test
    public void aDeclaredTechniqueShieldsTheParty() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(gepard, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(gepard);

        battle.startBattle();

        double expected = gepard.getAttribute(AttributeType.DEFENCE).get() * 0.24 + 150;
        Assertions.assertTrue(gepard.getShield() > 0, "「为我方全体提供…护盾」 -- himself");
        Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                "「等同于杰帕德24%防御力+150的护盾」: expected " + expected + ", shield " + ally.getShield());
    }

    /** ⚠ The control: no technique declared, no shield. */
    @Test
    public void withoutTheTechniqueNobodyIsShielded() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(gepard, ally), List.of(enemy), fixed());

        battle.startBattle();

        Assertions.assertEquals(0.0, ally.getShield(), 1e-9,
                "「使用秘技后」 -- the shield is the technique's, so without it there is none");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
