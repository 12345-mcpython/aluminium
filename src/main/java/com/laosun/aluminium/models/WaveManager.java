package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import lombok.Getter;

import java.util.List;

/**
 * Stage wave management (P7-4): spawns monsters in order, one entry of
 * {@link StageBean#monster()} (= one wave) at a time.
 *
 * <pre>{@code
 * WaveManager waves = new WaveManager(battle, Constant.stages().get(103201));
 * battle.startBattle();          // the enemy team is empty here, but because there are
 *                                // "waves still to spawn" it is not judged a win
 * waves.nextWave();              // enter wave 1
 * // …fight…
 * if (battle.isOver() && waves.hasNextWave()) {
 *     waves.nextWave();          // enter the next wave, the battle continues
 * }
 * }</pre>
 *
 * <p><b>How victory/defeat relates to waves</b> (the P7-3 × P7-4 seam, the easiest place to
 * trip over): {@code Battle.checkResult()} treats "one side is wiped out" as the battle
 * ending, whereas in wave mode "the enemy team is empty" only means **this wave has not
 * spawned yet**. So {@link Battle} asks this class
 * ({@link #hasPendingWaves()}): as long as waves remain, no victory is declared.
 *
 * <p>⚠ The data has **no** "between-wave cleanup" config item. So this class likewise does
 * not presume to clear buffs / reset the action bar — on a wave change it does only one
 * thing: spawn the monsters. If between-wave config ever appears, the extension point is
 * inside {@link #nextWave()}.
 */
public class WaveManager {
    private final Battle battle;
    private final StageBean stage;
    /**
     * Which wave has been entered already (starting from 0). {@code -1} = not even one wave
     * has been entered.
     */
    @Getter
    private int waveIndex = -1;

    /**
     * @param battle the target battle (it registers itself with
     *               {@link Battle#setWaveManager}, so the victory judgement can query it)
     * @param stage  the stage data
     */
    public WaveManager(Battle battle, StageBean stage) {
        if (battle == null) {
            throw new IllegalArgumentException("battle must not be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        this.battle = battle;
        this.stage = stage;
        battle.setWaveManager(this);
    }

    /**
     * Enter the next wave: create this wave's monsters, add them to {@code battle.enemies},
     * and **queue them for entry**.
     *
     * <p>Entry goes through {@code Battle.addRequestItems} ({@code processAddRequests} of
     * {@code processRequests()} pushes it into the action bar), so the monsters start running
     * from the **current action value** and do not go back to 0 to restart a round — which is
     * exactly how waves should behave.
     *
     * @return {@code true} = a wave was entered; {@code false} if there is no next wave
     */
    public boolean nextWave() {
        if (!hasNextWave()) {
            return false;
        }
        waveIndex++;
        spawnWave(waveIndex);
        // A new wave came in → judge again (the checkResult before spawning may have
        // decided nothing)
        battle.checkResult();
        return true;
    }

    /**
     * Whether any wave is still un-entered.
     */
    public boolean hasNextWave() {
        return waveIndex + 1 < stage.waveCount();
    }

    /**
     * Whether any wave is still **un-entered** — this is what {@link Battle} asks when
     * judging victory/defeat.
     *
     * <p>The name stresses pending: {@link #hasNextWave()} means "can still enter the next
     * wave"; the semantics are the same, but this one is for the victory judgement, and it is
     * deliberately named separately so the two cannot be confused later.
     */
    public boolean hasPendingWaves() {
        return hasNextWave();
    }

    /**
     * Total number of waves.
     */
    public int getWaveCount() {
        return stage.waveCount();
    }

    /**
     * Whether every enemy of the current wave (that is, the last wave entered) has died.
     *
     * <p>⚠ When not even one wave has been entered the enemy team is empty and this returns
     * {@code true} (the empty set is fully wiped out). Before using it as the "time to change
     * wave" test, first confirm {@link #getWaveIndex()} {@code >= 0}.
     *
     * @return whether every enemy of the current wave has died
     */
    public boolean isCurrentWaveCleared() {
        return battle.enemies.stream().allMatch(CanHit::isDeath);
    }

    /**
     * The current wave's units that are still alive — the enemy camp's, so a summon fighting
     * alongside the monsters counts as "not cleared" too (L-8).
     */
    public List<CanHit> aliveEnemies() {
        return battle.enemies.stream().filter(e -> !e.isDeath()).toList();
    }

    private void spawnWave(int index) {
        for (int monsterId : stage.monsterIds(index)) {
            Enemy enemy = EnemyFactory.create(monsterId, stage.level(), stage.hardLevelGroup());
            battle.enemies.add(enemy);
            battle.addRequestItems.add(enemy);           // processRequests pushes it into the action bar
        }
    }
}
