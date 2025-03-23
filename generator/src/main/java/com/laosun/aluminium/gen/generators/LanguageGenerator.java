package com.laosun.aluminium.gen.generators;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class LanguageGenerator {
    public static Map<String, String> LANGUAGE_MAPPING = Map.ofEntries(Map.entry("zh_cn", "TextMapCHS.json"),
            Map.entry("en_us", "TextMapEN.json"), Map.entry("fr_fr", "TextMapFR.json"));

    @SuppressWarnings("unchecked")
    public static Map<String, String> readLanguageMap(String language, String dataDir) throws IOException {
        if (!LANGUAGE_MAPPING.containsKey(language)) {
            System.out.println("Language not found: " + language);
            return null;
        }
        Gson gson = new Gson();
        return (Map<String, String>) gson.fromJson(Files.readString(Path.of(dataDir, "TextMap", LANGUAGE_MAPPING.get(language)), StandardCharsets.UTF_8), Map.class);
    }
}
