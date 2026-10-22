package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1408 (Phainon 白厄), trace 1408102 【身承炎炬万千】: 「受到<b>队友</b>提供的治疗效果或护盾时，造成的伤害提高
 * <b>45%</b>，持续 <b>4</b> 回合。该效果<b>单个回合内不可重复触发</b>。」 - and its second sentence, which is NOT written.
 *
 * <p>The four cases pin the four things that can go wrong with this sentence, each of them a different way to be wrong:
 * the share (0.45), the duration (gone after the fourth of his own turns, still there after the third), <b>who</b> counts as
 * the giver (a teammate yes, himself no), and the second source (a shield, not only a heal).
 *
 * <p>⚠ What is NOT asserted here, and why: 「单个回合内不可重复触发」 ({@code per_turn: 1}). Two applications inside one
 * turn produce the SAME number (the second one refreshes 0.45 to 0.45), so no reading of the attribute can tell them
 * apart; the field's own judge is the engine-level {@code TriggerLimitTest}. What this class pins instead is that the rule
 * carries the limit at all - see {@link #theRuleCarriesTheOncePerTurnLimit()}.
 *
 * <p>⚠ The second sentence (「受到队友提供的能量恢复的技能效果时，获得 1 点【火种】」) is a <b>witness</b>:
 * {@code ENERGY_GAINED} fires with the RECEIVER as its actor and carries no granter, so "from a teammate's ability" cannot
 * be asked today. {@link #theEnergyHalfIsStillANoOp()} pins today's (wrong-by-omission) reading so the gap cannot quietly
 * change, and the rule's own note registers it.
 */
public class PhainonTraceBideInFlamesTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;
    private static final String COREFLAME = "火种";

    /**
     * A teammate's heal raises his damage by 45%, and the boost is gone after his fourth own turn.
     */
    @Test
    public void aTeammatesHealRaisesHisDamageForFourTurns() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        phainon.consumeHp(phainon.getMaxHp() * 0.3);

        double before = boost(phainon);
        battle.heal(ally, phainon, phainon.getMaxHp() * 0.1);
        battle.processRequests();
        double gained = boost(phainon) - before;
        System.out.println("[1408] 队友治疗后的增伤: +" + gained);
        Assertions.assertEquals(0.45, gained, 1e-9, "「受到队友提供的治疗效果…造成的伤害提高 45%」");

        TestTurns.take(battle, phainon);
        TestTurns.take(battle, phainon);
        TestTurns.take(battle, phainon);
        Assertions.assertEquals(0.45, boost(phainon) - before, 1e-9,
                "「持续 4 回合」⇒ 第 3 个自己的回合之后仍然在");
        TestTurns.take(battle, phainon);
        System.out.println("[1408] 第 4 个自己的回合开始后的增伤: +" + (boost(phainon) - before));
        Assertions.assertEquals(0.0, boost(phainon) - before, 1e-9,
                "「持续 4 回合」⇒ 第 4 个自己的回合开始时到期");
    }

    /**
     * A teammate's SHIELD is the sentence's second source - the same rule must fire for it.
     */
    @Test
    public void aTeammatesShieldAlsoRaisesIt() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double before = boost(phainon);
        battle.grantShield(ally, phainon, 200);
        battle.processRequests();
        System.out.println("[1408] 队友护盾后的增伤: +" + (boost(phainon) - before));
        Assertions.assertEquals(0.45, boost(phainon) - before, 1e-9,
                "「治疗效果或护盾」—— 护盾那一半也是同一个效果");
    }

    /**
     * 「<b>队友</b>提供的」 - his own heal is not a teammate's.
     */
    @Test
    public void hisOwnHealDoesNotCount() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        phainon.consumeHp(phainon.getMaxHp() * 0.3);
        double before = boost(phainon);
        battle.heal(phainon, phainon, phainon.getMaxHp() * 0.1);
        battle.processRequests();
        System.out.println("[1408] 自己治疗后的增伤: +" + (boost(phainon) - before));
        Assertions.assertEquals(0.0, boost(phainon) - before, 1e-9,
                "「队友提供的」—— 少了 `actor is_other_ally` 时这一条会红");
    }

    /**
     * The rule carries the once-per-turn limit, and it is <b>one</b> rule on <b>both</b> events.
     *
     * <p>⚠ Two applications inside one turn are indistinguishable by the attribute's value (the second one refreshes 0.45
     * to 0.45), so the limit's reading is the engine-level {@code TriggerLimitTest}. What this case can pin is the shape the
     * sentence needs: one rule for the two sources, or the limit would be two allowances ("一次治疗 + 一次护盾" per turn).
     */
    @Test
    public void theRuleCarriesTheOncePerTurnLimit() {
        Character phainon = newBattle().characters().getFirst();
        List<TriggerTable.CompiledRule> onHeal = rulesFor(phainon, TriggerEvent.HEALED);
        Assertions.assertEquals(1, onHeal.size(), "HEALED 上只该有这一条");
        Assertions.assertEquals(1, onHeal.getFirst().perTurn(), "「该效果单个回合内不可重复触发」= per_turn: 1");
        Assertions.assertEquals(1, rulesFor(phainon, TriggerEvent.SHIELD_GRANTED).size(),
                "护盾那一半是**同一条**规则（`on_any`）—— 拆成两条就等于每回合可以触发两次");
    }

    /**
     * ⚠ <b>GAP WITNESS.</b> 「受到队友提供的能量恢复的技能效果时，获得 1 点【火种】」 - today's reading is "nothing", because
     * the energy event names the receiver and not the granter. When the event grows a granter, this case goes red and the
     * registered half can be written.
     */
    @Test
    public void theEnergyHalfIsStillANoOp() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        int before = phainon.getResources().value(COREFLAME);
        battle.applyEnergyGain(phainon, EnergyGain.normal(10));
        battle.processRequests();
        int after = phainon.getResources().value(COREFLAME);
        System.out.println("[1408] 收到 10 点回能后的火种: " + before + " -> " + after + " (登记：这一句还没写)");
        Assertions.assertEquals(before, after,
                "登记中的缺口：ENERGY_GAINED 不带「谁给的」，所以「队友提供的回能」今天判不了");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double boost(Character unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /**
     * The compiled rules one event carries for Phainon.
     */
    private static List<TriggerTable.CompiledRule> rulesFor(Character unit, TriggerEvent event) {
        return unit.getTriggerTable().rulesFor(event);
    }

    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
