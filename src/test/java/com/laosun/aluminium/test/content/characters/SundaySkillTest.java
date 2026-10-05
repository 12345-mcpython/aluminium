package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Sunday's (1313) Skill "纸醉金迷" (131302) - the shipped content the two conditions here were built for.
 *
 * <p><b>The document, and what each clause needed.</b> "使指定我方单体角色<b>及其召唤物</b>立即行动，并使其造成的伤害提高
 * #2[i]%，若目标拥有召唤物，则造成的伤害提高效果额外提高 #4[i]%，持续 #3[i]回合。 ...当星期日对"同谐"命途的角色施放该
 * 技能时，<b>无法触发</b>立即行动效果。" - the Skill names the chosen ally and its memosprite, the ally's own damage
 * share, and the exception that a Harmony path target is not advanced.
 * <ul>
 *  <li>"及其召唤物" is the {@code target_and_summon} selector: the chosen ally <b>and its</b>
 *       memosprite, not the caster's;</li>
 *  <li>"若目标拥有召唤物" is {@code target_summon_count} - the same question relic 12 asks about the wearer,
 *       asked about the ally;</li>
 *  <li>"对同谐 ...无法触发" is the {@code !} prefix on {@code target has_path 同谐} - an <b>exception</b>, and the
 *       reason a negation exists at all (the DSL's condition list is an AND).</li>
 * </ul>
 *
 * <p><b>What is pinned here.</b> That the ally AND its memosprite act; that a Harmony (同谐) target does <b>not</b> act
 * (but still gets the damage share, because the sentence only blocks the advance); that the share is the
 * document's own arithmetic - 15%, or 15% + 25% = 40% when the target has a summon - for 2 turns; and that the
 * file really carries three clauses (a missing one would show up in one of the cases above).
 *
 * <p>Note: Not authored from this Skill: "对[蒙福者]施放战技后恢复1个技能点" (casting the Skill on the Beatified
 * returns one Skill Point), which needs his ultimate's state.
 */
public class SundaySkillTest {
    private static final double EPS = 1e-6;

    private static final int SUNDAY = 1313;
    /** Himeko - Erudition (智识), so the advance happens; and she owns no summon, which is the 15% side. */
    private static final int ERUDITION_ALLY = 1003;
    /** Robin - Harmony (同谐), so the advance must <b>not</b> happen. */
    private static final int HARMONY_ALLY = 1309;
    /** Aglaea - Remembrance (记忆) and a memosprite owner, so she is the "target has a summon" side. */
    private static final int MEMOSPRITE_ALLY = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** His Skill's slot in {@code skills.json}. */
    private static final int SKILL_SLOT = 2;
    /** His ultimate's slot. */
    private static final int ULTIMATE_SLOT = 3;
    /** Himeko's CRIT DMG with no buffs - she is the fixture the derived value is measured against. */
    private static final double HARMONY_BARE_CRIT_DMG =
            CharacterFactory.create(ERUDITION_ALLY, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();

    // ==================================================================
    // 1. "指定我方单体及其召唤物立即行动"
    // ==================================================================

    /** The chosen ally and its memosprite both act now - the pair, not just the ally. */
    @Test
    public void theChosenAllyAndItsSummonActImmediately() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon evey = battle.summonMemosprite(ally);
        battle.processRequests();
        double sundayBefore = timeRemaining(battle, sunday);

        cast(battle, sunday, ally);

        Assertions.assertEquals(0, timeRemaining(battle, ally), EPS, "「指定我方单体角色…立即行动」");
        Assertions.assertEquals(0, timeRemaining(battle, evey), EPS, "「及其召唤物」 -- the pair moves together");
        Assertions.assertEquals(sundayBefore, timeRemaining(battle, sunday), EPS,
                "the caster is not part of the pair");
    }

    /**
     * Harmony (同谐) gets no advance - the sentence's exception, and the half a positive spelling could not express.
     *
     * <p>Robin and Sunday himself are both Harmony, so without the gate this rule would push exactly the units the
     * sentence excludes (and self-cast would push him too).
     */
    @Test
    public void aHarmonyTargetIsNotAdvanced() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character harmony = CharacterFactory.create(HARMONY_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, harmony), List.of(dummy()), new Random(0));
        battle.startBattle();
        double harmonyBefore = timeRemaining(battle, harmony);

        cast(battle, sunday, harmony);

        Assertions.assertEquals(harmonyBefore, timeRemaining(battle, harmony), EPS,
                "「当星期日对「同谐」命途的角色施放该技能时，无法触发立即行动效果」");
    }

    /** ...but the damage share is a different clause, and it is granted to a Harmony (同谐) target all the same. */
    @Test
    public void theShareIsStillGrantedToAHarmonyTarget() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character harmony = CharacterFactory.create(HARMONY_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, harmony), List.of(dummy()), new Random(0));
        battle.startBattle();

        cast(battle, sunday, harmony);

        Assertions.assertEquals(0.15, damageBoostOf(harmony), EPS,
                "only 立即行动 is blocked by the sentence, not 「使其造成的伤害提高」");
    }

    // ==================================================================
    // 2. The share: 15%, or 40% when the target has a summon
    // ==================================================================

    /** The document's own arithmetic: #2 alone, or #2 + #4 when the target has a memosprite, for #3 turns. */
    @Test
    public void theShareIsFifteenPercentWithoutASummonAndFortyWith() {
        Assertions.assertEquals(0.15, boostAfterCastAt(ERUDITION_ALLY, false), EPS,
                "「并使其造成的伤害提高#2[i]%」 -- #2 = 0.15 at Lv1");
        Assertions.assertEquals(0.40, boostAfterCastAt(MEMOSPRITE_ALLY, true), EPS,
                "「若目标拥有召唤物，则…额外提高#4[i]%」 -- 0.15 + 0.25, the sum the target ends up with");
    }

    /**  ...and it lasts the 2 turns the row says (#3), not a number this file invented. */
    @Test
    public void theShareLastsTheStatedTurns() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();

        cast(battle, sunday, ally);

        List<StatModifierBuff> buffs = ally.getBuffManager().allBuffsOf(StatModifierBuff.class);
        Assertions.assertEquals(1, buffs.size(), "one modifier, not two: the two clauses are disjoint rules");
        Assertions.assertEquals(2, buffs.getFirst().duration(), "「持续#3[i]回合」 -- #3 = 2");
    }

    // ==================================================================
    // 2b. The [蒙福者] kit: his ultimate's clause and the state it makes
    // ==================================================================

    /** The state and the CRIT DMG it carries land on the ally and its summon - and only on the newest target. */
    @Test
    public void theUltimateBlessesTheTargetAndItsSummon() {
        Battle battle = ultimateBattle(MEMOSPRITE_ALLY, null);
        Character sunday = battle.characters.getFirst();
        Character first = battle.characters.get(1);

        Assertions.assertTrue(first.getBuffManager().hasState("蒙福者"), "「使目标及其召唤物成为【蒙福者】」");
        Assertions.assertEquals(0.12 * critDamageOf(sunday) + 0.08, critDamageOf(first) - HARMONY_BARE_CRIT_DMG, EPS,
                "「提高数值等同于星期日#2%暴击伤害+#4%」 -- his own number, not the target's");

        //  ...and casting it on somebody new takes it off the previous holder: "仅对 ...最新的施放目标生效".
        Character second = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle next = new Battle(List.of(sunday, first, second), List.of(dummy()), new Random(0));
        next.startBattle();
        next.stepForward();
        next.beforeMove();
        next.afterMove();                                  // Sunday's turn comes first (96 speed vs 112)
        Assertions.assertTrue(first.getBuffManager().hasState("蒙福者"), "precondition: still on the first ally");

        castUltimate(next, sunday, second);

        Assertions.assertFalse(first.getBuffManager().hasState("蒙福者"),
                "「仅对…最新的施放目标生效」 -- the removal is what says that, there is no holder flag");
        Assertions.assertTrue(second.getBuffManager().hasState("蒙福者"));
    }

    /**
     * "恢复等同于#1[f1]%能量上限的能量": 20% of the <b>ally's</b> maximum energy, not a flat number.
     *
     * <p>Note: Measured with the clamp spelled out: the grant is the stated share, capped by the bar. The maximum
     * differs per character (120/130/140 across the roster), so a flat number would be wrong for all of them - which
     * is the whole reason the scale exists.
     */
    @Test
    public void theUltimateRestoresAShareOfTheAllysMaxEnergy() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double before = ally.getCurrentEnergy();
        double max = ally.getMaxEnergy();

        castUltimate(battle, sunday, ally);

        Assertions.assertEquals(Math.min(0.2 * max, max - before), ally.getCurrentEnergy() - before, EPS,
                "20% of HER maximum (" + max + "), not of his, and not a literal");
    }

    /** His death takes the state off the ally: "当星期日陷入无法战斗状态时，[蒙福者]效果也会被解除" (the state is
     * released when Sunday is knocked out).
     */
    @Test
    public void hisDeathTakesTheStateOff() {
        Battle battle = ultimateBattle(MEMOSPRITE_ALLY, null);
        Character sunday = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Assertions.assertTrue(ally.getBuffManager().hasState("蒙福者"), "precondition");

        sunday.takeDamage(9_999_999);
        battle.processRequests();

        Assertions.assertTrue(sunday.isDeath(), "precondition: he is down");
        Assertions.assertFalse(ally.getBuffManager().hasState("蒙福者"),
                "the state's clock was HIS turns -- once he is gone there is no clock, so it goes with him");
    }

    /** The state's duration is spent by <b>his</b> turns, not by the ally's. */
    @Test
    public void theStateIsSpentByHisTurns() {
        Battle battle = ultimateBattle(ERUDITION_ALLY, null);
        Character sunday = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        takeTurn(battle, ally);
        Assertions.assertEquals(3, blessingDuration(ally), "her turn does not spend it");

        takeTurn(battle, sunday);
        Assertions.assertEquals(2, blessingDuration(ally), "his does");
    }

    /** Casting the Skill on the [蒙福者] gives the skill point back -- the sentence's last clause. */
    @Test
    public void theSkillRefundsItsPointOnTheBlessed() {
        Battle battle = ultimateBattle(ERUDITION_ALLY, null);
        Character sunday = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Assertions.assertTrue(ally.getBuffManager().hasState("蒙福者"), "precondition: he is the blessed one");
        drainSkillPoints(battle);

        cast(battle, sunday, ally);

        Assertions.assertEquals(1, battle.getSkillPoints(),
                "「对【蒙福者】施放战技后恢复1个战技点」 -- the point the skill cost came back");
    }

    // ==================================================================
    // 3. The file
    // ==================================================================

    /**
     * The three clauses are three rules - and the count is what makes the cases above evidence.
     *
     * <p>They cannot be read back individually the way relic (遗器) rules are ({@code matching} only returns rules whose
     * conditions hold, and the two share-clauses are deliberately disjoint), so this pins the shape and the
     * behaviour above pins the numbers.
     */
    @Test
    public void theAuthoredSkillIsThreeClauses() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);

        Assertions.assertEquals(4, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.SKILL_CAST),
                "advance (unless Harmony) + share without a summon + share with one + the 【蒙福者】 refund");
        Assertions.assertEquals(1, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.ULT_CAST),
                "the 【蒙福者】 clause; the energy restore is registered separately");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** How much damage boost the ally carries after Sunday casts his Skill on it. */
    private static double boostAfterCastAt(int allyCid, boolean withMemosprite) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        if (withMemosprite) {
            battle.summonMemosprite(ally);
            battle.processRequests();
        }

        cast(battle, sunday, ally);

        return damageBoostOf(ally);
    }

    /** A real cast of his Skill, aimed at {@code ally} - the path that carries "指定" as the event's target. */
    private static void cast(Battle battle, Character sunday, Character ally) {
        battle.castImmediate(new DefaultSkill(SUNDAY, SKILL_SLOT, 1), sunday, List.of(ally));
    }

    private static double damageBoostOf(CanHit unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /** How much action value the unit still has - zero means "acts now". */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    /**
     * A battle where Sunday has already cast his ultimate at {@code allyCid}.
     *
     * <p>Note: The ally is made <b>faster</b> than him, deliberately: the case below has to observe "her turn, and his
     * turn has not happened yet", and Sunday's own speed ties with Himeko's - a tie is an arbitrary order, so the
     * window could contain his turn and the measurement would read two ticks as one (it did: 2 instead of 3).
     */
    private static Battle ultimateBattle(int allyCid, Character ignored) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        ally.setAttribute(AttributeType.SPEED, new DoubleValue(150));
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        castUltimate(battle, sunday, ally);
        return battle;
    }

    private static void castUltimate(Battle battle, Character sunday, Character ally) {
        battle.castImmediate(new DefaultSkill(SUNDAY, ULTIMATE_SLOT, 1), sunday, List.of(ally));
    }

    /** The CRIT DMG of the [蒙福者] state's modifier, or {@code -1} when it is not attached. */
    private static int blessingDuration(CanHit who) {
        for (StatModifierBuff buff : who.getBuffManager().allBuffsOf(StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.CRIT_ATTACK) {
                return buff.duration();
            }
        }
        return -1;
    }

    private static double critDamageOf(CanHit who) {
        return who.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
    }

    /** Runs the queue up to {@code who}'s turn and settles both boundaries of it. */
    private static void takeTurn(Battle battle, Character who) {
        for (int guard = 0; guard < 60; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue.getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
                battle.afterMove();
                return;
            }
            battle.afterMove();
        }
        throw new AssertionError("no turn for the requested unit within 60 steps");
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
