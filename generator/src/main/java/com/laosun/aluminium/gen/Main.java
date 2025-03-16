package com.laosun.aluminium.gen;

import com.laosun.aluminium.gen.generators.HardLevelGroupGenerator;
import com.laosun.aluminium.gen.generators.RelicAttributeGenerator;

import java.io.File;
import java.io.IOException;

public class Main {
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
        HardLevelGroupGenerator.exportHardLevelGroups(projectDir);
        RelicAttributeGenerator.exportRelicMainAttributes(projectDir);
        RelicAttributeGenerator.exportRelicSubAttributes(projectDir);
    }
}
