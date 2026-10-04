"""Was `TriggerSpecs.set` silently dropping the control? (round 13 of the goal)

Measured: the same `APPLY_CONTROL{control: 冻结, baseChance: 1.0, all_enemies}` lands neither on BATTLE_START (no actor) nor on
TURN_START (actor present) -- 0 controls either way. So the op itself never lands in a HAND-BUILT table, and the helper that built
it is the suspect: `TriggerSpecs.set(land, "control", ...)` uses the JAVA field name, while `"control"` is the DATA key the validator
prints. If the helper ignores a name it does not know, the effect goes out with no state to apply.

So this builds the very same effect through Gson -- the path content files use -- and compares.
"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/DebuffTimingProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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

/** Probe only: the same control, built by the helper and by Gson. */
public class DebuffTimingProbeTest {
    @Test
    public void helperVersusGson() {
        int byHelper = controls(byHelperSpec());
        int byGson = controls(byGsonSpec());
        System.out.println("[built] helper=" + byHelper + " gson=" + byGson);
        Assertions.assertTrue(byHelper >= 0 && byGson >= 0, "probe");
    }

    private static EffectSpec byHelperSpec() {
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");
        return land;
    }

    private static EffectSpec byGsonSpec() {
        TriggerSpec spec = new Gson().fromJson("{\\"on\\":\\"TURN_START\\",\\"when\\":[\\"actor == self\\"],"
                + "\\"do\\":[{\\"op\\":\\"APPLY_CONTROL\\",\\"control\\":\\"\\\\u51bb\\\\u7ed3\\",\\"turns\\":2,"
                + "\\"base_chance\\":1.0,\\"target\\":\\"all_enemies\\"}]}", TriggerSpec.class);
        Assertions.assertNotNull(spec, "precondition: the spec loads");
        return spec.getDoEffects().getFirst();
    }

    private static int controls(EffectSpec land) {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);
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
        return battle.enemies.getFirst().getBuffManager().countBuffs(ControlBuff.class);
    }
}
''')
print("ok   the helper-versus-gson probe")
