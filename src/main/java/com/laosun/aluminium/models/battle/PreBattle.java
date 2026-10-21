package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.enemy.Enemy;

import java.util.ArrayList;
import java.util.List;

/**
 * What the caller declared before the battle started: which units used a technique, who holds the
 * technique archetype mark, and which map basic attacks landed.
 *
 * <p><b>Why this is a separate object.</b> These facts happen on the map, before a {@code Battle}
 * exists, so the engine cannot observe them - it has to be told. They are also consumed once, during
 * {@code startBattle}, and then never change. Keeping them next to the battle's live turn state made
 * "what was declared" and "what is happening" look alike; this object holds only the declarations.
 */
public final class PreBattle {

    private final List<CanHit> techniqueUsers = new ArrayList<>();
    private final List<MapAttack> mapAttacks = new ArrayList<>();
    private CanHit archetypeHolder;

    /**
     * Records that this unit used its technique before the battle.
     *
     * @param unit the unit
     */
    public void markTechniqueUsed(CanHit unit) {
        if (unit != null && !techniqueUsers.contains(unit)) {
            techniqueUsers.add(unit);
        }
    }

    /**
     * @return the units that used a technique, in declaration order
     */
    public List<CanHit> techniqueUsers() {
        return List.copyOf(techniqueUsers);
    }

    /**
     * Records the unit that 【美学底本】 sat on when the battle began.
     *
     * @param unit the holder, or {@code null} for none
     */
    public void markArchetypeHolder(CanHit unit) {
        archetypeHolder = unit;
    }

    /**
     * @return the technique archetype holder, or {@code null}
     */
    public CanHit archetypeHolder() {
        return archetypeHolder;
    }

    /**
     * Declares that this unit used its map basic attack on an enemy before the battle.
     *
     * @param attacker the character whose map attack landed
     * @param target   the enemy it hit
     */
    public void declareMapAttack(CanHit attacker, CanHit target) {
        if (attacker == null || target == null) {
            throw new IllegalArgumentException(
                    "declareMapAttack needs both the unit that swung and the enemy it hit: a half-declared hit has "
                            + "no toughness value to apply");
        }
        if (!(target instanceof Enemy)) {
            throw new IllegalArgumentException(
                    "declareMapAttack names " + target.getName() + ", which is not an enemy: the map hit reduces an "
                            + "enemy's toughness bar, and nothing else on the field has one");
        }
        mapAttacks.add(new MapAttack(attacker, target));
    }

    /**
     * @return the declared map attacks, in declaration order
     */
    public List<MapAttack> mapAttacks() {
        return List.copyOf(mapAttacks);
    }

    /**
     * One declared map basic attack.
     *
     * @param attacker the character whose map attack landed
     * @param target   the enemy it hit
     */
    public record MapAttack(CanHit attacker, CanHit target) {
    }
}
