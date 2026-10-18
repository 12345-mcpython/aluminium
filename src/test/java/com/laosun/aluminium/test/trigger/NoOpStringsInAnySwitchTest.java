package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.models.trigger.TriggerOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * No {@code switch} arm anywhere in {@code src/main/java} compares the op against a <b>string</b>: every decision about
 * an op goes through {@link TriggerOperator}.
 *
 * <p>Why a source scan and not a behavioural test: a stray {@code case "MODIFY_ATTR"} behaves <b>exactly</b> like the
 * correct enum arm, so no runtime assertion can tell them apart - the difference is only that the string one is
 * unreachable when misspelt, which is the silent failure the enum was adopted to remove. Scanning for `case "UPPERCASE"`
 * whose name is an op catches the reintroduction at the point it happens.
 */
public class NoOpStringsInAnySwitchTest {

    private static final Pattern STRING_ARM = Pattern.compile("case \"([A-Z_]+)\"");

    @Test
    public void noArmComparesAnOpAgainstAString() throws IOException {
        Set<String> ops = Set.of(TriggerOperator.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = STRING_ARM.matcher(lines.get(i));
                    if (m.find() && ops.contains(m.group(1))) {
                        offenders.add(file + ":" + (i + 1) + " -> " + lines.get(i).trim());
                    }
                }
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "these arms compare an op against a string; use the TriggerOperator constant instead: " + offenders);
    }
}
