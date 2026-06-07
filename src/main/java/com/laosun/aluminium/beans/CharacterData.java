package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

// Use Double for maxEnergy for the value be null!
public record CharacterData(Translate name, String attribute, String mt, String id, double attack, double defence,
                            double health, int speed, @Nullable @SerializedName("max_energy")
                            Double maxEnergy, @SerializedName("crit_chance") double critChance, @SerializedName("crit_attack") double critAttack) {
}
