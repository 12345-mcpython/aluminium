package com.laosun.aluminium.test.content.characters.topaz;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
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
 * 1112 托帕&账账's Ultimate 「扭亏为盈！」: 「使账账进入【涨幅惊人！】状态，伤害倍率提高 150%，暴击伤害提高 25%，且当陷入【负债证明】状态下的敌方目标
 * 受到我方的普攻、战技或终结技的攻击时，账账行动提前 50%。账账施放 2 次攻击后退出【涨幅惊人！】状态。」, plus the two clauses that read it
 * (星魂 6 and 行迹 技术性调整).
 *
 * <p>The state is a real {@code APPLY_BUFF} on the SUMMON, so its two stat halves are gated by {@code while_holder_state} -
 * which is what keeps them attached to 账账 rather than to 托帕. The exit is a counter ({@code 涨幅次数}) that each of its
 * attacks spends.
 */
public class TopazUltimateTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;
    private static final String STATE = "涨幅惊人";
    private static final String CHARGES = "涨幅次数";
    private static final String MARK = "负债证明";
    private static final double EPS = 1e-9;

    @Test
    public void theUltimatePutsNumbyIntoWindfall() {
        Scene scene = new Scene(0, true);
        Assertions.assertFalse(scene.numby().getBuffManager().hasState(STATE), "control: not in the state before the Ultimate");
        scene.ultimate();
        Assertions.assertTrue(scene.numby().getBuffManager().hasState(STATE), "「使账账进入【涨幅惊人！】状态」");
        Assertions.assertEquals(2, scene.numby().getBuffManager().stacksOf(CHARGES), "「账账施放 2 次攻击后退出」");
        Assertions.assertEquals(0.25, scene.numby().getAttribute(AttributeType.CRIT_ATTACK).get()
                - CharacterFactory.create(TOPAZ, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "「暴击伤害提高 25%」 - on 账账, not on 托帕");
    }

    /**
     * 「伤害倍率提高 150%，暴击伤害提高 25%」 measured as one attack's total.
     *
     * <p>⚠ The boost is <b>additive</b> with the Fire boost she already carries, so the expected number is
     * {@code (1 + fire + 1.5) / (1 + fire)}, not a flat 2.5 - hardcoding 2.5 is what the first draft did, and it read
     * 2.5964 instead of 2.9167 (measured 2026-11-08). The crit half is the plain {@code (1 + 0.75) / (1 + 0.5)}.
     */
    @Test
    public void windfallRaisesNumbysStrike() {
        Scene plain = new Scene(0, true);
        double fireBoost = plain.numby().getAttribute(AttributeType.FIRE_DAMAGE_BOOST).get();
        double critBefore = plain.numby().getAttribute(AttributeType.CRIT_ATTACK).get();
        double without = plain.strike();

        Scene buffed = new Scene(0, true);
        buffed.ultimate();
        double critAfter = buffed.numby().getAttribute(AttributeType.CRIT_ATTACK).get();
        double with = buffed.strike();

        double expected = (1 + fireBoost + 1.5) / (1 + fireBoost) * (1 + critAfter) / (1 + critBefore);
        System.out.println("[1112] numby strike: plain=" + without + " windfall=" + with
                + " ratio=" + (with / without) + " expected=" + expected);
        Assertions.assertTrue(without > 0, "precondition: the strike landed");
        Assertions.assertEquals(expected, with / without, 0.01,
                "「伤害倍率提高 150%」 on top of the existing Fire boost, and 「暴击伤害提高 25%」: " + (with / without));
    }

    /** 「账账施放 2 次攻击后退出【涨幅惊人！】状态」 - and 星魂 6 buys a third attack. */
    @Test
    public void theStateEndsAfterTwoAttacksAndThreeAtEidolonSix() {
        Scene plain = new Scene(0, true);
        plain.ultimate();
        plain.takeTurn();
        Assertions.assertTrue(plain.numby().getBuffManager().hasState(STATE), "one attack is not enough");
        plain.takeTurn();
        Assertions.assertFalse(plain.numby().getBuffManager().hasState(STATE), "「施放 2 次攻击后退出」");
        Assertions.assertEquals(0, plain.numby().getBuffManager().stacksOf(CHARGES), "the budget is spent");

        // The two modifiers are gated on the state (`while_holder_state`), and the gate is asked at the summon's own turn
        // boundary - so once the state is gone they leave too, instead of being a permanent buff that outlives the sentence.
        plain.numby().getBuffManager().beforeMove();
        Assertions.assertEquals(0.0, plain.numby().getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), EPS,
                "「伤害倍率提高 150%」 left with 【涨幅惊人！】");
        Assertions.assertEquals(0.5, plain.numby().getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "…and so did 「暴击伤害提高 25%」");

        Scene six = new Scene(6, true);
        six.ultimate();
        Assertions.assertEquals(3, six.numby().getBuffManager().stacksOf(CHARGES),
                "「账账处于【涨幅惊人！】状态的攻击次数增加 1 次」");
        for (int attack = 1; attack <= 3; attack++) {
            six.takeTurn();
        }
        Assertions.assertFalse(six.numby().getBuffManager().hasState(STATE), "three attacks, then out");
    }

    /** 星魂 6 second half: 「且攻击时火属性抗性穿透提高 10%」 - on 账账, and only while in the state. */
    @Test
    public void eidolonSixPiercesResistanceInTheState() {
        Scene six = new Scene(6, true);
        Assertions.assertEquals(0.0, six.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "control: nothing before the Ultimate");
        six.ultimate();
        Assertions.assertEquals(0.1, six.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "「攻击时火属性抗性穿透提高 10%」");

        Scene plain = new Scene(0, true);
        plain.ultimate();
        Assertions.assertEquals(0.0, plain.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "control: that sentence is 星魂 6's");
    }

    /**
     * 「当陷入【负债证明】状态下的敌方目标受到我方的普攻…攻击时，账账行动提前 50%」 - three controls, each one of the sentence's
     * own conditions.
     */
    @Test
    public void anAllyHitAdvancesNumbyOnlyWhileInTheState() {
        Scene inState = new Scene(0, true);
        inState.ultimate();
        double before = inState.remaining();
        inState.basicAttack();
        Assertions.assertEquals(before * 0.5, inState.remaining(), 1e-6,
                "「…受到我方的普攻…攻击时，账账行动提前 50%」");

        Scene noState = new Scene(0, true);
        double armed = noState.remaining();
        noState.basicAttack();
        Assertions.assertEquals(armed, noState.remaining(), 1e-9,
                "control: without 【涨幅惊人！】 the sentence does not apply");

        Scene unmarked = new Scene(0, true);
        unmarked.ultimate();
        unmarked.clearMark();
        double untouched = unmarked.remaining();
        unmarked.basicAttack();
        Assertions.assertEquals(untouched, unmarked.remaining(), 1e-9,
                "control: no 【负债证明】 on the target, no advance");
    }

    /** 行迹 技术性调整:「当账账处于【涨幅惊人！】状态施放攻击后，额外使托帕恢复 10 点能量」 - judged as the difference from the SAME action without the state, so the attack own energy cancels. */
    @Test
    public void theTracePaysTenWhileInTheState() {
        Scene inState = new Scene(0, true);
        inState.ultimate();
        double withState = inState.act();

        Scene plain = new Scene(0, true);
        double withoutState = plain.act();

        Assertions.assertEquals(10.0, withState - withoutState, 1e-9,
                "「…额外使托帕恢复 10 点能量」 (" + withState + " vs " + withoutState + ")");
    }

    /**
     * 战技:「我方目标回合开始或行动时，若场上不存在陷入【负债证明】状态的敌方目标，托帕使随机敌方目标陷入【负债证明】状态」.
     *
     * <p>Two facts, and the second is what an over-eager version would get wrong: nobody carrying it ⇒ exactly one enemy gets
     * it; somebody carrying it ⇒ the mark does not move.
     */
    @Test
    public void theMarkRenewsItselfWhenNobodyCarriesIt() {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL);
        Enemy first = BattleFixture.monster();
        Enemy second = BattleFixture.monster();
        for (Enemy each : List.of(first, second)) {
            each.setMaxStance(10_000);
        }
        Battle battle = new Battle(List.of(topaz), List.of(first, second), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(holdsMark(first) || holdsMark(second),
                "control: nothing is marked before the first ally turn");

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, topaz, topaz, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(1, (holdsMark(first) ? 1 : 0) + (holdsMark(second) ? 1 : 0),
                "「若场上不存在…使随机敌方目标陷入【负债证明】」 - exactly one enemy is marked");

        boolean firstWasMarked = holdsMark(first);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, topaz, topaz, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(firstWasMarked, holdsMark(first),
                "somebody already carries it, so the clause does nothing - the mark does not move");
    }

    private static boolean holdsMark(Enemy enemy) {
        return enemy.getBuffManager().hasState(MARK);
    }

    // ==================================================================

    private static final class Scene {
        private final Character topaz;
        private final Enemy enemy;
        private final Battle battle;

        private Scene(int eidolon, boolean mark) {
            topaz = CharacterFactory.create(TOPAZ, LEVEL, false, null, null, eidolon);
            enemy = BattleFixture.monster();
            enemy.setMaxStance(10_000);
            battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            if (mark) {
                // Her Skill is the only way to put 【负债证明】 on a target (and it is what the sentence reads).
                battle.castImmediate(topaz.getSkills().get(SkillType.SKILL), topaz, List.of(enemy));
                battle.processRequests();
            }
        }

        private Summon numby() {
            return battle.summonsOf(topaz).getFirst();
        }

        private void ultimate() {
            battle.castImmediate(topaz.getSkills().get(SkillType.ULTRA), topaz, List.of(enemy));
            battle.processRequests();
        }

        private void basicAttack() {
            battle.castImmediate(topaz.getSkills().get(SkillType.COMMON), topaz, List.of(enemy));
            battle.processRequests();
        }

        private void clearMark() {
            enemy.getBuffManager().removeState(MARK);
        }

        /** One of Numby's actions (its turn start is its attack point; the budget is spent there too). */
        private void takeTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby(), numby(), 1, 0));
            battle.processRequests();
        }

        /** The energy one of its actions pays 托帕. */
        private double act() {
            topaz.setCurrentEnergy(0);
            takeTurn();
            return topaz.getCurrentEnergy();
        }


        private double remaining() {
            for (Signal signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == (CanHit) numby()) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new IllegalStateException("账账 is not in the action order");
        }

        /** One strike's total on the enemy, with or without the Ultimate in effect. */
        private double strike() {
            double before = enemy.getCurrentHp();
            takeTurn();
            return before - enemy.getCurrentHp();
        }
    }

}

