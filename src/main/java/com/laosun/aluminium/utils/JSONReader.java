package com.laosun.aluminium.utils;

import com.google.gson.Gson;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

public class JSONReader {
    public static <T> T fromJSON(String jsonName, Type type) {
        try {
            String json = Files.readString(Path.of("data/" + jsonName));
            return new Gson().fromJson(json, type);
        } catch (IOException e) {
            System.out.println("Unable to read resource.");
        }
        return null;
    }
}
