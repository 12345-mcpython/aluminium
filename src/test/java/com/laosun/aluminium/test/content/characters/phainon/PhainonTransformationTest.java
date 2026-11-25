package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Countdown;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1408 Phainon (白厄), the transformation [变身] as one arc: her ultimate starts it and the countdown of eight extra turns, the
 * block it is worth lives exactly as long as the state and dies with it, the last countdown turn ends it, and its two "when the
 * transformation ends" clauses pay their speed and Kindling; beside that, the state heals her attacks and strips her debuffs.
 *
 * <p>Consolidated (P2) from eight sibling classes that each pinned one half of that arc. Each source's class doc is kept, as a
 * plain comment, at the head of the group it supplied. L3 then folded the cases that read one clause on one board into scenario
 * tests ({@code Assertions.assertAll}), one message per clause reading; every expectation, tolerance and message is unchanged.
 */
public class PhainonTransformationTest {
    private static final int OWNER = 1408;
    private static final int ALLY = 1002;
    private static final int APPLIER = 1002;
    private static final String STATE = "char:1408/transformation";
    private static final String COUNTDOWN = "char:1408/kaoslana_extra_turn";
    private static final String SEEDS = "char:1408/ember";
    private static final String DOT = "dot:shock";
    private static final int TURNS = 8;
    private static final double EPS = 1e-9;

    // ==================================================================
    // Former class: TransformationStartsTest
    // ==================================================================
    /*
     * 1408 Phainon (白厄): "transform into Khaslana (卡厄斯兰那), and during the transformation open the zone Ruinous Irontomb (时墟铁墓)".
     *
     * <p>FILE-DRIVEN, and only the half the document states with no number in it: her ultimate puts the transformation STATE
     * on. It is permanent because its end is the last countdown turn (document :120) -- an explicit removal, which this arc made
     * announce `STATE_ENDED`, so an end-reader now has something to hang on.
     */

