package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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
 * 1201 Qingque, from her own file (2026-09-29, round 212): the tile COUNT (whose suits are flattened) and the Skill's self damage boost.
 */
public class QingqueTest {
    private static final int QINGQUE = 1201;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The count: one per TEAMMATE's turn start, two from the technique, and the document's cap of four enforced by exceeding it. */
    @Test
    public void theTileCountFollowsTheDocumentAndStopsAtFour() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(qingque, ally), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, tilesOf(qingque), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.TURN_START, ally, enemy, 0, 0);
        Assertions.assertEquals(1, tilesOf(qingque), "\u300c\u6211\u65b9\u76ee\u6807\u56de\u5408\u5f00\u59cb\u65f6\u2026\u968f\u673a\u62bd\u53d61\u5f20\u300d");
        battle.fireTriggers(TriggerEvent.TURN_START, qingque, enemy, 0, 0);
        Assertions.assertEquals(1, tilesOf(qingque),
                "the gate is `actor is_other_ally`: her OWN turn start adds nothing (the document's wording is ambiguous and the rule says so)");

        for (int i = 0; i < 6; i++) {
            battle.fireTriggers(TriggerEvent.TURN_START, ally, enemy, 0, 0);
        }
        Assertions.assertEquals(4, tilesOf(qingque),
                "\u300c\u6700\u591a\u6301\u67094\u5f20\u743c\u7389\u724c\u300d -- eight draws must still read four");
    }

    /** \u26a0 The technique's two tiles, only when the technique was declared. */
    @Test
    public void theTechniqueDrawsTwoTiles() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(qingque), List.of(enemy), fixed());
        battle.markTechniqueUsed(qingque);

        battle.startBattle();

        Assertions.assertEquals(2, tilesOf(qingque), "\u300c\u8fdb\u5165\u6218\u6597\u65f6\u9752\u96c0\u4f1a\u62bd\u53d62\u5f20\u743c\u7389\u724c\u300d");
    }

    /** \u26a0 The Skill's self damage boost, capped at the document's four stacks. */
    @Test
    public void theSkillRaisesHerOwnDamageUpToFourStacks() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(qingque, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = qingque.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        for (int i = 0; i < 5; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, qingque, enemy, 0, 0);
        }

        Assertions.assertEquals(0.28 * 4, qingque.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-6,
                "\u300c\u589e\u4f24 28%\u300d\u53e0\u5230\u300c\u6700\u591a 4 \u5c42\u300d -- five casts must still read four");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int tilesOf(Character qingque) {
        return qingque.getResources().get("\u743c\u7389\u724c").getValue();
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
