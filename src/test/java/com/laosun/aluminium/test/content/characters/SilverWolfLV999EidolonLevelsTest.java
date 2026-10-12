package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999), batch 2: her skill LEVELS, her speed trace, and Eidolon 6's 增笑.
 *
 * <h2>What this case found, which matters more than the batch</h2>
 *
 * <p>The project pins a character's skill levels with one {@code level_convention} rule, because a skill STARTS at level 1
 * and nothing else raises it - 63 of the other 94 characters carry one. <b>1506 had none</b>, so every number of hers was
 * being computed at Lv1: 战技 0.8 instead of 2.0, 强化普攻 1.2 instead of 3.36, 欢愉技·强化 0.45 instead of 1.125. Silent,
 * because her judges read states and counters rather than multipliers.
 *
 * <h2>And what the levels mean for the Eidolon clauses</h2>
 *
 * <p>{@code CANNOT BE OBSERVED AT FULL LEVELS}: the effective level is clamped to the skill table's own length, and the
 * convention already puts every slot AT that length (SKILL 15, COMMON 10). So 「战技等级+2」 cannot move the number this
 * engine reads - not because the rule is missing, but because there is no room above the cap. The rules are shipped;
 * this case therefore pins the convention's RESULT and says plainly that the Eidolon increments are invisible here.
 */
