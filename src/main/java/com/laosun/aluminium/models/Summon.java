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
 *
 * <p>🚧 <b>Skeleton, not wired up yet</b> (P9-4 memosprites/summons): the whole project has **nowhere that calls
 * {@code new Summon(...)}**, and `SkillEffectType.SUMMON` (8 entries in the data) is not dispatched either.
 * The mechanics related to "memosprites" (their own action bar, a stat snapshot, joint attacks) are not done yet.
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
