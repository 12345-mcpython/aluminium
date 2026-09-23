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
 *
 * <p>{@code mt} is the path string ({@code protection / destruction / single / all / help /
 * debuff / healing / elation / memory}), and {@code aggro} is the **aggro value itself** (not a percentage):
 * Preservation 150 / Destruction 125 / others 100 / Hunt·Erudition 75 — checked across the whole data set, matching
 * the official tiers (P5-1).
 *
 * <p>{@code rarity} (star rating) is **4 or 5** (added before P8-2): in the data 93 characters are 23 4★ + 70 5★.
 * It is derived by the generator from the last digit of {@code AvatarConfig.Rarity} (shaped like
 * {@code CombatPowerAvatarRarityType5}).
 * **P8-2 needs it**: the skill level cap differs by star rating (a 4★ basic attack/skill caps at 10/12 or so,
 * a 5★ caps higher), so the star rating must be readable before skills are assembled. {@code 0} = missing data.
 */
public record CharacterData(Translate name, String attribute, String mt, String id, double attack, double defence,
                            double health, int speed, @SerializedName("max_energy")
                            Double maxEnergy, @SerializedName("crit_chance") double critChance,
                            @SerializedName("crit_attack") double critAttack, int aggro, int rarity) {
}
