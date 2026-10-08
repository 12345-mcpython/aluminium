package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): Eidolon 6's extra Elation instance on her enhanced basic - 「额外造成 240% 的冰属性欢愉伤害，该次
 * 欢愉伤害使用【美学底本】的属性值计算」.
 *
 * <p>Two clauses state that sentence at different ranks (0.6 and 2.4), both 「使用【美学底本】的属性值计算」, so this file
 * separates them by RANK: at E6 the damage is form A's instance PLUS E6's, while at E0 it is form A alone.
 *
 * <p>⚠⚠ <b>The lever is the archetype's 欢愉度, and it used to be its ATTACK - which the Elation formula does not
 * contain.</b> That is the author's correction: {@code 欢愉伤害 = 欢愉基础值(等级) × (1+欢愉度) × (1+增笑) × [1 + 5×好活/(好活+240)]
 * × zones × 倍率}, where the UN-UNDERLINED terms (the base's level row, 欢愉度 and 增笑) are the ones taken from the
 * archetype and the attack power is nowhere in it. The old case asserted "E6's own instance follows the archetype's
 * Attack" and passed while the content read {@code holder_attr:美学底本:ATTACK}; both are corrected together.
 *
 * <p>What this file pins:
 * <ul>
 *   <li>E6's own instance exists, is much larger than form A's (2.4 against 0.6), and follows the archetype's 欢愉度;</li>
 *   <li>raising PEARL's own 欢愉度 does NOT move it - the sentence says the archetype's values, and this is the control
 *       that tells "the ally's" from "hers";</li>
 *   <li>and E0 is the control for the rank gate: it has neither clause, so nothing about the archetype may move it.</li>
 * </ul>
 * Note: E2 is deliberately NOT used as a middle point - it raises 增笑 for the team, which moves the damage for a
 * reason unrelated to this clause.
 */
public class PearlEidolon6DamageTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final int HUNTER = 1204;
    private static final int MONSTER = 1002011;
    /** The archetype's 欢愉度, the term the sentence borrows. */
    private static final double BOOSTED = 1.0;
    private static final double PLAIN = 0.0;

    /**
     * Eidolon 6 adds a much larger extra instance than form A's - 2.4 against 0.6 - and it is only there at E6.
     *
     * <p>How the two clauses are told apart. Both are extra instances reading the archetype's 欢愉度, so at E6 the damage
     * is form A's 0.6 PLUS E6's 2.4, while at E0 it is form A alone ({@code min_eidolon: 6}). The difference between
     * the two ranks at a fixed 欢愉度 therefore isolates E6's own instance.
     *
     * <p>Note: The assertion is on the SIZE of that difference rather than an exact ratio to form A. Measured, the ratio
     * between two 欢愉度 values is not the 4.0 the percentages suggest, because the two instances settle as separate hits
     * and the enemy's remaining HP truncates them differently as the totals grow - an exact ratio here would be
     * asserting the truncation as much as the clause.
     */
    @Test
    public void eidolonSixAddsAFarLargerInstanceThanFormA() {
        double sixBoosted = damage(6, BOOSTED);
        double zeroBoosted = damage(0, BOOSTED);
        double sixPlain = damage(6, PLAIN);
        double zeroPlain = damage(0, PLAIN);
        double e6Boosted = sixBoosted - zeroBoosted;
        double e6Plain = sixPlain - zeroPlain;
        System.out.println("[pearl_e6] E6: 欢愉度+" + BOOSTED + " -> " + sixBoosted + " ; +" + PLAIN + " -> " + sixPlain
                + " || E0 (form A alone): " + zeroBoosted + " ; " + zeroPlain
                + " || E6's own instance: " + e6Boosted + " vs " + e6Plain);

        Assertions.assertTrue(sixBoosted > 0, "precondition: the enhanced basic deals damage at E6");
        Assertions.assertTrue(e6Boosted > 0, "E6 pays more than E0 at the same 欢愉度");
        Assertions.assertTrue(e6Boosted > e6Plain,
                "E6's own instance follows the archetype's 欢愉度 (" + e6Boosted + " vs " + e6Plain + ")");
        // 2.4 against 0.6: E6's instance is four times form A's, so at the same 欢愉度 it must be several times the
        // difference form A alone produces.
        double formABoosted = zeroBoosted - zeroPlain;
        Assertions.assertTrue(e6Boosted > 2.0 * formABoosted,
                "E6's instance is 2.4 where form A's is 0.6, so it dominates the difference at the same 欢愉度"
                        + " (" + e6Boosted + " vs form A's " + formABoosted + ")");
        Assertions.assertTrue(zeroBoosted > zeroPlain,
                "control: form A alone (E0) also follows the archetype's 欢愉度, which is what the difference removes");
    }

    /**
     * The magnitudes are the ALLY's, not Pearl's - read as a SEPARATION between the two levers, because a small leak in
     * the other direction is real and measured.
     *
     * <p>⚠ Read as the E6−E0 DIFFERENCE, not as the total: her own enhanced basic carries an Elation rider of its own
     * (15% of the base, correctly computed from HER 欢愉度), so a total-based version of this case read that rider's growth
     * as if the clause had moved. The extra instance is what the two ranks differ by, so that difference is where "the
     * ally's, not hers" can be seen.
     *
     * <p>⚠⚠ <b>Measured, and REGISTERED rather than smoothed over</b>: moving the ARCHETYPE's 欢愉度 by +1.0 multiplies
     * E6's own instance by ~1.87, while moving PEARL's by the same amount moves it by ~1.014. The second number should be
     * exactly 1.0 - so something in that instance still reads her 欢愉度 a little. The bounds below say what is known (the
     * ally dominates by two orders of magnitude, and the wrong reading moves it ~2x) without pretending the leak is not
     * there; GAPS carries it as an open measurement.
     */
    @Test
    public void theInstanceReadsTheAllyAndNotPearl() {
        double pearlBoosted = damage(6, PLAIN, BOOSTED) - damage(0, PLAIN, BOOSTED);
        double neitherBoosted = damage(6, PLAIN, PLAIN) - damage(0, PLAIN, PLAIN);
        double allyBoosted = damage(6, BOOSTED, PLAIN) - damage(0, BOOSTED, PLAIN);
        System.out.println("[pearl_e6] E6's own instance: archetype 欢愉度 +" + BOOSTED + " = " + allyBoosted
                + " ; Pearl's 欢愉度 +" + BOOSTED + " = " + pearlBoosted
                + " ; neither = " + neitherBoosted);
        Assertions.assertTrue(allyBoosted > 1.5 * neitherBoosted,
                "the ARCHETYPE's 欢愉度 is what sizes this instance (" + allyBoosted + " vs " + neitherBoosted + ")");
        Assertions.assertTrue(pearlBoosted < 1.05 * neitherBoosted,
                "and Pearl's must not: the borrowed terms are the archetype's (" + pearlBoosted + " vs "
                        + neitherBoosted + ") - the wrong reading moves this by ~2x, not by the measured ~1.014x");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

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
        Character her = battle.characters.getFirst();

        // The Ultimate marks the aim on the ally and puts her into deep study, which is what swaps COMMON to slot 8.
        battle.fireTriggers(TriggerEvent.ULT_CAST, her, archetype, 0, 0);
        battle.processRequests();

        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }

    /** A source that never crits, so the two extra instances' difference is exactly the instance and not a roll order. */
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
