package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Does 姬子's kill clause fire, and for how much? */
public class ZoneProbeTest {

    @Test
    public void probe() {
        Character himeko = CharacterFactory.create(1003, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(himeko), List.of(EnemyFactory.create(1002011, 80, 1)),
                new Random(20260919L), null);
        battle.startBattle();

        double before = himeko.getCurrentEnergy();
        // simulate the engine's own kill credit path via the battle's event, so both the general credit and the rule can show
        CanHit enemy = battle.enemyUnits().get(0);
        System.out.println("K energy before=" + before);
        try {
            var m = Battle.class.getDeclaredMethod("fireTriggers",
                    com.laosun.aluminium.enums.TriggerEvent.class, CanHit.class, CanHit.class, double.class, double.class);
            m.setAccessible(true);
            m.invoke(battle, com.laosun.aluminium.enums.TriggerEvent.KILL, himeko, enemy, 0.0, 0.0);
            System.out.println("K after a KILL trigger: " + himeko.getCurrentEnergy()
                    + " (delta " + (himeko.getCurrentEnergy() - before) + ")");
        } catch (ReflectiveOperationException e) {
            System.out.println("K could not fire triggers: " + e);
        }

        // and the driven path, so the real credit is visible too
        Character h2 = CharacterFactory.create(1003, 80, false, null, null, 0);
        Battle b2 = new Battle(List.of(h2), List.of(EnemyFactory.create(1002011, 80, 1)),
                new Random(20260919L), null);
        BattleDriver driver = new BattleDriver(b2, new com.laosun.aluminium.models.battle.AutoPolicy());
        driver.withListener(new com.laosun.aluminium.models.battle.BattleListener() {
            @Override
            public void onDeath(CanHit who) {
                System.out.println("K death of " + who.getName() + "; Himeko energy=" + h2.getCurrentEnergy());
            }
        });
        for (int i = 0; i < 40; i++) {
            try {
                driver.step(1);
            } catch (Exception ex) {
                break;
            }
        }
        System.out.println("K done; final energy=" + h2.getCurrentEnergy());
    }
}
