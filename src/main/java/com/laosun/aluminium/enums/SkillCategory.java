package com.laosun.aluminium.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * A skill's data-side {@code attack_type} (the raw value in {@code skills.json}).
 *
 * <p>Note: <b>Naming</b>: this enum is deliberately not called {@code SkillAttackType} - that name is already
 * taken by {@link SkillAttackType} (target shape: single target / blast / AoE), which is another axis.
 * This enum answers "what class of skill is this", and it is orthogonal to {@link SkillEffectType} (what the skill
 * actually does) as well.
 *
 * <p><b>Why this enum is needed</b>: the data stores {@code attack_type} as a bare string, and the engine has two
 * places that branch on it (skill point settlement, skill energy gain). The problem with a string {@code switch} is
 * that a spelling change or a new type on the data side fails silently - it falls into the {@code default}
 * branch and is swallowed, with neither compile-time protection nor a runtime error. Once it is centralised into
 * this enum, "data value to engine semantics" is defined in exactly one place, and adding a new type forces the
 * compiler to make every {@code switch} take a stand.
 *
 * <p>Note: <b>This is not the same thing as {@link SkillType}</b>, do not substitute one for the other:
 * <ul>
 *   <li>{@code SkillType} is a slot category (which slot is equipped on the character), and contains the two
 *       values {@code SUMMON_SKILL} / {@code SUMMON_TALENT} that do not exist in the data;</li>
 *   <li>this enum is the attack type in the data, and contains the two values {@code ASSIST} /
 *       {@code ELATION_DAMAGE} that {@code SkillType} does not have.</li>
 * </ul>
 * The only overlap between the two is those five values {@code Normal / BPSkill / Ultra / Maze / MazeNormal}.
 *
 * <p>Measured on the data (638 skills, {@code skills.json}):
 * {@code Normal} 122, {@code Ultra} 114, {@code BPSkill} 109, {@code MazeNormal} 94,
 * {@code Maze} 93, {@code null} 94 (talents and follow-up attacks), {@code ElationDamage} 9,
 * {@code Assist} 3.
 */
public enum SkillCategory {
    /**
     * In-battle basic attack (data {@code "Normal"}).
     */
    NORMAL("Normal"),
    /**
     * Skill (data {@code "BPSkill"}).
     */
    BPSKILL("BPSkill"),
    /**
     * Ultimate (data {@code "Ultra"}).
     */
    ULTRA("Ultra"),
    /**
     * Map basic attack (data {@code "MazeNormal"}): the hit used outside battle.
     */
    MAZE_NORMAL("MazeNormal"),
    /**
     * Technique (data {@code "Maze"}): cast actively outside battle.
     */
    MAZE("Maze"),
    /**
     * Assist skill (data {@code "Assist"}, 3 measured).
     */
    ASSIST("Assist"),
    /**
     * Elation damage skill (data {@code "ElationDamage"}, 9 measured, part of the Elation system).
     */
    ELATION_DAMAGE("ElationDamage"),
    /**
     * {@code attack_type} is empty in the data - 94 measured, all of them talents and follow-up attacks
     * (they are not an "active cast", so they have no attack type).
     */
    UNSPECIFIED(""),
    /**
     * A value this project does not yet recognise showed up in the data.
     *
     * <p>Deliberately does not throw: the data is an external artefact, and blowing up the engine over one new
     * type is a stability problem. Here we choose "degrade safely + stay observable", and {@link #isKnownValue()}
     * lets the caller decide whether to make noise.
     */
    UNKNOWN("");

    /**
     * All data values this enum recognises (including mixed-case ones like {@code "ElationDamage"}).
     *
     * <p>The single source of truth: {@link #fromString} looks it up, and {@link #isKnownValue()} looks it up too.
     *
     * <p>The keys are always normalised through {@link #normalize} - otherwise "case-insensitive" would just be an
     * empty phrase in the javadoc (that is exactly how the first version got it wrong: the keys were stored as-is
     * while lookups used lower case, so they could never be found).
     */
    private static final Map<String, SkillCategory> BY_VALUE = new HashMap<>();

    static {
        for (SkillCategory category : values()) {
            if (category.isKnownValue()) {
                BY_VALUE.put(normalize(category.value), category);
            }
        }
    }

    /**
     * Value normalisation: trim whitespace + lower case. Building the table and looking up in it must go
     * through the same function.
     */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private final String value;

    SkillCategory(String value) {
        this.value = value;
    }

    /**
     * The raw string from the data ({@code UNSPECIFIED} / {@code UNKNOWN} are the empty string).
     */
    public String value() {
        return value;
    }

