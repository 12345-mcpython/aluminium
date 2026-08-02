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
    /** <star level> part attribute <base bonus> <double value> */
    /**
     * Relic main attribute value tables (keyed by star level 2-5).
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
     * Enemy level-group scaling ratios keyed by difficulty then level
     * (hard_level_group.json).
     */
    public static final Map<Integer, Map<Integer, HardLevelGroup>> HARD_LEVEL_GROUPS;

    /**
     * Break (击破) base damage values keyed by level (breaking_rate.json).
     */
    public static final Map<Integer, Double> BREAKING_RATE;

    /**
     * 欢愉 基础值 by level (elation_basic_level_damage.json, HSR.md §3.4).
     */
    public static final Map<Integer, Double> ELATION_BASE_DAMAGE;

    /**
     * Character 星魂 (eidolon) ranks keyed by character ID then rank
     * (ranks.json).
     */
    public static final Map<Integer, Map<Integer, Eidolon>> EIDOLONS;

    /**
     * Relic set bonuses keyed by set ID (relic_sets.json).
     */
    public static final Map<Integer, RelicSet> RELIC_SETS;

    /**
     * 忆灵 skills keyed by servant ID then skill index (servant_skills.json).
     */
    public static final Map<Integer, Map<Integer, Skill>> SERVANT_SKILLS;

    /**
     * 忆灵 definitions keyed by servant ID (servant_config.json).
     */
    public static final Map<Integer, ServantConfig> SERVANT_CONFIGS;

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
        HARD_LEVEL_GROUPS = JSONReader.fromJSON("hard_level_group.json",
                new TypeToken<Map<Integer, Map<Integer, HardLevelGroup>>>() {
                }.getType());
        BREAKING_RATE = JSONReader.fromJSON("breaking_rate.json", new TypeToken<Map<Integer, Double>>() {
        }.getType());
        ELATION_BASE_DAMAGE = JSONReader.fromJSON("elation_basic_level_damage.json", new TypeToken<Map<Integer, Double>>() {
        }.getType());
        EIDOLONS = JSONReader.fromJSON("ranks.json", new TypeToken<Map<Integer, Map<Integer, Eidolon>>>() {
        }.getType());
        RELIC_SETS = JSONReader.fromJSON("relic_sets.json", new TypeToken<Map<Integer, RelicSet>>() {
        }.getType());
        SERVANT_SKILLS = JSONReader.fromJSON("servant_skills.json",
                new TypeToken<Map<Integer, Map<Integer, Skill>>>() {
                }.getType());
        SERVANT_CONFIGS = JSONReader.fromJSON("servant_config.json", new TypeToken<Map<Integer, ServantConfig>>() {
        }.getType());
    }

    /**
     * Returns the level-group ratio for the given level, defaulting to
     * difficulty 1. Falls back to the nearest available level.
     */
    public static HardLevelGroup levelGroupRatio(int level) {
        Map<Integer, HardLevelGroup> levels = HARD_LEVEL_GROUPS.get(1);
        if (levels == null) {
            throw new IllegalStateException("hard_level_group.json: difficulty 1 missing");
        }
        HardLevelGroup ratio = levels.get(level);
        if (ratio != null) {
            return ratio;
        }
        Integer best = null;
        for (Integer l : levels.keySet()) {
            if (best == null || Math.abs(l - level) < Math.abs(best - level)) {
                best = l;
            }
        }
        return levels.get(best);
    }

    /**
     * Returns the base break damage value for the given level
     * (breaking_rate.json, HSR.md §3.2).
     */
    public static double breakingRate(int level) {
        Double rate = BREAKING_RATE.get(level);
        if (rate != null) {
            return rate;
        }
        Integer best = null;
        for (Integer l : BREAKING_RATE.keySet()) {
            if (best == null || Math.abs(l - level) < Math.abs(best - level)) {
                best = l;
            }
        }
        return best != null ? BREAKING_RATE.get(best) : 0;
    }

    /**
     * Returns the 欢愉 基础值 for the given level
     * (elation_basic_level_damage.json, HSR.md §3.4).
     */
    public static double elationBaseDamage(int level) {
        Double rate = ELATION_BASE_DAMAGE.get(level);
        if (rate != null) {
            return rate;
        }
        Integer best = null;
        for (Integer l : ELATION_BASE_DAMAGE.keySet()) {
            if (best == null || Math.abs(l - level) < Math.abs(best - level)) {
                best = l;
            }
        }
        return best != null ? ELATION_BASE_DAMAGE.get(best) : 0;
    }
}
