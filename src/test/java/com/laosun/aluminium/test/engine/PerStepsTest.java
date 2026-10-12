package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.trigger.TriggerInterpreter;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The milestone primitive's second half: {@code per_steps: "<counter>:<step>"} repeats ONE effect
 * {@code floor(value / step)} times, and {@code SPEND_STEPS} consumes {@code floor(value / step) * step} of that counter.
 *
 * <p>Why they are two things and not one: 1506 Silver Wolf LV.999's Eidolon 2 pays TWO effects per milestone -
 * 「每使【隐藏分】提高 120 点（含初始隐藏分），获得 1 个额外回合并恢复 1 次强化普攻的可施放次数」 - while the counter may be charged
 * only once. An effect that consumed as it counted could not say that, and a per-event "if it is over 120, pay one" check
 * silently under-pays: a single move of 400 is THREE milestones. That case is the first test here, because it is the whole
 * reason the primitive exists.
 *
 * <p>The counting reader was shipped on its own (the field and {@code perStepsRepeat}, with the effect loop untouched);
 * this file pins the wrap and the spending op that complete it.
 */
public class PerStepsTest {
    private static final int ALLY = 1204;
    private static final String COUNTER = "测试配额";
    private static final String TALLY = "测试结果";
    private static final String OTHER_TALLY = "测试结果二";
    private static final int MAX = 10000;
    private static final int STEP = 120;

    private Character unit;
    private Battle battle;

