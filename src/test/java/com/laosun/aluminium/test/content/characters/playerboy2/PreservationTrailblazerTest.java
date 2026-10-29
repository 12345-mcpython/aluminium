package com.laosun.aluminium.test.content.characters.playerboy2;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
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

    /**
     * Note: "每受到1次攻击，叠加1层[灼热意志]，最多可叠加8层".
     */
    @Test
    public void hisTalentStacksMagmaWillUpToEight() {
        Character tb = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 8; i++) {
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0));
        }
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"),
                "「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」 (every time it is attacked once, gains 1 stack of Magma Will (【灼热意志】), up to 8 stacks)");

        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0));
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"), "the ninth hit must not pass the cap");
    }

    /**
     * Note: "施放普攻、战技、终结技后，为我方全体提供…等同于6.00%防御力+80的护盾，持续2回合" -- all three casts, both ids.
     *
     * <p>⚠ <b>The battle is started first, and that is load-bearing.</b> The shield's two numbers are read out of the
     * TALENT's row at its <b>current level</b> (`percent_from_skill_param` / `amount_from_skill_param`), and the level is
     * put there by the file's own `level_convention` rule on `BATTLE_START`. This case used to cast into a battle that
     * had never started, which was invisible while the numbers were literals (they do not depend on any level) and
     * became a silent 6%-of-row-1 the moment they followed the row - measured: 52.744 instead of 129.116. A real battle
     * always starts, so starting it is what the sentence assumes.
     */
    @Test
    public void everyCastShieldsThePartyForBothIds() {
        for (int cid : new int[]{TB3, TB4}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Character ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
            battle.startBattle();
            double expected = tb.getAttribute(AttributeType.DEFENCE).get() * 0.06 + 80;

            battle.castImmediate(tb.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), tb, List.of(enemy));

            Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                    "cid " + cid + ": \"after casting the Skill, provides all allies with a shield of ... 6.00% DEF + 80\" (「施放战技后，为我方全体提供…6.00%防御力+80的护盾」)");
        }
    }

    /**
     * Note: The technique's self-shield, gated on the marker; and the control.
     */
    @Test
    public void theTechniqueShieldsHimOnlyWhenDeclared() {
        Character withTechnique = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(withTechnique), List.of(enemy), fixed());
        battle.markTechniqueUsed(withTechnique);
        battle.startBattle();
        double expected = withTechnique.getAttribute(AttributeType.DEFENCE).get() * 0.3 + 384;
        Assertions.assertEquals(expected, withTechnique.getShield(), expected * 0.02,
                "「给自身提供…等同于30%防御力+384的护盾，持续1回合」 (provides itself with a shield equal to 30% DEF + 384 for 1 turn)");

        Character without = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy2 = BattleFixture.monster();
        Battle plain = new Battle(List.of(without), List.of(enemy2), fixed());
        plain.startBattle();
        Assertions.assertEquals(0.0, without.getShield(), 1e-9,
                "「使用秘技后」 (after using the Technique) -- undeclared, so no shield");
    }

    /**
     * Census: the stacks, the three shields, the skill's two effects, the technique and the convention.
     */
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
        return BattleFixture.alwaysRoll(1.0);
    }
}
