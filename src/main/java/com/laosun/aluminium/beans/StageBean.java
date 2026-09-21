package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * 关卡数据（{@code stage.json}，P7-4）：{@code stage_id → 关卡}。
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
 * <p><b>怎么读</b>：
 * <ul>
 *   <li>{@link #monster()} 的**每一项是一波**（{@code List} 的元素 = 波），所以
 *       {@code monster().size()} 就是波数；</li>
 *   <li>每一波是 {@code {"Monster0": id, "Monster1": id, …}} —— 用 {@code Map}
 *       接住（键名 {@code MonsterN} 只是位置序号，不含语义）；
 *       <b>顺序必须是 {@code Monster0, Monster1, …}</b>，所以反序列化后要取
 *       {@link java.util.LinkedHashMap} 的**插入顺序**，不能当无序集合用；</li>
 *   <li>怪物的等级由 {@link #level()} + {@link #hardLevelGroup()} 决定，
 *       两者一起喂给 {@code EnemyFactory.create(id, level, hardLevelGroup)}。</li>
 * </ul>
 *
 * <p>⚠ 同一只怪可以在同一波里出现多次（{@code Monster0} 与 {@code Monster2} 同 id），
 * 这是**多个独立实例**，不是同一只。
 *
 * <p>⚠ {@code hardLevelGroup} 必须带 {@code @SerializedName("hard_level_group")}：
 * JSON 键是下划线风格、Java 是驼峰，Gson 不会自动换算。少了它会静默拿到 **0**，
 * 直到 {@code EnemyFactory} 报 "No hard level group 0 at level …" 才暴露。
 */
public record StageBean(
        String type,
        @SerializedName("hard_level_group") int hardLevelGroup,
        int level,
        List<Map<String, Integer>> monster) {

    /**
     * 波数（{@code monster} 元素个数）。数据缺失时为 0。
     */
    public int waveCount() {
        return monster == null ? 0 : monster.size();
    }

    /**
     * 第 {@code index} 波的怪物 id 列表，按 {@code Monster0, Monster1, …} 的顺序。
     *
     * @param index 波序号（从 0 开始）
     * @return 该波的怪物 id；越界或数据缺失返回空列表
     */
    public List<Integer> monsterIds(int index) {
        if (monster == null || index < 0 || index >= monster.size()) {
            return List.of();
        }
        Map<String, Integer> wave = monster.get(index);
        return wave == null ? List.of() : List.copyOf(wave.values());
    }
}
