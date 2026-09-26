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
 * @param hits       number of <b>segments</b> (each settles independently and rolls crit
 *                   independently). ⚠ See the note below — this is <b>not</b> a target count.
 * @param damageType damage type string (see {@code DamageType.fromString}); {@code null} = normal
 * @param effect     skill shape ({@code SingleAttack} / {@code AoEAttack} / {@code Blast}, parsed by
 *                   {@code SkillEffectType.fromString}); {@code null} = single target
 * @param guessed    whether the multiplier is a guessed value
 *
 *                   <p>⚠ <b>{@code hits} means segments here, not targets.</b> {@code ROADMAP}'s P9-1 plan describes a
 *                   future enemy skill table whose {@code hits} field means "how many targets, 0 = all". The two must
 *                   not be merged silently: the shipped entries were written as segments (8013010 "Trampling Stomp" has
 *                   {@code hits: 2} and means two segments on one target), so reusing the name for a target count when
 *                   the real table lands would change every existing enemy's behaviour without a single test failing.
 *                   Target count is expressed by {@link #effect()} instead, which is additive and defaults to today's
 *                   behaviour.
 */
public record EnemySkillData(int id,
                             Translate name,
                             DamageElement element,
                             double multiplier,
                             int hits,
                             @SerializedName("damage_type") String damageType,
                             String effect,
                             boolean guessed) {
}
