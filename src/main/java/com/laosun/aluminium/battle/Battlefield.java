package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.Camp;

import java.util.ArrayList;
import java.util.List;

/**
 * Camp queries: who is on which side, who can be targeted, and the seeded random opponent.
 *
 * <p><b>Why this is separate.</b> These answers all read the two camp rosters but do not change
 * them; keeping them beside the roster fields made every query look like roster management. The
 * targeting rule - dead units are never selectable - lives here once.
 */
public final class Battlefield {

    private final Battle battle;

    /**
     * @param battle the battle whose camps are queried
     */
    public Battlefield(Battle battle) {
        this.battle = battle;
    }

    /**
     * @param self the querier
     * @return the opposing camp
     */
    public List<? extends CanHit> opponents(CanHit self) {
        if (self == null || self.getCamp() == null) {
            return List.of();
        }
        return self.getCamp() == Camp.PLAYER ? battle.enemies() : battle.allies();
    }

    /**
     * @param self the querier
     * @return its own camp
     */
    public List<? extends CanHit> side(CanHit self) {
        if (self == null || self.getCamp() == null) {
            return List.of();
        }
        return self.getCamp() == Camp.PLAYER ? battle.allies() : battle.enemies();
    }

    /**
     * @return the enemy camp's living units, in roster order
     */
    public List<CanHit> targetableEnemies() {
        List<CanHit> targets = new ArrayList<>();
        for (CanHit enemy : battle.enemies()) {
            if (!enemy.isDeath()) {
                targets.add(enemy);
            }
        }
        return targets;
    }

    /**
     * @return the enemy camp's monsters, including dead ones
     */
    public List<Enemy> enemyUnits() {
        List<Enemy> monsters = new ArrayList<>();
        for (CanHit unit : battle.enemies()) {
            if (unit instanceof Enemy enemy) {
                monsters.add(enemy);
            }
        }
        return monsters;
    }

    /**
     * @param owner the querier
     * @return one living opponent, or {@code null}
     */
    public CanHit randomOpponent(CanHit owner) {
        List<CanHit> candidates = new ArrayList<>();
        for (CanHit unit : opponents(owner)) {
            if (unit != null && !unit.isDeath()) {
                candidates.add(unit);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(battle.getRng().nextInt(candidates.size()));
    }
}
