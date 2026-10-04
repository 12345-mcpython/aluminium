"""Is that ATK gain the rule, or just creating a character? (round 18 of the goal)

The mutant run left the reading unchanged, and there is no other dispatch path (`fireTriggers(event)` just forwards to the same core
loop) and no factory registry. So before anything else: create 1407, snapshot, run startBattle with NO registration and NO rule, and
see whether the ATTACK panel moves on its own.
"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/WarehouseGainProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: does the panel move by itself? */
public class WarehouseGainProbeTest {
    @Test
    public void doesItMoveWithoutAnyRule() {
        Character fighter = CharacterFactory.create(1002, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(fighter),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));

        // created but never registered, never put in the battle, and carrying no rule at all
        Character owned = CharacterFactory.create(1407, 80, false, null, null, 0);
        owned.setTriggerTable(com.laosun.aluminium.models.TriggerTable.EMPTY);
        double before = owned.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        battle.processRequests();
        double after = owned.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[gain] unattached panel moved by " + (after - before));
        Assertions.assertTrue(before > 0, "probe");
    }
}
''')
print("ok   the control probe is written")
