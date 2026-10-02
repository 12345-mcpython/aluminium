package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Does `GAIN_ENERGY` land when the engine's own announcement is fired? (1211, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and the event is fired through `Battle.fireStateEnded` -- the only entry that sets the state name the
 * condition reads (`lastStateEndedName`).
 */
public class StateEndedEnergyProbeTest {
    private static final int OWNER = 1211;
    private static final int MONSTER = 1002011;
    private static final String STATE = "生息";

    /** ⭐ The reader must hand back 8 energy for this event. */
    @Test
    public void theReaderLandsEnergyForTheAnnouncedState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        owner.setCurrentEnergy(0);
        double before = owner.getCurrentEnergy();
        battle.fireStateEnded(owner, STATE);
        battle.processRequests();

        Assertions.assertEquals(8.0, owner.getCurrentEnergy() - before, 1e-6,
                "the reader must grant the document's 8 energy when the engine announces this state's end ("
                        + before + " -> " + owner.getCurrentEnergy() + ")");
    }
}
