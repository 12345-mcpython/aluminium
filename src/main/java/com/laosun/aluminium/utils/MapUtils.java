package com.laosun.aluminium.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class MapUtils {
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

    public static <T> List<T> getRandomList(List<T> list, int sampleSize) {
        if (list.size() < sampleSize) {
            throw new IllegalArgumentException("List size can't be less than sample size");
        }

        return ThreadLocalRandom.current().ints(0, list.size())
                .distinct()
                .limit(sampleSize)
                .mapToObj(list::get)
                .collect(Collectors.toList());
    }
}
