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
 *
 * <p><b>L3:</b> the cases that share one fixture and one observed object are folded into the scenario tests below, one
 * {@code Assertions.assertAll} per scenario, each entry carrying a short message that names its clause: the state with its
 * two modifiers, and the two-attack exit with 星魂 6's second half. Every assertion, expected value, tolerance and message
 * is unchanged. The advance clause, the 行迹 技术性调整 and the mark's renewal keep their own tests - different fixtures and
 * different observed objects, and an {@code assertAll} is not where they belong.
 */
public class TopazUltimateTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;
    private static final String STATE = "char:1112/staggering_rise";
    private static final String CHARGES = "internal:1112/rise_count";
    private static final String MARK = "char:1112/debt_proof";
    private static final double EPS = 1e-9;

    /**
     * The Ultimate's 【涨幅惊人！】, judged on 账账 itself: the state and its budget, then the two modifiers as one attack's total.
     *
     * <p>⚠ The boost is <b>additive</b> with the Fire boost she already carries, so the expected number is
     * {@code (1 + fire + 1.5) / (1 + fire)}, not a flat 2.5 - hardcoding 2.5 is what the first draft did, and it read
     * 2.5964 instead of 2.9167 (measured 2026-11-08). The crit half is the plain {@code (1 + 0.75) / (1 + 0.5)}.
     *
     * <p>One scenario, because both readings are the same fixture (a marked 星魂 0 scene) and the same object - 账账 under
     * 【涨幅惊人！】.
     */
    @Test
    public void theUltimatePutsNumbyIntoWindfallAndRaisesItsStrike() {
        Scene buffed = new Scene(0, true);
        boolean inStateBefore = buffed.numby().getBuffManager().hasState(STATE);
        buffed.ultimate();
        boolean inStateAfter = buffed.numby().getBuffManager().hasState(STATE);
        int budget = buffed.numby().getBuffManager().stacksOf(CHARGES);
        double critAfter = buffed.numby().getAttribute(AttributeType.CRIT_ATTACK).get();
        double with = buffed.strike();

        Scene plain = new Scene(0, true);
        double fireBoost = plain.numby().getAttribute(AttributeType.FIRE_DAMAGE_BOOST).get();
        double critBefore = plain.numby().getAttribute(AttributeType.CRIT_ATTACK).get();
        double without = plain.strike();

        double baseCrit = CharacterFactory.create(TOPAZ, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();
        double expected = (1 + fireBoost + 1.5) / (1 + fireBoost) * (1 + critAfter) / (1 + critBefore);
        System.out.println("[1112] numby strike: plain=" + without + " windfall=" + with
                + " ratio=" + (with / without) + " expected=" + expected);
        Assertions.assertAll("终结技 「使账账进入【char:1112/staggering_rise！】状态」: the state, its budget and its two modifiers",
                () -> Assertions.assertFalse(inStateBefore, "control: not in the state before the Ultimate"),
                () -> Assertions.assertTrue(inStateAfter, "「使账账进入【char:1112/staggering_rise！】状态」"),
                () -> Assertions.assertEquals(2, budget, "「账账施放 2 次攻击后退出」"),
                () -> Assertions.assertEquals(0.25, critAfter - baseCrit, EPS,
                        "「暴击伤害提高 25%」 - on 账账, not on 托帕"),
                () -> Assertions.assertTrue(without > 0, "precondition: the strike landed"),
                () -> Assertions.assertEquals(expected, with / without, 0.01,
                        "「伤害倍率提高 150%」 on top of the existing Fire boost, and 「暴击伤害提高 25%」: " + (with / without)));
    }

    /**
     * 「账账施放 2 次攻击后退出【涨幅惊人！】状态」 - and 星魂 6 buys a third attack plus 「攻击时火属性抗性穿透提高 10%」.
     *
     * <p>The two modifiers are gated on the state (`while_holder_state`), and the gate is asked at the summon's own turn
     * boundary - so once the state is gone they leave too, instead of being a permanent buff that outlives the sentence.
     * The pierce is 星魂 6's own sentence, so it must be absent at 星魂 0.
     *
     * <p>One scenario, because both 星魂 6 halves read the same 星魂 6 scene, and the marked 星魂 0 scene is the control both
     * halves need (its exit is the base kit's, its zero pierce is what makes the 10% the Eidolon's).
     */
    @Test
    public void theStateEndsAfterTwoAttacksAndEidolonSixPaysAPierce() {
        Scene plain = new Scene(0, true);
        plain.ultimate();
        double pierceWithoutEidolon = plain.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        plain.takeTurn();
        boolean aliveAfterOne = plain.numby().getBuffManager().hasState(STATE);
        plain.takeTurn();
        boolean aliveAfterTwo = plain.numby().getBuffManager().hasState(STATE);
        int spentBudget = plain.numby().getBuffManager().stacksOf(CHARGES);

        // The two modifiers are gated on the state (`while_holder_state`), and the gate is asked at the summon's own turn
        // boundary - so once the state is gone they leave too, instead of being a permanent buff that outlives the sentence.
        plain.numby().getBuffManager().beforeMove();
        double boostAfterExit = plain.numby().getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double critAfterExit = plain.numby().getAttribute(AttributeType.CRIT_ATTACK).get();

        Scene six = new Scene(6, true);
        double pierceBefore = six.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        six.ultimate();
        double pierceInState = six.numby().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        int sixBudget = six.numby().getBuffManager().stacksOf(CHARGES);
        for (int attack = 1; attack <= 3; attack++) {
            six.takeTurn();
        }
        boolean aliveAfterThree = six.numby().getBuffManager().hasState(STATE);

        Assertions.assertAll("「账账施放 2 次攻击后退出【char:1112/staggering_rise！】状态」 and 星魂 6's third attack + 10% RES pierce",
                () -> Assertions.assertTrue(aliveAfterOne, "one attack is not enough"),
                () -> Assertions.assertFalse(aliveAfterTwo, "「施放 2 次攻击后退出」"),
                () -> Assertions.assertEquals(0, spentBudget, "the budget is spent"),
                () -> Assertions.assertEquals(0.0, boostAfterExit, EPS,
                        "「伤害倍率提高 150%」 left with 【char:1112/staggering_rise！】"),
                () -> Assertions.assertEquals(0.5, critAfterExit, EPS,
                        "…and so did 「暴击伤害提高 25%」"),
                () -> Assertions.assertEquals(0.0, pierceWithoutEidolon, EPS,
                        "control: that sentence is 星魂 6's"),
                () -> Assertions.assertEquals(0.0, pierceBefore, EPS,
                        "control: nothing before the Ultimate"),
                () -> Assertions.assertEquals(0.1, pierceInState, EPS,
                        "「攻击时火属性抗性穿透提高 10%」"),
                () -> Assertions.assertEquals(3, sixBudget,
                        "「账账处于【char:1112/staggering_rise！】状态的攻击次数增加 1 次」"),
                () -> Assertions.assertFalse(aliveAfterThree, "three attacks, then out"));
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
                "control: without 【char:1112/staggering_rise！】 the sentence does not apply");

        Scene unmarked = new Scene(0, true);
        unmarked.ultimate();
        unmarked.clearMark();
        double untouched = unmarked.remaining();
        unmarked.basicAttack();
        Assertions.assertEquals(untouched, unmarked.remaining(), 1e-9,
                "control: no 【char:1112/debt_proof】 on the target, no advance");
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
                "「若场上不存在…使随机敌方目标陷入【char:1112/debt_proof】」 - exactly one enemy is marked");

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

