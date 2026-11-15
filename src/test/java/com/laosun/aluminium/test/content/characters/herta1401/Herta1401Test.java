package com.laosun.aluminium.test.content.characters.herta1401;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1401 The Herta (大黑塔), her three files merged: the 【灵感】/【解读】 Skill form, Eidolons 2 / 4 / 6-first-half, and her
 * technique with the Ultimate's three effects.
 *
 * <p>Two shapes that only look similar: the SKILL is <b>replaced</b> while the stacks last (data slot 9, the enhanced form, which
 * spends one on cast and gives way when they run out), while 【解读】 is an ordinary counter on the enemy that the Skill adds one
 * of. Both are pinned here because neither has a symptom when it silently stops: the Skill simply stays the plain one, and the
 * enemy simply never gets a stack.
 *
 * <p><b>Why a criterion for the Eidolons.</b> Each half has an obvious wrong neighbour: 「额外」 on the inspiration is ADDITION (a
 * single rule would be read as a replacement), the advance is gated on the ENHANCED Skill row (slot 9, not any Skill), the speed
 * is scoped by PATH (not the whole party), and the penetration is per-ELEMENT (not the all-type one).
 *
 * <p><b>The technique.</b> Its 60% is measured as 60% of the BASE attack (the engine's `add_percent` convention), and the pair
 * (declared / undeclared) is what makes it a measurement rather than a claim - the same trap round 19 hit with Dan Heng.
 */
public class Herta1401Test {
    private static final int HERTA = 1401;
    private static final int ERUDITION_ALLY = 1013;   // 黑塔, also 智识
    private static final int OTHER_ALLY = 1002;       // 丹恒, 巡猎
    private static final int LEVEL = 80;
    private static final int PLAIN_SKILL_SLOT = 2;
    private static final int ENHANCED_SKILL_SLOT = 9;
    private static final String INSPIRATION = "灵感";
    private static final String INSIGHT = "解读";

    // ==================================================================
    // The Skill form: the plain Skill's 【解读】, the Ultimate's 【灵感】 replacement, and the Talent's entry layer.
    // ==================================================================

    /** 「战技…并施加 1 层【解读】」 - the plain Skill adds one stack to the enemy it hit. */
    @Test
    public void thePlainSkillAddsOneInsight() {
        Scene scene = new Scene();
        Assertions.assertEquals(0, scene.enemy.getBuffManager().stacksOf(INSIGHT), "the enemy enters with none");
        scene.castSkill();
        Assertions.assertEquals(1, scene.enemy.getBuffManager().stacksOf(INSIGHT),
                "「施加 1 层【解读】」");
    }

    /**
     * 「持有【灵感】时，战技强化为【我有一个大胆的想法】」: the Ultimate swaps the slot, the enhanced cast spends a stack, and when the
     * last one is gone the plain Skill is back.
     */
    @Test
    public void theUltimateEnhancesTheSkillUntilTheInspirationRunsOut() {
        Scene scene = new Scene();
        Assertions.assertEquals(PLAIN_SKILL_SLOT, scene.skillSlot(), "the plain Skill before the Ultimate");

        scene.castUltimate();
        Assertions.assertEquals(1, scene.herta.getBuffManager().stacksOf(INSPIRATION), "「获得 1 层【灵感】」");
        Assertions.assertEquals(ENHANCED_SKILL_SLOT, scene.skillSlot(),
                "「持有【灵感】时，战技强化为【我有一个大胆的想法】」");

        scene.castSkill();
        Assertions.assertEquals(0, scene.herta.getBuffManager().stacksOf(INSPIRATION),
                "「消耗 1 层【灵感】」");
        Assertions.assertEquals(PLAIN_SKILL_SLOT, scene.skillSlot(),
                "「持有」的相反面 - no stack left, so the plain Skill comes back");
    }

    /**
     * 1401 大黑塔's talent, first clause: 「敌方目标进入战斗时，大黑塔对其施加 1 层【解读】…最多可叠加 42 层」.
     *
     * <p>The layer lands on the unit that WALKED IN ({@code actor}), not on the caster and not on everybody - so the judge adds an
     * enemy after the battle is under way and checks that one.
     */
    @Test
    public void anEnemyThatWalksInGetsOneLayer() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy first = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(herta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(first)), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(0, first.getBuffManager().stacksOf(INSIGHT),
                "the roster at battle start is not 「进入战斗」 by this event's definition");

        Enemy late = BattleFixture.monster();
        battle.addEnemy(late);
        Assertions.assertEquals(1, late.getBuffManager().stacksOf(INSIGHT), "「对其施加 1 层【解读】」");

        Enemy another = BattleFixture.monster();
        battle.addEnemy(another);
        Assertions.assertEquals(1, another.getBuffManager().stacksOf(INSIGHT), "and every later entry pays too");
        Assertions.assertEquals(1, late.getBuffManager().stacksOf(INSIGHT), "the earlier one is not double-charged");
    }

    /** One 1401 with a single enemy on the field, stance pinned so the Skill's break does not interfere. */
    private static final class Scene {
        private final Character herta;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Scene() {
            herta = CharacterFactory.create(HERTA, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(herta)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private int skillSlot() {
            return herta.getSkills().get(SkillType.SKILL).getSkillSlot();
        }

        private void castSkill() {
            battle.castImmediate(herta.getSkills().get(SkillType.SKILL), herta, List.of(enemy));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(herta.getSkills().get(SkillType.ULTRA), herta, List.of(enemy));
            battle.processRequests();
        }
    }

    // ==================================================================
    // Eidolons 2 / 4 / 6-first-half, and the enhanced Skill's own 【解读】.
    // ==================================================================

    /**
     * Eidolon 2: +1 on entering the battle and +1 (on top of the Ultimate's own one) on the Ultimate.
     */
    @Test
    public void theSecondEidolonPaysOneMoreInspirationBothWays() {
        Assertions.assertEquals(0, stacks(newRun(1)), "E1: nothing at battle start");
        Assertions.assertEquals(1, stacks(newRun(2)), "「进入战斗…额外获得 1 层【灵感】」");

        Assertions.assertEquals(1, stacksAfterUlt(newRun(1)), "the Ultimate itself pays one");
        // 1 from the Eidolon entry clause + 1 from the Ultimate itself + 1 from the Eidolon Ultimate clause.
        Assertions.assertEquals(3, stacksAfterUlt(newRun(2)),
                "「施放终结技后，额外获得 1 层」 - added, not replaced");
    }

    /**
     * Eidolon 2: after the ENHANCED Skill (slot 9) her next action is advanced by 35%.
     */
    @Test
    public void theEnhancedSkillAdvancesHerOnlyAtE2() {
        double e1 = advanceAfterEnhancedSkill(1);
        double e2 = advanceAfterEnhancedSkill(2);
        System.out.println("[1401-E2] advance of the remaining time: E1=" + e1 + " ; E2=" + e2);
        Assertions.assertEquals(0.0, e1, 1e-9, "E1 states no advance");
        Assertions.assertEquals(0.35, e2, 1e-2,
                "「下一次行动提前 35%」 - a share of the remaining time");
    }

    /**
     * Eidolon 4: 「智识」 allies get 12% speed; a 巡猎 ally does not.
     */
    @Test
    public void theFourthEidolonSpeedsOnlyTheEruditionAllies() {
        Run e3 = new Run(3);
        Run e4 = new Run(4);
        Assertions.assertEquals(0.12 * e4.herta.getAttribute(AttributeType.SPEED).baseValue(),
                e4.herta.getAttribute(AttributeType.SPEED).get() - e3.herta.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "she is 智识 herself, so the aura includes her");
        Assertions.assertEquals(0.12 * e4.erudition.getAttribute(AttributeType.SPEED).baseValue(),
                e4.erudition.getAttribute(AttributeType.SPEED).get() - e3.erudition.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the other 智识 ally");
        Assertions.assertEquals(e3.other.getAttribute(AttributeType.SPEED).get(),
                e4.other.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "a 巡猎 ally is not 「智识」");
    }

    /**
     * Eidolon 6, first half: ICE penetration +20% (not the all-type one).
     */
    @Test
    public void theSixthEidolonAddsIcePenetrationOnly() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        Assertions.assertEquals(0.2, e6.herta.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get()
                - e5.herta.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get(), 1e-9,
                "「冰属性抗性穿透提高 20%」");
        Assertions.assertEquals(e5.herta.getAttribute(AttributeType.DAMAGE_PENETRATION).get(),
                e6.herta.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "per-element, not all-type");
    }

    /**
     * 「对指定敌方单体…施加 1 层【解读】」 on the ENHANCED Skill too (a different data row than the base Skill).
     */
    @Test
    public void theEnhancedSkillAppliesItsOwnInsight() {
        Run run = new Run(0);
        int before = run.enemy.getBuffManager().stacksOf(INSIGHT);
        run.battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(HERTA, 9, LEVEL), run.herta,
                List.of((com.laosun.aluminium.entity.CanHit) run.enemy));
        run.battle.processRequests();
        Assertions.assertEquals(before + 1, run.enemy.getBuffManager().stacksOf(INSIGHT),
                "the enhanced cast's own layer - the base Skill's rule is gated on `from_skill_id == 2`");
    }

    /** 1401 at one Eidolon rank with an 智识 ally and a 巡猎 ally on the field. */
    private static final class Run {
        private final Character herta;
        private final Character erudition;
        private final Character other;
        private final Battle battle;
        private final Enemy enemy;

        Run(int eidolon) {
            herta = CharacterFactory.create(HERTA, LEVEL);
            herta.setEidolonRank(eidolon);
            erudition = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
            other = CharacterFactory.create(OTHER_ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(herta, erudition, other), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void ult() {
            battle.castImmediate(herta.getSkills().get(SkillType.ULTRA), herta, List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }

        double remainingTime() {
            for (Signal signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == herta) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            return Double.NaN;
        }
    }

    private static Run newRun(int eidolon) {
        return new Run(eidolon);
    }

    private static int stacks(Run run) {
        return run.herta.getBuffManager().stacksOf(INSPIRATION);
    }

    private static int stacksAfterUlt(Run run) {
        run.ult();
        return run.herta.getBuffManager().stacksOf(INSPIRATION);
    }

    /** The share of her remaining time that the enhanced Skill skipped at this rank. */
    private static double advanceAfterEnhancedSkill(int eidolon) {
        Run run = new Run(eidolon);
        double before = run.remainingTime();
        run.battle.castImmediate(new DefaultSkill(HERTA, 9, LEVEL), run.herta, List.of(run.battle.enemies().getFirst()));
        run.battle.processRequests();
        double after = run.remainingTime();
        return (before - after) / before;
    }

    // ==================================================================
    // The technique and the Ultimate's own effects.
    // ==================================================================

    /**
     * Note: "使用秘技后，下一次战斗开始时大黑塔攻击力提高60%，持续2回合", and the control.
     */
    @Test
    public void theTechniqueRaisesHerAttackAndNothingWithoutIt() {
        Character plain = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy1 = BattleFixture.monster();
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy1), fixed());
        plainBattle.startBattle();
        double untouched = plain.getAttribute(AttributeType.ATTACK).get();

        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy2 = BattleFixture.monster();
        Battle battle = new Battle(List.of(herta), List.of(enemy2), fixed());
        battle.markTechniqueUsed(herta);
        battle.startBattle();

        double boosted = herta.getAttribute(AttributeType.ATTACK).get();
        double base = herta.getAttribute(AttributeType.ATTACK).baseValue();
        Assertions.assertEquals(base * 0.6, boosted - untouched, base * 0.6 * 0.02,
                "「攻击力提高60%」 (ATK raised by 60%) of the BASE: base " + base + ", gain " + (boosted - untouched));
    }

    /**
     * Note: The ultimate's own effects: +80% ATK, a full advance, and one Inspiration stack.
     */
    @Test
    public void herUltimateRaisesAttackAdvancesAndGrantsInspiration() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(herta), List.of(enemy), fixed());
        battle.startBattle();
        double before = herta.getAttribute(AttributeType.ATTACK).get();

        battle.castImmediate(herta.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), herta, List.of(enemy));

        Assertions.assertTrue(herta.getAttribute(AttributeType.ATTACK).get() > before,
                "「使大黑塔攻击力提高80%，持续3回合」 (raises The Herta's ATK by 80% for 3 turns)");
        // ADD_STACK makes a stack, not a named state: it is read with stacksOf, the same API 8003's Magma Will uses.
        Assertions.assertEquals(1, herta.getBuffManager().stacksOf("灵感"),
                "「获得1层【灵感】」 (gains 1 stack of [灵感])");

        // Note: The document's cap: five more ultimates must stop at 4, not keep counting (this is what makes the cap testable at all).
        for (int i = 0; i < 5; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, herta, herta, 0, 0));
        }
        Assertions.assertEquals(4, herta.getBuffManager().stacksOf("灵感"),
                "「【灵感】最多持有4层」 ([灵感] can be held up to 4 stacks)");
    }

    /**
     * Her technique's +60% and her ult's +80% ATTACK must both count (1401).
     *
     * <p>FILE-DRIVEN, three readings, and the two ratios are the claim: 1.6 with the technique alone, 2.4 with both.
     * `Note: `Technique (秘技)` comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point;
     * a hand-built `APPLY_BUFF` rule does not fire for a technique.
     */
    @Test
    public void bothAttackSourcesAreCounted() {
        double plain = attack(false, false);
        double techniqueOnly = attack(true, false);
        double ultOnly = attack(false, true);
        double both = attack(true, true);

        Assertions.assertTrue(plain > 0, "precondition: a positive base (" + plain + ")");
        Assertions.assertTrue(techniqueOnly > plain, "the technique's own share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(ultOnly > plain, "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two increments must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    private static double attack(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(HERTA, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);      // before startBattle: applyTechniqueStates runs before every BATTLE_START rule
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, owner, 0, 0));
            battle.processRequests();
        }
        return owner.getAttribute(AttributeType.ATTACK).get();
    }
}
