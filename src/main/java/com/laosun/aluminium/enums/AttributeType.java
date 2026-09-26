package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.beans.EliteGroup;
import lombok.Getter;

import java.lang.annotation.ElementType;
import java.util.HashMap;
import java.util.Map;

/**
 * All character and equipment attribute types used in combat stat calculation.
 *
 * <p>Attributes fall into two categories:
 * <ul>
 *   <li><b>Base attributes</b> ({@code isPercent = false}): HEALTH, ATTACK, DEFENCE, SPEED —
 *   these have raw numeric values and can receive percentage-based bonuses via their
 *   corresponding PERCENT variants.</li>
 *   <li><b>Percent/rate attributes</b> ({@code isPercent = true}): all others — these
 *   represent ratios (e.g. CRIT_CHANCE is between 0 and 1) and are not affected by
 *   percentage-based bonus conversion.</li>
 * </ul>
 *
 * <p>In the internal attribute array built by {@link com.laosun.aluminium.utils.AttributeBuilder},
 * PERCENT-typed attributes are stored as {@code null} because their values are merged into
 * the corresponding base attribute's modifier list.
 */
@Getter
public enum AttributeType {
    @SerializedName("health") HEALTH("health", false),
    @SerializedName("defence") DEFENCE("defence", false),
    @SerializedName("attack") ATTACK("attack", false),
    @SerializedName("speed") SPEED("speed", false),
    // In the attribute calculate these are temporary value need be null.
    @SerializedName("health_percent") HEALTH_PERCENT("health_percent"),
    @SerializedName("defence_percent") DEFENCE_PERCENT("defence_percent"),
    @SerializedName("attack_percent") ATTACK_PERCENT("attack_percent"),
    @SerializedName("speed_percent") SPEED_PERCENT("speed_percent"),
    // end value

    @SerializedName("crit_chance") CRIT_CHANCE("crit_chance"),
    @SerializedName("crit_attack") CRIT_ATTACK("crit_attack"),
    @SerializedName("effect_hit_rate") EFFECT_HIT_RATE("effect_hit_rate"),
    @SerializedName("effect_resistance") EFFECT_RESISTANCE("effect_resistance"),

    @SerializedName("outgoing_healing_boost") OUTGOING_HEALING_BOOST("outgoing_healing_boost"),
    @SerializedName("heal_taken_ratio") HEAL_TAKEN_RATIO("heal_taken_ratio"),

    @SerializedName("breaking_effect") BREAKING_EFFECT("breaking_effect"),
    @SerializedName("energy_regeneration_rate") ENERGY_REGENERATION_RATE("energy_regeneration_rate"),

    @SerializedName("physical_damage_boost") PHYSICAL_DAMAGE_BOOST("physical_damage_boost"),
    @SerializedName("fire_damage_boost") FIRE_DAMAGE_BOOST("fire_damage_boost"),
    @SerializedName("ice_damage_boost") ICE_DAMAGE_BOOST("ice_damage_boost"),
    @SerializedName("thunder_damage_boost") THUNDER_DAMAGE_BOOST("thunder_damage_boost"),
    @SerializedName("wind_damage_boost") WIND_DAMAGE_BOOST("wind_damage_boost"),
    @SerializedName("quantum_damage_boost") QUANTUM_DAMAGE_BOOST("quantum_damage_boost"),
    @SerializedName("imaginary_damage_boost") IMAGINARY_DAMAGE_BOOST("imaginary_damage_boost"),

    @SerializedName("damage_penetration") DAMAGE_PENETRATION("damage_penetration"),
    @SerializedName("defence_ignore") DEFENCE_IGNORE("defence_ignore"),

    @SerializedName("all_damage_type_boost") ALL_DAMAGE_TYPE_BOOST("all_damage_type_boost"),

    /**
     * Damage dealt by <b>follow-up attacks only</b> — i.e. by additional damage
     * ({@code DamageType.ADDITIONAL}), which is the engine's one representation of a follow-up.
     *
     * <p>It sits next to {@link #ALL_DAMAGE_TYPE_BOOST} rather than replacing it: set 115's 2-piece
     * ("Increases the DMG dealt by Follow-Up ATK by 20%") must boost follow-ups and nothing else, so a
     * rule that granted the all-type boost instead would silently buff basic attacks, skills and
     * ultimates too.
     *
     * <p>Applied in {@code Battle.assemble}'s DMG-boost zone, gated on the damage type.
     */
    @SerializedName("follow_up_damage_boost") FOLLOW_UP_DAMAGE_BOOST("follow_up_damage_boost"),

    @SerializedName("elation_damage_boost") ELATION_DAMAGE_BOOST("elation_damage_boost"),

