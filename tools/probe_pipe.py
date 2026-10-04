"""Is the probe's scene able to deliver anything at all? (round 10 of the goal)

Last round's probe reported `frozen = false` AND that neither DEBUFF_APPLIED rule fired -- including the unfiltered one. Before
building any channel, prove the two ends of the pipe: can a plain BATTLE_START rule move a number, and can a control land at all?

Deliberately no resources: the earlier probe's `ResourceSpec(...)` call is one of the things in doubt, so the readings here are the
ATTACK panel (rule A) and the DEFENCE panel (rule B) -- different attributes, because two modifiers on ONE attribute would evict
each other and one of the two readings would be lost.
"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/PipeProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: does a plain BATTLE_START rule fire, and does a control land? */
public class PipeProbeTest {
    @Test
    public void bothEndsOfThePipe() {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);

        EffectSpec atk = new EffectSpec();
        TriggerSpecs.set(atk, "op", "MODIFY_ATTR");
        TriggerSpecs.set(atk, "attribute", "ATTACK");
        TriggerSpecs.set(atk, "percent", 0.5);
        TriggerSpecs.set(atk, "permanent", Boolean.TRUE);
        TriggerSpecs.set(atk, "target", "self");

        EffectSpec def = new EffectSpec();
        TriggerSpecs.set(def, "op", "MODIFY_ATTR");
        TriggerSpecs.set(def, "attribute", "DEFENCE");
        TriggerSpecs.set(def, "percent", 0.5);
        TriggerSpecs.set(def, "permanent", Boolean.TRUE);
        TriggerSpecs.set(def, "target", "self");

        EffectSpec control = new EffectSpec();
        TriggerSpecs.set(control, "op", "APPLY_CONTROL");
        TriggerSpecs.set(control, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(control, "turns", 2);
        TriggerSpecs.set(control, "baseChance", 1.0);
        TriggerSpecs.set(control, "target", "all_enemies");

        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), atk),
                TriggerSpecs.rule("BATTLE_START", List.of(), control),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of(), def)),
                List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        double attackBefore = owner.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        battle.processRequests();
        double attackAfter = owner.getAttribute(AttributeType.ATTACK).get();
        double defence = owner.getAttribute(AttributeType.DEFENCE).get();
        double defenceBase = owner.getAttribute(AttributeType.DEFENCE).baseValue();

        System.out.println("[pipe] BATTLE_START moved ATTACK: " + (attackAfter - attackBefore)
                + " ; frozen on enemy = " + battle.enemies.getFirst().getBuffManager().hasState("\\u51bb\\u7ed3")
                + " ; DEFENCE " + defence + " vs base " + defenceBase
                + " => DEBUFF_APPLIED rule fired: " + (defence > defenceBase));
    }
}
''')
print("ok   the pipe probe is written")
