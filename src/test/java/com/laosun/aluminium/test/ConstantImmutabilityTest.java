package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * H-1: the tables loaded into {@link Constant} are <b>read-only shared engine data</b>.
 *
 * <p>{@code public static final} locks the reference, not the contents. Before this, every table was a
 * mutable {@code LinkedHashMap} straight out of Gson and the nesting was mutable too, so any caller — a
 * test, a future UI, a plugin — could do {@code Constant.SKILLS.clear()} or
 * {@code Constant.SKILLS.get(cid).put(...)} and silently change what <b>every later consumer in the same
 * JVM</b> sees. No compile error, no failing test, and the damage is invisible until something unrelated
 * reads the table much later.
 *
 * <p><b>Every operation here is chosen to be harmless if the guard is missing.</b> The first version of
 * this test used {@code clear()} and {@code put(...)}, and when it was mutated back to a writable table it
 * actually <b>emptied {@code SKILL_TRACES} for the whole JVM</b> — nine unrelated tests failed in suites
 * that had nothing to do with this one, and the run was unreadable. A test whose failure damages shared
 * state makes every mutation check a lottery, so the operations are now:
 * <ul>
 *   <li>maps → {@code remove(<key that is definitely not present>)}: a no-op on a writable map,
 *       {@link UnsupportedOperationException} on a read-only one;</li>
 *   <li>lists → {@code set(<out-of-range index>, …)}: {@code IndexOutOfBoundsException} on a writable
 *       list (still no change to the data), {@link UnsupportedOperationException} on a read-only one.</li>
 * </ul>
 * The mutation therefore reddens this test and touches nothing else — which is what makes the result
 * meaningful.
 *
 * <p>The tests are split because the fix has two layers, and a regression in either would otherwise hide
 * behind the other: wrapping the top level but forgetting to recurse leaves the nested containers writable,
 * which is exactly the case {@code SKILLS.get(cid)} and {@code SKILL_TRACES.get(cid)} are.
 */
public class ConstantImmutabilityTest {

    /** A key no table has: removing it changes nothing even when the map is writable. */
    private static final int ABSENT_KEY = Integer.MIN_VALUE;

    @Test
    public void theLoadedTablesRejectTopLevelMutation() {
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.WEAPONS.remove(ABSENT_KEY), "the weapon table is shared engine data");
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.CHARACTERS.remove(ABSENT_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.SKILLS.remove(ABSENT_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.BREAKING_RATE.remove(ABSENT_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.HARD_LEVEL_GROUPS.remove(ABSENT_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.MONSTER_TEMPLATES.remove(ABSENT_KEY));
        // Already immutable before this task (RelicSets.index ends in Map.copyOf) -- pinned so that a
        // future change there cannot quietly hand out a writable table.
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Constant.RELIC_SETS.remove(ABSENT_KEY));
    }

    /**
     * The nested containers are frozen as well — this is the half the concrete examples in H-1 were about
     * ({@code SKILLS[1001]} was a mutable map, {@code SKILL_TRACES[1001]} a mutable list).
     */
    @Test
    public void theNestedContainersRejectMutationToo() {
        Integer cid = Constant.SKILLS.keySet().iterator().next();
        Map<Integer, ?> slots = Constant.SKILLS.get(cid);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> slots.remove(ABSENT_KEY),
                "a character's skill slots are a nested map and must be read-only as well");

        Integer pointCid = Constant.SKILL_TRACES.keySet().iterator().next();
        List<?> points = Constant.SKILL_TRACES.get(pointCid);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> points.set(999_999, null),
                "a character's trace nodes are a nested list");

        Integer group = Constant.HARD_LEVEL_GROUPS.keySet().iterator().next();
        Map<Integer, ?> waves = Constant.HARD_LEVEL_GROUPS.get(group);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> waves.remove(ABSENT_KEY));
    }

    /**
     * The other half of the contract: freezing must not break reading. A defensive copy taken at load time
     * is only useful if the data still looks the same afterwards.
     */
    @Test
    public void theTablesStillReadNormally() {
        Assertions.assertFalse(Constant.SKILLS.isEmpty(), "skills.json loaded");
        Assertions.assertFalse(Constant.SKILL_TRACES.isEmpty(), "skill_traces.json loaded");
        Assertions.assertFalse(Constant.WEAPONS.isEmpty(), "weapons.json loaded");
        Assertions.assertFalse(Constant.HARD_LEVEL_GROUPS.isEmpty(), "hard_level_group.json loaded");

        Integer cid = Constant.SKILLS.keySet().iterator().next();
        Assertions.assertFalse(Constant.SKILLS.get(cid).isEmpty(),
                "and the nested map still serves its entries");
        Integer pointCid = Constant.SKILL_TRACES.keySet().iterator().next();
        Assertions.assertFalse(Constant.SKILL_TRACES.get(pointCid).isEmpty());
    }
}
