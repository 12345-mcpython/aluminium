package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A character marked {@code implemented} with no open gaps must have a rule for EVERY section of its document.
 *
 * <p><b>Why.</b> "Fully implemented" used to be read off an empty {@code _gaps}, and eight characters with that status had no
 * technique rule at all while five more were missing whole Eidolons (1109's 1 and 4, 1218's 4 and 6, 1512's 1/2/4/6). The
 * check is the one a reader would do by hand: open the document, list the sections, and see that each one lands somewhere.
 *
 * <p><b>How a section counts as covered.</b> The union of every rule's {@code id} and {@code source} text is searched for the
 * section's own name (or the engine's slot name: 战技 / SKILL, 星魂 3 / Rank03 / eidolon3 …). This is deliberately coarse - it
 * answers "does anything in this file talk about that section", which is what the gap list is supposed to guarantee.
 *
 * <p><b>Why it skips.</b> The corpus is outside this repository; without it there is nothing to list.
 */
public class ImplementedCharacterCoversEveryDocumentSectionTest {

    private static final Path CORPUS = Path.of(
            System.getenv().getOrDefault("ALUMINIUM_TEXTS", "E:/turnbasedgamedata/aluminium_texts"));
    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");
    private static final Pattern HEADING = Pattern.compile("<h3[^>]*>([^<]{2,60})</h3>");
    private static final Pattern RELEVANT = Pattern.compile(
            "^(普攻|强化普攻|战技|强化战技|终结技|强化终结技|天赋|秘技|星魂|行迹)");

    @Test
    public void everySectionOfAnImplementedCharacterHasARule() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(CORPUS),
                "the character corpus is not on this machine (" + CORPUS + "); the section check needs it");
        List<String> missing = new ArrayList<>();
        int checked = 0;
        List<Path> files = files(CHARACTERS);
        List<Path> docs = files(CORPUS);
        for (Path file : files) {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("rules") || !root.has("_status")
                    || !"implemented".equals(root.get("_status").getAsString())) {
                continue;
            }
            JsonElement gaps = root.get("_gaps");
            if (gaps != null && gaps.isJsonArray() && !gaps.getAsJsonArray().isEmpty()) {
                continue;   // it already says what is open
            }
            String cid = file.getFileName().toString().replace(".json", "");
            Path doc = docs.stream().filter(d -> d.getFileName().toString().startsWith(cid)).findFirst().orElse(null);
            if (doc == null) {
                continue;
            }
            checked++;
            String text = Files.readString(doc, StandardCharsets.UTF_8);
            String corpus = corpusOf(root.getAsJsonArray("rules"));
            var matcher = HEADING.matcher(text);
            while (matcher.find()) {
                String title = matcher.group(1).trim();
                if (!RELEVANT.matcher(title).find()) {
                    continue;
                }
                if (!covered(title, corpus)) {
                    missing.add(cid + " :: " + title);
                }
            }
        }
        System.out.println("[section-coverage] characters checked=" + checked + " missing sections=" + missing.size());
        Assertions.assertTrue(missing.isEmpty(),
                "these sections have no rule and no gap entry, so the character is not implemented as claimed: " + missing);
    }

    private static String corpusOf(JsonArray rules) {
        StringBuilder out = new StringBuilder();
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("id")) {
                out.append(rule.get("id").getAsString()).append(' ');
            }
            JsonElement source = rule.get("source");
            if (source != null) {
                out.append(source.isJsonPrimitive() ? source.getAsString() : source.toString()).append(' ');
            }
        }
        return out.toString();
    }

    private static boolean covered(String title, String corpus) {
        if (title.startsWith("普攻")) {
            return corpus.contains("普攻") || corpus.contains("COMMON");
        }
        if (title.startsWith("强化普攻")) {
            return corpus.contains("强化普攻") || corpus.contains("ENHANCED");
        }
        if (title.startsWith("战技")) {
            return corpus.contains("战技") || corpus.contains("SKILL");
        }
        if (title.startsWith("终结技")) {
            return corpus.contains("终结技") || corpus.contains("ULTRA");
        }
        if (title.startsWith("天赋")) {
            return corpus.contains("天赋") || corpus.contains("TALENT");
        }
        if (title.startsWith("秘技")) {
            return corpus.contains("秘技") || corpus.contains("TECHNIQUE");
        }
        if (title.startsWith("行迹")) {
            return corpus.contains("行迹") || corpus.contains("trace") || corpus.matches("(?s).*Rank0[1-6].*");
        }
        var rank = Pattern.compile("^星魂\\s*(\\d)").matcher(title);
        if (rank.find()) {
            String n = rank.group(1);
            return corpus.contains("星魂 " + n) || corpus.contains("星魂" + n)
                    || corpus.contains("Rank0" + n) || corpus.contains("Rank" + n)
                    || corpus.contains("eidolon" + n);
        }
        return true;
    }

    private static List<Path> files(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".html")
                    || p.toString().endsWith(".md")).sorted().forEach(out::add);
        }
        return out;
    }
}