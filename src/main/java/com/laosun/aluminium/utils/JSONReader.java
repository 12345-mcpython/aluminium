package com.laosun.aluminium.utils;

import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class JSONReader {
    private static final Gson GSON = new Gson();

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
