package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the two halves of her trace 洞察万物 (Insight Into All Things), the effect-resistance half
 * of 感知容差, and Eidolon 6's all-type resistance penetration.
 *
 * <p>Two things this class is built around, both measured:
 * <ul>
 *   <li><b>The DEF threshold has to be set, not earned.</b> Her Defence at Lv80 is under 2400, so the rule would
 *       never fire on a plain {@code CharacterFactory.create} - the judge sets the attribute directly and asserts
 *       three points around the threshold (2400 / 2500 / 4000), which is what makes "32%, then 3% per 100 over" a
 *       number rather than a claim.</li>
 *   <li><b>The two tiers are two modifiers on the SAME attribute</b>, and same-kind modifiers with the default
 *       {@code max_stacks: 1} REPLACE each other in this engine instead of adding. So the judge reads the total and
 *       says which shape the content currently ships; if the engine ever merges them differently, this is where it
 *       shows up.</li>
 * </ul>
 */
public class PearlInsightTraceTest {
    private static final int PEARL = 1503;
    private static final int HUNTER = 1204;      // The Hunt (巡猎): not Elation, a plain bystander
    private static final double ELATION = 0.32;
    private static final double PER_POINT_OVER = 0.0003;   // 3% per 100 DEF
    private static final double EPS = 1e-6;

    /**
     * 洞察万物: at or above 2400 DEF the boost is 32% plus 3% per 100 points over, counted up to 3600.
     *
     * <p>The three readings are chosen to separate the two clauses: 2400 is the threshold itself (32% flat, nothing
     * over it), 2500 adds exactly 100 points of excess, and 4000 is well past the 3600 cap (where the output is
     * expected to stop moving).
     */
    @Test
    public void theInsightTracePaysThirtyTwoPercentPlusThreePerHundredOver2400() {
        double below = elationWith(2300);
        double at = elationWith(2400);
        double over = elationWith(2500);
        double capped = elationWith(4000);
        double farPast = elationWith(5000);
        System.out.println("[pearl_insight] Defence -> Elation: 2300 = " + below + " | 2400 = " + at
                + " | 2500 = " + over + " | 4000 = " + capped + " | 5000 = " + farPast);

        // Note: The assertions are DIFFERENCES, not absolute values. Pearl's panel already carries her ten stat traces
        // (10% Elation - measured absent from `skill_traces.json` and shipped as content), so an absolute reading here
        // would be asserting that other clause as much as this one. Below 2400 this clause pays nothing, which makes
        // 2300 the baseline the rest is measured from.
        Assertions.assertEquals(ELATION, at - below, EPS,
                "at 2400 the clause adds its flat 32% and nothing for the excess");
        Assertions.assertEquals(PER_POINT_OVER * 100, over - at, EPS,
                "100 points over the threshold is one step of 3%");
        // 4000 is 1600 points over, but only 1200 of them are COUNTED ("最多计入 3600 点超出的防御力") - so the
        // clause adds 0.32 + 0.0003 x 1200 = 0.68 in total, i.e. 0.36 more than it pays at the threshold.
        Assertions.assertEquals(ELATION + PER_POINT_OVER * 1200, capped - below, 1e-4,
                "the boost stops at the 3600 counted point (32% + 36% = 68% in total)");
        Assertions.assertEquals(capped, farPast, EPS,
                "and past the cap it does not move any further");
    }

