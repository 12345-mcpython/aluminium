package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.data.origin.RawHardLevelGroup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {


    public static void exportHardLevelGroups(String projectDir) throws IOException {
        Map<Integer, HardLevelGroup> hardLevelGroups;

        Map<Integer, RawHardLevelGroup> rawHardLevelGroups;
        String hardLevelGroupString = Files.readString(Path.of(projectDir + "/ExcelOutput/HardLevelGroup.json"));
        Gson gson = new Gson();
        List<RawHardLevelGroup> rawHardLevelGroupList = gson.fromJson(hardLevelGroupString, new TypeToken<>() {});
    }

    public static void main(String[] args) throws IOException {
        String projectDir = System.getProperty("aluminium.dataDir");
        File dir = new File(projectDir);
        if(!dir.exists()){
            System.out.println("Directory does not exist.");
            System.exit(1);
        }
        String hardLevelGroupString = Files.readString(Path.of(projectDir + "/ExcelOutput/HardLevelGroup.json"));
        Gson gson = new Gson();
        List<RawHardLevelGroup> rawHardLevelGroups = gson.fromJson(hardLevelGroupString, new TypeToken<>() {});
        System.out.println(rawHardLevelGroups.getFirst().AttackRatio().Value());
        System.out.println(rawHardLevelGroups.getFirst().SpeedRatio().Value());
    }
}
