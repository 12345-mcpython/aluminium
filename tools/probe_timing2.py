"""Only the number still unknown: does the same control land when the event carries an actor? (round 13)"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/DebuffTimingProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: TURN_START, where the event does carry an actor. */
public class DebuffTimingProbeTest {
    @Test
    public void atTurnStart() {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");
        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule("TURN_START", List.of("actor == self"), land)), List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == owner).findFirst().orElseThrow();
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
        int landed = battle.enemies.getFirst().getBuffManager().countBuffs(ControlBuff.class);
        System.out.println("[turn-start] controls = " + landed);
        Assertions.assertTrue(landed >= 0, "probe");
    }
}
''')
print("ok   the turn-start-only probe")
