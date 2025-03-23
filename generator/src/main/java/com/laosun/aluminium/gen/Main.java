package com.laosun.aluminium.gen;

import com.laosun.aluminium.gen.generators.*;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

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
        var hard = new HardLevelGroupGenerator().generate(projectDir);
        var main = new RelicSubAttributeGenerator().generate(projectDir);
        var sub = new RelicMainAttributeGenerator().generate(projectDir);
        var elite = new EliteGroupGenerator().generate(projectDir);
        var chinese = Objects.requireNonNull(LanguageGenerator.readLanguageMap("en_us", projectDir));
        System.out.println(hard);
        System.out.println(main);
        System.out.println(sub);
        System.out.println(elite);
        System.out.println(chinese.get("12182123284380739113"));
    }
}