    /**
     * Damage dealt by <b>basic attacks</b> only (「普攻造成的伤害提高 X%」) — the basic-attack sibling of
     * {@link #FOLLOW_UP_DAMAGE_BOOST}.
     *
     * <p><b>Why a scoped attribute has to exist at all.</b> A basic attack and a skill are both
     * {@code DamageType.NORMAL}, so the damage <i>type</i> cannot tell them apart — which is why set 108's
     * "the DMG dealt by their Skill and Ultimate increases by 18%" had no way to be expressed. What can tell
     * them apart is the category of the cast that produced the instance
     * ({@code Damage.getCastCategory()}), and {@code Battle.assemble} reads it to pick one of these.
     *
     * <p>⚠ <b>Appended at the end of the enum on purpose.</b> {@code CanHit} indexes its attribute array by
     * {@link #ordinal()}, so inserting these next to {@link #FOLLOW_UP_DAMAGE_BOOST} would renumber every
     * constant after it — a silent, whole-engine shift. New constants go at the bottom.
     */
    @SerializedName("basic_attack_damage_boost") BASIC_ATTACK_DAMAGE_BOOST("basic_attack_damage_boost"),

    /**
     * Damage dealt by <b>skills</b> only (「战技造成的伤害提高 X%」). See
     * {@link #BASIC_ATTACK_DAMAGE_BOOST} for why the scope needs its own attribute.
     */
    @SerializedName("skill_damage_boost") SKILL_DAMAGE_BOOST("skill_damage_boost"),

    /**
     * Damage dealt by <b>ultimates</b> only (「终结技造成的伤害提高 X%」). See
     * {@link #BASIC_ATTACK_DAMAGE_BOOST} for why the scope needs its own attribute.
     */
    @SerializedName("ultimate_damage_boost") ULTIMATE_DAMAGE_BOOST("ultimate_damage_boost");

    private static final Map<String, AttributeType> BY_STRING = new HashMap<>();

    static {
        for (AttributeType type : values()) {
            BY_STRING.put(type.attributeString.toLowerCase(), type);
        }
    }

    /**
     * The string identifier used in JSON serialization and game data files.
     */
    public final String attributeString;
    /**
     * Whether this attribute represents a percentage/ratio rather than a raw value.
     * {@code true} for attributes like CRIT_CHANCE, DAMAGE_BOOST, etc.
     */
    public final boolean isPercent;

    private static final Map<DamageElement, AttributeType> BOOST_DAMAGE_MAPPING = Map.ofEntries(
            Map.entry(DamageElement.PHYSICAL, PHYSICAL_DAMAGE_BOOST),
            Map.entry(DamageElement.FIRE, FIRE_DAMAGE_BOOST),
            Map.entry(DamageElement.ICE, ICE_DAMAGE_BOOST),
            Map.entry(DamageElement.THUNDER, THUNDER_DAMAGE_BOOST),
            Map.entry(DamageElement.WIND, WIND_DAMAGE_BOOST),
            Map.entry(DamageElement.QUANTUM, QUANTUM_DAMAGE_BOOST),
            Map.entry(DamageElement.IMAGINARY, IMAGINARY_DAMAGE_BOOST)
    );

    public static AttributeType getBoostByElement(DamageElement elementType) {
        return BOOST_DAMAGE_MAPPING.get(elementType);
    }

