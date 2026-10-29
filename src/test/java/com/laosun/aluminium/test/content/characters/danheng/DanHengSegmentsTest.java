package com.laosun.aluminium.test.content.characters.danheng;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dan Heng (丹恒) 1002: his damage is dealt in <b>segments</b>, and the segment count is what several of his sentences
 * count.
 *
 * <h2>What the document states</h2>
 * 「官方最大命中次数」 is <b>2</b> for the basic attack and <b>4</b> for the Skill, with the split ratios
 * {@code 0.45 + 0.55} and {@code 0.30 + 0.15 + 0.15 + 0.40} (both summing to 1). The <b>total is therefore unchanged</b> -
 * what changes is the number of damage <b>instances</b>, and that is observable in three places:
 * <ul>
 *   <li>each instance rolls its <b>own crit</b> - the game's 「战技造成的伤害<b>触发暴击时</b>…减速」 is rolled per instance,
 *       so four chances instead of one;</li>
 *   <li>each instance settles its own {@code DEALING_DAMAGE} / {@code CRIT_DEALT} / {@code TAKING_HIT};</li>
 *   <li>the toughness is one number for the cast, so it is spread over the instances (the cast's total is unchanged).</li>
 * </ul>
 *
 * <p>Before this, the engine settled <b>one</b> instance of the whole multiplier - the total damage was right and the
 * trigger count was not, which is the kind of difference a damage-only assertion cannot see.
 */
public class DanHengSegmentsTest {
    private static final double EPS = 1e-6;

    private static final int DAN_HENG = 1002;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. How many instances, and do they still add up
    // ==================================================================

    /**
     * The basic attack settles two instances and the Skill four; the ultimate one (its table states a single segment).
     */
    @Test
    public void theCastSettlesTheSegmentsTheDocumentStates() {
        Assertions.assertEquals(2, instancesOf(SkillType.COMMON, 10),
                "「官方最大命中次数 2」 for the basic attack (0.45 + 0.55)");
        Assertions.assertEquals(4, instancesOf(SkillType.SKILL, 10),
                "「官方最大命中次数 4」 for the Skill (0.30 + 0.15 + 0.15 + 0.40)");
        Assertions.assertEquals(1, instancesOf(SkillType.ULTRA, 10),
                "the ultimate's table states one segment");
    }

    /**
     * The split does not change the total: the instances' base layer sums to the whole multiplier.
     *
     * <p>Note: A ratio <b>of the row's own multiplier</b> is the document's own reading (分裂比合计 1.0), so this case
     * pins "the sum is the number the single instance used to carry" rather than any absolute damage - zones, crit and
     * resistance all sit downstream of it.
     */
    @Test
    public void theSegmentsSumToTheWholeMultiplier() {
        Cast cast = castAt(SkillType.SKILL, 10);
        var row = SkillData.init(DAN_HENG, 2).getSkills().get(9);
        double expectedBase = cast.attacker.getAttribute(AttributeType.ATTACK).get() * row.getFirst();

        double sum = cast.segments.stream().mapToDouble(Damage::getSkillBaseValue).sum();
        Assertions.assertEquals(expectedBase, sum, EPS,
                "0.30 + 0.15 + 0.15 + 0.40 of the row's 2.6 is still ATK x 2.6");
        for (int i = 0; i < cast.segments.size(); i++) {
            double ratio = new double[]{0.3, 0.15, 0.15, 0.4}[i];
            Assertions.assertEquals(expectedBase * ratio, cast.segments.get(i).getSkillBaseValue(), EPS,
                    "instance " + (i + 1) + " carries its own fraction of the row");
        }
    }

    // ==================================================================
    // 2. Each instance rolls its own crit - the half the totals cannot see
    // ==================================================================

    /**
     * Every instance rolls crit on its own, so the Skill offers the 「触发暴击时…减速」 clause <b>four</b> chances instead
     * of one.
     *
     * <p>Note: The rolls are forced rather than sampled ({@code alwaysRoll(0.0)} = every roll crits), so "four chances"
     * is measured, not inferred from a probability. The slow then lands as <b>one</b> modifier refreshed four times, not
     * four stacked slows - which is the game's own behaviour for re-applying the same debuff.
     */
    @Test
    public void everySegmentRollsItsOwnCrit() {
        Cast allCrit = castAt(SkillType.SKILL, 10, BattleFixture.alwaysRoll(0.0));
        Assertions.assertEquals(4, allCrit.crits(), 4 + " instances, every roll a crit");

        Cast noCrit = castAt(SkillType.SKILL, 10, BattleFixture.alwaysRoll(1.0));
        Assertions.assertEquals(0, noCrit.crits(), "no roll crits, so the clause gets no chance at all");
        Assertions.assertEquals(4, noCrit.segments.size(), "…and the instances are still four");

        // The consequence in the character's own kit: one slow modifier, refreshed, not four of them.
        Assertions.assertEquals(-0.12, allCrit.attacker == null ? 0 : slowOn(allCrit.enemy), 1e-9,
                "four crits land ONE speed reduction of -12% (a re-application refreshes, it does not stack)");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * The instances a cast settled, plus the battle they settled into.
     */
    private record Cast(Character attacker, Enemy enemy, List<Damage> segments, Random rng) {

        private int crits() {
            return (int) segments.stream().filter(Damage::isCrit).count();
        }
    }

    private static int instancesOf(SkillType slot, int level) {
        return castAt(slot, level).segments.size();
    }

    private static Cast castAt(SkillType slot, int level) {
        return castAt(slot, level, BattleFixture.alwaysRoll(1.0));
    }

    /**
     * Casts the slot at the given level against one big enemy and collects the settled instances.
     */
    private static Cast castAt(SkillType slot, int level, Random rng) {
        Character danHeng = CharacterFactory.create(DAN_HENG, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(5_000_000));
        enemy.heal(5_000_000);
        Battle battle = new Battle(List.of(danHeng), List.of(enemy), rng);

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        SkillExecutor.execute(battle, new com.laosun.aluminium.models.skill.DefaultSkill(DAN_HENG, slotNumber(slot), level),
                danHeng, List.of(enemy));
        return new Cast(danHeng, enemy, settled, rng);
    }

    private static int slotNumber(SkillType slot) {
        return switch (slot) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("no data slot for " + slot);
        };
    }

    /**
     * The speed reduction currently on the enemy, as a single number (0 when nothing is applied).
     */
    private static double slowOn(Enemy enemy) {
        DoubleValue speed = enemy.getAttribute(AttributeType.SPEED);
        double base = speed.baseValue();
        return base == 0 ? 0 : (speed.get() / base) - 1;
    }
}
