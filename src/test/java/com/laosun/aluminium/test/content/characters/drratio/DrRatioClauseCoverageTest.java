package com.laosun.aluminium.test.content.characters.drratio;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1305 真理医生: the two traces whose sections the sweep found missing — 归纳 and 演绎.
 *
 * <p>归纳 has two halves and they only work together: the Skill adds a layer <b>per debuff on the target</b> (clamped at 6), and
 * a live per-stack modifier turns each layer into +2.5% crit chance and +5% crit damage. 演绎 is the debuff half: a 100%
 * <b>base</b> chance to lower the target's effect resistance for two turns.
 */
public class DrRatioClauseCoverageTest {

    private static final int DR_RATIO = 1305;

    /**
     * 归纳: three debuffs on the target ⇒ three layers, and the aura reads them live.
     */
    @Test
    public void theInductionTraceStacksOneLayerPerDebuff() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.layers(), "precondition: nothing before the Skill");
        f.putThreeDebuffsOnTheEnemy();

        double critChanceBefore = f.ratio.getAttribute(AttributeType.CRIT_CHANCE).get();
        f.castSkill();

        Assertions.assertEquals(3, f.layers(), "「目标每有 1 个负面效果」 — three debuffs, three layers");
        Assertions.assertEquals(critChanceBefore + 3 * 0.025,
                f.ratio.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "「暴击率提高 2.50%」 per layer");
    }

    /**
     * 归纳: nothing on the target ⇒ no layer at all (the floor of one would be a silent gift).
     */
    @Test
    public void theInductionTraceGivesNothingWithoutDebuffs() {
        Fixture f = new Fixture();
        f.castSkill();
        Assertions.assertEquals(0, f.layers(), "no debuff ⇒ no layer");
    }

    /**
     * 演绎: the Skill lowers the target's effect resistance by 10% for two turns.
     */
    @Test
    public void theDeductionTraceLowersEffectResistance() {
        Fixture f = new Fixture();
        double before = f.enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        f.castSkill();
        Assertions.assertTrue(f.enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get() < before,
                "「效果抵抗降低 10%」 (" + before + " -> " + f.enemy.getAttribute(AttributeType.EFFECT_RESISTANCE).get() + ")");
    }

    /**
     * 星魂 4: 「触发天赋时，真理医生额外恢复 15 点能量」 — on top of the talent's own 8, and only with the Eidolon.
     */
    @Test
    public void eidolon4PaysFifteenMoreEnergyWhenTheTalentFires() {
        Fixture e4 = new Fixture(4);
        double before = e4.ratio.getCurrentEnergy();
        e4.castSkill();
        double gainedWith = e4.ratio.getCurrentEnergy() - before;

        Fixture e0 = new Fixture(0);
        double beforeE0 = e0.ratio.getCurrentEnergy();
        e0.castSkill();
        double gainedWithout = e0.ratio.getCurrentEnergy() - beforeE0;

        Assertions.assertEquals(15, gainedWith - gainedWithout, 1e-9,
                "「触发天赋时…额外恢复 15 点能量」 — exactly 15 more than at E0");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character ratio;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            ratio = CharacterFactory.create(DR_RATIO, 80, false, null, null, eidolon);
            battle = new Battle(List.of(ratio), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /**
         * Three real debuffs: {@code VulnerabilityBuff} reports {@code isDebuff() == true}, which a plain state does not - and the
         * condition counts debuffs, so a state-based fixture would read zero and "prove" the rule wrong.
         */
        private void putThreeDebuffsOnTheEnemy() {
            for (String name : List.of("灼烧甲", "灼烧乙", "灼烧丙")) {
                enemy.getBuffManager().addBuff(new DotBuff(ratio, DamageElement.QUANTUM, 10, 3, 1, name));
            }
        }

        private void castSkill() {
            battle.castImmediate(ratio.getSkills().get(SkillType.SKILL), ratio, List.of(enemy));
            battle.processRequests();
        }

        private int layers() {
            return ratio.getBuffManager().stacksOf("归纳");
        }
    }
}




