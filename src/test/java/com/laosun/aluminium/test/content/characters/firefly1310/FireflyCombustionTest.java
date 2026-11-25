package com.laosun.aluminium.test.content.characters.firefly1310;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1310 流萤's 【完全燃烧】, and the Eidolons that hang off it.
 *
 * <p><b>Why the state came first.</b> Eidolons 2 / 4 / 6 are all written 「「完全燃烧」状态下…」, and before this the state did
 * not exist at all - so writing them would have been three rules gated on a name nothing ever applied.
 *
 * <p>What the state is made of is a set of effects that all carry {@code while_state}, which is what makes ONE
 * {@code REMOVE_STATE} enough: the enhanced slots, the +60 SPD and the Eidolon modifiers stop with it, with no "revert"
 * rules needed.
 */
public class FireflyCombustionTest {
    private static final int FIREFLY = 1310;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String STATE = "char:1310/complete_combustion";

    /**
     * The Ultimate enters the state, swaps both slots, adds 60 SPD, and puts a 70-speed countdown on the order.
     */
    @Test
    public void theUltimateEntersCompleteCombustion() {
        Run run = new Run(0);
        double before = run.firefly.getAttribute(AttributeType.SPEED).get();

        run.ult();

        Assertions.assertTrue(run.firefly.getBuffManager().hasState(STATE), "「进入「char:1310/complete_combustion」状态」");
        Assertions.assertEquals(before + 60, run.firefly.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "「速度提高 60 点」 - sixty POINTS, not a share");
        Assertions.assertEquals(8, run.firefly.getSkills().get(SkillType.COMMON).getSkillSlot(), "强化普攻 (row 8)");
        Assertions.assertEquals(9, run.firefly.getSkills().get(SkillType.SKILL).getSkillSlot(), "强化战技 (row 9)");
        Assertions.assertEquals(1, run.battle.countdownsOf(run.firefly).size(), "the char:1310/complete_combustion countdown is on the order");
    }

    /**
     * 「「完全燃烧」状态下装甲「萨姆」无法施放终结技」 - the state seals the Ultimate slot only.
     */
    @Test
    public void theStateSealsTheUltimate() {
        Run run = new Run(0);
        run.ult();
        Assertions.assertTrue(run.firefly.getBuffManager().suppresses("ultimate"),
                "「无法施放终结技」 - `blocks: \"ultimate\"`");
        Assertions.assertFalse(run.firefly.getBuffManager().suppresses("skill"),
                "a Skill is NOT sealed (it is the enhanced one)");
    }

    /**
     * The countdown's turn ends the state - and with it everything gated by {@code while_state}.
     */
    @Test
    public void theCountdownEndsTheStateAndEverythingRidingOnIt() {
        Run run = new Run(0);
        double before = run.firefly.getAttribute(AttributeType.SPEED).get();
        run.ult();
        Assertions.assertEquals(before + 60, run.firefly.getAttribute(AttributeType.SPEED).get(), 1e-6);

        run.countdownTurns();

        Assertions.assertFalse(run.firefly.getBuffManager().hasState(STATE), "「倒计时回合开始时…解除」");

        // The `while_state` modifiers are pruned at the next TURN BOUNDARY (the rule 1205 地狱变 follows), so the panel
        // has to cross one before it reads the baseline.
        run.battle.setCurrentMove(new com.laosun.aluminium.Signal(run.firefly));
        run.battle.beforeMove();
        run.battle.processRequests();
        Assertions.assertEquals(before, run.firefly.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the +60 rides on `while_state`, so it leaves with the state");
        Assertions.assertEquals(1, run.firefly.getSkills().get(SkillType.COMMON).getSkillSlot(), "the base basic is back");
        Assertions.assertEquals(2, run.firefly.getSkills().get(SkillType.SKILL).getSkillSlot(), "the base Skill is back");
        Assertions.assertFalse(run.firefly.getBuffManager().suppresses("ultimate"), "and the Ultimate is castable again");
    }

    /**
     * 「施放强化普攻、强化战技时自身弱点击破效率提高 50%…持续至本次攻击结束」.
     */
    @Test
    public void anEnhancedCastBreaksFasterOnlyForThatCast() {
        Run run = new Run(0);
        run.ult();
        double bare = run.firefly.getAttribute(AttributeType.WEAKNESS_BREAK_EFFICIENCY).get();

        run.battle.fire(TriggerPayload.Simple.of(TriggerEvent.CAST_SETUP, run.firefly, run.enemy, 0, 0));
        Assertions.assertEquals(0.5, run.firefly.getAttribute(AttributeType.WEAKNESS_BREAK_EFFICIENCY).get() - bare, 1e-9,
                "「弱点击破效率提高 50%」");

        // A real cast closes its own window (`cast_end`), and the bonus goes with it.
        run.battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(FIREFLY, 8, LEVEL), run.firefly,
                List.of(run.enemy));
        run.battle.processRequests();
        Assertions.assertEquals(0, run.firefly.getAttribute(AttributeType.WEAKNESS_BREAK_EFFICIENCY).get() - bare, 1e-9,
                "「持续至本次攻击结束」 - the cast window closed it");
    }

