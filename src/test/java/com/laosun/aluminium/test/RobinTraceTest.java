package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1309 知更鸟 (Robin): the first character whose <b>行迹 extra abilities</b> are wired, and the first
 * test of "one character, several mechanics" (her talent was already authored in the same file).
 *
 * <p><b>Why this class is the extensibility evidence, not just a character test.</b> Both rules
 * added for her use events and ops that already existed — {@code BATTLE_START}, {@code SKILL_CAST},
 * {@code ADVANCE}, {@code GAIN_ENERGY} — and a condition the DSL already had ({@code actor == self}).
 * So what is being pinned is not "the engine learned Robin" but "content can change a battle without
 * engine code", which is the claim the whole trigger-table design rests on. If a rule needs an op the
 * engine lacks, it is rejected at <b>load</b> time, so the failure mode is never a silent no-op.
 *
 * <p><b>How the assertions are made mutation-proof.</b> Every measurement is taken twice: once with
 * her real table, once with the <b>same character</b> (same cid, level, seed, roster) whose table has
 * been emptied. A number that only holds because a rule exists then cannot pass by accident — delete
 * the rule and the two sides become equal. The one absolute assertion is 0 (an ally's skill must not
 * pay her), which is the guardrail for {@code actor == self}.
 */
public class RobinTraceTest {
    private static final int ROBIN = 1309;
    /**
     * 1202 Tingyun (停云): the same path (harmony) and level, but no rule file of her own — so she is a
     * real character acting as the control, not a mock. Her skill and basic attack both deal no damage
     * beyond the basic attack itself, which is what makes the absolute assertions below safe.
     */
    private static final int CONTROL = 1202;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * 「战斗开始时，自身行动提前25%」 — 25% of the wait still ahead, not 25% of the round.
     */
    @Test
    public void battleStartAdvancesHerByAQuarterOfTheRemainingWait() {
        double withTrace = remainingAtBattleStart(true);
        double withoutTrace = remainingAtBattleStart(false);
        Assertions.assertTrue(withoutTrace > 0, "precondition: she has a wait to shorten at battle start");
        Assertions.assertTrue(withTrace < withoutTrace, "the trace must pull her forward");
        Assertions.assertEquals(0.75 * withoutTrace, withTrace, 1e-6,
                "ADVANCE skips a fraction of the REMAINING wait (Queue.advanceActionByPercent), "
                        + "and it is the owner who is advanced -- the other three are untouched");
    }

    /**
     * 「施放战技时额外恢复5点能量」 — her own skill, and only her own.
     *
     * <p>The absolute numbers are not 0/5: the skill <b>action</b> itself already credits the caster
     * 30 energy irrespective of any trigger rule (it is credited to whoever cast, which is why the
     * ally case below leaves her at 0). That 30 is therefore not asserted here — it belongs to the
     * energy model, not to this trace. What is asserted is the difference the rule makes, which is
     * exactly the word 「额外」 in the text.
     */
    @Test
    public void castingHerOwnSkillGrantsTheExtraFiveEnergy() {
        double withTrace = robinEnergyAfterSkillCast(true, 0);
        double withoutTrace = robinEnergyAfterSkillCast(false, 0);
        Assertions.assertTrue(withoutTrace > 0, "precondition: the skill action credits the caster by itself");
        Assertions.assertEquals(5.0, withTrace - withoutTrace, EPS,
                "the trace is ADDITIONAL to the action's own energy credit");
    }

    /**
     * The load-bearing condition: {@code SKILL_CAST} reaches the whole side, so without
     * {@code actor == self} an ally's skill would feed Robin too.
     */
    @Test
    public void anAllysSkillDoesNotFeedHer() {
        Assertions.assertEquals(0.0, robinEnergyAfterSkillCast(true, 1), EPS,
                "「施放战技时」 means HER skill -- drop `actor == self` and this becomes 5");
    }

    /**
     * 「施放战技时」 must not fire on a <b>basic attack</b>.
     *
     * <p>This is the test that found the bug rather than pinning a known behaviour: {@code SKILL_CAST}
     * used to mean "any cast that is not an ultimate" (the emitter only split ultimate from everything
     * else), so her own basic attacks were paying her the extra 5 as well. Her relic-109 sibling —
     * 「施放战技时攻击力提高20%」 — had the same over-trigger, shipped, with a note claiming the
     * emitter's split already handled it.
     */
    @Test
    public void herOwnBasicAttackIsNotASkillCast() {
        double withTrace = robinEnergyAfterSkillCast(true, 0, SkillType.COMMON);
        double withoutTrace = robinEnergyAfterSkillCast(false, 0, SkillType.COMMON);
        Assertions.assertEquals(0.0, withTrace - withoutTrace, EPS,
                "「施放战技时」 is the Skill slot, not 普攻: a basic attack must not feed her the extra 5");
    }

    /**
     * Composition: her talent (authored earlier, {@code ALLY_ATTACK} → +2) still works next to the
     * new rules, and a basic attack is <b>not</b> a skill cast, so the +5 must not leak into it.
     */
    @Test
    public void herEarlierTalentStillFiresAndABasicAttackIsNotASkillCast() {
        Assertions.assertEquals(2.0, robinEnergyAfterSkillCast(true, 1, SkillType.COMMON), EPS,
                "talent: 我方目标攻击敌方目标后 → +2 energy (flat per attack, not per target)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * @param keepRules {@code false} = the same character with an emptied table, the baseline that
     *                  makes every assertion above a difference rather than a coincidence
     */
    private static Battle battleWithRobin(boolean keepRules) {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character control = CharacterFactory.create(CONTROL, LEVEL);
        if (!keepRules) {
            robin.setTriggerTable(new TriggerTable(ROBIN, List.of()));
        }
        Battle battle = new Battle(List.of(robin, control), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static double remainingAtBattleStart(boolean keepRules) {
        Battle battle = battleWithRobin(keepRules);
        Character robin = battle.characters.getFirst();
        return battle.queue.snapshot().stream()
                .filter(signal -> signal.getCanHit() == robin)
                .findFirst()
                .map(battle.queue::getTimeRemaining)
                .orElseThrow(() -> new AssertionError("Robin is not on the action bar"));
    }

    /**
     * Robin's energy gain caused by {@code actorIndex}'s action.
     *
     * @param actorIndex 0 = Robin herself, 1 = the ally
     */
    private static double robinEnergyAfterSkillCast(boolean keepRules, int actorIndex) {
        return robinEnergyAfterSkillCast(keepRules, actorIndex, SkillType.SKILL);
    }

    private static double robinEnergyAfterSkillCast(boolean keepRules, int actorIndex, SkillType slot) {
        Battle battle = battleWithRobin(keepRules);
        Character robin = battle.characters.getFirst();
        Character actor = battle.characters.get(actorIndex);
        double before = robin.getCurrentEnergy();
        Skill skill = actor.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: the actor carries the " + slot + " slot");
        battle.castImmediate(skill, actor, List.of(battle.enemies.getFirst()));
        return robin.getCurrentEnergy() - before;
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 100_000, 100, 100, 100);
    }
}
