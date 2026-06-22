package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An enemy combatant in the action queue.
 *
 * <p>Enemies are typically aligned with {@link Camp#ENEMY} and may have
 * their attributes scaled by difficulty-level multipliers.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {
    /**
     * Constructs an enemy entity.
     *
     * @param name       display name
     * @param camp       faction alignment (typically {@link Camp#ENEMY})
     * @param attributes pre-computed attribute array
     */
    public Enemy(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }
}
