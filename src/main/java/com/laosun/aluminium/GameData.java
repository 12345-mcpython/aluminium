package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.*;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.data.WeaponTriggerTables;
import com.laosun.aluminium.trigger.RelicTriggerTableProvider;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.trigger.TriggerTableProvider;
import com.laosun.aluminium.trigger.WeaponTriggerTableProvider;
import com.laosun.aluminium.utils.JSONReader;
import com.laosun.aluminium.utils.MapUtils;

import java.util.*;

/**
 * One loaded copy of the generated game tables.
 *
 * <p><b>Why this exists next to {@link Constant}.</b> The tables used to be loaded inside
 * {@code Constant}'s static initializer, which made "the data" a JVM-wide singleton: a test could not
 * load a second copy, and a process could not choose a different data pack. This class owns the load
 * (and the freezing) as an object; {@link Constant} keeps its public static fields as a facade over
 * {@link #DEFAULT}, so every existing call site is unchanged while the seam exists.
 *
 * <p>The fields are private and the maps/lists are frozen: callers can read the data but cannot empty
 * a shared table. A caller that wants an independent load calls {@link #load()}.
 */
public final class GameData {

    /**
     * The process-wide default, loaded once when {@link Constant} is first touched.
     */
    public static final GameData DEFAULT = new GameData();

    private final Map<Integer, Double> elationBasicLevelDamage;
    private final RelicMainAttribute relicMainAttributes;
    private final RelicSubAttribute relicSubAttributes;
    private final Map<Integer, RelicSet> relicSets;
    private final Map<Integer, WeaponData> weapons;
    private final Map<Integer, CharacterData> characters;
    private final Map<Integer, List<SkillTraceData>> skillTraces;
    private final Map<Integer, Map<Integer, Skill>> skills;
    private final Map<Integer, MonsterTemplate> monsterTemplates;
    private final Map<Integer, Map<Integer, HardLevelGroup>> hardLevelGroups;
    private final Map<Integer, MonsterConfig> monsterConfigs;
    private final Map<Integer, Double> breakingRate;
    private final Map<Integer, EnemySkillData> enemySkills;
    private final Map<String, com.laosun.aluminium.beans.PredicateSpec> predicates;
    private final TriggerTableProvider triggerTables;
    private final RelicTriggerTableProvider relicTriggerTables;
    private final WeaponTriggerTableProvider weaponTriggerTables;

