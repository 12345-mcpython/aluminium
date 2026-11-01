package com.laosun.aluminium.test.content.characters.welt;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1004 Welt, from his own file: the ultimate's Imprison and the level convention.
 *
 * <p>Note: The target is hand-made with no resistances on purpose: the project's fixture monster is immune to controls
 * (round 131 measured `STAT_CTRL_Frozen` at 1.0, which clamps the chance to 0). A 100% BASE chance still rolls, so an
 * unresisting target is what makes this deterministic.
 *
 * <p>Registered in his file: the bounce (an engine bug today), the speed-down roll, [失重], the enhanced forms, his
 * traces and eidolons. The 60% slow-gated talent is now written and judged here.
 */
public class WeltTest {
    private static final int WELT = 1004;
    private static final int LEVEL = 80;

    @Test
    public void hisUltimateImprisonsEveryEnemy() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("禁锢"), "precondition: not imprisoned yet");

        f.battle.castImmediate(f.welt.getSkills().get(SkillType.ULTRA), f.welt, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("禁锢"),
                "「有100%的基础概率使受到攻击的敌方目标陷入禁锢状态，持续1回合」 (a 100% base chance to put the attacked enemy target into the Imprisonment (禁锢) state for 1 turn)");
    }

    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(WELT);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the imprison");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "the slow-gated talent");
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static final class Fixture {
        private final Character welt = CharacterFactory.create(WELT, LEVEL);
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle = new Battle(List.of(welt), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }

    /**
     * 「击中处于减速状态的敌方目标时，额外造成 1 次等同于瓦尔特 60% 攻击力的虚数属性附加伤害」: the slow-gated
     * additional instance must land, and the same hit against an unslowed target must not produce it.
     */
    @Test
    public void hisTalentAddsAnInstanceOnASlowedTarget() {
        Fixture normal = new Fixture();
        double beforeNormal = normal.enemy.getCurrentHp();
        normal.battle.castImmediate(normal.welt.getSkills().get(SkillType.COMMON), normal.welt,
                List.of(normal.enemy));
        double normalLoss = beforeNormal - normal.enemy.getCurrentHp();

        Fixture slowed = new Fixture();
        slowed.enemy.getBuffManager().addBuff(
                StatModifierBuff.percentDebuff(AttributeType.SPEED, -0.1, 2));
        double beforeSlowed = slowed.enemy.getCurrentHp();
        slowed.battle.castImmediate(slowed.welt.getSkills().get(SkillType.COMMON), slowed.welt,
                List.of(slowed.enemy));
        double slowedLoss = beforeSlowed - slowed.enemy.getCurrentHp();

        Assertions.assertTrue(slowedLoss > normalLoss,
                "the slow-gated talent must add an instance: normal " + normalLoss + ", slowed " + slowedLoss);
    }

    /**
     * Note: Bounce: his skill's own damage must land at all (it computed zero hits before the round-10 fix).
     */
    @Test
    public void hisSkillActuallyDealsDamage() {
        Fixture f = new Fixture();
        double before = f.enemy.getCurrentHp();

        f.battle.castImmediate(f.welt.getSkills().get(SkillType.SKILL), f.welt, List.of(f.enemy));

        double loss = before - f.enemy.getCurrentHp();
        Assertions.assertTrue(loss > 0,
                "「对指定敌方单体造成等同于维尔特#1[i]%攻击力的虚数伤害，并额外造成 2 次伤害」 (deals Imaginary damage to a designated single enemy equal to #1[i]% of Welt's ATK, and deals 2 extra instances of damage) — the skill must land: loss " + loss);
    }
}
