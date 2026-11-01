package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's Eidolon 6:「进入战斗首次施放终结技后，为 Saber 固定恢复 300 点能量」.
 *
 * <p>Measured at the EVENT level, not around a real cast: {@code castImmediate} does not deduct the Ultimate's energy, so
 * reading the bar before and after a cast cannot see this 300 (a full bar of 360 swallows it). Firing the {@code ULT_CAST}
 * payload the rule is written against isolates the clause itself - and the "first only" half is checked by firing twice.
 */
public class SaberEidolonSixEnergyTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;

    @Test
    public void theFirstUltimateRefundsThreeHundredOncePerBattle() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 6);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        double afterFirst = saber.getCurrentEnergy();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        double afterSecond = saber.getCurrentEnergy();
        System.out.println("[1014] energy after the first ULT_CAST " + afterFirst + " ; after the second " + afterSecond);
        Assertions.assertEquals(300.0, afterFirst, 1e-9, "「固定恢复 300 点能量」");
        Assertions.assertEquals(afterFirst, afterSecond, 1e-9, "「首次」: a second ultimate pays nothing");
    }

    @Test
    public void belowTheRankItPaysNothing() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 5);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
        Assertions.assertEquals(0.0, saber.getCurrentEnergy(), 1e-9, "the control: nothing at Eidolon 5");
    }
    /**
     * 「每再施放 3 次终结技可触发 1 次」: after the first refund, the counter runs 1 / 2 / 3 and the fourth ultimate pays
     * again. Judged at the event level for the same reason as the first case - a full bar swallows the number.
     */
    @Test
    public void everyThreeMoreUltimatesPayAgain() {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, 6);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        List<Double> energies = new ArrayList<>();
        for (int cast = 1; cast <= 4; cast++) {
            saber.setCurrentEnergy(0);
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, saber, saber, 0, 0));
            energies.add(saber.getCurrentEnergy());
        }
        System.out.println("[1014] energy after ultimates 1..4: " + energies);
        Assertions.assertEquals(300.0, energies.get(0), 1e-9, "the FIRST ultimate pays");
        Assertions.assertEquals(0.0, energies.get(1), 1e-9, "the second pays nothing (1 of 3)");
        Assertions.assertEquals(0.0, energies.get(2), 1e-9, "the third pays nothing (2 of 3)");
        Assertions.assertEquals(300.0, energies.get(3), 1e-9, "the fourth pays again (3 of 3)");
    }
}
