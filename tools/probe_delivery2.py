"""Is it the BATTLE_START timing? (round 12 of the goal)

`applyControl` reads fine: the chance defaults to 1.0 and everything goes through `tryApplyDebuff`. So the control either had no
target or lost the roll. This moves the very same effect to TURN_START -- the enemies are certainly on the field by then -- and
spends the owner's turn.
"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/DebuffDeliveryProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: the same APPLY_CONTROL, on BATTLE_START and on TURN_START. */
public class DebuffDeliveryProbeTest {
    @Test
    public void whereDoesItLand() {
        System.out.println("[delivery] at BATTLE_START: " + controlsAt("BATTLE_START", false));
        System.out.println("[delivery] at TURN_START:   " + controlsAt("TURN_START", true));
    }

    private static int controlsAt(String event, boolean spendTurn) {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");
        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule(event, List.of("actor == self"), land)), List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (spendTurn) {
            Signal signal = battle.queue.snapshot().stream()
                    .filter(candidate -> candidate.getCanHit() == owner).findFirst().orElseThrow();
            battle.currentMove = signal;
            battle.beforeMove();
            battle.afterMove();
            battle.processRequests();
        }
        return battle.enemies.getFirst().getBuffManager().countBuffs(ControlBuff.class);
    }
}
''')
print("ok   the probe asks both timings")
