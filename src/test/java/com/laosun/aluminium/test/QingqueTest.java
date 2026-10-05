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

    /** Note: The count: one per TEAMMATE's turn start, two from the technique, and the document's cap of four enforced by exceeding it. */
    @Test
    public void theTileCountFollowsTheDocumentAndStopsAtFour() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(qingque, ally), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, tilesOf(qingque), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.TURN_START, ally, enemy, 0, 0);
        Assertions.assertEquals(1, tilesOf(qingque), "「我方目标回合开始时…随机抽取1张」");
        battle.fireTriggers(TriggerEvent.TURN_START, qingque, enemy, 0, 0);
        Assertions.assertEquals(1, tilesOf(qingque),
                "the gate is `actor is_other_ally`: her OWN turn start adds nothing (the document's wording is ambiguous and the rule says so)");

        for (int i = 0; i < 6; i++) {
            battle.fireTriggers(TriggerEvent.TURN_START, ally, enemy, 0, 0);
        }
        Assertions.assertEquals(4, tilesOf(qingque),
                "「最多持有4张琼玉牌」 -- eight draws must still read four");
    }

    /** Note: The technique's two tiles, only when the technique was declared. */
    @Test
    public void theTechniqueDrawsTwoTiles() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(qingque), List.of(enemy), fixed());
        battle.markTechniqueUsed(qingque);

        battle.startBattle();

        Assertions.assertEquals(2, tilesOf(qingque), "「进入战斗时青雀会抽取2张琼玉牌」");
    }

    /** Note: The Skill's self damage boost, capped at the document's four stacks. */
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
                "「增伤 28%」叠到「最多 4 层」 -- five casts must still read four");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int tilesOf(Character qingque) {
        return qingque.getResources().get("琼玉牌").getValue();
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
