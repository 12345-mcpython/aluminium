package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.state.ResourceManager;

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
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the six scenarios below, one {@code assertAll} per
 * fixture/clause group so a failing clause still names itself. A case whose trigger table, counter or exception path
 * differs from the rest keeps its own test: the absent-field control, the dispatch-time read, the {@code SPEND_STEPS}
 * spelling refusals and the runtime attribute refusal cannot share a fixture with the wrap.
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

    // ==================================================================
    // 1. The wrap: one effect per whole milestone, counted without consuming, paid for once
    // ==================================================================

    /**
     * ⚠ The case that justifies the primitive: one move of 400 crosses THREE milestones, not one. On the same
     * {@code gain} fixture (a TURN_START rule charges {@code 测试配额}, a RESOURCE_CHANGED rule answers) and the same
     * two observables ({@code 测试结果}, {@code 测试配额}) it also pins that a second counted effect reads the same
     * number before anything is spent, and that a jump below one whole step pays nothing.
     *
     * <p>Three jumps - {@code 400} with one counted effect, {@code 400} with two, {@code 119} - because the
     * under-payment the primitive exists to prevent is a property of the count, not of the effect list.
     */
    @Test
    public void perStepsPaysOneEffectPerWholeMilestoneAndSpendStepsChargesItOnce() {
        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), spendSteps()));
        int tallyOfOneEffect = value(TALLY);
        int counterOfOneEffect = value(COUNTER);
        System.out.println("[per_steps] 400 across " + STEP + ": tally " + tallyOfOneEffect
                + ", counter " + counterOfOneEffect);

        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), repeatedGain(OTHER_TALLY, 1), spendSteps()));
        int tallyOfTwoEffects = value(TALLY);
        int otherTally = value(OTHER_TALLY);
        int counterOfTwoEffects = value(COUNTER);
        System.out.println("[per_steps] two counted effects: " + tallyOfTwoEffects + " and " + otherTally
                + ", counter " + counterOfTwoEffects);

        gain(STEP - 1,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        repeatedGain(TALLY, 1), spendSteps()));
        int tallyBelowAStep = value(TALLY);
        int counterBelowAStep = value(COUNTER);
        System.out.println("[per_steps] " + (STEP - 1) + " across " + STEP + ": tally " + tallyBelowAStep
                + ", counter " + counterBelowAStep);

        Assertions.assertAll("per_steps pays per whole milestone, and SPEND_STEPS takes back only those milestones",
                () -> Assertions.assertEquals(3, tallyOfOneEffect,
                        "a per-event '>= 120 then spend 120' check would say 1 and silently under-pay"),
                () -> Assertions.assertEquals(40, counterOfOneEffect,
                        "and the three milestones it paid for are consumed: 400 - 3 x 120 is the remainder"),
                () -> Assertions.assertEquals(3, tallyOfTwoEffects, "the extra turn, once per milestone"),
                () -> Assertions.assertEquals(3, otherTally,
                        "and the charge reads the same number, because counting does not consume"),
                () -> Assertions.assertEquals(40, counterOfTwoEffects,
                        "while the counter is charged once, not twice"),
                () -> Assertions.assertEquals(0, tallyBelowAStep, "119 of 120 pays nothing"),
                () -> Assertions.assertEquals(STEP - 1, counterBelowAStep, "and there is nothing to consume"));
    }

    // ==================================================================
    // 2. The control that keeps every shipped rule unchanged: no field, one run
    // ==================================================================

    /**
     * An effect that states no {@code per_steps} runs exactly once, which is what keeps every shipped rule unchanged.
     * Its answering rule has no {@code per_steps} at all, so it is the one positive case that cannot share a trigger
     * table with the wrap above.
     */
    @Test
    public void anEffectWithoutTheFieldRunsOnce() {
        gain(400,
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:" + COUNTER),
                        TriggerSpecs.gainResource(TALLY, 1)));
        int tally = value(TALLY);
        int counter = value(COUNTER);
        System.out.println("[per_steps] no per_steps with a counter of 400: tally " + tally);
        Assertions.assertAll("only a declared per_steps repeats",
                () -> Assertions.assertEquals(1, tally, "absent means once - not 'once per milestone'"),
                () -> Assertions.assertEquals(400, counter, "and nothing consumed it"));
    }

    // ==================================================================
    // 3. The count is read at DISPATCH time
    // ==================================================================

    /**
     * The count is read at DISPATCH time, so a counter moved by an earlier effect of the same rule is already visible -
     * which is what lets a rule grant the counter and then pay for it in the same firing.
     *
     * <p>One fixture of its own (a TURN_START rule that grants {@code 250} and then reads it) and one observable
     * ({@code 测试结果}, {@code 测试配额}), because the grant and the count sit in the SAME rule here.
     */
    @Test
    public void theCounterMovedByAnEarlierEffectOfTheSameRuleIsAlreadyVisible() {
        unit = unit();
        unit.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule("TURN_START", List.of(),
                TriggerSpecs.gainResource(COUNTER, 250), repeatedGain(TALLY, 1)))));
        fight();
        int tally = value(TALLY);
        int counter = value(COUNTER);
        System.out.println("[per_steps] grant 250 then count it: tally " + tally + ", counter " + counter);
        Assertions.assertAll("a rule may grant the counter and count it in the same firing",
                () -> Assertions.assertEquals(2, tally, "floor(250 / 120) read AFTER the grant, not before it"),
                () -> Assertions.assertEquals(250, counter, "and a counted effect does not consume"));
    }

    // ==================================================================
    // 4. The refusals: a bad spelling must fail at LOAD time, not read 0 forever
    // ==================================================================

    /**
     * Load time, not forever-zero: a step of zero (or a spec that does not spell {@code <counter>:<step>}) is refused
     * with a message saying what the spelling has to be; a repeat cannot also read {@code amount_from_previous} (it
     * overwrites what the previous effect booked once per iteration, so the share would be read off the last
     * repetition); and {@code steps} belongs to SPEND_STEPS - on any other op it would be silently ignored.
     *
     * <p>One fixture (an effect spec validated by {@link TriggerInterpreter#validate}) and one observable (the
     * load-time refusal and the field its message names) - and it stays apart from every positive scenario above,
     * because an {@code assertThrows} is not a positive clause.
     */
    @Test
    public void aBadPerStepsIsRefusedAtLoadTime() {
        Assertions.assertAll("the per_steps spelling is refused at load time, with a message that names what is wrong",
                () -> {
                    IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(repeatedGain(TALLY, 1, COUNTER, 0),
                                    spec("per_steps step 0")),
                            "a step of zero is refused");
                    System.out.println("[per_steps] step 0 refused: " + zero.getMessage());
                    Assertions.assertTrue(zero.getMessage().contains("greater than zero"),
                            "the message says what is wrong");
                },
                () -> {
                    IllegalArgumentException malformed = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(with(effect("GAIN_RESOURCE"), "perSteps", COUNTER),
                                    spec("per_steps without its step")),
                            "a counter with no step is refused");
                    System.out.println("[per_steps] a counter with no step refused: " + malformed.getMessage());
                    Assertions.assertTrue(malformed.getMessage().contains("must be \"<counter>:<step>\""),
                            "the message spells the shape out");
                },
                () -> {
                    EffectSpec conflict = repeatedGain(TALLY, 1);
                    TriggerSpecs.set(conflict, "amountFromPrevious", Boolean.TRUE);
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(conflict, spec("per_steps and amount_from_previous")),
                            "per_steps together with amount_from_previous is refused");
                    System.out.println("[per_steps] per_steps + amount_from_previous refused: "
                            + refused.getMessage());
                    Assertions.assertTrue(refused.getMessage().contains("amount_from_previous"),
                            "the message names the pair");
                },
                () -> {
                    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(
                                    with(effect("GAIN_RESOURCE"), "steps", COUNTER + ":" + STEP),
                                    spec("steps on GAIN_RESOURCE")),
                            "steps on GAIN_RESOURCE is refused");
                    System.out.println("[per_steps] steps on GAIN_RESOURCE refused: " + refused.getMessage());
                    Assertions.assertTrue(refused.getMessage().contains("only SPEND_STEPS reads"),
                            "the message names the reader");
                });
    }

    /**
     * {@code SPEND_STEPS} needs its own spelling, and refuses the counting one (it consumes, it does not repeat).
     *
     * <p>One fixture (an effect spec whose op is SPEND_STEPS, validated by {@link TriggerInterpreter#validate}) and the
     * same observable (the load-time refusal and the field its message names); it is a different op from the
     * {@code per_steps} spelling above, so it keeps its own validation scenario.
     */
    @Test
    public void spendStepsNeedsStepsAndRefusesPerSteps() {
        Assertions.assertAll("SPEND_STEPS needs \"steps\" and refuses the counting field",
                () -> {
                    IllegalArgumentException missing = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(effect("SPEND_STEPS"),
                                    spec("SPEND_STEPS without steps")),
                            "SPEND_STEPS without steps is refused");
                    System.out.println("[per_steps] SPEND_STEPS without steps refused: " + missing.getMessage());
                    Assertions.assertTrue(missing.getMessage().contains("requires \"steps\""),
                            "the message names the field");
                },
                () -> {
                    IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> TriggerInterpreter.validate(
                                    with(with(effect("SPEND_STEPS"), "steps", COUNTER + ":" + STEP),
                                            "perSteps", COUNTER + ":" + STEP),
                                    spec("SPEND_STEPS with per_steps")),
                            "SPEND_STEPS with per_steps is refused");
                    System.out.println("[per_steps] SPEND_STEPS with per_steps refused: " + both.getMessage());
                    Assertions.assertTrue(both.getMessage().contains("consumes milestones rather than"),
                            "the message says why the counting field does not belong here");
                });
    }

    // ==================================================================
    // 5. At run time: the counter has to be a STORE
    // ==================================================================

    /**
     * The counter has to be a real STORE. An attribute has nothing to spend from, and saying so beats quietly keeping
     * the milestones and paying them again on the next change.
     *
     * <p>This is a run-time refusal on a hand-built unit (its counter resolves to an attribute, not a resource), so it
     * is a fixture and an exception type of its own - it stays apart from the load-time validations above.
     */
    @Test
    public void spendStepsRefusesAnAttributeAsItsCounter() {
        unit = unit();
        unit.setAttribute(AttributeType.SPEED, new DoubleValue(400));
        unit.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule("TURN_START", List.of(),
                spendSteps("SPEED", STEP)))));
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class, this::fight,
                "an attribute as the counter is refused when the rule fires");
        System.out.println("[per_steps] an attribute as the counter refused: " + refused.getMessage());
        Assertions.assertAll("an attribute is not a store to spend from",
                () -> Assertions.assertTrue(refused.getMessage().contains("no store to spend from"),
                        "the message says what an attribute lacks"));
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
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, unit, unit, 0, 0));
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
