package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A summoned entity that participates in combat.
 *
 * <p>Summons are typically aligned with {@link Camp#PLAYER} and may inherit
 * stats from their summoner.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Summon extends CanHit {
    /**
     * Constructs a summoned entity.
     *
     * @param name       display name
     * @param camp       faction alignment (typically {@link Camp#PLAYER})
     * @param attributes pre-computed attribute array
     */
    public Summon(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }
}
