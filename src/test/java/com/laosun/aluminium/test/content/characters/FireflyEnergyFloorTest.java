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

/** F-9: Firefly's talent raises energy TO 50% at battle start -- a floor, not a gain. */
public class FireflyEnergyFloorTest {

    @Test
    public void theFloorRaisesLowEnergyAndLeavesHighEnergyAlone() {
        Character firefly = CharacterFactory.create(1310, 80);
        Battle battle = new Battle(List.of(firefly), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        firefly = battle.characters.getFirst();
        double max = firefly.getMaxEnergy();
        Assertions.assertTrue(max > 0, "precondition: she has an energy bar");

        double low = energyAfterTrigger(battle, firefly, 0.1 * max);
        double high = energyAfterTrigger(battle, firefly, 0.8 * max);
        System.out.println("[firefly_floor] max = " + max + " ; from 10% -> " + low + " ; from 80% -> " + high);

        Assertions.assertEquals(0.5 * max, low, 1e-6, "from below the floor, energy is raised TO 50%");
        Assertions.assertEquals(0.8 * max, high, 1e-6, "from above the floor, energy is left exactly as it was");
    }

    /** Sets energy, fires the battle-start trigger again, and reports where it ended up. */
    private static double energyAfterTrigger(Battle battle, Character firefly, double start) {
        firefly.setCurrentEnergy(start);
        battle.fireTriggers(TriggerEvent.BATTLE_START, firefly, null, 0, 0);
        return firefly.getCurrentEnergy();
    }
}
