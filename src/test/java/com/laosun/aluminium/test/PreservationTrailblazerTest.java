package com.laosun.aluminium.test;

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
 * The Preservation Trailblazer pair (2026-09-29, round 183): 8003 and 8004, one kit under two ids.
 *
 * <p>Three claims, each from the document: one stack of 【灼热意志】 per hit up to eight; the cast-triggered party shield at 6% DEF + 80; and the technique's
 * self-shield at 30% DEF + 384, which only exists when the technique was declared.
 */
public class PreservationTrailblazerTest {
    private static final int TB3 = 8003;
    private static final int TB4 = 8004;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」. */
    @Test
    public void hisTalentStacksMagmaWillUpToEight() {
        Character tb = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 8; i++) {
            battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0);
        }
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("\u707c\u70ed\u610f\u5fd7"),
                "\u300c\u6bcf\u53d7\u52301\u6b21\u653b\u51fb\uff0c\u53e0\u52a01\u5c42\u3010\u707c\u70ed\u610f\u5fd7\u3011\uff0c\u6700\u591a\u53ef\u53e0\u52a08\u5c42\u300d");

        battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0);
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("\u707c\u70ed\u610f\u5fd7"), "the ninth hit must not pass the cap");
    }

    /** \u26a0 「施放普攻、战技、终结技后，为我方全体提供\u2026\u7b49\u540c\u4e8e6.00%\u9632\u5fa1\u529b+80\u7684\u62a4\u76fe\uff0c\u6301\u7eed2\u56de\u5408」 -- all three casts, both ids. */
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
                    "cid " + cid + ": \u300c\u65bd\u653e\u6218\u6280\u540e\uff0c\u4e3a\u6211\u65b9\u5168\u4f53\u63d0\u4f9b\u20266.00%\u9632\u5fa1\u529b+80\u7684\u62a4\u76fe\u300d");
        }
    }

    /** \u26a0 The technique's self-shield, gated on the marker; and the control. */
    @Test
    public void theTechniqueShieldsHimOnlyWhenDeclared() {
        Character withTechnique = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withTechnique), List.of(enemy), fixed());
        battle.markTechniqueUsed(withTechnique);
        battle.startBattle();
        double expected = withTechnique.getAttribute(AttributeType.DEFENCE).get() * 0.3 + 384;
        Assertions.assertEquals(expected, withTechnique.getShield(), expected * 0.02,
                "\u300c\u7ed9\u81ea\u8eab\u63d0\u4f9b\u2026\u7b49\u540c\u4e8e30%\u9632\u5fa1\u529b+384\u7684\u62a4\u76fe\uff0c\u6301\u7eed1\u56de\u5408\u300d");

        Character without = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plain = new Battle(List.of(without), List.of(enemy2), fixed());
        plain.startBattle();
        Assertions.assertEquals(0.0, without.getShield(), 1e-9,
                "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- undeclared, so no shield");
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
