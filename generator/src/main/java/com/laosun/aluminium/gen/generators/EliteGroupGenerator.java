package com.laosun.aluminium.gen.generators;

import com.laosun.aluminium.beans.EliteGroup;
import com.laosun.aluminium.gen.origin.RawEliteGroup;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EliteGroupGenerator extends Generator<RawEliteGroup, Map<Integer, EliteGroup>> {

    @Override
    protected Path resolveInputPath(String projectDir) {
        return Path.of(projectDir, "ExcelOutput", "EliteGroup.json");
    }

    @Override
    protected Path resolveOutputPath(String projectDir) {
        return Path.of("data/elite_group.json");
    }

    @Override
    protected Map<Integer, EliteGroup> processData(List<RawEliteGroup> rawList) {
        Map<Integer, EliteGroup> map = new HashMap<>();
        for (RawEliteGroup rawEliteGroup : rawList) {
            map.put(rawEliteGroup.EliteGroup(), new EliteGroup(rawEliteGroup.HPRatio().Value(), rawEliteGroup.AttackRatio().Value(),
                    rawEliteGroup.DefenceRatio().Value(), rawEliteGroup.SpeedRatio().Value(), rawEliteGroup.StanceRatio().Value()));
        }
        return map;
    }
}
