package com.laosun.aluminium.test.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.data.WeaponTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The file header of every shipped trigger file: {@code schema} and the owning id
 * ({@code character} / {@code light_cone} / {@code relic_set}).
 *
 * <p><b>The shape decided 2026-10-31.</b> A cone or relic file keeps its rank / piece-count keys exactly where they
 * were; the header sits beside them, the same two keys a character file states as {@code schema} and
 * {@code character}. The alternative - wrapping the rows in a {@code "ranks": { ... }} object - would have re-indented
 * every cone and relic file to say nothing new, and the existing shape already has a precedent for metadata beside
 * rows ({@code resources} on a cone). A character file already had the object form; it was the last kind without the
 * header on every file.
 *
 * <p>Both keys are <b>optional</b> at the loader (a file written before the header stays readable) but <b>required by
 * this guard</b> on the shipped content, so "we forgot one" is a red test rather than a silently legacy file. A stated
 * schema must be {@code 1}, a stated owner must equal the id in the filename - and stating the <i>other</i> kind's key
 * is an unknown key, which is how a file copied between content kinds is caught.
 *
 * <p>The broken-header cases live in {@code src/test/resources/game/.../9999x.json}: they are loaded by id, never
 * enumerated, so they cannot pollute the coverage scan (which reads {@code src/main/resources}).
 */
public class ContentFileHeaderTest {

    private static final Path CHARACTERS = Path.of("src", "main", "resources", "game", "characters");
    private static final Path CONES = Path.of("src", "main", "resources", "game", "light_cones");
    private static final Path RELICS = Path.of("src", "main", "resources", "game", "relic_sets");

    /**
     * Every shipped character states {@code schema: 1} and its own {@code character} id.
     *
     * <p>The loader still accepts a bare array (a file with nothing but rules), but <b>shipped</b> content is the
     * object form: without it there is nowhere to state the version or the owner, and the other two kinds cannot be
     * checked the same way. A new character file is therefore expected to be an object.
     */
    @Test
    public void everyCharacterStatesItsHeader() throws Exception {
        List<String> problems = check(CHARACTERS, "character");
        Assertions.assertEquals(List.of(), problems,
                "a character file is missing its header or names the wrong owner:\n  "
                        + String.join("\n  ", problems));
    }

    /**
     * Every shipped cone states {@code schema: 1} and its own {@code light_cone} id.
     */
    @Test
    public void everyConeStatesItsHeader() throws Exception {
        List<String> problems = check(CONES, "light_cone");
        Assertions.assertEquals(List.of(), problems,
                "a light cone file is missing its header or names the wrong owner:\n  " + String.join("\n  ", problems));
    }

    /**
     * Every shipped relic set states {@code schema: 1} and its own {@code relic_set} id.
     */
    @Test
    public void everyRelicStatesItsHeader() throws Exception {
        List<String> problems = check(RELICS, "relic_set");
        Assertions.assertEquals(List.of(), problems,
                "a relic set file is missing its header or names the wrong owner:\n  "
                        + String.join("\n  ", problems));
    }

    /**
     * A well-formed header loads, so the guard above is not pinning a shape the loader cannot read.
     */
    @Test
    public void aWellFormedHeaderLoads() {
        Assertions.assertEquals(1,
                WeaponTriggerTables.of(99999, 1).rulesFor(TriggerEvent.BATTLE_START).size(),
                "the valid cone probe keeps its rule");
        Assertions.assertEquals(1,
                RelicTriggerTables.of(99990).at(2).rulesFor(TriggerEvent.BATTLE_START).size(),
                "the valid relic probe keeps its rule");
    }

    /**
     * Each broken shape is refused, and the message says which one it was.
     */
    @Test
    public void brokenHeadersAreRefused() {
        assertRefused(() -> WeaponTriggerTables.of(99998, 1), "schema");
        assertRefused(() -> WeaponTriggerTables.of(99997, 1), "light_cone");
        assertRefused(() -> WeaponTriggerTables.of(99996, 1), "lght_cone");
        assertRefused(() -> WeaponTriggerTables.of(99995, 1), "_status");
        assertRefused(() -> WeaponTriggerTables.of(99994, 1), "_notee");

        assertRefused(() -> RelicTriggerTables.of(99989), "schema");
        assertRefused(() -> RelicTriggerTables.of(99988), "relic_set");
        assertRefused(() -> RelicTriggerTables.of(99987), "thresholds");
        assertRefused(() -> RelicTriggerTables.of(99986), "_notee");
    }

    // ==================================================================

    private static void assertRefused(org.junit.jupiter.api.function.Executable call, String expected) {
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class, call);
        String message = refused.getMessage() == null ? "" : refused.getMessage();
        Assertions.assertTrue(message.contains(expected),
                "the refusal should name \"" + expected + "\": " + message);
    }

    private static List<String> check(Path root, String ownerKey) throws Exception {
        Assertions.assertTrue(Files.isDirectory(root), "content directory must exist: " + root.toAbsolutePath());
        List<String> problems = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .sorted().toList()) {
                files++;
                String name = file.getFileName().toString();
                int id = Integer.parseInt(name.substring(0, name.length() - ".json".length()));
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement rootElement = JsonParser.parseReader(reader);
                    if (!rootElement.isJsonObject()) {
                        problems.add(name + ": not a JSON object");
                        continue;
                    }
                    JsonObject object = rootElement.getAsJsonObject();
                    JsonElement schema = object.get("schema");
                    if (schema == null || !schema.isJsonPrimitive()
                            || !schema.getAsJsonPrimitive().isNumber() || schema.getAsInt() != 1) {
                        problems.add(name + ": \"schema\" is missing or is not 1");
                    }
                    JsonElement owner = object.get(ownerKey);
                    if (owner == null || !owner.isJsonPrimitive() || !owner.getAsString().equals(String.valueOf(id))) {
                        problems.add(name + ": \"" + ownerKey + "\" is missing or is not " + id);
                    }
                }
            }
        }
        Assertions.assertTrue(files > 0, "no files under " + root.toAbsolutePath());
        return problems;
    }
}

