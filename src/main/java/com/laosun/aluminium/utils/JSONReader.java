package com.laosun.aluminium.utils;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JSONReader {
    public static <T> T fromJSON(String jsonName, Class<T> clazz) throws IOException {
        String json = Files.readString(Path.of("data/" + jsonName));
        return new Gson().fromJson(json, clazz);
    }
}
