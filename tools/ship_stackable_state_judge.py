"""Judge v3: three separate BATTLE_START rules (one effect each) and the state ended from Java.

The probe proved one APPLY_BUFF attaches; the judge with THREE effects in one rule read zero, which could be the effects
sharing a rule or the extra rules in the scene. This version removes the second possibility (no TURN_START rule: the judge
calls removeState itself) and keeps the three applications as three rules, so whichever half fails is now visible.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StackableStateTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A state that carries a COUNT (2026-10-02): "stackable": true on APPLY_BUFF.
 *
 * <p>A plain state refreshes on re-application, and an ADD_STACK buff accumulates but is not a StateBuff (so its expiry
 * never announces STATE_ENDED). The corpus needs both at once for Bondmate-of-Appreciation.
 */
public class StackableStateTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";
    private static final String RECORD = "probeRecord";

    /** Re-applying it three times leaves three instances -- that is the count. */
    @Test
    public void theSameStateStacksInsteadOfRefreshing() {
        Character owner = owner();
        System.out.println("[stackable] instances=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "three applications, three instances");
    }

    /** And when it ends, the moment is announced once per instance. */
    @Test
    public void endingItAnnouncesOncePerInstance() {
        Character owner = owner();
        Battle battle = battle(owner);
        // ended from Java, so the scene needs no TURN_START rule: removeState is the explicit-removal path that announces
        int removed = owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        int announced = owner.getResources().value(RECORD);
        System.out.println("[stackable] removed=" + removed + " announcements=" + announced);
        Assertions.assertEquals(3, removed, "three instances come off");
        Assertions.assertEquals(3, announced, "one announcement per instance");
    }

    // ==================================================================

    private static Character owner() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        return owner;
    }

    private static Battle battle(Character owner) {
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static TriggerTable scene() {
        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amount", 1.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), stack()),
                        TriggerSpecs.rule("BATTLE_START", List.of(), stack()),
                        TriggerSpecs.rule("BATTLE_START", List.of(), stack()),
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null)));
    }

    private static EffectSpec stack() {
        return effect("APPLY_BUFF", "buff", STATE, "turns", 9, "stackable", Boolean.TRUE,
                "maxStacks", 9, "target", "self");
    }

    private static EffectSpec effect(String op, Object... pairs) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", op);
        for (int i = 0; i < pairs.length; i += 2) {
            TriggerSpecs.set(spec, (String) pairs[i], pairs[i + 1]);
        }
        return spec;
    }
}
''')
print("ok   judge v3 written")
