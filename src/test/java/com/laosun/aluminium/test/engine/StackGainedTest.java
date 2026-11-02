package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code STACK_GAINED} + {@code <who> stack_added <name>}: the fact that a named counter just gained a layer.
 *
 * <p><b>Why an event and not "do I hold any".</b> 「每获得 1 层【斗志】，额外恢复 3 点能量」 (1111 Luka's trace 循环制动) is about the
 * <b>gaining</b>. Read off {@code self_stacks:斗志 >= 1} it would pay again every time an unrelated event fired while the
 * layers sat there — a wrong number with no symptom, which is why the name rides on the payload.
 *
 * <p>The rules are built here ({@code TriggerSpecs}, the factory {@code LethalSetTest} uses) because what is judged is the
 * event and its name filter, not Luka's sentence: one rule adds a layer of [标记] on a Basic ATK, another adds a layer of
 * [另一个] on a Skill, and a third pays 5 energy per layer of [标记] only.
 */
public class StackGainedTest {
    private static final int OWNER = 1111;

    @Test
    public void oneFiringPerLayerAndOnlyForTheNamedCounter() {
        Payload scene = new Payload();
        double before = scene.owner.getCurrentEnergy();

        scene.fire(TriggerEvent.BASIC_ATTACK);
        Assertions.assertEquals(before + 5, scene.owner.getCurrentEnergy(), 1e-9,
                "one layer ⇒ one firing ⇒ 5 energy");

        double afterOne = scene.owner.getCurrentEnergy();
        scene.fire(TriggerEvent.BASIC_ATTACK);
        Assertions.assertEquals(afterOne + 5, scene.owner.getCurrentEnergy(), 1e-9,
                "and another layer pays again — the sentence is about gaining, not about holding");

        double afterTwo = scene.owner.getCurrentEnergy();
        scene.fire(TriggerEvent.SKILL_CAST);
        Assertions.assertEquals(afterTwo, scene.owner.getCurrentEnergy(), 1e-9,
                "the rule names 标记, so another counter's layer pays nothing");
    }

    // ==================================================================

    private static final class Payload {
        private final Character owner = CharacterFactory.create(OWNER, 80);
        private final Battle battle;

        private Payload() {
            battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                    addRule("BASIC_ATTACK", "标记"),
                    addRule("SKILL_CAST", "另一个"),
                    payRule())));
            battle.startBattle();
            battle.processRequests();
        }

        private static TriggerSpec addRule(String event, String counter) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "ADD_STACK");
            TriggerSpecs.set(effect, "buff", counter);
            TriggerSpecs.set(effect, "amount", 1.0);
            TriggerSpecs.set(effect, "maxStacks", 9);
            TriggerSpecs.set(effect, "permanent", true);
            TriggerSpecs.set(effect, "target", "self");
            return TriggerSpecs.rule(event, List.of("actor == self"), effect);
        }

        private static TriggerSpec payRule() {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
            TriggerSpecs.set(effect, "amount", 5.0);
            return TriggerSpecs.rule("STACK_GAINED", List.of("self stack_added 标记"), effect);
        }

        private void fire(TriggerEvent event) {
            battle.fire(TriggerPayload.Simple.of(event, owner, owner, 0, 0));
            battle.processRequests();
        }
    }
}


