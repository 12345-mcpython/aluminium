package com.laosun.aluminium.utils;

import java.util.List;
import java.util.Map;
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
}
