package com.laosun.aluminium.utils;

import com.google.gson.Gson;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JSONReader {
    public static <T> T fromJSON(String jsonName, Type type) {
        try {
            String json = Files.readString(Path.of("data/" + jsonName), StandardCharsets.UTF_8);
            return new Gson().fromJson(json, type);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read resource: " + jsonName, e);
        }
    }
}
