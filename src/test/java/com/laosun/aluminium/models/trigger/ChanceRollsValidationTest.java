package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.beans.TriggerSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

/**
 * The load-time half of {@code chance_rolls}: the two shapes that say nothing are refused when the table is read.
 *
 * <p>⚠ Why reflection to set the field: {@code TriggerSpec} is a Gson-mapped bean with getters only (@Getter), so
 * there is no setter to call - which is exactly what "content authors write JSON, not Java" looks like. The
 * alternative, shipping a deliberately broken rule in {@code resources/characters/}, would leave a permanently red
 * rule in the corpus for every other content guard to tolerate.
 *
 * <p>⚠ Why these are worth asserting at all: both refusals guard the "no symptom" failure mode. A
 * {@code chance_rolls: 0} rule never fires, and a {@code chance_rolls: 2} rule with no {@code chance} rolls
 * "always" twice - neither looks broken from the outside, and both would read as if the field were absent.
 */
public class ChanceRollsValidationTest {

    private static void set(TriggerSpec spec, String field, Object value) throws Exception {
        Field target = TriggerSpec.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(spec, value);
    }

    private static TriggerSpec specWith(Double chance, Integer rolls) {
        TriggerSpec spec = new TriggerSpec();
        try {
            set(spec, "chance", chance);
            set(spec, "chanceRolls", rolls);
        } catch (Exception e) {
            throw new AssertionError("could not build the spec", e);
        }
        return spec;
    }

    /** Absent means one roll, the behaviour every shipped rule has. */
    @Test
    public void absentMeansOneRoll() {
        Assertions.assertEquals(1, TriggerTable.validateChanceRolls(specWith(0.33, null)));
        Assertions.assertEquals(1, TriggerTable.validateChanceRolls(specWith(null, null)));
    }

    /** A stated count is returned as-is, and {@code 1} is legal because it IS the default. */
    @Test
    public void aStatedCountIsKept() {
        Assertions.assertEquals(2, TriggerTable.validateChanceRolls(specWith(0.33, 2)));
        Assertions.assertEquals(1, TriggerTable.validateChanceRolls(specWith(0.33, 1)));
    }

    /** "Roll zero times" is not a count: the rule could never fire, and nothing would say so. */
    @Test
    public void zeroRollsIsRefused() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerTable.validateChanceRolls(specWith(0.33, 0)));
        Assertions.assertTrue(thrown.getMessage().contains("chance_rolls"), thrown.getMessage());
    }

    /** "Roll always twice" says nothing: there is no probability to roll twice. */
    @Test
    public void rollsWithoutAChanceAreRefused() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerTable.validateChanceRolls(specWith(null, 2)));
        Assertions.assertTrue(thrown.getMessage().contains("chance"), thrown.getMessage());
    }
}
