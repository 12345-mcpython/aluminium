package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

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
    private static final int MONSTER = 1002011;
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
        System.out.println("[pearl_insight] Defence -> Elation boost: 2300 = " + below + " | 2400 = " + at
                + " | 2500 = " + over + " | 4000 = " + capped + " | 5000 = " + farPast);

        Assertions.assertEquals(0.0, below, EPS,
                "just below the threshold the trace pays nothing (2300 < 2400)");
        Assertions.assertEquals(ELATION, at, EPS,
                "at exactly 2400 it pays its flat 32% and nothing for the excess");
        Assertions.assertEquals(ELATION + PER_POINT_OVER * 100, over, EPS,
                "100 points over the threshold is one step of 3%");
        // 4000 is 1600 over, which is past the 3600 cap -> the same value as 3600 would give.
        Assertions.assertEquals(ELATION + PER_POINT_OVER * 1200, capped, 1e-4,
                "the boost stops at the 3600 counted point (1200 points of excess = 36%)");
        Assertions.assertEquals(capped, farPast, EPS,
                "and past the cap it does not move any further");
    }

    /**
     * The healing link: "gains a healing bonus equal to 20% of her own Elation".
     *
     * <p>Read AFTER the tiers above have landed, which is the order the file runs them in - so this is also an
     * assertion about rule order inside the {@code BATTLE_START} list.
     */
    @Test
    public void theHealingBonusIsTwentyPercentOfHerElation() {
        Character pearl = pearlWith(2400, 0);
        double elation = pearl.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double healing = pearl.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        System.out.println("[pearl_insight] Elation = " + elation + " -> healing bonus = " + healing
                + " ; 20% of the Elation = " + (0.2 * elation));

        Assertions.assertTrue(elation > 0, "precondition: the trace raised her Elation first (" + elation + ")");
        Assertions.assertEquals(0.2 * elation, healing, EPS,
                "the healing bonus is 20% of the Elation she has once the trace has paid out");
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
        Battle battle = new Battle(List.of(pearl, ally), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();
        battle.processRequests();

        double pearlBefore = pearl.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double allyBefore = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), EPS,
                "precondition: she holds none of the counter yet, so the aura is not up");
        Assertions.assertEquals(0.0, pearlBefore, EPS, "precondition: no effect resistance before the Ultimate");

        Character aim = battle.characters.get(1);
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

    /** Pearl at the given Defence and Eidolon rank, with every {@code BATTLE_START} rule settled. */
    private static Character pearlWith(double defence, int eidolon) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, eidolon);
        pearl.setAttribute(AttributeType.DEFENCE, new com.laosun.aluminium.models.DoubleValue(defence));
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst();
    }

    /** Her Elation (欢愉度) at the given Defence, i.e. after the 洞察万物 tier has run. */
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
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)), List.of(enemy),
                new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        if (deepStudy) {
            // The Eidolon's own gate, which is also the sentence's: the Ultimate puts her into 深度学习.
            // Note: The Ultimate is fired at an ALLY, not at herself, and that is load-bearing for this judge.
            // Aimed at herself she carries BOTH states the rule could name (【深度学习】 is hers and 【美学底本】
            // falls on the aim), so a mutant of the gate to the archetype state survived - the gate only becomes
            // observable when the aim is somebody else, which is also how the ability is actually played.
            Character aim = battle.characters.get(1);
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
