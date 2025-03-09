package com.laosun.aluminium.gen.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.gen.origin.DoubleGetter;
import com.laosun.aluminium.gen.origin.RawHardLevelGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HardLevelGroupGenerator {
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
}
