package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import com.laosun.aluminium.models.Moveable;

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
        queue.initialize();
        for (Moveable moveable : queue.getQueue()) {
            moveable.on_battle_start(this, moveable);
        }
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
