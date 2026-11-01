package com.laosun.aluminium.test.data;

import com.laosun.aluminium.data.TriggerTables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Every shipped character file must LOAD.
 *
 * <p><b>Why this guard exists.</b> The corpus has ninety-odd character files, and the load-time checks (unknown op,
 * vocabulary, selector closed set, magnitude spellings) only run when a file is actually compiled into a trigger table.
 * A file that no test happened to touch could therefore be broken and nobody would see it - and the failure mode is
 * silent: {@code TriggerTables.of(cid)} throws, but only inside whatever test names that character.
 *
 * <p>This walks the shipped files and compiles each one, so a bad write anywhere fails the gate.
 */
public class EveryCharacterFileLoadsTest {

    @Test
    public void allCharacterFilesCompileIntoATriggerTable() throws Exception {
        List<Path> files;
        try (var stream = Files.list(Path.of("src/main/resources/game/characters"))) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        Assertions.assertTrue(files.size() > 90, "precondition: the scan must see the character files");

        List<String> broken = new ArrayList<>();
        for (Path file : files) {
            int cid = Integer.parseInt(file.getFileName().toString().replace(".json", ""));
            try {
                var table = TriggerTables.of(cid);
                // An empty table is also a failure: a file with rules that all silently failed to compile is not "loaded".
                if (table == null || table.isEmpty()) {
                    broken.add(cid + " (empty)");
                }
            } catch (RuntimeException e) {
                broken.add(cid + " (" + e.getClass().getSimpleName() + ": "
                        + String.valueOf(e.getMessage()).lines().findFirst().orElse("") + ")");
            }
        }
        System.out.println("[load-all] files=" + files.size() + " broken=" + broken.size());
        Assertions.assertTrue(broken.isEmpty(),
                "every character file must compile into a trigger table; these do not: " + String.join(" | ", broken));
    }
}