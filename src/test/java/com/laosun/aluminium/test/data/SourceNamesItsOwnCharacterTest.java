package com.laosun.aluminium.test.data;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A rule's {@code source} that starts with its own cid must name its own character - not another one.
 *
 * <p><b>Why.</b> 1104 Gepard's talent carried {@code source: "1104 镜流（行迹）…"} - the right cid, the right quote, and the wrong
 * CHARACTER (镜流 is Jingliu). {@link QuotedSourceExistsTest} cannot see it: the sentence really is in Gepard's document, so
 * the quote check passes. Reading "镜流" over a Gepard rule is how a later reader concludes the wrong thing about whose kit
 * this is, which is why the label is worth checking on its own.
 *
 * <p>A section word (星魂 / 行迹 / 天赋 / 战技 / 终结技 / 普攻 / 秘技 / 技能表 …) is fine in that position - many rules cite
 * 「1002 星魂 3」 rather than the character's name.
 */
public class SourceNamesItsOwnCharacterTest {

    private static final Path CORPUS = Path.of(
            System.getenv().getOrDefault("ALUMINIUM_TEXTS", "E:/turnbasedgamedata/aluminium_texts"));
    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");
    private static final Pattern PREFIX = Pattern.compile("^\\s*(\\d{4})\\s*([\\u4e00-\\u9fff·•]{2,8})");
    private static final Set<String> SECTIONS = Set.of(
            "星魂", "行迹", "天赋", "战技", "终结技", "普攻", "秘技", "技能表", "强化普攻", "强化战技",
            "忆灵技", "忆灵天赋", "终结技数据", "等级", "数据");

    @Test
    public void aSourceNamesItsOwnCharacter() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(CORPUS),
                "the character corpus is not on this machine (" + CORPUS + "); the names come from its filenames");
        java.util.Map<String, String> nameOf = new java.util.HashMap<>();
        List<Path> docs = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CORPUS)) {
            paths.filter(p -> p.toString().endsWith(".html")).forEach(docs::add);
        }
        for (Path doc : docs) {
            String file = doc.getFileName().toString();
            int cut = file.indexOf('_');
            if (cut != 4) {
                continue;
            }
            String name = file.substring(cut + 1).replace(".html", "");
            int paren = name.indexOf('（');
            if (paren < 0) {
                paren = name.indexOf('(');
            }
            if (paren > 0) {
                name = name.substring(0, paren);
            }
            nameOf.putIfAbsent(file.substring(0, 4), name.trim());
        }
        Set<String> names = new HashSet<>(nameOf.values());
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            paths.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
        }
        for (Path file : files) {
            String cid = file.getFileName().toString().replace(".json", "");
            String own = nameOf.get(cid);
            if (own == null) {
                continue;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            for (JsonObject rule : rulesOf(root)) {
                JsonElement source = rule.get("source");
                if (source == null) {
                    continue;
                }
                String text = source.isJsonPrimitive() ? source.getAsString() : source.toString();
                Matcher m = PREFIX.matcher(text);
                if (!m.find() || !m.group(1).equals(cid)) {
                    continue;
                }
                checked++;
                String token = m.group(2);
                if (SECTIONS.contains(token) || names.contains(token) == false) {
                    continue;
                }
                if (!own.startsWith(token) && !token.equals(own)) {
                    offenders.add(cid + " :: " + rule.get("id").getAsString() + " :: 引文写「" + token
                            + "」，但本文档是「" + own + "」");
                }
            }
        }
        System.out.println("[source-name] rules checked=" + checked + " offenders=" + offenders.size());
        Assertions.assertTrue(offenders.isEmpty(),
                "a rule cites its own cid but names another character - the quote may be right and the label wrong: "
                        + offenders);
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
        for (var entry : object.entrySet()) {
            if (!"resources".equals(entry.getKey())) {
                collect(entry.getValue(), out);
            }
        }
    }
}