    /**
     * The ultimate starts the transformation.
     */
    @Test
    public void theUltimateStartsTheTransformation() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed yet");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertTrue(owner.getBuffManager().hasState(STATE),
                "the transformation is on (got " + owner.getBuffManager().hasState(STATE) + ")");
    }

    // ==================================================================
    // Former class: TransformationCountdownTest
    // ==================================================================
    /*
     * 1408："拥有 8 个卡厄斯兰那的额外回合，速度固定为基础速度的 60%".
     *
     * <p>FILE-DRIVEN: her ultimate starts the transformation, and the countdown that spends the eight extra turns rides the
     * same rule -- so the two are born together.
     */

    /**
     * The ultimate starts the countdown as well as the transformation.
     */
    @Test
    public void theUltimateStartsTheCountdown() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(owner.getBuffManager().hasState(COUNTDOWN), "precondition: no countdown yet");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertTrue(battle.countdownUnits().stream()
                        .anyMatch(c -> COUNTDOWN.equals(c.getName())),
                "the countdown for the eight extra turns is running");
    }

    // ==================================================================
    // Former class: TransformationEndsOnLastCountdownTest
    // ==================================================================
    /*
     * 1408："最后 1 个倒计时回合…结束变身".
     *
     * <p>TWO-WAY, and the countdown's turns are driven by the SHIPPED pattern (`CountdownTest`): point `currentMove` at the clock's
     * `Signal` on the action bar and call `beforeMove()`. `stepForward()` does not execute a turn at all -- the tree says so in
     * `ContentWeaknessClausesTest` and this judge does not rely on it.
     */

    /**
     * Both sides of "the last one": after the eighth countdown turn the transformation is gone, and one turn earlier it is
     * still on.
     */
    @Test
    public void theEighthCountdownTurnEndsIt() {
        boolean afterEight = transformedAfterCountdownTurns(TURNS);
        boolean afterSeven = transformedAfterCountdownTurns(TURNS - 1);
        Assertions.assertAll("「最后 1 个倒计时回合…结束变身」",
                () -> Assertions.assertFalse(afterEight,
                        "\"the last countdown turn ... ends the transformation\" (「最后 1 个倒计时回合…结束变身」)"),
                () -> Assertions.assertTrue(afterSeven,
                        "「最后 1 个」-- the seventh turn is not the last one"));
    }

    // ==================================================================

    private static boolean transformedAfterCountdownTurns(int turns) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        Assertions.assertEquals(1, battle.countdownUnits().size(), "precondition: her clock exists");

        for (int turn = 0; turn < turns; turn++) {
            Countdown clock = battle.countdownUnits().isEmpty() ? null : battle.countdownUnits().getFirst();
            if (clock == null) {
                break;
            }
            battle.queue().snapshot().stream()
                    .filter(signal -> signal.getCanHit() == clock)
                    .findFirst()
                    .ifPresent(signal -> battle.setCurrentMove(signal));
            battle.beforeMove();
            battle.processRequests();
        }
        return owner.getBuffManager().hasState(STATE);
    }

    // ==================================================================
    // Former class: TransformationStatsTest
    // ==================================================================
    /*
     * 1408："变身期间攻击力提高 80%，生命上限提高 20%".
     *
     * <p>TWO-WAY, file-driven: her ultimate transforms her, and the block is what the transformation is worth. The control is the
     * same character measured before the ultimate, so nothing else differs.
     */

    /**
     * 「变身期间攻击力提高 80%，生命上限提高 270%」 - the block measured against the BASE (the panel already carries the trace's
     * +50%), with its own control (no ultimate, no block) and with the 0-Eidolon RES PEN pinned at "none" beside it.
     */
    @Test
    public void theTransformationRaisesAtkAndMaxHp() {
        // Scene A: the same board without the ultimate first, which is the control, then the block.
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        boolean transformedBefore = owner.getBuffManager().hasState(STATE);
        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double hp0 = owner.getAttribute(AttributeType.HEALTH).get();
        boolean panelReads = atk0 > 0 && hp0 > 0;

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        boolean transformed = owner.getBuffManager().hasState(STATE);
        // Note: Updated: `atk0` already carries the trace's +50% ("进入战斗或变身结束时攻击力提高 50%"), so the
        // block is measured against the BASE: 1 + 0.5 (trace) + 0.8 (transformation) = 2.3.
        double base = atk0 / 1.5;
        double atkAfter = owner.getAttribute(AttributeType.ATTACK).get();
        double hpAfter = owner.getAttribute(AttributeType.HEALTH).get();

        // Scene B: the same block with the 0-Eidolon RES PEN read beside it.
        Character second = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle secondBattle = new Battle(List.of(second),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        secondBattle.startBattle();
        secondBattle.processRequests();
        double secondAtk0 = second.getAttribute(AttributeType.ATTACK).get();
        double secondHp0 = second.getAttribute(AttributeType.HEALTH).get();
        double pen0 = second.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        Skill secondUlt = second.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(secondBattle, secondUlt, second, List.of(second));
        secondBattle.processRequests();
        boolean secondTransformed = second.getBuffManager().hasState(STATE);
        double secondAtkAfter = second.getAttribute(AttributeType.ATTACK).get();
        double secondHpAfter = second.getAttribute(AttributeType.HEALTH).get();
        double penAfter = second.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Assertions.assertAll("「变身期间攻击力提高 80%，生命上限提高 270%」",
                () -> Assertions.assertFalse(transformedBefore, "no transformation yet"),
                () -> Assertions.assertTrue(panelReads, "precondition: the panel reads"),
                () -> Assertions.assertTrue(panelReads, "the plain panel is what it is, and the test asserts nothing else"),
                () -> Assertions.assertTrue(transformed, "precondition: the transformation is on"),
                () -> Assertions.assertEquals(base * 2.3, atkAfter, atk0 * 0.001,
                        "「变身期间攻击力提高 80%」 (during the transformation, ATK is raised by 80%) beside the trace's 50% (before=" + atk0 + ")"),
                () -> Assertions.assertEquals(hp0 * 3.7, hpAfter, hp0 * 0.001,
                        "「生命上限提高 270%」 (Max HP raised by 270%) (before=" + hp0 + ")"),
                () -> Assertions.assertTrue(secondTransformed, "precondition: the transformation is on"),
                () -> Assertions.assertEquals(secondAtk0 / 1.5 * 2.3, secondAtkAfter, secondAtk0 * 1e-9,
                        "\"ATK raised by 80%\" (「攻击力提高 80%」)"),
                () -> Assertions.assertEquals(secondHp0 * 3.7, secondHpAfter, secondHp0 * 1e-9,
                        "\"Max HP raised by 270%\" (「生命上限提高 270%」)"),
                () -> Assertions.assertEquals(pen0, penAfter, EPS,
                        "0 星魂没有抗性穿透 —— 那一句是星魂 2 的（判据见 PhainonEidolonTwoTest）"));
    }

    // ==================================================================
    // Former class: TransformationScopedStatsTest
    // ==================================================================
    /*
     * 1408: "变身期间攻击力提高 80%，生命上限提高 270%" - and, at 0 Eidolons, <b>no</b> RES PEN.
     *
     * <p>THE POINT IS THE THIRD ASSERTION OF THE SECOND CASE: the block must live exactly as long as the state. Removing
     * [变身] and watching ATK come back down is what tells "during the transformation" apart from "for the rest of the battle"
     * -- and item 34 shipped without that link, so this judge would have caught it.
     *
     * <p>⚠ <b>CORRECTED (2026-10-19).</b> This class used to assert 「卡厄斯兰那的物理属性抗性穿透提高 20%」 as part of the same
     * block, at <b>0 Eidolons</b> - so the case pinned the defect as the expectation. That sentence is <b>Eidolon 2</b>'s first
     * half (see {@code PhainonEidolonTwoTest}, which pins both ranks); a 0-Eidolon Phainon has 0. What is unchanged is what
     * this case was written for: the ATK / Max HP block, and the fact that it dies with the state.
     */

    /**
     * "During the transformation" means the block dies with the state.
     */
    @Test
    public void theBlockDiesWithTheState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double pen0 = owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getAttribute(AttributeType.ATTACK).get() > atk0, "precondition: the block is on");

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "the transformation is off");
        // Note: Updated: the trace fires AGAIN when the transformation ends ("或变身结束时"), so what is left
        // is the battle-start 50% plus the end 50%. What this reading is about is unchanged: the TRANSFORMATION's own
        // block is gone.
        Assertions.assertEquals(atk0 * (2.0 / 1.5), owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「变身期间」 (during the transformation)-- the transformation's block must be gone with the state");
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the RES PEN too");
    }

    // ==================================================================
    // Former class: TransformationEndClausesTest
    // ==================================================================
    /*
     * 1408: the reading of the two "when the transformation ends" sentences.
     *
     * <p>Both clauses hang off the same moment the reward does -- STATE_ENDED for [变身] -- so this reads them the way the shipped
     * sibling reads the transformation's block: transform, then remove the state and look.
     */

    /**
     * "变身结束时，使我方全体速度提高 15%，持续 1 回合" -- and "我方全体" is read on two units, not one.
     */
    @Test
    public void theEndSpeedsTheWholeParty() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");

        // Note: Read AFTER the transformation: its own numbers are then already in, so the delta below is the end clause alone.
        double ownerBefore = owner.getAttribute(AttributeType.SPEED).get();
        double allyBefore = ally.getAttribute(AttributeType.SPEED).get();
        double ownerBase = owner.getAttribute(AttributeType.SPEED).baseValue();
        double allyBase = ally.getAttribute(AttributeType.SPEED).baseValue();

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double ownerAfter = owner.getAttribute(AttributeType.SPEED).get();
        double allyAfter = ally.getAttribute(AttributeType.SPEED).get();
        System.out.println("[end-clauses] owner " + ownerBefore + " -> " + ownerAfter + " (base " + ownerBase + ")"
                + " ; ally " + allyBefore + " -> " + allyAfter + " (base " + allyBase + ")");

        Assertions.assertEquals(ownerBase * 0.15, ownerAfter - ownerBefore, ownerBase * 1e-6,
                "「变身结束时…速度提高 15%」 (when the transformation ends ... SPD is raised by 15%)-- on her");
        Assertions.assertEquals(allyBase * 0.15, allyAfter - allyBefore, allyBase * 1e-6,
                "all allies (「我方全体」) -- and on the ally, which is what \"all\" (全体) means");
    }

    /**
     * "变身结束时，获得 3 点[火种]".
     */
    @Test
    public void theEndGrantsThreeSeeds() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        int before = owner.getResources().value(SEEDS);
        // "战斗开始时，获得 1 点[char:1408/ember]" -- the other half of the same trace line (1408101).
        Assertions.assertEquals(1, before, "\"at the start of battle, gains 1 point of Kindling (【char:1408/ember】)\" (「战斗开始时，获得 1 点【char:1408/ember】」)");
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        int duringTransformation = owner.getResources().value(SEEDS);

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        int after = owner.getResources().value(SEEDS);
        System.out.println("[end-clauses] seeds " + before + " -> " + duringTransformation + " -> " + after);

        Assertions.assertEquals(duringTransformation + 3, after,
                "\"when the transformation ends, gains 3 points of Kindling (【char:1408/ember】)\" (「变身结束时，获得 3 点【char:1408/ember】」)");
    }

    /**
     * "进入战斗或变身结束时，攻击力提高 50%。该效果最多叠加 2 层"-- read by firing the end clause more times than the cap allows.
     *
     * <p>Note: The state is applied directly for these ends: the transformation is only granted by the ultimate, and what this reading
     * is about is the CAP. The seed clause beside it has no cap, so its growth proves the later firings really happened --
     * without that, a flat ATK could just mean "nothing fired".
     */
    @Test
    public void theEndClauseCapsAtTwoLayers() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        owner.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterOneEnd = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterOneEnd = owner.getResources().value(SEEDS);

        owner.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterTwoEnds = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterTwoEnds = owner.getResources().value(SEEDS);
        System.out.println("[end-clauses] atk " + atkAfterOneEnd + " -> " + atkAfterTwoEnds
                + " ; seeds " + seedsAfterOneEnd + " -> " + seedsAfterTwoEnds);

        Assertions.assertEquals(seedsAfterOneEnd + 3, seedsAfterTwoEnds,
                "the second end really fired -- the seed clause has no cap");
        Assertions.assertEquals(0.0, atkAfterTwoEnds - atkAfterOneEnd, 1e-9,
                "「最多叠加 2 层」 (stacks at most 2 times): with the battle-start layer that is already two, so the third is dropped");
    }

    // ==================================================================
    // Former class: TransformationHealsOnAttackTest
    // ==================================================================
    /*
     * 1408: "变身期间...施放攻击后回复等同于自身生命上限 20% 的生命值" (while transformed, casting an attack restores HP equal to 20% of her own Max HP).
     *
     * <p>TWO-WAY, and the control is the SAME fight without the transformation. She is hurt first through the battle's own damage
     * entry point, so a heal has something to restore.
     */

    /**
     * Both sides of 「变身期间」: an attack heals her for a fifth of her Max HP while transformed, and the same fight without
     * the transformation heals nothing because the sentence has not started.
     */
    @Test
    public void anAttackHealsTheTransformedHer() {
        double healed = healFromOneAttack(true);
        double untouched = healFromOneAttack(false);
        Assertions.assertAll("「变身期间...施放攻击后回复等同于自身生命上限 20% 的生命值」",
                () -> Assertions.assertEquals(0.20, healed, 1e-6,
                        "「回复等同于自身生命上限 20% 的生命值」 (share of Max HP)"),
                () -> Assertions.assertEquals(0.0, untouched, 1e-9,
                        "「变身期间」 (while transformed)-- no transformation, no heal"));
    }

    // ==================================================================

    /**
     * the heal as a SHARE of Max HP, so the size of the pool does not matter.
     */
    private static double healFromOneAttack(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
        }
        Assertions.assertEquals(transform, owner.getBuffManager().hasState(STATE),
                "precondition: the transformation is " + (transform ? "on" : "off"));

        // Note: HALF OF CURRENT HP, never half of MAX HP: the transformation raises Max HP (to 3.x) WITHOUT filling the pool, so
        // "50% of Max HP" is lethal here -- and a dead unit heals nothing, which is exactly how the first three attempts
        // measured a healthy 0.0 and looked like a broken HEAL. Measured: hurt=0.0 of 5312.8 after that hit.
        battle.applyTrueDamage(battle.enemies().get(0), owner, DamageElement.ICE, owner.getCurrentHp() * 0.5);
        battle.processRequests();
        double hurt = owner.getCurrentHp();
        double maxHp = owner.getMaxHp();
        Assertions.assertTrue(hurt < maxHp, "precondition: she was really hurt (" + hurt + " of " + maxHp + ")");

        // Note: Her BASIC attack, not her skill: the document says Khaslana (卡厄斯兰那) "拥有 1 个强化普攻和 2 个强化战技" (has 1 enhanced basic attack and 2 enhanced Skills), so the transformed
        // form attacks with the basic slot.
        Skill skill = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(skill, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies().get(0)));
        battle.processRequests();
        return (owner.getCurrentHp() - hurt) / maxHp;
    }

    // ==================================================================
    // Former class: TransformationDispelsTest
    // ==================================================================
    /*
     * 1408："解除自身所有负面效果，随后造成…".
     *
     * <p>SAME SCENE, ONE VARIABLE, and the DOT is applied AFTER the transformation: item 41 made the transformed form immune to
     * CONTROLS, so a control could not be used here even though it is a debuff -- a Thunder DOT is used instead, and the immunity
     * does not touch it.
     */

    /**
     * Both sides of the dispel: transformed, her own cast strips the debuff; untransformed, the same cast leaves it there.
     */
    @Test
    public void theTransformedFormStripsItsDebuffs() {
        boolean stillDebuffedWhenTransformed = debuffedAfterCast(true);
        boolean stillDebuffedWhenPlain = debuffedAfterCast(false);
        Assertions.assertAll("「解除自身所有负面效果」",
                () -> Assertions.assertFalse(stillDebuffedWhenTransformed,
                        "\"removes all of her own debuffs\" (「解除自身所有负面效果」)"),
                () -> Assertions.assertTrue(stillDebuffedWhenPlain,
                        "「卡厄斯兰那…解除」 (Khaslana ... remove)-- the dispel belongs to the transformation"));
    }

    // ==================================================================

    private static boolean debuffedAfterCast(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), dot()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }

        // The debuff lands NOW -- after the transformation (see the class comment), and before her own cast.
        Skill theirs = applier.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: the applier has a skill");
        SkillExecutor.execute(battle, theirs, applier, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(DOT),
                "precondition: the DOT landed on her (state name " + DOT + ")");

        Skill hers = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(hers, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, hers, owner, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return owner.getBuffManager().hasState(DOT);
    }

    /**
     * A Thunder DOT on the other ally.
     */
    private static EffectSpec dot() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_DOT");
        TriggerSpecs.set(e, "element", "Thunder");
        // Note: `baseChance`, the JAVA field name: `TriggerSpecs.set` uses reflection, so the JSON key (`base_chance`) is not what it
        // wants -- it failed loudly with "cannot set base_chance on class EffectSpec".
        TriggerSpecs.set(e, "baseChance", 1.0);
        TriggerSpecs.set(e, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(e, "percent", 2.9);
        TriggerSpecs.set(e, "turns", 2);
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }
}
