"""Probe: does her `\u79d8\u6280` state make the technique rule fire? (2026-10-02)

`1401`'s technique rule is `BATTLE_START` + `when: ["self has_state \u79d8\u6280"] -> +60% ATTACK`, and her ult is
`ULT_CAST -> +80% ATTACK`. A judge gives her no state, so the technique may never fire there -- which would explain why
the earlier judge's assertions held even with `max_stacks` removed (a false positive).

One question only: is her ATTACK at battle start HIGHER when a rule applying that state is appended? If yes, the
technique fires and a judge can be built on it; if no, the state is not what gates it (or `APPLY_BUFF` does not set it) and
the state route is closed.
ASCII only.
"""
import io

io.open("src/test/java/com/laosun/aluminium/test/StateProbeTest.java",
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

public class StateProbeTest {
    private static final int OWNER = 1401;

    /** \u2b50 Does giving her \u79d8\u6280 change her ATTACK at battle start? */
    @Test
    public void theStateMakesTheTechniqueFire() {
        double plain = attackAtStart(false);
        double withState = attackAtStart(true);
        Assertions.assertTrue(withState > plain,
                "the state must make the technique's +60% land (" + plain + " -> " + withState + ")");
    }

    private static double attackAtStart(boolean giveState) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (giveState) {
            EffectSpec state = new EffectSpec();
            TriggerSpecs.set(state, "op", "APPLY_BUFF");
            TriggerSpecs.set(state, "buff", "\u79d8\u6280");
            TriggerSpecs.set(state, "turns", 3);
            TriggerSpecs.set(state, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), state)))));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.ATTACK).get();
    }
}
''')
print("ok   probe written")