    /**
     * The category a <b>rule</b> means when it names a slot ({@link SkillType}).
     *
     * <p><b>Why two vocabularies, and why this table is needed.</b> The data labels each skill with an
     * {@code attack_type} ({@code Normal} / {@code BPSkill} / {@code Ultra} / empty for talents and follow-ups) and
     * that is what rides along on a {@code Damage} instance; content names a slot the way the engine's own
     * {@code "skill"} field does ({@code COMMON} / {@code SKILL} / {@code ULTRA} / {@code TALENT}). A sentence like
     * "when casting the Skill and breaking an enemy target's weakness" compares the two, so the translation lives here, once - a second copy
     * at the call site would be free to disagree with this one.
     *
     * <p>Note: {@code TALENT} maps to {@link #UNSPECIFIED}, and that is the point rather than a gap: a talent or a
     * follow-up attack carries no attack type in the data because it is <b>not an active cast</b>, so
     * "casting the Skill... breaking a weakness" must <b>not</b> match a break caused by Himeko (姬子)'s own follow-up.
     *
     * @param slot the slot a rule named, or {@code null}
     * @return the category a damage instance from that slot carries ({@code null} in to {@code null} out)
     */
    public static SkillCategory of(SkillType slot) {
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case COMMON -> NORMAL;
            case SKILL -> BPSKILL;
            case ULTRA -> ULTRA;
            case TALENT -> UNSPECIFIED;
            // Out-of-battle slots (technique / overworld basic attack) never produce an in-battle damage instance, so a rule that names
            // one is asking about an event that cannot happen -- `TriggerTable` refuses those names rather than
            // mapping them, so reaching here is an engine fault.
            default -> null;
        };
    }

    /**
     * Whether this value is a stand-in for {@code null} - i.e. the data simply has no attack type.
     *
     * <p>Used for branches like "talents/follow-up attacks": they are a legitimate empty, not a data error.
     */
    public boolean isUnspecified() {
        return this == UNSPECIFIED;
    }

    /**
     * Whether this value is a legitimate value that really exists in the data.
     *
     * <p>{@code false} means one of two things: {@link #UNSPECIFIED} (legitimate empty) or
     * {@link #UNKNOWN} (data the engine does not recognise). Used for data validation/diagnostics.
     */
    public boolean isKnownValue() {
        return this != UNSPECIFIED && this != UNKNOWN;
    }

    /**
     * Whether this type counts as one active cast inside battle.
     *
     * <p>{@code true}: basic attack / skill / ultimate. {@code false}: map basic attack, technique
     * (both outside battle), assist skill, elation damage, talents and follow-up attacks (empty in the data).
     *
     * <p>Note: do not use this as the test for "should skill points be settled" - the skill point rule is
     * "basic attack +1 / skill -1 / everything else neutral", expressed by
     * {@link com.laosun.aluminium.models.skillpoint.SkillPointPolicy}.
     */
    public boolean isCombatAction() {
        return this == NORMAL || this == BPSKILL || this == ULTRA;
    }

    /**
     * The <b>scoped damage-boost</b> attribute this kind of cast feeds, or {@code null} when the category is
     * not an in-battle cast.
     *
     * <p>This is the mapping behind "普攻/战技/终结技造成的伤害提高 X%": the damage instance carries the
     * category of the cast that produced it, and {@code Battle.assemble} asks here which attribute to add to
     * the DMG-boost zone. It lives in this enum because this is already the single place that turns a data
     * {@code attack_type} into engine semantics - and because the {@code switch} forces a new category to
     * take a stand instead of silently falling through.
     *
     * <p>{@code null} for everything else, deliberately: a technique or map attack happens outside battle, a
     * talent/follow-up has an empty {@code attack_type} (and follow-up damage has its own
     * {@link AttributeType#FOLLOW_UP_DAMAGE_BOOST}, gated on the damage type instead), and an assist or
     * elation instance is not one of the three casts the game's text names. Returning a boost for those
     * would be exactly the silent over-application this project keeps hunting.
     *
     * @return the attribute to read off the attacker, or {@code null} for "no scoped boost"
     */
    public AttributeType damageBoost() {
        return switch (this) {
            case NORMAL -> AttributeType.BASIC_ATTACK_DAMAGE_BOOST;
            case BPSKILL -> AttributeType.SKILL_DAMAGE_BOOST;
            case ULTRA -> AttributeType.ULTIMATE_DAMAGE_BOOST;
            default -> null;
        };
    }

    /**
     * Parses a string from the data into the enum, case-insensitively and trimming whitespace.
     *
     * <p>Parsing rules:
     * <ul>
     *   <li>{@code null} or the empty string to {@link #UNSPECIFIED} (a legitimate empty in the data);</li>
     *   <li>a recognised value to the matching enum constant;</li>
     *   <li>an unrecognised value to {@link #UNKNOWN} (does not throw, see the note on that constant).</li>
     * </ul>
     *
     * @param raw the {@code attack_type} from the data
     * @return never {@code null}
     */
    public static SkillCategory fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNSPECIFIED;
        }
        SkillCategory category = BY_VALUE.get(normalize(raw));
        return category == null ? UNKNOWN : category;
    }
}
