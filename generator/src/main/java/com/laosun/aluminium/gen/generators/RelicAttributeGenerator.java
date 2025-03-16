package com.laosun.aluminium.gen.generators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.gen.Utils;
import com.laosun.aluminium.gen.origin.RawRelicMainAffixConfig;
import com.laosun.aluminium.gen.origin.RawRelicSubAffixConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.beans.Model.PART_MAPPING;
import static com.laosun.aluminium.beans.Model.RELIC_MAPPING;

public class RelicAttributeGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXCEL_OUTPUT_DIR = "ExcelOutput";
    private static final String RELIC_MAIN_AFFIX_CONFIG = "RelicMainAffixConfig.json";
    private static final String RELIC_SUB_AFFIX_CONFIG = "RelicSubAffixConfig.json";
    private static final String OUTPUT_MAIN_ATTRIBUTE = "data/main_attribute.json";
    private static final String OUTPUT_SUB_ATTRIBUTE = "data/sub_attribute.json";

    public static void exportRelicMainAttributes(String projectDir) throws IOException {
        Map<Integer, Map<String, Map<String, Map<String, Double>>>> mainAttributesMap = new HashMap<>();

        List<RawRelicMainAffixConfig> configs = readJsonConfig(
                Path.of(projectDir, EXCEL_OUTPUT_DIR, RELIC_MAIN_AFFIX_CONFIG),
                new TypeToken<>() {}
        );

        for (RawRelicMainAffixConfig config : configs) {
            int groupId = config.GroupID();
            if (groupId > 100) continue;

            int star = groupId / 10;
            String part = PART_MAPPING.get(groupId % 10);
            String attributeKey = RELIC_MAPPING.get(config.Property());

            Map<String, Double> processedData = Utils.processRelicMainAttribute(config);

            mainAttributesMap
                    .computeIfAbsent(star, k -> new HashMap<>())
                    .computeIfAbsent(part, k -> new HashMap<>())
                    .put(attributeKey, processedData);
        }

        writeJsonFile(Path.of(OUTPUT_MAIN_ATTRIBUTE), mainAttributesMap);
    }

    public static void exportRelicSubAttributes(String projectDir) throws IOException {
        Map<Integer, Map<String, Map<String, Double>>> subAttributesMap = new HashMap<>();

        List<RawRelicSubAffixConfig> configs = readJsonConfig(
                Path.of(projectDir, EXCEL_OUTPUT_DIR, RELIC_SUB_AFFIX_CONFIG),
                new TypeToken<>() {}
        );

        for (RawRelicSubAffixConfig config : configs) {

            int star = config.GroupID();
            String attributeKey = RELIC_MAPPING.get(config.Property());

            Map<String, Double> processedData = Utils.processRelicSubAttribute(config);

            subAttributesMap
                    .computeIfAbsent(star, k -> new HashMap<>())
                    .put(attributeKey, processedData);
        }

        writeJsonFile(Path.of(OUTPUT_SUB_ATTRIBUTE), subAttributesMap);
    }

    private static <T> List<T> readJsonConfig(Path configPath, TypeToken<List<T>> typeToken) throws IOException {
        String jsonString = Files.readString(configPath);
        return GSON.fromJson(jsonString, typeToken.getType());
    }

    private static void writeJsonFile(Path outputPath, Object data) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, GSON.toJson(data).getBytes());
    }
}