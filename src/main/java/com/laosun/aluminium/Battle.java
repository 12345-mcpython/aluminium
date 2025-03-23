package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import com.laosun.aluminium.models.Moveable;

/**
 * The main class of battle
 * @author laosun
 * @since core version 1.0.0
 * */
@Getter
@Setter
public class Battle {
    private Queue queue;

    public Battle(Queue queue) {
        this.queue = queue;
    }

    public void addMoveable(Moveable moveable) {
        queue.add(List.of(moveable));
    }

    public void removeMoveable(Moveable moveable) {
        queue.getQueue().remove(moveable);
    }

    public void startBattle() {
        for (Moveable moveable : queue.getCharacters()) {
            moveable.onBattleStart(this, moveable);
        }
        for (Moveable moveable : queue.getEnemies()) {
            moveable.onBattleStart(this, moveable);
        }
        queue.calcTime();
    }

    public Moveable getMoveableByName(String name) {
        for (Moveable moveable : queue.getQueue()) {
            if (((CanHit) moveable).getName().equals(name)) {
                return moveable;
            }
        }
        return null;
    }
}
