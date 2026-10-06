package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The RUN TIME number behind {@code per_stack}: a count that only the engine can resolve.
 *
 * <p>The other judges assert the SPELLING (that an effect says {@code per_stack: self_stacks:流光}, that it carries
 * {@code instance}, and so on). This one asserts the NUMBER -- it arms a self counter, applies a
 * {@code MODIFY_ATTR ... per_stack: self_stacks:<that counter>} and requires the attribute to actually move.
 *
 * <p>Why it exists: until the merge of, {@code modifyAttr} resolved {@code per_stack} itself and knew only
 * {@code target_debuff_count} plus a generic counter, so {@code self_stacks:...} fell through to
 * {@code stacksOf("self_stacks:...")} = 0 and the bonus vanished. Note: Red-proved: with the old formula in place this
 * test fails (after == before).
 */
public class PerStackRuntimeTest {
    private static final int WEARER = 1210;
    private static final int MONSTER = 1002011;
    private static final String MARK = "per_stack_runtime_mark";

    @Test
    public void aSelfStackMultiplierActuallyScalesTheBonus() {
        Character wearer = CharacterFactory.create(WEARER, 80);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec add = new EffectSpec();
        TriggerSpecs.set(add, "op", "ADD_STACK");
        TriggerSpecs.set(add, "buff", MARK);
        TriggerSpecs.set(add, "amount", 2.0);
        TriggerSpecs.set(add, "maxStacks", 2);
        TriggerSpecs.set(add, "turns", 99);

        EffectSpec boost = TriggerSpecs.modifyAttr("ATTACK", 0.10, 99);
        TriggerSpecs.set(boost, "perStack", "self_stacks:" + MARK);
        TriggerSpecs.set(boost, "target", "self");

        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of(), add, boost);
        var rules = RelicTriggerTables.parse(99999, Map.of("4", List.of(spec)), "per-stack runtime test");
        var ctx = new TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null, battle, null);

        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        for (var rule : rules.at(4).matching(TriggerEvent.TURN_START, ctx)) {
            TriggerInterpreter.apply(battle, rule, ctx);
        }
        double after = wearer.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertEquals(2, wearer.getBuffManager().stacksOf(MARK), "the counter really stacked twice");
        Assertions.assertTrue(after > before,
                "a self_stacks per_stack must scale the bonus; before=" + before + " after=" + after);
        System.out.println("[perstack] before=" + before + " after=" + after);
    }
}
