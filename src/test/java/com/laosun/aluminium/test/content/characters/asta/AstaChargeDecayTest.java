package com.laosun.aluminium.test.content.characters.asta;

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
 * 1009 艾丝妲's talent, last sentence: 「从自身的第2回合开始，艾丝妲每回合开始时蓄能层数减少3层」.
 *
 * <p>The "from her second turn" gate is counted by a counter the rules raise on her own turn starts, so the FIRST turn pays
 * nothing and every later one spends three layers.
 */
public class AstaChargeDecayTest {

    private static final int LEVEL = 80;
    private static final int ASTA = 1009;
    private static final String CHARGE = "蓄能";

    @Test
    public void theFirstTurnPaysNothingAndEveryLaterOneSpendsThree() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        for (int i = 0; i < 6; i++) {
            asta.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff(CHARGE, 1, true, 99));
        }
        Assertions.assertEquals(6, asta.getBuffManager().stacksOf(CHARGE), "precondition: six layers");

        astaTurn(battle, asta);
        Assertions.assertEquals(6, asta.getBuffManager().stacksOf(CHARGE), "the first turn does not decay");

        astaTurn(battle, asta);
        Assertions.assertEquals(3, asta.getBuffManager().stacksOf(CHARGE), "from the second turn: -3");

        astaTurn(battle, asta);
        Assertions.assertEquals(0, asta.getBuffManager().stacksOf(CHARGE), "and every later one");
    }

    private static void astaTurn(Battle battle, Character asta) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, asta, asta, 0, 0));
    }
}
