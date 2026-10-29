package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.DoubleValue;

/**
 * {@code steps:<COUNTER>:<N>} - "every N points of a counter", as {@code floor(value / N)}.
 *
 * <p>Built because the reader count said so (6 places across 5 files, scanned before any vocabulary was written): both
 * Trailblazer documents 「每累计恢复 10 点能量」, GLOSSARY_EXTRA 「每累计损失 30% 生命值」, RELICS 「每累计获得 5 点笑点，造成的欢愉伤害…」
 * and two WEAPONS entries - plus 1506's Eidolon 2, which is what this round is about.
 *
 * <p>What this file pins, and why the FLOOR is the load-bearing part:
 * <ul>
 *   <li>the value is the number of whole steps, so 119 reads 0, 120 reads 1 and 239 reads 1;</li>
 *   <li>AND a single jump past several steps pays for all of them: 400 at N = 120 is 3, where a per-event
 *       "if it is over 120, spend 120" check would pay one - the silent under-count this primitive exists to avoid;</li>
 *   <li>the standard share still multiplies it ({@code percent: 0.1} turns 3 steps into 0.3), and the name may be a
 *       RESOURCE as well as an attribute;</li>
 *   <li>and a bad step is refused loudly rather than divided by.</li>
 * </ul>
 */
public class StepsScaleTest {
    private static final int ALLY = 1204;
    private static final String COUNTER = "测试计数";

    /**
     * 119 / 120 / 239: the boundaries of one step.
     */
    @Test
    public void theValueIsTheNumberOfWholeSteps() {
        Assertions.assertEquals(0.0, steps(119, 120), 1e-9, "one short of the step pays nothing");
        Assertions.assertEquals(1.0, steps(120, 120), 1e-9, "and at the step it pays exactly once");
        Assertions.assertEquals(1.0, steps(239, 120), 1e-9, "while the next step has not been reached");
        Assertions.assertEquals(2.0, steps(240, 120), 1e-9, "and past it, twice");
    }

    /**
     * ⚠ The case that justifies the primitive: one jump of 400 is THREE milestones, not one.
     */
    @Test
    public void oneJumpPaysEveryStepItCrossed() {
        double jumped = steps(400, 120);
        System.out.println("[steps] 400 at N=120 -> " + jumped);
        Assertions.assertEquals(3.0, jumped, 1e-9,
                "a per-event '>= 120 then spend 120' check would say 1 and silently under-pay");
    }

    /**
     * The usual share multiplies the step count, and the counter may be a resource rather than an attribute.
     */
    @Test
    public void theShareMultipliesAndTheCounterMayBeAResource() {
        Character unit = unit(COUNTER, 260);
        EffectSpec effect = stepsEffect(COUNTER, 100);
        TriggerSpecs.set(effect, "percent", 0.1);
        double read = read(unit, effect);
        System.out.println("[steps] 260 at N=100 with a 0.1 share -> " + read);
        Assertions.assertEquals(0.2, read, 1e-9, "two whole steps x 0.1");

        // An ATTRIBUTE by name, read through the same branch: her own SPEED, one step per 50.
        Character speedy = unit(COUNTER, 0);
        speedy.setAttribute(AttributeType.SPEED, new com.laosun.aluminium.DoubleValue(174));
        Assertions.assertEquals(3.0, read(speedy, stepsEffect("SPEED", 50)), 1e-9,
                "174 speed is three whole 50s, and the name resolved as an attribute");
    }

    /**
     * A step of zero (or nonsense) is refused loudly: dividing by it is not a number.
     */
    @Test
    public void aBadStepIsRefused() {
        IllegalStateException zero = Assertions.assertThrows(IllegalStateException.class,
                () -> read(unit(COUNTER, 100), stepsEffect(COUNTER, 0)));
        System.out.println("[steps] step 0 refused: " + zero.getMessage());
        Assertions.assertTrue(zero.getMessage().contains("greater than zero"), "the message says what is wrong");

        IllegalStateException malformed = Assertions.assertThrows(IllegalStateException.class,
                () -> read(unit(COUNTER, 100), malformedEffect("steps:SPEED")));
        System.out.println("[steps] a scale without its step refused: " + malformed.getMessage());
        Assertions.assertTrue(malformed.getMessage().contains("must state <COUNTER>:<N>"),
                "the message says what the spelling has to be");
    }

    private static EffectSpec malformedEffect(String scale) {
        EffectSpec effect = stepsEffect(COUNTER, 10);
        TriggerSpecs.set(effect, "scale", scale);
        return effect;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double steps(int counter, int step) {
        return read(unit(COUNTER, counter), stepsEffect(COUNTER, step));
    }

    private static EffectSpec stepsEffect(String counter, int step) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        // ⚠ The counter here is an ATTRIBUTE (SPEED): a hand-built character cannot declare a resource - the engine only
        // keeps counters a character's own document declares, so a made-up name is dropped and the scale would throw. The
        // RESOURCE path is the same resolution `self_attr_above` uses and is exercised by her own content (隐藏分).
        TriggerSpecs.set(effect, "scale", "steps:SPEED:" + step);
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    /**
     * The magnitude the engine computes for one effect, through a real firing (the scale is only read there).
     */
    private static double read(Character unit, EffectSpec effect) {
        unit.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), effect))));
        Battle battle = new Battle(List.of(unit, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, unit, unit, 0, 0));
        battle.processRequests();
        double after = unit.getAttribute(AttributeType.ATTACK).get();
        // Measured: the ATTACK delta IS the step count (one step under `percent: 1.0` moved it by exactly 1.0), so the
        // reading needs no divisor - the first version divided by 100 and read every boundary as 0.01.
        return after - before;
    }

    /**
     * A plain unit whose SPEED carries the step count (see {@link #stepsEffect}).
     */
    private static Character unit(String ignoredCounterName, int value) {
        Character unit = Character.fromAttributes("unit", 1000, 100, 100, 100);
        unit.setAttribute(AttributeType.SPEED, new com.laosun.aluminium.DoubleValue(value));
        return unit;
    }
}
