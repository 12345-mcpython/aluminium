package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.DamageElement;

/**
 * A single enemy skill (the values of {@code enemy_skills.json}) — home-made in P5-3.
 *
 * <p><b>Why home-made</b>: the game data of this project has no enemy skill table. {@code skills.json}
 * contains only character skills, and tbgd's delivered data has no monster skill multipliers either.
 * So the **multipliers in this table are guesses** (each entry carries a {@link #guessed()} flag); the
 * only goal is to make enemies "able to hit people with values of a sensible magnitude". Once the
 * source data is found (P9-1/P9-2), only the data file and the loading in {@code Constant} get
 * replaced — the engine side needs no change.
 *
 * @param id         skill id (unique; kept as the key for wiring up the real skill table in P9)
 * @param name       display name
 * @param element    damage element; {@code null} = use the monster's own {@code stance_type} (the
 *                   template's toughness attribute)
 * @param multiplier multiplier: {@code base = enemy attack × multiplier}
 * @param hits       number of hits (each hit settles independently and rolls crit independently)
 * @param damageType damage type string (see {@code DamageType.fromString}); {@code null} = normal
 * @param guessed    whether the multiplier is a guessed value
 */
public record EnemySkillData(int id,
                             Translate name,
                             DamageElement element,
                             double multiplier,
                             int hits,
                             @SerializedName("damage_type") String damageType,
                             boolean guessed) {
}
