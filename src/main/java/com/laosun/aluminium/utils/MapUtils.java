package com.laosun.aluminium.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MapUtils {
    /**
     * 从Map中随机获取一个条目（键值对）
     * @param map 非空的Map
     * @return 随机选中的条目
     * @throws IllegalArgumentException 如果Map为空或null
     */
    public static <K, V> Map.Entry<K, V> getRandomEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Map can't be null or empty");
        }

        List<Map.Entry<K, V>> entries = new ArrayList<>(map.entrySet());
        int randomIndex = ThreadLocalRandom.current().nextInt(entries.size());

        return entries.get(randomIndex);
    }

    public static <T> List<T> getRandomList(List<T> list, int sampleSize) {
        Random random = new Random();

        return random.ints(0, list.size())
                .distinct() // 去重索引
                .limit(sampleSize)
                .mapToObj(list::get)
                .collect(Collectors.toList());
    }
}
