package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.StageBean;

import java.util.List;

/**
 * 关卡波次管理（P7-4）：按 {@link StageBean#monster()} 的每一项（= 一波）依次进怪。
 *
 * <pre>{@code
 * WaveManager waves = new WaveManager(battle, Constant.stages().get(103201));
 * battle.startBattle();          // 此时敌队是空的，但因为有"待生成的波"，不会被判胜
 * waves.nextWave();              // 进第 1 波
 * // …打…
 * if (battle.isOver() && waves.hasNextWave()) {
 *     waves.nextWave();          // 进下一波，战斗继续
 * }
 * }</pre>
 *
 * <p><b>胜负与波次的关系</b>（P7-3 × P7-4 的接缝，最容易踩的一处）：
 * {@code Battle.checkResult()} 把"一方全灭"当作战斗结束，而"敌队是空的"在波次模式里
 * 只是**这一波还没进**。所以 {@link Battle} 会问到本类
 * （{@link #hasPendingWaves()}）：还有波没进就不再判胜。
 *
 * <p>⚠ 数据里**没有**"波间清理"的配置项。所以本类也不擅自清 buff / 重置行动条 ——
 * 换波时只做"进怪"这一件事。将来若拿到波间配置，扩展点就在 {@link #nextWave()} 里。
 */
public class WaveManager {
    private final Battle battle;
    private final StageBean stage;
    /**
     * 已经进到第几波（从 0 开始）。{@code -1} = 一波都还没进。
     */
    private int waveIndex = -1;

    /**
     * @param battle 目标战斗（会把自己登记到 {@link Battle#setWaveManager}，供胜负判定查询）
     * @param stage  关卡数据
     */
    public WaveManager(Battle battle, StageBean stage) {
        if (battle == null) {
            throw new IllegalArgumentException("battle 不能为 null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("stage 不能为 null");
        }
        this.battle = battle;
        this.stage = stage;
        battle.setWaveManager(this);
    }

    /**
     * 进下一波：把这波的怪造出来、加进 {@code battle.enemies}，并**排队入场**。
     *
     * <p>入场走 {@code Battle.addRequestItems}（{@code processRequests()} 的
     * {@code processAddRequests} 会把它推进行动条），所以怪是从**当前行动值**起跑的，
     * 不会回到 0 重开一轮 —— 这正是波次该有的表现。
     *
     * @return {@code true} = 进了一波；没有下一波则 {@code false}
     */
    public boolean nextWave() {
        if (!hasNextWave()) {
            return false;
        }
        waveIndex++;
        spawnWave(waveIndex);
        // 新一波进来了 → 重新判定（进怪前那次 checkResult 可能什么都没定）
        battle.checkResult();
        return true;
    }

    /**
     * 是否还有没进的波。
     */
    public boolean hasNextWave() {
        return waveIndex + 1 < stage.waveCount();
    }

    /**
     * 是否还有**没进**的波 —— {@link Battle} 判胜负时问的就是这个。
     *
     * <p>名字里强调 pending：{@link #hasNextWave()} 是"还能进下一波"，
     * 语义相同但这里是给胜负判定看的，故意分开命名以免将来两边改混。
     */
    public boolean hasPendingWaves() {
        return hasNextWave();
    }

    /**
     * 已经进到第几波（从 0 开始）。一波都没进时返回 {@code -1}。
     */
    public int getWaveIndex() {
        return waveIndex;
    }

    /**
     * 总波数。
     */
    public int getWaveCount() {
        return stage.waveCount();
    }

    /**
     * 当前波（也就是最后一波已进的那波）的敌人是否已全部阵亡。
     *
     * <p>⚠ 一波都没进时敌队是空的，这里返回 {@code true}（空集全灭）。
     * 拿它当"该换波了"的判断前请先确认 {@link #getWaveIndex()} {@code >= 0}。
     *
     * @return 当前波的敌人是否全部阵亡
     */
    public boolean isCurrentWaveCleared() {
        return battle.enemies.stream().allMatch(CanHit::isDeath);
    }

    /**
     * 当前这一波活着的敌人。
     */
    public List<Enemy> aliveEnemies() {
        return battle.enemies.stream().filter(e -> !e.isDeath()).toList();
    }

    private void spawnWave(int index) {
        for (int monsterId : stage.monsterIds(index)) {
            Enemy enemy = EnemyFactory.create(monsterId, stage.level(), stage.hardLevelGroup());
            battle.enemies.add(enemy);
            battle.addRequestItems.add(enemy);           // 由 processRequests 推进行动条
        }
    }
}
