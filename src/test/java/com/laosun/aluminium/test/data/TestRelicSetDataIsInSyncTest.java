package com.laosun.aluminium.test.data;


import com.laosun.aluminium.test.content.memosprites.SummonOpTest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The test-side copy of relic_sets.json may differ from the shipped one ONLY by the synthetic sets (2026-09-30).
 *
 * <p>Why it exists: a test fixture needs a set that will never be authored, so the test classpath carries a copy with
 * two synthetic entries (99001 for the SummonOpTest fixture, 99002 for NO_RULE_SET), both marked
 * {@code release_version: "test"}. The relic censuses skip anything so marked. A copy can drift, and a drifting copy
 * would silently change what every relic judge is looking at -- so the drift must fail loudly here.
 *
 * <p>Note: Measured 2026-09-30: the first version of this guard read BOTH files through {@code getResourceAsStream} with
 * the same path, so it saw null ("/data/relic_sets.json is not on the classpath"). The classpath can only ever show
 * one of the two copies; the two REAL files must be read from disk by path.
 */
public class TestRelicSetDataIsInSyncTest {
    private static final Path MAIN = Path.of("src/main/resources/data/relic_sets.json");
    private static final Path TEST = Path.of("src/test/resources/data/relic_sets.json");

    private static Map<String, String> load(Path p) {
        try {
            Map<String, Object> raw = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Object>>() { }.getType());
            Map<String, String> flat = new TreeMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                flat.put(e.getKey(), new Gson().toJson(e.getValue()));
            }
            return flat;
        } catch (Exception e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath(), e);
        }
    }

    private static boolean isSynthetic(String json) {
        return json.contains("\"release_version\":\"test\"");
    }

    @Test
    public void theTestSideCopyDiffersFromTheShippedFileOnlyByTheSyntheticSets() {
        Assertions.assertTrue(Files.exists(TEST),
                "the test-side copy must exist -- the fixture and NO_RULE_SET live on synthetic sets: " + TEST);
        Map<String, String> main = load(MAIN);
        Map<String, String> test = load(TEST);

        TreeSet<String> synthetic = test.entrySet().stream()
                .filter(e -> isSynthetic(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
        Assertions.assertEquals(new TreeSet<>(java.util.List.of("99001", "99002", "99004")), synthetic,
                "exactly the two synthetic sets may be marked release_version \"test\"");
        Assertions.assertEquals(main.size() + 3, test.size(),
                "the copy is the shipped file plus those synthetic sets");
        Assertions.assertTrue(main.values().stream().noneMatch(TestRelicSetDataIsInSyncTest::isSynthetic),
                "no SHIPPED set may be marked test");

        for (Map.Entry<String, String> e : main.entrySet()) {
            Assertions.assertEquals(e.getValue(), test.get(e.getKey()),
                    "set " + e.getKey() + " drifted between src/main and src/test -- re-copy the file");
        }
    }
}
