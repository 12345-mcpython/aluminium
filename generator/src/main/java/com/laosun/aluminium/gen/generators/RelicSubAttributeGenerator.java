package com.laosun.aluminium.gen.generators;

import com.laosun.aluminium.gen.Utils;
import com.laosun.aluminium.gen.origin.RawRelicConfig;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.beans.Model.RELIC_MAPPING;

public class RelicSubAttributeGenerator 
    extends Generator<RawRelicConfig.RawRelicSubAffixConfig, Map<Integer, Map<String, Map<String, Double>>>> {

    @Override
    protected Path resolveInputPath(String projectDir) {
        return Path.of(projectDir, "ExcelOutput/RelicSubAffixConfig.json");
    }

    @Override
    protected Path resolveOutputPath(String projectDir) {
        return Path.of("data/sub_attribute.json");
    }

    @Override
    protected Map<Integer, Map<String, Map<String, Double>>> processData(List<RawRelicConfig.RawRelicSubAffixConfig> rawList) {
        Map<Integer, Map<String, Map<String, Double>>> result = new HashMap<>();
        for (RawRelicConfig.RawRelicSubAffixConfig config : rawList) {
            String attributeKey = RELIC_MAPPING.get(config.Property());
            result
                .computeIfAbsent(config.GroupID(), k -> new HashMap<>())
                .put(attributeKey, Utils.processRelicSubAttribute(config));
        }
        return result;
    }
}