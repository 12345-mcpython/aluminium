"""Two facts in one run (round 12 of the goal).

Read (not guessed): `APPLY_CONTROL` reaches `battle.tryApplyDebuff(...)` (`TriggerInterpreter:3172`), which IS the chokepoint that
fires DEBUFF_APPLIED -- so the event ought to fire. That leaves two questions, and they need separating:

  A. did the control LAND at all (its roll can fail; `ControlBuff`'s own name is not necessarily the word the rule wrote);
  B. does a rule on DEBUFF_APPLIED move a panel when the event is raised the way the shipped judge raises it
     (`ConeEventsTest:99`: `battle.fireTriggers(TriggerEvent.DEBUFF_APPLIED, unit, enemy, 0, 0)`).
"""
import io

PROBE = "src/test/java/com/laosun/aluminium/test/DebuffDeliveryProbeTest.java"
io.open(PROBE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only. */
public class DebuffDeliveryProbeTest {
    private static final String WATCH = "watch";

    @Test
    public void didItLandAndDoesTheEventMoveAPanel() {
        Character owner = CharacterFactory.create(1002, 80, false, null, null, 0);

        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");

        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "MODIFY_ATTR");
        TriggerSpecs.set(mark, "attribute", "ATTACK");
        TriggerSpecs.set(mark, "percent", 0.5);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");

        owner.setTriggerTable(new TriggerTable(1002, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of(), mark)),
                List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        double before = owner.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        battle.processRequests();
        double afterLanding = owner.getAttribute(AttributeType.ATTACK).get();
        int controls = battle.enemies.getFirst().getBuffManager().countBuffs(ControlBuff.class);

        // B: raise the event by hand, the way the shipped judge does
        battle.fireTriggers(TriggerEvent.DEBUFF_APPLIED, owner, battle.enemies.getFirst(), 0, 0);
        battle.processRequests();
        double afterHandFired = owner.getAttribute(AttributeType.ATTACK).get();

        System.out.println("[delivery] controls on the enemy = " + controls
                + " ; ATTACK gain from the real landing = " + (afterLanding - before)
                + " ; gain from a HAND-FIRED event = " + (afterHandFired - afterLanding));
    }
}
''')
print("ok   the delivery probe is written")
