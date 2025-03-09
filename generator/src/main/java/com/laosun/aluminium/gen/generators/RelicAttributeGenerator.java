package com.laosun.aluminium.gen.generators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.gen.Utils;
import com.laosun.aluminium.gen.origin.RawRelicMainAffixConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.beans.Model.PART_MAPPING;
import static com.laosun.aluminium.beans.Model.RELIC_MAPPING;

public class RelicAttributeGenerator {
    public static void exportRelicMainAttributes(String projectDir) throws IOException {
        Map<Integer, Map<String, Map<String, Map<String, Double>>>> p = new HashMap<>();
        String relicMainAffixConfigString = Files.readString(Path.of(projectDir + "/ExcelOutput/RelicMainAffixConfig.json"));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<RawRelicMainAffixConfig> rawRelicMainAffixConfigList = gson.fromJson(relicMainAffixConfigString, new TypeToken<>() {
        });
        for (RawRelicMainAffixConfig rawRelicMainAffixConfig : rawRelicMainAffixConfigList) {
            int groupId = rawRelicMainAffixConfig.GroupID();
            if (groupId > 100) {
                continue;
            }
            String part = PART_MAPPING.get(groupId % 10);
            int star = groupId / 10;
            String left = RELIC_MAPPING.get(rawRelicMainAffixConfig.Property());
            Map<String, Double> processData = Utils.processRelicMainAttribute(rawRelicMainAffixConfig);
            if (!p.containsKey(star)) {
                p.put(star, new HashMap<>());
            }
            if (!p.get(star).containsKey(part)) {
                p.get(star).put(part, new HashMap<>());
            }
            p.get(star).get(part).put(left, processData);
        }
        Files.write(Path.of("data/main_attribute.json"), gson.toJson(p).getBytes());
    }

    public static void exportRelicSubAttributes(String projectDir) throws IOException {
        Map<Integer, Map<String, Map<String, Double>>> p = new HashMap<>();
        String relicSubAffixConfigString = Files.readString(Path.of(projectDir + "/ExcelOutput/RelicMainAffixConfig.json"));

    }
}
