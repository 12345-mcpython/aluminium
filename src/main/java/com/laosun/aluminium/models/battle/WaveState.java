package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.entity.CanHit;

import java.util.ArrayList;
import java.util.List;

/**
 * The wave sequence and the monsters that entered with the current wave.
 *
 * <p><b>Why this is separate.</b> The sequence is a counter that per-wave limits compare against;
 * the monster list answers "who arrived just now". Both are wave state, not battle state, and their
 * clear/record order is owned by the wave manager.
 */
public final class WaveState {

    private int sequence;
    private final List<CanHit> monsters = new ArrayList<>();

    /**
     * Starts a wave.
     */
    public void begin() {
        sequence++;
    }

    /**
     * @return the current wave's number
     */
    public int sequence() {
        return sequence;
    }

    /**
     * Forgets the previous wave's monsters.
     */
    public void forgetMonsters() {
        monsters.clear();
    }

    /**
     * @param enemy an enemy that just entered with a wave
     */
    public void noteMonster(CanHit enemy) {
        if (enemy != null && !monsters.contains(enemy)) {
            monsters.add(enemy);
        }
    }

    /**
     * @return the enemies that entered with the current wave
     */
    public List<CanHit> monsters() {
        return List.copyOf(monsters);
    }
}
