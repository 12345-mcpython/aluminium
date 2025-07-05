package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.utils.JSONReader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Benchmark {
    public record Suit(String set, String type, Relic.Setting json) {
    }

    public static void main(String[] args) throws InterruptedException {
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

        // 等待所有线程完成
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
            var test = suits.calcTotalValue(); // 确保此方法无副作用
        }
    }
}