    /**
     * Loads every table from the classpath.
     *
     * @throws IllegalStateException when a required data file is missing or unusable
     */
    public GameData() {
        elationBasicLevelDamage = frozen(JSONReader.fromJSON("elation_basic_level_damage.json",
                new TypeToken<Map<Integer, Double>>() {
                }.getType()));
        relicMainAttributes = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        relicSubAttributes = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        relicSets = RelicSets.table();
        weapons = frozen(JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, WeaponData>>() {
        }.getType()));
        characters = frozen(JSONReader.fromJSON("character_data.json",
                new TypeToken<Map<Integer, CharacterData>>() {
                }.getType()));
        skillTraces = frozen(JSONReader.fromJSON("skill_traces.json",
                new TypeToken<Map<Integer, List<SkillTraceData>>>() {
                }.getType()));
        skills = frozen(JSONReader.fromJSON("skills.json",
                new TypeToken<Map<Integer, Map<Integer, Skill>>>() {
                }.getType()));
        monsterTemplates = frozen(JSONReader.fromJSON("monster_template_config.json",
                new TypeToken<Map<Integer, MonsterTemplate>>() {
                }.getType()));
        hardLevelGroups = frozen(JSONReader.fromJSON("hard_level_group.json",
                new TypeToken<Map<Integer, Map<Integer, HardLevelGroup>>>() {
                }.getType()));
        monsterConfigs = normalizeMonsterConfigs(
                JSONReader.fromJSON("monster_config.json", new TypeToken<Map<Integer, MonsterConfig>>() {
                }.getType()),
                JSONReader.fromJSON("monster_attack_modify_ratio.json",
                        new TypeToken<Map<Integer, Double>>() {
                        }.getType()));
        breakingRate = frozen(JSONReader.fromJSON("breaking_rate.json",
                new TypeToken<Map<Integer, Double>>() {
                }.getType()));
        EnemySkillsFile enemySkillsFile = JSONReader.fromJSON("enemy_skills.json", EnemySkillsFile.class);
        enemySkills = Map.copyOf(enemySkillsFile.skills());
        // The named predicates: one registry file, so a guard is written once and referenced by name. Shipped (not
        // generated): hand-written data, the same shape a data pack would add, and `{}` is a legal empty registry.
        predicates = frozen(JSONReader.fromJSON("predicates.json",
                new TypeToken<Map<String, com.laosun.aluminium.beans.PredicateSpec>>() {
                }.getType()));
        triggerTables = TriggerTables::of;
        relicTriggerTables = (setId, wornPieces) -> RelicTriggerTables.of(setId).at(wornPieces);
        weaponTriggerTables = (weaponId, rank) -> WeaponTriggerTables.has(weaponId)
                ? WeaponTriggerTables.of(weaponId, rank)
                : TriggerTable.EMPTY;
    }

    /**
     * A copy of {@link #DEFAULT} with one table replaced, for a caller that wants a different monster
     * pack without re-loading the other twelve tables.
     *
     * @param monsterConfigs the replacement monster rows
     * @return the copy
     * @throws NullPointerException when {@code monsterConfigs} is {@code null}
     */
    public static GameData withMonsterConfigs(Map<Integer, MonsterConfig> monsterConfigs) {
        return new GameData(DEFAULT, null, java.util.Objects.requireNonNull(monsterConfigs, "monsterConfigs"),
                null, null, null, null, null);
    }

    /**
     * A copy of {@link #DEFAULT} with the character table replaced.
     *
     * @param characters the replacement character rows
     * @return the copy
     * @throws NullPointerException when {@code characters} is {@code null}
     */
    public static GameData withCharacters(Map<Integer, CharacterData> characters) {
        return new GameData(DEFAULT, java.util.Objects.requireNonNull(characters, "characters"), null, null,
                null, null, null, null);
    }

    /**
     * A copy of {@link #DEFAULT} with the trace table replaced.
     *
     * @param skillTraces the replacement trace rows
     * @return the copy
     * @throws NullPointerException when {@code skillTraces} is {@code null}
     */
    public static GameData withSkillTraces(Map<Integer, List<SkillTraceData>> skillTraces) {
        return new GameData(DEFAULT, null, null,
                java.util.Objects.requireNonNull(skillTraces, "skillTraces"), null, null, null, null);
    }

    /**
     * A copy of {@link #DEFAULT} with the skill table replaced.
     *
     * @param skills the replacement skill rows
     * @return the copy
     * @throws NullPointerException when {@code skills} is {@code null}
     */
    public static GameData withSkills(Map<Integer, Map<Integer, Skill>> skills) {
        return new GameData(DEFAULT, null, null, null,
                java.util.Objects.requireNonNull(skills, "skills"), null, null, null);
    }

    /**
     * A copy of {@link #DEFAULT} with the trigger-table provider replaced.
     *
     * @param triggerTables the replacement provider
     * @return the copy
     * @throws NullPointerException when {@code triggerTables} is {@code null}
     */
    public static GameData withTriggerTables(TriggerTableProvider triggerTables) {
        return new GameData(DEFAULT, null, null, null, null,
                java.util.Objects.requireNonNull(triggerTables, "triggerTables"), null, null);
    }

    /**
     * A copy of {@link #DEFAULT} with the relic-rule provider replaced.
     *
     * @param relicTriggerTables the replacement provider
     * @return the copy
     * @throws NullPointerException when {@code relicTriggerTables} is {@code null}
     */
    public static GameData withRelicTriggerTables(RelicTriggerTableProvider relicTriggerTables) {
        return new GameData(DEFAULT, null, null, null, null, null,
                java.util.Objects.requireNonNull(relicTriggerTables, "relicTriggerTables"), null);
    }

    /**
     * A copy of {@link #DEFAULT} with the light-cone-rule provider replaced.
     *
     * @param weaponTriggerTables the replacement provider
     * @return the copy
     * @throws NullPointerException when {@code weaponTriggerTables} is {@code null}
     */
    public static GameData withWeaponTriggerTables(WeaponTriggerTableProvider weaponTriggerTables) {
        return new GameData(DEFAULT, null, null, null, null, null, null,
                java.util.Objects.requireNonNull(weaponTriggerTables, "weaponTriggerTables"));
    }

    /**
     * @param base                the tables to copy
     * @param characters          the replacement character rows, or {@code null} to keep {@code base}'s
     * @param monsterConfigs      the replacement monster rows, or {@code null} to keep {@code base}'s
     * @param skillTraces         the replacement trace rows, or {@code null} to keep {@code base}'s
     * @param skills              the replacement skill rows, or {@code null} to keep {@code base}'s
     * @param triggerTables       the replacement trigger provider, or {@code null} to keep {@code base}'s
     * @param relicTriggerTables  the replacement relic provider, or {@code null} to keep {@code base}'s
     * @param weaponTriggerTables the replacement light-cone provider, or {@code null} to keep {@code base}'s
     */
    private GameData(GameData base, Map<Integer, CharacterData> characters,
                     Map<Integer, MonsterConfig> monsterConfigs,
                     Map<Integer, List<SkillTraceData>> skillTraces,
                     Map<Integer, Map<Integer, Skill>> skills,
                     TriggerTableProvider triggerTables,
                     RelicTriggerTableProvider relicTriggerTables,
                     WeaponTriggerTableProvider weaponTriggerTables) {
        elationBasicLevelDamage = base.elationBasicLevelDamage;
        relicMainAttributes = base.relicMainAttributes;
        relicSubAttributes = base.relicSubAttributes;
        relicSets = base.relicSets;
        weapons = base.weapons;
        this.characters = characters == null ? base.characters : frozen(characters);
        this.skillTraces = skillTraces == null ? base.skillTraces : frozen(skillTraces);
        this.skills = skills == null ? base.skills : frozen(skills);
        monsterTemplates = base.monsterTemplates;
        hardLevelGroups = base.hardLevelGroups;
        this.monsterConfigs = monsterConfigs == null ? base.monsterConfigs : frozen(monsterConfigs);
        breakingRate = base.breakingRate;
        enemySkills = base.enemySkills;
        predicates = base.predicates;
        this.triggerTables = triggerTables == null ? base.triggerTables : triggerTables;
        this.relicTriggerTables = relicTriggerTables == null ? base.relicTriggerTables : relicTriggerTables;
        this.weaponTriggerTables = weaponTriggerTables == null
                ? base.weaponTriggerTables : weaponTriggerTables;
    }

    /**
     * Loads an independent copy.
     *
     * @return the loaded tables
     */
    public static GameData load() {
        return new GameData();
    }

    /**
     * @return the level-indexed Elation base values
     */
    public Map<Integer, Double> elationBasicLevelDamage() {
        return elationBasicLevelDamage;
    }

    /**
     * @return the relic main-affix tables
     */
    public RelicMainAttribute relicMainAttributes() {
        return relicMainAttributes;
    }

    /**
     * @return the relic sub-affix tables
     */
    public RelicSubAttribute relicSubAttributes() {
        return relicSubAttributes;
    }

    /**
     * @return the relic-set table
     */
    public Map<Integer, RelicSet> relicSets() {
        return relicSets;
    }

    /**
     * @return the light-cone data by id
     */
    public Map<Integer, WeaponData> weapons() {
        return weapons;
    }

    /**
     * @return the character data by id
     */
    public Map<Integer, CharacterData> characters() {
        return characters;
    }

    /**
     * @return the named predicates by name (the {@code predicates.json} registry)
     */
    public Map<String, com.laosun.aluminium.beans.PredicateSpec> predicates() {
        return predicates;
    }

    /**
     * @return the trace trees by character id
     */
    public Map<Integer, List<SkillTraceData>> skillTraces() {
        return skillTraces;
    }

    /**
     * @return the skill rows by character id and slot
     */
    public Map<Integer, Map<Integer, Skill>> skills() {
        return skills;
    }

    /**
     * @return the monster templates by template id
     */
    public Map<Integer, MonsterTemplate> monsterTemplates() {
        return monsterTemplates;
    }

    /**
     * @return the level groups by group and level
     */
    public Map<Integer, Map<Integer, HardLevelGroup>> hardLevelGroups() {
        return hardLevelGroups;
    }

    /**
     * @return the normalized monster instances by monster id
     */
    public Map<Integer, MonsterConfig> monsterConfigs() {
        return monsterConfigs;
    }

    /**
     * @return the break base rates by level
     */
    public Map<Integer, Double> breakingRate() {
        return breakingRate;
    }

    /**
     * @return the enemy skills by monster id
     */
    public Map<Integer, EnemySkillData> enemySkills() {
        return enemySkills;
    }

    /**
     * @return the character trigger-table provider
     */
    public TriggerTableProvider triggerTables() {
        return triggerTables;
    }

    /**
     * @return the relic-rule provider
     */
    public RelicTriggerTableProvider relicTriggerTables() {
        return relicTriggerTables;
    }

    /**
     * @return the light-cone-rule provider
     */
    public WeaponTriggerTableProvider weaponTriggerTables() {
        return weaponTriggerTables;
    }

    /**
     * The top-level structure of {@code enemy_skills.json} (only to skip {@code _comment}).
     */
    private record EnemySkillsFile(Map<Integer, EnemySkillData> skills) {
    }

    /**
     * Fill in the ratios missing from the instance data so that downstream code (EnemyScaler) always gets a
     * definite value:
     * <ul>
     *   <li><b>Attack modifier</b>: this data set does not export tbgd's {@code AttackModifyRatio}
     *   (444 of the 2649 monsters are not 1), so it is merged from the patch file
     *   {@code monster_attack_modify_ratio.json}; anything not in the table is 1.0.</li>
     *   <li>Other missing ratios are 1.0 (game semantics = no modification).</li>
     *   <li>{@code stance_weak} missing (102 monster entries do not have this item) to empty list;
     *   {@code damage_resistance} to empty map.</li>
     * </ul>
     *
     * @param raw          the instance rows
     * @param attackRatios the patch table
     * @return the normalized rows, in file order
     */
    private static Map<Integer, MonsterConfig> normalizeMonsterConfigs(Map<Integer, MonsterConfig> raw,
                                                                       Map<Integer, Double> attackRatios) {
        Map<Integer, Double> patches = attackRatios == null ? Map.of() : attackRatios;
        Map<Integer, MonsterConfig> normalized = new LinkedHashMap<>();
        raw.forEach((id, config) -> normalized.put(id, new MonsterConfig(
                config.name(),
                config.templateId(),
                config.eliteGroup(),
                config.hardLevelGroup(),
                config.stanceWeak() == null ? List.of() : List.copyOf(config.stanceWeak()),
                orOne(config.hpRatio()),
                patches.getOrDefault(id, orOne(config.attackRatio())),
                orOne(config.defenceRatio()),
                orOne(config.speedRatio()),
                orOne(config.stanceRatio()),
                config.damageResistance() == null ? Map.of() : MapUtils.orderedMap(config.damageResistance()),
                config.debuffResistance() == null ? Map.of() : MapUtils.orderedMap(config.debuffResistance()),
                filterPositive(config.summonIds()))));
        return MapUtils.orderedMap(normalized);
    }

    /**
     * The positive entries of a summon roster, in order; missing field = empty roster.
     *
     * @param ids the raw ids
     * @return the positive ids, in order
     */
    private static List<Integer> filterPositive(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> kept = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            if (id != null && id > 0) {
                kept.add(id);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * A missing modifier ratio is 1.0 (no modification).
     *
     * @param value the raw value
     * @return the value, or 1.0
     */
    private static double orOne(Double value) {
        return value == null ? 1.0 : value;
    }

    /**
     * Freezes a loaded table so that no caller can mutate shared engine data.
     *
     * <p>Maps and lists are copied and wrapped; the beans inside are returned unchanged, because they
     * <i>are</i> the data and nothing mutates them. Order is preserved: {@link LinkedHashMap} and
     * {@link ArrayList} rather than the unspecified iteration order of {@code Map.copyOf} /
     * {@code List.copyOf}.
     *
     * @param table a map, a list, or anything else
     * @param <T>   the declared type of the table
     * @return an unmodifiable deep copy of the containers, or the value itself when it is not a container
     */
    @SuppressWarnings("unchecked")
    private static <T> T frozen(T table) {
        if (table instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(key, frozen(value)));
            return (T) Collections.unmodifiableMap(copy);
        }
        if (table instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(frozen(item)));
            return (T) Collections.unmodifiableList(copy);
        }
        return table;
    }
}
