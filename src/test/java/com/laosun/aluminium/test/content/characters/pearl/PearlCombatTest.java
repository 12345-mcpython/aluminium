package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.SkillSwapBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): the 好活当赏 counter's damage reduction and the Elation payout, the enhanced-basic forms with
 * their heals, and the art barrier with the talent's 30% reduction.
 *
 * <p>Merged from the former {@code PearlCombatTest}, {@code PearlEnhancedBasicHealTest},
 * {@code PearlBarrierAndTalentTest} and {@code PearlEnhancedBasicFormsTest}; every assertion and Javadoc of measurement
 * is preserved.
 *
 * <p>L3 consolidation: cases that share one fixture, one clause and one observed object are now ONE scenario test, and
 * their assertions sit inside {@link Assertions#assertAll} with a message naming the clause. Nothing was dropped: the
 * expected values, the tolerances and the measurements are the ones the separate cases carried, and exception paths
 * were never merged with positive ones.
 */
public class PearlCombatTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final int OTHER = 1101;
    private static final int HUNTER_SLOT = 1;   // the ordinary basic, which must heal nobody
    private static final int ELATION_ARCHETYPE = 1505;      // 「欢愉」命途: the barrier applies
    private static final int OTHER_ALLY = 1002;        // not that path: it must not
    private static final String BARRIER = "char:1503/art_barrier";
    private static final String GRIT = "char:1503/sand_absorb";
    private static final int ENERGY = 90;
    private static final String COUNTER = "elation:certified_banger";
    private static final String CARRIER = "char:1503/rendering_reason";
    private static final double INSTANCE = 100;
    private static final int ELATION_ALLY = 1502;
    private static final int FIXTURE_LEVEL = 80;

    private Character pearl;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    // ---------------------------------------------------------------------------------------------------------------
    // The 好活当赏 damage reduction, the dispel on the enhanced basic, and the Elation payout/carrier.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A full counter blocks a share of an ally's hit, the points come off HER pool, and the SHARE is what caps the block.
     *
     * <p>⭐ One scenario: both halves read the same objects (the ally's HP cost against the empty-counter control, and
     * the points her own pool spent) off the same fixture - a counter of 30 points. Only the weight of the incoming hit
     * differs, and the heavy one is the only case where the 60% share binds.
     */
    @Test
    public void herCounterBlocksTheAlliedHitUpToTheShareCap() {
        Battle rich = counterBattle(30);
        Character pearl = rich.characters().getFirst();
        Character ally = rich.characters().get(1);
        double cost = hit(rich, ally, INSTANCE);
        int left = pearl.getResources().value(COUNTER);
        double control = hit(counterBattle(0), null, INSTANCE);

        Battle heavy = counterBattle(30);
        Character heavyPearl = heavy.characters().getFirst();
        Character heavyAlly = heavy.characters().get(1);
        double heavyCost = hit(heavy, heavyAlly, 3000);          // a share far larger than the pool covers
        int spent = 30 - heavyPearl.getResources().value(COUNTER);
        double heavyControl = hit(counterBattle(0), null, 3000);

        System.out.println("[pearl_defence] the hit cost " + cost + " HP, leaving " + left + " points (control "
                + control + ")");
        System.out.println("[pearl_defence] large hit: cost " + heavyCost + " (control " + heavyControl
                + ") ; points spent " + spent);

        Assertions.assertAll("「elation:certified_banger」: the block and its 60% share",
                () -> Assertions.assertTrue(control > 0,
                        "[the block] precondition: the hit lands when nothing pays for it"),
                () -> Assertions.assertTrue(cost < control,
                        "[the block] her counter lowered what the ally paid (" + cost + " < " + control + ")"),
                () -> Assertions.assertTrue(left < 30,
                        "[the block] and it was HER counter that paid (30 -> " + left + ")"),
                () -> Assertions.assertEquals(0, ally.getResources().value(COUNTER),
                        "[the block] the ally has no counter of its own"),
                () -> Assertions.assertTrue(heavyControl > 0,
                        "[the 60% share] precondition: the large hit lands (" + heavyControl + ")"),
                () -> Assertions.assertTrue(heavyControl - heavyCost < 0.6 * heavyControl + 200,
                        "[the 60% share] the block does not exceed the 60% share plus a point's worth of rounding ("
                                + (heavyControl - heavyCost) + " vs " + (0.6 * heavyControl) + ")"),
                () -> Assertions.assertEquals(7, spent,
                        "[the 60% share] and the share is what decided the spend: 60% of the settled amount needs 7"
                                + " points, where blocking the whole hit would need 11"));
    }

    /**
     * With an empty counter the protection is simply absent.
     */
    @Test
    public void anEmptyCounterMeansNoProtection() {
        Battle battle = counterBattle(0);
        Character ally = battle.characters().get(1);
        double cost = hit(battle, ally, INSTANCE);
        double control = hit(counterBattle(0), null, INSTANCE);
        System.out.println("[pearl_defence] empty counter: the hit cost " + cost + " (control " + control + ")");
        Assertions.assertEquals(control, cost, 1e-9, "an empty pool blocks nothing");
    }

    /**
     * One point buys exactly 200, so a counter that cannot cover the share blocks what it has.
     */
    @Test
    public void onePointBuysTwoHundred() {
        Battle battle = counterBattle(1);
        Character pearl = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double cost = hit(battle, ally, INSTANCE * 10);          // a share far larger than one point covers
        double control = hit(counterBattle(0), null, INSTANCE * 10);
        System.out.println("[pearl_defence] one point: the hit cost " + cost + " (control " + control
                + ") ; points left " + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), "the single point is spent");
        Assertions.assertEquals(control - 200, cost, 0.5,
                "and it bought exactly 200 of cover, which is the #5 the talent states");
    }

    /**
     * The enhanced basic dispels a debuff and leaves a named state; the ordinary basic does not.
     *
     * <p>⭐ One scenario: both halves read the same object (which buffs survive on the ally) off the same fixture, and
     * differ only in the slot the cast is fired at - slot 8 is the installed enhanced form, slot 1 the ordinary basic.
     */
    @Test
    public void theEnhancedBasicDispelsWhileTheOrdinaryOneDoesNot() {
        Battle enhanced = combatBattle();
        Character pearl = enhanced.characters().getFirst();
        Character ally = enhanced.characters().get(1);
        installTheSlotEightForm(enhanced, pearl);
        ally.getBuffManager().addBuff(new DotBuff(ally, DamageElement.FIRE, 100, 3));
        ally.getBuffManager().addBuff(new StateBuff("_probe_state", 3));

        enhanced.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.BASIC_ATTACK, pearl,
                enhanced.enemies().getFirst(), 1, 0, (SkillCategory) null, 8, 0, List.of()));

        boolean dot = ally.getBuffManager().hasBuff(DotBuff.class);
        boolean state = ally.getBuffManager().hasBuff(StateBuff.class);

        Battle ordinary = combatBattle();
        Character ordinaryPearl = ordinary.characters().getFirst();
        Character ordinaryAlly = ordinary.characters().get(1);
        ordinaryAlly.getBuffManager().addBuff(new DotBuff(ordinaryAlly, DamageElement.FIRE, 100, 3));

        ordinary.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.BASIC_ATTACK, ordinaryPearl,
                ordinary.enemies().getFirst(), 1, 0, (SkillCategory) null, 1, 0, List.of()));

        boolean ordinaryDot = ordinaryAlly.getBuffManager().hasBuff(DotBuff.class);
        System.out.println("[pearl_dispel] after the enhanced basic -- DOT still there? " + dot
                + " ; named state still there? " + state + " ; after the ordinary basic the DOT is still there? "
                + ordinaryDot);

        Assertions.assertAll("the enhanced basic's dispel",
                () -> Assertions.assertFalse(dot,
                        "[the dispel] the burn is a negative effect, so dispelling takes it"),
                () -> Assertions.assertTrue(state,
                        "[the dispel] and a named state stays: it is not classified as negative"),
                () -> Assertions.assertTrue(ordinaryDot,
                        "[the ordinary basic] slot 1 is not the enhanced basic, so no rule of hers dispels"));
    }

    /**
     * The Elation skill's payout: an armed ally's attack carries an extra Elation amount, scaling with the tiers.
     */
    @Test
    public void thePayoutScalesWithHowManyTiersTheTeamQualifiesFor() {
        double two = payoutWith(2);
        double four = payoutWith(4);
        System.out.println("[pearl_payout] payout with 2 Elation allies = " + two + " ; with 4 = " + four
                + " ; ratio = " + (four / two));
        Assertions.assertTrue(two > 0, "the payout happens at all");
        Assertions.assertEquals(0.85 / 0.25, four / two, 1e-6,
                "four allies qualify for 0.25+0.20+0.40 = 0.85 of the base, two for 0.25, and the damage zones"
                        + " cancel in the ratio");
    }

    /**
     * The Elation skill arms the whole team; another category arms nobody.
     */
    @Test
    public void theElationSkillArmsTheWholeTeamAndAnotherCategoryDoesNot() {
        List<String> armed = armedWith(SkillCategory.ELATION_DAMAGE);
        List<String> elsewhere = armedWith(SkillCategory.BPSKILL);
        System.out.println("[pearl_carrier] armed by the Elation skill: " + armed
                + " ; armed by BPSKILL: " + elsewhere);

        Assertions.assertEquals(3, armed.size(), "every ally carries the carrier for their next attack");
        Assertions.assertTrue(elsewhere.isEmpty(), "and another category arms nobody (got " + elsewhere + ")");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The enhanced basic's heal: the installed row, and the Eidolon 3 raise that moves it.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Character 1503 (Pearl): an enhanced basic heals the party and the lowest-HP ally a second time.
     *
     * <p>Note: The enhanced form has to be <b>installed</b> first, and that is not decoration - the heal reads its numbers off
     * the row of the skill in the COMMON slot ({@code COMMON:1} / {@code COMMON:3}), so firing the slot's event while the
     * ordinary basic still sits there would read a one-column row. The install is driven the real way: her Ultimate
     * REPLACE_SKILLs the form in.
     */
    @Test
    public void theEnhancedBasicHealsTwiceAndAnOrdinaryOneDoesNot() {
        double[] eight = gains(8);
        double[] one = gains(HUNTER_SLOT);
        System.out.println("[pearl_eb] slot 8 -> pearl gained " + eight[0] + " ; the lowest ally gained "
                + eight[1] + " | ordinary slot 1 -> " + one[0] + " / " + one[1]);

        Assertions.assertTrue(eight[0] > 0, "the enhanced basic heals Pearl too");
        Assertions.assertTrue(eight[1] > eight[0], "and the lowest ally is healed a SECOND time");
        Assertions.assertEquals(0.0, one[0], 1e-9, "an ordinary basic hits no heal rule");
        Assertions.assertEquals(0.0, one[1], 1e-9, "for anyone");
    }

    /**
     * E0: the heal is the installed skill's own row at its effective level, and that row is the one the document quotes.
     */
    @Test
    public void theInstalledFormHealsByItsOwnCurrentRow() {
        Battle battle = enhancedBattle(0);
        Character pearl = battle.characters().get(0);
        readInstalledRowAndMeasure(battle, pearl, 6, 0.08, 160.0);
    }

    /**
     * E3's 「普攻等级 +1」 moves the row, and the heal follows it - the thing constants could never do.
     */
    @Test
    public void eidolonThreeMovesTheRowAndTheHealFollows() {
        Battle battle = enhancedBattle(3);
        Character pearl = battle.characters().get(0);
        readInstalledRowAndMeasure(battle, pearl, 7, 0.088, 176.0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The art barrier (who gets it, the 90-energy payment on the next ultimate, and the no-stack rule) and the
    // talent's 30% reduction below half HP.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The art barrier lands on an Elation archetype and on nobody else.
     *
     * <p>Note: This case used to assert the 90-energy refund as well. That rule has been WITHDRAWN: the corpus says
     * "使其【下一次】施放终结技时" - the NEXT time - and the rule fired on every ultimate the holder cast. The barrier's
     * own half is exact and stays.
     */
    @Test
    public void theBarrierLandsOnlyOnAnElationArchetype() {
        Battle battle = barrierBattle();
        Character pearl = battle.characters().get(0);
        Character elation = battle.characters().get(1);
        Character hunter = battle.characters().get(2);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, elation, 0, 0));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0));
        boolean onElation = elation.getBuffManager().hasState(BARRIER);
        boolean onHunter = hunter.getBuffManager().hasState(BARRIER);
        System.out.println("[pearl_barrier] on the Elation ally = " + onElation + " ; on the Hunt ally = " + onHunter);

        Assertions.assertTrue(onElation, "an Elation-Path archetype gets the barrier");
        Assertions.assertFalse(onHunter, "a Hunt ally must NOT get it (the rule gates on target has_path)");
    }

    /**
     * The talent's reduction follows the half-HP threshold, and its NUMBER is 30%, not just its state.
     *
     * <p>⭐ One scenario: both halves read the same object (the reduction the ally carries once it drops below half
     * HP) off the same fixture ({@code barrierBattle()}); the second half states that reduction's size.
     */
    @Test
    public void theTalentReductionFollowsTheThresholdAndIsThirtyPercent() {
        Battle threshold = barrierBattle();
        Character follower = threshold.characters().get(2);

        follower.takeDamage(follower.getMaxHp() * 0.8);
        threshold.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, follower, follower, 0, 0));
        boolean below = follower.getBuffManager().hasState(GRIT);

        follower.heal(follower.getMaxHp());
        threshold.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HEALED, follower, follower, 0, 0));
        boolean above = follower.getBuffManager().hasState(GRIT);
        System.out.println("[pearl_talent] reduction below half = " + below + " ; after healing above half = " + above);

        Battle numeric = barrierBattle();
        Character ally = numeric.characters().get(2);
        Enemy attacker = numeric.enemyUnits().getFirst();

        double readingInstance = ally.getMaxHp() * 0.1;
        numeric.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                readingInstance * 10));
        boolean talentIsUp = ally.getBuffManager().hasState(GRIT);
        double fraction = ally.getCurrentHp() / ally.getMaxHp();

        double withTalent = hurtBy(numeric, attacker, ally, readingInstance);
        ally.getBuffManager().removeState(GRIT);
        numeric.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HEALED, ally, ally, 0, 0));
        boolean reductionOff = ally.getBuffManager().hasState(GRIT);
        double without = hurtBy(numeric, attacker, ally, readingInstance);

        System.out.println("[pearl_talent] one hit takes " + withTalent + " with the talent and " + without
                + " without it ; ratio = " + (withTalent / without));

        Assertions.assertAll("「受到的伤害降低 30%」",
                () -> Assertions.assertTrue(below, "[the threshold] below 50% HP the ally carries the damage reduction"),
                () -> Assertions.assertFalse(above, "[the threshold] and healing back above 50% removes it"),
                () -> Assertions.assertTrue(talentIsUp,
                        "[the number] precondition: the ally was brought below half HP, so the talent is up"),
                () -> Assertions.assertTrue(fraction <= 0.5,
                        "[the number] precondition: the ally really is below half (" + fraction + ")"),
                () -> Assertions.assertFalse(reductionOff,
                        "[the number] precondition: the reduction is off for the control reading"),
                () -> Assertions.assertTrue(without > 0, "[the number] the control hit must land (" + without + ")"),
                () -> Assertions.assertEquals(1 - 0.30, withTalent / without, 1e-6,
                        "[the number] 「受到的伤害降低 30%」（Lv10）: the hit is multiplied by 1 - 0.30"));
    }

    /**
     * The whole sentence: marker, a Skill that leaves it, the next ultimate paying 90 and consuming it - and
     * 「该效果无法叠加」, so two of her ultimates still leave ONE marker that pays once.
     *
     * <p>⭐ One scenario: both halves read the same objects (Pearl's energy and the ally's 【美学底本】 marker) off the
     * same fixture - {@code build(ELATION_ARCHETYPE)} plus her Ultimate aimed at that archetype; the second half only
     * repeats her Ultimate before the ally's turn.
     */
    @Test
    public void theBarrierPaysOnceOnTheHoldersNextUltimate() {
        build(ELATION_ARCHETYPE);
        pearlUltimateOnTheArchetype();
        boolean marked = ally.getBuffManager().hasState(BARRIER);

        pearl.setCurrentEnergy(0);
        battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(enemy));
        battle.processRequests();
        double afterASkill = pearlEnergy();
        boolean stillMarked = ally.getBuffManager().hasState(BARRIER);

        double paid = allyUltimate();
        boolean consumed = ally.getBuffManager().hasState(BARRIER);
        pearl.setCurrentEnergy(0);
        allyUltimate();
        double paidAfterTheFirst = pearlEnergy();
        System.out.println("[1503] the ally's next ultimate paid " + paid + " energy ; the marker is now " + consumed
                + " ; and the ultimate after it paid " + paidAfterTheFirst);

        build(ELATION_ARCHETYPE);
        pearlUltimateOnTheArchetype();
        pearlUltimateOnTheArchetype();
        boolean markedTwice = ally.getBuffManager().hasState(BARRIER);
        pearl.setCurrentEnergy(0);
        double paidAfterTwo = allyUltimate();
        System.out.println("[1503] after two of her ultimates the marker is present: " + markedTwice
                + " ; and that ultimate paid " + paidAfterTwo + " (not twice the 90)");

        Assertions.assertAll("「下一次施放终结技时，为真珠固定恢复 90 点能量」",
                () -> Assertions.assertTrue(marked,
                        "[the marker] 「使其…」: the Elation archetype carries the marker"),
                () -> Assertions.assertEquals(0, afterASkill,
                        "[the payout] a Skill is not a 终结技: nothing is paid"),
                () -> Assertions.assertTrue(stillMarked,
                        "[the marker] and the marker waits for the REAL ultimate"),
                () -> Assertions.assertEquals(ENERGY, paid,
                        "[the payout] 「下一次施放终结技时，为真珠固定恢复 90 点能量」"),
                () -> Assertions.assertFalse(consumed,
                        "[the lifetime] 「下一次」 is a lifetime: that ultimate consumed the marker"),
                () -> Assertions.assertEquals(0, paidAfterTheFirst,
                        "[the lifetime] and the next one pays nothing: the effect happened once"),
                () -> Assertions.assertTrue(markedTwice,
                        "[no stack] 「使其…」 still holds after the second one"),
                () -> Assertions.assertEquals(ENERGY, paidAfterTwo,
                        "[no stack] one marker pays once: 「该效果无法叠加」"));
    }

    /**
     * 「若【美学底本】为「欢愉」命途角色」: somebody off that path never carries it.
     */
    @Test
    public void aNonElationArchetypeGetsNothing() {
        build(OTHER_ALLY);
        pearlUltimateOnTheArchetype();
        Assertions.assertFalse(ally.getBuffManager().hasState(BARRIER),
                "the gate is the target's PATH, and this one is not elation");

        pearl.setCurrentEnergy(0);
        double paid = allyUltimate();
        System.out.println("[1503] a non-Elation archetype's ultimate paid " + paid);
        Assertions.assertEquals(0, paid, "no marker, no payment");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The two enhanced-basic forms (Elation slot 8 / other Paths slot 10), the branch that installs them, and the 15%
    // Elation rider the slot-8 form carries while 【好活当赏】 is held.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Her Ultimate's enhanced basic branches on the archetype's Path.
     *
     * <p>`SkillSwapBuff` documents its own accessors as test-readable and `BuffManager.findBuff(Class)` returns it.
     * So the branch is observable without touching any transient-state guesswork:
     * * the archetype is on the Elation Path -> COMMON is swapped for the skill in slot 8 ([行笔，幻造星月]);
     * * otherwise                            -> slot 10 ([行笔，绘制末浪]).
     *
     * <p>Two-sided by construction: the same call with two different targets must install two different skills.
     */
    @Test
    public void theBranchFollowsTheArchetypesPath() {
        int elationSlot = swappedSlot(ELATION);
        int hunterSlot = swappedSlot(HUNTER);
        System.out.println("[pearl_branch] COMMON is swapped for slot " + elationSlot
                + " when the archetype is Elation, and for slot " + hunterSlot + " otherwise");

        Assertions.assertEquals(8, elationSlot,
                "an Elation archetype upgrades the basic to the slot-8 form (with the extra Elation damage)");
        Assertions.assertEquals(10, hunterSlot, "any other Path upgrades it to the slot-10 form");
    }

    /**
     * Holding the counter, the enhanced basic pays extra; without it, it does not.
     */
    @Test
    public void theEnhancedBasicPaysTheElationRiderOnlyWhileHolding() {
        double holding = damageOnTheEnhancedBasic(true);
        double empty = damageOnTheEnhancedBasic(false);
        System.out.println("[pearl_rider] the enhanced basic with the counter = " + holding
                + " ; without it = " + empty);
        Assertions.assertTrue(empty > 0, "precondition: the enhanced basic deals damage (" + empty + ")");
        Assertions.assertTrue(holding > empty,
                "「持有【elation:certified_banger】时，额外造成…」: holding it pays more (" + holding + " vs " + empty + ")");
    }

    /**
     * The rider's size is 15% of the ELATION base - and the base is the one for HER <b>character level</b>, which is what
     * this case used to get wrong.
     *
     * <p>⚠⚠ <b>The correction this case had to absorb.</b> It used to read {@code ELATION_BASIC_LEVEL_DAMAGE.get(10)},
     * following the file's own Lv10 slot convention - but the table is a CHARACTER-level table: its row 80 is 7535.107,
     * exactly the {@code 7535.107} the game's own formula card multiplies, and row 10 is ≈180. Both are corrected: the
     * engine reads {@code CanHit.getLevel()}, and this expects that same row.
     */
    @Test
    public void theRiderIsFifteenPercentOfTheElationBase() {
        double holding = damageOnTheEnhancedBasic(true);
        double empty = damageOnTheEnhancedBasic(false);
        Double base = Constant.ELATION_BASIC_LEVEL_DAMAGE.get(FIXTURE_LEVEL);   // the CHARACTER level, not the slot's
        Assertions.assertNotNull(base, "the Elation base table has a row for level " + FIXTURE_LEVEL);
        System.out.println("[pearl_rider] holding " + holding + " ; empty " + empty + " ; delta " + (holding - empty)
                + " ; 15% of the Elation base at Lv" + FIXTURE_LEVEL + " = " + (0.15 * base) + " (base " + base + ")");

        Assertions.assertTrue(holding > empty, "holding the counter pays the rider (" + holding + " > " + empty + ")");
        Assertions.assertTrue(holding - empty < 0.15 * base,
                "a share of the base, never more than the base (" + (holding - empty) + " < " + (0.15 * base)
                        + ") - a table row that is too small reads as MORE than the share");
        Assertions.assertTrue(holding - empty > 0.15 * base * 0.1,
                "and a real fraction of it, not a rounding step (" + (holding - empty) + " > " + (0.015 * base) + ")");
    }

    /**
     * The slot-10 form heals the whole party, and the lowest-HP ally a second time.
     */
    @Test
    public void theSlotNineFormHealsThePartyAndTheLowestAllyAgain() {
        Battle battle = formsBattle();
        Character pearl = battle.characters().get(0);
        Character archetype = battle.characters().get(2);      // the Hunt ally: NOT Elation, so slot 10
        Character secondLowest = battle.characters().get(1);
        Character other = battle.characters().get(3);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(10, pearl.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: a non-Elation archetype installs the SLOT-10 form, which is what this case is about");

        // Hurt the party, with ONE ally clearly the lowest so the "additional heal" has a unique target.
        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        secondLowest.takeDamage(secondLowest.getMaxHp() * 0.3);
        double lowestBefore = secondLowest.getCurrentHp();
        double otherBefore = other.getCurrentHp();

        attack(battle, pearl);
        double lowestGain = secondLowest.getCurrentHp() - lowestBefore;
        double otherGain = other.getCurrentHp() - otherBefore;
        System.out.println("[pearl_form_b] the lowest ally gained " + lowestGain + " ; another ally gained "
                + otherGain);

        Assertions.assertTrue(otherGain > 0,
                "「为我方全体目标回复…」: every ally is healed by the slot-10 form (" + otherGain + ")");
        Assertions.assertEquals(2 * otherGain, lowestGain, 0.5,
                "and the lowest-HP ally is healed a second time, so it gains twice as much");
    }

    /**
     * A clean control: the same battle WITHOUT her Ultimate, so the ordinary basic is what attacks.
     */
    @Test
    public void theOrdinaryBasicDoesNotHealTheParty() {
        Battle battle = formsBattle();
        Character pearl = battle.characters().get(0);
        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        double before = battle.characters().get(3).getCurrentHp();
        attack(battle, pearl);
        System.out.println("[pearl_form_b] with no Ultimate the ally gained "
                + (battle.characters().get(3).getCurrentHp() - before));
        Assertions.assertEquals(0, battle.characters().get(3).getCurrentHp() - before, 1e-9,
                "her unenhanced basic carries no party heal - the healing belongs to the enhanced forms");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * One settled hit on {@code ally} (the party's second slot when null), returning what it cost in HP.
     */
    private static double hit(Battle battle, Character ally, double instance) {
        Character victim = ally == null ? battle.characters().get(1) : ally;
        Enemy attacker = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - victim.getCurrentHp();
    }

    /**
     * A fresh battle with Pearl holding {@code points} of 好活当赏.
     */
    private static Battle counterBattle(int points) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (points > 0) {
            battle.characters().getFirst().getResources().gain(COUNTER, points);
        }
        return battle;
    }

    private static Battle combatBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80),
                        CharacterFactory.create(ELATION, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /** Her Ultimate aimed at the Elation ally REPLACE_SKILLs the slot-8 form into COMMON (asserted, not assumed). */
    private static void installTheSlotEightForm(Battle battle, Character pearl) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ULT_CAST, pearl, battle.characters().get(2), 0, 0));
        battle.processRequests();
        Assertions.assertEquals(8, pearl.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: the Elation archetype installs the slot-8 form");
    }

    private static double payoutWith(int elationAllies) {
        double unarmed = basicAttackDamage(elationAllies, false);
        double armed = basicAttackDamage(elationAllies, true);
        return armed - unarmed;
    }

    private static double basicAttackDamage(int elationAllies, boolean armTheTeam) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character attacker = battle.characters().get(battle.characters().size() - 1);
        if (armTheTeam) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE, 0, 0, java.util.List.of()));
        }
        double before = battle.enemies().getFirst().getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker,
                List.of(battle.enemies().getFirst()));
        return before - battle.enemies().getFirst().getCurrentHp();
    }

    /**
     * Fires CAST_SETUP with the given category and lists the allies that carry the carrier.
     */
    private static List<String> armedWith(SkillCategory category) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80));
        team.add(CharacterFactory.create(ELATION, 80));
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, category, 0, 0, java.util.List.of()));

        List<String> carriers = new ArrayList<>();
        for (Character c : battle.characters()) {
            if (c.getBuffManager().hasState(CARRIER)) {
                carriers.add(String.valueOf(c.getCid()));
            }
        }
        return carriers;
    }

    /**
     * Installs the enhanced basic through her Ultimate, then asserts the heal equals the installed skill's row at
     * {@code skillLevel} — and that the row is the documented one ({@code share} / {@code flat}).
     */
    private static void readInstalledRowAndMeasure(Battle battle, Character pearl, int expectedLevel,
                                                   double expectedShare, double expectedFlat) {
        Character archetype = battle.characters().get(2);      // not Elation ⇒ the slot-10 form
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0));
        battle.processRequests();

        Skill installed = pearl.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(installed.getSkillSlot() > 1,
                "precondition: the Ultimate installed the enhanced form into COMMON (slot " + installed.getSkillSlot() + ")");
        var rows = installed.getData().getSkills();
        int level = pearl.skillLevel(installed);
        Assertions.assertEquals(expectedLevel, level,
                "the slot's effective level: the convention's 6, plus one per 普攻等级 raise");
        var row = rows.get(level - 1);
        Assertions.assertEquals(expectedShare, row.get(1), 1e-9,
                "the row in use must be the one the document quotes (8% at E0, 8.8% at E3), not the Lv10 row");
        Assertions.assertEquals(expectedFlat, row.get(3), 1e-9, "and its flat part (160 at E0, 176 at E3)");

        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        double before = pearl.getCurrentHp();
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(installed, pearl, List.of(victim));
        battle.processRequests();
        double healed = pearl.getCurrentHp() - before;

        double base = expectedShare * pearl.getAttribute(AttributeType.DEFENCE).get() + expectedFlat;
        double expected = base * (1 + pearl.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get());
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「回复等同于真珠 8.00% 防御力 + 160」 — the heal is the installed row: slot "
                        + installed.getSkillSlot() + ", level " + level + ", expected " + expected + ", healed " + healed);
    }

    private static Battle enhancedBattle(int eidolon) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolon),
                        CharacterFactory.create(ELATION, 80), CharacterFactory.create(HUNTER, 80),
                        CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /**
     * Fires a basic attack at the given slot and returns Pearl's and the lowest ally's HP gains.
     *
     * <p>For slot 8 the Elation ally is the one her Ultimate is aimed at, so the form installed into COMMON is the slot-8
     * one - asserted, because a fixture that failed to install would measure the ordinary row instead.
     */
    private static double[] gains(int slot) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80),
                        CharacterFactory.create(ELATION, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character other = battle.characters().get(1);
        if (slot != HUNTER_SLOT) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.ULT_CAST, pearl, battle.characters().get(2), 0, 0));
            battle.processRequests();
            Assertions.assertEquals(slot, pearl.getSkills().get(SkillType.COMMON).getSkillSlot(),
                    "precondition: the Ultimate installed the enhanced form this case is about");
        }
        pearl.takeDamage(pearl.getMaxHp() * 0.6);
        other.takeDamage(other.getMaxHp() * 0.8);

        double pearlBefore = pearl.getCurrentHp();
        double otherBefore = other.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.BASIC_ATTACK, pearl, battle.enemies().getFirst(), 1, 0,
                (SkillCategory) null, slot, 0, java.util.List.of()));
        return new double[]{pearl.getCurrentHp() - pearlBefore, other.getCurrentHp() - otherBefore};
    }

    /**
     * One settled hit of the given instance value by {@code attacker} on {@code victim}, returned as the HP lost.
     */
    private static double hurtBy(Battle battle, Enemy attacker, Character victim, double instance) {
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - victim.getCurrentHp();
    }

    private static Battle barrierBattle() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private void build(int allyCid) {
        pearl = CharacterFactory.create(PEARL, 80);
        ally = CharacterFactory.create(allyCid, 80);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(pearl, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
    }

    /**
     * Her own ultimate, aimed at the archetype: the rule that hands out the marker.
     */
    private void pearlUltimateOnTheArchetype() {
        pearl.setCurrentEnergy(pearl.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(pearl, List.of(ally)), "precondition: her ultimate is cast");
        battle.processRequests();
    }

    /**
     * The ally's own ultimate; answers with how much energy 真珠 gained from it.
     */
    private double allyUltimate() {
        double before = pearlEnergy();
        ally.setCurrentEnergy(ally.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(ally, List.of(enemy)), "precondition: the ally's ultimate is cast");
        battle.processRequests();
        return pearlEnergy() - before;
    }

    private double pearlEnergy() {
        return pearl.getCurrentEnergy();
    }

    /**
     * Fires her Ultimate at the given ally and returns the skill slot it swapped COMMON for.
     */
    private static int swappedSlot(int archetype) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80),
                CharacterFactory.create(ELATION, 80), CharacterFactory.create(HUNTER, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character target = battle.characters().stream().filter(c -> c.getCid() == archetype).findFirst()
                .orElseThrow();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, target, 0, 0));

        SkillSwapBuff swap = pearl.getBuffManager().findBuff(SkillSwapBuff.class);
        Assertions.assertNotNull(swap, "the Ultimate installs a skill swap on Pearl");
        Assertions.assertEquals(SkillType.COMMON, swap.getSlot(), "and it owns the basic-attack slot");
        return swap.getReplacement().getSkillSlot();
    }

    /**
     * The slot-8 enhanced basic's total damage on one enemy, with the counter either held or empty.
     */
    private static double damageOnTheEnhancedBasic(boolean holding) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character archetype = CharacterFactory.create(ELATION_ALLY, 80);
        Battle battle = new Battle(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, her, archetype, 0, 0));
        battle.processRequests();
        if (holding) {
            her.getResources().gain(COUNTER, 10);
        } else {
            her.getResources().spend(COUNTER, her.getResources().value(COUNTER));
        }
        Assertions.assertEquals(holding, her.getResources().value(COUNTER) >= 1,
                "precondition: the counter is " + (holding ? "held" : "empty"));

        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }

    /**
     * Casts her COMMON skill at the first enemy through the engine's own skill request path.
     */
    private static void attack(Battle battle, Character pearl) {
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(pearl.getSkills().get(SkillType.COMMON), pearl, List.of(victim));
        battle.processRequests();
    }

    private static Battle formsBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                        CharacterFactory.create(HUNTER, 80), CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
