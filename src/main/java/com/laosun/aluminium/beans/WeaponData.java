package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Raw weapon (light cone) data deserialized from weapons.json.
 *
 * <p>Contains base stats at level 1 and skill data. Final weapon stats are
 * computed by {@link com.laosun.aluminium.models.Weapon#build(int, int, boolean)}
 * applying level scaling.
 */
public record WeaponData(Translate name, @SerializedName("skill_description") Translate skillDescription, double attack,
                         double defence, double health, String type,
                         @SerializedName("weapon_skill_data") List<SkillData> weaponSkillData) {

    /**
     * Per-level skill data with scaling values and ability properties.
     */
    public record SkillData(int level, @SerializedName("skill_value") List<Double> skillValue,
                            @SerializedName("ability_property") List<AbilityProperty> abilityProperties) {

        /**
         * An ability property that maps an attribute name to a numeric value.
         */
        public record AbilityProperty(String attribute, double value) {
        }
    }
}
