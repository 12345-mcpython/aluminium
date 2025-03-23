package com.laosun.aluminium.gen.generators;

import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.gen.origin.DoubleGetter;
import com.laosun.aluminium.gen.origin.RawHardLevelGroup;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HardLevelGroupGenerator
    extends Generator<RawHardLevelGroup, Map<Integer, Map<Integer, HardLevelGroup>>> {

    @Override
    protected Path resolveInputPath(String projectDir) {
        return Path.of(projectDir, "ExcelOutput/HardLevelGroup.json");
    }

    @Override
    protected Path resolveOutputPath(String projectDir) {
        return Path.of("data/hard_level_groups.json");
    }

    @Override
    protected Map<Integer, Map<Integer, HardLevelGroup>> processData(List<RawHardLevelGroup> rawList) {
        Map<Integer, Map<Integer, HardLevelGroup>> result = new HashMap<>();
        for (RawHardLevelGroup raw : rawList) {
            result
                .computeIfAbsent(raw.HardLevelGroup(), k -> new HashMap<>())
                .put(raw.Level(), convertToHardLevelGroup(raw));
        }
        return result;
    }

    private HardLevelGroup convertToHardLevelGroup(RawHardLevelGroup raw) {
        return new HardLevelGroup(
            safeGetValue(raw.AttackRatio()),
            safeGetValue(raw.DefenceRatio()),
            safeGetValue(raw.HPRatio()),
            safeGetValue(raw.SpeedRatio()),
            safeGetValue(raw.StanceRatio()),
            safeGetValue(raw.StatusProbability()),
            safeGetValue(raw.StatusResistance())
        );
    }

    private static double safeGetValue(DoubleGetter getter) {
        return Objects.requireNonNullElse(getter, new DoubleGetter(0.0)).Value();
    }
}