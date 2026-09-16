package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.*;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.JSONReader;

import java.util.List;
import java.util.Map;

/**
 * Global constants and static game data loaded at startup.
 *
 * <p>All game data files under {@code resources/data/} are deserialized via
 * {@link JSONReader} when this class is first loaded. The following data sets are available:
 * <ul>
 *   <li>{@link #RELIC_MAIN_ATTRIBUTES} — main attribute value tables by star level</li>
 *   <li>{@link #RELIC_SUB_ATTRIBUTES} — sub-attribute value tables by star level</li>
 *   <li>{@link #WEAPONS} — weapon (light cone) data by ID</li>
 *   <li>{@link #CHARACTERS} — character base stats by ID</li>
 *   <li>{@link #SKILL_POINTS} — skill point (trace) tree data by character ID</li>
 * </ul>
 *
 * <p>{@link #PERCENT_TO_BASE} maps percentage-type attributes to their corresponding
 * base attributes for modifier redirection during calculation.
 */
public final class Constant {
    /**
     * Relic main attribute value tables (keyed by star level 2-5).
     * <star level> part attribute <base bonus> <double value>
     */
    public static final RelicMainAttribute RELIC_MAIN_ATTRIBUTES;
    /**
     * Relic sub-attribute value tables (keyed by star level 2-5).
     */
    public static final RelicSubAttribute RELIC_SUB_ATTRIBUTES;
    /**
     * Weapon base data indexed by weapon ID.
     */
    public static final Map<Integer, WeaponData> WEAPONS;
    /**
     * Character base data indexed by character ID.
     */
    public static final Map<Integer, CharacterData> CHARACTERS;
    /**
     * Skill point tree data indexed by character ID.
     */
    public static final Map<Integer, List<SkillPoint>> SKILL_POINTS;

    public static final Map<Integer, Map<Integer, Skill>> SKILLS;

    /**
     * Maps percentage-type attributes to their corresponding base-type attributes.
     * E.g. HEALTH_PERCENT → HEALTH means health percentage bonuses are merged
     * into the HEALTH attribute's modifier list.
     */
    public static final Map<AttributeType, AttributeType> PERCENT_TO_BASE = Map.of(
            AttributeType.HEALTH_PERCENT, AttributeType.HEALTH,
            AttributeType.ATTACK_PERCENT, AttributeType.ATTACK,
            AttributeType.DEFENCE_PERCENT, AttributeType.DEFENCE,
            AttributeType.SPEED_PERCENT, AttributeType.SPEED
    );

    /**
     * Upper bound of the vulnerability zone multiplier (易伤区系数上限).
     */
    public static final double VULNERABLE_CAP = 3.5;
    /**
     * Lower bound of the damage-reduction zone multiplier (减伤区系数下限).
     */
    public static final double REDUCTION_MIN = 0.01;
    /**
     * Lower bound of the weakness zone multiplier (虚弱区系数下限).
     */
    public static final double WEAKNESS_MIN = 0.2;
    /**
     * Lower bound of the raw resistance value (-100%).
     */
    public static final double RESIST_MIN = -1.0;
    /**
     * Upper bound of the raw resistance value (90%).
     */
    public static final double RESIST_MAX = 0.9;
    /**
     * Level-independent term of the defence zone formula.
     */
    public static final double DEFENCE_CONST = 200.0;
    /**
     * Per-level term of the defence zone formula.
     */
    public static final double DEFENCE_PER_LEVEL = 10.0;
    /**
     * Whether true damage skips every damage zone (真伤跳过乘区开关).
     */
    public static final boolean TRUE_DMG_SKIP_ZONES = true;

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        WEAPONS = JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, WeaponData>>() {
        }.getType());
        CHARACTERS = JSONReader.fromJSON("character_data.json", new TypeToken<Map<Integer, CharacterData>>() {
        }.getType());
        SKILL_POINTS = JSONReader.fromJSON("point.json", new TypeToken<Map<Integer, List<SkillPoint>>>() {
        }.getType());
        SKILLS = JSONReader.fromJSON("skills.json", new TypeToken<Map<Integer, Map<Integer, Skill>>>() {
        }.getType());
    }
}