    /**
     * ⚠ The case that justifies the primitive: one move of 400 crosses THREE milestones, not one.
     */
    @Test
    public void oneJumpOfFourHundredPaysThreeMilestones() {
        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), spendSteps()));
        System.out.println("[per_steps] 400 across " + STEP + ": tally " + value(TALLY) + ", counter " + value(COUNTER));
        Assertions.assertEquals(3, value(TALLY),
                "a per-event '>= 120 then spend 120' check would say 1 and silently under-pay");
        Assertions.assertEquals(40, value(COUNTER),
                "and the three milestones it paid for are consumed: 400 - 3 x 120 is the remainder");
    }

    /**
     * Her sentence's own order: TWO effects are counted off the same number, and the counter is charged once - after both.
     */
    @Test
    public void bothEffectsReadTheSameNumberAndOnlyTheLastOneConsumes() {
        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), repeatedGain(OTHER_TALLY, 1), spendSteps()));
        System.out.println("[per_steps] two counted effects: " + value(TALLY) + " and " + value(OTHER_TALLY)
                + ", counter " + value(COUNTER));
        Assertions.assertEquals(3, value(TALLY), "the extra turn, once per milestone");
        Assertions.assertEquals(3, value(OTHER_TALLY),
                "and the charge reads the same number, because counting does not consume");
        Assertions.assertEquals(40, value(COUNTER), "while the counter is charged once, not twice");
    }

    /**
     * An effect that states no {@code per_steps} runs exactly once, which is what keeps every shipped rule unchanged.
     */
    @Test
    public void anEffectWithoutTheFieldRunsOnce() {
        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        TriggerSpecs.gainResource(TALLY, 1)));
        System.out.println("[per_steps] no per_steps with a counter of 400: tally " + value(TALLY));
        Assertions.assertEquals(1, value(TALLY), "absent means once - not 'once per milestone'");
        Assertions.assertEquals(400, value(COUNTER), "and nothing consumed it");
    }

    /**
     * Below one whole step nothing happens: no extra turn, and no error from a spend that has nothing to take.
     */
    @Test
    public void theLastIncompleteStepPaysNothing() {
        gain(STEP - 1,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), spendSteps()));
        System.out.println("[per_steps] " + (STEP - 1) + " across " + STEP + ": tally " + value(TALLY)
                + ", counter " + value(COUNTER));
        Assertions.assertEquals(0, value(TALLY), "119 of 120 pays nothing");
        Assertions.assertEquals(STEP - 1, value(COUNTER), "and there is nothing to consume");
    }

    /**
     * The count is read at DISPATCH time, so a counter moved by an earlier effect of the same rule is already visible -
     * which is what lets a rule grant the counter and then pay for it in the same firing.
     */
    @Test
    public void theCounterMovedByAnEarlierEffectOfTheSameRuleIsAlreadyVisible() {
        unit = unit();
        unit.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule("TURN_START", List.of(),
                TriggerSpecs.gainResource(COUNTER, 250), repeatedGain(TALLY, 1)))));
        fight();
        System.out.println("[per_steps] grant 250 then count it: tally " + value(TALLY) + ", counter " + value(COUNTER));
        Assertions.assertEquals(2, value(TALLY), "floor(250 / 120) read AFTER the grant, not before it");
        Assertions.assertEquals(250, value(COUNTER), "and a counted effect does not consume");
    }

    // ==================================================================
    // The refusals: a bad spelling must fail at LOAD time, not read 0 forever
    // ==================================================================

    /**
     * A step of zero (or one that is not a number) is refused, and the message says what the spelling has to be.
     */
    @Test
    public void aBadStepIsRefusedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(repeatedGain(TALLY, 1, COUNTER, 0), spec("per_steps step 0")));
        System.out.println("[per_steps] step 0 refused: " + zero.getMessage());
        Assertions.assertTrue(zero.getMessage().contains("greater than zero"), "the message says what is wrong");

        IllegalArgumentException malformed = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(with(effect("GAIN_RESOURCE"), "perSteps", COUNTER),
                        spec("per_steps without its step")));
        System.out.println("[per_steps] a counter with no step refused: " + malformed.getMessage());
        Assertions.assertTrue(malformed.getMessage().contains("must be \"<counter>:<step>\""),
                "the message spells the shape out");
    }

    /**
     * A repeat and {@code amount_from_previous} are mutually exclusive: the repeat overwrites what the previous effect
     * booked once per iteration, so the share would be read off the last repetition. Refused rather than silently
     * read that way.
     */
    @Test
    public void aRepeatCannotAlsoReadThePreviousEffectsAmount() {
        EffectSpec effect = repeatedGain(TALLY, 1);
        TriggerSpecs.set(effect, "amountFromPrevious", Boolean.TRUE);
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(effect, spec("per_steps and amount_from_previous")));
        System.out.println("[per_steps] per_steps + amount_from_previous refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("amount_from_previous"), "the message names the pair");
    }

    /**
     * {@code steps} belongs to SPEND_STEPS; on any other op it would be silently ignored.
     */
    @Test
    public void stepsOnAnotherOpIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(with(effect("GAIN_RESOURCE"), "steps", COUNTER + ":" + STEP),
                        spec("steps on GAIN_RESOURCE")));
        System.out.println("[per_steps] steps on GAIN_RESOURCE refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("only SPEND_STEPS reads"), "the message names the reader");
    }

    /**
     * SPEND_STEPS needs its own spelling, and refuses the counting one (it consumes, it does not repeat).
     */
    @Test
    public void spendStepsNeedsStepsAndRefusesPerSteps() {
        IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(effect("SPEND_STEPS"), spec("SPEND_STEPS without steps")));
        System.out.println("[per_steps] SPEND_STEPS without steps refused: " + missing.getMessage());
        Assertions.assertTrue(missing.getMessage().contains("requires \"steps\""), "the message names the field");

        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(with(with(effect("SPEND_STEPS"), "steps", COUNTER + ":" + STEP),
                        "perSteps", COUNTER + ":" + STEP), spec("SPEND_STEPS with per_steps")));
        System.out.println("[per_steps] SPEND_STEPS with per_steps refused: " + both.getMessage());
        Assertions.assertTrue(both.getMessage().contains("consumes milestones rather than"),
                "the message says why the counting field does not belong here");
    }

    /**
     * The counter has to be a real STORE. An attribute has nothing to spend from, and saying so beats quietly keeping
     * the milestones and paying them again on the next change.
     */
    @Test
    public void spendStepsRefusesAnAttributeAsItsCounter() {
        unit = unit();
        unit.setAttribute(AttributeType.SPEED, new DoubleValue(400));
        unit.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule("TURN_START", List.of(),
                spendSteps("SPEED", STEP)))));
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class, this::fight);
        System.out.println("[per_steps] an attribute as the counter refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("no store to spend from"),
                "the message says what an attribute lacks");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A TURN_START rule moves the counter by {@code jump}, then the given rules answer the change.
     */
    private void gain(int jump, TriggerSpec... answering) {
        unit = unit();
        List<TriggerSpec> rules = new ArrayList<>();
        rules.add(TriggerSpecs.rule("TURN_START", List.of(), TriggerSpecs.gainResource(COUNTER, jump)));
        rules.addAll(List.of(answering));
        unit.setTriggerTable(new TriggerTable(1, rules));
        fight();
    }

    private void fight() {
        battle = new Battle(List.of(unit, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, unit, unit, 0, 0);
        battle.processRequests();
    }

    private int value(String resource) {
        return unit.getResources().value(resource);
    }

    /**
     * A unit that declares its counters by hand.
     *
     * <p>⚠ A hand-built character cannot declare a resource the way a document does, so the two counters are registered
     * on its own {@code ResourceManager}. The resolution the primitive reads (an attribute of the owner, one of its
     * counters, or the battle's skill points) is the same one {@code self_attr_above} uses, and the ATTRIBUTE form is
     * exercised by the refusal test below.
     */
    private static Character unit() {
        Character unit = Character.fromAttributes("unit", 1000, 100, 100, 100);
        unit.getResources().register(COUNTER, MAX, 0);
        unit.getResources().register(TALLY, MAX, 0);
        unit.getResources().register(OTHER_TALLY, MAX, 0);
        return unit;
    }

    private static EffectSpec repeatedGain(String resource, int amount) {
        return repeatedGain(resource, amount, COUNTER, STEP);
    }

    private static EffectSpec repeatedGain(String resource, int amount, String counter, int step) {
        return with(TriggerSpecs.gainResource(resource, amount), "perSteps", counter + ":" + step);
    }

    private static EffectSpec spendSteps() {
        return spendSteps(COUNTER, STEP);
    }

    private static EffectSpec spendSteps(String counter, int step) {
        return with(effect("SPEND_STEPS"), "steps", counter + ":" + step);
    }

    private static EffectSpec effect(String op) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", op);
        return effect;
    }

    private static EffectSpec with(EffectSpec effect, String field, Object value) {
        TriggerSpecs.set(effect, field, value);
        return effect;
    }

    private static TriggerSpec spec(String what) {
        return TriggerSpecs.rule("TURN_START", List.of(), what, effect("GAIN_RESOURCE"));
    }
}
