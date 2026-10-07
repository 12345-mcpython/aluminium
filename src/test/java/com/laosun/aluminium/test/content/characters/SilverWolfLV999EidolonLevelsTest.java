package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

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
    private static final int MONSTER = 1002011;

    /** The convention puts each slot at the last row of its own table: 15 for the 15-row skills, 10 for the Basic. */
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

    /** Eidolon 3's and 5's increments are shipped, and cannot be seen at these levels - stated, not hidden. */
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

    /** The speed trace: at 160 Speed or more she gains 50% Elation on top of what she already has. */
    @Test
    public void theSpeedTraceNeedsOneSixty() {
        double above = elationWithSpeedBuff(0.5);
        double none = elationWithSpeedBuff(0.0);
        System.out.println("[1506] 欢愉度 with a +50% Speed buff: " + above + " ; with none: " + none);
        Assertions.assertEquals(0.5, above - none, 1e-9,
                "行迹 假结局速通攻略: 「速度大于等于 160 时，使自身欢愉度提高 50%」 (base 119 +50% = 178.5, past the gate)");
    }

    /** E6's 增笑 lands with the Ultimate and not before it. */
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
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
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
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
