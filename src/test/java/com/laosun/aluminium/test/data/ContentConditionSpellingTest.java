package com.laosun.aluminium.test.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The shipped rule files use the single {@code when} array, not the legacy {@code when_predicates} key.
 *
 * <p>The loader no longer accepts the legacy key; shipped content is the place the one-spelling rule has to hold, and
 * this guard fails if a file reintroduces it.
 */
public class ContentConditionSpellingTest {

    @Test
    public void noShippedRuleUsesWhenPredicates() throws IOException {
        List<String> offenders = new ArrayList<>();
        int files = 0;
        for (String dir : List.of("characters", "light_cones", "relic_sets")) {
            Path root = Path.of("src", "main", "resources", "game", dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(root)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    files++;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.contains("\"when_predicates\"")) {
                        offenders.add(file.getFileName().toString());
                    }
                }
            }
        }
        Assertions.assertTrue(files > 100, "expected the shipped content, found " + files + " files");
        Assertions.assertEquals(List.of(), offenders,
                "these files still use the legacy condition key; move its entries into \"when\": " + offenders);
    }
}
