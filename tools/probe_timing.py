"""One line, two timings (round 13 of the goal).

The previous probe printed two lines and only the first survived the XML capture, so the question -- does the very same
APPLY_CONTROL land when the event carries an actor? -- stayed open. One println this time.

Why the timing is the suspect: `applyControl` calls `battle.tryApplyDebuff(ctx.owner(), target, ...)`, and the engine's own loader
says BATTLE_START "carries no actor and no target" (it refuses `actor == self` for exactly that reason). 42 shipped BATTLE_START rules
DO use `all_enemies`/`all_allies`, so the selector is fine -- what may not be fine is the caster the roll is made with.
"""
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: the same APPLY_CONTROL on BATTLE_START (no actor) and on TURN_START (actor present). */
public class DebuffTimingProbeTest {
    @Test
    public void oneLine() {
        int atBattleStart = controls("BATTLE_START", false);
        int atTurnStart = controls("TURN_START", true);
        System.out.println("[timing] landed: BATTLE_START=" + atBattleStart + " TURN_START=" + atTurnStart);
    }

    private static int controls(String event, boolean spendTurn) {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");
        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule(event, spendTurn ? List.of("actor == self") : List.of(), land)), List.of()));

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
print("ok   one line, two timings")
