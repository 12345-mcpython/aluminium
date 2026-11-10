package com.laosun.aluminium.test.content.characters.herta1401;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1401 大黑塔's Eidolons 2 / 4 / 6-first-half: one more 【灵感】 on entry and on the Ultimate, the enhanced Skill's 35%
 * advance, the 12% speed for 「智识」 allies, and the 20% ICE penetration.
 *
 * <p><b>Why a criterion.</b> Each half has an obvious wrong neighbour: 「额外」 on the inspiration is ADDITION (a single
 * rule would be read as a replacement), the advance is gated on the ENHANCED Skill row (slot 9, not any Skill), the speed
 * is scoped by PATH (not the whole party), and the penetration is per-ELEMENT (not the all-type one).
 */
public class HertaEruditionEidolonsTest {
    private static final int HERTA = 1401;
    private static final int ERUDITION_ALLY = 1013;   // 黑塔, also 智识
    private static final int OTHER_ALLY = 1002;       // 丹恒, 巡猎
    private static final int LEVEL = 80;
    private static final String INSPIRATION = "灵感";

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

    // ==================================================================

    /**
     * 「对指定敌方单体…施加 1 层【解读】」 on the ENHANCED Skill too (a different data row than the base Skill).
     */
    @Test
    public void theEnhancedSkillAppliesItsOwnInsight() {
        Run run = new Run(0);
        int before = run.enemy.getBuffManager().stacksOf("解读");
        run.battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(HERTA, 9, LEVEL), run.herta,
                List.of((com.laosun.aluminium.entity.CanHit) run.enemy));
        run.battle.processRequests();
        Assertions.assertEquals(before + 1, run.enemy.getBuffManager().stacksOf("解读"),
                "the enhanced cast's own layer - the base Skill's rule is gated on `from_skill_id == 2`");
    }

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
}
