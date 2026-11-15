package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's eidolons: E2 (1% defence ignore per point of 【炉心共鸣】, at most 15 layers), E2's second half (7% more Skill
 * multiplier per point on the resonance branch), and E6 (300 energy after the first Ultimate, then every third one).
 *
 * <p><b>L3:</b> each Eidolon sentence is judged as one scenario test with an {@code Assertions.assertAll}, the rank that gates
 * it and the rank below it read on their own boards in the same test: the defence ignore with its rank-1 control, and E6's
 * first / every-third refund with its rank-5 control. The second half of E2 reads the Skill's damage at rank 1 against rank 2,
 * which is a different observed object, so it keeps its own test. Every assertion, expected value, tolerance and message is
 * unchanged.
 */
public class SaberEidolonTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";

    /**
     * 1014 Saber's Eidolon 2:「本场战斗中每获得 1 点【炉心共鸣】，使 Saber 造成的伤害无视目标 1.00% 的防御力，最多叠加 15 层」.
     *
     * <p>Live-read: the attribute follows the counter, so the judge adds points AFTER the battle starts and watches the value
     * grow - and then adds past the cap to watch it stop. The rank-1 board is the control: the same points, nothing.
     */
    @Test
    public void eachResonancePointIgnoresOnePercentOfDefenceUpToFifteen() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 2);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int start = saber.getBuffManager().stacksOf(RESONANCE);
        double atStart = value(saber);
        for (int i = 0; i < 5; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        double afterFive = value(saber);
        for (int i = 0; i < 30; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        double capped = value(saber);

        Character below = CharacterFactory.create(SABER, LEVEL, true, null, null, 1);
        Battle belowBattle = new Battle(new ArrayList<>(List.of(below)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        belowBattle.startBattle();
        for (int i = 0; i < 5; i++) {
            below.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        double belowValue = value(below);

        System.out.println("[1014] defence ignore: start(" + start + ") " + atStart + " ; +5 points " + afterFive
                + " ; +35 points " + capped + " ; Eidolon 1 " + belowValue);
        Assertions.assertAll("星魂 2: 每点 1% 防御无视，15 层封顶；星魂 1 是没有",
                () -> Assertions.assertEquals(0.01 * 5, afterFive - atStart, 1e-9, "「每获得 1 点…无视 1% 防御力」"),
                // The cap is ABSOLUTE 15 layers = 0.15, and the bar already had one point at start (the talent's), so the delta
                // from `atStart` is 0.14 - asserting the delta against 0.15 would have called a correct cap a failure.
                () -> Assertions.assertEquals(0.01 * 15, capped, 1e-9, "「最多叠加 15 层」"),
                () -> Assertions.assertEquals(0.0, belowValue, 1e-9, "the control: nothing at Eidolon 1"));
    }

    private static double value(Character saber) {
        return saber.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }

    /**
     * 1014 Saber's Eidolon 2, second half:「触发【炉心共鸣】提高战技倍率的效果时，每点【炉心共鸣】额外使本次战技的伤害倍率提高 7%」.
     *
     * <p>It rides the SAME branch as the 14%-per-point boost, so the check opens that branch (enough points to fill the bar) at
     * rank 1 and rank 2 and compares the settled damage.
     */
    @Test
    public void theExtraSevenPercentPerPointRaisesTheSkill() {
        double atOne = skillDamage(1);
        double atTwo = skillDamage(2);
        System.out.println("[1014] skill through the resonance branch: E1 " + atOne + " ; E2 " + atTwo);
        Assertions.assertTrue(atTwo > atOne, "「每点额外使本次战技的伤害倍率提高 7%」");
    }

    /** The Skill's damage on a fresh target, with the resonance branch open at the given Eidolon rank. */
    private static double skillDamage(int eidolon) {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        var target = battle.enemyUnits().getFirst();
        double before = target.getCurrentHp();
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(target));
        battle.processRequests();
        return before - target.getCurrentHp();
    }

    /**
     * 1014 Saber's Eidolon 6:「进入战斗首次施放终结技后，为 Saber 固定恢复 300 点能量」.
     *
     * <p>Measured at the EVENT level, not around a real cast: {@code castImmediate} does not deduct the Ultimate's energy, so
     * reading the bar before and after a cast cannot see this 300 (a full bar of 360 swallows it). Firing the {@code ULT_CAST}
     * payload the rule is written against isolates the clause itself - and the "first only" half is checked by firing twice.
     *
     * <p>「每再施放 3 次终结技可触发 1 次」: after the first refund, the counter runs 1 / 2 / 3 and the fourth ultimate pays
     * again. Judged at the event level for the same reason as the first case - a full bar swallows the number. The rank-5 board
     * is the control: the same event, nothing.
     */
    @Test
    public void theFirstUltimateRefundsThreeHundredOncePerBattleAndEveryThreeMorePayAgain() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 6);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        double afterFirst = saber.getCurrentEnergy();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        double afterSecond = saber.getCurrentEnergy();

        Character counter = CharacterFactory.create(SABER, LEVEL, true, null, null, 6);
        Battle counterBattle = new Battle(new ArrayList<>(List.of(counter)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        counterBattle.startBattle();
        List<Double> energies = new ArrayList<>();
        for (int cast = 1; cast <= 4; cast++) {
            counter.setCurrentEnergy(0);
            counterBattle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, counter, counter, 0, 0));
            energies.add(counter.getCurrentEnergy());
        }

        Character below = CharacterFactory.create(SABER, LEVEL, true, null, null, 5);
        Battle belowBattle = new Battle(new ArrayList<>(List.of(below)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        belowBattle.startBattle();
        below.setCurrentEnergy(0);
        belowBattle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, below, below, 0, 0));
        double belowEnergy = below.getCurrentEnergy();

        System.out.println("[1014] energy after the first ULT_CAST " + afterFirst + " ; after the second " + afterSecond
                + " ; ultimates 1..4 " + energies + " ; Eidolon 5 " + belowEnergy);
        Assertions.assertAll("星魂 6: 首次 300 点，之后每 3 次再付一次；星魂 5 没有",
                () -> Assertions.assertEquals(300.0, afterFirst, 1e-9, "「固定恢复 300 点能量」"),
                () -> Assertions.assertEquals(afterFirst, afterSecond, 1e-9, "「首次」: a second ultimate pays nothing"),
                () -> Assertions.assertEquals(300.0, energies.get(0), 1e-9, "the FIRST ultimate pays"),
                () -> Assertions.assertEquals(0.0, energies.get(1), 1e-9, "the second pays nothing (1 of 3)"),
                () -> Assertions.assertEquals(0.0, energies.get(2), 1e-9, "the third pays nothing (2 of 3)"),
                () -> Assertions.assertEquals(300.0, energies.get(3), 1e-9, "the fourth pays again (3 of 3)"),
                () -> Assertions.assertEquals(0.0, belowEnergy, 1e-9, "the control: nothing at Eidolon 5"));
    }
}
