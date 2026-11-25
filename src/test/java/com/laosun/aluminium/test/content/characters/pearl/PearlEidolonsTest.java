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
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl), Eidolons 1, 2, 4 and 6: the Elation tier at a second Elation ally and the lethal-damage save,
 * the widened action advance with the extra-turn reward, the doubled Elation payout with its four repeated conditions,
 * and E6's own extra instance on the enhanced basic.
 *
 * <p>Merged from the former {@code PearlEidolon1Test}, {@code PearlEidolon2Test} and {@code PearlEidolon46Test}; every
 * assertion and Javadoc of measurement is preserved.
 *
 * <p>L3 consolidation: cases that share one fixture, one clause and one observed object are now ONE scenario test, and
 * their assertions sit inside {@link Assertions#assertAll} with a message naming the clause. Nothing was dropped: the
 * expected values, the tolerances and the measurements are the ones the separate cases carried, and exception paths
 * were never merged with positive ones.
 */
public class PearlEidolonsTest {
    private static final int PEARL = 1503;      // Elation
    private static final int ELATION = 1502;    // Yao Guang, Elation
    private static final int ELATION_A = 1502;  // Elation
    private static final int ELATION_B = 1505;  // Elation
    private static final int HUNTER = 1204;
    private static final int ALLY = 1204;
    private static final int ELATION_ALLY = 1502;
    private static final double EPS = 1.0;
    private static final String COUNTER = "elation:certified_banger";
    private static final String PUNCHLINE = "elation:punchline";

    /**
     * The archetype's 欢愉度, the term the E6 sentence borrows.
     */
    private static final double BOOSTED = 1.0;
    private static final double PLAIN = 0.0;

    // ---------------------------------------------------------------------------------------------------------------
    // Eidolon 1: the tier at two Elation allies, and the lethal-damage save.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Eidolon 1's tiers, read as the difference the EIDOLON makes rather than as her absolute panel.
     *
     * <p>Note: Pearl's panel is no longer zero-based - her ten stat traces (measured absent from
     * {@code skill_traces.json}) put 10% Elation on her from the start - so an absolute assertion here would be
     * asserting that other clause as much as this one. Each reading below is therefore the difference against the
     * same team with the Eidolon condition left unmet.
     */
    @Test
    public void theElationBoostNeedsBothTheEidolonAndTheSecondElationAlly() {
        double ranked = boost(1, 2);
        double alone = boost(1, 1);
        double unranked = boost(0, 2);
        System.out.println("[pearl_e1] panel Elation -- eidolon 1 with 2 Elation allies = " + ranked
                + " ; eidolon 1 with 1 = " + alone + " ; eidolon 0 with 2 = " + unranked);

        // Note: `alone` and `unranked` both sit at the stat-trace floor, and the tier adds 0.10 on top of it.
        Assertions.assertEquals(0.10, ranked - unranked, 1e-9, "at 2 Elation allies the team gains 10%");
        Assertions.assertEquals(0.0, alone - unranked, 1e-9,
                "the first tier needs a second Elation ally, not just the Eidolon");
        Assertions.assertTrue(unranked > 0, "precondition: her stat traces give the panel a floor (" + unranked + ")");
    }

    /**
     * E1 saves the ally; E0 does not. It heals HALF the Max HP, not full and not one point.
     *
     * <p>⭐ One scenario: both halves read the same object (the ally's HP as a fraction of its Max HP after one lethal
     * blow) off the same fixture ({@code saveBattle} at rank 1, with rank 0 as the control); the second half states the
     * size of the restore the first half only shows to exist.
     *
     * <p>Note: Measured at 0.51 rather than exactly 0.50, and that is her own outgoing-healing boost riding along -
     * the 20% of her Elation that her trace grants (which is 0.02 at the Elation this fixture has) multiplies every
     * heal she makes, this one included. The tolerance states that rather than hiding it: the claim is "half", not
     * "half to the last decimal".
     */
    @Test
    public void theSaveNeedsTheEidolonAndRestoresHalfMaxHp() {
        double savedAtOne = hpFractionAfterLethalBlow(1);
        double savedAtZero = hpFractionAfterLethalBlow(0);
        double fraction = hpFractionAfterLethalBlow(1);
        System.out.println("[pearl_e1] after a lethal blow the ally is at " + savedAtOne + " of Max HP at E1, and "
                + savedAtZero + " at E0");
        System.out.println("[pearl_e1] the restored fraction is " + fraction
                + " (half, times her outgoing-healing boost)");

        Assertions.assertAll("「回复等同于其生命上限 50% 的生命值」",
                () -> Assertions.assertTrue(savedAtOne > 0.4,
                        "[the save] at E1 the ally is restored to about half its Max HP (" + savedAtOne + ")"),
                () -> Assertions.assertTrue(savedAtZero <= 0,
                        "[the save] and at E0 it simply falls (" + savedAtZero + ")"),
                () -> Assertions.assertEquals(0.5, fraction, 0.03,
                        "[its size] 「回复等同于其生命上限 50% 的生命值」 - half, plus the heal boost her own trace"
                                + " grants"));
    }

    /**
     * 「单场战斗中可以触发 2 次」: the third lethal blow in one battle is not saved.
     */
    @Test
    public void theAllowanceIsTwoSavesInOneBattle() {
        Battle battle = saveBattle(1);
        Character ally = battle.characters().get(1);
        Enemy attacker = battle.enemyUnits().getFirst();

        double first = lethalBlow(battle, attacker, ally);
        double second = lethalBlow(battle, attacker, ally);
        double third = lethalBlow(battle, attacker, ally);
        System.out.println("[pearl_e1] after 1/2/3 lethal blows the ally is at " + first + " / " + second + " / "
                + third + " of Max HP");
        Assertions.assertTrue(first > 0.4, "the first save happens");
        Assertions.assertTrue(second > 0.4, "the second save happens too");
        Assertions.assertTrue(third <= 0, "and the third does not: the allowance is two, and it is per battle");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eidolon 2: the widened advance, the 15% team 增笑, and the doubled extra-turn reward.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * From Eidolon 2 the OTHER Elation allies are advanced where they were not before, and the non-Elation archetype
     * no longer is - and the percentages are the tier's own, because the tiers still compound.
     *
     * <p>The archetype is the Hunt ally, i.e. not on the Elation path - which is what makes the two effects separable:
     * the tier's only Elation targets are the two allies beside Pearl, and the archetype is excluded by name.
     *
     * <p>Note: Pearl is on the Elation path HERSELF, so this team has three Elation characters (her plus the two
     * allies), which satisfies every tier: each ally is advanced by 10%, then 15%, then 30%, keeping
     * {@code 0.9 x 0.85 x 0.70}. The first version of this case expected only two tiers and was corrected by the
     * measurement - the count includes her.
     *
     * <p>⭐ One scenario: the compounding reading is the same observed object (the fraction of its wait an Elation ally
     * keeps) on the same fixture, {@code fractions(2)}, so it is one clause with the widened advance, not a second case.
     */
    @Test
    public void eidolonTwoAdvancesTheOtherElationAlliesWithTheTiersOwnPercentages() {
        Fraction e0 = fractions(0);
        Fraction e2 = fractions(2);
        System.out.println("[pearl_e2_advance] E0 -> archetype keeps " + e0.archetype
                + ", elation ally A " + e0.elationA + ", elation ally B " + e0.elationB
                + " || E2 -> archetype " + e2.archetype + ", A " + e2.elationA + ", B " + e2.elationB);
        System.out.println("[pearl_e2_advance] at E2 an Elation ally keeps " + e2.elationA
                + " (expected 0.9 x 0.85 x 0.70 = 0.5355)");

        Assertions.assertAll("「除…【char:1503/aesthetic_original】外」",
                () -> Assertions.assertEquals(1.0, e0.elationA, 1e-6,
                        "[the advance] at E0 the tiers advance the archetype only: an Elation ally beside Pearl is"
                                + " untouched"),
                () -> Assertions.assertEquals(1.0, e0.elationB, 1e-6, "[the advance] and so is the other one"),
                () -> Assertions.assertTrue(e0.archetype < 0.9,
                        "[the advance] while the archetype IS advanced at E0 (" + e0.archetype + ")"),
                () -> Assertions.assertTrue(e2.elationA < 0.9,
                        "[the widening] at E2 the same tier reaches the Elation allies (" + e2.elationA + ")"),
                () -> Assertions.assertTrue(e2.elationB < 0.9, "[the widening] both of them (" + e2.elationB + ")"),
                () -> Assertions.assertEquals(1.0, e2.archetype, 1e-6,
                        "[the exclusion] and the archetype, which is NOT on the Elation path, is excluded by"
                                + " 「除…【char:1503/aesthetic_original】外」"),
                () -> Assertions.assertEquals(0.9 * 0.85 * 0.70, e2.elationA, 1e-6,
                        "[the percentages] the tiers are unchanged: 10%, 15% then 30% on the same ally"));
    }

    /**
     * Returns a NON-Pearl ally's 增笑 at the given Eidolon rank -- "我方全体目标" includes the others.
     */
    @Test
    public void theAmpArrivesAtEidolonTwoAndNowhereElse() {
        double none = ampAt(0);
        double one = ampAt(1);
        double two = ampAt(2);
        System.out.println("[pearl_e2] team ELATION_DAMAGE_AMP at eidolon 0/1/2 = " + none + " / " + one + " / " + two);

        Assertions.assertEquals(0.0, none, 1e-9, "no amp without the eidolon");
        Assertions.assertEquals(0.0, one, 1e-9, "and Eidolon 1 does not grant it either");
        Assertions.assertEquals(0.15, two, 1e-9, "Eidolon 2 grants 15%");
    }

    /**
     * At Eidolon 2 the turn pays double - read off the counter that has NO cap - and the boosted amount comes back
     * off at BOTH ranks, which is the half that needed a different treatment.
     *
     * <p>Note: The 好活当赏 side cannot show "double" at full value, and that is not a bug in either the clause or the
     * cap: her talent states 「持有上限为 50 点」, so 30 + 30 lands on 50. Measured, and asserted as the cap rather than
     * quietly loosened - the doubling is what makes the cap BIND, and saying so is more useful than picking a starting
     * value that hides it. 笑点 is a battle-level pool with no cap (its declaration carries Integer.MAX_VALUE), so it
     * shows the doubling exactly.
     *
     * <p>The removal states 60/120 at every rank; below Eidolon 2 that is more than was granted, and the clamp takes
     * only what is there.
     *
     * <p>⭐ One scenario: both halves are one {@code Reading} off the same fixture pair, {@code turn(0)} and
     * {@code turn(2)} - the grant and what the closing clause takes back are two moments of the same turn.
     */
    @Test
    public void eidolonTwoDoublesTheRewardAndTheRemovalFollows() {
        Reading e0 = turn(0);
        Reading e2 = turn(2);
        System.out.println("[pearl_e2] E0 granted " + e0.granted + " and " + e0.punchlineGranted
                + " punchline ; E2 granted " + e2.granted + " and " + e2.punchlineGranted);
        System.out.println("[pearl_e2] after the turn ended: E0 left " + e0.counterLeft + " of the counter and "
                + e0.punchlineLeft + " punchline ; E2 left " + e2.counterLeft + " / " + e2.punchlineLeft);

        Assertions.assertAll("「提高 100%」",
                () -> Assertions.assertEquals(30, e0.granted,
                        "[the grant] below Eidolon 2 the base 30 is all there is"),
                () -> Assertions.assertEquals(60, e0.punchlineGranted,
                        "[the grant] and the base 60 of punchline"),
                () -> Assertions.assertEquals(50, e2.granted,
                        "[the grant] 「提高 100%」would be 60, and her counter's stated cap of 50 is what it lands on"),
                () -> Assertions.assertEquals(120, e2.punchlineGranted,
                        "[the grant] the uncapped punchline pool shows the doubling exactly: 60 + 60"),
                () -> Assertions.assertEquals(0, e0.counterLeft, "[the removal] E0: the 30 that was granted is gone"),
                () -> Assertions.assertEquals(0, e0.punchlineLeft, "[the removal] E0: and the 60"),
                () -> Assertions.assertEquals(0, e2.counterLeft, "[the removal] E2: the 60 that was granted is gone"),
                () -> Assertions.assertEquals(0, e2.punchlineLeft, "[the removal] E2: and the 120"));
    }

    /**
     * Part of it spent before the turn ends: what is left is taken and no more, at both ranks.
     *
     * <p>The author's ruling for this clause (「花掉部分就撤回剩余的」), and the reason one removal serves both ranks: the
     * clamp, not the stated number, decides what comes off.
     */
    @Test
    public void aPartlySpentBoostedRewardIsTakenBackUpToWhatIsLeft() {
        Reading e0 = turnWithSpend(0, 12);
        Reading e2 = turnWithSpend(2, 12);
        System.out.println("[pearl_e2] granted-then-spent-12: E0 left " + e0.counterLeft + " ; E2 left "
                + e2.counterLeft);
        Assertions.assertEquals(0, e0.counterLeft, "E0: 30 granted, 12 spent, the remaining 18 comes off");
        Assertions.assertEquals(0, e2.counterLeft, "E2: 60 granted, 12 spent, the remaining 48 comes off");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eidolons 4 and 6: the doubled payout, the four repeated clauses, and E6's own instance.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The four added clauses are gated on the Eidolon, and they carry the SAME conditions as the clauses they double.
     *
     * <p>Read from the FILE rather than from the parsed table, the same way {@code ContentDuplicateGuardTest} reads it -
     * the guard compares raw conditions, so this asserts the same thing the guard does.
     */
    @Test
    public void theAddedClausesRepeatTheirSiblingsConditionsAndNameTheRank() throws Exception {
        var path = java.nio.file.Path.of("src/main/resources/game/characters/" + PEARL + ".json");
        var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                .getAsJsonObject();
        int checked = 0;
        for (var element : root.getAsJsonArray("rules")) {
            var rule = element.getAsJsonObject();
            String id = rule.get("id").getAsString();
            if (!id.startsWith("eidolon4_doubles")) {
                continue;
            }
            checked++;
            java.util.List<String> when = new java.util.ArrayList<>();
            for (var condition : rule.getAsJsonArray("when")) {
                when.add(condition.getAsString());
            }
            Assertions.assertTrue(when.contains("eidolon >= 4"),
                    id + " names the rank in its content: " + when);
            Assertions.assertTrue(when.stream().anyMatch(c -> c.contains("char:1503/rendering_reason")),
                    id + " repeats its sibling's armed-attacker condition: " + when);
            Assertions.assertTrue(when.stream().anyMatch(c -> c.startsWith("allies_with_path")),
                    id + " repeats its sibling's tier condition: " + when);
            Assertions.assertEquals(4, rule.get("min_eidolon").getAsInt(),
                    id + " carries the rule-level gate too");
        }
        System.out.println("[pearl_e4] doubling clauses checked = " + checked);
        Assertions.assertEquals(4, checked, "one added clause per tier");
    }

    /**
     * At E4 the arming pays twice what it pays at E3 - the two ranks whose Elation-skill level is the SAME - and the
     * doubling follows the tier the team qualifies for, not a single flat number.
     *
     * <p>Note: Why E3 is the control rather than E0: Eidolon 3 grants 「欢愉技等级+1」, so the Elation skill's level goes
     * 1 -> 2 at E3 and the {@code elation_base} TABLE LOOKUP changes with it. E3 and E4 differ in exactly one thing - the
     * doubling clause - so their ratio is the doubling and nothing else.
     *
     * <p>⭐ One scenario: the 4-ally case is one turn of the sweep below, measured on the same fixture pair
     * ({@code payout(3, n)} against {@code payout(4, n)}) and the same observed object (their ratio), so it is stated
     * with its own reading and then again inside the sweep over every tier.
     */
    @Test
    public void theDoublingFollowsTheTier() {
        List<Executable> checks = new ArrayList<>();

        double atE3 = payout(3, 4);
        double atE4 = payout(4, 4);
        System.out.println("[pearl_e4] the payout at E3 = " + atE3 + " ; at E4 = " + atE4
                + " ; ratio = " + (atE4 / atE3));
        checks.add(() -> Assertions.assertTrue(atE3 > 0,
                "[the 4-ally tier] precondition: the Elation skill pays at all (" + atE3 + ")"));
        checks.add(() -> Assertions.assertEquals(2.0, atE4 / atE3, 1e-6,
                "[the 4-ally tier] 「提高原倍率的 100%」: the same percentages again, so twice the payout"));

        for (int allies : new int[]{1, 2, 3, 4}) {
            double tierE3 = payout(3, allies);
            double tierE4 = payout(4, allies);
            System.out.println("[pearl_e4] " + allies + " Elation allies: E3 = " + tierE3 + " ; E4 = " + tierE4);
            checks.add(() -> Assertions.assertTrue(tierE3 > 0,
                    "[the doubling] precondition: the skill pays at the " + allies + "-ally tier"));
            checks.add(() -> Assertions.assertEquals(2.0, tierE4 / tierE3, 1e-6,
                    "[the doubling] the doubling applies at the " + allies + "-ally tier as well"));
        }

        Assertions.assertAll("「提高原倍率的 100%」: the doubling follows the tier", checks);
    }

    /**
     * Eidolon 6 adds a much larger extra instance than form A's - 2.4 against 0.6 - and it is only there at E6; the
     * magnitudes are the ALLY's, not Pearl's.
     *
     * <p>⭐ One scenario: both halves read the same object (E6's own extra instance, {@code damage(6, …) - damage(0,
     * …)}) off the same fixture - the slot-8 enhanced basic with a fixed 欢愉度 on the archetype and, in the second
     * half, on Pearl - so the second half is the first one's SOURCE, not another clause. The separation is asserted
     * rather than an equality because a small leak in the other direction is real and measured.
     */
    @Test
    public void eidolonSixAddsAFarLargerInstanceThanFormAAndReadsTheAlly() {
        double sixBoosted = damage(6, BOOSTED);
        double zeroBoosted = damage(0, BOOSTED);
        double sixPlain = damage(6, PLAIN);
        double zeroPlain = damage(0, PLAIN);
        double e6Boosted = sixBoosted - zeroBoosted;
        double e6Plain = sixPlain - zeroPlain;
        System.out.println("[pearl_e6] E6: elation:elation_degree+" + BOOSTED + " -> " + sixBoosted + " ; +" + PLAIN + " -> " + sixPlain
                + " || E0 (form A alone): " + zeroBoosted + " ; " + zeroPlain
                + " || E6's own instance: " + e6Boosted + " vs " + e6Plain);

        double pearlBoosted = damage(6, PLAIN, BOOSTED) - damage(0, PLAIN, BOOSTED);
        double neitherBoosted = damage(6, PLAIN, PLAIN) - damage(0, PLAIN, PLAIN);
        double allyBoosted = damage(6, BOOSTED, PLAIN) - damage(0, BOOSTED, PLAIN);
        System.out.println("[pearl_e6] E6's own instance: archetype elation:elation_degree +" + BOOSTED + " = " + allyBoosted
                + " ; Pearl's elation:elation_degree +" + BOOSTED + " = " + pearlBoosted
                + " ; neither = " + neitherBoosted);

        double formABoosted = zeroBoosted - zeroPlain;
        Assertions.assertAll("E6's own extra instance",
                () -> Assertions.assertTrue(sixBoosted > 0,
                        "[the instance] precondition: the enhanced basic deals damage at E6"),
                () -> Assertions.assertTrue(e6Boosted > 0,
                        "[the instance] E6 pays more than E0 at the same elation:elation_degree"),
                () -> Assertions.assertTrue(e6Boosted > e6Plain,
                        "[the archetype] E6's own instance follows the archetype's elation:elation_degree (" + e6Boosted + " vs "
                                + e6Plain + ")"),
                () -> Assertions.assertTrue(e6Boosted > 2.0 * formABoosted,
                        "[versus form A] E6's instance is 2.4 where form A's is 0.6, so it dominates the difference at"
                                + " the same elation:elation_degree (" + e6Boosted + " vs form A's " + formABoosted + ")"),
                () -> Assertions.assertTrue(zeroBoosted > zeroPlain,
                        "[versus form A] control: form A alone (E0) also follows the archetype's elation:elation_degree, which is what"
                                + " the difference removes"),
                () -> Assertions.assertTrue(allyBoosted > 1.5 * neitherBoosted,
                        "[the source] the ARCHETYPE's elation:elation_degree is what sizes this instance (" + allyBoosted + " vs "
                                + neitherBoosted + ")"),
                () -> Assertions.assertTrue(pearlBoosted < 1.05 * neitherBoosted,
                        "[not Pearl] and Pearl's must not: the borrowed terms are the archetype's (" + pearlBoosted
                                + " vs " + neitherBoosted + ") - the wrong reading moves this by ~2x, not by the"
                                + " measured ~1.014x"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Builds a team with the given Eidolon rank and number of Elation characters.
     */
    private static double boost(int eidolonRank, int elationAllies) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, true, null, null, eidolonRank));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters().stream().filter(c -> c.getCid() == PEARL).findFirst().orElseThrow();
        return mine.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    /**
     * The ally's HP as a fraction of its Max HP after one lethal blow, for a Pearl of the given rank.
     */
    private static double hpFractionAfterLethalBlow(int eidolonRank) {
        Battle battle = saveBattle(eidolonRank);
        Character ally = battle.characters().get(1);
        return lethalBlow(battle, battle.enemyUnits().getFirst(), ally);
    }

    /**
     * Settles an overwhelming hit and returns the ally's HP afterwards, as a fraction of its maximum.
     */
    private static double lethalBlow(Battle battle, Enemy attacker, Character ally) {
        battle.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                ally.getMaxHp() * 4));
        battle.processRequests();
        return ally.getMaxHp() <= 0 ? 0 : ally.getCurrentHp() / ally.getMaxHp();
    }

    private static Battle saveBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /**
     * The fraction of its wait each unit KEEPS after the Ultimate.
     */
    private record Fraction(double archetype, double elationA, double elationB) {
    }

    private static Fraction fractions(int eidolonRank) {
        Battle battle = new Battle(team(eidolonRank), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character elationA = battle.characters().get(1);
        Character elationB = battle.characters().get(2);
        Character archetype = battle.characters().get(3);

        double[] before = waits(battle, pearl, elationA, elationB, archetype);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0));
        battle.processRequests();
        double[] after = waits(battle, pearl, elationA, elationB, archetype);
        return new Fraction(after[3] / before[3], after[1] / before[1], after[2] / before[2]);
    }

    /**
     * Remaining action value for Pearl, the two Elation allies and the archetype, in that order.
     */
    private static double[] waits(Battle battle, Character pearl, Character elationA, Character elationB,
                                  Character archetype) {
        double[] out = new double[4];
        Character[] wanted = {pearl, elationA, elationB, archetype};
        for (Signal signal : battle.queue().snapshot()) {
            for (int i = 0; i < wanted.length; i++) {
                if (signal.getCanHit() == wanted[i]) {
                    out[i] = battle.queue().getTimeRemaining(signal);
                }
            }
        }
        return out;
    }

    /**
     * Pearl, two other Elation allies, and a HUNT ally last - the Ultimate aims at the last member, so the archetype is
     * not on the Elation path and "who was aimed at" stays independent of "who qualifies".
     */
    private static List<Character> team(int eidolonRank) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank));
        team.add(CharacterFactory.create(ELATION_A, 80));
        team.add(CharacterFactory.create(ELATION_B, 80));
        team.add(CharacterFactory.create(HUNTER, 80));
        return team;
    }

    /**
     * Returns a NON-Pearl ally's 增笑 at the given Eidolon rank -- "我方全体目标" includes the others.
     */
    private static double ampAt(int rank) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80, true, null, null, rank),
                CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character ally = battle.characters().get(1);
        return ally.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
    }

    /**
     * What the turn granted, what it took back, and what is left on each counter.
     */
    private record Reading(int granted, int punchlineGranted, int counterLeft, int punchlineLeft) {
    }

    private static Reading turn(int eidolonRank) {
        return run(eidolonRank, 0);
    }

    private static Reading turnWithSpend(int eidolonRank, int spend) {
        return run(eidolonRank, spend);
    }

    private static Reading run(int eidolonRank, int spendAfterGrant) {
        Battle battle = turnBattle(eidolonRank);
        Character pearl = battle.characters().getFirst();
        Character archetype = battle.characters().get(1);

        int punchlineStart = battle.partyResource(PUNCHLINE).value();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        battle.processRequests();
        int granted = pearl.getResources().value(COUNTER);
        int punchlineGranted = battle.partyResource(PUNCHLINE).value() - punchlineStart;
        if (spendAfterGrant > 0) {
            pearl.getResources().spend(COUNTER, spendAfterGrant);
        }

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0));
        battle.processRequests();

        // What is left after the closing clause, measured against where the turn started on each counter.
        int counterLeft = pearl.getResources().value(COUNTER);
        int punchlineLeft = battle.partyResource(PUNCHLINE).value() - punchlineStart;
        return new Reading(granted, punchlineGranted, counterLeft, punchlineLeft);
    }

    private static Battle turnBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ELATION_ALLY, 80), CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.characters().get(1).getBuffManager()
                .addBuff(new com.laosun.aluminium.buff.StateBuff("char:1503/aesthetic_original", 3, false));
        return battle;
    }

    /**
     * What arming the team pays: (armed - unarmed) for one ally's basic attack on the same enemy.
     */
    private static double payout(int eidolonRank, int elationAllies) {
        double unarmed = attackingDamage(eidolonRank, elationAllies, false);
        double armed = attackingDamage(eidolonRank, elationAllies, true);
        return armed - unarmed;
    }

    private static double attackingDamage(int eidolonRank, int elationAllies, boolean armTheTeam) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank));
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

    private static double damage(int eidolon, double archetypeElationBoost) {
        return damage(eidolon, archetypeElationBoost, PLAIN);
    }

    /**
     * The slot-8 enhanced basic's total damage for a Pearl of the given Eidolon rank.
     *
     * @param archetypeElationBoost 欢愉度 set on the designated ally (the archetype)
     * @param pearlElationBoost     欢愉度 set on Pearl herself, which the clause must NOT read
     */
    private static double damage(int eidolon, double archetypeElationBoost, double pearlElationBoost) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, eidolon);
        Character archetype = CharacterFactory.create(ELATION_ALLY, 80);
        archetype.setAttribute(AttributeType.ELATION_DAMAGE_BOOST, new DoubleValue(archetypeElationBoost));
        pearl.setAttribute(AttributeType.ELATION_DAMAGE_BOOST, new DoubleValue(pearlElationBoost));
        List<Character> team = new ArrayList<>(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)));
        // ⚠ A DUMMY with a million HP, not a stage monster: the totals here are tens of thousands, and a real monster's
        // remaining HP truncates each separate hit differently - measured, that truncation was larger than the
        // difference this test reads.
        Enemy enemy = Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
        // ⚠ And a source that never crits: the two instances settle in sequence from one generator, so a crit roll
        // whose OUTCOME changes with the numbers shifts every later roll - measured as a 1.4% wobble in a difference
        // that is supposed to be exactly E6's own instance.
        Battle battle = new Battle(team, List.of(enemy), new NoCrit());
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
     * A source that never crits, so the two extra instances' difference is exactly the instance and not a roll order.
     */
    private static final class NoCrit extends Random {
        @Override
        public double nextDouble() {
            return 0.9999;
        }

        @Override
        public int nextInt(int bound) {
            return super.nextInt(bound);
        }
    }
}
