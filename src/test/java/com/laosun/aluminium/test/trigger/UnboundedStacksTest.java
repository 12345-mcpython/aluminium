package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code unbounded_stacks}: a counter whose sentence gives no ceiling must keep every layer.
 *
 * <p>Without the ask an {@code ADD_STACK} that states no {@code max_stacks} holds exactly one layer (right for a marker,
 * wrong for a resource-like counter) - the shape 1308's registration recorded as "cannot be expressed".
 */
public class UnboundedStacksTest {

    private static final int CID = 1001;
    private static final String MARK = "无限标记";

    @Test
    public void withoutTheAskTheCounterHoldsOneLayer() {
        Assertions.assertEquals(1, layersAfterFiveTurns(withoutAsk()), "the documented default is one layer");
    }

    @Test
    public void unboundedKeepsEveryLayer() {
        Assertions.assertEquals(5, layersAfterFiveTurns(unbounded()), "「没有上限」 means every layer stays");
    }

    @Test
    public void theTwoCeilingsCannotBeStatedTogether() {
        EffectSpec effect = unbounded();
        TriggerSpecs.set(effect, "maxStacks", 3);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(CID, List.of(TriggerSpecs.rule("TURN_START", List.of(), effect))));
        Assertions.assertTrue(error.getMessage().contains("unbounded_stacks"), error.getMessage());
    }

    // ==================================================================

    private static EffectSpec withoutAsk() {
        return stack(null);
    }

    private static EffectSpec unbounded() {
        return stack(true);
    }

    private static EffectSpec stack(Boolean unbounded) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", MARK);
        TriggerSpecs.set(effect, "amount", 1.0d);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        if (unbounded != null) {
            TriggerSpecs.set(effect, "unboundedStacks", unbounded);
        }
        return effect;
    }

    private static int layersAfterFiveTurns(EffectSpec effect) {
        Character unit = CharacterFactory.create(CID, 80);
        unit.setTriggerTable(new TriggerTable(CID,
                List.of(TriggerSpecs.rule("TURN_START", List.of(), effect))));
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1002, 80))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        for (int i = 0; i < 5; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, unit, unit, 0, 0));
        }
        return unit.getBuffManager().stacksOf(MARK);
    }
}