    /**
     * Eidolon 4: +50% effect RES while in the state.
     */
    @Test
    public void theFourthEidolonResistsMoreInCombustion() {
        Run e3 = new Run(3);
        Run e4 = new Run(4);
        e3.ult();
        e4.ult();
        Assertions.assertEquals(0.5, e4.firefly.getAttribute(AttributeType.EFFECT_RESISTANCE).get()
                - e3.firefly.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "「效果抵抗提高 50%」");
    }

    /**
     * Eidolon 6: 20% FIRE penetration while in the state - per element, not all-type.
     */
    @Test
    public void theSixthEidolonPiercesMoreWithFire() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        e5.ult();
        e6.ult();
        Assertions.assertEquals(0.2, e6.firefly.getAttribute(AttributeType.DAMAGE_PENETRATION_FIRE).get()
                - e5.firefly.getAttribute(AttributeType.DAMAGE_PENETRATION_FIRE).get(), 1e-9,
                "「火属性抗性穿透提高 20%」");
        Assertions.assertEquals(e5.firefly.getAttribute(AttributeType.DAMAGE_PENETRATION).get(),
                e6.firefly.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "per-element, not all-type");
    }

    /**
     * Eidolon 2: a kill in the state earns an extra turn; a break does too.
     */
    @Test
    public void theSecondEidolonEarnsATurnFromAKillorABreak() {
        Run kill = new Run(2);
        kill.ult();
        kill.battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, kill.firefly, kill.enemy, 0, 0));
        kill.battle.processRequests();
        Assertions.assertSame(kill.firefly, kill.battle.getExtraTurnActor(), "「消灭敌方目标…立即获得 1 个额外回合」");

        Run breach = new Run(2);
        breach.ult();
        breach.battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, breach.firefly, breach.enemy, 0, 0));
        breach.battle.processRequests();
        Assertions.assertSame(breach.firefly, breach.battle.getExtraTurnActor(),
                "「…或使目标陷入弱点击破状态时」");
    }

    /**
     * 「使敌方目标受到装甲「萨姆」造成的**击破伤害**提高 20%」 - scoped to break damage by `damage_type: BREAK`, so an
     * ordinary hit is untouched.
     *
     * <p>⚠ This clause was registered as unwritable for a while (「`MODIFY_DAMAGE_TAKEN` only narrows by element」). That
     * was stale: the op has a `damage_type` scope, and it is listed in `TriggerVocabulary.DAMAGE_TYPE_READERS`.
     */
    @Test
    public void anEnhancedCastRaisesBreakDamageTakenOnly() {
        double plainBreak = damageOf(false, false);
        double inCombustion = damageOf(false, true);
        Assertions.assertEquals(1.2, inCombustion / plainBreak, 1e-6,
                "「击破伤害提高 20%」 - a BREAK instance takes 20% more while the cast window is open");

        double plainHit = damageOf(true, false);
        double hitInCombustion = damageOf(true, true);
        Assertions.assertEquals(plainHit, hitInCombustion, 1e-6,
                "an ordinary hit is NOT break damage, so the zone does not touch it");
    }
    // ==================================================================

    /**
     * The damage one instance deals to a fresh dummy: a BREAK or an ORDINARY hit, with or without the cast window open.
     */
    private static double damageOf(boolean ordinary, boolean inCombustion) {
        Run run = new Run(0);
        if (inCombustion) {
            run.ult();
            run.battle.fire(TriggerPayload.Simple.of(TriggerEvent.CAST_SETUP, run.firefly, run.enemy, 0, 0));
        }
        double before = run.enemy.getCurrentHp();
        run.battle.applyDamage(run.enemy, new com.laosun.aluminium.damage.Damage(run.firefly, run.enemy,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                ordinary ? com.laosun.aluminium.enums.DamageType.NORMAL : com.laosun.aluminium.enums.DamageType.BREAK,
                1000));
        return before - run.enemy.getCurrentHp();
    }

    private static final class Run {
        private final Character firefly;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        Run(int eidolon) {
            firefly = CharacterFactory.create(FIREFLY, LEVEL);
            firefly.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(firefly, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void ult() {
            battle.castImmediate(firefly.getSkills().get(SkillType.ULTRA), firefly, List.of(enemy));
            battle.processRequests();
        }

        /** The countdown's own turn, announced the way the engine announces it. */
        void countdownTurns() {
            com.laosun.aluminium.entity.CanHit countdown = battle.countdownsOf(firefly).getFirst();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.COUNTDOWN_TURN, countdown, firefly, 0, 0));
            battle.processRequests();
        }
    }
}
