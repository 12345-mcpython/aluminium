package com.laosun.aluminium.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random-access utility methods for collections.
 */
public final class MapUtils {

    /**
     * Selects a random entry from a map (uniform distribution).
     *
     * <p>Iterates to a random index to avoid copying the entire entry set.
     *
     * @param <K> key type
     * @param <V> value type
     * @param map the source map, must not be null or empty
     * @return a randomly selected entry
     * @throws IllegalArgumentException if map is null or empty
     */
    public static <K, V> Map.Entry<K, V> getRandomEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Map can't be null or empty");
        }

        int targetIndex = ThreadLocalRandom.current().nextInt(map.size());
        int currentIndex = 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (currentIndex == targetIndex) {
                return entry;
            }
            currentIndex++;
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Selects a random sub-list of distinct elements from a list.
     *
     * @param <T>        element type
     * @param list       the source list
     * @param sampleSize number of distinct elements to pick
     * @return a new list containing the sampled elements
     * @throws IllegalArgumentException if list size is smaller than sampleSize
     */
    public static <T> List<T> getRandomList(List<T> list, int sampleSize) {
        if (list.size() < sampleSize) {
            throw new IllegalArgumentException("List size can't be less than sample size");
        }

        return ThreadLocalRandom.current().ints(0, list.size())
                .distinct()
                .limit(sampleSize)
                .mapToObj(list::get)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * An unmodifiable set that keeps the source's iteration order.
     *
     * <p><b>Why not {@link Set#copyOf}.</b> {@code Set.copyOf} is unmodifiable too, but its iteration
     * order is deliberately obscured: the returned set perturbs element hashes with a SALT taken from
     * {@code System.nanoTime()}, so the order differs between runs of the same program on the same
     * input. Anything that prints such a set, or that lets a caller observe the order, is therefore not
     * reproducible -- and this project relies on reproducible runs, a fixed seed being the point of its
     * random source. The loader reads weaknesses from JSON in a fixed order; that order is the honest
     * one to keep.
     *
     * <p>Duplicates are collapsed on first sight, as a set must, but the first occurrence fixes the
     * position.
     *
     * @param source the elements, in the order to preserve; may be {@code null}
     * @param <T>    element type
     * @return an unmodifiable, insertion-ordered set; empty when {@code source} is {@code null}
     */
    public static <T> Set<T> orderedSet(Collection<T> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /**
     * An unmodifiable map that keeps the source's iteration order.
     *
     * <p>The counterpart of {@link #orderedSet} and needed for the same reason: {@code Map.copyOf}
     * randomises iteration order via a SALT, so a printed resistance table reads differently on each
     * run even when the data and the code are identical.
     *
     * @param source the entries, in the order to preserve; may be {@code null}
     * @param <K>    key type
     * @param <V>    value type
     * @return an unmodifiable, insertion-ordered map; empty when {@code source} is {@code null}
     */
    public static <K, V> Map<K, V> orderedMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
