package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * The type of effect a skill produces, mapped from the {@code skill_effect} field
 * in {@code skills.json}.
 *
 * <p>Every value present in the game data ({@code src/main/resources/data/skills.json})
 * is covered here. Each effect is assigned to a {@link Category} which describes
 * whether the skill produces {@link Category#DAMAGE damage} or some other kind
 * of effect ({@link Category#HEAL heal}, {@link Category#BUFF buff}, ...).
 *
 * <p>The {@link Category} is the primary discriminator for skill execution:
 * only {@link Category#DAMAGE} effects construct and settle a
 * {@link com.laosun.aluminium.models.Damage Damage} object; all other categories
 * run side effects (healing, shielding, debuffs, summons...) and therefore must not
 * be fed into the damage pipeline.
 *
 * @see com.laosun.aluminium.models.SkillData#getEffect()
 */
public enum SkillEffectType {
    /**
     * Deals an element damage to a single enemy target.
     */
    @SerializedName("SingleAttack") SINGLE_ATTACK("SingleAttack", Category.DAMAGE),
    /**
     * Exploration (maze) attack: hits an enemy on the map before the battle
     * starts and reduces their toughness. Also covers the battle-entry attack.
     * <p>
     * IN MAP EFFECT
     */
    @SerializedName("MazeAttack") MAZE_ATTACK("MazeAttack", Category.DAMAGE),
    /**
     * Deals damage to all enemies on the field.
     */
    @SerializedName("AoEAttack") AOE_ATTACK("AoEAttack", Category.DAMAGE),
    /**
     * Deals damage to the primary target plus adjacent enemies (spread damage).
     */
    @SerializedName("Blast") BLAST("Blast", Category.DAMAGE),
    /**
     * Deals damage multiple times, each instance hitting a random/designated target.
     */
    @SerializedName("Bounce") BOUNCE("Bounce", Category.DAMAGE),

    /**
     * Restores HP (healing) or regenerates energy for friendly targets.
     */
    @SerializedName("Restore") RESTORE("Restore", Category.HEAL),

    /**
     * Applies a positive effect to friendly targets (buffs, action advance,
     * energy gain, ...).
     */
    @SerializedName("Support") SUPPORT("Support", Category.BUFF),
    /**
     * Applies a shield / defence-related positive effect to friendly targets.
     */
    @SerializedName("Defence") DEFENCE("Defence", Category.BUFF),
    /**
     * Passive / enhancement effect: talents, extra abilities and technique
     * effects that mostly describe a passive stance rather than an active action.
     */
    @SerializedName("Enhance") ENHANCE("Enhance", Category.PASSIVE),

    /**
     * Imposes a debuff / crowd control (e.g. Imprison, Slow, Frozen) or
     * impairs enemies in an area.
     * <p>
     * IN MAP EFFECT
     */
    @SerializedName("Impair") IMPAIR("Impair", Category.CONTROL),

    /**
     * Summons an entity such as
     * {@link com.laosun.aluminium.models.Summon}.
     */
    @SerializedName("Summon") SUMMON("Summon", Category.SUMMON);

    /**
     * The raw effect string used in game data files, e.g. {@code "Blast"}.
     */
    @Getter
    private final String type;

    /**
     * The broad category this effect belongs to.
     */
    @Getter
    private final Category category;

    private static final Map<String, SkillEffectType> BY_STRING = new HashMap<>();

    static {
        for (SkillEffectType type : values()) {
            BY_STRING.put(type.type, type);
        }
    }

    SkillEffectType(String type, Category category) {
        this.type = type;
        this.category = category;
    }

    /**
     * Looks up an effect type by its raw game-data string (e.g. {@code "Blast"}).
     *
     * @param string the raw value of the {@code skill_effect} field
     * @return the matching enumeration value, or {@code null} if the string is
     *         {@code null} or unknown
     */
    public static SkillEffectType fromString(String string) {
        if (string == null) {
            return null;
        }
        return BY_STRING.get(string);
    }

    /**
     * Whether skills of this effect type deal damage. Only {@link Category#DAMAGE}
     * effects construct a {@link com.laosun.aluminium.models.Damage} object.
     *
     * @return {@code true} if this is an attack effect
     */
    public boolean isDamaging() {
        return category == Category.DAMAGE;
    }

    /**
     * Broad classification of what a skill does.
     */
    public enum Category {
        /**
         * Deals damage (single, aoe, blast, bounce, maze attack).
         */
        DAMAGE,
        /**
         * Heals or restores HP / energy.
         */
        HEAL,
        /**
         * Grants a positive effect, shield or buff to friendly targets.
         */
        BUFF,
        /**
         * Imposes a debuff, crowd control or impairment on enemies.
         */
        CONTROL,
        /**
         * Summons an entity (e.g. {@link com.laosun.aluminium.models.Summon}).
         */
        SUMMON,
        /**
         * Passive / enhancement skill, no direct action parameter (e.g. talents).
         */
        PASSIVE
    }
}
