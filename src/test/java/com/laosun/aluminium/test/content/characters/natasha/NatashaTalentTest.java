package com.laosun.aluminium.test.content.characters.natasha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * Natasha's talent (天赋 生机焕发): +50% healing, but only on a recipient at 30% HP or lower.
 *
 * <p><b>What it says.</b> 「为当前生命值百分比小于等于30%的我方目标提供治疗时，娜塔莎的治疗量提高50%，
 * 该效果对持续治疗效果也会生效。」
 *
 * <h2>Why these cases are shaped as comparisons</h2>
 * The claim has two halves and either one alone is a wrong number: a bonus that never applies, and a bonus that
 * always applies. So every case compares the <b>same heal</b> on a recipient above the threshold against one
 * below it, rather than asserting an absolute amount:
 *
 * <ul>
 *   <li>a healthy ally must receive exactly what she would without the talent -- this is the half that catches
 *       the tempting wrong implementation, a plain {@code MODIFY_ATTR OUTGOING_HEALING_BOOST 0.5}, which cannot
 *       see who is being healed and would raise this one too;</li>
 *   <li>an ally at or below 30% must receive exactly 1.5× that same heal;</li>
 *   <li>and a regeneration tick must carry it too, because 「该效果对持续治疗效果也会生效」 -- the engine gets
 *       that for free by healing from {@code regen.getSource()}, and this case is what proves it rather than
 *       assuming it.</li>
 * </ul>
 *
 * <p>The ratio is measured, not the amount: what lands is post-cap and depends on the recipient's missing HP, so
 * an absolute figure here would be pinned to the fixture rather than to the rule.
 */
public class NatashaTalentTest {

    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * What the low-HP ratio measures: the talent's 1.5 <b>times</b> her 行迹 医者's 1.1.
     *
     * <p>Both factors are present on the low-HP side because both are rules in her table, and the comparison side is
     * built by emptying that table -- so the measured ratio is {@code 1.5 × 1.1}. The talent's own share is recovered
     * by dividing by the healthy ratio (which carries 医者 but not the talent).
     *
     * <p>Pinned this way because the two neighbouring implementations differ by exactly these numbers: adding the 50
     * as <b>percentage points</b> to the outgoing-healing zone would give 1.6, and ignoring 医者 would give 1.5.
     */
    private static final double TALENT_TIMES_MEDIC = 1.5 * 1.1;

    /**
     * The ally's HP fraction when it is meant to be <b>above</b> the talent's threshold.
     *
     * <p>Deliberately not 1.0: at full HP a heal restores 0, so a comparison there would be 0 vs 0.
     */
    private static final double HEALTHY_FRACTION = 0.8;

    /**
     * The ally's HP fraction when it is meant to be <b>below</b> the threshold.
     */
    private static final double LOW_FRACTION = 0.2;

    /**
     * A healthy ally gets no bonus; a low-HP ally gets +50%.
     *
     * <h2>Why this compares ratios rather than amounts</h2>
     * Her 行迹 医者 (+10% outgoing healing) is <b>also</b> a rule in the same table, so the "without the talent" side
     * is built by emptying the whole table and therefore loses the 医者 factor too. The two sides legitimately differ
     * by 1.1 before the talent is considered, and an absolute comparison would be measuring 医者 rather than the
     * talent.
     *
     * <p>The talent's own share is isolated by dividing the two ratios: measured, a low-HP ally gives
     * {@code 825/500 = 1.65 = 1.5 × 1.1} while a healthy one gives {@code 550/500 = 1.1}. So the bonus is exactly the
     * 1.5 the sentence states, and it is absent above the threshold.
     *
     * <p>⚠ Measured on {@code calculateHeal}, not on what lands: {@code heal} clamps to the recipient's missing HP,
     * and at 80% HP an ally is missing only ~316, so 500 and 825 would both land as 316 and no case below could tell
     * them apart.
     */
    @Test
    public void theBonusAppliesOnlyBelowThirtyPercent() {
        double healthyWithout = healed(false, HEALTHY_FRACTION);
        double healthyWith = healed(true, HEALTHY_FRACTION);
        double lowWithout = healed(false, LOW_FRACTION);
        double lowWith = healed(true, LOW_FRACTION);

        Assertions.assertTrue(healthyWithout > 0 && lowWithout > 0,
                "precondition: both heals compute to something");

        double healthyRatio = healthyWith / healthyWithout;
        double lowRatio = lowWith / lowWithout;

        // The claim, stated once: the talent's own share is exactly the 50% the sentence gives, and the side that
        // differs between the two ratios is the talent alone -- so dividing them out cancels 医者.
        Assertions.assertEquals(1.5, lowRatio / healthyRatio, 0.02,
                "the talent's own share -- the ratio of the ratios -- must be exactly 50%: "
                        + lowRatio + " / " + healthyRatio + " (raw heals " + lowWith + ", " + lowWithout
                        + ", " + healthyWith + ", " + healthyWithout + ")");

        // The healthy side must carry NO talent. It does read 1.1, and that 1.1 is 医者 rather than the talent --
        // the assertion is on it not being multiplied by 1.5, which is the whole difference.
        Assertions.assertEquals(1.1, healthyRatio, 0.02,
                "an ally above the threshold must get the 医者 trace and nothing else: "
                        + healthyWith + " vs " + healthyWithout);

        Assertions.assertEquals(TALENT_TIMES_MEDIC, lowRatio, 0.02,
                "「治疗量提高50%」 on an ally at 20% HP: " + lowWith + " vs " + lowWithout);
    }

