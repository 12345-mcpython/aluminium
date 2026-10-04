"""Probe: did the control land, and did the condition see it? (round 9 of the goal) -- temporary."""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/DebuffClassProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only. */
public class DebuffClassProbeTest {
    @Test
    public void whatHappened() {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");

        EffectSpec plain = new EffectSpec();
        TriggerSpecs.set(plain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(plain, "resource", "probePlain");
        TriggerSpecs.set(plain, "amount", 1.0);
        TriggerSpecs.set(plain, "target", "self");

        EffectSpec filtered = new EffectSpec();
        TriggerSpecs.set(filtered, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(filtered, "resource", "probeClass");
        TriggerSpecs.set(filtered, "amount", 1.0);
        TriggerSpecs.set(filtered, "target", "self");

        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of(), plain),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:control"), filtered)),
                List.of(new ResourceSpec("probePlain", 99, 0, null, null, "probe", null),
                        new ResourceSpec("probeClass", 99, 0, null, null, "probe", null))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        System.out.println("[debuff-probe] frozen on enemy = "
                + battle.enemies.getFirst().getBuffManager().hasState("\\u51bb\\u7ed3")
                + " ; unfiltered rule fired = " + value(owner, "probePlain")
                + " ; filtered rule fired = " + value(owner, "probeClass"));
    }

    private static int value(Character unit, String id) {
        return unit.getResources().has(id) ? unit.getResources().value(id) : -1;
    }
}
''')
print("ok   the probe is written")
