package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1014 Saber's trace 湖之祝福: 「Saber 可积攒最多 <b>120</b> 点溢出能量，施放终结技后清空并恢复相应的能量值」.
 *
 * <p><b>Why a bank and not a wider bar.</b> The bar keeps its meaning (an ultimate is castable at {@code max} and not
 * before), and what would have been lost above the cap is <em>remembered</em>, then handed back after the ultimate. A bar
 * raised to {@code max + 120} would instead have every later ultimate start charged - a different sentence.
 *
 * <p>The four readings below are the ones the clause can be wrong in, each with a control: the excess is kept, the
 * allowance caps it, a unit without the trace still loses it, and the ultimate empties the bank into the bar.
 */
public class SaberEnergyOverflowTest {

    private static final int SABER = 1014;
    private static final int OTHER = 1002;
    private static final int LEVEL = 80;

    /** 「可积攒…溢出能量」: a gain that does not fit is kept rather than dropped. */
    @Test
    public void theOverflowIsKeptInsteadOfLost() {
        Battle battle = battleAt(SABER, 0);
        Character saber = battle.characters().getFirst();
        double max = saber.getMaxEnergy();
        saber.setCurrentEnergy(max - 10);

        double credited = saber.gainEnergy(100);

        Assertions.assertEquals(10, credited, 1e-9, "the bar only takes what fits");
        Assertions.assertEquals(max, saber.getCurrentEnergy(), 1e-9, "so it is full");
        Assertions.assertEquals(90, saber.getBankedEnergy(), 1e-9,
                "「可积攒溢出能量」 — the other 90 is held, not dropped");
    }

    /** 「最多 120 点」: the allowance is the trace's number. */
    @Test
    public void theBankHoldsAtMostOneHundredAndTwenty() {
        Battle battle = battleAt(SABER, 0);
        Character saber = battle.characters().getFirst();
        saber.gainEnergy(500);

        Assertions.assertEquals(saber.getMaxEnergy(), saber.getCurrentEnergy(), 1e-9, "precondition: the bar fills first");
        Assertions.assertEquals(120, saber.getBankedEnergy(), 1e-9,
                "「可积攒**最多 120 点**溢出能量」 — 500 - 360 = 140 is clipped to 120");
    }

    /** The control: without the trace a unit keeps nothing above its cap. */
    @Test
    public void aUnitWithoutTheTraceStillLosesTheOverflow() {
        Battle battle = battleAt(OTHER, 0);
        Character other = battle.characters().getFirst();
        other.gainEnergy(500);

        Assertions.assertEquals(other.getMaxEnergy(), other.getCurrentEnergy(), 1e-9, "the bar fills");
        Assertions.assertEquals(0, other.getBankedEnergy(), 1e-9,
                "and the excess is lost - the default every other file keeps");
    }

    /** 「施放终结技后清空并恢复相应的能量值」: the bank becomes energy again and the bank is emptied. */
    @Test
    public void theUltimatePoursTheBankBackIntoTheBar() {
        Battle battle = battleAt(SABER, 0);
        Character saber = battle.characters().getFirst();
        saber.gainEnergy(500);
        Assertions.assertEquals(120, saber.getBankedEnergy(), 1e-9, "precondition: 120 banked");
        // What casting the ultimate does first: it pays the bar out (UltimateService.cast).
        saber.setCurrentEnergy(0);

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        battle.processRequests();

        Assertions.assertEquals(120, saber.getCurrentEnergy(), 1e-9,
                "「恢复相应的能量值」 — the banked amount is handed back");
        Assertions.assertEquals(0, saber.getBankedEnergy(), 1e-9, "「清空」");
    }

    /** 星魂 6: 「行迹【湖之祝福】可积攒的溢出能量提高到 200 点」. */
    @Test
    public void theEidolonRaisesTheAllowanceToTwoHundred() {
        Battle five = battleAt(SABER, 5);
        Character e5 = five.characters().getFirst();
        e5.gainEnergy(500);
        Assertions.assertEquals(120, e5.getBankedEnergy(), 1e-9, "E5 adds nothing: the trace's 120 stands");

        Battle six = battleAt(SABER, 6);
        Character e6 = six.characters().getFirst();
        e6.gainEnergy(500);
        Assertions.assertEquals(200, e6.getBankedEnergy(), 1e-9,
                "「提高到 200 点」 — the trace's 120 plus this Eidolon's 80");
    }

    // ==================================================================

    private static Battle battleAt(int cid, int eidolon) {
        Character main = CharacterFactory.create(cid, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(main),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
