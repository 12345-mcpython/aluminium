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
 *   <li><b>Base attributes</b> ({@code isPercent = false}): HEALTH, ATTACK, DEFENCE, SPEED - 
 *   these have raw numeric values and can receive percentage-based bonuses via their
 *   corresponding PERCENT variants.</li>
 *   <li><b>Percent/rate attributes</b> ({@code isPercent = true}): all others - these
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
    /**
     * How much a BREAK hit is worth (2026-09-30; reader: cone 21056's "使我方全体造成的击破伤容提高").
     *
     * <p>A channel of its own because {@link DamageType#BREAK} is deliberately NOT boostable: the ordinary DMG
     * boost zone never touches break damage, so a sentence about break damage needs somewhere else to land. It
     * multiplies the break BASE (see {@code BreakDamageCalculator.build}), next to break effect.
     */
    @SerializedName("break_damage_boost") BREAK_DAMAGE_BOOST("break_damage_boost"),
    /**
     * How much less damage this unit TAKES, across every element (2026-09-30; reader: cone 21002's
     * "使我方全体的全属性抗性提高").
     *
     * <p>The victim-side twin of {@link #RESISTANCE_REDUCTION}: that one is stated on the ATTACKER and
     * lowers the target's resistance, this one is stated on the VICTIM and raises its own. Both feed the same
     * subtraction in the resistance zone, and neither is folded into penetration -- a negative resistance is
     * meant to be fully effective, which is why the zone's clamp must not swallow them.
     */
    @SerializedName("all_type_resistance") ALL_TYPE_RESISTANCE("all_type_resistance"),
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
     * Damage dealt by <b>follow-up attacks only</b> - i.e. by additional damage
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
    /**
     * Damage dealt by a <b>memosprite</b> (忆灵) only -- the third sibling of
     * {@link #FOLLOW_UP_DAMAGE_BOOST} and {@link #BASIC_ATTACK_DAMAGE_BOOST}, for "装备者忆灵造成的暴击伤害额外提高 X%".
     *
     * <p>Note: Gated in {@code Battle.assemble} on `attacker == memospriteOf(summon.getMaster())`, NOT on {@code instanceof Summon}:
     * the documents distinguish a memosprite from an ordinary summon, and the loose test would raise a summon's damage under a sentence that
     * never mentions it.
     */
    @SerializedName("memosprite_damage_boost") MEMOSPRITE_DAMAGE_BOOST("memosprite_damage_boost"),

    @SerializedName("elation_damage_boost") ELATION_DAMAGE_BOOST("elation_damage_boost"),

    /**
     * Damage dealt by <b>basic attacks</b> only ("普攻造成的伤害提高 X%") - the basic-attack sibling of
     * {@link #FOLLOW_UP_DAMAGE_BOOST}.
     *
     * <p><b>Why a scoped attribute has to exist at all.</b> A basic attack and a skill are both
     * {@code DamageType.NORMAL}, so the damage <i>type</i> cannot tell them apart - which is why set 108's
     * "the DMG dealt by their Skill and Ultimate increases by 18%" had no way to be expressed. What can tell
     * them apart is the category of the cast that produced the instance
     * ({@code Damage.getCastCategory()}), and {@code Battle.assemble} reads it to pick one of these.
     *
     * <p>Note: <b>Appended at the end of the enum on purpose.</b> {@code CanHit} indexes its attribute array by
     * {@link #ordinal()}, so inserting these next to {@link #FOLLOW_UP_DAMAGE_BOOST} would renumber every
     * constant after it - a silent, whole-engine shift. New constants go at the bottom.
     */
    @SerializedName("basic_attack_damage_boost") BASIC_ATTACK_DAMAGE_BOOST("basic_attack_damage_boost"),

    /**
     * Damage dealt by <b>skills</b> only ("战技造成的伤害提高 X%"). See
     * {@link #BASIC_ATTACK_DAMAGE_BOOST} for why the scope needs its own attribute.
     */
    @SerializedName("skill_damage_boost") SKILL_DAMAGE_BOOST("skill_damage_boost"),

    /**
     * Damage dealt by <b>ultimates</b> only ("终结技造成的伤害提高 X%"). See
     * {@link #BASIC_ATTACK_DAMAGE_BOOST} for why the scope needs its own attribute.
     */
    @SerializedName("ultimate_damage_boost") ULTIMATE_DAMAGE_BOOST("ultimate_damage_boost"),
    /**
     * Damage-over-time only (322 Revelry by the Sea (逐火者的航迹), "使装备者造成的持续伤害额外提高 X%").
     *
     * <p>Note: <b>Appended, never inserted</b>: {@link #ordinal()} is part of the persisted attribute order (see the
     * warning above {@link #BASIC_ATTACK_DAMAGE_BOOST}), so a new scope goes at the end.
     *
     * <p>It is picked by the damage <b>type</b> in {@code Battle}'s assembly, exactly like
     * {@link #FOLLOW_UP_DAMAGE_BOOST} -- a DoT instance is {@code DamageType.DOT}, which the boost zone does
     * <b>not</b> skip (only break / super break / true damage are skipped), so the attribute reaches the instance.
     */
    @SerializedName("dot_damage_boost") DOT_DAMAGE_BOOST("dot_damage_boost"),

    /**
     * "使装备者<b>提供的护盾量</b>提高 X%" - the shield the <b>provider</b> creates absorbs more.
     *
     * <p><b>Whose attribute it is.</b> Unlike every other boost in this enum, it is not read from the dealer or the
     * victim but from the unit <i>granting</i> the shield: "装备者提供的" names the giver, and the same shield given
     * by somebody else is unaffected. That is why {@code Battle.grantShield} takes a provider and why the number is
     * snapshotted into the shield at grant time - a shield already standing does not grow when the giver later picks
     * up a boost.
     *
     * <p><b>Why it needed its own constant instead of reusing {@code MODIFY_ATTR} on an existing one.</b> There is no
     * other attribute that means "how much shield I make": {@code OUTGOING_HEALING_BOOST} is its healing twin, and
     * folding the two together would make "提供的护盾量提高" silently also strengthen heals.
     *
     * <p>Readers: relic 103 Knight of Purity Palace (净庭教宗的圣骑士) 4-piece (20%), relic 128 Self-Enshrouded Recluse (自匿星芒的隐士) 2-piece (10%) and 4-piece (12%), and a
     * light cone (12/15/18/21/24%). All four say "提供的护盾量" - the provider's side, never the receiver's; the
     * receiver-side spelling ("shield gained") has no reader in the corpus and is deliberately not modelled.
     *
     * <p>Note: <b>Appended, never inserted</b>: {@link #ordinal()} indexes every unit's attribute array (see the warning
     * above {@link #BASIC_ATTACK_DAMAGE_BOOST}).
     */
    @SerializedName("shield_boost") SHIELD_BOOST("shield_boost"),

    /**
     * "受到攻击的概率大幅提高" - the <b>soft</b> aggro weight, as a ratio added to the unit's own weight.
     *
     * <p><b>Why it is not {@code TauntBuff}.</b> A taunt is a hard constraint: while it is up, a single-target attack
     * can only pick the taunter. This is the other kind of thing - the same weighted draw, with this unit's weight
     * raised. Writing a taunt for "概率大幅提高" would replace a soft weight with a hard lock, i.e. a different
     * mechanic rather than a different number, which is why March 7th's Skill's third sentence stayed registered for so
     * long instead of being approximated.
     *
     * <p><b>Where the magnitude comes from - it is in the data, not invented.</b> The sentence states no number, but
     * 100102's {@code param_list} has <b>five</b> slots per level and the prose names only four
     * (#1 = DEF share 0.5, #2 = 3 turns, #3 = the 30% gate, #4 = the flat 60); the fifth is a constant <b>5</b>.
     * The game's own ability config for that Skill settles what it is: the shield modifier is attached with the
     * property <b>{@code AggroAddedRatio}</b> on the shield's owner, gated on {@code ByCompareHPRatio} against
     * param #3, where the {@code SuccessTaskList} (HP% >= 30%) carries that fifth value dynamically while the
     * {@code FailedTaskList} (HP% < 30%) pins it to <b>0</b> - the sentence's gate and its magnitude, one per branch.
     * {@code AggroAddedRatio} is a <i>ratio</i>, so 5 reads as  x (1 + 5) =  x 6 on the unit's weight
     * ({@code Battle.aggroOf}), which is what "大幅提高" means.
     *
     * <p>Note: <b>Appended, never inserted</b>: {@link #ordinal()} indexes every unit's attribute array (see the warning
     * above {@link #BASIC_ATTACK_DAMAGE_BOOST}).
     */
    @SerializedName("aggro_added_ratio") AGGRO_ADDED_RATIO("aggro_added_ratio"),

    /**
     * "使敌方全体<b>全属性抗性降低</b> X%" - the victim-side reduction of its own resistance, applied in the
     * damage pipeline beside penetration.
     *
     * <p><b>Why it is not {@link #DAMAGE_PENETRATION}.</b> Both lower the effective resistance, but the
     * difference shows below zero: penetration is the attacker's side and cannot "ignore" resistance the victim
     * does not have, while a reduction can drive the victim's resistance negative and the engine's own formula
     * already treats that as fully effective ({@code ResistArea.rate()}: {@code 1 - clamp(RES - pen, -1, 0.9)},
     * i.e. 0.1 to 2.0). Folding the two together would make "抗性降低" stop at zero and hand every attacker
     * penetration instead.
     *
     * <p><b>Readers - measured, not assumed (2026-09-29).</b> Nineteen corpus documents say
     * "全属性抗性降低"; the engine-side ones are 1004, 1006, 1203, 1218, 1304, 1308, 1321, 1405, 140, 1410,
     * 1504 and 150 (plus 1222/1505, which have no engine file, and the light cone / stat entry documents). Before this constant
     * existed, <b>no</b> shipped file mentioned any resistance-reduction spelling at all.
     *
     * <p>Note: <b>Appended, never inserted</b>: {@link #ordinal()} indexes every unit's attribute array.
     */
    @SerializedName("resistance_reduction") RESISTANCE_REDUCTION("resistance_reduction");

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
     * <p><b>Where this table comes from - it is not invented here.</b> The generator that produces
     * {@code main_attribute.json} / {@code sub_attribute.json} / {@code relic_sets.json} keeps one
     * {@code inner_outer_mapping} dictionary for exactly this translation; that dictionary is why the
     * generated affix files are keyed by {@code attack_percent} / {@code crit_chance} instead of by
     * {@code AttackAddedRatio} / {@code CriticalChanceBase}. Relic sets are the one place where the raw
     * game token survives into the JSON (the generator takes the property name straight out of the
     * obfuscated {@code PropertyList} entries and cannot pass it through the dictionary), so the engine
     * has to carry the same dictionary itself. Every value here is that generator table, verbatim.
     *
     * <p>Deliberately <b>not</b> derived from {@link #attributeString}: a name that merely "looks like" an
     * attribute (say {@code AttackAddedRatio} to {@code ATTACK}) is wrong - it means {@code attack_percent},
     * not flat attack - and silently mis-mapping one property is worse than failing.
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
     * for them. So a buff must never target one - it would look like it worked and change nothing.
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
