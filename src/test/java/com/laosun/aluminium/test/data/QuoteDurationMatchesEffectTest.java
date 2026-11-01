package com.laosun.aluminium.test.data;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * When a rule quotes 「…持续 N 回合…」, at least one of its effect durations must be N.
 *
 * <p><b>Why a second quote guard.</b> {@link QuotedSourceExistsTest} proves the quote is really in the document; this one
 * proves the number in the quote reached the effect. They fail in opposite directions and neither subsumes the other: 1101's
 * Eidolon 2 had <b>both</b> wrong (the quote said 2 turns where the document says 1, and the effect followed the quote), so
 * only the first guard could see it. A future edit that fixes the quote but transcribes the wrong duration is this guard's
 * case.
 *
 * <p>A long quote may state several durations (1225's skill gives 「持续3回合」 to one state and 「持续2回合」 to the defence
 * drop in the same sentence), so a match against ANY of them is accepted. Measured on 2026-11-01 over the shipped corpus:
 * 146 rule/quote pairs, 0 mismatches.
 */
public class QuoteDurationMatchesEffectTest {

    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");
    private static final Pattern QUOTE = Pattern.compile("「([^」]{6,})」");
    private static final Pattern DURATION = Pattern.compile("持续\\s*(\\d+)\\s*回合");

    @Test
    public void everyQuotedDurationReachesItsEffect() throws IOException {
        List<String> mismatches = new ArrayList<>();
        int pairs = 0;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            paths.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            for (JsonObject rule : rulesOf(root)) {
                String quote = quoted(rule);
                if (quote == null) {
                    continue;
                }
                List<Integer> stated = durations(quote);
                if (stated.isEmpty()) {
                    continue;
                }
                List<Integer> effects = turnsOf(rule);
                if (effects.isEmpty()) {
                    continue;
                }
                pairs++;
                if (effects.stream().noneMatch(stated::contains)) {
                    mismatches.add(name + " :: " + (rule.has("id") ? rule.get("id").getAsString() : "?")
                            + " quote=" + stated + " effects=" + effects);
                }
            }
        }
        System.out.println("[quote-duration] pairs=" + pairs + " mismatches=" + mismatches.size());
        Assertions.assertTrue(mismatches.isEmpty(),
                "a rule quotes a duration its effects do not use: " + mismatches
                        + ". Either the effect states the wrong number of turns or the quote is from another clause.");
    }

    private static String quoted(JsonObject rule) {
        JsonElement source = rule.get("source");
        if (source == null || !source.isJsonPrimitive()) {
            return null;
        }
        Matcher m = QUOTE.matcher(source.getAsString());
        return m.find() ? m.group(1) : null;
    }

    private static List<Integer> durations(String quote) {
        List<Integer> out = new ArrayList<>();
        Matcher m = DURATION.matcher(quote);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    private static List<Integer> turnsOf(JsonObject rule) {
        List<Integer> out = new ArrayList<>();
        JsonElement effects = rule.get("do");
        if (effects == null || !effects.isJsonArray()) {
            return out;
        }
        for (JsonElement effect : effects.getAsJsonArray()) {
            JsonElement turns = effect.getAsJsonObject().get("turns");
            if (turns != null && turns.isJsonPrimitive()) {
                out.add(turns.getAsInt());
            }
        }
        return out;
    }

    private static List<JsonObject> rulesOf(JsonElement element) {
        List<JsonObject> out = new ArrayList<>();
        collect(element, out);
        return out;
    }

    private static void collect(JsonElement element, List<JsonObject> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, out));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("on") && object.has("do")) {
            out.add(object);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"resources".equals(entry.getKey())) {
                collect(entry.getValue(), out);
            }
        }
    }
}