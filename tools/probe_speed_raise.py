"""DIAGNOSTIC (throwaway): does a `TriggerTable.plus` rule that raises SPEED actually take effect? (2026-10-02)

Asked because Cyrene's threshold rule did not fire at 200 speed, and one candidate is that the appended "raise SPEED"
rule never applied. One question only: after `startBattle`, is her speed higher than without the appended rule?
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/SpeedRaiseProbeTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

public class SpeedRaiseProbeTest {
    private static final int OWNER = 1415;

    @Test
    public void anAppendedSpeedRuleTakesEffect() {
        double plain = speed(false);
        double raised = speed(true);
        Assertions.assertTrue(raised > plain,
                "the appended rule must raise her speed (" + plain + " -> " + raised + ")");
    }

    private static double speed(boolean append) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (append) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
            TriggerSpecs.set(raise, "attribute", "SPEED");
            TriggerSpecs.set(raise, "amount", 100.0);
            TriggerSpecs.set(raise, "permanent", true);
            TriggerSpecs.set(raise, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)))));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.SPEED).get();
    }
}
''')
print("ok   probe written")
