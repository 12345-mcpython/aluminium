package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.DoubleValue;

/**
 * Sunday's (1313) Skill "纸醉金迷" (131302) - the shipped content the two conditions here were built for - together with the
 * support half that rides the same casts: the talent's CRIT Rate, the two traces that are exact, the 秘技 and the trace's
 * energy floor.
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
 *
 * <p><b>The talent and the traces.</b> 天赋「施放战技时，使目标的暴击率提高 20%，持续 3 回合」 - the ally gains it, he does
 * not. 行迹 崇高拂尘「战斗开始时，星期日恢复 25 点能量」 pays him at battle start, and 行迹 掌中安港「施放战技时，解除目标的 1 个
 * 负面效果」 takes exactly one debuff off the Skill's target.
 *
 * <p><b>The 秘技.</b> 荣光之秘:「使用秘技后，下一次战斗星期日首次对我方目标施放技能时，使目标造成的伤害提高 50%，持续 2
 * 回合」. 「首次」 has no rule-level switch (a per-turn one exists; a per-battle first does not), so the rule carries a marker
 * state laid at BATTLE_START and spends it with the first cast — and the case below is what proves the marker is spent.
 *
 * <p><b>The energy floor.</b> 行迹 主日渴慕:「施放终结技时，若为目标恢复的能量不足 40 点，恢复的能量提高至 40 点」 - a floor
 * stated in POINTS, which is why the engine grew a flat spelling for {@code RAISE_ENERGY_TO} (it only took a share of the
 * maximum before). The ally there has a small enough bar that 20% of it is under 40, so the floor is the only way the
 * reading can reach 40.
 *
 * <p><b>L3 consolidation.</b> Cases that shared one fixture, one clause and one observed object are now ONE scenario test,
 * their assertions inside {@link Assertions#assertAll} with a short tag naming the clause; every expected value,
 * tolerance and message is the one the separate case carried, and no exception path was folded into a positive one.
 */
public class SundayTest {
    private static final double EPS = 1e-6;

    private static final int SUNDAY = 1313;
    /**
     * Himeko - Erudition (智识), so the advance happens; and she owns no summon, which is the 15% side.
     */
    private static final int ERUDITION_ALLY = 1003;
    /**
     * Robin - Harmony (同谐), so the advance must <b>not</b> happen.
     */
    private static final int HARMONY_ALLY = 1309;
    /**
     * Aglaea - Remembrance (记忆) and a memosprite owner, so she is the "target has a summon" side.
     */
    private static final int MEMOSPRITE_ALLY = 1402;
    /**
     * A party member with no summon of its own - the plain ally the talent, the traces and the 秘技 are read on.
     */
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    /**
     * His Skill's slot in {@code skills.json}.
     */
    private static final int SKILL_SLOT = 2;
    /**
     * His ultimate's slot.
     */
    private static final int ULTIMATE_SLOT = 3;
    /**
     * Himeko's CRIT DMG with no buffs - she is the fixture the derived value is measured against.
     */
    private static final double HARMONY_BARE_CRIT_DMG =
            CharacterFactory.create(ERUDITION_ALLY, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();
    /**
     * The tolerance the flat point readings use (the energy grant is an exact amount, so this one is tighter than
     * {@link #EPS}).
     */
    private static final double EXACT_EPS = 1e-9;

    // ==================================================================
    // 1. "指定我方单体及其召唤物立即行动"
    // ==================================================================

    /**
     * The chosen ally and its memosprite both act now - the pair, not just the ally.
     */
    @Test
    public void theChosenAllyAndItsSummonActImmediately() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        Summon evey = battle.summonMemosprite(ally);
        battle.processRequests();
        double sundayBefore = timeRemaining(battle, sunday);

        cast(battle, sunday, ally);

        Assertions.assertEquals(0, timeRemaining(battle, ally), EPS, "「指定我方单体角色…立即行动」 (a designated single ally ... acts immediately)");
        Assertions.assertEquals(0, timeRemaining(battle, evey), EPS, "「及其召唤物」 (and its summons) -- the pair moves together");
        Assertions.assertEquals(sundayBefore, timeRemaining(battle, sunday), EPS,
                "the caster is not part of the pair");
    }

