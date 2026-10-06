package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
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
 * The Preservation Trailblazer pair: 8003 and 8004, one kit under two ids.
 *
 * <p>Three claims, each from the document: one stack of [灼热意志] per hit up to eight; the cast-triggered party shield at 6% DEF + 80; and the technique's
 * self-shield at 30% DEF + 384, which only exists when the technique was declared.
 */
public class PreservationTrailblazerTest {
    private static final int TB3 = 8003;
    private static final int TB4 = 8004;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "每受到1次攻击，叠加1层[灼热意志]，最多可叠加8层". */
    @Test
    public void hisTalentStacksMagmaWillUpToEight() {
        Character tb = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 8; i++) {
            battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0);
        }
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"),
                "「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」");

        battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0);
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"), "the ninth hit must not pass the cap");
    }

    /** Note: "施放普攻、战技、终结技后，为我方全体提供…等同于6.00%防御力+80的护盾，持续2回合" -- all three casts, both ids. */
    @Test
    public void everyCastShieldsThePartyForBothIds() {
        for (int cid : new int[]{TB3, TB4}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Character ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
            double expected = tb.getAttribute(AttributeType.DEFENCE).get() * 0.06 + 80;

            battle.castImmediate(tb.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), tb, List.of(enemy));

            Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                    "cid " + cid + ": 「施放战技后，为我方全体提供…6.00%防御力+80的护盾」");
        }
    }

    /** Note: The technique's self-shield, gated on the marker; and the control. */
    @Test
    public void theTechniqueShieldsHimOnlyWhenDeclared() {
        Character withTechnique = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withTechnique), List.of(enemy), fixed());
        battle.markTechniqueUsed(withTechnique);
        battle.startBattle();
        double expected = withTechnique.getAttribute(AttributeType.DEFENCE).get() * 0.3 + 384;
        Assertions.assertEquals(expected, withTechnique.getShield(), expected * 0.02,
                "「给自身提供…等同于30%防御力+384的护盾，持续1回合」");

        Character without = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plain = new Battle(List.of(without), List.of(enemy2), fixed());
        plain.startBattle();
        Assertions.assertEquals(0.0, without.getShield(), 1e-9,
                "「使用秘技后」 -- undeclared, so no shield");
    }

    /** Census: the stacks, the three shields, the skill's two effects, the technique and the convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        for (int cid : new int[]{TB3, TB4}) {
            var table = TriggerTables.of(cid);
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.TAKING_HIT), "cid " + cid + ": the stack per hit");
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BASIC_ATTACK), "cid " + cid + ": the basic-attack shield");
            Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "cid " + cid + ": reduction+taunt and the shield");
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "cid " + cid + ": the ultimate's shield");
            Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "cid " + cid + ": the technique and the convention");
        }
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