    /**
     * The healing link: "gains a healing bonus equal to 20% of her own Elation".
     *
     * <p>Note: The clause fires on BOTH fixtures - the difference between them is the Elation it reads - so the bonus
     * has to be compared against the Elation each run actually had, not against a single panel reading. The first
     * version of this case took the DIFFERENCE of the two runs' healing (0.084 - 0.020 = 0.064) and read it as "20% of
     * 0.32", which manufactured an engine puzzle that did not exist: a probe on the modifier path shows the rule
     * dividing exactly 20% of the value present when it fires (0.084 on the gated run, 0.020 on the below-gate one).
     */
    @Test
    public void theHealingBonusIsTwentyPercentOfTheElationItReads() {
        Character gated = pearlWith(2400, 0);
        Character belowGate = pearlWith(2300, 0);
        double elationGated = gated.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double elationBelow = belowGate.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double healingGated = gated.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double healingBelow = belowGate.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        System.out.println("[pearl_insight] gated: Elation " + elationGated + " -> healing " + healingGated
                + " ; below the gate: Elation " + elationBelow + " -> healing " + healingBelow
                + " ; the difference in Elation is " + (elationGated - elationBelow) + " and in healing "
                + (healingGated - healingBelow));

        Assertions.assertTrue(elationGated > elationBelow,
                "precondition: the gated run has more Elation (" + elationGated + " vs " + elationBelow + ")");
        Assertions.assertEquals(0.2 * elationGated, healingGated, EPS,
                "the bonus is 20% of the Elation she has when the clause fires");
        Assertions.assertEquals(0.2 * elationBelow, healingBelow, EPS,
                "and the same on the run below the gate: 20% of THAT run's Elation");
        // The two runs differ by exactly the gated tier, so their bonuses differ by 20% of it - which is the clause
        // answering to the value it actually reads rather than to a fixed number.
        Assertions.assertEquals(0.2 * (elationGated - elationBelow), healingGated - healingBelow, EPS,
                "the bonus tracks the Elation of the run it is in");
    }

    /**
     * 感知容差's other half: "while holding 【好活当赏】", the whole party's effect resistance is up by 50%.
     *
     * <p>Note: The gate is the <b>resource</b>, not a state - `self_resource:好活当赏 >= 1`, the spelling 1505 ships
     * three times. The first version of this rule used {@code self has_state 深度学习} and claimed the documented
     * gate "cannot be written today"; that claim was wrong, and this case is what pins the real one: the counter is
     * granted by the Ultimate, so the aura arrives on the cast that grants it (and the pre-cast reading is the
     * control that a wrong gate would break).
     */
    @Test
    public void theToleranceTraceRaisesThePartysEffectResistanceWhileHoldingTheCounter() {
        Character pearl = pearlWith(1200, 0);
        Character ally = CharacterFactory.create(HUNTER, 80);
        Battle battle = new Battle(List.of(pearl, ally), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double pearlBefore = pearl.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double allyBefore = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        // Note: The precondition is the COUNTER, not a zero on the panel: Pearl's stat traces put 10% effect RES on
        // her from the start (a different clause), so "no effect resistance before the Ultimate" would be asserting
        // that other clause's absence. What this case needs is that the AURA is not up yet, and the gate is the
        // counter - so the counter is what is asserted.
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), EPS,
                "precondition: she holds none of the counter yet, so the aura cannot be up");

