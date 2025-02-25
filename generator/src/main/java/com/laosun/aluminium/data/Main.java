package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.RelicMainAttribute;
import com.laosun.aluminium.data.origin.DoubleGetter;
import com.laosun.aluminium.data.origin.RawHardLevelGroup;
import com.laosun.aluminium.data.origin.RawRelicMainAffixConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.laosun.aluminium.beans.Model.PART_MAPPING;
import static com.laosun.aluminium.beans.Model.RELIC_MAPPING;

public class Main {
    public static void exportHardLevelGroups(String projectDir) throws IOException {
        Map<Integer, Map<Integer, HardLevelGroup>> hardLevelGroups = new HashMap<>();
        var hardLevelGroupString = Files.readString(Path.of(projectDir + "/ExcelOutput/HardLevelGroup.json"));
        var gson = new GsonBuilder().setPrettyPrinting().create();
        List<RawHardLevelGroup> rawHardLevelGroupList = gson.fromJson(hardLevelGroupString, new TypeToken<>() {
        });
        for (RawHardLevelGroup rawHardLevelGroup : rawHardLevelGroupList) {
            var h = new HardLevelGroup(
                    Objects.requireNonNullElse(rawHardLevelGroup.AttackRatio(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.DefenceRatio(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.HPRatio(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.SpeedRatio(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.StanceRatio(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.StatusProbability(), new DoubleGetter(0.0)).Value(),
                    Objects.requireNonNullElse(rawHardLevelGroup.StatusResistance(), new DoubleGetter(0.0)).Value());
            if (!hardLevelGroups.containsKey(rawHardLevelGroup.HardLevelGroup())) {
                hardLevelGroups.put(rawHardLevelGroup.HardLevelGroup(), new HashMap<>());
            } else {
                hardLevelGroups.get(rawHardLevelGroup.HardLevelGroup()).put(rawHardLevelGroup.Level(), h);
            }
        }
        Files.write(Path.of("data/hard_level_groups.json"), gson.toJson(hardLevelGroups).getBytes());
    }

    public static void exportRelicAttributes(String projectDir) throws IOException {
        Map<Integer, Map<String, Map<String, Map<String, Double>>>> p = new HashMap<>();
        var relicMainAffixConfigString = Files.readString(Path.of(projectDir + "/ExcelOutput/RelicMainAffixConfig.json"));
        var gson = new GsonBuilder().setPrettyPrinting().create();
        List<RawRelicMainAffixConfig> rawRelicMainAffixConfigList = gson.fromJson(relicMainAffixConfigString, new TypeToken<>() {
        });
        for (var rawRelicMainAffixConfig : rawRelicMainAffixConfigList) {
            int groupId = rawRelicMainAffixConfig.GroupID();
            if (groupId > 100) {
                continue;
            }
            var part = PART_MAPPING.get(groupId % 10);
            var star = groupId / 10;
            var left = RELIC_MAPPING.get(rawRelicMainAffixConfig.Property());
            var processData = Utils.processRelicMainAttribute(rawRelicMainAffixConfig);
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

    public static void main(String[] args) throws IOException {
        if (!new File("data").exists()) {
            var ignored = new File("data").mkdir();
        }
        var projectDir = System.getProperty("aluminium.dataDir");
        var dir = new File(projectDir);
        if (!dir.exists()) {
            System.out.println("Directory does not exist.");
            System.exit(1);
        }
        exportHardLevelGroups(projectDir);
        exportRelicAttributes(projectDir);
    }
}
