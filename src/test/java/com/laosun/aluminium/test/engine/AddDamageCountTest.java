package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A COUNT is not an amount: `ADD_DAMAGE` refuses `times` instead of ignoring it.
 *
 * <p>Found while reading 1415's ode of passage, whose second sentence is "…会<b>额外造成 #1 次附加伤害</b>". The sentence counts INSTANCES, the
 * obvious-looking spelling is `ADD_DAMAGE` with `times: 1`, and `ADD_DAMAGE`'s worker is `damage.addFlat(derivedMagnitude(...))` -- it adds ONE
 * amount to the damage being settled. A `times` there would be read by nobody: the rule would load, fire, and deliver one flat addition while
 * looking exactly like the sentence. That is the silence this project refuses, so the field is refused at load time with a message saying what it
 * would have meant.
 */
public class AddDamageCountTest {
    private static final int WEARER = 1402;

    @Test
    public void addDamageRefusesACount() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:DEFENCE");
        TriggerSpecs.set(effect, "percent", 0.3);
        TriggerSpecs.set(effect, "times", 1);
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", List.of(), effect);

        boolean threw = false;
        try {
            Character owner = CharacterFactory.create(WEARER, 80);
            owner.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));
        } catch (IllegalArgumentException | IllegalStateException expected) {
            threw = true;
            String message = String.valueOf(expected.getMessage());
            System.out.println("[add_damage] times -> " + expected.getClass().getSimpleName() + ": "
                    + message.substring(0, Math.min(150, message.length())));
            Assertions.assertTrue(message.contains("times"), "the message must name the field it refused");
        }
        Assertions.assertTrue(threw, "a count on an amount is refused, not silently dropped");
    }

    /** The same effect without the count still loads -- the guard is not a blanket refusal. */
    @Test
    public void theAmountItselfStillLoads() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:DEFENCE");
        TriggerSpecs.set(effect, "percent", 0.3);
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", List.of(), effect);

        Character owner = CharacterFactory.create(WEARER, 80);
        Assertions.assertDoesNotThrow(() -> owner.setTriggerTable(new TriggerTable(WEARER, List.of(rule))),
                "「使反击造成的伤害值提高…」 (raises the damage a counterattack deals ...) (1001) is exactly this shape and must keep working");
    }
}
