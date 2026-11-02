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
 * When a clause says 「对应属性」 ("of the corresponding element"), its damage must take the ATTACKER's element, not a fixed one.
 *
 * <p><b>Why.</b> 1502's talent reads 「对随机 1 个击中的目标额外造成 1 次 20.00% 的<b>对应属性</b>欢愉伤害」 and was implemented with
 * {@code element: "Physical"} written out. That is a wrong number for every ally whose element is not Physical, and it has no
 * symptom: the instance settles, the gate stays green. The engine already has the honest spelling -
 * {@code element: "attacker"} - so the guard is a pure spelling check on the quote the rule itself carries.
 */
public class CorrespondingElementIsTheAttackersTest {

    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");

    @Test
    public void aCorrespondingElementClauseUsesTheAttackersElement() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            paths.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
        }
        for (Path file : files) {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            for (JsonObject rule : rulesOf(root)) {
                String source = sourceOf(rule);
                if (!source.contains("对应属性")) {
                    continue;
                }
                checked++;
                JsonElement effects = rule.get("do");
                if (effects == null || !effects.isJsonArray()) {
                    continue;
                }
                for (JsonElement effect : effects.getAsJsonArray()) {
                    JsonObject op = effect.getAsJsonObject();
                    JsonElement element = op.get("element");
                    if (element != null && element.isJsonPrimitive()
                            && !"attacker".equalsIgnoreCase(element.getAsString())) {
                        offenders.add(file.getFileName() + " :: " + rule.get("id").getAsString()
                                + " :: element=" + element.getAsString());
                    }
                }
            }
        }
        System.out.println("[corresponding-element] rules quoting 对应属性=" + checked + " offenders=" + offenders.size());
        Assertions.assertTrue(offenders.isEmpty(),
                "a rule whose clause says 「对应属性」 must write element: \"attacker\" - a concrete element is wrong for "
                        + "every attacker of another element, and it has no symptom: " + offenders);
    }

    private static String sourceOf(JsonObject rule) {
        JsonElement source = rule.get("source");
        if (source == null) {
            return "";
        }
        return source.isJsonPrimitive() ? source.getAsString() : source.toString();
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