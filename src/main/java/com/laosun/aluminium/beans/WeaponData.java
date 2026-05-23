package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record WeaponData(Translate name, @SerializedName("skill_description") Translate skillDescription, double attack,
                         double defence, double health, String type,
                         @SerializedName("weapon_skill_data") List<SkillData> weaponSkillData) {
    public record SkillData(int level, @SerializedName("skill_value") List<Double> skillValue,
                            @SerializedName("ability_property") List<AbilityProperty> abilityProperties) {
        public record AbilityProperty(String attribute, double value) {
        }
    }
}
