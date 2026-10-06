package com.laosun.aluminium.test.engine;

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
 * 8005, the sibling of 8006: [伴舞] and the +30% Break Effect, mirrored where the two documents agree.
 */
public class SiblingHarmonyTest {
    private static final int TB = 8005;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "持有[伴舞]的我方目标击破特攻提高30%" -- the state and the modifier, for the party. */
    @Test
    public void herUltimateGrantsTheDanceAndTheBreakEffect() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        battle.castImmediate(tb.getSkills().get(SkillType.ULTRA), tb, List.of(enemy));

        Assertions.assertTrue(ally.getBuffManager().hasState("伴舞"),
                "\"applies the Backup Dancer (【伴舞】) effect to all allies\" (「为我方全体附上【伴舞】效果」)");
        // Measured: BREAKING_EFFECT is a FRACTION attribute whose base is 0, and the engine lands this modifier as an absolute 0.3 - i.e. exactly the
        // document's 30%. Asserting a share of the base (the first attempt) expected 0 and compared nothing.
        Assertions.assertEquals(0.3, ally.getAttribute(AttributeType.BREAKING_EFFECT).get() - before, 1e-9,
                "「击破特攻提高30%」 (Break Effect is raised by 30%): gain " + (ally.getAttribute(AttributeType.BREAKING_EFFECT).get() - before));
    }

    /** Note: The technique's own +30% for two turns, gated on the marker, with the control. */
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
                "\"raises all allies' Break Effect by 30% for 2 turns\" (「使我方全体的击破特攻提高30%，持续2回合」)");

        Character plain = CharacterFactory.create(TB, LEVEL);
        Character ally2 = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain, ally2), List.of(enemy2), fixed());
        double untouched = ally2.getAttribute(AttributeType.BREAKING_EFFECT).get();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, ally2.getAttribute(AttributeType.BREAKING_EFFECT).get(), 1e-9,
                "「使用秘技后」 (after using the Technique) -- undeclared, so nothing");
    }

    /** Census: the ultimate, the super-break rule, the talent, the technique and the convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = com.laosun.aluminium.data.TriggerTables.of(TB);
        Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST));
        //: 2 -- 8005 gained the skill clause "额外造成 4 次伤害，每次对随机敌方单体" (times).
        Assertions.assertEquals(2, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE));
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
