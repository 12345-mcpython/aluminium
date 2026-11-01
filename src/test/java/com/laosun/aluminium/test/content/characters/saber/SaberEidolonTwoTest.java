package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's Eidolon 2:「本场战斗中每获得 1 点【炉心共鸣】，使 Saber 造成的伤害无视目标 1.00% 的防御力，最多叠加 15 层」.
 *
 * <p>Live-read: the attribute follows the counter, so the judge adds points AFTER the battle starts and watches the value
 * grow - and then adds past the cap to watch it stop.
 */
public class SaberEidolonTwoTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";

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
        System.out.println("[1014] defence ignore: start(" + start + ") " + atStart + " ; +5 points " + afterFive
                + " ; +35 points " + capped);
        Assertions.assertEquals(0.01 * 5, afterFive - atStart, 1e-9, "「每获得 1 点…无视 1% 防御力」");
        // The cap is ABSOLUTE 15 layers = 0.15, and the bar already had one point at start (the talent's), so the delta
        // from `atStart` is 0.14 - asserting the delta against 0.15 would have called a correct cap a failure.
        Assertions.assertEquals(0.01 * 15, capped, 1e-9, "「最多叠加 15 层」");
    }

    @Test
    public void belowTheRankThereIsNone() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 1);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int i = 0; i < 5; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        Assertions.assertEquals(0.0, value(saber), 1e-9, "the control: nothing at Eidolon 1");
    }

    private static double value(Character saber) {
        return saber.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }
}