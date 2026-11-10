package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * A character that says {@code _status: "implemented"} must carry an explicit {@code _gaps} array.
 *
 * <p><b>Why.</b> "No gaps" was being read off the ABSENCE of the field, so a file that had never been reviewed at all looked
 * exactly like one that had been reviewed and found clean - 11 of the 18 "fully implemented" files were missing the array
 * outright (March 7th among them). An empty array is a claim ("I looked; nothing is open"); a missing array is not a claim
 * at all, and the difference matters for every count that is derived from it.
 */
public class EveryImplementedFileDeclaresItsGapsTest {

    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");

    @Test
    public void everyImplementedCharacterHasAGapsArray() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".json")).sorted()::iterator) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.has("rules") || !root.has("_status")
                        || !"implemented".equals(root.get("_status").getAsString())) {
                    continue;
                }
                checked++;
                JsonElement gaps = root.get("_gaps");
                if (gaps == null || !gaps.isJsonArray()) {
                    offenders.add(file.getFileName() + " (no _gaps)");
                    continue;
                }
                JsonArray array = gaps.getAsJsonArray();
                for (JsonElement entry : array) {
                    if (!entry.isJsonPrimitive() || entry.getAsString().isBlank()) {
                        offenders.add(file.getFileName() + " (blank _gaps entry)");
                        break;
                    }
                }
            }
        }
        System.out.println("[gaps-array] implemented files checked=" + checked + " offenders=" + offenders.size());
        Assertions.assertTrue(offenders.isEmpty(),
                "an implemented character must declare its gaps explicitly - `_gaps: []` means \"reviewed, none open\", "
                        + "while a missing array only means nobody looked: " + offenders);
    }

    /**
     * The other half: an {@code implemented} file may not carry an open clause. {@code ⛔} is the ledger's "still unresolved"
     * marker, so a file that claims to be complete and marks something unresolved contradicts itself - and the count in
     * {@code CONTENT-STATUS.md} ("未解阻塞 N 条") is derived from that glyph, so the two numbers would disagree.
     */
    @Test
    public void everyImplementedCharacterHasNoOpenClause() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".json")).sorted()::iterator) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.has("_status") || !"implemented".equals(root.get("_status").getAsString())
                        || !root.has("_gaps") || !root.get("_gaps").isJsonArray()) {
                    continue;
                }
                for (JsonElement entry : root.getAsJsonArray("_gaps")) {
                    if (entry.isJsonPrimitive() && entry.getAsString().contains("\u26D4")) {
                        offenders.add(file.getFileName().toString());
                        break;
                    }
                }
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "these files say `_status: implemented` but still mark a clause as unresolved (⛔): " + offenders);
    }
}