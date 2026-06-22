package com.laosun.aluminium.utils;

import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utility for loading JSON data from classpath resources under {@code /data/}.
 *
 * <p>Uses Gson for deserialization. All resources are read with UTF-8 encoding.
 */
public final class JSONReader {
    private static final Gson GSON = new Gson();

    /**
     * Reads and deserializes a JSON resource file.
     *
     * @param jsonName the file name under {@code /data/} (e.g. "weapons.json")
     * @param type     the target Gson type token
     * @param <T>      the expected return type
     * @return the deserialized object, or {@code null} if the resource is missing
     */
    @SneakyThrows
    public static <T> T fromJSON(String jsonName, Type type) {
        String resourcePath = "/data/" + jsonName;
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(JSONReader.class.getResourceAsStream(resourcePath)),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }
}
