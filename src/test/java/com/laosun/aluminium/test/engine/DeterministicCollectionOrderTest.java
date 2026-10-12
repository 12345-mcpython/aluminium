package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.utils.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loader output keeps a stable iteration order, so a run is reproducible byte for byte.
 *
 * <p><b>What this guards.</b> {@code Set.copyOf}, {@code Map.copyOf} and {@code Set.of} obscure their
 * iteration order with a SALT taken from {@code System.nanoTime()}, so the order of the same elements
 * differs between runs of the same program. That is invisible until something prints the collection:
 * the demo's weakness and resistance lines then read differently on every run, which makes the demo
 * useless as a regression anchor and contradicts the project's stated property that a fixed seed
 * reproduces a whole battle. The seed governs the damage rolls; the printed tables were outside it.
 *
 * <p>These cases assert the property that fixes it -- order is preserved -- rather than any particular
 * order, so they pin the behaviour without encoding today's data.
 */
public class DeterministicCollectionOrderTest {

    /**
     * A set built from a collection iterates in the source's order, and keeps doing so.
     *
     * <p>Checked on a repetitive call rather than once, because a salted implementation would still
     * look correct on a single observation: the order only reveals itself as unstable across calls.
     */
    @Test
    void orderedSetPreservesInsertionOrder() {
        List<String> source = List.of("IMAGINARY", "ICE", "QUANTUM", "PHYSICAL", "FIRE", "WIND");

        Set<String> once = MapUtils.orderedSet(source);
        Assertions.assertEquals(source, List.copyOf(once),
                "the set must iterate in the order the source listed");

        for (int attempt = 0; attempt < 200; attempt++) {
            Assertions.assertEquals(source, List.copyOf(MapUtils.orderedSet(source)),
                    "iteration order must not change between calls (a salted set would)");
        }
    }

    /**
     * A map built from another map iterates in the source's order, and keeps doing so.
     */
    @Test
    void orderedMapPreservesInsertionOrder() {
        Map<String, Double> source = new java.util.LinkedHashMap<>();
        source.put("ICE", 0.2);
        source.put("PHYSICAL", 0.2);
        source.put("IMAGINARY", 0.2);
        source.put("WIND", 0.2);
        source.put("THUNDER", 0.2);
        List<String> expected = new ArrayList<>(source.keySet());

        Assertions.assertEquals(expected, new ArrayList<>(MapUtils.orderedMap(source).keySet()));

        for (int attempt = 0; attempt < 200; attempt++) {
            Assertions.assertEquals(expected, new ArrayList<>(MapUtils.orderedMap(source).keySet()),
                    "iteration order must not change between calls (a salted map would)");
        }
    }

    /**
     * The result is unmodifiable, so a caller cannot invalidate the order that was just established.
     */
    @Test
    void theResultsAreUnmodifiable() {
        Set<String> set = MapUtils.orderedSet(List.of("A", "B"));
        Map<String, Double> map = MapUtils.orderedMap(Map.of("A", 1.0));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> set.add("C"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> map.put("B", 2.0));
    }

    /**
     * An empty or absent source yields an empty collection rather than throwing, because the loaders
     * pass {@code null} for columns the data omits (102 monsters have no {@code stance_weak}).
     */
    @Test
    void anEmptyOrAbsentSourceYieldsAnEmptyCollection() {
        Assertions.assertTrue(MapUtils.orderedSet(null).isEmpty());
        Assertions.assertTrue(MapUtils.orderedSet(List.of()).isEmpty());
        Assertions.assertTrue(MapUtils.orderedMap(null).isEmpty());
        Assertions.assertTrue(MapUtils.orderedMap(Map.of()).isEmpty());
    }
}