    /**
     * Harmony (同谐) gets no advance - the sentence's exception, and the half a positive spelling could not express - while
     * the damage share, which is a different clause of the same sentence, is granted all the same.
     *
     * <p>⭐ One scenario: one board ({@code sunday + harmony}), one cast, one observed ally; the two halves are the
     * sentence's own contrast ("only the immediate action is blocked"), so a case for each would read the same cast
     * twice.
     *
     * <p>Robin and Sunday himself are both Harmony, so without the gate this rule would push exactly the units the
     * sentence excludes (and self-cast would push him too).
     */
    @Test
    public void aHarmonyTargetGetsTheShareButNotTheAdvance() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character harmony = CharacterFactory.create(HARMONY_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, harmony), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        double harmonyBefore = timeRemaining(battle, harmony);

        cast(battle, sunday, harmony);

        Assertions.assertAll("「对同谐…无法触发立即行动」: the advance is blocked, the share is not",
                () -> Assertions.assertEquals(harmonyBefore, timeRemaining(battle, harmony), EPS,
                        "[no advance] 「当星期日对「同谐」命途的角色施放该技能时，无法触发立即行动效果」 (when Sunday casts this Skill on a character of the Harmony (「同谐」) Path, the immediate-action effect cannot be triggered)"),
                () -> Assertions.assertEquals(0.15, damageBoostOf(harmony), EPS,
                        "[the share] only the immediate action (立即行动) is blocked by the sentence, not the DMG boost (「使其造成的伤害提高」)"));
    }

    // ==================================================================
    // 2. The share: 15%, or 40% when the target has a summon
    // ==================================================================

    /**
     * The document's own arithmetic - #2 alone, or #2 + #4 when the target has a memosprite - and the #3 turns it
     * lasts, all read on the ally's damage-share modifier.
     *
     * <p>⭐ One scenario: the value, the count and the duration are the same object (the ally's
     * {@code ALL_DAMAGE_TYPE_BOOST} modifier after one Skill cast), and {@code boostAfterCastAt} is the same fixture the
     * no-summon and with-summon readings share; the separate cases were already reading the same two casts.
     */
    @Test
    public void theSkillShareIsFifteenPercentOrFortyWithASummonForTwoTurns() {
        double withoutSummon = boostAfterCastAt(ERUDITION_ALLY, false);
        double withSummon = boostAfterCastAt(MEMOSPRITE_ALLY, true);

        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();

        cast(battle, sunday, ally);

        // The talent now ALSO puts a CRIT_CHANCE modifier on the same ally (2026-11-06), so read the damage share by
        // attribute: counting every modifier would make this assertion about the whole kit, not about the share.
        List<StatModifierBuff> buffs = ally.getBuffManager().allBuffsOf(StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == com.laosun.aluminium.enums.AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .toList();
        Assertions.assertAll("「并使其造成的伤害提高#2[i]%…额外提高#4[i]%…持续#3[i]回合」",
                () -> Assertions.assertEquals(0.15, withoutSummon, EPS,
                        "[#2 alone] 「并使其造成的伤害提高#2[i]%」 (and raises its DMG dealt by #2[i]%) -- #2 = 0.15 at Lv1"),
                () -> Assertions.assertEquals(0.40, withSummon, EPS,
                        "[#2 + #4] 「若目标拥有召唤物，则…额外提高#4[i]%」 (if the target has a summon, ... raises it by an extra #4[i]%) -- 0.15 + 0.25, the sum the target ends up with"),
                () -> Assertions.assertEquals(1, buffs.size(),
                        "[one modifier] one damage-share modifier, not two: the two clauses are disjoint rules"),
                () -> Assertions.assertEquals(2, buffs.getFirst().duration(),
                        "[#3 turns] \"lasts #3[i] turns\" (「持续#3[i]回合」) -- #3 = 2"));
    }

    // ==================================================================
    // 2b. The [蒙福者] kit: his ultimate's clause and the state it makes
    // ==================================================================

    /**
     * The state and the CRIT DMG it carries land on the ally and its summon - and only on the newest target.
     */
    @Test
    public void theUltimateBlessesTheTargetAndItsSummon() {
        Battle battle = ultimateBattle(MEMOSPRITE_ALLY, null);
        Character sunday = battle.characters().getFirst();
        Character first = battle.characters().get(1);

        Assertions.assertTrue(first.getBuffManager().hasState("char:1313/blessed_one"), "「使目标及其召唤物成为【char:1313/blessed_one】」 (makes the target and its summons The Beatified (【char:1313/blessed_one】))");
        Assertions.assertEquals(0.12 * critDamageOf(sunday) + 0.08, critDamageOf(first) - HARMONY_BARE_CRIT_DMG, EPS,
                "「提高数值等同于星期日#2%暴击伤害+#4%」 (raises by an amount equal to #2% of Sunday's CRIT DMG + #4%) -- his own number, not the target's");

        //  ...and casting it on somebody new takes it off the previous holder: "仅对 ...最新的施放目标生效".
        Character second = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle next = new Battle(List.of(sunday, first, second), List.of(dummy()), BattleFixture.seeded());
        next.startBattle();
        next.stepForward();
        next.beforeMove();
        next.afterMove();                                  // Sunday's turn comes first (96 speed vs 112)
        Assertions.assertTrue(first.getBuffManager().hasState("char:1313/blessed_one"), "precondition: still on the first ally");

        castUltimate(next, sunday, second);

        Assertions.assertFalse(first.getBuffManager().hasState("char:1313/blessed_one"),
                "「仅对…最新的施放目标生效」 (applies only to ... the most recent cast target) -- the removal is what says that, there is no holder flag");
        Assertions.assertTrue(second.getBuffManager().hasState("char:1313/blessed_one"));
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
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        double before = ally.getCurrentEnergy();
        double max = ally.getMaxEnergy();

        castUltimate(battle, sunday, ally);

        // ⚠ Since 2026-11-06 the trace 主日渴慕 lifts this to a 40-POINT floor, so the reading is the larger of the two.
        Assertions.assertEquals(Math.min(Math.max(0.2 * max, 40), max - before), ally.getCurrentEnergy() - before, EPS,
                "20% of HER maximum (" + max + ") lifted to the trace's 40-point floor, not his maximum");
    }

    /**
     * His death takes the state off the ally: "当星期日陷入无法战斗状态时，[蒙福者]效果也会被解除" (the state is
     * released when Sunday is knocked out).
     */
    @Test
    public void hisDeathTakesTheStateOff() {
        Battle battle = ultimateBattle(MEMOSPRITE_ALLY, null);
        Character sunday = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Assertions.assertTrue(ally.getBuffManager().hasState("char:1313/blessed_one"), "precondition");

        sunday.takeDamage(9_999_999);
        battle.processRequests();

        Assertions.assertTrue(sunday.isDeath(), "precondition: he is down");
        Assertions.assertFalse(ally.getBuffManager().hasState("char:1313/blessed_one"),
                "the state's clock was HIS turns -- once he is gone there is no clock, so it goes with him");
    }

    /**
     * The state's duration is spent by <b>his</b> turns, not by the ally's - and the Skill cast on its holder gives the
     * point it costs back.
     *
     * <p>⭐ One scenario: both readings are the [蒙福者] state's own consequences on the one board
     * {@code ultimateBattle(ERUDITION_ALLY, null)} builds, so the second cast is the board the refund case already
     * needed, not another fixture.
     */
    @Test
    public void theStateIsSpentByHisTurnsAndTheSkillPaysThePointBack() {
        Battle battle = ultimateBattle(ERUDITION_ALLY, null);
        Character sunday = battle.characters().getFirst();
        Character ally = battle.characters().get(1);

        takeTurn(battle, ally);
        int afterHerTurn = blessingDuration(ally);
        takeTurn(battle, sunday);
        int afterHisTurn = blessingDuration(ally);

        Assertions.assertTrue(ally.getBuffManager().hasState("char:1313/blessed_one"), "precondition: he is the blessed one");
        drainSkillPoints(battle);

        cast(battle, sunday, ally);

        Assertions.assertAll("「[char:1313/blessed_one]」: his turns spend it, and the Skill on the holder pays its point back",
                () -> Assertions.assertEquals(3, afterHerTurn, "[her turn] her turn does not spend it"),
                () -> Assertions.assertEquals(2, afterHisTurn, "[his turn] his does"),
                () -> Assertions.assertEquals(1, battle.getSkillPoints(),
                        "[the refund] 「对【char:1313/blessed_one】施放战技后恢复1个战技点」 (restores 1 Skill Point after casting the Skill on The Beatified) -- the point the skill cost came back"));
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

        // Note: 8 since 2026-11-06 - the seven below plus 星魂 1 (the pair's DEF ignore, also on SKILL_CAST).
        // technique's first-ability share (all three also on SKILL_CAST); they are judged by the support cases below.
        // Note: 9 since 2026-11-06 - the eight below plus 星魂 6's talent variant (also on SKILL_CAST).
        Assertions.assertEquals(9, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.SKILL_CAST),
                "advance (unless Harmony) + share without a summon + share with one + the refund"
                        + " + the talent's crit rate + the trace's dispel + the technique's first-ability share"
                        + " + 星魂 1's defence ignore + 星魂 6's talent variant");
        Assertions.assertEquals(4, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.ULT_CAST),
                "the 【char:1313/blessed_one】 clause + the 40-point energy floor + 星魂 2 two rules");
    }

    // ==================================================================
    // 4. The talent and the traces: the target's CRIT Rate, the whisk, the harbour
    // ==================================================================

    /**
     * The support half that rides the same cast: 天赋「施放战技时，使目标的暴击率提高 20%，持续 3 回合」 gives the ally the rate
     * and not him, and 行迹 掌中安港「施放战技时，解除目标的 1 个负面效果」 takes exactly one debuff off the same ally.
     *
     * <p>⭐ One scenario: one board ({@code sunday + 1204}, the dummy), one Skill cast, both clauses read on the same
     * ally afterwards; the debuffs are laid before the cast and the CRIT Rate is read before it, so neither reading
     * moves the other.
     */
    @Test
    public void theSupportHalfOfTheCastRaisesTheCritRateAndDispelsOneDebuff() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // Real debuffs: a bare StateBuff is not counted by debuffCount (it carries no debuff role), so the first version
        // of this test read 0 before and 0 after and proved nothing.
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.percentDebuff(
                AttributeType.SPEED, -0.2, 3));
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.percentDebuff(
                AttributeType.DEFENCE, -0.2, 3));
        int debuffsBefore = ally.getBuffManager().debuffCount();
        double critBefore = ally.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertAll("the Skill's support half: the talent's CRIT Rate and the harbour's one dispel",
                () -> Assertions.assertEquals(2, debuffsBefore, "[precondition] two debuffs to take one from"),
                () -> Assertions.assertEquals(critBefore + 0.2, ally.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                        "[the talent] 「使目标的暴击率提高 20%（Lv10）」"),
                () -> Assertions.assertEquals(0.05, sunday.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                        "[not himself] …and not on himself: the sentence says 目标"),
                () -> Assertions.assertEquals(debuffsBefore - 1, ally.getBuffManager().debuffCount(),
                        "[the harbour] 「解除目标的 1 个负面效果」 — one, not all"));
    }

    /** 行迹 崇高拂尘「战斗开始时，星期日恢复 25 点能量」. */
    @Test
    public void theWhiskPaysTwentyFiveEnergyAtBattleStart() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Battle battle = new Battle(List.of(sunday), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(25.0, sunday.getCurrentEnergy(), EXACT_EPS,
                "「战斗开始时，星期日恢复 25 点能量」 (no other rule pays him anything at start)");
    }

    // ==================================================================
    // 5. 秘技 荣光之秘: only the first ability
    // ==================================================================

    @Test
    public void theTechniqueArmsOnlyTheFirstAbility() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        sunday.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("sys:technique", 1));
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        long afterFirst = shareModifiers(ally);
        Assertions.assertEquals(2, afterFirst,
                "「首次…使目标造成的伤害提高 50%」 on top of the Skill's own share");

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        Assertions.assertEquals(afterFirst, shareModifiers(ally),
                "…and the second cast adds nothing: the marker was spent");
    }

    // ==================================================================
    // 6. 行迹 主日渴慕: the 40-point energy floor
    // ==================================================================

    @Test
    public void theLentenGiftLiftsTheRestoreToFortyPoints() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        ally.setCurrentEnergy(0);
        Assertions.assertTrue(ally.getMaxEnergy() * 0.2 < 40,
                "precondition: 20 percent of the ally maximum is below the floor (" + ally.getMaxEnergy() + ")");

        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(40.0, ally.getCurrentEnergy(), 1e-9,
                "「恢复的能量提高至 40 点」 — 20 percent would have been less than 40");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * How much damage boost the ally carries after Sunday casts his Skill on it.
     */
    private static double boostAfterCastAt(int allyCid, boolean withMemosprite) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        if (withMemosprite) {
            battle.summonMemosprite(ally);
            battle.processRequests();
        }

        cast(battle, sunday, ally);

        return damageBoostOf(ally);
    }

    /**
     * A real cast of his Skill, aimed at {@code ally} - the path that carries "指定" as the event's target.
     */
    private static void cast(Battle battle, Character sunday, Character ally) {
        battle.castImmediate(new DefaultSkill(SUNDAY, SKILL_SLOT, 1), sunday, List.of(ally));
    }

    private static double damageBoostOf(CanHit unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /**
     * How much action value the unit still has - zero means "acts now".
     */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue().getTimeRemaining(signal);
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
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        castUltimate(battle, sunday, ally);
        return battle;
    }

    private static void castUltimate(Battle battle, Character sunday, Character ally) {
        battle.castImmediate(new DefaultSkill(SUNDAY, ULTIMATE_SLOT, 1), sunday, List.of(ally));
    }

    /**
     * The CRIT DMG of the [蒙福者] state's modifier, or {@code -1} when it is not attached.
     */
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

    /**
     * Runs the queue up to {@code who}'s turn and settles both boundaries of it.
     */
    private static void takeTurn(Battle battle, Character who) {
        for (int guard = 0; guard < 60; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue().getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
                battle.afterMove();
                return;
            }
            battle.afterMove();
        }
        throw new AssertionError("no turn for the requested unit within 60 steps");
    }

    /**
     * The standard test MONSTER, despite the local name - {@link BattleFixture#monster()}. It is NOT
     * {@link BattleFixture#dummy()}: the two are different fixtures, and the cases outside the Skill section ask for the
     * dummy by its qualified name, so this helper must not be substituted for it.
     */
    private static Enemy dummy() {
        return BattleFixture.monster();
    }

    /** How many ALL_DAMAGE_TYPE_BOOST modifiers the ally carries (the Skill's share and the technique's are one each). */
    private static long shareModifiers(Character ally) {
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .count();
    }
}
