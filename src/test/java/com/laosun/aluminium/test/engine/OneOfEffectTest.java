package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code one_of}: a rule executes ONE of its own effects, chosen at random.
 *
 * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「随机触发 1 种效果」 - and light cone 21032's 「随机生效 1 个效果」.
 *
 * <p>⚠ The reading that matters is a COUNT, not "did X happen": the two wrong implementations are "both ran" and "neither
 * ran", and {@code assertTrue(X appeared)} is satisfied by the first of those. So the fixture gives each effect its own
 * visible marker and asserts that EXACTLY one appears per firing, over several firings with different seeds.
 *
 * <p>The two refusals beside it are the "two readings" shape this vocabulary will not carry, and each has its own case.
 */
public class OneOfEffectTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String A = "标记A";
    private static final String B = "标记B";
    private static final String C = "标记C";

    /**
     * Exactly one of the three markers is applied, and which one moves with the seed.
     */
    @Test
    public void exactlyOneEffectRuns() {
        boolean sawA = false;
        boolean sawB = false;
        for (int seed = 0; seed < 24; seed++) {
            Character wolf = marksAfterOneFiring(seed);
            int count = (wolf.getBuffManager().hasState(A) ? 1 : 0)
                    + (wolf.getBuffManager().hasState(B) ? 1 : 0)
                    + (wolf.getBuffManager().hasState(C) ? 1 : 0);
            Assertions.assertEquals(1, count,
                    "seed " + seed + ": exactly one effect must run - 0 means none was picked, 2+ means the roll "
                            + "happened per effect instead of once");
            sawA |= wolf.getBuffManager().hasState(A);
            sawB |= wolf.getBuffManager().hasState(B);
        }
        System.out.println("[one_of] over 24 seeds: saw A=" + sawA + " saw B=" + sawB);
        Assertions.assertTrue(sawA && sawB,
                "and the pick varies with the roll - always the same effect would be a constant, not a choice");
    }

    /**
     * Without the field, EVERY effect runs - which is what makes the field meaningful.
     */
    @Test
    public void withoutTheFieldAllEffectsRun() {
        Character wolf = marksAfterOneFiring(0, false);
        int count = (wolf.getBuffManager().hasState(A) ? 1 : 0)
                + (wolf.getBuffManager().hasState(B) ? 1 : 0)
                + (wolf.getBuffManager().hasState(C) ? 1 : 0);
        System.out.println("[one_of] with no field: " + count + " of 3 markers applied");
        Assertions.assertEquals(3, count, "a rule without `one_of` runs all of its effects, as it always did");
    }

    /**
     * An effect marked {@code always} runs EVERY firing while exactly one of the rest runs.
     *
     * <p>Reader: 1506's 【头号补给盲盒】, whose own 90% is 「造成…伤害，<b>并</b>随机触发 1 种效果」 - the box's damage is not one of
     * the three that can be picked. It is the same rule and not a second one because the box's fixed chance is one roll.
     */
    @Test
    public void anAlwaysEffectStaysOutOfThePick() {
        boolean sawA = false;
        boolean sawB = false;
        int countA = 0;
        int countB = 0;
        for (int seed = 0; seed < 24; seed++) {
            Character wolf = marksWithAnAlwaysEffect(seed);
            boolean constant = wolf.getBuffManager().hasState(C);
            boolean a = wolf.getBuffManager().hasState(A);
            boolean b = wolf.getBuffManager().hasState(B);
            int picked = (a ? 1 : 0) + (b ? 1 : 0);
            System.out.println("[one_of] always seed " + seed + ": constant=" + constant + " A=" + a + " B=" + b);
            Assertions.assertTrue(constant,
                    "seed " + seed + ": the `always` effect must run every firing, picked or not");
            Assertions.assertEquals(1, picked,
                    "seed " + seed + ": exactly one of the two POOL effects runs - 0 means the always-effect "
                            + "swallowed the pick, 2 means the roll went per effect");
            countA += a ? 1 : 0;
            countB += b ? 1 : 0;
            sawA |= a;
            sawB |= b;
        }
        System.out.println("[one_of] always over 24 seeds: A=" + countA + " B=" + countB);
        Assertions.assertTrue(sawA && sawB, "and the pool is what the roll chooses from - both of its effects appear");
    }

    /**
     * `always` on a rule with no `one_of` marks nothing: it reads like a forgotten field, so it is refused.
     */
    @Test
    public void alwaysWithoutOneOfIsRefused() {
        EffectSpec constant = mark(A);
        TriggerSpecs.set(constant, "always", true);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("TURN_START", List.of(), constant, mark(B))));
        System.out.println("[one_of] `always` without the field refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field it needs");
    }

    /**
     * And so is a WEIGHT on a rule with no `one_of`: there is no pick for it to be a share of.
     */
    @Test
    public void aWeightWithoutOneOfIsRefused() {
        EffectSpec weighted = mark(A);
        TriggerSpecs.set(weighted, "weight", 10.0);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("TURN_START", List.of(), weighted, mark(B))));
        System.out.println("[one_of] a weight without the field refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field it needs");
    }

    /**
     * The POOL is what "at least two" counts: one constant plus one pick is "pick one of one".
     */
    @Test
    public void aPoolOfOneBesideAnAlwaysEffectIsRefused() {
        EffectSpec constant = mark(C);
        TriggerSpecs.set(constant, "always", true);
        EffectSpec onlyPick = mark(A);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of(), onlyPick, constant);
        TriggerSpecs.set(spec, "oneOf", true);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class, () -> table(spec));
        System.out.println("[one_of] a pool of one refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field");
    }

    /**
     * "Pick one of one" states nothing, and reads exactly like a rule that forgot the field.
     */
    @Test
    public void aSingleEffectWithTheFieldIsRefused() {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(spec(true, 1)));
        System.out.println("[one_of] one effect refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("one_of"), "the message names the field");
    }

    /**
     * Combined with a per-effect `min_eidolon` the two filters compose two ways, so the pair is refused.
     */
    @Test
    public void theFieldBesideAPerEffectEidolonGateIsRefused() {
        EffectSpec first = mark(A);
        EffectSpec gated = mark(B);
        TriggerSpecs.set(gated, "minEidolon", 2);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"), first, gated);
        TriggerSpecs.set(spec, "oneOf", true);
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class, () -> table(spec));
        System.out.println("[one_of] with a per-effect Eidolon gate refused: " + error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("min_eidolon"), "the message names the conflict");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Character marksAfterOneFiring(int seed) {
        return marksAfterOneFiring(seed, true);
    }

    /**
     * The same firing, with a rule of three marks where the THIRD is {@code always}: A/B are the pool, C is constant.
     */
    private static Character marksWithAnAlwaysEffect(int seed) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), new Random(seed));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        EffectSpec constant = mark(C);
        TriggerSpecs.set(constant, "always", true);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"), mark(A), mark(B), constant);
        TriggerSpecs.set(spec, "oneOf", true);
        wolf.setTriggerTable(table(spec));
        // ⚠ Burn four draws first, and the reason is measured rather than tidy: `nextInt(2)` takes the
        // POWER-OF-TWO branch of `java.util.Random`, which reads the TOP bit of `next(31)` - and the FIRST draw of
        // `new Random(seed)` has nearly the same top bits for small seeds. Measured: two effects and 24 seeds 0..23
        // answered 1 every single time, so the pool looked like a constant. Three effects hid it, because `nextInt(3)`
        // takes the other branch and reads the LOW bits. Nothing here is wrong with the engine - it is how this fixture
        // must hand it a source, and a battle that draws once before its first pick would never see it.
        for (int draw = 0; draw < 4; draw++) {
            battle.getRng().nextInt();
        }
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
        battle.processRequests();
        return wolf;
    }

    /**
     * Applies one firing of a three-effect rule and returns the unit, so the caller can read the markers.
     */
    private static Character marksAfterOneFiring(int seed, boolean oneOf) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), new Random(seed));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        wolf.setTriggerTable(table(spec(oneOf, 3)));
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
        battle.processRequests();
        return wolf;
    }

    private static TriggerTable table(TriggerSpec spec) {
        return new TriggerTable(WOLF, List.of(spec));
    }

    /**
     * A rule whose N effects each mark the unit, so a count says how many ran.
     */
    private static TriggerSpec spec(boolean oneOf, int effects) {
        List<EffectSpec> marks = List.of(mark(A), mark(B), mark(C)).subList(0, effects);
        TriggerSpec spec = TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                marks.toArray(new EffectSpec[0]));
        if (oneOf) {
            TriggerSpecs.set(spec, "oneOf", true);
        }
        return spec;
    }

    private static EffectSpec mark(String state) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", state);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
