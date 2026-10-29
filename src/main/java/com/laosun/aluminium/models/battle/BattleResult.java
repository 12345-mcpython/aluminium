package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * The battle's status and outcome judgement.
 *
 * <p><b>Why this is separate.</b> "Is it over" is one piece of state with one transition rule, and it
 * is read by the turn loop, the death sweep and every front end. Keeping the field beside the turn
 * code made each of them look responsible for deciding the outcome.
 */
public final class BattleResult {

    private final Battle battle;
    private Battle.Status status = Battle.Status.NOT_STARTED;

    /**
     * @param battle the battle whose camps are judged
     */
    public BattleResult(Battle battle) {
        this.battle = battle;
    }

    /**
     * @return the current status
     */
    public Battle.Status status() {
        return status;
    }

    /**
     * Starts the battle; called once.
     *
     * @throws IllegalStateException when it has already started
     */
    public void start() {
        if (status != Battle.Status.NOT_STARTED) {
            throw new IllegalStateException(
                    "startBattle() may be called once per battle; this one is already " + status);
        }
        status = Battle.Status.RUNNING;
    }

    /**
     * @return whether the outcome is terminal
     */
    public boolean isFinished() {
        return status == Battle.Status.WIN || status == Battle.Status.LOSE;
    }

    /**
     * Decides the outcome if the battle is running.
     *
     * @return the current status
     */
    public Battle.Status check() {
        if (status != Battle.Status.RUNNING) {
            return status;
        }
        boolean pendingWaves = battle.getWaveManager() != null && battle.getWaveManager().hasPendingWaves();
        if (battle.allies().stream().allMatch(CanHit::isDeath)) {
            status = Battle.Status.LOSE;
        } else if (battle.enemies().stream().allMatch(CanHit::isDeath) && !pendingWaves) {
            status = Battle.Status.WIN;
        }
        return status;
    }
}
