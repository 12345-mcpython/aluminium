package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): the Form A extra instance and the 洞察万物/感知容差 traces, the 充能 counter with the
 * 【深度学习】 state and the 感知容差 round allowance, the Technique and the Ultimate's advance/extra-turn half, and the
 * level convention with the ten stat traces.
 *
 * <p>Merged from the former {@code PearlKitTest}, {@code PearlResourceKitTest}, {@code PearlTurnKitTest} and
 * {@code PearlStatsTest}; every assertion and Javadoc of measurement is preserved.
 *
 * <p>L3 consolidation: cases that share one fixture, one clause and one observed object are now ONE scenario test, and
 * their assertions sit inside {@link Assertions#assertAll} with a message naming the clause. Nothing was dropped: the
 * expected values, the tolerances and the measurements are the ones the separate cases carried, and exception paths
 * were never merged with positive ones.
 */
public class PearlKitTest {
    private static final int PEARL = 1503;      // Elation path herself
    private static final int ELATION = 1502;    // Yao Guang, Elation
    private static final int ELATION_ALLY = 1502;
    private static final int ELATION_A = 1502;
    private static final int ELATION_B = 1505;
    private static final int ELATION_C = 1506;
    private static final int HUNTER = 1204;
    private static final int OTHER = 1204;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final String COUNTER = "elation:certified_banger";
    private static final String CHARGE = "char:1503/charge";
    private static final String DEEP = "char:1503/deep_learning";
    private static final String MARK = "char:1503/aesthetic_original";
    private static final String ARCHETYPE = "char:1503/aesthetic_original";
    private static final String PUNCHLINE = "elation:punchline";
    private static final double INSIGHT_ELATION = 0.32;
    private static final double PER_POINT_OVER = 0.0003;
    private static final double EPS = 1e-6;
    private static final double EPS_TIGHT = 1e-9;

    // ---------------------------------------------------------------------------------------------------------------
    // Form A's extra instance, the 洞察万物/感知容差 traces, E6's penetration, and the Skill's party heal.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The extra damage is real, and it is the ARCHETYPE's 欢愉度 that sizes it - not the archetype's Attack.
     *
     * <p>⭐ One scenario: both halves read the same object (the slot-8 enhanced basic's damage against the same enemy)
     * off the same fixture, {@code damageOnSlot8(ELATION_ALLY, …)}; the second half is the control the correction
     * needs, so it is one clause with a lever that must NOT move it.
     */
    @Test
    public void theExtraInstanceFollowsTheArchetypesElationAndNotItsAttack() {
        double withBoost = damageOnSlot8(ELATION_ALLY, 1.0, 2000);
        double without = damageOnSlot8(ELATION_ALLY, 0.0, 2000);
        System.out.println("[pearl_formA] slot-8 damage with the archetype's elation:elation_degree at +100% = " + withBoost
                + " ; at +0% = " + without);

        double strong = damageOnSlot8(ELATION_ALLY, 0.0, 3000);
        double weak = damageOnSlot8(ELATION_ALLY, 0.0, 1000);
        System.out.println("[pearl_formA] slot-8 damage with the archetype's Attack at 3000 = " + strong
                + " ; at 1000 = " + weak);

        Assertions.assertAll("「使用【char:1503/aesthetic_original】的属性值计算」",
                () -> Assertions.assertTrue(without > 0,
                        "[the instance] the slot-8 enhanced basic deals damage at all"),
                () -> Assertions.assertTrue(withBoost > without,
                        "[the elation:elation_degree] the extra instance reads the ARCHETYPE's elation:elation_degree (「使用【char:1503/aesthetic_original】的属性值计算」): "
                                + withBoost + " vs " + without),
                () -> Assertions.assertEquals(weak, strong, 1e-6,
                        "[the Attack] 欢愉伤害 has no attack-power term, so the archetype's Attack cannot move this"
                                + " instance"));
    }

    /**
     * The slot-9 form - the archetype is NOT on the Elation path - pays no extra damage.
     */
    @Test
    public void theNonElationArchetypePaysNoExtraDamage() {
        double boosted = damageOnSlot8(HUNTER, 1.0, 3000);
        double plain = damageOnSlot8(HUNTER, 0.0, 1000);
        System.out.println("[pearl_formA] slot-9 (non-Elation archetype): elation:elation_degree+100%/Attack 3000 = " + boosted
                + " ; +0%/Attack 1000 = " + plain);
        Assertions.assertEquals(plain, boosted, 1e-6,
                "「若【char:1503/aesthetic_original】为「elation」命途」: the other form's sentence has no extra damage, so neither the "
                        + "archetype's elation:elation_degree nor its Attack can move the number");
    }

    /**
     * 洞察万物: at or above 2400 DEF the boost is 32% plus 3% per 100 points over, counted up to 3600 - and "gains a
     * healing bonus equal to 20% of her own Elation".
     *
     * <p>⭐ One scenario: both halves read the same object (the Elation the 洞察万物 trace grants, and the healing it is
     * linked to) off the same fixture, {@code pearlWith(defence, 0)} at the DEF gate and just below it; the healing half
     * is that Elation's own consequence, not another clause.
     */
    @Test
    public void theInsightTracePaysItsTiersAndTheHealingLinkFollows() {
        double below = elationWith(2300);
        double at = elationWith(2400);
        double over = elationWith(2500);
        double capped = elationWith(4000);
        double farPast = elationWith(5000);
        System.out.println("[pearl_insight] Defence -> Elation: 2300 = " + below + " | 2400 = " + at
                + " | 2500 = " + over + " | 4000 = " + capped + " | 5000 = " + farPast);

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

        Assertions.assertAll("洞察万物",
                () -> Assertions.assertEquals(INSIGHT_ELATION, at - below, EPS,
                        "[the gate] at 2400 the clause adds its flat 32% and nothing for the excess"),
                () -> Assertions.assertEquals(PER_POINT_OVER * 100, over - at, EPS,
                        "[the steps] 100 points over the threshold is one step of 3%"),
                () -> Assertions.assertEquals(INSIGHT_ELATION + PER_POINT_OVER * 1200, capped - below, 1e-4,
                        "[the cap] the boost stops at the 3600 counted point (32% + 36% = 68% in total)"),
                () -> Assertions.assertEquals(capped, farPast, EPS,
                        "[past the cap] and past the cap it does not move any further"),
                () -> Assertions.assertTrue(elationGated > elationBelow,
                        "[the healing link] precondition: the gated run has more Elation (" + elationGated + " vs "
                                + elationBelow + ")"),
                () -> Assertions.assertEquals(0.2 * elationGated, healingGated, EPS,
                        "[the healing link] the bonus is 20% of the Elation she has when the clause fires"),
                () -> Assertions.assertEquals(0.2 * elationBelow, healingBelow, EPS,
                        "[the healing link] and the same on the run below the gate: 20% of THAT run's Elation"),
                () -> Assertions.assertEquals(0.2 * (elationGated - elationBelow), healingGated - healingBelow, EPS,
                        "[the healing link] the bonus tracks the Elation of the run it is in"));
    }

    /**
     * 感知容差's other half: "while holding 【好活当赏】", the whole party's effect resistance is up by 50%.
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
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), EPS,
                "precondition: she holds none of the counter yet, so the aura cannot be up");

        Character aim = battle.characters().get(1);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, aim, 0, 0));
        battle.processRequests();
        double pearlGain = pearl.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - pearlBefore;
        double allyGain = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - allyBefore;
        System.out.println("[pearl_insight] effect RES after the Ultimate: Pearl +" + pearlGain
                + " ; the ally +" + allyGain + " ; the counter reads " + pearl.getResources().value(COUNTER));

        Assertions.assertTrue(pearl.getResources().value(COUNTER) >= 1,
                "precondition: the Ultimate granted 【elation:certified_banger】, which is what the text's gate asks about");
        Assertions.assertEquals(0.5, pearlGain, EPS, "the party-wide clause includes Pearl herself");
        Assertions.assertEquals(0.5, allyGain, EPS, "and it is party-wide, not self-only");
    }

    /**
     * Eidolon 6: while deep-studying, the whole party's all-type resistance penetration is up by 20%.
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

    /**
     * Her Skill heals the party and the lowest-HP ally a SECOND time, and grants 15 of her counter.
     */
    @Test
    public void theSkillHealsThePartyAndTheLowestAllyAgain() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character ally = battle.characters().get(1);

        pearl.takeDamage(pearl.getMaxHp() * 0.6);
        ally.takeDamage(ally.getMaxHp() * 0.8);

        double pearlBefore = pearl.getCurrentHp();
        double allyBefore = ally.getCurrentHp();
        int counterBefore = (int) pearl.getResources().get(COUNTER).getValue();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, pearl, ally, 0, 0));

        double pearlGain = pearl.getCurrentHp() - pearlBefore;
        double allyGain = ally.getCurrentHp() - allyBefore;
        int counterAfter = (int) pearl.getResources().get(COUNTER).getValue();
        System.out.println("[pearl] pearl gained " + pearlGain + " ; the lowest ally gained " + allyGain
                + " ; counter " + counterBefore + " -> " + counterAfter);

        Assertions.assertTrue(pearlGain > 0, "the party heal reaches her too");
        Assertions.assertTrue(allyGain > pearlGain,
                "the ally standing lowest is healed a SECOND time (" + allyGain + " > " + pearlGain + ")");
        Assertions.assertEquals(counterBefore + 15, counterAfter, "and the Skill grants 15 of her counter");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The 充能 counter, the 【深度学习】 state, the 感知容差 allowance, and the trace's 5-per-ally-turn gain.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The Ultimate fills the counter to its declared three, and one enhanced basic spends exactly one charge - not all
     * three, and not none.
     *
     * <p>⭐ One scenario: both halves read the same object (the 【充能】 count and the 【深度学习】 state it pays for) off
     * the same fixture, {@code counterBattle(HUNTER)} plus her Ultimate, and the spending half simply continues the
     * state the filling half leaves behind.
     */
    @Test
    public void theUltimateFillsTheCounterAndEachBasicSpendsOne() {
        Battle battle = counterBattle(HUNTER);
        Character pearl = battle.characters().get(0);
        castUltimate(battle, pearl);
        int filled = pearl.getResources().value(CHARGE);
        boolean deep = pearl.getBuffManager().hasState(DEEP);
        System.out.println("[pearl_charge] after the Ultimate: charges=" + filled + " deep=" + deep);

        attack(battle, pearl);
        int afterOne = pearl.getResources().value(CHARGE);
        attack(battle, pearl);
        int afterTwo = pearl.getResources().value(CHARGE);
        System.out.println("[pearl_charge] charges after 1 / 2 enhanced basics: " + afterOne + " / " + afterTwo);

        Assertions.assertAll("「具有 3 点充能」",
                () -> Assertions.assertEquals(3, filled, "[the fill] 「具有 3 点充能」"),
                () -> Assertions.assertTrue(deep, "[the fill] and the state it pays for is on"),
                () -> Assertions.assertEquals(2, afterOne, "[the spend] one use, one charge"),
                () -> Assertions.assertEquals(1, afterTwo, "[the spend] and another use spends another"));
    }

    /**
     * While a charge is left the state stands, and when the last one is spent the state is gone - and re-entering the
     * state RESTORES the counter to three rather than adding three to what is left.
     *
     * <p>⭐ One scenario: both halves read the same objects (the 【充能】 count and the 【深度学习】 state) off the same
     * fixture, {@code counterBattle(HUNTER)} plus her Ultimate; the re-entry is the same clause's other end.
     */
    @Test
    public void theStateEndsWithTheLastChargeAndReEnteringRestoresThree() {
        Battle lifetime = counterBattle(HUNTER);
        Character pearl = lifetime.characters().get(0);
        castUltimate(lifetime, pearl);

        attack(lifetime, pearl);
        boolean afterOne = pearl.getBuffManager().hasState(DEEP);
        attack(lifetime, pearl);
        boolean afterTwo = pearl.getBuffManager().hasState(DEEP);
        attack(lifetime, pearl);
        int charges = pearl.getResources().value(CHARGE);
        boolean afterThree = pearl.getBuffManager().hasState(DEEP);
        System.out.println("[pearl_charge] char:1503/deep_learning after 1 / 2 / 3 enhanced basics: " + afterOne + " / " + afterTwo
                + " / " + afterThree + " (charges " + charges + ")");

        Battle reEntry = counterBattle(HUNTER);
        Character reEntryPearl = reEntry.characters().get(0);
        castUltimate(reEntry, reEntryPearl);
        attack(reEntry, reEntryPearl);
        attack(reEntry, reEntryPearl);
        int spentTwoLeft = reEntryPearl.getResources().value(CHARGE);
        boolean stillDeep = reEntryPearl.getBuffManager().hasState(DEEP);
        reEntryPearl.setCurrentEnergy(reEntryPearl.getMaxEnergy());
        castUltimate(reEntry, reEntryPearl);
        int afterReEntry = reEntryPearl.getResources().value(CHARGE);
        System.out.println("[pearl_charge] after spending two: " + spentTwoLeft + " (state up: " + stillDeep
                + ") ; after re-entering the state: " + afterReEntry);

        Assertions.assertAll("「行动结束后若充能不足则【char:1503/deep_learning】结束」",
                () -> Assertions.assertTrue(afterOne, "[the lifetime] two charges left, so it lasts"),
                () -> Assertions.assertTrue(afterTwo, "[the lifetime] one left, so it still lasts"),
                () -> Assertions.assertEquals(0, charges, "[the lifetime] and the third spends the last one"),
                () -> Assertions.assertFalse(afterThree,
                        "[the lifetime] 「行动结束后若充能不足则【char:1503/deep_learning】结束」"),
                () -> Assertions.assertEquals(1, spentTwoLeft, "[the re-entry] precondition: two charges were spent"),
                () -> Assertions.assertTrue(stillDeep,
                        "[the re-entry] precondition: the state is still up, so its counter is not empty"),
                () -> Assertions.assertEquals(3, afterReEntry,
                        "[the re-entry] re-entering restores the FULL three (1 + 3 = 4 would mean it merely added"
                                + " three)"));
    }

    /**
     * The Ultimate marks an ally as the archetype and Pearl as its deep learner.
     */
    @Test
    public void theUltimateMarksTheArchetypeAndTheDeepLearner() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character elation = battle.characters().get(1);
        Character hunter = battle.characters().get(2);

        int before = (int) pearl.getResources().get(COUNTER).getValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, elation, 0, 0));
        boolean archetype = elation.getBuffManager().hasState(ARCHETYPE);
        boolean deep = pearl.getBuffManager().hasState(DEEP);
        boolean hunterUntouched = hunter.getBuffManager().hasState(ARCHETYPE);
        int after = (int) pearl.getResources().get(COUNTER).getValue();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0));
        boolean hunterMarked = hunter.getBuffManager().hasState(ARCHETYPE);
        System.out.println("[pearl_states] archetype on the target = " + archetype + " ; deep study on Pearl = "
                + deep + " ; the other ally marked? " + hunterUntouched + " then " + hunterMarked
                + " ; counter " + before + " -> " + after);

        Assertions.assertTrue(archetype, "the designated ally becomes the archetype");
        Assertions.assertTrue(deep, "and Pearl carries deep study");
        Assertions.assertFalse(hunterUntouched, "a bystander is not marked");
        Assertions.assertTrue(hunterMarked, "but the next ultimate marks its own target");
        Assertions.assertEquals(before + 20, after, "and the ultimate grants 20 of her counter");
    }

    /**
     * Each ally turn pays 5, the round stops paying once it has handed out 50, and her own turn starts a new round.
     *
     * <p>⭐ One scenario: both halves read the same object (how much the round handed out, summed from the 5-point
     * grants Pearl drains after each turn) off the same fixture, {@code counterBattle()}. Her turn is what resets the
     * allowance, so the second half simply continues where the first one stopped and re-states the spent 50 as the
     * precondition it is.
     */
    @Test
    public void theRoundPaysFivePerTurnUpToFiftyAndHerOwnTurnStartsANewRound() {
        Battle battle = counterBattle();
        Character pearl = battle.characters().getFirst();
        drain(pearl);

        int total = 0;
        int turns = 0;
        while (turns < 20) {
            allyTurn(battle, battle.characters().get(2));
            total += pearl.getResources().value(COUNTER);
            drain(pearl);
            turns++;
        }
        int firstRound = total;
        System.out.println("[pearl_allowance] over " + turns + " ally turns the round handed out " + total);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, pearl, pearl, 0, 0));
        battle.processRequests();
        int afterHerTurn = pearl.getResources().value(COUNTER);
        drain(pearl);
        int second = 0;
        for (int i = 0; i < 20; i++) {
            allyTurn(battle, battle.characters().get(2));
            second += pearl.getResources().value(COUNTER);
            drain(pearl);
        }
        System.out.println("[pearl_allowance] on her turn she gained " + afterHerTurn
                + " ; the new round then handed out " + second);
        int secondTotal = second;

        Assertions.assertAll("「最多获得 50 点」",
                () -> Assertions.assertEquals(50, firstRound,
                        "[the allowance] 「最多获得 50 点」: the round stops at fifty, however many turns it is given"),
                () -> Assertions.assertEquals(50, firstRound, "[her own turn] precondition: the first round is spent"),
                () -> Assertions.assertEquals(5, afterHerTurn,
                        "[her own turn] her own turn pays its 5 from the round the reset just started"),
                () -> Assertions.assertEquals(45, secondTotal,
                        "[the new round] and the rest of the new round's 50 is still available"));
    }

    /**
     * Her trace gains 5 of the counter at the start of an ALLY's turn, capped at 50.
     */
    @Test
    public void anAllyTurnStartGrantsFiveCappedAtFifty() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character ally = battle.characters().get(1);

        int before = (int) pearl.getResources().get(COUNTER).getValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, ally, ally, 0, 0));
        int afterAlly = (int) pearl.getResources().get(COUNTER).getValue();

        for (int i = 0; i < 20; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, ally, ally, 0, 0));
        }
        int capped = (int) pearl.getResources().get(COUNTER).getValue();
        System.out.println("[pearl_trace] counter " + before + " -> " + afterAlly + " after one ally turn ; capped at " + capped);

        Assertions.assertEquals(before + 5, afterAlly, "an ally's turn start grants 5");
        Assertions.assertEquals(50, capped, "and the declared max of 50 holds it");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The Technique's archetype/deep-study half, the Ultimate's advance tiers, and the extra turn's 30/60 reward.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The technique marks the archetype and deep-studies THAT unit, and pays Pearl her 20.
     */
    @Test
    public void theTechniqueMarksTheArchetypeAndDeepStudiesIt() {
        Battle battle = techniqueBattle(true, true);
        Character pearl = battle.characters().get(0);
        Character archetype = battle.characters().get(1);
        Character other = battle.characters().get(2);
        System.out.println("[pearl_technique] the archetype holds " + MARK + "="
                + archetype.getBuffManager().hasState(MARK) + " " + DEEP + "="
                + archetype.getBuffManager().hasState(DEEP)
                + " ; another ally holds " + DEEP + "=" + other.getBuffManager().hasState(DEEP)
                + " ; Pearl holds " + DEEP + "=" + pearl.getBuffManager().hasState(DEEP)
                + " ; her counter " + pearl.getResources().value(COUNTER));

        Assertions.assertTrue(archetype.getBuffManager().hasState(MARK),
                "「使用秘技后获得【char:1503/aesthetic_original】」: the mark is on the unit the caller named");
        Assertions.assertTrue(archetype.getBuffManager().hasState(DEEP),
                "「并【char:1503/deep_learning】持有【char:1503/aesthetic_original】的角色」: char:1503/deep_learning lands on the HOLDER");
        Assertions.assertFalse(other.getBuffManager().hasState(DEEP),
                "and not on the rest of the party - the clause names one unit");
        Assertions.assertFalse(pearl.getBuffManager().hasState(DEEP),
                "「真珠只能【char:1503/deep_learning】除自身以外的角色」");
        Assertions.assertEquals(20, pearl.getResources().value(COUNTER), "and she gets her 20 points");
    }

    /**
     * Without the technique, the technique's own rule does nothing: no 深度学习 and no counter - while the MAP still
     * decides who holds the mark, so naming a holder is enough, and the engine applies it.
     *
     * <p>⭐ One scenario: both halves are the SAME fixture, {@code techniqueBattle(false, true)} - the mark named, the
     * 秘技 marker absent - and both read the same objects (the holder's 【美学底本】 / 【深度学习】 states and Pearl's
     * counter), so they are the two sides of the one 秘技 gate.
     */
    @Test
    public void theNamedHolderKeepsTheMarkButTheTechniqueGateStaysOff() {
        Battle battle = techniqueBattle(false, true);
        Character pearl = battle.characters().get(0);
        Character archetype = battle.characters().get(1);
        boolean marked = archetype.getBuffManager().hasState(MARK);
        boolean deep = archetype.getBuffManager().hasState(DEEP);
        int counter = pearl.getResources().value(COUNTER);
        System.out.println("[pearl_technique] with no technique: deep=" + deep + " counter=" + counter);
        System.out.println("[pearl_technique] a named holder with no technique state: mark=" + marked + " deep="
                + deep);

        Assertions.assertAll("the 秘技 gate",
                () -> Assertions.assertFalse(deep,
                        "[the gate] 「并【char:1503/deep_learning】持有【char:1503/aesthetic_original】的角色」 is gated on the 秘技 marker, which is not"
                                + " there"),
                () -> Assertions.assertEquals(0, counter, "[the counter] and no counter either"),
                () -> Assertions.assertTrue(marked,
                        "[the mark] the unit the caller named holds 【char:1503/aesthetic_original】 - that IS the map's answer"),
                () -> Assertions.assertFalse(deep,
                        "[the mark] while char:1503/deep_learning still needs the technique rule, whose gate is the 秘技 marker"));
    }

    /**
     * Nobody named means no archetype, and that is not an error.
     */
    @Test
    public void nobodyNamedMeansNoArchetype() {
        Battle battle = techniqueBattle(true, false);
        Character pearl = battle.characters().get(0);
        System.out.println("[pearl_technique] no archetype named: counter=" + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(20, pearl.getResources().value(COUNTER),
                "the counter half is about Pearl and happens anyway");
        for (Character ally : battle.characters()) {
            Assertions.assertFalse(ally.getBuffManager().hasState(MARK),
                    "and nobody was marked, because the caller named nobody");
        }
    }

    /**
     * Each tier moves the archetype by its own percentage, and the tiers compound because the thresholds nest.
     */
    @Test
    public void theThreeTiersCompoundOnTheSameTarget() {
        double one = advanceFraction(1);
        double two = advanceFraction(2);
        double three = advanceFraction(3);
        System.out.println("[pearl_advance] the target kept this fraction of its wait: 1 Elation ally -> " + one
                + " (0.90 expected) | 2 -> " + two + " (0.90x0.85 = 0.765) | 3 -> " + three
                + " (0.90x0.85x0.70 = 0.5355)");

        Assertions.assertEquals(0.90, one, 1e-6, "one Elation ally (Pearl herself) advances the archetype 10%");
        Assertions.assertEquals(0.90 * 0.85, two, 1e-6,
                "two Elation allies qualify for BOTH tiers, and the file runs both: 10% then 15%");
        Assertions.assertEquals(0.90 * 0.85 * 0.70, three, 1e-6,
                "three qualify for all three, so all three run: 10%, 15% then 30%");
    }

    /**
     * Four Elation allies hand the archetype an EXTRA TURN - and three do not.
     */
    @Test
    public void theExtraTurnArrivesAtFourElationAlliesAndNotAtThree() {
        Character atFour = extraTurnActorWith(4);
        Character atThree = extraTurnActorWith(3);
        System.out.println("[pearl_advance] the extra turn went to "
                + (atFour == null ? "nobody at four" : atFour.getCid()) + " at four Elation allies, and to "
                + (atThree == null ? "nobody" : atThree.getCid()) + " at three");

        Assertions.assertNotNull(atFour, "four Elation allies owe the archetype an extra turn");
        Assertions.assertEquals(HUNTER, atFour.getCid(),
                "and that extra turn belongs to 【char:1503/aesthetic_original】 - the ally the Ultimate aimed at, not to Pearl");
        Assertions.assertNull(atThree, "three Elation allies are one short of the extra turn");
    }

    /**
     * The Technique's opening counter: 20 with the marker, none without it.
     */
    @Test
    public void theTechniqueGrantsTheOpeningCounter() {
        double without = counterAfterOpening(false);
        double with = counterAfterOpening(true);
        System.out.println("[pearl_advance] the opening counter: without the technique " + without
                + " ; with it " + with);

        Assertions.assertEquals(0, without, EPS_TIGHT,
                "no technique was used, so the rule's own gate (the 秘技 marker) is not satisfied");
        Assertions.assertEquals(20, with, EPS_TIGHT,
                "「使用秘技后…下一次战斗开始时，真珠获得 20 点【elation:certified_banger】」");
    }

    /**
     * The extra turn begins: the count gains 30 and the team gains 60 punchline - only for 【美学底本】's own extra
     * turn, and the reward comes straight back off when that turn ends.
     *
     * <p>⭐ One scenario: all three halves read the same objects (【好活当赏】 and 笑点) off the same fixture,
     * {@code turnBattle()}, at the two ends of one extra turn; the unmarked unit's turn is the clause's own condition,
     * not a separate rule.
     */
    @Test
    public void theExtraTurnPaysThirtyAndSixtyOnlyToTheMarkedUnitAndTakesItBack() {
        Battle battle = turnBattle();
        Character pearl = battle.characters().getFirst();
        Character archetype = battle.characters().get(1);
        int partyBefore = battle.partyResource(PUNCHLINE).value();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        battle.processRequests();

        int gained = pearl.getResources().value(COUNTER);
        int partyGained = battle.partyResource(PUNCHLINE).value() - partyBefore;
        System.out.println("[pearl_extra_turn] the count gained " + gained + " ; the party gained " + partyGained
                + " punchline");

        int partyAfterGrant = battle.partyResource(PUNCHLINE).value();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0));
        battle.processRequests();

        int left = pearl.getResources().value(COUNTER);
        int partyLost = partyAfterGrant - battle.partyResource(PUNCHLINE).value();
        System.out.println("[pearl_extra_turn] after the turn: the count holds " + left + " ; the party lost "
                + partyLost + " punchline");

        Battle unmarked = turnBattle();
        Character unmarkedPearl = unmarked.characters().getFirst();
        Character hunter = unmarked.characters().get(2);
        unmarked.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, hunter, hunter, 0, 0));
        unmarked.processRequests();
        int unmarkedGain = unmarkedPearl.getResources().value(COUNTER);
        System.out.println("[pearl_extra_turn] the Hunt ally's extra turn paid " + unmarkedGain);

        Assertions.assertAll("【elation:certified_banger】获得 30 点",
                () -> Assertions.assertEquals(30, gained, "[the grant] 【elation:certified_banger】获得 30 点"),
                () -> Assertions.assertEquals(60, partyGained, "[the grant] and 60 points of punchline"),
                () -> Assertions.assertEquals(0, left, "[the removal] the granted 30 is removed"),
                () -> Assertions.assertEquals(60, partyLost, "[the removal] and so is the granted 60"),
                () -> Assertions.assertEquals(0, unmarkedGain,
                        "[the gate] only 【char:1503/aesthetic_original】's extra turn pays: the clause gates on the mark"));
    }

    /**
     * The turn ends with part of the reward already spent, and with the counter already empty: what is left is taken
     * and no more, and nothing errors.
     *
     * <p>⭐ One scenario: both halves read the same object (what the closing clause leaves on 【好活当赏】) off the same
     * fixture, {@code turnBattle()} at the end of an extra turn; only how much of the granted 30 is still there
     * differs, and the clamp is what decides in both.
     */
    @Test
    public void theRemovalTakesBackOnlyWhatIsLeft() {
        Battle partly = turnBattle();
        Character partlyPearl = partly.characters().getFirst();
        partly.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, partlyPearl, partlyPearl, 0, 0));
        partly.processRequests();
        partlyPearl.getResources().spend(COUNTER, 12);                // 30 granted, 12 already used

        partly.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, partlyPearl, partlyPearl, 0, 0));
        partly.processRequests();

        int partlyLeft = partlyPearl.getResources().value(COUNTER);
        System.out.println("[pearl_extra_turn] 30 granted, 12 spent, " + partlyLeft + " left after the removal");

        Battle emptied = turnBattle();
        Character emptiedPearl = emptied.characters().getFirst();
        Character archetype = emptied.characters().get(1);
        emptied.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        emptied.processRequests();
        emptiedPearl.getResources().spend(COUNTER, 30);                // all of it gone

        emptied.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0));
        emptied.processRequests();

        int emptiedLeft = emptiedPearl.getResources().value(COUNTER);
        System.out.println("[pearl_extra_turn] removal against an empty count left " + emptiedLeft);

        Assertions.assertAll("the removal clamps to what is there",
                () -> Assertions.assertEquals(0, partlyLeft,
                        "[partly spent] the removal clamps to what is there (18) rather than failing or leaving a"
                                + " residue"),
                () -> Assertions.assertEquals(0, emptiedLeft,
                        "[already empty] an empty count stays empty rather than the removal failing"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The level convention, the two level-granting Eidolons, and the ten stat traces.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The level convention and the two level-granting Eidolons.
     *
     * <p>Reads {@code CanHit.skillLevel(Skill)} -- the effective level -- not {@code Skill.getLevel()}, which is the
     * base level and excludes every raise by design.
     */
    @Test
    public void theConventionAndTheTwoEidolonsMoveTheEffectiveLevel() {
        int[] none = levels(0);
        int[] three = levels(3);
        int[] five = levels(5);
        System.out.println("[pearl_levels] E0 ULTRA=" + none[0] + " COMMON=" + none[1]
                + " | E3 ULTRA=" + three[0] + " COMMON=" + three[1]
                + " | E5 SKILL=" + five[2] + " TALENT=" + five[3]);

        Assertions.assertEquals(10, none[0], "the convention alone brings the Ultimate to 10");
        Assertions.assertEquals(6, none[1], "the basic: row 6, the level the documents quote as 100%");
        Assertions.assertEquals(12, three[0], "Eidolon 3 adds 2 to the Ultimate");
        Assertions.assertEquals(7, three[1], "and 1 to the basic, which starts at row 6");
        Assertions.assertEquals(12, five[2], "Eidolon 5 adds 2 to the Skill");
        Assertions.assertEquals(12, five[3], "and 2 to the talent");
    }

    /**
     * The three trivially attributable rows are asserted exactly; Defence is asserted as the ratio the data states.
     */
    @Test
    public void theTenDataRowsReachThePanel() {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();

        double effectRes = her.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double elation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double defence = her.getAttribute(AttributeType.DEFENCE).get();
        double speedWithTraces = speedOf();
        double defenceNoRules = defenceOfPearlWithNoRules();
        double elationNoRules = elationOfPearlWithNoRules();
        System.out.println("[pearl_stat_traces] effectRES=" + effectRes + " elation=" + elation
                + " (with her table emptied: " + elationNoRules + ")"
                + " defence=" + defence + " (emptied: " + defenceNoRules + ") speed=" + speedWithTraces);

        Assertions.assertEquals(0.10, effectRes, EPS,
                "effect RES = 4% + 6% from her trace rows (1503203 / 1503207)");
        Assertions.assertEquals(0.10, elation, 1e-4,
                "Elation = 4% + 6% from her trace rows (1503204 / 1503208), the DEF gate not being met yet");
        Assertions.assertEquals(0.225,
                defence / her.getAttribute(AttributeType.DEFENCE).baseValue() - 1, 1e-6,
                "Defence = 5% + 7.5% + 10% on her own base (1503201 / 1503205 / 1503210)");
    }

    /**
     * The Speed rows are an ABSOLUTE delta of nine points, and that is worth pinning exactly.
     *
     * <p>{@code SpeedDelta} carries {@code 2/3/4} (not 2%/3%/4%), so it has to be an {@code amount} rather than a
     * {@code percent}: written as a percentage those three points would become a share of her base speed - a number
     * an order of magnitude larger, and one that still looks plausible on a panel.
     */
    @Test
    public void theSpeedRowsAreNineFlatPoints() {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();
        double speed = her.getAttribute(AttributeType.SPEED).get();
        double base = her.getAttribute(AttributeType.SPEED).baseValue();
        System.out.println("[pearl_stat_traces] Speed = " + speed + " ; base = " + base
                + " ; flat delta = " + (speed - base));

        Assertions.assertTrue(base > 0, "precondition: a base speed to measure against (" + base + ")");
        Assertions.assertEquals(9.0, speed - base, 1e-6,
                "the +9 flat Speed from her three trace rows - a flat delta, not a share of her base ("
                        + base + ")");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The damage the enhanced basic deals, for an archetype aim with the given 欢愉度 boost and Attack.
     */
    private static double damageOnSlot8(int archetypeCid, double archetypeElationBoost, double archetypeAttack) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(archetypeCid, 80);
        archetype.setAttribute(AttributeType.ATTACK, new DoubleValue(archetypeAttack));
        archetype.setAttribute(AttributeType.ELATION_DAMAGE_BOOST, new DoubleValue(archetypeElationBoost));
        List<Character> team = new ArrayList<>(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(team, List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, her, archetype, 0, 0));
        battle.processRequests();

        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }

    /**
     * Pearl at the given Defence and Eidolon rank, with every {@code BATTLE_START} rule settled.
     */
    private static Character pearlWith(double defence, int eidolon) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, eidolon);
        pearl.setAttribute(AttributeType.DEFENCE, new DoubleValue(defence));
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
            Character aim = battle.characters().get(1);
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, her, aim, 0, 0));
            battle.processRequests();
            if (keepHerKit) {
                Assertions.assertTrue(her.getBuffManager().hasState(DEEP), "precondition: she is deep-studying");
                Assertions.assertFalse(her.getBuffManager().hasState("char:1503/aesthetic_original"),
                        "precondition: the archetype mark went to the AIM, not to her - otherwise this judge cannot"
                                + " tell the two gates apart");
            }
        }
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(her, victim, DamageElement.ICE, DamageType.NORMAL, 1000));
        return before - victim.getCurrentHp();
    }

    /**
     * Casts the Ultimate at the given ally, which is what installs the enhanced basic and fills the counter.
     */
    private static void castUltimate(Battle battle, Character pearl) {
        pearl.setCurrentEnergy(pearl.getMaxEnergy());
        battle.castUltra(pearl, List.of(battle.characters().get(1)));
        battle.processRequests();
    }

    /**
     * Drives one enhanced basic through the engine's own skill request path, so the cast events fire.
     */
    private static void attack(Battle battle, Character pearl) {
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(pearl.getSkills().get(SkillType.COMMON), pearl, List.of(victim));
        battle.processRequests();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, pearl, victim, 1, 0));
        battle.processRequests();
    }

    private static Battle counterBattle(int archetype) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(archetype, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static Battle counterBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION_ALLY, 80),
                        CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static void allyTurn(Battle battle, Character actor) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, actor, actor, 0, 0));
        battle.processRequests();
    }

    private static void drain(Character pearl) {
        pearl.getResources().spend(COUNTER, pearl.getResources().value(COUNTER));
    }

    private static Battle techniqueBattle(boolean techniqueUsed, boolean archetypeNamed) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(ELATION_ALLY, 80);
        Battle battle = new Battle(List.of(pearl, archetype, CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (techniqueUsed) {
            battle.markTechniqueUsed(pearl);
        }
        if (archetypeNamed) {
            battle.markArchetypeHolder(archetype);
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static Battle turnBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION_ALLY, 80),
                        CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // The archetype mark is normally applied by her ultimate; this case is about the reward, so it is set directly.
        battle.characters().get(1).getBuffManager()
                .addBuff(new com.laosun.aluminium.buff.StateBuff(MARK, 3, false));
        return battle;
    }

    /**
     * The fraction of its wait the archetype KEEPS after Pearl's Ultimate, for a team with that many Elation-path
     * characters (Pearl counts). Measured against the same team with Pearl's rules removed.
     */
    private static double advanceFraction(int elationAllies) {
        double untouched = timeRemaining(team(elationAllies), false);
        double advanced = timeRemaining(team(elationAllies), true);
        Assertions.assertTrue(untouched > 0, "precondition: the archetype had a wait to shorten");
        return advanced / untouched;
    }

    /**
     * Who was handed the extra turn, or {@code null} when the Ultimate granted none.
     */
    private static Character extraTurnActorWith(int elationAllies) {
        Battle battle = new Battle(team(elationAllies), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character archetype = battle.characters().get(battle.characters().size() - 1);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, battle.characters().get(0), archetype, 0, 0));
        battle.processRequests();
        CanHit actor = battle.getExtraTurnActor();
        return actor instanceof Character character ? character : null;
    }

    /**
     * The counter at the opening of a battle, with or without the Technique marker.
     */
    private static double counterAfterOpening(boolean techniqueUsed) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (techniqueUsed) {
            battle.markTechniqueUsed(pearl);
        }
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst().getResources().value(COUNTER);
    }

    /**
     * A team of exactly {@code elationAllies} Elation-path characters plus Pearl (who is one herself), with the Hunt
     * ally LAST: the Ultimate aims at the last member, so the archetype is a character who is NOT on the Elation path,
     * which keeps "who was aimed at" and "who qualifies" independent.
     */
    private static List<Character> team(int elationAllies) {
        List<Integer> elationIds = List.of(PEARL, ELATION_A, ELATION_B, ELATION_C);
        List<Character> team = new ArrayList<>();
        for (int i = 0; i < elationAllies; i++) {
            team.add(CharacterFactory.create(elationIds.get(i), 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        return team;
    }

    /**
     * How much action value the aiming target still has, fired through Pearl's own Ultimate or with no rules.
     */
    private static double timeRemaining(List<Character> team, boolean fireTheUltimate) {
        List<Character> fresh = new ArrayList<>();
        for (Character c : team) {
            fresh.add(CharacterFactory.create(c.getCid(), 80));
        }
        if (!fireTheUltimate) {
            fresh.getFirst().setTriggerTable(TriggerTable.EMPTY);
        }
        Battle battle = new Battle(fresh, List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character archetype = battle.characters().get(battle.characters().size() - 1);
        if (fireTheUltimate) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, battle.characters().get(0), archetype, 0, 0));
            battle.processRequests();
        }
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == archetype) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    /**
     * Effective levels for a Pearl at the given Eidolon rank: ULTRA, COMMON, SKILL, TALENT.
     */
    private static int[] levels(int rank) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, LEVEL, true, null, null, rank),
                CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        return new int[]{
                pearl.skillLevel(pearl.getSkills().get(SkillType.ULTRA)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.COMMON)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.SKILL)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.TALENT)),
        };
    }

    private static double speedOf() {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst().getAttribute(AttributeType.SPEED).get();
    }

    private static double elationOfPearlWithNoRules() {
        return attributeWithNoRules(AttributeType.ELATION_DAMAGE_BOOST);
    }

    private static double defenceOfPearlWithNoRules() {
        return attributeWithNoRules(AttributeType.DEFENCE);
    }

    private static double attributeWithNoRules(AttributeType attribute) {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        pearl.setTriggerTable(TriggerTable.EMPTY);
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst().getAttribute(attribute).get();
    }
}
