package com.laosun.aluminium.utils;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.WaveManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 关卡工厂（P7-5）：给一个 {@code stage_id}，组装出一个**可以直接开打**的 {@link Battle}。
 *
 * <pre>{@code
 * Battle battle = StageFactory.load(103201);
 * while (!battle.isOver()) {
 *     battle.stepForward();   // currentMove = 当前行动者
 *     battle.beforeMove();
 *     // …出手…
 *     battle.afterMove();
 *     if (battle.getWaveManager().isCurrentWaveCleared()
 *             && battle.getWaveManager().hasNextWave()) {
 *         battle.getWaveManager().nextWave();
 *         battle.processRequests();
 *     }
 * }
 * }</pre>
 *
 * <p>难度来自关卡本身：{@code StageBean} 的 {@code hardLevelGroup} + {@code level}
 * 直接喂给 {@link com.laosun.aluminium.models.EnemyFactory#create(int, int, int)}，
 * 所以"同一个怪在不同关卡里不一样强"是数据决定的，不需要调用方传系数。
 *
 * <p>⚠ <b>队伍是临时的</b>：P7-5 阶段还没有 {@code CharacterFactory}（P8-1），
 * 所以 {@link #load(int)} 用 {@link #temporaryTeam()} 造一支占位队。
 * 生命周期到 P8-5 为止 —— 那时会换成真实的 4 人队。详见 {@link #temporaryTeam()}。
 */
public final class StageFactory {
    /**
     * 占位角色的能量上限：{@code CanHit.maxEnergy} 默认 0 表示"没有能量条"，
     * 那种角色永远放不出终结技。给它一个值是为了让 demo/关卡至少能走到大招分支。
     */
    private static final double TEMPORARY_MAX_ENERGY = 120;

    private StageFactory() {
    }

    /**
     * 按关卡 id 组装一场**已经开打**的战斗：队伍就位、第 1 波已入场并排进行动条。
     *
     * <p>用固定种子的调用方请用 {@link #load(int, List, Random)}。
     *
     * @param stageId 关卡 id（见 {@code StageBean}）
     * @return 可直接 {@code stepForward()} 的战斗
     * @throws IllegalArgumentException 关卡不存在（含 {@code stage.json} 未生成的情况）
     */
    public static Battle load(int stageId) {
        return load(stageId, temporaryTeam(), new Random());
    }

    /**
     * 按关卡 id + 指定队伍/随机源组装战斗。
     *
     * @param stageId 关卡 id
     * @param team    我方队伍（不可为空）
     * @param rng     随机源；固定种子可让整场可复现
     * @return 可直接 {@code stepForward()} 的战斗
     * @throws IllegalArgumentException 关卡不存在、队伍为空
     */
    public static Battle load(int stageId, List<Character> team, Random rng) {
        StageBean stage = requireStage(stageId);
        if (team == null || team.isEmpty()) {
            throw new IllegalArgumentException("队伍不能为空（stage " + stageId + "）");
        }
        // 敌队先给空列表：怪由 WaveManager 按波次追加
        Battle battle = new Battle(team, new ArrayList<>(), rng == null ? new Random() : rng);
        WaveManager waves = new WaveManager(battle, stage);

        battle.startBattle();
        if (!waves.nextWave()) {
            throw new IllegalArgumentException("关卡 " + stageId + " 没有任何一波怪物");
        }
        battle.processRequests();          // 进怪是"排队入场"，必须结算一次才进行动条
        return battle;
    }

    /**
     * 取关卡数据。
     *
     * @param stageId 关卡 id
     * @return 关卡数据
     * @throws IllegalArgumentException 关卡不存在，或数据文件没生成
     */
    public static StageBean requireStage(int stageId) {
        StageBean stage = Constant.stages().get(stageId);
        if (stage == null) {
            throw new IllegalArgumentException(Constant.stages().isEmpty()
                    ? "关卡数据未加载（stage.json 是 generator 产出的，见 README 的 generator 一节）"
                    : "未知关卡 id: " + stageId);
        }
        return stage;
    }

    /**
     * 关卡是否存在（且数据已生成）。
     */
    public static boolean hasStage(int stageId) {
        return Constant.stages().containsKey(stageId);
    }

    /**
     * **临时**队伍：3 人占位，速度各不相同（好让行动条顺序有区分度）。
     *
     * <pre>
     *   临时角色 A  速度 100    —— 基准
     *   临时角色 B  速度 134    —— 先手
     *   临时角色 C  速度  90    —— 后手
     * </pre>
     *
     * <p>⚠ 这不是"角色"，只是能站进战斗的占位数据：没有光锥、遗器、真实技能与命途
     * （{@code fromAttributes} 给的是 {@code DefaultSkill}，命途 {@code OTHER}）。
     * P8-5 会把它换成 {@code CharacterFactory} 造的 4 人真队，本方法届时删除。
     *
     * <p>面板量级刻意取"能打完一关"的水平（HP 1 万 / 攻防 100），
     * 而不是真实角色面板 —— 真面板要等 P8-1。
     */
    public static List<Character> temporaryTeam() {
        List<Character> team = new ArrayList<>();
        team.add(temporaryCharacter("临时角色 A", 100));
        team.add(temporaryCharacter("临时角色 B", 134));
        team.add(temporaryCharacter("临时角色 C", 90));
        return team;
    }

    private static Character temporaryCharacter(String name, int speed) {
        Character character = Character.fromAttributes(name, 10_000, 100, 100, speed);
        character.setMaxEnergy(TEMPORARY_MAX_ENERGY);   // 否则永远放不出终结技
        character.setCurrentEnergy(0);
        return character;
    }
}
