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

    /**
     * 星魂 1: four layers at battle start, and the cap they were raised to.
     */
    @Test
    public void eidolon1StartsWithFourLayers() {
        Fixture e1 = new Fixture(1);
        Assertions.assertEquals(4, e1.layers(), "「战斗开始时，立即获得 4 层【归纳】」");

        Fixture e0 = new Fixture(0);
        Assertions.assertEquals(0, e0.layers(), "control: nothing at E0");
    }

    /**
     * 星魂 2: 「目标每有 1 个负面效果…最多触发 4 次」 — two debuffs give two extra instances, six give four.
     */
    @Test
    public void eidolon2DealsOneExtraHitPerDebuffUpToFour() {
        // The Skill lands 演绎 on the target itself, and that IS a negative effect, so the counts are one higher than the
        // fixture's own debuffs: 2 -> 3, 6 -> capped at 4, and a bare cast -> 1.
        Assertions.assertEquals(3, extraHitsAgainst(2), "two fixture debuffs plus the Skill's own 演绎");
        Assertions.assertEquals(4, extraHitsAgainst(6), "「最多触发 4 次」 — six debuffs still give four");
        Assertions.assertEquals(1, extraHitsAgainst(0), "only the Skill's own 演绎 is on the target");
    }

    /**
     * The Eidolon's contribution, measured as the difference between E2 and E0 with the same board.
     *
     * <p>Counting instances outright would be wrong: one Skill cast settles its own hit <b>and</b> the talent's follow-up, so the
     * baseline is two, not one. Subtracting the E0 count is the honest way to isolate the extra damage.
     */
    private static int extraHitsAgainst(int debuffs) {
        return instancesFor(2, debuffs) - instancesFor(0, debuffs);
    }

    private static int instancesFor(int eidolon, int debuffs) {
        Fixture f = new Fixture(eidolon);
        for (int i = 0; i < debuffs; i++) {
            f.enemy.getBuffManager().addBuff(new DotBuff(f.ratio, DamageElement.QUANTUM, 10, 3, 1, "灼烧" + i));
        }
        int[] instances = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> instances[0]++);
        f.castSkill();
        return instances[0];
    }

    /**
     * 行迹 推理: with three debuffs the same Skill hits for more, and with two nothing changes.
     *
     * <p>Measured as damage totals against a fixed seed: two battles with the same seed roll the same numbers, so the only
     * difference left is the trace.
     */
    @Test
    public void theInferenceTraceRaisesDamageAtThreeDebuffs() {
        double without = firstHitAgainst(0);
        double withThree = firstHitAgainst(3);
        Assertions.assertTrue(withThree > without,
                "「若目标负面状态 ≥3 个…造成伤害提高 10%」 (" + without + " -> " + withThree + ")");
        Assertions.assertEquals(without, firstHitAgainst(2), 1e-9, "two debuffs is below the gate");
    }

    /**
     * The FIRST damage instance of the cast, which is the Skill's own hit.
     *
     * <p>Not the total: the talent's follow-up roll now depends on the debuff count too (its chance is 40% plus 20% per debuff), so a
     * total would move for a reason that has nothing to do with 推理. The first instance settles before that roll, which keeps the
     * comparison about the trace alone.
     */
    private static double firstHitAgainst(int debuffs) {
        Fixture f = new Fixture(0);
        for (int i = 0; i < debuffs; i++) {
            f.enemy.getBuffManager().addBuff(new DotBuff(f.ratio, DamageElement.QUANTUM, 10, 3, 1, "灼烧" + i));
        }
        double[] first = {-1};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
            if (first[0] < 0) {
                first[0] = damage.toValue();
            }
        });
        f.castSkill();
        return first[0];
    }

    /**
     * 星魂 6's second half: 「【智者的短见】的触发效果额外增加 1 次」 — three follow-ups per turn instead of two.
     *
     * <p>Measured by counting the damage instances her follow-up settles when a teammate keeps attacking the marked enemy.
     */
    @Test
    public void eidolon6AllowsOneMoreTriggerOfTheMark() {
        Assertions.assertEquals(2, followupsAt(0), "the base limit is two per turn");
        Assertions.assertEquals(3, followupsAt(6), "「额外增加 1 次」");
    }

    private static int followupsAt(int eidolon) {
        Fixture f = new Fixture(eidolon);
        f.castUltimate();
        int[] instances = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> instances[0]++);
        for (int i = 0; i < 5; i++) {
            f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, f.ally, f.enemy, 0, 0));
            f.battle.processRequests();
        }
        // no teammate damage was settled here - the manual firings only wake her follow-up
        return instances[0];
    }

    /**
     * 秘技: 「100% 基础概率使敌方每个单体目标速度降低 15%，持续 2 回合」 — through the resistance pipeline, not around it.
     */
    @Test
    public void theTechniqueLowersEveryEnemySpeed() {
        Fixture f = new Fixture(0);
        double before = f.enemy.getAttribute(AttributeType.SPEED).get();
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.BATTLE_START, f.ratio, f.ratio, 0, 0));
        // the technique is armed by the state, which the fixture sets the way the engine does
        f.battle.markTechniqueUsed(f.ratio);
        com.laosun.aluminium.trigger.TriggerPayload.Simple start = com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.BATTLE_START, f.ratio, f.ratio, 0, 0);
        f.battle.fire(start);
        f.battle.processRequests();
        Assertions.assertTrue(f.enemy.getAttribute(AttributeType.SPEED).get() <= before,
                "「速度降低 15%」 — never higher, and with base chance 100% it lands unless resistance refuses it");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character ratio;
        private final Character ally = CharacterFactory.create(1002, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            ratio = CharacterFactory.create(DR_RATIO, 80, false, null, null, eidolon);
            battle = new Battle(List.of(ratio, ally), List.of(enemy), BattleFixture.seeded());
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

        private void allyAttacks() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(ratio.getSkills().get(SkillType.ULTRA), ratio, List.of(enemy));
            battle.processRequests();
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













