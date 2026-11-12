package com.laosun.aluminium.test.content.characters.welt;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("ctrl:imprisoned"), "precondition: not imprisoned yet");

        f.battle.castImmediate(f.welt.getSkills().get(SkillType.ULTRA), f.welt, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("ctrl:imprisoned"),
                "「有100%的基础概率使受到攻击的敌方目标陷入ctrl:imprisoned状态，持续1回合」 (a 100% base chance to put the attacked enemy target into the Imprisonment (ctrl:imprisoned) state for 1 turn)");
    }


    /**
     * 1004 瓦尔特's Eidolon 1 (「强化前」half):「施放终结技后…在随后 2 次施放普攻或战技时额外对敌方目标造成 1 次附加伤害」.
     *
     * <p>The judge counts the extra instances by damage: two empowered attacks add damage that a plain attack does not, and the
     * third one is back to normal - which is what 「随后 2 次」 means.
     */
    @Test
    public void theUltimateEmpowersExactlyTwoAttacks() {
        double[] d = damages();
        System.out.println("[1004] E1 after ult: #1=" + d[0] + " #2=" + d[1] + " #3=" + d[2] + " #4=" + d[3]);
        Assertions.assertTrue(d[0] > d[2], "the first attack after the Ultimate carries the additional damage");
        Assertions.assertTrue(d[1] > d[2], "the second one does too");
        Assertions.assertEquals(d[2], d[3], 1e-9, "「随后 2 次」 - attacks three and four are the same ordinary attack");
    }

    /** Raw damage of four consecutive basic attacks in ONE battle, so the crit stream is the same for all of them. */
    private static double[] damages() {
        Character him = CharacterFactory.create(WELT, LEVEL, true, null, null, 1);
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        battle.castImmediate(him.getSkills().get(SkillType.ULTRA), him, List.of(enemy));
        battle.processRequests();
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            double before = enemy.getCurrentHp();
            battle.castImmediate(him.getSkills().get(SkillType.COMMON), him, List.of(enemy));
            battle.processRequests();
            out[i] = before - enemy.getCurrentHp();
        }
        return out;
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



