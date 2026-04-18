package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.JSONReader;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Benchmark {
    public record Suit(String set, String type, Relic.Setting json) {
    }

    static void main() throws InterruptedException {
        int roundSingle = 1000000;
        long start = System.nanoTime();
        Map<Integer, List<Suit>> data = JSONReader.fromJSON("dump_data.json", new TypeToken<Map<Integer, List<Suit>>>() {
        }.getType());
        if (data == null) {
            System.exit(1);
        }
        for (int i = 0; i < roundSingle; i++) {
            processData(data);
        }
        long end = System.nanoTime();
        System.out.println("Round: " + roundSingle);
        System.out.println("Character count: " + data.size());
        System.out.println("Total time: " + (end - start) / 1000000 + "ms");
        int roundMulti = 1000000;
        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        long start1 = System.nanoTime();
        int roundsPerThread = roundMulti / threadCount;
        int remainingRounds = roundMulti % threadCount;

        for (int i = 0; i < threadCount; i++) {
            final int threadRounds = roundsPerThread + (i < remainingRounds ? 1 : 0);
            executor.submit(() -> {
                try {
                    for (int r = 0; r < threadRounds; r++) {
                        processData(data);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long end1 = System.nanoTime();
        System.out.println("Round: " + roundMulti);
        System.out.println("Character count: " + data.size());
        System.out.println("Thread count: " + threadCount);
        System.out.println("Total time: " + (end1 - start1) / 1000000 + "ms");
    }

    private static void processData(Map<Integer, List<Suit>> data) {
        for (Map.Entry<Integer, List<Suit>> entry : data.entrySet()) {
            RelicSuit suits = new RelicSuit();
            for (Suit suit : entry.getValue()) {
                Relic relic = Relic.createBySetting(
                        Relic.Type.getType(suit.type), 5, 15, suit.json
                );
                suits.addToSuit(relic);
            }
            buildCharacter(suits);
            Object2DoubleOpenHashMap<String> relicValue = new Object2DoubleOpenHashMap<>();
            suits.calcTotalValue(relicValue);
        }
    }

    private static void buildCharacter(RelicSuit suit) {
        Weapon wp = Weapon.build(23042, 80);
        Character character = Character.build(1409, 80, false, suit, wp,
                new ExtraBasicPromote(0, 0, 0, 14, 0.1, 0, 0, 0.3));
    }
}
