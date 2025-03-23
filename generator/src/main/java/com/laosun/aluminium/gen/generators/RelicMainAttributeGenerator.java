package com.laosun.aluminium.gen.generators;

import com.laosun.aluminium.gen.Utils;
import com.laosun.aluminium.gen.origin.RawRelicConfig;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.beans.Model.PART_MAPPING;
import static com.laosun.aluminium.beans.Model.RELIC_MAPPING;

public class RelicMainAttributeGenerator 
    extends Generator<RawRelicConfig.RawRelicMainAffixConfig, Map<Integer, Map<String, Map<String, Map<String, Double>>>>> {

    @Override
    protected Path resolveInputPath(String dataDir) {
        return Path.of(dataDir, "ExcelOutput/RelicMainAffixConfig.json");
    }

    @Override
    protected Path resolveOutputPath(String projectDir) {
        return Path.of("data/main_attribute.json");
    }

    @Override
    protected Map<Integer, Map<String, Map<String, Map<String, Double>>>> processData(List<RawRelicConfig.RawRelicMainAffixConfig> rawList) {
        Map<Integer, Map<String, Map<String, Map<String, Double>>>> result = new HashMap<>();
        for (RawRelicConfig.RawRelicMainAffixConfig config : rawList) {
            if (config.GroupID() > 100) continue;
            
            int groupId = config.GroupID();
            int star = groupId / 10;
            String part = PART_MAPPING.get(groupId % 10);
            String attributeKey = RELIC_MAPPING.get(config.Property());
            
            result
                .computeIfAbsent(star, k -> new HashMap<>())
                .computeIfAbsent(part, k -> new HashMap<>())
                .put(attributeKey, Utils.processRelicMainAttribute(config));
        }
        return result;
    }
}
