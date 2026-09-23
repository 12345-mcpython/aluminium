package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Stage data ({@code stage.json}, P7-4): {@code stage_id → stage}.
 *
 * <pre>{@code
 * "103201": {
 *     "type": "Mainline",
 *     "hard_level_group": 1,
 *     "level": 29,
 *     "monster": [ { "Monster0": 1022020, "Monster1": 1023010, "Monster2": 1022020 } ]
 * }
 * }</pre>
 *
 * <p><b>How to read it</b>:
 * <ul>
 *   <li>**Each entry** of {@link #monster()} is one wave (the elements of the {@code List} = waves), so
 *       {@code monster().size()} is the number of waves;</li>
 *   <li>each wave is {@code {"Monster0": id, "Monster1": id, …}} — caught with a {@code Map}
 *       (the key names {@code MonsterN} are just positional indices and carry no semantics);
 *       <b>the order must be {@code Monster0, Monster1, …}</b>, so after deserialisation you have to take the
 *       **insertion order** of the {@link java.util.LinkedHashMap}, it must not be used as an unordered
 *       collection;</li>
 *   <li>a monster's level is decided by {@link #level()} + {@link #hardLevelGroup()}, and the two are fed
 *       together into {@code EnemyFactory.create(id, level, hardLevelGroup)}.</li>
 * </ul>
 *
 * <p>⚠ The same monster may appear several times in one wave ({@code Monster0} and {@code Monster2} with the same
 * id) — those are **several independent instances**, not the same one.
 *
 * <p>⚠ {@code hardLevelGroup} must carry {@code @SerializedName("hard_level_group")}:
 * the JSON key is underscore style while Java is camel case, and Gson does not convert automatically. Without it
 * you silently get **0**, and it only shows up when {@code EnemyFactory} reports
 * "No hard level group 0 at level …".
 */
public record StageBean(
        String type,
        @SerializedName("hard_level_group") int hardLevelGroup,
        int level,
        List<Map<String, Integer>> monster) {

    /**
     * The number of waves (the element count of {@code monster}). Zero when the data is missing.
     */
    public int waveCount() {
        return monster == null ? 0 : monster.size();
    }

    /**
     * The list of monster ids in wave {@code index}, in the order {@code Monster0, Monster1, …}.
     *
     * @param index the wave index (starting from 0)
     * @return the monster ids of that wave; out of range or missing data returns an empty list
     */
    public List<Integer> monsterIds(int index) {
        if (monster == null || index < 0 || index >= monster.size()) {
            return List.of();
        }
        Map<String, Integer> wave = monster.get(index);
        return wave == null ? List.of() : List.copyOf(wave.values());
    }
}