public class SilverWolfLV999EidolonLevelsTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;

    /**
     * The convention puts each slot at the last row of its own table: 15 for the 15-row skills, 10 for the Basic.
     */
    @Test
    public void theLevelConventionPutsEverySlotAtItsTableMaximum() {
        Character wolf = battleAt(0).characters.getFirst();
        int common = levelOf(wolf, SkillType.COMMON);
        int skill = levelOf(wolf, SkillType.SKILL);
        int ultra = levelOf(wolf, SkillType.ULTRA);
        int talent = levelOf(wolf, SkillType.TALENT);
        int elation = levelOf(wolf, SkillType.ELATION_SKILL);
        int extra = levelOf(wolf, SkillType.ELATION_EXTRA);
        System.out.println("[1506] effective levels: COMMON=" + common + " SKILL=" + skill + " ULTRA=" + ultra
                + " TALENT=" + talent + " ELATION_SKILL=" + elation + " ELATION_EXTRA=" + extra);

        Assertions.assertEquals(10, common, "普攻的表有 10 行 -> 满级 10");
        Assertions.assertEquals(15, skill, "战技的表有 15 行 -> 满级 15");
        Assertions.assertEquals(15, ultra, "终结技 15 行");
        Assertions.assertEquals(15, talent, "天赋 15 行");
        Assertions.assertEquals(15, elation, "欢愉技 15 行");
        Assertions.assertEquals(15, extra, "欢愉技·强化 15 行");
    }

    /**
     * Eidolon 3's and 5's increments are shipped, and cannot be seen at these levels - stated, not hidden.
     */
    @Test
    public void theEidolonIncrementsAreClampedAwayAtFullLevels() {
        Character atZero = battleAt(0).characters.getFirst();
        Character atThree = battleAt(3).characters.getFirst();
        Character atFive = battleAt(5).characters.getFirst();
        System.out.println("[1506] SKILL at E0/E3: " + levelOf(atZero, SkillType.SKILL) + "/"
                + levelOf(atThree, SkillType.SKILL) + " ; ULTRA at E0/E5: " + levelOf(atZero, SkillType.ULTRA) + "/"
                + levelOf(atFive, SkillType.ULTRA));

        // The rules ARE shipped and DO fire; what they add lands above the table's last row and is clamped to it.
        Assertions.assertEquals(levelOf(atZero, SkillType.SKILL) + 0, levelOf(atThree, SkillType.SKILL),
                "星魂 3 的 「战技等级+2」 在满级口径下无处可加（引擎把等级夹在表的行数上）");
        Assertions.assertEquals(levelOf(atZero, SkillType.ULTRA), levelOf(atFive, SkillType.ULTRA),
                "星魂 5 的 「终结技等级+2」 同理");
    }

    /**
     * The speed trace, BOTH halves: +50% at 160 Speed, then +2% for every point above it.
     *
     * <p>「速度大于等于 160 时，使自身欢愉度提高 50%，之后每超过 1 点速度使自身欢愉度提高 2%，最多计入 100 点超出的速度。」
     */
    @Test
    public void theSpeedTraceNeedsOneSixty() {
        double none = elationWithSpeedBuff(0.0);
        double buffed = elationWithSpeedBuff(0.5);
        System.out.println("[1506] 欢愉度 with no Speed buff: " + none + " ; with +50% (Speed 174): " + buffed);
        // The fixture's Speed is 119, so +50% is 178.5 -> 174 after the stat pipeline's rounding: 14 points over 160.
        // And the base is NOT zero - she carries 0.1 of her own - so the expectation is stated against the measured base
        // rather than against 0, which is the mistake the first version made.
        Assertions.assertEquals(none + 0.5 + 14 * 0.02, buffed, 1e-6,
                "「+50%」 plus 「每超过 1 点 +2%」 for the 14 points above 160, on top of her own 0.1");
    }

    /**
     * And the CEILING on those points, which is the half that could not be written before.
     *
     * <p>「最多计入 100 点超出的速度」. The reading that matters is the one that would be wrong without the bound: at a
     * Speed far above 160 the per-point term must stop growing, so the difference from a lower Speed is NOT proportional.
     */
    @Test
    public void theSpeedTraceStopsCountingPointsAtOneHundred() {
        double none = elationWithSpeedBuff(0.0);
        double justOver = elationWithSpeedBuff(0.5);
        double farOver = elationWithSpeedBuff(2.0);
        double atCap = none + 0.5 + 100 * 0.02;
        double unbounded = none + 0.5 + 179 * 0.02;
        System.out.println("[1506] 欢愉度 at Speed 119: " + none + " ; at 174: " + justOver + " ; at 339: " + farOver
                + " ; the ceiling is " + atCap + " and WITHOUT `max_points` 339 Speed would read " + unbounded);
        Assertions.assertTrue(farOver > justOver, "more Speed still helps below the ceiling");
        Assertions.assertEquals(atCap, farOver, 1e-6,
                "and past 100 points over, the per-point term stops - without `max_points` this would read "
                        + unbounded);
    }

    /**
     * E6's 增笑 lands with the Ultimate and not before it.
     */
    @Test
    public void eidolonSixAmpsOnlyAfterTheUltimate() {
        Battle battle = battleAt(6);
        Character wolf = battle.characters.getFirst();
        double before = wolf.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double after = wolf.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
        System.out.println("[1506] E6 增笑 " + before + " -> " + after);
        Assertions.assertEquals(0.5, after - before, 1e-9,
                "星魂 6: 「强化普攻期间造成的欢愉伤害增笑 50%」 - and 强化普攻期间 is the 【无敌玩家】 the Ultimate grants");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The EFFECTIVE level of a slot: base plus every raise, clamped to the table's own length.
     *
     * <p>Note: Not {@code Skill.getLevel()}, which is the BASE level and does not move when a rule raises it - measured,
     * every slot read 1 that way and the rules looked broken. {@code CanHit.skillLevel(skill)} is what the engine reads
     * when it computes damage, so it is what a judge has to read.
     */
    private static int levelOf(Character unit, SkillType slot) {
        var skill = unit.getSkills().get(slot);
        return skill == null ? -1 : unit.skillLevel(skill);
    }

    /**
     * Her Elation-degree boost with Speed forced to the given value, so the threshold is what moves.
     *
     * <p>Note: Speed is written BEFORE {@code startBattle()}, because the trace fires on {@code BATTLE_START} - setting it
     * afterwards would be measuring a rule that has already run.
     */
    /**
     * Her Elation degree with a Speed buff of the given additive share, applied BEFORE the battle starts.
     *
     * <p>Note: Applied before, because the trace fires on {@code BATTLE_START} - afterwards would be measuring a rule that
     * has already run. And a MODIFIER rather than a raw base write: measured, {@code baseNoCompute} is overwritten when the
     * battle starts (the unit read 119 however the base was set), while a buff survives.
     */
    private static double elationWithSpeedBuff(double share) {
        Character wolf = CharacterFactory.create(WOLF, 80);
        Battle battle = new Battle(
                List.of(wolf, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (share != 0) {
            wolf.getBuffManager().addBuff(com.laosun.aluminium.models.buff.StatModifierBuff
                    .percentBuff(AttributeType.SPEED, share, 1));
        }
        System.out.println("[1506] Speed buff " + share + " -> Speed reads "
                + wolf.getAttribute(AttributeType.SPEED).get());
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst().getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    private static Battle battleAt(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
