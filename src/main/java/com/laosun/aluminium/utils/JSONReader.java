package com.laosun.aluminium.utils;

import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JSONReader {
    @SneakyThrows
    public static <T> T fromJSON(String jsonName, Type type) {
        String json = Files.readString(Path.of("data/" + jsonName), StandardCharsets.UTF_8);
        return new Gson().fromJson(json, type);
    }
}