    /**
     * The threshold is inclusive: exactly 30% shares the bonus.
     *
     * <p>「小于等于30%」 -- a `&lt;` instead of `&lt;=` would pass the case above and quietly drop this one.
     */
    @Test
    public void theThresholdIsInclusive() {
        double atThresholdWithout = healed(false, 0.3);
        double atThresholdWith = healed(true, 0.3);

        Assertions.assertEquals(TALENT_TIMES_MEDIC, atThresholdWith / atThresholdWithout, 0.05,
                "「小于等于30%」 includes exactly 30%: " + atThresholdWith + " vs " + atThresholdWithout);
    }

    /**
     * A regeneration tick carries the bonus: 「该效果对持续治疗效果也会生效」.
     *
     * <p>The engine applies it for free because {@code Battle.tickRegens} heals from {@code regen.getSource()},
     * the original applier -- but "for free" is a claim about the code, and this is the case that would go red if
     * somebody changed the tick to heal from the recipient instead.
     */
    @Test
    public void aRegenerationTickCarriesTheBonusToo() {
        double without = regenerationTick(false);
        double with = regenerationTick(true);

        Assertions.assertTrue(without > 0, "precondition: the tick heals");
        Assertions.assertEquals(TALENT_TIMES_MEDIC, with / without, 0.05,
                "the regeneration tick must carry the same +50%: " + with + " vs " + without);
    }

    /**
     * Measures the heal Natasha <b>computes</b> for an ally at the given HP fraction.
     *
     * <p>⚠ <b>Why {@code calculateHeal} and not {@code heal}.</b> {@code heal} returns what actually landed, which
     * {@code CanHit.heal} clamps to the recipient's missing HP. At 80% HP an ally is missing only ~316, so a bonus of
     * 500 and one of 825 both land as 316 and are indistinguishable -- the first version of this test measured the
     * clamped number and therefore could not detect an unconditional bonus at all. The computed amount is the
     * quantity the talent scales, so it is what the cases must compare.
     *
     * @param registerTalent whether her talent rule is in effect
     * @param allyHpFraction the ally's HP as a fraction of its maximum
     * @return the healing amount as computed, before the cap
     */
    private static double healed(boolean registerTalent, double allyHpFraction) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(1109, LEVEL, false, null, null, 0);
        positionAt(ally, allyHpFraction);

        Battle battle = battle(natasha, List.of(natasha, ally), registerTalent);
        return battle.calculateHeal(natasha, ally, 500);
    }

    /**
     * Runs one real regeneration tick on a low-HP ally and returns the HP it restored.
     *
     * <p>The regeneration is attached by hand rather than through her Skill, so this measures the <b>tick's</b>
     * healing path -- which is the path 「该效果对持续治疗效果也会生效」 is about -- and not her Skill's data row.
     *
     * <p>Unlike the cases above this can use the landed amount, because the ally is placed low enough that the
     * missing-HP clamp is not reached: at {@link #LOW_FRACTION} it is missing far more than the heal computes to, so
     * what lands <b>is</b> the computed amount. {@code tickRegens} also reports what it really healed, which is the
     * number that would silently stay flat if the tick healed from the recipient rather than the applier.
     *
     * @param registerTalent whether her talent rule is in effect
     * @return the HP the ally gained from one tick
     */
    private static double regenerationTick(boolean registerTalent) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(1109, LEVEL, false, null, null, 0);

        Battle battle = battle(natasha, List.of(natasha, ally), registerTalent);
        ally.getBuffManager().addBuff(
                new com.laosun.aluminium.models.buff.RegenBuff(natasha, "持续治疗", 500, 2));
        positionAt(ally, LOW_FRACTION);

        return battle.tickRegens(ally);
    }

    /**
     * Builds the battle, optionally without her talent rule.
     *
     * <p>Removing it is done by emptying her table, so the comparison isolates this one rule: everything else she
     * carries (traces, the level convention) is identical on both sides.
     *
     * @param natasha          the healer
     * @param party            the party
     * @param registerTalent   whether to keep her table
     * @return a started battle
     */
    private static Battle battle(Character natasha, List<Character> party, boolean registerTalent) {
        Battle battle = new Battle(party, List.of(EnemyFactory.create(MONSTER, LEVEL, 1)),
                BattleFixture.alwaysRoll(1.0));
        if (!registerTalent) {
            natasha.setTriggerTable(new com.laosun.aluminium.models.trigger.TriggerTable(NATASHA, List.of()));
        }
        battle.startBattle();
        return battle;
    }

    /**
     * Puts a unit at a fraction of its maximum HP.
     *
     * <p>By consuming the difference rather than healing down, so the result is exact; {@code consumeHp} is the
     * engine's own "pay HP as a price" and does not fire damage events.
     *
     * @param unit     the unit to place
     * @param fraction the target fraction of its maximum HP
     */
    private static void positionAt(Character unit, double fraction) {
        double want = unit.getMaxHp() * fraction;
        double excess = unit.getCurrentHp() - want;
        if (excess > 0) {
            unit.consumeHp(excess);
        } else if (excess < 0) {
            unit.heal(-excess);
        }
    }
}