        Character aim = battle.characters().get(1);
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, aim, 0, 0);
        battle.processRequests();
        double pearlGain = pearl.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - pearlBefore;
        double allyGain = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - allyBefore;
        System.out.println("[pearl_insight] effect RES after the Ultimate: Pearl +" + pearlGain
                + " ; the ally +" + allyGain + " ; the counter reads " + pearl.getResources().value(COUNTER));

        Assertions.assertTrue(pearl.getResources().value(COUNTER) >= 1,
                "precondition: the Ultimate granted 【好活当赏】, which is what the text's gate asks about");
        Assertions.assertEquals(0.5, pearlGain, EPS, "the party-wide clause includes Pearl herself");
        Assertions.assertEquals(0.5, allyGain, EPS, "and it is party-wide, not self-only");
    }

    /**
     * Eidolon 6: while deep-studying, the whole party's all-type resistance penetration is up by 20%.
     *
     * <p>Measured as a RATIO of one settled hit on the same enemy, with and without the penetration - the enemy has
     * real resistance to the element used, which is what makes penetration visible at all. The 20% penetration on a
     * 20% resistance cancels it (measured: the hit is a quarter stronger), so this also pins that
     * {@code DAMAGE_PENETRATION} is the all-type spelling, NOT one element's.
     *
     * <p>Note: The control is <b>a rank-6 Pearl whose WHOLE table is empty</b>, not a rank-0 Pearl. The first
     * version used rank 0, and a mutant of this rule's gate ("while deep-studying" changed to a state she never
     * has) SURVIVED it - because the control run was bypassing her table for a different reason, so the gate was
     * never the thing under test. Emptying the table keeps her Eidolon rank and removes exactly this rule.
     */
    @Test
    public void eidolonSixRaisesAllTypeResistancePenetrationForTheParty() {
        double plain = hitWith(true, false);
        double withPenetration = hitWith(true, true);
        double withoutTheRuleAtAll = hitWith(false, true);
        System.out.println("[pearl_insight] the same hit on the same enemy: " + plain + " without deep study ; "
                + withPenetration + " with it ; ratio = " + (withPenetration / plain)
                + " ; and " + withoutTheRuleAtAll + " with an empty table (the rule removed)");

        Assertions.assertTrue(plain > 0, "the control hit must land (" + plain + ")");
        Assertions.assertEquals(1.25, withPenetration / plain, 0.02,
                "20% penetration against this enemy's 20% resistance cancels it: 1 / 0.8 = 1.25");
        Assertions.assertEquals(plain, withoutTheRuleAtAll, 1e-9,
                "and with the rule removed the same hit lands unchanged, so the gate is what moves it");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final String DEEP = "深度学习";
    private static final String COUNTER = "好活当赏";

    /**
     * Pearl at the given Defence and Eidolon rank, with every {@code BATTLE_START} rule settled.
     */
    private static Character pearlWith(double defence, int eidolon) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, eidolon);
        pearl.setAttribute(AttributeType.DEFENCE, new com.laosun.aluminium.models.DoubleValue(defence));
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst();
    }

    /**
     * Her Elation (欢愉度) at the given Defence, i.e. after the 洞察万物 tier has run.
     */
    private static double elationWith(double defence) {
        return pearlWith(defence, 0).getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    /**
     * One settled hit on a fresh enemy, with or without Eidolon 6's penetration.
     *
     * <p>Both runs use the same enemy template and the same instance value, so the only thing that moves between
     * them is the penetration the Eidolon granted.
     */
    private static double hitWith(boolean keepHerKit, boolean deepStudy) {
        Character pearl = pearlWith(1200, 6);
        if (!keepHerKit) {
            pearl.setTriggerTable(TriggerTable.EMPTY);      // her Eidolon rank stays; only the rules go
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)), List.of(enemy),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        if (deepStudy) {
            // The Eidolon's own gate, which is also the sentence's: the Ultimate puts her into 深度学习.
            // Note: The Ultimate is fired at an ALLY, not at herself, and that is load-bearing for this judge.
            // Aimed at herself she carries BOTH states the rule could name (【深度学习】 is hers and 【美学底本】
            // falls on the aim), so a mutant of the gate to the archetype state survived - the gate only becomes
            // observable when the aim is somebody else, which is also how the ability is actually played.
            Character aim = battle.characters().get(1);
            battle.fireTriggers(TriggerEvent.ULT_CAST, her, aim, 0, 0);
            battle.processRequests();
            if (keepHerKit) {
                // Only assertable with her rules in place: 深度学习 is a state HER OWN Ultimate rule applies, so a
                // table-emptied Pearl never enters it (which is the whole point of that control run).
                Assertions.assertTrue(her.getBuffManager().hasState(DEEP), "precondition: she is deep-studying");
                Assertions.assertFalse(her.getBuffManager().hasState("美学底本"),
                        "precondition: the archetype mark went to the AIM, not to her - otherwise this judge cannot"
                                + " tell the two gates apart");
            }
        }
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(her, victim, DamageElement.ICE, DamageType.NORMAL, 1000));
        return before - victim.getCurrentHp();
    }
}
