package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Raw character data deserialized from the game's character_data.json.
 *
 * <p>Contains static base stats for a character at level 1. Final combat stats
 * are computed by {@link com.laosun.aluminium.models.Character.Builder} using
 * level scaling, equipment, and skill point bonuses on top of this data.
 *
 * <p>Use {@code Double} for {@code maxEnergy} because some characters may have
 * {@code null} energy values in the source data.
 */
public record CharacterData(Translate name, String attribute, String mt, String id, double attack, double defence,
                            double health, int speed, @Nullable @SerializedName("max_energy")
                            Double maxEnergy, @SerializedName("crit_chance") double critChance, @SerializedName("crit_attack") double critAttack) {
}
