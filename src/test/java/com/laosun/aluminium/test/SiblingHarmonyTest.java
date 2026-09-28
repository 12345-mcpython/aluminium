package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8005, the sibling of 8006 (2026-09-29, round 197): 【伴舞】 and the +30% Break Effect, mirrored where the two documents agree.
 */
public class SiblingHarmonyTest {
    private static final int TB = 8005;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「持有\u3010\u4f34\u821e\u3011\u7684\u6211\u65b9\u76ee\u6807\u51fb\u7834\u7279\u653b\u63d0\u9ad830%」 -- the state and the modifier, for the party. */
    @Test
    public void herUltimateGrantsTheDanceAndTheBreakEffect() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        battle.castImmediate(tb.getSkills().get(SkillType.ULTRA), tb, List.of(enemy));

        Assertions.assertTrue(ally.getBuffManager().hasState("\u4f34\u821e"),
                "\u300c\u4e3a\u6211\u65b9\u5168\u4f53\u9644\u4e0a\u3010\u4f34\u821e\u3011\u6548\u679c\u300d");
        // Measured: BREAKING_EFFECT is a FRACTION attribute whose base is 0, and the engine lands this modifier as an absolute 0.3 — i.e. exactly the
        // document's 30%. Asserting a share of the base (the first attempt) expected 0 and compared nothing.
        Assertions.assertEquals(0.3, ally.getAttribute(AttributeType.BREAKING_EFFECT).get() - before, 1e-9,
                "\u300c\u51fb\u7834\u7279\u653b\u63d0\u9ad830%\u300d: gain " + (ally.getAttribute(AttributeType.BREAKING_EFFECT).get() - before));
    }

    /** \u26a0 The technique's own +30% for two turns, gated on the marker, with the control. */
    @Test
    public void theTechniqueRaisesThePartysBreakEffect() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(tb);
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        battle.startBattle();

        Assertions.assertTrue(ally.getAttribute(AttributeType.BREAKING_EFFECT).get() > before,
                "\u300c\u4f7f\u6211\u65b9\u5168\u4f53\u7684\u51fb\u7834\u7279\u653b\u63d0\u9ad830%\uff0c\u6301\u7eed2\u56de\u5408\u300d");

        Character plain = CharacterFactory.create(TB, LEVEL);
        Character ally2 = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain, ally2), List.of(enemy2), fixed());
        double untouched = ally2.getAttribute(AttributeType.BREAKING_EFFECT).get();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, ally2.getAttribute(AttributeType.BREAKING_EFFECT).get(), 1e-9,
                "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- undeclared, so nothing");
    }

    /** Census: the ultimate, the super-break rule, the talent, the technique and the convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = com.laosun.aluminium.data.TriggerTables.of(TB);
        Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST));
        Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE));
        Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.BREAK));
        Assertions.assertEquals(2, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START),
                "the technique and the convention");
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
