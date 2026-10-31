package com.laosun.aluminium.beans;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Reads a rule's {@code source} as either the legacy string or the structured object:
 *
 * <pre>{@code
 * "source": "1001 三月七 战技 ... 「...」 param Lv10 = [...]"
 * "source": { "text": "1001 三月七 ... 「...」", "skill": "SKILL", "row": 10,
 *             "params": { "#1": 0.57, "#2": 3 } }
 * }</pre>
 *
 * <p>The engine only needs the <b>text</b>: every load failure quotes it. The structured fields are for readers and
 * for parameter reconciliation ({@code ParamAnnotationReconciliationTest}); they are validated here (so a misspelling
 * is loud) and then dropped, because no engine path reads them. Keeping {@code source} a {@code String} is deliberate:
 * it is the provenance in error messages, and changing the bean's type would ripple through every one of them.
 */
public final class SourceTextAdapter implements JsonDeserializer<String> {

    private static final Set<String> KEYS = Set.of("text", "skill", "row", "params");

    @Override
    public String deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return json.getAsString();
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("a rule's \"source\" must be a string or an object with \"text\"");
        }
        JsonObject object = json.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!KEYS.contains(key)) {
                throw new JsonParseException(
                        "unknown key \"" + key + "\" in a source object; known: " + KEYS.stream().sorted().toList());
            }
        }
        JsonElement text = object.get("text");
        if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()
                || text.getAsString().isBlank()) {
            throw new JsonParseException("a source object needs a non-blank \"text\"");
        }
        if (object.has("skill")) {
            JsonElement skill = object.get("skill");
            if (!skill.isJsonPrimitive() || !skill.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("a source object's \"skill\" must be a string");
            }
            try {
                com.laosun.aluminium.enums.SkillType.valueOf(
                        skill.getAsString().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new JsonParseException(
                        "a source object names unknown skill slot \"" + skill.getAsString() + "\"");
            }
        }
        if (object.has("row")) {
            JsonElement row = object.get("row");
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isNumber()
                    || row.getAsDouble() != Math.rint(row.getAsDouble()) || row.getAsInt() < 1) {
                throw new JsonParseException("a source object's \"row\" must be a positive whole number");
            }
        }
        JsonElement params = object.get("params");
        if (params != null) {
            if (!params.isJsonObject() || params.getAsJsonObject().size() == 0) {
                throw new JsonParseException("a source object's \"params\" must be a non-empty object");
            }
            if (!object.has("skill") || !object.has("row")) {
                throw new JsonParseException("a source object with \"params\" also needs \"skill\" and \"row\"");
            }
            for (var entry : params.getAsJsonObject().entrySet()) {
                if (!entry.getKey().matches("#[1-9][0-9]*")) {
                    throw new JsonParseException(
                            "a source object's param key \"" + entry.getKey() + "\" must be #1, #2, ...");
                }
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException(
                            "a source object's param " + entry.getKey() + " must be a number");
                }
            }
        }
        return text.getAsString();
    }
}
