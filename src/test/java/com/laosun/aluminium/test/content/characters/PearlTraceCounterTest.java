package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): her trace gains 5 of the counter at the start of an ALLY's turn, capped at 50. */
public class PearlTraceCounterTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final String COUNTER = "\u597d\u6d3b\u5f53\u8d4f";

    @Test
    public void anAllyTurnStartGrantsFiveCappedAtFifty() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character ally = battle.characters.get(1);

        int before = (int) pearl.getResources().get(COUNTER).getValue();
        battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
        int afterAlly = (int) pearl.getResources().get(COUNTER).getValue();

        // Push it to the declared cap and fire again: the resource's own max must hold it at 50.
        for (int i = 0; i < 20; i++) {
            battle.fireTriggers(TriggerEvent.TURN_START, ally, ally, 0, 0);
        }
        int capped = (int) pearl.getResources().get(COUNTER).getValue();
        System.out.println("[pearl_trace] counter " + before + " -> " + afterAlly + " after one ally turn ; capped at " + capped);

        Assertions.assertEquals(before + 5, afterAlly, "an ally's turn start grants 5");
        Assertions.assertEquals(50, capped, "and the declared max of 50 holds it");
    }
}
