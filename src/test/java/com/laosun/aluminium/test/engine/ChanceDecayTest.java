package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code chance_decay}: a rule's fixed probability is its INITIAL one, and each successful trigger multiplies it.
 *
 * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「触发该效果的初始固定概率为 100%，成功触发后下一次触发的固定概率降低至当前概率的
 * 20%」 - with {@code RESET_RULE_CHANCE} as the other half (「将触发…的固定概率重置为初始值」).
 *
 * <p>⚠ The chance is read through the ROLL here, not through a getter: a hand-built rule gains one point of a declared
 * counter per firing, so the count IS the number of successful triggers, and the scripted draw decides each roll - 0.9
 * cannot pass 20%, and the same 0.1 that passes 20% fails 4%.
 */
public class ChanceDecayTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
        /** A resource 1506's own file declares, so the fixture does not have to invent one. */
    private static final String COUNTER = "隐藏分";
    private static final String BOX = "test_decaying_rule";

    /** 100% (no draw taken) → 20% → 4%, and a failed roll leaves the number alone. */
    @Test
    public void eachSuccessMultipliesTheChanceAndAFailureDoesNot() {
        ScriptedRng rng = new ScriptedRng();
        Battle battle = battle(rng, List.of(
                rule(BOX, "TURN_START", 1.0, 0.2, gainOne()),
                rule("test_reset", "ULT_CAST", null, null, reset(BOX))));
        Character wolf = battle.characters.getFirst();

        fire(battle, rng, 0.9);
        Assertions.assertEquals(1, wolf.getResources().value(COUNTER),
                "the initial chance is 100%, so the first firing is certain - and it takes no draw at all");

        fire(battle, rng, 0.9);
        Assertions.assertEquals(1, wolf.getResources().value(COUNTER),
                "after one success the chance is 20%, so a 0.9 draw fires nothing");

        fire(battle, rng, 0.1);
        Assertions.assertEquals(2, wolf.getResources().value(COUNTER),
                "and that failure decayed nothing: a 0.1 draw still passes 20%");

        fire(battle, rng, 0.1);
        Assertions.assertEquals(2, wolf.getResources().value(COUNTER),
                "a second success makes it 4%, so the 0.1 draw that passed at 20% now fires nothing");

        battle.fireTriggers(TriggerEvent.ULT_CAST, wolf, wolf, 0, 0);
        battle.processRequests();
        fire(battle, rng, 0.9);
        Assertions.assertEquals(3, wolf.getResources().value(COUNTER),
                "RESET_RULE_CHANCE puts it back to the initial value, so a 0.9 draw cannot stop it again");
    }

    /** Every combination that would decay nothing, or make a rule that can never fire again, is refused at load. */
    @Test
    public void theTwoShapesOfNonsenseAreRefused() {
        assertRefused(rule(BOX, "TURN_START", null, 0.2, gainOne()), "chance");
        assertRefused(rule(BOX, "TURN_START", 1.0, 1.0, gainOne()), "decay");
        assertRefused(rule(BOX, "TURN_START", 1.0, 0.0, gainOne()), "decay");
    }

    /** A reset that names a rule which does not decay would change nothing, so it is refused rather than loaded. */
    @Test
    public void resettingARuleThatDoesNotDecayIsRefused() {
        assertRefused(List.of(
                rule("test_plain", "TURN_START", 0.5, null, gainOne()),
                rule("test_reset", "ULT_CAST", null, null, reset("test_plain"))), "chance_decay");
        assertRefused(List.of(
                rule("test_reset", "ULT_CAST", null, null, reset("no_such_rule"))), "not in this file");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static void assertRefused(TriggerSpec spec, String names) {
        assertRefused(List.of(spec), names);
    }

    private static void assertRefused(List<TriggerSpec> specs, String names) {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(WOLF, specs));
        System.out.println("[chance_decay] refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains(names),
                "the message must name the field it is about, and it says: " + error.getMessage());
    }

    /** One firing of {@code TURN_START} with the given draw. */
    private static void fire(Battle battle, ScriptedRng rng, double draw) {
        rng.nextDoubleValue = draw;
        Character wolf = battle.characters.getFirst();
        battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
        battle.processRequests();
    }

    private static Battle battle(ScriptedRng rng, List<TriggerSpec> specs) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        battle.characters.getFirst().setTriggerTable(new TriggerTable(WOLF, specs));
        return battle;
    }

    private static TriggerSpec rule(String id, String event, Double chance, Double decay, EffectSpec effect) {
        TriggerSpec spec = TriggerSpecs.rule(event, List.of(), effect);
        TriggerSpecs.set(spec, "id", id);
        TriggerSpecs.set(spec, "chance", chance);
        TriggerSpecs.set(spec, "chanceDecay", decay);
        return spec;
    }

    /** One point of a declared counter per firing, so the counter's value counts the successful triggers. */
    private static EffectSpec gainOne() {
        EffectSpec effect = TriggerSpecs.gainResource(COUNTER, 1);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static EffectSpec reset(String ruleId) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RESET_RULE_CHANCE");
        TriggerSpecs.set(effect, "rule", ruleId);
        return effect;
    }

    /** {@code nextDouble} is whatever the case set; nothing here draws an index. */
    private static final class ScriptedRng extends Random {
        private double nextDoubleValue = 0.9999;

        @Override
        public double nextDouble() {
            return nextDoubleValue;
        }
    }
}