    /**
     * The game's own property vocabulary ({@code relic_sets.json}'s {@code properties[].type},
     * {@code RelicMainAffixConfig.Property}, {@code RelicSubAffixConfig.Property}, …) mapped to the
     * attribute it modifies.
     *
     * <p><b>Where this table comes from — it is not invented here.</b> The generator that produces
     * {@code main_attribute.json} / {@code sub_attribute.json} / {@code relic_sets.json} keeps one
     * {@code inner_outer_mapping} dictionary for exactly this translation; that dictionary is why the
     * generated affix files are keyed by {@code attack_percent} / {@code crit_chance} instead of by
     * {@code AttackAddedRatio} / {@code CriticalChanceBase}. Relic sets are the one place where the raw
     * game token survives into the JSON (the generator takes the property name straight out of the
     * obfuscated {@code PropertyList} entries and cannot pass it through the dictionary), so the engine
     * has to carry the same dictionary itself. Every value here is that generator table, verbatim.
     *
     * <p>Deliberately <b>not</b> derived from {@link #attributeString}: a name that merely "looks like" an
     * attribute (say {@code AttackAddedRatio} → {@code ATTACK}) is wrong — it means {@code attack_percent},
     * not flat attack — and silently mis-mapping one property is worse than failing.
     */
    private static final Map<String, AttributeType> BY_GAME_PROPERTY = Map.ofEntries(
            Map.entry("HPDelta", HEALTH),
            Map.entry("AttackDelta", ATTACK),
            Map.entry("DefenceDelta", DEFENCE),
            Map.entry("SpeedDelta", SPEED),
            Map.entry("BaseSpeed", SPEED),
            Map.entry("HPAddedRatio", HEALTH_PERCENT),
            Map.entry("AttackAddedRatio", ATTACK_PERCENT),
            Map.entry("DefenceAddedRatio", DEFENCE_PERCENT),
            Map.entry("SpeedAddedRatio", SPEED_PERCENT),
            Map.entry("CriticalChanceBase", CRIT_CHANCE),
            Map.entry("CriticalDamageBase", CRIT_ATTACK),
            Map.entry("StatusProbabilityBase", EFFECT_HIT_RATE),
            Map.entry("StatusResistanceBase", EFFECT_RESISTANCE),
            Map.entry("BreakDamageAddedRatioBase", BREAKING_EFFECT),
            Map.entry("SPRatioBase", ENERGY_REGENERATION_RATE),
            Map.entry("HealRatioBase", OUTGOING_HEALING_BOOST),
            Map.entry("HealTakenRatio", HEAL_TAKEN_RATIO),
            Map.entry("PhysicalAddedRatio", PHYSICAL_DAMAGE_BOOST),
            Map.entry("FireAddedRatio", FIRE_DAMAGE_BOOST),
            Map.entry("IceAddedRatio", ICE_DAMAGE_BOOST),
            Map.entry("ThunderAddedRatio", THUNDER_DAMAGE_BOOST),
            Map.entry("WindAddedRatio", WIND_DAMAGE_BOOST),
            Map.entry("QuantumAddedRatio", QUANTUM_DAMAGE_BOOST),
            Map.entry("ImaginaryAddedRatio", IMAGINARY_DAMAGE_BOOST),
            Map.entry("AllDamageTypeAddedRatio", ALL_DAMAGE_TYPE_BOOST),
            Map.entry("ElationDamageAddedRatioBase", ELATION_DAMAGE_BOOST)
    );

    /**
     * Resolves a game property name (e.g. {@code AttackAddedRatio}) to its attribute.
     *
     * <p><b>Fails loudly on purpose.</b> A property the engine cannot map used to mean "this set bonus is
     * quietly dropped", which is invisible: the only symptom is a character being a few percent weaker
     * than the game. Throwing with the offending name turns that into a bug report.
     *
     * @param gameProperty the property name exactly as the game data spells it (e.g. {@code CriticalChanceBase})
     * @return the attribute that property modifies
     * @throws IllegalArgumentException when the name is null or is not in the game vocabulary
     */
    public static AttributeType fromGameProperty(String gameProperty) {
        if (gameProperty == null) {
            throw new IllegalArgumentException("Unknown game property name: null");
        }
        AttributeType type = BY_GAME_PROPERTY.get(gameProperty.trim());
        if (type == null) {
            throw new IllegalArgumentException("Unknown game property name: '" + gameProperty
                    + "' — add it to AttributeType.BY_GAME_PROPERTY instead of skipping it");
        }
        return type;
    }

    AttributeType(String string) {
        this(string, true);
    }

    AttributeType(String string, boolean isPercent) {
        attributeString = string;
        this.isPercent = isPercent;
    }

    /**
     * Looks up an attribute type by its string identifier (case-insensitive).
     *
     * @param string the attribute string, e.g. "health", "crit_chance"
     * @return the matching {@code AttributeType}
     * @throws IllegalArgumentException if no match is found
     */
    public static AttributeType fromString(String string) {
        if (string == null) {
            throw new IllegalArgumentException("Unknown AttributeType: null");
        }
        AttributeType type = BY_STRING.get(string.trim().toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Unknown AttributeType: " + string);
        }
        return type;
    }

    /**
     * Whether this is one of the four {@code *_PERCENT} variants, which exist only as
     * {@link com.laosun.aluminium.utils.AttributeBuilder} input keys.
     *
     * <p>They are <b>not</b> runtime attributes: the builder folds each one into its base
     * attribute's modifier list and then stores the variant slot as {@code null}, which is why
     * {@link com.laosun.aluminium.models.CanHit#getAttribute(AttributeType)} returns {@code null}
     * for them. So a buff must never target one — it would look like it worked and change nothing.
     *
     * <p>Note this is <b>not</b> the same question as {@link #isPercent}: that one is about the
     * value's units ({@code CRIT_CHANCE} and the damage-boost family are ratios too, and they are
     * perfectly valid buff targets). Only these four are unusable.
     */
    public boolean isPercentVariant() {
        return this == HEALTH_PERCENT || this == DEFENCE_PERCENT
                || this == ATTACK_PERCENT || this == SPEED_PERCENT;
    }
}
