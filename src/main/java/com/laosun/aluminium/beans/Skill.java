package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

// Some arguments may be enum
public record Skill(@SerializedName("attack_type") String attackType, @SerializedName("max_level") int maxLevel,
                    Translate name, @SerializedName("param_list") List<List<Double>> paramList,
                    @SerializedName("skill_effect") String skillEffect, @SerializedName("skill_id") int skillID,
                    @SerializedName("skill_introduction") Translate skillIntroduction,
                    @SerializedName("stance_list") StanceList stanceList) {
    public record StanceList(int single, int all, int spread) {
    }
}
